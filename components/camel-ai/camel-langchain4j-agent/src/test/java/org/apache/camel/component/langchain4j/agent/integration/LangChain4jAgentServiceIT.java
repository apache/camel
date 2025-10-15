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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.langchain4j.agent.api.Agent;
import org.apache.camel.component.langchain4j.agent.api.AgentConfiguration;
import org.apache.camel.component.langchain4j.agent.api.AgentWithoutMemory;
import org.apache.camel.component.langchain4j.agent.api.AiAgentBody;
import org.apache.camel.component.langchain4j.agent.pojos.TestJsonOutputGuardrail;
import org.apache.camel.component.langchain4j.agent.pojos.TestSuccessInputGuardrail;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisabledIfSystemProperty(named = "ci.env.name", matches = ".*", disabledReason = "Requires too much network resources")
public class LangChain4jAgentServiceIT extends AbstractRAGIT {

    private static final String USER_DATABASE = """
            {"id": "123", "name": "John Smith", "membership": "Gold", "rentals": 15, "preferredVehicle": "SUV"}
            """;

    private static final String WEATHER_INFO = "Sunny, 22°C, perfect driving conditions";

    @BeforeEach
    void setup() {
        // Reset all guardrails before each test
        TestSuccessInputGuardrail.reset();
        TestJsonOutputGuardrail.reset();
    }

    @Test
    void testCompleteIntegrationWithoutMemory() throws InterruptedException {
        MockEndpoint mockEndpoint = this.context.getEndpoint("mock:agent-response", MockEndpoint.class);
        mockEndpoint.expectedMessageCount(1);

        AiAgentBody<?> request = new AiAgentBody<>(
                """
                        Can you look up user 123 and tell me about our rental policies?
                        Please provide the response in this JSON format:
                        {
                          "userInfo": {"name": "string", "membership": "string"},
                          "policies": ["policy1", "policy2"],
                          "summary": "string"
                        }
                        """);

        String response = template.requestBody(
                "direct:complete-agent-no-memory",
                request,
                String.class);

        mockEndpoint.assertIsSatisfied();
        assertNotNull(response, "AI response should not be null");

        // check tools
        assertTrue(response.contains("John Smith") || response.contains("Gold"),
                "Response should contain user information from tools");

        // check RAG
        assertTrue(
                response.contains("21") || response.contains("age") || response.contains("license")
                        || response.contains("credit"),
                "Response should contain rental policy information from RAG");

        // Check guardrails
        assertTrue(TestSuccessInputGuardrail.wasValidated(),
                "Input guardrail should have been called");
        assertTrue(TestJsonOutputGuardrail.wasValidated(),
                "JSON output guardrail should have been called");

        assertTrue(response.trim().startsWith("{"), "Response should be JSON format");
        assertTrue(response.contains("userInfo") || response.contains("policies") || response.contains("summary"),
                "Response should contain JSON structure");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        // Create agent configuration with all features: RAG, Tools, and Guardrails
        AgentConfiguration configuration = new AgentConfiguration()
                .withChatModel(chatModel)
                .withRetrievalAugmentor(retrievalAugmentor)
                .withInputGuardrailClassesList("org.apache.camel.component.langchain4j.agent.pojos.TestSuccessInputGuardrail")
                .withOutputGuardrailClassesList("org.apache.camel.component.langchain4j.agent.pojos.TestJsonOutputGuardrail");

        Agent completeAgent = new AgentWithoutMemory(configuration);

        // Register agent in the context
        this.context.getRegistry().bind("completeAgent", completeAgent);

        return new RouteBuilder() {
            public void configure() {

                from("direct:complete-agent-no-memory")
                        .to("langchain4j-agent:no-memory?agent=#completeAgent&tags=users,weather")
                        .to("mock:agent-response");

                from("langchain4j-tools:userDb?tags=users&description=Query user database by user ID&parameter.userId=string")
                        .setBody(constant(USER_DATABASE));

                from("langchain4j-tools:weatherService?tags=weather&description=Get current weather information&parameter.location=string")
                        .setBody(constant("{\"weather\": \"" + WEATHER_INFO + "\", \"location\": \"Current Location\"}"));
            }
        };
    }
}
