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

import java.io.File;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.dsl.jbang.core.common.CliExitCode;
import org.apache.camel.dsl.jbang.core.common.ExternalCommand;
import org.apache.camel.dsl.jbang.core.common.InstallDetector;
import org.apache.camel.dsl.jbang.core.common.JsonOutput;
import org.apache.camel.dsl.jbang.core.common.OllamaDoctorSupport;
import org.apache.camel.dsl.jbang.core.common.VersionHelper;
import org.apache.camel.tooling.maven.MavenDownloaderImpl;
import org.apache.camel.tooling.maven.MavenResolutionException;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.json.JsonObject;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "doctor", description = "Checks the environment and reports potential issues",
         sortOptions = false, showDefaultValues = true,
         footer = {
                 "%nExamples:",
                 "  camel doctor",
                 "  camel doctor --json" })
public class Doctor extends CamelCommand {

    /**
     * The check works.
     */
    static final String OK = "OK";
    /**
     * The check works, but something optional is missing or could be better.
     */
    static final String WARN = "WARN";
    /**
     * The check fails: the Camel CLI cannot work as expected.
     */
    static final String FAIL = "FAIL";

    @CommandLine.Option(names = { "--json" },
                        description = "Output in JSON Format: the status (OK, WARN or FAIL) of each check")
    boolean jsonOutput;

    /**
     * The outcome of a check.
     *
     * @param name    the check, such as java
     * @param status  OK, WARN or FAIL
     * @param message what the check found, as the text output shows it
     */
    record Check(String name, String status, String message) {
    }

    private final List<Check> checks = new ArrayList<>();

    public Doctor(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        checks.clear();
        if (!jsonOutput) {
            printer().println("Camel CLI Doctor");
            printer().println("==================");
            printer().println();
        }

        checkCamelVersion();
        checkJava();
        checkJBang();
        checkMavenRepository();
        checkContainerRuntime();
        checkOllama();
        checkCommonPorts();
        checkDiskSpace();

        if ("true".equals(System.getProperty("camel.launcher"))) {
            checkInstallLocations();
        }

        boolean failed = checks.stream().anyMatch(c -> FAIL.equals(c.status()));
        if (jsonOutput) {
            boolean warned = checks.stream().anyMatch(c -> WARN.equals(c.status()));
            JsonObject root = new JsonObject();
            root.put("status", failed ? FAIL : warned ? WARN : OK);
            root.put("checks", checks.stream().map(c -> {
                JsonObject jo = new JsonObject();
                jo.put("name", c.name());
                jo.put("status", c.status());
                jo.put("message", c.message());
                return jo;
            }).toList());
            JsonOutput.print(printer(), root);
        }
        // warnings do not fail the command
        return failed ? CliExitCode.FAILURE : CliExitCode.OK;
    }

    /**
     * The outcomes of the checks of the last run.
     */
    List<Check> getChecks() {
        return checks;
    }

    private void report(String name, String label, String status, String message) {
        checks.add(new Check(name, status, message));
        if (!jsonOutput) {
            printer().printf("  %-13s%s%n", label + ":", message);
        }
    }

    private void checkJava() {
        String version = System.getProperty("java.version");
        String vendor = System.getProperty("java.vendor", "");
        int major = Runtime.version().feature();
        String status;
        String result;
        if (major >= 25) {
            status = OK;
            result = "OK";
        } else if (major >= 21) {
            status = OK;
            result = "OK (consider upgrading to 25 for better performance)";
        } else {
            status = FAIL;
            result = "UNSUPPORTED (21+ required)";
        }
        report("java", "Java", status, "%s (%s) (%s)".formatted(version, vendor, result));
    }

    private void checkJBang() {
        String version = VersionHelper.getJBangVersion();
        if (version != null) {
            report("jbang", "JBang", OK, version + " (OK)");
        } else {
            // the Camel CLI launcher does not need JBang
            report("jbang", "JBang", OK, "Not detected");
        }
    }

    private void checkCamelVersion() {
        CamelCatalog catalog = new DefaultCamelCatalog();
        String version = catalog.getCatalogVersion();
        report("camel", "Camel", OK, version);
    }

    private void checkMavenRepository() {
        try (MavenDownloaderImpl downloader = new MavenDownloaderImpl()) {
            downloader.build();
            CamelCatalog catalog = new DefaultCamelCatalog();
            String version = catalog.getCatalogVersion();
            downloader.resolveArtifacts(
                    List.of("org.apache.camel:camel-api:" + version),
                    Set.of(), false, false);
            report("maven", "Maven", OK, "Artifact resolution (OK)");
        } catch (MavenResolutionException e) {
            // camel run downloads the dependencies of the routes
            report("maven", "Maven", FAIL, "Artifact resolution failed (%s)".formatted(e.getMessage()));
        } catch (Exception e) {
            report("maven", "Maven", FAIL, "Error (%s)".formatted(e.getMessage()));
        }
    }

    private void checkContainerRuntime() {
        // check docker first, then podman as fallback
        for (String cmd : new String[] { "docker", "podman" }) {
            // a docker info that hangs (such as a stopped Docker Desktop) does not hang camel doctor
            if (ExternalCommand.succeeds(10000, cmd, "info")) {
                report("container", "Container", OK, "%s running (OK, optional)".formatted(StringHelper.capitalize(cmd)));
                return;
            }
        }
        report("container", "Container", WARN, "Not found (optional — needed for running external infra services)");
    }

    private void checkOllama() {
        printOllamaStatus(OllamaDoctorSupport.detect());
    }

    void printOllamaStatus(OllamaDoctorSupport.Status status) {
        if (status.running()) {
            report("ollama", "Ollama", OK, OllamaDoctorSupport.cliRunningLine(status));
        } else {
            report("ollama", "Ollama", WARN, OllamaDoctorSupport.cliNotDetectedLine());
        }
    }

    private void checkCommonPorts() {
        StringBuilder conflicts = new StringBuilder();
        for (int port : new int[] { 8080, 8443, 9090 }) {
            if (isPortInUse(port)) {
                if (!conflicts.isEmpty()) {
                    conflicts.append(", ");
                }
                conflicts.append(port);
            }
        }
        if (!conflicts.isEmpty()) {
            report("ports", "Ports", WARN, "In use: " + conflicts);
        } else {
            report("ports", "Ports", OK, "8080, 8443, 9090 free (OK)");
        }
    }

    private static boolean isPortInUse(int port) {
        try (ServerSocket ss = new ServerSocket(port)) {
            ss.setReuseAddress(true);
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    private void checkDiskSpace() {
        File tmpDir = new File(System.getProperty("java.io.tmpdir"));
        long free = tmpDir.getFreeSpace();
        long mb = free / (1024 * 1024);
        long gb = mb / (1024);
        boolean ok = mb > 500;
        String unit = gb > 0 ? "GB" : "MB";
        report("disk", "Disk Space", ok ? OK : WARN,
                "%d %s free in temp dir (%s)".formatted(gb > 0 ? gb : mb, unit, ok ? "OK" : "LOW"));
    }

    // Only meaningful when running via the native launcher (camel.launcher=true) — the web-installer
    // version-directory layout InstallDetector understands doesn't exist for the plain JBang-based CLI.
    private boolean checkInstallLocations() {
        return checkInstallLocations(InstallDetector.scanKnownLocations(), InstallDetector.resolveActiveOnPath());
    }

    // Package-visible overload taking the scan result directly: lets DoctorTest exercise the
    // "more than one installation found -> conflict" contract against fully controlled data, rather than
    // whatever InstallDetector.scanKnownLocations() happens to find on the machine running the test suite.
    boolean checkInstallLocations(List<InstallDetector.InstallInfo> installs, Optional<Path> active) {
        String found = "Found %d Camel CLI installation%s".formatted(installs.size(), installs.size() == 1 ? "" : "s");
        boolean conflict = installs.size() > 1;
        checks.add(new Check("installs", conflict ? FAIL : OK, found));
        if (jsonOutput) {
            return conflict;
        }
        printer().println();
        printer().printf("Installs:    %s%n", found);
        for (InstallDetector.InstallInfo install : installs) {
            boolean isActive = active.isPresent() && install.location() != null
                    && active.get().toAbsolutePath().normalize().startsWith(install.location().toAbsolutePath().normalize());
            printer().printf("             %s (%s)%s%n", install.location(), install.method(), isActive ? " <- active" : "");
        }

        if (conflict) {
            printer().println(
                    "Warning: more than one Camel CLI installation was found. The one marked active is the one your "
                              + "shell currently runs; the others are unused but still present. See "
                              + "camel-jbang-launcher-install.adoc for how each installation method is normally removed.");
        }
        return conflict;
    }
}
