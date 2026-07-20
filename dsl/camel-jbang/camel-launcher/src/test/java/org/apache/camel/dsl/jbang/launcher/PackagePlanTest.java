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
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.Yaml;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

    Path writeWingetFixture(String content) throws IOException {
        return writeReleaseFixture("-winget-bin.zip", content);
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
        Files.deleteIfExists(MODULE_DIR.resolve("target/camel-launcher-" + TEST_VERSION + "-winget-bin.zip"));
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

    /**
     * The production allowlist is the release gate for {@code --channel lts}: every line it advertises must still be
     * accepted by the wrapper. This starts failing once the last entry's {@code supportEnds} date passes, which is the
     * intended signal to add the next LTS line. Unlike a hardcoded date comparison, it exercises the real parsing and
     * expiry logic in camel-package.sh against the real file.
     */
    @Test
    void everyProductionLtsLineIsStillAccepted() throws Exception {
        List<String> lines = productionLtsLines();
        assertFalse(lines.isEmpty(), "supported-lts.yml must advertise at least one LTS line");

        for (String line : lines) {
            Result r = run("prepare", "--channel", "lts", "--lts-line", line, "--print-plan");

            assertEquals(0, r.exit,
                    "supported-lts.yml advertises LTS line " + line + " but the wrapper rejected it: " + r.stderr);
            assertTrue(r.stdout.contains("BREW_FORMULA=camel@" + line), r.stdout);
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> productionLtsLines() throws IOException {
        try (var in = Files.newInputStream(MODULE_DIR.resolve("src/jreleaser/supported-lts.yml"))) {
            Map<String, Object> root = new Yaml().load(in);
            List<Map<String, Object>> supported = (List<Map<String, Object>>) root.get("supported");
            return supported.stream().map(entry -> String.valueOf(entry.get("line"))).toList();
        }
    }

    /**
     * Chocolatey and Scoop install from the public archive, which no longer carries the native executables, so both
     * must enter through the architecture-neutral batch launcher. Rendering these Mustache templates needs JReleaser,
     * so the entry point is asserted on the template source.
     */
    @Test
    void archNeutralPackagersEnterThroughTheBatchLauncher() throws Exception {
        String chocolatey = Files.readString(MODULE_DIR.resolve(
                "src/jreleaser/distributions/camel-cli/chocolatey/tools/chocolateyinstall.ps1.tpl"),
                StandardCharsets.UTF_8);
        String scoop = Files.readString(
                MODULE_DIR.resolve("src/jreleaser/distributions/camel-cli/scoop/manifest.json.tpl"),
                StandardCharsets.UTF_8);

        for (String template : List.of(chocolatey, scoop)) {
            assertTrue(template.contains("{{distributionExecutableWindows}}"),
                    "the Windows entry point must resolve to camel.bat via JReleaser: " + template);
            assertFalse(template.contains("camel-x64.exe"), template);
            assertFalse(template.contains("camel-arm64.exe"), template);
        }
    }

    /**
     * The assembly descriptors decide what each archive carries. Asserted structurally rather than by substring so
     * reformatting the XML cannot break the test while a real content change slips through. The resulting archives are
     * checked for real by {@link CamelLauncherNativeExeIT}, which only runs with -Dcamel.exe.build=true.
     */
    @Test
    void nativeExecutablesAreConfinedToWingetArchive() throws Exception {
        Path publicAssembly = MODULE_DIR.resolve("src/main/assembly/bin.xml");
        Path wingetAssembly = MODULE_DIR.resolve("src/main/assembly/winget-bin.xml");

        List<String> publicIncludes = effectiveIncludes(publicAssembly);
        assertFalse(publicIncludes.contains("camel-x64.exe"),
                "the public ZIP/TAR assembly must not expose WinGet-only native executables");
        assertFalse(publicIncludes.contains("camel-arm64.exe"),
                "the public ZIP/TAR assembly must not expose WinGet-only native executables");

        List<String> wingetIncludes = effectiveIncludes(wingetAssembly);
        assertTrue(wingetIncludes.contains("camel-x64.exe"), wingetIncludes.toString());
        assertTrue(wingetIncludes.contains("camel-arm64.exe"), wingetIncludes.toString());
        assertTrue(wingetIncludes.contains("*.jar"), "the WinGet archive must retain the launcher JAR beside camel.bat");
        assertTrue(wingetIncludes.contains("camel.bat"),
                "the native bootstrap delegates to camel.bat beside its resolved target");
        assertEquals(List.of("zip"), elementTexts(parseXml(wingetAssembly), "format"),
                "the WinGet payload is a ZIP only; WinGet cannot consume a tar.gz");
    }

    /**
     * Both archives must deliver the same CLI, so their common content is declared once in a shared assembly component.
     * Re-inlining it into either descriptor would let the two drift apart silently, which is precisely what the
     * surrounding assertions could no longer detect.
     */
    @Test
    void bothArchivesShareOneContentDefinition() throws Exception {
        List<String> publicComponents = elementTexts(
                parseXml(MODULE_DIR.resolve("src/main/assembly/bin.xml")), "componentDescriptor");
        List<String> wingetComponents = elementTexts(
                parseXml(MODULE_DIR.resolve("src/main/assembly/winget-bin.xml")), "componentDescriptor");

        assertEquals(List.of("src/main/assembly/launcher-content.xml"), publicComponents);
        assertEquals(publicComponents, wingetComponents,
                "the public and WinGet archives must draw their common content from the same component");

        // The WinGet archive's only declared difference is the native bootstraps.
        assertEquals(List.of("camel-x64.exe", "camel-arm64.exe"),
                elementTexts(parseXml(MODULE_DIR.resolve("src/main/assembly/winget-bin.xml")), "include"),
                "winget-bin.xml must add the native executables and nothing else");
    }

    /**
     * The WinGet payload is deliberately never installed or deployed to a Maven repository, so it must be produced by a
     * non-attached execution that only the native-executable profile enables. CI additionally proves the exclusion by
     * deploying to a throwaway repository and failing if the ZIP appears.
     */
    @Test
    void wingetArchiveIsBuiltOnlyByTheNativeProfileAndNeverAttached() throws Exception {
        Document pom = parseXml(MODULE_DIR.resolve("pom.xml"));

        Element profile = profileById(pom, "include-camel-exe");
        assertNotNull(profile, "the native executable profile must exist");
        Element execution = executionById(profile, "assemble-winget-bin");
        assertNotNull(execution, "the native executable profile must create the WinGet archive");
        assertEquals(List.of("src/main/assembly/winget-bin.xml"), elementTexts(execution, "descriptor"));
        assertEquals(List.of("false"), elementTexts(execution, "attach"),
                "the WinGet payload must remain a local release file, not an attached Maven artifact");

        Element build = firstChild(pom.getDocumentElement(), "build");
        assertNotNull(build, "the module must declare a build section");
        assertFalse(elementTexts(build, "descriptor").contains("src/main/assembly/winget-bin.xml"),
                "ordinary builds must not create an incomplete WinGet archive");
    }

    @Test
    @SuppressWarnings("unchecked")
    void wingetUsesDedicatedJreleaserDistribution() throws Exception {
        Map<String, Object> distributions;
        try (var in = Files.newInputStream(MODULE_DIR.resolve("jreleaser.yml"))) {
            Map<String, Object> config = new Yaml().load(in);
            distributions = (Map<String, Object>) config.get("distributions");
        }

        Map<String, Object> publicDistribution = (Map<String, Object>) distributions.get("camel-cli");
        Map<String, Object> wingetDistribution = (Map<String, Object>) distributions.get("camel-cli-winget");
        assertNotNull(wingetDistribution, "a dedicated WinGet distribution must exist: " + distributions.keySet());
        assertFalse(publicDistribution.containsKey("winget"),
                "the public distribution must not generate the WinGet package");

        List<Map<String, Object>> artifacts = (List<Map<String, Object>>) wingetDistribution.get("artifacts");
        assertEquals(1, artifacts.size(), artifacts.toString());
        assertTrue(String.valueOf(artifacts.get(0).get("path")).endsWith("-winget-bin.zip"), artifacts.toString());

        String downloadUrl = String.valueOf(((Map<String, Object>) wingetDistribution.get("winget")).get("downloadUrl"));
        assertTrue(downloadUrl.startsWith("https://archive.apache.org/dist/camel/apache-camel/"),
                "the WinGet payload is served from the Apache archive, not Maven Central: " + downloadUrl);
    }

    /**
     * install.ps1 downloads the public archive, which no longer carries the native executables, so it must not select
     * one by architecture. The installer's behaviour is covered by {@code WebsiteInstallTest}, but those tests are
     * {@code @EnabledOnOs(WINDOWS)}; this keeps the invariant guarded on every platform.
     */
    @Test
    void websiteWindowsInstallerUsesArchitectureNeutralBatchLauncher() throws Exception {
        String installer = Files.readString(MODULE_DIR.resolve("src/install/install.ps1"), StandardCharsets.UTF_8);

        assertFalse(installer.contains("camel-x64.exe"), installer);
        assertFalse(installer.contains("camel-arm64.exe"), installer);
        assertFalse(installer.contains("PROCESSOR_ARCHITECTURE"),
                "the installer must not branch on host architecture: " + installer);
    }

    /**
     * Maven Central publishes only a SHA-1 sidecar for these artifacts, so pointing Scoop's autoupdate at one would
     * silently downgrade future versions from the SHA-256 pinned here to SHA-1. Omitting the autoupdate hash entirely
     * makes Scoop download the artifact and compute SHA-256 itself, keeping one algorithm across every version.
     */
    @Test
    @SuppressWarnings("unchecked")
    void scoopAutoupdateComputesSha256RatherThanReusingTheSha1Sidecar() throws Exception {
        // Every Mustache placeholder in this template sits inside a quoted JSON string, so it parses as JSON as-is.
        Map<String, Object> manifest;
        try (var in = Files.newInputStream(
                MODULE_DIR.resolve("src/jreleaser/distributions/camel-cli/scoop/manifest.json.tpl"))) {
            manifest = new Yaml().load(in);
        }

        assertTrue(String.valueOf(manifest.get("hash")).startsWith("sha256:"),
                "the pinned hash must be SHA-256: " + manifest.get("hash"));
        Map<String, Object> autoupdate = (Map<String, Object>) manifest.get("autoupdate");
        assertNotNull(autoupdate, "Scoop autoupdate must stay configured: " + manifest);
        assertFalse(autoupdate.containsKey("hash"),
                "declaring an autoupdate hash source would pin future versions to Maven Central's SHA-1 sidecar: "
                                                    + autoupdate);
    }

    /**
     * WinGet needs a real per-architecture executable, so the JReleaser default (a single {@code neutral} entry) is
     * overridden. Both entries describe the same approved ZIP and differ only by which nested exe they expose.
     */
    @Test
    void wingetInstallerManifestDeclaresBothArchitecturesFromOneArchive() throws Exception {
        String winget = Files.readString(
                MODULE_DIR.resolve("src/jreleaser/distributions/camel-cli/winget/installer.yaml.tpl"),
                StandardCharsets.UTF_8);

        assertTrue(winget.contains("Architecture: x64"), winget);
        assertTrue(winget.contains("Architecture: arm64"), winget);
        assertTrue(winget.contains("bin\\camel-x64.exe"), winget);
        assertTrue(winget.contains("bin\\camel-arm64.exe"), winget);
        assertEquals(2, countOccurrences(winget, "InstallerSha256: {{distributionChecksumSha256}}"),
                "both architecture entries must use the checksum of the same approved ZIP");

        // The editor schema hint and the declared manifest version are the same contract; drifting apart means the
        // manifest is validated locally against a version WinGet is not being told to expect.
        Matcher declared = Pattern.compile("ManifestVersion: (\\S+)").matcher(winget);
        assertTrue(declared.find(), winget);
        assertTrue(winget.contains("winget-manifest.installer." + declared.group(1) + ".schema.json"),
                "the yaml-language-server schema must match ManifestVersion " + declared.group(1));
    }

    private static int countOccurrences(String haystack, String needle) {
        return haystack.split(Pattern.quote(needle), -1).length - 1;
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

    private Map<String, String> testModeEnvWithMvnStub(Path tmp, Path recordFile) throws IOException {
        Path stubDir = tmp.resolve("stub-bin");
        Files.createDirectories(stubDir);
        Path mvnStub = stubDir.resolve("mvn");
        Files.writeString(mvnStub, "#!/bin/sh\nprintf '%s\\n' \"$*\" >> \"" + recordFile + "\"\nexit 0\n",
                StandardCharsets.UTF_8);
        assertTrue(mvnStub.toFile().setExecutable(true));

        Path winget = writeWingetFixture("fixture-winget");
        Map<String, String> env = new LinkedHashMap<>();
        env.put("CAMEL_PACKAGE_TEST_MODE", "true");
        env.put("CAMEL_PACKAGE_TEST_VERSION", TEST_VERSION);
        env.put("CAMEL_PACKAGE_TEST_WINGET_REMOTE", winget.toString());
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

        Path winget = writeWingetFixture("fixture-winget");
        Map<String, String> env = new LinkedHashMap<>();
        env.put("CAMEL_PACKAGE_TEST_WINGET_REMOTE", winget.toString());
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

        Path winget = writeWingetFixture("fixture-winget");
        Path curlRecord = tmp.resolve("archive-curl.txt");
        Path curlStub = stubDir.resolve("curl");
        Files.writeString(curlStub,
                "#!/bin/sh\n"
                                    + "printf '%s\\n' \"$*\" >> \"" + curlRecord + "\"\n"
                                    + "output=''\n"
                                    + "previous=''\n"
                                    + "for argument in \"$@\"; do\n"
                                    + "  if [ \"$previous\" = '-o' ]; then output=$argument; fi\n"
                                    + "  previous=$argument\n"
                                    + "done\n"
                                    + "[ -n \"$output\" ] || exit 98\n"
                                    + "cp \"" + winget + "\" \"$output\"\n",
                StandardCharsets.UTF_8);
        assertTrue(curlStub.toFile().setExecutable(true));

        Map<String, String> env = new LinkedHashMap<>();
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
    void productionPrepareVerifiesTheArchivedWingetPayload(@TempDir Path tmp) throws Exception {
        writeReleaseFixture("-bin.tar.gz", "fixture-tar");
        writeReleaseFixture("-bin.zip", "fixture-zip");
        Path recordFile = tmp.resolve("mvn-calls.txt");

        Result r = run(productionStyleEnvWithMvnStub(tmp, recordFile), "prepare", "--channel", "stable");

        assertEquals(0, r.exit, r.stderr);
        String curlCalls = Files.readString(tmp.resolve("archive-curl.txt"), StandardCharsets.UTF_8);
        assertTrue(curlCalls.contains("https://archive.apache.org/dist/camel/apache-camel/" + TEST_VERSION
                                      + "/camel-launcher-" + TEST_VERSION + "-winget-bin.zip"),
                curlCalls);
        assertTrue(Files.exists(recordFile), "JReleaser must run after the byte comparison succeeds");
    }

    @Test
    void prepareRejectsAnArchivedWingetPayloadWithDifferentBytes(@TempDir Path tmp) throws Exception {
        writeReleaseFixture("-bin.tar.gz", "fixture-tar");
        writeReleaseFixture("-bin.zip", "fixture-zip");
        Path remote = tmp.resolve("remote-winget.zip");
        Files.writeString(remote, "different-remote-winget", StandardCharsets.UTF_8);
        Path recordFile = tmp.resolve("mvn-calls.txt");
        Map<String, String> env = testModeEnvWithMvnStub(tmp, recordFile);
        writeWingetFixture("local-winget");
        env.put("CAMEL_PACKAGE_TEST_WINGET_REMOTE", remote.toString());

        Result r = run(env, "prepare", "--channel", "stable");

        assertNotEquals(0, r.exit);
        assertTrue(r.stderr.contains("does not match the archived WinGet payload"), r.stderr);
        assertFalse(Files.exists(recordFile), "JReleaser must not run after a byte mismatch");
    }

    @Test
    void wingetRemoteOverrideRequiresExplicitTestMode(@TempDir Path tmp) throws Exception {
        writeReleaseFixture("-bin.tar.gz", "fixture-tar");
        writeReleaseFixture("-bin.zip", "fixture-zip");
        Path recordFile = tmp.resolve("mvn-calls.txt");
        Map<String, String> env = new LinkedHashMap<>(productionStyleEnvWithMvnStub(tmp, recordFile));
        env.put("CAMEL_PACKAGE_TEST_WINGET_REMOTE", writeWingetFixture("fixture-winget").toString());

        Result r = run(env, "prepare", "--channel", "stable");

        assertEquals(2, r.exit);
        assertTrue(r.stderr.contains("CAMEL_PACKAGE_TEST_WINGET_REMOTE requires CAMEL_PACKAGE_TEST_MODE=true"),
                r.stderr);
        assertFalse(Files.exists(recordFile));
    }

    @Test
    void missingWingetPayloadFailsBeforeJReleaser(@TempDir Path tmp) throws Exception {
        writeReleaseFixture("-bin.tar.gz", "fixture-tar");
        writeReleaseFixture("-bin.zip", "fixture-zip");
        Path recordFile = tmp.resolve("mvn-calls.txt");
        Map<String, String> env = testModeEnvWithMvnStub(tmp, recordFile);
        Files.delete(MODULE_DIR.resolve("target/camel-launcher-" + TEST_VERSION + "-winget-bin.zip"));

        Result r = run(env, "prepare", "--channel", "stable");

        assertNotEquals(0, r.exit);
        assertTrue(r.stderr.contains("WinGet release ZIP not found"), r.stderr);
        assertFalse(Files.exists(recordFile));
    }

    @Test
    void ltsStagesInstallersButDoesNotWriteLatest(@TempDir Path tmp) throws Exception {
        writeReleaseFixture("-bin.tar.gz", "fixture-tar-lts");
        writeReleaseFixture("-bin.zip", "fixture-zip-lts");
        Path recordFile = tmp.resolve("mvn-calls.txt");
        // The stub must emit a Homebrew formula the way a real JReleaser run does: the LTS path treats a missing
        // formula directory as a packaging failure.
        Map<String, String> env = envWithMvnStubProducingFormula(tmp, recordFile, "camel-at-99.rb");
        env.put("CAMEL_PACKAGE_TEST_MODE", "true");
        env.put("CAMEL_PACKAGE_TEST_VERSION", TEST_VERSION);
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

    /**
     * The generated formula is the release artifact: nothing after JReleaser may rewrite it. Guards against
     * reintroducing the test-mode patching that used to swap in a published version's url/sha256.
     */
    @Test
    void prepareLeavesTheGeneratedHomebrewFormulaUntouched(@TempDir Path tmp) throws Exception {
        writeReleaseFixture("-bin.tar.gz", "fixture-tar");
        writeReleaseFixture("-bin.zip", "fixture-zip");
        Path recordFile = tmp.resolve("mvn-calls.txt");
        Map<String, String> env = envWithMvnStubProducingFormula(tmp, recordFile, "camel.rb");
        env.put("CAMEL_PACKAGE_TEST_MODE", "true");
        env.put("CAMEL_PACKAGE_TEST_VERSION", TEST_VERSION);

        Result r = run(env, "prepare", "--channel", "stable");

        assertEquals(0, r.exit, r.stderr);
        String formula = Files.readString(PACKAGE_DIR.resolve("camel-cli/brew/Formula/camel.rb"), StandardCharsets.UTF_8);
        assertTrue(formula.contains("url \"https://example.invalid/original.zip\""),
                "the url JReleaser generated must survive verbatim: " + formula);
        assertTrue(formula.contains("sha256 \"original\""),
                "the checksum JReleaser generated must survive verbatim: " + formula);
        assertTrue(formula.contains("version \"" + TEST_VERSION + "\""), formula);
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

    // --- XML helpers: the POM and assembly descriptors are asserted structurally, so reformatting them
    // (or reordering elements) cannot break these tests while a real content change slips through.

    private static Document parseXml(Path file) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        try (var in = Files.newInputStream(file)) {
            return factory.newDocumentBuilder().parse(in);
        }
    }

    /** Text of every descendant element with the given tag name, in document order. */
    private static List<String> elementTexts(Node scope, String tagName) {
        List<String> texts = new ArrayList<>();
        NodeList nodes = scope instanceof Document doc
                ? doc.getElementsByTagName(tagName) : ((Element) scope).getElementsByTagName(tagName);
        for (int i = 0; i < nodes.getLength(); i++) {
            texts.add(nodes.item(i).getTextContent().trim());
        }
        return texts;
    }

    /**
     * Every {@code <include>} an assembly descriptor contributes, following its {@code <componentDescriptor>}
     * references so the assertion describes what the archive actually carries rather than which file happens to declare
     * it.
     */
    private static List<String> effectiveIncludes(Path descriptor) throws Exception {
        Document document = parseXml(descriptor);
        List<String> includes = new ArrayList<>(elementTexts(document, "include"));
        for (String component : elementTexts(document, "componentDescriptor")) {
            includes.addAll(elementTexts(parseXml(MODULE_DIR.resolve(component)), "include"));
        }
        return includes;
    }

    private static Element firstChild(Element parent, String tagName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element element && element.getTagName().equals(tagName)) {
                return element;
            }
        }
        return null;
    }

    /** The {@code <profile>} whose direct {@code <id>} child matches, or null. */
    private static Element profileById(Document pom, String id) {
        return findByChildId(pom.getElementsByTagName("profile"), id);
    }

    /** The {@code <execution>} within the given scope whose direct {@code <id>} child matches, or null. */
    private static Element executionById(Element scope, String id) {
        return findByChildId(scope.getElementsByTagName("execution"), id);
    }

    private static Element findByChildId(NodeList candidates, String id) {
        for (int i = 0; i < candidates.getLength(); i++) {
            Element candidate = (Element) candidates.item(i);
            Element idElement = firstChild(candidate, "id");
            if (idElement != null && idElement.getTextContent().trim().equals(id)) {
                return candidate;
            }
        }
        return null;
    }
}
