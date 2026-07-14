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
import java.util.List;
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
    private static final String JAVA21 = "openjdk version \"21.0.3\" 2024-04-16";
    private static final String JAVA11 = "openjdk version \"11.0.20\" 2023-07-18";
    private static final String JAVA8 = "java version \"1.8.0_202\"";
    private static final String JAVA17_EA = "openjdk version \"17-ea\"";
    private static final String GARBAGE = "totally not a java banner";

    private Path home(Path base) throws Exception {
        return FakeJava.newLauncherHome(base, SCRIPT);
    }

    private Map<String, String> isolatedEnvironment() {
        String systemRoot = System.getenv("SystemRoot");
        assertTrue(systemRoot != null && !systemRoot.isBlank(), "Windows test fixture requires SystemRoot");
        Path system32 = Path.of(systemRoot, "System32");
        assertTrue(Files.isDirectory(system32), "Windows test fixture requires SystemRoot\\System32: " + system32);
        Map<String, String> env = new HashMap<>();
        env.put("PATH", system32.toString());
        return env;
    }

    private Path currentJavaHome() {
        Path javaHome = Path.of(System.getProperty("java.home"));
        assertTrue(Files.isRegularFile(javaHome.resolve("bin/java.exe")),
                "Windows test fixture requires the current JDK java.exe: " + javaHome);
        return javaHome;
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
    void acceptsJava21ViaJavacmd(@TempDir Path base) throws Exception {
        Path h = home(base);
        Path java = FakeJava.writeFakeJava(base, "java", JAVA21, 0, "JAVA21");
        Map<String, String> env = isolatedEnvironment();
        env.put("JAVACMD", java.toString());

        FakeJava.Result r = FakeJava.run(h.resolve("camel.bat"), env, "version");

        assertEquals(0, r.exitCode(), r.stderr());
        assertTrue(r.stdout().contains("RAN=JAVA21"), r.stdout());
    }

    @Test
    void precedenceJavacmdBeatsJavaHome(@TempDir Path base) throws Exception {
        Path h = home(base);
        Path java = FakeJava.writeFakeJava(base, "java", JAVA17, 0, "JAVACMD-FIRST");
        Map<String, String> env = isolatedEnvironment();
        env.put("JAVACMD", java.toString());
        env.put("JAVA_HOME", currentJavaHome().toString());

        FakeJava.Result r = FakeJava.run(h.resolve("camel.bat"), env, "version");

        assertEquals(0, r.exitCode(), r.stderr());
        assertTrue(r.stdout().contains("RAN=JAVACMD-FIRST"), r.stdout());
        assertFalse(r.stdout().contains("RAN=NATIVE-JAVA"), r.stdout());
    }

    @Test
    void candidateSourceOrderIsJavacmdJavaHomePathThenFallback(@TempDir Path base) throws Exception {
        Path h = home(base);
        String launcher = Files.readString(h.resolve("camel.bat"), StandardCharsets.UTF_8);
        List<String> expected = expectedDiscoveryLines();

        assertEquals(expected, executableDiscoveryLines(launcher),
                "active discovery order must be JAVACMD, JAVA_HOME, PATH java.exe, CAMEL_FALLBACK_JAVA");

        String commentedCandidates = String.join("\n", expected.stream().map(line -> "REM " + line).toList());
        String commentedDecoy = "set \"CAMEL_JAVACMD=\"\n" + commentedCandidates + "\n"
                                + ":haveJava\n"
                                + String.join("\n", expected);
        assertFalse(expected.equals(executableDiscoveryLines(commentedDecoy)),
                "commented or out-of-region candidate text must not satisfy the active discovery contract");
    }

    @Test
    void honorsJavaHomeWhenOnlySource(@TempDir Path base) throws Exception {
        Path h = home(base);
        writeRealJavaMarkerJar(h);
        Map<String, String> env = isolatedEnvironment();
        env.put("JAVA_HOME", currentJavaHome().toString());

        FakeJava.Result r = FakeJava.run(h.resolve("camel.bat"), env, "version");

        assertEquals(0, r.exitCode(), r.stderr());
        assertTrue(r.stdout().contains("RAN=NATIVE-JAVA"), r.stdout());
        assertTrue(r.stdout().contains("version"), r.stdout());
    }

    @Test
    void missingJavacmdContinuesToJavaHome(@TempDir Path base) throws Exception {
        Path h = home(base);
        writeRealJavaMarkerJar(h);
        Map<String, String> env = isolatedEnvironment();
        env.put("JAVACMD", base.resolve("missing-java.bat").toString());
        env.put("JAVA_HOME", currentJavaHome().toString());

        FakeJava.Result r = FakeJava.run(h.resolve("camel.bat"), env, "version");

        assertEquals(0, r.exitCode(), r.stderr());
        assertTrue(r.stdout().contains("RAN=NATIVE-JAVA"), r.stdout());
    }

    @Test
    void presentButNonRunnableJavacmdContinuesToJavaHome(@TempDir Path base) throws Exception {
        Path h = home(base);
        writeRealJavaMarkerJar(h);
        Path nonRunnable = base.resolve("not-a-program.exe");
        Files.writeString(nonRunnable, "not a Windows executable", StandardCharsets.UTF_8);
        Map<String, String> env = isolatedEnvironment();
        env.put("JAVACMD", nonRunnable.toString());
        env.put("JAVA_HOME", currentJavaHome().toString());

        FakeJava.Result r = FakeJava.run(h.resolve("camel.bat"), env, "version");

        assertEquals(0, r.exitCode(), r.stderr());
        assertTrue(r.stdout().contains("RAN=NATIVE-JAVA"), r.stdout());
    }

    @Test
    void acceptsNativeJavaFromPath(@TempDir Path base) throws Exception {
        Path h = home(base);
        writeRealJavaMarkerJar(h);
        Map<String, String> env = isolatedEnvironment();
        env.put("PATH", currentJavaHome().resolve("bin") + ";" + env.get("PATH"));

        FakeJava.Result r = FakeJava.run(h.resolve("camel.bat"), env, "version");

        assertEquals(0, r.exitCode(), r.stderr());
        assertTrue(r.stdout().contains("RAN=NATIVE-JAVA"), r.stdout());
    }

    @Test
    void precedenceNativePathBeatsCamelFallbackJava(@TempDir Path base) throws Exception {
        Path h = home(base);
        writeRealJavaMarkerJar(h);
        Path fallback = FakeJava.writeFakeJava(base.resolve("fallback"), "java", JAVA17, 0, "FALLBACK");
        Map<String, String> env = isolatedEnvironment();
        env.put("PATH", currentJavaHome().resolve("bin") + ";" + env.get("PATH"));
        env.put("CAMEL_FALLBACK_JAVA", fallback.toString());

        FakeJava.Result r = FakeJava.run(h.resolve("camel.bat"), env, "version");

        assertEquals(0, r.exitCode(), r.stderr());
        assertTrue(r.stdout().contains("RAN=NATIVE-JAVA"), r.stdout());
        assertFalse(r.stdout().contains("RAN=FALLBACK"), r.stdout());
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
    void rejectsNonnumericMajorAndContinuesToFallback(@TempDir Path base) throws Exception {
        Path h = home(base);
        Path earlyAccess = FakeJava.writeFakeJava(base.resolve("early-access"), "java", JAVA17_EA, 0, "EARLY");
        Path fallback = FakeJava.writeFakeJava(base.resolve("fallback"), "java", JAVA17, 0, "FALLBACK");
        Map<String, String> env = isolatedEnvironment();
        env.put("JAVACMD", earlyAccess.toString());
        env.put("CAMEL_FALLBACK_JAVA", fallback.toString());

        FakeJava.Result r = FakeJava.run(h.resolve("camel.bat"), env, "version");

        assertEquals(0, r.exitCode(), r.stderr());
        assertTrue(r.stdout().contains("RAN=FALLBACK"), r.stdout());
        assertFalse(r.stdout().contains("RAN=EARLY"), r.stdout());
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
        Path wrapper = writeBatch(base.resolve("wrapper"), "java",
                "if \"%~1\"==\"-version\" (",
                "  echo " + JAVA17 + " 1>&2",
                "  exit /b 0",
                ")",
                "set ARG_COUNT=0",
                "for %%a in (%*) do set /a ARG_COUNT+=1 >NUL",
                "echo ARG_COUNT=%ARG_COUNT%",
                "echo ARG1=[%~1]",
                "echo ARG2=[%~2]",
                "echo ARG3=[%~3]",
                "echo ARG4=[%~4]",
                "echo ARG5=[%~5]",
                "echo ARG6=[%~6]",
                "echo STDERR=JAVACMD-WRAPPER 1>&2",
                "more",
                "exit /b 37");
        Map<String, String> env = isolatedEnvironment();
        env.put("JAVACMD", wrapper.toString());

        FakeJava.Result r = FakeJava.runWithInput(h.resolve("camel.bat"), env, "route input\r\n", "run",
                "my route.yaml");

        assertEquals(37, r.exitCode(), "batch child exit code must be propagated");
        assertTrue(r.stdout().contains("ARG_COUNT=5"), r.stdout());
        assertTrue(r.stdout().contains("ARG1=[-Xmx512m]"), r.stdout());
        assertTrue(r.stdout().contains("ARG2=[-jar]"), r.stdout());
        assertTrue(r.stdout().contains("ARG3=[" + h.resolve("camel-launcher-9.9.9.jar") + "]"), r.stdout());
        assertTrue(r.stdout().contains("ARG4=[run]"), r.stdout());
        assertTrue(r.stdout().contains("ARG5=[my route.yaml]"), r.stdout());
        assertTrue(r.stdout().contains("ARG6=[]"), r.stdout());
        assertTrue(r.stdout().contains("route input"), "stdin did not reach batch child stdout: " + r.stdout());
        assertTrue(r.stderr().contains("STDERR=JAVACMD-WRAPPER"), "batch child stderr was not preserved: " + r.stderr());
    }

    @Test
    void finalBatchWrapperReturnsToLauncherStatementAfterCall(@TempDir Path base) throws Exception {
        Path sentinel = base.resolve("launcher-resumed.txt");
        Path h = instrumentBatchLaunchReturn(home(base), sentinel);
        Path wrapper = FakeJava.writeFakeJava(base.resolve("wrapper"), "java", JAVA17, 41, "RETURN");
        Map<String, String> env = isolatedEnvironment();
        env.put("JAVACMD", wrapper.toString());

        FakeJava.Result r = FakeJava.run(h.resolve("camel.bat"), env, "version");

        assertEquals(41, r.exitCode(), "captured batch child exit code must survive sentinel instrumentation");
        assertEquals("LAUNCHER-RESUMED", Files.readString(sentinel).trim(),
                "launcher did not resume after the final batch wrapper call");
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
        assertFalse(r.stdout().contains("wrapper version metadata"), "probe output leaked: " + r.stdout());
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

    private List<String> expectedDiscoveryLines() {
        return List.of(
                "call :tryJava \"%JAVACMD%\"",
                "if defined CAMEL_JAVACMD goto haveJava",
                "if defined JAVA_HOME call :tryJava \"%JAVA_HOME%\\bin\\java.exe\"",
                "if defined CAMEL_JAVACMD goto haveJava",
                "for %%p in (java.exe) do call :tryJava \"%%~$PATH:p\"",
                "if defined CAMEL_JAVACMD goto haveJava",
                "call :tryJava \"%CAMEL_FALLBACK_JAVA%\"",
                "if defined CAMEL_JAVACMD goto haveJava",
                "goto noJava");
    }

    private List<String> executableDiscoveryLines(String source) {
        List<String> lines = source.lines()
                .map(String::trim)
                .map(line -> line.replaceAll("\\s+", " "))
                .toList();
        assertEquals(1, lines.stream().filter("set \"CAMEL_JAVACMD=\""::equals).count(),
                "active discovery start must be unique");
        assertEquals(1, lines.stream().filter(":haveJava"::equals).count(),
                "active discovery end must be unique");
        int start = lines.indexOf("set \"CAMEL_JAVACMD=\"");
        int end = lines.indexOf(":haveJava");
        assertTrue(start >= 0, "active discovery start was not found");
        assertTrue(end > start, "active discovery end was not found after its start");
        return lines.subList(start + 1, end).stream()
                .filter(line -> !line.isBlank())
                .filter(line -> !isBatchComment(line))
                .toList();
    }

    private boolean isBatchComment(String line) {
        String command = line.startsWith("@") ? line.substring(1).stripLeading() : line;
        return command.equalsIgnoreCase("REM")
                || command.regionMatches(true, 0, "REM ", 0, 4)
                || command.startsWith("::");
    }

    private Path instrumentBatchLaunchReturn(Path launcherHome, Path sentinel) throws Exception {
        Path launcher = launcherHome.resolve("camel.bat");
        String source = Files.readString(launcher, StandardCharsets.UTF_8);
        String newline = source.contains("\r\n") ? "\r\n" : "\n";
        String launchBlock = "call \"%JAVACMD%\" %JAVA_OPTS% -jar \"%LAUNCHER_JAR%\" %*" + newline
                             + "set ERROR_CODE=%ERRORLEVEL%" + newline
                             + "goto javaExecuted";
        String instrumentedBlock = "call \"%JAVACMD%\" %JAVA_OPTS% -jar \"%LAUNCHER_JAR%\" %*" + newline
                                   + "set ERROR_CODE=%ERRORLEVEL%" + newline
                                   + "echo LAUNCHER-RESUMED>\"" + sentinel + "\"" + newline
                                   + "goto javaExecuted";
        String instrumented = source.replace(launchBlock, instrumentedBlock);
        assertFalse(instrumented.equals(source), "could not instrument copied batch-launch block");
        Files.writeString(launcher, instrumented, StandardCharsets.UTF_8);
        return launcherHome;
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
        System.out.println("RAN=NATIVE-JAVA");
        System.out.println("ARGS=" + Arrays.toString(args));
    }
}
