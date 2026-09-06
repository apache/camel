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
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.dsl.jbang.core.common.InstallDetector;
import org.apache.camel.dsl.jbang.core.common.VersionHelper;
import org.apache.camel.tooling.maven.MavenDownloaderImpl;
import org.apache.camel.tooling.maven.MavenResolutionException;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import picocli.CommandLine.Command;

@Command(name = "doctor", description = "Checks the environment and reports potential issues",
         sortOptions = false, showDefaultValues = true,
         footer = {
                 "%nExamples:",
                 "  camel doctor" })
public class Doctor extends CamelCommand {

    public Doctor(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        printer().println("Camel CLI Doctor");
        printer().println("==================");
        printer().println();

        checkCamelVersion();
        checkJava();
        checkJBang();
        checkMavenRepository();
        checkContainerRuntime();
        checkOllama();
        checkCommonPorts();
        checkDiskSpace();

        boolean conflict = false;
        if ("true".equals(System.getProperty("camel.launcher"))) {
            conflict = checkInstallLocations();
        }

        return conflict ? 1 : 0;
    }

    private void checkJava() {
        String version = System.getProperty("java.version");
        String vendor = System.getProperty("java.vendor", "");
        int major = Runtime.version().feature();
        String status;
        if (major >= 21) {
            status = "OK";
        } else if (major >= 17) {
            status = "OK (consider upgrading to 21 or 25 for better performance)";
        } else {
            status = "UNSUPPORTED (17+ required)";
        }
        printer().printf("  Java:        %s (%s) (%s)%n", version, vendor, status);
    }

    private void checkJBang() {
        String version = VersionHelper.getJBangVersion();
        if (version != null) {
            printer().printf("  JBang:       %s (OK)%n", version);
        } else {
            printer().printf("  JBang:       Not detected%n");
        }
    }

    private void checkCamelVersion() {
        CamelCatalog catalog = new DefaultCamelCatalog();
        String version = catalog.getCatalogVersion();
        printer().printf("  Camel:       %s%n", version);
    }

    private void checkMavenRepository() {
        try (MavenDownloaderImpl downloader = new MavenDownloaderImpl()) {
            downloader.build();
            CamelCatalog catalog = new DefaultCamelCatalog();
            String version = catalog.getCatalogVersion();
            downloader.resolveArtifacts(
                    List.of("org.apache.camel:camel-api:" + version),
                    Set.of(), false, false);
            printer().printf("  Maven:       Artifact resolution (OK)%n");
        } catch (MavenResolutionException e) {
            printer().printf("  Maven:       Artifact resolution failed (%s)%n", e.getMessage());
        } catch (Exception e) {
            printer().printf("  Maven:       Error (%s)%n", e.getMessage());
        }
    }

    private void checkContainerRuntime() {
        // check docker first, then podman as fallback
        for (String cmd : new String[] { "docker", "podman" }) {
            try {
                Process p = new ProcessBuilder(cmd, "info")
                        .redirectErrorStream(true)
                        .start();
                // drain output to prevent blocking
                p.getInputStream().transferTo(OutputStream.nullOutputStream());
                int exit = p.waitFor();
                if (exit == 0) {
                    printer().printf("  Container:   %s running (OK, optional)%n", StringHelper.capitalize(cmd));
                    return;
                }
            } catch (Exception e) {
                // not found, try next
            }
        }
        printer().printf("  Container:   Not found (optional — needed for running external infra services)%n");
    }

    private void checkOllama() {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(3))
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:11434/api/tags"))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                List<String> models = parseOllamaModels(response.body());
                if (models.isEmpty()) {
                    printer().printf("  Ollama:      Running at localhost:11434 — no models pulled yet%n");
                } else {
                    printer().printf("  Ollama:      Running at localhost:11434 — models: %s%n",
                            String.join(", ", models));
                }
            } else {
                printer().printf("  Ollama:      Not detected (optional — start for local AI with F8 in TUI)%n");
            }
        } catch (Exception e) {
            printer().printf("  Ollama:      Not detected (optional — start for local AI with F8 in TUI)%n");
        }
    }

    private List<String> parseOllamaModels(String json) {
        List<String> names = new ArrayList<>();
        try {
            JsonObject root = (JsonObject) Jsoner.deserialize(json);
            JsonArray models = (JsonArray) root.get("models");
            if (models != null) {
                for (Object entry : models) {
                    if (entry instanceof JsonObject model) {
                        Object name = model.get("name");
                        if (name != null) {
                            names.add(name.toString());
                        }
                    }
                }
            }
        } catch (Exception e) {
            // ignore parse errors — not critical
        }
        return names;
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
            printer().printf("  Ports:       In use: %s%n", conflicts);
        } else {
            printer().printf("  Ports:       8080, 8443, 9090 free (OK)%n");
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
        String status = mb > 500 ? "OK" : "LOW";
        String unit = gb > 0 ? "GB" : "MB";
        printer().printf("  Disk Space:  %d %s free in temp dir (%s)%n", gb > 0 ? gb : mb, unit, status);
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
        printer().println();
        printer().printf("Installs:    Found %d Camel CLI installation%s%n", installs.size(), installs.size() == 1 ? "" : "s");
        for (InstallDetector.InstallInfo install : installs) {
            boolean isActive = active.isPresent() && install.location() != null
                    && active.get().toAbsolutePath().normalize().startsWith(install.location().toAbsolutePath().normalize());
            printer().printf("             %s (%s)%s%n", install.location(), install.method(), isActive ? " <- active" : "");
        }

        if (installs.size() > 1) {
            printer().println(
                    "Warning: more than one Camel CLI installation was found. The one marked active is the one your "
                              + "shell currently runs; the others are unused but still present. See "
                              + "camel-jbang-launcher-install.adoc for how each installation method is normally removed.");
            return true;
        }
        return false;
    }
}
