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
import org.apache.camel.spi.DataFormat
import org.apache.camel.support.service.ServiceSupport

class UnmarshalVariableTest extends YamlTestSupport {

    def "unmarshalVariable send"() {
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
                        - unmarshal:
                            custom: myDF
                            variableSend: hello
                        - to:
                            uri: mock:result
                - route:
                    from:
                      uri: direct:receive
                      steps:
                        - unmarshal:
                            custom: myDF
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
                        - unmarshal:
                            custom: myDF
                            variableReceive: bye
                            variableSend: hello
                        - to:
                            uri: mock:result
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
            context.registry.bind("myDF", new MyByeDataFormat())
            context.start()

            withTemplate {
                to('direct:send').withBody('World').send()
            }

        then:
            MockEndpoint.assertIsSatisfied(context)
    }


    def "unmarshalVariable receive"() {
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
                        - unmarshal:
                            custom: myDF
                            variableSend: hello
                        - to:
                            uri: mock:result
                - route:
                    from:
                      uri: direct:receive
                      steps:
                        - unmarshal:
                            custom: myDF
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
                        - unmarshal:
                            custom: myDF
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
        context.registry.bind("myDF", new MyByeDataFormat())
        context.start()

        withTemplate {
            to('direct:receive').withBody('World').send()
        }

        then:
        MockEndpoint.assertIsSatisfied(context)
    }

    def "unmarshalVariable sendAndReceive"() {
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
                        - unmarshal:
                            custom: myDF
                            variableSend: hello
                        - to:
                            uri: mock:result
                - route:
                    from:
                      uri: direct:receive
                      steps:
                        - unmarshal:
                            custom: myDF
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
                        - unmarshal:
                            custom: myDF
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
        context.registry.bind("myDF", new MyByeDataFormat())
        context.start()

        withTemplate {
            to('direct:sendAndReceive').withBody('World').send()
        }

        then:
        MockEndpoint.assertIsSatisfied(context)
    }

    static class MyByeDataFormat extends ServiceSupport implements DataFormat {

        @Override
        void marshal(Exchange exchange, Object graph, OutputStream stream) throws Exception {
            // noop
        }

        @Override
        Object unmarshal(Exchange exchange, InputStream stream) throws Exception {
            return "Bye " + exchange.getContext().getTypeConverter().convertTo(String.class, exchange, stream);
        }
    }

}
