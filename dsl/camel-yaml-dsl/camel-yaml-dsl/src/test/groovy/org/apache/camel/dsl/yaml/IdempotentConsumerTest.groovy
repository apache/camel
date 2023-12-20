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
import org.junit.jupiter.api.Assertions

class IdempotentConsumerTest extends YamlTestSupport {
    def 'idempotentConsumer'() {
        setup:
            loadRoutes '''
                - beans:
                  - name: myRepo
                    type: org.apache.camel.support.processor.idempotent.MemoryIdempotentRepository
                - from:
                    uri: "direct:route"
                    steps:
                      - idempotentConsumer:
                          simple: "${header.id}"
                          idempotentRepository: "myRepo"
                          steps:
                            - to: "mock:idempotent"
                      - to: "mock:route"
            '''

            withMock('mock:idempotent') {
                expectedBodiesReceived 'a', 'b', 'c'
            }
            withMock('mock:route') {
                expectedBodiesReceived 'a', 'b', 'a2', 'b2', 'c'
            }

        when:
            context.start()

            withTemplate {
                to('direct:route').withBody('a').withHeader('id', '1').send()
                to('direct:route').withBody('b').withHeader('id', '2').send()
                to('direct:route').withBody('a2').withHeader('id', '1').send()
                to('direct:route').withBody('b2').withHeader('id', '2').send()
                to('direct:route').withBody('c').withHeader('id', '3').send()
            }
        then:
            MockEndpoint.assertIsSatisfied(context)
    }

    def 'Error: kebab-case: idempotent-consumer'() {
        when:
        var route = '''
                - beans:
                  - name: myRepo
                    type: org.apache.camel.support.processor.idempotent.MemoryIdempotentRepository
                - from:
                    uri: "direct:route"
                    steps:
                      - idempotent-consumer:
                          simple: "${header.id}"
                          idempotentRepository: "myRepo"
                          steps:
                            - to: "mock:idempotent"
                      - to: "mock:route"
            '''
        then:
        try {
            loadRoutes route
            Assertions.fail('Should have thrown exception')
        } catch (e) {
            assert e.message.contains('additional properties')
        }
    }

    def 'Error: kebab-case: idempotent-repository'() {
        when:
        var route = '''
                - beans:
                  - name: myRepo
                    type: org.apache.camel.support.processor.idempotent.MemoryIdempotentRepository
                - from:
                    uri: "direct:route"
                    steps:
                      - idempotentConsumer:
                          simple: "${header.id}"
                          idempotent-repository: "myRepo"
                          steps:
                            - to: "mock:idempotent"
                      - to: "mock:route"
            '''
        then:
        try {
            loadRoutes route
            Assertions.fail('Should have thrown exception')
        } catch (e) {
            assert e.message.contains('additional properties')
        }
    }
}
