package com.hp.oo.enginefacade.execution;

import com.hp.oo.enginefacade.execution.log.ExecutionLog;
import com.hp.oo.enginefacade.execution.log.StepInfo;
import com.hp.oo.enginefacade.execution.log.StepLog;
import com.hp.oo.internal.sdk.execution.events.ExecutionEvent;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: butensky
 * Date: 11/28/12
 * Time: 2:09 PM
 */
@SuppressWarnings("UnusedDeclaration")
public interface EngineExecutionFacade {
    /**
     * Returns list of Executions that started on the given startDate, or afterwards.
     *
     * @param flowPath  - a pattern that is matched against the flowPath field of the entities. null or empty string disables this filter.
     * @param statuses  - the list of execution statuses used for filtering. null or empty list disables this filter.
     * @param resultStatusTypes - the list of result status types for filtering. null or empty list disables this filter.
     * @param pauseReasons - the list of pause reasons for filtering. Available only when PAUSED is in the list of statuses. null or empty list disables this filter.
     * @param owner     - a pattern that is matched against the owner field of the entities. null or empty string disables this filter.
     * @param startedBefore      - the earlier start time of the executions (we'll return executions that started on this data, or before)
     * @param startedAfter       - the furthest start time of the executions (we'll return executions that started on this data, or after)
     * @param pageNum   - the page to return, one-based.
     * @param pageSize  - the number of executions to return  @return a list of Executions that started on the given startDate or before.
     */
    List<ExecutionSummary> readExecutions(String flowPath,
                                          List<ExecutionEnums.ExecutionStatus> statuses,
                                          List<String> resultStatusTypes,
                                          List<PauseReason> pauseReasons,
                                          String owner,
                                          Date startedBefore,
                                          Date startedAfter,
                                          int pageNum,
                                          int pageSize);

    /**
     * Returns list of Executions for the given executions Ids.
     *
     * @param executionIds the executions to return
     * @return a list of ExecutionSummary objects, corresponding to the given executionIds.
     */
    List<ExecutionSummary> readExecutionsByIds(List<String> executionIds);

    /**
     * Returns list of ExecutionSummary objects, filtered by the given executionIds and statuses.
     *
     * @param executionIds - the ids of the Executions to return.
     * @param statuses     - filter by the given statuses.
     * @return ExecutionSummary if its executionId is in the given executionIds list, and its status is in the given statuses list.
     */
    List<ExecutionSummary> readExecutionsByStatus(List<String> executionIds, List<ExecutionEnums.ExecutionStatus> statuses);

    /**
     * Returns a representation of the steps under the specified path for a given executionId
     * This method will aggregate the event table into an hierarchical representation of it for a given
     * execution
     *
     * @param executionId the execution id
     * @param path        a path (in the form of %d.%d.%d) for a given parent step
     * @return a list of the child steps
     */
    List<StepInfo> readStepsByPath(String executionId, String path);

    /**
     * Returns a representation of the steps under the specified path for a given executionId
     * This method will aggregate the event table into an hierarchical representation of it for a given
     * execution
     *
     * @param executionId the execution id
     * @param path        a path (in the form of %d.%d.%d) for a given parent step
     * @param count
     * @return a list of the child steps
     */
    List<StepInfo> readStepsByPath(String executionId, String path, int count) ;

    /**
     * Returns a representation of all the steps for a given executionId, for use when execution logLevel = ERROR
     * This method will aggregate the event table into an hierarchical representation of it for a given
     * execution
     *
     * @param executionId the execution id
     * @return a list of the child steps
     */
    List<StepInfo> readAllSteps(String executionId);

    /**
     * Returns an aggregation of all the events for a given step
     *
     * @param executionId the execution id
     * @param path        a path (in the form of %d.%d.%d) for a given step
     * @return an object of aggregated step data
     */
    StepLog readExecutionStepLogByPath(String executionId, String path);

    /**
     * @param executionId the execution id to query
     * @return an object with info of a specific execution
     */
    ExecutionLog readExecutionLog(String executionId);

    /**
     * @param executionId the execution id to query
     * @return the log level of the requested execution
     */
    ExecutionEnums.LogLevel readExecutionLogLevel(String executionId);

    List<ExecutionEvent> readEventsByExecutionId(String executionId); // used by FlowExecutionController in flow-execution-impl

    ExecutionSummary readExecutionSummary(String executionId); // used by FlowExecutionController in flow-execution-impl

    List<ExecutionSummary> readExecutionsByFlow(String flowUuid, Date startTime, Date endTime, int offsetIndex, int pageSize);

    List<String> createExecutionsSummaries(Collection<ExecutionSummary> executionSummaries);

    List<ExecutionEvent> readEventsByExecutionIdByEventType(String executionId, ExecutionEnums.Event... eventType);

    List<ExecutionEvent> readEventsByExecutionIdAndIndexGreaterByEventType(String executionId, Long index, ExecutionEnums.Event... eventType);

    /**
     * support creating and updating execution debug interrupts
     * as will as overriding execution context content such as step/flow/global
     */
    void setExecutionContextsAndInterrupts(String executionId, Map<String, String> map);

    /**
     * Request the engine to cancel the given execution.
     *
     * @param executionId - the execution to cancel.
     * @return true if the request is processed, false otherwise.
     */
    boolean requestCancelExecution(String executionId);

    /**
     * Create a bound input entity and persist it to the DB
     *
     * @param executionId    the executionId the input was recorded under
     * @param inputName      the name of the input
     * @param domainTermName the name of the domain term
     * @param value          the bounded value of this input
     */
    void createBoundInput(String executionId, String inputName, String domainTermName, String value);


    /**
     * Create a list of bound inputs
     *
     * @param executionBoundInputs a collection of bound input entities
     */
    void createBoundInputs(List<ExecutionBoundInput> executionBoundInputs);

    /**
     * Filters all bound inputs by 2 params
     *
     * @param inputName the name of the input
     * @param value     the value of the input
     * @return a list of execution id's that were filtered
     */
    List<String> readExecutionIdsByInputNameAndValue(String inputName, String value);

    /**
     * Filters all bound inputs by 2 params
     *
     * @param domainTermName the name of the domain term
     * @param value          the value of the input
     * @return a list of execution id's that were filtered
     */
    List<String> readExecutionIdsByDomainTermNameAndValue(String domainTermName, String value);

    /**
     * Updates the current owner of the execution
     *
     * @param executionId - the execution id
     * @param owner       - the user to update to
     * @return the updated ExecutionSummary object, or null if the given Execution wasn't found.
     */
    ExecutionSummary updateExecutionOwner(String executionId, String owner);

    /**
     * Returns the statistic data of the given flow.
     *
     * @param flowUuid - the uuid of the flow to return its statistic.
     *                 todo: dates params
     */
    FlowStatisticsData readFlowStatistics(String flowUuid, Date fromDate, Date toDate);

    /**
     * Returns the statistic data of list of flows, according to the given criteria.
     *
     * @param top          - Number of flows to return. The selected flows are according to sort. If null - returns all flows.
     * @param measurements - The statistic measurement to return. If the list is empty - returns all supported measurements.
     * @param sortBy       - which measurement to sort By. If ‘measurement’ is given, so ‘sortBy’ must be one of the values that were given in ‘measurement’. If empty, we sort by number-of-executions.
     * @param isDescending - determines the sorting direction.
     *                     todo: dates params
     */
    List<FlowStatisticsData> readFlowsStatistics(Integer top, List<StatisticMeasurementsEnum> measurements, SortingStatisticMeasurementsEnum sortBy, Boolean isDescending, Date fromDate, Date toDate);

}