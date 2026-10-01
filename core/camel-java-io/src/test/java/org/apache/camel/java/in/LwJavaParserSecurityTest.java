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

import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

import org.apache.camel.model.ThrowExceptionDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * What the parser promises for a source nobody vetted: nothing of it runs, the DSL it can reach is pinned, and it ends
 * and never throws whatever it is given (CAMEL-25148).
 */
class LwJavaParserSecurityTest {

    private static final Path SURFACE = Path.of("src/test/resources/org/apache/camel/java/in/replay-builder-methods.txt");

    private static JavaParseResult parse(String source) {
        return new LwJavaParser().parse(source);
    }

    private static List<String> reasons(JavaParseResult result) {
        return result.unresolved().stream().map(JavaParseResult.Unresolved::reason).toList();
    }

    /**
     * The methods a source can call on the route builder and the static DSL. A new one fails this test until someone
     * has checked it does nothing but build the model (no context, no files, no class loading), then regenerated the
     * file with -Dcamel.java.in.updateSurface=true.
     */
    @Test
    void builderSurfaceIsPinned() throws Exception {
        String now = String.join("\n", ChainReplayer.builderSurface()) + "\n";
        if (Boolean.getBoolean("camel.java.in.updateSurface")) {
            Files.writeString(SURFACE, now, StandardCharsets.UTF_8);
        }
        assertThat(now).as("the DSL a parse can call changed: review the new methods, then regenerate %s", SURFACE)
                .isEqualTo(Files.readString(SURFACE, StandardCharsets.UTF_8));
        assertThat(now).doesNotContain(".setCamelContext(", ".setContext(", ".getContext(", ".configure(",
                ".addRoutesToCamelContext(", ".includeRoutes(", "CamelContext", ".populate", ".prepareModel(",
                ".initializeCamelContext(", ".bindToRegistry(", ".propertyInject(", ".endpoint(");
    }

    @Test
    void neverAContext() {
        JavaParseResult result = parse("""
                getContext().setTracing(true);
                setCamelContext(null);
                from("direct:a").to("mock:a");
                bindToRegistry("x", "y");
                """);
        assertThat(result.routes().getRoutes()).hasSize(1);
        assertThat(result.unresolved()).extracting(JavaParseResult.Unresolved::line).containsExactly(1, 2, 4);
        // what touches the context or the registry is not the DSL a parse calls
        assertThat(result.unresolved().get(0)).matches(JavaParseResult::configuresTheContext);
        assertThat(reasons(result).subList(1, 3)).allMatch(r -> r.startsWith("not a DSL method"));
    }

    @Test
    void onlyConstantsOfCamelAndSomeJdkClasses() {
        JavaParseResult result = parse("""
                from("direct:a")
                    .log(LoggingLevel.WARN, "x")
                    .setHeader("max", constant(Integer.MAX_VALUE))
                    .setHeader("out", constant(java.lang.System.out))
                    .setHeader("color", constant(java.awt.Color.RED))
                    .setHeader("file", constant(Exchange.FILE_NAME));
                """);
        assertThat(result.unresolved()).extracting(JavaParseResult.Unresolved::text)
                .containsExactly("java.lang.System.out", "java.awt.Color.RED");
    }

    @Test
    void onlyExceptionsAreCreatedAndOnlyFromMessages(@TempDir Path dir) {
        Path file = dir.resolve("created-by-a-parse");
        JavaParseResult result = parse("""
                from("direct:a").throwException(new java.io.UncheckedIOException("x", new java.io.IOException("y")));
                from("direct:b").throwException(new ArrayIndexOutOfBoundsException(5));
                from("direct:c").throwException(new java.io.FileOutputStream("%s"));
                """.formatted(file));
        ThrowExceptionDefinition created = (ThrowExceptionDefinition) result.routes().getRoutes().get(0).getOutputs().get(0);
        assertThat(created.getException()).isInstanceOf(UncheckedIOException.class);
        assertThat(result.unresolved()).extracting(JavaParseResult.Unresolved::line).containsExactly(2, 3);
        assertThat(file).as("nothing but exceptions is created").doesNotExist();
    }

    @Test
    void aBoundedNumberOfStubs() {
        StringBuilder sb = new StringBuilder("from(\"direct:a\")");
        for (int i = 0; i < StubClassLoader.MAX_STUBS + 50; i++) {
            sb.append(".onException(com.acme.E").append(i).append(".class).handled(true).end()");
        }
        sb.append(".to(\"mock:a\");");
        JavaParseResult result = parse(sb.toString());
        assertThat(result.routes().getRoutes()).hasSize(1);
        assertThat(reasons(result)).hasSize(50).allMatch(r -> r.contains("class name not usable"));
    }

    @Test
    void deepNestingAndHugeSources() {
        String deep = "from(\"direct:a\").to(" + "a(".repeat(20_000) + ")".repeat(20_001) + ";";
        JavaParseResult result = parse(deep);
        assertThat(result.routes().getRoutes()).hasSize(1);

        String huge = "from(\"direct:a\")" + ".to(\"mock:a\")".repeat(LwJavaParser.MAX_SOURCE_LENGTH / 10) + ";";
        assertThat(reasons(parse(huge))).singleElement().asString().contains("is not read");
        assertThat(reasons(parse(null))).hasSize(1);
    }

    /** Broken sources (cut anywhere, tokens dropped): the parser ends quickly and does not throw. */
    @Test
    void fuzzedSourcesEndAndNeverThrow() throws Exception {
        List<String> seeds = new ArrayList<>();
        try (Stream<Path> s = Files.walk(Path.of("src/test/java/org/apache/camel/java"))) {
            for (Path p : s.filter(p -> p.toString().endsWith(".java")).toList()) {
                seeds.add(Files.readString(p));
            }
        }
        Random random = new Random(25148);
        for (int i = 0; i < 400; i++) {
            String seed = seeds.get(random.nextInt(seeds.size()));
            String mutated = switch (i % 4) {
                case 0 -> seed.substring(0, random.nextInt(seed.length()));
                case 1 -> drop(seed, random, "()");
                case 2 -> drop(seed, random, "{};\"");
                default -> seed.substring(random.nextInt(seed.length()));
            };
            assertTimeoutPreemptively(Duration.ofSeconds(5), () -> parse(mutated), () -> "mutation " + mutated);
        }
    }

    /** The source with a tenth of the given characters removed. */
    private static String drop(String s, Random random, String chars) {
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            if (chars.indexOf(c) < 0 || random.nextInt(10) != 0) {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
