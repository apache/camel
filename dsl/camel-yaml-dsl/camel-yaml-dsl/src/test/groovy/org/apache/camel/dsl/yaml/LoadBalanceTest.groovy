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

class LoadBalanceTest extends YamlTestSupport {

    def "loadBalance"() {
        setup:
            loadRoutes '''
                - from:
                   uri: "direct:start"
                   steps:
                     - loadBalance:
                         weighted:
                           distributionRatio: "2,1"
                           roundRobin: false
                         steps:
                           - to: "mock:x"
                           - to: "mock:y"
            '''

            withMock('mock:x') {
                expectedMessageCount = 2
            }
            withMock('mock:y') {
                expectedMessageCount = 1
            }

        when:
            context.start()

            withTemplate {
                to('direct:start').withBody('hello').send()
                to('direct:start').withBody('hello').send()
                to('direct:start').withBody('hello').send()
            }
        then:
            MockEndpoint.assertIsSatisfied(context)
    }

    def "Error: kebab-case: load-balance"() {
        when:
        var route = '''
                - from:
                   uri: "direct:start"
                   steps:
                     - load-balance:
                         weighted:
                           distributionRatio: "2,1"
                           roundRobin: false
                         steps:
                           - to: "mock:x"
                           - to: "mock:y"
            '''
        then:
        try {
            loadRoutes route
            Assertions.fail("Should have thrown exception")
        } catch (e) {
            assert e.message.contains("additional properties")
        }
    }

    def "Error: kebab-case: distribution-ratio"() {
        when:
        var route = '''
                - from:
                   uri: "direct:start"
                   steps:
                     - loadBalance:
                         weighted:
                           distribution-ratio: "2,1"
                           roundRobin: false
                         steps:
                           - to: "mock:x"
                           - to: "mock:y"
            '''
        then:
        try {
            loadRoutes route
            Assertions.fail("Should have thrown exception")
        } catch (e) {
            assert e.message.contains("additional properties")
        }
    }
}
