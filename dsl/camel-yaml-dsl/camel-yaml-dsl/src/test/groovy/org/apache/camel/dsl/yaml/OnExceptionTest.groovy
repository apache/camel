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

import org.apache.camel.dsl.yaml.support.YamlTestSupport
import org.apache.camel.component.mock.MockEndpoint
import org.apache.camel.dsl.yaml.support.model.MyException
import org.apache.camel.dsl.yaml.support.model.MyFailingProcessor
import org.junit.jupiter.api.Assertions

class OnExceptionTest extends YamlTestSupport {
    def "onException"() {
        setup:
            loadRoutes """
                - beans:
                  - name: myFailingProcessor
                    type: ${MyFailingProcessor.name}
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

    def "Error: kebab-case: on-exception"() {
        when:
        var route = """
                - beans:
                  - name: myFailingProcessor
                    type: ${MyFailingProcessor.name}
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
        } catch (e) {
            with(e) {
                message.contains("additional properties")
            }
        }

    }
}
