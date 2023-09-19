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
import org.apache.camel.dsl.yaml.support.model.MyUppercaseProcessor
import org.junit.jupiter.api.Assertions

class BeanTest extends YamlTestSupport {

    def "bean"() {
        setup:
            loadRoutes """
                - from:
                   uri: "direct:route"
                   steps:
                     - bean:
                         beanType: ${MyUppercaseProcessor.name}
                     - to: "mock:route"
            """

            withMock('mock:route') {
                expectedBodiesReceived 'TEST'
            }

        when:
            context.start()

            withTemplate {
                to('direct:route').withBody('test').send()
            }
        then:
            MockEndpoint.assertIsSatisfied(context)
    }

    def "Error: kebab-case: bean-type"() {
        when:
        var route = """
                - from:
                   uri: "direct:route"
                   steps:
                     - bean:
                         bean-type: ${MyUppercaseProcessor.name}
                     - to: "mock:route"
            """
        then:
        try {
            loadRoutes(route)
            Assertions.fail("Should have thrown exception")
        } catch (e) {
            assert e.message.contains("additional properties")
        }
    }
 }
