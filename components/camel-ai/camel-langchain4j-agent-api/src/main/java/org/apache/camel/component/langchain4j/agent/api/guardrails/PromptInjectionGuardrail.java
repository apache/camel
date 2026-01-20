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
import java.util.List;
import java.util.regex.Pattern;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailResult;

/**
 * Input guardrail that detects potential prompt injection attacks.
 *
 * <p>
 * This guardrail uses pattern matching to detect common prompt injection techniques such as:
 * </p>
 * <ul>
 * <li>Instructions to ignore previous prompts</li>
 * <li>Role manipulation attempts (e.g., "you are now...")</li>
 * <li>System prompt override attempts</li>
 * <li>Jailbreak patterns</li>
 * <li>Delimiter injection</li>
 * </ul>
 *
 * <p>
 * Example usage:
 * </p>
 *
 * <pre>{@code
 * AgentConfiguration config = new AgentConfiguration()
 *         .withChatModel(chatModel)
 *         .withInputGuardrailClasses(List.of(PromptInjectionGuardrail.class));
 * }</pre>
 *
 * <p>
 * <strong>Note:</strong> This guardrail provides a basic defense layer. For production systems, consider combining with
 * additional security measures and LLM-based content moderation.
 * </p>
 *
 * @since 4.17.0
 */
public class PromptInjectionGuardrail implements InputGuardrail {

    /**
     * Categories of prompt injection patterns.
     */
    public enum InjectionCategory {
        /** Attempts to ignore or override previous instructions */
        IGNORE_INSTRUCTIONS,
        /** Attempts to change the AI's role or persona */
        ROLE_MANIPULATION,
        /** Attempts to access or reveal system prompts */
        SYSTEM_PROMPT_LEAK,
        /** Common jailbreak techniques */
        JAILBREAK,
        /** Delimiter or encoding tricks */
        DELIMITER_INJECTION
    }

    private static final List<InjectionPattern> DEFAULT_PATTERNS = Arrays.asList(
            // Ignore instructions patterns
            new InjectionPattern(
                    InjectionCategory.IGNORE_INSTRUCTIONS,
                    Pattern.compile(
                            "(?i)ignore\\s+(all\\s+)?(previous|prior|above|earlier)\\s+(instructions?|prompts?|rules?|guidelines?|constraints?)")),
            new InjectionPattern(
                    InjectionCategory.IGNORE_INSTRUCTIONS,
                    Pattern.compile(
                            "(?i)disregard\\s+(all\\s+)?(previous|prior|above|earlier)\\s+(instructions?|prompts?|rules?)")),
            new InjectionPattern(
                    InjectionCategory.IGNORE_INSTRUCTIONS,
                    Pattern.compile("(?i)forget\\s+(everything|all|what)\\s+(you|i)\\s+(told|said|instructed)")),
            new InjectionPattern(
                    InjectionCategory.IGNORE_INSTRUCTIONS,
                    Pattern.compile("(?i)do\\s+not\\s+follow\\s+(your|the)\\s+(previous|original|initial)\\s+instructions?")),

            // Role manipulation patterns
            new InjectionPattern(
                    InjectionCategory.ROLE_MANIPULATION,
                    Pattern.compile("(?i)you\\s+are\\s+now\\s+(a|an|the|acting\\s+as)")),
            new InjectionPattern(
                    InjectionCategory.ROLE_MANIPULATION,
                    Pattern.compile("(?i)pretend\\s+(you\\s+are|to\\s+be)\\s+(a|an|the)")),
            new InjectionPattern(
                    InjectionCategory.ROLE_MANIPULATION,
                    Pattern.compile("(?i)act\\s+as\\s+(if\\s+you\\s+are|a|an|the)")),
            new InjectionPattern(
                    InjectionCategory.ROLE_MANIPULATION,
                    Pattern.compile("(?i)roleplay\\s+as\\s+(a|an|the)")),
            new InjectionPattern(
                    InjectionCategory.ROLE_MANIPULATION,
                    Pattern.compile("(?i)switch\\s+(to|into)\\s+(a|an|the|developer|admin|sudo)\\s+mode")),

            // System prompt leak attempts
            new InjectionPattern(
                    InjectionCategory.SYSTEM_PROMPT_LEAK,
                    Pattern.compile(
                            "(?i)(show|reveal|display|print|output|repeat)\\s+(me\\s+)?(your|the)\\s+(system\\s+)?prompt")),
            new InjectionPattern(
                    InjectionCategory.SYSTEM_PROMPT_LEAK,
                    Pattern.compile("(?i)what\\s+(is|are)\\s+your\\s+(system\\s+)?(instructions?|prompts?|rules?)")),
            new InjectionPattern(
                    InjectionCategory.SYSTEM_PROMPT_LEAK,
                    Pattern.compile(
                            "(?i)(tell|give)\\s+me\\s+(your|the)\\s+(initial|system|hidden)\\s+(prompt|instructions?)")),

            // Jailbreak patterns
            new InjectionPattern(
                    InjectionCategory.JAILBREAK,
                    Pattern.compile("(?i)\\bDAN\\b.*\\b(do\\s+anything|jailbreak)")),
            new InjectionPattern(
                    InjectionCategory.JAILBREAK,
                    Pattern.compile("(?i)\\bjailbreak(ed)?\\b")),
            new InjectionPattern(
                    InjectionCategory.JAILBREAK,
                    Pattern.compile("(?i)bypass\\s+(your\\s+)?(safety|security|restrictions?|limitations?|filters?)")),
            new InjectionPattern(
                    InjectionCategory.JAILBREAK,
                    Pattern.compile(
                            "(?i)disable\\s+(your\\s+)?(safety|security|ethical|content)\\s+(filters?|restrictions?|guidelines?)")),

            // Delimiter injection
            new InjectionPattern(
                    InjectionCategory.DELIMITER_INJECTION,
                    Pattern.compile("```\\s*(system|assistant|user)\\s*\\n")),
            new InjectionPattern(
                    InjectionCategory.DELIMITER_INJECTION,
                    Pattern.compile("\\[\\s*(SYSTEM|INST|/INST)\\s*\\]")),
            new InjectionPattern(
                    InjectionCategory.DELIMITER_INJECTION,
                    Pattern.compile("<\\|?(system|im_start|im_end|endoftext)\\|?>")));

    private final List<InjectionPattern> patterns;
    private final boolean strict;

    /**
     * Creates a guardrail with default patterns in non-strict mode.
     */
    public PromptInjectionGuardrail() {
        this(DEFAULT_PATTERNS, false);
    }

    /**
     * Creates a guardrail with custom patterns.
     *
     * @param patterns the list of injection patterns to detect
     * @param strict   if true, any match results in failure; if false, multiple matches required
     */
    public PromptInjectionGuardrail(List<InjectionPattern> patterns, boolean strict) {
        this.patterns = new ArrayList<>(patterns);
        this.strict = strict;
    }

    /**
     * Creates a guardrail in strict mode (blocks on any single pattern match).
     *
     * @return a new strict PromptInjectionGuardrail
     */
    public static PromptInjectionGuardrail strict() {
        return new PromptInjectionGuardrail(DEFAULT_PATTERNS, true);
    }

    /**
     * Creates a builder for custom configuration.
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
        List<InjectionCategory> detectedCategories = new ArrayList<>();

        for (InjectionPattern pattern : patterns) {
            if (pattern.getPattern().matcher(text).find()) {
                if (!detectedCategories.contains(pattern.getCategory())) {
                    detectedCategories.add(pattern.getCategory());
                }

                // In strict mode, fail on first match
                if (strict) {
                    return failure(
                            String.format("Potential prompt injection detected: %s pattern. " +
                                          "Please rephrase your request.",
                                    pattern.getCategory()));
                }
            }
        }

        // In non-strict mode, require multiple category matches to reduce false positives
        if (detectedCategories.size() >= 2) {
            return failure(
                    String.format("Potential prompt injection detected: multiple suspicious patterns (%s). " +
                                  "Please rephrase your request.",
                            detectedCategories));
        }

        return success();
    }

    /**
     * @return true if running in strict mode
     */
    public boolean isStrict() {
        return strict;
    }

    /**
     * Represents a pattern used to detect prompt injection attempts.
     */
    public static class InjectionPattern {
        private final InjectionCategory category;
        private final Pattern pattern;

        public InjectionPattern(InjectionCategory category, Pattern pattern) {
            this.category = category;
            this.pattern = pattern;
        }

        public InjectionCategory getCategory() {
            return category;
        }

        public Pattern getPattern() {
            return pattern;
        }
    }

    /**
     * Builder for creating PromptInjectionGuardrail instances.
     */
    public static class Builder {
        private List<InjectionPattern> patterns = new ArrayList<>(DEFAULT_PATTERNS);
        private boolean strict = false;

        /**
         * Sets strict mode.
         *
         * @param  strict true to fail on any single pattern match
         * @return        this builder
         */
        public Builder strict(boolean strict) {
            this.strict = strict;
            return this;
        }

        /**
         * Adds a custom injection pattern.
         *
         * @param  category the category of injection
         * @param  pattern  the regex pattern to match
         * @return          this builder
         */
        public Builder addPattern(InjectionCategory category, Pattern pattern) {
            this.patterns.add(new InjectionPattern(category, pattern));
            return this;
        }

        /**
         * Clears all default patterns.
         *
         * @return this builder
         */
        public Builder clearPatterns() {
            this.patterns.clear();
            return this;
        }

        /**
         * Uses only default patterns (resets any custom patterns).
         *
         * @return this builder
         */
        public Builder useDefaultPatterns() {
            this.patterns = new ArrayList<>(DEFAULT_PATTERNS);
            return this;
        }

        /**
         * Builds the guardrail instance.
         *
         * @return a new PromptInjectionGuardrail instance
         */
        public PromptInjectionGuardrail build() {
            return new PromptInjectionGuardrail(patterns, strict);
        }
    }
}
