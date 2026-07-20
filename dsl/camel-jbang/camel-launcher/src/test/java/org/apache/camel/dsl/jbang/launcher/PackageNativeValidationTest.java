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
import java.nio.file.attribute.PosixFilePermission;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Host-gated orchestration tests for the POSIX package-native validator dispatcher and assertion library. Runs on macOS
 * and Linux (disabled on Windows).
 */
@DisabledOnOs(OS.WINDOWS)
class PackageNativeValidationTest {

    private static final Path MODULE_DIR = Path.of("src").toAbsolutePath();
    private static final Path LIB = MODULE_DIR.resolve("jreleaser/bin/lib/assert-camel-cli.sh");
    private static final Path VALIDATE = MODULE_DIR.resolve("jreleaser/bin/camel-validate.sh");
    private static final Path FIXTURE = MODULE_DIR.resolve("test/resources/validate/expected-init-route.txt");

    @BeforeAll
    static void checkPreconditions() throws IOException {
        assertTrue(Files.exists(LIB), "assert-camel-cli.sh must exist: " + LIB);
        assertTrue(Files.exists(VALIDATE), "camel-validate.sh must exist: " + VALIDATE);
        assertTrue(Files.exists(FIXTURE), "init fixture must exist: " + FIXTURE);
    }

    @Test
    void assertionLibraryPassesForConformantCli(@TempDir Path tmp) throws Exception {
        Path fake = writeFakeCamel(tmp);
        String script = ". " + LIB.toAbsolutePath()
                        + "; assert_camel_cli '" + fake + "' '" + tmp + "' '4.22.0'"
                        + " && assert_camel_absent '" + tmp.resolve("does-not-exist-camel").toAbsolutePath() + "'";
        Result r = sh(null, List.of("ASSERT_INIT_FIXTURE=" + FIXTURE.toAbsolutePath()), script);
        assertEquals(0, r.exit(), r.out());
        assertTrue(r.out().contains("PASS"), r.out());
    }

    @Test
    void assertionLibraryFailsWhenInitContentMissing(@TempDir Path tmp) throws Exception {
        Path fake = tmp.resolve("camel");
        Files.writeString(fake,
                "#!/bin/sh\ncase \"$1\" in version) echo v;; init) echo 'nope' > \"$2\";; *) exit 3;; esac\n",
                StandardCharsets.UTF_8);
        Files.setPosixFilePermissions(fake, Set.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_EXECUTE));
        String script = ". " + LIB.toAbsolutePath() + "; assert_camel_cli '" + fake + "' '" + tmp + "'";
        Result r = sh(null, List.of("ASSERT_INIT_FIXTURE=" + FIXTURE.toAbsolutePath()), script);
        assertTrue(r.exit() != 0, r.out());
        assertFalse(r.out().contains("PASS"), r.out());
    }

    @Test
    void unknownTargetIsUsageError() throws Exception {
        String script = "/bin/sh " + VALIDATE.toAbsolutePath() + " frobnicate 2>&1 || exit 0";
        Result r = sh(null, List.of("PATH=/usr/bin:/bin"), script);
        assertTrue(r.out().contains("Error:") || r.out().contains("Usage:"), r.out());
    }

    @Test
    void unavailablePackageManagersSelfSkip() throws Exception {
        Result r = validateWithNoPackageManagers();
        assertEquals(0, r.exit(), r.out());
        assertTrue(r.out().contains("SKIP:"), r.out());
    }

    @Test
    void validateAllSucceedsWhenPackageManagersAreUnavailable() throws Exception {
        Result r = validateWithNoPackageManagers();
        assertEquals(0, r.exit(), r.out());
        assertTrue(r.out().contains("SKIP:"), r.out());
    }

    private static Result validateWithNoPackageManagers() throws Exception {
        return sh(null, noPackageManagerEnv(), "/bin/bash " + VALIDATE.toAbsolutePath() + " all");
    }

    private static List<String> noPackageManagerEnv() {
        String originalPath = System.getenv("PATH");
        String cleanPath = originalPath != null ? originalPath : "/usr/bin:/bin";
        String filtered = Arrays.stream(cleanPath.split(":"))
                .filter(e -> !e.contains("homebrew") && !e.contains("Homebrew"))
                .collect(Collectors.joining(":"));
        return List.of(
                "PATH=" + filtered,
                "HOME=" + System.getProperty("java.io.tmpdir"),
                "SDKMAN_DIR=/nonexistent-sdkman",
                "CAMEL_PACKAGE_TEST_MODE=true",
                "CAMEL_PACKAGE_TEST_VERSION=4.22.0");
    }

    private Path writeFakeCamel(Path dir) throws IOException {
        String body = "#!/bin/sh\n"
                      + "case \"$1\" in\n"
                      + "  --version) echo 'Apache Camel 4.22.0'; exit 0 ;;\n"
                      + "  init) name=$(basename \"$2\" .java); "
                      + "sed \"s/public class hello/public class $name/\" '" + FIXTURE.toAbsolutePath()
                      + "' > \"$2\"; exit 0 ;;\n"
                      + "  *) exit 3 ;;\n"
                      + "esac\n";
        Path fake = dir.resolve("camel");
        Files.writeString(fake, body, StandardCharsets.UTF_8);
        Files.setPosixFilePermissions(fake, Set.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_EXECUTE));
        return fake;
    }

    private static Result sh(Path cwd, List<String> envOverrides, String... commands) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("/bin/sh", "-c", String.join(" && ", commands));
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

        return new Result(exitCode, out);
    }

    record Result(int exit, String out) {
    }
}
