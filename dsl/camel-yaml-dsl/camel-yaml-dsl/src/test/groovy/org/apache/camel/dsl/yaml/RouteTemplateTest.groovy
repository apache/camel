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
import org.apache.camel.dsl.yaml.support.model.MyBean
import org.apache.camel.dsl.yaml.support.model.MySetBody
import org.apache.camel.dsl.yaml.support.model.MyUppercaseProcessor
import org.apache.camel.model.LogDefinition
import org.apache.camel.model.RouteTemplateDefinition

class RouteTemplateTest extends YamlTestSupport {
    @Override
    def doSetup() {
        context.start()
    }

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
        when:
            loadRoutes """
                - template:
                    id: "myTemplate"
                    beans:
                      - name: "myBean"
                        type: ${MyBean.class.name}
                        properties:
                          field1: 'f1'
                    from:
                      uri: "direct:info"
                      steps:
                        - log: "message"
            """
        then:
            context.routeTemplateDefinitions.size() == 1

            with(context.routeTemplateDefinitions[0], RouteTemplateDefinition) {
                id == 'myTemplate'
                configurer != null

                route.input.endpointUri == 'direct:info'
                with (route.outputs[0], LogDefinition) {
                    message == 'message'
                }
            }
    }
    def "kamelet"() {
        setup:
            loadRoutes """
                - template:
                    id: "myTemplate"
                    beans:
                      - name: "myProcessor"
                        type: ${MyUppercaseProcessor.class.name}
                    from:
                      uri: "kamelet:source"
                      steps:
                        - process:
                            ref: "myProcessor"
                - from:
                    uri: "direct:start"
                    steps:
                      - to: "kamelet:myTemplate"
                      - to: "mock:result"
            """

            withMock('mock:result') {
                expectedMessageCount 1
                expectedBodiesReceived 'HELLO'
            }
        when:
            withTemplate {
                to('direct:start').withBody('hello').send()
            }

        then:
            MockEndpoint.assertIsSatisfied(context)
    }


    def "kamelet with properties"() {
        setup:
            loadRoutes """
                - template:
                    id: "myTemplate"  
                    beans:
                      - name: "myProcessor"
                        type: ${MySetBody.class.name}
                        properties:
                          payload: "{{bodyValue}}"
                    from: 
                      uri: "kamelet:source"
                      steps:
                        - process: 
                            ref: "myProcessor"
                - from:
                    uri: "direct:start"
                    steps:
                      - to: "kamelet:myTemplate?bodyValue=myValue"
                      - to: "mock:result" 
            """

            withMock('mock:result') {
                expectedMessageCount 1
                expectedBodiesReceived 'myValue'
            }
        when:
            withTemplate {
                to('direct:start').withBody('hello').send()
            }

        then:
            MockEndpoint.assertIsSatisfied(context)
    }
}
