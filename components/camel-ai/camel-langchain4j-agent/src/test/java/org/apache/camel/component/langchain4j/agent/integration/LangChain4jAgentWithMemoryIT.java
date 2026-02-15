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
import java.util.Map;

import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.langchain4j.agent.api.Agent;
import org.apache.camel.component.langchain4j.agent.api.AgentConfiguration;
import org.apache.camel.component.langchain4j.agent.api.AgentWithMemory;
import org.apache.camel.component.langchain4j.agent.pojos.PersistentChatMemoryStore;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.ollama.services.OllamaService;
import org.apache.camel.test.infra.ollama.services.OllamaServiceFactory;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.apache.camel.component.langchain4j.agent.api.Headers.MEMORY_ID;
import static org.apache.camel.component.langchain4j.agent.api.Headers.SYSTEM_MESSAGE;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisabledIfSystemProperty(named = "ci.env.name", matches = ".*", disabledReason = "Requires too much network resources")
public class LangChain4jAgentWithMemoryIT extends CamelTestSupport {

    private static final int MEMORY_ID_SESSION_1 = 1;
    private static final int MEMORY_ID_SESSION_2 = 2;
    private static final String USER_NAME = "Alice";
    private static final String USER_FAVORITE_COLOR = "blue";

    protected ChatModel chatModel;
    protected ChatMemoryProvider chatMemoryProvider;
    private PersistentChatMemoryStore store;

    @RegisterExtension
    static OllamaService OLLAMA = OllamaServiceFactory.createSingletonService();

    @Override
    protected void setupResources() throws Exception {
        super.setupResources();

        chatModel = ModelHelper.loadChatModel(OLLAMA);
        store = new PersistentChatMemoryStore();
        chatMemoryProvider = createMemoryProvider();
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

    @BeforeEach
    void setup() {
        store.clearAll();
    }

    @Test
    void testBasicMemoryConversation() throws InterruptedException {
        MockEndpoint mockEndpoint = this.context.getEndpoint("mock:memory-response", MockEndpoint.class);
        mockEndpoint.expectedMessageCount(2);

        String firstResponse = template.requestBodyAndHeader(
                "direct:agent-with-memory",
                "My name is " + USER_NAME,
                MEMORY_ID, MEMORY_ID_SESSION_1,
                String.class);

        String secondResponse = template.requestBodyAndHeader(
                "direct:agent-with-memory",
                "What is my name?",
                MEMORY_ID, MEMORY_ID_SESSION_1,
                String.class);

        mockEndpoint.assertIsSatisfied();

        assertNotNull(firstResponse, "First AI response should not be null");
        assertNotNull(secondResponse, "Second AI response should not be null");
        assertTrue(secondResponse.contains(USER_NAME),
                "Agent should remember the user's name: " + secondResponse);
    }

    @Test
    void testMemoryPersistenceAcrossMultipleExchanges() throws InterruptedException {
        MockEndpoint mockEndpoint = this.context.getEndpoint("mock:memory-response", MockEndpoint.class);
        mockEndpoint.expectedMessageCount(3);

        template.requestBodyAndHeader(
                "direct:agent-with-memory",
                "My name is " + USER_NAME,
                MEMORY_ID, MEMORY_ID_SESSION_1,
                String.class);

        template.requestBodyAndHeader(
                "direct:agent-with-memory",
                "My favorite color is " + USER_FAVORITE_COLOR,
                MEMORY_ID, MEMORY_ID_SESSION_1,
                String.class);

        String finalResponse = template.requestBodyAndHeader(
                "direct:agent-with-memory",
                "Tell me about myself - what's my name and favorite color?",
                MEMORY_ID, MEMORY_ID_SESSION_1,
                String.class);

        mockEndpoint.assertIsSatisfied();

        assertNotNull(finalResponse, "Final AI response should not be null");
        assertTrue(finalResponse.contains(USER_NAME),
                "Agent should remember the user's name: " + finalResponse);
        assertTrue(finalResponse.contains(USER_FAVORITE_COLOR),
                "Agent should remember the user's favorite color: " + finalResponse);
    }

    @Test
    void testMemoryIsolationBetweenSessions() throws InterruptedException {
        MockEndpoint mockEndpoint = this.context.getEndpoint("mock:memory-response", MockEndpoint.class);
        mockEndpoint.expectedMessageCount(3);

        template.requestBodyAndHeader(
                "direct:agent-with-memory",
                "My name is " + USER_NAME,
                MEMORY_ID, MEMORY_ID_SESSION_1,
                String.class);

        String session2Response = template.requestBodyAndHeader(
                "direct:agent-with-memory",
                "What is my name?",
                MEMORY_ID, MEMORY_ID_SESSION_2,
                String.class);

        String session1Response = template.requestBodyAndHeader(
                "direct:agent-with-memory",
                "What is my name?",
                MEMORY_ID, MEMORY_ID_SESSION_1,
                String.class);

        mockEndpoint.assertIsSatisfied();

        assertNotNull(session1Response, "Session 1 response should not be null");
        assertNotNull(session2Response, "Session 2 response should not be null");

        assertTrue(session1Response.contains(USER_NAME),
                "Session 1 should remember the name: " + session1Response);
        assertTrue(!session2Response.contains(USER_NAME) ||
                session2Response.toLowerCase().contains("don't know") ||
                session2Response.toLowerCase().contains("not sure"),
                "Session 2 should not know the name from session 1: " + session2Response);
    }

    @Test
    void testMemoryWithSystemMessage() throws InterruptedException {
        MockEndpoint mockEndpoint = this.context.getEndpoint("mock:memory-response", MockEndpoint.class);
        mockEndpoint.expectedMessageCount(2);

        String firstResponse = template.requestBodyAndHeaders(
                "direct:agent-with-memory-system",
                "My favorite programming language is Java",
                Map.of(
                        MEMORY_ID, MEMORY_ID_SESSION_1,
                        SYSTEM_MESSAGE, "You are a helpful coding assistant. Always be enthusiastic about programming."),
                String.class);

        String secondResponse = template.requestBodyAndHeader(
                "direct:agent-with-memory-system",
                "What programming language do I like?",
                MEMORY_ID, MEMORY_ID_SESSION_1,
                String.class);

        mockEndpoint.assertIsSatisfied();

        assertNotNull(firstResponse, "First response should not be null");
        assertNotNull(secondResponse, "Second response should not be null");
        assertTrue(secondResponse.contains("Java"),
                "Agent should remember the programming language preference: " + secondResponse);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        // Create agent configuration with memory support
        AgentConfiguration configuration = new AgentConfiguration()
                .withChatModel(chatModel)
                .withChatMemoryProvider(chatMemoryProvider)
                .withInputGuardrailClasses(List.of())
                .withOutputGuardrailClasses(List.of());

        Agent agentWithMemory = new AgentWithMemory(configuration);

        // Register agent in the context
        this.context.getRegistry().bind("agentWithMemory", agentWithMemory);

        return new RouteBuilder() {
            public void configure() {
                // Agent routes for memory testing
                from("direct:agent-with-memory")
                        .to("langchain4j-agent:test-memory-agent?agent=#agentWithMemory")
                        .to("mock:memory-response");

                from("direct:agent-with-memory-system")
                        .to("langchain4j-agent:test-memory-agent?agent=#agentWithMemory")
                        .to("mock:memory-response");

            }
        };
    }

}
