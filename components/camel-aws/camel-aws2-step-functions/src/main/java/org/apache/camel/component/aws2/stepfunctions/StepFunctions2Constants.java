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

    @Metadata(description = "The operation we want to perform", javaType = "String")
    String OPERATION = "CamelAwsStepFunctionsOperation";

    @Metadata(description = "The name of the state machine", javaType = "String")
    String STATE_MACHINE_NAME = "CamelAwsStepFunctionsStateMachineName";

    @Metadata(description = "The Amazon States Language definition of the state machine", javaType = "String")
    String STATE_MACHINE_DEFINITION = "CamelAwsStepFunctionsStateMachineDefinition";

    @Metadata(description = "Determines whether a Standard or Express state machine is created", javaType = "String")
    String STATE_MACHINE_TYPE = "CamelAwsStepFunctionsStateMachineType";

    @Metadata(description = "The Amazon Resource Name (ARN) of the IAM role to use for this state machine.",
              javaType = "String")
    String STATE_MACHINE_ROLE_ARN = "CamelAwsStepFunctionsStateMachineRoleArn";

    @Metadata(description = "The Amazon Resource Name (ARN) of state machine.", javaType = "String")
    String STATE_MACHINE_ARN = "CamelAwsStepFunctionsStateMachineArn";

    @Metadata(description = "The limit number of results while listing state machines", javaType = "Integer")
    String STATE_MACHINES_MAX_RESULTS = "CamelAwsStepFunctionsStateMachinesMaxResults";

    @Metadata(description = "The name of the state machine activity", javaType = "String")
    String ACTIVITY_NAME = "CamelAwsStepFunctionsActivityName";

    @Metadata(description = "The ARN of the state machine activity", javaType = "String")
    String ACTIVITY_ARN = "CamelAwsStepFunctionsActivityArn";

    @Metadata(description = "The limit number of results while listing state machines", javaType = "Integer")
    String ACTIVITIES_MAX_RESULTS = "CamelAwsStepFunctionsActivitiesMaxResults";

    @Metadata(description = "The Amazon Resource Name (ARN) of the execution.", javaType = "String")
    String EXECUTION_ARN = "CamelAwsStepFunctionsExecutionArn";

    @Metadata(description = "Optional name of the execution.", javaType = "String")
    String EXECUTION_NAME = "CamelAwsStepFunctionsExecutionName";

    @Metadata(description = "The string that contains the JSON input data for the execution.", javaType = "String")
    String EXECUTION_INPUT = "CamelAwsStepFunctionsExecutionInput";

    @Metadata(description = "Passes the X-Ray trace header.", javaType = "String")
    String EXECUTION_TRACE_HEADER = "CamelAwsStepFunctionsExecutionTraceHeader";

    @Metadata(description = "The limit number of results while listing execution history", javaType = "Integer")
    String EXECUTION_HISTORY_MAX_RESULTS = "CamelAwsStepFunctionsExecutionHistoryMaxResults";

    @Metadata(description = "You can select whether execution data (input or output of a history event) is returned.",
              javaType = "Boolean")
    String EXECUTION_HISTORY_INCLUDE_EXECUTION_DATA = "CamelAwsStepFunctionsExecutionHistoryIncludeExecutionData";

    @Metadata(description = "Lists events in descending order of their timeStamp.", javaType = "Boolean")
    String EXECUTION_HISTORY_REVERSE_ORDER = "CamelAwsStepFunctionsExecutionHistoryReverseOrder";

    @Metadata(description = "The limit number of results while listing executions", javaType = "Integer")
    String EXECUTIONS_MAX_RESULTS = "CamelAwsStepFunctionsExecutionMaxResults";

}
