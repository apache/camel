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
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the JDK-only WebsiteManifestGenerator source-file tool: format, immutability, monotonic latest
 * updates, and checksum correctness.
 */
class WebsiteManifestGeneratorTest {

    private static final Path GENERATOR = Paths.get("src/jreleaser/java/WebsiteManifestGenerator.java");

    // Must stay byte-identical to WebsiteManifestGenerator.LICENSE_HEADER: the generator now prepends
    // this ASF header to every manifest, and the assertions below compare the output byte-for-byte.
    // Package-private so PackagePlanTest (which runs the generator via camel-package.sh) can reuse it.
    static final String LICENSE_HEADER
            = "## ---------------------------------------------------------------------------\n"
              + "## Licensed to the Apache Software Foundation (ASF) under one or more\n"
              + "## contributor license agreements.  See the NOTICE file distributed with\n"
              + "## this work for additional information regarding copyright ownership.\n"
              + "## The ASF licenses this file to You under the Apache License, Version 2.0\n"
              + "## (the \"License\"); you may not use this file except in compliance with\n"
              + "## the License.  You may obtain a copy of the License at\n"
              + "##\n"
              + "##      http://www.apache.org/licenses/LICENSE-2.0\n"
              + "##\n"
              + "## Unless required by applicable law or agreed to in writing, software\n"
              + "## distributed under the License is distributed on an \"AS IS\" BASIS,\n"
              + "## WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.\n"
              + "## See the License for the specific language governing permissions and\n"
              + "## limitations under the License.\n"
              + "## ---------------------------------------------------------------------------\n";

    private static final class Result {
        int exit;
        String stdout;
        String stderr;
    }

    private Result run(String... args) throws Exception {
        String javaBin = Paths.get(System.getProperty("java.home"), "bin", "java").toString();
        List<String> cmd = new ArrayList<>();
        cmd.add(javaBin);
        cmd.add(GENERATOR.toString());
        Collections.addAll(cmd, args);
        Process p = new ProcessBuilder(cmd).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String err = new String(p.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(p.waitFor(60, TimeUnit.SECONDS), "generator did not exit in time");
        Result r = new Result();
        r.exit = p.exitValue();
        r.stdout = out;
        r.stderr = err;
        return r;
    }

    private Path writeFixture(Path dir, String name, String content) throws IOException {
        Path file = dir.resolve(name);
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    private String sha256Hex(Path file) throws IOException, NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(md.digest(Files.readAllBytes(file)));
    }

    private String expectedManifest(String version, String tarSha256, String zipSha256) {
        return LICENSE_HEADER + "format=1\n" + "version=" + version + "\n" + "tar_sha256=" + tarSha256 + "\n"
               + "zip_sha256=" + zipSha256 + "\n";
    }

    @Test
    void stableWritesVersionAndLatestManifests(@TempDir Path work) throws Exception {
        Path tar = writeFixture(work, "camel-launcher-4.22.0-bin.tar.gz", "tar-content-A");
        Path zip = writeFixture(work, "camel-launcher-4.22.0-bin.zip", "zip-content-A");
        Path output = work.resolve("website");

        Result r = run("--version", "4.22.0", "--tar", tar.toString(), "--zip", zip.toString(), "--output",
                output.toString(), "--latest", "true");

        assertEquals(0, r.exit, r.stderr);
        String expected = expectedManifest("4.22.0", sha256Hex(tar), sha256Hex(zip));
        Path versionFile = output.resolve("releases").resolve("4.22.0.properties");
        Path latestFile = output.resolve("releases").resolve("latest.properties");
        assertEquals(expected, Files.readString(versionFile, StandardCharsets.UTF_8));
        assertArrayEquals(Files.readAllBytes(versionFile), Files.readAllBytes(latestFile),
                "latest.properties must be byte-identical to the version manifest");
    }

    @Test
    void ltsDoesNotWriteLatest(@TempDir Path work) throws Exception {
        Path tar = writeFixture(work, "camel-launcher-4.18.5-bin.tar.gz", "tar-content-lts");
        Path zip = writeFixture(work, "camel-launcher-4.18.5-bin.zip", "zip-content-lts");
        Path output = work.resolve("website");

        Result r = run("--version", "4.18.5", "--tar", tar.toString(), "--zip", zip.toString(), "--output",
                output.toString(), "--latest", "false");

        assertEquals(0, r.exit, r.stderr);
        assertTrue(Files.exists(output.resolve("releases").resolve("4.18.5.properties")));
        assertFalse(Files.exists(output.resolve("releases").resolve("latest.properties")),
                "LTS run must not create latest.properties");
    }

    @Test
    void repeatedRunIsIdempotent(@TempDir Path work) throws Exception {
        Path tar = writeFixture(work, "t.tar.gz", "same-tar");
        Path zip = writeFixture(work, "t.zip", "same-zip");
        Path output = work.resolve("website");

        Result r1 = run("--version", "4.22.0", "--tar", tar.toString(), "--zip", zip.toString(), "--output",
                output.toString(), "--latest", "true");
        assertEquals(0, r1.exit, r1.stderr);

        byte[] versionBytesBefore = Files.readAllBytes(output.resolve("releases").resolve("4.22.0.properties"));
        byte[] latestBytesBefore = Files.readAllBytes(output.resolve("releases").resolve("latest.properties"));

        Result r2 = run("--version", "4.22.0", "--tar", tar.toString(), "--zip", zip.toString(), "--output",
                output.toString(), "--latest", "true");
        assertEquals(0, r2.exit, r2.stderr);

        assertArrayEquals(versionBytesBefore, Files.readAllBytes(output.resolve("releases").resolve("4.22.0.properties")));
        assertArrayEquals(latestBytesBefore, Files.readAllBytes(output.resolve("releases").resolve("latest.properties")));
    }

    @Test
    void immutableVersionConflictFails(@TempDir Path work) throws Exception {
        Path tar1 = writeFixture(work, "a.tar.gz", "content-A");
        Path zip1 = writeFixture(work, "a.zip", "content-A-zip");
        Path output = work.resolve("website");

        Result r1 = run("--version", "4.22.0", "--tar", tar1.toString(), "--zip", zip1.toString(), "--output",
                output.toString(), "--latest", "false");
        assertEquals(0, r1.exit, r1.stderr);
        byte[] before = Files.readAllBytes(output.resolve("releases").resolve("4.22.0.properties"));

        Path tar2 = writeFixture(work, "b.tar.gz", "content-B-different");
        Path zip2 = writeFixture(work, "b.zip", "content-B-zip-different");

        Result r2 = run("--version", "4.22.0", "--tar", tar2.toString(), "--zip", zip2.toString(), "--output",
                output.toString(), "--latest", "false");

        assertNotEquals(0, r2.exit, "conflicting checksums for an existing version manifest must fail");
        assertArrayEquals(before, Files.readAllBytes(output.resolve("releases").resolve("4.22.0.properties")),
                "existing version manifest must remain untouched after a rejected conflicting write");
    }

    @Test
    void latestRollbackFails(@TempDir Path work) throws Exception {
        Path tar1 = writeFixture(work, "hi.tar.gz", "hi-tar");
        Path zip1 = writeFixture(work, "hi.zip", "hi-zip");
        Path output = work.resolve("website");

        Result seed = run("--version", "4.23.0", "--tar", tar1.toString(), "--zip", zip1.toString(), "--output",
                output.toString(), "--latest", "true");
        assertEquals(0, seed.exit, seed.stderr);
        byte[] latestBefore = Files.readAllBytes(output.resolve("releases").resolve("latest.properties"));

        Path tar2 = writeFixture(work, "lo.tar.gz", "lo-tar");
        Path zip2 = writeFixture(work, "lo.zip", "lo-zip");

        Result r = run("--version", "4.22.0", "--tar", tar2.toString(), "--zip", zip2.toString(), "--output",
                output.toString(), "--latest", "true");

        assertNotEquals(0, r.exit, "latest must not move backward to a lower version");
        assertArrayEquals(latestBefore, Files.readAllBytes(output.resolve("releases").resolve("latest.properties")));
        assertTrue(Files.exists(output.resolve("releases").resolve("4.22.0.properties")),
                "the version-specific manifest is still written even if the latest update is rejected");
    }

    @Test
    void sameVersionChecksumConflictOnLatestFails(@TempDir Path work) throws Exception {
        Path output = work.resolve("website");
        Files.createDirectories(output);

        // Seed a latest.properties directly (bypassing the generator) that claims version 4.22.0 with
        // checksums that do not correspond to any releases/4.22.0.properties file yet. This simulates
        // stale/foreign state: the releases/ manifest for that version does not exist, so the
        // version-file write below succeeds fresh, isolating the latest-specific conflict check.
        Path tarStale = writeFixture(work, "stale.tar.gz", "stale-tar");
        Path zipStale = writeFixture(work, "stale.zip", "stale-zip");
        byte[] tamperedLatest
                = expectedManifest("4.22.0", sha256Hex(tarStale), sha256Hex(zipStale)).getBytes(StandardCharsets.UTF_8);
        Files.createDirectories(output.resolve("releases"));
        Files.write(output.resolve("releases").resolve("latest.properties"), tamperedLatest);
        assertFalse(Files.exists(output.resolve("releases").resolve("4.22.0.properties")));

        Path tarNew = writeFixture(work, "new.tar.gz", "new-tar-different");
        Path zipNew = writeFixture(work, "new.zip", "new-zip-different");

        Result r = run("--version", "4.22.0", "--tar", tarNew.toString(), "--zip", zipNew.toString(), "--output",
                output.toString(), "--latest", "true");

        assertNotEquals(0, r.exit, "same-version checksum conflict against latest.properties must fail");
        assertArrayEquals(tamperedLatest, Files.readAllBytes(output.resolve("releases").resolve("latest.properties")),
                "latest.properties must remain untouched after a rejected conflicting update");
        assertTrue(Files.exists(output.resolve("releases").resolve("4.22.0.properties")),
                "the version-specific manifest still gets written since it did not previously exist");
    }

    @Test
    void malformedExistingLatestFailsBeforeUpdate(@TempDir Path work) throws Exception {
        Path output = work.resolve("website");
        Path releases = output.resolve("releases");
        Files.createDirectories(releases);
        Path latest = releases.resolve("latest.properties");
        Files.writeString(latest,
                "format=1\n"
                                  + "format=1\n"
                                  + "version=4.22.0\n"
                                  + "tar_sha256=not-hex\n"
                                  + "zip_sha256=" + "a".repeat(64) + "\n",
                StandardCharsets.UTF_8);
        byte[] latestBefore = Files.readAllBytes(latest);

        Path tar = writeFixture(work, "new.tar.gz", "new-tar");
        Path zip = writeFixture(work, "new.zip", "new-zip");

        Result r = run("--version", "4.23.0", "--tar", tar.toString(), "--zip", zip.toString(), "--output",
                output.toString(), "--latest", "true");

        assertNotEquals(0, r.exit, "malformed existing latest.properties must reject an update");
        assertArrayEquals(latestBefore, Files.readAllBytes(latest),
                "malformed existing latest.properties must remain untouched");
    }

    // --rebuild-aliases --check expects latest to be the highest X.Y.Z manifest, so publishing a new highest
    // version without --latest true must fail up front instead of leaving latest behind.
    @Test
    void newHighestVersionWithoutLatestFailsBeforeWritingAnything(@TempDir Path work) throws Exception {
        Path tar1 = writeFixture(work, "old.tar.gz", "old-tar");
        Path zip1 = writeFixture(work, "old.zip", "old-zip");
        Path installSh = writeFixture(work, "install.sh", "#!/bin/sh\n");
        Path installPs1 = writeFixture(work, "install.ps1", "# ps1\n");
        Path output = work.resolve("website").resolve("camel-cli");
        Path releases = output.resolve("releases");

        Result seed = run("--version", "4.22.0", "--tar", tar1.toString(), "--zip", zip1.toString(), "--output",
                output.toString(), "--latest", "true", "--lts", "true");
        assertEquals(0, seed.exit, seed.stderr);
        List<String> before = listing(releases);
        byte[] latestBefore = Files.readAllBytes(releases.resolve("latest.properties"));

        Path tar2 = writeFixture(work, "new.tar.gz", "new-tar");
        Path zip2 = writeFixture(work, "new.zip", "new-zip");
        Result r = run("--version", "4.23.0", "--tar", tar2.toString(), "--zip", zip2.toString(), "--output",
                output.toString(), "--latest", "false", "--lts", "true", "--install-sh", installSh.toString(),
                "--install-ps1", installPs1.toString());

        assertNotEquals(0, r.exit, "a new highest version must not be published without --latest true");
        assertThat(r.stderr).contains("4.23.0 is higher than every published release (highest is 4.22.0)")
                .contains("pass --latest true");
        assertThat(listing(releases)).isEqualTo(before);
        assertArrayEquals(latestBefore, Files.readAllBytes(releases.resolve("latest.properties")));
        assertFalse(Files.exists(output.getParent().resolve("install.sha256")), "nothing may be written");
    }

    @Test
    void olderVersionWithoutLatestIsStillPublished(@TempDir Path work) throws Exception {
        Path tar1 = writeFixture(work, "hi.tar.gz", "hi-tar");
        Path zip1 = writeFixture(work, "hi.zip", "hi-zip");
        Path output = work.resolve("website");
        Result seed = run("--version", "4.23.0", "--tar", tar1.toString(), "--zip", zip1.toString(), "--output",
                output.toString(), "--latest", "true");
        assertEquals(0, seed.exit, seed.stderr);

        Path tar2 = writeFixture(work, "lo.tar.gz", "lo-tar");
        Path zip2 = writeFixture(work, "lo.zip", "lo-zip");
        Result r = run("--version", "4.22.3", "--tar", tar2.toString(), "--zip", zip2.toString(), "--output",
                output.toString(), "--latest", "false", "--lts", "true");

        assertEquals(0, r.exit, r.stderr);
        assertTrue(Files.exists(output.resolve("releases").resolve("4.22.3.properties")));
        assertTrue(Files.exists(output.resolve("releases").resolve("lts.properties")));
    }

    private static List<String> listing(Path dir) throws IOException {
        try (var files = Files.list(dir)) {
            return files.map(f -> f.getFileName().toString()).sorted().toList();
        }
    }

    @Test
    void forwardUpdateSucceeds(@TempDir Path work) throws Exception {
        Path tar1 = writeFixture(work, "old.tar.gz", "old-tar");
        Path zip1 = writeFixture(work, "old.zip", "old-zip");
        Path output = work.resolve("website");

        Result seed = run("--version", "4.22.0", "--tar", tar1.toString(), "--zip", zip1.toString(), "--output",
                output.toString(), "--latest", "true");
        assertEquals(0, seed.exit, seed.stderr);

        Path tar2 = writeFixture(work, "new.tar.gz", "new-tar");
        Path zip2 = writeFixture(work, "new.zip", "new-zip");

        Result r = run("--version", "4.23.0", "--tar", tar2.toString(), "--zip", zip2.toString(), "--output",
                output.toString(), "--latest", "true");

        assertEquals(0, r.exit, r.stderr);
        String expected = expectedManifest("4.23.0", sha256Hex(tar2), sha256Hex(zip2));
        assertEquals(expected,
                Files.readString(output.resolve("releases").resolve("latest.properties"), StandardCharsets.UTF_8));
        assertTrue(Files.exists(output.resolve("releases").resolve("4.22.0.properties")));
        assertTrue(Files.exists(output.resolve("releases").resolve("4.23.0.properties")));
    }

    @Test
    void invalidSnapshotVersionRejected(@TempDir Path work) throws Exception {
        Path tar = writeFixture(work, "s.tar.gz", "s-tar");
        Path zip = writeFixture(work, "s.zip", "s-zip");
        Path output = work.resolve("website");

        Result r = run("--version", "4.24.0-SNAPSHOT", "--tar", tar.toString(), "--zip", zip.toString(), "--output",
                output.toString(), "--latest", "false");

        assertNotEquals(0, r.exit, "SNAPSHOT versions must be rejected");
        assertFalse(Files.exists(output), "no output must be written on validation failure");
    }

    @Test
    void missingArtifactFails(@TempDir Path work) throws Exception {
        Path zip = writeFixture(work, "present.zip", "present-zip");
        Path missingTar = work.resolve("does-not-exist.tar.gz");
        Path output = work.resolve("website");

        Result r = run("--version", "4.22.0", "--tar", missingTar.toString(), "--zip", zip.toString(), "--output",
                output.toString(), "--latest", "false");

        assertNotEquals(0, r.exit, "a missing artifact must fail before any manifest is written");
        assertFalse(Files.exists(output));
    }

    @Test
    void unknownOptionRejected(@TempDir Path work) throws Exception {
        Path tar = writeFixture(work, "u.tar.gz", "u-tar");
        Path zip = writeFixture(work, "u.zip", "u-zip");
        Path output = work.resolve("website");

        Result r = run("--version", "4.22.0", "--tar", tar.toString(), "--zip", zip.toString(), "--output",
                output.toString(), "--latest", "false", "--bogus", "surprise");

        assertNotEquals(0, r.exit, "unknown options must be rejected");
        assertFalse(Files.exists(output));
    }

    @Test
    void handlesOutputPathsWithSpacesAndUnicode(@TempDir Path work) throws Exception {
        Path tar = writeFixture(work, "sp.tar.gz", "sp-tar");
        Path zip = writeFixture(work, "sp.zip", "sp-zip");
        Path output = work.resolve("café output ünïcödé");

        Result r = run("--version", "4.22.0", "--tar", tar.toString(), "--zip", zip.toString(), "--output",
                output.toString(), "--latest", "true");

        assertEquals(0, r.exit, r.stderr);
        String expected = expectedManifest("4.22.0", sha256Hex(tar), sha256Hex(zip));
        assertEquals(expected,
                Files.readString(output.resolve("releases").resolve("4.22.0.properties"), StandardCharsets.UTF_8));
        assertEquals(expected,
                Files.readString(output.resolve("releases").resolve("latest.properties"), StandardCharsets.UTF_8));
    }

    @Test
    void writesInstallChecksums(@TempDir Path temp) throws Exception {
        Path tar = writeFixture(temp, "camel-launcher-4.22.0-bin.tar.gz", "tar-content");
        Path zip = writeFixture(temp, "camel-launcher-4.22.0-bin.zip", "zip-content");
        Path installSh = writeFixture(temp, "install.sh", "#!/bin/sh\necho hi\n");
        Path installPs1 = writeFixture(temp, "install.ps1", "Write-Host 'hi'\n");
        Path websiteRoot = temp.resolve("website");
        Path output = websiteRoot.resolve("camel-cli");
        Files.createDirectories(output);

        Result r = run("--version", "4.22.0", "--tar", tar.toString(), "--zip", zip.toString(),
                "--install-sh", installSh.toString(), "--install-ps1", installPs1.toString(),
                "--output", output.toString(), "--latest", "true");

        assertThat(r.exit).isZero();
        Path checksumFile = websiteRoot.resolve("install.sha256");
        assertThat(checksumFile).exists();
        List<String> lines = Files.readAllLines(checksumFile);
        assertThat(lines).containsExactly(
                "install_sh_sha256=" + sha256Hex(installSh),
                "install_ps1_sha256=" + sha256Hex(installPs1));
    }

    @Test
    void failsWhenInstallShMissing(@TempDir Path temp) throws Exception {
        Path tar = writeFixture(temp, "camel-launcher-4.22.0-bin.tar.gz", "tar-content");
        Path zip = writeFixture(temp, "camel-launcher-4.22.0-bin.zip", "zip-content");
        Path output = temp.resolve("website/camel-cli");
        Files.createDirectories(output);

        Result r = run("--version", "4.22.0", "--tar", tar.toString(), "--zip", zip.toString(),
                "--install-sh", temp.resolve("missing-install.sh").toString(),
                "--install-ps1", temp.resolve("missing-install.ps1").toString(),
                "--output", output.toString(), "--latest", "true");

        assertThat(r.exit).isEqualTo(2);
        assertThat(r.stderr).contains("install.sh not found");
    }

    private Result publish(Path work, Path output, String version, String content, boolean latest, boolean lts)
            throws Exception {
        Path tar = writeFixture(work, version + "-" + content + ".tar.gz", "tar-" + content);
        Path zip = writeFixture(work, version + "-" + content + ".zip", "zip-" + content);
        return run("--version", version, "--tar", tar.toString(), "--zip", zip.toString(), "--output",
                output.toString(), "--latest", String.valueOf(latest), "--lts", String.valueOf(lts));
    }

    private String aliasVersion(Path releases, String alias) throws IOException {
        for (String line : Files.readAllLines(releases.resolve(alias + ".properties"), StandardCharsets.UTF_8)) {
            if (line.startsWith("version=")) {
                return line.substring("version=".length());
            }
        }
        throw new IllegalStateException("no version in " + alias);
    }

    @Test
    void lineAliasMovesForwardOnly(@TempDir Path work) throws Exception {
        Path output = work.resolve("website");
        Path releases = output.resolve("releases");

        assertThat(publish(work, output, "4.22.1", "a", false, false).exit).isZero();
        assertThat(releases.resolve("4.22.properties")).hasSameBinaryContentAs(releases.resolve("4.22.1.properties"));
        assertThat(releases.resolve("lts.properties")).doesNotExist();
        assertThat(releases.resolve("latest.properties")).doesNotExist();

        assertThat(publish(work, output, "4.22.2", "b", true, false).exit).isZero();
        assertThat(aliasVersion(releases, "4.22")).isEqualTo("4.22.2");

        // A late, older patch of the same line is published but leaves the line alias alone.
        Result late = publish(work, output, "4.22.0", "c", false, false);
        assertThat(late.exit).as(late.stderr).isZero();
        assertThat(releases.resolve("4.22.0.properties")).exists();
        assertThat(aliasVersion(releases, "4.22")).isEqualTo("4.22.2");

        // Another line gets its own alias.
        assertThat(publish(work, output, "4.23.0", "d", true, false).exit).isZero();
        assertThat(aliasVersion(releases, "4.23")).isEqualTo("4.23.0");
        assertThat(aliasVersion(releases, "4.22")).isEqualTo("4.22.2");
        assertThat(aliasVersion(releases, "latest")).isEqualTo("4.23.0");
    }

    @Test
    void olderLtsPatchDoesNotMoveLts(@TempDir Path work) throws Exception {
        Path output = work.resolve("website");
        Path releases = output.resolve("releases");

        assertThat(publish(work, output, "4.18.5", "a", false, true).exit).isZero();
        assertThat(aliasVersion(releases, "lts")).isEqualTo("4.18.5");
        assertThat(publish(work, output, "4.22.2", "b", true, true).exit).isZero();
        assertThat(releases.resolve("lts.properties")).hasSameBinaryContentAs(releases.resolve("4.22.2.properties"));

        // A non-LTS release never touches lts.properties.
        assertThat(publish(work, output, "4.23.0", "c", true, false).exit).isZero();
        assertThat(aliasVersion(releases, "lts")).isEqualTo("4.22.2");

        // A patch on the older 4.18 LTS line moves 4.18 but not lts.
        Result r = publish(work, output, "4.18.6", "d", false, true);
        assertThat(r.exit).as(r.stderr).isZero();
        assertThat(aliasVersion(releases, "4.18")).isEqualTo("4.18.6");
        assertThat(aliasVersion(releases, "lts")).isEqualTo("4.22.2");
        assertThat(aliasVersion(releases, "latest")).isEqualTo("4.23.0");
    }

    @Test
    void aliasChecksumConflictFailsWithoutTouchingAliases(@TempDir Path work) throws Exception {
        Path output = work.resolve("website");
        Path releases = output.resolve("releases");
        Files.createDirectories(releases);

        // lts.properties claims 4.22.2 with checksums that 4.22.2 will not have.
        Path staleTar = writeFixture(work, "stale.tar.gz", "stale-tar");
        Path staleZip = writeFixture(work, "stale.zip", "stale-zip");
        byte[] staleLts = expectedManifest("4.22.2", sha256Hex(staleTar), sha256Hex(staleZip))
                .getBytes(StandardCharsets.UTF_8);
        Files.write(releases.resolve("lts.properties"), staleLts);

        Result r = publish(work, output, "4.22.2", "real", true, true);

        assertThat(r.exit).isEqualTo(1);
        assertThat(r.stderr).contains("lts.properties already has different checksums");
        assertThat(Files.readAllBytes(releases.resolve("lts.properties"))).isEqualTo(staleLts);
        assertThat(releases.resolve("4.22.2.properties")).exists();
        // Aliases are all-or-nothing: neither the line alias nor latest moved.
        assertThat(releases.resolve("4.22.properties")).doesNotExist();
        assertThat(releases.resolve("latest.properties")).doesNotExist();
    }

    @Test
    void lineAliasPointingAtAnotherLineFails(@TempDir Path work) throws Exception {
        Path output = work.resolve("website");
        Path releases = output.resolve("releases");
        assertThat(publish(work, output, "4.23.0", "a", false, false).exit).isZero();
        Files.copy(releases.resolve("4.23.0.properties"), releases.resolve("4.22.properties"));

        Result r = publish(work, output, "4.22.2", "b", false, false);

        assertThat(r.exit).isEqualTo(1);
        assertThat(r.stderr).contains("not on that release line");
    }

    @Test
    void invalidLtsValueRejected(@TempDir Path work) throws Exception {
        Path tar = writeFixture(work, "x.tar.gz", "x-tar");
        Path zip = writeFixture(work, "x.zip", "x-zip");
        Path output = work.resolve("website");

        Result r = run("--version", "4.22.2", "--tar", tar.toString(), "--zip", zip.toString(), "--output",
                output.toString(), "--latest", "false", "--lts", "yes");

        assertThat(r.exit).isEqualTo(2);
        assertThat(output).doesNotExist();
    }

    private Path seedReleases(Path work, String... versions) throws Exception {
        Path output = work.resolve("website");
        Path releases = output.resolve("releases");
        Files.createDirectories(releases);
        for (String v : versions) {
            Path tar = writeFixture(work, v + ".tar.gz", "tar-" + v);
            Path zip = writeFixture(work, v + ".zip", "zip-" + v);
            Files.writeString(releases.resolve(v + ".properties"), expectedManifest(v, sha256Hex(tar), sha256Hex(zip)),
                    StandardCharsets.UTF_8);
        }
        return output;
    }

    @Test
    void rebuildAliasesUsesNumericOrderingAndIsIdempotent(@TempDir Path work) throws Exception {
        // 4.9.0 < 4.10.0 < 4.18.10 numerically, the opposite of lexical order.
        Path output = seedReleases(work, "4.9.0", "4.10.0", "4.18.9", "4.18.10", "4.22.2", "4.23.0");
        Path releases = output.resolve("releases");

        Result r = run("--rebuild-aliases", "--output", output.toString(), "--lts-versions", "4.10.0, 4.18.10,4.22.2,4.18.9");
        assertThat(r.exit).as(r.stderr).isZero();

        assertThat(releases.resolve("latest.properties")).hasSameBinaryContentAs(releases.resolve("4.23.0.properties"));
        assertThat(releases.resolve("lts.properties")).hasSameBinaryContentAs(releases.resolve("4.22.2.properties"));
        assertThat(releases.resolve("4.18.properties")).hasSameBinaryContentAs(releases.resolve("4.18.10.properties"));
        assertThat(aliasVersion(releases, "4.9")).isEqualTo("4.9.0");
        assertThat(aliasVersion(releases, "4.10")).isEqualTo("4.10.0");
        assertThat(aliasVersion(releases, "4.22")).isEqualTo("4.22.2");
        assertThat(aliasVersion(releases, "4.23")).isEqualTo("4.23.0");

        byte[] ltsBefore = Files.readAllBytes(releases.resolve("lts.properties"));

        Result again
                = run("--rebuild-aliases", "--output", output.toString(), "--lts-versions", "4.10.0,4.18.10,4.22.2,4.18.9");
        assertThat(again.exit).as(again.stderr).isZero();
        assertThat(again.stdout).contains("up to date");
        assertThat(Files.readAllBytes(releases.resolve("lts.properties"))).isEqualTo(ltsBefore);

        Result check = run("--rebuild-aliases", "--check", "--output", output.toString(), "--lts-versions",
                "4.10.0,4.18.10,4.22.2,4.18.9");
        assertThat(check.exit).as(check.stderr).isZero();
    }

    @Test
    void rebuildAliasesMatchesPublishedAliases(@TempDir Path work) throws Exception {
        Path output = work.resolve("website");
        assertThat(publish(work, output, "4.18.5", "a", false, true).exit).isZero();
        assertThat(publish(work, output, "4.22.2", "b", true, true).exit).isZero();
        assertThat(publish(work, output, "4.23.0", "c", true, false).exit).isZero();
        assertThat(publish(work, output, "4.18.6", "d", false, true).exit).isZero();

        Result check = run("--rebuild-aliases", "--check", "--output", output.toString(), "--lts-versions",
                "4.18.5,4.18.6,4.22.2");

        assertThat(check.exit).as(check.stderr).isZero();
    }

    @Test
    void checkModeReportsDriftAndWritesNothing(@TempDir Path work) throws Exception {
        Path output = seedReleases(work, "4.18.5", "4.22.2", "4.23.0");
        Path releases = output.resolve("releases");
        assertThat(run("--rebuild-aliases", "--output", output.toString(), "--lts-versions", "4.18.5,4.22.2").exit)
                .isZero();

        // lts points back at 4.18.5, 4.23 is missing and an orphan 4.99 alias appeared.
        Files.copy(releases.resolve("4.18.5.properties"), releases.resolve("lts.properties"),
                StandardCopyOption.REPLACE_EXISTING);
        Files.delete(releases.resolve("4.23.properties"));
        Files.copy(releases.resolve("4.23.0.properties"), releases.resolve("4.99.properties"));
        byte[] ltsBefore = Files.readAllBytes(releases.resolve("lts.properties"));

        Result check = run("--rebuild-aliases", "--check", "--output", output.toString(), "--lts-versions",
                "4.18.5,4.22.2");

        assertThat(check.exit).isEqualTo(1);
        assertThat(check.stderr)
                .contains("lts.properties points at 4.18.5")
                .contains("4.23.properties is missing")
                .contains("4.99.properties is not expected");
        assertThat(Files.readAllBytes(releases.resolve("lts.properties"))).isEqualTo(ltsBefore);
        assertThat(releases.resolve("4.23.properties")).doesNotExist();

        // Rebuilding refuses to delete the orphan, so the published files stay as they are.
        Result rebuild = run("--rebuild-aliases", "--output", output.toString(), "--lts-versions", "4.18.5,4.22.2");
        assertThat(rebuild.exit).isEqualTo(1);
        assertThat(rebuild.stderr).contains("refusing to delete");
        assertThat(Files.readAllBytes(releases.resolve("lts.properties"))).isEqualTo(ltsBefore);

        Files.delete(releases.resolve("4.99.properties"));
        assertThat(run("--rebuild-aliases", "--output", output.toString(), "--lts-versions", "4.18.5,4.22.2").exit)
                .isZero();
        assertThat(aliasVersion(releases, "lts")).isEqualTo("4.22.2");
        assertThat(aliasVersion(releases, "4.23")).isEqualTo("4.23.0");
    }

    @Test
    void rebuildFailsForLtsVersionWithoutManifest(@TempDir Path work) throws Exception {
        Path output = seedReleases(work, "4.18.5", "4.22.2");

        Result r = run("--rebuild-aliases", "--output", output.toString(), "--lts-versions", "4.18.5,4.22.3");

        assertThat(r.exit).isEqualTo(1);
        assertThat(r.stderr).contains("LTS version 4.22.3 has no manifest");
        assertThat(output.resolve("releases").resolve("lts.properties")).doesNotExist();
        assertThat(output.resolve("releases").resolve("latest.properties")).doesNotExist();
    }

    @Test
    void rebuildRejectsMissingLtsVersionsOption(@TempDir Path work) throws Exception {
        Path output = seedReleases(work, "4.22.2");

        Result r = run("--rebuild-aliases", "--check", "--output", output.toString());

        assertThat(r.exit).isEqualTo(2);
        assertThat(r.stderr).contains("--lts-versions");
    }
}
