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
import org.apache.camel.model.MulticastDefinition
import org.apache.camel.model.ToDefinition
import org.junit.jupiter.api.Assertions

class MulticastTest extends YamlTestSupport {

    def "multicast definition"() {
        when:
            loadRoutes '''
                - from:
                    uri: "direct:start"
                    steps:    
                      - multicast:  
                         stopOnException: true
                         parallelProcessing: true
                         steps:
                           - to: "direct:a"
                           - to: "direct:b"
                      - to: "direct:result" 
            '''
        then:
            context.routeDefinitions.size() == 1

            with(context.routeDefinitions[0].outputs[0], MulticastDefinition) {
                stopOnException == 'true'
                parallelProcessing == 'true'
                with(outputs[0], ToDefinition) {
                    endpointUri == 'direct:a'
                }
                with(outputs[1], ToDefinition) {
                    endpointUri == 'direct:b'
                }
            }
    }

    def "Error: kebab-case: stop-on-exception"() {
        when:
        var route = '''
                - from:
                    uri: "direct:start"
                    steps:    
                      - multicast:  
                         stop--on-exception: true
                         parallelProcessing: true
                         steps:
                           - to: "direct:a"
                           - to: "direct:b"
                      - to: "direct:result" 
            '''
        then:
        try {
            loadRoutes route
            Assertions.fail("Should have thrown exception")
        } catch (e) {
            e.message.contains("additional properties")

        }
    }
}
