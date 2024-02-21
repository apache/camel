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

import org.apache.camel.FailedToCreateRouteException
import org.apache.camel.component.mock.MockEndpoint
import org.apache.camel.dsl.yaml.common.YamlDeserializationMode
import org.apache.camel.dsl.yaml.support.YamlTestSupport

class SplitTest extends YamlTestSupport {

    @Override
    def doSetup() {
        context.start()
    }

    def "split"() {
        setup:
            loadRoutes '''
                - from:
                    uri: "direct:route"
                    steps:
                      - split:
                          tokenize: ","
                          steps:
                            - to: "mock:split"
                      - to: "mock:route"
            '''

            withMock('mock:split') {
                expectedMessageCount 3
                expectedBodiesReceived 'a', 'b', 'c'
            }
            withMock('mock:route') {
                expectedMessageCount 1
                expectedBodiesReceived 'a,b,c'
            }

        when:
            withTemplate {
                to('direct:route').withBody('a,b,c').send()
            }

        then:
            MockEndpoint.assertIsSatisfied(context)
    }

    def "split (flow)"() {
        setup:
            loadRoutes '''
                - from:
                    uri: "direct:route"
                    steps:
                      - split:
                          tokenize: ","
                      - to: "mock:split"
            '''

            withMock('mock:split') {
                expectedMessageCount 3
                expectedBodiesReceived 'a', 'b', 'c'
            }

        when:
            withTemplate {
                to('direct:route').withBody('a,b,c').send()
            }

        then:
            MockEndpoint.assertIsSatisfied(context)
    }

    def "split xtokenize"() {
        setup:
        loadRoutes '''
                - from:
                    uri: "direct:route"
                    steps:
                      - split:
                          expression:
                            xtokenize:
                              mode: i
                              expression: /orders/order 
                          steps:
                            - to: "mock:split"
                      - to: "mock:route"
            '''

        withMock('mock:split') {
            expectedMessageCount 3
            expectedBodiesReceived '<order>Camel in Action</order>', '<order>ActiveMQ in Action</order>', '<order>DSL in Action</order>'
        }

        when:
        withTemplate {
            to('direct:route').withBody(createBody()).send()
        }

        then:
        MockEndpoint.assertIsSatisfied(context)
    }

    protected String createBody() {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\"?>\n");
        sb.append("<orders>\n");
        sb.append("  <order>Camel in Action</order>\n");
        sb.append("  <order>ActiveMQ in Action</order>\n");
        sb.append("  <order>DSL in Action</order>\n");
        sb.append("</orders>");
        return sb.toString();
    }


}
