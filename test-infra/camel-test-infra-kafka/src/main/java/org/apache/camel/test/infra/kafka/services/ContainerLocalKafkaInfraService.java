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

package org.apache.camel.test.infra.kafka.services;

import org.apache.camel.spi.annotations.InfraService;
import org.apache.camel.test.infra.common.LocalPropertyResolver;
import org.apache.camel.test.infra.common.services.ContainerEnvironmentUtil;
import org.apache.camel.test.infra.common.services.ContainerService;
import org.apache.camel.test.infra.kafka.common.KafkaProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

@InfraService(service = KafkaInfraService.class,
              description = "Apache Kafka, Distributed event streaming platform",
              serviceAlias = "kafka", uiSupported = true)
public class ContainerLocalKafkaInfraService implements KafkaInfraService, ContainerService<KafkaContainer> {
    public static final String KAFKA_IMAGE_NAME = LocalPropertyResolver.getProperty(
            ContainerLocalKafkaInfraService.class,
            KafkaProperties.KAFKA_CONTAINER);

    private static final String KAFKA_UI_CONTAINER_IMAGE = "kafka-ui.container.image";
    private static final int KAFKA_UI_PORT = 9080;
    private static final String KAFKA_NETWORK_ALIAS = "kafka-broker";

    private static final Logger LOG = LoggerFactory.getLogger(ContainerLocalKafkaInfraService.class);
    protected KafkaContainer kafka;
    private GenericContainer<?> uiContainer;
    private Network uiNetwork;

    public ContainerLocalKafkaInfraService() {
        kafka = initContainer();
        String name = ContainerEnvironmentUtil.containerName(this.getClass());
        if (name != null) {
            kafka.withCreateContainerCmdModifier(cmd -> cmd.withName(name));
        }
    }

    public ContainerLocalKafkaInfraService(KafkaContainer kafka) {
        this.kafka = kafka;
        String name = ContainerEnvironmentUtil.containerName(this.getClass());
        if (name != null) {
            kafka.withCreateContainerCmdModifier(cmd -> cmd.withName(name));
        }
    }

    protected KafkaContainer initContainer() {
        boolean fixedPort = ContainerEnvironmentUtil.isFixedPort(this.getClass());

        class TestInfraKafkaContainer extends KafkaContainer {
            public TestInfraKafkaContainer(boolean fixedPort) {
                super(DockerImageName.parse(System.getProperty(KafkaProperties.KAFKA_CONTAINER, KAFKA_IMAGE_NAME))
                        .asCompatibleSubstituteFor("apache/kafka"));

                ContainerEnvironmentUtil.configurePort(this, fixedPort, 9092);
            }
        }

        KafkaContainer container = new TestInfraKafkaContainer(fixedPort);

        if (ContainerEnvironmentUtil.isWithUi()) {
            uiNetwork = Network.newNetwork();
            container.withNetwork(uiNetwork)
                    .withNetworkAliases(KAFKA_NETWORK_ALIAS)
                    .withListener(KAFKA_NETWORK_ALIAS + ":19092");
        }

        return container;
    }

    public String getBootstrapServers() {
        return kafka.getBootstrapServers();
    }

    @Override
    public String brokers() {
        return getBootstrapServers();
    }

    @Override
    public void registerProperties() {
        System.setProperty(KafkaProperties.KAFKA_BOOTSTRAP_SERVERS, getBootstrapServers());
    }

    @Override
    public void initialize() {
        kafka.start();
        registerProperties();

        LOG.info("Kafka bootstrap server running at address {}", kafka.getBootstrapServers());

        if (ContainerEnvironmentUtil.isWithUi()) {
            try {
                startUiContainer();
            } catch (Exception e) {
                LOG.warn("Failed to start Kafka UI container: {}", e.getMessage(), e);
            }
        }
    }

    private void startUiContainer() {
        String uiImage = LocalPropertyResolver.getProperty(
                ContainerLocalKafkaInfraService.class, KAFKA_UI_CONTAINER_IMAGE);

        uiContainer = new GenericContainer<>(uiImage)
                .withNetwork(uiNetwork)
                .withEnv("DYNAMIC_CONFIG_ENABLED", "true")
                .withEnv("KAFKA_CLUSTERS_0_NAME", "camel-infra")
                .withEnv("KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS", KAFKA_NETWORK_ALIAS + ":19092")
                .withEnv("SERVER_PORT", String.valueOf(KAFKA_UI_PORT));
        ContainerEnvironmentUtil.configurePort(uiContainer, true, KAFKA_UI_PORT);
        uiContainer.start();

        LOG.info("Kafka UI running at http://{}:{}", uiContainer.getHost(), uiContainer.getMappedPort(KAFKA_UI_PORT));
    }

    @Override
    public void shutdown() {
        if (uiContainer != null) {
            LOG.info("Shutting down Kafka UI container");
            uiContainer.stop();
        }
        LOG.info("Shutting down Kafka container");
        kafka.stop();
    }

    @Override
    public String uiUrl() {
        if (uiContainer != null && uiContainer.isRunning()) {
            return String.format("http://%s:%d", uiContainer.getHost(), uiContainer.getMappedPort(KAFKA_UI_PORT));
        }
        return null;
    }

    @Override
    public KafkaContainer getContainer() {
        return kafka;
    }

    public static ContainerLocalKafkaInfraService kafkaContainer() {
        KafkaContainer container
                = new KafkaContainer(
                        DockerImageName.parse(System.getProperty(KafkaProperties.KAFKA_CONTAINER, KAFKA_IMAGE_NAME))
                                .asCompatibleSubstituteFor("apache/kafka"));

        return new ContainerLocalKafkaInfraService(container);
    }
}
