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

import java.util.List;

import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.annotations.YamlType;
import org.junit.jupiter.api.Test;
import org.snakeyaml.engine.v2.api.ConstructNode;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.nodes.Node;
import org.snakeyaml.engine.v2.nodes.ScalarNode;

import static org.assertj.core.api.Assertions.assertThat;

public class ConstructorResolverTest {

    static int getJavaMajorVersion() {
        String javaSpecVersion = System.getProperty("java.specification.version");
        return Integer.parseInt(javaSpecVersion);
    }

    @Test
    void preservesSameOrderResolversForDifferentNodeIds() {
        var settings = LoadSettings.builder().build();
        var ctr = new YamlDeserializationContext(settings);
        ctr.setCamelContext(new DefaultCamelContext());
        ctr.addResolver(new MyNodeResolver());
        ctr.addResolver(new MyNestedResolver());

        var load = new Load(settings, ctr);

        var result = load.loadFromString("""
                - my-node:
                    message: node
                    nested:
                      message: nested
                """);

        assertThat(result).isNotNull();
        var list = (List<?>) result;
        assertThat(list.size()).isEqualTo(1);

        var node = (MyNode) list.get(0);
        assertThat(node.message).isEqualTo("node");
        assertThat(node.nested).isNotNull();
        assertThat(node.nested.message).isEqualTo("nested");
    }

    @Test
    void clearsConstructorCacheWhenResolverListChanges() {
        var settings = LoadSettings.builder().build();
        var ctr = new YamlDeserializationContext(settings);
        ctr.setCamelContext(new DefaultCamelContext());
        ctr.addResolver(new FixedMyNodeResolver("first", YamlDeserializerResolver.ORDER_DEFAULT + 1));

        var load = new Load(settings, ctr);

        var first = (List<?>) load.loadFromString("""
                - my-node: {}
                """);

        ctr.addResolver(new FixedMyNodeResolver("second", YamlDeserializerResolver.ORDER_DEFAULT));
        var second = (List<?>) load.loadFromString("""
                - my-node: {}
                """);

        assertThat(((MyNode) first.get(0)).message).isEqualTo("first");
        assertThat(((MyNode) second.get(0)).message).isEqualTo("second");
    }

    // ---- inner support classes ----

    static class MyNodeResolver implements YamlDeserializerResolver {
        @Override
        public ConstructNode resolve(String id) {
            switch (id) {
                case "my-node":
                case "org.apache.camel.dsl.yaml.common.ConstructorResolverTest$MyNode":
                    return new MyNodeConstructor();
                default:
                    return null;
            }
        }
    }

    static class MyNestedResolver implements YamlDeserializerResolver {
        @Override
        public ConstructNode resolve(String id) {
            switch (id) {
                case "nested":
                case "org.apache.camel.dsl.yaml.common.ConstructorResolverTest$MyNested":
                    return new MyNestedConstructor();
                default:
                    return null;
            }
        }
    }

    static class FixedMyNodeResolver implements YamlDeserializerResolver {
        private final String message;
        private final int order;

        FixedMyNodeResolver(String message, int order) {
            this.message = message;
            this.order = order;
        }

        @Override
        public int getOrder() {
            return order;
        }

        @Override
        public ConstructNode resolve(String id) {
            if ("my-node".equals(id)) {
                return new FixedMyNodeConstructor(message);
            }
            return null;
        }
    }

    static class FixedMyNodeConstructor extends YamlDeserializerBase<MyNode> {
        private final String message;

        FixedMyNodeConstructor(String message) {
            super(MyNode.class);
            this.message = message;
        }

        @Override
        protected MyNode newInstance() {
            MyNode node = new MyNode();
            node.message = message;
            return node;
        }

        @Override
        protected boolean setProperty(MyNode target, String propertyKey, String propertyName, Node value) {
            return false;
        }
    }

    static class MyNode {
        String message;
        MyNested nested;

        @Override
        public String toString() {
            return "MyNode{message='" + message + "', nested=" + nested + "}";
        }
    }

    static class MyNested {
        String message;

        @Override
        public String toString() {
            return "MyNested{message='" + message + "'}";
        }
    }

    @YamlType(types = MyNode.class, nodes = { "my-node" })
    static class MyNodeConstructor extends YamlDeserializerBase<MyNode> {
        MyNodeConstructor() {
            super(MyNode.class);
        }

        @Override
        protected MyNode newInstance() {
            return new MyNode();
        }

        @Override
        protected MyNode newInstance(String value) {
            MyNode node = new MyNode();
            node.message = value;
            return node;
        }

        @Override
        protected boolean setProperty(MyNode target, String propertyKey, String propertyName, Node value) {
            switch (propertyKey) {
                case "message":
                    target.message = ((ScalarNode) value).getValue();
                    break;
                case "nested":
                    target.nested = asType(value, MyNested.class);
                    break;
                default:
                    return false;
            }
            return true;
        }
    }

    @YamlType(types = MyNested.class)
    static class MyNestedConstructor extends YamlDeserializerBase<MyNested> {
        MyNestedConstructor() {
            super(MyNested.class);
        }

        @Override
        protected MyNested newInstance() {
            return new MyNested();
        }

        @Override
        protected MyNested newInstance(String value) {
            MyNested nested = new MyNested();
            nested.message = value;
            return nested;
        }

        @Override
        protected boolean setProperty(MyNested target, String propertyKey, String propertyName, Node value) {
            switch (propertyKey) {
                case "message":
                    target.message = ((ScalarNode) value).getValue();
                    break;
                default:
                    return false;
            }
            return true;
        }
    }
}
