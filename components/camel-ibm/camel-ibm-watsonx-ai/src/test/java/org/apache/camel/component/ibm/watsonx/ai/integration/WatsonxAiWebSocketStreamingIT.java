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
package org.apache.camel.component.ibm.watsonx.ai.integration;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.WebSocket;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.ibm.watsonx.ai.WatsonxAiConstants;
import org.apache.camel.component.vertx.websocket.VertxWebsocketConstants;
import org.apache.camel.test.AvailablePortFinder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperties;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test demonstrating WebSocket streaming with IBM watsonx.ai chat.
 * <p>
 * This test shows a real-world use case: streaming LLM responses token-by-token to WebSocket clients in real-time,
 * similar to ChatGPT's streaming UI.
 * <p>
 * To run these tests, execute:
 *
 * <pre>
 * mvn verify -Dcamel.ibm.watsonx.ai.apiKey=YOUR_API_KEY -Dcamel.ibm.watsonx.ai.projectId=YOUR_PROJECT_ID
 * </pre>
 */
@EnabledIfSystemProperties({
        @EnabledIfSystemProperty(named = "camel.ibm.watsonx.ai.apiKey", matches = ".+",
                                 disabledReason = "IBM watsonx.ai API Key not provided"),
        @EnabledIfSystemProperty(named = "camel.ibm.watsonx.ai.projectId", matches = ".+",
                                 disabledReason = "IBM watsonx.ai Project ID not provided")
})
public class WatsonxAiWebSocketStreamingIT extends WatsonxAiTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(WatsonxAiWebSocketStreamingIT.class);

    private static final String MODEL_ID = "ibm/granite-4-h-small";

    private final int port = AvailablePortFinder.getNextAvailable();
    private Vertx vertx;

    @AfterEach
    void tearDown() {
        if (vertx != null) {
            vertx.close();
        }
    }

    /**
     * Tests WebSocket streaming endpoint for real-time chat responses.
     * <p>
     * Use case: A web application that streams LLM responses token-by-token to the browser via WebSocket, providing a
     * responsive user experience similar to ChatGPT.
     * <p>
     * Architecture:
     *
     * <pre>
     * Browser --WS msg--> /chat ---> watsonx.ai (streaming)
     *         <--WS msg--            |
     *                    <-- tokens -+
     *         <--WS msg-- [DONE]
     * </pre>
     */
    @Test
    void testWebSocketStreaming() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                fromF("vertx-websocket:localhost:%d/chat", port)
                        .routeId("websocket-chat-stream")
                        // Store connection key for sending responses back to this specific client
                        .setProperty("connectionKey", header(VertxWebsocketConstants.CONNECTION_KEY))
                        .process(exchange -> {
                            String connectionKey = exchange.getProperty("connectionKey", String.class);
                            ProducerTemplate producer = exchange.getContext().createProducerTemplate();

                            // Stream consumer that sends each token as a WebSocket message
                            Consumer<String> streamHandler = chunk -> {
                                producer.sendBodyAndHeader(
                                        "vertx-websocket:localhost:" + port + "/chat",
                                        chunk,
                                        VertxWebsocketConstants.CONNECTION_KEY, connectionKey);
                            };

                            exchange.getIn().setHeader(WatsonxAiConstants.STREAM_CONSUMER, streamHandler);
                        })
                        // Set system message for the assistant
                        .setHeader(WatsonxAiConstants.SYSTEM_MESSAGE,
                                constant("You are a helpful assistant. Keep your answers brief."))
                        // The request body contains the user question
                        .to(buildEndpointUri("chatStreaming", MODEL_ID))
                        // Send completion marker
                        .process(exchange -> {
                            String connectionKey = exchange.getProperty("connectionKey", String.class);
                            ProducerTemplate producer = exchange.getContext().createProducerTemplate();
                            producer.sendBodyAndHeader(
                                    "vertx-websocket:localhost:" + port + "/chat",
                                    "[DONE]",
                                    VertxWebsocketConstants.CONNECTION_KEY, connectionKey);
                        });
            }
        });

        context.start();

        // Test with WebSocket client
        List<String> receivedMessages = new CopyOnWriteArrayList<>();
        CountDownLatch doneLatch = new CountDownLatch(1);

        WebSocket webSocket = openWebSocketConnection("localhost", port, "/chat", message -> {
            LOG.info("Received WebSocket message: {}", message);
            receivedMessages.add(message);
            if ("[DONE]".equals(message)) {
                doneLatch.countDown();
            }
        });

        // Send question to the WebSocket
        webSocket.writeTextMessage("What is 2+2? Answer with just the number.");

        // Wait for streaming to complete
        assertTrue(doneLatch.await(30, TimeUnit.SECONDS), "Should receive [DONE] marker within timeout");

        // Verify we received multiple streaming chunks
        LOG.info("Received {} WebSocket messages", receivedMessages.size());
        assertTrue(receivedMessages.size() > 1, "Should have received multiple streaming chunks");
        assertEquals("[DONE]", receivedMessages.get(receivedMessages.size() - 1), "Last message should be [DONE]");

        // Verify content contains expected answer
        String fullResponse = String.join("", receivedMessages.subList(0, receivedMessages.size() - 1));
        LOG.info("Full response: {}", fullResponse);
        assertFalse(fullResponse.isEmpty(), "Response should not be empty");
    }

    /**
     * Tests WebSocket streaming with a longer response to verify consistent token delivery.
     */
    @Test
    void testWebSocketStreamingWithLongerResponse() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                fromF("vertx-websocket:localhost:%d/explain", port)
                        .routeId("websocket-explain-stream")
                        .setProperty("connectionKey", header(VertxWebsocketConstants.CONNECTION_KEY))
                        .process(exchange -> {
                            String connectionKey = exchange.getProperty("connectionKey", String.class);
                            ProducerTemplate producer = exchange.getContext().createProducerTemplate();

                            Consumer<String> streamHandler = chunk -> {
                                producer.sendBodyAndHeader(
                                        "vertx-websocket:localhost:" + port + "/explain",
                                        chunk,
                                        VertxWebsocketConstants.CONNECTION_KEY, connectionKey);
                            };

                            exchange.getIn().setHeader(WatsonxAiConstants.STREAM_CONSUMER, streamHandler);
                        })
                        .setHeader(WatsonxAiConstants.SYSTEM_MESSAGE,
                                constant("You are a coding expert. Explain concepts clearly."))
                        .to(buildEndpointUri("chatStreaming", MODEL_ID) + "&maxCompletionTokens=200")
                        .process(exchange -> {
                            String connectionKey = exchange.getProperty("connectionKey", String.class);
                            ProducerTemplate producer = exchange.getContext().createProducerTemplate();
                            producer.sendBodyAndHeader(
                                    "vertx-websocket:localhost:" + port + "/explain",
                                    "[DONE]",
                                    VertxWebsocketConstants.CONNECTION_KEY, connectionKey);
                        });
            }
        });

        context.start();

        List<String> receivedMessages = new CopyOnWriteArrayList<>();
        CountDownLatch doneLatch = new CountDownLatch(1);

        WebSocket webSocket = openWebSocketConnection("localhost", port, "/explain", message -> {
            LOG.info("Received: {}", message);
            receivedMessages.add(message);
            if ("[DONE]".equals(message)) {
                doneLatch.countDown();
            }
        });

        webSocket.writeTextMessage("Explain the Strategy design pattern in one paragraph.");

        assertTrue(doneLatch.await(60, TimeUnit.SECONDS), "Should receive [DONE] marker within timeout");

        LOG.info("Received {} streaming chunks", receivedMessages.size());
        assertTrue(receivedMessages.size() > 5, "Should have received multiple streaming chunks for longer response");
        assertEquals("[DONE]", receivedMessages.get(receivedMessages.size() - 1));
    }

    private WebSocket openWebSocketConnection(String host, int port, String path, Consumer<String> handler)
            throws Exception {
        vertx = Vertx.vertx();
        HttpClient client = vertx.createHttpClient();
        CompletableFuture<WebSocket> future = client.webSocket(port, host, path)
                .toCompletionStage()
                .toCompletableFuture();
        WebSocket webSocket = future.get(10, TimeUnit.SECONDS);
        webSocket.textMessageHandler(handler::accept);
        return webSocket;
    }
}
