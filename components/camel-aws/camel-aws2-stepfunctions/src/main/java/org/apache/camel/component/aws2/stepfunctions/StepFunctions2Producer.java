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

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Message;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckHelper;
import org.apache.camel.health.WritableHealthCheckRepository;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.sfn.SfnClient;
import software.amazon.awssdk.services.sfn.model.*;

/**
 * A Producer which sends messages to the Amazon Web Service Step Functions
 * <a href="https://aws.amazon.com/step-functions/">AWS Step Functions</a>
 */
public class StepFunctions2Producer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(StepFunctions2Producer.class);

    private transient String sfnProducerToString;

    private HealthCheck producerHealthCheck;
    private WritableHealthCheckRepository healthCheckRepository;

    public StepFunctions2Producer(final Endpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(final Exchange exchange) throws Exception {
        switch (determineOperation(exchange)) {
            case createStateMachine -> createStateMachine(getEndpoint().getAwsSfnClient(), exchange);
            case deleteStateMachine -> deleteStateMachine(getEndpoint().getAwsSfnClient(), exchange);
            case updateStateMachine -> updateStateMachine(getEndpoint().getAwsSfnClient(), exchange);
            case describeStateMachine -> describeStateMachine(getEndpoint().getAwsSfnClient(), exchange);
            case listStateMachines -> listStateMachines(getEndpoint().getAwsSfnClient(), exchange);
            case createActivity -> createActivity(getEndpoint().getAwsSfnClient(), exchange);
            case deleteActivity -> deleteActivity(getEndpoint().getAwsSfnClient(), exchange);
            case describeActivity -> describeActivity(getEndpoint().getAwsSfnClient(), exchange);
            case getActivityTask -> getActivityTask(getEndpoint().getAwsSfnClient(), exchange);
            case listActivities -> listActivities(getEndpoint().getAwsSfnClient(), exchange);
            case startExecution -> startExecution(getEndpoint().getAwsSfnClient(), exchange);
            case startSyncExecution -> startSyncExecution(getEndpoint().getAwsSfnClient(), exchange);
            case stopExecution -> stopExecution(getEndpoint().getAwsSfnClient(), exchange);
            case describeExecution -> describeExecution(getEndpoint().getAwsSfnClient(), exchange);
            case listExecutions -> listExecutions(getEndpoint().getAwsSfnClient(), exchange);
            case getExecutionHistory -> getExecutionHistory(getEndpoint().getAwsSfnClient(), exchange);
            default -> throw new IllegalArgumentException("Unsupported operation");
        }
    }

    private StepFunctions2Operations determineOperation(Exchange exchange) {
        StepFunctions2Operations operation
                = exchange.getIn().getHeader(StepFunctions2Constants.OPERATION, StepFunctions2Operations.class);
        if (operation == null) {
            operation = getConfiguration().getOperation();
        }
        return operation;
    }

    protected StepFunctions2Configuration getConfiguration() {
        return getEndpoint().getConfiguration();
    }

    @Override
    public String toString() {
        if (sfnProducerToString == null) {
            sfnProducerToString = "StepFunctionsProducer[" + URISupport.sanitizeUri(getEndpoint().getEndpointUri()) + "]";
        }
        return sfnProducerToString;
    }

    @Override
    public StepFunctions2Endpoint getEndpoint() {
        return (StepFunctions2Endpoint) super.getEndpoint();
    }

    private void createStateMachine(SfnClient sfnClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof CreateStateMachineRequest request) {
                CreateStateMachineResponse result;
                try {
                    result = sfnClient.createStateMachine(request);
                } catch (AwsServiceException ase) {
                    LOG.trace("Create State Machine command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            CreateStateMachineRequest.Builder builder = CreateStateMachineRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(StepFunctions2Constants.STATE_MACHINE_NAME))) {
                String stateMachineName = exchange.getIn().getHeader(StepFunctions2Constants.STATE_MACHINE_NAME, String.class);
                builder.name(stateMachineName);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(StepFunctions2Constants.STATE_MACHINE_DEFINITION))) {
                String stateMachineDefinition
                        = exchange.getIn().getHeader(StepFunctions2Constants.STATE_MACHINE_DEFINITION, String.class);
                builder.definition(stateMachineDefinition);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(StepFunctions2Constants.STATE_MACHINE_TYPE))) {
                if (exchange.getIn().getHeader(StepFunctions2Constants.STATE_MACHINE_TYPE) instanceof StateMachineType) {
                    StateMachineType stateMachineType
                            = exchange.getIn().getHeader(StepFunctions2Constants.STATE_MACHINE_TYPE, StateMachineType.class);
                    builder.type(stateMachineType);
                } else {
                    String stateMachineType
                            = exchange.getIn().getHeader(StepFunctions2Constants.STATE_MACHINE_TYPE, String.class);
                    builder.type(stateMachineType);
                }
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(StepFunctions2Constants.STATE_MACHINE_ROLE_ARN))) {
                String stateMachineRoleArn
                        = exchange.getIn().getHeader(StepFunctions2Constants.STATE_MACHINE_ROLE_ARN, String.class);
                builder.roleArn(stateMachineRoleArn);
            }
            CreateStateMachineResponse result;
            try {
                result = sfnClient.createStateMachine(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Create State Machine command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void deleteStateMachine(SfnClient sfnClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof DeleteStateMachineRequest request) {
                DeleteStateMachineResponse result;
                try {
                    result = sfnClient.deleteStateMachine(request);
                } catch (AwsServiceException ase) {
                    LOG.trace("Delete State Machine command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            DeleteStateMachineRequest.Builder builder = DeleteStateMachineRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(StepFunctions2Constants.STATE_MACHINE_ARN))) {
                String stateMachineArn = exchange.getIn().getHeader(StepFunctions2Constants.STATE_MACHINE_ARN, String.class);
                builder.stateMachineArn(stateMachineArn);
            }

            DeleteStateMachineResponse result;
            try {
                DeleteStateMachineRequest request = builder.build();
                result = sfnClient.deleteStateMachine(request);
            } catch (AwsServiceException ase) {
                LOG.trace("Delete State Machine command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void updateStateMachine(SfnClient sfnClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof UpdateStateMachineRequest request) {
                UpdateStateMachineResponse result;
                try {
                    result = sfnClient.updateStateMachine(request);
                } catch (AwsServiceException ase) {
                    LOG.trace("Update State Machine command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            UpdateStateMachineRequest.Builder builder = UpdateStateMachineRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(StepFunctions2Constants.STATE_MACHINE_ARN))) {
                String stateMachineArn = exchange.getIn().getHeader(StepFunctions2Constants.STATE_MACHINE_ARN, String.class);
                builder.stateMachineArn(stateMachineArn);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(StepFunctions2Constants.STATE_MACHINE_DEFINITION))) {
                String stateMachineDefinition
                        = exchange.getIn().getHeader(StepFunctions2Constants.STATE_MACHINE_DEFINITION, String.class);
                builder.definition(stateMachineDefinition);
            }

            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(StepFunctions2Constants.STATE_MACHINE_ROLE_ARN))) {
                String stateMachineRoleArn
                        = exchange.getIn().getHeader(StepFunctions2Constants.STATE_MACHINE_ROLE_ARN, String.class);
                builder.roleArn(stateMachineRoleArn);
            }
            UpdateStateMachineResponse result;
            try {
                result = sfnClient.updateStateMachine(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Update State Machine command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void describeStateMachine(SfnClient sfnClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof DescribeStateMachineRequest request) {
                DescribeStateMachineResponse result;
                try {
                    result = sfnClient.describeStateMachine(request);
                } catch (AwsServiceException ase) {
                    LOG.trace("Describe State Machine command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            DescribeStateMachineRequest.Builder builder = DescribeStateMachineRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(StepFunctions2Constants.STATE_MACHINE_ARN))) {
                String stateMachineArn = exchange.getIn().getHeader(StepFunctions2Constants.STATE_MACHINE_ARN, String.class);
                builder.stateMachineArn(stateMachineArn);
            }

            DescribeStateMachineResponse result;
            try {
                result = sfnClient.describeStateMachine(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Describe State Machine command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void listStateMachines(SfnClient sfnClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof ListStateMachinesRequest request) {
                ListStateMachinesResponse result;
                try {
                    result = sfnClient.listStateMachines(request);
                } catch (AwsServiceException ase) {
                    LOG.trace("List State Machines command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            ListStateMachinesRequest.Builder builder = ListStateMachinesRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(StepFunctions2Constants.STATE_MACHINES_MAX_RESULTS))) {
                int maxRes = exchange.getIn().getHeader(StepFunctions2Constants.STATE_MACHINES_MAX_RESULTS, Integer.class);
                builder.maxResults(maxRes);
            }

            ListStateMachinesResponse result;
            try {
                result = sfnClient.listStateMachines(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("List State Machines command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void createActivity(SfnClient sfnClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof CreateActivityRequest request) {
                CreateActivityResponse result;
                try {
                    result = sfnClient.createActivity(request);
                } catch (AwsServiceException ase) {
                    LOG.trace("Create Activity command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            CreateActivityRequest.Builder builder = CreateActivityRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(StepFunctions2Constants.ACTIVITY_NAME))) {
                String activityName = exchange.getIn().getHeader(StepFunctions2Constants.ACTIVITY_NAME, String.class);
                builder.name(activityName);
            }

            CreateActivityResponse result;
            try {
                result = sfnClient.createActivity(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Create Activity command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void deleteActivity(SfnClient sfnClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof DeleteActivityRequest request) {
                DeleteActivityResponse result;
                try {
                    result = sfnClient.deleteActivity(request);
                } catch (AwsServiceException ase) {
                    LOG.trace("Delete Activity command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            DeleteActivityRequest.Builder builder = DeleteActivityRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(StepFunctions2Constants.ACTIVITY_ARN))) {
                String activityArn = exchange.getIn().getHeader(StepFunctions2Constants.ACTIVITY_ARN, String.class);
                builder.activityArn(activityArn);
            }

            DeleteActivityResponse result;
            try {
                result = sfnClient.deleteActivity(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Delete Activity command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void describeActivity(SfnClient sfnClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof DescribeActivityRequest request) {
                DescribeActivityResponse result;
                try {
                    result = sfnClient.describeActivity(request);
                } catch (AwsServiceException ase) {
                    LOG.trace("Describe Activity command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            DescribeActivityRequest.Builder builder = DescribeActivityRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(StepFunctions2Constants.ACTIVITY_ARN))) {
                String activityArn = exchange.getIn().getHeader(StepFunctions2Constants.ACTIVITY_ARN, String.class);
                builder.activityArn(activityArn);
            }

            DescribeActivityResponse result;
            try {
                result = sfnClient.describeActivity(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Describe Activity command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void getActivityTask(SfnClient sfnClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof GetActivityTaskRequest request) {
                GetActivityTaskResponse result;
                try {
                    result = sfnClient.getActivityTask(request);
                } catch (AwsServiceException ase) {
                    LOG.trace("Get Activity Task command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            GetActivityTaskRequest.Builder builder = GetActivityTaskRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(StepFunctions2Constants.ACTIVITY_ARN))) {
                String activityArn = exchange.getIn().getHeader(StepFunctions2Constants.ACTIVITY_ARN, String.class);
                builder.activityArn(activityArn);
            }

            GetActivityTaskResponse result;
            try {
                result = sfnClient.getActivityTask(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Get Activity Task command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void listActivities(SfnClient sfnClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof ListActivitiesRequest request) {
                ListActivitiesResponse result;
                try {
                    result = sfnClient.listActivities(request);
                } catch (AwsServiceException ase) {
                    LOG.trace("List Activities command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            ListActivitiesRequest.Builder builder = ListActivitiesRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(StepFunctions2Constants.ACTIVITIES_MAX_RESULTS))) {
                int maxRes = exchange.getIn().getHeader(StepFunctions2Constants.ACTIVITIES_MAX_RESULTS, Integer.class);
                builder.maxResults(maxRes);
            }

            ListActivitiesResponse result;
            try {
                result = sfnClient.listActivities(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("List Activities command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void startExecution(SfnClient sfnClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof StartExecutionRequest request) {
                StartExecutionResponse result;
                try {
                    result = sfnClient.startExecution(request);
                } catch (AwsServiceException ase) {
                    LOG.trace("Start Execution command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            StartExecutionRequest.Builder builder = StartExecutionRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(StepFunctions2Constants.STATE_MACHINE_ARN))) {
                String stateMachineArn = exchange.getIn().getHeader(StepFunctions2Constants.STATE_MACHINE_ARN, String.class);
                builder.stateMachineArn(stateMachineArn);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(StepFunctions2Constants.EXECUTION_NAME))) {
                String executionName = exchange.getIn().getHeader(StepFunctions2Constants.EXECUTION_NAME, String.class);
                builder.name(executionName);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(StepFunctions2Constants.EXECUTION_INPUT))) {
                String executionInput = exchange.getIn().getHeader(StepFunctions2Constants.EXECUTION_INPUT, String.class);
                builder.input(executionInput);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(StepFunctions2Constants.EXECUTION_TRACE_HEADER))) {
                String executionTraceHeader
                        = exchange.getIn().getHeader(StepFunctions2Constants.EXECUTION_TRACE_HEADER, String.class);
                builder.traceHeader(executionTraceHeader);
            }

            StartExecutionResponse result;
            try {
                result = sfnClient.startExecution(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Start Execution command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void startSyncExecution(SfnClient sfnClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof StartSyncExecutionRequest request) {
                StartSyncExecutionResponse result;
                try {
                    result = sfnClient.startSyncExecution(request);
                } catch (AwsServiceException ase) {
                    LOG.trace("Start Sync Execution command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            StartSyncExecutionRequest.Builder builder = StartSyncExecutionRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(StepFunctions2Constants.STATE_MACHINE_ARN))) {
                String stateMachineArn = exchange.getIn().getHeader(StepFunctions2Constants.STATE_MACHINE_ARN, String.class);
                builder.stateMachineArn(stateMachineArn);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(StepFunctions2Constants.EXECUTION_NAME))) {
                String executionName = exchange.getIn().getHeader(StepFunctions2Constants.EXECUTION_NAME, String.class);
                builder.name(executionName);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(StepFunctions2Constants.EXECUTION_INPUT))) {
                String executionInput = exchange.getIn().getHeader(StepFunctions2Constants.EXECUTION_INPUT, String.class);
                builder.input(executionInput);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(StepFunctions2Constants.EXECUTION_TRACE_HEADER))) {
                String executionTraceHeader
                        = exchange.getIn().getHeader(StepFunctions2Constants.EXECUTION_TRACE_HEADER, String.class);
                builder.traceHeader(executionTraceHeader);
            }

            StartSyncExecutionResponse result;
            try {
                result = sfnClient.startSyncExecution(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Start Sync Execution command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void stopExecution(SfnClient sfnClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof StopExecutionRequest request) {
                StopExecutionResponse result;
                try {
                    result = sfnClient.stopExecution(request);
                } catch (AwsServiceException ase) {
                    LOG.trace("Stop Execution command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            StopExecutionRequest.Builder builder = StopExecutionRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(StepFunctions2Constants.EXECUTION_ARN))) {
                String stateMachineArn = exchange.getIn().getHeader(StepFunctions2Constants.EXECUTION_ARN, String.class);
                builder.executionArn(stateMachineArn);
            }

            StopExecutionResponse result;
            try {
                result = sfnClient.stopExecution(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Stop Execution command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void describeExecution(SfnClient sfnClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof DescribeExecutionRequest request) {
                DescribeExecutionResponse result;
                try {
                    result = sfnClient.describeExecution(request);
                } catch (AwsServiceException ase) {
                    LOG.trace("Describe Execution command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            DescribeExecutionRequest.Builder builder = DescribeExecutionRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(StepFunctions2Constants.EXECUTION_ARN))) {
                String stateMachineArn = exchange.getIn().getHeader(StepFunctions2Constants.EXECUTION_ARN, String.class);
                builder.executionArn(stateMachineArn);
            }

            DescribeExecutionResponse result;
            try {
                result = sfnClient.describeExecution(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Describe Execution command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void listExecutions(SfnClient sfnClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof ListExecutionsRequest request) {
                ListExecutionsResponse result;
                try {
                    result = sfnClient.listExecutions(request);
                } catch (AwsServiceException ase) {
                    LOG.trace("List Executions command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            ListExecutionsRequest.Builder builder = ListExecutionsRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(StepFunctions2Constants.EXECUTIONS_MAX_RESULTS))) {
                int maxRes = exchange.getIn().getHeader(StepFunctions2Constants.EXECUTIONS_MAX_RESULTS, Integer.class);
                builder.maxResults(maxRes);
            }

            ListExecutionsResponse result;
            try {
                result = sfnClient.listExecutions(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("List Executions command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void getExecutionHistory(SfnClient sfnClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof GetExecutionHistoryRequest request) {
                GetExecutionHistoryResponse result;
                try {
                    result = sfnClient.getExecutionHistory(request);
                } catch (AwsServiceException ase) {
                    LOG.trace("Get Execution History command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            GetExecutionHistoryRequest.Builder builder = GetExecutionHistoryRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(StepFunctions2Constants.EXECUTION_ARN))) {
                String executionArn = exchange.getIn().getHeader(StepFunctions2Constants.EXECUTION_ARN, String.class);
                builder.executionArn(executionArn);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(StepFunctions2Constants.EXECUTION_HISTORY_MAX_RESULTS))) {
                int maxRes = exchange.getIn().getHeader(StepFunctions2Constants.EXECUTION_HISTORY_MAX_RESULTS, Integer.class);
                builder.maxResults(maxRes);
            }
            if (ObjectHelper
                    .isNotEmpty(exchange.getIn().getHeader(StepFunctions2Constants.EXECUTION_HISTORY_INCLUDE_EXECUTION_DATA))) {
                boolean includeExecutionData = exchange.getIn()
                        .getHeader(StepFunctions2Constants.EXECUTION_HISTORY_INCLUDE_EXECUTION_DATA, Boolean.class);
                builder.includeExecutionData(includeExecutionData);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(StepFunctions2Constants.EXECUTION_HISTORY_REVERSE_ORDER))) {
                Boolean executionReverseOrder
                        = exchange.getIn().getHeader(StepFunctions2Constants.EXECUTION_HISTORY_REVERSE_ORDER, Boolean.class);
                builder.reverseOrder(executionReverseOrder);
            }

            GetExecutionHistoryResponse result;
            try {
                result = sfnClient.getExecutionHistory(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Get Execution History command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    public static Message getMessageForResponse(final Exchange exchange) {
        return exchange.getMessage();
    }

    @Override
    protected void doStart() throws Exception {
        // health-check is optional so discover and resolve
        healthCheckRepository = HealthCheckHelper.getHealthCheckRepository(
                getEndpoint().getCamelContext(),
                "producers",
                WritableHealthCheckRepository.class);

        if (healthCheckRepository != null) {
            String id = getEndpoint().getId();
            producerHealthCheck = new StepFunctions2ProducerHealthCheck(getEndpoint(), id);
            producerHealthCheck.setEnabled(getEndpoint().getComponent().isHealthCheckProducerEnabled());
            healthCheckRepository.addHealthCheck(producerHealthCheck);
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (healthCheckRepository != null && producerHealthCheck != null) {
            healthCheckRepository.removeHealthCheck(producerHealthCheck);
            producerHealthCheck = null;
        }
    }

}
