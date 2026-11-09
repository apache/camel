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

package org.apache.camel.test.infra.azure.common.services;

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
import org.apache.camel.test.infra.azure.common.AzureCredentialsHolder;
import org.apache.camel.test.infra.azure.common.AzureProperties;
import org.apache.camel.test.infra.common.LocalPropertyResolver;
import org.apache.camel.test.infra.common.services.ContainerEnvironmentUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.RemoteDockerImage;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.ResourceReaper;

/**
 * A container running Floci AZ (https://github.com/floci-io/floci-az), a local Azure emulator. It serves every Azure
 * service on one port, and tells them apart by the first segment of the path: {@code /devstoreaccount1} for Blob and
 * Data Lake, {@code /devstoreaccount1-queue} for Queue, {@code /devstoreaccount1-keyvault} for Key Vault, and so on.
 */
public class FlociAzContainer extends GenericContainer<FlociAzContainer> {
    public static final int SERVICE_PORT = 4577;
    public static final String DEFAULT_ACCOUNT_NAME = "devstoreaccount1";
    // the well-known development storage key, which Floci AZ accepts as Azurite does
    public static final String DEFAULT_ACCOUNT_KEY
            = "Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==";

    private static final Logger LOG = LoggerFactory.getLogger(FlociAzContainer.class);
    // Floci AZ puts this namespace in the name of the containers it starts, and in their floci_namespace label
    private static final String RESOURCE_NAMESPACE_ENV = "FLOCI_AZ_DOCKER_RESOURCE_NAMESPACE";
    private static final String RESOURCE_NAMESPACE_LABEL = "floci_namespace";
    private static final String DOCKER_SOCKET = "/var/run/docker.sock";

    private String resourceNamespace;

    public FlociAzContainer() {
        this(LocalPropertyResolver.getProperty(FlociAzContainer.class, AzureProperties.AZURE_CONTAINER), false);
    }

    public FlociAzContainer(String imageName, boolean fixedPort) {
        super(imageName);

        ContainerEnvironmentUtil.configurePort(this, fixedPort, SERVICE_PORT);

        waitingFor(Wait.forHttp("/health")
                .forPort(SERVICE_PORT)
                .forStatusCode(200)
                .withStartupTimeout(Duration.ofSeconds(120)));
    }

    public AzureCredentialsHolder azureCredentials() {
        return new AzureCredentialsHolder() {
            @Override
            public String accountName() {
                return DEFAULT_ACCOUNT_NAME;
            }

            @Override
            public String accountKey() {
                return DEFAULT_ACCOUNT_KEY;
            }
        };
    }

    /**
     * The address of the emulator, for example {@code http://localhost:32768}
     */
    public String serviceAddress() {
        return String.format("http://%s:%d", getHost(), getMappedPort(SERVICE_PORT));
    }

    /**
     * The endpoint of the storage account, used by the Blob and Data Lake clients, for example
     * {@code http://localhost:32768/devstoreaccount1}
     */
    public String accountEndpoint() {
        return serviceAddress() + "/" + DEFAULT_ACCOUNT_NAME;
    }

    /**
     * Lets Floci AZ start containers of its own, such as the Artemis brokers of the AMQP data plane of Event Hubs and
     * Service Bus: mounts the Docker socket of the host, and gives the containers that Floci AZ starts a resource
     * namespace of this container, by which {@link #stop()} removes them. Each container has a namespace of its own:
     * when it starts, Floci AZ removes the containers of its namespace that it finds, as left over.
     */
    public FlociAzContainer withDockerSocket() {
        resourceNamespace = "camel" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        withEnv(RESOURCE_NAMESPACE_ENV, resourceNamespace);
        return withCreateContainerCmdModifier(FlociAzContainer::bindDockerSocket);
    }

    /**
     * Has Floci AZ start an AMQP broker with the Artemis image of {@link AzureProperties#AZURE_BROKER_CONTAINER}, which
     * this method pulls as Testcontainers pulls its own images, so that image substitution and registry authentication
     * apply: Floci AZ would pull it itself otherwise. Needs {@link #withDockerSocket()}.
     *
     * @param imageVariable the variable of Floci AZ that names the broker image of a service, for example
     *                      {@code FLOCI_AZ_SERVICES_EVENT_HUB_ARTEMIS_IMAGE}
     */
    public FlociAzContainer withBrokerImage(String imageVariable) {
        String image = LocalPropertyResolver.getProperty(FlociAzContainer.class, AzureProperties.AZURE_BROKER_CONTAINER);
        return withEnv(imageVariable, new RemoteDockerImage(DockerImageName.parse(image)).get());
    }

    @Override
    public void start() {
        if (resourceNamespace != null) {
            // Testcontainers does not know the containers that Floci AZ starts: have them removed when the JVM exits,
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

    // the containers that Floci AZ started are not removed with it
    private void removeStartedContainers() {
        DockerClient client = DockerClientFactory.instance().client();
        List<Container> containers = client.listContainersCmd()
                .withShowAll(true)
                .withLabelFilter(Map.of(RESOURCE_NAMESPACE_LABEL, resourceNamespace))
                .exec();
        for (Container container : containers) {
            LOG.debug("Removing the container {} that Floci AZ started", Arrays.toString(container.getNames()));
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
