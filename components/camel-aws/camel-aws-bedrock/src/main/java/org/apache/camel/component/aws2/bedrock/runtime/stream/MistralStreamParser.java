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
 * Parser for Mistral AI model streaming responses
 * <p>
 * Mistral streaming response format: {"outputs": [{"text": "chunk", "stop_reason": null}]} {"outputs": [{"text": " more
 * text", "stop_reason": null}]} {"outputs": [{"text": "", "stop_reason": "stop"}]}
 */
public class MistralStreamParser implements StreamResponseParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String extractText(String chunk) throws JsonProcessingException {
        if (chunk == null || chunk.trim().isEmpty()) {
            return "";
        }
        JsonNode node = MAPPER.readTree(chunk);
        JsonNode outputs = node.get("outputs");
        if (outputs != null && outputs.isArray() && outputs.size() > 0) {
            JsonNode firstOutput = outputs.get(0);
            JsonNode text = firstOutput.get("text");
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
        JsonNode outputs = node.get("outputs");
        if (outputs != null && outputs.isArray() && outputs.size() > 0) {
            JsonNode firstOutput = outputs.get(0);
            JsonNode stopReason = firstOutput.get("stop_reason");
            return stopReason != null && !stopReason.isNull() ? stopReason.asText() : null;
        }
        return null;
    }

    @Override
    public Integer extractTokenCount(String chunk) throws JsonProcessingException {
        // Mistral doesn't provide token count in streaming responses
        // Could be added if Mistral adds this feature
        return null;
    }

    @Override
    public boolean isFinalChunk(String chunk) throws JsonProcessingException {
        String stopReason = extractCompletionReason(chunk);
        return stopReason != null && !stopReason.isEmpty();
    }
}
