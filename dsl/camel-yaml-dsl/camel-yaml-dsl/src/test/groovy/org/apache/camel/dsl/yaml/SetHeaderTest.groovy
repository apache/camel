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
import org.apache.camel.model.SetHeaderDefinition
import org.apache.camel.model.language.ExpressionDefinition
import org.apache.camel.model.language.SimpleExpression
import org.apache.camel.spi.Resource
import org.apache.camel.support.PluginHelper
import org.junit.jupiter.api.Assertions

class SetHeaderTest extends YamlTestSupport {

    def "setHeader definition (#resource.location)"(Resource resource) {
        when:
            PluginHelper.getRoutesLoader(context).loadRoutes(resource)
        then:
        with(context.routeDefinitions[0].outputs[0], SetHeaderDefinition) {
            name == 'test'

            with(expression, ExpressionDefinition) {
                language == 'simple'
                expression == '${body}'
            }
        }
        where:
        resource << [
                asResource('expression', '''
                    - from:
                        uri: "direct:start"
                        steps:    
                          - setHeader:
                              name: test
                              simple: "${body}"
                          - to: "mock:result"
                    '''),
                asResource('expression-block', '''
                    - from:
                        uri: "direct:start"
                        steps:    
                          - setHeader:
                              name: test
                              expression:
                                simple: "${body}"
                          - to: "mock:result"
                    ''')
        ]
    }

    def "setHeader resultType"() {
        when:
        loadRoutes '''
                - from:
                    uri: "direct:start"
                    steps:    
                      - setHeader:
                          name: test
                          simple:
                            expression: "${body}"
                            resultType: "long" 
                      - to: "mock:result"
            '''
        then:
        with(context.routeDefinitions[0].outputs[0], SetHeaderDefinition) {
            name == 'test'

            with(expression, SimpleExpression) {
                language == 'simple'
                expression == '${body}'
                resultTypeName == 'long'
            }
        }
    }

    def "Error: kebab-case: set-header"() {
        when:
        var route = '''
                - from:
                    uri: "direct:start"
                    steps:    
                      - set-header:
                          name: test
                          simple: "${body}"
                      - to: "mock:result"
            '''
        then:
        try {
            loadRoutes(route)
            Assertions.fail("Should have thrown exception")
        } catch (Exception e) {
            Assertions.assertTrue(e.message.contains("additional properties"), e.getMessage())
        }
    }

    def "Error: kebab-case: result-type"() {
        when:
        var route = '''
                - from:
                    uri: "direct:start"
                    steps:    
                      - setHeader:
                          name: test
                          simple:
                            expression: "${body}"
                            result-type: "long" 
                      - to: "mock:result"
            '''
        then:
        try {
            loadRoutes(route)
            Assertions.fail("Should have thrown exception")
        } catch (Exception e) {
            Assertions.assertTrue(e.message.contains("additional properties"), e.getMessage())
        }
    }
}
