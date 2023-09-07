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
package org.apache.camel.dsl.yaml

import org.apache.camel.dsl.yaml.support.MockRestConsumerFactory
import org.apache.camel.dsl.yaml.support.YamlTestSupport
import org.apache.camel.dsl.yaml.support.model.MyBean
import org.apache.camel.dsl.yaml.support.model.MyFooBar
import org.apache.camel.model.ToDefinition
import org.apache.camel.model.rest.GetDefinition
import org.apache.camel.model.rest.ParamDefinition
import org.apache.camel.model.rest.PostDefinition
import org.apache.camel.model.rest.RestDefinition
import org.apache.camel.model.rest.VerbDefinition
import org.apache.camel.support.PluginHelper
import org.junit.jupiter.api.Assertions

class RestTest extends YamlTestSupport {

    def "load rest configuration"() {
        when:
            loadRoutes """
                - beans:
                  - name: myRestConsumerFactory
                    type: ${MockRestConsumerFactory.name}
                - restConfiguration:
                    component: "servlet"
                    contextPath: "/foo"       
            """
        then:
            context.restConfiguration.component == 'servlet'
            context.restConfiguration.contextPath == '/foo'
    }

    def "load rest (to)"() {
        when:
            loadRoutes """
                - beans:
                  - name: myRestConsumerFactory
                    type: ${MockRestConsumerFactory.name}
                - rest:
                    get:
                      - path: "/foo"
                        type: ${MyFooBar.name}
                        outType: ${MyBean.name}
                        to: "direct:bar"
                - from:
                    uri: 'direct:bar'
                    steps:
                      - to: 'mock:bar'          
            """
        then:
            context.restDefinitions.size() == 1

            with(context.restDefinitions[0], RestDefinition) {
                verbs.size() == 1

                with(verbs[0], VerbDefinition) {
                    path == '/foo'
                    type == MyFooBar.name
                    outType == MyBean.name

                    with(to, ToDefinition) {
                        endpointUri == 'direct:bar'
                    }
                }
            }
    }

    def "load rest (route)"() {
        when:
            loadRoutes """
                - beans:
                  - name: myRestConsumerFactory
                    type: ${MockRestConsumerFactory.name}
                - rest:
                    get:
                     -  path: "/foo"
                        type: ${MyFooBar.name}
                        outType: ${MyBean.name}
                        to: "direct:bar"
                - from:
                    uri: 'direct:bar'
                    steps:
                      - to: 'mock:bar'
            """
        then:
            context.restDefinitions.size() == 1

            with(context.restDefinitions[0], RestDefinition) {
                verbs.size() == 1

                with(verbs[0], VerbDefinition) {
                    path == '/foo'
                    type == MyFooBar.name
                    outType == MyBean.name
                    with (to, ToDefinition) {
                        endpointUri  == 'direct:bar'
                    }
                }
            }
    }

    def "load rest (verb alias)"() {
        when:
            loadRoutes """
                - beans:
                  - name: myRestConsumerFactory
                    type: ${MockRestConsumerFactory.name}
                - rest:
                    post:
                      - path: "/foo"
                        id: "foolish"
                        type: ${MyFooBar.name}
                        outType: ${MyBean.name}
                        to: "direct:foo"
                      - path: "/baz"
                        id: "bazzy"
                        to: "direct:baz"
                    get:
                      - path: "/getFoo"
                        to: "direct:getFoo"
                - from:
                    uri: 'direct:bar'
                    steps:
                      - to: 'mock:bar'          
            """
        then:
            context.restDefinitions.size() == 1

            with(context.restDefinitions[0], RestDefinition) {
                verbs.size() == 3

                with(verbs[0], PostDefinition) {
                    path == '/foo'
                    type == MyFooBar.name
                    outType == MyBean.name

                    with(to, ToDefinition) {
                        endpointUri == 'direct:foo'
                    }
                }
                with(verbs[1], PostDefinition) {
                    path == '/baz'
                    with(to, ToDefinition) {
                        endpointUri == 'direct:baz'
                    }
                }
                with(verbs[2], GetDefinition) {
                    path == '/getFoo'
                    with(to, ToDefinition) {
                        endpointUri == 'direct:getFoo'
                    }
                }
            }
    }

    def "load rest (full)"() {
        setup:
            def rloc = 'classpath:/routes/rest-dsl.yaml'
            def rdsl = PluginHelper.getResourceLoader(context).resolveResource(rloc)
        when:
            loadRoutes rdsl
        then:
            context.restDefinitions != null
            !context.restDefinitions.isEmpty()
    }

    def "load rest (generated)"() {
        setup:
            def rloc = 'classpath:/rest-dsl/generated-rest-dsl.yaml'
            def rdsl = PluginHelper.getResourceLoader(context).resolveResource(rloc)
        when:
            loadRoutes rdsl
        then:
            context.restDefinitions != null
            !context.restDefinitions.isEmpty()
    }

    def "load rest (allowableValues)"() {
        setup:
            def rloc = 'classpath:/routes/rest-allowable-values-dsl.yaml'
            def rdsl = PluginHelper.getResourceLoader(context).resolveResource(rloc)
        when:
            loadRoutes rdsl
        then:
            context.restDefinitions != null
            !context.restDefinitions.isEmpty()

            with(context.restDefinitions[0].verbs[0].params[0], ParamDefinition) {
                allowableValues.size() == 3
                allowableValues[0].value == 'available'
                allowableValues[1].value == 'pending'
                allowableValues[2].value == 'sold'
            }
    }

    def "Error: kebab-case: rest-configuration"() {
        when:
        var route = """
                - beans:
                  - name: myRestConsumerFactory
                    type: ${MockRestConsumerFactory.name}
                - rest-configuration:
                    component: "servlet"
                    contextPath: "/foo"       
            """
        then:
        try {
            loadRoutes(route)
            Assertions.fail("Should have thrown exception")
        } catch (Exception e) {
            Assertions.assertTrue(e.message.contains("additional properties"), e.getMessage())
        }
    }

    def "Error: kebab-case: context-path"() {
        when:
        var route = """
                - beans:
                  - name: myRestConsumerFactory
                    type: ${MockRestConsumerFactory.name}
                - restConfiguration:
                    component: "servlet"
                    context-path: "/foo"       
            """
        then:
        try {
            loadRoutes(route)
            Assertions.fail("Should have thrown exception")
        } catch (Exception e) {
            Assertions.assertTrue(e.message.contains("additional properties"), e.getMessage())
        }
    }

    def "Error: kebab-case: out-type"() {
        when:
        var route = """
                - beans:
                  - name: myRestConsumerFactory
                    type: ${MockRestConsumerFactory.name}
                - rest:
                    get:
                      - path: "/foo"
                        type: ${MyFooBar.name}
                        out-type: ${MyBean.name}
                        to: "direct:bar"
                - from:
                    uri: 'direct:bar'
                    steps:
                      - to: 'mock:bar'          
            """
        then:
        try {
            loadRoutes(route)
            Assertions.fail("Should have thrown exception")
        } catch (Exception e) {
            Assertions.assertTrue(e.message.contains("additional properties"), e.getMessage())
        }
    }
}
