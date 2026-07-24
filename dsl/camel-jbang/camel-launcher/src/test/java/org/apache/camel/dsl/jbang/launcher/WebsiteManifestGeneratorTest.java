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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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

        Result r = run("--version", "4.22.0-SNAPSHOT", "--tar", tar.toString(), "--zip", zip.toString(), "--output",
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
}
