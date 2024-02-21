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

class ToVariableDynamicTest extends YamlTestSupport {

    def "toDVariable send"() {
        setup:
            loadRoutes '''
                - route:
                    from:
                      uri: direct:send
                      steps:
                        - setVariable:
                            name: hello
                            simple:
                              expression: Camel
                        - to:
                            uri: mock:before
                        - toD:
                            uri: direct:${header.where}
                            variableSend: hello
                        - to:
                            uri: mock:result
                - route:
                    from:
                      uri: direct:receive
                      steps:
                        - toD:
                            uri: direct:${header.where}
                            variableReceive: bye
                        - to:
                            uri: mock:after
                        - setBody:
                            simple:
                              expression: "${variable:bye}"
                        - to:
                            uri: mock:result
                - route:
                    from:
                      uri: direct:sendAndReceive
                      steps:
                        - setVariable:
                            name: hello
                            simple:
                              expression: Camel
                        - to:
                            uri: mock:before
                        - toD:
                            uri: direct:${header.where}
                            variableReceive: bye
                            variableSend: hello
                        - to:
                            uri: mock:result
                - route:
                    from:
                      uri: direct:foo
                      steps:
                        - transform:
                            simple:
                              expression: "Bye ${body}"
            '''

            withMock('mock:before') {
                expectedBodiesReceived 'World'
                expectedVariableReceived("hello", "Camel")
            }
            withMock('mock:result') {
                expectedBodiesReceived 'Bye Camel'
                expectedVariableReceived("hello", "Camel")
            }

        when:
            context.start()

            withTemplate {
                to('direct:send').withBody('World').withHeader("where", "foo").send()
            }

        then:
            MockEndpoint.assertIsSatisfied(context)
    }


    def "toDVariable receive"() {
        setup:
        loadRoutes '''
                - route:
                    from:
                      uri: direct:send
                      steps:
                        - setVariable:
                            name: hello
                            simple:
                              expression: Camel
                        - to:
                            uri: mock:before
                        - toD:
                            uri: direct:${header.where}
                            variableSend: hello
                        - to:
                            uri: mock:result
                - route:
                    from:
                      uri: direct:receive
                      steps:
                        - toD:
                            uri: direct:${header.where}
                            variableReceive: bye
                        - to:
                            uri: mock:after
                        - setBody:
                            simple:
                              expression: "${variable:bye}"
                        - to:
                            uri: mock:result
                - route:
                    from:
                      uri: direct:sendAndReceive
                      steps:
                        - setVariable:
                            name: hello
                            simple:
                              expression: Camel
                        - to:
                            uri: mock:before
                        - toD:
                            uri: direct:${header.where}
                            variableReceive: bye
                            variableSend: hello
                        - to:
                            uri: mock:result
                - route:
                    from:
                      uri: direct:foo
                      steps:
                        - transform:
                            simple:
                              expression: "Bye ${body}"
            '''

        withMock('mock:after') {
            expectedBodiesReceived 'World'
            expectedVariableReceived("bye", "Bye World")
        }
        withMock('mock:result') {
            expectedBodiesReceived 'Bye World'
            expectedVariableReceived("bye", "Bye World")
        }

        when:
        context.start()

        withTemplate {
            to('direct:receive').withBody('World').withHeader("where", "foo").send()
        }

        then:
        MockEndpoint.assertIsSatisfied(context)
    }

    def "toDVariable sendAndReceive"() {
        setup:
        loadRoutes '''
                - route:
                    from:
                      uri: direct:send
                      steps:
                        - setVariable:
                            name: hello
                            simple:
                              expression: Camel
                        - to:
                            uri: mock:before
                        - toD:
                            uri: direct:${header.where}
                            variableSend: hello
                        - to:
                            uri: mock:result
                - route:
                    from:
                      uri: direct:receive
                      steps:
                        - toD:
                            uri: direct:${header.where}
                            variableReceive: bye
                        - to:
                            uri: mock:after
                        - setBody:
                            simple:
                              expression: "${variable:bye}"
                        - to:
                            uri: mock:result
                - route:
                    from:
                      uri: direct:sendAndReceive
                      steps:
                        - setVariable:
                            name: hello
                            simple:
                              expression: Camel
                        - to:
                            uri: mock:before
                        - toD:
                            uri: direct:${header.where}
                            variableReceive: bye
                            variableSend: hello
                        - to:
                            uri: mock:result
                - route:
                    from:
                      uri: direct:foo
                      steps:
                        - transform:
                            simple:
                              expression: "Bye ${body}"
            '''

        withMock('mock:before') {
            expectedBodiesReceived 'World'
            expectedVariableReceived("hello", "Camel")
        }
        withMock('mock:result') {
            expectedBodiesReceived 'World'
            expectedVariableReceived("bye", "Bye Camel")
        }

        when:
        context.start()

        withTemplate {
            to('direct:sendAndReceive').withBody('World').withHeader("where", "foo").send()
        }

        then:
        MockEndpoint.assertIsSatisfied(context)
    }
}
