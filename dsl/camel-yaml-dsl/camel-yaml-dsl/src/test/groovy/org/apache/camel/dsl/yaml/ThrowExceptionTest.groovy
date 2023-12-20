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
import org.apache.camel.model.ThrowExceptionDefinition
import org.junit.jupiter.api.Assertions

class ThrowExceptionTest extends YamlTestSupport {

    def "throwException definition"() {
        when:
            loadRoutes '''
                - from:
                    uri: "direct:start"
                    steps:    
                      - throwException:  
                          exceptionType: "java.lang.IllegalArgumentException"
                          message: "test"
            '''
        then:
            with(context.routeDefinitions[0].outputs[0], ThrowExceptionDefinition) {
                message == 'test'
                exceptionType == "java.lang.IllegalArgumentException"
            }
    }

    def "Error: kebab-case: throw-exception"() {
        when:
        var route = '''
                - from:
                    uri: "direct:start"
                    steps:    
                      - throw-exception:
                          exceptionType: "java.lang.IllegalArgumentException"
                          message: "test"
            '''
        then:
        try {
            loadRoutes(route)
            Assertions.fail("Should have thrown exception")
        } catch (Exception e) {
            Assertions.assertTrue(e.message.contains("additional properties"), e.getMessage())
        }
    }

    def "Error: kebab-case: exception-type"() {
        when:
        var route = '''
                - from:
                    uri: "direct:start"
                    steps:    
                      - throwException:
                          exception-type: "java.lang.IllegalArgumentException"
                          message: "test"
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
