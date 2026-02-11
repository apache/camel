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
package org.apache.camel.test.infra.cli.services;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.apache.camel.test.infra.cli.common.CliProperties;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Assertions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link CliService} implementation that runs JBang/Camel CLI directly on the host via {@link ProcessBuilder},
 * eliminating the Docker dependency. Requires JBang to be pre-installed on the host.
 */
public class CliLocalProcessService implements CliService {

    private static final Logger LOG = LoggerFactory.getLogger(CliLocalProcessService.class);
    private static final int COMMAND_TIMEOUT_SECONDS = 120;
    private static final boolean IS_WINDOWS = System.getProperty("os.name", "").toLowerCase().startsWith("win");

    private final String repo;
    private final String branch;
    private final String jbangVersion;
    private final String forceToRunVersion;
    private final String mavenRepos;
    private final String localMavenRepo;
    private final boolean skipInstall;
    private final Path workDir;
    private final boolean tempWorkDir;
    private Path mavenSettingsFile;
    private final List<String> backgroundPids = new ArrayList<>();
    private String version;

    public CliLocalProcessService() {
        this.repo = System.getProperty(CliProperties.REPO, "apache/camel");
        this.branch = System.getProperty(CliProperties.BRANCH, "main");
        this.jbangVersion = System.getProperty(CliProperties.VERSION, "default");
        this.forceToRunVersion = System.getProperty(CliProperties.FORCE_RUN_VERSION, "");
        this.mavenRepos = System.getProperty(CliProperties.MVN_REPOS);
        this.localMavenRepo = System.getProperty(CliProperties.MVN_LOCAL_REPO);
        this.skipInstall = Boolean.parseBoolean(System.getProperty(CliProperties.SKIP_INSTALL, "false"));

        String dataFolder = System.getProperty(CliProperties.DATA_FOLDER);
        if (ObjectHelper.isNotEmpty(dataFolder)) {
            this.workDir = Path.of(dataFolder);
            this.tempWorkDir = false;
        } else {
            try {
                this.workDir = Files.createTempDirectory("camel-cli-process-");
            } catch (IOException e) {
                throw new RuntimeException("Failed to create temp work directory", e);
            }
            this.tempWorkDir = true;
        }
    }

    @Override
    public void registerProperties() {
        // no-op
    }

    @Override
    public void initialize() {
        LOG.info("Initializing local process CLI service");

        if (ObjectHelper.isNotEmpty(localMavenRepo)) {
            createMavenSettingsFile();
        }

        if (skipInstall) {
            LOG.info("Skipping JBang installation, using existing camel command");
        } else {
            backupUserFiles();

            if (!isJBangInstalled()) {
                installJBang();
            }

            executeGenericCommand(String.format("jbang trust add https://github.com/%s/", repo));

            String installCmd;
            if ("default".equals(jbangVersion)) {
                installCmd = String.format("jbang app install --force --fresh camel@%s/%s", repo, branch);
            } else {
                installCmd = String.format(
                        "jbang app install --force --fresh -Dcamel.jbang.version=%s camel@%s/%s", jbangVersion, repo, branch);
            }
            executeGenericCommand(installCmd);
        }

        if (ObjectHelper.isNotEmpty(forceToRunVersion)) {
            LOG.info("force to use version {}", forceToRunVersion);
            execute("version set " + forceToRunVersion);
        }
        if (ObjectHelper.isNotEmpty(mavenRepos)) {
            LOG.info("set repositories {}", mavenRepos);
            execute(String.format("config set repos=%s", mavenRepos));
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("Camel JBang version {}", version());
        }
    }

    @Override
    public void shutdown() {
        LOG.info("Shutting down local process CLI service");
        if (isCamelCommandAvailable()) {
            stopBackgroundProcesses();
            if (!skipInstall) {
                try {
                    executeGenericCommand("jbang app uninstall camel");
                } catch (Exception e) {
                    LOG.warn("Failed to uninstall camel jbang app: {}", e.getMessage());
                }
            }
        }
        if (mavenSettingsFile != null) {
            FileUtils.deleteQuietly(mavenSettingsFile.toFile());
        }
        if (tempWorkDir) {
            FileUtils.deleteQuietly(workDir.toFile());
        } else {
            FileUtils.deleteQuietly(Path.of(workDir.toFile().getAbsolutePath(), ".camel-jbang").toFile());
        }
        FileUtils.deleteQuietly(new File(".camel-jbang"));
    }

    @Override
    public String execute(String command) {
        String camelCommand = getMainCommand() + " " + command;
        if (mavenSettingsFile != null && command.startsWith("run ")) {
            camelCommand += " --maven-settings=" + mavenSettingsFile.toAbsolutePath();
        }
        return executeGenericCommand(camelCommand);
    }

    @Override
    public String executeBackground(String command) {
        final String pid = StringHelper.after(execute(command.concat(" --background")), "PID:").trim();
        String cleanPid = org.apache.camel.support.ObjectHelper.isNumber(pid) ? pid : StringHelper.before(pid, " ");
        if (cleanPid != null) {
            backgroundPids.add(cleanPid);
        }
        return cleanPid;
    }

    @Override
    public String executeGenericCommand(String command) {
        try {
            LOG.debug("Executing command: {}", command);

            ProcessBuilder pb;
            if (IS_WINDOWS) {
                pb = new ProcessBuilder("cmd", "/c", command);
            } else {
                pb = new ProcessBuilder("/bin/bash", "-c", command);
            }

            pb.directory(workDir.toFile());

            // Prepend ~/.jbang/bin to PATH
            String jbangBin = Path.of(System.getProperty("user.home"), ".jbang", "bin").toString();
            String currentPath = pb.environment().getOrDefault("PATH", "");
            pb.environment().put("PATH", jbangBin + (IS_WINDOWS ? ";" : ":") + currentPath);

            if (ObjectHelper.isNotEmpty(localMavenRepo)) {
                pb.environment().put("JBANG_REPO", localMavenRepo);
            }

            Process process = pb.start();

            // Read stderr concurrently to avoid deadlocks
            CompletableFuture<String> stderrFuture = CompletableFuture.supplyAsync(() -> {
                try (BufferedReader reader
                        = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line).append(System.lineSeparator());
                    }
                    return sb.toString();
                } catch (IOException e) {
                    return e.getMessage();
                }
            });

            String stdout;
            try (BufferedReader reader
                    = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append(System.lineSeparator());
                }
                stdout = sb.toString();
            }

            boolean finished = process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                Assertions.fail(String.format("command '%s' timed out after %d seconds", command, COMMAND_TIMEOUT_SECONDS));
            }

            String stderr = stderrFuture.join();

            if (process.exitValue() != 0) {
                Assertions.fail(String.format("command %s failed with output %s and error %s", command, stdout, stderr));
            }

            if (LOG.isDebugEnabled()) {
                if (ObjectHelper.isNotEmpty(stdout)) {
                    LOG.debug("result out {}", stdout);
                }
                if (ObjectHelper.isNotEmpty(stderr)) {
                    LOG.debug("result error {}", stderr);
                }
            }

            return stdout;
        } catch (IOException | InterruptedException e) {
            LOG.error("ERROR running command: {}", command, e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public void copyFileInternally(String source, String destination) {
        try {
            Files.copy(Path.of(source), Path.of(destination), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            Assertions.fail(String.format("unable to copy file %s to %s", source, destination), e);
        }
    }

    @Override
    public String getMountPoint() {
        return workDir.toAbsolutePath().toString();
    }

    @Override
    public String getContainerLogs() {
        LOG.warn("getContainerLogs() is not supported for local process service");
        return "";
    }

    @Override
    public int getDevConsolePort() {
        return 8080;
    }

    @Override
    public Stream<String> listDirectory(String directoryPath) {
        try {
            return Files.list(Path.of(directoryPath))
                    .map(p -> p.getFileName().toString());
        } catch (IOException e) {
            Assertions.fail("unable to list " + directoryPath, e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public String id() {
        return "local-" + ProcessHandle.current().pid();
    }

    @Override
    public String version() {
        return Optional.ofNullable(version)
                .orElseGet(() -> {
                    final String versionSummary = execute("version");
                    if (versionSummary.contains("User configuration") && versionSummary.contains("camel-version = ")) {
                        version = StringHelper.between(versionSummary, "camel-version = ", "\n").trim();
                    }
                    if (version == null) {
                        version = StringHelper.between(versionSummary, "Camel JBang version:", "\n").trim();
                    }
                    return version;
                });
    }

    private boolean isCamelCommandAvailable() {
        try {
            String checkCmd = IS_WINDOWS
                    ? "where " + getMainCommand()
                    : "which " + getMainCommand();
            ProcessBuilder pb = IS_WINDOWS
                    ? new ProcessBuilder("cmd", "/c", checkCmd)
                    : new ProcessBuilder("/bin/bash", "-c", checkCmd);
            String jbangBin = Path.of(System.getProperty("user.home"), ".jbang", "bin").toString();
            String currentPath = pb.environment().getOrDefault("PATH", "");
            pb.environment().put("PATH", jbangBin + (IS_WINDOWS ? ";" : ":") + currentPath);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return false;
            }
            boolean available = process.exitValue() == 0;
            LOG.info("Camel command '{}' available: {}", getMainCommand(), available);
            return available;
        } catch (IOException | InterruptedException e) {
            LOG.info("Camel command '{}' not found: {}", getMainCommand(), e.getMessage());
            return false;
        }
    }

    private boolean isJBangInstalled() {
        try {
            String jbangBin = Path.of(System.getProperty("user.home"), ".jbang", "bin").toString();
            String pathSeparator = IS_WINDOWS ? ";" : ":";
            ProcessBuilder pb = IS_WINDOWS
                    ? new ProcessBuilder("cmd", "/c", "jbang version")
                    : new ProcessBuilder("/bin/bash", "-c", "jbang version");
            String currentPath = pb.environment().getOrDefault("PATH", "");
            pb.environment().put("PATH", jbangBin + pathSeparator + currentPath);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return false;
            }
            boolean installed = process.exitValue() == 0;
            LOG.info("JBang installed: {}", installed);
            return installed;
        } catch (IOException | InterruptedException e) {
            LOG.info("JBang not found: {}", e.getMessage());
            return false;
        }
    }

    private void installJBang() {
        LOG.info("Installing JBang");
        try {
            ProcessBuilder pb;
            if (IS_WINDOWS) {
                String script = "iex \"& { $(iwr -useb https://ps.jbang.dev) } app setup\"";
                String encoded = java.util.Base64.getEncoder().encodeToString(
                        script.getBytes(StandardCharsets.UTF_16LE));
                pb = new ProcessBuilder("powershell", "-NoProfile", "-EncodedCommand", encoded);
            } else {
                pb = new ProcessBuilder(
                        "/bin/bash", "-c",
                        "curl -Ls https://sh.jbang.dev | bash -s - app setup");
            }
            pb.directory(workDir.toFile());
            pb.redirectErrorStream(true);
            Process process = pb.start();

            String output;
            try (BufferedReader reader
                    = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append(System.lineSeparator());
                }
                output = sb.toString();
            }

            boolean finished = process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                Assertions.fail("JBang installation timed out");
            }
            if (process.exitValue() != 0) {
                Assertions.fail("JBang installation failed: " + output);
            }
            LOG.info("JBang installed successfully");
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Failed to install JBang", e);
        }
    }

    private void stopBackgroundProcesses() {
        // Try graceful stop first
        try {
            execute("stop");
        } catch (Exception e) {
            LOG.warn("Failed to stop camel instances gracefully: {}", e.getMessage());
        }
        // Force kill any remaining tracked PIDs
        for (String pid : backgroundPids) {
            try {
                ProcessHandle.of(Long.parseLong(pid)).ifPresent(ph -> {
                    LOG.info("Force killing background process {}", pid);
                    ph.destroyForcibly();
                });
            } catch (NumberFormatException e) {
                LOG.warn("Invalid PID: {}", pid);
            }
        }
        backgroundPids.clear();
    }

    private Path getUserPropertiesFile() {
        return Path.of(System.getProperty("user.home"), ".camel-jbang-user.properties");
    }

    private void backupUserFiles() {
        backupFile(getUserPropertiesFile());
        backupFile(Path.of(System.getProperty("user.home"), ".camel-jbang-plugins.json"));
    }

    private void backupFile(Path file) {
        if (Files.exists(file)) {
            try {
                Path backup = Path.of(file + ".backup" + System.currentTimeMillis());
                Files.move(file, backup, StandardCopyOption.REPLACE_EXISTING);
                LOG.info("Backed up {} to {}", file, backup);
            } catch (IOException e) {
                LOG.warn("Failed to backup {}: {}", file, e.getMessage());
            }
        }
    }

    private void createMavenSettingsFile() {
        try {
            mavenSettingsFile = Files.createTempFile(workDir, "maven-settings", ".xml");
            String settingsContent = String.format(
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>%n"
                                                   + "<settings xmlns=\"http://maven.apache.org/SETTINGS/1.0.0\"%n"
                                                   + "          xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"%n"
                                                   + "          xsi:schemaLocation=\"http://maven.apache.org/SETTINGS/1.0.0 "
                                                   + "http://maven.apache.org/xsd/settings-1.0.0.xsd\">%n"
                                                   + "    <localRepository>%s</localRepository>%n"
                                                   + "</settings>%n",
                    Path.of(localMavenRepo).toAbsolutePath());
            Files.writeString(mavenSettingsFile, settingsContent, StandardCharsets.UTF_8);
            LOG.info("Created temporary Maven settings file at {} with localRepository={}", mavenSettingsFile,
                    localMavenRepo);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create temporary Maven settings file", e);
        }
    }

    @Override
    public int getSshPort() {
        throw new UnsupportedOperationException("SSH is not supported for local process service");
    }

    @Override
    public String getSshPassword() {
        throw new UnsupportedOperationException("SSH is not supported for local process service");
    }

}
