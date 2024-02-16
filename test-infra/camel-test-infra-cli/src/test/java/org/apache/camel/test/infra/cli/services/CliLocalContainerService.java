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

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.camel.test.infra.cli.common.CliProperties;
import org.apache.camel.test.infra.common.services.ContainerService;
import org.junit.jupiter.api.Assertions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Container;
import org.testcontainers.shaded.org.apache.commons.lang3.StringUtils;

public class CliLocalContainerService implements CliService, ContainerService<CliBuiltContainer> {
    public static final String CONTAINER_NAME = "camel-cli";
    private static final Logger LOG = LoggerFactory.getLogger(CliLocalContainerService.class);
    private final CliBuiltContainer container;
    private String version;

    private String forceToRunVersion;

    private String mavenRepos;

    public CliLocalContainerService() {
        this(System.getProperty(CliProperties.VERSION, "main"), true, System.getProperty(CliProperties.DATA_FOLDER),
             System.getProperty(CliProperties.SSH_PASSWORD, "jbang"), System.getProperty(CliProperties.FORCE_RUN_VERSION, ""),
             System.getProperty(CliProperties.MVN_REPOS), getHostsMap(), getCertPaths());
    }

    protected CliLocalContainerService(String camelRef, Boolean keepRunning, String dataFolder, String sshPassword,
                                       String forceToRunVersion, String mavenRepos, Map<String, String> extraHosts,
                                       List<String> trustedCertPaths) {
        container = new CliBuiltContainer(camelRef, keepRunning, dataFolder, sshPassword, extraHosts, trustedCertPaths);
        this.forceToRunVersion = forceToRunVersion;
        this.mavenRepos = mavenRepos;
    }

    @Override
    public void registerProperties() {
        //do nothing
    }

    @Override
    public void initialize() {
        if (!container.isRunning()) {
            LOG.info("Trying to start the {} container", CONTAINER_NAME);
            container.start();

            registerProperties();
            LOG.info("{} instance running", CONTAINER_NAME);
            if (StringUtils.isNotBlank(forceToRunVersion)) {
                LOG.info("force to use version {}", forceToRunVersion);
                execute("version set " + forceToRunVersion);
            }
            if (StringUtils.isNotBlank(mavenRepos)) {
                LOG.info("set repositories {}", mavenRepos);
                execute(String.format("config set repos=%s", mavenRepos));
            }
            if (LOG.isDebugEnabled()) {
                LOG.debug("Camel JBang version {}", version());
            }
        } else {
            LOG.debug("the container is already running");
        }

    }

    @Override
    public void shutdown() {
        if (container.isRunning()) {
            LOG.info("Stopping the {} container", CONTAINER_NAME);
            container.stop();
        } else {
            LOG.debug("the container is already stopped");
        }

    }

    @Override
    public CliBuiltContainer getContainer() {
        return container;
    }

    @Override
    public String execute(String command) {
        return executeGenericCommand(String.format("camel %s", command));
    }

    @Override
    public String executeBackground(String command) {
        return StringUtils.substringAfter(execute(command.concat(" --background")), "PID:").trim();
    }

    @Override
    public String executeGenericCommand(String command) {
        try {
            LOG.debug("executing {}", command);
            Container.ExecResult execResult = container.execInContainer("/bin/bash", "-c", command);
            if (execResult.getExitCode() != 0) {
                Assertions.fail(String.format("command %s failed with output %s and error %s", command, execResult.getStdout(),
                        execResult.getStderr()));
            }
            if (LOG.isDebugEnabled()) {
                LOG.debug("result out {}", execResult.getStdout());
                LOG.debug("result error {}", execResult.getStderr());
            }
            return execResult.getStdout();
        } catch (Exception e) {
            Assertions.fail(String.format("command %s failed", command), e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public void copyFileInternally(String source, String destination) {
        try {
            Assertions.assertEquals(0,
                    container.execInContainer(String.format("cp %s %s", source, destination).split(" ")).getExitCode(),
                    "copy file exit code");
        } catch (IOException | InterruptedException e) {
            Assertions.fail(String.format("unable to copy file %s to %s", source, destination), e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getMountPoint() {
        return container.getMountPoint();
    }

    @Override
    public String getContainerLogs() {
        return container.getLogs();
    }

    @Override
    public int getDevConsolePort() {
        return container.getMappedPort(container.DEV_CONSOLE_PORT);
    }

    @Override
    public Stream<String> listDirectory(String directoryPath) {
        try {
            Container.ExecResult result = container.execInContainer("ls", "-m", directoryPath);
            Assertions.assertEquals(0, result.getExitCode(), "list folder exit code");
            return Arrays.stream(result.getStdout().split(",")).map(String::trim);
        } catch (IOException | InterruptedException e) {
            Assertions.fail("unable to list " + directoryPath, e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public String id() {
        return container.getContainerId().substring(0, 13);
    }

    @Override
    public String version() {
        return Optional.ofNullable(version)
                .orElseGet(() -> {
                    final String versionSummary = execute("version");
                    if (versionSummary.contains("User configuration") && versionSummary.contains("camel-version = ")) {
                        version = StringUtils.substringBetween(versionSummary, "camel-version = ", "\n").trim();
                    }
                    if (version == null) {
                        version = StringUtils.substringBetween(versionSummary, "Camel JBang version:", "\n").trim();
                    }
                    return version;
                });
    }

    @Override
    public int getSshPort() {
        return container.getMappedPort(container.SSH_PORT);
    }

    @Override
    public String getSshPassword() {
        return container.getSshPassword();
    }

    private static Map<String, String> getHostsMap() {
        return Optional.ofNullable(System.getProperty(CliProperties.EXTRA_HOSTS))
                .map(p -> p.split(","))
                .stream().flatMap(strings -> Arrays.asList(strings).stream())
                .map(s -> s.split("="))
                .collect(Collectors.toMap(entry -> entry[0], entry -> entry[1]));
    }

    private static List<String> getCertPaths() {
        return Optional.ofNullable(System.getProperty(CliProperties.TRUSTED_CERT_PATHS))
                .map(p -> p.split(","))
                .stream().flatMap(strings -> Arrays.asList(strings).stream())
                .collect(Collectors.toList());
    }
}
