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

/**
 * CAMEL-24884: resolveResource: true on an expression loads a result that names a resource, so a script can choose
 * a file or a template per message and hand its content on.
 */
class ResolveResourceTest extends YamlTestSupport {

    def "a result that names a resource is loaded with resolveResource"() {
        setup:
            loadRoutes '''
                - route:
                    from:
                      uri: direct:start
                      steps:
                        - setBody:
                            expression:
                              simple:
                                expression: "${header.template}"
                                resolveResource: true
                        - to: mock:result
            '''
            withMock('mock:result') {
                expectedBodiesReceived '{"orderId": "ORD-TEMPLATE"}'
            }
        when:
            context.start()
            withTemplate {
                to('direct:start').withHeader('template', 'resource:resolve/order-template.json').withBody('x').send()
            }
        then:
            MockEndpoint.assertIsSatisfied(context)
    }
}
