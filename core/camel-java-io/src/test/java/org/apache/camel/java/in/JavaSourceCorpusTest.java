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
package org.apache.camel.java.in;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Hand-written Java routes: the RouteBuilders of camel-core's tests. There is no model to compare with, so it measures
 * how many sources read completely and sums up what was not understood; a source must never break the parser.
 */
class JavaSourceCorpusTest {

    private static final Path CORE_TESTS = Path.of("../camel-core/src/test/java");

    private static final Path ENDPOINT_DSL_TESTS = Path.of("../../dsl/camel-endpointdsl/src/test/java");

    @Test
    void camelCoreTests() throws Exception {
        assumeTrue(Files.isDirectory(CORE_TESTS), "camel-core is in the source tree");
        report(CORE_TESTS, "target/java-corpus-report.txt");
    }

    /** The endpoint DSL's own tests: routes that use it, read without it on the class path. */
    @Test
    void endpointDslTests() throws Exception {
        assumeTrue(Files.isDirectory(ENDPOINT_DSL_TESTS), "camel-endpointdsl is in the source tree");
        report(ENDPOINT_DSL_TESTS, "target/java-corpus-endpointdsl-report.txt");
    }

    /**
     * Any Java sources, such as a checkout of the Camel example projects: run with
     * {@code -Dcamel.java.in.corpus=../camel-examples,../camel-spring-boot-examples}; skipped otherwise.
     */
    @Test
    void externalCorpus() throws Exception {
        String dirs = System.getProperty("camel.java.in.corpus");
        assumeTrue(dirs != null && !dirs.isBlank(), "-Dcamel.java.in.corpus=dir,dir to read other sources");
        int i = 0;
        for (String dir : dirs.split(",")) {
            Path corpus = Path.of(dir.strip());
            if (Files.isDirectory(corpus)) {
                report(corpus, "target/java-corpus-external-" + (i++) + "-" + corpus.getFileName() + ".txt");
            }
        }
    }

    private static void report(Path corpus, String reportFile) throws Exception {
        List<Path> files;
        try (Stream<Path> s = Files.walk(corpus)) {
            files = s.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        }
        int sources = 0;
        int complete = 0;
        int routes = 0;
        Map<String, Integer> reasons = new TreeMap<>();
        Map<String, String> examples = new TreeMap<>();
        long start = System.nanoTime();
        for (Path file : files) {
            String java = Files.readString(file);
            if (!java.contains("configure()") || !java.contains("from(")) {
                continue;
            }
            sources++;
            JavaParseResult result = new LwJavaParser().parse(java);
            routes += result.routes().getRoutes().size();
            if (result.isComplete()) {
                complete++;
            }
            for (JavaParseResult.Unresolved u : result.unresolved()) {
                String key = u.reason().replaceAll(" on \\w+$", " on ...");
                if (key.startsWith("not a DSL method") || key.startsWith("arguments the DSL")
                        || key.startsWith("no such DSL method")) {
                    // by method: which ones to look at
                    key = key + ": " + u.text().replaceAll("\\(.*", "").replaceAll(".*\\.", "");
                }
                reasons.merge(key, 1, Integer::sum);
                examples.putIfAbsent(key, corpus.relativize(file) + ":" + u.line() + "  " + u.text());
            }
        }
        long ms = (System.nanoTime() - start) / 1_000_000;
        StringBuilder report = new StringBuilder(
                String.format(
                        "%d of %d sources read completely, %d routes, in %d ms%n", complete, sources, routes, ms));
        reasons.entrySet().stream().sorted(Map.Entry.<String, Integer> comparingByValue().reversed())
                .forEach(e -> report.append(String.format("%6d  %s%n        e.g. %s%n", e.getValue(), e.getKey(),
                        examples.get(e.getKey()))));
        Files.writeString(Path.of(reportFile), report);
        assertThat(sources).isGreaterThan(10);
    }
}
