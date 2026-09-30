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
package org.apache.camel.dsl.yaml;

import org.apache.camel.dsl.yaml.support.YamlTestSupport;
import org.apache.camel.model.CircuitBreakerDefinition;
import org.apache.camel.model.LogDefinition;
import org.apache.camel.model.ToDefinition;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CircuitBreakerTest extends YamlTestSupport {

    @Test
    void circuitBreaker() throws Exception {
        loadRoutes("""
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
                """);

        var route = context.getRouteDefinitions().get(0);
        assertThat(route.getInput().getEndpointUri()).isEqualTo("direct:start");

        var cb = (CircuitBreakerDefinition) route.getOutputs().get(0);
        assertThat(cb.getConfiguration()).isEqualTo("my-config");

        assertThat(cb.getResilience4jConfiguration()).isNotNull();
        assertThat(cb.getResilience4jConfiguration().getFailureRateThreshold()).isEqualTo("10");

        assertThat(cb.getOnFallback()).isNotNull();
        assertThat(cb.getOnFallback().getFallbackViaNetwork()).isEqualTo("true");

        var logDef = (LogDefinition) cb.getOutputs().get(0);
        assertThat(logDef.getMessage()).isEqualTo("test");
    }

    @Test
    void circuitBreakerInheritErrorHandlerPlaceholder() throws Exception {
        // YamlTestSupport validates against the raw schema (without the placeholder leniency of YamlValidator),
        // which types inheritErrorHandler as boolean; the runtime resolves the placeholder when the route starts
        // and YamlValidatorSchemaGroupsTest covers that the validator accepts it (CAMEL-24696)
        loadRoutesNoValidate("""
                - from:
                    uri: "direct:start"
                    steps:
                      - circuitBreaker:
                         inheritErrorHandler: "{{myInherit}}"
                         steps:
                           - log: "test"
                """);

        var cb = (CircuitBreakerDefinition) context.getRouteDefinitions().get(0).getOutputs().get(0);
        assertThat(cb.getInheritErrorHandler()).isEqualTo("{{myInherit}}");
    }

    @Test
    void circuitBreakerWithOnFallbackSteps() throws Exception {
        loadRoutes("""
                - from:
                    uri: "direct:start"
                    steps:
                      - circuitBreaker:
                         steps:
                           - to: "log:cb"
                         onFallback:
                             steps:
                               - to: "log:fb"
                """);

        var route = context.getRouteDefinitions().get(0);
        assertThat(route.getInput().getEndpointUri()).isEqualTo("direct:start");

        var cb = (CircuitBreakerDefinition) route.getOutputs().get(0);
        assertThat(cb.getOutputs().size()).isEqualTo(1);
        var cbTo = (ToDefinition) cb.getOutputs().get(0);
        assertThat(cbTo.getEndpointUri()).isEqualTo("log:cb");

        assertThat(cb.getOnFallback().getOutputs().size()).isEqualTo(1);
        var fbTo = (ToDefinition) cb.getOnFallback().getOutputs().get(0);
        assertThat(fbTo.getEndpointUri()).isEqualTo("log:fb");
    }

    @Test
    void circuitBreakerWithOnFallbackBeforeSteps() throws Exception {
        // CAMEL-24700: the order of the keys must not matter
        loadRoutes("""
                - from:
                    uri: "direct:start"
                    steps:
                      - circuitBreaker:
                         onFallback:
                             steps:
                               - to: "log:fb"
                         steps:
                           - to: "log:cb"
                """);

        var route = context.getRouteDefinitions().get(0);
        assertThat(route.getInput().getEndpointUri()).isEqualTo("direct:start");

        var cb = (CircuitBreakerDefinition) route.getOutputs().get(0);
        assertThat(cb.getOutputs().size()).isEqualTo(1);
        var cbTo = (ToDefinition) cb.getOutputs().get(0);
        assertThat(cbTo.getEndpointUri()).isEqualTo("log:cb");

        assertThat(cb.getOnFallback().getOutputs().size()).isEqualTo(1);
        var fbTo = (ToDefinition) cb.getOnFallback().getOutputs().get(0);
        assertThat(fbTo.getEndpointUri()).isEqualTo("log:fb");
    }
}
