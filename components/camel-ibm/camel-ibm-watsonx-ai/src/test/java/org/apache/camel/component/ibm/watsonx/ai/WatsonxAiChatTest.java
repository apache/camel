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

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Chat and deploymentChat against a fake watsonx.ai: the failures and the content of a stream, the headers of an
 * earlier answer, literal stop sequences and the thinking of deploymentChat.
 */
class WatsonxAiChatTest extends CamelTestSupport {

    private static final String CHAT = "/ml/v1/text/chat";
    private static final String CHAT_STREAM = "/ml/v1/text/chat_stream";
    private static final String DEPLOYMENT_CHAT = "/ml/v1/deployments/my-deployment/text/chat";
    private static final String ANSWER = """
            {"id":"chat-1","object":"chat.completion","model_id":"model","model":"model","created":1,
             "created_at":"2026-01-01T00:00:00.000Z",
             "choices":[{"index":0,"message":{"role":"assistant","content":"Paris"},"finish_reason":"stop"}],
             "usage":{"completion_tokens":1,"prompt_tokens":2,"total_tokens":3}}""";

    private static FakeWatsonxServer server;

    @BeforeAll
    static void startServer() throws IOException {
        server = FakeWatsonxServer.start();
    }

    @AfterAll
    static void stopServer() {
        server.close();
    }

    @BeforeEach
    void resetServer() {
        server.reset();
        server.respond(CHAT, ANSWER).respond(DEPLOYMENT_CHAT, ANSWER);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                getContext().getRegistry().bind("auth", FakeWatsonxServer.TEST_AUTHENTICATOR);
                from("direct:failingChunks").throwException(new IllegalStateException("chunk refused"));
            }
        };
    }

    private String uri(String operation, String options) {
        return "ibm-watsonx-ai:test?authenticator=#auth&baseUrl=" + server.baseUrl()
               + "&projectId=my-project&modelId=model&deploymentId=my-deployment&operation=" + operation
               + (options.isEmpty() ? "" : "&" + options);
    }

    private Exchange send(String operation, String options, Object body, Map<String, Object> headers) {
        return template.send(uri(operation, options), e -> {
            e.getIn().setBody(body);
            e.getIn().getHeaders().putAll(headers);
        });
    }

    private static String event(int id, String choice) {
        return "id: " + id + "\nevent: message\ndata: {\"id\":\"chat-1\",\"object\":\"chat.completion.chunk\","
               + "\"model_id\":\"model\",\"model\":\"model\",\"created\":1,\"created_at\":\"2026-01-01T00:00:00.000Z\","
               + "\"choices\":[" + choice + "]}\n\n";
    }

    private static String delta(int index, String content, String finishReason) {
        return "{\"index\":" + index + ",\"finish_reason\":" + (finishReason == null ? "null" : "\"" + finishReason + "\"")
               + ",\"delta\":{\"content\":\"" + content + "\"}}";
    }

    @Test
    void chatRemovesTheStaleToolCallsOfAnEarlierAnswer() {
        Exchange result = send("chat", "", "What is the capital of France?",
                Map.of(WatsonxAiConstants.TOOL_CALLS, List.of("stale"), WatsonxAiConstants.REFUSAL, "stale"));

        assertNull(result.getException());
        assertFalse(result.getMessage().getHeaders().containsKey(WatsonxAiConstants.TOOL_CALLS));
        assertFalse(result.getMessage().getHeaders().containsKey(WatsonxAiConstants.REFUSAL));
    }

    @Test
    void streamingFailsOnAnErrorInTheStreamAndDeliversNoMoreChunks() {
        server.respondStream(CHAT_STREAM, event(1, delta(0, "Par", null))
                                          + "id: 2\nevent: error\ndata: {\"errors\":[{\"code\":\"boom\"}]}\n\n"
                                          + event(3, delta(0, "is", "stop")));
        List<String> chunks = new CopyOnWriteArrayList<>();
        Exchange result = send("chatStreaming", "", "What is the capital of France?",
                Map.of(WatsonxAiConstants.STREAM_CONSUMER, (Consumer<String>) chunks::add));

        assertNotNull(result.getException());
        assertTrue(result.getException().getMessage().contains("boom"), result.getException().toString());
        assertEquals(List.of("Par"), chunks);
    }

    @Test
    void streamingFailsWhenTheStreamEndpointFails() {
        server.respondStream(CHAT_STREAM, event(1, delta(0, "Par", null)) + event(2, delta(0, "is", "stop")));
        Exchange result = send("chatStreaming", "streamEndpoint=direct:failingChunks", "What is the capital of France?",
                Map.of());

        assertNotNull(result.getException());
        assertTrue(result.getException().getMessage().contains("chunk refused")
                || result.getException().getCause() != null
                        && result.getException().getCause().getMessage().contains("chunk refused"),
                result.getException().toString());
    }

    @Test
    void streamingKeepsTheWhitespaceAndTheFirstAnswerOnly() throws Exception {
        server.respondStream(CHAT_STREAM, event(1, delta(0, "  code", null)) + event(2, delta(1, "other", null))
                                          + event(3, delta(0, "\\n", "stop")) + event(4, delta(1, "!", "stop")));
        MockEndpoint chunks = getMockEndpoint("mock:chunks");
        chunks.expectedBodiesReceived("  code", "\n");
        Exchange result = send("chatStreaming", "streamEndpoint=mock:chunks&numberOfChoices=2",
                "Indent the word code.", Map.of());

        assertNull(result.getException());
        chunks.assertIsSatisfied();
        assertEquals("  code\n", result.getMessage().getBody());
        // the chunks carry the exchange that streams them
        assertEquals(result.getExchangeId(), chunks.getExchanges().get(0).getIn().getHeader(Exchange.CORRELATION_ID));
    }

    @Test
    void stopSequencesAreLiteral() {
        send("chat", "", "Write two paragraphs.", Map.of(WatsonxAiConstants.STOP, "\n\n, END"));

        JsonNode stop = server.lastRequest(CHAT).json().get("stop");
        assertEquals("\n\n", stop.get(0).asString());
        assertEquals(" END", stop.get(1).asString());
    }

    @Test
    void deploymentChatSendsNoThinkingWhenItIsOff() {
        send("deploymentChat", "thinking=false&includeReasoning=true", "What is the capital of France?", Map.of());

        JsonNode request = server.lastRequest(DEPLOYMENT_CHAT).json();
        assertFalse(request.has("chat_template_kwargs"), request::toString);
        assertFalse(request.has("include_reasoning"), request::toString);
    }
}
