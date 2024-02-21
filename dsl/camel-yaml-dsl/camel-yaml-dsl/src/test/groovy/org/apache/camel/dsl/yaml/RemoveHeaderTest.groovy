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
import org.apache.camel.model.RemoveHeaderDefinition
import org.apache.camel.model.RemoveHeadersDefinition
import org.apache.camel.spi.Resource
import org.apache.camel.support.PluginHelper
import org.junit.jupiter.api.Assertions

class RemoveHeaderTest extends YamlTestSupport {

    def "removeHeader definition (#resource.location)"(Resource resource) {
        when:
            PluginHelper.getRoutesLoader(context).loadRoutes(resource)
        then:
            with(context.routeDefinitions[0].outputs[0], RemoveHeaderDefinition) {
                name == 'test'
            }
        where:
            resource << [
                asResource('expression', '''
                    - from:
                        uri: "direct:start"
                        steps:    
                          - removeHeader:
                              name: test
                          - to: "mock:result"
                    '''),
                asResource('expression-block', '''
                    - from:
                        uri: "direct:start"
                        steps:    
                          - removeHeader:
                              name: test
                          - to: "mock:result"
                    ''')
            ]
    }

    def "removeHeaders definition"() {
        when:
            loadRoutes'''
                - from:
                    uri: "direct:start"
                    steps:    
                      - removeHeaders:
                          pattern: toRemove
                          excludePattern: toExclude
                      - to: "mock:result"
            '''
        then:
            with(context.routeDefinitions[0].outputs[0], RemoveHeadersDefinition) {
                pattern == 'toRemove'
                excludePattern == 'toExclude'
            }
    }

    def "Error: kebab-case: remove-headers definition"() {
        when:
        var route = '''
                - from:
                    uri: "direct:start"
                    steps:    
                      - remove-headers:
                          pattern: toRemove
                          excludePattern: toExclude
                      - to: "mock:result"
            '''
        then:
        try {
            loadRoutes(route)
            Assertions.fail("Should have thrown exception")
        } catch (e) {
            with(e) {
                message.contains("additional properties")
            }
        }
    }
}
