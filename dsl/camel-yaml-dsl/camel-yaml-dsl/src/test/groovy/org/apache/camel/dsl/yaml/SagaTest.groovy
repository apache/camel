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
import org.apache.camel.model.SagaActionUriDefinition
import org.apache.camel.model.SagaDefinition
import org.apache.camel.model.SagaOptionDefinition
import org.apache.camel.model.ToDefinition
import org.apache.camel.model.language.ExpressionDefinition
import org.apache.camel.spi.Resource

class SagaTest extends YamlTestSupport {

    def "saga (#resource.location)"(Resource resource) {
        when:
            context.routesLoader.loadRoutes(resource)
        then:
            with(context.routeDefinitions[0].outputs[0], SagaDefinition) {
                propagation == "MANDATORY"
                completionMode == "MANUAL"

                with(compensation, SagaActionUriDefinition) {
                    uri == "direct:compensation"
                }
                with(completion, SagaActionUriDefinition) {
                    uri == "direct:completion"
                }
                with(outputs[0], ToDefinition) {
                    endpointUri == 'direct:something'
                }
                with(options[0], SagaOptionDefinition) {
                    optionName == 'o1'
                    with(expression, ExpressionDefinition) {
                        language == 'simple'
                        expression == '${body}'
                    }
                }
                with(options[1], SagaOptionDefinition) {
                    optionName == 'o2'
                    with(expression, ExpressionDefinition) {
                        language == 'simple'
                        expression == '${body}'
                    }
                }
            }
        where:
            resource << [
                asResource('full', '''
                    - from:
                        uri: "direct:start"
                        steps:    
                          - saga:  
                             propagation: "MANDATORY"
                             completion-mode: "MANUAL"
                             compensation: 
                                 uri: "direct:compensation"
                             completion:
                                 uri: "direct:completion"
                             steps:
                               - to: "direct:something"
                             option:
                               - option-name: o1
                                 simple: "${body}" 
                               - option-name: o2
                                 expression:
                                   simple: "${body}"        
                          - to: "mock:result"
                    '''),
                asResource('short', '''
                    - from:
                        uri: "direct:start"
                        steps:    
                          - saga: 
                             propagation: "MANDATORY"
                             completion-mode: "MANUAL"
                             compensation: "direct:compensation"
                             completion: "direct:completion"
                             steps:
                               - to: "direct:something"    
                             option:
                               - option-name: o1
                                 simple: "${body}" 
                               - option-name: o2
                                 expression:
                                   simple: "${body}"        
                          - to: "mock:result"
                    '''),
                asResource('endpoint-dsl', '''
                    - from:
                        uri: "direct:start"
                        steps:    
                          - saga: 
                             propagation: "MANDATORY"
                             completion-mode: "MANUAL"
                             compensation: 
                               direct:
                                 name: "compensation"
                             completion:
                               direct:
                                 name: "completion"
                             steps:
                               - to: "direct:something"  
                             option:
                               - option-name: o1
                                 simple: "${body}" 
                               - option-name: o2
                                 expression:
                                   simple: "${body}"          
                          - to: "mock:result"
                    ''')
           ]
    }
}
