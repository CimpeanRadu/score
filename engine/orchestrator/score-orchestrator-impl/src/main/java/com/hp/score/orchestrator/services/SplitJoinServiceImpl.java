package com.hp.score.orchestrator.services;

import ch.lambdaj.function.convert.Converter;
import com.hp.score.engine.queue.entities.ExecutionMessage;
import com.hp.score.engine.queue.entities.ExecutionMessageConverter;
import com.hp.score.engine.queue.services.QueueDispatcherService;
import com.hp.oo.enginefacade.execution.EndBranchDataContainer;
import com.hp.oo.enginefacade.execution.ExecutionStatus;
import com.hp.oo.internal.sdk.execution.Execution;
import com.hp.oo.internal.sdk.execution.ExecutionConstants;
import com.hp.score.orchestrator.entities.BranchContexts;
import com.hp.score.orchestrator.entities.FinishedBranch;
import com.hp.score.orchestrator.entities.SplitMessage;
import com.hp.score.orchestrator.entities.SuspendedExecution;
import com.hp.score.orchestrator.repositories.FinishedBranchRepository;
import com.hp.score.orchestrator.repositories.SuspendedExecutionsRepository;
import org.apache.commons.lang.Validate;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static ch.lambdaj.Lambda.convert;
import static ch.lambdaj.Lambda.extract;
import static ch.lambdaj.Lambda.on;

public final class SplitJoinServiceImpl implements SplitJoinService {
    private final Logger logger = Logger.getLogger(getClass());

    private final Integer BULK_SIZE = Integer.getInteger("splitjoin.job.bulk.size", 200);

    @Autowired
    private SuspendedExecutionsRepository suspendedExecutionsRepository;

    @Autowired
    private FinishedBranchRepository finishedBranchRepository;

    @Autowired
    private QueueDispatcherService queueDispatcherService;

    @Autowired
    private ExecutionMessageConverter converter;

    /*
        converts an execution to a fresh execution message for triggering a new flow
     */
    private final Converter<Execution, ExecutionMessage> executionToStartExecutionMessage = new Converter<Execution, ExecutionMessage>() {
        @Override
        public ExecutionMessage convert(Execution execution) {
            return new ExecutionMessage(execution.getExecutionId().toString(),
                    converter.createPayload(execution));
        }
    };

    /*
        converts an execution to a finish branch entity
     */
    private final Converter<Execution, FinishedBranch> executionToFinishedBranch = new Converter<Execution, FinishedBranch>() {
        @Override
        public FinishedBranch convert(Execution execution) {
            boolean isBranchCancelled = ExecutionStatus.CANCELED.equals(execution.getSystemContext().get(ExecutionConstants.FLOW_TERMINATION_TYPE));
            return new FinishedBranch(execution.getExecutionId().toString(), execution.getBranchId(), execution.getSplitId(), execution.getError(), new BranchContexts(isBranchCancelled, execution.getContexts(), execution.getSystemContext()));
        }
    };

    @Override
    @Transactional
    public void split(List<SplitMessage> splitMessages) {
        Validate.notNull(splitMessages, "split messages cannot be null");

        if (splitMessages.isEmpty())
            return;

        // these lists will be populated with values and inserted in bulk to the db
        List<ExecutionMessage> stepFinishMessages = new ArrayList<>();
        List<ExecutionMessage> branchTriggerMessages = new ArrayList<>();
        List<SuspendedExecution> suspendedParents = new ArrayList<>();

        for (SplitMessage splitMessage : splitMessages) {
            // 1. trigger all the child branches
            List<ExecutionMessage> childExecutionMessages = convert(splitMessage.getChildren(), executionToStartExecutionMessage);
            branchTriggerMessages.addAll(childExecutionMessages);

            // 2. suspend the parent
            suspendedParents.add(new SuspendedExecution(splitMessage.getParent().getExecutionId().toString(),
                    splitMessage.getSplitId(),
                    splitMessage.getChildren().size(),
                    splitMessage.getParent()));
        }

        List<ExecutionMessage> queueMessages = new ArrayList<>();
        queueMessages.addAll(branchTriggerMessages);
        queueMessages.addAll(stepFinishMessages);

        // write new branches and end of step messages to queue
        queueDispatcherService.dispatch(queueMessages);

        // save the suspended parent entities
        suspendedExecutionsRepository.save(suspendedParents);
    }

    @Override
    @Transactional
    public void endBranch(List<Execution> executions) {
        Validate.notNull(executions, "executions cannot be null");

        if (executions.isEmpty())
            return;

        for (Execution execution : executions) {
            //TODO - NonBlocking this will be deleted when the old branch mechanism is completely removed (after NB is re-implemented)
            Validate.isTrue(
                    (execution.getPosition() == null) ||
                            (execution.getPosition() != -1L && execution.getPosition() != -2L),
                    "branch finished with position = " + execution.getPosition() + " -> new branch mechanism is ending a branch from old mechanism");
            if (logger.isDebugEnabled())
                logger.debug("finishing branch " + execution.getBranchId() + " for execution " + execution.getExecutionId());
        }

        // get the split id's for a batch query
        List<String> splitIds = extract(executions, on(Execution.class).getSplitId());

        // fetch all suspended executions
        List<SuspendedExecution> suspendedExecutions = suspendedExecutionsRepository.findBySplitIdIn(splitIds);
        Map<String, SuspendedExecution> suspendedMap = new HashMap<>();
        for (SuspendedExecution se : suspendedExecutions) {
            suspendedMap.put(se.getSplitId(), se);
        }

        // validate that the returned result from the query contains entities for each of the split id's we asked
        // each finished branch must have it's parent in the suspended table, it is an illegal state otherwise
        for (String splitId : splitIds) {
            if (!suspendedMap.containsKey(splitId)) {
                Long executionId = findExecutionId(executions, splitId);
                logger.error("Couldn't find suspended execution for split " + splitId + " execution id: " + executionId);
            }
        }

        // create a finished branch entity for each execution
        List<FinishedBranch> finishedBranches = convert(executions, executionToFinishedBranch);

        List<SuspendedExecution> suspendedExecutionsWithOneBranch = new ArrayList<>();

        // add each finished branch to it's parent
        for (FinishedBranch finishedBranch : finishedBranches) {
            SuspendedExecution suspendedExecution = suspendedMap.get(finishedBranch.getSplitId());
            if (suspendedExecution != null) {
                finishedBranch.connectToSuspendedExecution(suspendedExecution);

                //this is an optimization for subflow (also works for MI with one branch :) )
                if (suspendedExecution.getNumberOfBranches() == 1) {
                    suspendedExecutionsWithOneBranch.add(suspendedExecution);
                } else {
                    finishedBranchRepository.save(finishedBranch);
                }
            }
        }

        if (!suspendedExecutionsWithOneBranch.isEmpty()) {
            joinAndSendToQueue(suspendedExecutionsWithOneBranch);
        }
    }

    private Long findExecutionId(List<Execution> executions, String splitId) {
        for (Execution execution : executions) {
            if (execution.getSplitId().equals(splitId)) {
                return execution.getExecutionId();
            }
        }
        return null;
    }

    @Override
    @Transactional
    public int joinFinishedSplits(int bulkSize) {

        // 1. Find all suspended executions that have all their branches ended
        PageRequest pageRequest = new PageRequest(0, bulkSize);
        List<SuspendedExecution> suspendedExecutions = suspendedExecutionsRepository.findFinishedSuspendedExecutions(pageRequest);

        return joinAndSendToQueue(suspendedExecutions);
    }

    @Override
    @Transactional
    public void joinFinishedSplits() {
        try {
            joinFinishedSplits(BULK_SIZE);
        } catch (Exception ex) {
            logger.error("SplitJoinJob failed", ex);
        }
    }

    private int joinAndSendToQueue(List<SuspendedExecution> suspendedExecutions) {
        List<ExecutionMessage> messages = new ArrayList<>();

        if (logger.isDebugEnabled())
            logger.debug("Joining finished branches, found " + suspendedExecutions.size() + " suspended executions with all branches finished");

        // nothing to do here
        if (suspendedExecutions.isEmpty())
            return 0;

        for (SuspendedExecution se : suspendedExecutions) {
            Execution exec = joinSplit(se);
            messages.add(executionToStartExecutionMessage.convert(exec));
        }

        // 3. send the suspended execution back to the queue
        queueDispatcherService.dispatch(messages);

        // 4. delete the suspended execution from the suspended table
        suspendedExecutionsRepository.delete(suspendedExecutions);

        return suspendedExecutions.size();
    }

    private Execution joinSplit(SuspendedExecution suspendedExecution) {

        List<FinishedBranch> finishedBranches = suspendedExecution.getFinishedBranches();
        Execution exec = suspendedExecution.getExecutionObj();

        Validate.isTrue(suspendedExecution.getNumberOfBranches().equals(finishedBranches.size()),
                "Expected suspended execution " + exec.getExecutionId() + " to have " + suspendedExecution.getNumberOfBranches() + "finished branches, but found " + finishedBranches.size());

        if (logger.isDebugEnabled())
            logger.debug("Joining execution " + exec.getExecutionId());

        boolean wasExecutionCancelled = false;
        ArrayList<EndBranchDataContainer> finishedContexts = new ArrayList<>();
        for (FinishedBranch fb : finishedBranches) {
            finishedContexts.add(new EndBranchDataContainer(fb.getBranchContexts().getContexts(), fb.getBranchContexts().getSystemContext(), fb.getBranchException()));
            if (fb.getBranchContexts().isBranchCancelled()) {
                wasExecutionCancelled = true;
            }
        }

        // 2. insert all of the branches into the parent execution
        exec.putFinishedChildBranchesData(finishedContexts);

        //mark cancelled on parent
        if (wasExecutionCancelled) {
            exec.getSystemContext().put(ExecutionConstants.FLOW_TERMINATION_TYPE, ExecutionStatus.CANCELED);
        }

        return exec;
    }
}
