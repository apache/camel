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

import java.io.File;
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
    void allDispatcherAggregatesAFailureAcrossValidators(@TempDir Path target) throws Exception {
        // Unlike dispatcherPropagatesValidatorFailure above (which calls the `local` validator
        // directly), this drives main()'s actual `all` dispatch: validate_local_archive || rc=1;
        // validate_homebrew || rc=1; validate_sdkman || rc=1. Nothing is staged for homebrew/SDKMAN,
        // so they SKIP - proving the local FAIL doesn't short-circuit the remaining legs and still
        // survives to the aggregated exit code.
        stageLocalArchive(target, "9.9.9");
        Result r = sh(null, List.of(
                "ASSERT_INIT_FIXTURE=" + FIXTURE.toAbsolutePath(),
                "CAMEL_PACKAGE_TEST_MODE=true",
                "CAMEL_PACKAGE_TEST_VERSION=" + VERSION,
                "CAMEL_VALIDATE_TARGET_DIR=" + target.toAbsolutePath()),
                "/bin/bash " + VALIDATE.toAbsolutePath() + " all");

        assertThat(r.exit()).as(r.out()).isNotZero();
        assertThat(r.out())
                .contains("camel version mismatch")
                .contains("FAIL")
                .contains("SKIP: homebrew formula not found")
                // `sdk` is a shell function sourced from sdkman-init.sh, not a PATH binary, so a bare
                // subprocess (this test's `sh`, and CI's separate "Run POSIX validator unit tests" step)
                // always hits the host-gate SKIP rather than the descriptor-not-found one.
                .contains("SKIP: sdkman not available");
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

    @Test
    void homebrewValidatorFailsWhenAuditStrictReportsIssues(@TempDir Path tmp) throws Exception {
        // The PR's own hardening goal is making `brew audit --strict` a hard gate; pin that a nonzero
        // audit exit propagates as a FAIL and a nonzero overall exit, not a warning.
        Path fakeBinDir = Files.createDirectory(tmp.resolve("fake-bin"));
        writeFakeBrew(fakeBinDir.resolve("brew"));
        Path target = Files.createDirectory(tmp.resolve("target"));
        Path tapDir = Files.createDirectory(tmp.resolve("tap"));
        stageHomebrewFormula(target, "camel-launcher-9.9.9-bin.tar.gz");

        Result r = sh(null, List.of(
                "PATH=" + fakeBinDir.toAbsolutePath() + File.pathSeparator + System.getenv("PATH"),
                "CAMEL_PACKAGE_TEST_MODE=true",
                "CAMEL_PACKAGE_TEST_VERSION=" + VERSION,
                "CAMEL_VALIDATE_TARGET_DIR=" + target.toAbsolutePath(),
                "FAKE_BREW_TAP_DIR=" + tapDir.toAbsolutePath(),
                "FAKE_BREW_AUDIT_RC=1",
                "FAKE_BREW_AUDIT_OUTPUT=Error: FormulaAudit/Foo: fake audit issue"),
                "/bin/bash " + VALIDATE.toAbsolutePath() + " homebrew");

        assertThat(r.exit()).as(r.out()).isNotZero();
        assertThat(r.out()).contains("FAIL: homebrew audit --strict reported issues (exit 1)");
    }

    @Test
    void homebrewValidatorSkipsWhenJavaDependencyMissing(@TempDir Path tmp) throws Exception {
        // A `brew install` failure caused by a missing/incompatible Java on the host is an environment
        // limitation, not a defect in the generated formula, and must SKIP (exit 0), not FAIL - this pins
        // the classification heuristic so a real regression can't get silently downgraded the same way.
        Path fakeBinDir = Files.createDirectory(tmp.resolve("fake-bin"));
        writeFakeBrew(fakeBinDir.resolve("brew"));
        Path target = Files.createDirectory(tmp.resolve("target"));
        Path tapDir = Files.createDirectory(tmp.resolve("tap"));
        stageHomebrewFormula(target, "camel-launcher-9.9.9-bin.tar.gz");

        Result r = sh(null, List.of(
                "PATH=" + fakeBinDir.toAbsolutePath() + File.pathSeparator + System.getenv("PATH"),
                "CAMEL_PACKAGE_TEST_MODE=true",
                "CAMEL_PACKAGE_TEST_VERSION=" + VERSION,
                "CAMEL_VALIDATE_TARGET_DIR=" + target.toAbsolutePath(),
                "FAKE_BREW_TAP_DIR=" + tapDir.toAbsolutePath(),
                "FAKE_BREW_INSTALL_RC=1",
                "FAKE_BREW_INSTALL_OUTPUT=Error: no suitable java version found, please install java"),
                "/bin/bash " + VALIDATE.toAbsolutePath() + " homebrew");

        assertThat(r.exit()).as(r.out()).isZero();
        assertThat(r.out())
                .contains("SKIP: homebrew install could not resolve a Java dependency")
                .doesNotContain("FAIL");
    }

    @Test
    void homebrewAuditFailureSurvivesASubsequentInstallSkip(@TempDir Path tmp) throws Exception {
        // A failed `brew audit --strict` is a real formula defect (see
        // homebrewValidatorFailsWhenAuditStrictReportsIssues above). If install *also* hits an
        // unrelated environment limitation (missing Java) afterwards, that SKIP must not erase the
        // already-recorded audit failure - the two conditions are independent and both can be true at
        // once. Pins the fix for validate_homebrew's SKIP branches returning the accumulated $rc
        // instead of an unconditional 0.
        Path fakeBinDir = Files.createDirectory(tmp.resolve("fake-bin"));
        writeFakeBrew(fakeBinDir.resolve("brew"));
        Path target = Files.createDirectory(tmp.resolve("target"));
        Path tapDir = Files.createDirectory(tmp.resolve("tap"));
        stageHomebrewFormula(target, "camel-launcher-9.9.9-bin.tar.gz");

        Result r = sh(null, List.of(
                "PATH=" + fakeBinDir.toAbsolutePath() + File.pathSeparator + System.getenv("PATH"),
                "CAMEL_PACKAGE_TEST_MODE=true",
                "CAMEL_PACKAGE_TEST_VERSION=" + VERSION,
                "CAMEL_VALIDATE_TARGET_DIR=" + target.toAbsolutePath(),
                "FAKE_BREW_TAP_DIR=" + tapDir.toAbsolutePath(),
                "FAKE_BREW_AUDIT_RC=1",
                "FAKE_BREW_AUDIT_OUTPUT=Error: FormulaAudit/Foo: fake audit issue",
                "FAKE_BREW_INSTALL_RC=1",
                "FAKE_BREW_INSTALL_OUTPUT=Error: no suitable java version found, please install java"),
                "/bin/bash " + VALIDATE.toAbsolutePath() + " homebrew");

        assertThat(r.exit()).as(r.out()).isNotZero();
        assertThat(r.out())
                .contains("FAIL: homebrew audit --strict reported issues (exit 1)")
                .contains("SKIP: homebrew install could not resolve a Java dependency");
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

    /**
     * A fake {@code brew} that stands in for Homebrew in {@code validate_homebrew} so its SKIP-vs-FAIL classification
     * heuristics can be pinned without needing a real Homebrew install. Behavior for the {@code audit} and
     * {@code install} subcommands is driven by {@code FAKE_BREW_AUDIT_RC}/{@code
     * FAKE_BREW_AUDIT_OUTPUT} and {@code FAKE_BREW_INSTALL_RC}/{@code FAKE_BREW_INSTALL_OUTPUT}; the tap directory it
     * reports is {@code FAKE_BREW_TAP_DIR}. Every other subcommand it needs (tap-new, untap, style, uninstall) just
     * succeeds.
     */
    private static Path writeFakeBrew(Path file) throws IOException {
        return writeExecutable(file, "#!/bin/sh\n"
                                     + "case \"$1\" in\n"
                                     + "    tap-new) mkdir -p \"$FAKE_BREW_TAP_DIR/Formula\"; exit 0 ;;\n"
                                     + "    --repository) echo \"$FAKE_BREW_TAP_DIR\"; exit 0 ;;\n"
                                     + "    untap) exit 0 ;;\n"
                                     + "    style) exit 0 ;;\n"
                                     + "    audit)\n"
                                     + "        [ -n \"$FAKE_BREW_AUDIT_OUTPUT\" ] && echo \"$FAKE_BREW_AUDIT_OUTPUT\"\n"
                                     + "        exit \"${FAKE_BREW_AUDIT_RC:-0}\" ;;\n"
                                     + "    install)\n"
                                     + "        [ -n \"$FAKE_BREW_INSTALL_OUTPUT\" ] && echo \"$FAKE_BREW_INSTALL_OUTPUT\"\n"
                                     + "        exit \"${FAKE_BREW_INSTALL_RC:-0}\" ;;\n"
                                     + "    uninstall) exit 0 ;;\n"
                                     + "    *) exit 0 ;;\n"
                                     + "esac\n");
    }

    /**
     * Stages {@code <targetDir>/jreleaser/package/camel-cli/brew/Formula/camel.rb} (the path {@code validate_homebrew}
     * reads) plus a same-named dummy archive under {@code targetDir}, so the script's real {@code sed}-based url/sha256
     * rewrite for the offline install has something to operate on.
     */
    private static void stageHomebrewFormula(Path targetDir, String archiveBasename) throws Exception {
        Path formulaDir = Files.createDirectories(targetDir.resolve("jreleaser/package/camel-cli/brew/Formula"));
        Files.writeString(targetDir.resolve(archiveBasename), "dummy archive content");
        String formula = "class Camel < Formula\n"
                         + "  url \"https://repo1.maven.org/maven2/org/apache/camel/camel-launcher/9.9.9/"
                         + archiveBasename + "\"\n"
                         + "  sha256 \"" + "0".repeat(64) + "\"\n"
                         + "  version \"9.9.9\"\n"
                         + "end\n";
        Files.writeString(formulaDir.resolve("camel.rb"), formula);
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
