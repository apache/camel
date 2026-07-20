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
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the channel -> packaging-plan mapping, LTS validation, and website-staging logic in camel-package.sh.
 */
class PackagePlanTest {

    static final Path MODULE_DIR = Paths.get("").toAbsolutePath();
    static final String TEST_VERSION = "9.9.9";
    static final Path PACKAGE_DIR = MODULE_DIR.resolve("target/jreleaser/package");

    // Synthetic LTS allowlist (see supported-lts-test-fixture.yml), decoupled from the real
    // supported-lts.yml so LTS-expiry assertions never expire on their own wall-clock date.
    static final Path SUPPORTED_LTS_FIXTURE = MODULE_DIR.resolve("src/test/resources/supported-lts-test-fixture.yml");
    static final String LTS_LINE_FUTURE = "9.9";
    static final String LTS_LINE_EXPIRED = "1.0";

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

    Map<String, String> supportedLtsFixtureEnv() {
        Map<String, String> env = new LinkedHashMap<>();
        env.put("CAMEL_PACKAGE_TEST_MODE", "true");
        env.put("CAMEL_PACKAGE_TEST_SUPPORTED_LTS", SUPPORTED_LTS_FIXTURE.toString());
        return env;
    }

    @AfterEach
    void cleanupFixtures() throws IOException {
        Files.deleteIfExists(MODULE_DIR.resolve("target/camel-launcher-" + TEST_VERSION + "-bin.tar.gz"));
        Files.deleteIfExists(MODULE_DIR.resolve("target/camel-launcher-" + TEST_VERSION + "-bin.zip"));
        deleteRecursively(websiteDir());
        deleteRecursively(PACKAGE_DIR);
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

    @Test
    void productionSupportedLtsEntryIsDocumentedBeforeExpiry() throws Exception {
        String supportedLts = Files.readString(MODULE_DIR.resolve("src/jreleaser/supported-lts.yml"), StandardCharsets.UTF_8);

        assertTrue(supportedLts.contains("line: \"4.22\""), supportedLts);
        assertTrue(supportedLts.contains("supportEnds: \"2027-09-30\""),
                "The real 4.22 LTS entry is intentionally checked here, but plan tests use a synthetic future-dated"
                                                                         + " fixture so they do not expire on 2027-10-01.");
        assertTrue(LocalDate.parse("2027-09-30").isAfter(LocalDate.parse("2026-07-19")),
                "Update this test when changing the documented 4.22 support window.");
    }

    @Test
    void publicArchivePackagersDoNotRemoveNativeExecutables() throws Exception {
        String chocolatey = Files.readString(MODULE_DIR.resolve(
                "src/jreleaser/distributions/camel-cli/chocolatey/tools/chocolateyinstall.ps1.tpl"),
                StandardCharsets.UTF_8);
        String scoop = Files.readString(
                MODULE_DIR.resolve("src/jreleaser/distributions/camel-cli/scoop/manifest.json.tpl"),
                StandardCharsets.UTF_8);

        assertFalse(chocolatey.contains("camel-x64.exe"), chocolatey);
        assertFalse(chocolatey.contains("camel-arm64.exe"), chocolatey);
        assertFalse(scoop.contains("camel-x64.exe"), scoop);
        assertFalse(scoop.contains("camel-arm64.exe"), scoop);
    }

    @Test
    void nativeExecutablesAreConfinedToWingetArchive() throws Exception {
        String publicAssembly = Files.readString(MODULE_DIR.resolve("src/main/assembly/bin.xml"), StandardCharsets.UTF_8);
        String wingetAssembly
                = Files.readString(MODULE_DIR.resolve("src/main/assembly/winget-bin.xml"), StandardCharsets.UTF_8);
        String pom = Files.readString(MODULE_DIR.resolve("pom.xml"), StandardCharsets.UTF_8);
        int nativeProfile = pom.indexOf("<id>include-camel-exe</id>");

        assertFalse(publicAssembly.contains("camel-x64.exe"),
                "the public ZIP/TAR assembly must not expose WinGet-only native executables");
        assertFalse(publicAssembly.contains("camel-arm64.exe"),
                "the public ZIP/TAR assembly must not expose WinGet-only native executables");
        assertTrue(wingetAssembly.contains("camel-x64.exe"), wingetAssembly);
        assertTrue(wingetAssembly.contains("camel-arm64.exe"), wingetAssembly);
        assertTrue(wingetAssembly.contains("<include>*.jar</include>"),
                "the WinGet archive must retain the launcher JAR beside camel.bat");
        assertTrue(wingetAssembly.contains("<include>camel.bat</include>"),
                "the native bootstrap delegates to camel.bat beside its resolved target");
        assertTrue(wingetAssembly.contains("<format>zip</format>"), wingetAssembly);
        assertFalse(wingetAssembly.contains("<format>tar.gz</format>"), wingetAssembly);
        assertTrue(nativeProfile >= 0, "the native executable profile must exist");
        assertFalse(pom.substring(0, nativeProfile).contains("winget-bin.xml"),
                "ordinary builds must not create an incomplete WinGet archive");
        String nativeProfilePom = pom.substring(nativeProfile);
        int wingetExecutionStart = nativeProfilePom.indexOf("<id>assemble-winget-bin</id>");
        assertTrue(wingetExecutionStart >= 0, "the native executable profile must create the WinGet archive");
        int wingetExecutionEnd = nativeProfilePom.indexOf("</execution>", wingetExecutionStart);
        assertTrue(wingetExecutionEnd >= 0, "the WinGet assembly execution must be complete");
        String wingetExecution = nativeProfilePom.substring(wingetExecutionStart, wingetExecutionEnd);

        assertTrue(wingetExecution.contains("<descriptor>src/main/assembly/winget-bin.xml</descriptor>"),
                wingetExecution);
        assertTrue(wingetExecution.contains("<attach>false</attach>"),
                "the WinGet payload must remain a local release file, not an attached Maven artifact");
    }

    @Test
    void wingetUsesDedicatedJreleaserDistribution() throws Exception {
        String config = Files.readString(MODULE_DIR.resolve("jreleaser.yml"), StandardCharsets.UTF_8);

        assertTrue(config.contains("camel-cli-winget:"), config);
        assertTrue(config.contains("camel-launcher-{{projectVersion}}-winget-bin.zip"), config);
        assertFalse(config.substring(config.indexOf("camel-cli:"), config.indexOf("camel-cli-winget:"))
                .contains("winget:"), "the public distribution must not generate the WinGet package");
    }

    @Test
    void nativeWorkflowChecksReproducibilityAndMavenExclusion() throws Exception {
        String workflow = Files.readString(
                MODULE_DIR.resolve("../../../.github/workflows/camel-launcher-native-exe.yml").normalize(),
                StandardCharsets.UTF_8);

        assertTrue(workflow.contains("-name 'camel-launcher-*-winget-bin.zip'"), workflow);
        assertTrue(workflow.contains("sha256sum --check \"$HASH_FILE\""), workflow);
        assertTrue(workflow.contains("-DaltSnapshotDeploymentRepository=\"winget-check::file://$MAVEN_REPO\""),
                workflow);
        assertTrue(workflow.contains("-name '*-winget-bin.zip'"), workflow);
        assertTrue(workflow.contains("-name '*-winget-bin.zip.sha1'"), workflow);
    }

    @Test
    void websiteWindowsInstallerUsesArchitectureNeutralBatchLauncher() throws Exception {
        String installer = Files.readString(MODULE_DIR.resolve("src/install/install.ps1"), StandardCharsets.UTF_8);

        assertTrue(installer.contains("bin\\camel.bat"), installer);
        assertFalse(installer.contains("camel-x64.exe"), installer);
        assertFalse(installer.contains("camel-arm64.exe"), installer);
        assertFalse(installer.contains("PROCESSOR_ARCHITECTURE"), installer);
    }

    @Test
    void scoopAutoupdateUsesPublishedMavenCentralSha1Sidecar() throws Exception {
        String scoop = Files.readString(
                MODULE_DIR.resolve("src/jreleaser/distributions/camel-cli/scoop/manifest.json.tpl"),
                StandardCharsets.UTF_8);

        assertTrue(scoop.contains("\"url\": \"$url.sha1\""),
                "Scoop autoupdate should use the SHA-1 sidecar that Maven Central publishes for camel-launcher"
                                                             + " classified artifacts.");
    }

    @Test
    void wingetInstallerManifestUsesLatestAcceptedSchema() throws Exception {
        String winget = Files.readString(
                MODULE_DIR.resolve("src/jreleaser/distributions/camel-cli/winget/installer.yaml.tpl"),
                StandardCharsets.UTF_8);

        assertTrue(winget.contains("winget-manifest.installer.1.12.0.schema.json"), winget);
        assertTrue(winget.contains("ManifestVersion: 1.12.0"), winget);
    }

    private static final Path SCRIPT = Paths.get("src/jreleaser/bin/camel-package.sh");

    private Result run(String... args) throws Exception {
        return run(Map.of(), args);
    }

    private Result run(Map<String, String> extraEnv, String... args) throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add("bash");
        cmd.add(SCRIPT.toString());
        Collections.addAll(cmd, args);
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

    @Test
    void posixWrapperUsesBashWithPipefail() throws Exception {
        String script = Files.readString(SCRIPT, StandardCharsets.UTF_8);

        assertTrue(script.startsWith("#!/usr/bin/env bash\n"), script);
        assertTrue(script.contains("set -euo pipefail"), script);
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

    private Map<String, String> envWithMvnStubProducingFormula(Path tmp, Path recordFile, String formulaName)
            throws IOException {
        Path stubDir = tmp.resolve("stub-bin");
        Files.createDirectories(stubDir);
        Path formulaDir = PACKAGE_DIR.resolve("camel-cli/brew/Formula");
        Path mvnStub = stubDir.resolve("mvn");
        Files.writeString(mvnStub,
                "#!/bin/sh\n"
                                   + "case \"$*\" in\n"
                                   + "  *evaluate*) printf '%s\\n' '" + TEST_VERSION + "' ; exit 0 ;;\n"
                                   + "esac\n"
                                   + "printf '%s\\n' \"$*\" >> \"" + recordFile + "\"\n"
                                   + "mkdir -p \"" + formulaDir + "\"\n"
                                   + "cat > \"" + formulaDir.resolve(formulaName) + "\" <<'EOF'\n"
                                   + "  url \"https://example.invalid/original.zip\"\n"
                                   + "  version \"" + TEST_VERSION + "\"\n"
                                   + "  sha256 \"original\"\n"
                                   + "  assert_match \"" + TEST_VERSION + "\", output\n"
                                   + "EOF\n"
                                   + "exit 0\n",
                StandardCharsets.UTF_8);
        assertTrue(mvnStub.toFile().setExecutable(true));

        Map<String, String> env = new LinkedHashMap<>();
        env.put("PATH", stubDir + File.pathSeparator + System.getenv("PATH"));
        return env;
    }

    private Map<String, String> productionStyleEnvWithMvnStub(Path tmp, Path recordFile) throws IOException {
        Path stubDir = tmp.resolve("stub-bin");
        Files.createDirectories(stubDir);
        Path mvnStub = stubDir.resolve("mvn");
        Files.writeString(mvnStub,
                "#!/bin/sh\n"
                                   + "case \"$*\" in\n"
                                   + "  *evaluate*) printf '%s\\n' '" + TEST_VERSION + "' ; exit 0 ;;\n"
                                   + "esac\n"
                                   + "printf '<%s>\\n' \"$@\" > \"" + recordFile + "\"\n"
                                   + "exit 0\n",
                StandardCharsets.UTF_8);
        assertTrue(mvnStub.toFile().setExecutable(true));

        return Map.of("PATH", stubDir + File.pathSeparator + System.getenv("PATH"));
    }

    private void addFailingCurlStub(Path tmp, Map<String, String> env, Path curlRecordFile) throws IOException {
        Path stubDir = tmp.resolve("curl-stub-bin");
        Files.createDirectories(stubDir);
        Path curlStub = stubDir.resolve("curl");
        Files.writeString(curlStub,
                "#!/bin/sh\nprintf '%s\\n' \"$*\" >> \"" + curlRecordFile + "\"\nexit 99\n",
                StandardCharsets.UTF_8);
        assertTrue(curlStub.toFile().setExecutable(true));
        env.put("PATH", stubDir + File.pathSeparator + env.getOrDefault("PATH", System.getenv("PATH")));
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
        Result r = run(supportedLtsFixtureEnv(), "prepare", "--channel", "stable", "--lts-line", LTS_LINE_FUTURE,
                "--print-plan");

        assertEquals(0, r.exit, r.stderr);
        assertTrue(r.stdout.contains("CHANNEL=stable"), r.stdout);
        assertTrue(r.stdout.contains("LTS_LINE=" + LTS_LINE_FUTURE), r.stdout);
        assertTrue(r.stdout.contains("PACKAGERS=brew,sdkman,winget,scoop,chocolatey"), r.stdout);
        assertTrue(r.stdout.contains("BREW_LTS_FORMULA=camel@" + LTS_LINE_FUTURE), r.stdout);
        assertTrue(r.stdout.contains("SDKMAN_DEFAULT=true"), r.stdout);
    }

    @Test
    void ltsSelectsFourPackagersExcludingScoopWithSdkmanNotDefault() throws Exception {
        // Deliberately reads the real production supported-lts.yml (no CAMEL_PACKAGE_TEST_SUPPORTED_LTS
        // override) so an accidental deletion/typo of the real "4.22" entry is caught here.
        Result r = run("prepare", "--channel", "lts", "--lts-line", "4.22", "--print-plan");

        assertEquals(0, r.exit, r.stderr);
        assertTrue(r.stdout.contains("CHANNEL=lts"), r.stdout);
        assertTrue(r.stdout.contains("PACKAGERS=brew,sdkman,winget,chocolatey"), r.stdout);
        assertFalse(r.stdout.contains("scoop"), "LTS maintenance excludes Scoop: " + r.stdout);
        assertTrue(r.stdout.contains("BREW_FORMULA=camel@4.22"),
                "LTS produces a versioned brew formula: " + r.stdout);
        assertTrue(r.stdout.contains("BREW_CLASS=CamelAT422"),
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
    void rejectsExpiredLtsLine() throws Exception {
        Result r = run(supportedLtsFixtureEnv(), "prepare", "--channel", "lts", "--lts-line", LTS_LINE_EXPIRED,
                "--print-plan");

        assertEquals(2, r.exit);
        assertTrue(r.stderr.toLowerCase().contains("support ended") || r.stderr.contains(LTS_LINE_EXPIRED),
                r.stderr);
    }

    @Test
    void rejectsMalformedSupportedLtsMetadata() throws Exception {
        Map<String, String> env = new LinkedHashMap<>();
        env.put("CAMEL_PACKAGE_TEST_MODE", "true");
        env.put("CAMEL_PACKAGE_TEST_SUPPORTED_LTS", MODULE_DIR.resolve("src/test/resources/bad.yaml").toString());

        Result r = run(env, "prepare", "--channel", "lts", "--lts-line", LTS_LINE_FUTURE, "--print-plan");

        assertEquals(1, r.exit);
        assertTrue(r.stderr.toLowerCase().contains("malformed"), r.stderr);
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
        assertTrue(recorded.contains("jreleaser:package"), recorded);
        assertTrue(recorded.contains("-Djreleaser.packagers=brew,sdkman,winget,scoop,chocolatey"), recorded);
        assertTrue(recorded.contains("-Djreleaser.project.snapshot.pattern="), recorded);
    }

    @Test
    void productionPrepareDoesNotPassAnEmptyMavenArgument(@TempDir Path tmp) throws Exception {
        writeReleaseFixture("-bin.tar.gz", "fixture-tar");
        writeReleaseFixture("-bin.zip", "fixture-zip");
        Path recordFile = tmp.resolve("mvn-args.txt");

        Result r = run(productionStyleEnvWithMvnStub(tmp, recordFile), "prepare", "--channel", "stable");

        assertEquals(0, r.exit, r.stderr);
        String recorded = Files.readString(recordFile, StandardCharsets.UTF_8);
        assertFalse(recorded.contains("<>"),
                "production prepare must omit the unused snapshot-pattern argument:\n" + recorded);
        assertTrue(recorded.contains("<-Djreleaser.distributions=camel-cli,camel-cli-winget>"), recorded);
    }

    @Test
    void ltsStagesInstallersButDoesNotWriteLatest(@TempDir Path tmp) throws Exception {
        writeReleaseFixture("-bin.tar.gz", "fixture-tar-lts");
        writeReleaseFixture("-bin.zip", "fixture-zip-lts");
        Path recordFile = tmp.resolve("mvn-calls.txt");
        Map<String, String> env = testModeEnvWithMvnStub(tmp, recordFile);
        env.putAll(supportedLtsFixtureEnv());

        Result r = run(env, "prepare", "--channel", "lts", "--lts-line", LTS_LINE_FUTURE);

        assertEquals(0, r.exit, r.stderr);
        assertTrue(Files.exists(websiteDir().resolve("camel-cli/releases/" + TEST_VERSION + ".properties")));
        assertFalse(Files.exists(websiteDir().resolve("camel-cli/releases/latest.properties")),
                "LTS prepare must not create or modify latest.properties");
        assertTrue(Files.exists(recordFile));
    }

    @Test
    void ltsPrepareRenamesGeneratedHomebrewFormula(@TempDir Path tmp) throws Exception {
        writeReleaseFixture("-bin.tar.gz", "fixture-tar-lts");
        writeReleaseFixture("-bin.zip", "fixture-zip-lts");
        Path recordFile = tmp.resolve("mvn-calls.txt");
        Map<String, String> env = envWithMvnStubProducingFormula(tmp, recordFile, "camel-at-99.rb");
        env.put("CAMEL_PACKAGE_TEST_MODE", "true");
        env.put("CAMEL_PACKAGE_TEST_VERSION", TEST_VERSION);
        env.putAll(supportedLtsFixtureEnv());

        Result r = run(env, "prepare", "--channel", "lts", "--lts-line", LTS_LINE_FUTURE);

        assertEquals(0, r.exit, r.stderr);
        Path formulaDir = PACKAGE_DIR.resolve("camel-cli/brew/Formula");
        assertFalse(Files.exists(formulaDir.resolve("camel-at-99.rb")),
                "JReleaser's generated LTS filename must not be left behind");
        assertTrue(Files.exists(formulaDir.resolve("camel@" + LTS_LINE_FUTURE + ".rb")),
                "LTS Homebrew formula must use Homebrew's versioned-formula filename");
    }

    @Test
    void testModeWithoutSyntheticVersionDoesNotPatchGeneratedHomebrewFormula(@TempDir Path tmp) throws Exception {
        writeReleaseFixture("-bin.tar.gz", "fixture-tar");
        writeReleaseFixture("-bin.zip", "fixture-zip");
        Path recordFile = tmp.resolve("mvn-calls.txt");
        Path curlRecordFile = tmp.resolve("curl-calls.txt");
        Map<String, String> env = envWithMvnStubProducingFormula(tmp, recordFile, "camel.rb");
        env.put("CAMEL_PACKAGE_TEST_MODE", "true");
        addFailingCurlStub(tmp, env, curlRecordFile);

        Result r = run(env, "prepare", "--channel", "stable");

        assertEquals(0, r.exit, r.stderr);
        assertFalse(Files.exists(curlRecordFile),
                "Formula patching must require CAMEL_PACKAGE_TEST_VERSION, not CAMEL_PACKAGE_TEST_MODE alone");
        Path formulaFile = PACKAGE_DIR.resolve("camel-cli/brew/Formula/camel.rb");
        assertTrue(Files.readString(formulaFile, StandardCharsets.UTF_8).contains("version \"" + TEST_VERSION + "\""));
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
