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
package org.apache.camel.dsl.jbang.core.common.genai;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.component.ai.observability.GenAiAttributes;
import org.apache.camel.component.ai.observability.GenAiMessages;
import org.apache.camel.component.ai.observability.GenAiObservabilityLevel;
import org.apache.camel.dsl.jbang.core.common.genai.GenAiAgentRuns.AgentRun;
import org.apache.camel.dsl.jbang.core.common.genai.GenAiAgentRuns.Step;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GenAiAgentRunsTest {

    @Test
    void agentRunsAreTheNewestFirstWithTheirStepsInOrder() {
        List<SpanEntry> spans = List.of(
                route("route-old", 0), agent("old", "route-old", 10, 100),
                route("route-new", 1000), agent("new", "route-new", 1010, 300),
                chat("new-chat1", "new", 1020, 120, 300, 40, 270, "tool_calls"),
                tool("new-tool", "new", 1150, 5, "{\"sku\":\"CAMEL-CAP\"}", "0 CAMEL-CAP in stock"),
                chat("new-chat2", "new", 1160, 140, 400, 20, 0, "stop"));

        List<AgentRun> runs = GenAiAgentRuns.agentRuns(spans);

        assertThat(runs).extracting(run -> run.span().spanId()).containsExactly("new", "old");
        AgentRun run = runs.get(0);
        assertThat(run.agentName()).isEqualTo("support");
        assertThat(run.model()).isEqualTo("granite4:3b");
        assertThat(run.steps()).extracting(Step::name)
                .containsExactly("chat granite4:3b", "lookupStock", "chat granite4:3b");
        assertThat(run.steps().get(1).arguments()).isEqualTo("{\"sku\":\"CAMEL-CAP\"}");
        assertThat(run.steps().get(1).result()).isEqualTo("0 CAMEL-CAP in stock");
        assertThat(run.modelCalls()).isEqualTo(2);
        assertThat(run.toolCalls()).isEqualTo(1);
        assertThat(run.inputTokens()).isEqualTo(700);
        assertThat(run.cachedTokens()).isEqualTo(270);
        assertThat(run.modelMs()).isEqualTo(260);
        assertThat(run.toolMs()).isEqualTo(5);
        assertThat(run.otherMs()).isEqualTo(35);
        assertThat(runs.get(1).steps()).isEmpty();
        assertThat(GenAiSpans.level(spans)).isEqualTo(GenAiObservabilityLevel.FULL);
    }

    @Test
    void exchangeIdComesFromTheCamelSpanAboveTheRun() {
        List<SpanEntry> spans = List.of(route("route1", 0), agent("run1", "route1", 10, 100));

        assertThat(GenAiAgentRuns.exchangeId(spans.get(1), spans)).isEqualTo("EX-route1");
        assertThat(GenAiAgentRuns.exchangeId(agent("lonely", null, 0, 10), List.of())).isNull();
    }

    @Test
    void firstUserMessageIsTheQuestionOfTheRun() {
        Map<String, Object> attrs = attrs("chat", GenAiAttributes.REQUEST_MODEL, "granite4:3b");
        attrs.put(GenAiAttributes.INPUT_MESSAGES, new GenAiMessages()
                .text(GenAiMessages.SYSTEM, "You answer the customers.")
                .text(GenAiMessages.USER, "Is order ORD-1001 paid?")
                .toJson(8192));
        List<SpanEntry> spans = List.of(agent("run1", null, 0, 100), span("chat", "run1", 10, 50, attrs));

        AgentRun run = GenAiAgentRuns.agentRun(spans.get(0), spans);

        assertThat(GenAiAgentRuns.firstUserMessage(run, spans)).isEqualTo("Is order ORD-1001 paid?");
        assertThat(GenAiAgentRuns.firstUserMessage(GenAiAgentRuns.agentRun(spans.get(0), List.of(spans.get(0))),
                List.of(spans.get(0)))).isNull();
    }

    private static SpanEntry route(String id, long startMs) {
        return new SpanEntry(
                "t", id, null, "file://questions", "INTERNAL", "OK", startMs * 1_000_000L,
                (startMs + 500) * 1_000_000L, 500, "support", null, "camel", Map.of("exchangeId", "EX-" + id));
    }

    private static SpanEntry agent(String id, String parent, long startMs, long ms) {
        return span(id, parent, startMs, ms, attrs("invoke_agent", GenAiAttributes.AGENT_NAME, "support",
                GenAiAttributes.REQUEST_MODEL, "granite4:3b"));
    }

    private static SpanEntry chat(
            String id, String parent, long startMs, long ms, long in, long out, long cached, String finish) {
        Map<String, Object> attrs = attrs("chat", GenAiAttributes.REQUEST_MODEL, "granite4:3b",
                GenAiAttributes.FINISH_REASONS, finish);
        attrs.put(GenAiAttributes.INPUT_TOKENS, in);
        attrs.put(GenAiAttributes.OUTPUT_TOKENS, out);
        if (cached > 0) {
            attrs.put(GenAiAttributes.CACHE_READ_INPUT_TOKENS, cached);
        }
        return span(id, parent, startMs, ms, attrs);
    }

    private static SpanEntry tool(String id, String parent, long startMs, long ms, String args, String result) {
        return span(id, parent, startMs, ms, attrs("execute_tool", GenAiAttributes.TOOL_NAME, "lookupStock",
                GenAiAttributes.TOOL_CALL_ARGUMENTS, args, GenAiAttributes.TOOL_CALL_RESULT, result));
    }

    private static Map<String, Object> attrs(String operation, String... keyValues) {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put(GenAiAttributes.OPERATION_NAME, operation);
        for (int i = 0; i < keyValues.length; i += 2) {
            attrs.put(keyValues[i], keyValues[i + 1]);
        }
        return attrs;
    }

    private static SpanEntry span(String id, String parent, long startMs, long ms, Map<String, Object> attributes) {
        return new SpanEntry(
                "t", id, parent, id, "INTERNAL", "OK", startMs * 1_000_000L, (startMs + ms) * 1_000_000L,
                ms, "support", null, "camel", attributes);
    }
}
