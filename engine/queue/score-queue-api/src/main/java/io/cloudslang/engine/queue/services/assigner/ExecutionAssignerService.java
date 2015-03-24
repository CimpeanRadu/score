/*******************************************************************************
* (c) Copyright 2014 Hewlett-Packard Development Company, L.P.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Apache License v2.0 which accompany this distribution.
*
* The Apache License is available at
* http://www.apache.org/licenses/LICENSE-2.0
*
*******************************************************************************/

package io.cloudslang.engine.queue.services.assigner;

import io.cloudslang.engine.queue.entities.ExecutionMessage;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User:
 * Date: 19/11/12
 *
 * Responsible for assigning messages to workers while considering the workers groups
 */
public interface ExecutionAssignerService {

    /**
     *
     * assigns a list of {@link io.cloudslang.engine.queue.entities.ExecutionMessage} to
     * workers
     *
     * @param messages List of {@link io.cloudslang.engine.queue.entities.ExecutionMessage} to assign
     * @return List of assigned {@link io.cloudslang.engine.queue.entities.ExecutionMessage}
     */
    List<ExecutionMessage> assignWorkers(List<ExecutionMessage> messages);
}
