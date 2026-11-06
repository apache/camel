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

package org.apache.camel.test.infra.azure.servicebus.services;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Volume;
import org.apache.camel.spi.annotations.InfraService;
import org.apache.camel.test.infra.azure.common.services.FlociAzContainer;
import org.apache.camel.test.infra.azure.common.services.FlociAzInfraService;
import org.apache.camel.test.infra.azure.servicebus.common.AzureServiceBusProperties;
import org.apache.camel.test.infra.common.LocalPropertyResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.RemoteDockerImage;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.ResourceReaper;

/**
 * A local Service Bus namespace. Floci AZ serves the AMQP 1.0 data plane of Service Bus with an Artemis broker, which
 * it starts in a container of its own through the Docker socket of the host. The namespace has the queues, topics and
 * subscriptions declared in {@code Config.json}, in the topology format of the official Service Bus emulator, which
 * Floci AZ creates when it starts.
 * <p>
 * Floci AZ does not implement every operation of Service Bus over AMQP: scheduled messages, deferred messages, session
 * state and rule management are not available.
 */
@InfraService(service = AzureServiceBusInfraService.class,
              description = "Local Azure Service Bus service with Floci AZ",
              serviceAlias = "azure", serviceImplementationAlias = "service-bus")
public class AzureServiceBusLocalContainerInfraService extends FlociAzInfraService implements AzureServiceBusInfraService {
    private static final Logger LOG = LoggerFactory.getLogger(AzureServiceBusLocalContainerInfraService.class);

    private static final String DOCKER_SOCKET = "/var/run/docker.sock";
    // where the official Service Bus emulator reads its topology, which Floci AZ reads as well
    private static final String TOPOLOGY_FILE = "/ServiceBus_Emulator/ConfigFiles/Config.json";
    private static final String TOPOLOGY_RESOURCE = "Config.json";
    // reports the namespaces and the host ports that Docker published their broker on
    private static final String NAMESPACES_PATH = "/" + FlociAzContainer.DEFAULT_ACCOUNT_NAME + "-servicebus/namespaces";
    private static final Pattern AMQP_HOST_PORT = Pattern.compile("\"amqpHostPort\"\\s*:\\s*(\\d+)");
    // Floci AZ puts this namespace in the name of the containers it creates, and in their floci_namespace label
    private static final String RESOURCE_NAMESPACE_ENV = "FLOCI_AZ_DOCKER_RESOURCE_NAMESPACE";
    private static final String RESOURCE_NAMESPACE_LABEL = "floci_namespace";
    private static final String ARTEMIS_IMAGE_ENV = "FLOCI_AZ_SERVICES_SERVICE_BUS_ARTEMIS_IMAGE";

    private int amqpPort;

    @Override
    protected FlociAzContainer initContainer(String imageName) {
        FlociAzContainer container = super.initContainer(imageName);

        container.withEnv("FLOCI_AZ_SERVICES_SERVICE_BUS_MOCKED", "false")
                // Docker publishes the broker on free host ports, read back once it started, so that builds running at
                // the same time on one Docker host do not clash
                .withEnv("FLOCI_AZ_SERVICES_SERVICE_BUS_AMQP_PORT", "0")
                .withEnv("FLOCI_AZ_SERVICES_SERVICE_BUS_AMQP_TLS_PORT", "0")
                // when it starts, Floci AZ removes the containers of its namespace it finds, as left over: each
                // instance has a namespace of its own, so that it does not remove the broker of another instance
                .withEnv(RESOURCE_NAMESPACE_ENV, "camel" + UUID.randomUUID().toString().replace("-", "").substring(0, 12))
                .withCopyToContainer(Transferable.of(readTopology()), TOPOLOGY_FILE)
                .withCreateContainerCmdModifier(cmd -> {
                    HostConfig hostConfig = cmd.getHostConfig();
                    List<Bind> binds = new ArrayList<>();
                    if (hostConfig.getBinds() != null) {
                        binds.addAll(Arrays.asList(hostConfig.getBinds()));
                    }
                    binds.add(new Bind(
                            DockerClientFactory.instance().getRemoteDockerUnixSocketPath(), new Volume(DOCKER_SOCKET)));
                    hostConfig.withBinds(binds)
                            // where SELinux is enforcing, it denies a container the Docker socket of the host unless
                            // the labeling of the container is disabled
                            .withSecurityOpts(List.of("label=disable"));
                })
                // Floci AZ answers once the broker started and the topology is created
                .waitingFor(Wait.forHttp(NAMESPACES_PATH)
                        .forPort(FlociAzContainer.SERVICE_PORT)
                        .forStatusCode(200)
                        .forResponsePredicate(body -> AMQP_HOST_PORT.matcher(body).find())
                        .withStartupTimeout(Duration.ofMinutes(2)));

        return container;
    }

    private static byte[] readTopology() {
        try (InputStream topology = AzureServiceBusLocalContainerInfraService.class.getResourceAsStream(TOPOLOGY_RESOURCE)) {
            return Objects.requireNonNull(topology, "Missing the Service Bus topology " + TOPOLOGY_RESOURCE).readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void initialize() {
        FlociAzContainer container = getContainer();

        // Floci AZ pulls the broker image before it answers: pull it here instead, as Testcontainers pulls its images
        String brokerImage = LocalPropertyResolver.getProperty(AzureServiceBusLocalContainerInfraService.class,
                AzureServiceBusProperties.BROKER_CONTAINER);
        container.withEnv(ARTEMIS_IMAGE_ENV, new RemoteDockerImage(DockerImageName.parse(brokerImage)).get());

        // Testcontainers does not know the broker container, which Floci AZ creates: have it removed when the JVM
        // exits, should the service not be shut down
        ResourceReaper.instance().registerLabelsFilterForCleanup(Map.of(RESOURCE_NAMESPACE_LABEL, resourceNamespace()));

        container.start();
        amqpPort = readAmqpPort();

        LOG.info("Floci AZ running at address {}, with the Service Bus broker at {}:{}", container.serviceAddress(), host(),
                amqpPort);

        registerProperties();
    }

    private int readAmqpPort() {
        HttpRequest request = HttpRequest.newBuilder(URI.create(getContainer().serviceAddress() + NAMESPACES_PATH))
                .timeout(Duration.ofSeconds(30))
                .build();
        try {
            String namespaces = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString()).body();
            Matcher matcher = AMQP_HOST_PORT.matcher(namespaces);
            if (!matcher.find()) {
                throw new IllegalStateException("Floci AZ reports no Service Bus broker: " + namespaces);
            }
            return Integer.parseInt(matcher.group(1));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void registerProperties() {
        System.setProperty(AzureServiceBusProperties.CONNECTION_STRING, connectionString());
    }

    @Override
    public void shutdown() {
        try {
            super.shutdown();
        } finally {
            removeBrokerContainers();
        }
    }

    // the broker containers are not removed with Floci AZ, which Testcontainers kills
    private void removeBrokerContainers() {
        DockerClient client = DockerClientFactory.instance().client();
        List<Container> containers = client.listContainersCmd()
                .withShowAll(true)
                .withLabelFilter(Map.of(RESOURCE_NAMESPACE_LABEL, resourceNamespace()))
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

    private String resourceNamespace() {
        return getContainer().getEnvMap().get(RESOURCE_NAMESPACE_ENV);
    }

    @Override
    public String connectionString() {
        // Floci AZ ignores the key, and UseDevelopmentEmulator has the Azure SDK connect with AMQP rather than AMQPS
        return String.format("Endpoint=sb://%s:%d;SharedAccessKeyName=RootManageSharedAccessKey;SharedAccessKey=SAS_KEY_VALUE;"
                             + "UseDevelopmentEmulator=true;",
                host(), amqpPort);
    }
}
