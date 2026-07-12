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

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Windows equivalent of {@link CamelShDiscoveryTest}: verifies camel.bat implements the same shared Java 17+ discovery
 * contract with observably equivalent behavior.
 */
@EnabledOnOs(OS.WINDOWS)
class CamelBatDiscoveryTest {

    private static final Path SCRIPT = Paths.get("src/main/resources/bin/camel.bat");

    private static final String JAVA17 = "openjdk version \"17.0.2\" 2022-01-18";
    private static final String JAVA11 = "openjdk version \"11.0.20\" 2023-07-18";
    private static final String JAVA8 = "java version \"1.8.0_202\"";
    private static final String GARBAGE = "totally not a java banner";

    private Path home(Path base) throws Exception {
        return FakeJava.newLauncherHome(base, SCRIPT);
    }

    private Map<String, String> isolatedEnvironment() {
        Map<String, String> env = new HashMap<>();
        env.put("PATH", Path.of(System.getenv("SystemRoot"), "System32").toString());
        return env;
    }

    @Test
    void acceptsJava17ViaJavacmd(@TempDir Path base) throws Exception {
        Path h = home(base);
        Path java = FakeJava.writeFakeJava(base, "java", JAVA17, 0, "JAVACMD");
        Map<String, String> env = isolatedEnvironment();
        env.put("JAVACMD", java.toString());

        FakeJava.Result r = FakeJava.run(h.resolve("camel.bat"), env, "version");

        assertEquals(0, r.exitCode(), r.stderr());
        assertTrue(r.stdout().contains("RAN=JAVACMD"), r.stdout());
        assertFalse(r.stdout().contains("openjdk version"), "probe banner leaked to stdout");
        assertFalse(r.stderr().contains("openjdk version"), "probe banner leaked to stderr");
    }

    @Test
    void honorsJavaHomeWhenOnlySource(@TempDir Path base) throws Exception {
        Path h = home(base);
        writeRealJavaMarkerJar(h);
        Map<String, String> env = isolatedEnvironment();
        env.put("JAVA_HOME", System.getProperty("java.home"));

        FakeJava.Result r = FakeJava.run(h.resolve("camel.bat"), env, "version");

        assertEquals(0, r.exitCode(), r.stderr());
        assertTrue(r.stdout().contains("RAN=JAVAHOME"), r.stdout());
        assertTrue(r.stdout().contains("version"), r.stdout());
    }

    @Test
    void skipsOldJavacmdFallsBackToFallback(@TempDir Path base) throws Exception {
        Path h = home(base);
        Path old = FakeJava.writeFakeJava(base.resolve("old"), "java", JAVA11, 0, "OLD");
        Path fallback = FakeJava.writeFakeJava(base.resolve("fallback"), "java", JAVA17, 0, "FALLBACK");
        Map<String, String> env = isolatedEnvironment();
        env.put("JAVACMD", old.toString());
        env.put("JAVA_HOME", base.resolve("invalid-java-home").toString());
        env.put("CAMEL_FALLBACK_JAVA", fallback.toString());

        FakeJava.Result r = FakeJava.run(h.resolve("camel.bat"), env, "version");

        assertEquals(0, r.exitCode(), r.stderr());
        assertTrue(r.stdout().contains("RAN=FALLBACK"), r.stdout());
        assertFalse(r.stdout().contains("RAN=OLD"), r.stdout());
    }

    @Test
    void rejectsWhenNoCandidateQualifies(@TempDir Path base) throws Exception {
        Path h = home(base);
        Path old = FakeJava.writeFakeJava(base.resolve("old"), "java", JAVA8, 0, "OLD");
        Map<String, String> env = isolatedEnvironment();
        env.put("JAVACMD", old.toString());

        FakeJava.Result r = FakeJava.run(h.resolve("camel.bat"), env, "version");

        assertEquals(1, r.exitCode());
        assertTrue(r.stderr().contains("17"), "diagnostic must state the Java 17 minimum: " + r.stderr());
        assertTrue(r.stderr().toLowerCase().contains("java"), r.stderr());
    }

    @Test
    void rejectsUnparseableCandidate(@TempDir Path base) throws Exception {
        Path h = home(base);
        Path garbage = FakeJava.writeFakeJava(base.resolve("garbage"), "java", GARBAGE, 0, "GARBAGE");
        Map<String, String> env = isolatedEnvironment();
        env.put("JAVACMD", garbage.toString());

        FakeJava.Result r = FakeJava.run(h.resolve("camel.bat"), env, "version");

        assertEquals(1, r.exitCode());
    }

    @Test
    void handlesSpacesAndUnicodeInJavaPath(@TempDir Path base) throws Exception {
        Path h = home(base);
        Path java = FakeJava.writeFakeJava(base.resolve("spa ce/über"), "java", JAVA17, 0, "UNICODE");
        Map<String, String> env = isolatedEnvironment();
        env.put("JAVACMD", java.toString());

        FakeJava.Result r = FakeJava.run(h.resolve("camel.bat"), env, "version");

        assertEquals(0, r.exitCode(), r.stderr());
        assertTrue(r.stdout().contains("RAN=UNICODE"), r.stdout());
    }

    @Test
    void defaultsHeapWhenJavaOptsUnset(@TempDir Path base) throws Exception {
        Path h = home(base);
        Path java = FakeJava.writeFakeJava(base, "java", JAVA17, 0, "HEAP");
        Map<String, String> env = isolatedEnvironment();
        env.put("JAVACMD", java.toString());

        FakeJava.Result r = FakeJava.run(h.resolve("camel.bat"), env, "version");

        assertEquals(0, r.exitCode(), r.stderr());
        assertTrue(r.stdout().contains("-Xmx512m"), "default heap must be applied: " + r.stdout());
    }

    @Test
    void preservesJavaOptsWhenSet(@TempDir Path base) throws Exception {
        Path h = home(base);
        Path java = FakeJava.writeFakeJava(base, "java", JAVA17, 0, "OPTS");
        Map<String, String> env = isolatedEnvironment();
        env.put("JAVACMD", java.toString());
        env.put("JAVA_OPTS", "-Dcamel.test=1 -Xmx256m");

        FakeJava.Result r = FakeJava.run(h.resolve("camel.bat"), env, "version");

        assertEquals(0, r.exitCode(), r.stderr());
        assertTrue(r.stdout().contains("-Dcamel.test=1"), r.stdout());
        assertTrue(r.stdout().contains("-Xmx256m"), r.stdout());
        assertFalse(r.stdout().contains("-Xmx512m"), "default heap must not be added when JAVA_OPTS is set");
    }

    @Test
    void javacmdBatchWrapperReturnsForProbeAndLaunchAndPreservesBehavior(@TempDir Path base) throws Exception {
        Path h = home(base);
        Path wrapper = FakeJava.writeFakeJava(base.resolve("wrapper"), "java", JAVA17, 37, "JAVACMD-WRAPPER");
        Map<String, String> env = isolatedEnvironment();
        env.put("JAVACMD", wrapper.toString());

        FakeJava.Result r = FakeJava.runWithInput(h.resolve("camel.bat"), env, "route input\r\n", "run",
                "my route.yaml");

        assertEquals(37, r.exitCode(), "batch child exit code must be propagated");
        assertTrue(r.stdout().contains("RAN=JAVACMD-WRAPPER"), r.stdout());
        assertTrue(r.stdout().contains("run"), r.stdout());
        assertTrue(r.stdout().contains("my route.yaml"), r.stdout());
        assertTrue(r.stdout().contains("route input"), "stdin did not reach batch child stdout: " + r.stdout());
        assertTrue(r.stderr().contains("STDERR=JAVACMD-WRAPPER"), "batch child stderr was not preserved: " + r.stderr());
    }

    @Test
    void fallbackBatchWrapperIsReachedAfterInvalidCandidatesAndPropagatesExit(@TempDir Path base) throws Exception {
        Path h = home(base);
        Path old = FakeJava.writeFakeJava(base.resolve("old"), "java", JAVA11, 0, "OLD");
        Path fallback = FakeJava.writeFakeJava(base.resolve("fallback"), "java", JAVA17, 29, "FALLBACK-WRAPPER");
        Map<String, String> env = isolatedEnvironment();
        env.put("JAVACMD", old.toString());
        env.put("JAVA_HOME", base.resolve("missing-home").toString());
        env.put("CAMEL_FALLBACK_JAVA", fallback.toString());

        FakeJava.Result r = FakeJava.run(h.resolve("camel.bat"), env, "version");

        assertEquals(29, r.exitCode(), "fallback batch child exit code must be propagated");
        assertTrue(r.stdout().contains("RAN=FALLBACK-WRAPPER"), r.stdout());
    }

    @Test
    void baseDirProbeFallbackCleansArtifactWhenTempAndTmpAreAbsent(@TempDir Path base) throws Exception {
        Path h = home(base);
        Path probePathMarker = base.resolve("probe-path.txt");
        Path java = writeBatch(base.resolve("wrapper"), "java",
                "if \"%~1\"==\"-version\" (",
                "  echo %_PROBE% >\"" + probePathMarker + "\"",
                "  echo " + JAVA17 + " 1>&2",
                "  exit /b 0",
                ")",
                "echo RAN=BASEDIR-PROBE",
                "exit /b 0");
        Map<String, String> env = isolatedEnvironment();
        env.put("JAVACMD", java.toString());
        assertFalse(env.containsKey("TEMP"));
        assertFalse(env.containsKey("TMP"));

        FakeJava.Result r = FakeJava.run(h.resolve("camel.bat"), env, "version");

        assertEquals(0, r.exitCode(), r.stderr());
        Path actualProbe = Path.of(Files.readString(probePathMarker).trim()).toAbsolutePath().normalize();
        assertEquals(h.toAbsolutePath().normalize(), actualProbe.getParent(), "probe did not use BASEDIR fallback");
        try (var probes = Files.list(h)) {
            assertFalse(probes.anyMatch(path -> path.getFileName().toString().startsWith("camel-java-probe-")
                    && path.getFileName().toString().endsWith(".tmp")), "probe artifact remained in BASEDIR");
        }
    }

    @Test
    void skipsValidLookingBannerWhenProbeExitsNonzero(@TempDir Path base) throws Exception {
        Path h = home(base);
        Path rejected = writeBatch(base.resolve("rejected"), "java",
                "if \"%~1\"==\"-version\" (",
                "  echo " + JAVA17 + " 1>&2",
                "  exit /b 7",
                ")",
                "echo RAN=REJECTED");
        Path fallback = FakeJava.writeFakeJava(base.resolve("fallback"), "java", JAVA17, 0, "FALLBACK");
        Map<String, String> env = isolatedEnvironment();
        env.put("JAVACMD", rejected.toString());
        env.put("CAMEL_FALLBACK_JAVA", fallback.toString());

        FakeJava.Result r = FakeJava.run(h.resolve("camel.bat"), env, "version");

        assertEquals(0, r.exitCode(), r.stderr());
        assertTrue(r.stdout().contains("RAN=FALLBACK"), r.stdout());
        assertFalse(r.stdout().contains("RAN=REJECTED"), r.stdout());
    }

    @Test
    void acceptsValidBannerAfterPreBannerLineContainingVersion(@TempDir Path base) throws Exception {
        Path h = home(base);
        Path java = writeBatch(base.resolve("banner"), "java",
                "if \"%~1\"==\"-version\" (",
                "  echo wrapper version metadata 1>&2",
                "  echo " + JAVA17 + " 1>&2",
                "  exit /b 0",
                ")",
                "echo RAN=VALID-BANNER",
                "exit /b 0");
        Map<String, String> env = isolatedEnvironment();
        env.put("JAVACMD", java.toString());

        FakeJava.Result r = FakeJava.run(h.resolve("camel.bat"), env, "version");

        assertEquals(0, r.exitCode(), r.stderr());
        assertTrue(r.stdout().contains("RAN=VALID-BANNER"), r.stdout());
        assertFalse(r.stderr().contains("wrapper version metadata"), "probe output leaked: " + r.stderr());
    }

    @Test
    void probeCannotConsumeChildStdin(@TempDir Path base) throws Exception {
        Path h = home(base);
        Path java = writeBatch(base.resolve("stdin"), "java",
                "if \"%~1\"==\"-version\" (",
                "  set /p \"ignored=\"",
                "  echo " + JAVA17 + " 1>&2",
                "  exit /b 0",
                ")",
                "echo RAN=PROBE-STDIN",
                "more",
                "exit /b 0");
        Map<String, String> env = isolatedEnvironment();
        env.put("JAVACMD", java.toString());

        FakeJava.Result r = FakeJava.runWithInput(h.resolve("camel.bat"), env, "route input\r\n", "version");

        assertEquals(0, r.exitCode(), r.stderr());
        assertTrue(r.stdout().contains("RAN=PROBE-STDIN"), r.stdout());
        assertTrue(r.stdout().contains("route input"), "version probe consumed child stdin: " + r.stdout());
    }

    private Path writeBatch(Path dir, String name, String... lines) throws Exception {
        Files.createDirectories(dir);
        Path script = dir.resolve(name + ".bat");
        Files.writeString(script, "@echo off\r\n" + String.join("\r\n", lines) + "\r\n", StandardCharsets.UTF_8);
        return script;
    }

    private void writeRealJavaMarkerJar(Path launcherHome) throws Exception {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, CamelBatRealJavaMarker.class.getName());
        String entryName = CamelBatRealJavaMarker.class.getName().replace('.', '/') + ".class";
        Path jar = launcherHome.resolve("camel-launcher-9.9.9.jar");
        try (InputStream classBytes = CamelBatRealJavaMarker.class.getResourceAsStream("/" + entryName);
             JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar), manifest)) {
            assertTrue(classBytes != null, "could not load marker class bytes");
            out.putNextEntry(new JarEntry(entryName));
            classBytes.transferTo(out);
            out.closeEntry();
        }
    }

}

final class CamelBatRealJavaMarker {

    private CamelBatRealJavaMarker() {
    }

    public static void main(String[] args) {
        System.out.println("RAN=JAVAHOME");
        System.out.println("ARGS=" + Arrays.toString(args));
    }
}
