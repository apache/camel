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
package org.apache.camel.dsl.yaml.common

import org.snakeyaml.engine.v2.common.FlowStyle
import org.snakeyaml.engine.v2.common.ScalarStyle
import org.snakeyaml.engine.v2.nodes.MappingNode
import org.snakeyaml.engine.v2.nodes.NodeTuple
import org.snakeyaml.engine.v2.nodes.ScalarNode
import org.snakeyaml.engine.v2.nodes.Tag
import spock.lang.Specification

class NodeAtTest extends Specification {

    private static ScalarNode scalar(String value) {
        return new ScalarNode(Tag.STR, value, ScalarStyle.PLAIN)
    }

    private static MappingNode mapping(Map<String, Object> entries) {
        def tuples = entries.collect { k, v ->
            def valNode = v instanceof Map ? mapping(v as Map<String, Object>) : scalar(v.toString())
            new NodeTuple(scalar(k), valNode)
        }
        return new MappingNode(Tag.MAP, tuples, FlowStyle.BLOCK)
    }

    def "nodeAt with leading slash"() {
        given:
            def root = mapping(foo: [bar: 'hello'])
        when:
            def result = YamlDeserializerSupport.nodeAt(root, "/foo/bar")
        then:
            result instanceof ScalarNode
            (result as ScalarNode).value == 'hello'
    }

    def "nodeAt without leading slash"() {
        given:
            def root = mapping(foo: [bar: 'hello'])
        when:
            def result = YamlDeserializerSupport.nodeAt(root, "foo/bar")
        then:
            result instanceof ScalarNode
            (result as ScalarNode).value == 'hello'
    }

    def "nodeAt single segment"() {
        given:
            def root = mapping(foo: 'hello')
        when:
            def result = YamlDeserializerSupport.nodeAt(root, "/foo")
        then:
            result instanceof ScalarNode
            (result as ScalarNode).value == 'hello'
    }

    def "nodeAt returns null for missing path"() {
        given:
            def root = mapping(foo: [bar: 'hello'])
        when:
            def result = YamlDeserializerSupport.nodeAt(root, "/foo/missing")
        then:
            result == null
    }

    def "nodeAt with empty pointer returns root"() {
        given:
            def root = mapping(foo: 'hello')
        when:
            def result = YamlDeserializerSupport.nodeAt(root, "")
        then:
            result == root
    }

    def "nodeAt three levels deep"() {
        given:
            def root = mapping(a: [b: [c: 'deep']])
        when:
            def result = YamlDeserializerSupport.nodeAt(root, "/a/b/c")
        then:
            result instanceof ScalarNode
            (result as ScalarNode).value == 'deep'
    }

    def "nodeAt stops at first missing segment"() {
        given:
            def root = mapping(a: [b: [c: 'deep']])
        when:
            def result = YamlDeserializerSupport.nodeAt(root, "/a/missing/c")
        then:
            result == null
    }
}
