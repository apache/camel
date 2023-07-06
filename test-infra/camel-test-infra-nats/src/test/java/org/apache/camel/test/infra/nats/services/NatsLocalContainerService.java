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
package org.apache.camel.test.infra.nats.services;

import org.apache.camel.test.infra.common.services.ContainerService;
import org.apache.camel.test.infra.nats.common.NatsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

public class NatsLocalContainerService implements NatsService, ContainerService<GenericContainer> {
    public static final String CONTAINER_IMAGE = "nats:2.9.19";
    public static final String CONTAINER_NAME = "nats";
    private static final int PORT = 4222;

    private static final Logger LOG = LoggerFactory.getLogger(NatsLocalContainerService.class);
    private final GenericContainer container;

    public NatsLocalContainerService() {
        this(System.getProperty(NatsProperties.NATS_CONTAINER, CONTAINER_IMAGE));
    }

    public NatsLocalContainerService(String imageName) {
        container = initContainer(imageName, CONTAINER_NAME);
    }

    protected GenericContainer initContainer(String imageName, String containerName) {
        return new GenericContainer(imageName)
                .withNetworkAliases(containerName)
                .withExposedPorts(PORT)
                .waitingFor(Wait.forLogMessage(".*Listening.*for.*route.*connections.*", 1));
    }

    @Override
    public void registerProperties() {
        System.setProperty(NatsProperties.SERVICE_ADDRESS, getServiceAddress());
    }

    @Override
    public void initialize() {
        LOG.info("Trying to start the Nats container");
        container.start();

        registerProperties();
        LOG.info("Nats instance running at {}", getServiceAddress());
    }

    @Override
    public void shutdown() {
        LOG.info("Stopping the Nats container");
        container.stop();
    }

    @Override
    public GenericContainer getContainer() {
        return container;
    }

    protected String getHost() {
        return container.getHost();
    }

    protected int getPort() {
        return container.getMappedPort(PORT);
    }

    @Override
    public String getServiceAddress() {
        return String.format("%s:%d", getHost(), getPort());
    }
}
