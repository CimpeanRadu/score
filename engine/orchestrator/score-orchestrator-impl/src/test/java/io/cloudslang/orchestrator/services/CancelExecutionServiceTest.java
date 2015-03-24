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

import io.cloudslang.engine.queue.entities.ExecutionMessageConverter;
import io.cloudslang.engine.queue.services.QueueDispatcherService;
import io.cloudslang.facade.entities.Execution;
import io.cloudslang.facade.execution.ExecutionActionResult;
import io.cloudslang.facade.execution.ExecutionStatus;
import io.cloudslang.orchestrator.entities.ExecutionState;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.cloudslang.facade.execution.ExecutionSummary.EMPTY_BRANCH;
import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

@SuppressWarnings({"SpringContextConfigurationInspection"})
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = CancelExecutionServiceTest.Configurator.class)
public class CancelExecutionServiceTest {

    @Autowired
    private CancelExecutionService service;

    @Autowired
    private ExecutionStateService executionStateService;

    @Autowired
    private ExecutionSerializationUtil executionSerializationUtil;

    @Before
    public void resetMocks() {
        reset(executionStateService);
    }

    /////////////// requestCancelExecution ///////////////

    @Test(expected = RuntimeException.class)
    public void testRequestCancelNullExecutionId() {
        service.requestCancelExecution(null);
    }

    @Test
    public void testValidRequestCancel() {

        // Running
        checkValidRequestCancel(ExecutionStatus.RUNNING, ExecutionStatus.PENDING_CANCEL, ExecutionActionResult.SUCCESS);


        Map<String, String> contexts = new HashMap<>();
        contexts.put("context_a", "");
        // Paused
        Execution pausedExecutionObj = new Execution(1L, 1L, contexts);
        when(executionSerializationUtil.objFromBytes(any(byte[].class))).thenReturn(pausedExecutionObj);
        checkValidRequestCancel(ExecutionStatus.PAUSED, ExecutionStatus.PENDING_CANCEL, ExecutionActionResult.SUCCESS);
        assertThat(pausedExecutionObj.getPosition()).isNull();
        assertThat(pausedExecutionObj.getSystemContext().getFlowTerminationType()).isEqualTo(ExecutionStatus.CANCELED);

        // Cancel
        checkValidRequestCancel(ExecutionStatus.CANCELED, ExecutionStatus.CANCELED, ExecutionActionResult.FAILED_ALREADY_CANCELED_OR_PENDING_CANCELLATION);
    }

    private void checkValidRequestCancel(ExecutionStatus origStatus, ExecutionStatus expStatusAfterCancellation, ExecutionActionResult expectedResult) {
        Long executionId = 111L;
        ExecutionState ex1 = createRun(executionId, origStatus);
        when(executionStateService.readByExecutionIdAndBranchId(executionId, EMPTY_BRANCH)).thenReturn(ex1);
        ExecutionActionResult result = service.requestCancelExecution(executionId);

        // Validation - Status should be updated by the service
        assertThat(result).isEqualTo(expectedResult);
        assertThat(ex1.getStatus()).as("Wrong status after cancelling the execution").isEqualTo(expStatusAfterCancellation);
    }

    @Test
    public void testValidRequestCancel_pausedBranches() {
        mockPausedParentAndBranchAndRequestCancel();
    }

    @Test
    public void testValidRequestCancel_userPausedWithBranches() {
        mockPausedParentAndBranchAndRequestCancel();
    }

    @Test
    public void testValidRequestCancel_pausedBranchesNoWorkersInGroup() {
        mockPausedParentAndBranchAndRequestCancel();
    }

    private void mockPausedParentAndBranchAndRequestCancel() {
        Long executionId = 111L;
        ExecutionState parent = createRun(executionId, ExecutionStatus.PAUSED);
        when(executionStateService.readByExecutionIdAndBranchId(executionId, EMPTY_BRANCH)).thenReturn(parent);
        // mock branch
        ExecutionState branch1 = createRun(executionId, ExecutionStatus.PAUSED);
        branch1.setBranchId("b1");

        Map<String, String> contexts = new HashMap<>();
        contexts.put("context_a", "");

        Execution pausedExecutionObj = new Execution(1L, 1L, contexts);
        when(executionSerializationUtil.objFromBytes(any(byte[].class))).thenReturn(pausedExecutionObj);
        when(executionStateService.readByExecutionId(executionId)).thenReturn(Arrays.asList(parent, branch1));

        ExecutionActionResult result = service.requestCancelExecution(executionId);

        // Validation - Parent status should be Pending-cancel
        assertThat(result).isEqualTo(ExecutionActionResult.SUCCESS);
        assertThat(parent.getStatus()).as("Wrong status after cancelling the execution").isEqualTo(ExecutionStatus.PENDING_CANCEL);

        // Branch should be canceled
        assertThat(branch1.getStatus()).as("Wrong status after cancelling the branch").isEqualTo(ExecutionStatus.PENDING_CANCEL);
        assertThat(pausedExecutionObj.getPosition()).isNull();
        assertThat(pausedExecutionObj.getSystemContext().getFlowTerminationType()).isEqualTo(ExecutionStatus.CANCELED);
    }

    @Test
    public void testInvalidRequestCancel() {
        checkInvalidRequestCancel(ExecutionStatus.COMPLETED, ExecutionActionResult.FAILED_ALREADY_COMPLETED);
        checkInvalidRequestCancel(ExecutionStatus.PENDING_PAUSE, ExecutionActionResult.FAILED_PENDING_PAUSE);
        checkInvalidRequestCancel(ExecutionStatus.SYSTEM_FAILURE, ExecutionActionResult.FAILED_SYSTEM_FAILURE);
    }

    private void checkInvalidRequestCancel(ExecutionStatus executionStatus, ExecutionActionResult expectedResponse) {
        Long executionId = 111L;
        ExecutionState ex1 = createRun(executionId, executionStatus);

        when(executionStateService.readByExecutionIdAndBranchId(executionId, EMPTY_BRANCH)).thenReturn(ex1);
        ExecutionActionResult result = service.requestCancelExecution(executionId);

        // Validation - Status should be updated by the service
        assertThat(result).isEqualTo(expectedResponse);
        assertThat(ex1.getStatus()).as("Execution status shouldn't change").isEqualTo(executionStatus);
    }

    @Test
    public void testNotExistExecution() {
        Long executionId = 123L;
        when(executionStateService.readByExecutionIdAndBranchId(executionId, EMPTY_BRANCH)).thenReturn(null);
        ExecutionActionResult result = service.requestCancelExecution(executionId);

        assertThat(result).isEqualTo(ExecutionActionResult.FAILED_NOT_FOUND);
    }

    /////////////// isCancelledExecution ///////////////

    @Test
    public void testIsCancelledExecution() {
        checkIsCancelledExecution(ExecutionStatus.CANCELED, true);
        checkIsCancelledExecution(ExecutionStatus.PENDING_CANCEL, true);

        checkIsCancelledExecution(ExecutionStatus.RUNNING, false);
        checkIsCancelledExecution(ExecutionStatus.PAUSED, false);
        checkIsCancelledExecution(ExecutionStatus.PENDING_PAUSE, false);
        checkIsCancelledExecution(ExecutionStatus.COMPLETED, false);
        checkIsCancelledExecution(ExecutionStatus.SYSTEM_FAILURE, false);
    }

    private void checkIsCancelledExecution(ExecutionStatus executionStatus, boolean expResult) {
        Long executionId = 123L;
        ExecutionState entity = null;
        if (expResult) {
            entity = createRun(executionId, executionStatus);
        }

        when(executionStateService.readCancelledExecution(executionId)).thenReturn(entity);

        boolean result = service.isCanceledExecution(executionId);
        assertThat(result).isEqualTo(expResult);
    }

    /////////////// readCancelledExecutions ///////////////

    @Test
    public void testReadCancelledExecutions() {
        when(executionStateService.readExecutionIdByStatuses(getCancelStatuses())).thenReturn(Arrays.asList(123L, 456L));
        List<Long> result = service.readCanceledExecutionsIds();
        assertThat(result).hasSize(2);
    }

    @Test
    public void testReadCancelledExecutions_emptyList() {
        //noinspection unchecked
        when(executionStateService.readExecutionIdByStatuses(getCancelStatuses())).thenReturn(Collections.EMPTY_LIST);
        List<Long> result = service.readCanceledExecutionsIds();
        assertThat(result).isEmpty();
    }

    @Test
    public void testReadCancelledExecutions_null() {
        when(executionStateService.readExecutionIdByStatuses(getCancelStatuses())).thenReturn(null);
        List<Long> result = service.readCanceledExecutionsIds();
        assertThat(result).isEmpty();
    }

    /////////////// Helpers ///////////////

    private ExecutionState createRun(Long executionId, ExecutionStatus status) {
        ExecutionState executionState = new ExecutionState();
        executionState.setExecutionId(executionId);
        executionState.setStatus(status);

        return executionState;
    }

    private List<ExecutionStatus> getCancelStatuses() {
        return Arrays.asList(ExecutionStatus.CANCELED, ExecutionStatus.PENDING_CANCEL);
    }

    @Configuration
    static class Configurator {

        @Bean
        CancelExecutionService cancelExecutionService() {
            return new CancelExecutionServiceImpl();
        }

        @Bean
        ExecutionMessageConverter executionMessageConverter() {
            return new ExecutionMessageConverter();
        }

        @Bean
        ExecutionSerializationUtil executionSerializationUtil() {
            return mock(ExecutionSerializationUtil.class);
        }

        @Bean
        ExecutionStateService runService() {
            return mock(ExecutionStateService.class);
        }

        @Bean
        QueueDispatcherService queueDispatcherService() {
            return mock(QueueDispatcherService.class);
        }
    }
}