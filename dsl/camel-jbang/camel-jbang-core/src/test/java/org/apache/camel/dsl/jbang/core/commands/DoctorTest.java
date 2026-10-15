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

import org.apache.camel.dsl.jbang.core.common.CliExitCode;
import org.apache.camel.dsl.jbang.core.common.InstallDetector;
import org.apache.camel.dsl.jbang.core.common.OllamaDoctorSupport;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DoctorTest extends CamelCommandBaseTestSupport {

    @Test
    public void exitCodeFollowsTheChecks() throws Exception {
        Doctor command = new Doctor(new CamelJBangMain().withPrinter(printer));
        int exit = command.doCall();

        // a check that fails (such as Maven on a machine without network) fails the command, a warning does not
        boolean failed = command.getChecks().stream().anyMatch(c -> Doctor.FAIL.equals(c.status()));
        Assertions.assertEquals(failed ? CliExitCode.FAILURE : CliExitCode.OK, exit);
    }

    @Test
    void shouldPrintChecksAsJson() throws Exception {
        Doctor command = new Doctor(new CamelJBangMain().withPrinter(printer));
        command.jsonOutput = true;
        command.doCall();

        JsonObject root = Jsoner.deserialize(printer.getStdout(), (JsonObject) null);
        assertThat(root).as(printer.getStdout()).isNotNull();
        assertThat(root.getString("status")).isIn(Doctor.OK, Doctor.WARN, Doctor.FAIL);
        JsonArray checks = root.getCollection("checks");
        assertThat(checks.stream().map(c -> ((JsonObject) c).getString("name")))
                .contains("camel", "java", "jbang", "maven", "container", "ollama", "ports", "disk");
        assertThat(checks.stream().map(c -> ((JsonObject) c).getString("status")))
                .allMatch(st -> List.of(Doctor.OK, Doctor.WARN, Doctor.FAIL).contains(st));
        // only the JSON document: no banner
        assertThat(printer.getStdout()).doesNotContain("Camel CLI Doctor");
    }

    @Test
    void optionalToolsAreWarnings() throws Exception {
        Doctor command = new Doctor(new CamelJBangMain().withPrinter(printer));
        command.printOllamaStatus(OllamaDoctorSupport.Status.notRunning());

        assertThat(command.getChecks()).singleElement()
                .satisfies(c -> assertThat(c.name()).isEqualTo("ollama"))
                .satisfies(c -> assertThat(c.status()).isEqualTo(Doctor.WARN));
    }

    @Test
    void moreThanOneInstallFails() {
        Path locationA = Path.of("/home/user/.local/share/camel-cli/versions/4.22.0");
        Path locationB = Path.of("/opt/homebrew/Cellar/apache-camel/4.21.0/libexec");
        Doctor command = new Doctor(new CamelJBangMain().withPrinter(printer));

        command.checkInstallLocations(List.of(
                new InstallDetector.InstallInfo(locationA, InstallDetector.InstallMethod.WEB_INSTALLER),
                new InstallDetector.InstallInfo(locationB, InstallDetector.InstallMethod.HOMEBREW)), Optional.empty());

        assertThat(command.getChecks()).singleElement()
                .satisfies(c -> assertThat(c.status()).isEqualTo(Doctor.FAIL))
                .satisfies(c -> assertThat(c.message()).isEqualTo("Found 2 Camel CLI installations"));
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
    void shouldReportOllamaSection() throws Exception {
        Doctor command = new Doctor(new CamelJBangMain().withPrinter(printer));
        command.doCall();

        assertThat(printer.getOutput()).contains("Ollama:");
    }

    @Test
    void shouldReportOllamaNotDetectedWhenUnavailable() throws Exception {
        Doctor command = new Doctor(new CamelJBangMain().withPrinter(printer));
        command.printOllamaStatus(OllamaDoctorSupport.Status.notRunning());

        assertThat(printer.getOutput())
                .contains("Ollama:")
                .contains("Not detected");
    }

    @Test
    void shouldPrintRunningOllamaWithModels() throws Exception {
        Doctor command = new Doctor(new CamelJBangMain().withPrinter(printer));
        OllamaDoctorSupport.Status status = new OllamaDoctorSupport.Status(
                true, "http://localhost:11434", List.of("qwen2.5:32b", "llama3.2:latest"));

        command.printOllamaStatus(status);

        assertThat(printer.getOutput())
                .contains("Ollama:")
                .contains("Running at localhost:11434")
                .contains("qwen2.5:32b, llama3.2:latest");
    }

    @Test
    void skipsInstallCheckOutsideLauncher() throws Exception {
        System.clearProperty("camel.launcher");
        Doctor command = new Doctor(new CamelJBangMain().withPrinter(printer));

        command.doCall();

        assertThat(command.getChecks()).extracting(Doctor.Check::name).doesNotContain("installs");
        assertThat(printer.getOutput()).doesNotContain("Camel CLI installations");
    }

    @Test
    void reportsSingleInstallAsOkUnderLauncher() throws Exception {
        System.setProperty("camel.launcher", "true");
        try {
            Doctor command = new Doctor(new CamelJBangMain().withPrinter(printer));

            command.doCall();

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
