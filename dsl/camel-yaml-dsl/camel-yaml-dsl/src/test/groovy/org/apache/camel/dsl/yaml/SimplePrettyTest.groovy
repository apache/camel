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

class SimplePrettyTest extends YamlTestSupport {

    def "prettyXML"() {
        setup:
        loadRoutes '''
                - route:
                    from:
                      uri: direct:xml
                      steps:    
                        - setBody:
                            simple:
                              expression: "<person><name>Jack</name></person>"
                              pretty: true
                        - to:
                            uri: mock:result
                        '''
        withMock('mock:result') {
            expectedMessageCount 1
        }

        when:
        context.start()

        withTemplate {
            to('direct:xml').withBody('Hello World').send()
        }

        then:
        MockEndpoint.assertIsSatisfied(context)
    }

    def "prettyJSon"() {
        setup:
        loadRoutes '''
                - route:
                    from:
                      uri: direct:json
                      steps:    
                        - setBody:
                            simple:
                              expression: '"name": "Jack", "age": 44 }'
                              pretty: true
                        - to:
                            uri: mock:result
                        '''
        withMock('mock:result') {
            expectedMessageCount 1
        }

        when:
        context.start()

        withTemplate {
            to('direct:json').withBody('Hello World').send()
        }

        then:
        MockEndpoint.assertIsSatisfied(context)
    }

    def "prettyText"() {
        setup:
        loadRoutes '''
                - route:
                    from:
                      uri: direct:text
                      steps:    
                        - setBody:
                            simple:
                              expression: 'Hello ${body}'
                              pretty: true
                        - to:
                            uri: mock:result
                        '''
        withMock('mock:result') {
            expectedBodiesReceived 'Hello World'
        }

        when:
        context.start()

        withTemplate {
            to('direct:text').withBody('World').send()
        }

        then:
        MockEndpoint.assertIsSatisfied(context)
    }
}
