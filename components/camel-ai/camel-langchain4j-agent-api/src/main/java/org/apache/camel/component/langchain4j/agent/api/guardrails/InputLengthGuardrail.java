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
package org.apache.camel.component.langchain4j.agent.api.guardrails;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailResult;

/**
 * Input guardrail that validates the length of user messages.
 *
 * <p>
 * This guardrail prevents excessively long messages from being sent to the LLM, which can help control costs, prevent
 * potential abuse, and ensure consistent response quality.
 * </p>
 *
 * <p>
 * Example usage:
 * </p>
 *
 * <pre>{@code
 * AgentConfiguration config = new AgentConfiguration()
 *         .withChatModel(chatModel)
 *         .withInputGuardrailClasses(List.of(InputLengthGuardrail.class));
 * }</pre>
 *
 * <p>
 * To customize the limits, extend this class or use {@link InputLengthGuardrail#create(int, int)}.
 * </p>
 *
 * @since 4.17.0
 */
public class InputLengthGuardrail implements InputGuardrail {

    /**
     * Default maximum character length for input messages.
     */
    public static final int DEFAULT_MAX_CHARS = 10000;

    /**
     * Default minimum character length for input messages.
     */
    public static final int DEFAULT_MIN_CHARS = 1;

    private final int maxChars;
    private final int minChars;

    /**
     * Creates a guardrail with default length limits.
     */
    public InputLengthGuardrail() {
        this(DEFAULT_MAX_CHARS, DEFAULT_MIN_CHARS);
    }

    /**
     * Creates a guardrail with custom length limits.
     *
     * @param maxChars maximum allowed character count
     * @param minChars minimum required character count
     */
    public InputLengthGuardrail(int maxChars, int minChars) {
        if (maxChars <= 0) {
            throw new IllegalArgumentException("maxChars must be positive");
        }
        if (minChars < 0) {
            throw new IllegalArgumentException("minChars cannot be negative");
        }
        if (minChars > maxChars) {
            throw new IllegalArgumentException("minChars cannot exceed maxChars");
        }
        this.maxChars = maxChars;
        this.minChars = minChars;
    }

    /**
     * Factory method to create a guardrail with custom limits.
     *
     * @param  maxChars maximum allowed character count
     * @param  minChars minimum required character count
     * @return          a new InputLengthGuardrail instance
     */
    public static InputLengthGuardrail create(int maxChars, int minChars) {
        return new InputLengthGuardrail(maxChars, minChars);
    }

    /**
     * Factory method to create a guardrail with only max length limit.
     *
     * @param  maxChars maximum allowed character count
     * @return          a new InputLengthGuardrail instance
     */
    public static InputLengthGuardrail maxLength(int maxChars) {
        return new InputLengthGuardrail(maxChars, DEFAULT_MIN_CHARS);
    }

    @Override
    public InputGuardrailResult validate(UserMessage userMessage) {
        if (userMessage == null || userMessage.singleText() == null) {
            return fatal("User message cannot be null or empty");
        }

        String text = userMessage.singleText();
        int length = text.length();

        if (length < minChars) {
            return failure(
                    String.format("Input too short: %d characters (minimum: %d)", length, minChars));
        }

        if (length > maxChars) {
            return failure(
                    String.format("Input too long: %d characters (maximum: %d)", length, maxChars));
        }

        return success();
    }

    /**
     * @return the maximum allowed character count
     */
    public int getMaxChars() {
        return maxChars;
    }

    /**
     * @return the minimum required character count
     */
    public int getMinChars() {
        return minChars;
    }
}
