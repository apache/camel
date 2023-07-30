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

import com.github.dockerjava.api.command.CreateContainerCmd;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;

public class StrimziContainer extends GenericContainer<StrimziContainer> {
    public static final String DEFAULT_STRIMZI_CONTAINER = "quay.io/strimzi/kafka:latest-kafka-3.5.1";
    static final String STRIMZI_CONTAINER
            = System.getProperty("itest.strimzi.container.image", DEFAULT_STRIMZI_CONTAINER);
    private static final int KAFKA_PORT = 9092;

    public StrimziContainer(Network network, String name, String zookeeperInstanceName) {
        this(network, name, STRIMZI_CONTAINER, zookeeperInstanceName);
    }

    public StrimziContainer(Network network, String name, String containerName, String zookeeperInstanceName) {
        super(containerName);

        withEnv("LOG_DIR", "/tmp/logs");
        withExposedPorts(KAFKA_PORT);
        withEnv("KAFKA_ADVERTISED_LISTENERS", String.format("PLAINTEXT://%s:9092", getHost()));
        withEnv("KAFKA_LISTENERS", "PLAINTEXT://0.0.0.0:9092");
        withEnv("KAFKA_ZOOKEEPER_CONNECT", zookeeperInstanceName + ":2181");
        withNetwork(network);

        withCreateContainerCmdModifier(createContainerCmd -> setupContainer(name, createContainerCmd));

        withCommand("sh", "-c",
                "bin/kafka-server-start.sh config/server.properties "
                                + "--override listeners=${KAFKA_LISTENERS} "
                                + "--override advertised.listeners=${KAFKA_ADVERTISED_LISTENERS} "
                                + "--override zookeeper.connect=${KAFKA_ZOOKEEPER_CONNECT}");

        waitingFor(Wait.forListeningPort());
    }

    private void setupContainer(String name, CreateContainerCmd createContainerCmd) {
        createContainerCmd.withHostName(name);
        createContainerCmd.withName(name);
    }

    public int getKafkaPort() {
        return getMappedPort(KAFKA_PORT);
    }

    @Override
    public void start() {
        addFixedExposedPort(KAFKA_PORT, KAFKA_PORT);
        super.start();
    }
}
