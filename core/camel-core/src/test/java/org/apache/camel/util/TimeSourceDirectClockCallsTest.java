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
package org.apache.camel.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The core classes that read the clock and sleep through {@link TimeSource} (CAMEL-25911) must not call the system
 * clock directly, or a deterministic simulation test (camel-test-dst) would not run that wait in virtual time. The
 * check is a textual scan of their sources, as Camel does not use the forbidden-apis plugin: it finds the usual forms
 * of these calls, it is not a parser. camel-chaos is not scanned: nothing was moved there, and the waits of its
 * assertions run in real time on purpose.
 */
class TimeSourceDirectClockCallsTest {

    /**
     * The classes that read the clock and sleep through {@link TimeSource}, by source file relative to the core
     * directory. To add a class: move its clock reads and sleeps to {@link TimeSource}, then add its source file here
     * (and to the list of core classes in test-dst.adoc of camel-test-dst).
     */
    static final List<String> CLASSES = List.of(
            // camel-util
            "camel-util/src/main/java/org/apache/camel/util/TimeSource.java",
            "camel-util/src/main/java/org/apache/camel/util/StopWatch.java",
            "camel-util/src/main/java/org/apache/camel/util/backoff/BackOffTimerTask.java",
            // camel-support
            "camel-support/src/main/java/org/apache/camel/support/DefaultTimeoutMap.java",
            "camel-support/src/main/java/org/apache/camel/support/MonotonicClock.java",
            "camel-support/src/main/java/org/apache/camel/support/SendHelper.java",
            "camel-support/src/main/java/org/apache/camel/support/StepHelper.java",
            "camel-support/src/main/java/org/apache/camel/support/startup/DefaultStartupConditionStrategy.java",
            "camel-support/src/main/java/org/apache/camel/support/task/AbstractTask.java",
            "camel-support/src/main/java/org/apache/camel/support/task/BackgroundTask.java",
            "camel-support/src/main/java/org/apache/camel/support/task/ForegroundTask.java",
            "camel-support/src/main/java/org/apache/camel/support/task/budget/IterationBoundedBudget.java",
            "camel-support/src/main/java/org/apache/camel/support/task/budget/TimeBoundedBudget.java",
            "camel-support/src/main/java/org/apache/camel/throttling/ThrottlingExceptionRoutePolicy.java",
            // camel-core-processor
            "camel-core-processor/src/main/java/org/apache/camel/processor/ConcurrentRequestsThrottler.java",
            "camel-core-processor/src/main/java/org/apache/camel/processor/DelayProcessorSupport.java",
            "camel-core-processor/src/main/java/org/apache/camel/processor/MulticastProcessor.java",
            "camel-core-processor/src/main/java/org/apache/camel/processor/SamplingThrottler.java",
            "camel-core-processor/src/main/java/org/apache/camel/processor/TotalRequestsThrottler.java",
            "camel-core-processor/src/main/java/org/apache/camel/processor/aggregate/OptimisticLockRetryPolicy.java",
            "camel-core-processor/src/main/java/org/apache/camel/processor/errorhandler/RedeliveryErrorHandler.java",
            "camel-core-processor/src/main/java/org/apache/camel/processor/errorhandler/RedeliveryPolicy.java",
            // camel-base-engine
            "camel-base-engine/src/main/java/org/apache/camel/impl/engine/DefaultShutdownStrategy.java");

    /** The core modules of the classes in {@link #CLASSES}. */
    static final List<String> MODULES
            = List.of("camel-util", "camel-support", "camel-core-processor", "camel-base-engine");

    /**
     * The nested classes allowed to call the system clock, by source file: only the default provider of
     * {@link TimeSource}.
     */
    static final Map<String, String> ALLOWED = Map.of(
            "camel-util/src/main/java/org/apache/camel/util/TimeSource.java", "SystemProvider");

    /** A forbidden call and what to use instead. */
    private record Rule(Pattern pattern, String call, String instead) {
    }

    private static final List<Rule> RULES = List.of(
            rule("\\bSystem\\s*\\.\\s*nanoTime\\s*\\(", "System.nanoTime()", "TimeSource.nanoTime()"),
            rule("\\bSystem\\s*\\.\\s*currentTimeMillis\\s*\\(", "System.currentTimeMillis()",
                    "TimeSource.currentTimeMillis()"),
            rule("\\bThread\\s*\\.\\s*sleep\\s*\\(", "Thread.sleep", "TimeSource.sleep(millis)"),
            rule("\\bSystem\\s*::\\s*nanoTime\\b", "System::nanoTime", "TimeSource::nanoTime"),
            rule("\\bSystem\\s*::\\s*currentTimeMillis\\b", "System::currentTimeMillis",
                    "TimeSource::currentTimeMillis"),
            rule("\\bThread\\s*::\\s*sleep\\b", "Thread::sleep", "TimeSource::sleep"),
            rule("\\bInstant\\s*::\\s*now\\b", "Instant::now", "TimeSource::instant"),
            rule("\\bInstant\\s*\\.\\s*now\\s*\\(", "Instant.now()", "TimeSource.instant()"),
            rule("\\bTimeUnit\\s*\\.\\s*[A-Z]+\\s*\\.\\s*sleep\\s*\\(", "TimeUnit.<unit>.sleep(...)",
                    "TimeSource.sleep(unit.toMillis(duration))"),
            rule("\\bimport\\s+static\\s+java\\.lang\\.(System\\s*\\.\\s*(nanoTime|currentTimeMillis)"
                 + "|Thread\\s*\\.\\s*sleep)\\b",
                    "a static import of the system clock or sleep", "the TimeSource methods"));

    private static Rule rule(String regex, String call, String instead) {
        return new Rule(Pattern.compile(regex), call, instead);
    }

    /** The core directory: the parent of the camel-core module. */
    private static Path coreDir() {
        return Paths.get(System.getProperty("basedir", ".")).toAbsolutePath().normalize().getParent();
    }

    @Test
    void theClassesReadTheClockOnlyThroughTimeSource() throws IOException {
        Path core = coreDir();
        List<String> missing = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        for (String file : CLASSES) {
            Path path = core.resolve(file);
            if (!Files.isRegularFile(path)) {
                missing.add(file);
                continue;
            }
            problems.addAll(scan(file, Files.readString(path, StandardCharsets.UTF_8), ALLOWED.get(file)));
        }
        if (!missing.isEmpty()) {
            fail("These sources are not found under " + core + " (a class was moved or renamed: update the list in "
                 + TimeSourceDirectClockCallsTest.class.getSimpleName() + "):\n  " + String.join("\n  ", missing));
        }
        if (!problems.isEmpty()) {
            fail("These classes read the clock and sleep through org.apache.camel.util.TimeSource (so a deterministic"
                 + " simulation test runs them in virtual time), but call the system clock directly:\n  "
                 + String.join("\n  ", problems));
        }
    }

    @Test
    void everyClassOfTheCoreModulesThatUsesTimeSourceIsInTheList() throws IOException {
        Path core = coreDir();
        Pattern use = Pattern.compile("\\bTimeSource\\s*(\\.|::)");
        List<String> unlisted = new ArrayList<>();
        for (String module : MODULES) {
            Path root = core.resolve(module).resolve("src/main/java");
            assertTrue(Files.isDirectory(root), root + " is not found");
            try (Stream<Path> files = Files.walk(root)) {
                for (Path path : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                    String file = core.relativize(path).toString().replace('\\', '/');
                    String code = blankCommentsAndLiterals(Files.readString(path, StandardCharsets.UTF_8));
                    if (!CLASSES.contains(file) && use.matcher(code).find()) {
                        unlisted.add(file);
                    }
                }
            }
        }
        if (!unlisted.isEmpty()) {
            fail("These classes use org.apache.camel.util.TimeSource but are not in the list of "
                 + TimeSourceDirectClockCallsTest.class.getSimpleName() + ": add them, so their direct clock calls are"
                 + " checked too:\n  " + String.join("\n  ", unlisted));
        }
    }

    @Test
    void theScanFindsDirectCallsOnly() {
        String source = """
                class Foo {
                    // System.nanoTime() in a comment
                    /* System.currentTimeMillis() in a block comment */
                    String s = "System.nanoTime() in a string, http://x";
                    String t = \"""
                        Instant.now() in a text block \\\\\""";
                    char u = '\\\\';
                    String v = "\\\\";
                    long a = System.nanoTime();
                    void b() throws Exception {
                        Thread.sleep (5);
                        TimeUnit.SECONDS.sleep(1);
                    }
                    long c = java.lang.System.currentTimeMillis();
                    Object d = Instant.now();
                    LongSupplier e = System::nanoTime;
                    long f = TimeSource.nanoTime();
                    static final class SystemProvider {
                        long nanoTime() {
                            return System.nanoTime();
                        }
                    }
                }
                """;

        assertEquals(List.of(
                "Foo.java:9: System.nanoTime(), use TimeSource.nanoTime() instead",
                "Foo.java:11: Thread.sleep, use TimeSource.sleep(millis) instead",
                "Foo.java:12: TimeUnit.<unit>.sleep(...), use TimeSource.sleep(unit.toMillis(duration)) instead",
                "Foo.java:14: System.currentTimeMillis(), use TimeSource.currentTimeMillis() instead",
                "Foo.java:15: Instant.now(), use TimeSource.instant() instead",
                "Foo.java:16: System::nanoTime, use TimeSource::nanoTime instead"),
                scan("Foo.java", source, "SystemProvider"));
        assertEquals(7, scan("Foo.java", source, null).size());
        assertThrows(AssertionFailedError.class, () -> scan("Foo.java", "class Foo { /* no end", null));
        assertThrows(AssertionFailedError.class, () -> scan("Foo.java", "class Foo { String s = \"no end\n}", null));
    }

    /**
     * The direct clock calls in the source, as {@code file:line: call, use ... instead}, outside comments, string and
     * character literals and the body of the allowed nested class.
     */
    static List<String> scan(String file, String source, String allowedNestedClass) {
        String code = blankCommentsAndLiterals(source);
        if (allowedNestedClass != null) {
            code = blankNestedClass(file, code, allowedNestedClass);
        }
        List<Problem> problems = new ArrayList<>();
        for (Rule rule : RULES) {
            Matcher m = rule.pattern().matcher(code);
            while (m.find()) {
                problems.add(new Problem(lineOf(code, m.start()), rule));
            }
        }
        problems.sort(Comparator.comparingInt(Problem::line));
        return problems.stream()
                .map(p -> file + ":" + p.line() + ": " + p.rule().call() + ", use " + p.rule().instead() + " instead")
                .toList();
    }

    private record Problem(int line, Rule rule) {
    }

    private static int lineOf(String code, int index) {
        int line = 1;
        for (int i = 0; i < index; i++) {
            if (code.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    /** Replaces comments and string, text block and character literals by spaces, keeping the line breaks. */
    static String blankCommentsAndLiterals(String source) {
        StringBuilder sb = new StringBuilder(source);
        int i = 0;
        int n = source.length();
        while (i < n) {
            char c = source.charAt(i);
            if (source.startsWith("//", i)) {
                int end = source.indexOf('\n', i);
                i = blank(sb, i, end < 0 ? n : end);
            } else if (source.startsWith("/*", i)) {
                int end = source.indexOf("*/", i + 2);
                if (end < 0) {
                    fail("Unterminated comment at offset " + i);
                }
                i = blank(sb, i, end + 2);
            } else if (source.startsWith("\"\"\"", i)) {
                // a text block: skip escapes (an escaped quote does not end it, an escaped backslash does not escape)
                int j = i + 3;
                while (j < n && !source.startsWith("\"\"\"", j)) {
                    j += source.charAt(j) == '\\' ? 2 : 1;
                }
                if (j >= n) {
                    fail("Unterminated text block at offset " + i);
                }
                i = blank(sb, i, j + 3);
            } else if (c == '"' || c == '\'') {
                int j = i + 1;
                while (j < n && source.charAt(j) != c && source.charAt(j) != '\n') {
                    j += source.charAt(j) == '\\' ? 2 : 1;
                }
                if (j >= n || source.charAt(j) != c) {
                    fail("Unterminated literal at offset " + i);
                }
                i = blank(sb, i, j + 1);
            } else {
                i++;
            }
        }
        return sb.toString();
    }

    private static int blank(StringBuilder sb, int from, int to) {
        for (int k = from; k < to; k++) {
            if (sb.charAt(k) != '\n') {
                sb.setCharAt(k, ' ');
            }
        }
        return to;
    }

    /** Replaces the body of the given nested class by spaces, keeping the line breaks. */
    private static String blankNestedClass(String file, String code, String name) {
        Matcher m = Pattern.compile("\\bclass\\s+" + Pattern.quote(name) + "\\b").matcher(code);
        if (!m.find()) {
            fail(file + ": the allowed nested class " + name + " is not found: update the allow-list in "
                 + TimeSourceDirectClockCallsTest.class.getSimpleName());
        }
        int open = code.indexOf('{', m.end());
        int depth = 0;
        int close = open;
        for (int k = open; k < code.length(); k++) {
            char c = code.charAt(k);
            if (c == '{') {
                depth++;
            } else if (c == '}' && --depth == 0) {
                close = k;
                break;
            }
        }
        StringBuilder sb = new StringBuilder(code);
        blank(sb, open, close + 1);
        return sb.toString();
    }
}
