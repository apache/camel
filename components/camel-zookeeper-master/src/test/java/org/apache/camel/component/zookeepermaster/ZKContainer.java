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
package org.apache.camel.component.zookeepermaster;

import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;

public class ZKContainer extends GenericContainer  {
    public static final String CONTAINER_IMAGE = "zookeeper:3.5";
    public static final String CONTAINER_NAME = "zookeeper";
    public static final int CLIENT_PORT = 2181;

    public ZKContainer() {
        this(CONTAINER_NAME, -1);
    }

    public ZKContainer(int clientPort) {
        this(CONTAINER_NAME, clientPort);
    }

    public ZKContainer(String name) {
        this(name, -1);

        setWaitStrategy(Wait.forListeningPort());

        withNetworkAliases(name);
        withExposedPorts(CLIENT_PORT);
        withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger(ZKContainer.class)));
    }

    public ZKContainer(String name, int clientPort) {
        super(CONTAINER_IMAGE);

        setWaitStrategy(Wait.forListeningPort());

        withNetworkAliases(name);
        withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger(ZKContainer.class)));

        if (clientPort > 0) {
            addFixedExposedPort(clientPort, CLIENT_PORT);
        } else {
            withExposedPorts(CLIENT_PORT);
        }
    }

    public String getConnectionString() {
        return getContainerIpAddress() + ":" + getMappedPort(CLIENT_PORT);
    }
}
