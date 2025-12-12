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
import java.util.concurrent.TimeoutException;

import io.dapr.workflows.Workflow;
import io.dapr.workflows.WorkflowStub;
import io.dapr.workflows.client.DaprWorkflowClient;
import io.dapr.workflows.client.NewWorkflowOptions;
import io.dapr.workflows.client.WorkflowFailureDetails;
import io.dapr.workflows.client.WorkflowInstanceStatus;
import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.dapr.DaprConfiguration;
import org.apache.camel.component.dapr.DaprConfigurationOptionsProxy;
import org.apache.camel.component.dapr.DaprConstants;
import org.apache.camel.component.dapr.DaprEndpoint;
import org.apache.camel.component.dapr.DaprOperation;
import org.apache.camel.component.dapr.WorkflowOperation;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class DaprWorkflowTest extends CamelTestSupport {

    @Mock
    private DaprWorkflowClient client;
    @Mock
    private DaprEndpoint endpoint;

    @Test
    void testScheduleNew() {
        String mockResult = "newInstanceId";

        when(endpoint.getWorkflowClient()).thenReturn(client);
        when(client.scheduleNewWorkflow(any(), any(NewWorkflowOptions.class))).thenReturn(mockResult);

        DaprConfiguration configuration = new DaprConfiguration();
        configuration.setOperation(DaprOperation.workflow);
        configuration.setWorkflowOperation(WorkflowOperation.scheduleNew);
        configuration.setWorkflowClass("org.apache.camel.component.dapr.operations.DaprWorkflowTest$DemoWorkflow");
        configuration.setWorkflowVersion("myVersion");
        configuration.setWorkflowInstanceId("myInstance");
        configuration.setWorkflowStartTime(Instant.now());
        DaprConfigurationOptionsProxy configurationOptionsProxy = new DaprConfigurationOptionsProxy(configuration);

        final Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody("myBody");

        final DaprWorkflowHandler operation = new DaprWorkflowHandler(configurationOptionsProxy, endpoint);
        final DaprOperationResponse operationResponse = operation.handle(exchange);

        assertNotNull(operationResponse);
        assertEquals(mockResult, operationResponse.getHeaders().get(DaprConstants.NEW_WORKFLOW_INSTANCE_ID));
    }

    @Test
    void testScheduleNewConfig() {
        DaprConfiguration configuration = new DaprConfiguration();
        configuration.setOperation(DaprOperation.workflow);
        configuration.setWorkflowOperation(WorkflowOperation.scheduleNew);
        DaprConfigurationOptionsProxy configurationOptionsProxy = new DaprConfigurationOptionsProxy(configuration);

        final Exchange exchange = new DefaultExchange(context);

        // case 1: workflowClass not configured
        final DaprWorkflowHandler operation = new DaprWorkflowHandler(configurationOptionsProxy, endpoint);
        assertThrows(IllegalArgumentException.class, () -> operation.validateConfiguration(exchange));

        // case 2: workflowClass not found
        configuration.setWorkflowClass("org.apache.camel.component.dapr.operations.DaprWorkflowTest$NoClass");
        assertThrows(IllegalArgumentException.class, () -> operation.validateConfiguration(exchange));

        // case 3: workflowClass does not extend io.dapr.workflows.Workflow
        configuration.setWorkflowClass("org.apache.camel.component.dapr.operations.DaprWorkflowTest$NotWorkflow");
        assertThrows(IllegalArgumentException.class, () -> operation.validateConfiguration(exchange));

        // case 4: valid configuration
        configuration.setWorkflowClass("org.apache.camel.component.dapr.operations.DaprWorkflowTest$DemoWorkflow");
        assertDoesNotThrow(() -> operation.validateConfiguration(exchange));
    }

    @Test
    void testTerminate() {
        String instanceId = "myInstance";
        String body = "output";

        when(endpoint.getWorkflowClient()).thenReturn(client);

        DaprConfiguration configuration = new DaprConfiguration();
        configuration.setOperation(DaprOperation.workflow);
        configuration.setWorkflowOperation(WorkflowOperation.terminate);
        configuration.setWorkflowInstanceId(instanceId);
        DaprConfigurationOptionsProxy configurationOptionsProxy = new DaprConfigurationOptionsProxy(configuration);

        final Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody(body);

        final DaprWorkflowHandler operation = new DaprWorkflowHandler(configurationOptionsProxy, endpoint);
        final DaprOperationResponse response = operation.handle(exchange);

        assertNotNull(response);
        verify(client).terminateWorkflow(instanceId, body);
        assertEquals(body, response.getBody());
        assertEquals(instanceId, response.getHeaders().get(DaprConstants.WORKFLOW_INSTANCE_ID));
    }

    @Test
    void testTerminateConfig() {
        DaprConfiguration configuration = new DaprConfiguration();
        configuration.setOperation(DaprOperation.workflow);
        configuration.setWorkflowOperation(WorkflowOperation.terminate);
        DaprConfigurationOptionsProxy configurationOptionsProxy = new DaprConfigurationOptionsProxy(configuration);

        final Exchange exchange = new DefaultExchange(context);

        // case 1: instanceId empty
        final DaprWorkflowHandler operation = new DaprWorkflowHandler(configurationOptionsProxy, endpoint);
        assertThrows(IllegalArgumentException.class, () -> operation.validateConfiguration(exchange));

        // case 2: valid configuration
        configuration.setWorkflowInstanceId("myInstance");
        assertDoesNotThrow(() -> operation.validateConfiguration(exchange));
    }

    @Test
    void testPurge() {
        String instanceId = "myInstance";

        when(endpoint.getWorkflowClient()).thenReturn(client);

        DaprConfiguration configuration = new DaprConfiguration();
        configuration.setOperation(DaprOperation.workflow);
        configuration.setWorkflowOperation(WorkflowOperation.purge);
        configuration.setWorkflowInstanceId(instanceId);
        DaprConfigurationOptionsProxy configurationOptionsProxy = new DaprConfigurationOptionsProxy(configuration);

        final Exchange exchange = new DefaultExchange(context);

        final DaprWorkflowHandler operation = new DaprWorkflowHandler(configurationOptionsProxy, endpoint);
        final DaprOperationResponse response = operation.handle(exchange);

        assertNotNull(response);
        verify(client).purgeInstance(instanceId);
        assertEquals(instanceId, response.getHeaders().get(DaprConstants.WORKFLOW_INSTANCE_ID));
    }

    @Test
    void testPurgeConfig() {
        DaprConfiguration configuration = new DaprConfiguration();
        configuration.setOperation(DaprOperation.workflow);
        configuration.setWorkflowOperation(WorkflowOperation.purge);
        DaprConfigurationOptionsProxy configurationOptionsProxy = new DaprConfigurationOptionsProxy(configuration);

        final Exchange exchange = new DefaultExchange(context);

        // case 1: instanceId empty
        final DaprWorkflowHandler operation = new DaprWorkflowHandler(configurationOptionsProxy, endpoint);
        assertThrows(IllegalArgumentException.class, () -> operation.validateConfiguration(exchange));

        // case 2: valid configuration
        configuration.setWorkflowInstanceId("myInstance");
        assertDoesNotThrow(() -> operation.validateConfiguration(exchange));
    }

    @Test
    void testSuspend() {
        String instanceId = "myInstance";
        String reason = "myReason";

        when(endpoint.getWorkflowClient()).thenReturn(client);

        DaprConfiguration configuration = new DaprConfiguration();
        configuration.setOperation(DaprOperation.workflow);
        configuration.setWorkflowOperation(WorkflowOperation.suspend);
        configuration.setWorkflowInstanceId(instanceId);
        configuration.setReason(reason);
        DaprConfigurationOptionsProxy configurationOptionsProxy = new DaprConfigurationOptionsProxy(configuration);

        final Exchange exchange = new DefaultExchange(context);

        final DaprWorkflowHandler operation = new DaprWorkflowHandler(configurationOptionsProxy, endpoint);
        final DaprOperationResponse response = operation.handle(exchange);

        assertNotNull(response);
        verify(client).suspendWorkflow(instanceId, reason);
        assertEquals(instanceId, response.getHeaders().get(DaprConstants.WORKFLOW_INSTANCE_ID));
        assertEquals(reason, response.getHeaders().get(DaprConstants.REASON));
    }

    @Test
    void testSuspendConfig() {
        DaprConfiguration configuration = new DaprConfiguration();
        configuration.setOperation(DaprOperation.workflow);
        configuration.setWorkflowOperation(WorkflowOperation.suspend);
        DaprConfigurationOptionsProxy configurationOptionsProxy = new DaprConfigurationOptionsProxy(configuration);

        final Exchange exchange = new DefaultExchange(context);

        // case 1: instanceId, reason empty
        final DaprWorkflowHandler operation = new DaprWorkflowHandler(configurationOptionsProxy, endpoint);
        assertThrows(IllegalArgumentException.class, () -> operation.validateConfiguration(exchange));

        // case 2: reason empty
        configuration.setWorkflowInstanceId("myInstance");
        assertThrows(IllegalArgumentException.class, () -> operation.validateConfiguration(exchange));

        // case 3: valid configuration
        configuration.setReason("myReason");
        assertDoesNotThrow(() -> operation.validateConfiguration(exchange));
    }

    @Test
    void testResume() {
        String instanceId = "myInstance";
        String reason = "myReason";

        when(endpoint.getWorkflowClient()).thenReturn(client);

        DaprConfiguration configuration = new DaprConfiguration();
        configuration.setOperation(DaprOperation.workflow);
        configuration.setWorkflowOperation(WorkflowOperation.resume);
        configuration.setWorkflowInstanceId(instanceId);
        configuration.setReason(reason);
        DaprConfigurationOptionsProxy configurationOptionsProxy = new DaprConfigurationOptionsProxy(configuration);

        final Exchange exchange = new DefaultExchange(context);

        final DaprWorkflowHandler operation = new DaprWorkflowHandler(configurationOptionsProxy, endpoint);
        final DaprOperationResponse response = operation.handle(exchange);

        assertNotNull(response);
        verify(client).resumeWorkflow(instanceId, reason);
        assertEquals(instanceId, response.getHeaders().get(DaprConstants.WORKFLOW_INSTANCE_ID));
        assertEquals(reason, response.getHeaders().get(DaprConstants.REASON));
    }

    @Test
    void testResumeConfig() {
        DaprConfiguration configuration = new DaprConfiguration();
        configuration.setOperation(DaprOperation.workflow);
        configuration.setWorkflowOperation(WorkflowOperation.resume);
        DaprConfigurationOptionsProxy configurationOptionsProxy = new DaprConfigurationOptionsProxy(configuration);

        final Exchange exchange = new DefaultExchange(context);

        // case 1: instanceId, reason empty
        final DaprWorkflowHandler operation = new DaprWorkflowHandler(configurationOptionsProxy, endpoint);
        assertThrows(IllegalArgumentException.class, () -> operation.validateConfiguration(exchange));

        // case 2: reason empty
        configuration.setWorkflowInstanceId("myInstance");
        assertThrows(IllegalArgumentException.class, () -> operation.validateConfiguration(exchange));

        // case 3: valid configuration
        configuration.setReason("myReason");
        assertDoesNotThrow(() -> operation.validateConfiguration(exchange));
    }

    @Test
    void testState() {
        final String name = "myName";
        final Instant createdAt = Instant.now().minusMillis(1000);
        final Instant updatedAt = Instant.now();
        final String serializedInput = "myInput";
        final String serializedOutput = "myOutput";
        final boolean isRunning = true;
        final boolean isCompleted = false;
        WorkflowFailureDetails failureDetails = mock(WorkflowFailureDetails.class);

        WorkflowInstanceStatus workflowStatus = mock(WorkflowInstanceStatus.class);
        when(workflowStatus.getName()).thenReturn(name);
        when(workflowStatus.getCreatedAt()).thenReturn(createdAt);
        when(workflowStatus.getLastUpdatedAt()).thenReturn(updatedAt);
        when(workflowStatus.getSerializedInput()).thenReturn(serializedInput);
        when(workflowStatus.getSerializedOutput()).thenReturn(serializedOutput);
        when(workflowStatus.getFailureDetails()).thenReturn(failureDetails);
        when(workflowStatus.isRunning()).thenReturn(isRunning);
        when(workflowStatus.isCompleted()).thenReturn(isCompleted);

        when(endpoint.getWorkflowClient()).thenReturn(client);
        when(client.getInstanceState(anyString(), any(Boolean.class))).thenReturn(workflowStatus);

        DaprConfiguration configuration = new DaprConfiguration();
        configuration.setOperation(DaprOperation.workflow);
        configuration.setWorkflowOperation(WorkflowOperation.state);
        configuration.setWorkflowInstanceId("myInstance");
        DaprConfigurationOptionsProxy configurationOptionsProxy = new DaprConfigurationOptionsProxy(configuration);

        final Exchange exchange = new DefaultExchange(context);

        DaprWorkflowHandler operation = new DaprWorkflowHandler(configurationOptionsProxy, endpoint);
        DaprOperationResponse response = operation.handle(exchange);

        assertNotNull(response);
        assertEquals(workflowStatus, response.getBody());
        assertEquals(name, response.getHeaders().get(DaprConstants.WORKFLOW_NAME));
        assertEquals(createdAt, response.getHeaders().get(DaprConstants.WORKFLOW_CREATED_AT));
        assertEquals(updatedAt, response.getHeaders().get(DaprConstants.WORKFLOW_UPDATED_AT));
        assertEquals(serializedInput, response.getHeaders().get(DaprConstants.WORKFLOW_SERIALIZED_INPUT));
        assertEquals(serializedOutput, response.getHeaders().get(DaprConstants.WORKFLOW_SERIALIZED_OUTPUT));
        assertEquals(failureDetails, response.getHeaders().get(DaprConstants.WORKFLOW_FAILURE_DETAILS));
        assertEquals(isRunning, response.getHeaders().get(DaprConstants.IS_WORKFLOW_RUNNING));
        assertEquals(isCompleted, response.getHeaders().get(DaprConstants.IS_WORKFLOW_COMPLETED));
    }

    @Test
    void testStateConfig() {
        DaprConfiguration configuration = new DaprConfiguration();
        configuration.setOperation(DaprOperation.workflow);
        configuration.setWorkflowOperation(WorkflowOperation.state);
        DaprConfigurationOptionsProxy configurationOptionsProxy = new DaprConfigurationOptionsProxy(configuration);

        final Exchange exchange = new DefaultExchange(context);

        // case 1: instanceId empty
        final DaprWorkflowHandler operation = new DaprWorkflowHandler(configurationOptionsProxy, endpoint);
        assertThrows(IllegalArgumentException.class, () -> operation.validateConfiguration(exchange));

        // case 2: valid configuration
        configuration.setWorkflowInstanceId("myInstance");
        assertDoesNotThrow(() -> operation.validateConfiguration(exchange));
    }

    @Test
    void testWaitForInstanceStart() throws TimeoutException {
        final String name = "myName";
        final Instant createdAt = Instant.now().minusMillis(1000);
        final Instant updatedAt = Instant.now();
        final String serializedInput = "myInput";
        final String serializedOutput = "myOutput";
        final boolean isRunning = true;
        final boolean isCompleted = false;
        WorkflowFailureDetails failureDetails = mock(WorkflowFailureDetails.class);

        WorkflowInstanceStatus workflowStatus = mock(WorkflowInstanceStatus.class);
        when(workflowStatus.getName()).thenReturn(name);
        when(workflowStatus.getCreatedAt()).thenReturn(createdAt);
        when(workflowStatus.getLastUpdatedAt()).thenReturn(updatedAt);
        when(workflowStatus.getSerializedInput()).thenReturn(serializedInput);
        when(workflowStatus.getSerializedOutput()).thenReturn(serializedOutput);
        when(workflowStatus.getFailureDetails()).thenReturn(failureDetails);
        when(workflowStatus.isRunning()).thenReturn(isRunning);
        when(workflowStatus.isCompleted()).thenReturn(isCompleted);

        when(endpoint.getWorkflowClient()).thenReturn(client);
        when(client.waitForInstanceStart(anyString(), any(Duration.class), any(Boolean.class)))
                .thenReturn(workflowStatus);

        DaprConfiguration configuration = new DaprConfiguration();
        configuration.setOperation(DaprOperation.workflow);
        configuration.setWorkflowOperation(WorkflowOperation.waitForInstanceStart);
        configuration.setWorkflowInstanceId("myInstance");
        configuration.setTimeout(Duration.ofSeconds(10));
        DaprConfigurationOptionsProxy configurationOptionsProxy = new DaprConfigurationOptionsProxy(configuration);

        final Exchange exchange = new DefaultExchange(context);

        final DaprWorkflowHandler operation = new DaprWorkflowHandler(configurationOptionsProxy, endpoint);
        final DaprOperationResponse response = operation.handle(exchange);

        assertNotNull(response);
        assertEquals(workflowStatus, response.getBody());
        assertEquals(name, response.getHeaders().get(DaprConstants.WORKFLOW_NAME));
        assertEquals(createdAt, response.getHeaders().get(DaprConstants.WORKFLOW_CREATED_AT));
        assertEquals(updatedAt, response.getHeaders().get(DaprConstants.WORKFLOW_UPDATED_AT));
        assertEquals(serializedInput, response.getHeaders().get(DaprConstants.WORKFLOW_SERIALIZED_INPUT));
        assertEquals(serializedOutput, response.getHeaders().get(DaprConstants.WORKFLOW_SERIALIZED_OUTPUT));
        assertEquals(failureDetails, response.getHeaders().get(DaprConstants.WORKFLOW_FAILURE_DETAILS));
        assertEquals(isRunning, response.getHeaders().get(DaprConstants.IS_WORKFLOW_RUNNING));
        assertEquals(isCompleted, response.getHeaders().get(DaprConstants.IS_WORKFLOW_COMPLETED));
    }

    @Test
    void testWaitForInstanceStartTimeout() throws TimeoutException {
        String instanceId = "myInstance";
        Duration timeout = Duration.ofSeconds(10);

        when(endpoint.getWorkflowClient()).thenReturn(client);
        when(client.waitForInstanceStart(anyString(), any(Duration.class), any(Boolean.class)))
                .thenThrow(TimeoutException.class);

        DaprConfiguration configuration = new DaprConfiguration();
        configuration.setOperation(DaprOperation.workflow);
        configuration.setWorkflowOperation(WorkflowOperation.waitForInstanceStart);
        configuration.setWorkflowInstanceId(instanceId);
        configuration.setTimeout(timeout);
        DaprConfigurationOptionsProxy configurationOptionsProxy = new DaprConfigurationOptionsProxy(configuration);

        final Exchange exchange = new DefaultExchange(context);
        final DaprWorkflowHandler operation = new DaprWorkflowHandler(configurationOptionsProxy, endpoint);

        RuntimeCamelException e = assertThrows(RuntimeCamelException.class, () -> operation.handle(exchange));

        assertEquals(TimeoutException.class, e.getCause().getClass());
        assertTrue(e.getMessage().contains(instanceId));
        assertTrue(e.getMessage().contains(timeout.toString()));
    }

    @Test
    void testWaitForInstanceStartConfig() {
        DaprConfiguration configuration = new DaprConfiguration();
        configuration.setOperation(DaprOperation.workflow);
        configuration.setWorkflowOperation(WorkflowOperation.waitForInstanceStart);
        DaprConfigurationOptionsProxy configurationOptionsProxy = new DaprConfigurationOptionsProxy(configuration);

        final Exchange exchange = new DefaultExchange(context);

        // case 1: instanceId and timeout empty
        final DaprWorkflowHandler operation = new DaprWorkflowHandler(configurationOptionsProxy, endpoint);
        assertThrows(IllegalArgumentException.class, () -> operation.validateConfiguration(exchange));

        // case 2: timeout empty
        configuration.setWorkflowInstanceId("myInstance");
        assertThrows(IllegalArgumentException.class, () -> operation.validateConfiguration(exchange));

        // case 3: valid configuration
        configuration.setTimeout(Duration.ofSeconds(10));
        assertDoesNotThrow(() -> operation.validateConfiguration(exchange));
    }

    @Test
    void testWaitForInstanceCompletion() throws TimeoutException {
        final String name = "myName";
        final Instant createdAt = Instant.now().minusMillis(1000);
        final Instant updatedAt = Instant.now();
        final String serializedInput = "myInput";
        final String serializedOutput = "myOutput";
        final boolean isRunning = true;
        final boolean isCompleted = false;
        WorkflowFailureDetails failureDetails = mock(WorkflowFailureDetails.class);

        WorkflowInstanceStatus workflowStatus = mock(WorkflowInstanceStatus.class);
        when(workflowStatus.getName()).thenReturn(name);
        when(workflowStatus.getCreatedAt()).thenReturn(createdAt);
        when(workflowStatus.getLastUpdatedAt()).thenReturn(updatedAt);
        when(workflowStatus.getSerializedInput()).thenReturn(serializedInput);
        when(workflowStatus.getSerializedOutput()).thenReturn(serializedOutput);
        when(workflowStatus.getFailureDetails()).thenReturn(failureDetails);
        when(workflowStatus.isRunning()).thenReturn(isRunning);
        when(workflowStatus.isCompleted()).thenReturn(isCompleted);

        when(endpoint.getWorkflowClient()).thenReturn(client);
        when(client.waitForInstanceCompletion(anyString(), any(Duration.class), any(Boolean.class)))
                .thenReturn(workflowStatus);

        DaprConfiguration configuration = new DaprConfiguration();
        configuration.setOperation(DaprOperation.workflow);
        configuration.setWorkflowOperation(WorkflowOperation.waitForInstanceCompletion);
        configuration.setWorkflowInstanceId("myInstance");
        configuration.setTimeout(Duration.ofSeconds(10));
        DaprConfigurationOptionsProxy configurationOptionsProxy = new DaprConfigurationOptionsProxy(configuration);

        final Exchange exchange = new DefaultExchange(context);

        final DaprWorkflowHandler operation = new DaprWorkflowHandler(configurationOptionsProxy, endpoint);
        final DaprOperationResponse response = operation.handle(exchange);

        assertNotNull(response);
        assertEquals(workflowStatus, response.getBody());
        assertEquals(name, response.getHeaders().get(DaprConstants.WORKFLOW_NAME));
        assertEquals(createdAt, response.getHeaders().get(DaprConstants.WORKFLOW_CREATED_AT));
        assertEquals(updatedAt, response.getHeaders().get(DaprConstants.WORKFLOW_UPDATED_AT));
        assertEquals(serializedInput, response.getHeaders().get(DaprConstants.WORKFLOW_SERIALIZED_INPUT));
        assertEquals(serializedOutput, response.getHeaders().get(DaprConstants.WORKFLOW_SERIALIZED_OUTPUT));
        assertEquals(failureDetails, response.getHeaders().get(DaprConstants.WORKFLOW_FAILURE_DETAILS));
        assertEquals(isRunning, response.getHeaders().get(DaprConstants.IS_WORKFLOW_RUNNING));
        assertEquals(isCompleted, response.getHeaders().get(DaprConstants.IS_WORKFLOW_COMPLETED));
    }

    @Test
    void testWaitForInstanceCompletionTimeout() throws TimeoutException {
        String instanceId = "myInstance";
        Duration timeout = Duration.ofSeconds(10);

        when(endpoint.getWorkflowClient()).thenReturn(client);
        when(client.waitForInstanceCompletion(anyString(), any(Duration.class), any(Boolean.class)))
                .thenThrow(TimeoutException.class);

        DaprConfiguration configuration = new DaprConfiguration();
        configuration.setOperation(DaprOperation.workflow);
        configuration.setWorkflowOperation(WorkflowOperation.waitForInstanceCompletion);
        configuration.setWorkflowInstanceId(instanceId);
        configuration.setTimeout(timeout);
        DaprConfigurationOptionsProxy configurationOptionsProxy = new DaprConfigurationOptionsProxy(configuration);

        final Exchange exchange = new DefaultExchange(context);
        final DaprWorkflowHandler operation = new DaprWorkflowHandler(configurationOptionsProxy, endpoint);

        RuntimeCamelException e = assertThrows(RuntimeCamelException.class, () -> operation.handle(exchange));

        assertEquals(TimeoutException.class, e.getCause().getClass());
        assertTrue(e.getMessage().contains(instanceId));
        assertTrue(e.getMessage().contains(timeout.toString()));
    }

    @Test
    void testWaitForInstanceCompletionConfig() {
        DaprConfiguration configuration = new DaprConfiguration();
        configuration.setOperation(DaprOperation.workflow);
        configuration.setWorkflowOperation(WorkflowOperation.waitForInstanceCompletion);
        DaprConfigurationOptionsProxy configurationOptionsProxy = new DaprConfigurationOptionsProxy(configuration);

        final Exchange exchange = new DefaultExchange(context);

        // case 1: instanceId and timeout empty
        final DaprWorkflowHandler operation = new DaprWorkflowHandler(configurationOptionsProxy, endpoint);
        assertThrows(IllegalArgumentException.class, () -> operation.validateConfiguration(exchange));

        // case 2: timeout empty
        configuration.setWorkflowInstanceId("myInstance");
        assertThrows(IllegalArgumentException.class, () -> operation.validateConfiguration(exchange));

        // case 3: valid configuration
        configuration.setTimeout(Duration.ofSeconds(10));
        assertDoesNotThrow(() -> operation.validateConfiguration(exchange));
    }

    @Test
    void testRaiseEvent() {
        String instanceId = "myInstance";
        String eventName = "myEvent";
        String body = "payload";

        when(endpoint.getWorkflowClient()).thenReturn(client);

        DaprConfiguration configuration = new DaprConfiguration();
        configuration.setOperation(DaprOperation.workflow);
        configuration.setWorkflowOperation(WorkflowOperation.raiseEvent);
        configuration.setWorkflowInstanceId(instanceId);
        configuration.setEventName(eventName);
        DaprConfigurationOptionsProxy configurationOptionsProxy = new DaprConfigurationOptionsProxy(configuration);

        final Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody(body);

        final DaprWorkflowHandler operation = new DaprWorkflowHandler(configurationOptionsProxy, endpoint);
        final DaprOperationResponse response = operation.handle(exchange);

        assertNotNull(response);
        verify(client).raiseEvent(instanceId, eventName, body);
        assertEquals(body, response.getBody());
        assertEquals(instanceId, response.getHeaders().get(DaprConstants.WORKFLOW_INSTANCE_ID));
        assertEquals(eventName, response.getHeaders().get(DaprConstants.EVENT_NAME));
    }

    @Test
    void testRaiseEventConfig() {
        DaprConfiguration configuration = new DaprConfiguration();
        configuration.setOperation(DaprOperation.workflow);
        configuration.setWorkflowOperation(WorkflowOperation.raiseEvent);
        DaprConfigurationOptionsProxy configurationOptionsProxy = new DaprConfigurationOptionsProxy(configuration);

        final Exchange exchange = new DefaultExchange(context);

        // case 1: instanceId and eventName empty
        final DaprWorkflowHandler operation = new DaprWorkflowHandler(configurationOptionsProxy, endpoint);
        assertThrows(IllegalArgumentException.class, () -> operation.validateConfiguration(exchange));

        // case 2: eventName empty
        configuration.setWorkflowInstanceId("myInstance");
        assertThrows(IllegalArgumentException.class, () -> operation.validateConfiguration(exchange));

        // case 3: valid configuration
        configuration.setEventName("myEvent");
        assertDoesNotThrow(() -> operation.validateConfiguration(exchange));
    }

    public static class NotWorkflow {
        public void foo() {
            System.out.println("hello");
        }
    }

    public static class DemoWorkflow implements Workflow {
        @Override
        public WorkflowStub create() {
            return ctx -> {
            };
        }
    }
}
