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
package org.apache.camel.component.openai.integration;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.mcp.everything.services.McpEverythingService;
import org.apache.camel.test.infra.mcp.everything.services.McpEverythingServiceFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test demonstrating per-user conversation memory with MCP tools using an external store.
 *
 * <p>
 * Simulates a multi-user API scenario where each authenticated user has their own conversation history persisted across
 * exchanges. The store is a simple {@link ConcurrentHashMap} keyed by user ID.
 *
 * <p>
 * The route:
 * <ol>
 * <li>Loads the user's conversation history from the store (if any) into the exchange property</li>
 * <li>Calls the LLM with MCP tools and conversation memory enabled</li>
 * <li>Saves the updated conversation history back to the store</li>
 * </ol>
 */
@DisabledIfSystemProperty(named = "ci.env.name", matches = ".*", disabledReason = "Requires too much network resources")
public class OpenAIMcpConversationStoreIT extends OpenAITestSupport {

    private static final String MCP_PROTOCOL_VERSIONS = "2024-11-05,2025-03-26,2025-06-18";
    private static final String CONVERSATION_HISTORY_PROPERTY = "CamelOpenAIConversationHistory";

    /** Simple per-user conversation store — in production this would be Redis, a database, etc. */
    private final Map<String, List<?>> conversationStore = new ConcurrentHashMap<>();

    @RegisterExtension
    static McpEverythingService MCP_EVERYTHING = McpEverythingServiceFactory.createService();

    @Override
    protected RouteBuilder createRouteBuilder() {
        String mcpUrl = MCP_EVERYTHING.url();

        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:chat")
                        // Load conversation history for the authenticated user
                        .process(exchange -> {
                            String userId = exchange.getIn().getHeader("X-User-Id", String.class);
                            List<?> history = conversationStore.get(userId);
                            if (history != null) {
                                exchange.setProperty(CONVERSATION_HISTORY_PROPERTY, history);
                            }
                        })

                        // Call LLM with MCP tools and conversation memory
                        .toF("openai:chat-completion?conversationMemory=true"
                             + "&mcpServer.everything.transportType=streamableHttp"
                             + "&mcpServer.everything.url=%s"
                             + "&mcpProtocolVersions=%s",
                                mcpUrl, MCP_PROTOCOL_VERSIONS)

                        // Save updated conversation history back to the store
                        .process(exchange -> {
                            String userId = exchange.getIn().getHeader("X-User-Id", String.class);
                            List<?> history = exchange.getProperty(CONVERSATION_HISTORY_PROPERTY, List.class);
                            if (userId != null && history != null) {
                                conversationStore.put(userId, history);
                            }
                        })

                        .to("mock:response");
            }
        };
    }

    /**
     * Tests that a single user's conversation history persists across multiple exchanges, including tool call chains.
     */
    @Test
    void testMultiTurnConversationForSingleUser() throws InterruptedException {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:response");

        // Turn 1: Use a tool — the tool call chain is stored in the conversation store
        mockEndpoint.expectedMessageCount(1);

        Exchange turn1 = template.request("direct:chat", e -> {
            e.getIn().setHeader("X-User-Id", "alice");
            e.getIn().setBody("Use the add tool to add 15 and 27. What is the result?");
        });

        mockEndpoint.assertIsSatisfied();
        String turn1Response = turn1.getMessage().getBody(String.class);
        assertNotNull(turn1Response);
        assertTrue(turn1Response.contains("42"),
                "Turn 1 should contain 42 but was: " + turn1Response);

        // Verify history was saved to the store
        List<?> aliceHistory = conversationStore.get("alice");
        assertNotNull(aliceHistory, "Alice's history should be in the store");
        assertTrue(aliceHistory.size() >= 3,
                "History should contain tool call chain (at least 3 entries) but had: " + aliceHistory.size());

        // Turn 2: Follow-up question — the model should remember the previous tool interaction
        mockEndpoint.reset();
        mockEndpoint.expectedMessageCount(1);

        Exchange turn2 = template.request("direct:chat", e -> {
            e.getIn().setHeader("X-User-Id", "alice");
            e.getIn().setBody("What was the result of the calculation you just did?");
        });

        mockEndpoint.assertIsSatisfied();
        String turn2Response = turn2.getMessage().getBody(String.class);
        assertNotNull(turn2Response);
        assertTrue(turn2Response.contains("42"),
                "Turn 2 should reference 42 from the previous turn but was: " + turn2Response);
    }

    /**
     * Tests that different users have isolated conversation histories.
     */
    @Test
    void testIsolatedConversationsPerUser() throws InterruptedException {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:response");

        // Alice uses the add tool
        mockEndpoint.expectedMessageCount(1);

        template.request("direct:chat", e -> {
            e.getIn().setHeader("X-User-Id", "alice");
            e.getIn().setBody("Use the add tool to add 100 and 200. What is the result?");
        });

        mockEndpoint.assertIsSatisfied();

        // Bob uses the echo tool — completely separate conversation
        mockEndpoint.reset();
        mockEndpoint.expectedMessageCount(1);

        template.request("direct:chat", e -> {
            e.getIn().setHeader("X-User-Id", "bob");
            e.getIn().setBody("Use the echo tool to echo 'Bob was here'.");
        });

        mockEndpoint.assertIsSatisfied();

        // Verify both users have separate histories
        assertNotNull(conversationStore.get("alice"), "Alice should have her own history");
        assertNotNull(conversationStore.get("bob"), "Bob should have his own history");

        // Bob asks a follow-up — should NOT know about Alice's calculation
        mockEndpoint.reset();
        mockEndpoint.expectedMessageCount(1);

        Exchange bobTurn2 = template.request("direct:chat", e -> {
            e.getIn().setHeader("X-User-Id", "bob");
            e.getIn().setBody("What tool did you just use and what was the message?");
        });

        mockEndpoint.assertIsSatisfied();
        String bobResponse = bobTurn2.getMessage().getBody(String.class);
        assertNotNull(bobResponse);
        // Bob should remember the echo, not Alice's add
        assertTrue(bobResponse.toLowerCase().contains("echo") || bobResponse.toLowerCase().contains("bob"),
                "Bob's follow-up should reference his echo, not Alice's add, but was: " + bobResponse);
    }
}
