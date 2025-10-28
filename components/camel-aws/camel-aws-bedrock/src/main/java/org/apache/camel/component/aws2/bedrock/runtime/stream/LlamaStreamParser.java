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
 * Parser for Meta Llama model streaming responses
 * <p>
 * Llama streaming response format: {"generation": "chunk text", "prompt_token_count": 10, "generation_token_count": 1,
 * "stop_reason": null} {"generation": " more text", "prompt_token_count": 10, "generation_token_count": 2,
 * "stop_reason": null} {"generation": "", "prompt_token_count": 10, "generation_token_count": 150, "stop_reason":
 * "stop"}
 */
public class LlamaStreamParser implements StreamResponseParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String extractText(String chunk) throws JsonProcessingException {
        if (chunk == null || chunk.trim().isEmpty()) {
            return "";
        }
        JsonNode node = MAPPER.readTree(chunk);
        JsonNode generation = node.get("generation");
        return generation != null && !generation.isNull() ? generation.asText() : "";
    }

    @Override
    public String extractCompletionReason(String chunk) throws JsonProcessingException {
        if (chunk == null || chunk.trim().isEmpty()) {
            return null;
        }
        JsonNode node = MAPPER.readTree(chunk);
        JsonNode stopReason = node.get("stop_reason");
        return stopReason != null && !stopReason.isNull() ? stopReason.asText() : null;
    }

    @Override
    public Integer extractTokenCount(String chunk) throws JsonProcessingException {
        if (chunk == null || chunk.trim().isEmpty()) {
            return null;
        }
        JsonNode node = MAPPER.readTree(chunk);
        JsonNode tokenCount = node.get("generation_token_count");
        return tokenCount != null && !tokenCount.isNull() ? tokenCount.asInt() : null;
    }

    @Override
    public boolean isFinalChunk(String chunk) throws JsonProcessingException {
        String stopReason = extractCompletionReason(chunk);
        return stopReason != null && !stopReason.isEmpty();
    }
}
