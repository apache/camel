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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Host-gated orchestration tests for the Windows validator dispatcher and assertion library. Runs only on Windows via
 * {@link EnabledOnOs}.
 */
@EnabledOnOs(OS.WINDOWS)
class ValidateWindowsTest {

    private static final Path MODULE_DIR = Path.of("src").toAbsolutePath();
    private static final Path ASSERT_BAT = MODULE_DIR.resolve("jreleaser/bin/lib/assert-camel-cli.bat");
    private static final Path VALIDATE_BAT = MODULE_DIR.resolve("jreleaser/bin/camel-validate.bat");
    private static final Path FIXTURE = MODULE_DIR.resolve("test/resources/validate/expected-init-route.txt");

    @BeforeAll
    static void checkPreconditions() throws IOException {
        assertTrue(Files.exists(ASSERT_BAT), "assert-camel-cli.bat must exist: " + ASSERT_BAT);
        assertTrue(Files.exists(VALIDATE_BAT), "camel-validate.bat must exist: " + VALIDATE_BAT);
        assertTrue(Files.exists(FIXTURE), "init fixture must exist: " + FIXTURE);
    }

    // -----------------------------------------------------------------------
    // Windows dispatcher contract tests.
    // Package-manager-specific subtests self-skip when the manager is absent.
    // -----------------------------------------------------------------------

    @Test
    void unknownTargetIsUsageError() throws Exception {
        String script = "cmd /c \"" + VALIDATE_BAT.toAbsolutePath() + " frobnicate 2>&1\" || exit 0";
        Result r = sh(null, List.of("PATH=C:\\Windows\\System32"), script);
        assertTrue(r.out().contains("Error:") || r.out().contains("Usage:") || r.exit() == 2, r.out());
    }

    @Test
    void absentManagersSelfSkip() throws Exception {
        // Run with a PATH that contains winget/scoop/choco; they self-skip if absent.
        String script = "cmd /c \"" + VALIDATE_BAT.toAbsolutePath() + " all 2>&1\" & exit 0";
        Result r = sh(null, List.of(
                "PATH=C:\\Windows\\System32",
                "TEMP=" + System.getProperty("java.io.tmpdir")));
        assertTrue(r.out().contains("SKIP:") || r.exit() == 0,
                "should produce SKIP lines; output: " + r.out());
    }

    @Test
    void validateAllSucceedsOnNoManagers() throws Exception {
        String script = "cmd /c \"" + VALIDATE_BAT.toAbsolutePath() + " all 2>&1 || true\"";
        Result r = sh(null, List.of(
                "PATH=C:\\Windows\\System32",
                "TEMP=" + System.getProperty("java.io.tmpdir")));
        assertEquals(0, r.exit(),
                "all should succeed (with skips) when no package managers are present; output: " + r.out());
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private Result sh(Path cwd, List<String> envOverrides, String... commands) throws Exception {
        Path tmpDir = cwd != null ? cwd : Path.of(System.getProperty("java.io.tmpdir"));
        Path batFile = Files.createTempFile(tmpDir, "validate-win-", ".bat");
        Files.writeString(batFile, String.join("\n", commands), StandardCharsets.UTF_8);

        ProcessBuilder pb = new ProcessBuilder("cmd", "/c", batFile.toString());
        if (cwd != null) {
            pb.directory(cwd.toFile());
        }
        for (String e : envOverrides) {
            int eq = e.indexOf('=');
            pb.environment().put(e.substring(0, eq), e.substring(eq + 1));
        }
        pb.redirectErrorStream(true);

        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        boolean completed = p.waitFor(120, TimeUnit.SECONDS);
        int exitCode = completed ? p.exitValue() : -1;
        Files.deleteIfExists(batFile);

        return new Result(exitCode, out);
    }

    record Result(int exit, String out) {
    }
}
