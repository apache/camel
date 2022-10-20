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
import org.apache.camel.model.ChoiceDefinition
import org.apache.camel.model.ToDefinition
import org.apache.camel.model.WhenDefinition

class ChoiceCamelExtTest extends YamlTestSupport {

    def "choice definition"() {
        when:
            loadRoutesExt("camel.yaml", '''
                - from:
                    uri: "direct:start"
                    steps:    
                      - choice:  
                          when:
                            - simple: "${body.size()} == 1"
                              steps:
                                - to: "log:when-a"
                            - expression:
                                simple: "${body.size()} == 2"
                              steps:
                                - to: "log:when-b"
                          otherwise:
                            steps:
                              - to: "log:otherwise"
            ''')
        then:
            context.routeDefinitions.size() == 1

            with(context.routeDefinitions[0].outputs[0], ChoiceDefinition) {
                with(whenClauses[0], WhenDefinition) {
                    expression.language == 'simple'
                    expression.expression == '${body.size()} == 1'
                    with(outputs[0], ToDefinition) {
                        endpointUri == 'log:when-a'
                    }
                }
                with(whenClauses[1], WhenDefinition) {
                    expression.language == 'simple'
                    expression.expression == '${body.size()} == 2'
                    with(outputs[0], ToDefinition) {
                        endpointUri == 'log:when-b'
                    }
                }
                with(otherwise.outputs[0], ToDefinition) {
                    endpointUri == 'log:otherwise'
                }
            }
    }

    def "choice in precondition mode"() {
        when:
        loadRoutesExt("camel.yaml", '''
                - from:
                    uri: "direct:start"
                    steps:    
                      - choice:
                          precondition: true
                          when:
                            - simple: "{{?red}}"
                              steps:
                                - to: "mock:red"
                            - simple: "{{?blue}}"
                              steps:
                                - to: "mock:blue"
                          otherwise:
                            steps:
                              - to: "mock:other"    
            ''')
        then:
        context.routeDefinitions.size() == 1

        with(context.routeDefinitions[0].outputs[0], ChoiceDefinition) {
            with(whenClauses[0], WhenDefinition) {
                expression.language == 'simple'
                expression.expression == '{{?red}}'
                with(outputs[0], ToDefinition) {
                    endpointUri == 'mock:red'
                }
            }
            with(whenClauses[1], WhenDefinition) {
                expression.language == 'simple'
                expression.expression == '{{?blue}}'
                with(outputs[0], ToDefinition) {
                    endpointUri == 'mock:blue'
                }
            }
            with(otherwise.outputs[0], ToDefinition) {
                endpointUri == 'mock:other'
            }
        }
    }
}
