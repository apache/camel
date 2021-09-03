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

    def "split (flow disabled)"() {
        setup:
            setFlowMode(YamlDeserializationMode.CLASSIC)
        when:
            loadRoutes '''
                - from:
                    uri: "direct:route"
                    steps:
                      - split:
                          tokenize: ","
                      - to: "mock:split"
            '''
        then:
            def ex = thrown(FailedToCreateRouteException)
            ex.message.contains('Failed to create route')
    }
}
