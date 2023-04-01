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
import org.apache.camel.model.UnmarshalDefinition
import org.apache.camel.spi.Resource
import org.apache.camel.support.PluginHelper

class UnmarshalTest extends YamlTestSupport {

    def "unmarshal definition (#resource.location, #expected)"(Resource resource, String expected) {
        when:
            PluginHelper.getRoutesLoader(context).loadRoutes(resource)
        then:
            with(context.routeDefinitions[0].outputs[0], UnmarshalDefinition) {
                with(dataFormatType) {
                    dataFormatName == expected
                }
            }
        where:
            resource << [
                asResource('data-format', '''
                    - from:
                        uri: "direct:start"
                        steps:    
                          - unmarshal:
                             json: 
                               library: Gson
                          - to: "mock:result"
                    '''),
                asResource('data-format-block', '''
                    - from:
                        uri: "direct:start"
                        steps:    
                          - unmarshal:
                             data-format-type:
                               json: 
                                 library: Gson
                          - to: "mock:result"
                    '''),
                asResource('data-format', '''
                    - from:
                        uri: "direct:start"
                        steps:    
                          - unmarshal:
                             json: {}
                          - to: "mock:result"
                    '''),
                asResource('data-format-block', '''
                    - from:
                        uri: "direct:start"
                        steps:    
                          - unmarshal:
                             data-format-type:
                               json: {}
                          - to: "mock:result"
                    ''')
            ]

            expected << [
                'gson', 'gson', 'jackson', 'jackson'
            ]
    }

    def "unmarshal definition with allow null body (#resource.location, #expected)"(Resource resource, String expected) {
        when:
            PluginHelper.getRoutesLoader(context).loadRoutes(resource)
        then:
            with(context.routeDefinitions[0].outputs[0], UnmarshalDefinition) {
                allowNullBody == expected
            }
        where:
            resource << [
                asResource('allow-null-body-set-to-true', '''
                    - from:
                        uri: "direct:start"
                        steps:
                          - unmarshal:
                             allow-null-body: true
                             json:
                               library: Gson
                          - to: "mock:result"
                    '''),
                asResource('allow-null-body-set-to-false', '''
                    - from:
                        uri: "direct:start"
                        steps:
                          - unmarshal:
                             allow-null-body: false
                             json:
                               library: Gson
                          - to: "mock:result"
                    '''),
                asResource('allow-null-body-not-set', '''
               - from:
                        uri: "direct:start"
                        steps:
                          - unmarshal:
                             json:
                               library: Gson
                          - to: "mock:result"
                    ''')
            ]
        expected << [
                'true', 'false', null
        ]
    }
}
