/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.aws2.stepfunctions;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import software.amazon.awssdk.services.sfn.SfnClient;
import software.amazon.awssdk.services.sfn.model.*;

public class AmazonStepFunctionsClientMock implements SfnClient {

    public AmazonStepFunctionsClientMock() {
    }

    @Override
    public CreateActivityResponse createActivity(CreateActivityRequest createActivityRequest) {
        CreateActivityResponse.Builder result = CreateActivityResponse.builder();
        result.activityArn("aws:sfn-activity::test:arn");
        return result.build();
    }

    @Override
    public CreateStateMachineResponse createStateMachine(CreateStateMachineRequest createStateMachineRequest) {
        CreateStateMachineResponse.Builder result = CreateStateMachineResponse.builder();
        result.stateMachineArn("aws:sfn-state-machine::test:arn");
        return result.build();
    }

    @Override
    public DeleteActivityResponse deleteActivity(DeleteActivityRequest deleteActivityRequest) {
        DeleteActivityResponse.Builder result = DeleteActivityResponse.builder();
        return result.build();
    }

    @Override
    public DeleteStateMachineResponse deleteStateMachine(DeleteStateMachineRequest deleteStateMachineRequest) {
        DeleteStateMachineResponse.Builder result = DeleteStateMachineResponse.builder();
        return result.build();
    }

    @Override
    public DescribeActivityResponse describeActivity(DescribeActivityRequest describeActivityRequest) {
        DescribeActivityResponse.Builder result = DescribeActivityResponse.builder();
        result.activityArn("aws:sfn-activity::test:arn");
        return result.build();
    }

    @Override
    public DescribeExecutionResponse describeExecution(DescribeExecutionRequest describeExecutionRequest) {
        DescribeExecutionResponse.Builder result = DescribeExecutionResponse.builder();
        result.executionArn("aws:sfn-activity::test:arn");
        return result.build();
    }

    @Override
    public DescribeStateMachineResponse describeStateMachine(DescribeStateMachineRequest describeStateMachineRequest) {
        DescribeStateMachineResponse.Builder result = DescribeStateMachineResponse.builder();
        result.stateMachineArn("aws:sfn-state-machine::test-arn");
        return result.build();
    }

    @Override
    public GetActivityTaskResponse getActivityTask(GetActivityTaskRequest getActivityTaskRequest) {
        GetActivityTaskResponse.Builder result = GetActivityTaskResponse.builder();
        result.input("activity-input");
        return result.build();
    }

    @Override
    public GetExecutionHistoryResponse getExecutionHistory(GetExecutionHistoryRequest getExecutionHistoryRequest) {
        GetExecutionHistoryResponse.Builder result = GetExecutionHistoryResponse.builder();
        List<HistoryEvent> events = new ArrayList<>();
        events.add(HistoryEvent.builder().id(1L).build());
        result.events(events);
        return result.build();
    }

    @Override
    public ListActivitiesResponse listActivities(ListActivitiesRequest listActivitiesRequest) {
        ListActivitiesResponse.Builder result = ListActivitiesResponse.builder();
        List<ActivityListItem> activityListItems = new ArrayList<>();
        activityListItems.add(ActivityListItem.builder().activityArn("aws:sfn-activity::test:arn").build());
        result.activities(activityListItems);
        return result.build();
    }

    @Override
    public ListExecutionsResponse listExecutions(ListExecutionsRequest listExecutionsRequest) {
        ListExecutionsResponse.Builder result = ListExecutionsResponse.builder();
        List<ExecutionListItem> executionListItems = new ArrayList<>();
        executionListItems.add(ExecutionListItem.builder().executionArn("aws:sfn-execution::test-arn").build());
        result.executions(executionListItems);
        return result.build();
    }

    @Override
    public ListStateMachinesResponse listStateMachines(ListStateMachinesRequest listStateMachinesRequest) {
        ListStateMachinesResponse.Builder result = ListStateMachinesResponse.builder();
        List<StateMachineListItem> stateMachineListItems = new ArrayList<>();
        stateMachineListItems.add(StateMachineListItem.builder().stateMachineArn("aws:sfn-state-machine::test-arn").build());
        result.stateMachines(stateMachineListItems);
        return result.build();
    }

    @Override
    public StartExecutionResponse startExecution(StartExecutionRequest startExecutionRequest) {
        StartExecutionResponse.Builder result = StartExecutionResponse.builder();
        result.executionArn("aws:sfn-execution::test:arn");
        return result.build();
    }

    @Override
    public StartSyncExecutionResponse startSyncExecution(StartSyncExecutionRequest startSyncExecutionRequest) {
        StartSyncExecutionResponse.Builder result = StartSyncExecutionResponse.builder();
        result.executionArn("aws:sfn-execution::test:arn");
        return result.build();
    }

    @Override
    public StopExecutionResponse stopExecution(StopExecutionRequest stopExecutionRequest) {
        StopExecutionResponse.Builder result = StopExecutionResponse.builder();
        result.stopDate(new Date(1691423142).toInstant());
        return result.build();
    }

    @Override
    public UpdateStateMachineResponse updateStateMachine(UpdateStateMachineRequest updateStateMachineRequest) {
        UpdateStateMachineResponse.Builder result = UpdateStateMachineResponse.builder();
        result.stateMachineVersionArn("aws:sfn-state-machine:version-2::test:arn");
        return result.build();
    }

    @Override
    public String serviceName() {
        return SfnClient.SERVICE_NAME;
    }

    @Override
    public void close() {

    }
}
