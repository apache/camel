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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WingetDistroScriptsTest {

    private static final Path ROOT = Paths.get("").toAbsolutePath().resolve("../../..").normalize();
    private static final Path STAGE_SCRIPT = ROOT.resolve("etc/scripts/stage-winget-distro.sh");
    private static final Path RELEASE_SCRIPT = ROOT.resolve("etc/scripts/release-distro.sh");
    private static final String VERSION = "9.9.9";
    private static final String FILE_NAME = "camel-launcher-9.9.9-winget-bin.zip";

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

    @Test
    void releaseScriptExportsAndVerifiesTheApprovedCandidate() throws Exception {
        String script = Files.readString(RELEASE_SCRIPT, StandardCharsets.UTF_8);

        assertTrue(script.contains("WINGET_CANDIDATE=${3:-}"), script);
        assertTrue(script.contains("https://dist.apache.org/repos/dist/dev/camel/apache-camel/"), script);
        assertTrue(script.contains("svn export"), script);
        assertTrue(script.contains("WINGET_NAME=\"camel-launcher-${VERSION}-winget-bin.zip\""), script);
        assertTrue(script.contains("for suffix in \"\" \".asc\" \".sha512\""), script);
        assertTrue(script.contains("sha512sum -c"), script);
        assertTrue(script.contains("gpg --verify"), script);
        assertFalse(script.contains("mvn "), "promotion must not rebuild the approved WinGet payload");
    }

    private static void writeExecutable(Path path, String content) throws Exception {
        Files.writeString(path, content, StandardCharsets.UTF_8);
        assertTrue(path.toFile().setExecutable(true));
    }
}
