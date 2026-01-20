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

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailResult;

/**
 * Output guardrail that validates the length of AI responses.
 *
 * <p>
 * This guardrail ensures that AI responses meet length requirements, which can be useful for:
 * </p>
 * <ul>
 * <li>Preventing overly verbose responses</li>
 * <li>Ensuring responses are substantive (not too short)</li>
 * <li>Maintaining consistent response formats</li>
 * </ul>
 *
 * <p>
 * Example usage:
 * </p>
 *
 * <pre>{@code
 * AgentConfiguration config = new AgentConfiguration()
 *         .withChatModel(chatModel)
 *         .withOutputGuardrailClasses(List.of(OutputLengthGuardrail.class));
 * }</pre>
 *
 * @since 4.17.0
 */
public class OutputLengthGuardrail implements OutputGuardrail {

    /**
     * Default maximum character length for output messages.
     */
    public static final int DEFAULT_MAX_CHARS = 50000;

    /**
     * Default minimum character length for output messages.
     */
    public static final int DEFAULT_MIN_CHARS = 1;

    private final int maxChars;
    private final int minChars;
    private final boolean truncateOnOverflow;

    /**
     * Creates a guardrail with default length limits.
     */
    public OutputLengthGuardrail() {
        this(DEFAULT_MAX_CHARS, DEFAULT_MIN_CHARS, false);
    }

    /**
     * Creates a guardrail with custom length limits.
     *
     * @param maxChars           maximum allowed character count
     * @param minChars           minimum required character count
     * @param truncateOnOverflow if true, truncate instead of failing on overflow
     */
    public OutputLengthGuardrail(int maxChars, int minChars, boolean truncateOnOverflow) {
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
        this.truncateOnOverflow = truncateOnOverflow;
    }

    /**
     * Factory method to create a guardrail with custom limits.
     *
     * @param  maxChars maximum allowed character count
     * @param  minChars minimum required character count
     * @return          a new OutputLengthGuardrail instance
     */
    public static OutputLengthGuardrail create(int maxChars, int minChars) {
        return new OutputLengthGuardrail(maxChars, minChars, false);
    }

    /**
     * Factory method to create a guardrail with only max length limit.
     *
     * @param  maxChars maximum allowed character count
     * @return          a new OutputLengthGuardrail instance
     */
    public static OutputLengthGuardrail maxLength(int maxChars) {
        return new OutputLengthGuardrail(maxChars, DEFAULT_MIN_CHARS, false);
    }

    /**
     * Factory method to create a guardrail that truncates on overflow.
     *
     * @param  maxChars maximum allowed character count
     * @return          a new OutputLengthGuardrail instance
     */
    public static OutputLengthGuardrail truncatingAt(int maxChars) {
        return new OutputLengthGuardrail(maxChars, DEFAULT_MIN_CHARS, true);
    }

    /**
     * Creates a new builder for configuring the guardrail.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public OutputGuardrailResult validate(AiMessage aiMessage) {
        if (aiMessage == null || aiMessage.text() == null) {
            return fatal("AI response cannot be null or empty");
        }

        String text = aiMessage.text();
        int length = text.length();

        if (length < minChars) {
            return retry(
                    String.format("Response too short: %d characters (minimum: %d). " +
                                  "Please provide a more detailed response.",
                            length, minChars));
        }

        if (length > maxChars) {
            if (truncateOnOverflow) {
                String truncated = text.substring(0, maxChars);
                // Try to truncate at a word boundary
                int lastSpace = truncated.lastIndexOf(' ');
                if (lastSpace > maxChars * 0.8) {
                    truncated = truncated.substring(0, lastSpace) + "...";
                } else {
                    truncated = truncated + "...";
                }
                return successWith(truncated);
            }
            return retry(
                    String.format("Response too long: %d characters (maximum: %d). " +
                                  "Please provide a more concise response.",
                            length, maxChars));
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

    /**
     * @return whether truncation is enabled on overflow
     */
    public boolean isTruncateOnOverflow() {
        return truncateOnOverflow;
    }

    /**
     * Builder for creating OutputLengthGuardrail instances.
     */
    public static class Builder {
        private int maxChars = DEFAULT_MAX_CHARS;
        private int minChars = DEFAULT_MIN_CHARS;
        private boolean truncateOnOverflow = false;

        /**
         * Sets the maximum character count.
         *
         * @param  maxChars maximum allowed character count
         * @return          this builder
         */
        public Builder maxChars(int maxChars) {
            this.maxChars = maxChars;
            return this;
        }

        /**
         * Sets the minimum character count.
         *
         * @param  minChars minimum required character count
         * @return          this builder
         */
        public Builder minChars(int minChars) {
            this.minChars = minChars;
            return this;
        }

        /**
         * Enables truncation instead of failure on overflow.
         *
         * @param  truncate true to truncate on overflow
         * @return          this builder
         */
        public Builder truncateOnOverflow(boolean truncate) {
            this.truncateOnOverflow = truncate;
            return this;
        }

        /**
         * Builds the guardrail instance.
         *
         * @return a new OutputLengthGuardrail instance
         */
        public OutputLengthGuardrail build() {
            return new OutputLengthGuardrail(maxChars, minChars, truncateOnOverflow);
        }
    }
}
