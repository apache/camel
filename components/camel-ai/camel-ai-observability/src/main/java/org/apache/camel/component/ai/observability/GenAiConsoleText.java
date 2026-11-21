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
package org.apache.camel.component.ai.observability;

import java.util.List;
import java.util.Map;

import org.apache.camel.util.json.JsonObject;

/**
 * The text answer of the {@code genai} dev console, made from its JSON answer.
 */
final class GenAiConsoleText {

    private GenAiConsoleText() {
    }

    static String format(Map<String, Object> json) {
        JsonObject root = new JsonObject(json);
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("GenAI (level: %s%s)%n", root.getString("level"),
                Boolean.TRUE.equals(root.getBoolean("recording")) ? "" : ", not recording: the dev console is off"));
        sb.append(String.format("    Model calls: %d, agent runs: %d, tool calls: %d, errors: %d%n",
                number(root, "modelCalls"), number(root, "agentRuns"), number(root, "toolCalls"),
                number(root, "errors")));
        sb.append(String.format("    Tokens: in %d (cached %d), out %d%n", number(root, "inputTokens"),
                number(root, "cachedInputTokens"), number(root, "outputTokens")));
        List<JsonObject> models = root.getCollection("models");
        if (models != null && !models.isEmpty()) {
            sb.append(String.format("%nModels:%n"));
            for (JsonObject model : models) {
                sb.append(String.format("    %s/%s: %d calls (%d failed), in %d (cached %d), out %d, %d ms%n",
                        orDash(model.getString("provider")), orDash(model.getString("model")),
                        number(model, "calls"), number(model, "errors"), number(model, "inputTokens"),
                        number(model, "cachedInputTokens"), number(model, "outputTokens"), number(model, "durationMs")));
            }
        }
        JsonObject one = root.getMap("call");
        if (one != null) {
            sb.append(String.format("%n"));
            call(sb, one);
        }
        List<JsonObject> calls = root.getCollection("calls");
        if (calls != null && !calls.isEmpty()) {
            sb.append(String.format("%nCalls (newest first):%n"));
            for (JsonObject call : calls) {
                call(sb, call);
            }
        }
        if (number(root, "moreCalls") > 0) {
            sb.append(String.format("    ... %d more%n", number(root, "moreCalls")));
        }
        if (root.getString("error") != null) {
            sb.append(String.format("%n%s%n", root.getString("error")));
        }
        return sb.toString();
    }

    private static void call(StringBuilder sb, JsonObject call) {
        sb.append(String.format("    #%s %s%s%s%n", call.getString("id"), call.getString("name"), duration(call),
                usage(call)));
        if (call.getString("question") != null) {
            sb.append(String.format("        question: %s%n", call.getString("question")));
        }
        List<JsonObject> steps = call.getCollection("steps");
        if (steps != null) {
            for (JsonObject step : steps) {
                sb.append(String.format("        #%s %s%s%s%n", step.getString("id"), step.getString("name"),
                        duration(step), usage(step)));
            }
        }
        if (call.getString("answer") != null) {
            sb.append(String.format("        answer: %s%n", call.getString("answer")));
        }
    }

    private static String duration(JsonObject call) {
        if (Boolean.TRUE.equals(call.getBoolean("running"))) {
            return " (running)";
        }
        return " " + number(call, "durationMs") + " ms";
    }

    private static String usage(JsonObject call) {
        StringBuilder sb = new StringBuilder();
        if (call.get("inputTokens") != null || call.get("outputTokens") != null) {
            sb.append(String.format(", in %d out %d", number(call, "inputTokens"), number(call, "outputTokens")));
        }
        if (call.getString("finishReason") != null) {
            sb.append(", ").append(call.getString("finishReason"));
        }
        if (call.getString("error") != null) {
            sb.append(", failed: ").append(call.getString("error"));
        }
        return sb.toString();
    }

    private static long number(JsonObject json, String key) {
        return json.get(key) instanceof Number n ? n.longValue() : 0;
    }

    private static String orDash(String value) {
        return value != null ? value : "-";
    }
}
