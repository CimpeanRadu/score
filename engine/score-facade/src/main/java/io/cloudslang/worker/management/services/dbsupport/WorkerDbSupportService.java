/*******************************************************************************
* (c) Copyright 2014 Hewlett-Packard Development Company, L.P.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Apache License v2.0 which accompany this distribution.
*
* The Apache License is available at
* http://www.apache.org/licenses/LICENSE-2.0
*
*******************************************************************************/

package io.cloudslang.worker.management.services.dbsupport;
import io.cloudslang.score.facade.entities.RunningExecutionPlan;

/**
 * Created by IntelliJ IDEA.
 * User: kravtsov
 * Date: 03/07/12
 * Time: 08:39
 */
public interface WorkerDbSupportService {
    /**
     *
     * @param id of the running execution plan
     * @return the running execution plan of the given id
     */
    RunningExecutionPlan readExecutionPlanById(Long id);
}
