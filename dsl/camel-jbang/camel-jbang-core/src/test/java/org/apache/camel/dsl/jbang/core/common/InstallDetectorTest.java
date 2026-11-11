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
package org.apache.camel.dsl.jbang.core.common;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import org.apache.camel.util.FileUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class InstallDetectorTest {

    @Test
    void classifiesWebInstallerPath(@TempDir Path temp) throws Exception {
        Path versionDir = temp.resolve("camel-cli/versions/4.22.0");
        Files.createDirectories(versionDir);

        InstallDetector.InstallMethod method = InstallDetector.classify(versionDir, temp);

        assertThat(method).isEqualTo(InstallDetector.InstallMethod.WEB_INSTALLER);
    }

    @Test
    void classifiesHomebrewPath() {
        Path path = Path.of("/opt/homebrew/Cellar/apache-camel/4.21.0/libexec");

        InstallDetector.InstallMethod method = InstallDetector.classify(path, Path.of("/unrelated"));

        assertThat(method).isEqualTo(InstallDetector.InstallMethod.HOMEBREW);
    }

    @Test
    void classifiesSdkmanPath() {
        Path path = Path.of(System.getProperty("user.home"), ".sdkman/candidates/camel/4.21.0");

        InstallDetector.InstallMethod method = InstallDetector.classify(path, Path.of("/unrelated"));

        assertThat(method).isEqualTo(InstallDetector.InstallMethod.SDKMAN);
    }

    @Test
    void classifiesJBangPath() {
        Path path = Path.of(System.getProperty("user.home"), ".jbang/cache/apps/camel");

        InstallDetector.InstallMethod method = InstallDetector.classify(path, Path.of("/unrelated"));

        assertThat(method).isEqualTo(InstallDetector.InstallMethod.JBANG);
    }

    @Test
    void classifiesChocolateyPath() {
        Path path = Path.of("C:\\ProgramData\\chocolatey\\lib\\camel-cli\\tools\\camel.bat");

        InstallDetector.InstallMethod method = InstallDetector.classify(path, Path.of("C:\\unrelated"));

        assertThat(method).isEqualTo(InstallDetector.InstallMethod.CHOCOLATEY);
    }

    @Test
    void classifiesWinGetPath() {
        Path path = Path.of("C:\\Users\\me\\AppData\\Local\\Microsoft\\WinGet\\Packages\\ApacheCamel.CLI_abc123\\camel.exe");

        InstallDetector.InstallMethod method = InstallDetector.classify(path, Path.of("C:\\unrelated"));

        assertThat(method).isEqualTo(InstallDetector.InstallMethod.WINGET);
    }

    @Test
    void classifiesScoopPath() {
        Path path = Path.of("C:\\Users\\me\\scoop\\apps\\camel-cli\\4.21.0\\bin\\camel.bat");

        InstallDetector.InstallMethod method = InstallDetector.classify(path, Path.of("C:\\unrelated"));

        assertThat(method).isEqualTo(InstallDetector.InstallMethod.SCOOP);
    }

    @Test
    void classifiesUnknownPath() {
        Path path = Path.of("/some/random/place/camel");

        InstallDetector.InstallMethod method = InstallDetector.classify(path, Path.of("/unrelated"));

        assertThat(method).isEqualTo(InstallDetector.InstallMethod.UNKNOWN);
    }

    @Test
    void pinnedVersionIsEmptyWhenFileAbsent(@TempDir Path temp) {
        Path versionsRoot = temp.resolve("camel-cli/versions");

        var result = InstallDetector.pinnedVersion(versionsRoot);

        assertThat(result).isEmpty();
    }

    @Test
    void pinnedVersionReadsContentWhenFilePresent(@TempDir Path temp) throws Exception {
        Path versionsRoot = temp.resolve("camel-cli/versions");
        Files.createDirectories(versionsRoot.getParent());
        Files.writeString(versionsRoot.getParent().resolve("pinned-version"), "4.21.0");

        var result = InstallDetector.pinnedVersion(versionsRoot);

        assertThat(result).contains("4.21.0");
    }

    @Test
    void pinnedVersionStripsSurroundingWhitespace(@TempDir Path temp) throws Exception {
        Path versionsRoot = temp.resolve("camel-cli/versions");
        Files.createDirectories(versionsRoot.getParent());
        Files.writeString(versionsRoot.getParent().resolve("pinned-version"), "4.21.0\n");

        var result = InstallDetector.pinnedVersion(versionsRoot);

        assertThat(result).contains("4.21.0");
    }

    @Test
    void pinnedVersionIsEmptyWhenFileIsBlank(@TempDir Path temp) throws Exception {
        Path versionsRoot = temp.resolve("camel-cli/versions");
        Files.createDirectories(versionsRoot.getParent());
        Files.writeString(versionsRoot.getParent().resolve("pinned-version"), "   \n");

        var result = InstallDetector.pinnedVersion(versionsRoot);

        assertThat(result).isEmpty();
    }

    @Test
    void resolveActiveOnPathFindsExecutableOnPath(@TempDir Path temp) throws Exception {
        String exeName = FileUtil.isWindows() ? "camel.cmd" : "camel";
        Path binDir = Files.createDirectories(temp.resolve("bin"));
        Path exe = Files.createFile(binDir.resolve(exeName));
        exe.toFile().setExecutable(true);

        var result = InstallDetector.resolveActiveOnPath(binDir + File.pathSeparator + "/nonexistent");

        assertThat(result).contains(exe);
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void resolveActiveOnPathFollowsTheSymlinkOfTheWebInstaller(@TempDir Path temp) throws Exception {
        Path camelSh = Files.createDirectories(temp.resolve("share/camel-cli/versions/4.23.0/bin")).resolve("camel.sh");
        Files.createFile(camelSh);
        Path binDir = Files.createDirectories(temp.resolve("bin"));
        // a relative link, as the web installer writes, and a second link to that one in another PATH entry
        Files.createSymbolicLink(binDir.resolve("camel"), Path.of("../share/camel-cli/versions/4.23.0/bin/camel.sh"));
        Path otherBin = Files.createDirectories(temp.resolve("other-bin"));
        Files.createSymbolicLink(otherBin.resolve("camel"), binDir.resolve("camel"));

        assertThat(InstallDetector.resolveActiveOnPath(binDir.toString())).contains(camelSh);
        assertThat(InstallDetector.resolveActiveOnPath(otherBin.toString())).contains(camelSh);
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void followLinksStopsAtALinkCycle(@TempDir Path temp) throws Exception {
        Path a = temp.resolve("a");
        Path b = temp.resolve("b");
        Files.createSymbolicLink(a, b);
        Files.createSymbolicLink(b, a);

        assertThat(InstallDetector.followLinks(a)).isIn(a, b);
    }

    @Test
    void nightlyBuildIsTheVersionDirectoryOfTheLauncher() {
        Path versions = Path.of("/home/user/.local/share/camel-cli/versions");

        assertThat(InstallDetector.nightlyBuildOf(
                versions.resolve("4.24.0-20261108.143653-24/lib/camel-launcher-4.24.0-SNAPSHOT.jar"), versions))
                .contains("4.24.0-20261108.143653-24");
        assertThat(InstallDetector.nightlyBuildOf(versions.resolve("4.23.0/lib/camel-launcher-4.23.0.jar"), versions))
                .isEmpty();
        assertThat(InstallDetector.nightlyBuildOf(Path.of("/opt/camel/lib/camel-launcher.jar"), versions)).isEmpty();
        assertThat(InstallDetector.nightlyBuildOf(versions, versions)).isEmpty();
        assertThat(InstallDetector.nightlyBuildOf(null, versions)).isEmpty();
    }

    @Test
    void nightlyBuildTimeIsTheUtcDeployTimestamp() {
        assertThat(InstallDetector.nightlyBuildTime("4.24.0-20261108.143653-24"))
                .contains(ZonedDateTime.of(2026, 11, 8, 14, 36, 53, 0, ZoneOffset.UTC));
        // not a valid date or time
        assertThat(InstallDetector.nightlyBuildTime("4.24.0-20261308.143653-24")).isEmpty();
        assertThat(InstallDetector.nightlyBuildTime("4.24.0")).isEmpty();
        assertThat(InstallDetector.nightlyBuildTime(null)).isEmpty();
    }

    @Test
    void resolveActiveOnPathReturnsEmptyWhenNotFound() {
        var result = InstallDetector.resolveActiveOnPath("/nonexistent/dir-a" + File.pathSeparator + "/nonexistent/dir-b");

        assertThat(result).isEmpty();
    }
}
