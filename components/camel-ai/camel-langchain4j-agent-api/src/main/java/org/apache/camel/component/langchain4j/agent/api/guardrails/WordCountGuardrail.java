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
 * Output guardrail that validates the word count of AI responses.
 *
 * <p>
 * This guardrail ensures responses meet word count requirements, useful for:
 * </p>
 * <ul>
 * <li>Ensuring detailed responses (minimum words)</li>
 * <li>Keeping responses concise (maximum words)</li>
 * <li>Enforcing specific response formats</li>
 * </ul>
 *
 * <p>
 * Example usage:
 * </p>
 *
 * <pre>{@code
 * // Ensure responses are at least 50 words
 * WordCountGuardrail guardrail = WordCountGuardrail.atLeast(50);
 *
 * // Ensure responses are between 100 and 500 words
 * WordCountGuardrail guardrail = WordCountGuardrail.between(100, 500);
 * }</pre>
 *
 * @since 4.17.0
 */
public class WordCountGuardrail implements OutputGuardrail {

    /**
     * Default maximum word count.
     */
    public static final int DEFAULT_MAX_WORDS = 10000;

    /**
     * Default minimum word count.
     */
    public static final int DEFAULT_MIN_WORDS = 1;

    private final int minWords;
    private final int maxWords;

    /**
     * Creates a guardrail with default word limits.
     */
    public WordCountGuardrail() {
        this(DEFAULT_MIN_WORDS, DEFAULT_MAX_WORDS);
    }

    /**
     * Creates a guardrail with custom word limits.
     *
     * @param minWords minimum required word count
     * @param maxWords maximum allowed word count
     */
    public WordCountGuardrail(int minWords, int maxWords) {
        if (minWords < 0) {
            throw new IllegalArgumentException("minWords cannot be negative");
        }
        if (maxWords <= 0) {
            throw new IllegalArgumentException("maxWords must be positive");
        }
        if (minWords > maxWords) {
            throw new IllegalArgumentException("minWords cannot exceed maxWords");
        }
        this.minWords = minWords;
        this.maxWords = maxWords;
    }

    /**
     * Creates a guardrail requiring at least the specified number of words.
     *
     * @param  minWords minimum required word count
     * @return          a new WordCountGuardrail instance
     */
    public static WordCountGuardrail atLeast(int minWords) {
        return new WordCountGuardrail(minWords, DEFAULT_MAX_WORDS);
    }

    /**
     * Creates a guardrail allowing at most the specified number of words.
     *
     * @param  maxWords maximum allowed word count
     * @return          a new WordCountGuardrail instance
     */
    public static WordCountGuardrail atMost(int maxWords) {
        return new WordCountGuardrail(DEFAULT_MIN_WORDS, maxWords);
    }

    /**
     * Creates a guardrail with both minimum and maximum word limits.
     *
     * @param  minWords minimum required word count
     * @param  maxWords maximum allowed word count
     * @return          a new WordCountGuardrail instance
     */
    public static WordCountGuardrail between(int minWords, int maxWords) {
        return new WordCountGuardrail(minWords, maxWords);
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

        String text = aiMessage.text().trim();
        int wordCount = countWords(text);

        if (wordCount < minWords) {
            return retry(String.format(
                    "Response too brief: %d words (minimum: %d). Please provide a more detailed response.",
                    wordCount, minWords));
        }

        if (wordCount > maxWords) {
            return retry(String.format(
                    "Response too verbose: %d words (maximum: %d). Please provide a more concise response.",
                    wordCount, maxWords));
        }

        return success();
    }

    /**
     * Counts the number of words in the text.
     */
    private int countWords(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        String[] words = text.split("\\s+");
        int count = 0;
        for (String word : words) {
            if (!word.isEmpty()) {
                count++;
            }
        }
        return count;
    }

    /**
     * @return the minimum required word count
     */
    public int getMinWords() {
        return minWords;
    }

    /**
     * @return the maximum allowed word count
     */
    public int getMaxWords() {
        return maxWords;
    }

    /**
     * Builder for creating WordCountGuardrail instances.
     */
    public static class Builder {
        private int minWords = 0;
        private int maxWords = Integer.MAX_VALUE;

        /**
         * Sets the minimum word count.
         *
         * @param  minWords minimum required word count
         * @return          this builder
         */
        public Builder minWords(int minWords) {
            this.minWords = minWords;
            return this;
        }

        /**
         * Sets the maximum word count.
         *
         * @param  maxWords maximum allowed word count
         * @return          this builder
         */
        public Builder maxWords(int maxWords) {
            this.maxWords = maxWords;
            return this;
        }

        /**
         * Builds the guardrail instance.
         *
         * @return a new WordCountGuardrail instance
         */
        public WordCountGuardrail build() {
            return new WordCountGuardrail(minWords, maxWords);
        }
    }
}
