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
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

@InfraService(service = KafkaInfraService.class,
              description = "Apache Kafka, Distributed event streaming platform",
              serviceAlias = "kafka")
public class ContainerLocalKafkaInfraService implements KafkaInfraService, ContainerService<KafkaContainer> {
    public static final String KAFKA3_IMAGE_NAME = LocalPropertyResolver.getProperty(
            ContainerLocalKafkaInfraService.class,
            KafkaProperties.KAFKA3_CONTAINER);

    private static final Logger LOG = LoggerFactory.getLogger(ContainerLocalKafkaInfraService.class);
    protected KafkaContainer kafka;

    public ContainerLocalKafkaInfraService() {
        kafka = initContainer();
    }

    public ContainerLocalKafkaInfraService(KafkaContainer kafka) {
        this.kafka = kafka;
    }

    protected KafkaContainer initContainer() {
        class TestInfraKafkaContainer extends KafkaContainer {
            public TestInfraKafkaContainer(boolean fixedPort) {
                super(DockerImageName.parse(System.getProperty(KafkaProperties.KAFKA_CONTAINER, KAFKA3_IMAGE_NAME))
                        .asCompatibleSubstituteFor("apache/kafka"));

                if (fixedPort) {
                    addFixedExposedPort(9092, 9092);
                }
            }
        }

        return new TestInfraKafkaContainer(ContainerEnvironmentUtil.isFixedPort(this.getClass()));
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
    }

    @Override
    public void shutdown() {
        LOG.info("Shutting down Kafka container");
        kafka.stop();
    }

    @Override
    public KafkaContainer getContainer() {
        return kafka;
    }

    public static ContainerLocalKafkaInfraService kafka3Container() {
        KafkaContainer container
                = new KafkaContainer(
                        DockerImageName.parse(System.getProperty(KafkaProperties.KAFKA_CONTAINER, KAFKA3_IMAGE_NAME))
                                .asCompatibleSubstituteFor("apache/kafka"));

        return new ContainerLocalKafkaInfraService(container);
    }
}
