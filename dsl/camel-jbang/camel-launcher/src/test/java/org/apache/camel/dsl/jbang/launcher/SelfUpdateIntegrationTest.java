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

import java.io.InputStream;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.HexFormat;
import java.util.Map;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.StringPrinter;
import org.apache.camel.dsl.jbang.launcher.selfupdate.InstallScriptFetcher;
import org.apache.camel.dsl.jbang.launcher.selfupdate.ManifestFetcher;
import org.apache.camel.dsl.jbang.launcher.selfupdate.SelfUpdateCommand;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;

class SelfUpdateIntegrationTest {

    private static final Path REAL_INSTALL_SH = Paths.get("src/install/install.sh").toAbsolutePath();

    // SelfUpdateCommand compares the published manifest version against the REAL running Camel catalog version
    // (org.apache.camel.catalog.DefaultCamelCatalog#getCatalogVersion(), e.g. "4.22.0-SNAPSHOT" in this repo) - not
    // against the arbitrary labels markRunningAsWebInstaller() uses for its fake install directory, which only
    // matter for InstallDetector's classification. NEWER_VERSION/OLDER_VERSION must therefore stay far outside
    // Camel's own version range so "is an update available" comes out right regardless of what version this repo
    // is currently built at.
    private static final String NEWER_VERSION = "99.0.0";
    private static final String OLDER_VERSION = "0.0.1";

    private StringPrinter printer;
    private String savedUserHome;

    @BeforeEach
    void setup() {
        printer = new StringPrinter();
        savedUserHome = System.getProperty("user.home");
    }

    @AfterEach
    void cleanup() {
        System.clearProperty("camel.launcher.jar");
        if (savedUserHome != null) {
            System.setProperty("user.home", savedUserHome);
        } else {
            System.clearProperty("user.home");
        }
    }

    private String sha256Hex(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(Files.readAllBytes(file)));
    }

    // Publishes the REAL install.sh from this repo (and an install.sha256 matching it) at the fixture's
    // site root, so InstallScriptFetcher's download+verify step and the eventual `/bin/sh <script>` delegation
    // both exercise the actual production script, not a stand-in.
    private void publishRealInstallSh(WebsiteInstallerFixture fixture) throws Exception {
        byte[] script = Files.readAllBytes(REAL_INSTALL_SH);
        fixture.publish("/install.sh", script);
        String checksums = "install_sh_sha256=" + sha256Hex(REAL_INSTALL_SH) + "\n"
                           + "install_ps1_sha256=" + "0".repeat(64) + "\n";
        fixture.publish("/install.sha256", checksums.getBytes(StandardCharsets.UTF_8));
    }

    private void markRunningAsWebInstaller(Path home, Path xdgDataHome, String runningVersion) throws Exception {
        Path versionDir = xdgDataHome.resolve("camel-cli/versions/" + runningVersion);
        Files.createDirectories(versionDir.resolve("bin"));
        System.setProperty("camel.launcher.jar", versionDir.resolve("camel-launcher.jar").toString());
        System.setProperty("user.home", home.toString());
    }

    // Builds a client whose SSLContext trusts the fixture's self-signed certificate authority - the fixture serves
    // over HTTPS with its own generated CA, which the JVM's default trust store has no way to know about. Mirrors
    // WebsiteInstallerFixtureTest's own trustingContext() helper.
    private static HttpClient trustingHttpClient(WebsiteInstallerFixture fixture) throws Exception {
        CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
        Certificate certificate;
        try (InputStream in = Files.newInputStream(fixture.caCertificate())) {
            certificate = certificateFactory.generateCertificate(in);
        }
        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(null, null);
        trustStore.setCertificateEntry("camel-self-update-test", certificate);
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, tmf.getTrustManagers(), new SecureRandom());
        return HttpClient.newBuilder().sslContext(context).build();
    }

    private int run(WebsiteInstallerFixture fixture, Path home, Path xdgDataHome, String... args) throws Exception {
        HttpClient httpClient = trustingHttpClient(fixture);
        ManifestFetcher fetcher
                = new ManifestFetcher(fixture.baseUrl() + "/camel-cli/releases", fixture.mavenUrl(), httpClient);
        InstallScriptFetcher scriptFetcher = new InstallScriptFetcher(fixture.baseUrl(), httpClient);
        Map<String, String> installerEnv = fixture.environment(home);
        SelfUpdateCommand cmd
                = new SelfUpdateCommand(new CamelJBangMain().withPrinter(printer), fetcher, scriptFetcher, installerEnv);
        return new CommandLine(cmd).execute(args);
    }

    @Test
    void installsLatestVersionByDelegatingToRealInstallSh(@TempDir Path temp) throws Exception {
        try (WebsiteInstallerFixture fixture = WebsiteInstallerFixture.start(temp.resolve("fixture"))) {
            Path home = Files.createDirectories(temp.resolve("home"));
            Path xdgDataHome = home.resolve(".local/share");
            markRunningAsWebInstaller(home, xdgDataHome, "1.0.0");
            publishRealInstallSh(fixture);
            // SelfUpdateCommand always pins the resolved version when it invokes install.sh (see runInstaller),
            // so install.sh fetches the VERSION-SPECIFIC manifest (99.0.0.properties), not latest.properties -
            // publishVersion is required here in addition to publishLatest (which only the Java-side
            // ManifestFetcher.fetchLatest() consumes).
            WebsiteInstallTest.publishLatest(fixture, NEWER_VERSION);
            WebsiteInstallTest.publishVersion(fixture, NEWER_VERSION);

            int exit = run(fixture, home, xdgDataHome);

            assertThat(exit).isZero();
            assertThat(printer.getOutput()).contains("Installed Camel CLI " + NEWER_VERSION);
            assertThat(Files.isDirectory(xdgDataHome.resolve("camel-cli/versions/" + NEWER_VERSION))).isTrue();
            assertThat(Files.exists(home.resolve(".local/bin/camel"))).isTrue();
        }
    }

    @Test
    void alreadyOnLatestVersionSkipsDelegationEntirely(@TempDir Path temp) throws Exception {
        try (WebsiteInstallerFixture fixture = WebsiteInstallerFixture.start(temp.resolve("fixture"))) {
            Path home = Files.createDirectories(temp.resolve("home"));
            Path xdgDataHome = home.resolve(".local/share");
            markRunningAsWebInstaller(home, xdgDataHome, "1.0.0");
            // Deliberately do NOT publish install.sh/install.sha256: if the "already latest" fast path
            // regresses into always delegating, this test would fail on a 404 rather than silently passing.
            WebsiteInstallTest.publishLatest(fixture, OLDER_VERSION);

            int exit = run(fixture, home, xdgDataHome);

            assertThat(exit).isZero();
            assertThat(printer.getOutput()).contains("already on the latest version");
            try (var listing = Files.list(xdgDataHome.resolve("camel-cli/versions"))) {
                assertThat(listing.count()).isEqualTo(1);
            }
        }
    }

    @Test
    void checksOnlyWithoutDelegating(@TempDir Path temp) throws Exception {
        try (WebsiteInstallerFixture fixture = WebsiteInstallerFixture.start(temp.resolve("fixture"))) {
            Path home = Files.createDirectories(temp.resolve("home"));
            Path xdgDataHome = home.resolve(".local/share");
            markRunningAsWebInstaller(home, xdgDataHome, "1.0.0");
            // Same deliberate omission as above: --check must never reach InstallScriptFetcher.
            WebsiteInstallTest.publishLatest(fixture, NEWER_VERSION);

            int exit = run(fixture, home, xdgDataHome, "--check");

            assertThat(exit).isZero();
            assertThat(printer.getOutput()).contains("A new version is available");
            assertThat(Files.isDirectory(xdgDataHome.resolve("camel-cli/versions/" + NEWER_VERSION))).isFalse();
        }
    }

    @Test
    void refusesInstallScriptChecksumMismatch(@TempDir Path temp) throws Exception {
        try (WebsiteInstallerFixture fixture = WebsiteInstallerFixture.start(temp.resolve("fixture"))) {
            Path home = Files.createDirectories(temp.resolve("home"));
            Path xdgDataHome = home.resolve(".local/share");
            markRunningAsWebInstaller(home, xdgDataHome, "1.0.0");
            fixture.publish("/install.sh", Files.readAllBytes(REAL_INSTALL_SH));
            // install.sha256 doesn't match the published install.sh - InstallScriptFetcher must refuse before
            // ever invoking the script.
            String badChecksums = "install_sh_sha256=" + "a".repeat(64) + "\ninstall_ps1_sha256=" + "b".repeat(64) + "\n";
            fixture.publish("/install.sha256", badChecksums.getBytes(StandardCharsets.UTF_8));
            WebsiteInstallTest.publishLatest(fixture, NEWER_VERSION);

            int exit = run(fixture, home, xdgDataHome);

            assertThat(exit).isEqualTo(1);
            assertThat(Files.isDirectory(xdgDataHome.resolve("camel-cli/versions/" + NEWER_VERSION))).isFalse();
        }
    }

    @Test
    void pinsToResolvedVersionEvenWithoutExplicitFlag(@TempDir Path temp) throws Exception {
        try (WebsiteInstallerFixture fixture = WebsiteInstallerFixture.start(temp.resolve("fixture"))) {
            Path home = Files.createDirectories(temp.resolve("home"));
            Path xdgDataHome = home.resolve(".local/share");
            markRunningAsWebInstaller(home, xdgDataHome, "1.0.0");
            publishRealInstallSh(fixture);
            WebsiteInstallTest.publishLatest(fixture, NEWER_VERSION);
            WebsiteInstallTest.publishVersion(fixture, NEWER_VERSION);

            int exit = run(fixture, home, xdgDataHome);

            assertThat(exit).isZero();
            // install.sh received "--version 99.0.0" explicitly (not a bare invocation that would re-resolve
            // "latest" on its own) - asserted indirectly: the installed directory matches the version this
            // command's own manifest compare resolved, not merely "whatever install.sh's own default fetch found"
            // (which would be true either way against this fixture, but the explicit --version pin is what
            // prevents a TOCTOU race against a real, changing latest.properties in production).
            assertThat(Files.isDirectory(xdgDataHome.resolve("camel-cli/versions/" + NEWER_VERSION))).isTrue();
        }
    }

    @Test
    void refusesWhenInstallIsPinned(@TempDir Path temp) throws Exception {
        try (WebsiteInstallerFixture fixture = WebsiteInstallerFixture.start(temp.resolve("fixture"))) {
            Path home = Files.createDirectories(temp.resolve("home"));
            Path xdgDataHome = home.resolve(".local/share");
            markRunningAsWebInstaller(home, xdgDataHome, "1.0.0");
            Path pinnedVersionFile = xdgDataHome.resolve("camel-cli/pinned-version");
            Files.writeString(pinnedVersionFile, "1.0.0");
            // Deliberately do NOT publish install.sh/install.sha256: a pinned install must refuse before ever
            // reaching InstallScriptFetcher.
            WebsiteInstallTest.publishLatest(fixture, NEWER_VERSION);

            int exit = run(fixture, home, xdgDataHome);

            assertThat(exit).isEqualTo(1);
            assertThat(printer.getOutput()).contains("pinned").contains("1.0.0");
            assertThat(Files.isDirectory(xdgDataHome.resolve("camel-cli/versions/" + NEWER_VERSION))).isFalse();
        }
    }

    // Guards the self-lockout bug this feature could otherwise introduce: without CAMEL_INSTALL_SELF_UPDATE
    // suppressing install.sh's pin-state write, a bare `camel self-update` (which always passes --version for
    // TOCTOU-safety, see SelfUpdateCommand.runInstaller) would pin itself on every successful run, and the very
    // next self-update would then hit the refusal above and never run again.
    @Test
    void successfulSelfUpdateDoesNotCreateAPin(@TempDir Path temp) throws Exception {
        try (WebsiteInstallerFixture fixture = WebsiteInstallerFixture.start(temp.resolve("fixture"))) {
            Path home = Files.createDirectories(temp.resolve("home"));
            Path xdgDataHome = home.resolve(".local/share");
            markRunningAsWebInstaller(home, xdgDataHome, "1.0.0");
            publishRealInstallSh(fixture);
            WebsiteInstallTest.publishLatest(fixture, NEWER_VERSION);
            WebsiteInstallTest.publishVersion(fixture, NEWER_VERSION);

            int exit = run(fixture, home, xdgDataHome);

            assertThat(exit).isZero();
            assertThat(Files.exists(xdgDataHome.resolve("camel-cli/pinned-version"))).isFalse();
        }
    }

    @Test
    void refusesNonWebInstallerInstall(@TempDir Path temp) throws Exception {
        try (WebsiteInstallerFixture fixture = WebsiteInstallerFixture.start(temp.resolve("fixture"))) {
            Path home = Files.createDirectories(temp.resolve("home"));
            Path xdgDataHome = home.resolve(".local/share");
            System.setProperty("camel.launcher.jar", "/opt/homebrew/Cellar/apache-camel/1.0.0/libexec/camel-launcher.jar");
            WebsiteInstallTest.publishLatest(fixture, "2.0.0");

            int exit = run(fixture, home, xdgDataHome);

            assertThat(exit).isEqualTo(1);
            assertThat(printer.getOutput()).contains("Homebrew");
        }
    }
}
