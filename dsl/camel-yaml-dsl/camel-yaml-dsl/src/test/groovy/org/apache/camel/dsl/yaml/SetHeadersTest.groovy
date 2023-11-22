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
import org.apache.camel.model.SetHeadersDefinition
import org.apache.camel.model.SetHeaderDefinition
import org.apache.camel.model.language.ExpressionDefinition
import org.apache.camel.model.language.SimpleExpression
import org.apache.camel.spi.Resource
import org.apache.camel.support.PluginHelper
import org.junit.jupiter.api.Assertions

class SetHeadersTest extends YamlTestSupport {

    def "setHeaders definition"() {
        when:
        loadRoutes '''
                    - from:
                        uri: "direct:start"
                        steps:    
                          - setHeaders:
                              headers:
                                - name: testbody
                                  simple: "${body}"
                                - name: testconstant
                                  constant: ABC
                          - to: "mock:result"
                    '''
        then:
        with(context.routeDefinitions[0].outputs[0], SetHeadersDefinition) {
            with(it.headers[0], SetHeaderDefinition) {
                name == 'testbody'
                with(expression, ExpressionDefinition) {
                    language == 'simple'
                    expression == '${body}'
                }
            }
            with(it.headers[1], SetHeaderDefinition) {
                name == 'testconstant'
                with(expression, ExpressionDefinition) {
                    language == 'constant'
                    expression == "ABC"
                }
            }
        }
    }
    
        def "setHeaders resultType"() {
        when:
        loadRoutes '''
                - from:
                    uri: "direct:start"
                    steps:    
                      - setHeaders:
                          headers:
                            - name: foo
                              simple: "${body}"
                            - name: bar
                              simple:
                                expression: "${header.foo} > 10"
                                resultType: "boolean"      
                      - to: "mock:result"
            '''
        then:
        with(context.routeDefinitions[0].outputs[0], SetHeadersDefinition) {
            with(it.headers[1], SetHeaderDefinition) {
                name == 'bar'
                with(expression, ExpressionDefinition) {
                    language == 'simple'
                    expression == '${header.foo} > 10'
                    resultTypeName == 'boolean'
                }
            }
        }
    }

}
