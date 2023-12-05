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
package org.apache.camel.test.infra.rocketmq.services;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.apache.camel.test.infra.common.LocalPropertyResolver;
import org.apache.camel.test.infra.common.services.ContainerService;
import org.apache.camel.test.infra.rocketmq.common.RocketMQProperties;
import org.awaitility.Awaitility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.Network;

public class RocketMQContainer implements RocketMQService, ContainerService<RocketMQNameserverContainer> {
    private static final Logger LOG = LoggerFactory.getLogger(RocketMQContainer.class);
    public static final String ROCKETMQ_VERSION = LocalPropertyResolver.getProperty(
            RocketMQContainer.class,
            RocketMQProperties.ROCKETMQ_VERSION_PROPERTY);
    public static final String ROCKETMQ_IMAGE = LocalPropertyResolver.getProperty(
            RocketMQContainer.class,
            RocketMQProperties.ROCKETMQ_IMAGE_PROPERTY) + ":" + ROCKETMQ_VERSION;

    private final RocketMQNameserverContainer nameserverContainer;
    private final RocketMQBrokerContainer brokerContainer1;

    public RocketMQContainer() {
        Network network = Network.newNetwork();

        nameserverContainer = new RocketMQNameserverContainer(network);

        brokerContainer1 = new RocketMQBrokerContainer(network, "broker1");
    }

    @Override
    public RocketMQNameserverContainer getContainer() {
        return nameserverContainer;
    }

    @Override
    public void registerProperties() {

    }

    @Override
    public void initialize() {
        nameserverContainer.start();
        LOG.info("Apache RocketMQ running at address {}", nameserverAddress());

        brokerContainer1.start();
    }

    @Override
    public void shutdown() {
        nameserverContainer.stop();
        brokerContainer1.stop();
    }

    public void createTopic(String topic) {
        Awaitility.await()
                .atMost(20, TimeUnit.SECONDS)
                .pollDelay(100, TimeUnit.MILLISECONDS).until(() -> {
                    Container.ExecResult execResult = brokerContainer1.execInContainer(
                            "sh", "mqadmin", "updateTopic", "-n", "nameserver:9876", "-t",
                            topic, "-c", "DefaultCluster");

                    LOG.info("Exit code: {}. Stderr: {} Stdout: {} ", execResult.getExitCode(), execResult.getStderr(),
                            execResult.getStdout());

                    return execResult.getStdout() != null && execResult.getStdout().contains("success");
                });
    }

    public void deleteTopic(String topic) throws IOException, InterruptedException {
        brokerContainer1.execInContainer(
                "sh", "mqadmin", "deleteTopic", "-n", "nameserver:9876", "-t",
                topic);
    }

    @Override
    public String nameserverAddress() {
        return nameserverContainer.getHost() + ":"
               + nameserverContainer.getMappedPort(RocketMQProperties.ROCKETMQ_NAMESRV_PORT);
    }
}
