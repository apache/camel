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
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.apache.camel.component.ai.observability.GenAiObservabilityLevel;
import org.apache.camel.dsl.jbang.core.common.genai.GenAiSpans.GenAiActivity;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-27313: the AI usage view reads the GenAI calls of the integration from its genai dev console.
 */
class GenAiConsoleUsageTest {

    private static final long T0 = 1_795_255_200_000L;

    @Test
    void noConsoleIsNoUsage() {
        assertThat(GenAiConsoleUsage.from(null)).isSameAs(GenAiConsoleUsage.NONE);
        assertThat(GenAiConsoleUsage.from(new JsonObject(Map.of("enabled", false)))).isSameAs(GenAiConsoleUsage.NONE);
        assertThat(GenAiConsoleUsage.NONE.activity(null).level()).isNull();
    }

    @Test
    void theModelCallsOfTheRunsAndOutsideThemAreUsageEntries() throws Exception {
        JsonObject run = call("invoke_agent", "support", T0 + 1000);
        // a model call made by the route of a tool has no route of its own: the run's is used
        run.put("steps", new JsonArray(
                List.of(chat(null, T0 + 1100, 300, 40, 270), call("execute_tool", null, T0 + 1200),
                        chat("support", T0 + 1300, 400, 20, 0))));
        JsonObject answer = answer("full", chat("orders", T0, 10, 5, 0), run);

        // as the action file carries it: JSON text read back
        GenAiConsoleUsage usage = GenAiConsoleUsage.from((JsonObject) Jsoner.deserialize(answer.toJson()));

        assertThat(usage.level()).isEqualTo(GenAiObservabilityLevel.FULL);
        assertThat(usage.recording()).isTrue();
        assertThat(usage.modelCalls()).extracting(AiUsageEntry::routeId).containsExactly("orders", "support", "support");
        AiUsageEntry first = usage.modelCalls().get(1);
        assertThat(first.source()).isEqualTo(AiUsageSource.ROUTE);
        assertThat(first.model()).isEqualTo("granite4:3b");
        assertThat(first.provider()).isEqualTo("ollama");
        assertThat(first.totalTokens()).isEqualTo(340);
        assertThat(first.cachedInputTokens()).isEqualTo(270);
        assertThat(first.latencyMs()).isEqualTo(100);
        assertThat(first.timestamp()).isEqualTo(Instant.ofEpochMilli(T0 + 1100));

        GenAiActivity activity = usage.activity(Instant.EPOCH);
        assertThat(activity.agentRuns()).isEqualTo(1);
        assertThat(activity.agentModelCalls()).isEqualTo(2);
        assertThat(activity.toolCalls()).isEqualTo(1);
    }

    @Test
    void theActivitySinceAResetLeavesOutTheRunsBefore() {
        JsonObject old = call("invoke_agent", "support", T0);
        old.put("steps", new JsonArray(List.of(chat("support", T0, 1, 1, 0))));
        JsonObject recent = call("invoke_agent", "support", T0 + 60_000);
        recent.put("steps", new JsonArray(List.of(call("execute_tool", null, T0 + 60_100))));

        GenAiActivity activity = GenAiConsoleUsage.from(answer("detailed", recent, old))
                .activity(Instant.ofEpochMilli(T0 + 30_000));

        assertThat(activity.agentRuns()).isEqualTo(1);
        assertThat(activity.agentModelCalls()).isZero();
        assertThat(activity.toolCalls()).isEqualTo(1);
        assertThat(activity.level()).isEqualTo(GenAiObservabilityLevel.DETAILED);
    }

    private static JsonObject answer(String level, JsonObject... calls) {
        JsonObject answer = new JsonObject();
        answer.put("recording", true);
        answer.put("level", level);
        answer.put("calls", new JsonArray(List.of((Object[]) calls)));
        return answer;
    }

    private static JsonObject chat(String routeId, long timestamp, long input, long output, long cached) {
        JsonObject chat = call("chat", routeId, timestamp);
        chat.put("name", "chat granite4:3b");
        chat.put("provider", "ollama");
        chat.put("model", "granite4:3b");
        chat.put("inputTokens", input);
        chat.put("outputTokens", output);
        if (cached > 0) {
            chat.put("cachedInputTokens", cached);
        }
        return chat;
    }

    private static JsonObject call(String operation, String routeId, long timestamp) {
        JsonObject call = new JsonObject();
        call.put("operation", operation);
        if (routeId != null) {
            call.put("routeId", routeId);
        }
        call.put("timestamp", timestamp);
        call.put("running", false);
        call.put("durationMs", 100L);
        return call;
    }
}
