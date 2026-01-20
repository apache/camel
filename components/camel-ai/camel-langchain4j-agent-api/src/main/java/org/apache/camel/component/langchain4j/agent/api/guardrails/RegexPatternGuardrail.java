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
import java.util.List;
import java.util.regex.Pattern;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailResult;

/**
 * A flexible input guardrail that uses custom regex patterns for validation.
 *
 * <p>
 * This guardrail allows you to define custom patterns to either block (deny patterns) or require (allow patterns) in
 * user messages. It's useful when you need custom validation beyond the built-in guardrails.
 * </p>
 *
 * <p>
 * Example usage:
 * </p>
 *
 * <pre>{@code
 * // Block messages containing URLs
 * RegexPatternGuardrail guardrail = RegexPatternGuardrail.builder()
 *         .denyPattern("https?://[^\\s]+", "URLs are not allowed")
 *         .build();
 *
 * // Require messages to contain a ticket number
 * RegexPatternGuardrail guardrail = RegexPatternGuardrail.builder()
 *         .requirePattern("TICKET-\\d+", "Please include a ticket number (e.g., TICKET-123)")
 *         .build();
 * }</pre>
 *
 * @since 4.17.0
 */
public class RegexPatternGuardrail implements InputGuardrail {

    private final List<PatternRule> denyPatterns;
    private final List<PatternRule> requirePatterns;
    private final boolean failOnFirstMatch;

    /**
     * Creates an empty guardrail with no patterns.
     */
    public RegexPatternGuardrail() {
        this(new ArrayList<>(), new ArrayList<>(), true);
    }

    /**
     * Creates a guardrail with the specified patterns.
     *
     * @param denyPatterns     patterns that will cause validation to fail if matched
     * @param requirePatterns  patterns that must be present for validation to pass
     * @param failOnFirstMatch if true, stop checking after first failure
     */
    public RegexPatternGuardrail(List<PatternRule> denyPatterns, List<PatternRule> requirePatterns,
                                 boolean failOnFirstMatch) {
        this.denyPatterns = new ArrayList<>(denyPatterns);
        this.requirePatterns = new ArrayList<>(requirePatterns);
        this.failOnFirstMatch = failOnFirstMatch;
    }

    /**
     * Creates a guardrail that blocks messages matching the pattern.
     *
     * @param  pattern      the regex pattern to block
     * @param  errorMessage the error message to show when blocked
     * @return              a new RegexPatternGuardrail instance
     */
    public static RegexPatternGuardrail blocking(String pattern, String errorMessage) {
        return builder().denyPattern(pattern, errorMessage).build();
    }

    /**
     * Creates a guardrail that requires messages to match the pattern.
     *
     * @param  pattern      the regex pattern to require
     * @param  errorMessage the error message when pattern is missing
     * @return              a new RegexPatternGuardrail instance
     */
    public static RegexPatternGuardrail requiring(String pattern, String errorMessage) {
        return builder().requirePattern(pattern, errorMessage).build();
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
        List<String> errors = new ArrayList<>();

        // Check deny patterns (should NOT match)
        for (PatternRule rule : denyPatterns) {
            if (rule.getPattern().matcher(text).find()) {
                if (failOnFirstMatch) {
                    return failure(rule.getErrorMessage());
                }
                errors.add(rule.getErrorMessage());
            }
        }

        // Check require patterns (MUST match)
        for (PatternRule rule : requirePatterns) {
            if (!rule.getPattern().matcher(text).find()) {
                if (failOnFirstMatch) {
                    return failure(rule.getErrorMessage());
                }
                errors.add(rule.getErrorMessage());
            }
        }

        if (!errors.isEmpty()) {
            return failure(String.join("; ", errors));
        }

        return success();
    }

    /**
     * @return the list of deny patterns
     */
    public List<PatternRule> getDenyPatterns() {
        return new ArrayList<>(denyPatterns);
    }

    /**
     * @return the list of require patterns
     */
    public List<PatternRule> getRequirePatterns() {
        return new ArrayList<>(requirePatterns);
    }

    /**
     * Represents a pattern rule with an error message.
     */
    public static class PatternRule {
        private final Pattern pattern;
        private final String errorMessage;

        public PatternRule(Pattern pattern, String errorMessage) {
            this.pattern = pattern;
            this.errorMessage = errorMessage;
        }

        public Pattern getPattern() {
            return pattern;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }

    /**
     * Builder for creating RegexPatternGuardrail instances.
     */
    public static class Builder {
        private List<PatternRule> denyPatterns = new ArrayList<>();
        private List<PatternRule> requirePatterns = new ArrayList<>();
        private boolean failOnFirstMatch = true;

        /**
         * Adds a pattern that will cause validation to fail if matched.
         *
         * @param  regex        the regex pattern string
         * @param  errorMessage the error message when matched
         * @return              this builder
         */
        public Builder denyPattern(String regex, String errorMessage) {
            denyPatterns.add(new PatternRule(Pattern.compile(regex), errorMessage));
            return this;
        }

        /**
         * Adds a pattern that will cause validation to fail if matched.
         *
         * @param  pattern      the compiled pattern
         * @param  errorMessage the error message when matched
         * @return              this builder
         */
        public Builder denyPattern(Pattern pattern, String errorMessage) {
            denyPatterns.add(new PatternRule(pattern, errorMessage));
            return this;
        }

        /**
         * Adds a pattern that must be present for validation to pass.
         *
         * @param  regex        the regex pattern string
         * @param  errorMessage the error message when not matched
         * @return              this builder
         */
        public Builder requirePattern(String regex, String errorMessage) {
            requirePatterns.add(new PatternRule(Pattern.compile(regex), errorMessage));
            return this;
        }

        /**
         * Adds a pattern that must be present for validation to pass.
         *
         * @param  pattern      the compiled pattern
         * @param  errorMessage the error message when not matched
         * @return              this builder
         */
        public Builder requirePattern(Pattern pattern, String errorMessage) {
            requirePatterns.add(new PatternRule(pattern, errorMessage));
            return this;
        }

        /**
         * Sets whether to fail on first pattern match or collect all errors.
         *
         * @param  failOnFirstMatch true to stop at first failure
         * @return                  this builder
         */
        public Builder failOnFirstMatch(boolean failOnFirstMatch) {
            this.failOnFirstMatch = failOnFirstMatch;
            return this;
        }

        /**
         * Builds the guardrail instance.
         *
         * @return a new RegexPatternGuardrail instance
         */
        public RegexPatternGuardrail build() {
            return new RegexPatternGuardrail(denyPatterns, requirePatterns, failOnFirstMatch);
        }
    }
}
