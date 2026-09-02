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

class CacheTest extends YamlTestSupport {
    def 'cache'() {
        setup:
        loadRoutes '''
                - from:
                    uri: "direct:route"
                    steps:
                      - cache:
                          simple: "${header.key}"
                          steps:
                            - to: "mock:service"
                            - setBody:
                                simple: "response-${header.key}"
                      - to: "mock:result"
            '''

        withMock('mock:service') {
            expectedMessageCount 2
        }
        withMock('mock:result') {
            expectedMessageCount 3
        }

        when:
        context.start()

        withTemplate {
            to('direct:route').withBody('req1').withHeader('key', 'A').send()
            to('direct:route').withBody('req2').withHeader('key', 'B').send()
            // third call with key A should hit cache, so mock:service only gets 2 calls
            to('direct:route').withBody('req3').withHeader('key', 'A').send()
        }
        then:
        MockEndpoint.assertIsSatisfied(context)
    }

    def 'cache with options'() {
        setup:
        loadRoutes '''
                - beans:
                  - name: myKvr
                    type: org.apache.camel.support.MemoryKeyValueRepository
                - from:
                    uri: "direct:route"
                    steps:
                      - cache:
                          simple: "${header.key}"
                          keyValueRepository: "myKvr"
                          ttl: "10m"
                          steps:
                            - to: "mock:service"
                            - setBody:
                                simple: "response-${header.key}"
                      - to: "mock:result"
            '''

        withMock('mock:service') {
            expectedMessageCount 1
        }
        withMock('mock:result') {
            expectedMessageCount 2
        }

        when:
        context.start()

        withTemplate {
            to('direct:route').withBody('req1').withHeader('key', 'X').send()
            // second call with same key should hit cache
            to('direct:route').withBody('req2').withHeader('key', 'X').send()
        }
        then:
        MockEndpoint.assertIsSatisfied(context)
    }
}
