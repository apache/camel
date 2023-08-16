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
        String javaSpecVersion = System.getProperty("java.specification.version");

        return Integer.parseInt(javaSpecVersion);

    }

    def "test"() {
        given:
            def settings = LoadSettings.builder().build()
        when:
            def ctr = new YamlDeserializationContext(settings)
            ctr.setCamelContext(new DefaultCamelContext());
            ctr.addResolver(new LocalResolver())

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


    static class LocalResolver implements YamlDeserializerResolver {
        @Override
        ConstructNode resolve(String id) {
            switch (id) {
                case 'my-node':
                case 'org.apache.camel.dsl.yaml.common.ConstructorResolverTest$MyNode':
                    return new MyNodeConstructor();
                case 'nested':
                case 'org.apache.camel.dsl.yaml.common.ConstructorResolverTest$MyNested':
                    return new MyNestedConstructor();
            }
            return null;
        }
    }

    @ToString
    static class MyNode {
        String message
        MyNested nested;
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
                    return false;
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
                    return false;
            }

            return true
        }
    }
}
