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
package org.apache.camel.test.infra.consul.services;

import org.apache.camel.test.infra.common.LocalPropertyResolver;
import org.apache.camel.test.infra.common.services.ContainerService;
import org.apache.camel.test.infra.consul.common.ConsulProperties;
import org.kiwiproject.consul.Consul;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

public class ConsulLocalContainerService implements ConsulService, ContainerService<GenericContainer> {
    public static final String CONTAINER_NAME = "consul";

    private static final Logger LOG = LoggerFactory.getLogger(ConsulLocalContainerService.class);

    private final GenericContainer container;

    public ConsulLocalContainerService() {
        this(LocalPropertyResolver.getProperty(
                ConsulLocalContainerService.class,
                ConsulProperties.CONSUL_CONTAINER));
    }

    public ConsulLocalContainerService(String containerName) {
        container = initContainer(containerName, CONTAINER_NAME);
    }

    public ConsulLocalContainerService(GenericContainer container) {
        this.container = container;
    }

    protected GenericContainer initContainer(String imageName, String containerName) {
        return new GenericContainer(imageName)
                .withNetworkAliases(containerName)
                .withExposedPorts(Consul.DEFAULT_HTTP_PORT)
                .waitingFor(Wait.forLogMessage(".*Synced node info.*", 1))
                .withCommand("agent", "-dev", "-server", "-bootstrap", "-client", "0.0.0.0", "-log-level", "trace");
    }

    @Override
    public void registerProperties() {
        System.setProperty(ConsulProperties.CONSUL_URL, getConsulUrl());
        System.setProperty(ConsulProperties.CONSUL_HOST, host());
        System.setProperty(ConsulProperties.CONSUL_PORT, String.valueOf(port()));
    }

    @Override
    public void initialize() {
        LOG.info("Trying to start the Consul container");
        container.start();

        registerProperties();
        LOG.info("Consul instance running at {}", getConsulUrl());
    }

    @Override
    public void shutdown() {
        LOG.info("Stopping the Consul container");
        container.stop();
    }

    @Override
    public GenericContainer getContainer() {
        return container;
    }

    @Override
    public String getConsulUrl() {
        return String.format("http://%s:%d", container.getHost(), container.getMappedPort(Consul.DEFAULT_HTTP_PORT));
    }

    @Override
    public String host() {
        return container.getHost();
    }

    @Override
    public int port() {
        return container.getMappedPort(Consul.DEFAULT_HTTP_PORT);
    }
}
