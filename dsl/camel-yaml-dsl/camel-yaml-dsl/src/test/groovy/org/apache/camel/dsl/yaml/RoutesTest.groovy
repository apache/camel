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
import org.apache.camel.model.LogDefinition
import org.apache.camel.model.RouteDefinition
import spock.lang.Ignore

class RoutesTest extends YamlTestSupport {

    @Ignore
    def "load from"() {
        when:
            loadRoutes '''
                - from:
                    uri: "direct:info"
                    steps:
                      - log: "message"
            '''
        then:
            context.routeDefinitions.size() == 1

            with(context.routeDefinitions[0], RouteDefinition) {
                input.endpointUri == 'direct:info'

                with (outputs[0], LogDefinition) {
                    message == 'message'
                }
            }
    }
    @Ignore
    def "load multi from "() {
        when:
            loadRoutes '''
                - from:
                    uri: "direct:1"
                    steps:
                      - log: "1"
                - from:
                    uri: "direct:2"
                    steps:
                      - log: "2"
            '''
        then:
            context.routeDefinitions.size() == 2

            with(context.routeDefinitions[0], RouteDefinition) {
                input.endpointUri == 'direct:1'

                with (outputs[0], LogDefinition) {
                    message == '1'
                }
            }
            with(context.routeDefinitions[1], RouteDefinition) {
                input.endpointUri == 'direct:2'

                with (outputs[0], LogDefinition) {
                    message == '2'
                }
            }
    }

    def "load route"() {
        when:
            loadRoutes '''
                - route:
                    from:
                      uri: "direct:info"
                    steps:
                      - log: "message"
            '''
        then:
            context.routeDefinitions.size() == 1

            with(context.routeDefinitions[0], RouteDefinition) {
                input.endpointUri == 'direct:info'

                with (outputs[0], LogDefinition) {
                    message == 'message'
                }
            }
    }
}
