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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

/**
 * The GenAI part of the AI trace and the conversation of a run, from the genai dev console of camel-ai-observability
 * (CAMEL-27312, CAMEL-27313). Written for a model that reads the answer: the summary comes first and fits in the few
 * hundred characters a chat history keeps of a tool result, the runs are the newest first and shallow, and the long
 * texts are cut with a note. The words are those of the Camel TUI: agent run, model call, tool call.
 */
final class GenAiTraceReport {

    static final String CONVERSATION_TOOL = "camel_runtime_ai_conversation";
    /** The top-level calls read from the console: all it keeps, so that a route filter still finds runs. */
    static final int CONSOLE_LIMIT = 50;
    static final int MAX_RUNS = 5;
    static final int MAX_STEP_TEXT = 200;
    static final int MAX_QUESTION = 200;
    static final int MAX_MESSAGE = 1000;
    static final int MAX_CONVERSATION = 6000;
    private static final int MAX_MODELS_IN_SUMMARY = 3;
    private static final String TRUNCATED = "...[truncated]";
    private static final String AGENT_RUN = "invoke_agent";
    private static final String TOOL_CALL = "execute_tool";

    static final String NO_CONSOLE = "No GenAI calls: the genai dev console is not there. It comes with "
                                     + "camel-ai-observability, which camel run adds when a camel.aiObservability "
                                     + "option is set: set camel.aiObservability.level=detailed to see the agent runs "
                                     + "and tool calls (full adds the messages).";
    static final String NOT_RECORDING = "No GenAI calls: the genai dev console records only while the dev console is "
                                        + "enabled (camel.main.devConsoleEnabled=true, on with camel run).";

    /** The model calls of one model. */
    record ModelCalls(String model, String provider, long calls, Long failed, long inputTokens, long outputTokens,
            Long cachedTokens, long avgMs) {
    }

    /** A model call or tool call of a run; the arguments and result are recorded at level full only. */
    record RunStep(String step, String name, Long durationMs, Long inputTokens, Long outputTokens, Long cachedTokens,
            String finishReason, String error, String arguments, String result) {
    }

    /** An agent run; the question and the answer are recorded at level full only. */
    record Run(String runId, String question, String answer, String agent, String model, String routeId,
            String exchangeId, String start, Boolean running, Long durationMs, long modelCalls, long toolCalls,
            long inputTokens, long outputTokens, Long cachedTokens, String error, List<RunStep> steps) {
    }

    /** The GenAI part of the AI trace. */
    record GenAi(String level, List<ModelCalls> modelCalls, List<Run> agentRuns, String moreRuns) {
    }

    /** A part of a message of a conversation. */
    record Message(String role, String type, String tool, String text, String finishReason) {
    }

    /** The conversation of a run. */
    record Conversation(String summary, String level, String runId, String agent, String model, List<Message> messages,
            String cut) {
    }

    private GenAiTraceReport() {
    }

    /** Whether the answer of the genai action is the console (the console is there). */
    static boolean hasConsole(JsonObject answer) {
        return answer != null && !Boolean.FALSE.equals(answer.getBoolean("enabled")) && answer.getString("level") != null;
    }

    /** The one-line summary: the level, the counts and what to do next. */
    static String summary(JsonObject console, String routeFilter) {
        if (!hasConsole(console)) {
            return NO_CONSOLE;
        }
        String level = console.getString("level");
        long modelCalls = number(console, "modelCalls");
        if (modelCalls == 0 && !Boolean.TRUE.equals(console.getBoolean("recording"))) {
            return NOT_RECORDING;
        }
        String calls = modelCalls + " model call" + (modelCalls == 1 ? "" : "s") + models(console);
        if ("basic".equals(level)) {
            return "GenAI level=basic: " + calls + ". Agent runs and tool calls are not traced: set "
                   + "camel.aiObservability.level=detailed.";
        }
        long agentRuns = number(console, "agentRuns");
        long toolCalls = number(console, "toolCalls");
        StringBuilder sb = new StringBuilder("GenAI level=").append(level).append(": ")
                .append(agentRuns).append(" agent run").append(agentRuns == 1 ? "" : "s").append(", ")
                .append(calls).append(", ").append(toolCalls).append(" tool call").append(toolCalls == 1 ? "" : "s");
        long errors = number(console, "errors");
        if (errors > 0) {
            sb.append(", ").append(errors).append(" failed");
        }
        if (!isAll(routeFilter)) {
            sb.append(" (all routes; the runs below are of route ").append(routeFilter).append(")");
        }
        if (agentRuns > 0) {
            sb.append("; runs newest first. ");
            sb.append("full".equals(level)
                    ? CONVERSATION_TOOL + " with a runId shows the messages of a run."
                    : "The messages need camel.aiObservability.level=full.");
        }
        return sb.toString();
    }

    /** The model calls per model, the newest five agent runs and the level; {@code null} without the console. */
    static GenAi genAi(JsonObject console, String routeFilter) {
        if (!hasConsole(console) || number(console, "modelCalls") == 0 && number(console, "agentRuns") == 0) {
            return null;
        }
        List<Run> runs = new ArrayList<>();
        for (JsonObject call : objects(console, "calls")) {
            if (AGENT_RUN.equals(call.getString("operation"))
                    && (isAll(routeFilter) || routeFilter.equals(call.getString("routeId")))) {
                runs.add(run(call));
            }
        }
        return new GenAi(
                console.getString("level"), modelCalls(console), runs.isEmpty() ? null : runs.stream().limit(MAX_RUNS).toList(),
                runs.size() > MAX_RUNS ? "+" + (runs.size() - MAX_RUNS) + " more runs" : null);
    }

    /**
     * The messages of a run (or of a model call or tool call) from the answer of the console for its id, cut at
     * {@value #MAX_MESSAGE} characters a message and {@value #MAX_CONVERSATION} in all; {@code null} when the console
     * has no call with that id.
     */
    static Conversation conversation(JsonObject answer, String runId) {
        JsonObject call = answer != null ? answer.getMap("call") : null;
        if (call == null) {
            return null;
        }
        String level = answer.getString("level");
        String agent = call.getString("agentName");
        String model = call.getString("model");
        List<Message> transcript = transcript(call);
        if (transcript.isEmpty()) {
            return new Conversation(
                    "GenAI level=" + level + ": no messages for run " + runId
                                    + ". The messages are recorded at camel.aiObservability.level=full only.",
                    level, runId, agent, model, null, null);
        }
        List<Message> messages = new ArrayList<>();
        int total = 0;
        int left = 0;
        for (Message message : transcript) {
            if (total >= MAX_CONVERSATION) {
                left++;
                continue;
            }
            String text = cut(message.text(), Math.min(MAX_MESSAGE, MAX_CONVERSATION - total));
            total += text != null ? text.length() : 0;
            messages.add(new Message(message.role(), message.type(), message.tool(), text, message.finishReason()));
        }
        String cut = left > 0
                ? left + " more message parts not shown (" + MAX_CONVERSATION + " characters in all)"
                : null;
        return new Conversation(
                "GenAI level=" + level + ": " + messages.size() + " message parts of run " + runId
                                + (left > 0 ? ", " + left + " more cut" : "")
                                + ". Texts over " + MAX_MESSAGE + " characters are cut and end with '...'.",
                level, runId, agent, model, messages, cut);
    }

    /**
     * The conversation of a call in order: the input of its first model call (the system instructions and the
     * question), then the answer of each model call and the result of each tool call. The later model calls repeat the
     * history in their input, so only their answers are taken.
     */
    private static List<Message> transcript(JsonObject call) {
        List<Message> messages = new ArrayList<>();
        List<JsonObject> steps = objects(call, "steps");
        JsonObject content = content(call);
        if (TOOL_CALL.equals(call.getString("operation"))) {
            toolCall(messages, call);
            return messages;
        }
        if (steps.isEmpty()) {
            // a model call
            input(messages, content);
            messages.addAll(messages(content.get("outputMessages")));
            return messages;
        }
        JsonObject firstModelCall = steps.stream().filter(GenAiTraceReport::isModelCall).findFirst().orElse(null);
        // camel-openai records the input on the run, langchain4j-agent on its model calls
        input(messages, content.get("inputMessages") != null || firstModelCall == null ? content : content(firstModelCall));
        boolean answered = false;
        for (JsonObject step : steps) {
            if (isModelCall(step)) {
                List<Message> output = messages(content(step).get("outputMessages"));
                answered |= !output.isEmpty();
                messages.addAll(output);
            } else if (TOOL_CALL.equals(step.getString("operation"))) {
                toolResult(messages, step);
            }
        }
        if (!answered) {
            messages.addAll(messages(content.get("outputMessages")));
        }
        return messages;
    }

    private static void input(List<Message> messages, JsonObject content) {
        List<Message> input = messages(content.get("inputMessages"));
        if (input.stream().noneMatch(m -> "system".equals(m.role()))) {
            messages.addAll(messages(content.get("systemInstructions"), "system"));
        }
        messages.addAll(input);
    }

    private static void toolCall(List<Message> messages, JsonObject tool) {
        Object arguments = content(tool).get("toolArguments");
        if (arguments != null) {
            messages.add(new Message("assistant", "tool call", tool.getString("toolName"), arguments.toString(), null));
        }
        toolResult(messages, tool);
    }

    private static void toolResult(List<Message> messages, JsonObject tool) {
        Object result = content(tool).get("toolResult");
        if (result != null) {
            messages.add(new Message("tool", "tool result", tool.getString("toolName"), result.toString(), null));
        }
    }

    private static List<Message> messages(Object value) {
        return messages(value, null);
    }

    /**
     * The parts of GenAI messages JSON (as the console gives it, or as text when the length cap cut the JSON), or of
     * system instructions (a list of parts) with the given role.
     */
    private static List<Message> messages(Object value, String role) {
        Object json = value instanceof String text ? parse(text) : value;
        List<Message> messages = new ArrayList<>();
        if (json instanceof String text) {
            messages.add(new Message(role != null ? role : "assistant", "text", null, text, null));
        } else if (json instanceof JsonArray array) {
            for (Object item : array) {
                if (item instanceof JsonObject message && message.get("parts") instanceof JsonArray parts) {
                    String finish = message.getString("finish_reason");
                    for (Object part : parts) {
                        if (part instanceof JsonObject p) {
                            add(messages, part(message.getString("role"), p, finish));
                        }
                    }
                } else if (item instanceof JsonObject part && role != null) {
                    add(messages, part(role, part, null));
                }
            }
        }
        return messages;
    }

    /** Adds a part, but not an empty text (a model gives one beside its tool calls). */
    private static void add(List<Message> messages, Message message) {
        if (!"text".equals(message.type()) || message.text() != null && !message.text().isBlank()) {
            messages.add(message);
        }
    }

    private static Message part(String role, JsonObject part, String finishReason) {
        String type = part.getString("type");
        return switch (type != null ? type : "text") {
            case "tool_call" -> new Message(
                    role, "tool call", part.getString("name"), text(part.get("arguments")),
                    finishReason);
            case "tool_call_response" -> new Message(role, "tool result", null, text(part.get("response")), finishReason);
            case "reasoning" -> new Message(role, "reasoning", null, text(part.get("content")), finishReason);
            default -> new Message(role, "text", null, text(part.get("content")), finishReason);
        };
    }

    private static List<ModelCalls> modelCalls(JsonObject console) {
        List<ModelCalls> result = new ArrayList<>();
        for (JsonObject model : objects(console, "models")) {
            long calls = number(model, "calls");
            long failed = number(model, "errors");
            long cached = number(model, "cachedInputTokens");
            result.add(new ModelCalls(
                    model.getString("model") != null ? model.getString("model") : "unknown", model.getString("provider"),
                    calls, failed > 0 ? failed : null, number(model, "inputTokens"), number(model, "outputTokens"),
                    cached > 0 ? cached : null, calls > 0 ? number(model, "durationMs") / calls : 0));
        }
        return result;
    }

    private static String models(JsonObject console) {
        List<String> models = new ArrayList<>();
        for (JsonObject model : objects(console, "models")) {
            if (model.getString("model") != null && models.size() < MAX_MODELS_IN_SUMMARY) {
                models.add(model.getString("model"));
            }
        }
        return models.isEmpty() ? "" : " (" + String.join(", ", models) + ")";
    }

    private static Run run(JsonObject call) {
        List<RunStep> steps = new ArrayList<>();
        for (JsonObject step : objects(call, "steps")) {
            if (TOOL_CALL.equals(step.getString("operation"))) {
                JsonObject content = content(step);
                steps.add(new RunStep(
                        "tool call", step.getString("name"), optional(step, "durationMs"), null, null, null, null,
                        step.getString("error"), cut(text(content.get("toolArguments")), MAX_STEP_TEXT),
                        cut(text(content.get("toolResult")), MAX_STEP_TEXT)));
            } else {
                steps.add(new RunStep(
                        "model call", step.getString("name"), optional(step, "durationMs"),
                        optional(step, "inputTokens"), optional(step, "outputTokens"), positive(step, "cachedInputTokens"),
                        step.getString("finishReason"), step.getString("error"), null, null));
            }
        }
        long timestamp = number(call, "timestamp");
        return new Run(
                call.getString("id"), cut(call.getString("question"), MAX_QUESTION),
                cut(call.getString("answer"), MAX_QUESTION), call.getString("agentName"), call.getString("model"),
                call.getString("routeId"), call.getString("exchangeId"),
                timestamp > 0 ? Instant.ofEpochMilli(timestamp).toString() : null,
                Boolean.TRUE.equals(call.getBoolean("running")) ? Boolean.TRUE : null, optional(call, "durationMs"),
                number(call, "modelCalls"), number(call, "toolCalls"), number(call, "inputTokens"),
                number(call, "outputTokens"), positive(call, "cachedInputTokens"), call.getString("error"), steps);
    }

    private static boolean isModelCall(JsonObject call) {
        String operation = call.getString("operation");
        return !AGENT_RUN.equals(operation) && !TOOL_CALL.equals(operation);
    }

    private static boolean isAll(String routeFilter) {
        return routeFilter == null || routeFilter.isBlank() || "*".equals(routeFilter);
    }

    private static JsonObject content(JsonObject call) {
        JsonObject content = call.getMap("content");
        return content != null ? content : new JsonObject();
    }

    private static List<JsonObject> objects(JsonObject json, String key) {
        List<JsonObject> list = new ArrayList<>();
        if (json != null && json.get(key) instanceof List<?> items) {
            for (Object item : items) {
                if (item instanceof Map<?, ?> map) {
                    list.add(map instanceof JsonObject jo ? jo : new JsonObject(castMap(map)));
                }
            }
        }
        return list;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }

    private static Object parse(String text) {
        try {
            return Jsoner.deserialize(text);
        } catch (Exception e) {
            // cut by the length cap: the text as it is
            return text;
        }
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        return value instanceof JsonObject || value instanceof JsonArray ? Jsoner.serialize(value) : value.toString();
    }

    private static long number(JsonObject json, String key) {
        return json.get(key) instanceof Number n ? n.longValue() : 0;
    }

    private static Long optional(JsonObject json, String key) {
        return json.get(key) instanceof Number n ? n.longValue() : null;
    }

    private static Long positive(JsonObject json, String key) {
        Long value = optional(json, key);
        return value != null && value > 0 ? value : null;
    }

    /** The text on one line, cut at {@code max} characters with "...". */
    static String cut(String text, int max) {
        if (text == null) {
            return null;
        }
        String line = text.replaceAll("\\s+", " ").strip();
        if (line.endsWith(TRUNCATED)) {
            line = line.substring(0, line.length() - TRUNCATED.length()) + "...";
        }
        return line.length() > max ? line.substring(0, Math.max(0, max - 3)) + "..." : line;
    }
}
