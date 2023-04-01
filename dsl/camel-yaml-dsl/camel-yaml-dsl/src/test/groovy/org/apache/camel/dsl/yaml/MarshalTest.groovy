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
import org.apache.camel.model.MarshalDefinition
import org.apache.camel.spi.Resource
import org.apache.camel.support.PluginHelper

class MarshalTest extends YamlTestSupport {

    def "marshal definition (#resource.location, #expected)"(Resource resource, String expected) {
        when:
            PluginHelper.getRoutesLoader(context).loadRoutes(resource)
        then:
            with(context.routeDefinitions[0].outputs[0], MarshalDefinition) {
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
                          - marshal:
                             json: 
                               library: Gson
                          - to: "mock:result"
                    '''),
                asResource('data-format-block', '''
                    - from:
                        uri: "direct:start"
                        steps:    
                          - marshal:
                             data-format-type:
                               json: 
                                 library: Gson
                          - to: "mock:result"
                    '''),
                asResource('data-format', '''
                    - from:
                        uri: "direct:start"
                        steps:    
                          - marshal:
                             json: {}
                          - to: "mock:result"
                    '''),
                asResource('data-format-block', '''
                    - from:
                        uri: "direct:start"
                        steps:    
                          - marshal:
                             data-format-type:
                               json: {}
                          - to: "mock:result"
                    '''),
                asResource('data-format-library-case', '''
                    - from:
                        uri: "direct:start"
                        steps:    
                          - marshal:
                             json: 
                               library: gson
                          - to: "mock:result"
                    ''')
            ]

            expected << [
                'gson', 'gson', 'jackson', 'jackson', 'gson'
            ]
    }
}
