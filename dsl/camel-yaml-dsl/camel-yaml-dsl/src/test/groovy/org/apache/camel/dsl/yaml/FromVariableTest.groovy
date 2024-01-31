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
import org.junit.jupiter.api.Assertions

class FromVariableTest extends YamlTestSupport {

    def "fromVariable"() {
        setup:
            loadRoutes '''
                - from:
                    uri: "direct:start"
                    variableReceive: "myKey"
                    steps:
                      - setHeader:
                          name: foo
                          constant: "456"
                      - setHeader:
                          name: bar
                          constant: "Murphy"
                      - transform:
                          simple: "Bye ${body}"
                      - to: "mock:foo"
                      - setBody:
                          simple: "${variable:myKey}"
                      - to: "mock:result"
            '''

            withMock('mock:foo') {
                expectedBodiesReceived 'Bye '
                whenAnyExchangeReceived { e -> {
                    Map m = e.getVariable("header:myKey", Map.class)
                    Assertions.assertNotNull(m)
                    Assertions.assertEquals(1, m.size())
                    Assertions.assertEquals(123, m.get("foo"))
                }}
            }
            withMock('mock:result') {
                expectedBodiesReceived 'World'
            }

        when:
            context.start()

            withTemplate {
                to('direct:start').withBody('World').withHeader("foo", 123).send()
            }

        then:
            MockEndpoint.assertIsSatisfied(context)
    }

}
