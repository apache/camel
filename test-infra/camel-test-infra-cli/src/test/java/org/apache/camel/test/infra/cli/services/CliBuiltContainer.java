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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.camel.test.infra.common.TestUtils;
import org.junit.platform.commons.util.StringUtils;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.utility.MountableFile;

public class CliBuiltContainer extends GenericContainer<CliBuiltContainer> {

    private static final String CAMEL_REF_ARG = "CAMEL_REF";
    private static final String KEEP_RUNNING_ARG = "KEEP_RUNNING";
    private static final String MOUNT_POINT = "/deployments/data";
    private static final String SSH_PASSWORD_ARG = "SSH_PASSWORD_ARG";
    private static final String FROM_IMAGE_NAME = "fedora:39";
    private static final String FROM_IMAGE_ARG = "FROMIMAGE";
    protected static final int DEV_CONSOLE_PORT = 8080;
    protected static final int SSH_PORT = 22;
    protected static final String TRUSTED_CERT_FOLDER = "/etc/pki/ca-trust/source/anchors";

    private final String sshPassword;

    public CliBuiltContainer(final String camelRef, final Boolean keepContainerRunning, final String dataFolder,
                             final String sshPassword, final Map<String, String> extraHosts,
                             final List<String> trustedCertPaths) {
        super(new ImageFromDockerfile("localhost/camel-cli:" + camelRef + (keepContainerRunning ? "-R" : ""), false)
                .withFileFromClasspath("Dockerfile",
                        "org/apache/camel/test/infra/cli/services/Dockerfile")
                .withFileFromClasspath("entrypoint.sh",
                        "org/apache/camel/test/infra/cli/services/entrypoint.sh")
                .withFileFromClasspath("99-ssh-jbang.conf",
                        "org/apache/camel/test/infra/cli/services/99-ssh-jbang.conf")
                .withBuildArg(FROM_IMAGE_ARG, TestUtils.prependHubImageNamePrefixIfNeeded(FROM_IMAGE_NAME))
                .withBuildArg(CAMEL_REF_ARG, camelRef)
                .withBuildArg(KEEP_RUNNING_ARG, String.valueOf(keepContainerRunning))
                .withBuildArg(SSH_PASSWORD_ARG, sshPassword));
        this.sshPassword = sshPassword;
        if (StringUtils.isNotBlank(dataFolder)) {
            withFileSystemBind(dataFolder, MOUNT_POINT, BindMode.READ_WRITE);
        }
        if (keepContainerRunning) {
            waitingFor(Wait.forLogMessage(".*keep container running.*", 1));
        }
        withExposedPorts(DEV_CONSOLE_PORT, SSH_PORT);
        if (Objects.nonNull(extraHosts)) {
            extraHosts.forEach((host, ip) -> withExtraHost(host, ip));
        }
        if (Objects.nonNull(trustedCertPaths)) {
            trustedCertPaths.forEach(t -> {
                final Path path = Paths.get(t);
                withCopyToContainer(MountableFile.forHostPath(path),
                        String.format("%s/%s", TRUSTED_CERT_FOLDER, path.getFileName()));
            });
        }
    }

    public String getMountPoint() {
        return MOUNT_POINT;
    }

    public String getSshPassword() {
        return sshPassword;
    }
}
