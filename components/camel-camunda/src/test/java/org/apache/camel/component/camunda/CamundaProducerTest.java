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

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelException;
import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.component.camunda.internal.CamundaService;
import org.apache.camel.component.camunda.internal.OperationName;
import org.apache.camel.component.camunda.model.DeploymentResponse;
import org.apache.camel.component.camunda.model.JobRequest;
import org.apache.camel.component.camunda.model.JobResponse;
import org.apache.camel.component.camunda.model.MessageRequest;
import org.apache.camel.component.camunda.model.MessageResponse;
import org.apache.camel.component.camunda.model.ProcessRequest;
import org.apache.camel.component.camunda.model.ProcessResponse;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CamundaProducerTest {

    private CamelContext context;
    private CamundaService service;
    private CamundaComponent component;

    @BeforeEach
    void setUp() {
        context = new DefaultCamelContext();
        context.start();
        service = mock(CamundaService.class);
        component = mock(CamundaComponent.class);
        when(component.getCamundaService()).thenReturn(service);
    }

    @AfterEach
    void tearDown() {
        context.stop();
    }

    private CamundaProducer producer(OperationName operationName) {
        CamundaEndpoint endpoint = mock(CamundaEndpoint.class);
        when(endpoint.getOperationName()).thenReturn(operationName);
        when(endpoint.getComponent()).thenReturn(component);
        when(endpoint.getCamelContext()).thenReturn(context);
        return new CamundaProducer(endpoint);
    }

    private Exchange exchange(Object body) {
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody(body);
        return exchange;
    }

    private static ProcessResponse processSuccess() {
        ProcessResponse response = new ProcessResponse();
        response.setSuccess(true);
        return response;
    }

    @Test
    void startProcessReadsJsonFromBytes() throws Exception {
        when(service.startProcess(any())).thenReturn(processSuccess());

        producer(OperationName.START_PROCESS).process(
                exchange("{\"process_id\":\"order\",\"variables\":{\"v1\":\"a\"}}".getBytes(StandardCharsets.UTF_8)));

        ArgumentCaptor<ProcessRequest> request = ArgumentCaptor.forClass(ProcessRequest.class);
        verify(service).startProcess(request.capture());
        assertEquals("order", request.getValue().getProcessId());
        assertEquals("a", request.getValue().getVariables().get("v1"));
    }

    @Test
    void cancelProcessReadsJsonFromInputStream() throws Exception {
        when(service.cancelProcessInstance(any())).thenReturn(processSuccess());

        producer(OperationName.CANCEL_PROCESS).process(
                exchange(new ByteArrayInputStream("{\"process_instance_key\":123}".getBytes(StandardCharsets.UTF_8))));

        ArgumentCaptor<ProcessRequest> request = ArgumentCaptor.forClass(ProcessRequest.class);
        verify(service).cancelProcessInstance(request.capture());
        assertEquals(123L, request.getValue().getProcessInstanceKey());
    }

    @Test
    void startProcessWithoutBodyIsDataMissing() {
        CamundaProducer producer = producer(OperationName.START_PROCESS);
        Exchange exchange = exchange(null);

        CamelException e = assertThrows(CamelException.class, () -> producer.process(exchange));
        assertEquals("Process data missing", e.getMessage());
    }

    @Test
    void publishMessageReadsJsonFromBytes() throws Exception {
        MessageResponse response = new MessageResponse();
        response.setSuccess(true);
        when(service.publishMessage(any())).thenReturn(response);

        producer(OperationName.PUBLISH_MESSAGE).process(
                exchange("{\"name\":\"msg\",\"correlation_key\":\"c1\"}".getBytes(StandardCharsets.UTF_8)));

        ArgumentCaptor<MessageRequest> request = ArgumentCaptor.forClass(MessageRequest.class);
        verify(service).publishMessage(request.capture());
        assertEquals("msg", request.getValue().getName());
        assertEquals("c1", request.getValue().getCorrelationKey());
    }

    @Test
    void completeJobReadsJsonFromInputStream() throws Exception {
        JobResponse response = new JobResponse();
        response.setSuccess(true);
        when(service.completeJob(any())).thenReturn(response);

        producer(OperationName.COMPLETE_JOB).process(
                exchange(new ByteArrayInputStream("{\"job_key\":42}".getBytes(StandardCharsets.UTF_8))));

        ArgumentCaptor<JobRequest> request = ArgumentCaptor.forClass(JobRequest.class);
        verify(service).completeJob(request.capture());
        assertEquals(42L, request.getValue().getJobKey());
    }

    @Test
    void completeJobUsesJobKeyHeaderForWorkerJson() throws Exception {
        JobResponse response = new JobResponse();
        response.setSuccess(true);
        when(service.completeJob(any())).thenReturn(response);

        // the JSON of a worker with formatJSON=true has no job_key
        Exchange exchange = exchange("{\"key\":42,\"type\":\"myJobType\",\"variablesAsMap\":{\"v1\":\"a\"}}");
        exchange.getMessage().setHeader(CamundaConstants.JOB_KEY, 42L);
        producer(OperationName.COMPLETE_JOB).process(exchange);

        ArgumentCaptor<JobRequest> request = ArgumentCaptor.forClass(JobRequest.class);
        verify(service).completeJob(request.capture());
        assertEquals(42L, request.getValue().getJobKey());
    }

    @Test
    void failedOperationFailsTheExchange() {
        ProcessResponse response = new ProcessResponse();
        response.setSuccess(false);
        response.setErrorMessage("connection refused");
        when(service.startProcess(any())).thenReturn(response);

        CamundaProducer producer = producer(OperationName.START_PROCESS);
        Exchange exchange = exchange("{\"process_id\":\"order\"}");

        CamelExchangeException e = assertThrows(CamelExchangeException.class, () -> producer.process(exchange));
        assertTrue(e.getMessage().contains("startProcess failed: connection refused"), e.getMessage());
        assertSame(response, exchange.getMessage().getBody());
    }

    @Test
    void failedJobOperationFailsTheExchange() {
        JobResponse response = new JobResponse();
        response.setSuccess(false);
        response.setErrorMessage("job not found");
        when(service.completeJob(any())).thenReturn(response);

        CamundaProducer producer = producer(OperationName.COMPLETE_JOB);
        Exchange exchange = exchange(null);
        exchange.getMessage().setHeader(CamundaConstants.JOB_KEY, 42L);

        CamelExchangeException e = assertThrows(CamelExchangeException.class, () -> producer.process(exchange));
        assertTrue(e.getMessage().contains("completeJob failed: job not found"), e.getMessage());
        assertSame(response, exchange.getMessage().getBody());
        assertEquals(Boolean.TRUE, exchange.getProperty(CamundaConstants.JOB_HANDLED, Boolean.class));
    }

    @Test
    void failedPublishMessageFailsTheExchange() {
        MessageResponse response = new MessageResponse();
        response.setSuccess(false);
        response.setErrorMessage("Correlation Key is missing!");
        when(service.publishMessage(any())).thenReturn(response);

        CamundaProducer producer = producer(OperationName.PUBLISH_MESSAGE);
        Exchange exchange = exchange(new MessageRequest());

        assertThrows(CamelExchangeException.class, () -> producer.process(exchange));
        assertSame(response, exchange.getMessage().getBody());
    }

    @Test
    void failedDeploymentFailsTheExchange() {
        DeploymentResponse response = new DeploymentResponse();
        response.setSuccess(false);
        response.setErrorMessage("invalid resource");
        when(service.deployResource(any())).thenReturn(response);

        CamundaProducer producer = producer(OperationName.DEPLOY_RESOURCE);
        Exchange exchange = exchange("<bpmn/>");
        exchange.getMessage().setHeader(CamundaConstants.RESOURCE_NAME, "process.bpmn");

        assertThrows(CamelExchangeException.class, () -> producer.process(exchange));
        assertSame(response, exchange.getMessage().getBody());
        assertFalse(exchange.getMessage().getHeader(CamundaConstants.IS_SUCCESS, Boolean.class));
        assertEquals("invalid resource", exchange.getMessage().getHeader(CamundaConstants.ERROR_MESSAGE));
    }

    @Test
    void successfulOperationKeepsTheResponse() throws Exception {
        ProcessResponse response = processSuccess();
        when(service.startProcess(any())).thenReturn(response);

        Exchange exchange = exchange("{\"process_id\":\"order\"}");
        producer(OperationName.START_PROCESS).process(exchange);

        assertInstanceOf(ProcessResponse.class, exchange.getMessage().getBody());
        assertSame(response, exchange.getMessage().getBody());
    }
}
