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
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailResult;

/**
 * Input guardrail that detects potential code injection attempts in user messages.
 *
 * <p>
 * This guardrail identifies attempts to inject executable code such as:
 * </p>
 * <ul>
 * <li>Shell commands and scripts</li>
 * <li>SQL injection patterns</li>
 * <li>JavaScript/HTML injection</li>
 * <li>Path traversal attempts</li>
 * <li>Command chaining patterns</li>
 * </ul>
 *
 * <p>
 * Example usage:
 * </p>
 *
 * <pre>{@code
 * AgentConfiguration config = new AgentConfiguration()
 *         .withChatModel(chatModel)
 *         .withInputGuardrailClasses(List.of(CodeInjectionGuardrail.class));
 * }</pre>
 *
 * @since 4.17.0
 */
public class CodeInjectionGuardrail implements InputGuardrail {

    /**
     * Types of code injection that can be detected.
     */
    public enum InjectionType {
        /** Shell command injection (bash, sh, cmd) */
        SHELL_COMMAND,

        /** SQL injection patterns */
        SQL_INJECTION,

        /** JavaScript injection */
        JAVASCRIPT,

        /** HTML/XSS injection */
        HTML_XSS,

        /** Path traversal attacks */
        PATH_TRAVERSAL,

        /** Command chaining (;, &&, ||, |) */
        COMMAND_CHAINING,

        /** Template injection */
        TEMPLATE_INJECTION
    }

    private static final List<InjectionPattern> DEFAULT_PATTERNS = Arrays.asList(
            // Shell command injection
            new InjectionPattern(
                    InjectionType.SHELL_COMMAND,
                    Pattern.compile("(?i)\\b(bash|sh|cmd|powershell|exec|eval|system)\\s*\\(")),
            new InjectionPattern(
                    InjectionType.SHELL_COMMAND,
                    Pattern.compile("(?i)`[^`]+`")), // Backtick execution
            new InjectionPattern(
                    InjectionType.SHELL_COMMAND,
                    Pattern.compile("(?i)\\$\\([^)]+\\)")), // $() command substitution
            new InjectionPattern(
                    InjectionType.SHELL_COMMAND,
                    Pattern.compile("(?i)\\b(rm|del|format|mkfs|dd)\\s+(-rf?\\s+)?/")),

            // SQL injection
            new InjectionPattern(
                    InjectionType.SQL_INJECTION,
                    Pattern.compile("(?i)'\\s*(OR|AND)\\s+['\"]?\\d+['\"]?\\s*=\\s*['\"]?\\d+['\"]?")),
            new InjectionPattern(
                    InjectionType.SQL_INJECTION,
                    Pattern.compile("(?i)(UNION\\s+(ALL\\s+)?SELECT|INSERT\\s+INTO|DELETE\\s+FROM|DROP\\s+TABLE)")),
            new InjectionPattern(
                    InjectionType.SQL_INJECTION,
                    Pattern.compile("(?i);\\s*(SELECT|INSERT|UPDATE|DELETE|DROP|TRUNCATE)\\b")),
            new InjectionPattern(
                    InjectionType.SQL_INJECTION,
                    Pattern.compile("(?i)--\\s*$")), // SQL comment at end

            // JavaScript injection
            new InjectionPattern(
                    InjectionType.JAVASCRIPT,
                    Pattern.compile("(?i)<script[^>]*>.*?</script>", Pattern.DOTALL)),
            new InjectionPattern(
                    InjectionType.JAVASCRIPT,
                    Pattern.compile("(?i)javascript\\s*:")),
            new InjectionPattern(
                    InjectionType.JAVASCRIPT,
                    Pattern.compile("(?i)\\bon(click|load|error|mouseover|focus)\\s*=")),

            // HTML/XSS
            new InjectionPattern(
                    InjectionType.HTML_XSS,
                    Pattern.compile("(?i)<(iframe|embed|object|applet|form|input)[^>]*>")),
            new InjectionPattern(
                    InjectionType.HTML_XSS,
                    Pattern.compile("(?i)\\bstyle\\s*=\\s*['\"].*?(expression|javascript)[^'\"]*['\"]")),

            // Path traversal
            new InjectionPattern(
                    InjectionType.PATH_TRAVERSAL,
                    Pattern.compile("\\.\\.[\\\\/]")),
            new InjectionPattern(
                    InjectionType.PATH_TRAVERSAL,
                    Pattern.compile("(?i)%2e%2e[%/\\\\]")), // URL encoded
            new InjectionPattern(
                    InjectionType.PATH_TRAVERSAL,
                    Pattern.compile("(?i)\\b(etc/passwd|etc/shadow|windows/system32)")),

            // Command chaining
            new InjectionPattern(
                    InjectionType.COMMAND_CHAINING,
                    Pattern.compile("[;&|]{2}\\s*\\w+")),
            new InjectionPattern(
                    InjectionType.COMMAND_CHAINING,
                    Pattern.compile(";\\s*(cat|ls|dir|type|rm|del)\\b")),

            // Template injection
            new InjectionPattern(
                    InjectionType.TEMPLATE_INJECTION,
                    Pattern.compile("\\{\\{.*?\\}\\}")),
            new InjectionPattern(
                    InjectionType.TEMPLATE_INJECTION,
                    Pattern.compile("\\$\\{.*?\\}")),
            new InjectionPattern(
                    InjectionType.TEMPLATE_INJECTION,
                    Pattern.compile("<%.*?%>")));

    private final List<InjectionPattern> patterns;
    private final Set<InjectionType> detectTypes;
    private final boolean strict;

    /**
     * Creates a guardrail that detects all code injection types.
     */
    public CodeInjectionGuardrail() {
        this(DEFAULT_PATTERNS, EnumSet.allOf(InjectionType.class), false);
    }

    /**
     * Creates a guardrail with specific configuration.
     *
     * @param patterns    the injection patterns to use
     * @param detectTypes the types of injection to detect
     * @param strict      if true, fail on any match; if false, require context
     */
    public CodeInjectionGuardrail(List<InjectionPattern> patterns, Set<InjectionType> detectTypes, boolean strict) {
        this.patterns = new ArrayList<>(patterns);
        this.detectTypes = EnumSet.copyOf(detectTypes);
        this.strict = strict;
    }

    /**
     * Creates a strict guardrail that fails on any code pattern detection.
     *
     * @return a new strict CodeInjectionGuardrail
     */
    public static CodeInjectionGuardrail strict() {
        return new CodeInjectionGuardrail(DEFAULT_PATTERNS, EnumSet.allOf(InjectionType.class), true);
    }

    /**
     * Creates a guardrail that only detects specific injection types.
     *
     * @param  types the injection types to detect
     * @return       a new CodeInjectionGuardrail
     */
    public static CodeInjectionGuardrail forTypes(InjectionType... types) {
        return builder().detectTypes(types).build();
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
        List<InjectionType> detected = new ArrayList<>();

        for (InjectionPattern pattern : patterns) {
            if (!detectTypes.contains(pattern.getType())) {
                continue;
            }

            if (pattern.getPattern().matcher(text).find()) {
                detected.add(pattern.getType());

                if (strict) {
                    return failure(String.format(
                            "Potential code injection detected: %s. " +
                                                 "Please remove any code or command patterns from your message.",
                            pattern.getType()));
                }
            }
        }

        // In non-strict mode, require multiple different types to reduce false positives
        if (!strict && detected.size() >= 2) {
            return failure(String.format(
                    "Multiple potential code injection patterns detected: %s. " +
                                         "Please rephrase your message without code-like syntax.",
                    detected));
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
     * @return the set of injection types being detected
     */
    public Set<InjectionType> getDetectTypes() {
        return EnumSet.copyOf(detectTypes);
    }

    /**
     * Represents a pattern used to detect code injection attempts.
     */
    public static class InjectionPattern {
        private final InjectionType type;
        private final Pattern pattern;

        public InjectionPattern(InjectionType type, Pattern pattern) {
            this.type = type;
            this.pattern = pattern;
        }

        public InjectionType getType() {
            return type;
        }

        public Pattern getPattern() {
            return pattern;
        }
    }

    /**
     * Builder for creating CodeInjectionGuardrail instances.
     */
    public static class Builder {
        private List<InjectionPattern> patterns = new ArrayList<>(DEFAULT_PATTERNS);
        private Set<InjectionType> detectTypes = EnumSet.allOf(InjectionType.class);
        private boolean strict = false;

        /**
         * Sets the injection types to detect.
         *
         * @param  types the types to detect
         * @return       this builder
         */
        public Builder detectTypes(InjectionType... types) {
            this.detectTypes = EnumSet.noneOf(InjectionType.class);
            this.detectTypes.addAll(Arrays.asList(types));
            return this;
        }

        /**
         * Sets strict mode.
         *
         * @param  strict true to fail on any single match
         * @return        this builder
         */
        public Builder strict(boolean strict) {
            this.strict = strict;
            return this;
        }

        /**
         * Adds a custom injection pattern.
         *
         * @param  type    the injection type
         * @param  pattern the regex pattern
         * @return         this builder
         */
        public Builder addPattern(InjectionType type, Pattern pattern) {
            this.patterns.add(new InjectionPattern(type, pattern));
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
         * Builds the guardrail instance.
         *
         * @return a new CodeInjectionGuardrail instance
         */
        public CodeInjectionGuardrail build() {
            return new CodeInjectionGuardrail(patterns, detectTypes, strict);
        }
    }
}
