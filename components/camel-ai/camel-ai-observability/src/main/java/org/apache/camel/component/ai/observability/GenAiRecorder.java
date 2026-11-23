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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

/**
 * Keeps the GenAI calls of a CamelContext in memory for the {@code genai} dev console: the totals since start (per
 * model, and the agent runs and tool calls), and the newest top-level calls (agent runs, and model calls outside a run)
 * with their steps in the order they started.
 * <p>
 * It records only when the dev console is enabled ({@code camel.main.devConsoleEnabled}, on in the dev profile), so it
 * needs no tracer. Message content is only there at level {@link GenAiObservabilityLevel#FULL}, capped as on the spans.
 */
final class GenAiRecorder {

    /** The newest top-level calls kept. */
    static final int MAX_CALLS = 50;
    /** The steps kept per top-level call; the steps after are only counted. */
    static final int MAX_STEPS = 200;
    /** The length of the question and the answer of a run in the list of calls. */
    static final int MAX_PREVIEW = 200;

    // the call open on the exchange (an agent run or a tool call), as the span stack of camel-telemetry: the copies of
    // the exchange made for tool calls carry it, so the calls started on them nest under the run
    private static final String ACTIVE_CALL = "genai.activeCall";
    private static final Object LOCK = new Object();

    private final CamelContext camelContext;
    // all guarded by this
    private final Deque<Call> calls = new ArrayDeque<>();
    private final Map<String, ModelTotals> models = new LinkedHashMap<>();
    private long ids;
    private long agentRuns;
    private long toolCalls;
    private long modelCalls;
    private long errors;
    private long droppedCalls;

    private GenAiRecorder(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    /** Whether the calls are recorded: when the dev console is enabled. */
    static boolean isRecording(CamelContext camelContext) {
        return Boolean.TRUE.equals(camelContext.isDevConsole());
    }

    /** The recorder of the context, kept as a context plugin so it goes away with the context. */
    static GenAiRecorder get(CamelContext camelContext) {
        ExtendedCamelContext extension = camelContext.getCamelContextExtension();
        GenAiRecorder recorder = extension.getContextPlugin(GenAiRecorder.class);
        if (recorder == null) {
            synchronized (LOCK) {
                recorder = extension.getContextPlugin(GenAiRecorder.class);
                if (recorder == null) {
                    recorder = new GenAiRecorder(camelContext);
                    extension.addContextPlugin(GenAiRecorder.class, recorder);
                }
            }
        }
        return recorder;
    }

    /** The agent run or tool call open on the exchange, or {@code null}. */
    static Call active(Exchange exchange) {
        Frame frame = exchange.getProperty(ACTIVE_CALL, Frame.class);
        return frame != null ? frame.call : null;
    }

    /** Makes an agent run or tool call the one open on the exchange, until {@link #pop(Exchange, Call)}. */
    static void push(Exchange exchange, Call call) {
        exchange.setProperty(ACTIVE_CALL, new Frame(call, exchange.getProperty(ACTIVE_CALL, Frame.class)));
    }

    /** Ends the call open on the exchange, when it is the given one. */
    static void pop(Exchange exchange, Call call) {
        Frame frame = exchange.getProperty(ACTIVE_CALL, Frame.class);
        if (frame != null && frame.call == call) {
            if (frame.parent != null) {
                exchange.setProperty(ACTIVE_CALL, frame.parent);
            } else {
                exchange.removeProperty(ACTIVE_CALL);
            }
        }
    }

    /** Records the start of a call, as a step of the parent when there is one, else as a new top-level call. */
    synchronized Call start(GenAiObservationContext context, Exchange exchange, Call parent) {
        Call call = new Call(String.valueOf(++ids), context, exchange, parent);
        if (parent != null) {
            Call root = parent.root();
            if (root.stepCount < MAX_STEPS) {
                root.steps.add(call);
                root.stepCount++;
            } else {
                root.droppedSteps++;
            }
        } else {
            calls.addFirst(call);
            if (calls.size() > MAX_CALLS) {
                calls.removeLast();
                droppedCalls++;
            }
        }
        return call;
    }

    /** Records the end of a call, and counts it in the totals. */
    synchronized void end(
            Call call, GenAiUsage usage, Long cachedInputTokens, Throwable error, Map<String, String> content,
            long durationMs) {
        call.durationMs = durationMs;
        if (usage != null) {
            call.inputTokens = usage.inputTokens();
            call.outputTokens = usage.outputTokens();
            call.finishReason = usage.finishReason();
            if (error == null && ObjectHelper.isNotEmpty(usage.responseModel())) {
                call.responseModel = usage.responseModel();
            }
        }
        call.cachedInputTokens = cachedInputTokens;
        call.errorType = error != null ? error.getClass().getSimpleName() : null;
        call.content.putAll(content);
        if (error != null) {
            errors++;
        }
        switch (call.kind()) {
            case AGENT_RUN -> agentRuns++;
            case TOOL_CALL -> toolCalls++;
            default -> {
                modelCalls++;
                models.computeIfAbsent(call.provider() + "/" + call.model(), k -> new ModelTotals(call)).add(call);
            }
        }
    }

    /**
     * The totals and the newest top-level calls, newest first.
     *
     * @param limit   the top-level calls to list
     * @param content whether to add the message content (only recorded at level full)
     */
    synchronized JsonObject toJson(int limit, boolean content) {
        JsonObject root = summary();
        JsonArray list = new JsonArray();
        for (Call call : calls) {
            if (list.size() >= limit) {
                break;
            }
            list.add(call.toJson(content));
        }
        root.put("calls", list);
        root.put("moreCalls", calls.size() - list.size() + droppedCalls);
        return root;
    }

    /** One call (a top-level call, or a step of one) with its content, or {@code null} when it is no longer kept. */
    synchronized JsonObject toJson(String id) {
        for (Call call : calls) {
            if (call.id.equals(id)) {
                return call.toJson(true);
            }
            for (Call step : call.steps) {
                if (step.id.equals(id)) {
                    return step.toJson(true);
                }
            }
        }
        return null;
    }

    private JsonObject summary() {
        JsonObject root = new JsonObject();
        root.put("recording", isRecording(camelContext));
        root.put("level", GenAiObservability.level(camelContext).name().toLowerCase(Locale.ROOT));
        root.put("contentMaxLength", GenAiObservability.contentMaxLength(camelContext));
        root.put("modelCalls", modelCalls);
        root.put("agentRuns", agentRuns);
        root.put("toolCalls", toolCalls);
        root.put("errors", errors);
        long in = 0;
        long out = 0;
        long cached = 0;
        JsonArray list = new JsonArray();
        for (ModelTotals totals : models.values()) {
            in += totals.inputTokens;
            out += totals.outputTokens;
            cached += totals.cachedInputTokens;
            list.add(totals.toJson());
        }
        root.put("inputTokens", in);
        root.put("outputTokens", out);
        root.put("cachedInputTokens", cached);
        root.put("models", list);
        return root;
    }

    enum Kind {
        AGENT_RUN,
        TOOL_CALL,
        MODEL_CALL
    }

    private record Frame(Call call, Frame parent) {
    }

    /** One GenAI call; changed only under the lock of its recorder. */
    static final class Call {

        private final String id;
        private final Call parent;
        private final GenAiOperationName operation;
        private final String name;
        private final String agentName;
        private final String toolName;
        private final String toolCallId;
        private final String conversationId;
        private final String system;
        private final String requestModel;
        private final String component;
        private final String routeId;
        private final String exchangeId;
        private final long timestamp = System.currentTimeMillis();
        // the steps of a top-level call, in the order they started (all the levels below it)
        private final List<Call> steps = new ArrayList<>();
        private final Map<String, String> content = new LinkedHashMap<>();
        private int stepCount;
        private int droppedSteps;
        private Long durationMs;
        private String responseModel;
        private Long inputTokens;
        private Long outputTokens;
        private Long cachedInputTokens;
        private String finishReason;
        private String errorType;

        private Call(String id, GenAiObservationContext context, Exchange exchange, Call parent) {
            this.id = id;
            this.parent = parent;
            this.operation = context.operationName();
            this.name = context.spanName();
            this.agentName = context.agentName();
            this.toolName = context.toolName();
            this.toolCallId = context.toolCallId();
            this.conversationId = context.conversationId();
            this.system = context.system();
            this.requestModel = context.requestModel();
            this.component = context.componentScheme();
            this.routeId = ExchangeHelper.getAtRouteId(exchange);
            this.exchangeId = exchange.getExchangeId();
        }

        Kind kind() {
            if (operation == GenAiOperationName.INVOKE_AGENT) {
                return Kind.AGENT_RUN;
            }
            return operation == GenAiOperationName.EXECUTE_TOOL ? Kind.TOOL_CALL : Kind.MODEL_CALL;
        }

        private Call root() {
            Call call = this;
            while (call.parent != null) {
                call = call.parent;
            }
            return call;
        }

        private String provider() {
            return ObjectHelper.isEmpty(system) || "unknown".equals(system) ? null : system;
        }

        private String model() {
            String model = responseModel != null ? responseModel : requestModel;
            return ObjectHelper.isEmpty(model) || "unknown".equals(model) ? null : model;
        }

        private JsonObject toJson(boolean withContent) {
            JsonObject json = fields();
            if (parent == null) {
                JsonArray list = new JsonArray();
                long in = 0;
                long out = 0;
                long cached = 0;
                long modelMs = 0;
                long toolMs = 0;
                int models = 0;
                int tools = 0;
                for (Call step : steps) {
                    JsonObject stepJson = step.fields();
                    if (withContent) {
                        putContent(stepJson, step.content);
                    }
                    list.add(stepJson);
                    if (step.kind() == Kind.MODEL_CALL) {
                        models++;
                        in += zero(step.inputTokens);
                        out += zero(step.outputTokens);
                        cached += zero(step.cachedInputTokens);
                        modelMs += zero(step.durationMs);
                    } else if (step.kind() == Kind.TOOL_CALL) {
                        tools++;
                        toolMs += zero(step.durationMs);
                    }
                }
                if (kind() == Kind.AGENT_RUN) {
                    // the model calls carry the token usage of the run
                    json.put("modelCalls", models);
                    json.put("toolCalls", tools);
                    json.put("inputTokens", in);
                    json.put("outputTokens", out);
                    json.put("cachedInputTokens", cached);
                    json.put("modelMs", modelMs);
                    json.put("toolMs", toolMs);
                }
                if (!list.isEmpty() || kind() != Kind.MODEL_CALL) {
                    json.put("steps", list);
                }
                if (droppedSteps > 0) {
                    json.put("droppedSteps", droppedSteps);
                }
                if (withContent) {
                    putContent(json, content);
                    putIfNotNull(json, "question", preview(firstText(GenAiMessages.USER, true)));
                    putIfNotNull(json, "answer", preview(firstText(GenAiMessages.ASSISTANT, false)));
                }
            } else if (withContent) {
                putContent(json, content);
            }
            return json;
        }

        private JsonObject fields() {
            JsonObject json = new JsonObject();
            json.put("id", id);
            putIfNotNull(json, "parentId", parent != null ? parent.id : null);
            json.put("operation", operation.value());
            json.put("name", name);
            putIfNotNull(json, "agentName", agentName);
            putIfNotNull(json, "toolName", toolName);
            putIfNotNull(json, "toolCallId", toolCallId);
            putIfNotNull(json, "provider", provider());
            if (kind() != Kind.TOOL_CALL) {
                putIfNotNull(json, "model", model());
            }
            putIfNotNull(json, "component", component);
            putIfNotNull(json, "conversationId", conversationId);
            putIfNotNull(json, "routeId", routeId);
            putIfNotNull(json, "exchangeId", exchangeId);
            json.put("timestamp", timestamp);
            json.put("running", durationMs == null);
            putIfNotNull(json, "durationMs", durationMs);
            if (kind() == Kind.MODEL_CALL) {
                putIfNotNull(json, "inputTokens", inputTokens);
                putIfNotNull(json, "outputTokens", outputTokens);
                putIfNotNull(json, "cachedInputTokens", cachedInputTokens);
            }
            putIfNotNull(json, "finishReason", finishReason);
            putIfNotNull(json, "error", errorType);
            return json;
        }

        /**
         * The first text of a role in the messages of the run, else of its model calls: the question is the first user
         * message (searched from the start), the answer the last assistant message (searched from the end).
         */
        private String firstText(String role, boolean fromStart) {
            List<String> sources = new ArrayList<>();
            String own = content.get(fromStart ? "inputMessages" : "outputMessages");
            if (own != null) {
                sources.add(own);
            }
            for (Call step : steps) {
                if (step.kind() == Kind.MODEL_CALL) {
                    String messages = step.content.get(fromStart ? "inputMessages" : "outputMessages");
                    if (messages != null) {
                        sources.add(messages);
                    }
                }
            }
            if (!fromStart) {
                Collections.reverse(sources);
            }
            for (String messages : sources) {
                String text = text(messages, role, fromStart);
                if (text != null) {
                    return text;
                }
            }
            return null;
        }
    }

    private static final class ModelTotals {
        private final String provider;
        private final String model;
        private long calls;
        private long errors;
        private long inputTokens;
        private long outputTokens;
        private long cachedInputTokens;
        private long durationMs;

        private ModelTotals(Call call) {
            this.provider = call.provider();
            this.model = call.model();
        }

        private void add(Call call) {
            calls++;
            if (call.errorType != null) {
                errors++;
            }
            inputTokens += zero(call.inputTokens);
            outputTokens += zero(call.outputTokens);
            cachedInputTokens += zero(call.cachedInputTokens);
            durationMs += zero(call.durationMs);
        }

        private JsonObject toJson() {
            JsonObject json = new JsonObject();
            putIfNotNull(json, "provider", provider);
            putIfNotNull(json, "model", model);
            json.put("calls", calls);
            json.put("errors", errors);
            json.put("inputTokens", inputTokens);
            json.put("outputTokens", outputTokens);
            json.put("cachedInputTokens", cachedInputTokens);
            json.put("durationMs", durationMs);
            return json;
        }
    }

    /** The content of a call, the messages as JSON (they are recorded in the GenAI messages format). */
    private static void putContent(JsonObject json, Map<String, String> content) {
        if (content.isEmpty()) {
            return;
        }
        JsonObject map = new JsonObject();
        content.forEach((key, value) -> map.put(key, key.endsWith("Messages") || "toolDefinitions".equals(key)
                ? parse(value) : value));
        json.put("content", map);
    }

    private static Object parse(String value) {
        try {
            return Jsoner.deserialize(value);
        } catch (Exception e) {
            // cut by the length cap: given as the text it is
            return value;
        }
    }

    /** The first (or last) text part of a role in GenAI messages JSON, or {@code null}. */
    static String text(String messages, String role, boolean fromStart) {
        if (!(parse(messages) instanceof JsonArray array)) {
            return null;
        }
        String found = null;
        for (Object item : array) {
            if (item instanceof JsonObject message && role.equals(message.getString("role"))
                    && message.get("parts") instanceof JsonArray parts) {
                for (Object part : parts) {
                    if (part instanceof JsonObject p && "text".equals(p.getString("type"))
                            && ObjectHelper.isNotEmpty(p.getString("content"))) {
                        if (fromStart) {
                            return p.getString("content");
                        }
                        found = p.getString("content");
                    }
                }
            }
        }
        return found;
    }

    private static String preview(String text) {
        if (text == null) {
            return null;
        }
        String line = text.strip().replaceAll("\\s+", " ");
        return line.length() > MAX_PREVIEW ? line.substring(0, MAX_PREVIEW) + "..." : line;
    }

    private static long zero(Long value) {
        return value != null ? value : 0;
    }

    private static void putIfNotNull(JsonObject json, String key, Object value) {
        if (value != null) {
            json.put(key, value);
        }
    }
}
