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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckHelper;
import org.apache.camel.health.WritableHealthCheckRepository;
import org.apache.camel.test.infra.opa.services.OpaWasmBundleBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * In-process evaluation of a bundle compiled from the very {@code authz.rego} that {@link OpaIT} uploads to a real OPA
 * server.
 * <p/>
 * Sharing one policy between the two classes is the point rather than a convenience: the component promises that a
 * route sees the same decision whichever engine evaluated it, and that promise is only tested if both engines are asked
 * about the same rules. It also closes the way that promise was broken before - a compiled bundle committed beside the
 * Rego drifted from it, and the two suites asserted opposite things about {@code authz/decision} while both stayed
 * green (CAMEL-24741). Nothing is committed now; the bundle is built from the policy under test.
 */
public class OpaWasmIT extends CamelTestSupport {

    @TempDir
    static Path bundles;

    private static String authz;
    private static String module;
    private static String roles;

    private static String resource(String name) throws Exception {
        try (InputStream in = OpaWasmIT.class.getResourceAsStream(name)) {
            if (in == null) {
                throw new IllegalStateException("Test resource not found on the classpath: " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @BeforeAll
    static void compileBundles() throws Exception {
        byte[] authzBundle = OpaWasmBundleBuilder.build(
                "authz.rego", resource("/authz.rego"),
                "authz/allow", "authz/decision", "authz/strict_allow");
        authz = write("authz-bundle.tar.gz", authzBundle);
        module = extractModule(authzBundle);

        // roles.rego decides from data.admins, which opa build packs beside it as data.json
        byte[] rolesBundle = OpaWasmBundleBuilder.build(
                Map.of("roles.rego", resource("/wasm-data/roles.rego").getBytes(StandardCharsets.UTF_8),
                        "data.json", resource("/wasm-data/data.json").getBytes(StandardCharsets.UTF_8)),
                "roles/allow");
        roles = write("roles-bundle.tar.gz", rolesBundle);
    }

    /** The /policy.wasm inside a bundle, so the bare-module branch of loadPolicy keeps its coverage. */
    private static String extractModule(byte[] bundle) throws Exception {
        try (TarArchiveInputStream tar
                = new TarArchiveInputStream(new GzipCompressorInputStream(new ByteArrayInputStream(bundle)))) {
            TarArchiveEntry entry;
            while ((entry = tar.getNextEntry()) != null) {
                if (!entry.isDirectory() && entry.getName().endsWith("policy.wasm")) {
                    return write("authz.wasm", tar.readAllBytes());
                }
            }
        }
        throw new IllegalStateException("opa build emitted no policy.wasm");
    }

    private static String write(String name, byte[] bundle) throws Exception {
        Path path = bundles.resolve(name);
        Files.write(path, bundle);
        return "file:" + path.toAbsolutePath();
    }

    private String wasm(String policyPath) {
        return "opa:" + policyPath + "?evaluationMode=wasm&policyBundle=" + authz;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // a rest-mode route registers a producer readiness check (positive control), a wasm-mode route
                // sharing the same policy path must not - the difference is exactly what the health-check test
                // asserts. These moved here from OpaWasmModeValidationTest when the committed authz.wasm went
                // away (CAMEL-24742): both routes have to start, so both need a bundle that actually loads.
                from("direct:rest").to("opa:authz/allow?serverUrl=http://opa-rest:8181");
                from("direct:wasm").to(wasm("authz/allow"));
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

    /**
     * In {@code wasm} mode the policy is evaluated in-process, so there is no OPA server to probe and no producer
     * health check is registered (CAMEL-24743).
     */
    @Test
    void registersTheCheckForTheRestRouteButNotTheWasmRoute() {
        List<HealthCheck> checks = producerChecks();
        // exactly one check, and it is the rest route's - the wasm route evaluates in-process with no server to probe
        assertThat(checks).hasSize(1);
        assertThat(checks.get(0).getId()).contains("opa-rest");
    }

    /**
     * {@code failOpen} still governs an evaluation failure (a busy pool, a bad bundle) in {@code wasm} mode, so it must
     * not be rejected (CAMEL-24743).
     */
    @Test
    void acceptsFailOpenInWasmMode() {
        assertThatCode(() -> context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:failopen").to(wasm("authz/allow") + "&failOpen=true");
            }
        })).doesNotThrowAnyException();
    }

    @Test
    void allowsWhenThePolicyMatches() {
        Exchange out = template.request(wasm("authz/allow"), e -> e.getMessage().setHeader("user", "alice"));

        assertThat(out.getException()).isNull();
        assertThat(out.getMessage().getHeader(OpaConstants.DECISION_ALLOW)).isEqualTo(true);
    }

    @Test
    void acceptsABareModuleAsWellAsTheBundleTarball() {
        // every other test here loads the tarball opa build emits, so without this the other half of loadPolicy -
        // a bare .wasm, which is what an operator extracting the module by hand would have - goes untested
        Exchange out = template.request(
                "opa:authz/allow?evaluationMode=wasm&policyBundle=" + module,
                e -> e.getMessage().setHeader("user", "alice"));

        assertThat(out.getException()).isNull();
        assertThat(out.getMessage().getHeader(OpaConstants.DECISION_ALLOW)).isEqualTo(true);
    }

    @Test
    void deniesWhenThePolicyDoesNotMatch() {
        Exchange out = template.request(wasm("authz/allow"), e -> e.getMessage().setHeader("user", "mallory"));

        assertThat(out.getException()).isNull();
        assertThat(out.getMessage().getHeader(OpaConstants.DECISION_ALLOW)).isEqualTo(false);
    }

    @Test
    void readsAVerdictOutOfADecisionObject() {
        Exchange out = template.request(wasm("authz/decision"), e -> e.getMessage().setHeader("user", "alice"));

        assertThat(out.getException()).isNull();
        assertThat(out.getMessage().getHeader(OpaConstants.DECISION_ALLOW)).isEqualTo(true);
        assertThat(out.getMessage().getHeader(OpaConstants.DECISION, Map.class)).containsEntry("allow", true);
    }

    @Test
    void keepsTheDenyReasonsJustLikeTheRestEngine() {
        // OpaIT.keepsTheDenyReasonsFromADecisionObject asserts exactly this against the server
        Exchange out = template.request(wasm("authz/decision"), e -> e.getMessage().setHeader("user", "mallory"));

        assertThat(out.getException()).isNull();
        assertThat(out.getMessage().getHeader(OpaConstants.DECISION_ALLOW)).isEqualTo(false);
        assertThat(out.getMessage().getHeader(OpaConstants.DECISION, Map.class))
                .containsEntry("reasons", List.of("not the owner"));
    }

    @Test
    void failsClosedOnAnUndefinedDecisionJustLikeTheRestEngine() {
        // authz/strict_allow has no default, so it is undefined for mallory. The WASM ABI reports that as an empty
        // result array where the REST client raises an error; OpaIT.failsClosedOnAnUndefinedDecision is the twin
        Exchange out = template.request(wasm("authz/strict_allow"), e -> e.getMessage().setHeader("user", "mallory"));

        assertThat(out.getException()).isInstanceOf(OpaPolicyEvaluationException.class);
        assertThat(out.getMessage().getHeader(OpaConstants.DECISION_ALLOW)).isNull();
    }

    @Test
    void keepsTheEntrypointAcrossPooledReuse() {
        // returning a borrowed instance resets it, putting the entrypoint back to 0 - so an instance configured
        // only where it was built answers the first exchange from authz/decision and every later one from
        // whatever rule is entrypoint 0. A single-instance pool and several messages is what shows it
        String decision = wasm("authz/decision") + "&poolSize=1";

        for (int i = 0; i < 5; i++) {
            Exchange out = template.request(decision, e -> e.getMessage().setHeader("user", "alice"));

            assertThat(out.getException()).as("exchange %d", i).isNull();
            assertThat(out.getMessage().getHeader(OpaConstants.DECISION))
                    .as("message %d was still decided by authz/decision", i)
                    .isInstanceOf(Map.class);
            // the type alone would pass for any rule returning an object; the content is what pins the entrypoint
            assertThat(out.getMessage().getHeader(OpaConstants.DECISION, Map.class)).containsEntry("allow", true);
        }
    }

    @Test
    void appliesTheDataDocumentPackedInTheBundle() {
        String policy = "opa:roles/allow?evaluationMode=wasm&policyBundle=" + roles + "&poolSize=1";

        for (int i = 0; i < 3; i++) {
            Exchange allowed = template.request(policy, e -> e.getMessage().setHeader("user", "carol"));
            Exchange denied = template.request(policy, e -> e.getMessage().setHeader("user", "alice"));

            assertThat(allowed.getException()).as("exchange %d", i).isNull();
            assertThat(allowed.getMessage().getHeader(OpaConstants.DECISION_ALLOW))
                    .as("data.admins was still visible on message %d", i)
                    .isEqualTo(true);
            assertThat(denied.getMessage().getHeader(OpaConstants.DECISION_ALLOW)).isEqualTo(false);
        }
    }

    @Test
    @Timeout(60)
    void keepsThePoolUsableAfterRepeatedEvaluationFailures() {
        String strict = wasm("authz/strict_allow") + "&poolSize=1";

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
                Exchange out = template.request(wasm("authz/allow"), e -> e.getMessage().setHeader("user", user));
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
