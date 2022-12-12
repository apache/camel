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

import org.apache.camel.Exchange
import org.apache.camel.component.mock.MockEndpoint
import org.apache.camel.dsl.yaml.support.YamlTestSupport
import org.apache.camel.dsl.yaml.support.model.MyException
import org.apache.camel.dsl.yaml.support.model.MyFailingProcessor
import org.apache.camel.model.RouteConfigurationDefinition
import org.junit.jupiter.api.Assertions

import static org.apache.camel.util.PropertiesHelper.asProperties

class RouteConfigurationTest extends YamlTestSupport {
    def "route-configuration"() {
        setup:
        loadRoutes """
                - beans:
                  - name: myFailingProcessor
                    type: ${MyFailingProcessor.name}
                - route-configuration:
                    on-exception:
                      - on-exception:
                          handled:
                            constant: "true"
                          exception:
                            - ${MyException.name}
                          steps:
                            - transform:
                                constant: "Sorry"
                            - to: "mock:on-exception"  
                - from:
                    uri: "direct:start"
                    steps:
                      - process: 
                          ref: "myFailingProcessor"            
            """

        withMock('mock:on-exception') {
            expectedBodiesReceived 'Sorry'
        }

        when:
        context.start()

        withTemplate {
            to('direct:start').withBody('hello').send()
        }
        then:
        MockEndpoint.assertIsSatisfied(context)
    }

    def "route-configuration-precondition"() {
        setup:
        context.getPropertiesComponent().setInitialProperties(asProperties("activate", "true"))
        loadRoutes """
                - beans:
                  - name: myFailingProcessor
                    type: ${MyFailingProcessor.name}
                - route-configuration:
                    precondition: "{{!activate}}"
                    on-exception:
                      - on-exception:
                          handled:
                            constant: "true"
                          exception:
                            - ${MyException.name}
                          steps:
                            - transform:
                                constant: "Not Activated"
                            - to: "mock:on-exception"  
                - route-configuration:
                    precondition: "{{activate}}"
                    on-exception:
                      - on-exception:
                          handled:
                            constant: "true"
                          exception:
                            - ${MyException.name}
                          steps:
                            - transform:
                                constant: "Activated"
                            - to: "mock:on-exception"
                    on-completion:
                      - on-completion:
                          steps:
                            - transform:
                                constant: "Completed"
                            - to: "mock:on-completion"
                - from:
                    uri: "direct:start"
                    steps:
                      - process: 
                          ref: "myFailingProcessor"            
            """

        withMock('mock:on-exception') {
            expectedBodiesReceived 'Activated'
        }

        withMock('mock:on-completion') {
            expectedBodiesReceived 'Completed'
        }

        when:
        context.start()

        withTemplate {
            to('direct:start').withBody('hello').send()
        }
        then:
        MockEndpoint.assertIsSatisfied(context)
        context.getRouteConfigurationDefinitions().size() == 1

        with(context.getRouteConfigurationDefinitions().get(0), RouteConfigurationDefinition) {
            precondition == '{{activate}}'
        }
    }

    def "route-configuration-separate"() {
        setup:
        // global configurations
        loadRoutes """
                - beans:
                  - name: myFailingProcessor
                    type: ${MyFailingProcessor.name}
                - route-configuration:
                    on-exception:
                    - on-exception:
                        handled:
                          constant: "true"
                        exception:
                          - ${MyException.name}
                        steps:
                          - transform:
                              constant: "Sorry"
                          - to: "mock:on-exception"  
            """
        // routes
        loadRoutes """
                - from:
                    uri: "direct:start"
                    steps:
                      - process: 
                          ref: "myFailingProcessor"            
                - from:
                    uri: "direct:start2"
                    steps:
                      - process: 
                          ref: "myFailingProcessor"            
            """

        withMock('mock:on-exception') {
            expectedBodiesReceived 'Sorry', 'Sorry'
        }

        when:
        context.start()

        withTemplate {
            to('direct:start').withBody('hello').send()
            to('direct:start2').withBody('hello2').send()
        }
        then:
        MockEndpoint.assertIsSatisfied(context)
    }

    def "route-configuration-id"() {
        setup:
        // global configurations
        loadRoutes """
                - beans:
                  - name: myFailingProcessor
                    type: ${MyFailingProcessor.name}
                - route-configuration:
                    id: handleError
                    on-exception:
                      - on-exception:
                          handled:
                            constant: "true"
                          exception:
                            - ${MyException.name}
                          steps:
                            - transform:
                                constant: "Sorry"
                            - to: "mock:on-exception"  
            """
        // routes
        loadRoutes """
                - route:
                    route-configuration-id: handleError 
                    from:
                      uri: "direct:start"
                      steps:
                        - process: 
                            ref: "myFailingProcessor"            
                - route:
                    from:
                      uri: "direct:start2"
                      steps:
                        - process: 
                            ref: "myFailingProcessor"            
            """

        withMock('mock:on-exception') {
            expectedBodiesReceived 'Sorry'
        }

        when:
        context.start()

        Exchange out1
        Exchange out2
        withTemplate {
            out1 = to('direct:start').withBody('hello').send()
            out2 = to('direct:start2').withBody('hello2').send()
        }

        then:
        MockEndpoint.assertIsSatisfied(context)

        Assertions.assertFalse(out1.isFailed())
        Assertions.assertTrue(out2.isFailed())
    }

    def "route-configuration-error-handler"() {
        setup:
        // global configurations
        loadRoutes """
                - beans:
                  - name: myFailingProcessor
                    type: ${MyFailingProcessor.name}
                - route-configuration:
                    error-handler:
                      dead-letter-channel: 
                        dead-letter-uri: "mock:on-error"
            """
        // routes
        loadRoutes """
                - from:
                    uri: "direct:start"
                    steps:
                      - process: 
                          ref: "myFailingProcessor"            
                - from:
                    uri: "direct:start2"
                    steps:
                      - process: 
                          ref: "myFailingProcessor"            
            """

        withMock('mock:on-error') {
            expectedBodiesReceived 'hello', 'hello2'
        }

        when:
        context.start()

        withTemplate {
            to('direct:start').withBody('hello').send()
            to('direct:start2').withBody('hello2').send()
        }
        then:
        MockEndpoint.assertIsSatisfied(context)
    }


}
