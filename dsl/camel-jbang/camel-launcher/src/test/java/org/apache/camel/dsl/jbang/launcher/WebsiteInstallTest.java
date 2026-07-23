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
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract suite for the canonical website installers (install.sh for POSIX, install.ps1 for Windows). Every scenario
 * runs the script against a loopback {@link WebsiteInstallerFixture} HTTPS server so no production endpoint is ever
 * contacted. Platform-specific tests live in the {@link PosixShell} and {@link WindowsPowerShell} nested classes.
 */
class WebsiteInstallTest {

    static void publishLatest(WebsiteInstallerFixture fixture, String version) throws Exception {
        Path tar = fixture.safeTar(version);
        Path zip = fixture.safeZip(version);
        publishRelease(fixture, version, tar, zip);
        fixture.publishManifest("/camel-cli/releases/latest.properties", version, tar, zip);
    }

    static void publishVersion(WebsiteInstallerFixture fixture, String version) throws Exception {
        Path tar = fixture.safeTar(version);
        Path zip = fixture.safeZip(version);
        publishRelease(fixture, version, tar, zip);
        fixture.publishManifest("/camel-cli/releases/" + version + ".properties", version, tar, zip);
    }

    static void publishRelease(WebsiteInstallerFixture fixture, String version, Path tar, Path zip) throws Exception {
        fixture.publish("/maven2/org/apache/camel/camel-launcher/" + version + "/camel-launcher-" + version + "-bin.tar.gz",
                Files.readAllBytes(tar));
        fixture.publish("/maven2/org/apache/camel/camel-launcher/" + version + "/camel-launcher-" + version + "-bin.zip",
                Files.readAllBytes(zip));
    }

    static void serveTruncatedThenClose(ServerSocket rawServer, byte[] fullBody) {
        try (Socket client = rawServer.accept()) {
            String headers = "HTTP/1.1 200 OK\r\nContent-Length: " + fullBody.length + "\r\nConnection: close\r\n\r\n";
            OutputStream out = client.getOutputStream();
            out.write(headers.getBytes(StandardCharsets.US_ASCII));
            out.write(fullBody, 0, fullBody.length / 2);
            out.flush();
        } catch (IOException ignored) {
        }
    }

    static Stream<Arguments> invalidManifests() {
        String hash = "a".repeat(64);
        return Stream.of(
                Arguments.of("missing-key", "format=1\nversion=1.0.0\ntar_sha256=" + hash + "\n"),
                Arguments.of("duplicate-key",
                        "format=1\nformat=1\nversion=1.0.0\ntar_sha256=" + hash + "\nzip_sha256=" + hash + "\n"),
                Arguments.of("blank-line",
                        "format=1\n\nversion=1.0.0\ntar_sha256=" + hash + "\nzip_sha256=" + hash + "\n"),
                Arguments.of("unknown-key",
                        "format=1\nversion=1.0.0\ntar_sha256=" + hash + "\nzip_sha256=" + hash + "\nextra=1\n"),
                Arguments.of("bad-format",
                        "format=2\nversion=1.0.0\ntar_sha256=" + hash + "\nzip_sha256=" + hash + "\n"),
                Arguments.of("bad-version",
                        "format=1\nversion=1.0\ntar_sha256=" + hash + "\nzip_sha256=" + hash + "\n"),
                Arguments.of("bad-tar-hash",
                        "format=1\nversion=1.0.0\ntar_sha256=not-hex\nzip_sha256=" + hash + "\n"),
                Arguments.of("bad-zip-hash",
                        "format=1\nversion=1.0.0\ntar_sha256=" + hash + "\nzip_sha256=short\n"));
    }

    interface MaliciousArchive {
        Path build(WebsiteInstallerFixture fixture) throws Exception;
    }

    // POSIX (install.sh)

    @Nested
    @DisabledOnOs(OS.WINDOWS)
    class PosixShell {

        private static final Path SCRIPT = Paths.get("src/install/install.sh").toAbsolutePath();

        private static WebsiteInstallerFixture.Result install(
                WebsiteInstallerFixture fixture, Path home, String version)
                throws Exception {
            List<String> command = new ArrayList<>(List.of("/bin/sh", SCRIPT.toString()));
            if (version != null) {
                command.add("--version");
                command.add(version);
            }
            return fixture.run(command, fixture.environment(home));
        }

        private static void assertVersionInstalled(Path home, String version) throws Exception {
            Path shim = home.resolve(".local/bin/camel");
            assertTrue(Files.isSymbolicLink(shim), "expected a symlink at " + shim);
            Path versionDir = home.resolve(".local/share/camel-cli/versions/" + version);
            assertTrue(Files.isDirectory(versionDir), "expected version directory " + versionDir);

            Process check = new ProcessBuilder("/bin/sh", shim.toString(), "version")
                    .redirectErrorStream(true)
                    .start();
            String output = new String(check.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            check.waitFor();
            assertTrue(output.contains("Camel " + version), output);
        }

        private void assertMaliciousTarRejected(Path temp, MaliciousArchive archiveBuilder) throws Exception {
            try (WebsiteInstallerFixture fixture = WebsiteInstallerFixture.start(temp.resolve("fixture"))) {
                Path home = Files.createDirectory(temp.resolve("home"));
                Path tar = archiveBuilder.build(fixture);
                Path zip = fixture.safeZip("9.9.9");
                publishRelease(fixture, "9.9.9", tar, zip);
                fixture.publishManifest("/camel-cli/releases/9.9.9.properties", "9.9.9", tar, zip);

                WebsiteInstallerFixture.Result r = install(fixture, home, "9.9.9");

                assertNotEquals(0, r.exit());
                assertFalse(Files.exists(home.resolve(".local/bin/camel")));
            }
        }

        @Test
        void installsLatestVersion(@TempDir Path temp) throws Exception {
            try (WebsiteInstallerFixture fixture = WebsiteInstallerFixture.start(temp.resolve("fixture"))) {
                Path home = Files.createDirectory(temp.resolve("home"));
                publishLatest(fixture, "1.2.3");

                WebsiteInstallerFixture.Result r = install(fixture, home, null);

                assertEquals(0, r.exit(), r.stderr());
                assertVersionInstalled(home, "1.2.3");
            }
        }

        @Test
        void installsExactVersion(@TempDir Path temp) throws Exception {
            try (WebsiteInstallerFixture fixture = WebsiteInstallerFixture.start(temp.resolve("fixture"))) {
                Path home = Files.createDirectory(temp.resolve("home"));
                publishVersion(fixture, "2.5.0");

                WebsiteInstallerFixture.Result r = install(fixture, home, "2.5.0");

                assertEquals(0, r.exit(), r.stderr());
                assertVersionInstalled(home, "2.5.0");
            }
        }

        @Test
        void honorsCustomXdgDataHome(@TempDir Path temp) throws Exception {
            try (WebsiteInstallerFixture fixture = WebsiteInstallerFixture.start(temp.resolve("fixture"))) {
                Path home = Files.createDirectory(temp.resolve("home"));
                Path xdg = Files.createDirectory(temp.resolve("xdg-data"));
                publishLatest(fixture, "1.0.0");

                Map<String, String> env = new HashMap<>(fixture.environment(home));
                env.put("XDG_DATA_HOME", xdg.toString());
                WebsiteInstallerFixture.Result r = fixture.run(List.of("/bin/sh", SCRIPT.toString()), env);

                assertEquals(0, r.exit(), r.stderr());
                assertTrue(Files.isDirectory(xdg.resolve("camel-cli/versions/1.0.0")), r.stdout());
                assertTrue(Files.isSymbolicLink(home.resolve(".local/bin/camel")));
            }
        }

        @Test
        void handlesSpacesAndUnicodeInHome(@TempDir Path temp) throws Exception {
            try (WebsiteInstallerFixture fixture = WebsiteInstallerFixture.start(temp.resolve("fixture"))) {
                Path home = Files.createDirectories(temp.resolve("home dir/über"));
                publishLatest(fixture, "1.0.0");

                WebsiteInstallerFixture.Result r = install(fixture, home, null);

                assertEquals(0, r.exit(), r.stderr());
                assertVersionInstalled(home, "1.0.0");
            }
        }

        @Test
        void upgradeKeepsPreviousVersionDirectory(@TempDir Path temp) throws Exception {
            try (WebsiteInstallerFixture fixture = WebsiteInstallerFixture.start(temp.resolve("fixture"))) {
                Path home = Files.createDirectory(temp.resolve("home"));
                publishVersion(fixture, "1.0.0");
                assertEquals(0, install(fixture, home, "1.0.0").exit());

                publishVersion(fixture, "2.0.0");
                WebsiteInstallerFixture.Result r = install(fixture, home, "2.0.0");

                assertEquals(0, r.exit(), r.stderr());
                assertVersionInstalled(home, "2.0.0");
                assertTrue(Files.isDirectory(home.resolve(".local/share/camel-cli/versions/1.0.0")),
                        "old version dir removed");
            }
        }

        @Test
        void downgradeToExplicitOlderVersionSucceeds(@TempDir Path temp) throws Exception {
            try (WebsiteInstallerFixture fixture = WebsiteInstallerFixture.start(temp.resolve("fixture"))) {
                Path home = Files.createDirectory(temp.resolve("home"));
                publishVersion(fixture, "1.0.0");
                assertEquals(0, install(fixture, home, "1.0.0").exit());
                publishVersion(fixture, "2.0.0");
                assertEquals(0, install(fixture, home, "2.0.0").exit());

                WebsiteInstallerFixture.Result r = install(fixture, home, "1.0.0");

                assertEquals(0, r.exit(), r.stderr());
                assertVersionInstalled(home, "1.0.0");
                assertTrue(Files.isDirectory(home.resolve(".local/share/camel-cli/versions/2.0.0")));
            }
        }

        @Test
        void reinstallingSameVersionSucceeds(@TempDir Path temp) throws Exception {
            try (WebsiteInstallerFixture fixture = WebsiteInstallerFixture.start(temp.resolve("fixture"))) {
                Path home = Files.createDirectory(temp.resolve("home"));
                publishVersion(fixture, "1.0.0");

                assertEquals(0, install(fixture, home, "1.0.0").exit());
                WebsiteInstallerFixture.Result r = install(fixture, home, "1.0.0");

                assertEquals(0, r.exit(), r.stderr());
                assertVersionInstalled(home, "1.0.0");
            }
        }

        @Test
        void printsPathGuidanceWhenBinDirMissingFromPath(@TempDir Path temp) throws Exception {
            try (WebsiteInstallerFixture fixture = WebsiteInstallerFixture.start(temp.resolve("fixture"))) {
                Path home = Files.createDirectory(temp.resolve("home"));
                publishLatest(fixture, "1.0.0");

                WebsiteInstallerFixture.Result r = install(fixture, home, null);

                assertEquals(0, r.exit(), r.stderr());
                assertTrue(r.stdout().contains(".local/bin"), r.stdout());
                assertTrue(r.stdout().toUpperCase().contains("PATH"), r.stdout());
            }
        }

        @Test
        void neverWritesShellProfileFiles(@TempDir Path temp) throws Exception {
            try (WebsiteInstallerFixture fixture = WebsiteInstallerFixture.start(temp.resolve("fixture"))) {
                Path home = Files.createDirectory(temp.resolve("home"));
                publishLatest(fixture, "1.0.0");

                assertEquals(0, install(fixture, home, null).exit());

                for (String profile : List.of(".bashrc", ".profile", ".bash_profile", ".zshrc")) {
                    assertFalse(Files.exists(home.resolve(profile)), profile + " must not be created");
                }
            }
        }

        @Test
        void preservesActiveInstallWhenLaterInstallFails(@TempDir Path temp) throws Exception {
            try (WebsiteInstallerFixture fixture = WebsiteInstallerFixture.start(temp.resolve("fixture"))) {
                Path home = Files.createDirectory(temp.resolve("home"));
                publishVersion(fixture, "1.0.0");
                assertEquals(0, install(fixture, home, "1.0.0").exit());

                Path badTar = fixture.safeTar("2.0.0");
                Path badZip = fixture.safeZip("2.0.0");
                fixture.publish("/maven2/org/apache/camel/camel-launcher/2.0.0/camel-launcher-2.0.0-bin.tar.gz",
                        Files.readAllBytes(badTar));
                fixture.publish("/maven2/org/apache/camel/camel-launcher/2.0.0/camel-launcher-2.0.0-bin.zip",
                        Files.readAllBytes(badZip));
                String bogusHash = "0".repeat(64);
                fixture.publish("/camel-cli/releases/2.0.0.properties",
                        ("format=1\nversion=2.0.0\ntar_sha256=" + bogusHash + "\nzip_sha256=" + bogusHash + "\n")
                                .getBytes(StandardCharsets.UTF_8));

                WebsiteInstallerFixture.Result r = install(fixture, home, "2.0.0");

                assertNotEquals(0, r.exit());
                assertVersionInstalled(home, "1.0.0");
                assertFalse(Files.exists(home.resolve(".local/share/camel-cli/versions/2.0.0")));
            }
        }

        @Test
        void rejectsUnknownFlag(@TempDir Path temp) throws Exception {
            try (WebsiteInstallerFixture fixture = WebsiteInstallerFixture.start(temp.resolve("fixture"))) {
                Path home = Files.createDirectory(temp.resolve("home"));
                WebsiteInstallerFixture.Result r = fixture.run(
                        List.of("/bin/sh", SCRIPT.toString(), "--bogus"), fixture.environment(home));

                assertNotEquals(0, r.exit());
            }
        }

        @Test
        void rejectsMissingVersionValue(@TempDir Path temp) throws Exception {
            try (WebsiteInstallerFixture fixture = WebsiteInstallerFixture.start(temp.resolve("fixture"))) {
                Path home = Files.createDirectory(temp.resolve("home"));
                WebsiteInstallerFixture.Result r = fixture.run(
                        List.of("/bin/sh", SCRIPT.toString(), "--version"), fixture.environment(home));

                assertNotEquals(0, r.exit());
            }
        }

        @Test
        void rejectsMalformedVersionArgument(@TempDir Path temp) throws Exception {
            try (WebsiteInstallerFixture fixture = WebsiteInstallerFixture.start(temp.resolve("fixture"))) {
                Path home = Files.createDirectory(temp.resolve("home"));
                WebsiteInstallerFixture.Result r = install(fixture, home, "1.2.3-SNAPSHOT");

                assertNotEquals(0, r.exit());
                assertFalse(Files.exists(home.resolve(".local/bin/camel")));
            }
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("org.apache.camel.dsl.jbang.launcher.WebsiteInstallTest#invalidManifests")
        void rejectsInvalidManifest(String name, String manifestBody, @TempDir Path temp) throws Exception {
            try (WebsiteInstallerFixture fixture = WebsiteInstallerFixture.start(temp.resolve("fixture"))) {
                Path home = Files.createDirectory(temp.resolve("home"));
                fixture.publish("/camel-cli/releases/latest.properties",
                        manifestBody.getBytes(StandardCharsets.UTF_8));

                WebsiteInstallerFixture.Result r = install(fixture, home, null);

                assertNotEquals(0, r.exit(), name);
                assertFalse(Files.exists(home.resolve(".local/bin/camel")), name);
            }
        }

        @Test
        void manifestInjectionNeverExecutesShellCode(@TempDir Path temp) throws Exception {
            try (WebsiteInstallerFixture fixture = WebsiteInstallerFixture.start(temp.resolve("fixture"))) {
                Path home = Files.createDirectory(temp.resolve("home"));
                String hash = "a".repeat(64);
                String manifest
                        = "format=1\nversion=$(touch owned)\ntar_sha256=" + hash + "\nzip_sha256=" + hash + "\n";
                fixture.publish("/camel-cli/releases/latest.properties",
                        manifest.getBytes(StandardCharsets.UTF_8));

                WebsiteInstallerFixture.Result r = install(fixture, home, null);

                assertNotEquals(0, r.exit());
                assertFalse(Files.exists(home.resolve("owned")), "manifest value must never be executed");
            }
        }

        @Test
        void rejectsChecksumMismatch(@TempDir Path temp) throws Exception {
            try (WebsiteInstallerFixture fixture = WebsiteInstallerFixture.start(temp.resolve("fixture"))) {
                Path home = Files.createDirectory(temp.resolve("home"));
                Path tar = fixture.safeTar("1.0.0");
                Path zip = fixture.safeZip("1.0.0");
                publishRelease(fixture, "1.0.0", tar, zip);
                String bogusHash = "0".repeat(64);
                fixture.publish("/camel-cli/releases/latest.properties",
                        ("format=1\nversion=1.0.0\ntar_sha256=" + bogusHash + "\nzip_sha256=" + bogusHash + "\n")
                                .getBytes(StandardCharsets.UTF_8));

                WebsiteInstallerFixture.Result r = install(fixture, home, null);

                assertNotEquals(0, r.exit());
                assertFalse(Files.exists(home.resolve(".local/bin/camel")));
            }
        }

        @Test
        void rejectsInterruptedDownload(@TempDir Path temp) throws Exception {
            try (WebsiteInstallerFixture fixture = WebsiteInstallerFixture.start(temp.resolve("fixture"));
                 ServerSocket rawServer = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
                Path home = Files.createDirectory(temp.resolve("home"));
                Path tar = fixture.safeTar("1.0.0");
                Path zip = fixture.safeZip("1.0.0");
                fixture.publishManifest("/camel-cli/releases/latest.properties", "1.0.0", tar, zip);

                byte[] tarBytes = Files.readAllBytes(tar);
                Thread server = new Thread(() -> serveTruncatedThenClose(rawServer, tarBytes));
                server.setDaemon(true);
                server.start();

                Map<String, String> env = new HashMap<>(fixture.environment(home));
                env.put("CAMEL_INSTALL_MAVEN_BASE_URL", "http://127.0.0.1:" + rawServer.getLocalPort());

                WebsiteInstallerFixture.Result r = fixture.run(List.of("/bin/sh", SCRIPT.toString()), env);

                assertNotEquals(0, r.exit());
                assertFalse(Files.exists(home.resolve(".local/bin/camel")));
            }
        }

        @Test
        void rejectsWhenChecksumToolMissing(@TempDir Path temp) throws Exception {
            try (WebsiteInstallerFixture fixture = WebsiteInstallerFixture.start(temp.resolve("fixture"))) {
                Path home = Files.createDirectory(temp.resolve("home"));
                publishLatest(fixture, "1.0.0");

                Map<String, String> env = new HashMap<>(fixture.environment(home));
                env.put("PATH",
                        pathWithout(temp.resolve("restricted-path"), "sha256sum", "shasum", "openssl").toString());

                WebsiteInstallerFixture.Result r = fixture.run(List.of("/bin/sh", SCRIPT.toString()), env);

                assertNotEquals(0, r.exit());
                assertFalse(Files.exists(home.resolve(".local/bin/camel")));
            }
        }

        @Test
        void rejectsAbsolutePathEntry(@TempDir Path temp) throws Exception {
            assertMaliciousTarRejected(temp, fixture -> fixture.maliciousTar("/etc/passwd"));
        }

        @Test
        void rejectsTraversalEntry(@TempDir Path temp) throws Exception {
            assertMaliciousTarRejected(temp, fixture -> fixture.maliciousTar("../escape"));
        }

        @Test
        void rejectsMultipleTopLevelRoots(@TempDir Path temp) throws Exception {
            assertMaliciousTarRejected(temp, fixture -> fixture.maliciousTar("evil-root/payload"));
        }

        @Test
        void rejectsEscapingSymlinkEntry(@TempDir Path temp) throws Exception {
            assertMaliciousTarRejected(temp,
                    fixture -> fixture.maliciousTarSymlink("camel-launcher-9.9.9/escape-link", "../../outside"));
        }

        @Test
        void rejectsArchiveMissingCamelSh(@TempDir Path temp) throws Exception {
            try (WebsiteInstallerFixture fixture = WebsiteInstallerFixture.start(temp.resolve("fixture"))) {
                Path home = Files.createDirectory(temp.resolve("home"));
                Path tar = fixture.safeTarMissingCamelSh("9.9.9");
                Path zip = fixture.safeZip("9.9.9");
                publishRelease(fixture, "9.9.9", tar, zip);
                fixture.publishManifest("/camel-cli/releases/9.9.9.properties", "9.9.9", tar, zip);

                WebsiteInstallerFixture.Result r = install(fixture, home, "9.9.9");

                assertNotEquals(0, r.exit());
                assertFalse(Files.exists(home.resolve(".local/bin/camel")));
            }
        }

        @Test
        void rejectsWhenStagedLauncherFails(@TempDir Path temp) throws Exception {
            try (WebsiteInstallerFixture fixture = WebsiteInstallerFixture.start(temp.resolve("fixture"))) {
                Path home = Files.createDirectory(temp.resolve("home"));
                Path tar = fixture.safeTarWithFailingLauncher("9.9.9");
                Path zip = fixture.safeZip("9.9.9");
                publishRelease(fixture, "9.9.9", tar, zip);
                fixture.publishManifest("/camel-cli/releases/9.9.9.properties", "9.9.9", tar, zip);

                WebsiteInstallerFixture.Result r = install(fixture, home, "9.9.9");

                assertNotEquals(0, r.exit());
                assertFalse(Files.exists(home.resolve(".local/bin/camel")));
                assertFalse(Files.exists(home.resolve(".local/share/camel-cli/versions/9.9.9")));
            }
        }

        private static Path pathWithout(Path scratch, String... blockedNames) throws Exception {
            Set<String> blocked = new HashSet<>(List.of(blockedNames));
            Path bin = Files.createDirectories(scratch.resolve("bin-" + UUID.randomUUID()));
            Set<String> seen = new HashSet<>();
            String realPath = System.getenv("PATH");
            for (String dir : realPath.split(File.pathSeparator)) {
                Path dirPath = Paths.get(dir);
                if (!Files.isDirectory(dirPath)) {
                    continue;
                }
                try (var stream = Files.list(dirPath)) {
                    for (Path entry : (Iterable<Path>) stream::iterator) {
                        String name = entry.getFileName().toString();
                        if (blocked.contains(name) || !seen.add(name) || !Files.isExecutable(entry)) {
                            continue;
                        }
                        Files.createSymbolicLink(bin.resolve(name), entry.toAbsolutePath());
                    }
                } catch (Exception ignored) {
                }
            }
            return bin;
        }
    }

    // Windows (install.ps1)

    @Nested
    @EnabledOnOs(OS.WINDOWS)
    class WindowsPowerShell {

        private static final Path SCRIPT = Paths.get("src/install/install.ps1").toAbsolutePath();

        private final List<String> binDirsForCleanup = new ArrayList<>();

        @AfterEach
        void restoreUserPath() throws Exception {
            if (binDirsForCleanup.isEmpty()) {
                return;
            }
            StringBuilder script = new StringBuilder("$dirs = @(");
            for (int i = 0; i < binDirsForCleanup.size(); i++) {
                if (i > 0) {
                    script.append(',');
                }
                script.append('\'').append(binDirsForCleanup.get(i).replace("'", "''")).append('\'');
            }
            // Read and write the user PATH raw (DoNotExpandEnvironmentNames, preserving the value kind) so
            // the cleanup never flattens existing %VAR% references, mirroring install.ps1's Add-UserPath.
            script.append("); $k = [Microsoft.Win32.Registry]::CurrentUser.OpenSubKey('Environment', $true); "
                          + "if ($k) { $path = $k.GetValue('Path', '', "
                          + "[Microsoft.Win32.RegistryValueOptions]::DoNotExpandEnvironmentNames); "
                          + "if ($path) { $kind = $k.GetValueKind('Path'); "
                          + "$kept = ($path -split ';') | Where-Object { $_ -and (-not ($dirs -icontains $_)) }; "
                          + "$k.SetValue('Path', ($kept -join ';'), $kind) } $k.Close() }");
            Process cleanup = new ProcessBuilder("powershell", "-NoProfile", "-Command", script.toString())
                    .redirectErrorStream(true).start();
            cleanup.waitFor(30, TimeUnit.SECONDS);
        }

        private WebsiteInstallerFixture.Result install(
                WebsiteInstallerFixture fixture, Path home, String version)
                throws Exception {
            binDirsForCleanup.add(expectedBinDir(home).toString());
            List<String> command = new ArrayList<>(
                    List.of("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", SCRIPT.toString()));
            if (version != null) {
                command.add("-Version");
                command.add(version);
            }
            return fixture.run(command, fixture.environment(home));
        }

        private static Path expectedBinDir(Path home) {
            return home.resolve("AppData").resolve("Local").resolve("Apache Camel").resolve("bin");
        }

        private static Path versionDir(Path home, String version) {
            return home.resolve("AppData").resolve("Local").resolve("Apache Camel").resolve("cli").resolve("versions")
                    .resolve(version);
        }

        private static void assertVersionInstalled(Path home, String version) throws Exception {
            Path shim = expectedBinDir(home).resolve("camel.cmd");
            assertTrue(Files.isRegularFile(shim), "expected shim at " + shim);
            assertTrue(Files.isDirectory(versionDir(home, version)),
                    "expected version directory " + versionDir(home, version));

            Process check = new ProcessBuilder("cmd.exe", "/c", shim.toString(), "version")
                    .redirectErrorStream(true)
                    .start();
            String output = new String(check.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            check.waitFor(30, TimeUnit.SECONDS);
            assertTrue(output.contains("Camel " + version), output);
        }

        private void assertMaliciousZipRejected(Path temp, MaliciousArchive archiveBuilder) throws Exception {
            try (WebsiteInstallerFixture fixture = WebsiteInstallerFixture.start(temp.resolve("fixture"))) {
                Path home = Files.createDirectory(temp.resolve("home"));
                Path zip = archiveBuilder.build(fixture);
                Path tar = fixture.safeTar("9.9.9");
                publishRelease(fixture, "9.9.9", tar, zip);
                fixture.publishManifest("/camel-cli/releases/9.9.9.properties", "9.9.9", tar, zip);

                WebsiteInstallerFixture.Result r = install(fixture, home, "9.9.9");

                assertNotEquals(0, r.exit());
                assertFalse(Files.exists(expectedBinDir(home).resolve("camel.cmd")));
            }
        }

        private static String queryEnvironmentPath(String scope) throws Exception {
            Process p = new ProcessBuilder(
                    "powershell", "-NoProfile", "-Command",
                    "[Environment]::GetEnvironmentVariable('Path','" + scope + "')")
                    .redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            p.waitFor(30, TimeUnit.SECONDS);
            return out;
        }

        private static long countOccurrencesCaseInsensitive(String path, String dir) {
            if (path == null || path.isEmpty()) {
                return 0;
            }
            return Arrays.stream(path.split(";"))
                    .filter(entry -> entry.equalsIgnoreCase(dir))
                    .count();
        }

        // Reads HKCU\Environment\Path without expanding %VAR% references, returning "" when unset.
        private static String readUserPathRaw() throws Exception {
            Process p = new ProcessBuilder(
                    "powershell", "-NoProfile", "-Command",
                    "$k = [Microsoft.Win32.Registry]::CurrentUser.OpenSubKey('Environment'); "
                                                            + "if ($k) { [Console]::Out.Write($k.GetValue('Path', '', "
                                                            + "[Microsoft.Win32.RegistryValueOptions]::DoNotExpandEnvironmentNames)); $k.Close() }")
                    .start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            p.waitFor(30, TimeUnit.SECONDS);
            return out;
        }

        // Writes HKCU\Environment\Path as a REG_EXPAND_SZ value; the value is passed via the environment
        // to avoid any quoting or injection in the PowerShell command line.
        private static void writeUserPathExpandable(String value) throws Exception {
            ProcessBuilder pb = new ProcessBuilder(
                    "powershell", "-NoProfile", "-Command",
                    "$k = [Microsoft.Win32.Registry]::CurrentUser.CreateSubKey('Environment'); "
                                                            + "$k.SetValue('Path', $env:CAMEL_TEST_USERPATH, "
                                                            + "[Microsoft.Win32.RegistryValueKind]::ExpandString); $k.Close()")
                    .redirectErrorStream(true);
            pb.environment().put("CAMEL_TEST_USERPATH", value);
            Process p = pb.start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!p.waitFor(30, TimeUnit.SECONDS) || p.exitValue() != 0) {
                throw new IllegalStateException("failed to seed user PATH: " + out);
            }
        }

        private static void deleteUserPath() throws Exception {
            Process p = new ProcessBuilder(
                    "powershell", "-NoProfile", "-Command",
                    "$k = [Microsoft.Win32.Registry]::CurrentUser.OpenSubKey('Environment', $true); "
                                                            + "if ($k) { $k.DeleteValue('Path', $false); $k.Close() }")
                    .redirectErrorStream(true).start();
            p.getInputStream().readAllBytes();
            p.waitFor(30, TimeUnit.SECONDS);
        }

        @Test
        void installsLatestVersion(@TempDir Path temp) throws Exception {
            try (WebsiteInstallerFixture fixture = WebsiteInstallerFixture.start(temp.resolve("fixture"))) {
                Path home = Files.createDirectory(temp.resolve("home"));
                publishLatest(fixture, "1.2.3");

                WebsiteInstallerFixture.Result r = install(fixture, home, null);

                assertEquals(0, r.exit(), r.stderr());
                assertVersionInstalled(home, "1.2.3");
            }
        }

        @Test
        void installsExactVersion(@TempDir Path temp) throws Exception {
            try (WebsiteInstallerFixture fixture = WebsiteInstallerFixture.start(temp.resolve("fixture"))) {
                Path home = Files.createDirectory(temp.resolve("home"));
                publishVersion(fixture, "2.5.0");

                WebsiteInstallerFixture.Result r = install(fixture, home, "2.5.0");

                assertEquals(0, r.exit(), r.stderr());
                assertVersionInstalled(home, "2.5.0");
            }
        }

        @Test
        void handlesSpacesAndUnicodeInLocalAppData(@TempDir Path temp) throws Exception {
            try (WebsiteInstallerFixture fixture = WebsiteInstallerFixture.start(temp.resolve("fixture"))) {
                Path home = Files.createDirectories(temp.resolve("home dir/über"));
                publishLatest(fixture, "1.0.0");

                WebsiteInstallerFixture.Result r = install(fixture, home, null);

                assertEquals(0, r.exit(), r.stderr());
                assertVersionInstalled(home, "1.0.0");
            }
        }

        @Test
        void upgradeKeepsPreviousVersionDirectory(@TempDir Path temp) throws Exception {
            try (WebsiteInstallerFixture fixture = WebsiteInstallerFixture.start(temp.resolve("fixture"))) {
                Path home = Files.createDirectory(temp.resolve("home"));
                publishVersion(fixture, "1.0.0");
                assertEquals(0, install(fixture, home, "1.0.0").exit());

                publishVersion(fixture, "2.0.0");
                WebsiteInstallerFixture.Result r = install(fixture, home, "2.0.0");

                assertEquals(0, r.exit(), r.stderr());
                assertVersionInstalled(home, "2.0.0");
                assertTrue(Files.isDirectory(versionDir(home, "1.0.0")), "old version dir removed");
            }
        }

        @Test
        void downgradeToExplicitOlderVersionSucceeds(@TempDir Path temp) throws Exception {
            try (WebsiteInstallerFixture fixture = WebsiteInstallerFixture.start(temp.resolve("fixture"))) {
                Path home = Files.createDirectory(temp.resolve("home"));
                publishVersion(fixture, "1.0.0");
                assertEquals(0, install(fixture, home, "1.0.0").exit());
                publishVersion(fixture, "2.0.0");
                assertEquals(0, install(fixture, home, "2.0.0").exit());

                WebsiteInstallerFixture.Result r = install(fixture, home, "1.0.0");

                assertEquals(0, r.exit(), r.stderr());
                assertVersionInstalled(home, "1.0.0");
                assertTrue(Files.isDirectory(versionDir(home, "2.0.0")));
            }
        }

        @Test
        void reinstallingSameVersionSucceeds(@TempDir Path temp) throws Exception {
            try (WebsiteInstallerFixture fixture = WebsiteInstallerFixture.start(temp.resolve("fixture"))) {
                Path home = Files.createDirectory(temp.resolve("home"));
                publishVersion(fixture, "1.0.0");

                assertEquals(0, install(fixture, home, "1.0.0").exit());
                WebsiteInstallerFixture.Result r = install(fixture, home, "1.0.0");

                assertEquals(0, r.exit(), r.stderr());
                assertVersionInstalled(home, "1.0.0");
            }
        }

        @Test
        void addsUserPathOnceAndIsIdempotent(@TempDir Path temp) throws Exception {
            try (WebsiteInstallerFixture fixture = WebsiteInstallerFixture.start(temp.resolve("fixture"))) {
                Path home = Files.createDirectory(temp.resolve("home"));
                publishLatest(fixture, "1.0.0");
                String binDir = expectedBinDir(home).toString();

                assertEquals(0, install(fixture, home, null).exit());
                assertEquals(1, countOccurrencesCaseInsensitive(queryEnvironmentPath("User"), binDir));

                assertEquals(0, install(fixture, home, null).exit());
                assertEquals(1, countOccurrencesCaseInsensitive(queryEnvironmentPath("User"), binDir));
            }
        }

        @Test
        void neverWritesMachinePath(@TempDir Path temp) throws Exception {
            try (WebsiteInstallerFixture fixture = WebsiteInstallerFixture.start(temp.resolve("fixture"))) {
                Path home = Files.createDirectory(temp.resolve("home"));
                publishLatest(fixture, "1.0.0");
                String before = queryEnvironmentPath("Machine");

                WebsiteInstallerFixture.Result r = install(fixture, home, null);

                assertEquals(0, r.exit(), r.stderr());
                assertEquals(before, queryEnvironmentPath("Machine"), "machine PATH must never be modified");
            }
        }

        @Test
        void forwardsArgumentsAndExitCodeThroughShim(@TempDir Path temp) throws Exception {
            try (WebsiteInstallerFixture fixture = WebsiteInstallerFixture.start(temp.resolve("fixture"))) {
                Path home = Files.createDirectory(temp.resolve("home"));
                publishLatest(fixture, "1.0.0");
                assertEquals(0, install(fixture, home, null).exit());

                Path shim = expectedBinDir(home).resolve("camel.cmd");

                Process argsProc = new ProcessBuilder("cmd.exe", "/c", shim.toString(), "echo-args", "foo", "bar baz")
                        .redirectErrorStream(true).start();
                String argsOut = new String(argsProc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                argsProc.waitFor(30, TimeUnit.SECONDS);
                assertTrue(argsOut.contains("foo") && argsOut.contains("bar baz"), argsOut);

                Process exitProc = new ProcessBuilder("cmd.exe", "/c", shim.toString(), "exit-code", "7")
                        .redirectErrorStream(true).start();
                exitProc.waitFor(30, TimeUnit.SECONDS);
                assertEquals(7, exitProc.exitValue());
            }
        }

        @Test
        void preservesActiveInstallWhenLaterInstallFails(@TempDir Path temp) throws Exception {
            try (WebsiteInstallerFixture fixture = WebsiteInstallerFixture.start(temp.resolve("fixture"))) {
                Path home = Files.createDirectory(temp.resolve("home"));
                publishVersion(fixture, "1.0.0");
                assertEquals(0, install(fixture, home, "1.0.0").exit());

                Path badTar = fixture.safeTar("2.0.0");
                Path badZip = fixture.safeZip("2.0.0");
                fixture.publish("/maven2/org/apache/camel/camel-launcher/2.0.0/camel-launcher-2.0.0-bin.tar.gz",
                        Files.readAllBytes(badTar));
                fixture.publish("/maven2/org/apache/camel/camel-launcher/2.0.0/camel-launcher-2.0.0-bin.zip",
                        Files.readAllBytes(badZip));
                String bogusHash = "0".repeat(64);
                fixture.publish("/camel-cli/releases/2.0.0.properties",
                        ("format=1\nversion=2.0.0\ntar_sha256=" + bogusHash + "\nzip_sha256=" + bogusHash + "\n")
                                .getBytes(StandardCharsets.UTF_8));

                WebsiteInstallerFixture.Result r = install(fixture, home, "2.0.0");

                assertNotEquals(0, r.exit());
                assertVersionInstalled(home, "1.0.0");
                assertFalse(Files.exists(versionDir(home, "2.0.0")));
            }
        }

        @Test
        void rejectsUnknownParameter(@TempDir Path temp) throws Exception {
            try (WebsiteInstallerFixture fixture = WebsiteInstallerFixture.start(temp.resolve("fixture"))) {
                Path home = Files.createDirectory(temp.resolve("home"));
                WebsiteInstallerFixture.Result r = fixture.run(
                        List.of("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", SCRIPT.toString(),
                                "-Bogus", "value"),
                        fixture.environment(home));

                assertNotEquals(0, r.exit());
            }
        }

        @Test
        void rejectsMissingVersionValue(@TempDir Path temp) throws Exception {
            try (WebsiteInstallerFixture fixture = WebsiteInstallerFixture.start(temp.resolve("fixture"))) {
                Path home = Files.createDirectory(temp.resolve("home"));
                WebsiteInstallerFixture.Result r = fixture.run(
                        List.of("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", SCRIPT.toString(),
                                "-Version"),
                        fixture.environment(home));

                assertNotEquals(0, r.exit());
            }
        }

        @Test
        void rejectsMalformedVersionArgument(@TempDir Path temp) throws Exception {
            try (WebsiteInstallerFixture fixture = WebsiteInstallerFixture.start(temp.resolve("fixture"))) {
                Path home = Files.createDirectory(temp.resolve("home"));
                WebsiteInstallerFixture.Result r = install(fixture, home, "1.2.3-SNAPSHOT");

                assertNotEquals(0, r.exit());
                assertFalse(Files.exists(expectedBinDir(home).resolve("camel.cmd")));
            }
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("org.apache.camel.dsl.jbang.launcher.WebsiteInstallTest#invalidManifests")
        void rejectsInvalidManifest(String name, String manifestBody, @TempDir Path temp) throws Exception {
            try (WebsiteInstallerFixture fixture = WebsiteInstallerFixture.start(temp.resolve("fixture"))) {
                Path home = Files.createDirectory(temp.resolve("home"));
                fixture.publish("/camel-cli/releases/latest.properties",
                        manifestBody.getBytes(StandardCharsets.UTF_8));

                WebsiteInstallerFixture.Result r = install(fixture, home, null);

                assertNotEquals(0, r.exit(), name);
                assertFalse(Files.exists(expectedBinDir(home).resolve("camel.cmd")), name);
            }
        }

        @Test
        void manifestInjectionNeverExecutesCode(@TempDir Path temp) throws Exception {
            try (WebsiteInstallerFixture fixture = WebsiteInstallerFixture.start(temp.resolve("fixture"))) {
                Path home = Files.createDirectory(temp.resolve("home"));
                String hash = "a".repeat(64);
                String manifest = "format=1\nversion=$(New-Item owned)\ntar_sha256=" + hash + "\nzip_sha256=" + hash
                                  + "\n";
                fixture.publish("/camel-cli/releases/latest.properties",
                        manifest.getBytes(StandardCharsets.UTF_8));

                WebsiteInstallerFixture.Result r = install(fixture, home, null);

                assertNotEquals(0, r.exit());
                assertFalse(Files.exists(home.resolve("owned")), "manifest value must never be executed");
            }
        }

        @Test
        void rejectsChecksumMismatch(@TempDir Path temp) throws Exception {
            try (WebsiteInstallerFixture fixture = WebsiteInstallerFixture.start(temp.resolve("fixture"))) {
                Path home = Files.createDirectory(temp.resolve("home"));
                Path tar = fixture.safeTar("1.0.0");
                Path zip = fixture.safeZip("1.0.0");
                publishRelease(fixture, "1.0.0", tar, zip);
                String bogusHash = "0".repeat(64);
                fixture.publish("/camel-cli/releases/latest.properties",
                        ("format=1\nversion=1.0.0\ntar_sha256=" + bogusHash + "\nzip_sha256=" + bogusHash + "\n")
                                .getBytes(StandardCharsets.UTF_8));

                WebsiteInstallerFixture.Result r = install(fixture, home, null);

                assertNotEquals(0, r.exit());
                assertFalse(Files.exists(expectedBinDir(home).resolve("camel.cmd")));
            }
        }

        @Test
        void rejectsInterruptedDownload(@TempDir Path temp) throws Exception {
            try (WebsiteInstallerFixture fixture = WebsiteInstallerFixture.start(temp.resolve("fixture"));
                 ServerSocket rawServer = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
                Path home = Files.createDirectory(temp.resolve("home"));
                Path tar = fixture.safeTar("1.0.0");
                Path zip = fixture.safeZip("1.0.0");
                fixture.publishManifest("/camel-cli/releases/latest.properties", "1.0.0", tar, zip);

                byte[] zipBytes = Files.readAllBytes(zip);
                Thread server = new Thread(() -> serveTruncatedThenClose(rawServer, zipBytes));
                server.setDaemon(true);
                server.start();

                Map<String, String> env = new HashMap<>(fixture.environment(home));
                env.put("CAMEL_INSTALL_MAVEN_BASE_URL", "http://127.0.0.1:" + rawServer.getLocalPort());

                WebsiteInstallerFixture.Result r = fixture.run(
                        List.of("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", SCRIPT.toString()),
                        env);

                assertNotEquals(0, r.exit());
                assertFalse(Files.exists(expectedBinDir(home).resolve("camel.cmd")));
            }
        }

        @Test
        void rejectsAbsolutePathEntry(@TempDir Path temp) throws Exception {
            assertMaliciousZipRejected(temp, fixture -> fixture.maliciousZip("/etc/passwd"));
        }

        @Test
        void rejectsTraversalEntry(@TempDir Path temp) throws Exception {
            assertMaliciousZipRejected(temp, fixture -> fixture.maliciousZip("../escape"));
        }

        @Test
        void rejectsMultipleTopLevelRoots(@TempDir Path temp) throws Exception {
            assertMaliciousZipRejected(temp, fixture -> fixture.maliciousZip("evil-root/payload"));
        }

        @Test
        void rejectsEscapingReparsePointEntry(@TempDir Path temp) throws Exception {
            assertMaliciousZipRejected(temp,
                    fixture -> fixture.maliciousZipSymlink("camel-launcher-9.9.9/escape-link", "../../outside"));
        }

        @Test
        void rejectsArchiveMissingCamelBat(@TempDir Path temp) throws Exception {
            try (WebsiteInstallerFixture fixture = WebsiteInstallerFixture.start(temp.resolve("fixture"))) {
                Path home = Files.createDirectory(temp.resolve("home"));
                Path zip = fixture.safeZipMissingCamelBat("9.9.9");
                Path tar = fixture.safeTar("9.9.9");
                publishRelease(fixture, "9.9.9", tar, zip);
                fixture.publishManifest("/camel-cli/releases/9.9.9.properties", "9.9.9", tar, zip);

                WebsiteInstallerFixture.Result r = install(fixture, home, "9.9.9");

                assertNotEquals(0, r.exit());
                assertFalse(Files.exists(expectedBinDir(home).resolve("camel.cmd")));
            }
        }

        @Test
        void rejectsWhenStagedLauncherFails(@TempDir Path temp) throws Exception {
            try (WebsiteInstallerFixture fixture = WebsiteInstallerFixture.start(temp.resolve("fixture"))) {
                Path home = Files.createDirectory(temp.resolve("home"));
                Path zip = fixture.safeZipWithFailingLauncher("9.9.9");
                Path tar = fixture.safeTar("9.9.9");
                publishRelease(fixture, "9.9.9", tar, zip);
                fixture.publishManifest("/camel-cli/releases/9.9.9.properties", "9.9.9", tar, zip);

                WebsiteInstallerFixture.Result r = install(fixture, home, "9.9.9");

                assertNotEquals(0, r.exit());
                assertFalse(Files.exists(expectedBinDir(home).resolve("camel.cmd")));
                assertFalse(Files.exists(versionDir(home, "9.9.9")));
            }
        }

        @Test
        void shimIsWrittenWithoutByteOrderMark(@TempDir Path temp) throws Exception {
            try (WebsiteInstallerFixture fixture = WebsiteInstallerFixture.start(temp.resolve("fixture"))) {
                Path home = Files.createDirectory(temp.resolve("home"));
                publishLatest(fixture, "1.0.0");
                assertEquals(0, install(fixture, home, null).exit());

                byte[] shim = Files.readAllBytes(expectedBinDir(home).resolve("camel.cmd"));
                // A UTF-8 BOM (EF BB BF) prefix makes cmd.exe mis-parse the '@echo off' line and emit a stray
                // error on every 'camel' invocation, so the generated shim must be written BOM-free.
                boolean hasBom = shim.length >= 3 && (shim[0] & 0xFF) == 0xEF
                        && (shim[1] & 0xFF) == 0xBB && (shim[2] & 0xFF) == 0xBF;
                assertFalse(hasBom, "camel.cmd must not start with a UTF-8 BOM");
                assertTrue(new String(shim, StandardCharsets.UTF_8).startsWith("@echo off"),
                        "camel.cmd must start with '@echo off'");
            }
        }

        @Test
        void addUserPathPreservesExistingExpandableReferences(@TempDir Path temp) throws Exception {
            try (WebsiteInstallerFixture fixture = WebsiteInstallerFixture.start(temp.resolve("fixture"))) {
                Path home = Files.createDirectory(temp.resolve("home"));
                publishLatest(fixture, "1.0.0");
                String binDir = expectedBinDir(home).toString();

                // Seed the user's PATH with a REG_EXPAND_SZ entry referencing an environment variable, and
                // snapshot the raw value so it can be restored verbatim afterwards.
                String sentinel = "%SystemRoot%\\camel-userpath-test";
                String originalRaw = readUserPathRaw();
                writeUserPathExpandable(originalRaw.isEmpty() ? sentinel : originalRaw + ";" + sentinel);
                try {
                    assertEquals(0, install(fixture, home, null).exit());

                    String afterRaw = readUserPathRaw();
                    // The installer must append its bin dir without expanding pre-existing %VAR% references.
                    assertTrue(Arrays.asList(afterRaw.split(";")).contains(sentinel),
                            "existing %VAR% reference must be preserved unexpanded, was: " + afterRaw);
                    assertTrue(Arrays.stream(afterRaw.split(";")).anyMatch(e -> e.equalsIgnoreCase(binDir)),
                            "installer bin dir must be appended to the user PATH");
                } finally {
                    if (originalRaw.isEmpty()) {
                        deleteUserPath();
                    } else {
                        writeUserPathExpandable(originalRaw);
                    }
                }
            }
        }
    }
}
