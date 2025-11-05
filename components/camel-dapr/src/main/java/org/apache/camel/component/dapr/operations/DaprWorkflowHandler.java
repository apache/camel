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
package org.apache.camel.component.dapr.operations;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import io.dapr.workflows.Workflow;
import io.dapr.workflows.client.DaprWorkflowClient;
import io.dapr.workflows.client.NewWorkflowOptions;
import io.dapr.workflows.client.WorkflowInstanceStatus;
import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.dapr.DaprConfigurationOptionsProxy;
import org.apache.camel.component.dapr.DaprConstants;
import org.apache.camel.component.dapr.DaprEndpoint;
import org.apache.camel.component.dapr.WorkflowOperation;
import org.apache.camel.util.ObjectHelper;

public class DaprWorkflowHandler implements DaprOperationHandler {

    private final DaprConfigurationOptionsProxy configurationOptionsProxy;
    private final DaprEndpoint endpoint;

    public DaprWorkflowHandler(DaprConfigurationOptionsProxy configurationOptionsProxy, DaprEndpoint endpoint) {
        this.configurationOptionsProxy = configurationOptionsProxy;
        this.endpoint = endpoint;
    }

    @Override
    public DaprOperationResponse handle(Exchange exchange) {
        WorkflowOperation workflowOperation = configurationOptionsProxy.getWorkflowOperation(exchange);
        DaprWorkflowClient client = endpoint.getWorkflowClient();

        switch (workflowOperation) {
            case scheduleNew:
                return scheduleNewWorkflow(exchange, client);
            case terminate:
                return terminateWorkflow(exchange, client);
            case purge:
                return purgeWorkflow(exchange, client);
            case suspend:
                return suspendWorkflow(exchange, client);
            case resume:
                return resumeWorkflow(exchange, client);
            case state:
                return getWorkflowState(exchange, client);
            case waitForInstanceStart:
                return waitForInstanceStart(exchange, client);
            case waitForInstanceCompletion:
                return waitForInstanceCompletion(exchange, client);
            case raiseEvent:
                return raiseEvent(exchange, client);
            default:
                throw new IllegalArgumentException("Unsupported workflow operation");
        }
    }

    private DaprOperationResponse scheduleNewWorkflow(Exchange exchange, DaprWorkflowClient client) {
        String className = configurationOptionsProxy.getWorkflowClass(exchange);
        Class<? extends Workflow> workflowClass = null;
        try {
            workflowClass = resolveWorkflowClass(className);
        } catch (ClassNotFoundException e) {
            // ignore
        }

        String version = configurationOptionsProxy.getWorkflowVersion(exchange);
        String instanceId = configurationOptionsProxy.getWorkflowInstanceId(exchange);
        Object input = exchange.getIn().getBody();
        Instant startTime = configurationOptionsProxy.getWorkflowStartTime(exchange);

        NewWorkflowOptions options = new NewWorkflowOptions();
        options.setVersion(version);
        options.setInstanceId(instanceId);
        options.setInput(input);
        options.setStartTime(startTime);

        String response = client.scheduleNewWorkflow(workflowClass, options);

        return DaprOperationResponse.create(options, Map.of(DaprConstants.NEW_WORKFLOW_INSTANCE_ID, response));
    }

    private DaprOperationResponse terminateWorkflow(Exchange exchange, DaprWorkflowClient client) {
        String instanceId = configurationOptionsProxy.getWorkflowInstanceId(exchange);
        Object output = exchange.getIn().getBody();

        client.terminateWorkflow(instanceId, output);

        return DaprOperationResponse.create(output, Map.of(DaprConstants.WORKFLOW_INSTANCE_ID, instanceId));
    }

    private DaprOperationResponse purgeWorkflow(Exchange exchange, DaprWorkflowClient client) {
        String instanceId = configurationOptionsProxy.getWorkflowInstanceId(exchange);

        client.purgeInstance(instanceId);

        return DaprOperationResponse.create(null, Map.of(DaprConstants.WORKFLOW_INSTANCE_ID, instanceId));
    }

    private DaprOperationResponse suspendWorkflow(Exchange exchange, DaprWorkflowClient client) {
        String instanceId = configurationOptionsProxy.getWorkflowInstanceId(exchange);
        String reason = configurationOptionsProxy.getReason(exchange);

        client.suspendWorkflow(instanceId, reason);

        return DaprOperationResponse.create(null,
                Map.of(DaprConstants.WORKFLOW_INSTANCE_ID, instanceId, DaprConstants.REASON, reason));
    }

    private DaprOperationResponse resumeWorkflow(Exchange exchange, DaprWorkflowClient client) {
        String instanceId = configurationOptionsProxy.getWorkflowInstanceId(exchange);
        String reason = configurationOptionsProxy.getReason(exchange);

        client.resumeWorkflow(instanceId, reason);

        return DaprOperationResponse.create(null,
                Map.of(DaprConstants.WORKFLOW_INSTANCE_ID, instanceId, DaprConstants.REASON, reason));
    }

    private DaprOperationResponse getWorkflowState(Exchange exchange, DaprWorkflowClient client) {
        String instanceId = configurationOptionsProxy.getWorkflowInstanceId(exchange);
        boolean getWorkflowIO = configurationOptionsProxy.getWorkflowIO(exchange);

        WorkflowInstanceStatus response = client.getInstanceState(instanceId, getWorkflowIO);

        return DaprOperationResponse.createFromWorkflowStatus(response);
    }

    private DaprOperationResponse waitForInstanceStart(Exchange exchange, DaprWorkflowClient client) {
        String instanceId = configurationOptionsProxy.getWorkflowInstanceId(exchange);
        Duration timeout = configurationOptionsProxy.getTimeout(exchange);
        boolean getWorkflowIO = configurationOptionsProxy.getWorkflowIO(exchange);

        try {
            WorkflowInstanceStatus response = client.waitForInstanceStart(instanceId, timeout, getWorkflowIO);
            return DaprOperationResponse.createFromWorkflowStatus(response);
        } catch (TimeoutException exception) {
            throw new RuntimeCamelException(
                    "Workflow instance " + instanceId + " could not be started in " + timeout, exception);
        }
    }

    private DaprOperationResponse waitForInstanceCompletion(Exchange exchange, DaprWorkflowClient client) {
        String instanceId = configurationOptionsProxy.getWorkflowInstanceId(exchange);
        Duration timeout = configurationOptionsProxy.getTimeout(exchange);
        boolean getWorkflowIO = configurationOptionsProxy.getWorkflowIO(exchange);

        try {
            WorkflowInstanceStatus response = client.waitForInstanceCompletion(instanceId, timeout, getWorkflowIO);
            return DaprOperationResponse.createFromWorkflowStatus(response);
        } catch (TimeoutException exception) {
            throw new RuntimeCamelException(
                    "Workflow instance " + instanceId + " could not be completed in " + timeout, exception);
        }
    }

    private DaprOperationResponse raiseEvent(Exchange exchange, DaprWorkflowClient client) {
        String instanceId = configurationOptionsProxy.getWorkflowInstanceId(exchange);
        String eventName = configurationOptionsProxy.getEventName(exchange);
        Object payload = exchange.getIn().getBody();

        client.raiseEvent(instanceId, eventName, payload);

        return DaprOperationResponse.create(payload,
                Map.of(DaprConstants.WORKFLOW_INSTANCE_ID, instanceId, DaprConstants.EVENT_NAME, eventName));
    }

    @Override
    public void validateConfiguration(Exchange exchange) {
        WorkflowOperation workflowOperation = configurationOptionsProxy.getWorkflowOperation(exchange);
        String instanceId = configurationOptionsProxy.getWorkflowInstanceId(exchange);

        switch (workflowOperation) {
            case scheduleNew:
                String className = configurationOptionsProxy.getWorkflowClass(exchange);
                if (ObjectHelper.isEmpty(className)) {
                    throw new IllegalArgumentException("workflowClass not configured");
                }
                try {
                    resolveWorkflowClass(className);
                } catch (ClassNotFoundException e) {
                    throw new IllegalArgumentException(className + " not found");
                }
                break;
            case terminate:
            case purge:
            case state:
                if (ObjectHelper.isEmpty(instanceId)) {
                    throw new IllegalArgumentException(
                            "Instance Id must not be empty for 'state', `terminate` and `purge` operations");
                }
                break;
            case suspend:
            case resume:
                String reason = configurationOptionsProxy.getReason(exchange);
                if (ObjectHelper.isEmpty(instanceId) || ObjectHelper.isEmpty(reason)) {
                    throw new IllegalArgumentException(
                            "Instance Id and Reason must not be empty for 'suspend' and `resume` operations");
                }
                break;
            case waitForInstanceStart:
            case waitForInstanceCompletion:
                Duration timeout = configurationOptionsProxy.getTimeout(exchange);
                if (ObjectHelper.isEmpty(instanceId) || ObjectHelper.isEmpty(timeout)) {
                    throw new IllegalArgumentException(
                            "Instance Id and Timeout must not be empty for "
                                                       + "'waitForInstanceStart' and `waitForInstanceCompletion` operation");
                }
                break;
            case raiseEvent:
                String eventName = configurationOptionsProxy.getEventName(exchange);
                if (ObjectHelper.isEmpty(instanceId) || ObjectHelper.isEmpty(eventName)) {
                    throw new IllegalArgumentException(
                            "Instance Id and Event Name must not be empty for 'raiseEvent' operation");
                }
                break;
            default:
                throw new IllegalArgumentException("Unsupported workflow operation");
        }
    }

    @SuppressWarnings("unchecked")
    private Class<? extends Workflow> resolveWorkflowClass(String className) throws ClassNotFoundException {
        Class<?> rawClass = Class.forName(className);
        if (!Workflow.class.isAssignableFrom(rawClass)) {
            throw new IllegalArgumentException(className + " does not extend Workflow");
        }
        return (Class<? extends Workflow>) rawClass;
    }
}
