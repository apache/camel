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
package org.apache.camel.component.opa;

import java.util.List;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckHelper;
import org.apache.camel.health.WritableHealthCheckRepository;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * In {@code wasm} mode the policy is evaluated in-process, so there is no OPA server to probe and no producer health
 * check is registered. {@code failOpen}, on the other hand, still governs an evaluation failure (a busy pool, a bad
 * bundle) in {@code wasm} mode too, so it must not be rejected (CAMEL-24743).
 */
public class OpaWasmModeValidationTest extends CamelTestSupport {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // a rest-mode route registers a producer readiness check (positive control), a wasm-mode route
                // sharing the same policy path must not - the difference is exactly what this test asserts
                from("direct:rest").to("opa:authz/allow?serverUrl=http://opa-rest:8181");
                from("direct:wasm").to("opa:authz/allow?evaluationMode=wasm&policyBundle=classpath:authz.wasm");
            }
        };
    }

    private List<HealthCheck> producerChecks() {
        WritableHealthCheckRepository repository = HealthCheckHelper.getHealthCheckRepository(
                context, "producers", WritableHealthCheckRepository.class);
        assertThat(repository).isNotNull();
        // producer health checks are disabled globally by default, so enable the repository to read them back
        repository.setEnabled(true);
        return repository.stream().toList();
    }

    @Test
    void registersTheCheckForTheRestRouteButNotTheWasmRoute() {
        List<HealthCheck> checks = producerChecks();
        // exactly one check, and it is the rest route's - the wasm route evaluates in-process with no server to probe
        assertThat(checks).hasSize(1);
        assertThat(checks.get(0).getId()).contains("opa-rest");
    }

    @Test
    void acceptsFailOpenInWasmMode() {
        // failOpen governs an evaluation failure (a busy pool, a bad bundle), which happens in wasm too, so it is a
        // valid option here and starting the route must not throw
        assertThatCode(() -> context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:failopen")
                        .to("opa:authz/allow?evaluationMode=wasm&policyBundle=classpath:authz.wasm&failOpen=true");
            }
        })).doesNotThrowAnyException();
    }
}
