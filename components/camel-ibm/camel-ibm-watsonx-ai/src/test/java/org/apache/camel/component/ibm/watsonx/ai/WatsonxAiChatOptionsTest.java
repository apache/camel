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
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.ibm.watsonx.ai.chat.model.AssistantMessage;
import com.ibm.watsonx.ai.chat.model.Tool;
import com.ibm.watsonx.ai.chat.model.ToolCall;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The chat options and headers reach the request of chat, chatStreaming and deploymentChat, and the answers reach the
 * headers: response format and JSON schema, guided decoding, seed, stop, number of choices, time limit, reasoning,
 * guardrails, per-request model, tools of a stream.
 */
class WatsonxAiChatOptionsTest extends CamelTestSupport {

    private static final String CHAT = "/ml/v1/text/chat";
    private static final String CHAT_STREAM = "/ml/v1/text/chat_stream";
    private static final String DEPLOYMENT_CHAT = "/ml/v1/deployments/my-deployment/text/chat";

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
        server.respond(CHAT, answer("{\"role\":\"assistant\",\"content\":\"Paris\"}", "stop"))
                .respond(DEPLOYMENT_CHAT, answer("{\"role\":\"assistant\",\"content\":\"Paris\"}", "stop"));
    }

    private static String answer(String message, String finishReason) {
        return """
                {"id":"chat-1","object":"chat.completion","model_id":"model","model":"model","created":1,
                 "created_at":"2026-01-01T00:00:00.000Z",
                 "choices":[{"index":0,"message":%s,"finish_reason":"%s"}],
                 "usage":{"completion_tokens":1,"prompt_tokens":2,"total_tokens":3}}""".formatted(message, finishReason);
    }

    private static String event(int id, String choice, String usage) {
        return "id: " + id + "\nevent: message\ndata: {\"id\":\"chat-1\",\"object\":\"chat.completion.chunk\","
               + "\"model_id\":\"model\",\"model\":\"model\",\"created\":1,\"created_at\":\"2026-01-01T00:00:00.000Z\","
               + "\"choices\":[" + choice + "]" + (usage == null ? "" : ",\"usage\":" + usage) + "}\n\n";
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                getContext().getRegistry().bind("auth", FakeWatsonxServer.TEST_AUTHENTICATOR);
            }
        };
    }

    private String uri(String operation, String options) {
        return "ibm-watsonx-ai:test?authenticator=#auth&baseUrl=" + server.baseUrl()
               + "&projectId=my-project&modelId=model&deploymentId=my-deployment&operation=" + operation
               + (options.isEmpty() ? "" : "&" + options);
    }

    private Exchange send(String operation, String options, Map<String, Object> headers) {
        Exchange result = template.send(uri(operation, options), e -> {
            e.getIn().setBody("What is the capital of France?");
            e.getIn().getHeaders().putAll(headers);
        });
        assertNull(result.getException(), () -> "Failed: " + result.getException());
        return result;
    }

    private JsonNode chat(String options, Map<String, Object> headers) {
        send("chat", options, headers);
        return server.lastRequest(CHAT).json();
    }

    @Test
    void responseFormatJson() {
        assertEquals("json_object", chat("responseFormat=json", Map.of()).get("response_format").get("type").asText());
        assertEquals("text", chat("responseFormat=json", Map.of(WatsonxAiConstants.RESPONSE_FORMAT, "text"))
                .get("response_format").get("type").asText());
        assertFalse(chat("", Map.of()).has("response_format"));
    }

    @Test
    void jsonSchemaOptionAndHeader() {
        JsonNode format = chat("jsonSchema=RAW({\"type\":\"object\",\"properties\":{\"city\":{\"type\":\"string\"}}})",
                Map.of()).get("response_format");
        assertEquals("json_schema", format.get("type").asText());
        assertEquals("string", format.get("json_schema").get("schema").get("properties").get("city").get("type").asText());
        assertTrue(format.get("json_schema").get("strict").asBoolean());

        format = chat("jsonSchemaStrict=false", Map.of(WatsonxAiConstants.JSON_SCHEMA,
                Map.of("type", "object", "properties", Map.of("country", Map.of("type", "string")))))
                .get("response_format");
        assertEquals("json_schema", format.get("type").asText());
        assertFalse(format.get("json_schema").get("strict").asBoolean());
        assertEquals("object", format.get("json_schema").get("schema").get("type").asText());
    }

    @Test
    void guidedDecodingSeedStopAndChoices() {
        JsonNode request = chat("guidedChoice=Paris, Rome&guidedRegex=RAW([A-Z][a-z]+)&guidedGrammar=root ::= \"yes\""
                                + "&seed=42&stop=END,STOP&numberOfChoices=2",
                Map.of());
        // the SDK keeps the choices in a set, in no particular order
        assertEquals(Set.of("Paris", "Rome"), Set.of(request.get("guided_choice").get(0).asText(),
                request.get("guided_choice").get(1).asText()));
        assertEquals("[A-Z][a-z]+", request.get("guided_regex").asText());
        assertEquals("root ::= \"yes\"", request.get("guided_grammar").asText());
        assertEquals(42, request.get("seed").asInt());
        assertEquals("END", request.get("stop").get(0).asText());
        assertEquals("STOP", request.get("stop").get(1).asText());
        assertEquals(2, request.get("n").asInt());
    }

    @Test
    void headersOverrideTheOptions() {
        JsonNode request = chat("seed=42&stop=END", Map.of(
                WatsonxAiConstants.SEED, 7,
                WatsonxAiConstants.STOP, List.of("\n\n"),
                WatsonxAiConstants.GUIDED_CHOICE, "yes,no",
                WatsonxAiConstants.MODEL_ID, "meta-llama/llama-3-3-70b-instruct"));
        assertEquals(7, request.get("seed").asInt());
        assertEquals("\n\n", request.get("stop").get(0).asText());
        assertEquals(2, request.get("guided_choice").size());
        assertEquals("meta-llama/llama-3-3-70b-instruct", request.get("model_id").asText());
    }

    @Test
    void reasoningRequestAndAnswer() {
        server.reset();
        server.respond(CHAT, answer(
                "{\"role\":\"assistant\",\"content\":\"Paris\",\"reasoning_content\":\"France's capital is Paris.\"}",
                "stop"));
        Exchange result = send("chat", "thinking=true&includeReasoning=true&reasoningEffort=high", Map.of());

        JsonNode request = server.lastRequest(CHAT).json();
        assertTrue(request.get("include_reasoning").asBoolean());
        assertEquals("high", request.get("reasoning_effort").asText());
        assertTrue(request.get("chat_template_kwargs").get("thinking").asBoolean());
        assertEquals("Paris", result.getMessage().getBody());
        assertEquals("France's capital is Paris.", result.getMessage().getHeader(WatsonxAiConstants.REASONING_CONTENT));
    }

    @Test
    void reasoningEffortHeader() {
        JsonNode request = chat("", Map.of(WatsonxAiConstants.REASONING_EFFORT, "low"));
        assertEquals("low", request.get("reasoning_effort").asText());
        assertFalse(chat("", Map.of()).has("reasoning_effort"));
    }

    @Test
    void thinkingTagsSplitTheAnswer() {
        server.reset();
        server.respond(CHAT, answer(
                "{\"role\":\"assistant\",\"content\":\"<think>The capital is Paris.</think><response>Paris</response>\"}",
                "stop"));
        Exchange result = send("chat", "thinkingTags=think,response", Map.of());

        assertEquals("Paris", result.getMessage().getBody());
        assertEquals("The capital is Paris.", result.getMessage().getHeader(WatsonxAiConstants.REASONING_CONTENT));
    }

    @Test
    void guardrails() {
        server.reset();
        server.respond(CHAT, """
                {"id":"chat-1","object":"chat.completion","model_id":"model","model":"model","created":1,
                 "created_at":"2026-01-01T00:00:00.000Z",
                 "choices":[{"index":0,"message":{"role":"assistant","content":"Paris"},"finish_reason":"stop"}],
                 "usage":{"completion_tokens":1,"prompt_tokens":2,"total_tokens":3},
                 "moderations":{"pii":[{"score":0.9,"input":true,"position":{"start":0,"end":5},
                   "entity":"EmailAddress","word":"a@b.c"}]}}""");
        Exchange result = send("chat", "moderationHap=true&moderationPii=true&moderationGraniteGuardian=true"
                                       + "&moderationThreshold=0.7",
                Map.of());

        JsonNode moderations = server.lastRequest(CHAT).json().get("moderations");
        assertTrue(moderations.get("hap").get("input").get("enabled").asBoolean());
        assertEquals(0.7, moderations.get("hap").get("input").get("threshold").asDouble(), 0.001);
        assertTrue(moderations.get("hap").get("output").get("enabled").asBoolean());
        assertTrue(moderations.get("pii").get("input").get("enabled").asBoolean());
        assertTrue(moderations.get("pii").get("output").get("enabled").asBoolean());
        assertEquals(0.7, moderations.get("granite_guardian").get("input").get("threshold").asDouble(), 0.001);
        Map<?, ?> results = assertInstanceOf(Map.class,
                result.getMessage().getHeader(WatsonxAiConstants.MODERATION_RESULTS));
        assertTrue(results.containsKey("pii"));
        assertFalse(chat("", Map.of()).has("moderations"));
    }

    @Test
    void severalChoicesAndARefusal() {
        server.reset();
        server.respond(CHAT, """
                {"id":"chat-1","object":"chat.completion","model_id":"model","model":"model","created":1,
                 "created_at":"2026-01-01T00:00:00.000Z",
                 "choices":[{"index":0,"message":{"role":"assistant","content":"Paris"},"finish_reason":"stop"},
                            {"index":1,"message":{"role":"assistant","refusal":"I cannot say"},"finish_reason":"stop"}],
                 "usage":{"completion_tokens":1,"prompt_tokens":2,"total_tokens":3}}""");
        Exchange result = send("chat", "numberOfChoices=2", Map.of());

        assertEquals("Paris", result.getMessage().getBody());
        List<?> answers = result.getMessage().getHeader(WatsonxAiConstants.ASSISTANT_MESSAGES, List.class);
        assertEquals(2, answers.size());
        assertEquals("I cannot say", ((AssistantMessage) answers.get(1)).refusal());
        assertNull(result.getMessage().getHeader(WatsonxAiConstants.REFUSAL));
    }

    @Test
    void deploymentChatSendsTheOptionsAndTools() {
        Exchange result = send("deploymentChat", "temperature=0.2&maxCompletionTokens=50&seed=1&toolChoice=required",
                Map.of(WatsonxAiConstants.TOOLS, List.of(Tool.of("capital", "The capital of a country"))));

        assertEquals("Paris", result.getMessage().getBody());
        JsonNode request = server.lastRequest(DEPLOYMENT_CHAT).json();
        assertEquals(0.2, request.get("temperature").asDouble(), 0.001);
        assertEquals(50, request.get("max_completion_tokens").asInt());
        assertEquals(1, request.get("seed").asInt());
        assertEquals("required", request.get("tool_choice_option").asText());
        assertEquals("capital", request.get("tools").get(0).get("function").get("name").asText());
        // the deployment gives the model
        assertFalse(request.has("model_id"));
    }

    @Test
    void chatStreamingSendsTheToolsAndGivesTheToolCalls() {
        server.reset();
        server.respondStream(CHAT_STREAM,
                event(1, "{\"index\":0,\"finish_reason\":null,\"delta\":{\"role\":\"assistant\",\"tool_calls\":"
                         + "[{\"index\":0,\"id\":\"call-1\",\"type\":\"function\",\"function\":{\"name\":\"capital\","
                         + "\"arguments\":\"{\\\"country\\\":\"}}]}}",
                        null)
                                          + event(2, "{\"index\":0,\"finish_reason\":null,\"delta\":{\"tool_calls\":"
                                                     + "[{\"index\":0,\"function\":{\"arguments\":\"\\\"France\\\"}\"}}]}}",
                                                  null)
                                          + event(3, "{\"index\":0,\"finish_reason\":\"tool_calls\",\"delta\":{}}",
                                                  "{\"completion_tokens\":1,\"prompt_tokens\":2,\"total_tokens\":3}"));
        List<String> chunks = new CopyOnWriteArrayList<>();
        Exchange result = send("chatStreaming", "", Map.of(
                WatsonxAiConstants.TOOLS, List.of(Tool.of("capital", "The capital of a country")),
                WatsonxAiConstants.STREAM_CONSUMER, (Consumer<String>) chunks::add));

        JsonNode request = server.lastRequest(CHAT_STREAM).json();
        assertEquals("capital", request.get("tools").get(0).get("function").get("name").asText());
        assertTrue(result.getMessage().getHeader(WatsonxAiConstants.HAS_TOOL_CALLS, Boolean.class));
        List<?> toolCalls = result.getMessage().getHeader(WatsonxAiConstants.TOOL_CALLS, List.class);
        ToolCall toolCall = (ToolCall) toolCalls.get(0);
        assertEquals("capital", toolCall.function().name());
        assertEquals("{\"country\":\"France\"}", toolCall.function().arguments());
        // processToolCalls can go on from the conversation
        assertEquals(1, result.getMessage().getHeader(WatsonxAiConstants.MESSAGES, List.class).size());
        assertEquals("tool_calls", result.getMessage().getHeader(WatsonxAiConstants.STOP_REASON));
        assertTrue(chunks.isEmpty());
    }

    @Test
    void chatStreamingGivesTheReasoningAndSendsTheChunksToTheStreamEndpoint() throws Exception {
        server.reset();
        server.respondStream(CHAT_STREAM,
                event(1, "{\"index\":0,\"finish_reason\":null,\"delta\":{\"role\":\"assistant\","
                         + "\"reasoning_content\":\"The capital \"}}",
                        null)
                                          + event(2, "{\"index\":0,\"finish_reason\":null,\"delta\":{"
                                                     + "\"reasoning_content\":\"is Paris.\"}}",
                                                  null)
                                          + event(3, "{\"index\":0,\"finish_reason\":null,\"delta\":{\"content\":\"Par\"}}",
                                                  null)
                                          + event(4, "{\"index\":0,\"finish_reason\":\"stop\",\"delta\":{\"content\":\"is\"}}",
                                                  "{\"completion_tokens\":2,\"prompt_tokens\":2,\"total_tokens\":4}"));
        MockEndpoint chunks = getMockEndpoint("mock:chunks");
        chunks.expectedBodiesReceived("Par", "is");

        Exchange result = send("chatStreaming", "streamEndpoint=mock:chunks&includeReasoning=true", Map.of());

        chunks.assertIsSatisfied();
        assertEquals("Paris", result.getMessage().getBody());
        assertEquals("The capital is Paris.", result.getMessage().getHeader(WatsonxAiConstants.REASONING_CONTENT));
        assertEquals(2, result.getMessage().getHeader(WatsonxAiConstants.OUTPUT_TOKEN_COUNT));
        assertTrue(server.lastRequest(CHAT_STREAM).json().get("include_reasoning").asBoolean());
    }

    @Test
    void chatStreamingFailsWithTheErrorOfWatsonx() {
        server.reset();
        server.respond(CHAT_STREAM, 400, "application/json", """
                {"errors":[{"code":"model_not_supported","message":"Model 'x' is not supported"}],
                 "trace":"t","status_code":400}""");
        Exchange result = template.send(uri("chatStreaming", ""), e -> {
            e.getIn().setBody("What is the capital of France?");
            e.getIn().setHeader(WatsonxAiConstants.STREAM_CONSUMER, (Consumer<String>) chunk -> {
            });
        });

        // the error of the SDK itself, not an ExecutionException wrapping it
        assertTrue(result.getException().getMessage().contains("model_not_supported"),
                result.getException().toString());
        assertFalse(result.getException() instanceof ExecutionException);
    }
}
