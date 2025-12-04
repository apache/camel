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

package org.apache.camel.component.langchain4j.agent.integration;

import static org.apache.camel.component.langchain4j.agent.api.Headers.SYSTEM_MESSAGE;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.langchain4j.agent.api.Agent;
import org.apache.camel.component.langchain4j.agent.api.AgentConfiguration;
import org.apache.camel.component.langchain4j.agent.api.AgentWithoutMemory;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@DisabledIfSystemProperty(named = "ci.env.name", matches = ".*", disabledReason = "Requires too much network resources")
public class LangChain4jAgentNaiveRagIT extends AbstractRAGIT {

    private static final String SYSTEM_MESSAGE_CUSTOMER_SERVICE =
            "You are a friendly customer service representative for Miles of Camels Car Rental. Always be helpful and polite.";

    @Test
    void testAgentWithRagBasicQuery() throws InterruptedException {
        MockEndpoint mockEndpoint = this.context.getEndpoint("mock:rag-response", MockEndpoint.class);
        mockEndpoint.expectedMessageCount(1);

        String response = template.requestBodyAndHeader(
                "direct:agent-with-rag",
                "What is the cancellation policy?",
                SYSTEM_MESSAGE,
                SYSTEM_MESSAGE_CUSTOMER_SERVICE,
                String.class);

        mockEndpoint.assertIsSatisfied();
        assertNotNull(response, "AI response should not be null");
        assertTrue(
                response.toLowerCase().contains("cancel")
                        || response.toLowerCase().contains("refund"),
                "Response should contain cancellation information: " + response);
        assertTrue(
                response.contains("24 hours") || response.contains("12 hours"),
                "Response should mention timeframes: " + response);
    }

    @Test
    void testAgentWithRagInsuranceQuery() throws InterruptedException {
        MockEndpoint mockEndpoint = this.context.getEndpoint("mock:rag-response", MockEndpoint.class);
        mockEndpoint.expectedMessageCount(1);

        String response = template.requestBodyAndHeader(
                "direct:agent-with-rag",
                "Tell me about insurance coverage options",
                SYSTEM_MESSAGE,
                SYSTEM_MESSAGE_CUSTOMER_SERVICE,
                String.class);

        mockEndpoint.assertIsSatisfied();
        assertNotNull(response, "AI response should not be null");
        assertTrue(
                response.toLowerCase().contains("insurance"),
                "Response should contain insurance information: " + response);
        assertTrue(
                response.contains("$15") || response.contains("premium") || response.contains("basic"),
                "Response should mention insurance options: " + response);
    }

    @Test
    void testAgentWithRagAgeRequirements() throws InterruptedException {
        MockEndpoint mockEndpoint = this.context.getEndpoint("mock:rag-response", MockEndpoint.class);
        mockEndpoint.expectedMessageCount(1);

        String response = template.requestBodyAndHeader(
                "direct:agent-with-rag",
                "How old do I need to be to rent a car?",
                SYSTEM_MESSAGE,
                SYSTEM_MESSAGE_CUSTOMER_SERVICE,
                String.class);

        mockEndpoint.assertIsSatisfied();
        assertNotNull(response, "AI response should not be null");
        assertTrue(
                response.contains("21") || response.contains("age"),
                "Response should mention age requirements: " + response);
    }

    @Test
    void testAgentWithRagDamagePolicy() throws InterruptedException {
        MockEndpoint mockEndpoint = this.context.getEndpoint("mock:rag-response", MockEndpoint.class);
        mockEndpoint.expectedMessageCount(1);

        String response = template.requestBodyAndHeader(
                "direct:agent-with-rag",
                "What happens if I damage the car?",
                SYSTEM_MESSAGE,
                SYSTEM_MESSAGE_CUSTOMER_SERVICE,
                String.class);

        mockEndpoint.assertIsSatisfied();
        assertNotNull(response, "AI response should not be null");
        assertTrue(
                response.toLowerCase().contains("damage")
                        || response.toLowerCase().contains("$200"),
                "Response should contain damage policy information: " + response);
    }

    @Test
    void testAgentWithRagReturnPolicy() throws InterruptedException {
        MockEndpoint mockEndpoint = this.context.getEndpoint("mock:rag-response", MockEndpoint.class);
        mockEndpoint.expectedMessageCount(1);

        String response = template.requestBodyAndHeader(
                "direct:agent-with-rag",
                "What are the rules for returning the vehicle?",
                SYSTEM_MESSAGE,
                SYSTEM_MESSAGE_CUSTOMER_SERVICE,
                String.class);

        mockEndpoint.assertIsSatisfied();
        assertNotNull(response, "AI response should not be null");
        assertTrue(
                response.toLowerCase().contains("fuel")
                        || response.toLowerCase().contains("return")
                        || response.contains("$25"),
                "Response should contain return policy information: " + response);
    }

    @Test
    void testAgentWithRagSystemMessage() throws InterruptedException {
        MockEndpoint mockEndpoint = this.context.getEndpoint("mock:rag-response", MockEndpoint.class);
        mockEndpoint.expectedMessageCount(1);

        String response = template.requestBodyAndHeader(
                "direct:agent-with-rag",
                "What's your cancellation policy?",
                SYSTEM_MESSAGE,
                SYSTEM_MESSAGE_CUSTOMER_SERVICE,
                String.class);

        mockEndpoint.assertIsSatisfied();
        assertNotNull(response, "AI response should not be null");
        assertTrue(
                response.toLowerCase().contains("cancel")
                        || response.toLowerCase().contains("refund"),
                "Response should contain cancellation information: " + response);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        // Create agent configuration with RAG support
        AgentConfiguration configuration = new AgentConfiguration()
                .withChatModel(chatModel)
                .withRetrievalAugmentor(retrievalAugmentor)
                .withInputGuardrailClasses(List.of())
                .withOutputGuardrailClasses(List.of());

        Agent agentWithRag = new AgentWithoutMemory(configuration);

        // Register agent in the context
        this.context.getRegistry().bind("agentWithRag", agentWithRag);

        return new RouteBuilder() {
            public void configure() {
                from("direct:agent-with-rag")
                        .to("langchain4j-agent:test-rag-agent?agent=#agentWithRag")
                        .to("mock:rag-response");
            }
        };
    }
}
