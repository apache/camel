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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.camel.Exchange;
import org.flowable.engine.runtime.ProcessInstance;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class FlowableRedeployChannelTest extends CamelFlowableTestCase {

    @Test
    public void testUnregisterChannelModel() throws Exception {
        String deploymentId = deployProcessDefinition("process/start.bpmn20.xml");
        try {
            eventRegistryEngineConfiguration.getEventRepositoryService().createDeployment()
                    .addClasspathResource("channel/userChannel.channel")
                    .addClasspathResource("event/userEvent.event")
                    .deploy();

            ObjectNode bodyNode = new ObjectMapper().createObjectNode();
            bodyNode.put("name", "John Doe");
            bodyNode.put("age", 23);
            Exchange exchange = context.getEndpoint("direct:start").createExchange();
            exchange.getIn().setBody(bodyNode);
            template.send("direct:start", exchange);

            ProcessInstance processInstance
                    = runtimeService.createProcessInstanceQuery().processDefinitionKey("camelProcess").singleResult();
            assertNotNull(processInstance);

            assertEquals("John Doe", runtimeService.getVariable(processInstance.getId(), "name"));
            assertEquals(23, runtimeService.getVariable(processInstance.getId(), "age"));

            eventRegistryEngineConfiguration.getEventRepositoryService().createDeployment()
                    .addClasspathResource("channel/userChannelUpdated.channel")
                    .addClasspathResource("event/userEventUpdated.event")
                    .deploy();

            bodyNode = new ObjectMapper().createObjectNode();
            bodyNode.put("name", "Jane Doe");
            bodyNode.put("age", 28);
            bodyNode.put("address", "Main street 1");
            assertEquals(null, context.hasEndpoint("direct:start"));

            assertEquals(1, runtimeService.createProcessInstanceQuery().processDefinitionKey("camelProcess").count());

            exchange = context.getEndpoint("direct:startUpdated").createExchange();
            exchange.getIn().setBody(bodyNode);
            template.send("direct:startUpdated", exchange);

            assertEquals(2, runtimeService.createProcessInstanceQuery().processDefinitionKey("camelProcess").count());

        } finally {
            repositoryService.deleteDeployment(deploymentId, true);
        }
    }
}
