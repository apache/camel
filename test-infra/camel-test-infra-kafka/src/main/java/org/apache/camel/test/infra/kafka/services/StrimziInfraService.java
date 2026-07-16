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
import org.apache.camel.test.infra.common.services.ContainerEnvironmentUtil;
import org.apache.camel.test.infra.common.services.ContainerService;
import org.apache.camel.test.infra.kafka.common.KafkaProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Network;

@InfraService(service = KafkaInfraService.class,
              description = "Apache Kafka, Distributed event streaming platform",
              serviceAlias = "kafka", serviceImplementationAlias = "strimzi")
public class StrimziInfraService implements KafkaInfraService, ContainerService<StrimziContainer> {
    private static final Logger LOG = LoggerFactory.getLogger(StrimziInfraService.class);

    private final StrimziContainer strimziContainer;

    public StrimziInfraService() {
        this("strimzi-" + TestUtils.randomWithRange(1, 100));
    }

    public StrimziInfraService(String strimziInstanceName) {
        Network network = Network.newNetwork();

        strimziContainer = initStrimziContainer(network, strimziInstanceName);
        String name = ContainerEnvironmentUtil.containerName(this.getClass());
        if (name != null) {
            strimziContainer.withCreateContainerCmdModifier(cmd -> cmd.withName(name));
        }
    }

    public StrimziInfraService(StrimziContainer strimziContainer) {
        this.strimziContainer = strimziContainer;
    }

    protected StrimziContainer initStrimziContainer(Network network, String instanceName) {
        class TestInfraStrimziContainer extends StrimziContainer {
            public TestInfraStrimziContainer(Network network, String name, boolean fixedPort) {
                super(network, name);

                ContainerEnvironmentUtil.configurePort(this, fixedPort, 9092);
            }
        }

        return new TestInfraStrimziContainer(
                network, instanceName, ContainerEnvironmentUtil.isFixedPort(this.getClass()));
    }

    protected Integer getKafkaPort() {
        return strimziContainer.getKafkaPort();
    }

    @Override
    public String getBootstrapServers() {
        return strimziContainer.getHost() + ":" + getKafkaPort();
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
        strimziContainer.start();

        registerProperties();
        LOG.info("Kafka bootstrap server running at address {}", getBootstrapServers());
    }

    private boolean stopped() {
        return !strimziContainer.isRunning();
    }

    @Override
    public void shutdown() {
        try {
            LOG.info("Stopping Kafka container");
            strimziContainer.stop();
        } finally {
            TestUtils.waitFor(this::stopped);
        }
    }

    @Override
    public StrimziContainer getContainer() {
        return strimziContainer;
    }
}
