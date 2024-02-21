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
import org.apache.camel.model.StepDefinition
import org.apache.camel.model.ToDefinition
import org.apache.camel.model.TransformDefinition

class TransformTest extends YamlTestSupport {

    def "transform with data types"() {
        when:
            loadRoutes '''
                - from:
                    uri: "direct:start"
                    steps:    
                      - step:
                          steps:
                            - transform:
                                fromType: "plain-text"
                                toType: "application-octet-stream"
                      - to: "mock:result"    
                             
            '''
        then:
            with(context.routeDefinitions[0].outputs[0], StepDefinition) {
                with(outputs[0], TransformDefinition) {
                    fromType == 'plain-text'
                    toType == 'application-octet-stream'
                }
            }
            with(context.routeDefinitions[0].outputs[1], ToDefinition) {
                endpointUri == 'mock:result'
            }
    }
}
