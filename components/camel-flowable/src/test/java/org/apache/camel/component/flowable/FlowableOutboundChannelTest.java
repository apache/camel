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
package org.apache.camel.component.flowable;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.camel.component.mock.MockEndpoint;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class FlowableOutboundChannelTest extends CamelFlowableTestCase {

    @BeforeEach
    public void deployEventRegistryModels() throws Exception {
        eventRegistryEngineConfiguration.getEventRepositoryService().createDeployment()
                .addClasspathResource("channel/userOutboundChannel.channel")
                .addClasspathResource("event/userEvent.event")
                .deploy();
    }

    @Test
    public void testSendBasicEvent() throws Exception {
        String deploymentId = deployProcessDefinition("process/sendEvent.bpmn20.xml");
        try {
            ProcessInstance processInstance = runtimeService.createProcessInstanceBuilder().processDefinitionKey("camelProcess")
                    .variable("name", "John Doe")
                    .variable("age", 23)
                    .start();

            assertEquals("John Doe", runtimeService.getVariable(processInstance.getId(), "name"));
            assertEquals(23, runtimeService.getVariable(processInstance.getId(), "age"));

            Task task = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
            taskService.complete(task.getId());

            MockEndpoint mockEndpoint = (MockEndpoint) context.getEndpoint("mock:testQueue");
            assertEquals(1, mockEndpoint.getExchanges().size());
            String bodyString = mockEndpoint.getExchanges().get(0).getIn().getBody(String.class);
            JsonNode bodyNode = processEngineConfiguration.getObjectMapper().readTree(bodyString);
            assertEquals("John Doe", bodyNode.get("name").asText());
            assertEquals(23, bodyNode.get("age").asInt());

        } finally {
            repositoryService.deleteDeployment(deploymentId, true);
        }
    }
}
