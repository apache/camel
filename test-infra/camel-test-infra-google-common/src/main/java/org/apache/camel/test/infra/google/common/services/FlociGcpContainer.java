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

package org.apache.camel.test.infra.google.common.services;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Volume;
import org.apache.camel.test.infra.common.LocalPropertyResolver;
import org.apache.camel.test.infra.common.services.ContainerEnvironmentUtil;
import org.apache.camel.test.infra.google.common.GoogleProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.RemoteDockerImage;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.ResourceReaper;

/**
 * A container running Floci GCP (https://github.com/floci-io/floci-gcp), a local Google Cloud emulator. It serves the
 * gRPC and the REST APIs of the Google Cloud services on one port, and accepts any project id.
 */
public class FlociGcpContainer extends GenericContainer<FlociGcpContainer> {
    public static final int SERVICE_PORT = 4588;

    private static final Logger LOG = LoggerFactory.getLogger(FlociGcpContainer.class);
    // Floci GCP puts this namespace in the name of the containers it starts, and in their floci_namespace label
    private static final String RESOURCE_NAMESPACE_ENV = "FLOCI_GCP_DOCKER_RESOURCE_NAMESPACE";
    private static final String RESOURCE_NAMESPACE_LABEL = "floci_namespace";
    private static final String DOCKER_SOCKET = "/var/run/docker.sock";
    private static final String BIGQUERY_ENGINE_IMAGE_ENV = "FLOCI_GCP_SERVICES_BIGQUERY_DUCK_DEFAULT_IMAGE";

    private String resourceNamespace;

    public FlociGcpContainer(String imageName, boolean fixedPort) {
        super(imageName);

        ContainerEnvironmentUtil.configurePort(this, fixedPort, SERVICE_PORT);

        waitingFor(Wait.forHttp("/health")
                .forPort(SERVICE_PORT)
                .forStatusCode(200)
                .withStartupTimeout(Duration.ofSeconds(120)));
    }

    /**
     * The address of the emulator, for example {@code http://localhost:32768}
     */
    public String serviceAddress() {
        return String.format("http://%s:%d", getHost(), getMappedPort(SERVICE_PORT));
    }

    /**
     * The address of the emulator for the gRPC clients, which connect to it in plain text, for example
     * {@code localhost:32768}
     */
    public String grpcAddress() {
        return String.format("%s:%d", getHost(), getMappedPort(SERVICE_PORT));
    }

    /**
     * Lets Floci GCP start containers of its own, such as the SQL engine of BigQuery: mounts the Docker socket of the
     * host, and gives the containers that Floci GCP starts a resource namespace of this container, by which
     * {@link #stop()} removes them. Each container has a namespace of its own: when it starts, Floci GCP removes the
     * containers of its namespace that it finds, as left over.
     */
    public FlociGcpContainer withDockerSocket() {
        resourceNamespace = "camel" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        withEnv(RESOURCE_NAMESPACE_ENV, resourceNamespace);
        return withCreateContainerCmdModifier(FlociGcpContainer::bindDockerSocket);
    }

    /**
     * Has Floci GCP run the queries of BigQuery on the floci-duck image of
     * {@link GoogleProperties#BIGQUERY_ENGINE_CONTAINER}, which this method pulls as Testcontainers pulls its own
     * images, so that image substitution and registry authentication apply: Floci GCP would pull it itself otherwise.
     * Needs {@link #withDockerSocket()}.
     */
    public FlociGcpContainer withBigQueryEngine() {
        String image = LocalPropertyResolver.getProperty(FlociGcpContainer.class,
                GoogleProperties.BIGQUERY_ENGINE_CONTAINER);
        return withEnv(BIGQUERY_ENGINE_IMAGE_ENV, new RemoteDockerImage(DockerImageName.parse(image)).get());
    }

    @Override
    public void start() {
        if (resourceNamespace != null) {
            // Testcontainers does not know the containers that Floci GCP starts: have them removed when the JVM exits,
            // should this container not be stopped
            ResourceReaper.instance().registerLabelsFilterForCleanup(Map.of(RESOURCE_NAMESPACE_LABEL, resourceNamespace));
        }
        super.start();
    }

    @Override
    public void stop() {
        try {
            super.stop();
        } finally {
            if (resourceNamespace != null) {
                removeStartedContainers();
            }
        }
    }

    // the containers that Floci GCP started are not removed with it
    private void removeStartedContainers() {
        DockerClient client = DockerClientFactory.instance().client();
        List<Container> containers = client.listContainersCmd()
                .withShowAll(true)
                .withLabelFilter(Map.of(RESOURCE_NAMESPACE_LABEL, resourceNamespace))
                .exec();
        for (Container container : containers) {
            LOG.debug("Removing the container {} that Floci GCP started", Arrays.toString(container.getNames()));
            try {
                client.removeContainerCmd(container.getId()).withForce(true).withRemoveVolumes(true).exec();
            } catch (NotFoundException e) {
                LOG.debug("The container {} was already removed", container.getId());
            }
        }
    }

    // where SELinux is enforcing, it denies a container the Docker socket of the host unless the labeling of the
    // container is disabled
    private static void bindDockerSocket(CreateContainerCmd cmd) {
        HostConfig hostConfig = cmd.getHostConfig();

        List<Bind> binds = new ArrayList<>();
        if (hostConfig.getBinds() != null) {
            binds.addAll(Arrays.asList(hostConfig.getBinds()));
        }
        binds.add(new Bind(DockerClientFactory.instance().getRemoteDockerUnixSocketPath(), new Volume(DOCKER_SOCKET)));

        List<String> securityOpts = new ArrayList<>();
        if (hostConfig.getSecurityOpts() != null) {
            securityOpts.addAll(hostConfig.getSecurityOpts());
        }
        securityOpts.add("label=disable");

        hostConfig.withBinds(binds).withSecurityOpts(securityOpts);
    }
}
