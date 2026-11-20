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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.component.ai.observability.GenAiAttributes;
import org.apache.camel.component.ai.observability.GenAiMessages;
import org.apache.camel.component.ai.observability.GenAiOperationName;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

/**
 * The conversation of a GenAI span, from the message attributes the Camel AI producers record at observability level
 * {@code full} ({@code gen_ai.system_instructions}, {@code gen_ai.input.messages}, {@code gen_ai.output.messages}, and
 * the arguments and result of a tool call), in the shape of the OpenTelemetry GenAI semantic conventions: messages with
 * a {@code role} and {@code parts} of type {@code text}, {@code reasoning}, {@code tool_call} and
 * {@code tool_call_response}.
 * <p/>
 * An agent run ({@code invoke_agent}) shows the input and output of its last model call: that input already holds the
 * whole conversation of the run, with every tool call and result.
 */
final class GenAiTranscript {

    enum Kind {
        TEXT,
        REASONING,
        TOOL_CALL,
        TOOL_RESULT
    }

    /**
     * A part of a message: a text, a tool call with its arguments, or a tool result. {@code id} pairs a tool call with
     * its result.
     */
    record Part(Kind kind, String text, String toolName, String id) {

        Part(Kind kind, String text) {
            this(kind, text, null, null);
        }
    }

    /** A message: its role (system, user, assistant, tool), its parts, and the finish reason of an output message. */
    record Turn(String role, List<Part> parts, String finishReason) {
    }

    /** The mark {@code GenAiMessages} puts at the end of a text cut at {@code contentMaxLength}. */
    static final String TRUNCATED = "...[truncated]";

    private static final List<String> CONTENT_ATTRIBUTES = List.of(
            GenAiAttributes.INPUT_MESSAGES, GenAiAttributes.OUTPUT_MESSAGES, GenAiAttributes.SYSTEM_INSTRUCTIONS,
            GenAiAttributes.TOOL_CALL_ARGUMENTS, GenAiAttributes.TOOL_CALL_RESULT);
    private static final int MAX_PARENT_HOPS = 50;

    private final List<Turn> turns;

    private GenAiTranscript(List<Turn> turns) {
        this.turns = List.copyOf(turns);
    }

    List<Turn> turns() {
        return turns;
    }

    boolean isEmpty() {
        return turns.isEmpty();
    }

    /**
     * The conversation as Markdown: a heading per message, the texts as they are (a model answers in Markdown), and the
     * tool arguments and results as code blocks.
     */
    String toMarkdown() {
        StringBuilder sb = new StringBuilder();
        for (Turn turn : turns) {
            sb.append("### ").append(heading(turn)).append("\n\n");
            for (Part part : turn.parts()) {
                switch (part.kind()) {
                    case TEXT -> sb.append(markTruncated(part.text())).append("\n\n");
                    case REASONING -> {
                        for (String line : ("*Thinking:* " + markTruncated(part.text())).split("\n")) {
                            sb.append("> ").append(line).append('\n');
                        }
                        sb.append('\n');
                    }
                    case TOOL_CALL -> {
                        sb.append("Calls **").append(part.toolName() != null ? part.toolName() : "a tool").append("**\n\n");
                        codeBlock(sb, part.text());
                    }
                    case TOOL_RESULT -> codeBlock(sb, part.text());
                }
            }
        }
        return sb.toString().strip();
    }

    private static String heading(Turn turn) {
        String role = turn.role() != null && !turn.role().isEmpty() ? turn.role() : "message";
        String heading = Character.toUpperCase(role.charAt(0)) + role.substring(1);
        if (GenAiMessages.TOOL.equals(role)) {
            String name = turn.parts().isEmpty() ? null : turn.parts().get(0).toolName();
            if (name != null) {
                heading += " " + name;
            }
        }
        if (turn.finishReason() != null) {
            heading += " · " + turn.finishReason();
        }
        return heading;
    }

    private static String markTruncated(String text) {
        if (text == null) {
            return "*(empty)*";
        }
        return text.endsWith(TRUNCATED)
                ? text.substring(0, text.length() - TRUNCATED.length()) + " … *(truncated)*"
                : text;
    }

    /** A tool argument or result: pretty JSON when it is JSON, else the text, in a fence it cannot close. */
    private static void codeBlock(StringBuilder sb, String text) {
        if (text == null) {
            sb.append("*(empty)*\n\n");
            return;
        }
        boolean truncated = text.endsWith(TRUNCATED);
        String body = truncated ? text.substring(0, text.length() - TRUNCATED.length()) : text;
        String language = "";
        if (!truncated) {
            try {
                Object parsed = Jsoner.deserialize(body);
                if (parsed instanceof Map || parsed instanceof List) {
                    body = Jsoner.prettyPrint(body, 2);
                    language = "json";
                }
            } catch (Exception e) {
                // not JSON: shown as it is
            }
        }
        String fence = body.contains("```") ? "````" : "```";
        sb.append(fence).append(language).append('\n').append(body.strip()).append('\n').append(fence).append("\n\n");
        if (truncated) {
            sb.append("*(truncated)*\n\n");
        }
    }

    /** Whether the span is a GenAI span: a model call, an agent run or a tool call. */
    static boolean isGenAiSpan(SpanEntry span) {
        return span != null && span.attributes() != null
                && span.attributes().containsKey(GenAiAttributes.OPERATION_NAME);
    }

    /** Whether the span carries message content, recorded at observability level {@code full}. */
    static boolean hasContent(SpanEntry span) {
        if (!isGenAiSpan(span)) {
            return false;
        }
        for (String key : CONTENT_ATTRIBUTES) {
            if (span.attributes().containsKey(key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The conversation of a GenAI span. {@code traceSpans} are the spans of its trace, where the model calls of an
     * agent run are found.
     */
    static GenAiTranscript of(SpanEntry span, List<SpanEntry> traceSpans) {
        if (!isGenAiSpan(span)) {
            return new GenAiTranscript(List.of());
        }
        String operation = String.valueOf(span.attributes().get(GenAiAttributes.OPERATION_NAME));
        if (GenAiOperationName.EXECUTE_TOOL.value().equals(operation)) {
            return toolCall(span.attributes());
        }
        SpanEntry source = span;
        if (GenAiOperationName.INVOKE_AGENT.value().equals(operation)) {
            SpanEntry lastCall = lastModelCallOf(span, traceSpans);
            if (lastCall != null) {
                source = lastCall;
            }
        }
        List<Turn> turns = new ArrayList<>();
        // the system instructions of the run, when its model call has none of its own
        String instructions = text(source.attributes(), GenAiAttributes.SYSTEM_INSTRUCTIONS);
        if (instructions == null) {
            instructions = text(span.attributes(), GenAiAttributes.SYSTEM_INSTRUCTIONS);
        }
        List<Turn> input = parseMessages(text(source.attributes(), GenAiAttributes.INPUT_MESSAGES));
        if (instructions != null && !startsWithSystem(input)) {
            List<Turn> system = parseMessages(instructions);
            List<Part> parts = system.isEmpty()
                    ? List.of(new Part(Kind.TEXT, instructions))
                    : system.get(0).parts();
            turns.add(new Turn(GenAiMessages.SYSTEM, parts, null));
        }
        turns.addAll(input);
        turns.addAll(parseMessages(text(source.attributes(), GenAiAttributes.OUTPUT_MESSAGES)));
        return new GenAiTranscript(nameToolResults(turns));
    }

    private static GenAiTranscript toolCall(Map<String, Object> attrs) {
        String name = text(attrs, GenAiAttributes.TOOL_NAME);
        List<Turn> turns = new ArrayList<>();
        String arguments = text(attrs, GenAiAttributes.TOOL_CALL_ARGUMENTS);
        if (arguments != null) {
            turns.add(new Turn(GenAiMessages.ASSISTANT, List.of(new Part(Kind.TOOL_CALL, arguments, name, null)), null));
        }
        String result = text(attrs, GenAiAttributes.TOOL_CALL_RESULT);
        if (result != null) {
            turns.add(new Turn(GenAiMessages.TOOL, List.of(new Part(Kind.TOOL_RESULT, result, name, null)), null));
        }
        return new GenAiTranscript(turns);
    }

    /** The model call with messages that started last inside the agent run. */
    private static SpanEntry lastModelCallOf(SpanEntry agent, List<SpanEntry> traceSpans) {
        if (traceSpans == null || agent.spanId() == null) {
            return null;
        }
        Map<String, SpanEntry> byId = new HashMap<>();
        for (SpanEntry s : traceSpans) {
            if (s.spanId() != null) {
                byId.put(s.spanId(), s);
            }
        }
        SpanEntry last = null;
        for (SpanEntry s : traceSpans) {
            if (s != agent && isGenAiSpan(s) && s.attributes().containsKey(GenAiAttributes.INPUT_MESSAGES)
                    && !isAgentOrTool(s) && isInside(s, agent.spanId(), byId)
                    && (last == null || s.startEpochNanos() >= last.startEpochNanos())) {
                last = s;
            }
        }
        return last;
    }

    private static boolean isAgentOrTool(SpanEntry span) {
        Object operation = span.attributes().get(GenAiAttributes.OPERATION_NAME);
        return GenAiOperationName.INVOKE_AGENT.value().equals(operation)
                || GenAiOperationName.EXECUTE_TOOL.value().equals(operation);
    }

    private static boolean isInside(SpanEntry span, String ancestorId, Map<String, SpanEntry> byId) {
        SpanEntry current = span;
        for (int hops = 0; hops < MAX_PARENT_HOPS && current != null && !current.isRoot(); hops++) {
            if (ancestorId.equals(current.parentSpanId())) {
                return true;
            }
            current = byId.get(current.parentSpanId());
        }
        return false;
    }

    private static boolean startsWithSystem(List<Turn> turns) {
        return !turns.isEmpty() && GenAiMessages.SYSTEM.equals(turns.get(0).role());
    }

    /** A tool result names its tool only by the id of the call: give it the name of that call. */
    private static List<Turn> nameToolResults(List<Turn> turns) {
        Map<String, String> namesById = new HashMap<>();
        List<Turn> named = new ArrayList<>(turns.size());
        for (Turn turn : turns) {
            List<Part> parts = new ArrayList<>(turn.parts().size());
            for (Part part : turn.parts()) {
                if (part.kind() == Kind.TOOL_CALL && part.id() != null) {
                    namesById.put(part.id(), part.toolName());
                } else if (part.kind() == Kind.TOOL_RESULT && part.toolName() == null && part.id() != null) {
                    part = new Part(Kind.TOOL_RESULT, part.text(), namesById.get(part.id()), part.id());
                }
                parts.add(part);
            }
            named.add(new Turn(turn.role(), parts, turn.finishReason()));
        }
        return named;
    }

    /**
     * Parses a {@code gen_ai.*.messages} or {@code gen_ai.system_instructions} attribute. A value that is not a JSON
     * array of messages is shown as one text.
     */
    static List<Turn> parseMessages(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        Object parsed;
        try {
            parsed = Jsoner.deserialize(json);
        } catch (Exception e) {
            return List.of(new Turn(GenAiMessages.USER, List.of(new Part(Kind.TEXT, json)), null));
        }
        if (!(parsed instanceof JsonArray array)) {
            return List.of(new Turn(GenAiMessages.USER, List.of(new Part(Kind.TEXT, json)), null));
        }
        List<Turn> turns = new ArrayList<>();
        for (Object item : array) {
            if (item instanceof JsonObject message) {
                if (message.containsKey("role")) {
                    turns.add(new Turn(
                            message.getString("role"), parseParts(message.getCollection("parts")),
                            message.getString("finish_reason")));
                } else {
                    // system instructions: an array of parts
                    turns.add(new Turn(GenAiMessages.SYSTEM, parseParts(List.of(message)), null));
                }
            }
        }
        return turns;
    }

    private static List<Part> parseParts(Collection<?> parts) {
        List<Part> result = new ArrayList<>();
        if (parts == null) {
            return result;
        }
        for (Object item : parts) {
            if (!(item instanceof JsonObject part)) {
                continue;
            }
            String type = part.getStringOrDefault("type", "text");
            switch (type) {
                case "tool_call" -> result.add(new Part(
                        Kind.TOOL_CALL, jsonText(part.get("arguments")),
                        part.getString("name"), part.getString("id")));
                case "tool_call_response" -> result.add(new Part(
                        Kind.TOOL_RESULT, jsonText(part.get("response")),
                        null, part.getString("id")));
                case "reasoning" -> result.add(new Part(Kind.REASONING, part.getString("content")));
                default -> result.add(new Part(Kind.TEXT, jsonText(part.get("content"))));
            }
        }
        return result;
    }

    private static String jsonText(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String s) {
            return s;
        }
        return Jsoner.serialize(value);
    }

    private static String text(Map<String, Object> attrs, String key) {
        Object value = attrs != null ? attrs.get(key) : null;
        if (value == null) {
            return null;
        }
        String text = value.toString();
        return text.isBlank() ? null : text;
    }
}
