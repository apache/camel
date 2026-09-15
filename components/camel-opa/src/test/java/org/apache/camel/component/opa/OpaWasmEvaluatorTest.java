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
import org.junit.jupiter.api.Timeout;

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
    void keepsTheDenyReasonsJustLikeTheRestEngine() {
        Exchange out = template.request(
                "opa:authz/decision?evaluationMode=wasm&policyBundle=classpath:authz.wasm",
                e -> e.getMessage().setHeader("user", "mallory"));

        assertThat(out.getException()).isNull();
        assertThat(out.getMessage().getHeader(OpaConstants.DECISION_ALLOW)).isEqualTo(false);
        assertThat(out.getMessage().getHeader(OpaConstants.DECISION, Map.class))
                .containsEntry("reasons", List.of("not the owner"));
    }

    @Test
    void keepsTheEntrypointAcrossPooledReuse() {
        // Returning a borrowed OpaPolicy resets it, and a reset puts the entrypoint back to 0 - so an instance
        // configured only where it was built answers the first exchange from authz/decision and every later one
        // from whatever rule happens to be entrypoint 0 (here authz/allow, a bare boolean). A single-instance
        // pool and more than one message is what makes that visible.
        String decision = "opa:authz/decision?evaluationMode=wasm&policyBundle=classpath:authz.wasm&poolSize=1";

        for (int i = 0; i < 5; i++) {
            Exchange out = template.request(decision, e -> e.getMessage().setHeader("user", "alice"));

            assertThat(out.getException()).as("exchange %d", i).isNull();
            assertThat(out.getMessage().getHeader(OpaConstants.DECISION))
                    .as("message %d was still decided by authz/decision", i)
                    .isInstanceOf(Map.class);
            assertThat(out.getMessage().getHeader(OpaConstants.DECISION, Map.class)).containsEntry("allow", true);
        }
    }

    @Test
    void appliesTheDataDocumentPackedInTheBundle() {
        // roles.rego decides from data.admins, which opa build packs into the bundle as data.json rather than
        // into the module. A reset clears the data as well as the entrypoint, so it too has to be re-applied on
        // every borrow - without it the policy sees an empty data document and denies everyone.
        String roles = "opa:roles/allow?evaluationMode=wasm&policyBundle=classpath:roles-bundle.tar.gz&poolSize=1";

        for (int i = 0; i < 3; i++) {
            Exchange allowed = template.request(roles, e -> e.getMessage().setHeader("user", "carol"));
            Exchange denied = template.request(roles, e -> e.getMessage().setHeader("user", "alice"));

            assertThat(allowed.getException()).as("exchange %d", i).isNull();
            assertThat(allowed.getMessage().getHeader(OpaConstants.DECISION_ALLOW))
                    .as("data.admins was still visible on message %d", i)
                    .isEqualTo(true);
            assertThat(denied.getMessage().getHeader(OpaConstants.DECISION_ALLOW)).isEqualTo(false);
        }
    }

    @Test
    void failsClosedOnAnUndefinedDecisionJustLikeTheRestEngine() {
        // authz/strict_allow has no default, so for mallory the rule is undefined. The WASM ABI returns an
        // empty array where the REST client raises an error; both must reach the route the same way.
        Exchange out = template.request(
                "opa:authz/strict_allow?evaluationMode=wasm&policyBundle=classpath:authz.wasm",
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
    void rejectsAPoolSizeBelowOne() {
        // the pool rejects it as well, but as "maxSize must be positive" - its own parameter rather than the
        // option that was set, which is what the operator has to go looking for
        assertThatThrownBy(() -> template.request(
                "opa:authz/allow?evaluationMode=wasm&policyBundle=classpath:authz.wasm&poolSize=0", e -> {
                }))
                .isInstanceOf(ResolveEndpointFailedException.class)
                .hasMessageContaining("poolSize must be at least 1");
    }

    @Test
    @Timeout(60)
    void keepsThePoolUsableAfterRepeatedEvaluationFailures() {
        // a failed evaluation discards its instance. Mishandle the pool's permit while doing so and a
        // single-instance pool either wedges on the next borrow or quietly stops bounding anything - neither of
        // which a single failing exchange would show.
        String strict = "opa:authz/strict_allow?evaluationMode=wasm&policyBundle=classpath:authz.wasm&poolSize=1";

        for (int i = 0; i < 5; i++) {
            Exchange failed = template.request(strict, e -> e.getMessage().setHeader("user", "mallory"));
            assertThat(failed.getException()).as("failure %d", i).isInstanceOf(OpaPolicyEvaluationException.class);
        }

        Exchange out = template.request(strict, e -> e.getMessage().setHeader("user", "alice"));

        assertThat(out.getException()).isNull();
        assertThat(out.getMessage().getHeader(OpaConstants.DECISION_ALLOW)).isEqualTo(true);
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
