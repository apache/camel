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
import org.apache.camel.model.ToDefinition
import org.apache.camel.spi.Resource
import org.apache.camel.support.PluginHelper

class SagaTest extends YamlTestSupport {

    def "saga (#resource.location)"(Resource resource) {
        when:
            PluginHelper.getRoutesLoader(context).loadRoutes(resource)
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
                // saga spans the entire route so steps are inserted before any saga specific step
                // https://issues.apache.org/jira/browse/CAMEL-17129
                with(outputs[0], ToDefinition) {
                    endpointUri == 'mock:result'
                }
                with(outputs[1], ToDefinition) {
                    endpointUri == 'direct:something'
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
                             completionMode: "MANUAL"
                             compensation: 
                                 uri: "direct:compensation"
                             completion:
                                 uri: "direct:completion"
                             steps:
                               - to: "direct:something"
                             option:
                               - key: o1
                                 simple: "${body}" 
                               - key: o2
                                 expression:
                                   simple: "${body}"        
                          - to: "mock:result"
                    '''),
                asResource('full-parameters', '''
                    - from:
                        uri: "direct:start"
                        steps:    
                          - saga:  
                             propagation: "MANDATORY"
                             completionMode: "MANUAL"
                             compensation: 
                                 uri: "direct"
                                 parameters:
                                   name: compensation
                             completion:
                                 uri: "direct:completion"
                             steps:
                               - to: "direct:something"
                             option:
                               - key: o1
                                 simple: "${body}" 
                               - key: o2
                                 expression:
                                   simple: "${body}"        
                          - to: "mock:result"
                    '''),
                asResource('full-parameters-out-of-order)', '''
                    - from:
                        uri: "direct:start"
                        steps:    
                          - saga:  
                             propagation: "MANDATORY"
                             completionMode: "MANUAL"
                             compensation: 
                                 parameters:
                                   name: compensation
                                 uri: "direct"
                             completion:
                                 uri: "direct:completion"
                             steps:
                               - to: "direct:something"
                             option:
                               - key: o1
                                 simple: "${body}" 
                               - key: o2
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
                             completionMode: "MANUAL"
                             compensation: "direct:compensation"
                             completion: "direct:completion"
                             steps:
                               - to: "direct:something"    
                             option:
                               - key: o1
                                 simple: "${body}" 
                               - key: o2
                                 expression:
                                   simple: "${body}"        
                          - to: "mock:result"
                    ''')
           ]
    }
}
