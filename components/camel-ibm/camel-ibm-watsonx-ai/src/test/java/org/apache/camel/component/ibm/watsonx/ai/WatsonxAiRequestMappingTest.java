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
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How the options and headers of the endpoint map to the requests the watsonx.ai SDK sends, captured by a fake
 * watsonx.ai server.
 */
class WatsonxAiRequestMappingTest extends CamelTestSupport {

    static final String CHAT = "/ml/v1/text/chat";
    static final String CHAT_RESPONSE = """
            {"id":"chat-1","object":"chat.completion","model_id":"model","model":"model","created":1,
             "created_at":"2026-01-01T00:00:00.000Z",
             "choices":[{"index":0,"message":{"role":"assistant","content":"Paris"},"finish_reason":"stop"}],
             "usage":{"completion_tokens":1,"prompt_tokens":2,"total_tokens":3}}""";
    static final String MODELS = "/ml/v1/foundation_model_specs";
    static final String MODELS_RESPONSE = """
            {"total_count":1,"limit":100,"first":{"href":"https://x/ml/v1/foundation_model_specs?limit=100"},
             "resources":[{"model_id":"ibm/granite-4-h-small","label":"granite-4-h-small","provider":"IBM"}]}""";
    static final String DETECTION = "/ml/v1/text/detection";
    static final String DEPLOYMENT = "/ml/v4/deployments/my-deployment";

    static FakeWatsonxServer server;

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
        server.respond(CHAT, CHAT_RESPONSE).respond(MODELS, MODELS_RESPONSE);
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

    String uri(String operation, String options) {
        return "ibm-watsonx-ai:test?authenticator=#auth&baseUrl=" + server.baseUrl()
               + "&projectId=my-project&modelId=model&operation=" + operation + (options.isEmpty() ? "" : "&" + options);
    }

    Exchange send(String uri, Object body, Map<String, Object> headers) {
        Exchange result = template.send(uri, e -> {
            e.getIn().setBody(body);
            e.getIn().setHeaders(headers);
        });
        assertNull(result.getException(), () -> "Failed: " + result.getException());
        return result;
    }

    JsonNode chatRequest(String options, Map<String, Object> headers) {
        send(uri("chat", options), "What is the capital of France?", headers);
        return server.lastRequest(CHAT).json();
    }

    // tool choice

    @Test
    void toolChoiceOptionValues() {
        assertEquals("required", chatRequest("toolChoice=required", Map.of()).get("tool_choice_option").asString());
        assertEquals("none", chatRequest("toolChoice=none", Map.of()).get("tool_choice_option").asString());
        assertEquals("auto", chatRequest("", Map.of(WatsonxAiConstants.TOOL_CHOICE, "AUTO"))
                .get("tool_choice_option").asString());
    }

    @Test
    void toolChoiceNamesTheToolToCall() {
        JsonNode request = chatRequest("toolChoice=auto", Map.of(WatsonxAiConstants.TOOL_CHOICE, "get_weather"));
        assertFalse(request.has("tool_choice_option"));
        assertEquals("function", request.get("tool_choice").get("type").asString());
        assertEquals("get_weather", request.get("tool_choice").get("function").get("name").asString());
    }

    @Test
    void noToolChoiceByDefault() {
        JsonNode request = chatRequest("", Map.of());
        assertFalse(request.has("tool_choice_option"));
        assertFalse(request.has("tool_choice"));
    }

    // foundation models

    @Test
    void listModelsFilterAndTechPreview() {
        Exchange result = send(uri("listModels", ""), null, Map.of(
                WatsonxAiConstants.FOUNDATION_MODEL_FILTER, "function_rerank,!function_embedding:and",
                WatsonxAiConstants.FOUNDATION_MODEL_TECH_PREVIEW, "true"));

        assertEquals(1, result.getMessage().getBody(List.class).size());
        String query = URLDecoder.decode(server.lastRequest(MODELS).query(), StandardCharsets.UTF_8);
        assertTrue(query.contains("filters=function_rerank,!function_embedding:and"), query);
        assertTrue(query.contains("tech_preview=true"), query);
    }

    @Test
    void listModelsWithoutFilter() {
        send(uri("listModels", ""), null, Map.of());
        String query = server.lastRequest(MODELS).query();
        assertFalse(query != null && query.contains("filters="), query);
    }

    // detection

    @Test
    void graniteGuardianAnswerNoIsNotDetected() {
        server.respond(DETECTION, """
                {"detections":[{"text":"I hate you","detection_type":"risk","detection":"No","score":0.42,
                 "start":0,"end":10}]}""");
        Exchange result = send(uri("detect", ""), "I hate you", Map.of());

        assertEquals(Boolean.FALSE, result.getMessage().getHeader(WatsonxAiConstants.DETECTED));
        assertEquals(1, result.getMessage().getHeader(WatsonxAiConstants.DETECTION_COUNT));
    }

    @Test
    void graniteGuardianAnswerYesIsDetected() {
        server.respond(DETECTION, """
                {"detections":[{"text":"I hate you","detection_type":"risk","detection":"Yes","score":0.92,
                 "start":0,"end":10}]}""");
        Exchange result = send(uri("detect", ""), "I hate you", Map.of());

        assertEquals(Boolean.TRUE, result.getMessage().getHeader(WatsonxAiConstants.DETECTED));
    }

    @Test
    void detectContextWithNamedDetectorsAndGovernanceInstance() {
        server.respond(DETECTION + "/context", """
                {"detections":[{"detector_id":"granite_guardian_3_2_5b","detection_type":"faithfulness",
                 "detection":"factual","score":0.94}]}""");
        Exchange result = send(uri("detectContext", "detectors=context_relevance,faithfulness&detectionThreshold=0.6"
                                                    + "&governanceInstanceId=my-governance"),
                "The capital of France is Paris.",
                Map.of(WatsonxAiConstants.DETECTION_CONTEXT, List.of("Paris is the capital of France.", "https://x/y")));

        FakeWatsonxServer.Request request = server.lastRequest(DETECTION + "/context");
        JsonNode json = request.json();
        assertEquals("The capital of France is Paris.", json.get("input").asString());
        assertEquals("docs", json.get("context_type").asString());
        assertEquals(2, json.get("context").size());
        assertEquals(0.6, json.get("detectors").get("context_relevance").get("threshold").asDouble(), 0.001);
        assertTrue(json.get("detectors").has("faithfulness"));
        assertEquals("my-governance", request.header("x-governance-instance-id"));
        Map<?, ?> results = result.getMessage().getHeader(WatsonxAiConstants.DETECTION_RESULTS, Map.class);
        assertTrue(results.containsKey("faithfulness"));
        assertNull(result.getMessage().getHeader(WatsonxAiConstants.DETECTED));
    }

    @Test
    void detectGeneratedDefaultsToAnswerRelevance() {
        server.respond(DETECTION + "/generated", """
                {"detections":[{"detector_id":"granite_guardian_3_2_5b","detection_type":"answer_relevance",
                 "detection":"relevant","score":0.97}]}""");
        Exchange result = send(uri("detectGenerated", ""), "The capital of France is Paris.",
                Map.of(WatsonxAiConstants.DETECTION_PROMPT, "What is the capital of France?"));

        JsonNode json = server.lastRequest(DETECTION + "/generated").json();
        assertEquals("What is the capital of France?", json.get("prompt").asString());
        assertEquals("The capital of France is Paris.", json.get("generated_text").asString());
        assertTrue(json.get("detectors").has("answer_relevance"));
        assertEquals(1, result.getMessage().getHeader(WatsonxAiConstants.DETECTION_COUNT));
    }

    @Test
    void detectWithGraniteGuardianByName() {
        server.respond(DETECTION, """
                {"detections":[]}""");
        send(uri("detect", "detectors=granite_guardian,pii&detectionThreshold=0.3"), "Hello", Map.of());

        JsonNode detectors = server.lastRequest(DETECTION).json().get("detectors");
        assertEquals(0.3, detectors.get("granite_guardian").get("threshold").asDouble(), 0.001);
        assertTrue(detectors.has("pii"));
        assertFalse(detectors.has("hap"));
    }

    // deployments

    @Test
    void deploymentInfoInTheProjectWithoutSpace() {
        server.respond(DEPLOYMENT, """
                {"metadata":{"id":"my-deployment","name":"my deployment"},
                 "entity":{"deployed_asset_type":"foundation_model","status":{"state":"ready"}}}""");
        Exchange result = send(uri("deploymentInfo", "deploymentId=my-deployment"), null, Map.of());

        assertEquals("my deployment", result.getMessage().getHeader(WatsonxAiConstants.DEPLOYMENT_NAME));
        assertTrue(server.lastRequest(DEPLOYMENT).query().contains("project_id=my-project"));
    }
}
