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

package org.apache.camel.test.infra.azure.eventhubs.services;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.spi.annotations.InfraService;
import org.apache.camel.test.infra.azure.common.services.FlociAzContainer;
import org.apache.camel.test.infra.azure.common.services.FlociAzInfraService;
import org.apache.camel.test.infra.azure.eventhubs.common.AzureEventHubsProperties;
import org.apache.camel.test.infra.common.services.ContainerEnvironmentUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A local Event Hubs service with Floci AZ, which runs the AMQP broker of an Event Hubs namespace in an Apache ActiveMQ
 * Artemis container that it starts itself, through the Docker socket mounted into the Floci AZ container. The namespace
 * has one event hub, {@value #EVENT_HUB_NAME}, with {@value #PARTITION_COUNT} partitions, and the checkpoints go to the
 * Blob service of Floci AZ.
 * <p>
 * Floci AZ reaches the broker on the Docker network, the clients on the port Docker publishes it on. Testcontainers
 * does not know the broker container: stopping the Floci AZ container removes it (see
 * {@link FlociAzContainer#withDockerSocket()}).
 * <p>
 * Floci AZ answers the {@code $management} requests for the properties of the event hub, but not for the properties of
 * a partition. It spreads the events of a batch sent with no partition id or key over the partitions, one event after
 * the other, where Azure stores the whole batch in one partition.
 */
@InfraService(service = AzureEventHubsInfraService.class,
              description = "Local Azure Event Hubs service with Floci AZ",
              serviceAlias = "azure", serviceImplementationAlias = "event-hubs")
public class AzureEventHubsLocalContainerInfraService extends FlociAzInfraService implements AzureEventHubsInfraService {
    /**
     * The event hub, in lower case: an event processor of the Azure SDK lower-cases the name of its event hub
     */
    public static final String EVENT_HUB_NAME = "camel-test";
    public static final int PARTITION_COUNT = 2;
    /**
     * The consumer groups of the event hub. Floci AZ takes their names as case-sensitive, while Azure does not, and an
     * event processor of the Azure SDK lower-cases the name of its consumer group: {@code $default} is the default
     * consumer group of the event processors, {@code $Default} the one of the other consumers.
     */
    public static final List<String> CONSUMER_GROUPS = List.of("$Default", "$default");

    private static final Logger LOG = LoggerFactory.getLogger(AzureEventHubsLocalContainerInfraService.class);

    // the namespace that Floci AZ creates by default
    private static final String NAMESPACE = "emulatorNs1";
    private static final String ARTEMIS_IMAGE_VARIABLE = "FLOCI_AZ_SERVICES_EVENT_HUB_ARTEMIS_IMAGE";
    private static final Pattern AMQP_HOST_PORT = Pattern.compile("\"amqpHostPort\"\\s*:\\s*(\\d+)");
    // Floci AZ ignores the key: the broker accepts any client
    private static final String SHARED_ACCESS_KEY_NAME = "RootManageSharedAccessKey";
    private static final String SHARED_ACCESS_KEY = "devkey";

    private int amqpPort;
    private volatile boolean started;

    @Override
    protected FlociAzContainer initContainer(String imageName) {
        FlociAzContainer container = super.initContainer(imageName)
                .withDockerSocket()
                .withEnv("FLOCI_AZ_SERVICES_EVENT_HUB_ENTITIES", EVENT_HUB_NAME + ":" + PARTITION_COUNT)
                .withEnv("FLOCI_AZ_SERVICES_EVENT_HUB_CONSUMER_GROUPS", String.join(",", CONSUMER_GROUPS));
        if (!ContainerEnvironmentUtil.isFixedPort(getClass())) {
            // Docker publishes the broker on free host ports, so that builds running at the same time do not clash;
            // camel infra run keeps the ports 5672 (AMQP) and 5671 (AMQPS) that Floci AZ documents
            container.withEnv("FLOCI_AZ_SERVICES_EVENT_HUB_AMQP_PORT", "0")
                    .withEnv("FLOCI_AZ_SERVICES_EVENT_HUB_AMQP_TLS_PORT", "0");
        }
        return container;
    }

    @Override
    public void initialize() {
        started = true;
        try {
            getContainer().withBrokerImage(ARTEMIS_IMAGE_VARIABLE);
            getContainer().start();
            amqpPort = startNamespace();
        } catch (RuntimeException e) {
            // also removes the broker, should Floci AZ have started it
            try {
                shutdown();
            } catch (RuntimeException suppressed) {
                e.addSuppressed(suppressed);
            }
            throw e;
        }

        LOG.info("Floci AZ running at address {}, with the Event Hubs broker at {}:{}", getContainer().serviceAddress(),
                host(), amqpPort);

        registerProperties();
    }

    /**
     * Floci AZ starts the broker of a namespace when the namespace is created, and the request returns once the broker
     * listens.
     *
     * @return the host port of the AMQP port of the broker
     */
    private int startNamespace() {
        URI uri = URI.create(getContainer().serviceAddress() + "/" + FlociAzContainer.DEFAULT_ACCOUNT_NAME
                             + "-eventhub/namespaces/" + NAMESPACE);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMinutes(2))
                .PUT(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> response;
        try (HttpClient client = HttpClient.newHttpClient()) {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new IllegalStateException("Floci AZ did not start the Event Hubs namespace " + NAMESPACE, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while Floci AZ was starting the Event Hubs namespace", e);
        }

        Matcher matcher = AMQP_HOST_PORT.matcher(response.body());
        if (response.statusCode() / 100 != 2 || !matcher.find()) {
            throw new IllegalStateException(
                    "Floci AZ did not start the Event Hubs namespace " + NAMESPACE + ": HTTP " + response.statusCode() + " "
                                            + response.body());
        }
        return Integer.parseInt(matcher.group(1));
    }

    /**
     * Stops Floci AZ, which removes the broker too. A singleton service is shut down by a JVM shutdown hook for each
     * test class that used it, and these hooks run at the same time.
     */
    @Override
    public synchronized void shutdown() {
        if (!started) {
            return;
        }
        started = false;

        super.shutdown();
    }

    @Override
    public void registerProperties() {
        super.registerProperties();
        System.setProperty(AzureEventHubsProperties.CONNECTION_STRING, connectionString());
        System.setProperty(AzureEventHubsProperties.BLOB_ENDPOINT, blobEndpoint());
        System.setProperty(AzureEventHubsProperties.BLOB_ACCOUNT_NAME, blobAccountName());
        System.setProperty(AzureEventHubsProperties.BLOB_ACCESS_KEY, blobAccessKey());
    }

    /**
     * The connection string of the event hub. {@code UseDevelopmentEmulator=true} makes the client connect with plain
     * AMQP.
     */
    @Override
    public String connectionString() {
        return "Endpoint=sb://" + host() + ":" + amqpPort
               + ";SharedAccessKeyName=" + SHARED_ACCESS_KEY_NAME
               + ";SharedAccessKey=" + SHARED_ACCESS_KEY
               + ";UseDevelopmentEmulator=true;EntityPath=" + EVENT_HUB_NAME;
    }

    @Override
    public String blobEndpoint() {
        return getContainer().accountEndpoint();
    }

    @Override
    public String blobAccountName() {
        return accountName();
    }

    @Override
    public String blobAccessKey() {
        return accessKey();
    }
}
