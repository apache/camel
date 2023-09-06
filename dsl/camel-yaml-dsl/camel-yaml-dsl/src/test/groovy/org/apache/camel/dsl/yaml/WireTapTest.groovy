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
import org.apache.camel.model.WireTapDefinition
import org.junit.jupiter.api.Assertions

class WireTapTest extends YamlTestSupport {

    def "wireTap definition (#resource.location)"() {
        when:
            loadRoutes '''
                - from:
                    uri: "direct:start"
                    steps:    
                      - wireTap:
                         uri: "direct:wt"
            '''
        then:
            with(context.routeDefinitions[0].outputs[0], WireTapDefinition) {
                uri == "direct:wt"
            }
    }

    def "wireTap uri parameters (#resource.location)"() {
        when:
            loadRoutes '''
                - from:
                    uri: "direct:start"
                    steps:
                      - wireTap:
                         uri: direct
                         parameters:
                           name: wt2  
            '''
        then:
            with(context.routeDefinitions[0].outputs[0], WireTapDefinition) {
                uri == "direct:wt2"
            }
    }

    def "Error: kebab-case"() {
        when:
        var route = '''
                - from:
                    uri: "direct:start"
                    steps:
                      - wire-tap:
                         uri: direct
                         parameters:
                           name: wt2  
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
