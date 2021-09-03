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
import org.apache.camel.model.RouteDefinition
import org.apache.camel.model.TryDefinition
import org.apache.camel.model.language.SimpleExpression

class TryTest extends YamlTestSupport {

    def "do-try"() {
        when:
            loadRoutes '''
                - from:
                    uri: "direct:start"
                    steps:
                      - do-try:                              
                         steps:
                           - to: "log:when-a"
                           - to: "log:when-b"
                         do-catch:
                           - exception: 
                               - "java.io.FileNotFoundException"
                               - "java.io.IOException"
                             steps:
                               - to: "log:io-error"
            '''
        then:
            with(context.routeDefinitions[0], RouteDefinition) {
                input.endpointUri == 'direct:start'

                with (outputs[0], TryDefinition) {
                    catchClauses.size() == 1
                    catchClauses[0].outputs.size() == 1
                    catchClauses[0].exceptions.contains('java.io.FileNotFoundException')
                    catchClauses[0].exceptions.contains('java.io.IOException')

                    finallyClause == null
                }
            }
    }

    def "do-try with on-when"() {
        when:
            loadRoutes '''
                - from:
                    uri: "direct:start"
                    steps:
                      - do-try:
                         steps:
                           - to: "log:when-a"
                           - to: "log:when-b"
                         do-catch:
                           - exception: 
                               - "java.io.FileNotFoundException"
                               - "java.io.IOException"
                             on-when:
                               simple: "${body.size()} == 1"
                             steps:
                               - to: "log:io-error"
            '''
        then:
            with(context.routeDefinitions[0], RouteDefinition) {
                input.endpointUri == 'direct:start'

                with (outputs[0], TryDefinition) {
                    catchClauses.size() == 1
                    catchClauses[0].outputs.size() == 1
                    catchClauses[0].exceptions.contains('java.io.FileNotFoundException')
                    catchClauses[0].exceptions.contains('java.io.IOException')

                    with(catchClauses[0].onWhen.expression, SimpleExpression) {
                        expression == '${body.size()} == 1'
                    }

                    finallyClause == null
                }
            }
    }

    def "do-try with do-when and do-finally"() {
        when:
            loadRoutes '''
                - from:
                    uri: "direct:start"
                    steps:
                      - do-try:
                         steps:
                           - to: "log:when-a"
                           - to: "log:when-b"
                         do-catch:
                           - exception: 
                               - "java.io.FileNotFoundException"
                               - "java.io.IOException"
                             on-when:
                               simple: "${body.size()} == 1"
                             steps:
                               - to: "log:io-error"
                         do-finally:
                           steps:
                             - to: "log:finally"
            '''
        then:
            with(context.routeDefinitions[0], RouteDefinition) {
                input.endpointUri == 'direct:start'

                with (outputs[0], TryDefinition) {
                    catchClauses.size() == 1

                    with(catchClauses[0]) {
                        outputs.size() == 1
                        exceptions.contains('java.io.FileNotFoundException')
                        exceptions.contains('java.io.IOException')

                        with(onWhen.expression, SimpleExpression) {
                            expression == '${body.size()} == 1'
                        }
                    }

                    with(finallyClause) {
                        outputs.size() == 1
                    }
                }
            }
    }

    def "do-try with do-finally"() {
        when:
            loadRoutes '''
                - from:
                    uri: "direct:start"
                    steps:
                      - do-try:
                         steps:
                           - to: "log:when-a"
                           - to: "log:when-b"
                         do-finally:
                           steps:
                             - to: "log:finally"
            '''
        then:
            with(context.routeDefinitions[0], RouteDefinition) {
                input.endpointUri == 'direct:start'

                with (outputs[0], TryDefinition) {
                    finallyClause.outputs.size() == 1
                }
            }
    }

}
