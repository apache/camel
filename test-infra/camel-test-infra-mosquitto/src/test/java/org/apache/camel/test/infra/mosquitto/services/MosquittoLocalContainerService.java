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
package org.apache.camel.test.infra.mosquitto.services;

import org.apache.camel.test.infra.common.services.ContainerService;
import org.apache.camel.test.infra.mosquitto.common.MosquittoProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.FixedHostPortGenericContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

public class MosquittoLocalContainerService implements MosquittoService, ContainerService<GenericContainer> {
    // mosquitto 2.x needs extra config for remote connections
    public static final String CONTAINER_IMAGE = "eclipse-mosquitto:1.6.12";
    public static final String CONTAINER_NAME = "mosquitto";
    public static final int CONTAINER_PORT = 1883;

    private static final Logger LOG = LoggerFactory.getLogger(MosquittoLocalContainerService.class);

    private GenericContainer container;

    public MosquittoLocalContainerService() {
        String containerName = System.getProperty("mosquitto.container", CONTAINER_IMAGE);

        initContainer(containerName, null);
    }

    public MosquittoLocalContainerService(int port) {
        String containerName = System.getProperty("mosquitto.container", CONTAINER_IMAGE);

        initContainer(containerName, port);
    }

    public MosquittoLocalContainerService(String containerName) {
        initContainer(containerName, null);
    }

    protected void initContainer(String containerName, Integer port) {
        if (port == null) {
            container = new GenericContainer(containerName)
                    .withExposedPorts(CONTAINER_PORT);
        } else {
            @SuppressWarnings("deprecation")
            GenericContainer fixedPortContainer = new FixedHostPortGenericContainer(containerName)
                    .withFixedExposedPort(port, CONTAINER_PORT);
            container = fixedPortContainer;
        }
        container.withNetworkAliases(CONTAINER_NAME)
                .waitingFor(Wait.forLogMessage(".* mosquitto version .* running", 1))
                .waitingFor(Wait.forListeningPort());
    }

    @Override
    public void registerProperties() {
        System.setProperty(MosquittoProperties.PORT, String.valueOf(getPort()));
    }

    @Override
    public void initialize() {
        LOG.info("Trying to start the Mosquitto container");
        container.start();

        registerProperties();
        LOG.info("Mosquitto instance running at {}", getPort());
    }

    @Override
    public void shutdown() {
        LOG.info("Stopping the Mosquitto container");
        container.stop();
    }

    @Override
    public GenericContainer getContainer() {
        return container;
    }

    @Override
    public Integer getPort() {
        return container.getMappedPort(CONTAINER_PORT);
    }
}
