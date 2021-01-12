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
package org.apache.camel.test.infra.infinispan.services;

import org.apache.camel.test.infra.common.services.ContainerService;
import org.apache.camel.test.infra.infinispan.common.InfinispanProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

public class InfinispanLocalContainerService implements InfinispanService, ContainerService<GenericContainer> {
    public static final String CONTAINER_IMAGE = "infinispan/server:11.0.7.Final-1";
    public static final String CONTAINER_NAME = "infinispan";
    private static final String DEFAULT_USERNAME = "admin";
    private static final String DEFAULT_PASSWORD = "password";

    private static final Logger LOG = LoggerFactory.getLogger(InfinispanLocalContainerService.class);

    private GenericContainer container;

    public InfinispanLocalContainerService() {
        String containerName = System.getProperty("infinispan.container", CONTAINER_IMAGE);

        initContainer(containerName);
    }

    public InfinispanLocalContainerService(String containerName) {
        initContainer(containerName);
    }

    protected void initContainer(String containerName) {
        container = new GenericContainer(containerName)
                .withNetworkAliases(CONTAINER_NAME)
                .withEnv("USER", DEFAULT_USERNAME)
                .withEnv("PASS", DEFAULT_PASSWORD)
                .withExposedPorts(InfinispanProperties.DEFAULT_SERVICE_PORT)
                .waitingFor(Wait.forListeningPort())
                .waitingFor(Wait.forLogMessage(".*Infinispan.*Server.*started.*", 1));
    }

    @Override
    public void registerProperties() {
        System.setProperty(InfinispanProperties.SERVICE_HOST, host());
        System.setProperty(InfinispanProperties.SERVICE_PORT, String.valueOf(port()));
        System.setProperty(InfinispanProperties.SERVICE_ADDRESS, getServiceAddress());
    }

    @Override
    public void initialize() {
        LOG.info("Trying to start the Infinispan container");
        container.start();

        registerProperties();
        LOG.info("Infinispan instance running at {}", getServiceAddress());
    }

    @Override
    public void shutdown() {
        LOG.info("Stopping the Infinispan container");
        container.stop();
    }

    @Override
    public GenericContainer getContainer() {
        return container;
    }

    @Override
    public String getServiceAddress() {
        return String.format("%s:%d", host(), port());
    }

    @Override
    public int port() {
        return container.getMappedPort(InfinispanProperties.DEFAULT_SERVICE_PORT);
    }

    @Override
    public String host() {
        return container.getHost();
    }

    @Override
    public String username() {
        return DEFAULT_USERNAME;
    }

    @Override
    public String password() {
        return DEFAULT_PASSWORD;
    }
}
