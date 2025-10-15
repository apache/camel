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

import java.util.List;

import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.langchain4j.agent.api.Agent;
import org.apache.camel.component.langchain4j.agent.api.AgentConfiguration;
import org.apache.camel.component.langchain4j.agent.api.AgentWithMemory;
import org.apache.camel.component.langchain4j.agent.api.AiAgentBody;
import org.apache.camel.component.langchain4j.agent.pojos.PersistentChatMemoryStore;
import org.apache.camel.component.langchain4j.agent.pojos.TestJsonOutputGuardrail;
import org.apache.camel.component.langchain4j.agent.pojos.TestSuccessInputGuardrail;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Class to test a mix match between all those different concepts : memory / tool / RAG / guardrails
 */
@DisabledIfSystemProperty(named = "ci.env.name", matches = ".*", disabledReason = "Requires too much network resources")
public class LangChain4jAgentWithMemoryServiceIT extends AbstractRAGIT {

    private static final int MEMORY_ID_SESSION = 42;

    private static final String USER_DATABASE = """
            {"id": "123", "name": "John Smith", "membership": "Gold", "rentals": 15, "preferredVehicle": "SUV"}
            """;

    private static final String WEATHER_INFO = "Sunny, 22°C, perfect driving conditions";

    private ChatMemoryProvider chatMemoryProvider;
    private PersistentChatMemoryStore store;

    @Override
    protected void setupResources() throws Exception {
        super.setupResources();
        store = new PersistentChatMemoryStore();
        chatMemoryProvider = createMemoryProvider();
    }

    @BeforeEach
    void setup() {
        // Reset all guardrails before each test
        TestSuccessInputGuardrail.reset();
        TestJsonOutputGuardrail.reset();

        // reset store
        store.clearAll();
    }

    protected ChatMemoryProvider createMemoryProvider() {
        // Create a message window memory that keeps the last 10 messages
        ChatMemoryProvider chatMemoryProvider = memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(10)
                .chatMemoryStore(store)
                .build();
        return chatMemoryProvider;
    }

    @Test
    void testCompleteIntegrationWithMemoryAndTools() throws InterruptedException {
        MockEndpoint mockEndpoint = this.context.getEndpoint("mock:agent-response", MockEndpoint.class);
        mockEndpoint.expectedMessageCount(2);

        AiAgentBody<?> firstRequest = new AiAgentBody<>(
                "Hi! Can you look up user 123 and tell me about our rental policies?",
                null,
                MEMORY_ID_SESSION);

        String firstResponse = template.requestBody(
                "direct:complete-agent",
                firstRequest,
                String.class);

        assertNotNull(firstResponse, "First response should not be null");
        assertTrue(firstResponse.contains("John Smith") || firstResponse.contains("Gold"),
                "Response should contain user information from tools but was: " + firstResponse);
        assertTrue(firstResponse.contains("21") || firstResponse.contains("age") || firstResponse.contains("rental"),
                "Response should contain rental policy information from RAG");

        // Second interaction: Follow-up question
        AiAgentBody<?> secondRequest = new AiAgentBody<>(
                "What's his preferred vehicle type?",
                null,
                MEMORY_ID_SESSION);

        String secondResponse = template.requestBody(
                "direct:complete-agent",
                secondRequest,
                String.class);

        assertNotNull(secondResponse, "Second response should not be null");
        assertTrue(secondResponse.contains("SUV"),
                "Response should remember user context and mention SUV preference");

        mockEndpoint.assertIsSatisfied();

        // Verify guardrails were called
        assertTrue(TestSuccessInputGuardrail.wasValidated(),
                "Input guardrail should have been called");

        // Verify memory persistence
        assertTrue(store.getMemoryCount() > 0, "Memory should be persisted");
        assertFalse(store.getMessages(MEMORY_ID_SESSION).isEmpty(), "Session memory should contain messages");
    }

    @Test
    void testCompleteIntegrationWithJsonOutput() throws InterruptedException {
        MockEndpoint mockEndpoint = this.context.getEndpoint("mock:agent-response", MockEndpoint.class);
        mockEndpoint.expectedMessageCount(1);

        AiAgentBody<?> request = new AiAgentBody<>(
                """
                        Please provide user 123's information in this exact JSON format:
                        {
                          "userId": "string",
                          "name": "string",
                          "membership": "string",
                          "totalRentals": "number"
                        }
                        """,
                null,
                MEMORY_ID_SESSION);

        String response = template.requestBody(
                "direct:complete-agent-json",
                request,
                String.class);

        mockEndpoint.assertIsSatisfied();
        assertNotNull(response, "AI response should not be null");

        // Verify JSON output guardrail
        assertTrue(TestJsonOutputGuardrail.wasValidated(),
                "JSON output guardrail should have been called");

        // Verify JSON structure
        assertTrue(response.trim().startsWith("{"), "Response should be JSON");
        assertTrue(response.contains("123") && response.contains("John Smith"),
                "Response should contain user data from tools");
    }

    @Test
    void testRagWithoutTools() throws InterruptedException {
        MockEndpoint mockEndpoint = this.context.getEndpoint("mock:agent-response", MockEndpoint.class);
        mockEndpoint.expectedMessageCount(1);

        AiAgentBody<?> request = new AiAgentBody<>(
                "What are your business hours on weekends?",
                null,
                MEMORY_ID_SESSION);

        String response = template.requestBody(
                "direct:rag-only-agent",
                request,
                String.class);

        mockEndpoint.assertIsSatisfied();
        assertNotNull(response, "AI response should not be null");
        assertTrue(response.contains("Saturday") || response.contains("9:00") || response.contains("Sunday"),
                "Response should contain weekend hours from RAG knowledge base");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        // Create complete agent configuration with Tools + Memory + Input Guardrails + RAG
        AgentConfiguration completeConfig = new AgentConfiguration()
                .withChatModel(chatModel)
                .withChatMemoryProvider(chatMemoryProvider)
                .withRetrievalAugmentor(retrievalAugmentor)
                .withInputGuardrailClassesList("org.apache.camel.component.langchain4j.agent.pojos.TestSuccessInputGuardrail")
                .withOutputGuardrailClasses(List.of());
        Agent completeAgent = new AgentWithMemory(completeConfig);

        // Create complete agent with JSON output guardrails
        AgentConfiguration completeJsonConfig = new AgentConfiguration()
                .withChatModel(chatModel)
                .withChatMemoryProvider(chatMemoryProvider)
                .withRetrievalAugmentor(retrievalAugmentor)
                .withInputGuardrailClassesList("org.apache.camel.component.langchain4j.agent.pojos.TestSuccessInputGuardrail")
                .withOutputGuardrailClassesList("org.apache.camel.component.langchain4j.agent.pojos.TestJsonOutputGuardrail");
        Agent completeJsonAgent = new AgentWithMemory(completeJsonConfig);

        // Create RAG-only agent (no tools, just RAG + Memory + Input Guardrails)
        AgentConfiguration ragOnlyConfig = new AgentConfiguration()
                .withChatModel(chatModel)
                .withChatMemoryProvider(chatMemoryProvider)
                .withRetrievalAugmentor(retrievalAugmentor)
                .withInputGuardrailClassesList("org.apache.camel.component.langchain4j.agent.pojos.TestSuccessInputGuardrail")
                .withOutputGuardrailClasses(List.of());
        Agent ragOnlyAgent = new AgentWithMemory(ragOnlyConfig);

        // Register agents in the context
        this.context.getRegistry().bind("completeAgent", completeAgent);
        this.context.getRegistry().bind("completeJsonAgent", completeJsonAgent);
        this.context.getRegistry().bind("ragOnlyAgent", ragOnlyAgent);

        return new RouteBuilder() {
            public void configure() {
                //  Tools + Memory + Guardrails + RAG
                from("direct:complete-agent")
                        .to("langchain4j-agent:complete?agent=#completeAgent&tags=users,weather")
                        .to("mock:agent-response");

                //  Tools + Memory + JSON output Guardrails + RAG
                from("direct:complete-agent-json")
                        .to("langchain4j-agent:complete-json?agent=#completeJsonAgent&tags=users")
                        .to("mock:agent-response");

                // RAG only without tools
                from("direct:rag-only-agent")
                        .to("langchain4j-agent:rag-only?agent=#ragOnlyAgent")
                        .to("mock:agent-response");

                // Tool routes for function calling
                from("langchain4j-tools:userDb?tags=users&description=Query user database by user ID&parameter.userId=string")
                        .setBody(constant(USER_DATABASE));

                from("langchain4j-tools:weatherService?tags=weather&description=Get current weather information&parameter.location=string")
                        .setBody(constant("{\"weather\": \"" + WEATHER_INFO + "\", \"location\": \"Current Location\"}"));
            }
        };
    }

}
