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

import org.apache.camel.spi.Metadata;

public interface StepFunctions2Constants {

    @Metadata(description = "The operation that the producer performs, as text or as a StepFunctions2Operations. It replaces"
                            + " the operation option.",
              javaType = "String")
    String OPERATION = "CamelAwsStepFunctionsOperation";

    @Metadata(description = "The name of the state machine that createStateMachine creates.",
              javaType = "String")
    String STATE_MACHINE_NAME = "CamelAwsStepFunctionsStateMachineName";

    @Metadata(description = "The definition of the state machine in the Amazon States Language, as JSON text, for"
                            + " createStateMachine and updateStateMachine.",
              javaType = "String")
    String STATE_MACHINE_DEFINITION = "CamelAwsStepFunctionsStateMachineDefinition";

    @Metadata(description = "The type of the state machine that createStateMachine creates: STANDARD or EXPRESS, as text or"
                            + " as a StateMachineType. If not set, Step Functions creates a STANDARD state machine, and the"
                            + " type cannot be changed later.",
              javaType = "String")
    String STATE_MACHINE_TYPE = "CamelAwsStepFunctionsStateMachineType";

    @Metadata(description = "The Amazon Resource Name (ARN) of the IAM role that the state machine uses, for"
                            + " createStateMachine and updateStateMachine.",
              javaType = "String")
    String STATE_MACHINE_ROLE_ARN = "CamelAwsStepFunctionsStateMachineRoleArn";

    @Metadata(description = "The Amazon Resource Name (ARN) of the state machine, for deleteStateMachine,"
                            + " updateStateMachine, describeStateMachine, startExecution, startSyncExecution and"
                            + " listExecutions. To start or list executions, it can also be the ARN of a version or an alias"
                            + " of the state machine.",
              javaType = "String")
    String STATE_MACHINE_ARN = "CamelAwsStepFunctionsStateMachineArn";

    @Metadata(description = "The maximum number of state machines that listStateMachines returns, at most 1000. If not set"
                            + " or 0, Step Functions returns up to 100. The response holds one page only, with a nextToken"
                            + " when there are more.",
              javaType = "Integer")
    String STATE_MACHINES_MAX_RESULTS = "CamelAwsStepFunctionsStateMachinesMaxResults";

    @Metadata(description = "The name of the activity that createActivity creates.",
              javaType = "String")
    String ACTIVITY_NAME = "CamelAwsStepFunctionsActivityName";

    @Metadata(description = "The Amazon Resource Name (ARN) of the activity, for deleteActivity, describeActivity and"
                            + " getActivityTask.",
              javaType = "String")
    String ACTIVITY_ARN = "CamelAwsStepFunctionsActivityArn";

    @Metadata(description = "The maximum number of activities that listActivities returns, at most 1000. If not set or 0,"
                            + " Step Functions returns up to 100. The response holds one page only, with a nextToken when"
                            + " there are more.",
              javaType = "Integer")
    String ACTIVITIES_MAX_RESULTS = "CamelAwsStepFunctionsActivitiesMaxResults";

    @Metadata(description = "The Amazon Resource Name (ARN) of the execution, for stopExecution, describeExecution and"
                            + " getExecutionHistory.",
              javaType = "String")
    String EXECUTION_ARN = "CamelAwsStepFunctionsExecutionArn";

    @Metadata(description = "The name of the execution that startExecution or startSyncExecution starts, at most 80"
                            + " characters. If not set, Step Functions generates a UUID as the name.",
              javaType = "String")
    String EXECUTION_NAME = "CamelAwsStepFunctionsExecutionName";

    @Metadata(description = "The JSON input data of the execution that startExecution or startSyncExecution starts, as text"
                            + " (a String, a byte array or a stream). A value of another type, such as a Map, fails the"
                            + " exchange: marshal it to JSON first.",
              javaType = "String")
    String EXECUTION_INPUT = "CamelAwsStepFunctionsExecutionInput";

    @Metadata(label = "startSyncExecution",
              description = "The status of the execution: SUCCEEDED, FAILED or TIMED_OUT. The exchange does not fail when the"
                            + " execution did not succeed: check this header.",
              javaType = "String")
    String EXECUTION_STATUS = "CamelAwsStepFunctionsExecutionStatus";

    @Metadata(label = "startSyncExecution", description = "The error code of the failure, when the execution failed.",
              javaType = "String")
    String EXECUTION_ERROR = "CamelAwsStepFunctionsExecutionError";

    @Metadata(label = "startSyncExecution", description = "The cause of the failure, when the execution failed.",
              javaType = "String")
    String EXECUTION_CAUSE = "CamelAwsStepFunctionsExecutionCause";

    @Metadata(description = "The AWS X-Ray trace header of the execution that startExecution or startSyncExecution starts.",
              javaType = "String")
    String EXECUTION_TRACE_HEADER = "CamelAwsStepFunctionsExecutionTraceHeader";

    @Metadata(description = "The maximum number of events that getExecutionHistory returns, at most 1000. If not set or 0,"
                            + " Step Functions returns up to 100. The response holds one page only, with a nextToken when"
                            + " there are more.",
              javaType = "Integer")
    String EXECUTION_HISTORY_MAX_RESULTS = "CamelAwsStepFunctionsExecutionHistoryMaxResults";

    @Metadata(description = "Whether getExecutionHistory returns the execution data, that is the input and output of the"
                            + " history events. If not set, Step Functions returns it.",
              javaType = "Boolean")
    String EXECUTION_HISTORY_INCLUDE_EXECUTION_DATA = "CamelAwsStepFunctionsExecutionHistoryIncludeExecutionData";

    @Metadata(description = "Whether getExecutionHistory lists the events in descending order of their timestamp, the latest"
                            + " first. If not set, the events are in ascending order.",
              javaType = "Boolean")
    String EXECUTION_HISTORY_REVERSE_ORDER = "CamelAwsStepFunctionsExecutionHistoryReverseOrder";

    @Metadata(description = "The maximum number of executions of the state machine that listExecutions returns, at most"
                            + " 1000. If not set or 0, Step Functions returns up to 100. The response holds one page only,"
                            + " with a nextToken when there are more.",
              javaType = "Integer")
    String EXECUTIONS_MAX_RESULTS = "CamelAwsStepFunctionsExecutionMaxResults";

}
