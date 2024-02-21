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
import org.apache.camel.model.cloud.BlacklistServiceCallServiceFilterConfiguration
import org.apache.camel.model.cloud.ServiceCallDefinition
import org.apache.camel.model.cloud.StaticServiceCallServiceDiscoveryConfiguration
import org.junit.jupiter.api.Assertions

class ServiceCallTest extends YamlTestSupport {

    def "service-call"() {
        when:
            loadRoutes '''
                - from:
                   uri: "direct:start"
                   steps:
                     - serviceCall:
                         name: "sc"
                         staticServiceDiscovery:
                             servers:
                                 - "service1@host1"
                                 - "service1@host2"                         
                         blacklistServiceFilter:
                             servers:
                                 - "service2@host1"
            '''
        then:
            context.routeDefinitions.size() == 1

            with(context.routeDefinitions[0], RouteDefinition) {
                with (outputs[0], ServiceCallDefinition) {
                    name == 'sc'
                    with (serviceDiscoveryConfiguration, StaticServiceCallServiceDiscoveryConfiguration) {
                        servers.contains('service1@host1')
                        servers.contains('service1@host2')
                    }
                    with (serviceFilterConfiguration, BlacklistServiceCallServiceFilterConfiguration) {
                        servers.contains('service2@host1')
                    }
                }
            }
    }

    def "Error: kebab-case: service-call"() {
        when:
        var route = '''
                - from:
                   uri: "direct:start"
                   steps:
                     - service-call:
                         name: "sc"
                         staticServiceDiscovery:
                             servers:
                                 - "service1@host1"
                                 - "service1@host2"                         
                         blacklistServiceFilter:
                             servers:
                                 - "service2@host1"
            '''
        then:
        try {
            loadRoutes(route)
            Assertions.fail("Should have thrown exception")
        } catch (Exception e) {
            Assertions.assertTrue(e.message.contains("additional properties"), e.getMessage())
        }
    }

    def "Error: kebab-case: static-service-discovery"() {
        when:
        var route = '''
                - from:
                   uri: "direct:start"
                   steps:
                     - serviceCall:
                         name: "sc"
                         static-service-discovery:
                             servers:
                                 - "service1@host1"
                                 - "service1@host2"                         
                         blacklistServiceFilter:
                             servers:
                                 - "service2@host1"
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
