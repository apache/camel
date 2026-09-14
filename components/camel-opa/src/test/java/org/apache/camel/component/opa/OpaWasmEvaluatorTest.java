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

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.camel.Exchange;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * In-process evaluation of a bundle produced by {@code opa build -t wasm}.
 * <p/>
 * The bundle here was compiled from the same {@code authz.rego} the REST tests use, so the assertions double as a check
 * that a route sees the same decision whichever engine evaluated it.
 */
public class OpaWasmEvaluatorTest extends CamelTestSupport {

    private static final String WASM = "opa:authz/allow?evaluationMode=wasm&policyBundle=classpath:authz.wasm";

    @Test
    void allowsWhenThePolicyMatches() {
        Exchange out = template.request(WASM, e -> e.getMessage().setHeader("user", "alice"));

        assertThat(out.getException()).isNull();
        assertThat(out.getMessage().getHeader(OpaConstants.DECISION_ALLOW)).isEqualTo(true);
    }

    @Test
    void deniesWhenThePolicyDoesNotMatch() {
        Exchange out = template.request(WASM, e -> e.getMessage().setHeader("user", "mallory"));

        assertThat(out.getException()).isNull();
        assertThat(out.getMessage().getHeader(OpaConstants.DECISION_ALLOW)).isEqualTo(false);
    }

    @Test
    void readsAVerdictOutOfADecisionObject() {
        Exchange out = template.request(
                "opa:authz/decision?evaluationMode=wasm&policyBundle=classpath:authz.wasm",
                e -> e.getMessage().setHeader("user", "alice"));

        assertThat(out.getException()).isNull();
        assertThat(out.getMessage().getHeader(OpaConstants.DECISION_ALLOW)).isEqualTo(true);
        assertThat(out.getMessage().getHeader(OpaConstants.DECISION, Map.class)).containsEntry("allow", true);
    }

    @Test
    void failsClosedOnAnUndefinedDecisionJustLikeTheRestEngine() {
        // authz/decision has no default, so for mallory the rule is undefined. The WASM ABI returns an empty
        // array where the REST client raises an error; both must reach the route the same way.
        Exchange out = template.request(
                "opa:authz/decision?evaluationMode=wasm&policyBundle=classpath:authz.wasm",
                e -> e.getMessage().setHeader("user", "mallory"));

        assertThat(out.getException()).isInstanceOf(OpaPolicyEvaluationException.class);
        assertThat(out.getMessage().getHeader(OpaConstants.DECISION_ALLOW)).isNull();
    }

    @Test
    void acceptsTheBundleTarballOpaBuildActuallyEmits() {
        Exchange out = template.request(
                "opa:authz/allow?evaluationMode=wasm&policyBundle=classpath:authz-bundle.tar.gz",
                e -> e.getMessage().setHeader("user", "alice"));

        assertThat(out.getException()).isNull();
        assertThat(out.getMessage().getHeader(OpaConstants.DECISION_ALLOW)).isEqualTo(true);
    }

    @Test
    void requiresAPolicyBundle() {
        // the check runs when the endpoint starts, so a misconfiguration fails fast rather than once per message
        assertThatThrownBy(() -> template.request("opa:authz/allow?evaluationMode=wasm", e -> {
        }))
                .isInstanceOf(ResolveEndpointFailedException.class)
                .hasMessageContaining("policyBundle is required");
    }

    @Test
    void evaluatesCorrectlyFromManyThreadsAtOnce() throws Exception {
        // OpaPolicy is not thread-safe; without pooling a concurrent route would interleave input and data
        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            var tasks = IntStream.range(0, threads * 8).mapToObj(i -> (Callable<Boolean>) () -> {
                String user = i % 2 == 0 ? "alice" : "mallory";
                Exchange out = template.request(WASM, e -> e.getMessage().setHeader("user", user));
                assertThat(out.getException()).isNull();
                return Boolean.valueOf("alice".equals(user))
                        .equals(out.getMessage().getHeader(OpaConstants.DECISION_ALLOW));
            }).collect(Collectors.toList());

            for (var future : pool.invokeAll(tasks)) {
                assertThat(future.get()).as("verdict matched the user on every thread").isTrue();
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
