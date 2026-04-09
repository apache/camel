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
package org.apache.camel.component.camunda;

import java.util.List;
import java.util.Map;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.command.CancelProcessInstanceCommandStep1;
import io.camunda.client.api.command.CompleteJobCommandStep1;
import io.camunda.client.api.command.CreateProcessInstanceCommandStep1;
import io.camunda.client.api.command.DeployResourceCommandStep1;
import io.camunda.client.api.command.FailJobCommandStep1;
import io.camunda.client.api.command.PublishMessageCommandStep1;
import io.camunda.client.api.command.ThrowErrorCommandStep1;
import io.camunda.client.api.command.UpdateRetriesJobCommandStep1;
import io.camunda.client.api.response.CancelProcessInstanceResponse;
import io.camunda.client.api.response.CompleteJobResponse;
import io.camunda.client.api.response.DeploymentEvent;
import io.camunda.client.api.response.FailJobResponse;
import io.camunda.client.api.response.Process;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.response.PublishMessageResponse;
import io.camunda.client.api.response.ThrowErrorResponse;
import io.camunda.client.api.response.UpdateRetriesJobResponse;
import org.apache.camel.component.camunda.internal.CamundaService;
import org.apache.camel.component.camunda.model.DeploymentRequest;
import org.apache.camel.component.camunda.model.DeploymentResponse;
import org.apache.camel.component.camunda.model.JobRequest;
import org.apache.camel.component.camunda.model.JobResponse;
import org.apache.camel.component.camunda.model.MessageRequest;
import org.apache.camel.component.camunda.model.MessageResponse;
import org.apache.camel.component.camunda.model.ProcessDeploymentResponse;
import org.apache.camel.component.camunda.model.ProcessRequest;
import org.apache.camel.component.camunda.model.ProcessResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CamundaServiceTest {

    @Mock
    CamundaClient client;

    CamundaService service;

    @BeforeEach
    void setUp() {
        service = new CamundaService(client);
    }

    @Test
    void startProcessSuccess() {
        CreateProcessInstanceCommandStep1 step1 = mock(CreateProcessInstanceCommandStep1.class);
        CreateProcessInstanceCommandStep1.CreateProcessInstanceCommandStep2 step2
                = mock(CreateProcessInstanceCommandStep1.CreateProcessInstanceCommandStep2.class);
        CreateProcessInstanceCommandStep1.CreateProcessInstanceCommandStep3 step3
                = mock(CreateProcessInstanceCommandStep1.CreateProcessInstanceCommandStep3.class);

        when(client.newCreateInstanceCommand()).thenReturn(step1);
        when(step1.bpmnProcessId(anyString())).thenReturn(step2);
        when(step2.latestVersion()).thenReturn(step3);
        when(step3.variables(anyMap())).thenReturn(step3);

        ProcessInstanceEvent event = mock(ProcessInstanceEvent.class);
        when(event.getBpmnProcessId()).thenReturn("testProcess");
        when(event.getProcessDefinitionKey()).thenReturn(100L);
        when(event.getVersion()).thenReturn(1);
        when(event.getProcessInstanceKey()).thenReturn(200L);

        CamundaFuture<ProcessInstanceEvent> future = mockFuture(event);
        when(step3.send()).thenReturn(future);

        ProcessRequest request = new ProcessRequest();
        request.setProcessId("testProcess");
        request.setVariables(Map.of("key", "value"));

        ProcessResponse response = service.startProcess(request);

        assertTrue(response.isSuccess());
        assertEquals("testProcess", response.getProcessId());
        assertEquals(100L, response.getProcessKey());
        assertEquals(1, response.getProcessVersion());
        assertEquals(200L, response.getProcessInstanceKey());
    }

    @Test
    void startProcessError() {
        when(client.newCreateInstanceCommand()).thenThrow(new RuntimeException("connection refused"));

        ProcessRequest request = new ProcessRequest();
        request.setProcessId("testProcess");

        ProcessResponse response = service.startProcess(request);

        assertFalse(response.isSuccess());
        assertEquals("connection refused", response.getErrorMessage());
    }

    @Test
    void cancelProcessSuccess() {
        CancelProcessInstanceCommandStep1 step1 = mock(CancelProcessInstanceCommandStep1.class);
        when(client.newCancelInstanceCommand(anyLong())).thenReturn(step1);

        CamundaFuture<CancelProcessInstanceResponse> future = mockFuture(mock(CancelProcessInstanceResponse.class));
        when(step1.send()).thenReturn(future);

        ProcessRequest request = new ProcessRequest();
        request.setProcessInstanceKey(123L);

        ProcessResponse response = service.cancelProcessInstance(request);

        assertTrue(response.isSuccess());
        verify(client).newCancelInstanceCommand(123L);
    }

    @Test
    void publishMessageSuccess() {
        PublishMessageCommandStep1 step1 = mock(PublishMessageCommandStep1.class);
        PublishMessageCommandStep1.PublishMessageCommandStep2 step2
                = mock(PublishMessageCommandStep1.PublishMessageCommandStep2.class);
        PublishMessageCommandStep1.PublishMessageCommandStep3 step3
                = mock(PublishMessageCommandStep1.PublishMessageCommandStep3.class);

        when(client.newPublishMessageCommand()).thenReturn(step1);
        when(step1.messageName(anyString())).thenReturn(step2);
        when(step2.correlationKey(anyString())).thenReturn(step3);
        when(step3.variables(anyMap())).thenReturn(step3);

        PublishMessageResponse messageResponse = mock(PublishMessageResponse.class);
        when(messageResponse.getMessageKey()).thenReturn(999L);

        CamundaFuture<PublishMessageResponse> future = mockFuture(messageResponse);
        when(step3.send()).thenReturn(future);

        MessageRequest request = new MessageRequest();
        request.setName("testMessage");
        request.setCorrelationKey("key1");
        request.setVariables(Map.of("v1", "a"));

        MessageResponse response = service.publishMessage(request);

        assertTrue(response.isSuccess());
        assertEquals("key1", response.getCorrelationKey());
        assertEquals(999L, response.getMessageKey());
    }

    @Test
    void publishMessageMissingCorrelationKey() {
        MessageRequest request = new MessageRequest();
        request.setName("testMessage");

        MessageResponse response = service.publishMessage(request);

        assertFalse(response.isSuccess());
        assertEquals("Correlation Key is missing!", response.getErrorMessage());
    }

    @Test
    void completeJobSuccess() {
        CompleteJobCommandStep1 step1 = mock(CompleteJobCommandStep1.class);
        when(client.newCompleteCommand(anyLong())).thenReturn(step1);

        CamundaFuture<CompleteJobResponse> future = mockFuture(mock(CompleteJobResponse.class));
        when(step1.send()).thenReturn(future);

        JobRequest request = new JobRequest();
        request.setJobKey(456L);

        JobResponse response = service.completeJob(request);

        assertTrue(response.isSuccess());
        verify(client).newCompleteCommand(456L);
    }

    @Test
    void failJobSuccess() {
        FailJobCommandStep1 step1 = mock(FailJobCommandStep1.class);
        FailJobCommandStep1.FailJobCommandStep2 step2 = mock(FailJobCommandStep1.FailJobCommandStep2.class);
        when(client.newFailCommand(anyLong())).thenReturn(step1);
        when(step1.retries(anyInt())).thenReturn(step2);
        when(step2.errorMessage(anyString())).thenReturn(step2);

        CamundaFuture<FailJobResponse> future = mockFuture(mock(FailJobResponse.class));
        when(step2.send()).thenReturn(future);

        JobRequest request = new JobRequest();
        request.setJobKey(789L);
        request.setRetries(3);
        request.setFailMessage("some error");

        JobResponse response = service.failJob(request);

        assertTrue(response.isSuccess());
        verify(client).newFailCommand(789L);
    }

    @Test
    void throwErrorSuccess() {
        ThrowErrorCommandStep1 step1 = mock(ThrowErrorCommandStep1.class);
        ThrowErrorCommandStep1.ThrowErrorCommandStep2 step2 = mock(ThrowErrorCommandStep1.ThrowErrorCommandStep2.class);
        when(client.newThrowErrorCommand(anyLong())).thenReturn(step1);
        when(step1.errorCode(anyString())).thenReturn(step2);
        when(step2.errorMessage(anyString())).thenReturn(step2);

        CamundaFuture<ThrowErrorResponse> future = mockFuture(mock(ThrowErrorResponse.class));
        when(step2.send()).thenReturn(future);

        JobRequest request = new JobRequest();
        request.setJobKey(111L);
        request.setErrorCode("ERR_001");
        request.setErrorMessage("business error");

        JobResponse response = service.throwError(request);

        assertTrue(response.isSuccess());
        verify(client).newThrowErrorCommand(111L);
    }

    @Test
    void updateJobRetriesSuccess() {
        UpdateRetriesJobCommandStep1 step1 = mock(UpdateRetriesJobCommandStep1.class);
        UpdateRetriesJobCommandStep1.UpdateRetriesJobCommandStep2 step2
                = mock(UpdateRetriesJobCommandStep1.UpdateRetriesJobCommandStep2.class);
        when(client.newUpdateRetriesCommand(anyLong())).thenReturn(step1);
        when(step1.retries(anyInt())).thenReturn(step2);

        CamundaFuture<UpdateRetriesJobResponse> future = mockFuture(mock(UpdateRetriesJobResponse.class));
        when(step2.send()).thenReturn(future);

        JobRequest request = new JobRequest();
        request.setJobKey(222L);
        request.setRetries(5);

        JobResponse response = service.updateJobRetries(request);

        assertTrue(response.isSuccess());
        verify(client).newUpdateRetriesCommand(222L);
    }

    @Test
    void deployResourceProcessSuccess() {
        DeployResourceCommandStep1 step1 = mock(DeployResourceCommandStep1.class);
        DeployResourceCommandStep1.DeployResourceCommandStep2 step2
                = mock(DeployResourceCommandStep1.DeployResourceCommandStep2.class);
        when(client.newDeployResourceCommand()).thenReturn(step1);
        when(step1.addResourceBytes(any(byte[].class), anyString())).thenReturn(step2);

        DeploymentEvent event = mock(DeploymentEvent.class);
        Process process = mock(Process.class);
        when(process.getBpmnProcessId()).thenReturn("myProcess");
        when(process.getResourceName()).thenReturn("process.bpmn");
        when(process.getProcessDefinitionKey()).thenReturn(500L);
        when(process.getVersion()).thenReturn(2);
        when(event.getProcesses()).thenReturn(List.of(process));

        CamundaFuture<DeploymentEvent> future = mockFuture(event);
        when(step2.send()).thenReturn(future);

        DeploymentRequest request = new DeploymentRequest();
        request.setName("process.bpmn");
        request.setFileContent("<bpmn>content</bpmn>".getBytes());

        DeploymentResponse response = service.deployResource(request);

        assertTrue(response.isSuccess());
        assertInstanceOf(ProcessDeploymentResponse.class, response);
        ProcessDeploymentResponse processResponse = (ProcessDeploymentResponse) response;
        assertEquals("myProcess", processResponse.getBpmnProcessId());
        assertEquals("process.bpmn", processResponse.getResourceName());
        assertEquals(500L, processResponse.getProcessDefinitionKey());
        assertEquals(2, processResponse.getVersion());
    }

    @SuppressWarnings("unchecked")
    private <T> CamundaFuture<T> mockFuture(T value) {
        CamundaFuture<T> future = mock(CamundaFuture.class);
        when(future.join()).thenReturn(value);
        return future;
    }
}
