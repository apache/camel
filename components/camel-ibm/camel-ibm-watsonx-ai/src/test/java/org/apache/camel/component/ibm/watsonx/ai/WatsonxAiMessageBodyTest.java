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
package org.apache.camel.component.ibm.watsonx.ai;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.watsonx.ai.chat.ChatService;
import com.ibm.watsonx.ai.chat.model.SystemMessage;
import com.ibm.watsonx.ai.chat.model.UserMessage;
import com.ibm.watsonx.ai.core.auth.Authenticator;
import com.ibm.watsonx.ai.deployment.DeploymentService;
import com.ibm.watsonx.ai.embedding.EmbeddingService;
import com.ibm.watsonx.ai.rerank.RerankService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The message bodies chat, chatStreaming, deploymentChat, embedding and rerank take, sent with the watsonx.ai SDK to a
 * JDK HttpServer that plays watsonx.ai and records the requests.
 */
class WatsonxAiMessageBodyTest extends CamelTestSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String CHAT_RESPONSE = """
            {"id":"chat-1","object":"chat.completion","model_id":"model","model":"model","created":1,
             "created_at":"2026-01-01T00:00:00.000Z",
             "choices":[{"index":0,"message":{"role":"assistant","content":"Paris"},"finish_reason":"stop"}],
             "usage":{"completion_tokens":1,"prompt_tokens":2,"total_tokens":3}}""";

    private static final String CHAT_STREAM_RESPONSE = """
            id: 1
            event: message
            data: {"id":"chat-1","object":"chat.completion.chunk","model_id":"model","model":"model","created":1,\
            "created_at":"2026-01-01T00:00:00.000Z","choices":[{"index":0,"finish_reason":null,\
            "delta":{"role":"assistant","content":"Paris"}}]}

            id: 2
            event: message
            data: {"id":"chat-1","object":"chat.completion.chunk","model_id":"model","model":"model","created":1,\
            "created_at":"2026-01-01T00:00:00.000Z","choices":[{"index":0,"finish_reason":"stop",\
            "delta":{"content":""}}],"usage":{"completion_tokens":1,"prompt_tokens":2,"total_tokens":3}}

            """;

    private static final String EMBEDDING_RESPONSE = """
            {"model_id":"model","created_at":"2026-01-01T00:00:00.000Z","results":[{"embedding":[0.5,0.25]}],
             "input_token_count":2}""";

    private static final String RERANK_RESPONSE = """
            {"model_id":"model","created_at":"2026-01-01T00:00:00.000Z","results":[{"index":0,"score":0.75}],
             "input_token_count":2}""";

    private static final Authenticator TEST_AUTHENTICATOR = new Authenticator() {
        @Override
        public String token() {
            return "test-token";
        }

        @Override
        public CompletableFuture<String> tokenAsync() {
            return CompletableFuture.completedFuture(token());
        }

        @Override
        public String scheme() {
            return "Bearer";
        }
    };

    private static final Map<String, String> REQUESTS = new ConcurrentHashMap<>();
    private static HttpServer server;

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", WatsonxAiMessageBodyTest::handle);
        server.start();
    }

    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private static void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        REQUESTS.put(path, new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));

        String contentType = "application/json";
        String response;
        if (path.equals("/ml/v1/text/chat") || path.equals("/ml/v1/deployments/my-deployment/text/chat")) {
            response = CHAT_RESPONSE;
        } else if (path.equals("/ml/v1/text/chat_stream")) {
            contentType = "text/event-stream";
            response = CHAT_STREAM_RESPONSE;
        } else if (path.equals("/ml/v1/text/embeddings")) {
            response = EMBEDDING_RESPONSE;
        } else if (path.equals("/ml/v1/text/rerank")) {
            response = RERANK_RESPONSE;
        } else {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
            return;
        }
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", contentType);
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    @BeforeEach
    void clearRequests() {
        REQUESTS.clear();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        // keep a stream body as it is sent
        context.setStreamCaching(false);
        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                WatsonxAiConfiguration configuration = new WatsonxAiConfiguration();
                configuration.setApiKey("test-key");
                configuration.setBaseUrl("http://localhost:" + server.getAddress().getPort());
                configuration.setProjectId("my-project");
                configuration.setModelId("model");
                configuration.setDeploymentId("my-deployment");
                WatsonxAiComponent component = getContext().getComponent("ibm-watsonx-ai", WatsonxAiComponent.class);

                from("direct:watsonx")
                        .to(new TestServerEndpoint("ibm-watsonx-ai:test", component, configuration));
            }
        };
    }

    // chat, chatStreaming and deploymentChat: a List body

    @Test
    void chatTakesAListOfStringsAsUserMessages() throws Exception {
        Exchange result = call(WatsonxAiOperations.chat, List.of("Hello", "What is the capital of France?"));

        assertEquals("Paris", result.getMessage().getBody());
        JsonNode messages = request("/ml/v1/text/chat").get("messages");
        assertEquals(2, messages.size());
        assertUserMessage("Hello", messages.get(0));
        assertUserMessage("What is the capital of France?", messages.get(1));
    }

    @Test
    void chatTakesAListOfChatMessagesAsTheyAre() throws Exception {
        Exchange result = call(WatsonxAiOperations.chat,
                List.of(SystemMessage.of("Answer in one word"), UserMessage.text("What is the capital of France?")));

        assertEquals("Paris", result.getMessage().getBody());
        JsonNode messages = request("/ml/v1/text/chat").get("messages");
        assertEquals(2, messages.size());
        assertEquals("system", messages.get(0).get("role").asText());
        assertEquals("Answer in one word", messages.get(0).get("content").asText());
        assertUserMessage("What is the capital of France?", messages.get(1));
    }

    @Test
    void chatTakesAListMixingChatMessagesAndStrings() throws Exception {
        Exchange result = call(WatsonxAiOperations.chat,
                List.of(SystemMessage.of("Answer in one word"), "What is the capital of France?"));

        assertEquals("Paris", result.getMessage().getBody());
        JsonNode messages = request("/ml/v1/text/chat").get("messages");
        assertEquals(2, messages.size());
        assertEquals("system", messages.get(0).get("role").asText());
        assertUserMessage("What is the capital of France?", messages.get(1));
    }

    @Test
    void chatFailsForAListWithAnotherElement() {
        List<Object> body = new ArrayList<>();
        body.add("What is the capital of France?");
        body.add(42);

        Exchange result = send(WatsonxAiOperations.chat, body);

        IllegalArgumentException e = assertInstanceOf(IllegalArgumentException.class, result.getException());
        assertTrue(e.getMessage().contains("must be ChatMessage objects or Strings"), e.getMessage());
        assertTrue(e.getMessage().contains("java.lang.Integer"), e.getMessage());
        assertFalse(REQUESTS.containsKey("/ml/v1/text/chat"), "no request is sent");
    }

    @Test
    void chatTakesStringsInTheMessagesHeader() throws Exception {
        Exchange result = template.send("direct:watsonx", exchange -> {
            exchange.getIn().setHeader(WatsonxAiConstants.OPERATION, WatsonxAiOperations.chat);
            exchange.getIn().setHeader(WatsonxAiConstants.MESSAGES, List.of("What is the capital of France?"));
        });

        assertNoException(result);
        assertEquals("Paris", result.getMessage().getBody());
        JsonNode messages = request("/ml/v1/text/chat").get("messages");
        assertEquals(1, messages.size());
        assertUserMessage("What is the capital of France?", messages.get(0));
    }

    @Test
    void chatStreamingTakesAListOfStringsAsUserMessages() throws Exception {
        List<String> chunks = new CopyOnWriteArrayList<>();
        Consumer<String> streamConsumer = chunks::add;
        Exchange result = template.send("direct:watsonx", exchange -> {
            exchange.getIn().setHeader(WatsonxAiConstants.OPERATION, WatsonxAiOperations.chatStreaming);
            exchange.getIn().setHeader(WatsonxAiConstants.STREAM_CONSUMER, streamConsumer);
            exchange.getIn().setBody(List.of("What is the capital of France?"));
        });

        assertNoException(result);
        assertEquals("Paris", result.getMessage().getBody());
        assertEquals(List.of("Paris"), chunks);
        JsonNode messages = request("/ml/v1/text/chat_stream").get("messages");
        assertEquals(1, messages.size());
        assertUserMessage("What is the capital of France?", messages.get(0));
    }

    @Test
    void deploymentChatTakesAListOfStringsAsUserMessages() throws Exception {
        Exchange result = call(WatsonxAiOperations.deploymentChat, List.of("What is the capital of France?"));

        assertEquals("Paris", result.getMessage().getBody());
        JsonNode messages = request("/ml/v1/deployments/my-deployment/text/chat").get("messages");
        assertEquals(1, messages.size());
        assertUserMessage("What is the capital of France?", messages.get(0));
    }

    // chat, chatStreaming and deploymentChat: other bodies

    @Test
    void chatReadsABytesBodyAsTheUserMessage() throws Exception {
        Exchange result = call(WatsonxAiOperations.chat,
                "What is the capital of France?".getBytes(StandardCharsets.UTF_8));

        assertEquals("Paris", result.getMessage().getBody());
        JsonNode messages = request("/ml/v1/text/chat").get("messages");
        assertEquals(1, messages.size());
        assertUserMessage("What is the capital of France?", messages.get(0));
    }

    @Test
    void chatReadsAStreamBodyAsTheUserMessage() throws Exception {
        Exchange result = call(WatsonxAiOperations.chat,
                new ByteArrayInputStream("What is the capital of France?".getBytes(StandardCharsets.UTF_8)));

        assertEquals("Paris", result.getMessage().getBody());
        JsonNode messages = request("/ml/v1/text/chat").get("messages");
        assertEquals(1, messages.size());
        assertUserMessage("What is the capital of France?", messages.get(0));
    }

    @Test
    void chatAddsTheSystemMessageHeaderToABytesBody() throws Exception {
        Exchange result = template.send("direct:watsonx", exchange -> {
            exchange.getIn().setHeader(WatsonxAiConstants.OPERATION, WatsonxAiOperations.chat);
            exchange.getIn().setHeader(WatsonxAiConstants.SYSTEM_MESSAGE, "Answer in one word");
            exchange.getIn().setBody("What is the capital of France?".getBytes(StandardCharsets.UTF_8));
        });

        assertNoException(result);
        JsonNode messages = request("/ml/v1/text/chat").get("messages");
        assertEquals(2, messages.size());
        assertEquals("Answer in one word", messages.get(0).get("content").asText());
        assertUserMessage("What is the capital of France?", messages.get(1));
    }

    @Test
    void chatTakesAChatMessageBody() throws Exception {
        Exchange result = call(WatsonxAiOperations.chat, UserMessage.text("What is the capital of France?"));

        assertEquals("Paris", result.getMessage().getBody());
        JsonNode messages = request("/ml/v1/text/chat").get("messages");
        assertEquals(1, messages.size());
        assertUserMessage("What is the capital of France?", messages.get(0));
    }

    @Test
    void chatStreamingReadsABytesBodyAsTheUserMessage() throws Exception {
        Consumer<String> streamConsumer = chunk -> {
        };
        Exchange result = template.send("direct:watsonx", exchange -> {
            exchange.getIn().setHeader(WatsonxAiConstants.OPERATION, WatsonxAiOperations.chatStreaming);
            exchange.getIn().setHeader(WatsonxAiConstants.STREAM_CONSUMER, streamConsumer);
            exchange.getIn().setBody("What is the capital of France?".getBytes(StandardCharsets.UTF_8));
        });

        assertNoException(result);
        assertEquals("Paris", result.getMessage().getBody());
        JsonNode messages = request("/ml/v1/text/chat_stream").get("messages");
        assertUserMessage("What is the capital of France?", messages.get(0));
    }

    @Test
    void deploymentChatReadsAStreamBodyAsTheUserMessage() throws Exception {
        Exchange result = call(WatsonxAiOperations.deploymentChat,
                new ByteArrayInputStream("What is the capital of France?".getBytes(StandardCharsets.UTF_8)));

        assertEquals("Paris", result.getMessage().getBody());
        JsonNode messages = request("/ml/v1/deployments/my-deployment/text/chat").get("messages");
        assertUserMessage("What is the capital of France?", messages.get(0));
    }

    @Test
    void chatTakesAStringBody() throws Exception {
        Exchange result = call(WatsonxAiOperations.chat, "What is the capital of France?");

        assertEquals("Paris", result.getMessage().getBody());
        assertUserMessage("What is the capital of France?", request("/ml/v1/text/chat").get("messages").get(0));
    }

    @Test
    void chatFailsWithoutABody() {
        Exchange result = send(WatsonxAiOperations.chat, null);

        IllegalArgumentException e = assertInstanceOf(IllegalArgumentException.class, result.getException());
        assertTrue(e.getMessage().startsWith("Chat messages must be provided"), e.getMessage());
    }

    // embedding and rerank

    @Test
    void embeddingReadsABytesBodyAsOneInput() throws Exception {
        Exchange result = call(WatsonxAiOperations.embedding, "Apache Camel".getBytes(StandardCharsets.UTF_8));

        assertEquals(List.of(List.of(0.5f, 0.25f)), result.getMessage().getBody());
        assertInputs(request("/ml/v1/text/embeddings").get("inputs"), "Apache Camel");
    }

    @Test
    void embeddingReadsAStreamBodyAsOneInput() throws Exception {
        Exchange result = call(WatsonxAiOperations.embedding,
                new ByteArrayInputStream("Apache Camel".getBytes(StandardCharsets.UTF_8)));

        assertEquals(List.of(List.of(0.5f, 0.25f)), result.getMessage().getBody());
        assertInputs(request("/ml/v1/text/embeddings").get("inputs"), "Apache Camel");
    }

    @Test
    void embeddingTakesAStringOrAListOfStrings() throws Exception {
        call(WatsonxAiOperations.embedding, "Apache Camel");
        assertInputs(request("/ml/v1/text/embeddings").get("inputs"), "Apache Camel");

        call(WatsonxAiOperations.embedding, List.of("Apache Camel", "Integration"));
        assertInputs(request("/ml/v1/text/embeddings").get("inputs"), "Apache Camel", "Integration");
    }

    @Test
    void embeddingFailsWithoutABody() {
        Exchange result = send(WatsonxAiOperations.embedding, null);

        IllegalArgumentException e = assertInstanceOf(IllegalArgumentException.class, result.getException());
        assertTrue(e.getMessage().startsWith("Input texts must be provided"), e.getMessage());
    }

    @Test
    void rerankReadsABytesBodyAsOneInput() throws Exception {
        Exchange result = rerank("Apache Camel is an integration framework".getBytes(StandardCharsets.UTF_8));

        assertNoException(result);
        JsonNode request = request("/ml/v1/text/rerank");
        assertEquals("What is Apache Camel?", request.get("query").asText());
        assertEquals(1, request.get("inputs").size());
        assertEquals("Apache Camel is an integration framework", request.get("inputs").get(0).get("text").asText());
    }

    @Test
    void rerankReadsAStreamBodyAsOneInput() throws Exception {
        Exchange result = rerank(
                new ByteArrayInputStream("Apache Camel is an integration framework".getBytes(StandardCharsets.UTF_8)));

        assertNoException(result);
        JsonNode request = request("/ml/v1/text/rerank");
        assertEquals(1, request.get("inputs").size());
        assertEquals("Apache Camel is an integration framework", request.get("inputs").get(0).get("text").asText());
    }

    private Exchange send(WatsonxAiOperations operation, Object body) {
        return template.send("direct:watsonx", exchange -> {
            exchange.getIn().setHeader(WatsonxAiConstants.OPERATION, operation);
            exchange.getIn().setBody(body);
        });
    }

    private Exchange call(WatsonxAiOperations operation, Object body) {
        Exchange result = send(operation, body);
        assertNoException(result);
        return result;
    }

    private Exchange rerank(Object body) {
        return template.send("direct:watsonx", exchange -> {
            exchange.getIn().setHeader(WatsonxAiConstants.OPERATION, WatsonxAiOperations.rerank);
            exchange.getIn().setHeader(WatsonxAiConstants.RERANK_QUERY, "What is Apache Camel?");
            exchange.getIn().setBody(body);
        });
    }

    private static JsonNode request(String path) throws IOException {
        String body = REQUESTS.get(path);
        assertNotNull(body, "no request to " + path + " in " + REQUESTS.keySet());
        return MAPPER.readTree(body);
    }

    private static void assertNoException(Exchange result) {
        if (result.getException() != null) {
            throw new AssertionError("the exchange failed: " + result.getException(), result.getException());
        }
    }

    private static void assertUserMessage(String expected, JsonNode message) {
        assertEquals("user", message.get("role").asText(), message.toString());
        assertEquals(expected, message.get("content").get(0).get("text").asText(), message.toString());
    }

    private static void assertInputs(JsonNode inputs, String... expected) {
        List<String> actual = new ArrayList<>();
        inputs.forEach(input -> actual.add(input.asText()));
        assertEquals(List.of(expected), actual);
    }

    /**
     * Builds the SDK services against the test server with a fixed token, instead of exchanging the API key with IBM
     * Cloud IAM.
     */
    private static final class TestServerEndpoint extends WatsonxAiEndpoint {

        TestServerEndpoint(String uri, WatsonxAiComponent component, WatsonxAiConfiguration configuration) {
            super(uri, component, configuration);
        }

        @Override
        public synchronized ChatService getChatService() {
            return ChatService.builder()
                    .baseUrl(getConfiguration().getBaseUrl())
                    .authenticator(TEST_AUTHENTICATOR)
                    .projectId(getConfiguration().getProjectId())
                    .modelId(getConfiguration().getModelId())
                    .build();
        }

        @Override
        public synchronized EmbeddingService getEmbeddingService() {
            return EmbeddingService.builder()
                    .baseUrl(getConfiguration().getBaseUrl())
                    .authenticator(TEST_AUTHENTICATOR)
                    .projectId(getConfiguration().getProjectId())
                    .modelId(getConfiguration().getModelId())
                    .build();
        }

        @Override
        public synchronized RerankService getRerankService() {
            return RerankService.builder()
                    .baseUrl(getConfiguration().getBaseUrl())
                    .authenticator(TEST_AUTHENTICATOR)
                    .projectId(getConfiguration().getProjectId())
                    .modelId(getConfiguration().getModelId())
                    .build();
        }

        @Override
        public synchronized DeploymentService getDeploymentService() {
            return DeploymentService.builder()
                    .baseUrl(getConfiguration().getBaseUrl())
                    .authenticator(TEST_AUTHENTICATOR)
                    .build();
        }
    }
}
