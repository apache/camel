/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.camel.component.zeebe;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.zeebe.model.*;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.*;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("standalone")
@MockitoSettings(strictness = Strictness.LENIENT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ZeebeConsumerIntegrationTest extends CamelTestSupport {

    public static final String TEST_1_DEFINITION_BPMN = "test1_definition.bpmn";
    private ZeebeComponent component;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeAll
    void initAll() throws Exception {
        createComponent();
        component.doStart();

        DeploymentRequest deployProcessMessage = new DeploymentRequest();
        deployProcessMessage.setName(TEST_1_DEFINITION_BPMN);
        deployProcessMessage.setContent(this.getClass().getClassLoader().getResourceAsStream("data/test1_definition.bpmn").readAllBytes());

        DeploymentResponse deploymentResponse = component.getZeebeService().deployResource(deployProcessMessage);

        ProcessRequest processRequest = new ProcessRequest();
        processRequest.setProcessId(((ProcessDeploymentResponse) deploymentResponse).getBpmnProcessId());
        ProcessResponse processResponse = component.getZeebeService().startProcess(processRequest);
    }

    @BeforeEach
    void init() throws Exception {

    }

    @Test
    public void shouldProcessJobWorkerMessage() throws Exception {
        MockEndpoint workerMock = getMockEndpoint("mock:jobWorker");
        workerMock.expectedMinimumMessageCount(1);

        for (int i=0; i<10; i++) {
            if (!workerMock.getExchanges().isEmpty()) {
                break;
            }
            TimeUnit.SECONDS.sleep(2);
        }
        MockEndpoint.assertIsSatisfied(context);

        List<Exchange> exchanges = workerMock.getExchanges();
        for (Exchange exchange : exchanges) {
            JobWorkerMessage jobWorkerMessage = exchange.getIn().getBody(JobWorkerMessage.class);
            if (jobWorkerMessage != null) {
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
            } else {
                Assertions.fail();
            }
        }
    }

    @Test
    public void shouldProcessJobWorkerMessageJSON() throws Exception {
        MockEndpoint workerMock = getMockEndpoint("mock:jobWorker_JSON");
        workerMock.expectedMinimumMessageCount(1);

        for (int i=0; i<10; i++) {
            if (!workerMock.getExchanges().isEmpty()) {
                break;
            }
            TimeUnit.SECONDS.sleep(2);
        }
        MockEndpoint.assertIsSatisfied(context);

        List<Exchange> exchanges = workerMock.getExchanges();
        for (Exchange exchange : exchanges) {
            String jobWorkerMessageString = exchange.getIn().getBody(String.class);
            if (jobWorkerMessageString != null) {
                JobWorkerMessage jobWorkerMessage = objectMapper.readValue(jobWorkerMessageString, JobWorkerMessage.class);

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
            } else {
                Assertions.fail();
            }
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("zeebe://worker?jobKey=consumerTest&timeOut=1")
                        .to("mock:jobWorker");

                from("zeebe://worker?jobKey=consumerTestJSON&timeOut=1&formatJSON=true")
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