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

import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.styra.opa.wasm.OpaPolicy;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.infra.opa.services.OpaWasmBundleBuilder;

/**
 * Opt-in executable smoke benchmark of the actual Camel pool and managed SDK. Not a unit test or a JMH substitute.
 * Compile permissions.rego with the pinned test-infra OPA compiler first; no precompiled WASM is checked in. Arguments:
 * bare-module-path-or---compile [entries=1000] [threads=1] [warmup-ms=1000] [measurement-ms=2000].
 */
public final class OpaWasmStaticDataBenchmark {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String ALLOW = "{\"subject\":\"user0\",\"resource\":\"document0\",\"action\":\"read\"}";
    private static final String DENY = "{\"subject\":\"user0\",\"resource\":\"other\",\"action\":\"read\"}";

    private OpaWasmStaticDataBenchmark() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            throw new IllegalArgumentException("Expected bare WASM module path; see design/opa-wasm-static-data.adoc");
        }
        byte[] wasm = "--compile".equals(args[0]) ? compileModule() : Files.readAllBytes(Path.of(args[0]));
        int entries = args.length > 1 ? Integer.parseInt(args[1]) : 1000;
        int threads = args.length > 2 ? Integer.parseInt(args[2]) : 1;
        long warmup = args.length > 3 ? Long.parseLong(args[3]) : 1000;
        long measurement = args.length > 4 ? Long.parseLong(args[4]) : 2000;
        if (entries < 1 || threads < 1 || warmup < 1 || measurement < 1) {
            throw new IllegalArgumentException("All numeric arguments must be positive");
        }
        String data = data(entries);
        System.out.printf("java=%s vm=%s arch=%s dataEntries=%d dataBytes=%d threads=%d warmupMs=%d measurementMs=%d%n",
                System.getProperty("java.version"), System.getProperty("java.vm.name"), System.getProperty("os.arch"),
                entries, data.getBytes(java.nio.charset.StandardCharsets.UTF_8).length, threads, warmup, measurement);
        System.out.println(
                "mode,operations,operationsPerSecond,allocatedBytesPerOperation,createdInstances,meanFirstEvaluationMs,checksum");
        run(wasm, data, threads, warmup, measurement, false);
        run(wasm, data, threads, warmup, measurement, true);
    }

    static byte[] compileModule() throws Exception {
        String policy;
        try (var in = OpaWasmStaticDataBenchmark.class.getResourceAsStream("/wasm-benchmark/permissions.rego")) {
            if (in == null) {
                throw new IllegalStateException("Missing permissions.rego");
            }
            policy = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        byte[] bundle = OpaWasmBundleBuilder.build("permissions.rego", policy, "benchmark/allow", "benchmark/strict",
                "benchmark/isolation");
        Path temporary = Files.createTempFile("camel-opa-static-data", ".tar.gz");
        try (var context = new DefaultCamelContext()) {
            Files.write(temporary, bundle);
            return OpaWasmEvaluator.loadPolicy(context, temporary.toUri().toString()).wasm();
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    static String data(int entries) throws Exception {
        var grants = MAPPER.createObjectNode();
        for (int i = 0; i < entries; i++) {
            grants.putObject("user" + i).put("resource", "document" + i).put("enabled", true)
                    .put("tenant", "tenant" + (i % 100)).put("description", "static authorization grant");
        }
        return MAPPER.writeValueAsString(MAPPER.createObjectNode().set("permissions", grants));
    }

    private static void run(byte[] wasm, String data, int threads, long warmup, long measurement, boolean prepareOnce)
            throws Exception {
        var created = new java.util.concurrent.atomic.AtomicInteger();
        var firstEvaluationNanos = new java.util.concurrent.atomic.AtomicLong();
        var allocations = ManagementFactory.getThreadMXBean() instanceof com.sun.management.ThreadMXBean bean
                && bean.isThreadAllocatedMemorySupported() ? bean : null;
        if (allocations != null && !allocations.isThreadAllocatedMemoryEnabled()) {
            allocations.setThreadAllocatedMemoryEnabled(true);
        }
        try (var pool = new OpaWasmPolicyPool(() -> {
            created.incrementAndGet();
            OpaPolicy policy = OpaPolicy.builder().withPolicy(wasm).build();
            if (prepareOnce) {
                prepare(policy, data);
            }
            return policy;
        }, threads, 30000)) {
            var executor = Executors.newFixedThreadPool(threads);
            var ready = new CountDownLatch(threads);
            var start = new CountDownLatch(1);
            var tasks = new ArrayList<java.util.concurrent.Future<Sample>>();
            try {
                for (int worker = 0; worker < threads; worker++) {
                    tasks.add(executor.submit((Callable<Sample>) () -> {
                        long count = 0;
                        try {
                            // Exclude lazy module compilation and initial data loading from the warmup clock as well.
                            long firstEvaluationStart = System.nanoTime();
                            evaluate(pool, data, prepareOnce, 0);
                            firstEvaluationNanos.addAndGet(System.nanoTime() - firstEvaluationStart);
                            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(warmup);
                            do {
                                evaluate(pool, data, prepareOnce, count++);
                            } while (System.nanoTime() < deadline);
                        } finally {
                            ready.countDown();
                        }
                        if (!start.await(30, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("Timed out waiting for benchmark start");
                        }
                        long allocatedBefore = allocated(allocations);
                        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(measurement);
                        count = 0;
                        long checksum = 0;
                        do {
                            checksum += evaluate(pool, data, prepareOnce, count++);
                        } while (System.nanoTime() < deadline);
                        return new Sample(count, allocated(allocations) - allocatedBefore, checksum);
                    }));
                }
                if (!ready.await(60, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Benchmark warmup failed or timed out");
                }
                long begin = System.nanoTime();
                start.countDown();
                long operations = 0;
                long bytes = 0;
                long checksum = 0;
                for (var task : tasks) {
                    Sample sample = task.get(measurement + 30000, TimeUnit.MILLISECONDS);
                    operations += sample.operations;
                    bytes += sample.allocated;
                    checksum += sample.checksum;
                }
                double seconds = (System.nanoTime() - begin) / 1_000_000_000.0;
                System.out.printf(Locale.ROOT, "%s,%d,%.2f,%.2f,%d,%.2f,%d%n", prepareOnce ? "prepare-once" : "per-borrow",
                        operations, operations / seconds, allocations != null ? (double) bytes / operations : -1.0,
                        created.get(), firstEvaluationNanos.get() / (threads * 1_000_000.0), checksum);
            } finally {
                start.countDown();
                executor.shutdownNow();
            }
        }
    }

    private static long allocated(com.sun.management.ThreadMXBean bean) {
        return bean != null ? bean.getCurrentThreadAllocatedBytes() : 0;
    }

    private static int evaluate(OpaWasmPolicyPool pool, String data, boolean prepareOnce, long sequence) throws Exception {
        var lease = pool.borrow();
        try {
            if (!prepareOnce) {
                prepare(lease.policy(), data);
            }
            boolean allow = sequence % 2 == 0;
            String result = lease.policy().evaluate(allow ? ALLOW : DENY);
            if (!result.equals(allow ? "[{\"result\":true}]" : "[{\"result\":false}]")) {
                throw new IllegalStateException("Unexpected decision or cross-request state: " + result);
            }
            lease.close();
            return result.hashCode();
        } catch (Exception e) {
            lease.discard();
            throw e;
        }
    }

    private static void prepare(OpaPolicy policy, String data) {
        policy.entrypoint("benchmark/allow");
        policy.data(data);
    }

    private record Sample(long operations, long allocated, long checksum) {
    }
}
