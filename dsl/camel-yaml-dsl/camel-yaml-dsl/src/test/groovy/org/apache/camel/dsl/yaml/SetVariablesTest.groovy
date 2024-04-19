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
import org.apache.camel.model.SetVariableDefinition
import org.apache.camel.model.SetVariablesDefinition
import org.apache.camel.model.language.ExpressionDefinition

class SetVariablesTest extends YamlTestSupport {

    def "setVariables definition"() {
        when:
        loadRoutes '''
                    - from:
                        uri: "direct:start"
                        steps:    
                          - setVariables:
                              variables:
                                - name: testbody
                                  simple: "${body}"
                                - name: testconstant
                                  constant: ABC
                          - to: "mock:result"
                    '''
        then:
        with(context.routeDefinitions[0].outputs[0], SetVariablesDefinition) {
            with(it.variables[0], SetVariableDefinition) {
                name == 'testbody'
                with(expression, ExpressionDefinition) {
                    language == 'simple'
                    expression == '${body}'
                }
            }
            with(it.variables[1], SetVariableDefinition) {
                name == 'testconstant'
                with(expression, ExpressionDefinition) {
                    language == 'constant'
                    expression == "ABC"
                }
            }
        }
    }
    
        def "setVariables resultType"() {
        when:
        loadRoutes '''
                - from:
                    uri: "direct:start"
                    steps:    
                      - setVariables:
                          variables:
                            - name: foo
                              simple: "${body}"
                            - name: bar
                              simple:
                                expression: "${variable.foo} > 10"
                                resultType: "boolean"      
                      - to: "mock:result"
            '''
        then:
        with(context.routeDefinitions[0].outputs[0], SetVariablesDefinition) {
            with(it.variables[1], SetVariableDefinition) {
                name == 'bar'
                with(expression, ExpressionDefinition) {
                    language == 'simple'
                    expression == '${variable.foo} > 10'
                    resultTypeName == 'boolean'
                }
            }
        }
    }

}
