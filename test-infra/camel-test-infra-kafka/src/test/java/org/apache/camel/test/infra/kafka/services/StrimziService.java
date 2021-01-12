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

    private static ZookeeperContainer zookeeperContainer;
    private static StrimziContainer strimziContainer;
    private static String zookeeperInstanceName;
    private static String strimziInstanceName;

    public StrimziService() {

        if (zookeeperContainer == null && strimziContainer == null) {

            Network network = Network.newNetwork();

            if (zookeeperContainer == null) {
                zookeeperInstanceName = "zookeeper-" + TestUtils.randomWithRange(1, 100);
                zookeeperContainer = new ZookeeperContainer(network, zookeeperInstanceName);
            }

            if (strimziContainer == null) {
                strimziInstanceName = "strimzi-" + TestUtils.randomWithRange(1, 100);
                strimziContainer = new StrimziContainer(network, strimziInstanceName, zookeeperInstanceName);
            }
        }
    }

    private Integer getKafkaPort() {
        return strimziContainer.getKafkaPort();
    }

    @Override
    public String getBootstrapServers() {
        return strimziContainer.getContainerIpAddress() + ":" + getKafkaPort();
    }

    @Override
    public void registerProperties() {
        System.setProperty(KafkaProperties.KAFKA_BOOTSTRAP_SERVERS, getBootstrapServers());
    }

    @Override
    public void initialize() {
        if (!zookeeperContainer.isRunning()) {
            /*
             When running multiple tests at once, this throttles the startup to give
             time for docker to fully shutdown previously running instances (which
             happens asynchronously). This prevents problems with false positive errors
             such as docker complaining of multiple containers with the same name or
             trying to reuse port numbers too quickly.
             */
            throttle();
            zookeeperContainer.start();
        }

        String zookeeperConnect = zookeeperInstanceName + ":" + zookeeperContainer.getZookeeperPort();
        LOG.info("Apache Zookeeper running at address {}", zookeeperConnect);

        if (!strimziContainer.isRunning()) {
            strimziContainer.start();
        }

        registerProperties();
        LOG.info("Kafka bootstrap server running at address {}", getBootstrapServers());
    }

    private void throttle() {
        try {
            String throttleDelay = System.getProperty("itest.strimzi.throttle.delay", "10000");
            Thread.sleep(Integer.parseInt(throttleDelay));
        } catch (InterruptedException e) {
            LOG.warn("Strimzi startup interrupted");
        }
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
}
