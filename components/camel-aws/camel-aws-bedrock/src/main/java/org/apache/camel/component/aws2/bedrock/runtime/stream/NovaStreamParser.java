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
 * Parser for Amazon Nova model streaming responses
 * <p>
 * Nova streaming response format: {"messageStart": {"role": "assistant"}} {"contentBlockDelta": {"delta": {"text":
 * "chunk"}, "contentBlockIndex": 0}} {"contentBlockStop": {"contentBlockIndex": 0}} {"messageStop": {"stopReason":
 * "end_turn"}} {"metadata": {"usage": {"inputTokens": 10, "outputTokens": 150}}}
 */
public class NovaStreamParser implements StreamResponseParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String extractText(String chunk) throws JsonProcessingException {
        JsonNode text = path(chunk, "contentBlockDelta", "delta", "text");
        return text != null ? text.asText() : "";
    }

    @Override
    public String extractCompletionReason(String chunk) throws JsonProcessingException {
        JsonNode stopReason = path(chunk, "messageStop", "stopReason");
        return stopReason != null ? stopReason.asText() : null;
    }

    @Override
    public Integer extractTokenCount(String chunk) throws JsonProcessingException {
        JsonNode outputTokens = path(chunk, "metadata", "usage", "outputTokens");
        return outputTokens != null ? outputTokens.asInt() : null;
    }

    @Override
    public boolean isFinalChunk(String chunk) throws JsonProcessingException {
        return path(chunk, "messageStop") != null;
    }

    /**
     * The node at the given path of the chunk, or null when the chunk has no such node.
     */
    private static JsonNode path(String chunk, String... names) throws JsonProcessingException {
        if (chunk == null || chunk.trim().isEmpty()) {
            return null;
        }
        JsonNode node = MAPPER.readTree(chunk);
        for (String name : names) {
            node = node.get(name);
            if (ObjectHelper.isEmpty(node) || node.isNull()) {
                return null;
            }
        }
        return node;
    }
}
