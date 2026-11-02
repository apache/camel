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
package org.apache.camel.component.aws2.bedrock.runtime.stream;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.util.ObjectHelper;

/**
 * Parser for AI21 Labs Jamba model streaming responses
 * <p>
 * Jamba streaming response format, as the chat completions: {"choices": [{"index": 0, "delta": {"role": "assistant",
 * "content": "chunk"}, "finish_reason": null}]} {"choices": [{"index": 0, "delta": {"content": ""}, "finish_reason":
 * "stop"}], "usage": {"prompt_tokens": 10, "completion_tokens": 150, "total_tokens": 160}}
 */
public class Ai21StreamParser implements StreamResponseParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String extractText(String chunk) throws JsonProcessingException {
        JsonNode choice = firstChoice(chunk);
        if (choice != null) {
            JsonNode delta = choice.get("delta");
            if (ObjectHelper.isNotEmpty(delta)) {
                JsonNode content = delta.get("content");
                return ObjectHelper.isNotEmpty(content) && !content.isNull() ? content.asText() : "";
            }
        }
        return "";
    }

    @Override
    public String extractCompletionReason(String chunk) throws JsonProcessingException {
        JsonNode choice = firstChoice(chunk);
        if (choice != null) {
            JsonNode finishReason = choice.get("finish_reason");
            return ObjectHelper.isNotEmpty(finishReason) && !finishReason.isNull() ? finishReason.asText() : null;
        }
        return null;
    }

    @Override
    public Integer extractTokenCount(String chunk) throws JsonProcessingException {
        if (chunk == null || chunk.trim().isEmpty()) {
            return null;
        }
        JsonNode usage = MAPPER.readTree(chunk).get("usage");
        if (ObjectHelper.isNotEmpty(usage)) {
            JsonNode completionTokens = usage.get("completion_tokens");
            return ObjectHelper.isNotEmpty(completionTokens) && !completionTokens.isNull() ? completionTokens.asInt() : null;
        }
        return null;
    }

    @Override
    public boolean isFinalChunk(String chunk) throws JsonProcessingException {
        return ObjectHelper.isNotEmpty(extractCompletionReason(chunk));
    }

    private static JsonNode firstChoice(String chunk) throws JsonProcessingException {
        if (chunk == null || chunk.trim().isEmpty()) {
            return null;
        }
        JsonNode choices = MAPPER.readTree(chunk).get("choices");
        if (ObjectHelper.isNotEmpty(choices) && choices.isArray() && !choices.isEmpty()) {
            return choices.get(0);
        }
        return null;
    }
}
