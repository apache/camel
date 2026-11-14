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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.styra.opa.wasm.OpaPolicy;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Real WASM coverage for immutable per-instance data and input heap isolation, with no committed module binary. */
@Timeout(60)
class OpaWasmStaticDataIT {

    private static byte[] wasm;
    private static String data;

    @BeforeAll
    static void compilePolicy() throws Exception {
        String module = System.getProperty("camel.opa.staticDataModule");
        if (module != null) {
            // Explicit local compiler output also permits running these checks without Docker.
            wasm = Files.readAllBytes(Path.of(module));
        } else {
            wasm = OpaWasmStaticDataBenchmark.compileModule();
        }
        data = OpaWasmStaticDataBenchmark.data(1000);
    }

    private OpaWasmEvaluator evaluator(String entrypoint, int size) {
        return new OpaWasmEvaluator(
                wasm, data, entrypoint, size, 10000,
                entrypoint, "allow", "*", "", false, false);
    }

    private static Map<String, Object> input(int user, boolean allow) {
        return Map.of("subject", "user" + user, "resource", allow ? "document" + user : "other",
                "action", "read", "trap", false);
    }

    @Test
    void alternatesAllowDenyAndInputSizesWithoutLeakingState() throws Exception {
        try (var evaluator = evaluator("benchmark/allow", 1)) {
            for (int i = 0; i < 1000; i++) {
                boolean allow = i % 2 == 0;
                var input = new java.util.HashMap<>(input(i % 1000, allow));
                input.put("padding", "x".repeat(i % 2 == 0 ? 4096 : 1));
                assertThat(evaluator.evaluateDecision(input)).as("iteration %d", i).isEqualTo(allow);
            }
        }
    }

    @Test
    void preservesStaticDataAcrossConcurrentReuseAndPoolContention() throws Exception {
        int threads = 8;
        var executor = Executors.newFixedThreadPool(threads);
        var ready = new CountDownLatch(threads);
        var start = new CountDownLatch(1);
        try (var evaluator = evaluator("benchmark/allow", 4)) {
            var tasks = new ArrayList<java.util.concurrent.Future<Void>>();
            for (int worker = 0; worker < threads; worker++) {
                int user = worker;
                tasks.add(executor.submit((Callable<Void>) () -> {
                    ready.countDown();
                    assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                    for (int i = 0; i < 200; i++) {
                        boolean allow = i % 2 == 0;
                        assertThat(evaluator.evaluateDecision(input(user, allow))).isEqualTo(allow);
                    }
                    return null;
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (var task : tasks) {
                task.get(30, TimeUnit.SECONDS);
            }
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void realRuntimeFailuresAreFollowedByCorrectDecisionsFromPreparedReplacements() throws Exception {
        try (var evaluator = evaluator("benchmark/strict", 1)) {
            for (int i = 0; i < 10; i++) {
                assertThatThrownBy(() -> evaluator.evaluateDecision(Map.of("trap", true)))
                        .isInstanceOf(RuntimeException.class);
                assertThat(evaluator.evaluateDecision(input(i, true))).isEqualTo(true);
                assertThat(evaluator.evaluateDecision(input(i, false))).isEqualTo(false);
            }
        }
    }

    @Test
    void requestScopedDataOverrideDoesNotChangeTheFollowingRequestsStaticData() throws Exception {
        try (var evaluator = evaluator("benchmark/isolation", 1)) {
            var overridden = new java.util.HashMap<>(input(0, true));
            overridden.put("resource", "overridden-document");
            overridden.put("override", Map.of("user0", Map.of("resource", "overridden-document", "enabled", true)));
            for (int i = 0; i < 100; i++) {
                assertThat(evaluator.evaluateDecision(overridden)).isEqualTo(true);
                assertThat(evaluator.evaluateDecision(input(0, true))).isEqualTo(true);
                var noOverride = new java.util.HashMap<>(input(0, true));
                noOverride.put("resource", "overridden-document");
                assertThat(evaluator.evaluateDecision(noOverride)).isEqualTo(false);
            }
        }
    }

    @Test
    void discardedRealInstanceIsReplacedAndPreparedWithOriginalData() throws Exception {
        var created = new AtomicInteger();
        try (var pool = new OpaWasmPolicyPool(() -> {
            created.incrementAndGet();
            return OpaPolicy.builder().withPolicy(wasm).build().entrypoint("benchmark/strict").data(data);
        }, 1, 1000)) {
            var broken = pool.borrow();
            assertThatThrownBy(() -> broken.policy().evaluate("{\"trap\":true}"))
                    .isInstanceOf(RuntimeException.class);
            broken.discard();
            try (var replacement = pool.borrow()) {
                assertThat(replacement.policy()).isNotSameAs(broken.policy());
                assertThat(replacement.policy()
                        .evaluate("{\"subject\":\"user0\",\"resource\":\"document0\",\"action\":\"read\",\"trap\":false}"))
                        .isEqualTo("[{\"result\":true}]");
            }
            assertThat(created.get()).isEqualTo(2);
        }
    }

    @Test
    void validatesEntrypointAtConstructionAndAcceptsBareModuleWithoutBundledData() throws Exception {
        assertThatThrownBy(() -> evaluator("benchmark/missing", 1)).isInstanceOf(IllegalArgumentException.class);
        try (var evaluator = new OpaWasmEvaluator(
                wasm, null, "benchmark/strict", 1, 1000,
                "benchmark/strict", "allow", "*", "", false, false)) {
            for (int i = 0; i < 100; i++) {
                assertThat(evaluator.evaluateDecision(input(i, true))).isEqualTo(false);
            }
        }
    }
}
