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

import java.util.Locale;

/**
 * How much GenAI telemetry the Camel AI producers record. Each level includes everything recorded by the previous one.
 * <p/>
 * The higher levels add spans and span attributes; message content and ids never become metric tags. The token totals
 * are the same at every level, but at {@link #DETAILED} the model calls of an agent run are timed one by one.
 *
 * @since 4.24
 */
public enum GenAiObservabilityLevel {

    /**
     * One span per LLM call with the operation, provider, model, token usage and finish reason (the default).
     */
    BASIC,

    /**
     * Adds the structure of agent runs: an {@code invoke_agent} span with one generation span per LLM call and an
     * {@code execute_tool} span per tool call, plus request parameters, cached tokens and the conversation id.
     */
    DETAILED,

    /**
     * Adds message content: prompts, completions, system instructions and tool call arguments and results. Each text is
     * capped at {@link GenAiObservabilityProperties#CONTENT_MAX_LENGTH} characters. Content may hold personal or
     * confidential data, so enable it only when the tracing backend is allowed to store it.
     */
    FULL;

    /**
     * Whether this level records everything that the given level records.
     */
    public boolean includes(GenAiObservabilityLevel other) {
        return ordinal() >= other.ordinal();
    }

    /**
     * Parses a level name, ignoring case. A {@code null} or blank value is {@link #BASIC}.
     *
     * @throws IllegalArgumentException if the value is not a level name
     */
    public static GenAiObservabilityLevel parse(String value) {
        if (value == null || value.isBlank()) {
            return BASIC;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Unknown GenAI observability level '" + value + "', expected one of basic, detailed, full", e);
        }
    }
}
