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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.camel.component.ai.observability.GenAiObservabilityLevel;
import org.apache.camel.dsl.jbang.core.common.genai.GenAiSpans.GenAiActivity;
import org.apache.camel.util.json.JsonObject;

/**
 * The GenAI calls of the monitored integration for the AI usage view, from the {@code genai} dev console of
 * camel-ai-observability (the {@code genai} action of the CLI connector). The console records without a tracer, so the
 * view needs no {@code --observe}, and it states the observability level.
 *
 * @param level      the level of the integration, or {@code null} when it has no genai console
 * @param recording  whether the console records: only while the dev console is enabled
 * @param modelCalls the model calls, made in an agent run or not
 * @param runs       the agent runs, with how many model calls and tool calls each made
 */
record GenAiConsoleUsage(GenAiObservabilityLevel level, boolean recording, List<AiUsageEntry> modelCalls, List<Run> runs) {

    /** The top-level calls asked of the console: all it keeps. */
    static final int LIMIT = 50;

    static final GenAiConsoleUsage NONE = new GenAiConsoleUsage(null, false, List.of(), List.of());

    private static final String AGENT_RUN = "invoke_agent";
    private static final String TOOL_CALL = "execute_tool";

    /** An agent run (or a tool call outside a run, with no model calls): when it started and what it called. */
    record Run(Instant timestamp, boolean agentRun, int modelCalls, int toolCalls) {
    }

    /** The answer of the genai action; {@link #NONE} when the integration has no genai console. */
    static GenAiConsoleUsage from(JsonObject answer) {
        if (answer == null || Boolean.FALSE.equals(answer.getBoolean("enabled")) || answer.getString("level") == null) {
            return NONE;
        }
        GenAiObservabilityLevel level;
        try {
            level = GenAiObservabilityLevel.parse(answer.getString("level"));
        } catch (IllegalArgumentException e) {
            level = GenAiObservabilityLevel.BASIC;
        }
        List<AiUsageEntry> modelCalls = new ArrayList<>();
        List<Run> runs = new ArrayList<>();
        for (JsonObject call : objects(answer, "calls")) {
            String operation = call.getString("operation");
            if (!AGENT_RUN.equals(operation) && !TOOL_CALL.equals(operation)) {
                modelCalls.add(entry(call, null));
                continue;
            }
            int models = 0;
            int tools = TOOL_CALL.equals(operation) ? 1 : 0;
            for (JsonObject step : objects(call, "steps")) {
                String stepOperation = step.getString("operation");
                if (TOOL_CALL.equals(stepOperation)) {
                    tools++;
                } else if (!AGENT_RUN.equals(stepOperation)) {
                    models++;
                    modelCalls.add(entry(step, call.getString("routeId")));
                }
            }
            runs.add(new Run(timestamp(call), AGENT_RUN.equals(operation), models, tools));
        }
        return new GenAiConsoleUsage(
                level, Boolean.TRUE.equals(answer.getBoolean("recording")), List.copyOf(modelCalls),
                List.copyOf(runs));
    }

    /** The agent runs, their model calls and the tool calls started at or after {@code since}, and the level. */
    GenAiActivity activity(Instant since) {
        int agentRuns = 0;
        int agentModelCalls = 0;
        int toolCalls = 0;
        for (Run run : runs) {
            if (since == null || !run.timestamp().isBefore(since)) {
                agentRuns += run.agentRun() ? 1 : 0;
                agentModelCalls += run.modelCalls();
                toolCalls += run.toolCalls();
            }
        }
        return new GenAiActivity(agentRuns, agentModelCalls, toolCalls, level);
    }

    private static AiUsageEntry entry(JsonObject call, String runRouteId) {
        String model = call.getString("model") != null ? call.getString("model") : call.getString("name");
        String provider = call.getString("provider") != null ? call.getString("provider") : "unknown";
        int in = (int) number(call, "inputTokens");
        int out = (int) number(call, "outputTokens");
        String routeId = call.getString("routeId") != null ? call.getString("routeId") : runRouteId;
        return new AiUsageEntry(
                model != null ? model : "unknown", provider, in, out, in + out, number(call, "durationMs"),
                call.getString("finishReason"), timestamp(call), AiUsageSource.ROUTE, routeId, 0,
                (int) number(call, "cachedInputTokens"));
    }

    private static Instant timestamp(JsonObject call) {
        long millis = number(call, "timestamp");
        return millis > 0 ? Instant.ofEpochMilli(millis) : Instant.EPOCH;
    }

    private static long number(JsonObject json, String key) {
        return json.get(key) instanceof Number n ? Math.max(0, n.longValue()) : 0;
    }

    private static List<JsonObject> objects(JsonObject json, String key) {
        List<JsonObject> list = new ArrayList<>();
        if (json.get(key) instanceof List<?> items) {
            for (Object item : items) {
                if (item instanceof JsonObject jo) {
                    list.add(jo);
                } else if (item instanceof Map<?, ?> map) {
                    JsonObject jo = new JsonObject();
                    map.forEach((k, v) -> jo.put(String.valueOf(k), v));
                    list.add(jo);
                }
            }
        }
        return list;
    }
}
