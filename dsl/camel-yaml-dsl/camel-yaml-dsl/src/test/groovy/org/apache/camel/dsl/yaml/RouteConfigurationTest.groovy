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
    def "routeConfiguration"() {
        setup:
        loadRoutes """
                - beans:
                  - name: myFailingProcessor
                    type: ${MyFailingProcessor.name}
                - routeConfiguration:
                    onException:
                      - onException:
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

    def "routeConfiguration onCompletion"() {
        setup:
        loadRoutes """
                - routeConfiguration:
                    onCompletion:
                      - onCompletion:
                          steps:
                            - transform:
                                constant: "Completed"
                            - to: "mock:on-completion"
                - from:
                    uri: "direct:start"
                    steps:
                      - log: "hello"
            """

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
    }

    def "routeConfiguration-precondition"() {
        setup:
        context.getPropertiesComponent().setInitialProperties(asProperties("activate", "true"))
        loadRoutes """
                - beans:
                  - name: myFailingProcessor
                    type: ${MyFailingProcessor.name}
                - routeConfiguration:
                    precondition: "{{!activate}}"
                    onException:
                      - onException:
                          handled:
                            constant: "true"
                          exception:
                            - ${MyException.name}
                          steps:
                            - transform:
                                constant: "Not Activated"
                            - to: "mock:on-exception"
                - routeConfiguration:
                    precondition: "{{activate}}"
                    onException:
                      - onException:
                          handled:
                            constant: "true"
                          exception:
                            - ${MyException.name}
                          steps:
                            - transform:
                                constant: "Activated"
                            - to: "mock:on-exception"
                    onCompletion:
                      - onCompletion:
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

    def "routeConfiguration-separate"() {
        setup:
        // global configurations
        loadRoutes """
                - beans:
                  - name: myFailingProcessor
                    type: ${MyFailingProcessor.name}
                - routeConfiguration:
                    onException:
                      - onException:
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

    def "routeConfigurationId"() {
        setup:
        // global configurations
        loadRoutes """
                - beans:
                  - name: myFailingProcessor
                    type: ${MyFailingProcessor.name}
                - routeConfiguration:
                    id: handleError
                    onException:
                      - onException:
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
                    routeConfigurationId: handleError 
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

    def "routeConfiguration-errorHandler"() {
        setup:
        // global configurations
        loadRoutes """
                - beans:
                  - name: myFailingProcessor
                    type: ${MyFailingProcessor.name}
                - routeConfiguration:
                    errorHandler:
                      deadLetterChannel:
                        deadLetterUri: "mock:on-error"
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

    def "routeConfiguration intercept"() {
        setup:
        loadRoutesNoValidate """
                - routeConfiguration:
                    intercept:
                      - intercept:
                          steps:
                            - transform:
                                constant: "intercepted"
                            - to: "mock:intercepted"
                - from:
                    uri: "direct:start"
                    steps:
                      - log: "hello"
            """

        withMock('mock:intercepted') {
            expectedBodiesReceived 'intercepted'
        }

        when:
        context.start()

        withTemplate {
            to('direct:start').withBody('hello').send()
        }
        then:
        MockEndpoint.assertIsSatisfied(context)
    }

    def "routeConfiguration interceptFrom"() {
        setup:
        loadRoutesNoValidate """
                - routeConfiguration:
                    interceptFrom:
                      - interceptFrom:
                          uri: "direct:start"
                          steps:
                            - transform:
                                constant: "intercepted"
                            - to: "mock:intercepted"
                - from:
                    uri: "direct:start"
                    steps:
                      - log: "hello"
            """

        withMock('mock:intercepted') {
            expectedBodiesReceived 'intercepted'
        }

        when:
        context.start()

        withTemplate {
            to('direct:start').withBody('hello').send()
        }
        then:
        MockEndpoint.assertIsSatisfied(context)
    }

    def "routeConfiguration interceptSendToEndpoint"() {
        setup:
        loadRoutesNoValidate """
                - routeConfiguration:
                    interceptSendToEndpoint:
                      - interceptSendToEndpoint:
                          uri: "direct:start"
                          steps:
                            - transform:
                                constant: "intercepted"
                            - to: "mock:intercepted"
                - from:
                    uri: "direct:start"
                    steps:
                      - log: "hello"
            """

        withMock('mock:intercepted') {
            expectedBodiesReceived 'intercepted'
        }

        when:
        context.start()

        withTemplate {
            to('direct:start').withBody('hello').send()
        }
        then:
        MockEndpoint.assertIsSatisfied(context)
    }

    def "Error: kebab-case: route-configuration"() {
        when:
        var route = """
                - beans:
                  - name: myFailingProcessor
                    type: ${MyFailingProcessor.name}
                - route-configuration:
                    onException:
                      - onException:
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
        then:
        try {
            loadRoutes(route)
            Assertions.fail("Should have thrown exception")
        } catch (Exception e) {
            Assertions.assertTrue(e.message.contains("additional properties"), e.getMessage())
        }
    }

    def "Error: kebab-case: on-exception"() {
        when:
        var route = """
                - beans:
                  - name: myFailingProcessor
                    type: ${MyFailingProcessor.name}
                - routeConfiguration:
                    on-exception:
                      - onException:
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
        then:
        try {
            loadRoutes(route)
            Assertions.fail("Should have thrown exception")
        } catch (Exception e) {
            Assertions.assertTrue(e.message.contains("additional properties"), e.getMessage())
        }
    }

    def "Error: kebab-case: onException/on-exception"() {
        when:
        var route = """
                - beans:
                  - name: myFailingProcessor
                    type: ${MyFailingProcessor.name}
                - routeConfiguration:
                    onException:
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
        then:
        try {
            loadRoutes(route)
            Assertions.fail("Should have thrown exception")
        } catch (Exception e) {
            Assertions.assertTrue(e.message.contains("additional properties"), e.getMessage())
        }
    }

    def "Error: kebab-case: dead-letter-channel"() {
        when:
        var route = """
                - beans:
                  - name: myFailingProcessor
                    type: ${MyFailingProcessor.name}
                - routeConfiguration:
                    errorHandler:
                      dead-letter-channel:
                        deadLetterUri: "mock:on-error"
            """
        then:
        try {
            loadRoutes(route)
            Assertions.fail("Should have thrown exception")
        } catch (Exception e) {
            Assertions.assertTrue(e.message.contains("additional properties"), e.getMessage())
        }
    }

    def "Error: kebab-case: dead-letter-uri"() {
        when:
        var route = """
                - beans:
                  - name: myFailingProcessor
                    type: ${MyFailingProcessor.name}
                - routeConfiguration:
                    errorHandler:
                      deadLetterChannel:
                        dead-letter-uri: "mock:on-error"
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
