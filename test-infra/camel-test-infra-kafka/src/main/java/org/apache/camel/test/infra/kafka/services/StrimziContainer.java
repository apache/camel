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

import java.util.UUID;

import com.github.dockerjava.api.command.CreateContainerCmd;
import org.apache.camel.test.infra.common.LocalPropertyResolver;
import org.apache.camel.test.infra.common.services.ContainerEnvironmentUtil;
import org.apache.camel.test.infra.kafka.common.KafkaProperties;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;

public class StrimziContainer extends GenericContainer<StrimziContainer> {
    static final String STRIMZI_CONTAINER = LocalPropertyResolver.getProperty(
            StrimziContainer.class,
            KafkaProperties.STRIMZI_CONTAINER);
    private static final int KAFKA_PORT = 9092;

    public StrimziContainer(Network network, String name) {
        this(network, name, STRIMZI_CONTAINER);
    }

    public StrimziContainer(Network network, String name, String containerName) {
        super(containerName);

        String clusterId = UUID.randomUUID().toString().replace("-", "").substring(0, 22);

        withEnv("LOG_DIR", "/tmp/logs")
                .withExposedPorts(KAFKA_PORT)
                .withEnv("KAFKA_NODE_ID", "1")
                .withEnv("KAFKA_PROCESS_ROLES", "broker,controller")
                .withEnv("KAFKA_LISTENERS", "PLAINTEXT://0.0.0.0:9092,CONTROLLER://0.0.0.0:9093")
                .withEnv("KAFKA_ADVERTISED_LISTENERS", String.format("PLAINTEXT://%s:9092", getHost()))
                .withEnv("KAFKA_CONTROLLER_LISTENER_NAMES", "CONTROLLER")
                .withEnv("KAFKA_LISTENER_SECURITY_PROTOCOL_MAP", "PLAINTEXT:PLAINTEXT,CONTROLLER:PLAINTEXT")
                .withEnv("KAFKA_CONTROLLER_QUORUM_VOTERS", "1@localhost:9093")
                .withEnv("KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR", "1")
                .withEnv("KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR", "1")
                .withEnv("KAFKA_TRANSACTION_STATE_LOG_MIN_ISR", "1")
                .withEnv("KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS", "0")
                .withNetwork(network)
                .withCreateContainerCmdModifier(createContainerCmd -> setupContainer(name, createContainerCmd))
                .withCommand("sh", "-c",
                        "bin/kafka-storage.sh format -t " + clusterId + " -c config/kraft/server.properties && "
                                         + "bin/kafka-server-start.sh config/kraft/server.properties "
                                         + "--override listeners=${KAFKA_LISTENERS} "
                                         + "--override advertised.listeners=${KAFKA_ADVERTISED_LISTENERS} "
                                         + "--override listener.security.protocol.map=${KAFKA_LISTENER_SECURITY_PROTOCOL_MAP} "
                                         + "--override controller.listener.names=${KAFKA_CONTROLLER_LISTENER_NAMES} "
                                         + "--override controller.quorum.voters=${KAFKA_CONTROLLER_QUORUM_VOTERS} "
                                         + "--override node.id=${KAFKA_NODE_ID} "
                                         + "--override process.roles=${KAFKA_PROCESS_ROLES} "
                                         + "--override offsets.topic.replication.factor=${KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR} "
                                         + "--override transaction.state.log.replication.factor=${KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR} "
                                         + "--override transaction.state.log.min.isr=${KAFKA_TRANSACTION_STATE_LOG_MIN_ISR} "
                                         + "--override group.initial.rebalance.delay.ms=${KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS}")
                .waitingFor(Wait.forListeningPort());
    }

    private void setupContainer(String name, CreateContainerCmd createContainerCmd) {
        createContainerCmd.withHostName(name);
    }

    public int getKafkaPort() {
        return getMappedPort(KAFKA_PORT);
    }

    @Override
    public void start() {
        ContainerEnvironmentUtil.configurePort(this, true, KAFKA_PORT);
        super.start();
    }
}
