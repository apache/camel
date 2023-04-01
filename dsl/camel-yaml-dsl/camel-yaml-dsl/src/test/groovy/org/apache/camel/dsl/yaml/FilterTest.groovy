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
import org.apache.camel.model.FilterDefinition
import org.apache.camel.model.ToDefinition
import org.apache.camel.model.language.ExpressionDefinition
import org.apache.camel.spi.Resource
import org.apache.camel.support.PluginHelper

class FilterTest extends YamlTestSupport {

    def "filter definition (#resource.location)"(Resource resource) {
        when:
            PluginHelper.getRoutesLoader(context).loadRoutes(resource)
        then:
            with(context.routeDefinitions[0].outputs[0], FilterDefinition) {
                with(expression, ExpressionDefinition) {
                    language == 'simple'
                    expression == '${body}'
                }
                with(outputs[0], ToDefinition) {
                    endpointUri == 'mock:filter'
                }
            }
        where:
            resource << [
                asResource('expression', '''
                    - from:
                        uri: "direct:start"
                        steps:    
                          - filter:  
                              simple: "${body}"
                              steps:
                                - to: "mock:filter"
                          - to: "mock:result"
                    '''),
                asResource('expression-block', '''
                    - from:
                        uri: "direct:start"
                        steps:    
                          - filter: 
                              expression: 
                                simple: "${body}"
                              steps:
                                - to: "mock:filter"
                          - to: "mock:result"
                    ''')
            ]
    }

    def "filter"() {
        setup:
            loadRoutes '''
                - from:
                    uri: "direct:route"
                    steps:
                      - filter:
                          simple: "${body.startsWith(\\"a\\")}"
                          steps:
                            - to: "mock:filter"
                      - to: "mock:route"
            '''

            withMock('mock:route') {
                expectedMessageCount 2
                expectedBodiesReceived 'a', 'b'
            }
            withMock('mock:filter') {
                expectedMessageCount 1
                expectedBodiesReceived 'a'
            }

        when:
            context.start()

            withTemplate {
                to('direct:route').withBody('a').send()
                to('direct:route').withBody('b').send()
            }

        then:
            MockEndpoint.assertIsSatisfied(context)
    }

    def "filter (flow)"() {
        setup:
            loadRoutes '''
                - from:
                    uri: "direct:route"
                    steps:
                      - filter:
                          simple: "${body.startsWith(\\"a\\")}"
                      - to: "mock:filter"
            '''

            withMock('mock:filter') {
                expectedMessageCount 1
                expectedBodiesReceived 'a'
            }

        when:
            context.start()

            withTemplate {
                to('direct:route').withBody('a').send()
                to('direct:route').withBody('b').send()
            }

        then:
            MockEndpoint.assertIsSatisfied(context)
    }

}
