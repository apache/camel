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

import java.util.HashSet;
import java.util.Set;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailResult;

/**
 * Output guardrail that validates AI responses are valid JSON format.
 *
 * <p>
 * This guardrail is useful when you expect structured JSON responses from the LLM. It can validate basic JSON syntax,
 * required fields, and optionally extract the JSON portion from a response that contains additional text.
 * </p>
 *
 * <p>
 * Example usage:
 * </p>
 *
 * <pre>{@code
 * JsonFormatGuardrail guardrail = JsonFormatGuardrail.builder()
 *         .requireFields("name", "email", "age")
 *         .extractJson(true)
 *         .build();
 * }</pre>
 *
 * @since 4.17.0
 */
public class JsonFormatGuardrail implements OutputGuardrail {

    private final Set<String> requiredFields;
    private final boolean extractJson;
    private final boolean allowArray;

    /**
     * Creates a guardrail that validates any valid JSON.
     */
    public JsonFormatGuardrail() {
        this(new HashSet<>(), true, true);
    }

    /**
     * Creates a guardrail with specific configuration.
     *
     * @param requiredFields the set of required field names in the JSON
     * @param extractJson    whether to extract JSON from surrounding text
     * @param allowArray     whether to allow JSON arrays (not just objects)
     */
    public JsonFormatGuardrail(Set<String> requiredFields, boolean extractJson, boolean allowArray) {
        this.requiredFields = new HashSet<>(requiredFields);
        this.extractJson = extractJson;
        this.allowArray = allowArray;
    }

    /**
     * Creates a guardrail that requires specific fields.
     *
     * @param  fields the required field names
     * @return        a new JsonFormatGuardrail instance
     */
    public static JsonFormatGuardrail requireFields(String... fields) {
        return builder().requireFields(fields).build();
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
        String json = text;

        // Try to extract JSON from the response if enabled
        if (extractJson && !text.startsWith("{") && !text.startsWith("[")) {
            json = extractJsonFromText(text);
            if (json == null) {
                return retry("Response does not contain valid JSON. Expected a JSON object or array.");
            }
        }

        // Validate basic JSON structure
        json = json.trim();
        if (!json.startsWith("{") && !json.startsWith("[")) {
            return retry("Response is not valid JSON. Expected to start with '{' or '['.");
        }

        if (json.startsWith("[") && !allowArray) {
            return retry("Response is a JSON array, but only objects are allowed.");
        }

        // Validate JSON syntax using a simple parser
        if (!isValidJson(json)) {
            return retry("Response contains malformed JSON. Please check the syntax.");
        }

        // Check for required fields (only for objects)
        if (json.startsWith("{") && !requiredFields.isEmpty()) {
            for (String field : requiredFields) {
                // Simple field presence check using regex
                if (!containsField(json, field)) {
                    return retry(String.format("Response is missing required field: '%s'", field));
                }
            }
        }

        // If we extracted JSON, return the extracted portion
        if (extractJson && !json.equals(text)) {
            return successWith(json);
        }

        return success();
    }

    /**
     * Extracts JSON from text by finding the first { or [ and matching closing bracket.
     */
    private String extractJsonFromText(String text) {
        if (text == null) {
            return null;
        }

        int objectStart = text.indexOf('{');
        int arrayStart = text.indexOf('[');

        int start;
        char openBracket;
        char closeBracket;

        if (objectStart == -1 && arrayStart == -1) {
            return null;
        } else if (objectStart == -1) {
            start = arrayStart;
            openBracket = '[';
            closeBracket = ']';
        } else if (arrayStart == -1) {
            start = objectStart;
            openBracket = '{';
            closeBracket = '}';
        } else {
            // Take the first occurrence
            if (objectStart < arrayStart) {
                start = objectStart;
                openBracket = '{';
                closeBracket = '}';
            } else {
                start = arrayStart;
                openBracket = '[';
                closeBracket = ']';
            }
        }

        int count = 0;
        boolean inString = false;
        boolean escaped = false;

        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);

            if (escaped) {
                escaped = false;
                continue;
            }

            if (c == '\\') {
                escaped = true;
                continue;
            }

            if (c == '"') {
                inString = !inString;
                continue;
            }

            if (!inString) {
                if (c == openBracket) {
                    count++;
                } else if (c == closeBracket) {
                    count--;
                    if (count == 0) {
                        return text.substring(start, i + 1);
                    }
                }
            }
        }

        return null;
    }

    /**
     * Simple JSON validation using bracket matching.
     */
    private boolean isValidJson(String json) {
        if (json == null || json.isEmpty()) {
            return false;
        }

        int braceCount = 0;
        int bracketCount = 0;
        boolean inString = false;
        boolean escaped = false;

        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);

            if (escaped) {
                escaped = false;
                continue;
            }

            if (c == '\\') {
                escaped = true;
                continue;
            }

            if (c == '"') {
                inString = !inString;
                continue;
            }

            if (!inString) {
                switch (c) {
                    case '{':
                        braceCount++;
                        break;
                    case '}':
                        braceCount--;
                        if (braceCount < 0) {
                            return false;
                        }
                        break;
                    case '[':
                        bracketCount++;
                        break;
                    case ']':
                        bracketCount--;
                        if (bracketCount < 0) {
                            return false;
                        }
                        break;
                }
            }
        }

        return braceCount == 0 && bracketCount == 0 && !inString;
    }

    /**
     * Checks if the JSON contains a field with the given name.
     */
    private boolean containsField(String json, String fieldName) {
        // Look for "fieldName": or "fieldName" :
        String pattern1 = "\"" + fieldName + "\":";
        String pattern2 = "\"" + fieldName + "\" :";
        return json.contains(pattern1) || json.contains(pattern2);
    }

    /**
     * @return the set of required fields
     */
    public Set<String> getRequiredFields() {
        return new HashSet<>(requiredFields);
    }

    /**
     * @return whether JSON extraction is enabled
     */
    public boolean isExtractJson() {
        return extractJson;
    }

    /**
     * @return whether JSON arrays are allowed
     */
    public boolean isAllowArray() {
        return allowArray;
    }

    /**
     * Builder for creating JsonFormatGuardrail instances.
     */
    public static class Builder {
        private Set<String> requiredFields = new HashSet<>();
        private boolean extractJson = true;
        private boolean allowArray = true;

        /**
         * Adds required fields to check for.
         *
         * @param  fields the required field names
         * @return        this builder
         */
        public Builder requireFields(String... fields) {
            for (String field : fields) {
                this.requiredFields.add(field);
            }
            return this;
        }

        /**
         * Sets whether to extract JSON from surrounding text.
         *
         * @param  extractJson true to extract JSON from text
         * @return             this builder
         */
        public Builder extractJson(boolean extractJson) {
            this.extractJson = extractJson;
            return this;
        }

        /**
         * Sets whether to allow JSON arrays.
         *
         * @param  allowArray true to allow arrays, false for objects only
         * @return            this builder
         */
        public Builder allowArray(boolean allowArray) {
            this.allowArray = allowArray;
            return this;
        }

        /**
         * Builds the guardrail instance.
         *
         * @return a new JsonFormatGuardrail instance
         */
        public JsonFormatGuardrail build() {
            return new JsonFormatGuardrail(requiredFields, extractJson, allowArray);
        }
    }
}
