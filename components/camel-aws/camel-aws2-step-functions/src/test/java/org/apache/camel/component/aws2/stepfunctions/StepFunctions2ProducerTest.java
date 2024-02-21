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

import java.util.Date;

import org.apache.camel.BindToRegistry;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sfn.model.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class StepFunctions2ProducerTest extends CamelTestSupport {

    @BindToRegistry("awsSfnClient")
    AmazonStepFunctionsClientMock clientMock = new AmazonStepFunctionsClientMock();

    @EndpointInject("mock:result")
    private MockEndpoint mock;

    @Test
    public void sfnCreateActivityTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:createActivity", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(StepFunctions2Constants.OPERATION, StepFunctions2Operations.createActivity);
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        CreateActivityResponse resultGet = (CreateActivityResponse) exchange.getIn().getBody();
        assertEquals("aws:sfn-activity::test:arn", resultGet.activityArn());
    }

    @Test
    public void sfnCreateActivityPojoTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:createPojoActivity", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(StepFunctions2Constants.OPERATION, StepFunctions2Operations.createActivity);
                exchange.getIn().setBody(CreateActivityRequest.builder().name("activity").build());
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        CreateActivityResponse resultGet = (CreateActivityResponse) exchange.getIn().getBody();
        assertEquals("aws:sfn-activity::test:arn", resultGet.activityArn());
    }

    @Test
    public void sfnCreateStateMachineTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:createStateMachine", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(StepFunctions2Constants.OPERATION, StepFunctions2Operations.createStateMachine);
                exchange.getIn().setHeader(StepFunctions2Constants.STATE_MACHINE_NAME, "state-machine");
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        CreateStateMachineResponse resultGet = (CreateStateMachineResponse) exchange.getIn().getBody();
        assertEquals("aws:sfn-state-machine::test:arn", resultGet.stateMachineArn());
    }

    @Test
    public void sfnDeleteStateMachineTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:deleteStateMachine", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(StepFunctions2Constants.OPERATION, StepFunctions2Operations.deleteStateMachine);
                exchange.getIn().setHeader(StepFunctions2Constants.STATE_MACHINE_ARN, "aws:sfn-state-machine::test:arn");
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        DeleteStateMachineResponse resultGet = (DeleteStateMachineResponse) exchange.getIn().getBody();
        assertNotNull(resultGet);
    }

    @Test
    public void sfnUpdateStateMachineTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:updateStateMachine", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(StepFunctions2Constants.OPERATION, StepFunctions2Operations.updateStateMachine);
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        UpdateStateMachineResponse resultGet = (UpdateStateMachineResponse) exchange.getIn().getBody();
        assertEquals("aws:sfn-state-machine:version-2::test:arn", resultGet.stateMachineVersionArn());
    }

    @Test
    public void sfnDescribeStateMachineTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:describeStateMachine", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(StepFunctions2Constants.OPERATION, StepFunctions2Operations.describeStateMachine);
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        DescribeStateMachineResponse resultGet = (DescribeStateMachineResponse) exchange.getIn().getBody();
        assertEquals("aws:sfn-state-machine::test-arn", resultGet.stateMachineArn());
    }

    @Test
    public void sfnListStateMachinesTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:listStateMachines", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(StepFunctions2Constants.OPERATION, StepFunctions2Operations.listStateMachines);
                exchange.getIn().setHeader(StepFunctions2Constants.STATE_MACHINES_MAX_RESULTS, 5);
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        ListStateMachinesResponse resultGet = (ListStateMachinesResponse) exchange.getIn().getBody();
        assertEquals(1, resultGet.stateMachines().size());
        assertEquals("aws:sfn-state-machine::test-arn", resultGet.stateMachines().get(0).stateMachineArn());
    }

    @Test
    public void sfnDeleteActivityTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:deleteActivity", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(StepFunctions2Constants.OPERATION, StepFunctions2Operations.deleteActivity);
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        DeleteActivityResponse resultGet = (DeleteActivityResponse) exchange.getIn().getBody();
        assertNotNull(resultGet);
    }

    @Test
    public void sfnDescribeActivityTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:describeActivity", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(StepFunctions2Constants.OPERATION, StepFunctions2Operations.describeActivity);
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        DescribeActivityResponse resultGet = (DescribeActivityResponse) exchange.getIn().getBody();
        assertEquals("aws:sfn-activity::test:arn", resultGet.activityArn());
    }

    @Test
    public void sfnGetActivityTaskTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:getActivityTask", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(StepFunctions2Constants.OPERATION, StepFunctions2Operations.getActivityTask);
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        GetActivityTaskResponse resultGet = (GetActivityTaskResponse) exchange.getIn().getBody();
        assertEquals("activity-input", resultGet.input());
    }

    @Test
    public void sfnListActivitiesTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:listActivities", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(StepFunctions2Constants.OPERATION, StepFunctions2Operations.listActivities);
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        ListActivitiesResponse resultGet = (ListActivitiesResponse) exchange.getIn().getBody();
        assertEquals(1, resultGet.activities().size());
        assertEquals("aws:sfn-activity::test:arn", resultGet.activities().get(0).activityArn());
    }

    @Test
    public void sfnStartExecutionTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:startExecution", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(StepFunctions2Constants.OPERATION, StepFunctions2Operations.startExecution);
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        StartExecutionResponse resultGet = (StartExecutionResponse) exchange.getIn().getBody();
        assertEquals("aws:sfn-execution::test:arn", resultGet.executionArn());
    }

    @Test
    public void sfnStartSyncExecutionTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:startSyncExecution", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(StepFunctions2Constants.OPERATION, StepFunctions2Operations.startSyncExecution);
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        StartSyncExecutionResponse resultGet = (StartSyncExecutionResponse) exchange.getIn().getBody();
        assertEquals("aws:sfn-execution::test:arn", resultGet.executionArn());
    }

    @Test
    public void sfnStopExecutionTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:stopExecution", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(StepFunctions2Constants.OPERATION, StepFunctions2Operations.stopExecution);
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        StopExecutionResponse resultGet = (StopExecutionResponse) exchange.getIn().getBody();
        assertEquals(new Date(1691423142).toInstant(), resultGet.stopDate());
    }

    @Test
    public void sfnDescribeExecutionTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:describeExecution", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(StepFunctions2Constants.OPERATION, StepFunctions2Operations.describeExecution);
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        DescribeExecutionResponse resultGet = (DescribeExecutionResponse) exchange.getIn().getBody();
        assertEquals("aws:sfn-activity::test:arn", resultGet.executionArn());
    }

    @Test
    public void sfnListExecutionsTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:listExecutions", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(StepFunctions2Constants.OPERATION, StepFunctions2Operations.listExecutions);
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        ListExecutionsResponse resultGet = (ListExecutionsResponse) exchange.getIn().getBody();
        assertEquals(1, resultGet.executions().size());
        assertEquals("aws:sfn-execution::test-arn", resultGet.executions().get(0).executionArn());
    }

    @Test
    public void sfnGetExecutionHistoryTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:getExecutionHistory", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(StepFunctions2Constants.OPERATION, StepFunctions2Operations.getExecutionHistory);
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        GetExecutionHistoryResponse resultGet = (GetExecutionHistoryResponse) exchange.getIn().getBody();
        assertEquals(1, resultGet.events().size());
        assertEquals(1L, resultGet.events().get(0).id());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:createActivity")
                        .to("aws2-step-functions://test?awsSfnClient=#awsSfnClient&operation=createActivity")
                        .to("mock:result");
                from("direct:createPojoActivity")
                        .to("aws2-step-functions://test?awsSfnClient=#awsSfnClient&operation=createActivity&pojoRequest=true")
                        .to("mock:result");
                from("direct:createStateMachine")
                        .to("aws2-step-functions://test?awsSfnClient=#awsSfnClient&operation=createStateMachine")
                        .to("mock:result");
                from("direct:deleteStateMachine")
                        .to("aws2-step-functions://test?awsSfnClient=#awsSfnClient&operation=deleteStateMachine")
                        .to("mock:result");
                from("direct:updateStateMachine")
                        .to("aws2-step-functions://test?awsSfnClient=#awsSfnClient&operation=updateStateMachine")
                        .to("mock:result");
                from("direct:listStateMachines")
                        .to("aws2-step-functions://test?awsSfnClient=#awsSfnClient&operation=listStateMachines")
                        .to("mock:result");
                from("direct:deleteActivity")
                        .to("aws2-step-functions://test?awsSfnClient=#awsSfnClient&operation=deleteActivity")
                        .to("mock:result");
                from("direct:describeActivity")
                        .to("aws2-step-functions://test?awsSfnClient=#awsSfnClient&operation=describeActivity")
                        .to("mock:result");
                from("direct:getActivityTask")
                        .to("aws2-step-functions://test?awsSfnClient=#awsSfnClient&operation=getActivityTask")
                        .to("mock:result");
                from("direct:listActivities")
                        .to("aws2-step-functions://test?awsSfnClient=#awsSfnClient&operation=listActivities")
                        .to("mock:result");
                from("direct:startExecution")
                        .to("aws2-step-functions://test?awsSfnClient=#awsSfnClient&operation=startExecution")
                        .to("mock:result");
                from("direct:startSyncExecution")
                        .to("aws2-step-functions://test?awsSfnClient=#awsSfnClient&operation=startSyncExecution")
                        .to("mock:result");
                from("direct:stopExecution").to("aws2-step-functions://test?awsSfnClient=#awsSfnClient&operation=stopExecution")
                        .to("mock:result");
                from("direct:describeExecution")
                        .to("aws2-step-functions://test?awsSfnClient=#awsSfnClient&operation=describeExecution")
                        .to("mock:result");
                from("direct:listExecutions")
                        .to("aws2-step-functions://test?awsSfnClient=#awsSfnClient&operation=listExecutions")
                        .to("mock:result");
                from("direct:getExecutionHistory")
                        .to("aws2-step-functions://test?awsSfnClient=#awsSfnClient&operation=getExecutionHistory")
                        .to("mock:result");
                from("direct:describeStateMachine")
                        .to("aws2-step-functions://test?awsSfnClient=#awsSfnClient&operation=describeStateMachine")
                        .to("mock:result");
            }
        };
    }
}
