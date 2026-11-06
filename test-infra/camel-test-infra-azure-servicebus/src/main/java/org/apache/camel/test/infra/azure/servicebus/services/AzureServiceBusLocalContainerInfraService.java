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
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.spi.annotations.InfraService;
import org.apache.camel.test.infra.azure.common.services.FlociAzContainer;
import org.apache.camel.test.infra.azure.common.services.FlociAzInfraService;
import org.apache.camel.test.infra.azure.servicebus.common.AzureServiceBusProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;

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

    // where the official Service Bus emulator reads its topology, which Floci AZ reads as well
    private static final String TOPOLOGY_FILE = "/ServiceBus_Emulator/ConfigFiles/Config.json";
    private static final String TOPOLOGY_RESOURCE = "Config.json";
    // reports the namespaces and the host ports that Docker published their broker on
    private static final String NAMESPACES_PATH = "/" + FlociAzContainer.DEFAULT_ACCOUNT_NAME + "-servicebus/namespaces";
    private static final Pattern AMQP_HOST_PORT = Pattern.compile("\"amqpHostPort\"\\s*:\\s*(\\d+)");
    private static final String ARTEMIS_IMAGE_VARIABLE = "FLOCI_AZ_SERVICES_SERVICE_BUS_ARTEMIS_IMAGE";

    private int amqpPort;

    @Override
    protected FlociAzContainer initContainer(String imageName) {
        FlociAzContainer container = super.initContainer(imageName);

        container.withDockerSocket()
                .withEnv("FLOCI_AZ_SERVICES_SERVICE_BUS_MOCKED", "false")
                // Docker publishes the broker on free host ports, read back once it started, so that builds running at
                // the same time on one Docker host do not clash
                .withEnv("FLOCI_AZ_SERVICES_SERVICE_BUS_AMQP_PORT", "0")
                .withEnv("FLOCI_AZ_SERVICES_SERVICE_BUS_AMQP_TLS_PORT", "0")
                .withCopyToContainer(Transferable.of(readTopology()), TOPOLOGY_FILE)
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

        container.withBrokerImage(ARTEMIS_IMAGE_VARIABLE);
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
        try (HttpClient client = HttpClient.newHttpClient()) {
            String namespaces = client.send(request, HttpResponse.BodyHandlers.ofString()).body();
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
    public String connectionString() {
        // Floci AZ ignores the key, and UseDevelopmentEmulator has the Azure SDK connect with AMQP rather than AMQPS
        return String.format("Endpoint=sb://%s:%d;SharedAccessKeyName=RootManageSharedAccessKey;SharedAccessKey=SAS_KEY_VALUE;"
                             + "UseDevelopmentEmulator=true;",
                host(), amqpPort);
    }
}
