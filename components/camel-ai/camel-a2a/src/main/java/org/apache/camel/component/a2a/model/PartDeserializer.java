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
package org.apache.camel.component.a2a.model;

import java.io.IOException;
import java.util.Map;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.camel.RuntimeCamelException;

/**
 * Deserializes A2A message parts using explicit {@code kind} values when present and legacy field-based detection for
 * existing preview payloads.
 *
 * @since 4.21
 */
public final class PartDeserializer extends JsonDeserializer<Part<?>> {

    @Override
    public Part<?> deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        ObjectCodec codec = parser.getCodec();
        JsonNode node = parser.getCodec().readTree(parser);
        String kind = resolveKind(node);
        if (kind == null) {
            throw new RuntimeCamelException("Cannot determine A2A part kind");
        }
        return switch (kind) {
            case "text" -> new TextPart(textValue(node, "text"), mapValue(codec, node.get("metadata")));
            case "data" -> new DataPart(objectValue(codec, node.get("data")), mapValue(codec, node.get("metadata")));
            case "file" -> new FilePart(
                    textValue(node, "raw"),
                    textValue(node, "url"),
                    textValue(node, "mediaType"),
                    textValue(node, "filename"),
                    mapValue(codec, node.get("metadata")));
            default -> throw new RuntimeCamelException("Cannot determine A2A part kind");
        };
    }

    private static String resolveKind(JsonNode node) {
        JsonNode kind = node.get("kind");
        validateOneOfContent(node);
        if (kind != null && kind.isTextual()) {
            return switch (kind.asText()) {
                case "text", "data", "file" -> kind.asText();
                default -> throw new RuntimeCamelException("Unsupported A2A part kind: " + kind.asText());
            };
        }
        if (node.has("text")) {
            return "text";
        }
        if (node.has("data")) {
            return "data";
        }
        if (node.has("raw") || node.has("url") || node.has("mediaType") || node.has("filename")) {
            return "file";
        }
        return null;
    }

    private static void validateOneOfContent(JsonNode node) {
        int count = 0;
        count += hasNonNull(node, "text") ? 1 : 0;
        count += hasNonNull(node, "data") ? 1 : 0;
        count += hasNonNull(node, "raw") ? 1 : 0;
        count += hasNonNull(node, "url") ? 1 : 0;
        if (count > 1) {
            throw new RuntimeCamelException("A2A part must contain exactly one content field");
        }
    }

    private static boolean hasNonNull(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        return value != null && !value.isNull();
    }

    private static String textValue(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        return value != null && !value.isNull() ? value.asText() : null;
    }

    private static Object objectValue(ObjectCodec codec, JsonNode node) throws IOException {
        return node != null && !node.isNull() ? codec.treeToValue(node, Object.class) : null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapValue(ObjectCodec codec, JsonNode node) throws IOException {
        return node != null && !node.isNull() ? codec.treeToValue(node, Map.class) : null;
    }
}
