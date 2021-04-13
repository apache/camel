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
import org.apache.camel.model.ToDynamicDefinition
import org.apache.camel.spi.Resource

class ToDynamicTest extends YamlTestSupport {

    def "to-d definition (#resource.location)"(Resource resource) {
        when:
            context.routesLoader.loadRoutes(resource)
        then:
            with(context.routeDefinitions[0].outputs[0], ToDynamicDefinition) {
                uri == 'direct:start'
            }
        where:
            resource << [
                asResource('inline', '''
                    - from:
                        uri: "direct:start"
                        steps:    
                          - to-d: "direct:start"
                    '''),
                asResource('uri', '''
                    - from:
                        uri: "direct:start"
                        steps:    
                          - to-d: 
                              uri: "direct:start"
                    '''),
                asResource('properties', '''
                    - from:
                        uri: "direct:start"
                        steps:    
                          - to-d: 
                              uri: "direct"
                              parameters:
                                name: "start"
                    '''),
                asResource('endpoint', '''
                    - from:
                        uri: "direct:start"
                        steps:    
                          - to-d: 
                              direct:
                                name: "start"
                    '''),
                asResource('properties-out-of-order', '''
                    - from:
                        uri: "direct:start"
                        steps:    
                          - to-d: 
                              parameters:
                                name: "start"
                              uri: "direct"
                    '''),
            ]
    }

    def "to definition (#resource.location)"(Resource resource) {
        when:
            context.routesLoader.loadRoutes(resource)
        then:
            with(context.routeDefinitions[0].outputs[0], ToDynamicDefinition) {
                uri == 'direct:start'
            }
        where:
            resource << [
                    asResource('inline', '''
                    - from:
                        uri: "direct:start"
                        steps:    
                          - tod: "direct:start"
                    '''),
                    asResource('uri', '''
                    - from:
                        uri: "direct:start"
                        steps:    
                          - tod: 
                              uri: "direct:start"
                    '''),
                    asResource('properties', '''
                    - from:
                        uri: "direct:start"
                        steps:    
                          - tod: 
                              uri: "direct"
                              parameters:
                                name: "start"
                    '''),
                    asResource('endpoint', '''
                    - from:
                        uri: "direct:start"
                        steps:    
                          - tod: 
                              direct:
                                name: "start"
                    '''),
                    asResource('properties-out-of-order', '''
                    - from:
                        uri: "direct:start"
                        steps:    
                          - tod: 
                              parameters:
                                name: "start"
                              uri: "direct"
                    '''),
            ]
    }
}
