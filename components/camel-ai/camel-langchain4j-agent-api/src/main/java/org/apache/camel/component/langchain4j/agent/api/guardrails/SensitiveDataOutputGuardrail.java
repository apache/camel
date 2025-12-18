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
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailResult;

/**
 * Output guardrail that detects and optionally redacts sensitive data in AI responses.
 *
 * <p>
 * This guardrail helps prevent the LLM from leaking sensitive information such as:
 * </p>
 * <ul>
 * <li>API keys and secrets</li>
 * <li>Passwords</li>
 * <li>Credit card numbers</li>
 * <li>Social Security Numbers</li>
 * <li>Private keys and certificates</li>
 * </ul>
 *
 * <p>
 * Example usage:
 * </p>
 *
 * <pre>{@code
 * AgentConfiguration config = new AgentConfiguration()
 *         .withChatModel(chatModel)
 *         .withOutputGuardrailClasses(List.of(SensitiveDataOutputGuardrail.class));
 * }</pre>
 *
 * @since 4.17.0
 */
public class SensitiveDataOutputGuardrail implements OutputGuardrail {

    /**
     * Types of sensitive data that can be detected.
     */
    public enum SensitiveDataType {
        /** API keys (various formats) */
        API_KEY("API key", Pattern.compile(
                "(?i)(?:api[_-]?key|apikey|access[_-]?key|secret[_-]?key)\\s*[:=]\\s*['\"]?([a-zA-Z0-9_\\-]{20,})['\"]?")),

        /** AWS access keys */
        AWS_KEY("AWS access key", Pattern.compile(
                "(?:AKIA|ABIA|ACCA|ASIA)[0-9A-Z]{16}")),

        /** Generic secrets in key-value format */
        SECRET("secret value", Pattern.compile(
                "(?i)(?:password|passwd|pwd|secret|token|bearer)\\s*[:=]\\s*['\"]?([^\\s'\"]{8,})['\"]?")),

        /** Private keys (PEM format) */
        PRIVATE_KEY("private key", Pattern.compile(
                "-----BEGIN\\s+(?:RSA\\s+)?PRIVATE\\s+KEY-----")),

        /** Credit card numbers */
        CREDIT_CARD("credit card number", Pattern.compile(
                "\\b(?:4[0-9]{12}(?:[0-9]{3})?|5[1-5][0-9]{14}|3[47][0-9]{13}|6(?:011|5[0-9]{2})[0-9]{12})\\b")),

        /** Social Security Numbers */
        SSN("Social Security Number", Pattern.compile(
                "\\b(?!000|666|9\\d{2})\\d{3}[-]?(?!00)\\d{2}[-]?(?!0000)\\d{4}\\b")),

        /** JWT tokens */
        JWT("JWT token", Pattern.compile(
                "eyJ[a-zA-Z0-9_-]*\\.eyJ[a-zA-Z0-9_-]*\\.[a-zA-Z0-9_-]*")),

        /** Database connection strings */
        CONNECTION_STRING("connection string", Pattern.compile(
                "(?i)(?:mongodb|mysql|postgresql|redis|amqp)://[^\\s]+")),

        /** GitHub tokens */
        GITHUB_TOKEN("GitHub token", Pattern.compile(
                "(?:ghp|gho|ghu|ghs|ghr)_[a-zA-Z0-9]{36}"));

        private final String displayName;
        private final Pattern pattern;

        SensitiveDataType(String displayName, Pattern pattern) {
            this.displayName = displayName;
            this.pattern = pattern;
        }

        public String getDisplayName() {
            return displayName;
        }

        public Pattern getPattern() {
            return pattern;
        }
    }

    /**
     * Action to take when sensitive data is detected.
     */
    public enum Action {
        /** Block the response entirely */
        BLOCK,
        /** Redact the sensitive data and allow the response */
        REDACT,
        /** Allow the response with a warning (for logging/monitoring) */
        WARN
    }

    private final Set<SensitiveDataType> detectTypes;
    private final Action action;
    private final String redactionText;

    /**
     * Creates a guardrail that detects all sensitive data types and blocks on detection.
     */
    public SensitiveDataOutputGuardrail() {
        this(EnumSet.allOf(SensitiveDataType.class), Action.BLOCK, "[REDACTED]");
    }

    /**
     * Creates a guardrail with specific configuration.
     *
     * @param detectTypes   the set of sensitive data types to detect
     * @param action        the action to take when sensitive data is detected
     * @param redactionText the text to use for redaction (only used with REDACT action)
     */
    public SensitiveDataOutputGuardrail(Set<SensitiveDataType> detectTypes, Action action, String redactionText) {
        this.detectTypes = EnumSet.copyOf(detectTypes);
        this.action = action;
        this.redactionText = redactionText;
    }

    /**
     * Creates a guardrail that redacts sensitive data instead of blocking.
     *
     * @return a new SensitiveDataOutputGuardrail configured for redaction
     */
    public static SensitiveDataOutputGuardrail redacting() {
        return new SensitiveDataOutputGuardrail(EnumSet.allOf(SensitiveDataType.class), Action.REDACT, "[REDACTED]");
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
        List<String> detectedTypes = new ArrayList<>();
        String processedText = text;

        for (SensitiveDataType dataType : detectTypes) {
            if (dataType.getPattern().matcher(text).find()) {
                detectedTypes.add(dataType.getDisplayName());

                if (action == Action.REDACT) {
                    processedText = dataType.getPattern().matcher(processedText)
                            .replaceAll(redactionText);
                }
            }
        }

        if (!detectedTypes.isEmpty()) {
            String message = String.format(
                    "Sensitive data detected in response: %s",
                    String.join(", ", detectedTypes));

            switch (action) {
                case BLOCK:
                    return fatal(message + ". Response blocked for security reasons.");
                case REDACT:
                    return successWith(processedText);
                case WARN:
                    // Log warning but allow the response
                    return success();
                default:
                    return fatal(message);
            }
        }

        return success();
    }

    /**
     * @return the set of sensitive data types being detected
     */
    public Set<SensitiveDataType> getDetectTypes() {
        return EnumSet.copyOf(detectTypes);
    }

    /**
     * @return the action taken when sensitive data is detected
     */
    public Action getAction() {
        return action;
    }

    /**
     * @return the text used for redaction
     */
    public String getRedactionText() {
        return redactionText;
    }

    /**
     * Builder for creating SensitiveDataOutputGuardrail instances.
     */
    public static class Builder {
        private Set<SensitiveDataType> detectTypes = EnumSet.allOf(SensitiveDataType.class);
        private Action action = Action.BLOCK;
        private String redactionText = "[REDACTED]";

        /**
         * Sets the sensitive data types to detect.
         *
         * @param  types the sensitive data types to detect
         * @return       this builder
         */
        public Builder detectTypes(SensitiveDataType... types) {
            this.detectTypes = EnumSet.noneOf(SensitiveDataType.class);
            for (SensitiveDataType type : types) {
                this.detectTypes.add(type);
            }
            return this;
        }

        /**
         * Sets the action to take when sensitive data is detected.
         *
         * @param  action the action (BLOCK, REDACT, or WARN)
         * @return        this builder
         */
        public Builder action(Action action) {
            this.action = action;
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
         * Configures the guardrail to redact sensitive data.
         *
         * @return this builder
         */
        public Builder redact() {
            this.action = Action.REDACT;
            return this;
        }

        /**
         * Configures the guardrail to block responses with sensitive data.
         *
         * @return this builder
         */
        public Builder block() {
            this.action = Action.BLOCK;
            return this;
        }

        /**
         * Builds the guardrail instance.
         *
         * @return a new SensitiveDataOutputGuardrail instance
         */
        public SensitiveDataOutputGuardrail build() {
            return new SensitiveDataOutputGuardrail(detectTypes, action, redactionText);
        }
    }
}
