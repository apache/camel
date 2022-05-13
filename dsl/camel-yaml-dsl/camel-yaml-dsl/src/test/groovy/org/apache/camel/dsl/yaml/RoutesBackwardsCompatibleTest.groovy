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

class RoutesBackwardsCompatibleTest extends YamlTestSupport {

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

    def "load from with parameters"() {
        when:
            loadRoutes '''
                - from:
                    uri: "direct:info"
                    parameters:
                      timeout: 1234    
                    steps:
                    - log: "message"
            '''
        then:
            context.routeDefinitions.size() == 1

            with(context.routeDefinitions[0], RouteDefinition) {
                input.endpointUri == 'direct:info?timeout=1234'

                with (outputs[0], LogDefinition) {
                    message == 'message'
                }
            }
    }

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
            loadRoutesNoValidate '''
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

    def "load route with parameters"() {
        when:
            loadRoutesNoValidate '''
                - route:
                    from:
                      uri: "direct:info"
                      parameters:
                        timeout: 1234
                    steps:
                      - log: "message"
            '''
        then:
            context.routeDefinitions.size() == 1

            with(context.routeDefinitions[0], RouteDefinition) {
                input.endpointUri == 'direct:info?timeout=1234'

                with (outputs[0], LogDefinition) {
                    message == 'message'
                }
            }
    }

    def "load route inlined"() {
        when:
            loadRoutesNoValidate '''
                - route:
                    id: demo-route
                    stream-caching: true
                    auto-startup: false
                    from:
                      uri: "direct:info"
                    steps:
                      - log: "message"
            '''
        then:
        context.routeDefinitions.size() == 1

        with(context.routeDefinitions[0], RouteDefinition) {
            routeId == 'demo-route'
            streamCache == 'true'
            autoStartup == 'false'
            input.endpointUri == 'direct:info'

            with (outputs[0], LogDefinition) {
                message == 'message'
            }
        }
    }

    def "load route description"() {
        when:
            loadRoutesNoValidate '''
                - route:
                    id: demo-route
                    description: something cool
                    from:
                      uri: "direct:info"
                    steps:
                      - log: "message"
            '''
        then:
        context.routeDefinitions.size() == 1

        with(context.routeDefinitions[0], RouteDefinition) {
            routeId == 'demo-route'
            description.text == 'something cool'
            input.endpointUri == 'direct:info'

            with (outputs[0], LogDefinition) {
                message == 'message'
            }
        }
    }

    def "load route description with precondition"() {
        when:
            loadRoutesNoValidate '''
                - route:
                    id: demo-route
                    description: something cool
                    precondition: "{{?red}}"
                    from:
                      uri: "direct:info"
                    steps:
                      - log: "message"
            '''
        then:
        context.routeDefinitions.size() == 1

        with(context.routeDefinitions[0], RouteDefinition) {
            routeId == 'demo-route'
            description.text == 'something cool'
            input.endpointUri == 'direct:info'
            precondition == '{{?red}}'

            with (outputs[0], LogDefinition) {
                message == 'message'
            }
        }
    }
}
