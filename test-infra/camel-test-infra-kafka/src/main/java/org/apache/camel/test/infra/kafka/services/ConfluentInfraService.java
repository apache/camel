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
import org.apache.camel.test.infra.common.TestUtils;
import org.apache.camel.test.infra.common.services.ContainerService;
import org.apache.camel.test.infra.kafka.common.KafkaProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Network;

@InfraService(service = KafkaInfraService.class,
              description = "Apache Kafka, Distributed event streaming platform",
              serviceAlias = "kafka", serviceImplementationAlias = "confluent")
public class ConfluentInfraService implements KafkaInfraService, ContainerService<ConfluentContainer> {
    private static final Logger LOG = LoggerFactory.getLogger(ConfluentInfraService.class);

    private final ConfluentContainer confluentContainer;

    public ConfluentInfraService() {
        this("confluent-" + TestUtils.randomWithRange(1, 100));
    }

    public ConfluentInfraService(String confluentInstanceName) {
        Network network = Network.newNetwork();
        confluentContainer = initConfluentContainer(network, confluentInstanceName);
    }

    public ConfluentInfraService(ConfluentContainer confluentContainer) {
        this.confluentContainer = confluentContainer;
    }

    protected ConfluentContainer initConfluentContainer(Network network, String instanceName) {
        return new ConfluentContainer(network, instanceName);
    }

    protected Integer getKafkaPort() {
        return confluentContainer.getKafkaPort();
    }

    @Override
    public String getBootstrapServers() {
        return confluentContainer.getHost() + ":" + getKafkaPort();
    }

    @Override
    public void registerProperties() {
        System.setProperty(KafkaProperties.KAFKA_BOOTSTRAP_SERVERS, getBootstrapServers());
    }

    @Override
    public void initialize() {
        confluentContainer.start();

        registerProperties();
        LOG.info("Kafka bootstrap server running at address {}", getBootstrapServers());
    }

    private boolean stopped() {
        return !confluentContainer.isRunning();
    }

    @Override
    public void shutdown() {
        try {
            LOG.info("Stopping Kafka container");
            confluentContainer.stop();
        } finally {
            TestUtils.waitFor(this::stopped);
        }
    }

    @Override
    public ConfluentContainer getContainer() {
        return confluentContainer;
    }
}
