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
import org.apache.camel.model.CircuitBreakerDefinition
import org.apache.camel.model.LogDefinition
import org.apache.camel.model.RouteDefinition
import org.apache.camel.model.ToDefinition

class CircuitBreakerTest extends YamlTestSupport {

    def "circuit-breaker"() {
        when:
            loadRoutes '''
                - from:
                    uri: "direct:start"
                    steps:
                      - circuit-breaker:   
                         steps:
                           - log: "test"                           
                         configuration: "my-config"
                         resilience4j-configuration:
                           failure-rate-threshold: 10
                         on-fallback:
                           fallback-via-network: true
            '''
        then:
            with(context.routeDefinitions[0], RouteDefinition) {
                input.endpointUri == 'direct:start'

                with(outputs[0], CircuitBreakerDefinition) {
                    configuration == 'my-config'

                    resilience4jConfiguration != null
                    resilience4jConfiguration.failureRateThreshold == '10'

                    onFallback != null
                    onFallback.fallbackViaNetwork == "true"

                    with (outputs[0], LogDefinition) {
                        message == "test"
                    }
                }
            }
    }
    def "circuit-breaker-camelCase"() {
        when:
            loadRoutes '''
                - from:
                    uri: "direct:start"
                    steps:
                      - circuitBreaker:   
                         steps:
                           - log: "test"                           
                         configuration: "my-config"
                         resilience4jConfiguration:
                           failureRateThreshold: 10
                         onFallback:
                           fallbackViaNetwork: true
            '''
        then:
            with(context.routeDefinitions[0], RouteDefinition) {
                input.endpointUri == 'direct:start'

                with(outputs[0], CircuitBreakerDefinition) {
                    configuration == 'my-config'

                    resilience4jConfiguration != null
                    resilience4jConfiguration.failureRateThreshold == '10'

                    onFallback != null
                    onFallback.fallbackViaNetwork == "true"

                    with (outputs[0], LogDefinition) {
                        message == "test"
                    }
                }
            }
    }

    def "circuit-breaker with on-fallback steps"() {
        when:
            loadRoutes '''
                - from:
                    uri: "direct:start"
                    steps:
                      - circuit-breaker: 
                         # TODO: steps need to be defined before on-fallback             
                         steps:
                           - to: "log:cb"
                         on-fallback:
                             steps:
                               - to: "log:fb" 
            '''
        then:
            with(context.routeDefinitions[0], RouteDefinition) {
                input.endpointUri == 'direct:start'

                with(outputs[0], CircuitBreakerDefinition) {
                    with (outputs[0], ToDefinition) {
                        endpointUri == "log:cb"
                    }
                    with (onFallback.outputs[0], ToDefinition) {
                        endpointUri == "log:fb"
                    }
                }
            }
    }
}
