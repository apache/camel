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
package org.apache.camel.test.dst;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider;

/**
 * The JUnit extension behind {@link DstTest}: one invocation per seed, failure reports with the seed and the trace,
 * replay of single seeds, a summary per test method and the escape point report per test class.
 */
public class DstExtension implements TestTemplateInvocationContextProvider, BeforeAllCallback, AfterAllCallback {

    private static final ExtensionContext.Namespace NS = ExtensionContext.Namespace.create(DstExtension.class);
    private static final int TRACE_TAIL = 40;

    static final class Stats {
        final String name;
        final boolean replay;
        long runs;
        long failures;
        long firstFailing = -1;
        long nanos;
        final List<String> replayHashes = new ArrayList<>();
        final List<String> failing = new ArrayList<>();

        Stats(String name, boolean replay) {
            this.name = name;
            this.replay = replay;
        }

        String summary() {
            double seconds = nanos / 1e9;
            return String.format(
                    "DST summary %s: %d seeds run%s, %d failing, first failing seed %s, total %.2f s, %.2f ms/seed",
                    name, runs, replay ? " (replay)" : "", failures, firstFailing < 0 ? "-" : firstFailing, seconds,
                    runs == 0 ? 0.0 : seconds * 1000 / runs);
        }
    }

    static Path traceDir() {
        return Paths.get(System.getProperty("dst.traceDir", "target/dst"));
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        ExtensionContext.Store store = context.getStore(NS);
        store.put("threads", DstEscapes.liveThreads());
        store.put("stats", new ArrayList<Stats>());
        DstInstrument instrument = context.getRequiredTestClass().getAnnotation(DstInstrument.class);
        if (instrument == null) {
            return;
        }
        DstInstrumentation.install(instrument.value());
        StringBuilder sb = new StringBuilder();
        for (String p : instrument.value()) {
            List<String> found = DstInstrumentation.scan(p);
            sb.append("DST escape point scan of ").append(p).append(" (").append(found.size()).append(" call sites)\n");
            found.forEach(line -> sb.append("  ").append(line).append('\n'));
        }
        System.out.print(sb);
        write(traceDir().resolve(context.getRequiredTestClass().getSimpleName() + ".escapes.txt"), sb.toString());
    }

    @Override
    @SuppressWarnings("unchecked")
    public void afterAll(ExtensionContext context) {
        ExtensionContext.Store store = context.getStore(NS);
        StringBuilder sb = new StringBuilder();
        for (Stats s : (List<Stats>) store.get("stats", List.class)) {
            sb.append(s.summary()).append('\n');
            for (String h : s.replayHashes) {
                sb.append("  ").append(h).append('\n');
            }
            for (String f : s.failing) {
                sb.append("  failing ").append(f).append('\n');
            }
        }
        Set<String> before = (Set<String>) store.get("threads", Set.class);
        Set<String> started = new TreeSet<>(DstEscapes.liveThreads());
        started.removeAll(before);
        started.removeIf(n -> n.startsWith("surefire") || n.startsWith("junit") || n.startsWith("Attach Listener")
                || n.startsWith("Common-Cleaner") || n.startsWith("process reaper"));
        sb.append("DST threads started during the runs and still alive: ").append(started).append('\n');
        sb.append("DST runtime escapes: ").append(DstEscapes.runtime().size()).append('\n');
        DstEscapes.runtime().forEach(e -> sb.append("  ").append(e).append('\n'));
        sb.append("DST escape points in loaded classes of the instrumented packages:\n");
        DstEscapes.loaded().forEach(e -> sb.append("  ").append(e).append('\n'));
        System.out.print(sb);
        write(traceDir().resolve(context.getRequiredTestClass().getSimpleName() + ".summary.txt"), sb.toString());
    }

    @Override
    public boolean supportsTestTemplate(ExtensionContext context) {
        return context.getTestMethod().map(m -> m.isAnnotationPresent(DstTest.class)).orElse(false);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Stream<TestTemplateInvocationContext> provideTestTemplateInvocationContexts(ExtensionContext context) {
        Method method = context.getRequiredTestMethod();
        DstTest test = method.getAnnotation(DstTest.class);
        String name = context.getRequiredTestClass().getSimpleName() + "." + method.getName();

        long[] replay = null;
        String prop = System.getProperty("dst.seed");
        if (prop != null && !prop.isBlank()) {
            replay = Arrays.stream(prop.split(",")).map(String::trim).mapToLong(Long::parseLong).toArray();
        } else if (method.isAnnotationPresent(DstSeed.class)) {
            replay = method.getAnnotation(DstSeed.class).value();
        }
        Stats stats = new Stats(name, replay != null);
        List<Stats> all = (List<Stats>) context.getParent().orElseThrow().getStore(NS).get("stats", List.class);
        if (all != null) {
            all.add(stats);
        }
        long maxSteps = test.maxSteps();
        if (replay != null) {
            return Arrays.stream(replay).mapToObj(seed -> invocation(name, seed, maxSteps, true, stats));
        }
        int seeds = Integer.getInteger("dst.seeds", test.seeds());
        long first = Long.getLong("dst.firstSeed", test.firstSeed());
        int maxFailures = Integer.getInteger("dst.maxFailures", test.maxFailures());
        return LongStream.range(0, seeds).mapToObj(i -> first + i)
                .takeWhile(seed -> stats.failures < maxFailures)
                .map(seed -> invocation(name, seed, maxSteps, false, stats));
    }

    private static TestTemplateInvocationContext invocation(
            String name, long seed, long maxSteps, boolean replay, Stats stats) {
        SeedRun run = new SeedRun(name, seed, maxSteps, replay, stats);
        return new TestTemplateInvocationContext() {
            @Override
            public String getDisplayName(int invocationIndex) {
                return "seed " + seed;
            }

            @Override
            public List<Extension> getAdditionalExtensions() {
                return List.of(run);
            }
        };
    }

    static final class SeedRun
            implements ParameterResolver, BeforeTestExecutionCallback, AfterTestExecutionCallback,
            TestExecutionExceptionHandler {
        private final String name;
        private final long seed;
        private final long maxSteps;
        private final boolean replay;
        private final Stats stats;
        private Dst dst;
        private long start;
        private boolean failed;
        private Path written;

        SeedRun(String name, long seed, long maxSteps, boolean replay, Stats stats) {
            this.name = name;
            this.seed = seed;
            this.maxSteps = maxSteps;
            this.replay = replay;
            this.stats = stats;
        }

        @Override
        public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
            return parameterContext.getParameter().getType() == Dst.class;
        }

        @Override
        public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
            if (dst == null) {
                dst = new Dst(seed, maxSteps);
                dst.trace("seed " + seed);
            }
            return dst;
        }

        @Override
        public void beforeTestExecution(ExtensionContext context) {
            start = System.nanoTime();
        }

        @Override
        public void handleTestExecutionException(ExtensionContext context, Throwable throwable) throws Throwable {
            failed = true;
            stats.failures++;
            if (stats.firstFailing < 0) {
                stats.firstFailing = seed;
            }
            String first = String.valueOf(throwable.getMessage());
            stats.failing.add("seed " + seed + ": " + (first.length() > 400 ? first.substring(0, 400) + "..." : first));
            if (dst == null) {
                throw throwable;
            }
            dst.trace("FAILED " + throwable);
            written = writeTrace("FAILED " + throwable);
            List<String> lines = dst.traceLines();
            StringBuilder sb = new StringBuilder();
            sb.append("DST seed ").append(seed).append(" of ").append(name).append(" failed: ").append(throwable.getMessage())
                    .append("\nReplay: @DstSeed(").append(seed).append(") or -Ddst.seed=").append(seed)
                    .append("\nTrace hash ").append(dst.traceHash()).append(", ").append(lines.size())
                    .append(" lines, full trace: ").append(written)
                    .append("\nLast ").append(Math.min(TRACE_TAIL, lines.size())).append(" lines:\n");
            for (String line : lines.subList(Math.max(0, lines.size() - TRACE_TAIL), lines.size())) {
                sb.append("  ").append(line).append('\n');
            }
            AssertionError error = new AssertionError(sb.toString(), throwable);
            throw error;
        }

        @Override
        public void afterTestExecution(ExtensionContext context) {
            stats.runs++;
            stats.nanos += System.nanoTime() - start;
            if (dst != null) {
                if (replay) {
                    if (written == null) {
                        written = writeTrace(failed ? "FAILED" : "PASSED");
                    }
                    String line = String.format("seed %d %s trace %s (%d lines) -> %s", seed, failed ? "FAILED" : "PASSED",
                            dst.traceHash(), dst.traceLines().size(), written);
                    stats.replayHashes.add(line);
                    System.out.println("DST replay " + line);
                }
                dst.close();
            }
        }

        private Path writeTrace(String result) {
            Path file = traceDir().resolve(name).resolve("seed-" + seed + ".trace");
            StringBuilder sb = new StringBuilder();
            sb.append("# ").append(name).append(" seed ").append(seed).append(' ').append(result).append('\n');
            sb.append("# trace hash ").append(dst.traceHash()).append(dst.isTraceTruncated() ? " (truncated)" : "")
                    .append('\n');
            for (String line : dst.traceLines()) {
                sb.append(line).append('\n');
            }
            write(file, sb.toString());
            return file;
        }
    }

    static void write(Path file, String content) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
