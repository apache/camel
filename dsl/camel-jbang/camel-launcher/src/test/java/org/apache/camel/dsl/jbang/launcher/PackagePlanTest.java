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
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the channel -> packaging-plan mapping, LTS validation, and website-staging logic in camel-package.sh (POSIX)
 * and camel-package.bat (Windows). Platform-specific process launching lives in the nested classes; shared fixture
 * helpers and assertions live in the enclosing class.
 */
class PackagePlanTest {

    static final Path MODULE_DIR = Paths.get("").toAbsolutePath();
    static final String TEST_VERSION = "9.9.9";

    static final class Result {
        int exit;
        String stdout;
        String stderr;
    }

    Path writeReleaseFixture(String suffix, String content) throws IOException {
        Path target = MODULE_DIR.resolve("target");
        Files.createDirectories(target);
        Path file = target.resolve("camel-launcher-" + TEST_VERSION + suffix);
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    Path websiteDir() {
        return MODULE_DIR.resolve("target/jreleaser/website");
    }

    @AfterEach
    void cleanupFixtures() throws IOException {
        Files.deleteIfExists(MODULE_DIR.resolve("target/camel-launcher-" + TEST_VERSION + "-bin.tar.gz"));
        Files.deleteIfExists(MODULE_DIR.resolve("target/camel-launcher-" + TEST_VERSION + "-bin.zip"));
        deleteRecursively(websiteDir());
    }

    static void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (var stream = Files.walk(dir)) {
            stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
    }

    static String sha256Hex(Path file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(md.digest(Files.readAllBytes(file)));
    }

    // ── POSIX (camel-package.sh) ──────────────────────────────────────

    @Nested
    @DisabledOnOs(OS.WINDOWS)
    class Posix {

        private static final Path SCRIPT = Paths.get("src/jreleaser/bin/camel-package.sh");

        private Result run(String... args) throws Exception {
            return run(Map.of(), args);
        }

        private Result run(Map<String, String> extraEnv, String... args) throws Exception {
            List<String> cmd = new ArrayList<>();
            cmd.add("/bin/sh");
            cmd.add(SCRIPT.toString());
            for (String a : args) {
                cmd.add(a);
            }
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.environment().putAll(extraEnv);
            Process p = pb.start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String err = new String(p.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(p.waitFor(60, TimeUnit.SECONDS), "wrapper did not exit in time");
            Result r = new Result();
            r.exit = p.exitValue();
            r.stdout = out;
            r.stderr = err;
            return r;
        }

        private Map<String, String> testModeEnvWithMvnStub(Path tmp, Path recordFile) throws IOException {
            Path stubDir = tmp.resolve("stub-bin");
            Files.createDirectories(stubDir);
            Path mvnStub = stubDir.resolve("mvn");
            Files.writeString(mvnStub, "#!/bin/sh\nprintf '%s\\n' \"$*\" >> \"" + recordFile + "\"\nexit 0\n",
                    StandardCharsets.UTF_8);
            assertTrue(mvnStub.toFile().setExecutable(true));

            Map<String, String> env = new LinkedHashMap<>();
            env.put("CAMEL_PACKAGE_TEST_MODE", "true");
            env.put("CAMEL_PACKAGE_TEST_VERSION", TEST_VERSION);
            env.put("PATH", stubDir + File.pathSeparator + System.getenv("PATH"));
            return env;
        }

        @Test
        void stableSelectsAllFivePackagersWithSdkmanDefault() throws Exception {
            Result r = run("prepare", "--channel", "stable", "--print-plan");

            assertEquals(0, r.exit, r.stderr);
            assertTrue(r.stdout.contains("CHANNEL=stable"), r.stdout);
            assertTrue(r.stdout.contains("PACKAGERS=brew,sdkman,winget,scoop,chocolatey"), r.stdout);
            assertTrue(r.stdout.contains("BREW_FORMULA=camel\n") || r.stdout.contains("BREW_FORMULA=camel\r\n")
                    || r.stdout.trim().endsWith("BREW_FORMULA=camel"), r.stdout);
            assertTrue(r.stdout.contains("BREW_CLASS=Camel"), r.stdout);
            assertTrue(r.stdout.contains("SDKMAN_CANDIDATE=camel"), r.stdout);
            assertTrue(r.stdout.contains("SDKMAN_DEFAULT=true"), r.stdout);
            assertTrue(r.stdout.contains("WEBSITE_VERSION_MANIFEST=true"), r.stdout);
            assertTrue(r.stdout.contains("WEBSITE_LATEST=true"), r.stdout);
            assertFalse(r.stdout.contains("BREW_LTS_FORMULA="), "stable without --lts-line has no LTS formula");
        }

        @Test
        void stableWithLtsAddsVersionedBrewFormula() throws Exception {
            Result r = run("prepare", "--channel", "stable", "--lts-line", "4.18", "--print-plan");

            assertEquals(0, r.exit, r.stderr);
            assertTrue(r.stdout.contains("CHANNEL=stable"), r.stdout);
            assertTrue(r.stdout.contains("LTS_LINE=4.18"), r.stdout);
            assertTrue(r.stdout.contains("PACKAGERS=brew,sdkman,winget,scoop,chocolatey"), r.stdout);
            assertTrue(r.stdout.contains("BREW_LTS_FORMULA=camel@4.18"), r.stdout);
            assertTrue(r.stdout.contains("SDKMAN_DEFAULT=true"), r.stdout);
        }

        @Test
        void ltsSelectsFourPackagersExcludingScoopWithSdkmanNotDefault() throws Exception {
            Result r = run("prepare", "--channel", "lts", "--lts-line", "4.18", "--print-plan");

            assertEquals(0, r.exit, r.stderr);
            assertTrue(r.stdout.contains("CHANNEL=lts"), r.stdout);
            assertTrue(r.stdout.contains("PACKAGERS=brew,sdkman,winget,chocolatey"), r.stdout);
            assertFalse(r.stdout.contains("scoop"), "LTS maintenance excludes Scoop: " + r.stdout);
            assertTrue(r.stdout.contains("BREW_FORMULA=camel@4.18"),
                    "LTS produces a versioned brew formula: " + r.stdout);
            assertTrue(r.stdout.contains("BREW_CLASS=CamelAT418"),
                    "LTS formulaName must be a valid Ruby class name, pre-converted to Homebrew's AT convention: "
                                                                   + r.stdout);
            assertTrue(r.stdout.contains("SDKMAN_DEFAULT=false"), r.stdout);
            assertTrue(r.stdout.contains("WEBSITE_VERSION_MANIFEST=true"), r.stdout);
            assertTrue(r.stdout.contains("WEBSITE_LATEST=false"), r.stdout);
        }

        @Test
        void ltsChannelRequiresLtsLine() throws Exception {
            Result r = run("prepare", "--channel", "lts", "--print-plan");

            assertEquals(2, r.exit, "missing --lts-line for lts channel must be a usage error");
            assertTrue(r.stderr.toLowerCase().contains("lts-line"), r.stderr);
        }

        @Test
        void rejectsUnsupportedLtsLine() throws Exception {
            Result r = run("prepare", "--channel", "lts", "--lts-line", "3.14", "--print-plan");

            assertEquals(2, r.exit);
            assertTrue(r.stderr.toLowerCase().contains("not a supported lts line")
                    || r.stderr.contains("3.14"), r.stderr);
        }

        @Test
        void rejectsUnknownChannel() throws Exception {
            Result r = run("prepare", "--channel", "nightly", "--print-plan");

            assertEquals(2, r.exit);
            assertTrue(r.stderr.toLowerCase().contains("channel"), r.stderr);
        }

        @Test
        void rejectsUnknownSubcommand() throws Exception {
            Result r = run("frobnicate", "--channel", "stable", "--print-plan");

            assertEquals(2, r.exit);
        }

        @Test
        void stableStagesInstallersAndWritesWebsiteManifests(@TempDir Path tmp) throws Exception {
            Path tar = writeReleaseFixture("-bin.tar.gz", "fixture-tar");
            Path zip = writeReleaseFixture("-bin.zip", "fixture-zip");
            Path recordFile = tmp.resolve("mvn-calls.txt");
            Map<String, String> env = testModeEnvWithMvnStub(tmp, recordFile);

            Result r = run(env, "prepare", "--channel", "stable");

            assertEquals(0, r.exit, r.stderr);
            Path installSh = websiteDir().resolve("install.sh");
            Path installPs1 = websiteDir().resolve("install.ps1");
            assertArrayEquals(Files.readAllBytes(MODULE_DIR.resolve("src/install/install.sh")),
                    Files.readAllBytes(installSh));
            assertArrayEquals(Files.readAllBytes(MODULE_DIR.resolve("src/install/install.ps1")),
                    Files.readAllBytes(installPs1));

            Path versionManifest = websiteDir().resolve("camel-cli/releases/" + TEST_VERSION + ".properties");
            Path latestManifest = websiteDir().resolve("camel-cli/releases/latest.properties");
            assertTrue(Files.exists(versionManifest));
            assertTrue(Files.exists(latestManifest));
            String expected = "format=1\nversion=" + TEST_VERSION + "\ntar_sha256=" + sha256Hex(tar) + "\nzip_sha256="
                              + sha256Hex(zip) + "\n";
            assertEquals(expected, Files.readString(versionManifest, StandardCharsets.UTF_8));
            assertArrayEquals(Files.readAllBytes(versionManifest), Files.readAllBytes(latestManifest));

            assertTrue(Files.exists(recordFile),
                    "the JReleaser (stubbed mvn) step must be reached once staging succeeds");
            String recorded = Files.readString(recordFile, StandardCharsets.UTF_8);
            assertTrue(recorded.contains("jreleaser:config"), recorded);
        }

        @Test
        void ltsStagesInstallersButDoesNotWriteLatest(@TempDir Path tmp) throws Exception {
            writeReleaseFixture("-bin.tar.gz", "fixture-tar-lts");
            writeReleaseFixture("-bin.zip", "fixture-zip-lts");
            Path recordFile = tmp.resolve("mvn-calls.txt");
            Map<String, String> env = testModeEnvWithMvnStub(tmp, recordFile);

            Result r = run(env, "prepare", "--channel", "lts", "--lts-line", "4.18");

            assertEquals(0, r.exit, r.stderr);
            assertTrue(Files.exists(websiteDir().resolve("camel-cli/releases/" + TEST_VERSION + ".properties")));
            assertFalse(Files.exists(websiteDir().resolve("camel-cli/releases/latest.properties")),
                    "LTS prepare must not create or modify latest.properties");
            assertTrue(Files.exists(recordFile));
        }

        @Test
        void snapshotVersionFailsBeforeReachingJReleaser(@TempDir Path tmp) throws Exception {
            Path recordFile = tmp.resolve("mvn-calls.txt");
            Map<String, String> env = new LinkedHashMap<>(testModeEnvWithMvnStub(tmp, recordFile));
            env.put("CAMEL_PACKAGE_TEST_VERSION", "9.9.9-SNAPSHOT");

            Result r = run(env, "prepare", "--channel", "stable");

            assertNotEquals(0, r.exit, "a snapshot version must be rejected");
            assertFalse(Files.exists(recordFile), "JReleaser must never be invoked for a snapshot version");
            assertFalse(Files.exists(websiteDir()));
        }

        @Test
        void missingArtifactFailsBeforeReachingJReleaser(@TempDir Path tmp) throws Exception {
            Path recordFile = tmp.resolve("mvn-calls.txt");
            Map<String, String> env = testModeEnvWithMvnStub(tmp, recordFile);

            Result r = run(env, "prepare", "--channel", "stable");

            assertNotEquals(0, r.exit, "missing release artifacts must fail before JReleaser runs");
            assertFalse(Files.exists(recordFile));
            assertFalse(Files.exists(websiteDir()));
        }

        @Test
        void testVersionOverrideRequiresExplicitTestMode(@TempDir Path tmp) throws Exception {
            Path recordFile = tmp.resolve("mvn-calls.txt");
            Map<String, String> env = testModeEnvWithMvnStub(tmp, recordFile);
            env.remove("CAMEL_PACKAGE_TEST_MODE");

            Result r = run(env, "prepare", "--channel", "stable");

            assertEquals(2, r.exit,
                    "CAMEL_PACKAGE_TEST_VERSION alone (without test mode) must be a fatal usage error");
            assertTrue(r.stderr.toLowerCase().contains("test_mode"), r.stderr);
            assertFalse(Files.exists(recordFile));
        }
    }

    // ── Windows (camel-package.bat) ───────────────────────────────────

    @Nested
    @EnabledOnOs(OS.WINDOWS)
    class Windows {

        private static final Path SCRIPT = Paths.get("src/jreleaser/bin/camel-package.bat");

        private Result run(String... args) throws Exception {
            return run(Map.of(), args);
        }

        private Result run(Map<String, String> extraEnv, String... args) throws Exception {
            List<String> cmd = new ArrayList<>();
            cmd.add("cmd.exe");
            cmd.add("/c");
            cmd.add(SCRIPT.toString());
            for (String a : args) {
                cmd.add(a);
            }
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.environment().putAll(extraEnv);
            Process p = pb.start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String err = new String(p.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(p.waitFor(60, TimeUnit.SECONDS), "wrapper did not exit in time");
            Result r = new Result();
            r.exit = p.exitValue();
            r.stdout = out;
            r.stderr = err;
            return r;
        }

        private Map<String, String> testModeEnvWithMvnStub(Path tmp, Path recordFile) throws IOException {
            Path stubDir = tmp.resolve("stub-bin");
            Files.createDirectories(stubDir);
            Path mvnStub = stubDir.resolve("mvn.cmd");
            Files.writeString(mvnStub, "@echo off\r\necho %* >> \"" + recordFile + "\"\r\nexit /b 0\r\n",
                    StandardCharsets.UTF_8);

            Map<String, String> env = new LinkedHashMap<>();
            env.put("CAMEL_PACKAGE_TEST_MODE", "true");
            env.put("CAMEL_PACKAGE_TEST_VERSION", TEST_VERSION);
            env.put("PATH", stubDir + File.pathSeparator + System.getenv("PATH"));
            return env;
        }

        @Test
        void stableSelectsAllFivePackagersWithSdkmanDefault() throws Exception {
            Result r = run("prepare", "--channel", "stable", "--print-plan");

            assertEquals(0, r.exit, r.stderr);
            assertTrue(r.stdout.contains("PACKAGERS=brew,sdkman,winget,scoop,chocolatey"), r.stdout);
            assertTrue(r.stdout.contains("BREW_CLASS=Camel"), r.stdout);
            assertTrue(r.stdout.contains("SDKMAN_DEFAULT=true"), r.stdout);
            assertTrue(r.stdout.contains("WEBSITE_LATEST=true"), r.stdout);
            assertFalse(r.stdout.contains("BREW_LTS_FORMULA="), r.stdout);
        }

        @Test
        void ltsExcludesScoopWithSdkmanNotDefault() throws Exception {
            Result r = run("prepare", "--channel", "lts", "--lts-line", "4.18", "--print-plan");

            assertEquals(0, r.exit, r.stderr);
            assertTrue(r.stdout.contains("PACKAGERS=brew,sdkman,winget,chocolatey"), r.stdout);
            assertFalse(r.stdout.contains("scoop"), r.stdout);
            assertTrue(r.stdout.contains("BREW_FORMULA=camel@4.18"),
                    "LTS produces a versioned brew formula: " + r.stdout);
            assertTrue(r.stdout.contains("BREW_CLASS=CamelAT418"),
                    "LTS formulaName must be a valid Ruby class name, pre-converted to Homebrew's AT convention: "
                                                                   + r.stdout);
            assertTrue(r.stdout.contains("SDKMAN_DEFAULT=false"), r.stdout);
            assertTrue(r.stdout.contains("WEBSITE_LATEST=false"), r.stdout);
        }

        @Test
        void rejectsUnsupportedLtsLine() throws Exception {
            Result r = run("prepare", "--channel", "lts", "--lts-line", "3.14", "--print-plan");

            assertEquals(2, r.exit);
        }

        @Test
        void stableStagesInstallersAndWritesWebsiteManifests(@TempDir Path tmp) throws Exception {
            Path tar = writeReleaseFixture("-bin.tar.gz", "fixture-tar");
            Path zip = writeReleaseFixture("-bin.zip", "fixture-zip");
            Path recordFile = tmp.resolve("mvn-calls.txt");
            Map<String, String> env = testModeEnvWithMvnStub(tmp, recordFile);

            Result r = run(env, "prepare", "--channel", "stable");

            assertEquals(0, r.exit, r.stderr);
            Path installSh = websiteDir().resolve("install.sh");
            Path installPs1 = websiteDir().resolve("install.ps1");
            assertArrayEquals(Files.readAllBytes(MODULE_DIR.resolve("src/install/install.sh")),
                    Files.readAllBytes(installSh));
            assertArrayEquals(Files.readAllBytes(MODULE_DIR.resolve("src/install/install.ps1")),
                    Files.readAllBytes(installPs1));

            Path versionManifest = websiteDir().resolve("camel-cli/releases/" + TEST_VERSION + ".properties");
            Path latestManifest = websiteDir().resolve("camel-cli/releases/latest.properties");
            assertTrue(Files.exists(versionManifest));
            assertTrue(Files.exists(latestManifest));
            String expected = "format=1\nversion=" + TEST_VERSION + "\ntar_sha256=" + sha256Hex(tar) + "\nzip_sha256="
                              + sha256Hex(zip) + "\n";
            assertEquals(expected, Files.readString(versionManifest, StandardCharsets.UTF_8));
            assertArrayEquals(Files.readAllBytes(versionManifest), Files.readAllBytes(latestManifest));

            assertTrue(Files.exists(recordFile),
                    "the JReleaser (stubbed mvn) step must be reached once staging succeeds");
            String recorded = Files.readString(recordFile, StandardCharsets.UTF_8);
            assertTrue(recorded.contains("jreleaser:config"), recorded);
        }

        @Test
        void ltsStagesInstallersButDoesNotWriteLatest(@TempDir Path tmp) throws Exception {
            writeReleaseFixture("-bin.tar.gz", "fixture-tar-lts");
            writeReleaseFixture("-bin.zip", "fixture-zip-lts");
            Path recordFile = tmp.resolve("mvn-calls.txt");
            Map<String, String> env = testModeEnvWithMvnStub(tmp, recordFile);

            Result r = run(env, "prepare", "--channel", "lts", "--lts-line", "4.18");

            assertEquals(0, r.exit, r.stderr);
            assertTrue(Files.exists(websiteDir().resolve("camel-cli/releases/" + TEST_VERSION + ".properties")));
            assertFalse(Files.exists(websiteDir().resolve("camel-cli/releases/latest.properties")),
                    "LTS prepare must not create or modify latest.properties");
            assertTrue(Files.exists(recordFile));
        }

        @Test
        void snapshotVersionFailsBeforeReachingJReleaser(@TempDir Path tmp) throws Exception {
            Path recordFile = tmp.resolve("mvn-calls.txt");
            Map<String, String> env = new LinkedHashMap<>(testModeEnvWithMvnStub(tmp, recordFile));
            env.put("CAMEL_PACKAGE_TEST_VERSION", "9.9.9-SNAPSHOT");

            Result r = run(env, "prepare", "--channel", "stable");

            assertNotEquals(0, r.exit, "a snapshot version must be rejected");
            assertFalse(Files.exists(recordFile), "JReleaser must never be invoked for a snapshot version");
            assertFalse(Files.exists(websiteDir()));
        }

        @Test
        void missingArtifactFailsBeforeReachingJReleaser(@TempDir Path tmp) throws Exception {
            Path recordFile = tmp.resolve("mvn-calls.txt");
            Map<String, String> env = testModeEnvWithMvnStub(tmp, recordFile);

            Result r = run(env, "prepare", "--channel", "stable");

            assertNotEquals(0, r.exit, "missing release artifacts must fail before JReleaser runs");
            assertFalse(Files.exists(recordFile));
            assertFalse(Files.exists(websiteDir()));
        }

        @Test
        void testVersionOverrideRequiresExplicitTestMode(@TempDir Path tmp) throws Exception {
            Path recordFile = tmp.resolve("mvn-calls.txt");
            Map<String, String> env = testModeEnvWithMvnStub(tmp, recordFile);
            env.remove("CAMEL_PACKAGE_TEST_MODE");

            Result r = run(env, "prepare", "--channel", "stable");

            assertEquals(2, r.exit,
                    "CAMEL_PACKAGE_TEST_VERSION alone (without test mode) must be a fatal usage error");
            assertFalse(Files.exists(recordFile));
        }
    }
}
