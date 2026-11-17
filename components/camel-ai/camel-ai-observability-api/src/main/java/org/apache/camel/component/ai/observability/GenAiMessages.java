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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

/**
 * Chat messages in the shape of the OpenTelemetry GenAI semantic conventions, for the {@code gen_ai.input.messages},
 * {@code gen_ai.output.messages} and {@code gen_ai.system_instructions} span attributes.
 * <p/>
 * Producers build these lazily: they are only rendered when the observability level is
 * {@link GenAiObservabilityLevel#FULL}.
 *
 * @since 4.24
 */
public final class GenAiMessages {

    /** Message roles of the GenAI semantic conventions. */
    public static final String SYSTEM = "system";
    public static final String USER = "user";
    public static final String ASSISTANT = "assistant";
    public static final String TOOL = "tool";

    private static final String TRUNCATED = "...[truncated]";

    private final List<JsonObject> messages = new ArrayList<>();

    /**
     * Adds a message with a single text part. A {@code null} text adds a message without parts.
     */
    public GenAiMessages text(String role, String text) {
        JsonObject message = message(role);
        if (text != null) {
            parts(message).add(textPart(text));
        }
        messages.add(message);
        return this;
    }

    /**
     * Adds a tool call part to the last message, which must have been added by the caller (normally an assistant
     * message). When there is no message yet, an assistant message is added.
     *
     * @param id        the tool call id, may be {@code null}
     * @param name      the tool name
     * @param arguments the arguments as a JSON string, as returned by the model
     */
    public GenAiMessages toolCall(String id, String name, String arguments) {
        if (messages.isEmpty()) {
            messages.add(message(ASSISTANT));
        }
        JsonObject part = new JsonObject();
        part.put("type", "tool_call");
        if (id != null) {
            part.put("id", id);
        }
        part.put("name", name);
        part.put("arguments", parseJsonOrText(arguments));
        parts(messages.get(messages.size() - 1)).add(part);
        return this;
    }

    /**
     * Adds a reasoning (thinking) part to the last message, which must be an assistant message added by the caller.
     */
    public GenAiMessages reasoning(String text) {
        if (text == null || text.isBlank()) {
            return this;
        }
        if (messages.isEmpty()) {
            messages.add(message(ASSISTANT));
        }
        JsonObject part = new JsonObject();
        part.put("type", "reasoning");
        part.put("content", text);
        parts(messages.get(messages.size() - 1)).add(0, part);
        return this;
    }

    /**
     * Adds a {@code tool} message carrying the result of a tool call.
     */
    public GenAiMessages toolResult(String id, String result) {
        JsonObject message = message(TOOL);
        JsonObject part = new JsonObject();
        part.put("type", "tool_call_response");
        if (id != null) {
            part.put("id", id);
        }
        part.put("response", result);
        parts(message).add(part);
        messages.add(message);
        return this;
    }

    /**
     * Sets the finish reason on the last message, as the semantic conventions require for output messages.
     */
    public GenAiMessages finishReason(String finishReason) {
        if (!messages.isEmpty() && finishReason != null) {
            messages.get(messages.size() - 1).put("finish_reason", finishReason.toLowerCase(Locale.ROOT));
        }
        return this;
    }

    public boolean isEmpty() {
        return messages.isEmpty();
    }

    /**
     * Renders the messages as a JSON array, cutting every text longer than {@code maxLength} characters.
     */
    public String toJson(int maxLength) {
        JsonArray array = new JsonArray();
        for (JsonObject message : messages) {
            array.add(truncate(message, maxLength));
        }
        return Jsoner.serialize(array);
    }

    /**
     * Renders system instructions ({@code gen_ai.system_instructions}): a JSON array of text parts.
     */
    public static String systemInstructions(String text, int maxLength) {
        JsonArray array = new JsonArray();
        array.add(truncate(textPart(text), maxLength));
        return Jsoner.serialize(array);
    }

    /**
     * Cuts a text longer than {@code maxLength} characters, marking the cut.
     */
    public static String truncate(String text, int maxLength) {
        if (text == null || maxLength <= 0 || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + TRUNCATED;
    }

    private static JsonObject message(String role) {
        JsonObject message = new JsonObject();
        message.put("role", role);
        message.put("parts", new JsonArray());
        return message;
    }

    private static JsonArray parts(JsonObject message) {
        return (JsonArray) message.get("parts");
    }

    private static JsonObject textPart(String text) {
        JsonObject part = new JsonObject();
        part.put("type", "text");
        part.put("content", text);
        return part;
    }

    private static Object parseJsonOrText(String json) {
        if (json == null || json.isBlank()) {
            return new JsonObject();
        }
        try {
            return Jsoner.deserialize(json);
        } catch (Exception e) {
            return json;
        }
    }

    @SuppressWarnings("unchecked")
    private static Object truncate(Object value, int maxLength) {
        if (value instanceof String s) {
            return truncate(s, maxLength);
        }
        if (value instanceof Map<?, ?> map) {
            JsonObject copy = new JsonObject();
            ((Map<String, Object>) map).forEach((k, v) -> copy.put(k, truncate(v, maxLength)));
            return copy;
        }
        if (value instanceof List<?> list) {
            JsonArray copy = new JsonArray();
            list.forEach(v -> copy.add(truncate(v, maxLength)));
            return copy;
        }
        return value;
    }
}
