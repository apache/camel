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

class EndpointsTest extends YamlTestSupport {

    /*
    def "endpoints dsl (#resource.location)"(Resource resource) {
        setup:
            context.propertiesComponent.initialProperties = [
                'direct.id': 'myDirect',
                'direct.timeout': '1234s',
                'seda.id': 'mySeda',
                'telegram.token': 'myToken+'
            ] as Properties

        when:
            context.routesLoader.loadRoutes(resource)
            context.start()

        then:
            context.routeDefinitions.size() == 1

            with(context.routeDefinitions[0], RouteDefinition) {
                input.endpointUri == 'direct:{{direct.id}}?timeout=#property:direct.timeout'

                with (outputs[0], ToDefinition) {
                    endpointUri == 'seda:{{seda.id}}'
                }
                with (outputs[1], ToDefinition) {
                    endpointUri == 'telegram:bots?authorizationToken=RAW({{telegram.token}})'
                }
            }

            with (context.endpoints.find {it instanceof DirectEndpoint}, DirectEndpoint) {
                timeout == 1_234_000
            }
            with (context.endpoints.find {it instanceof SedaEndpoint}, SedaEndpoint) {
                 endpointUri == 'seda://mySeda'
            }
            with (context.endpoints.find { it instanceof TelegramEndpoint }, TelegramEndpoint) {
                endpointUri == 'telegram://bots?authorizationToken=RAW(myToken+)'
                configuration.authorizationToken == 'myToken+'
            }

        where:
            resource << [
                Resource.fromString('route.yaml', '''
                    - route:
                        from:
                          direct:
                            name: "{{direct.id}}"
                            timeout: "#property:direct.timeout"
                        steps:
                          - seda: "{{seda.id}}"
                          - telegram:
                              type: "bots"
                              authorization-token: "{{telegram.token}}"
                '''.stripIndent()),
                Resource.fromString('route-raw.yaml', '''
                    - route:
                        from:
                          direct:
                            name: "{{direct.id}}"
                            timeout: "#property:direct.timeout"
                        steps:
                          - seda: "{{seda.id}}"
                          - telegram:
                              type: "bots"
                              authorization-token: "RAW({{telegram.token}})"
                '''.stripIndent()),
                Resource.fromString('from.yaml', '''
                    - from:
                        direct:
                          name: "{{direct.id}}"
                          timeout: "#property:direct.timeout"
                        steps:
                          - seda: "{{seda.id}}"
                          - telegram:
                              type: "bots"
                              authorization-token: "{{telegram.token}}"
                '''.stripIndent()),
                Resource.fromString('from-raw.yaml', '''
                    - from:
                        direct:
                          name: "{{direct.id}}"
                          timeout: "#property:direct.timeout"
                        steps:
                          - seda: "{{seda.id}}"
                          - telegram:
                              type: "bots"
                              authorization-token: "RAW({{telegram.token}})"
                '''.stripIndent())
            ]
    }
    */
}
