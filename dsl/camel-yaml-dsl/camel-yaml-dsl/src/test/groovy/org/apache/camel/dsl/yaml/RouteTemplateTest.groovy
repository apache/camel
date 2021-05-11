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

import org.apache.camel.component.mock.MockEndpoint
import org.apache.camel.dsl.yaml.support.YamlTestSupport
import org.apache.camel.dsl.yaml.support.model.MyUppercaseProcessor
import org.apache.camel.model.LogDefinition
import org.apache.camel.model.RouteTemplateDefinition

class RouteTemplateTest extends YamlTestSupport {
    def "create template"() {
        when:
            loadRoutes '''
                - template:
                    id: "myTemplate"
                    from:
                      uri: "direct:info"
                      steps:
                        - log: "message"
            '''
        then:
            context.routeTemplateDefinitions.size() == 1

            with(context.routeTemplateDefinitions[0], RouteTemplateDefinition) {
                id == 'myTemplate'

                route.input.endpointUri == 'direct:info'
                with (route.outputs[0], LogDefinition) {
                    message == 'message'
                }
            }
    }

    def "create template with beans"() {
        setup:
            loadRoutes """
                - template:
                    id: "myTemplate"
                    beans:
                      - name: "myProcessor"
                        type: ${MyUppercaseProcessor.class.name}
                    from:
                      uri: "direct:{{directName}}"
                      steps:
                        - process:
                            ref: "{{myProcessor}}"
                - from:
                    uri: "direct:start"
                    steps:
                      - to: "direct:myId"
                      - to: "mock:result"
            """

            withMock('mock:result') {
                expectedMessageCount 1
                expectedBodiesReceived 'HELLO'
            }
        when:
            context.addRouteFromTemplate('myId', 'myTemplate', ['directName': 'myId'])
            context.start()

            withTemplate {
                to('direct:start').withBody('hello').send()
            }
        then:
            context.routeTemplateDefinitions.size() == 1

            with(context.routeTemplateDefinitions[0], RouteTemplateDefinition) {
                id == 'myTemplate'
                configurer != null
            }

            MockEndpoint.assertIsSatisfied(context)
    }

    def "create template with properties"() {
        when:
            loadRoutes """
                - template:
                    id: "myTemplate"
                    parameters:
                      - name: "foo"
                        default-value: "myDefaultFoo"
                        description: "myFooDescription"
                      - name: "bar"
                        description: "myBarDescription"
                    from:
                      uri: "direct:info"
                      steps:
                        - log: "message"
            """
        then:
            context.routeTemplateDefinitions.size() == 1

            with(context.routeTemplateDefinitions[0], RouteTemplateDefinition) {
                id == 'myTemplate'
                configurer == null

                templateParameters.any {
                    it.name == 'foo' && it.defaultValue == 'myDefaultFoo' && it.description == 'myFooDescription'
                }
                templateParameters.any {
                    it.name == 'bar' && it.defaultValue == null && it.description == 'myBarDescription'
                }

                route.input.endpointUri == 'direct:info'
                with (route.outputs[0], LogDefinition) {
                    message == 'message'
                }
            }
    }
}
