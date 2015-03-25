/*******************************************************************************
* (c) Copyright 2014 Hewlett-Packard Development Company, L.P.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Apache License v2.0 which accompany this distribution.
*
* The Apache License is available at
* http://www.apache.org/licenses/LICENSE-2.0
*
*******************************************************************************/

package io.cloudslang.orchestrator.services;

import io.cloudslang.engine.node.services.WorkerLockService;
import io.cloudslang.engine.node.services.WorkerNodeService;
import io.cloudslang.engine.queue.entities.ExecutionMessage;
import io.cloudslang.engine.queue.services.QueueDispatcherService;
import io.cloudslang.orchestrator.entities.SplitMessage;
import org.apache.commons.lang.Validate;
import org.apache.log4j.Logger;
import org.hamcrest.Matchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static ch.lambdaj.Lambda.filter;

/**
 * Date: 12/1/13
 *
 * @author
 */
public final class OrchestratorDispatcherServiceImpl implements OrchestratorDispatcherService {
	private final Logger logger = Logger.getLogger(getClass());

	@Autowired
	private QueueDispatcherService queueDispatcher;

	@Autowired
	private SplitJoinService splitJoinService;

    @Autowired
    private WorkerNodeService workerNodeService;

    @Autowired
    private WorkerLockService workerLockService;

    @Override
    @Transactional
    public void dispatch(List<? extends Serializable> messages, String bulkNumber, String wrv, String workerUuid) {
        //lock to synchronize with the recovery job
        workerLockService.lock(workerUuid);
        Validate.notNull(messages, "Messages list is null");

        String currentBulkNumber = workerNodeService.readByUUID(workerUuid).getBulkNumber();
        //can not be null at this point
        String currentWRV = workerNodeService.readByUUID(workerUuid).getWorkerRecoveryVersion();

        //This is done in order to make sure that if we do retries in worker we won't insert same bulk twice
        if(currentBulkNumber!=null && currentBulkNumber.equals(bulkNumber)){
            logger.warn("Orchestrator got messages bulk with same bulk number: " + bulkNumber + " This bulk was inserted to DB before. Discarding...");
        }
        //This is done in order to make sure that we are not getting messages from worker that was already recovered and does not know about it yet
        else if(!currentWRV.equals(wrv)){
            logger.warn("Orchestrator got messages from worker: " + workerUuid + " with wrong WRV:" + wrv + " Current WRV is: " + currentWRV +  ". Discarding...");
        }
        else {
            dispatch(messages);
            workerNodeService.updateBulkNumber(workerUuid, bulkNumber);
        }
    }

    private void dispatch(List<? extends Serializable> messages) {
        Validate.notNull(messages, "Messages list is null");

        if (logger.isDebugEnabled()) logger.debug("Dispatching " + messages.size() + " messages");
        long t = System.currentTimeMillis();
        final AtomicInteger messagesCounter = new AtomicInteger(0);

        dispatch(messages, ExecutionMessage.class, new Handler<ExecutionMessage>() {
            @Override
            public void handle(List<ExecutionMessage> messages) {
                messagesCounter.addAndGet(messages.size());
                queueDispatcher.dispatch(messages);
            }
        });

        dispatch(messages, SplitMessage.class, new Handler<SplitMessage>() {
            @Override
            public void handle(List<SplitMessage> messages) {
                messagesCounter.addAndGet(messages.size());
                splitJoinService.split(messages);
            }
        });

        t = System.currentTimeMillis()-t;
        if (logger.isDebugEnabled()) logger.debug("Dispatching " + messagesCounter.get() + " messages is done in " + t + " ms");
        if (messages.size() > messagesCounter.get()){
            logger.warn((messages.size() - messagesCounter.get()) + " messages were not being dispatched, since unknown type");
        }
    }

	private <T extends Serializable> void dispatch(List<? extends Serializable> messages, Class<T> messageClass, Handler<T> handler){
		@SuppressWarnings("unchecked")
		List<T> filteredMessages = (List<T>) filter(Matchers.instanceOf(messageClass), messages);
		if (!messages.isEmpty()){
			handler.handle(filteredMessages);
		}
	}

	private interface Handler<T>{
		public void handle(List<T> messages);
	}
}
