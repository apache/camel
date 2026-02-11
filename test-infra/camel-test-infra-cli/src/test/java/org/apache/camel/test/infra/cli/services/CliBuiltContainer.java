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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.camel.test.infra.common.TestUtils;
import org.junit.platform.commons.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.utility.MountableFile;

public class CliBuiltContainer extends GenericContainer<CliBuiltContainer> {

    private static final Logger LOGGER = LoggerFactory.getLogger(CliBuiltContainer.class);

    private static final String UID_ARG = "CUSTOM_UID";
    private static final String GID_ARG = "CUSTOM_GID";
    private static final String CAMEL_REF_ARG = "CAMEL_REF";
    private static final String CAMEL_REPO_ARG = "CAMEL_REPO";
    private static final String CAMEL_JBANG_VERSION_ARG = "CAMEL_JBANG_VERSION";
    private static final String KEEP_RUNNING_ARG = "KEEP_RUNNING";
    private static final String MOUNT_POINT = "/deployments/data";
    private static final String SSH_PASSWORD_ARG = "SSH_PASSWORD_ARG";
    // NOTE: make sure to run integration tests locally when changing in order to
    // validate potential breaking changes compatibility when building the container
    private static final String FROM_IMAGE_NAME = "mirror.gcr.io/fedora:43";
    private static final String FROM_IMAGE_ARG = "FROMIMAGE";
    protected static final int DEV_CONSOLE_PORT = 8080;
    protected static final int SSH_PORT = 22;
    protected static final String TRUSTED_CERT_FOLDER = "/etc/pki/ca-trust/source/anchors";
    public static final String CONTAINER_MVN_REPO = "/home/jbang/.m2/repository";

    private final String sshPassword;

    private static int UID = 1000;
    private static int GID = 1000;

    static {
        final Path target = Path.of("target");
        try {
            UID = (Integer) Files.getAttribute(target, "unix:uid");
            GID = (Integer) Files.getAttribute(target, "unix:gid");
            LOGGER.info("building container using user {} and group {}", UID, GID);
        } catch (IOException | UnsupportedOperationException e) {
            LOGGER.warn("unable to retrieve user id and group: {}", e.getMessage());
        }
    }

    public CliBuiltContainer(CliBuiltContainerParams params) {
        super(new ImageFromDockerfile(
                "localhost/camel-cli:" + params.getCamelRef() + "-" + params.getCamelJBangVersion()
                                      + (params.getKeepContainerRunning() ? "-R" : ""),
                false)
                .withFileFromPath("Dockerfile", StringUtils.isNotBlank(params.getDockerFile())
                        ? Path.of(params.getDockerFile())
                        : Path.of(MountableFile.forClasspathResource("org/apache/camel/test/infra/cli/services/Dockerfile")
                                .getResolvedPath()))
                .withFileFromClasspath("entrypoint.sh",
                        "org/apache/camel/test/infra/cli/services/entrypoint.sh")
                .withFileFromClasspath("99-ssh-jbang.conf",
                        "org/apache/camel/test/infra/cli/services/99-ssh-jbang.conf")
                .withBuildArg(FROM_IMAGE_ARG, TestUtils.prependHubImageNamePrefixIfNeeded(FROM_IMAGE_NAME))
                .withBuildArg(CAMEL_REF_ARG, params.getCamelRef())
                .withBuildArg(KEEP_RUNNING_ARG, String.valueOf(params.getKeepContainerRunning()))
                .withBuildArg(SSH_PASSWORD_ARG, params.getSshPassword())
                .withBuildArg(CAMEL_REPO_ARG, params.getCamelRepo())
                .withBuildArg(CAMEL_JBANG_VERSION_ARG, params.getCamelJBangVersion())
                .withBuildArg(UID_ARG, String.valueOf(UID))
                .withBuildArg(GID_ARG, String.valueOf(GID)));
        this.sshPassword = params.getSshPassword();
        if (StringUtils.isNotBlank(params.getDataFolder())) {
            withFileSystemBind(params.getDataFolder(), MOUNT_POINT, BindMode.READ_WRITE);
        }
        if (params.getKeepContainerRunning()) {
            waitingFor(Wait.forLogMessage(".*keep container running.*", 1));
        }
        withExposedPorts(DEV_CONSOLE_PORT, SSH_PORT);
        if (Objects.nonNull(params.getExtraHosts())) {
            params.getExtraHosts().forEach((host, ip) -> withExtraHost(host, ip));
        }
        if (Objects.nonNull(params.getTrustedCertPaths())) {
            params.getTrustedCertPaths().forEach(t -> {
                final Path path = Paths.get(t);
                withCopyToContainer(MountableFile.forHostPath(path),
                        String.format("%s/%s", TRUSTED_CERT_FOLDER, path.getFileName()));
            });
        }
        if (StringUtils.isNotBlank(params.getLocalMavenRepo())) {
            withFileSystemBind(params.getLocalMavenRepo(), CONTAINER_MVN_REPO, BindMode.READ_WRITE);
        }
    }

    public String getMountPoint() {
        return MOUNT_POINT;
    }

    public String getSshPassword() {
        return sshPassword;
    }

    public static class CliBuiltContainerParams {

        private String camelRepo;
        private String camelRef;
        private String camelJBangVersion;
        private Boolean keepContainerRunning;
        private String dataFolder;
        private String sshPassword;
        private Map<String, String> extraHosts;
        private List<String> trustedCertPaths;
        private String localMavenRepo;
        private String dockerFile;

        public String getCamelRepo() {
            return camelRepo;
        }

        public CliBuiltContainerParams setCamelRepo(String camelRepo) {
            this.camelRepo = camelRepo;
            return this;
        }

        public String getCamelJBangVersion() {
            return camelJBangVersion;
        }

        public CliBuiltContainerParams setCamelJBangVersion(String camelJBangVersion) {
            this.camelJBangVersion = camelJBangVersion;
            return this;
        }

        public String getCamelRef() {
            return camelRef;
        }

        public CliBuiltContainerParams setCamelRef(String camelRef) {
            this.camelRef = camelRef;
            return this;
        }

        public Boolean getKeepContainerRunning() {
            return keepContainerRunning;
        }

        public CliBuiltContainerParams setKeepContainerRunning(Boolean keepContainerRunning) {
            this.keepContainerRunning = keepContainerRunning;
            return this;
        }

        public String getDataFolder() {
            return dataFolder;
        }

        public CliBuiltContainerParams setDataFolder(String dataFolder) {
            this.dataFolder = dataFolder;
            return this;
        }

        public String getSshPassword() {
            return sshPassword;
        }

        public CliBuiltContainerParams setSshPassword(String sshPassword) {
            this.sshPassword = sshPassword;
            return this;
        }

        public Map<String, String> getExtraHosts() {
            return extraHosts;
        }

        public CliBuiltContainerParams setExtraHosts(Map<String, String> extraHosts) {
            this.extraHosts = extraHosts;
            return this;
        }

        public List<String> getTrustedCertPaths() {
            return trustedCertPaths;
        }

        public CliBuiltContainerParams setTrustedCertPaths(List<String> trustedCertPaths) {
            this.trustedCertPaths = trustedCertPaths;
            return this;
        }

        public String getLocalMavenRepo() {
            return localMavenRepo;
        }

        public CliBuiltContainerParams setLocalMavenRepo(String localMavenRepo) {
            this.localMavenRepo = localMavenRepo;
            return this;
        }

        public String getDockerFile() {
            return dockerFile;
        }

        public CliBuiltContainerParams setDockerFile(String dockerFile) {
            this.dockerFile = dockerFile;
            return this;
        }
    }
}
