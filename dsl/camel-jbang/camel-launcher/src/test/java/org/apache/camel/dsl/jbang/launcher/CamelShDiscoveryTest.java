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
package org.apache.camel.dsl.jbang.launcher;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the shared Java 17+ discovery contract implemented in camel.sh. POSIX only; the Windows batch equivalent
 * lives in {@link CamelBatDiscoveryTest}.
 */
@DisabledOnOs(OS.WINDOWS)
class CamelShDiscoveryTest {

    private static final Path SCRIPT = Paths.get("src/main/resources/bin/camel.sh");

    private static final String JAVA17 = "openjdk version \"17.0.2\" 2022-01-18";
    private static final String JAVA21 = "openjdk version \"21.0.3\" 2024-04-16";
    private static final String JAVA11 = "openjdk version \"11.0.20\" 2023-07-18";
    private static final String JAVA8 = "java version \"1.8.0_202\"";
    private static final String GARBAGE = "totally not a java banner";

    private Path home(Path base) throws Exception {
        return FakeJava.newLauncherHome(base, SCRIPT);
    }

    @Test
    void acceptsJava17ViaJavacmd(@TempDir Path base) throws Exception {
        Path h = home(base);
        Path java = FakeJava.writeFakeJava(base, "java", JAVA17, 0, "JAVACMD");
        Map<String, String> env = new HashMap<>();
        env.put("JAVACMD", java.toString());

        FakeJava.Result r = FakeJava.run(h.resolve("camel.sh"), env, "version");

        assertEquals(0, r.exitCode(), r.stderr());
        assertTrue(r.stdout().contains("RAN=JAVACMD"), r.stdout());
        // Probe output must not leak to the user during a normal invocation.
        assertFalse(r.stdout().contains("openjdk version"), "probe banner leaked to stdout");
        assertFalse(r.stderr().contains("openjdk version"), "probe banner leaked to stderr");
    }

    @Test
    void acceptsJava21(@TempDir Path base) throws Exception {
        Path h = home(base);
        Path java = FakeJava.writeFakeJava(base, "java", JAVA21, 0, "JAVACMD");
        Map<String, String> env = new HashMap<>();
        env.put("JAVACMD", java.toString());

        FakeJava.Result r = FakeJava.run(h.resolve("camel.sh"), env, "version");

        assertEquals(0, r.exitCode(), r.stderr());
        assertTrue(r.stdout().contains("RAN=JAVACMD"));
    }

    @Test
    void precedenceJavacmdBeatsJavaHomeAndPath(@TempDir Path base) throws Exception {
        Path h = home(base);
        Path first = FakeJava.writeFakeJava(base.resolve("j1"), "java", JAVA17, 0, "JAVACMD");
        Path jhomeBin = base.resolve("jh/bin");
        FakeJava.writeFakeJava(jhomeBin, "java", JAVA21, 0, "JAVAHOME");
        Path pathDir = base.resolve("pd");
        FakeJava.writeFakeJava(pathDir, "java", JAVA21, 0, "PATH");

        Map<String, String> env = new HashMap<>();
        env.put("JAVACMD", first.toString());
        env.put("JAVA_HOME", base.resolve("jh").toString());
        env.put("PATH", pathDir + ":/usr/bin:/bin");

        FakeJava.Result r = FakeJava.run(h.resolve("camel.sh"), env, "version");

        assertEquals(0, r.exitCode(), r.stderr());
        assertTrue(r.stdout().contains("RAN=JAVACMD"), r.stdout());
    }

    @Test
    void honorsJavaHomeWhenOnlySourceLikeSdkman(@TempDir Path base) throws Exception {
        // SDKMAN provides Java by exporting JAVA_HOME (candidate #2) with no JAVACMD set.
        Path h = home(base);
        Path jhomeBin = base.resolve("jh/bin");
        FakeJava.writeFakeJava(jhomeBin, "java", JAVA17, 0, "JAVAHOME");

        Map<String, String> env = new HashMap<>();
        env.put("JAVA_HOME", base.resolve("jh").toString());
        env.put("PATH", "/usr/bin:/bin"); // no java on PATH

        FakeJava.Result r = FakeJava.run(h.resolve("camel.sh"), env, "version");

        assertEquals(0, r.exitCode(), r.stderr());
        assertTrue(r.stdout().contains("RAN=JAVAHOME"), r.stdout());
    }

    @Test
    void skipsInvalidJavacmdFallsBackToJavaHome(@TempDir Path base) throws Exception {
        Path h = home(base);
        Path jhomeBin = base.resolve("jh/bin");
        FakeJava.writeFakeJava(jhomeBin, "java", JAVA17, 0, "JAVAHOME");

        Map<String, String> env = new HashMap<>();
        env.put("JAVACMD", base.resolve("does-not-exist").toString());
        env.put("JAVA_HOME", base.resolve("jh").toString());

        FakeJava.Result r = FakeJava.run(h.resolve("camel.sh"), env, "version");

        assertEquals(0, r.exitCode(), r.stderr());
        assertTrue(r.stdout().contains("RAN=JAVAHOME"), r.stdout());
    }

    @Test
    void skipsOldJavaHomeFallsBackToPath(@TempDir Path base) throws Exception {
        Path h = home(base);
        Path jhomeBin = base.resolve("jh/bin");
        FakeJava.writeFakeJava(jhomeBin, "java", JAVA11, 0, "JAVAHOME"); // too old
        Path pathDir = base.resolve("pd");
        FakeJava.writeFakeJava(pathDir, "java", JAVA17, 0, "PATH");

        Map<String, String> env = new HashMap<>();
        env.put("JAVA_HOME", base.resolve("jh").toString());
        env.put("PATH", pathDir + ":/usr/bin:/bin");

        FakeJava.Result r = FakeJava.run(h.resolve("camel.sh"), env, "version");

        assertEquals(0, r.exitCode(), r.stderr());
        assertTrue(r.stdout().contains("RAN=PATH"), r.stdout());
    }

    @Test
    void usesCamelFallbackJavaWhenNothingElseQualifies(@TempDir Path base) throws Exception {
        Path h = home(base);
        Path fb = FakeJava.writeFakeJava(base.resolve("fb"), "java", JAVA17, 0, "FALLBACK");

        Map<String, String> env = new HashMap<>();
        env.put("PATH", "/usr/bin:/bin"); // no java on PATH
        env.put("CAMEL_FALLBACK_JAVA", fb.toString());

        FakeJava.Result r = FakeJava.run(h.resolve("camel.sh"), env, "version");

        assertEquals(0, r.exitCode(), r.stderr());
        assertTrue(r.stdout().contains("RAN=FALLBACK"), r.stdout());
    }

    @Test
    void rejectsWhenNoCandidateQualifies(@TempDir Path base) throws Exception {
        Path h = home(base);
        Path old = FakeJava.writeFakeJava(base.resolve("old"), "java", JAVA8, 0, "OLD");

        Map<String, String> env = new HashMap<>();
        env.put("JAVACMD", old.toString());
        env.put("PATH", "/usr/bin:/bin");

        FakeJava.Result r = FakeJava.run(h.resolve("camel.sh"), env, "version");

        assertEquals(1, r.exitCode());
        assertTrue(r.stderr().contains("17"), "diagnostic must state the Java 17 minimum: " + r.stderr());
        assertTrue(r.stderr().toLowerCase().contains("java"), r.stderr());
    }

    @Test
    void rejectsUnparseableCandidate(@TempDir Path base) throws Exception {
        Path h = home(base);
        Path garbage = FakeJava.writeFakeJava(base.resolve("g"), "java", GARBAGE, 0, "GARBAGE");

        Map<String, String> env = new HashMap<>();
        env.put("JAVACMD", garbage.toString());
        env.put("PATH", "/usr/bin:/bin");

        FakeJava.Result r = FakeJava.run(h.resolve("camel.sh"), env, "version");

        assertEquals(1, r.exitCode());
    }

    @Test
    void parsesLegacyOneDotEightAsMajorEight(@TempDir Path base) throws Exception {
        // 1.8.0_202 must resolve to major 8 and therefore be rejected.
        Path h = home(base);
        Path java8 = FakeJava.writeFakeJava(base.resolve("j8"), "java", JAVA8, 0, "J8");

        Map<String, String> env = new HashMap<>();
        env.put("JAVACMD", java8.toString());
        env.put("PATH", "/usr/bin:/bin");

        FakeJava.Result r = FakeJava.run(h.resolve("camel.sh"), env, "version");

        assertEquals(1, r.exitCode(), "Java 8 must be rejected: " + r.stdout());
    }

    @Test
    void handlesSpacesAndUnicodeInJavaPath(@TempDir Path base) throws Exception {
        Path h = home(base);
        Path dir = base.resolve("spa ce/über");
        Path java = FakeJava.writeFakeJava(dir, "java", JAVA17, 0, "UNICODE");

        Map<String, String> env = new HashMap<>();
        env.put("JAVACMD", java.toString());

        FakeJava.Result r = FakeJava.run(h.resolve("camel.sh"), env, "version");

        assertEquals(0, r.exitCode(), r.stderr());
        assertTrue(r.stdout().contains("RAN=UNICODE"), r.stdout());
    }

    @Test
    void preservesArgumentsWithSpaces(@TempDir Path base) throws Exception {
        Path h = home(base);
        Path java = FakeJava.writeFakeJava(base, "java", JAVA17, 0, "ARGS");
        Map<String, String> env = new HashMap<>();
        env.put("JAVACMD", java.toString());

        FakeJava.Result r = FakeJava.run(h.resolve("camel.sh"), env, "run", "my route.yaml");

        assertEquals(0, r.exitCode(), r.stderr());
        assertTrue(r.stdout().contains("|run|my route.yaml|"), r.stdout());
    }

    @Test
    void defaultsHeapWhenJavaOptsUnset(@TempDir Path base) throws Exception {
        Path h = home(base);
        Path java = FakeJava.writeFakeJava(base, "java", JAVA17, 0, "HEAP");
        Map<String, String> env = new HashMap<>();
        env.put("JAVACMD", java.toString());

        FakeJava.Result r = FakeJava.run(h.resolve("camel.sh"), env, "version");

        assertTrue(r.stdout().contains("-Xmx512m"), "default heap must be applied: " + r.stdout());
    }

    @Test
    void preservesJavaOptsWhenSet(@TempDir Path base) throws Exception {
        Path h = home(base);
        Path java = FakeJava.writeFakeJava(base, "java", JAVA17, 0, "OPTS");
        Map<String, String> env = new HashMap<>();
        env.put("JAVACMD", java.toString());
        env.put("JAVA_OPTS", "-Dcamel.test=1 -Xmx256m");

        FakeJava.Result r = FakeJava.run(h.resolve("camel.sh"), env, "version");

        assertTrue(r.stdout().contains("-Dcamel.test=1"), r.stdout());
        assertTrue(r.stdout().contains("-Xmx256m"), r.stdout());
        assertFalse(r.stdout().contains("-Xmx512m"), "default heap must not be added when JAVA_OPTS set");
    }

    @Test
    void preservesChildExitCode(@TempDir Path base) throws Exception {
        Path h = home(base);
        Path java = FakeJava.writeFakeJava(base, "java", JAVA17, 42, "EXIT");
        Map<String, String> env = new HashMap<>();
        env.put("JAVACMD", java.toString());

        FakeJava.Result r = FakeJava.run(h.resolve("camel.sh"), env, "version");

        assertEquals(42, r.exitCode(), "child exit code must be propagated");
    }

    @Test
    void scriptItselfIsExecutableFixture(@TempDir Path base) throws Exception {
        Path h = home(base);
        assertTrue(Files.exists(h.resolve("camel.sh")));
    }
}
