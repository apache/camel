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

public class StrimziService implements KafkaService, ContainerService<StrimziContainer> {
    private static final Logger LOG = LoggerFactory.getLogger(StrimziService.class);

    private final ZookeeperContainer zookeeperContainer;
    private final StrimziContainer strimziContainer;

    public StrimziService() {
        this("zookeeper-" + TestUtils.randomWithRange(1, 100),
             "strimzi-" + TestUtils.randomWithRange(1, 100));
    }

    public StrimziService(String zookeeperInstanceName, String strimziInstanceName) {
        Network network = Network.newNetwork();

        zookeeperContainer = initZookeeperContainer(network, zookeeperInstanceName);
        strimziContainer = initStrimziContainer(network, strimziInstanceName, zookeeperInstanceName);
    }

    public StrimziService(ZookeeperContainer zookeeperContainer, StrimziContainer strimziContainer) {
        this.zookeeperContainer = zookeeperContainer;
        this.strimziContainer = strimziContainer;
    }

    protected StrimziContainer initStrimziContainer(Network network, String instanceName, String zookeeperInstanceName) {
        return new StrimziContainer(network, instanceName, zookeeperInstanceName);
    }

    protected ZookeeperContainer initZookeeperContainer(Network network, String instanceName) {
        return new ZookeeperContainer(network, instanceName);
    }

    protected Integer getKafkaPort() {
        return strimziContainer.getKafkaPort();
    }

    @Override
    public String getBootstrapServers() {
        return strimziContainer.getHost() + ":" + getKafkaPort();
    }

    @Override
    public void registerProperties() {
        System.setProperty(KafkaProperties.KAFKA_BOOTSTRAP_SERVERS, getBootstrapServers());
    }

    @Override
    public void initialize() {
        zookeeperContainer.start();

        String zookeeperConnect = zookeeperContainer.getHost() + ":" + zookeeperContainer.getZookeeperPort();
        LOG.info("Apache Zookeeper running at address {}", zookeeperConnect);

        strimziContainer.start();

        registerProperties();
        LOG.info("Kafka bootstrap server running at address {}", getBootstrapServers());
    }

    private boolean stopped() {
        return !strimziContainer.isRunning() && !zookeeperContainer.isRunning();
    }

    @Override
    public void shutdown() {
        try {
            LOG.info("Stopping Kafka container");
            strimziContainer.stop();
        } finally {
            LOG.info("Stopping Zookeeper container");
            zookeeperContainer.stop();

            TestUtils.waitFor(this::stopped);
        }
    }

    @Override
    public StrimziContainer getContainer() {
        return strimziContainer;
    }

    protected ZookeeperContainer getZookeeperContainer() {
        return zookeeperContainer;
    }
}
