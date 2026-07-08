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

import groovy.transform.ToString
import org.apache.camel.impl.DefaultCamelContext
import org.apache.camel.spi.annotations.YamlType
import org.snakeyaml.engine.v2.api.ConstructNode
import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings
import org.snakeyaml.engine.v2.nodes.Node
import org.snakeyaml.engine.v2.nodes.ScalarNode
import spock.lang.Specification

class ConstructorResolverTest extends Specification {

    static int getJavaMajorVersion() {
        String javaSpecVersion = System.getProperty("java.specification.version")

        return Integer.parseInt(javaSpecVersion)

    }

    def "preserves same order resolvers for different node ids"() {
        given:
            def settings = LoadSettings.builder().build()
        when:
            def ctr = new YamlDeserializationContext(settings)
            ctr.setCamelContext(new DefaultCamelContext())
            ctr.addResolver(new MyNodeResolver())
            ctr.addResolver(new MyNestedResolver())

            def load = new Load(settings, ctr)

            def result = load.loadFromString('''
                - my-node:
                    message: node
                    nested:
                      message: nested
            '''.stripLeading())
        then:
            with(result, List) {
                size() == 1

                with(get(0), MyNode) {
                    message == 'node'
                    nested != null
                    nested.message == 'nested'
                }
            }

    }

    def "clears constructor cache when resolver list changes"() {
        given:
            def settings = LoadSettings.builder().build()
            def ctr = new YamlDeserializationContext(settings)
            ctr.setCamelContext(new DefaultCamelContext())
            ctr.addResolver(new FixedMyNodeResolver('first', YamlDeserializerResolver.ORDER_DEFAULT + 1))

            def load = new Load(settings, ctr)

        when:
            def first = load.loadFromString('''
                - my-node: {}
            '''.stripLeading())

            ctr.addResolver(new FixedMyNodeResolver('second', YamlDeserializerResolver.ORDER_DEFAULT))
            def second = load.loadFromString('''
                - my-node: {}
            '''.stripLeading())

        then:
            first[0].message == 'first'
            second[0].message == 'second'
    }


    static class MyNodeResolver implements YamlDeserializerResolver {
        @Override
        ConstructNode resolve(String id) {
            switch (id) {
                case 'my-node':
                case 'org.apache.camel.dsl.yaml.common.ConstructorResolverTest$MyNode':
                    return new MyNodeConstructor()
            }
            return null
        }
    }

    static class MyNestedResolver implements YamlDeserializerResolver {
        @Override
        ConstructNode resolve(String id) {
            switch (id) {
                case 'nested':
                case 'org.apache.camel.dsl.yaml.common.ConstructorResolverTest$MyNested':
                    return new MyNestedConstructor()
            }
            return null
        }
    }

    static class FixedMyNodeResolver implements YamlDeserializerResolver {
        private final String message
        private final int order

        FixedMyNodeResolver(String message, int order) {
            this.message = message
            this.order = order
        }

        @Override
        int getOrder() {
            return order
        }

        @Override
        ConstructNode resolve(String id) {
            switch (id) {
                case 'my-node':
                    return new FixedMyNodeConstructor(message)
            }
            return null
        }
    }

    static class FixedMyNodeConstructor extends YamlDeserializerBase<MyNode> {
        private final String message

        FixedMyNodeConstructor(String message) {
            super(MyNode.class)
            this.message = message
        }

        @Override
        protected MyNode newInstance() {
            return new MyNode(message: message)
        }

        @Override
        protected boolean setProperty(MyNode target, String propertyKey, String propertyName, Node value) {
            return false
        }
    }

    @ToString
    static class MyNode {
        String message
        MyNested nested
    }

    @ToString
    static class MyNested {
        String message
    }

    @YamlType(types = MyNode.class, nodes = [ "my-node" ])
    static class MyNodeConstructor extends YamlDeserializerBase<MyNode> {
        MyNodeConstructor() {
            super(MyNode.class)
        }

        @Override
        protected MyNode newInstance() {
            return new MyNode()
        }

        @Override
        protected MyNode newInstance(String value) {
            return new MyNode(message: value)
        }

        @Override
        protected boolean setProperty(MyNode target, String propertyKey, String propertyName, Node value) throws Exception {
            switch (propertyKey) {
                case 'message':
                    target.message = ((ScalarNode)value).value
                    break
                case 'nested':
                    target.nested = asType(value, MyNested.class)
                    break
                default:
                    return false
            }

            return true
        }
    }

    @YamlType(types = MyNested.class)
    static class MyNestedConstructor extends YamlDeserializerBase<MyNested> {
        MyNestedConstructor() {
            super(MyNested.class)
        }

        @Override
        protected MyNested newInstance() {
            return new MyNested()
        }

        @Override
        protected MyNested newInstance(String value) {
            return new MyNested(message: value)
        }

        @Override
        protected boolean setProperty(MyNested target, String propertyKey, String propertyName, Node value) throws Exception {
            switch (propertyKey) {
                case 'message':
                    target.message = ((ScalarNode)value).value
                    break
                default:
                    return false
            }

            return true
        }
    }
}
