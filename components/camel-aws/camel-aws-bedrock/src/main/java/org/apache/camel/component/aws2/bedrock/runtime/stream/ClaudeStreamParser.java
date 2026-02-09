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
 * Parser for Anthropic Claude model streaming responses (v3+ format)
 * <p>
 * Claude streaming response format: {"type": "message_start", "message": {...}} {"type": "content_block_start",
 * "index": 0, "content_block": {"type": "text", "text": ""}} {"type": "content_block_delta", "index": 0, "delta":
 * {"type": "text_delta", "text": "chunk"}} {"type": "content_block_delta", "index": 0, "delta": {"type": "text_delta",
 * "text": " more"}} {"type": "content_block_stop", "index": 0} {"type": "message_delta", "delta": {"stop_reason":
 * "end_turn", "stop_sequence": null}, "usage": {"output_tokens": 150}} {"type": "message_stop"}
 */
public class ClaudeStreamParser implements StreamResponseParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String extractText(String chunk) throws JsonProcessingException {
        if (chunk == null || chunk.trim().isEmpty()) {
            return "";
        }
        JsonNode node = MAPPER.readTree(chunk);
        JsonNode type = node.get("type");

        if (ObjectHelper.isNotEmpty(type) && "content_block_delta".equals(type.asText())) {
            JsonNode delta = node.get("delta");
            if (ObjectHelper.isNotEmpty(delta)) {
                JsonNode deltaType = delta.get("type");
                if (ObjectHelper.isNotEmpty(deltaType) && "text_delta".equals(deltaType.asText())) {
                    JsonNode text = delta.get("text");
                    return ObjectHelper.isNotEmpty(text) && !text.isNull() ? text.asText() : "";
                }
            }
        }
        return "";
    }

    @Override
    public String extractCompletionReason(String chunk) throws JsonProcessingException {
        if (chunk == null || chunk.trim().isEmpty()) {
            return null;
        }
        JsonNode node = MAPPER.readTree(chunk);
        JsonNode type = node.get("type");

        if (ObjectHelper.isNotEmpty(type) && "message_delta".equals(type.asText())) {
            JsonNode delta = node.get("delta");
            if (ObjectHelper.isNotEmpty(delta)) {
                JsonNode stopReason = delta.get("stop_reason");
                return ObjectHelper.isNotEmpty(stopReason) && !stopReason.isNull() ? stopReason.asText() : null;
            }
        }
        return null;
    }

    @Override
    public Integer extractTokenCount(String chunk) throws JsonProcessingException {
        if (chunk == null || chunk.trim().isEmpty()) {
            return null;
        }
        JsonNode node = MAPPER.readTree(chunk);
        JsonNode type = node.get("type");

        if (ObjectHelper.isNotEmpty(type) && "message_delta".equals(type.asText())) {
            JsonNode usage = node.get("usage");
            if (ObjectHelper.isNotEmpty(usage)) {
                JsonNode outputTokens = usage.get("output_tokens");
                return ObjectHelper.isNotEmpty(outputTokens) && !outputTokens.isNull() ? outputTokens.asInt() : null;
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
        JsonNode type = node.get("type");
        return ObjectHelper.isNotEmpty(type) && "message_stop".equals(type.asText());
    }
}
