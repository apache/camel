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
import org.apache.camel.support.ResourceHelper
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

    // CAMEL-24702: handled is an expression; a plain value must fail with a message that says what to write
    def "onException handled as plain value fails with a helpful message"() {
        when:
            loadRoutes([ResourceHelper.fromString("route-0.yaml", '''
                - onException:
                    handled: true
                    exception:
                      - java.lang.Exception
                    steps:
                      - to: "mock:on-exception"
            '''.stripIndent())], false)
        then:
            def e = thrown(Exception)
            def messages = []
            for (Throwable t = e; t != null; t = t.cause) {
                messages << t.message
            }
            messages.any { it != null && it.contains('an expression is expected here, not a plain value (true)') && it.contains('constant: {expression: "true"}') }
    }


    // CAMEL-24702: an unsupported field names the node and the field; bean as a language says method:
    def "setBody with bean as a field fails with a message naming the field"() {
        when:
            loadRoutes([ResourceHelper.fromString("route-1.yaml", '''
                - from:
                    uri: timer:tick
                    steps:
                      - setBody:
                          bean: myBean
            '''.stripIndent())], false)
        then:
            def e = thrown(Exception)
            def messages = []
            for (Throwable t = e; t != null; t = t.cause) {
                messages << t.message
            }
            messages.any { it != null && it.contains('Error constructing YAML node id: setBody: unsupported field: bean') && it.contains('expression: {method: {ref: myBean, method: process}}') }
    }
}
