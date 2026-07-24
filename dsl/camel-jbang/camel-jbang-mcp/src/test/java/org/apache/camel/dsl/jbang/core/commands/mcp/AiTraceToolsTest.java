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
package org.apache.camel.dsl.jbang.core.commands.mcp;

import java.util.List;

import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiTraceToolsTest {

    private AiTraceTools tools;

    @BeforeEach
    void setUp() {
        tools = new AiTraceTools();
    }

    @Test
    void shouldExtractAiComponentsFromRouteStructure() {
        JsonObject route = new JsonObject();
        JsonArray steps = new JsonArray();

        JsonObject bedrockStep = new JsonObject();
        bedrockStep.put("uri", "aws-bedrock:label");
        bedrockStep.put("routeId", "ai-route");
        bedrockStep.put("nodeId", "to1");
        steps.add(bedrockStep);

        JsonObject directStep = new JsonObject();
        directStep.put("uri", "direct:start");
        steps.add(directStep);

        JsonObject doclingStep = new JsonObject();
        doclingStep.put("uri", "docling:convert");
        doclingStep.put("routeId", "ai-route");
        doclingStep.put("nodeId", "to2");
        steps.add(doclingStep);

        route.put("steps", steps);

        List<AiTraceTools.AiComponentInfo> result = tools.extractAiComponents(route);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).scheme()).isEqualTo("aws-bedrock");
        assertThat(result.get(0).routeId()).isEqualTo("ai-route");
        assertThat(result.get(1).scheme()).isEqualTo("docling");
    }

    @Test
    void shouldIgnoreNonAiComponents() {
        JsonObject route = new JsonObject();
        route.put("uri", "kafka:my-topic");

        List<AiTraceTools.AiComponentInfo> result = tools.extractAiComponents(route);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldExtractAiHeaders() {
        JsonObject history = new JsonObject();
        JsonObject headers = new JsonObject();
        headers.put("CamelAwsBedrockTokenCount", "1500");
        headers.put("CamelAwsBedrockCompletionReason", "end_turn");
        headers.put("CamelAwsBedrockChunkCount", "12");
        headers.put("CamelAwsBedrockGuardrailOutput", "allowed");
        headers.put("breadcrumbId", "abc-123");
        history.put("headers", headers);

        List<AiTraceTools.AiHeaderInfo> result = tools.extractAiHeaders(history);

        assertThat(result).hasSize(4);
        assertThat(result).extracting(AiTraceTools.AiHeaderInfo::header)
                .containsExactlyInAnyOrder(
                        "CamelAwsBedrockTokenCount",
                        "CamelAwsBedrockCompletionReason",
                        "CamelAwsBedrockChunkCount",
                        "CamelAwsBedrockGuardrailOutput");
    }

    @Test
    void shouldCategorizeHeadersCorrectly() {
        JsonObject history = new JsonObject();
        history.put("CamelAwsBedrockTokenCount", "500");
        history.put("CamelAwsBedrockConverseUsage", "{input: 100, output: 400}");
        history.put("CamelAwsBedrockCompletionReason", "end_turn");
        history.put("CamelAwsBedrockChunkCount", "5");
        history.put("CamelAwsBedrockGuardrailAssessments", "[]");
        history.put("CamelAwsBedrockOperation", "converse");

        List<AiTraceTools.AiHeaderInfo> result = tools.extractAiHeaders(history);

        assertThat(result).anyMatch(h -> "token_usage".equals(h.category()));
        assertThat(result).anyMatch(h -> "completion".equals(h.category()));
        assertThat(result).anyMatch(h -> "streaming".equals(h.category()));
        assertThat(result).anyMatch(h -> "guardrail".equals(h.category()));
        assertThat(result).anyMatch(h -> "operation".equals(h.category()));
    }

    @Test
    void shouldExtractAiProcessorStats() {
        JsonObject top = new JsonObject();
        JsonArray processors = new JsonArray();

        JsonObject bedrockProc = new JsonObject();
        bedrockProc.put("id", "to1");
        bedrockProc.put("uri", "aws-bedrock:label");
        bedrockProc.put("total", "42");
        bedrockProc.put("mean", "1200");
        bedrockProc.put("max", "3500");
        bedrockProc.put("min", "450");
        bedrockProc.put("last", "980");
        processors.add(bedrockProc);

        JsonObject kafkaProc = new JsonObject();
        kafkaProc.put("id", "to2");
        kafkaProc.put("uri", "kafka:output");
        kafkaProc.put("total", "42");
        processors.add(kafkaProc);

        top.put("processors", processors);

        List<AiTraceTools.AiProcessorStat> result = tools.extractAiProcessorStats(top);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).uri()).isEqualTo("aws-bedrock:label");
        assertThat(result.get(0).totalExchanges()).isEqualTo("42");
        assertThat(result.get(0).meanMs()).isEqualTo("1200");
    }

    @Test
    void shouldHandleEmptyInputGracefully() {
        assertThat(tools.extractAiComponents(new JsonObject())).isEmpty();
        assertThat(tools.extractAiHeaders(new JsonObject())).isEmpty();
        assertThat(tools.extractAiProcessorStats(new JsonObject())).isEmpty();
    }

    @Test
    void shouldExtractSchemeCorrectly() {
        assertThat(AiTraceTools.extractScheme("aws-bedrock:label")).isEqualTo("aws-bedrock");
        assertThat(AiTraceTools.extractScheme("docling:convert")).isEqualTo("docling");
        assertThat(AiTraceTools.extractScheme("langchain4j-chat:model")).isEqualTo("langchain4j-chat");
        assertThat(AiTraceTools.extractScheme(null)).isNull();
        assertThat(AiTraceTools.extractScheme("noscheme")).isNull();
    }

    @Test
    void shouldDetectLangChain4jComponents() {
        JsonObject route = new JsonObject();
        route.put("uri", "langchain4j-chat:myModel");

        List<AiTraceTools.AiComponentInfo> result = tools.extractAiComponents(route);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).scheme()).isEqualTo("langchain4j-chat");
    }

    @Test
    void shouldDetectTextractComponents() {
        JsonObject route = new JsonObject();
        route.put("uri", "aws2-textract:detect");

        List<AiTraceTools.AiComponentInfo> result = tools.extractAiComponents(route);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).scheme()).isEqualTo("aws2-textract");
    }

    @Test
    void shouldExtractOpenAiHeadersWithCorrectCasing() {
        JsonObject history = new JsonObject();
        history.put("CamelOpenAIModel", "gpt-4o");
        history.put("CamelOpenAIFinishReason", "stop");
        history.put("breadcrumbId", "abc-123");

        List<AiTraceTools.AiHeaderInfo> result = tools.extractAiHeaders(history);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(AiTraceTools.AiHeaderInfo::header)
                .containsExactlyInAnyOrder("CamelOpenAIModel", "CamelOpenAIFinishReason");
    }

    @Test
    void shouldDetectWebSearchAndEmbeddingStoreComponents() {
        JsonObject route = new JsonObject();
        JsonArray steps = new JsonArray();
        JsonObject ws = new JsonObject();
        ws.put("uri", "langchain4j-web-search:search");
        steps.add(ws);
        JsonObject es = new JsonObject();
        es.put("uri", "langchain4j-embeddingstore:store");
        steps.add(es);
        route.put("steps", steps);

        List<AiTraceTools.AiComponentInfo> result = tools.extractAiComponents(route);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(AiTraceTools.AiComponentInfo::scheme)
                .containsExactlyInAnyOrder("langchain4j-web-search", "langchain4j-embeddingstore");
    }

    @Test
    void shouldExtractTextractHeaders() {
        JsonObject history = new JsonObject();
        history.put("CamelAwsTextractJobId", "job-123");

        List<AiTraceTools.AiHeaderInfo> result = tools.extractAiHeaders(history);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).header()).isEqualTo("CamelAwsTextractJobId");
    }
}
