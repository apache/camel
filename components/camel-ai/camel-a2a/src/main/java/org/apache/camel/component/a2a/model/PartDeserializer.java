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

import java.util.Map;

import org.apache.camel.RuntimeCamelException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;

/**
 * Deserializes A2A message parts using explicit {@code kind} values when present and legacy field-based detection for
 * existing preview payloads.
 *
 * @since 4.21
 */
public final class PartDeserializer extends ValueDeserializer<Part<?>> {

    @Override
    public Part<?> deserialize(JsonParser parser, DeserializationContext context) {
        JsonNode node = context.readTree(parser);
        String kind = resolveKind(node);
        if (kind == null) {
            throw new RuntimeCamelException("Cannot determine A2A part kind");
        }
        return switch (kind) {
            case "text" -> new TextPart(textValue(node, "text"), mapValue(context, node.get("metadata")));
            case "data" -> new DataPart(objectValue(context, node.get("data")), mapValue(context, node.get("metadata")));
            case "file" -> new FilePart(
                    textValue(node, "raw"),
                    textValue(node, "url"),
                    textValue(node, "mediaType"),
                    textValue(node, "filename"),
                    mapValue(context, node.get("metadata")));
            default -> throw new RuntimeCamelException("Cannot determine A2A part kind");
        };
    }

    private static String resolveKind(JsonNode node) {
        JsonNode kind = node.get("kind");
        validateOneOfContent(node);
        if (kind != null && kind.isString()) {
            return switch (kind.asString()) {
                case "text", "data", "file" -> kind.asString();
                default -> throw new RuntimeCamelException("Unsupported A2A part kind: " + kind.asString());
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
        if (value == null || value.isNull()) {
            return null;
        }
        // a container in a text field reads as empty text, asString() would reject it
        return value.isContainer() ? "" : value.asString();
    }

    private static Object objectValue(DeserializationContext context, JsonNode node) {
        return node != null && !node.isNull() ? context.readTreeAsValue(node, Object.class) : null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapValue(DeserializationContext context, JsonNode node) {
        return node != null && !node.isNull() ? context.readTreeAsValue(node, Map.class) : null;
    }
}
