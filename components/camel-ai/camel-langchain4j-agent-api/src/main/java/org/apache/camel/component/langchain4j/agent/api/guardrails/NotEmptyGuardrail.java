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
 * Output guardrail that ensures AI responses are not empty or contain only whitespace.
 *
 * <p>
 * This simple guardrail validates that the LLM actually produced a meaningful response. It can also check for common
 * "refusal" patterns where the LLM declines to answer.
 * </p>
 *
 * <p>
 * Example usage:
 * </p>
 *
 * <pre>{@code
 * AgentConfiguration config = new AgentConfiguration()
 *         .withChatModel(chatModel)
 *         .withOutputGuardrailClasses(List.of(NotEmptyGuardrail.class));
 * }</pre>
 *
 * @since 4.17.0
 */
public class NotEmptyGuardrail implements OutputGuardrail {

    private final boolean detectRefusals;
    private final int minMeaningfulLength;

    /**
     * Default refusal phrases to detect.
     */
    private static final String[] REFUSAL_PATTERNS = {
            "I cannot", "I can't", "I'm unable to", "I am unable to",
            "I don't have", "I do not have", "I'm not able to",
            "I apologize, but I cannot", "Sorry, but I cannot",
            "I'm sorry, I cannot", "I'm afraid I cannot"
    };

    /**
     * Creates a guardrail with default settings.
     */
    public NotEmptyGuardrail() {
        this(false, 1);
    }

    /**
     * Creates a guardrail with custom settings.
     *
     * @param detectRefusals      whether to detect refusal patterns
     * @param minMeaningfulLength minimum length for a meaningful response
     */
    public NotEmptyGuardrail(boolean detectRefusals, int minMeaningfulLength) {
        this.detectRefusals = detectRefusals;
        this.minMeaningfulLength = Math.max(1, minMeaningfulLength);
    }

    /**
     * Creates a guardrail that also detects refusal patterns.
     *
     * @return a new NotEmptyGuardrail that detects refusals
     */
    public static NotEmptyGuardrail withRefusalDetection() {
        return new NotEmptyGuardrail(true, 1);
    }

    /**
     * Creates a guardrail with a minimum meaningful length.
     *
     * @param  minLength minimum character length for meaningful response
     * @return           a new NotEmptyGuardrail instance
     */
    public static NotEmptyGuardrail withMinLength(int minLength) {
        return new NotEmptyGuardrail(false, minLength);
    }

    @Override
    public OutputGuardrailResult validate(AiMessage aiMessage) {
        if (aiMessage == null || aiMessage.text() == null) {
            return retry("AI response cannot be null. Please try again.");
        }

        String text = aiMessage.text().trim();

        if (text.isEmpty()) {
            return retry("AI response is empty. Please provide a meaningful response.");
        }

        if (text.length() < minMeaningfulLength) {
            return retry(String.format(
                    "AI response is too short (%d chars). Please provide a more complete response.",
                    text.length()));
        }

        if (detectRefusals) {
            String lowerText = text.toLowerCase();
            for (String pattern : REFUSAL_PATTERNS) {
                if (lowerText.startsWith(pattern.toLowerCase())) {
                    return retry("AI declined to answer. Please rephrase the question or try again.");
                }
            }
        }

        return success();
    }

    /**
     * @return whether refusal detection is enabled
     */
    public boolean isDetectRefusals() {
        return detectRefusals;
    }

    /**
     * @return the minimum meaningful length
     */
    public int getMinMeaningfulLength() {
        return minMeaningfulLength;
    }
}
