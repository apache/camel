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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailResult;

/**
 * Input guardrail that blocks messages containing specific keywords or phrases.
 *
 * <p>
 * This guardrail is useful for filtering out profanity, inappropriate content, competitor names, or any other terms
 * that should not be processed by the LLM.
 * </p>
 *
 * <p>
 * Example usage:
 * </p>
 *
 * <pre>{@code
 * KeywordFilterGuardrail guardrail = KeywordFilterGuardrail.builder()
 *         .blockedWords("spam", "inappropriate", "banned")
 *         .caseSensitive(false)
 *         .build();
 * }</pre>
 *
 * @since 4.17.0
 */
public class KeywordFilterGuardrail implements InputGuardrail {

    private final Set<String> blockedWords;
    private final List<Pattern> blockedPatterns;
    private final boolean caseSensitive;
    private final boolean wholeWordMatch;

    /**
     * Creates a guardrail with no blocked words (allows all messages).
     */
    public KeywordFilterGuardrail() {
        this(new HashSet<>(), new ArrayList<>(), false, true);
    }

    /**
     * Creates a guardrail with the specified configuration.
     *
     * @param blockedWords    the set of blocked words
     * @param blockedPatterns the list of regex patterns to block
     * @param caseSensitive   whether matching should be case-sensitive
     * @param wholeWordMatch  whether to match whole words only
     */
    public KeywordFilterGuardrail(Set<String> blockedWords, List<Pattern> blockedPatterns,
                                  boolean caseSensitive, boolean wholeWordMatch) {
        this.blockedWords = new HashSet<>(blockedWords);
        this.blockedPatterns = new ArrayList<>(blockedPatterns);
        this.caseSensitive = caseSensitive;
        this.wholeWordMatch = wholeWordMatch;
    }

    /**
     * Creates a simple guardrail with blocked words.
     *
     * @param  words the words to block
     * @return       a new KeywordFilterGuardrail instance
     */
    public static KeywordFilterGuardrail blocking(String... words) {
        return builder().blockedWords(words).build();
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
    public InputGuardrailResult validate(UserMessage userMessage) {
        if (userMessage == null || userMessage.singleText() == null) {
            return success();
        }

        String text = userMessage.singleText();
        String searchText = caseSensitive ? text : text.toLowerCase();

        // Check blocked words
        for (String word : blockedWords) {
            String searchWord = caseSensitive ? word : word.toLowerCase();

            if (wholeWordMatch) {
                // Use word boundary matching
                Pattern wordPattern = Pattern.compile(
                        "\\b" + Pattern.quote(searchWord) + "\\b",
                        caseSensitive ? 0 : Pattern.CASE_INSENSITIVE);
                if (wordPattern.matcher(text).find()) {
                    return failure("Message contains blocked content. Please rephrase your request.");
                }
            } else {
                if (searchText.contains(searchWord)) {
                    return failure("Message contains blocked content. Please rephrase your request.");
                }
            }
        }

        // Check blocked patterns
        for (Pattern pattern : blockedPatterns) {
            if (pattern.matcher(text).find()) {
                return failure("Message contains blocked content. Please rephrase your request.");
            }
        }

        return success();
    }

    /**
     * @return the set of blocked words
     */
    public Set<String> getBlockedWords() {
        return new HashSet<>(blockedWords);
    }

    /**
     * @return whether matching is case-sensitive
     */
    public boolean isCaseSensitive() {
        return caseSensitive;
    }

    /**
     * @return whether only whole words are matched
     */
    public boolean isWholeWordMatch() {
        return wholeWordMatch;
    }

    /**
     * Builder for creating KeywordFilterGuardrail instances.
     */
    public static class Builder {
        private Set<String> blockedWords = new HashSet<>();
        private List<Pattern> blockedPatterns = new ArrayList<>();
        private boolean caseSensitive = false;
        private boolean wholeWordMatch = true;

        /**
         * Adds words to the block list.
         *
         * @param  words the words to block
         * @return       this builder
         */
        public Builder blockedWords(String... words) {
            this.blockedWords.addAll(Arrays.asList(words));
            return this;
        }

        /**
         * Adds words to the block list.
         *
         * @param  words the set of words to block
         * @return       this builder
         */
        public Builder blockedWords(Set<String> words) {
            this.blockedWords.addAll(words);
            return this;
        }

        /**
         * Adds a regex pattern to the block list.
         *
         * @param  pattern the pattern to block
         * @return         this builder
         */
        public Builder blockedPattern(Pattern pattern) {
            this.blockedPatterns.add(pattern);
            return this;
        }

        /**
         * Adds a regex pattern to the block list.
         *
         * @param  regex the regex string to block
         * @return       this builder
         */
        public Builder blockedPattern(String regex) {
            this.blockedPatterns.add(Pattern.compile(regex, caseSensitive ? 0 : Pattern.CASE_INSENSITIVE));
            return this;
        }

        /**
         * Sets whether matching should be case-sensitive.
         *
         * @param  caseSensitive true for case-sensitive matching
         * @return               this builder
         */
        public Builder caseSensitive(boolean caseSensitive) {
            this.caseSensitive = caseSensitive;
            return this;
        }

        /**
         * Sets whether to match whole words only.
         *
         * @param  wholeWordMatch true to match whole words only
         * @return                this builder
         */
        public Builder wholeWordMatch(boolean wholeWordMatch) {
            this.wholeWordMatch = wholeWordMatch;
            return this;
        }

        /**
         * Builds the guardrail instance.
         *
         * @return a new KeywordFilterGuardrail instance
         */
        public KeywordFilterGuardrail build() {
            return new KeywordFilterGuardrail(blockedWords, blockedPatterns, caseSensitive, wholeWordMatch);
        }
    }
}
