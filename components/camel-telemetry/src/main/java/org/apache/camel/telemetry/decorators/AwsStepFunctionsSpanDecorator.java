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
package org.apache.camel.telemetry.decorators;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.telemetry.Span;

public class AwsStepFunctionsSpanDecorator extends AbstractSpanDecorator {

    static final String STEPFUNCTIONS_OPERATION = "operation";
    static final String STEPFUNCTIONS_STATE_MACHINE_NAME = "stateMachineName";
    static final String STEPFUNCTIONS_STATE_MACHINE_ARN = "stateMachineArn";
    static final String STEPFUNCTIONS_EXECUTION_ARN = "executionArn";
    static final String STEPFUNCTIONS_EXECUTION_NAME = "executionName";

    /**
     * Constants copied from {@link org.apache.camel.component.aws2.stepfunctions.StepFunctions2Constants}
     */
    static final String OPERATION = "CamelAwsStepFunctionsOperation";
    static final String STATE_MACHINE_NAME = "CamelAwsStepFunctionsStateMachineName";
    static final String STATE_MACHINE_ARN = "CamelAwsStepFunctionsStateMachineArn";
    static final String EXECUTION_ARN = "CamelAwsStepFunctionsExecutionArn";
    static final String EXECUTION_NAME = "CamelAwsStepFunctionsExecutionName";

    @Override
    public String getComponent() {
        return "aws2-step-functions";
    }

    @Override
    public String getComponentClassName() {
        return "org.apache.camel.component.aws2.stepfunctions.StepFunctions2Component";
    }

    @Override
    public void beforeTracingEvent(Span span, Exchange exchange, Endpoint endpoint) {
        super.beforeTracingEvent(span, exchange, endpoint);

        String operation = exchange.getIn().getHeader(OPERATION, String.class);
        if (operation != null) {
            span.setTag(STEPFUNCTIONS_OPERATION, operation);
        }

        String stateMachineName = exchange.getIn().getHeader(STATE_MACHINE_NAME, String.class);
        if (stateMachineName != null) {
            span.setTag(STEPFUNCTIONS_STATE_MACHINE_NAME, stateMachineName);
        }

        String stateMachineArn = exchange.getIn().getHeader(STATE_MACHINE_ARN, String.class);
        if (stateMachineArn != null) {
            span.setTag(STEPFUNCTIONS_STATE_MACHINE_ARN, stateMachineArn);
        }

        String executionArn = exchange.getIn().getHeader(EXECUTION_ARN, String.class);
        if (executionArn != null) {
            span.setTag(STEPFUNCTIONS_EXECUTION_ARN, executionArn);
        }

        String executionName = exchange.getIn().getHeader(EXECUTION_NAME, String.class);
        if (executionName != null) {
            span.setTag(STEPFUNCTIONS_EXECUTION_NAME, executionName);
        }
    }

}
