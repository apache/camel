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

package org.apache.camel.component.zeebe;

import java.time.Duration;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.zeebe.model.DeploymentRequest;
import org.apache.camel.component.zeebe.model.DeploymentResponse;
import org.apache.camel.component.zeebe.model.JobRequest;
import org.apache.camel.component.zeebe.model.JobWorkerMessage;
import org.apache.camel.component.zeebe.model.ProcessDeploymentResponse;
import org.apache.camel.component.zeebe.model.ProcessRequest;
import org.apache.camel.component.zeebe.model.ProcessResponse;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.testcontainers.shaded.org.awaitility.Awaitility;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MockitoSettings(strictness = Strictness.LENIENT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfSystemProperty(named = "zeebe.test.integration.enable", matches = "true",
                         disabledReason = "Requires locally installed test system")
class ZeebeConsumerIT extends CamelTestSupport {

    public static final String TEST_1_DEFINITION_BPMN = "test1_definition.bpmn";
    private ZeebeComponent component;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeAll
    void initAll() throws Exception {
        createComponent();
        component.doStart();

        DeploymentRequest deployProcessMessage = new DeploymentRequest();
        deployProcessMessage.setName(TEST_1_DEFINITION_BPMN);
        deployProcessMessage
                .setContent(this.getClass().getClassLoader().getResourceAsStream("data/test1_definition.bpmn").readAllBytes());

        DeploymentResponse deploymentResponse = component.getZeebeService().deployResource(deployProcessMessage);

        ProcessRequest processRequest = new ProcessRequest();
        processRequest.setProcessId(((ProcessDeploymentResponse) deploymentResponse).getBpmnProcessId());
        ProcessResponse processResponse = component.getZeebeService().startProcess(processRequest);
    }

    @Test
    public void shouldProcessJobWorkerMessage() throws Exception {
        MockEndpoint workerMock = getMockEndpoint("mock:jobWorker");
        workerMock.expectedMinimumMessageCount(1);

        Awaitility.await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> MockEndpoint.assertIsSatisfied(context));

        List<Exchange> exchanges = workerMock.getExchanges();
        for (Exchange exchange : exchanges) {
            JobWorkerMessage jobWorkerMessage = exchange.getMessage().getBody(JobWorkerMessage.class);

            assertNotNull(jobWorkerMessage);

            assertTrue(jobWorkerMessage.getKey() > 0);
            assertTrue(jobWorkerMessage.getProcessInstanceKey() > 0);
            assertNotNull(jobWorkerMessage.getBpmnProcessId());
            assertTrue(jobWorkerMessage.getProcessDefinitionVersion() > 0);
            assertTrue(jobWorkerMessage.getProcessDefinitionKey() > 0);
            assertNotNull(jobWorkerMessage.getElementId());
            assertTrue(jobWorkerMessage.getProcessInstanceKey() > 0);
            assertNotNull(jobWorkerMessage.getWorker());
            assertTrue(jobWorkerMessage.getRetries() > 0);
            assertTrue(jobWorkerMessage.getDeadline() > 0);

            JobRequest jobRequest = new JobRequest();
            jobRequest.setJobKey(jobWorkerMessage.getKey());
            component.getZeebeService().completeJob(jobRequest);
        }
    }

    @Test
    public void shouldProcessJobWorkerMessageJSON() throws Exception {
        MockEndpoint workerMock = getMockEndpoint("mock:jobWorker_JSON");
        workerMock.expectedMinimumMessageCount(1);

        Awaitility.await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> MockEndpoint.assertIsSatisfied(context));

        List<Exchange> exchanges = workerMock.getExchanges();
        for (Exchange exchange : exchanges) {
            String jobWorkerMessageString = exchange.getMessage().getBody(String.class);
            assertNotNull(jobWorkerMessageString);

            JobWorkerMessage jobWorkerMessage
                    = assertDoesNotThrow(() -> objectMapper.readValue(jobWorkerMessageString, JobWorkerMessage.class));

            assertTrue(jobWorkerMessage.getKey() > 0);
            assertTrue(jobWorkerMessage.getProcessInstanceKey() > 0);
            assertNotNull(jobWorkerMessage.getBpmnProcessId());
            assertTrue(jobWorkerMessage.getProcessDefinitionVersion() > 0);
            assertTrue(jobWorkerMessage.getProcessDefinitionKey() > 0);
            assertNotNull(jobWorkerMessage.getElementId());
            assertTrue(jobWorkerMessage.getProcessInstanceKey() > 0);
            assertNotNull(jobWorkerMessage.getWorker());
            assertTrue(jobWorkerMessage.getRetries() > 0);
            assertTrue(jobWorkerMessage.getDeadline() > 0);

            JobRequest jobRequest = new JobRequest();
            jobRequest.setJobKey(jobWorkerMessage.getKey());
            component.getZeebeService().completeJob(jobRequest);
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("zeebe://worker?jobKey=consumerTest&timeOut=3")
                        .to("mock:jobWorker");

                from("zeebe://worker?jobKey=consumerTestJSON&timeOut=3&formatJSON=true")
                        .to("mock:jobWorker_JSON");
            }
        };
    }

    protected void createComponent() throws Exception {
        component = new ZeebeComponent();

        component.setGatewayHost(ZeebeConstants.DEFAULT_GATEWAY_HOST);
        component.setGatewayPort(ZeebeConstants.DEFAULT_GATEWAY_PORT);
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        context.addComponent("zeebe", component);

        return context;
    }
}
