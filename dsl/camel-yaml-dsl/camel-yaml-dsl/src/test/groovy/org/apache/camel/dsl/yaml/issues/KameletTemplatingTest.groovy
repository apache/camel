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
package org.apache.camel.dsl.yaml.issues

import org.apache.camel.component.mock.MockEndpoint
import org.apache.camel.dsl.yaml.support.YamlTestSupport

class KameletTemplatingTest extends YamlTestSupport {
    @Override
    def doSetup() {
        context.start()
    }

    def "kamelet (mustache)"() {
        setup:
            def template = Base64.encoder.encodeToString('''
                Dear {{headers.lastName}}, {{headers.firstName}}                            
                Thanks for the order of {{headers.item}}.                            
                Regards Camel Riders Bookstore
                {{body}}
            '''.stripIndent().bytes)

            loadKamelets """
                apiVersion: camel.apache.org/v1
                kind: Kamelet
                metadata:
                  name: mustache-template-action
                  labels:
                    camel.apache.org/kamelet.type: "action"
                spec:
                  definition:
                    title: "Mustache Template Action"
                    required:
                      - template
                    type: object
                    properties:
                      template:
                        title: Template
                        description: The inline template
                        type: binary
                  template:
                    from:
                      uri: "kamelet:source"
                      steps:
                      - to:
                          uri: "mustache:"
                          parameters:
                            resourceUri: "base64:{{template}}"
            """

            loadRoutes """
                - from:
                    uri: 'direct:start'
                    steps:    
                      - kamelet:
                          name: 'mustache-template-action'
                          parameters:
                            template: ${template}
                      - to: 'mock:result'  
            """

            withMock('mock:result') {
                expectedBodiesReceived '''
                    Dear Cosentino, Andrea                            
                    Thanks for the order of Camel in Action.                            
                    Regards Camel Riders Bookstore
                    the Camel team
                '''.stripIndent()

            }
        when:
            withTemplate {
                to('direct:start')
                    .withHeader('lastName', 'Cosentino')
                    .withHeader('firstName', 'Andrea')
                    .withHeader('item', 'Camel in Action')
                    .withBody("the Camel team").send()
            }
        then:
            MockEndpoint.assertIsSatisfied(context)
    }
}
