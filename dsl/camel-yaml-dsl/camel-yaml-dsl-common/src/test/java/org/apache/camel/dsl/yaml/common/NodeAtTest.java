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
package org.apache.camel.dsl.yaml.common;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.snakeyaml.engine.v2.common.FlowStyle;
import org.snakeyaml.engine.v2.common.ScalarStyle;
import org.snakeyaml.engine.v2.nodes.MappingNode;
import org.snakeyaml.engine.v2.nodes.NodeTuple;
import org.snakeyaml.engine.v2.nodes.ScalarNode;
import org.snakeyaml.engine.v2.nodes.Tag;

import static org.assertj.core.api.Assertions.assertThat;

public class NodeAtTest {

    private static ScalarNode scalar(String value) {
        return new ScalarNode(Tag.STR, value, ScalarStyle.PLAIN);
    }

    private static MappingNode mapping(Map<String, Object> entries) {
        List<NodeTuple> tuples = new ArrayList<>();
        for (Map.Entry<String, Object> e : entries.entrySet()) {
            var valNode = e.getValue() instanceof Map
                    ? mapping((Map<String, Object>) e.getValue())
                    : scalar(e.getValue().toString());
            tuples.add(new NodeTuple(scalar(e.getKey()), valNode));
        }
        return new MappingNode(Tag.MAP, tuples, FlowStyle.BLOCK);
    }

    @Test
    void nodeAtWithLeadingSlash() {
        var root = mapping(Map.of("foo", Map.of("bar", "hello")));
        var result = YamlDeserializerSupport.nodeAt(root, "/foo/bar");
        assertThat(result).isInstanceOf(ScalarNode.class);
        assertThat(((ScalarNode) result).getValue()).isEqualTo("hello");
    }

    @Test
    void nodeAtWithoutLeadingSlash() {
        var root = mapping(Map.of("foo", Map.of("bar", "hello")));
        var result = YamlDeserializerSupport.nodeAt(root, "foo/bar");
        assertThat(result).isInstanceOf(ScalarNode.class);
        assertThat(((ScalarNode) result).getValue()).isEqualTo("hello");
    }

    @Test
    void nodeAtSingleSegment() {
        var root = mapping(Map.of("foo", "hello"));
        var result = YamlDeserializerSupport.nodeAt(root, "/foo");
        assertThat(result).isInstanceOf(ScalarNode.class);
        assertThat(((ScalarNode) result).getValue()).isEqualTo("hello");
    }

    @Test
    void nodeAtReturnsNullForMissingPath() {
        var root = mapping(Map.of("foo", Map.of("bar", "hello")));
        var result = YamlDeserializerSupport.nodeAt(root, "/foo/missing");
        assertThat(result).isNull();
    }

    @Test
    void nodeAtWithEmptyPointerReturnsRoot() {
        var root = mapping(Map.of("foo", "hello"));
        var result = YamlDeserializerSupport.nodeAt(root, "");
        assertThat(result).isEqualTo(root);
    }

    @Test
    void nodeAtThreeLevelsDeep() {
        var root = mapping(Map.of("a", Map.of("b", Map.of("c", "deep"))));
        var result = YamlDeserializerSupport.nodeAt(root, "/a/b/c");
        assertThat(result).isInstanceOf(ScalarNode.class);
        assertThat(((ScalarNode) result).getValue()).isEqualTo("deep");
    }

    @Test
    void nodeAtStopsAtFirstMissingSegment() {
        var root = mapping(Map.of("a", Map.of("b", Map.of("c", "deep"))));
        var result = YamlDeserializerSupport.nodeAt(root, "/a/missing/c");
        assertThat(result).isNull();
    }
}
