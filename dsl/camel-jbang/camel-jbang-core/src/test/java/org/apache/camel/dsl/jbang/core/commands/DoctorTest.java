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
package org.apache.camel.dsl.jbang.core.commands;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.dsl.jbang.core.common.CamelJBangConstants;
import org.apache.camel.dsl.jbang.core.common.InstallDetector;
import org.apache.camel.tooling.maven.MavenDownloaderImpl;
import org.apache.camel.tooling.maven.MavenResolutionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.SetSystemProperty;
import org.mockito.MockedConstruction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;

class DoctorTest extends CamelCommandBaseTestSupport {

    private MockedConstruction<MavenDownloaderImpl> downloaders;
    private MavenResolutionException resolutionFailure;
    private boolean initializationFailure;

    @BeforeEach
    void mockDownloader() {
        downloaders = mockConstruction(MavenDownloaderImpl.class, (downloader, context) -> {
            if (initializationFailure) {
                doThrow(new IllegalStateException("Invalid Maven settings")).when(downloader).build();
            }
            if (resolutionFailure != null) {
                doThrow(resolutionFailure).when(downloader).resolveArtifacts(anyList(), anySet(), anyBoolean(), anyBoolean());
            }
        });
    }

    @AfterEach
    void closeDownloaderMocks() {
        downloaders.close();
    }

    @Test
    void mavenResolutionFailureFailsCommand() throws Exception {
        resolutionFailure = new MavenResolutionException("Product artifact unavailable");
        Doctor command = new Doctor(new CamelJBangMain().withPrinter(printer));

        assertThat(command.doCall()).isEqualTo(1);
        assertThat(printer.getOutput()).contains("Artifact resolution failed", "Product artifact unavailable", "Disk Space:");
    }

    @Test
    void mavenInitializationFailureFailsCommand() throws Exception {
        initializationFailure = true;
        Doctor command = new Doctor(new CamelJBangMain().withPrinter(printer));

        assertThat(command.doCall()).isEqualTo(1);
        assertThat(printer.getOutput()).contains("Invalid Maven settings", "Disk Space:");
    }

    @Test
    @SetSystemProperty(key = CamelJBangConstants.REPOS, value = "product=file:/product-repository")
    @SetSystemProperty(key = CamelJBangConstants.MAVEN_SETTINGS, value = "custom-settings.xml")
    @SetSystemProperty(key = CamelJBangConstants.MAVEN_SETTINGS_SECURITY, value = "custom-settings-security.xml")
    @SetSystemProperty(key = CamelJBangConstants.MAVEN_CENTRAL_ENABLED, value = "false")
    @SetSystemProperty(key = CamelJBangConstants.MAVEN_APACHE_SNAPSHOTS, value = "false")
    @SetSystemProperty(key = CamelJBangConstants.DOWNLOAD, value = "false")
    @SetSystemProperty(key = CamelJBangConstants.FRESH, value = "true")
    void usesConfiguredMavenEnvironment() throws Exception {
        Doctor command = new Doctor(new CamelJBangMain().withPrinter(printer));

        assertThat(command.doCall()).isZero();
        assertThat(downloaders.constructed()).hasSize(1);
        MavenDownloaderImpl downloader = downloaders.constructed().get(0);
        verify(downloader).setRepos("product=file:/product-repository");
        verify(downloader).setMavenSettingsLocation("custom-settings.xml");
        verify(downloader).setMavenSettingsSecurityLocation("custom-settings-security.xml");
        verify(downloader).setMavenCentralEnabled(false);
        verify(downloader).setMavenApacheSnapshotEnabled(false);
        verify(downloader).setOffline(true);
        verify(downloader).setFresh(true);
        verify(downloader).resolveArtifacts(
                List.of("org.apache.camel:camel-api:" + new DefaultCamelCatalog().getCatalogVersion()),
                Set.of(), false, false);
    }

    @Test
    public void shouldReturnZeroExitCode() throws Exception {
        Doctor command = new Doctor(new CamelJBangMain().withPrinter(printer));
        int exit = command.doCall();

        Assertions.assertEquals(0, exit);
    }

    @Test
    public void shouldPrintBanner() throws Exception {
        Doctor command = new Doctor(new CamelJBangMain().withPrinter(printer));
        command.doCall();

        String output = printer.getOutput();
        Assertions.assertTrue(output.contains("Camel CLI Doctor"));
        Assertions.assertTrue(output.contains("=================="));
    }

    @Test
    public void shouldCheckJava() throws Exception {
        Doctor command = new Doctor(new CamelJBangMain().withPrinter(printer));
        command.doCall();

        String output = printer.getOutput();
        Assertions.assertTrue(output.contains("Java:"));
        String javaVersion = System.getProperty("java.version");
        Assertions.assertTrue(output.contains(javaVersion));
    }

    @Test
    public void shouldCheckCamelVersion() throws Exception {
        Doctor command = new Doctor(new CamelJBangMain().withPrinter(printer));
        command.doCall();

        String output = printer.getOutput();
        Assertions.assertTrue(output.contains("Camel:"));
    }

    @Test
    public void shouldCheckPorts() throws Exception {
        Doctor command = new Doctor(new CamelJBangMain().withPrinter(printer));
        command.doCall();

        String output = printer.getOutput();
        Assertions.assertTrue(output.contains("Ports:"));
    }

    @Test
    public void shouldCheckDiskSpace() throws Exception {
        Doctor command = new Doctor(new CamelJBangMain().withPrinter(printer));
        command.doCall();

        String output = printer.getOutput();
        Assertions.assertTrue(output.contains("Disk Space:"));
    }

    @Test
    public void shouldIncludeAllSections() throws Exception {
        Doctor command = new Doctor(new CamelJBangMain().withPrinter(printer));
        command.doCall();

        List<String> lines = printer.getLines();
        Assertions.assertTrue(lines.size() >= 7, "Doctor should output at least 7 lines (banner + checks)");
    }

    @Test
    void skipsInstallCheckOutsideLauncher() throws Exception {
        System.clearProperty("camel.launcher");
        Doctor command = new Doctor(new CamelJBangMain().withPrinter(printer));

        int exit = command.doCall();

        assertThat(exit).isZero();
        assertThat(printer.getOutput()).doesNotContain("Camel CLI installations");
    }

    @Test
    void reportsSingleInstallAsOkUnderLauncher() throws Exception {
        System.setProperty("camel.launcher", "true");
        try {
            Doctor command = new Doctor(new CamelJBangMain().withPrinter(printer));

            int exit = command.doCall();

            assertThat(printer.getOutput()).contains("Installs:");
        } finally {
            System.clearProperty("camel.launcher");
        }
    }

    @Test
    void exitsNonZeroWhenMultipleInstallsFound() {
        Path locationA = Path.of("/home/user/.local/share/camel-cli/versions/4.22.0");
        Path locationB = Path.of("/opt/homebrew/Cellar/apache-camel/4.21.0/libexec");
        List<InstallDetector.InstallInfo> installs = List.of(
                new InstallDetector.InstallInfo(locationA, InstallDetector.InstallMethod.WEB_INSTALLER),
                new InstallDetector.InstallInfo(locationB, InstallDetector.InstallMethod.HOMEBREW));
        Doctor command = new Doctor(new CamelJBangMain().withPrinter(printer));

        boolean conflict = command.checkInstallLocations(installs, Optional.of(locationA.resolve("bin/camel.sh")));

        assertThat(conflict).isTrue();
        assertThat(printer.getOutput()).contains("Found 2 Camel CLI installations")
                .contains("Warning: more than one Camel CLI installation was found");
    }

    @Test
    void reportsNoConflictWhenSingleInstallFound() {
        Path location = Path.of("/home/user/.local/share/camel-cli/versions/4.22.0");
        List<InstallDetector.InstallInfo> installs = List.of(
                new InstallDetector.InstallInfo(location, InstallDetector.InstallMethod.WEB_INSTALLER));
        Doctor command = new Doctor(new CamelJBangMain().withPrinter(printer));

        boolean conflict = command.checkInstallLocations(installs, Optional.of(location.resolve("bin/camel.sh")));

        assertThat(conflict).isFalse();
        assertThat(printer.getOutput()).contains("Found 1 Camel CLI installation")
                .doesNotContain("Warning: more than one");
    }
}
