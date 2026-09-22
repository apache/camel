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
package org.apache.camel.component.opa.security;

import java.util.List;

import org.apache.camel.CamelAuthorizationException;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckRegistry;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link OpaSecurityPolicy} enforcing a route with an in-process WebAssembly bundle. The decision contract is the same
 * as the REST engine - a match proceeds, a non-match throws {@code CamelAuthorizationException} - and no server
 * readiness check is registered, because the policy is evaluated in-process (CAMEL-24830).
 */
public class OpaSecurityPolicyWasmTest extends CamelTestSupport {

    private final OpaSecurityPolicy wasmPolicy = new OpaSecurityPolicy();
    private final OpaSecurityPolicy restPolicy = new OpaSecurityPolicy();

    @Override
    protected RouteBuilder createRouteBuilder() {
        wasmPolicy.setEvaluationMode("wasm");
        wasmPolicy.setPolicyBundle("classpath:authz.wasm");
        wasmPolicy.setPolicyPath("authz/allow");

        // a rest-mode policy is the positive control for the readiness-check assertion: it registers a check, the
        // wasm one must not
        restPolicy.setServerUrl("http://opa-rest:8181");
        restPolicy.setPolicyPath("authz/allow");

        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:wasm").policy(wasmPolicy).to("mock:allowed");
                from("direct:rest").policy(restPolicy).to("mock:rest");
            }
        };
    }

    @Test
    void allowsWhenTheWasmPolicyMatches() throws Exception {
        MockEndpoint allowed = getMockEndpoint("mock:allowed");
        allowed.expectedMessageCount(1);

        template.sendBodyAndHeader("direct:wasm", "payload", "user", "alice");

        allowed.assertIsSatisfied();
    }

    @Test
    void deniesWhenTheWasmPolicyDoesNotMatch() throws Exception {
        MockEndpoint allowed = getMockEndpoint("mock:allowed");
        allowed.expectedMessageCount(0);

        assertThatThrownBy(() -> template.sendBodyAndHeader("direct:wasm", "payload", "user", "mallory"))
                .isInstanceOf(CamelExecutionException.class)
                .hasCauseInstanceOf(CamelAuthorizationException.class);

        allowed.assertIsSatisfied();
    }

    @Test
    void registersOnlyTheRestPolicysReadinessCheck() {
        HealthCheckRegistry registry = HealthCheckRegistry.get(context);
        assertThat(registry).isNotNull();
        List<HealthCheck> checks = registry.stream()
                .filter(hc -> hc.getId().startsWith("security-policy:opa-"))
                .toList();

        // exactly one, and it is the rest policy's - the wasm policy evaluates in-process with no server to probe.
        // Assert the full ID contract, not just the hostname, so a refactor of the ID-building logic cannot pass here
        // silently: OpaSecurityPolicyHealthCheck builds it as "security-policy:opa-" + sanitized serverUrl/policyPath.
        assertThat(checks).hasSize(1);
        assertThat(checks.get(0).getId()).startsWith("security-policy:opa-").contains("opa-rest");
    }

    @Test
    void failsRouteStartOnABundleThatLoadsButIsNotAValidModule() {
        // OpaWasmEvaluator borrows an instance at startup so a broken bundle fails fast, and beforeWrap - which cannot
        // throw a checked exception - must surface that rather than swallow it, or a route would start and then
        // authorize nothing. authz.rego is the Rego source: it loads as bytes but is not a compiled wasm module.
        OpaSecurityPolicy corrupt = new OpaSecurityPolicy();
        corrupt.setEvaluationMode("wasm");
        corrupt.setPolicyBundle("classpath:authz.rego");
        corrupt.setPolicyPath("authz/allow");

        // OpaPolicy rejects the module during the warmup borrow with an unchecked exception, which buildEvaluator's
        // catch(RuntimeException) rethrows as-is; reaching the caller of addRoutes is what proves the route did not
        // start (a first-exchange failure would not surface here).
        assertThatThrownBy(() -> context.addRoutes(routeWith(corrupt)))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void failsRouteStartWhenTheBundleResourceCannotBeLoaded() {
        // a bundle location that resolves to nothing is a checked failure in loadPolicy, which beforeWrap wraps; the
        // wrapper message is what proves the failure surfaced at startup rather than being swallowed
        OpaSecurityPolicy missing = new OpaSecurityPolicy();
        missing.setEvaluationMode("wasm");
        missing.setPolicyBundle("classpath:does-not-exist.wasm");
        missing.setPolicyPath("authz/allow");

        assertThatThrownBy(() -> context.addRoutes(routeWith(missing)))
                .isInstanceOf(RuntimeCamelException.class)
                .hasMessageContaining("Could not load the wasm policy bundle");
    }

    @Test
    void failsRouteStartOnAnUnknownEvaluationMode() {
        // this path lives entirely in the policy's buildEvaluator and is covered by no endpoint test
        OpaSecurityPolicy bogus = new OpaSecurityPolicy();
        bogus.setEvaluationMode("bogus");
        bogus.setPolicyPath("authz/allow");

        assertThatThrownBy(() -> context.addRoutes(routeWith(bogus)))
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown evaluationMode");
    }

    @Test
    void failsRouteStartWhenNoWasmBundleIsConfigured() {
        // buildEvaluator forwards to OpaWasmEvaluator.create, so its validation applies through the policy too
        OpaSecurityPolicy noBundle = new OpaSecurityPolicy();
        noBundle.setEvaluationMode("wasm");
        noBundle.setPolicyPath("authz/allow");

        assertThatThrownBy(() -> context.addRoutes(routeWith(noBundle)))
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("policyBundle is required");
    }

    @Test
    void failsRouteStartOnAPoolSizeBelowOne() {
        OpaSecurityPolicy badPool = new OpaSecurityPolicy();
        badPool.setEvaluationMode("wasm");
        badPool.setPolicyBundle("classpath:authz.wasm");
        badPool.setPolicyPath("authz/allow");
        badPool.setPoolSize(0);

        assertThatThrownBy(() -> context.addRoutes(routeWith(badPool)))
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("poolSize must be at least 1");
    }

    private static RouteBuilder routeWith(OpaSecurityPolicy policy) {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:probe").policy(policy).to("mock:never");
            }
        };
    }
}
