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

import org.apache.camel.test.infra.common.LocalPropertyResolver;
import org.apache.camel.test.infra.common.services.ContainerService;
import org.apache.camel.test.infra.mosquitto.common.MosquittoProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.FixedHostPortGenericContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

public class MosquittoLocalContainerService implements MosquittoService, ContainerService<GenericContainer> {
    public static final String CONTAINER_NAME = "mosquitto";
    public static final int CONTAINER_PORT = 1883;

    private static final Logger LOG = LoggerFactory.getLogger(MosquittoLocalContainerService.class);

    private final GenericContainer container;

    public MosquittoLocalContainerService() {
        this(LocalPropertyResolver.getProperty(MosquittoLocalContainerService.class, MosquittoProperties.MOSQUITTO_CONTAINER));
    }

    public MosquittoLocalContainerService(int port) {
        String imageName = LocalPropertyResolver.getProperty(
                MosquittoLocalContainerService.class,
                MosquittoProperties.MOSQUITTO_CONTAINER);

        container = initContainer(imageName, port);
    }

    public MosquittoLocalContainerService(String imageName) {
        container = initContainer(imageName, null);
    }

    public MosquittoLocalContainerService(GenericContainer container) {
        this.container = container;
    }

    protected GenericContainer initContainer(String imageName, Integer port) {
        GenericContainer ret;

        if (port == null) {
            ret = new GenericContainer(imageName)
                    .withExposedPorts(CONTAINER_PORT);
        } else {
            @SuppressWarnings("deprecation")
            GenericContainer fixedPortContainer = new FixedHostPortGenericContainer(imageName)
                    .withFixedExposedPort(port, CONTAINER_PORT);
            ret = fixedPortContainer;
        }

        ret.withNetworkAliases(CONTAINER_NAME)
                .withClasspathResourceMapping("mosquitto.conf", "/mosquitto/config/mosquitto.conf", BindMode.READ_ONLY)
                .waitingFor(Wait.forLogMessage(".* mosquitto version .* running", 1))
                .waitingFor(Wait.forListeningPort());

        return ret;
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
