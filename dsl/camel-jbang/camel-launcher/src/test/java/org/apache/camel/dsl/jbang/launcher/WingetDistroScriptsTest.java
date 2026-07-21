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
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WingetDistroScriptsTest {

    private static final Path ROOT = Paths.get("").toAbsolutePath().resolve("../../..").normalize();
    private static final Path STAGE_SCRIPT = ROOT.resolve("etc/scripts/stage-winget-distro.sh");
    private static final Path RELEASE_SCRIPT = ROOT.resolve("etc/scripts/release-distro.sh");
    private static final String VERSION = "9.9.9";
    private static final String FILE_NAME = "camel-launcher-9.9.9-winget-bin.zip";
    private static final String APPROVED_BYTES = "approved-winget-bytes";

    @Test
    void stageScriptCreatesSignedChecksummedUncommittedCandidate(@TempDir Path tmp) throws Exception {
        Path zip = tmp.resolve(FILE_NAME);
        Files.writeString(zip, "approved-winget-bytes", StandardCharsets.UTF_8);
        Path calls = tmp.resolve("calls.txt");
        Path bin = tmp.resolve("bin");
        Files.createDirectories(bin);
        writeExecutable(bin.resolve("svn"),
                "#!/bin/sh\n"
                                            + "printf 'svn %s\\n' \"$*\" >> \"" + calls + "\"\n"
                                            + "if [ \"$1\" = checkout ]; then\n"
                                            + "  for destination in \"$@\"; do :; done\n"
                                            + "  mkdir -p \"$destination\"\n"
                                            + "fi\n");
        writeExecutable(bin.resolve("gpg"),
                "#!/bin/sh\n"
                                            + "output=''\n"
                                            + "input=''\n"
                                            + "while [ $# -gt 0 ]; do\n"
                                            + "  case \"$1\" in\n"
                                            + "    --output) output=$2; shift 2 ;;\n"
                                            + "    *) input=$1; shift ;;\n"
                                            + "  esac\n"
                                            + "done\n"
                                            + "printf 'signature for %s\\n' \"$input\" > \"$output\"\n");

        ProcessBuilder pb = new ProcessBuilder(
                "bash", STAGE_SCRIPT.toString(), VERSION, "1", zip.toString(),
                tmp.resolve("work").toString());
        pb.environment().put("PATH", bin + File.pathSeparator + System.getenv("PATH"));
        Process process = pb.start();
        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(process.waitFor(30, TimeUnit.SECONDS));
        assertEquals(0, process.exitValue(), stderr);

        Path candidate = tmp.resolve("work/dist-dev/" + VERSION + "-rc1");
        Path stagedZip = candidate.resolve(FILE_NAME);
        assertEquals("approved-winget-bytes", Files.readString(stagedZip, StandardCharsets.UTF_8));
        assertTrue(Files.exists(candidate.resolve(FILE_NAME + ".asc")));
        String expectedSha512 = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-512").digest(Files.readAllBytes(stagedZip)));
        assertEquals(expectedSha512 + "  " + FILE_NAME + "\n",
                Files.readString(candidate.resolve(FILE_NAME + ".sha512"), StandardCharsets.UTF_8));
        String recorded = Files.readString(calls, StandardCharsets.UTF_8);
        assertTrue(recorded.contains("svn checkout --depth immediates"), recorded);
        assertTrue(recorded.contains("svn add " + VERSION + "-rc1"), recorded);
        assertFalse(recorded.contains(" commit "), "the staging script must stop before remote mutation");
        assertFalse(recorded.contains(" ci "), "the staging script must stop before remote mutation");
    }

    @Test
    void stageScriptRejectsAFileWithTheWrongReleaseName(@TempDir Path tmp) throws Exception {
        Path zip = tmp.resolve("wrong.zip");
        Files.writeString(zip, "bytes", StandardCharsets.UTF_8);

        Process process = new ProcessBuilder(
                "bash", STAGE_SCRIPT.toString(), VERSION, "1", zip.toString(),
                tmp.resolve("work").toString()).start();
        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(process.waitFor(30, TimeUnit.SECONDS));

        assertEquals(2, process.exitValue());
        assertTrue(stderr.contains(FILE_NAME), stderr);
    }

    @Test
    void stageScriptRejectsSnapshots(@TempDir Path tmp) throws Exception {
        String snapshot = VERSION + "-SNAPSHOT";
        Path zip = tmp.resolve("camel-launcher-" + snapshot + "-winget-bin.zip");
        Files.writeString(zip, "bytes", StandardCharsets.UTF_8);

        Process process = new ProcessBuilder(
                "bash", STAGE_SCRIPT.toString(), snapshot, "1", zip.toString(),
                tmp.resolve("work").toString()).start();
        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(process.waitFor(30, TimeUnit.SECONDS));

        assertEquals(2, process.exitValue());
        assertTrue(stderr.contains("refusing to stage snapshot version"), stderr);
    }

    @Test
    void stageScriptRejectsCandidateZero(@TempDir Path tmp) throws Exception {
        Path zip = tmp.resolve(FILE_NAME);
        Files.writeString(zip, "bytes", StandardCharsets.UTF_8);

        Process process = new ProcessBuilder(
                "bash", STAGE_SCRIPT.toString(), VERSION, "0", zip.toString(),
                tmp.resolve("work").toString()).start();
        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(process.waitFor(30, TimeUnit.SECONDS));

        assertEquals(2, process.exitValue());
        assertTrue(stderr.contains("candidate number must be a positive integer"), stderr);
    }

    /**
     * Promotion must copy the voted bytes through unchanged. The approved candidate is exported from dist/dev and lands
     * in the dist/release working copy byte-for-byte, having been checksum- and signature-verified on the way.
     */
    @Test
    void releaseScriptPromotesTheApprovedCandidateByteForByte(@TempDir Path tmp) throws Exception {
        ReleaseHarness harness = new ReleaseHarness(tmp, APPROVED_BYTES, true);

        Process process = harness.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(process.waitFor(60, TimeUnit.SECONDS));
        assertEquals(0, process.exitValue(), output);

        Path promoted = harness.distDir.resolve(VERSION + "/" + FILE_NAME);
        assertEquals(APPROVED_BYTES, Files.readString(promoted, StandardCharsets.UTF_8),
                "the promoted payload must be the exact bytes exported from dist/dev, never a rebuild");
        assertTrue(Files.exists(harness.distDir.resolve(VERSION + "/" + FILE_NAME + ".asc")),
                "the detached signature must be promoted alongside the payload");
        assertTrue(Files.exists(harness.distDir.resolve(VERSION + "/" + FILE_NAME + ".sha512")));
        assertTrue(harness.recordedSvn().contains("export"), harness.recordedSvn());
        assertTrue(harness.recordedGpg().contains("--verify"),
                "the exported candidate must have its signature checked: " + harness.recordedGpg());
    }

    /** A tampered payload must stop promotion before anything reaches the dist/release working copy. */
    @Test
    void releaseScriptRefusesACandidateThatFailsChecksumVerification(@TempDir Path tmp) throws Exception {
        ReleaseHarness harness = new ReleaseHarness(tmp, APPROVED_BYTES, false);

        Process process = harness.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(process.waitFor(60, TimeUnit.SECONDS));

        assertNotEquals(0, process.exitValue(), "a checksum mismatch must abort promotion: " + output);
        assertTrue(output.contains("SHA-512 verification failed"), output);
        assertFalse(Files.exists(harness.distDir.resolve(VERSION + "/" + FILE_NAME)),
                "nothing may reach the dist/release working copy after a failed verification");
    }

    /**
     * Pointing at the wrong RC directory is the dangerous case: every candidate carries its own self-consistent .sha512
     * and .asc, so those checks pass on a superseded candidate. Only the digest carried in the vote email distinguishes
     * them, so a mismatch must stop promotion.
     */
    @Test
    void releaseScriptRefusesACandidateThatIsNotTheOneTheVoteApproved(@TempDir Path tmp) throws Exception {
        ReleaseHarness harness = new ReleaseHarness(tmp, "bytes-of-a-superseded-candidate", true);

        Process process = harness.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(process.waitFor(60, TimeUnit.SECONDS));

        assertNotEquals(0, process.exitValue(), "a candidate the vote did not approve must abort promotion: " + output);
        assertTrue(output.contains("does not match the SHA-512 approved in the vote"), output);
        assertFalse(Files.exists(harness.distDir.resolve(VERSION + "/" + FILE_NAME)),
                "a superseded candidate must never reach the dist/release working copy");
    }

    @Test
    void releaseScriptRequiresTheApprovedDigestAlongsideACandidateNumber(@TempDir Path tmp) throws Exception {
        ReleaseHarness harness = new ReleaseHarness(tmp, APPROVED_BYTES, true);

        Process process = harness.start(null);
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(process.waitFor(60, TimeUnit.SECONDS));

        assertNotEquals(0, process.exitValue(), output);
        assertTrue(output.contains("winget-sha512"), output);
    }

    /**
     * Drives etc/scripts/release-distro.sh against stubbed wget/svn/gpg/chown/chmod so the real promotion flow runs
     * without network or ASF credentials. The svn stub serves the dist/dev candidate and backs the dist/release
     * checkout; {@code corruptChecksum} makes the exported .sha512 describe different bytes than the exported ZIP.
     */
    private final class ReleaseHarness {
        final Path download;
        final Path distDir;
        private final Path binDir;
        private final Path svnCalls;
        private final Path gpgCalls;

        ReleaseHarness(Path tmp, String payload, boolean validChecksum) throws Exception {
            download = tmp.resolve("download");
            distDir = download.resolve("dist");
            binDir = tmp.resolve("bin");
            svnCalls = tmp.resolve("svn-calls.txt");
            gpgCalls = tmp.resolve("gpg-calls.txt");
            Files.createDirectories(binDir);
            Files.createDirectories(download);

            Path artifacts = download.resolve(VERSION + "/org/apache/camel/apache-camel/" + VERSION);
            Files.createDirectories(artifacts);
            // What the real wget -r pulls down from the Nexus release repository, including the sidecars
            // release-distro.sh strips before generating its own SHA-512 files.
            for (String name : List.of("apache-camel-" + VERSION + ".pom", "apache-camel-" + VERSION + ".tar.gz",
                    "apache-camel-" + VERSION + ".zip")) {
                Files.writeString(artifacts.resolve(name), name, StandardCharsets.UTF_8);
                for (String sidecar : List.of(".asc", ".asc.asc", ".md5", ".sha1")) {
                    Files.writeString(artifacts.resolve(name + sidecar), "sidecar", StandardCharsets.UTF_8);
                }
            }

            writeExecutable(binDir.resolve("wget"), "#!/bin/sh\nexit 0\n");
            writeExecutable(binDir.resolve("chown"), "#!/bin/sh\nexit 0\n");
            writeExecutable(binDir.resolve("chmod"), "#!/bin/sh\nexit 0\n");
            writeExecutable(binDir.resolve("gpg"),
                    "#!/bin/sh\nprintf '%s\\n' \"$*\" >> \"" + gpgCalls + "\"\nexit 0\n");
            writeExecutable(binDir.resolve("svn"), svnStub(payload, validChecksum));
        }

        /**
         * Handles the four svn verbs release-distro.sh uses. `export` serves the approved candidate: the ZIP gets the
         * payload, the .sha512 is computed from whatever the ZIP actually holds (or from different bytes when the test
         * wants a mismatch), and the .asc is a placeholder the stubbed gpg accepts.
         */
        private String svnStub(String payload, boolean validChecksum) {
            String checksumSource = validChecksum ? "$destination_zip" : "-";
            return "#!/bin/sh\n"
                   + "printf '%s\\n' \"$*\" >> \"" + svnCalls + "\"\n"
                   + "verb=$1\n"
                   + "case \"$verb\" in\n"
                   + "  export)\n"
                   + "    destination=$3\n"
                   + "    destination_zip=${destination%.asc}\n"
                   + "    destination_zip=${destination_zip%.sha512}\n"
                   + "    case \"$destination\" in\n"
                   + "      *.sha512)\n"
                   + "        printf 'tampered' | sha512sum " + checksumSource + " \\\n"
                   + "          | sed \"s#[ ].*#  " + FILE_NAME + "#\" > \"$destination\" ;;\n"
                   + "      *.asc) printf 'signature\\n' > \"$destination\" ;;\n"
                   + "      *) printf '%s' '" + payload + "' > \"$destination\" ;;\n"
                   + "    esac ;;\n"
                   + "  co|checkout)\n"
                   + "    mkdir -p \"" + distDir + "/" + VERSION + "\" ;;\n"
                   + "  mkdir|add) : ;;\n"
                   + "esac\n"
                   + "exit 0\n";
        }

        /** Runs promotion with the digest the vote approved, i.e. the digest of {@link #APPROVED_BYTES}. */
        Process start() throws Exception {
            return start(sha512Hex(APPROVED_BYTES));
        }

        /** Runs promotion with an explicit approved digest, or omits the argument entirely when null. */
        Process start(String approvedDigest) throws Exception {
            List<String> command = new ArrayList<>(
                    List.of("bash", RELEASE_SCRIPT.toString(), VERSION, download.toString(), "1"));
            if (approvedDigest != null) {
                command.add(approvedDigest);
            }
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.environment().put("PATH", binDir + File.pathSeparator + System.getenv("PATH"));
            pb.redirectErrorStream(true);
            return pb.start();
        }

        String recordedSvn() throws IOException {
            return Files.exists(svnCalls) ? Files.readString(svnCalls, StandardCharsets.UTF_8) : "";
        }

        String recordedGpg() throws IOException {
            return Files.exists(gpgCalls) ? Files.readString(gpgCalls, StandardCharsets.UTF_8) : "";
        }
    }

    private static String sha512Hex(String content) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-512").digest(content.getBytes(StandardCharsets.UTF_8)));
    }

    private static void writeExecutable(Path path, String content) throws Exception {
        Files.writeString(path, content, StandardCharsets.UTF_8);
        assertTrue(path.toFile().setExecutable(true));
    }
}
