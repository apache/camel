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

class PollEnrichVariableTest extends YamlTestSupport {

    def "pollEnrichVariable receive"() {
        setup:
            loadRoutes '''
                - route:
                    from:
                      uri: direct:receive
                      steps:
                        - pollEnrich:  
                            constant: "seda:foo"
                            timeout: "1000"
                            variableReceive: bye
                        - to:
                            uri: mock:after
                        - setBody:
                            simple: ${variable:bye}
                        - to:
                            uri: mock:result
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
                to('seda:foo').withBody('Bye World').send()
                to('direct:receive').withBody('World').send()
            }

        then:
            MockEndpoint.assertIsSatisfied(context)
    }

}
