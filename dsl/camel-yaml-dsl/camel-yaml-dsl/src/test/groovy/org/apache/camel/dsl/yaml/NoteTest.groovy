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
import org.apache.camel.model.RouteDefinition
import org.apache.camel.model.ToDefinition
import org.apache.camel.spi.Resource
import org.apache.camel.support.PluginHelper

class NoteTest extends YamlTestSupport {

    def "note definition (#resource.location)"(Resource resource) {
        when:
            PluginHelper.getRoutesLoader(context).loadRoutes(resource)
        then:
            with(context.routeDefinitions[0].outputs[0], ToDefinition) {
                endpointUri == 'direct:start'
                description == 'some description here'
                note == 'some note here'
            }
            with(context.routeDefinitions[0], RouteDefinition) {
                if (it.id == 'cheese') {
                    description == 'some route description here'
                    note == 'some route note here'
                } else {
                    description == null
                    note == null
                }
            }
        where:
            resource << [
                asResource('route', '''
                    - route:
                        id: cheese
                        note: "some route note here"
                        description: "some route description here"
                        from:
                          uri: "direct:start"
                          steps:
                            - to: 
                                uri: "direct:start"
                                note: "some note here"
                                description: "some description here"
                    '''),
                asResource('uri', '''
                    - from:
                        uri: "direct:start"
                        steps:    
                          - to: 
                              uri: "direct:start"
                              note: "some note here"
                              description: "some description here"
                    '''),
                asResource('properties', '''
                    - from:
                        uri: "direct:start"
                        steps:    
                          - to: 
                              uri: "direct"
                              note: "some note here"
                              description: "some description here"
                              parameters:
                                name: "start"
                    '''),
                asResource('properties-out-of-order', '''
                    - from:
                        uri: "direct:start"
                        steps:    
                          - to: 
                              parameters:
                                name: "start"
                              note: "some note here"
                              description: "some description here"
                              uri: "direct"
                    '''),
            ]
    }
}
