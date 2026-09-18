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
package org.apache.camel.component.mcp.server.main;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.component.mcp.server.McpServerIcon;

/**
 * Parses {@code camel.server.mcp-server-icons} JSON into {@link McpServerIcon} instances.
 */
final class McpServerIconsParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private McpServerIconsParser() {
    }

    static List<McpServerIcon> parse(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonNode root = MAPPER.readTree(json);
            if (!root.isArray()) {
                throw new IllegalArgumentException("mcp-server-icons must be a JSON array");
            }
            List<McpServerIcon> icons = new ArrayList<>(root.size());
            for (JsonNode node : root) {
                if (!node.isObject()) {
                    throw new IllegalArgumentException("Each MCP server icon must be a JSON object");
                }
                String src = text(node, "src");
                if (src == null || src.isBlank()) {
                    throw new IllegalArgumentException("Each MCP server icon must include a non-blank src");
                }
                icons.add(new McpServerIcon(
                        src,
                        text(node, "mimeType"),
                        stringList(node.get("sizes")),
                        text(node, "theme")));
            }
            return List.copyOf(icons);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse mcp-server-icons JSON", e);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && !value.isNull() ? value.asText() : null;
    }

    private static List<String> stringList(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isArray()) {
            throw new IllegalArgumentException("MCP server icon sizes must be a JSON array");
        }
        List<String> values = new ArrayList<>(node.size());
        for (JsonNode item : node) {
            values.add(item.asText());
        }
        return List.copyOf(values);
    }
}
