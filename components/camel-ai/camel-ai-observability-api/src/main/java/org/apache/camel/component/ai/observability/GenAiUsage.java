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
package org.apache.camel.component.ai.observability;

/**
 * Token usage and completion metadata captured after a GenAI operation.
 */
public record GenAiUsage(
        Long inputTokens,
        Long outputTokens,
        String finishReason,
        String responseModel) {

    public static GenAiUsage of(Long inputTokens, Long outputTokens, Object finishReason, String responseModel) {
        String reason = finishReason == null ? null : finishReason.toString();
        return new GenAiUsage(inputTokens, outputTokens, reason, responseModel);
    }

    /**
     * Convenience factory when token counts come from APIs that expose {@code Integer} counts (e.g. LangChain4j).
     */
    public static GenAiUsage of(Integer inputTokens, Integer outputTokens, Object finishReason, String responseModel) {
        return of(toLong(inputTokens), toLong(outputTokens), finishReason, responseModel);
    }

    private static Long toLong(Integer value) {
        return value == null ? null : value.longValue();
    }
}
