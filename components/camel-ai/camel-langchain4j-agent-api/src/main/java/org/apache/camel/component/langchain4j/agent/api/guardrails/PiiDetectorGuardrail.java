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

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailResult;

/**
 * Input guardrail that detects Personally Identifiable Information (PII) in user messages.
 *
 * <p>
 * This guardrail uses pattern matching to detect common PII types such as:
 * </p>
 * <ul>
 * <li>Email addresses</li>
 * <li>Phone numbers (various formats)</li>
 * <li>Social Security Numbers (SSN)</li>
 * <li>Credit card numbers</li>
 * <li>IP addresses</li>
 * </ul>
 *
 * <p>
 * Example usage:
 * </p>
 *
 * <pre>{@code
 * AgentConfiguration config = new AgentConfiguration()
 *         .withChatModel(chatModel)
 *         .withInputGuardrailClasses(List.of(PiiDetectorGuardrail.class));
 * }</pre>
 *
 * <p>
 * For customizing which PII types to detect, use the builder pattern:
 * </p>
 *
 * <pre>{@code
 * PiiDetectorGuardrail guardrail = PiiDetectorGuardrail.builder()
 *         .detectTypes(PiiType.EMAIL, PiiType.SSN)
 *         .build();
 * }</pre>
 *
 * @since 4.17.0
 */
public class PiiDetectorGuardrail implements InputGuardrail {

    /**
     * Types of PII that can be detected.
     */
    public enum PiiType {
        /** Email addresses */
        EMAIL("email address", Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}")),

        /** Phone numbers in various formats */
        PHONE("phone number", Pattern.compile(
                "(?:\\+?1[-.]?)?\\(?[0-9]{3}\\)?[-. ]?[0-9]{3}[-. ]?[0-9]{4}")),

        /** US Social Security Numbers */
        SSN("Social Security Number", Pattern.compile(
                "\\b(?!000|666|9\\d{2})\\d{3}[-]?(?!00)\\d{2}[-]?(?!0000)\\d{4}\\b")),

        /** Credit card numbers (major card types) */
        CREDIT_CARD("credit card number", Pattern.compile(
                "\\b(?:4[0-9]{12}(?:[0-9]{3})?|5[1-5][0-9]{14}|3[47][0-9]{13}|6(?:011|5[0-9]{2})[0-9]{12})\\b")),

        /** IPv4 addresses */
        IP_ADDRESS("IP address", Pattern.compile(
                "\\b(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\b"));

        private final String displayName;
        private final Pattern pattern;

        PiiType(String displayName, Pattern pattern) {
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

    private final Set<PiiType> detectTypes;
    private final boolean blockOnDetection;

    /**
     * Creates a guardrail that detects all PII types and blocks on detection.
     */
    public PiiDetectorGuardrail() {
        this(EnumSet.allOf(PiiType.class), true);
    }

    /**
     * Creates a guardrail with specific PII types to detect.
     *
     * @param detectTypes      the set of PII types to detect
     * @param blockOnDetection whether to block the message when PII is detected
     */
    public PiiDetectorGuardrail(Set<PiiType> detectTypes, boolean blockOnDetection) {
        this.detectTypes = EnumSet.copyOf(detectTypes);
        this.blockOnDetection = blockOnDetection;
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
        List<String> detectedPii = new ArrayList<>();

        for (PiiType piiType : detectTypes) {
            if (piiType.getPattern().matcher(text).find()) {
                detectedPii.add(piiType.getDisplayName());
            }
        }

        if (!detectedPii.isEmpty()) {
            String message = String.format(
                    "Potential PII detected: %s. Please remove sensitive information before sending.",
                    String.join(", ", detectedPii));

            if (blockOnDetection) {
                return failure(message);
            }
            // If not blocking, log a warning but allow the message
            return success();
        }

        return success();
    }

    /**
     * @return the set of PII types being detected
     */
    public Set<PiiType> getDetectTypes() {
        return EnumSet.copyOf(detectTypes);
    }

    /**
     * @return whether the guardrail blocks messages when PII is detected
     */
    public boolean isBlockOnDetection() {
        return blockOnDetection;
    }

    /**
     * Builder for creating PiiDetectorGuardrail instances.
     */
    public static class Builder {
        private Set<PiiType> detectTypes = EnumSet.allOf(PiiType.class);
        private boolean blockOnDetection = true;

        /**
         * Sets the PII types to detect.
         *
         * @param  types the PII types to detect
         * @return       this builder
         */
        public Builder detectTypes(PiiType... types) {
            this.detectTypes = EnumSet.noneOf(PiiType.class);
            for (PiiType type : types) {
                this.detectTypes.add(type);
            }
            return this;
        }

        /**
         * Sets the PII types to detect.
         *
         * @param  types the set of PII types to detect
         * @return       this builder
         */
        public Builder detectTypes(Set<PiiType> types) {
            this.detectTypes = EnumSet.copyOf(types);
            return this;
        }

        /**
         * Sets whether to block messages when PII is detected.
         *
         * @param  block true to block, false to allow (with warning logged)
         * @return       this builder
         */
        public Builder blockOnDetection(boolean block) {
            this.blockOnDetection = block;
            return this;
        }

        /**
         * Builds the guardrail instance.
         *
         * @return a new PiiDetectorGuardrail instance
         */
        public PiiDetectorGuardrail build() {
            return new PiiDetectorGuardrail(detectTypes, blockOnDetection);
        }
    }
}
