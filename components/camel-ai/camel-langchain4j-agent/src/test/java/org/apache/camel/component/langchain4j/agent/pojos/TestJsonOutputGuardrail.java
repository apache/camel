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
package org.apache.camel.component.langchain4j.agent.pojos;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.JsonExtractorOutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailResult;

/**
 * Test JSON output guardrail that extends JsonExtractorOutputGuardrail but returns the JSON as a String instead of a
 * parsed object. This maintains compatibility with LangChain4j service interfaces.
 */
public class TestJsonOutputGuardrail extends JsonExtractorOutputGuardrail<Object> {

    private static volatile boolean wasValidated = false;
    private static volatile boolean allowReprompt = true;

    public TestJsonOutputGuardrail() {
        super(Object.class);
    }

    @Override
    public OutputGuardrailResult validate(AiMessage aiMessage) {
        wasValidated = true;

        // Use the parent's JSON extraction and validation logic
        OutputGuardrailResult parentResult = super.validate(aiMessage);

        // If the parent successfully extracted JSON, extract just the JSON part
        // instead of returning the parsed object to maintain String compatibility
        if (parentResult.isSuccess()) {
            String cleanJson = extractJsonFromText(aiMessage.text());
            return OutputGuardrailResult.successWith(cleanJson != null ? cleanJson : aiMessage.text());
        }

        // If parent validation failed, return the failure to trigger reprompt with JSON instructions
        return parentResult;
    }

    /**
     * Extracts JSON from text by finding the first { and matching }.
     */
    private String extractJsonFromText(String text) {
        if (text == null) {
            return null;
        }

        final int start = text.indexOf('{');
        if (start == -1) {
            return null;
        }

        int count = 0;
        for (int i = start; i < text.length(); i++) {
            count = switch (text.charAt(i)) {
                case '{' -> count + 1;
                case '}' -> count - 1;
                default -> count;
            };

            if (count == 0) {
                return text.substring(start, i + 1);
            }
        }
        return null;
    }

    @Override
    protected OutputGuardrailResult invokeInvalidJson(AiMessage aiMessage, String json) {
        if (allowReprompt) {
            // Default behavior: reprompt to get valid JSON
            return super.invokeInvalidJson(aiMessage, json);
        }
        // Fail immediately without reprompting
        return fatal("Output validation failed: Invalid JSON format");
    }

    /**
     * Sets whether reprompting is allowed when JSON validation fails.
     *
     * @param allow true to allow reprompting (default), false to fail immediately
     */
    public static void setAllowReprompt(boolean allow) {
        allowReprompt = allow;
    }

    public static void reset() {
        wasValidated = false;
        allowReprompt = true;
    }

    /**
     * @return true if this guardrail was validated at least once
     */
    public static boolean wasValidated() {
        return wasValidated;
    }
}
