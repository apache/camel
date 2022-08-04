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
import org.apache.camel.model.language.XPathExpression

class XPathNamespaceTest extends YamlTestSupport {

    def "xpath namespace"() {
        when:
            loadRoutes '''
                - from:
                    uri: "direct:start"
                    steps:    
                      - choice:  
                          when:
                            - expression:
                                xpath:
                                  expression: "/c:number = 55"
                                  namespace:
                                    - key: 'c'
                                      value: 'http://acme.com/cheese'
                                    - key: 'w'
                                      value: 'http://acme.com/wine'
                              steps:
                                - to: "mock:55"
                          otherwise:
                            steps:
                              - to: "mock:other"
            '''
        then:
            context.routeDefinitions.size() == 1

            with(context.routeDefinitions[0].outputs[0], ChoiceDefinition) {
                with(whenClauses[0], WhenDefinition) {
                    var x = expression as XPathExpression
                    x.expression == '/c:number = 55'
                    x.namespace.size() == 2
                    x.namespace[0].key == 'c'
                    x.namespace[0].value == 'http://acme.com/cheese'
                    x.namespace[1].key == 'w'
                    x.namespace[1].value == 'http://acme.com/wine'
                    with(outputs[0], ToDefinition) {
                        endpointUri == 'mock:55'
                    }
                }
                with(otherwise.outputs[0], ToDefinition) {
                    endpointUri == 'mock:other'
                }
            }
    }

}
