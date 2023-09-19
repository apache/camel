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
import org.apache.camel.model.ClaimCheckDefinition
import org.junit.jupiter.api.Assertions

class ClaimCheckTest extends YamlTestSupport {

    def "claimCheck definition"() {
        when:
            loadRoutes '''
                - from:
                    uri: "direct:start"
                    steps:    
                      - claimCheck:  
                          operation: "Push"
                          key: "foo"
                          filter: "header:(foo|bar)"
            '''
        then:
            context.routeDefinitions.size() == 1

            with(context.routeDefinitions[0].outputs[0], ClaimCheckDefinition) {
                operation == 'Push'
                key == 'foo'
                filter == 'header:(foo|bar)'
            }
    }

    def "Error: kebab-case: claim-check definition"() {
        when:
        var route = '''
                - from:
                    uri: "direct:start"
                    steps:    
                      - claim-check:  
                          operation: "Push"
                          key: "foo"
                          filter: "header:(foo|bar)"
            '''
        then:
        try {
            loadRoutes(route)
            Assertions.fail("Should have thrown exception")
        } catch (e) {
            assert e.message.contains("additional properties")
        }
    }
}
