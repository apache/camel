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
import org.apache.camel.model.RecipientListDefinition
import org.apache.camel.model.language.ExpressionDefinition
import org.apache.camel.spi.Resource

class RecipientListTest extends YamlTestSupport {

    def "recipient-list definition (#resource.location)"(Resource resource) {
        when:
            context.routesLoader.loadRoutes(resource)
        then:
            with(context.routeDefinitions[0].outputs[0], RecipientListDefinition) {
                stopOnException == 'true'
                parallelProcessing == 'true'

                with(expression, ExpressionDefinition) {
                    language == 'constant'
                    expression == 'direct:a,direct:b'
                }
            }
        where:
            resource << [
                asResource('expression', '''
                    - from:
                        uri: "direct:start"
                        steps:    
                          - recipient-list:
                              constant: "direct:a,direct:b"
                              stop-on-exception: true
                              parallel-processing: true
                          - to: "mock:result"
                    '''),
                asResource('expression-block', '''
                    - from:
                        uri: "direct:start"
                        steps:    
                          - recipient-list:
                              expression:  
                                constant: "direct:a,direct:b"
                              stop-on-exception: true
                              parallel-processing: true
                          - to: "mock:result"
                    ''')
            ]
    }
}
