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
package org.apache.camel.dsl.yaml;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the generated YAML DSL completion tree has the expected structure and metadata.
 */
class YamlCompletionTreeTest {

    private static JsonNode tree;
    private static JsonNode nodes;

    @BeforeAll
    static void loadTree() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream is
                = YamlCompletionTreeTest.class.getResourceAsStream("/schema/camelYamlDsl-model.json")) {
            assertThat(is).as("completion tree must be on classpath").isNotNull();
            tree = mapper.readTree(is);
        }
        nodes = tree.get("nodes");
        assertThat(nodes).as("nodes must be present").isNotNull();
    }

    // --- Structural invariants ---

    @Test
    void rootNodeHasTopLevelElements() {
        JsonNode root = nodes.get("root");
        assertThat(root).as("root node must exist").isNotNull();

        Set<String> names = childNames(root);
        assertThat(names).contains("route", "from", "rest", "beans",
                "errorHandler", "onException", "routeConfiguration",
                "intercept", "interceptFrom", "interceptSendToEndpoint");
    }

    @Test
    void stepsNodeHasCommonEips() {
        JsonNode steps = nodes.get("steps");
        assertThat(steps).as("steps node must exist").isNotNull();

        Set<String> names = childNames(steps);
        assertThat(names).contains("log", "to", "setHeader", "setBody",
                "filter", "choice", "split", "marshal", "unmarshal",
                "bean", "aggregate", "wireTap", "delay", "throttle");
        assertThat(names.size()).as("steps should have many EIPs").isGreaterThan(50);
    }

    @Test
    void expressionNodeHasLanguages() {
        JsonNode expr = nodes.get("expression");
        assertThat(expr).as("expression node must exist").isNotNull();

        Set<String> names = childNames(expr);
        assertThat(names).contains("simple", "jsonpath", "xpath", "constant",
                "header", "jq", "groovy", "variable");
        assertThat(names.size()).as("expression should have many languages").isGreaterThan(15);
    }

    @Test
    void splitNodeHasExpressionAndAttributes() {
        JsonNode split = nodes.get("split");
        assertThat(split).as("split node must exist").isNotNull();
        assertThat(split.get("title").asText()).isEqualTo("Split");
        assertThat(split.get("label").asText()).contains("routing");

        Set<String> names = childNames(split);
        assertThat(names).contains("expression", "delimiter", "streaming",
                "parallelProcessing", "steps");

        // expression child should have ref and be required
        JsonNode exprChild = findChild(split, "expression");
        assertThat(exprChild).isNotNull();
        assertThat(exprChild.get("ref").asText()).isEqualTo("expression");
        assertThat(exprChild.get("required").asBoolean()).isTrue();
        assertThat(exprChild.get("kind").asText()).isEqualTo("expression");
    }

    @Test
    void splitNodeHasAliases() {
        JsonNode split = nodes.get("split");
        assertThat(split.has("aliases")).isTrue();

        Set<String> aliases = new HashSet<>();
        split.get("aliases").forEach(a -> aliases.add(a.asText()));
        assertThat(aliases).contains("split");
    }

    @Test
    void simpleLanguageNodeHasOptions() {
        JsonNode simple = findLanguageNode("simple");
        assertThat(simple).as("simple language node must exist").isNotNull();

        Set<String> names = childNames(simple);
        assertThat(names).contains("expression", "resultType", "trim");

        // expression should be required and kind=value
        JsonNode exprChild = findChild(simple, "expression");
        assertThat(exprChild).isNotNull();
        assertThat(exprChild.get("required").asBoolean()).isTrue();
        assertThat(exprChild.get("kind").asText()).isEqualTo("value");
    }

    @Test
    void marshalNodeHasDataFormats() {
        JsonNode marshal = nodes.get("marshal");
        assertThat(marshal).as("marshal node must exist").isNotNull();

        Set<String> names = childNames(marshal);
        assertThat(names).contains("json", "csv", "avro", "protobuf", "jaxb");
    }

    @Test
    void csvDataFormatNodeHasOptions() {
        JsonNode csv = nodes.get("csv");
        assertThat(csv).as("csv data format node must exist").isNotNull();
        assertThat(csv.get("title").asText()).isEqualTo("CSV");

        Set<String> names = childNames(csv);
        assertThat(names).contains("delimiter");
    }

    @Test
    void routeNodeHasRouteOptions() {
        JsonNode route = nodes.get("route");
        assertThat(route).as("route node must exist").isNotNull();

        Set<String> names = childNames(route);
        assertThat(names).contains("autoStartup", "streamCache", "logMask",
                "messageHistory", "group", "from");
    }

    @Test
    void setHeaderNodeHasNameAndExpression() {
        JsonNode setHeader = nodes.get("setHeader");
        assertThat(setHeader).as("setHeader node must exist").isNotNull();

        Set<String> names = childNames(setHeader);
        assertThat(names).contains("name", "expression");

        // name should be required
        JsonNode nameChild = findChild(setHeader, "name");
        assertThat(nameChild).isNotNull();
        assertThat(nameChild.get("required").asBoolean()).isTrue();
    }

    // --- Metadata completeness ---

    @Test
    void allChildrenHaveNameAndType() {
        nodes.fieldNames().forEachRemaining(nodeName -> {
            JsonNode node = nodes.get(nodeName);
            JsonNode children = node.get("children");
            if (children != null) {
                for (JsonNode child : children) {
                    assertThat(child.has("name"))
                            .as("child in node '%s' must have name", nodeName).isTrue();
                    assertThat(child.has("type"))
                            .as("child '%s' in node '%s' must have type",
                                    child.get("name").asText(), nodeName)
                            .isTrue();
                }
            }
        });
    }

    @Test
    void catalogEnrichedNodesHaveTitle() {
        // well-known nodes should have a title from catalog
        for (String name : new String[] {
                "split", "filter", "choice", "log",
                "marshal", "unmarshal", "route" }) {
            JsonNode node = nodes.get(name);
            assertThat(node).as("node '%s' must exist", name).isNotNull();
            assertThat(node.has("title"))
                    .as("node '%s' must have title from catalog", name).isTrue();
        }
    }

    @Test
    void childrenWithRefPointToExistingNodes() {
        nodes.fieldNames().forEachRemaining(nodeName -> {
            JsonNode node = nodes.get(nodeName);
            JsonNode children = node.get("children");
            if (children != null) {
                for (JsonNode child : children) {
                    if (child.has("ref")) {
                        String ref = child.get("ref").asText();
                        assertThat(nodes.has(ref))
                                .as("ref '%s' from child '%s' in node '%s' must point to existing node",
                                        ref, child.get("name").asText(), nodeName)
                                .isTrue();
                    }
                }
            }
        });
    }

    @Test
    void childrenWithIndexAreSorted() {
        nodes.fieldNames().forEachRemaining(nodeName -> {
            JsonNode node = nodes.get(nodeName);
            JsonNode children = node.get("children");
            if (children != null) {
                int lastIndex = -1;
                for (JsonNode child : children) {
                    if (child.has("index")) {
                        int index = child.get("index").asInt();
                        assertThat(index)
                                .as("children in node '%s' must be sorted by index (found %d after %d for '%s')",
                                        nodeName, index, lastIndex, child.get("name").asText())
                                .isGreaterThanOrEqualTo(lastIndex);
                        lastIndex = index;
                    }
                }
            }
        });
    }

    // --- Helpers ---

    private static Set<String> childNames(JsonNode node) {
        Set<String> names = new HashSet<>();
        JsonNode children = node.get("children");
        if (children != null) {
            for (JsonNode child : children) {
                names.add(child.get("name").asText());
            }
        }
        return names;
    }

    private static JsonNode findChild(JsonNode node, String name) {
        JsonNode children = node.get("children");
        if (children != null) {
            for (JsonNode child : children) {
                if (name.equals(child.get("name").asText())) {
                    return child;
                }
            }
        }
        return null;
    }

    private static JsonNode findLanguageNode(String langName) {
        // language nodes may have a suffix to avoid name collisions
        if (nodes.has(langName)) {
            return nodes.get(langName);
        }
        if (nodes.has(langName + "-lang")) {
            return nodes.get(langName + "-lang");
        }
        return null;
    }
}
