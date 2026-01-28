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
 * Parser for Amazon Titan model streaming responses
 * <p>
 * Titan streaming response format: {"outputText": "chunk text", "index": 0, "totalOutputTextTokenCount": null,
 * "completionReason": null} {"outputText": "more text", "index": 1, "totalOutputTextTokenCount": null,
 * "completionReason": null} {"outputText": "", "index": 2, "totalOutputTextTokenCount": 150, "completionReason":
 * "FINISH"}
 */
public class TitanStreamParser implements StreamResponseParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String extractText(String chunk) throws JsonProcessingException {
        if (chunk == null || chunk.trim().isEmpty()) {
            return "";
        }
        JsonNode node = MAPPER.readTree(chunk);
        JsonNode outputText = node.get("outputText");
        return ObjectHelper.isNotEmpty(outputText) && !outputText.isNull() ? outputText.asText() : "";
    }

    @Override
    public String extractCompletionReason(String chunk) throws JsonProcessingException {
        if (chunk == null || chunk.trim().isEmpty()) {
            return null;
        }
        JsonNode node = MAPPER.readTree(chunk);
        JsonNode completionReason = node.get("completionReason");
        return ObjectHelper.isNotEmpty(completionReason) && !completionReason.isNull() ? completionReason.asText() : null;
    }

    @Override
    public Integer extractTokenCount(String chunk) throws JsonProcessingException {
        if (chunk == null || chunk.trim().isEmpty()) {
            return null;
        }
        JsonNode node = MAPPER.readTree(chunk);
        JsonNode tokenCount = node.get("totalOutputTextTokenCount");
        return ObjectHelper.isNotEmpty(tokenCount) && !tokenCount.isNull() ? tokenCount.asInt() : null;
    }

    @Override
    public boolean isFinalChunk(String chunk) throws JsonProcessingException {
        String completionReason = extractCompletionReason(chunk);
        return ObjectHelper.isNotEmpty(completionReason);
    }
}
