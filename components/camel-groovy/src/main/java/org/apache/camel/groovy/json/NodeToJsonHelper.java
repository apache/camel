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
package org.apache.camel.groovy.json;

import java.util.List;

import groovy.util.Node;
import groovy.util.NodeList;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

class NodeToJsonHelper {

    public static JsonObject nodeToJson(Node rootNode) {
        JsonObject jo = nodeToJsonObject(rootNode);

        JsonObject root = new JsonObject();
        root.put(rootNode.name().toString(), jo);
        return root;
    }

    private static JsonObject nodeToJsonObject(Node node) {
        JsonObject result = new JsonObject();

        // Add attributes
        node.attributes().forEach((key, value) -> {
            result.put(key.toString(), value);
        });

        // Process children
        List<Node> children = node.children();
        if (!children.isEmpty()) {
            if (children.size() == 1) {
                var val = node.value();
                if (val instanceof NodeList nl) {
                    // flatten
                    val = nl.get(0);
                }
                result.put(node.name().toString(), val);
                return result;
            }
            // Group children by name
            children.stream()
                    .collect(java.util.stream.Collectors.groupingBy(
                            Node::name,
                            java.util.LinkedHashMap::new,
                            java.util.stream.Collectors.toList()))
                    .forEach((name, nodes) -> {
                        if (nodes.size() == 1) {
                            Node child = nodes.get(0);
                            Object value = convertChild(child);
                            if (value instanceof JsonObject jo) {
                                value = jo.get(name.toString());
                            }
                            result.put(name.toString(), value);
                        } else {
                            JsonArray array = new JsonArray();
                            nodes.forEach(child -> array.add(convertChild(child)));
                            result.put(name.toString(), array);
                        }
                    });
        } else {
            // Leaf node with text content
            String text = node.text();
            if (text != null && !text.trim().isEmpty()) {
                JsonObject jo = new JsonObject();
                jo.put("#text", text.trim()); // or just return text
                return jo;
            }
        }

        return result;
    }

    private static Object convertChild(Node child) {
        if (child.children().isEmpty()) {
            String text = child.text();
            return text != null && !text.trim().isEmpty() ? text.trim() : null;
        } else {
            return nodeToJsonObject(child);
        }
    }

}
