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
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.watsonx.ai.chat.model.Tool;
import com.ibm.watsonx.ai.chat.model.ToolCall;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.ibm.watsonx.ai.WatsonxAiConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperties;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The chat options against watsonx.ai: structured output, guided decoding, stop sequences, several choices, per-request
 * model, reasoning settings, guardrails, and streaming with tools and a stream endpoint. The options are set as
 * endpoint URI options and plain headers, as YAML and XML routes set them.
 */
@EnabledIfSystemProperties({
        @EnabledIfSystemProperty(named = "camel.ibm.watsonx.ai.apiKey", matches = ".+",
                                 disabledReason = "IBM watsonx.ai API Key not provided"),
        @EnabledIfSystemProperty(named = "camel.ibm.watsonx.ai.projectId", matches = ".+",
                                 disabledReason = "IBM watsonx.ai Project ID not provided")
})
public class WatsonxAiChatOptionsIT extends WatsonxAiTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(WatsonxAiChatOptionsIT.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String CHAT_MODEL
            = System.getProperty("camel.ibm.watsonx.ai.chatModelId", "ibm/granite-4-h-small");
    // guided decoding depends on the runtime of the model: granite-4-h-small and llama-3-3-70b ignore it in eu-de
    private static final String GUIDED_MODEL = System.getProperty("camel.ibm.watsonx.ai.guidedModelId",
            "mistralai/mistral-small-3-1-24b-instruct-2503");
    private static final String SCHEMA
            = "{\"type\":\"object\",\"properties\":{\"country\":{\"type\":\"string\"},\"capital\":{\"type\":\"string\"}},"
              + "\"required\":[\"country\",\"capital\"]}";

    private Exchange chat(String options, Object body, Map<String, Object> headers) {
        Exchange result = template.send(buildEndpointUri("chat", CHAT_MODEL) + options, e -> {
            e.getIn().setBody(body);
            e.getIn().getHeaders().putAll(headers);
        });
        LOG.info("{} -> {} (stop reason {}){}", options, result.getMessage().getBody(),
                result.getMessage().getHeader(WatsonxAiConstants.STOP_REASON),
                result.getException() != null ? " failed: " + result.getException() : "");
        return result;
    }

    @Test
    void jsonSchema() throws Exception {
        Exchange result = chat("&jsonSchema=RAW(" + SCHEMA + ")", "What is the capital of France?", Map.of());

        assertNull(result.getException());
        JsonNode answer = MAPPER.readTree(result.getMessage().getBody(String.class));
        assertTrue(answer.get("capital").asText().contains("Paris"), answer.toString());
        assertTrue(answer.has("country"), answer.toString());
    }

    @Test
    void jsonSchemaHeaderAsMap() throws Exception {
        Map<String, Object> schema = MAPPER.readValue(SCHEMA, Map.class);
        Exchange result = chat("", "What is the capital of Italy?", Map.of(WatsonxAiConstants.JSON_SCHEMA, schema));

        assertNull(result.getException());
        JsonNode answer = MAPPER.readTree(result.getMessage().getBody(String.class));
        assertTrue(answer.get("capital").asText().contains("Rome"), answer.toString());
    }

    @Test
    void responseFormatJson() throws Exception {
        Exchange result = chat("&responseFormat=json",
                "Give the capital of France as a JSON object with the key capital.", Map.of());

        assertNull(result.getException());
        assertTrue(MAPPER.readTree(result.getMessage().getBody(String.class)).isObject());
    }

    @Test
    void guidedChoice() {
        Exchange result = chat("&guidedChoice=yes,no", "Is Paris the capital of France?",
                Map.of(WatsonxAiConstants.MODEL_ID, GUIDED_MODEL));

        assertNull(result.getException());
        assertTrue(List.of("yes", "no").contains(result.getMessage().getBody(String.class)));
        Exchange header = chat("", "Which is the capital of France?",
                Map.of(WatsonxAiConstants.MODEL_ID, GUIDED_MODEL,
                        WatsonxAiConstants.GUIDED_CHOICE, List.of("Lyon", "Paris", "Marseille")));
        assertTrue(List.of("Lyon", "Paris", "Marseille").contains(header.getMessage().getBody(String.class)));
    }

    @Test
    void guidedRegex() {
        Exchange result = chat("&guidedRegex=RAW([0-9]{4})", "In which year did the French revolution begin?",
                Map.of(WatsonxAiConstants.MODEL_ID, GUIDED_MODEL));

        assertNull(result.getException());
        assertTrue(result.getMessage().getBody(String.class).matches("[0-9]{4}"), result.getMessage().getBody(String.class));
    }

    @Test
    void stopAndSeed() {
        Exchange result = chat("&stop=five&seed=42&temperature=0",
                "Count from one to ten in words, separated by spaces, and nothing else.", Map.of());

        assertNull(result.getException());
        String answer = result.getMessage().getBody(String.class).toLowerCase();
        assertTrue(answer.contains("four") && !answer.contains("six"), answer);
    }

    @Test
    void severalChoices() {
        Exchange result = chat("&numberOfChoices=2&temperature=1", "Name a color.", Map.of());

        assertNull(result.getException());
        List<?> answers = result.getMessage().getHeader(WatsonxAiConstants.ASSISTANT_MESSAGES, List.class);
        assertEquals(2, answers.size());
    }

    @Test
    void perRequestModel() {
        Exchange other = chat("", "What is the capital of France? One word.",
                Map.of(WatsonxAiConstants.MODEL_ID, "meta-llama/llama-3-3-70b-instruct"));
        assertNull(other.getException());
        // a model that watsonx.ai does not have proves the header reaches the request
        Exchange missing = chat("", "What is the capital of France?",
                Map.of(WatsonxAiConstants.MODEL_ID, "ibm/no-such-model"));
        assertNotNull(missing.getException());
        assertTrue(missing.getException().getMessage().contains("no-such-model"), missing.getException().getMessage());
    }

    @Test
    void reasoningSettings() {
        // no provided model of eu-de reasons: watsonx.ai accepts the settings and answers
        Exchange result = chat("&thinking=true&includeReasoning=true&reasoningEffort=low",
                "What is the capital of France? One word.", Map.of());

        assertNull(result.getException());
        LOG.info("Reasoning: {}", result.getMessage().getHeader(WatsonxAiConstants.REASONING_CONTENT));
    }

    @Test
    void guardrails() {
        Exchange benign = chat("&moderationHap=true&moderationPii=true", "What is the capital of France? One word.",
                Map.of());
        assertNull(benign.getException());
        assertTrue(benign.getMessage().getBody(String.class).contains("Paris"));

        Exchange pii = chat("&moderationPii=true",
                "Repeat exactly this e-mail address and nothing else: john.smith@example.com", Map.of());
        LOG.info("PII guardrail: {} {}", pii.getMessage().getHeader(WatsonxAiConstants.MODERATION_RESULTS),
                pii.getException());
        assertTrue(pii.getException() != null
                || pii.getMessage().getHeader(WatsonxAiConstants.MODERATION_RESULTS) != null,
                "The PII guardrail should report the e-mail address");
    }

    @Test
    void streamingWithToolsAndStreamEndpoint() throws Exception {
        MockEndpoint chunks = getMockEndpoint("mock:chunks");
        chunks.expectedMinimumMessageCount(2);

        Exchange text = template.send(
                buildEndpointUri("chatStreaming", CHAT_MODEL) + "&streamEndpoint=mock:chunks",
                e -> e.getIn().setBody("Name the three largest cities of France."));
        assertNull(text.getException());
        chunks.assertIsSatisfied();
        LOG.info("Streamed {} chunks: {}", chunks.getReceivedCounter(), text.getMessage().getBody());

        Exchange tools = template.send(
                buildEndpointUri("chatStreaming", CHAT_MODEL) + "&streamEndpoint=mock:chunks&toolChoice=required",
                e -> {
                    e.getIn().setBody("What is the weather in Paris?");
                    e.getIn().setHeader(WatsonxAiConstants.TOOLS, List.of(Tool.of("get_weather", "The weather of a city",
                            Map.of("type", "object", "properties", Map.of("city", Map.of("type", "string")),
                                    "required", List.of("city")))));
                });
        assertNull(tools.getException());
        assertTrue(tools.getMessage().getHeader(WatsonxAiConstants.HAS_TOOL_CALLS, Boolean.class));
        ToolCall toolCall = (ToolCall) tools.getMessage().getHeader(WatsonxAiConstants.TOOL_CALLS, List.class).get(0);
        LOG.info("Streamed tool call: {}", toolCall);
        assertEquals("get_weather", toolCall.function().name());
        assertTrue(toolCall.function().arguments().contains("Paris"));
        assertFalse(tools.getMessage().getHeader(WatsonxAiConstants.MESSAGES, List.class).isEmpty());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:unused").to("mock:unused");
            }
        };
    }
}
