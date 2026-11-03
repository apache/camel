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
import org.apache.camel.builder.RouteBuilder;
import org.flowable.common.engine.api.FlowableException;
import org.flowable.engine.runtime.ProcessInstance;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A route that sends to a channel starts before the channel model is deployed in the Flowable Event Registry.
 */
public class FlowableProducerChannelNotDeployedTest extends CamelFlowableTestCase {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:send").to("flowable:userRouteChannel");
            }
        };
    }

    @Test
    public void testSendBeforeChannelIsDeployed() {
        Exchange exchange = template.send("direct:send", e -> e.getIn().setBody(userBody()));

        FlowableException cause = assertInstanceOf(FlowableException.class, exchange.getException());
        assertEquals("No inbound channel with key userRouteChannel is deployed in the Flowable Event Registry,"
                     + " so flowable:userRouteChannel cannot send the event",
                cause.getMessage());
    }

    @Test
    public void testSendAfterChannelIsDeployed() {
        String deploymentId = deployProcessDefinition("process/start.bpmn20.xml");
        try {
            eventRegistryEngineConfiguration.getEventRepositoryService().createDeployment()
                    .addClasspathResource("channel/userRouteChannel.channel")
                    .addClasspathResource("event/userEvent.event")
                    .deploy();

            Exchange exchange = template.send("direct:send", e -> e.getIn().setBody(userBody()));
            assertNull(exchange.getException());

            ProcessInstance processInstance
                    = runtimeService.createProcessInstanceQuery().processDefinitionKey("camelProcess").singleResult();
            assertNotNull(processInstance);
            assertEquals("John Doe", runtimeService.getVariable(processInstance.getId(), "name"));
            assertEquals(23, runtimeService.getVariable(processInstance.getId(), "age"));
        } finally {
            repositoryService.deleteDeployment(deploymentId, true);
        }
    }

    private static ObjectNode userBody() {
        ObjectNode bodyNode = new ObjectMapper().createObjectNode();
        bodyNode.put("name", "John Doe");
        bodyNode.put("age", 23);
        return bodyNode;
    }
}
