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

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailResult;

/**
 * Output guardrail that blocks or redacts specific keywords in AI responses.
 *
 * <p>
 * This guardrail is useful for filtering out inappropriate content, competitor mentions, confidential terms, or any
 * other content that should not appear in AI responses.
 * </p>
 *
 * <p>
 * Example usage:
 * </p>
 *
 * <pre>{@code
 * KeywordOutputFilterGuardrail guardrail = KeywordOutputFilterGuardrail.builder()
 *         .blockedWords("confidential", "proprietary", "internal-only")
 *         .action(Action.REDACT)
 *         .build();
 * }</pre>
 *
 * @since 4.17.0
 */
public class KeywordOutputFilterGuardrail implements OutputGuardrail {

    /**
     * Action to take when blocked content is detected.
     */
    public enum Action {
        /** Block the entire response */
        BLOCK,
        /** Redact the blocked words and allow the response */
        REDACT
    }

    private final Set<String> blockedWords;
    private final List<Pattern> blockedPatterns;
    private final boolean caseSensitive;
    private final boolean wholeWordMatch;
    private final Action action;
    private final String redactionText;

    /**
     * Creates a guardrail with no blocked words (allows all responses).
     */
    public KeywordOutputFilterGuardrail() {
        this(new HashSet<>(), new ArrayList<>(), false, true, Action.BLOCK, "[REDACTED]");
    }

    /**
     * Creates a guardrail with the specified configuration.
     *
     * @param blockedWords    the set of blocked words
     * @param blockedPatterns the list of regex patterns to block
     * @param caseSensitive   whether matching should be case-sensitive
     * @param wholeWordMatch  whether to match whole words only
     * @param action          the action to take when blocked content is found
     * @param redactionText   the text to replace blocked content with
     */
    public KeywordOutputFilterGuardrail(Set<String> blockedWords, List<Pattern> blockedPatterns,
                                        boolean caseSensitive, boolean wholeWordMatch, Action action, String redactionText) {
        this.blockedWords = new HashSet<>(blockedWords);
        this.blockedPatterns = new ArrayList<>(blockedPatterns);
        this.caseSensitive = caseSensitive;
        this.wholeWordMatch = wholeWordMatch;
        this.action = action;
        this.redactionText = redactionText;
    }

    /**
     * Creates a simple guardrail that blocks responses containing the specified words.
     *
     * @param  words the words to block
     * @return       a new KeywordOutputFilterGuardrail instance
     */
    public static KeywordOutputFilterGuardrail blocking(String... words) {
        return builder().blockedWords(words).action(Action.BLOCK).build();
    }

    /**
     * Creates a guardrail that redacts specified words from responses.
     *
     * @param  words the words to redact
     * @return       a new KeywordOutputFilterGuardrail instance
     */
    public static KeywordOutputFilterGuardrail redacting(String... words) {
        return builder().blockedWords(words).action(Action.REDACT).build();
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
            return success();
        }

        String text = aiMessage.text();
        String processedText = text;
        boolean hasBlockedContent = false;

        // Check blocked words
        for (String word : blockedWords) {
            String searchWord = caseSensitive ? word : word.toLowerCase();
            Pattern wordPattern;

            if (wholeWordMatch) {
                wordPattern = Pattern.compile(
                        "\\b" + Pattern.quote(searchWord) + "\\b",
                        caseSensitive ? 0 : Pattern.CASE_INSENSITIVE);
            } else {
                wordPattern = Pattern.compile(
                        Pattern.quote(searchWord),
                        caseSensitive ? 0 : Pattern.CASE_INSENSITIVE);
            }

            if (wordPattern.matcher(text).find()) {
                hasBlockedContent = true;
                if (action == Action.REDACT) {
                    processedText = wordPattern.matcher(processedText).replaceAll(redactionText);
                } else {
                    break; // No need to continue if we're blocking
                }
            }
        }

        // Check blocked patterns
        for (Pattern pattern : blockedPatterns) {
            if (pattern.matcher(text).find()) {
                hasBlockedContent = true;
                if (action == Action.REDACT) {
                    processedText = pattern.matcher(processedText).replaceAll(redactionText);
                } else {
                    break;
                }
            }
        }

        if (hasBlockedContent) {
            if (action == Action.BLOCK) {
                return fatal("Response contains blocked content and cannot be displayed.");
            } else {
                return successWith(processedText);
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
     * @return the action taken when blocked content is found
     */
    public Action getAction() {
        return action;
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
     * Builder for creating KeywordOutputFilterGuardrail instances.
     */
    public static class Builder {
        private Set<String> blockedWords = new HashSet<>();
        private List<Pattern> blockedPatterns = new ArrayList<>();
        private boolean caseSensitive = false;
        private boolean wholeWordMatch = true;
        private Action action = Action.BLOCK;
        private String redactionText = "[REDACTED]";

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
         * Sets the action to take when blocked content is found.
         *
         * @param  action the action (BLOCK or REDACT)
         * @return        this builder
         */
        public Builder action(Action action) {
            this.action = action;
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
         * Sets the text to use for redaction.
         *
         * @param  redactionText the redaction text
         * @return               this builder
         */
        public Builder redactionText(String redactionText) {
            this.redactionText = redactionText;
            return this;
        }

        /**
         * Builds the guardrail instance.
         *
         * @return a new KeywordOutputFilterGuardrail instance
         */
        public KeywordOutputFilterGuardrail build() {
            return new KeywordOutputFilterGuardrail(
                    blockedWords, blockedPatterns,
                    caseSensitive, wholeWordMatch, action, redactionText);
        }
    }
}
