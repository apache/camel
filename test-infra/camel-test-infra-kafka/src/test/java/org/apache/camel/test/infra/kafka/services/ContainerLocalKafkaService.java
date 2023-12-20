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

import org.apache.camel.test.infra.common.LocalPropertyResolver;
import org.apache.camel.test.infra.common.services.ContainerService;
import org.apache.camel.test.infra.kafka.common.KafkaProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

public class ContainerLocalKafkaService implements KafkaService, ContainerService<KafkaContainer> {
    public static final String KAFKA3_IMAGE_NAME = LocalPropertyResolver.getProperty(
            ContainerLocalKafkaService.class,
            KafkaProperties.KAFKA3_CONTAINER);
    public static final String KAFKA2_IMAGE_NAME = LocalPropertyResolver.getProperty(
            ContainerLocalKafkaService.class,
            KafkaProperties.KAFKA2_CONTAINER);

    private static final Logger LOG = LoggerFactory.getLogger(ContainerLocalKafkaService.class);
    private final KafkaContainer kafka;

    public ContainerLocalKafkaService() {
        kafka = initContainer();
    }

    public ContainerLocalKafkaService(KafkaContainer kafka) {
        this.kafka = kafka;
    }

    protected KafkaContainer initContainer() {
        return new KafkaContainer(DockerImageName.parse(System.getProperty(KafkaProperties.KAFKA_CONTAINER, KAFKA3_IMAGE_NAME)))
                .withEmbeddedZookeeper();
    }

    public String getBootstrapServers() {
        return kafka.getBootstrapServers();
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
        kafka.stop();
    }

    @Override
    public KafkaContainer getContainer() {
        return kafka;
    }

    public static ContainerLocalKafkaService kafka2Container() {
        KafkaContainer container
                = new KafkaContainer(
                        DockerImageName.parse(System.getProperty(KafkaProperties.KAFKA_CONTAINER, KAFKA2_IMAGE_NAME))
                                .asCompatibleSubstituteFor(ContainerLocalKafkaService.KAFKA2_IMAGE_NAME));
        container = container.withEmbeddedZookeeper();

        return new ContainerLocalKafkaService(container);
    }

    public static ContainerLocalKafkaService kafka3Container() {
        KafkaContainer container
                = new KafkaContainer(
                        DockerImageName.parse(System.getProperty(KafkaProperties.KAFKA_CONTAINER, KAFKA3_IMAGE_NAME))
                                .asCompatibleSubstituteFor(ContainerLocalKafkaService.KAFKA3_IMAGE_NAME));
        container = container.withEmbeddedZookeeper();

        return new ContainerLocalKafkaService(container);
    }
}
