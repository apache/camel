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

/**
 * Parser for Cohere model streaming responses
 * <p>
 * Cohere streaming response format: {"is_finished": false, "event_type": "text-generation", "text": "chunk"}
 * {"is_finished": false, "event_type": "text-generation", "text": " more"} {"is_finished": true, "event_type":
 * "stream-end", "finish_reason": "COMPLETE", "response": {...}}
 */
public class CohereStreamParser implements StreamResponseParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String extractText(String chunk) throws JsonProcessingException {
        if (chunk == null || chunk.trim().isEmpty()) {
            return "";
        }
        JsonNode node = MAPPER.readTree(chunk);
        JsonNode eventType = node.get("event_type");

        if (eventType != null && "text-generation".equals(eventType.asText())) {
            JsonNode text = node.get("text");
            return text != null && !text.isNull() ? text.asText() : "";
        }
        return "";
    }

    @Override
    public String extractCompletionReason(String chunk) throws JsonProcessingException {
        if (chunk == null || chunk.trim().isEmpty()) {
            return null;
        }
        JsonNode node = MAPPER.readTree(chunk);
        JsonNode finishReason = node.get("finish_reason");
        return finishReason != null && !finishReason.isNull() ? finishReason.asText() : null;
    }

    @Override
    public Integer extractTokenCount(String chunk) throws JsonProcessingException {
        if (chunk == null || chunk.trim().isEmpty()) {
            return null;
        }
        JsonNode node = MAPPER.readTree(chunk);
        JsonNode response = node.get("response");
        if (response != null) {
            JsonNode meta = response.get("meta");
            if (meta != null) {
                JsonNode tokens = meta.get("tokens");
                if (tokens != null) {
                    JsonNode outputTokens = tokens.get("output_tokens");
                    return outputTokens != null && !outputTokens.isNull() ? outputTokens.asInt() : null;
                }
            }
        }
        return null;
    }

    @Override
    public boolean isFinalChunk(String chunk) throws JsonProcessingException {
        if (chunk == null || chunk.trim().isEmpty()) {
            return false;
        }
        JsonNode node = MAPPER.readTree(chunk);
        JsonNode isFinished = node.get("is_finished");
        return isFinished != null && isFinished.asBoolean(false);
    }
}
