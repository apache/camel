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
import org.apache.camel.test.infra.kafka.common.KafkaProperties;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;

public class ConfluentContainer extends GenericContainer<ConfluentContainer> {
    static final String CONFLUENT_CONTAINER = LocalPropertyResolver.getProperty(
            ConfluentContainer.class,
            KafkaProperties.CONFLUENT_CONTAINER);
    private static final int KAFKA_PORT = 9092;

    public ConfluentContainer(Network network, String name) {
        this(network, name, CONFLUENT_CONTAINER);
    }

    public ConfluentContainer(Network network, String name, String containerName) {
        super(containerName);

        withEnv("LOG_DIR", "/tmp/logs")
                .withExposedPorts(KAFKA_PORT)
                .withEnv("KAFKA_BROKER_ID", "1")
                .withEnv("KAFKA_LISTENER_SECURITY_PROTOCOL_MAP",
                        "BROKER:PLAINTEXT,PLAINTEXT:PLAINTEXT,CONTROLLER:PLAINTEXT")
                .withEnv("KAFKA_ADVERTISED_LISTENERS",
                        String.format("PLAINTEXT://%s:9092,BROKER://%s:9093", getHost(), getHost()))
                .withEnv("KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS", "0")
                .withEnv("KAFKA_TRANSACTION_STATE_LOG_MIN_ISR", "1")
                .withEnv("KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR", "1")
                .withEnv("KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR", "1")
                .withEnv("KAFKA_PROCESS_ROLES", "broker,controller")
                .withEnv("KAFKA_NODE_ID", "1")
                .withEnv("KAFKA_CONTROLLER_QUORUM_VOTERS", "1@0.0.0.0:9094")
                .withEnv("KAFKA_LISTENERS",
                        "PLAINTEXT://0.0.0.0:9092,BROKER://0.0.0.0:9093,CONTROLLER://0.0.0.0:9094")
                .withEnv("KAFKA_INTER_BROKER_LISTENER_NAME", "BROKER")
                .withEnv("KAFKA_CONTROLLER_LISTENER_NAMES", "CONTROLLER")
                .withEnv("KAFKA_LOG_DIRS", "/tmp/kraft-combined-logs")
                .withEnv("KAFKA_REST_HOST_NAME", "rest-proxy")
                .withEnv("KAFKA_REST_LISTENERS", String.format("http://%s:9092", getHost()))
                .withEnv("KAFKA_REST_BOOTSTRAP_SERVERS", "localhost:9092")
                .withEnv("PATH", "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin")
                .withEnv("container", "oci")
                .withEnv("LANG", "C.UTF-8")
                .withEnv("UB_CLASSPATH", "/usr/share/java/cp-base-lite/*")
                .withEnv("KAFKA_ZOOKEEPER_CONNECT", "")
                .withEnv("CLUSTER_ID", UUID.randomUUID().toString().replace("-", "").substring(0, 22))
                .withNetwork(network)
                .withCreateContainerCmdModifier(createContainerCmd -> setupContainer(name, createContainerCmd))
                .withCommand("sh", "-c",
                        "/etc/confluent/docker/run")
                .waitingFor(Wait.forLogMessage(".*Kafka Server started.*", 1));
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
