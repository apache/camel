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

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The {@code wasm} misconfigurations that fail an {@link OpaSecurityPolicy} route at startup.
 * <p/>
 * These stay unit tests deliberately: none of them needs a bundle that loads and evaluates, so they need no compiled
 * artifact and no container. Everything that evaluates a policy lives in {@link OpaSecurityPolicyWasmIT}, where the
 * bundle is compiled from the Rego under test rather than committed beside it (CAMEL-24742).
 */
public class OpaSecurityPolicyWasmTest extends CamelTestSupport {

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
        // rejected before the bundle is resolved, so the location here is never opened
        OpaSecurityPolicy badPool = new OpaSecurityPolicy();
        badPool.setEvaluationMode("wasm");
        badPool.setPolicyBundle("file:unused.wasm");
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
