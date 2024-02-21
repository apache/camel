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

import org.apache.camel.test.infra.common.TestUtils;
import org.apache.camel.test.infra.common.services.ContainerService;
import org.apache.camel.test.infra.kafka.common.KafkaProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Network;
import org.testcontainers.redpanda.RedpandaContainer;

public class RedpandaService implements KafkaService, ContainerService<RedpandaContainer> {
    private static final Logger LOG = LoggerFactory.getLogger(RedpandaService.class);

    private final RedpandaContainer redpandaContainer;

    public RedpandaService() {
        this("redpanda-" + TestUtils.randomWithRange(1, 100));
    }

    public RedpandaService(String redpandaInstanceName) {
        Network network = Network.newNetwork();

        redpandaContainer = initRedpandaContainer(network, redpandaInstanceName);
    }

    public RedpandaService(RedpandaContainer redpandaContainer) {
        this.redpandaContainer = redpandaContainer;
    }

    protected RedpandaContainer initRedpandaContainer(Network network, String instanceName) {
        return new RedpandaTransactionsEnabledContainer(RedpandaTransactionsEnabledContainer.REDPANDA_CONTAINER);
    }

    protected Integer getKafkaPort() {
        return redpandaContainer.getMappedPort(RedpandaTransactionsEnabledContainer.REDPANDA_PORT);
    }

    @Override
    public String getBootstrapServers() {
        return redpandaContainer.getHost() + ":" + getKafkaPort();
    }

    @Override
    public void registerProperties() {
        System.setProperty(KafkaProperties.KAFKA_BOOTSTRAP_SERVERS, getBootstrapServers());
    }

    @Override
    public void initialize() {
        redpandaContainer.start();

        registerProperties();
        LOG.info("Redpanda bootstrap server running at address {}", getBootstrapServers());
    }

    private boolean stopped() {
        return !redpandaContainer.isRunning();
    }

    @Override
    public void shutdown() {
        LOG.info("Stopping Redpanda container");
        redpandaContainer.stop();
    }

    @Override
    public RedpandaContainer getContainer() {
        return redpandaContainer;
    }
}
