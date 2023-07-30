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

public class ZookeeperContainer extends GenericContainer<ZookeeperContainer> {
    private static final String ZOOKEEPER_CONTAINER = StrimziContainer.STRIMZI_CONTAINER;
    private static final int ZOOKEEPER_PORT = 2181;

    public ZookeeperContainer(Network network, String name) {
        this(network, name, ZOOKEEPER_CONTAINER);
    }

    public ZookeeperContainer(Network network, String name, String containerName) {
        super(containerName);

        withEnv("LOG_DIR", "/tmp/logs");
        withExposedPorts(ZOOKEEPER_PORT);
        withNetwork(network);

        withCreateContainerCmdModifier(createContainerCmd -> setupContainer(name, createContainerCmd));

        withCommand("sh", "-c",
                "bin/zookeeper-server-start.sh config/zookeeper.properties");

        waitingFor(Wait.forListeningPort());
    }

    private void setupContainer(String name, CreateContainerCmd createContainerCmd) {
        createContainerCmd.withHostName(name);
        createContainerCmd.withName(name);
    }

    public int getZookeeperPort() {
        return getMappedPort(ZOOKEEPER_PORT);
    }

}
