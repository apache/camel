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
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

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

    private static final String VERSION = "4.22.0";

    @Test
    void assertionLibraryPassesForConformantCli(@TempDir Path tmp) throws Exception {
        Path fake = writeFakeCamel(tmp.resolve("camel"), VERSION);
        String script = ". " + LIB.toAbsolutePath()
                        + "; assert_camel_cli '" + fake + "' '" + tmp + "' '" + VERSION + "'"
                        + " && assert_camel_absent '" + tmp.resolve("does-not-exist-camel").toAbsolutePath() + "'";
        Result r = sh(null, List.of("ASSERT_INIT_FIXTURE=" + FIXTURE.toAbsolutePath()), script);

        assertThat(r.exit()).as(r.out()).isZero();
        // Assert the specific behavioral lines, not just "PASS": a passing version comparison and a
        // fixture-matching init route are what this test exists to protect.
        assertThat(r.out())
                .contains("camel version matches expected '" + VERSION + "'")
                .contains("camel init route content matches expected fixture")
                .doesNotContain("FAIL");
    }

    @Test
    void assertionLibraryFailsWhenInitContentMissing(@TempDir Path tmp) throws Exception {
        // Version reports correctly (so the version check passes); only the init route content is wrong,
        // so the failure this test asserts can only come from the init-content comparison.
        Path fake = writeExecutable(tmp.resolve("camel"),
                "#!/bin/sh\n"
                                                          + "case \"$1\" in\n"
                                                          + "  --version) echo 'Apache Camel " + VERSION + "' ;;\n"
                                                          + "  init) echo 'nope' > \"$2\" ;;\n"
                                                          + "  *) exit 3 ;;\n"
                                                          + "esac\n");
        String script = ". " + LIB.toAbsolutePath() + "; assert_camel_cli '" + fake + "' '" + tmp + "' '" + VERSION + "'";
        Result r = sh(null, List.of("ASSERT_INIT_FIXTURE=" + FIXTURE.toAbsolutePath()), script);

        assertThat(r.exit()).as(r.out()).isNotZero();
        assertThat(r.out())
                .contains("camel init route content differs from fixture")
                .doesNotContain("camel init route content matches expected fixture");
    }

    @Test
    void assertionLibraryFailsOnVersionMismatch(@TempDir Path tmp) throws Exception {
        // Init content is correct, but the reported version is wrong. This pins the fix that makes a
        // version mismatch propagate out of assert_camel_cli instead of being printed and swallowed.
        Path fake = writeFakeCamel(tmp.resolve("camel"), "9.9.9");
        String script = ". " + LIB.toAbsolutePath() + "; assert_camel_cli '" + fake + "' '" + tmp + "' '" + VERSION + "'";
        Result r = sh(null, List.of("ASSERT_INIT_FIXTURE=" + FIXTURE.toAbsolutePath()), script);

        assertThat(r.exit()).as(r.out()).isNotZero();
        assertThat(r.out()).contains("camel version mismatch: expected '" + VERSION + "', got '9.9.9'");
    }

    @Test
    void unknownTargetIsUsageError() throws Exception {
        // The script's contract is to reject an unknown command with exit code 2; assert the exit code
        // directly rather than only grepping output (usage text prints for valid commands too).
        Result r = sh(null, List.of("PATH=/usr/bin:/bin"), "/bin/sh " + VALIDATE.toAbsolutePath() + " frobnicate");

        assertThat(r.exit()).as(r.out()).isEqualTo(2);
        assertThat(r.out()).contains("Error:").contains("unknown command");
    }

    @Test
    void allValidatorsSelfSkipWhenNothingIsStaged(@TempDir Path emptyTarget) throws Exception {
        // With an empty target dir, every validator has nothing to act on: local finds no archive,
        // homebrew finds no formula, SDKMAN finds no descriptor. All must SKIP, none may FAIL, exit 0.
        Result r = sh(null, List.of(
                "CAMEL_PACKAGE_TEST_MODE=true",
                "CAMEL_PACKAGE_TEST_VERSION=" + VERSION,
                "CAMEL_VALIDATE_TARGET_DIR=" + emptyTarget.toAbsolutePath()),
                "/bin/bash " + VALIDATE.toAbsolutePath() + " all");

        assertThat(r.exit()).as(r.out()).isZero();
        assertThat(r.out()).contains("SKIP:").doesNotContain("FAIL");
    }

    @Test
    void localArchiveValidatorPassesForStagedArchive(@TempDir Path target) throws Exception {
        // Stage a real tar.gz laid out exactly like the release archive (camel-launcher-<v>/bin/camel.sh)
        // and run the `local` validator against it. This is the one validator the whole dispatcher design
        // is built around, so it gets a genuine end-to-end happy-path assertion, not just an exit code.
        stageLocalArchive(target);
        Result r = sh(null, List.of(
                "ASSERT_INIT_FIXTURE=" + FIXTURE.toAbsolutePath(),
                "CAMEL_PACKAGE_TEST_MODE=true",
                "CAMEL_PACKAGE_TEST_VERSION=" + VERSION,
                "CAMEL_VALIDATE_TARGET_DIR=" + target.toAbsolutePath()),
                "/bin/bash " + VALIDATE.toAbsolutePath() + " local");

        assertThat(r.exit()).as(r.out()).isZero();
        assertThat(r.out())
                .contains("PASS: local archive version OK")
                .contains("camel init route content matches expected fixture")
                .doesNotContain("FAIL");
    }

    @Test
    void versionOverrideRequiresTestMode() throws Exception {
        // CAMEL_PACKAGE_TEST_VERSION without CAMEL_PACKAGE_TEST_MODE=true must be rejected (exit 2), so
        // production can never silently run against a synthetic version.
        Result r = sh(null, List.of("CAMEL_PACKAGE_TEST_VERSION=" + VERSION),
                "/bin/bash " + VALIDATE.toAbsolutePath() + " local");

        assertThat(r.exit()).as(r.out()).isEqualTo(2);
        assertThat(r.out()).contains("CAMEL_PACKAGE_TEST_VERSION requires CAMEL_PACKAGE_TEST_MODE=true");
    }

    @Test
    void unknownOptionIsUsageError() throws Exception {
        Result r = sh(null, List.of("PATH=/usr/bin:/bin"), "/bin/sh " + VALIDATE.toAbsolutePath() + " all --bogus");

        assertThat(r.exit()).as(r.out()).isEqualTo(2);
        assertThat(r.out()).contains("unknown option");
    }

    @Test
    void helpIsUsageError() throws Exception {
        Result r = sh(null, List.of("PATH=/usr/bin:/bin"), "/bin/sh " + VALIDATE.toAbsolutePath() + " help");

        assertThat(r.exit()).as(r.out()).isEqualTo(2);
        assertThat(r.out()).contains("Usage:");
    }

    @Test
    void dispatcherPropagatesValidatorFailure(@TempDir Path target) throws Exception {
        // A validator that FAILs (here the local archive reports the wrong version) must make the
        // dispatcher itself exit nonzero - the FAIL must not be swallowed by main's rc aggregation.
        stageLocalArchive(target, "9.9.9");
        Result r = sh(null, List.of(
                "ASSERT_INIT_FIXTURE=" + FIXTURE.toAbsolutePath(),
                "CAMEL_PACKAGE_TEST_MODE=true",
                "CAMEL_PACKAGE_TEST_VERSION=" + VERSION,
                "CAMEL_VALIDATE_TARGET_DIR=" + target.toAbsolutePath()),
                "/bin/bash " + VALIDATE.toAbsolutePath() + " local");

        assertThat(r.exit()).as(r.out()).isNotZero();
        assertThat(r.out()).contains("camel version mismatch").contains("FAIL");
    }

    @Test
    void assertUninstalledFlagsLeftoverButPassesWhenAbsent(@TempDir Path tmp) throws Exception {
        // Exercise the multi-arg assert_uninstalled helper directly: all-absent passes, a leftover fails.
        Path present = Files.writeString(tmp.resolve("leftover"), "x");
        Path absent = tmp.resolve("gone");

        Result ok = sh(null, List.of(), ". " + LIB.toAbsolutePath() + "; assert_uninstalled '" + absent + "'");
        assertThat(ok.exit()).as(ok.out()).isZero();
        assertThat(ok.out()).contains("does not exist");

        Result bad = sh(null, List.of(), ". " + LIB.toAbsolutePath() + "; assert_uninstalled '" + present + "'");
        assertThat(bad.exit()).as(bad.out()).isNotZero();
        assertThat(bad.out()).contains("uninstall left behind");
    }

    // ---------- helpers ----------

    /** Body of a conformant fake `camel` CLI: reports {@code version} and generates the fixture route on init. */
    private static String fakeCamelBody(String version) {
        return "#!/bin/sh\n"
               + "case \"$1\" in\n"
               + "  --version) echo 'Apache Camel " + version + "'; exit 0 ;;\n"
               + "  init) name=$(basename \"$2\" .java); "
               + "sed \"s/public class hello/public class $name/\" '" + FIXTURE.toAbsolutePath()
               + "' > \"$2\"; exit 0 ;;\n"
               + "  *) exit 3 ;;\n"
               + "esac\n";
    }

    private static Path writeFakeCamel(Path file, String version) throws IOException {
        return writeExecutable(file, fakeCamelBody(version));
    }

    private static Path writeExecutable(Path file, String body) throws IOException {
        Files.writeString(file, body, StandardCharsets.UTF_8);
        Files.setPosixFilePermissions(file, Set.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_EXECUTE));
        return file;
    }

    private static void stageLocalArchive(Path targetDir) throws Exception {
        stageLocalArchive(targetDir, PackageNativeValidationTest.VERSION);
    }

    /**
     * Builds {@code <targetDir>/camel-launcher-<VERSION>-bin.tar.gz} whose bin/camel.sh reports {@code reportedVersion}
     * (pass a value other than VERSION to drive a version-mismatch FAIL).
     */
    private static void stageLocalArchive(Path targetDir, String reportedVersion) throws Exception {
        Path work = Files.createTempDirectory("camel-archive");
        String root = "camel-launcher-" + PackageNativeValidationTest.VERSION;
        Path bin = work.resolve(root).resolve("bin");
        Files.createDirectories(bin);
        writeExecutable(bin.resolve("camel.sh"), fakeCamelBody(reportedVersion));

        Files.createDirectories(targetDir);
        Path archive = targetDir.resolve(root + "-bin.tar.gz");
        Result r = sh(work, List.of(), "tar czf '" + archive + "' '" + root + "'");
        assertThat(r.exit()).as("staging archive failed: " + r.out()).isZero();
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
        if (!completed) {
            p.destroyForcibly();
        }
        int exitCode = completed ? p.exitValue() : -1;

        return new Result(exitCode, out);
    }

    record Result(int exit, String out) {
    }
}
