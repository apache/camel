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
import org.apache.camel.model.ChoiceDefinition
import org.apache.camel.model.ToDefinition
import org.apache.camel.model.WhenDefinition
import org.junit.jupiter.api.Assertions

class ExpressionTest extends YamlTestSupport {

    def "Error: duplicate inline expressions"() {
        when:
        var route = '''
                - from:
                    uri: "direct:start"
                    steps:    
                      - choice:  
                          when:
                            - simple: "${body.size()} == 1"
                              jq: ".size == 1"
                              steps:
                                - to: "log:when-a"
            '''
        then:
        try {
            loadRoutes(route);
            Assertions.fail("Should have thrown exception")
        } catch (IllegalArgumentException e) {
            Assertions.assertTrue(e.getMessage().contains("2 are valid"), e.getMessage());
        }
    }

    def "Error: duplicate explicit expressions"() {
        when:
        var route = '''
                - from:
                    uri: "direct:start"
                    steps:    
                      - choice:  
                          when:
                            - expression:
                                simple: "${body.size()} == 1"
                                jq: ".size == 1"
                              steps:
                                - to: "log:when-a"
            '''
        then:
        try {
            loadRoutes(route);
            Assertions.fail("Should have thrown exception")
        } catch (IllegalArgumentException e) {
            Assertions.assertTrue(e.getMessage().contains("2 are valid"), e.getMessage());
        }
    }

    def "Error: inline and explicit expressions"() {
        when:
        var route = '''
                - from:
                    uri: "direct:start"
                    steps:    
                      - choice:  
                          when:
                            - simple: "${body.size()} == 1"
                              expression:
                                jq: ".size == 1"
                              steps:
                                - to: "log:when-a"
            '''
        then:
        try {
            loadRoutes(route);
            Assertions.fail("Should have thrown exception")
        } catch (IllegalArgumentException e) {
            Assertions.assertTrue(e.getMessage().contains("2 are valid"), e.getMessage());
        }
    }

    def "Error: inline not existing"() {
        when:
        var route = '''
                - from:
                    uri: "direct:start"
                    steps:    
                      - choice:  
                          when:
                            - notsimple: "${body.size()} == 1"
                              steps:
                                - to: "log:when-a"
            '''
        then:
        try {
            loadRoutes(route);
            Assertions.fail("Should have thrown exception")
        } catch (Exception e) {
            Assertions.assertTrue(e.getMessage().contains("Unsupported field: notsimple"), e.getMessage());
        }
    }

    def "Error: explicit not existing"() {
        when:
        var route = '''
                - from:
                    uri: "direct:start"
                    steps:    
                      - choice:  
                          when:
                            - expression:
                                notsimple: "${body.size()} == 1"
                              steps:
                                - to: "log:when-a"
            '''
        then:
        try {
            loadRoutes(route);
            Assertions.fail("Should have thrown exception")
        } catch (Exception e) {
            Assertions.assertTrue(e.getMessage().contains("0 are valid"), e.getMessage());
        }
    }

    def "no expression"() {
        when:
        var route = '''
                - from:
                    uri: "direct:start"
                    steps:    
                      - choice:  
                          when:
                            - steps:
                                - to: "log:when-a"
            '''
        loadRoutes(route)
        then:
        context.routeDefinitions.size() == 1
    }

}
