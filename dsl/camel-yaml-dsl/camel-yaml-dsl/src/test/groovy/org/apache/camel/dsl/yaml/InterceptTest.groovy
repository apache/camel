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

class InterceptTest extends YamlTestSupport {
    def "intercept"() {
        setup:
            loadRoutes """
                - intercept:
                    steps:
                      - to: "mock:intercepted"  
                - from:
                    uri: "direct:start"
                    steps:
                      - to: "mock:foo"
                      - setBody:
                          constant: "Hello Bar"
                      - to: "mock:bar"
                      - to: "mock:result"
            """

            withMock('mock:intercepted') {
                expectedBodiesReceived("hello", "hello", "Hello Bar", "Hello Bar")
            }

        when:
            context.start()

            withTemplate {
                to('direct:start').withBody('hello').send()
            }
        then:
            MockEndpoint.assertIsSatisfied(context)
    }
}
