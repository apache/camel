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

import java.util.function.Consumer;

import org.apache.camel.test.infra.common.services.ContainerService;
import org.apache.camel.test.infra.infinispan.common.InfinispanProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;

public class InfinispanLocalContainerService implements InfinispanService, ContainerService<GenericContainer<?>> {
    public static final String CONTAINER_IMAGE = "quay.io/infinispan/server:14.0.14.Final";
    public static final String CONTAINER_NAME = "infinispan";
    private static final String DEFAULT_USERNAME = "admin";
    private static final String DEFAULT_PASSWORD = "password";

    private static final Logger LOG = LoggerFactory.getLogger(InfinispanLocalContainerService.class);

    private final GenericContainer container;
    private final boolean isNetworkHost;

    public InfinispanLocalContainerService() {
        this(System.getProperty(InfinispanProperties.INFINISPAN_CONTAINER, CONTAINER_IMAGE));
    }

    public InfinispanLocalContainerService(String containerImage) {
        isNetworkHost = isHostNetworkMode();
        container = initContainer(containerImage, CONTAINER_NAME);
    }

    public InfinispanLocalContainerService(GenericContainer container) {
        isNetworkHost = isHostNetworkMode();
        this.container = container;
    }

    protected GenericContainer initContainer(String imageName, String containerName) {
        final Logger containerLog = LoggerFactory.getLogger("container." + containerName);
        final Consumer<OutputFrame> logConsumer = new Slf4jLogConsumer(containerLog);

        final GenericContainer c = new GenericContainer<>(imageName)
                .withNetworkAliases(containerName)
                .withEnv("USER", DEFAULT_USERNAME)
                .withEnv("PASS", DEFAULT_PASSWORD)
                .withLogConsumer(logConsumer)
                .withClasspathResourceMapping("infinispan.xml", "/user-config/infinispan.xml", BindMode.READ_ONLY)
                .withCommand("-c", "/user-config/infinispan.xml")
                .waitingFor(Wait.forLogMessage(".*Infinispan.*Server.*started.*", 1));
        if (isNetworkHost) {
            c.withNetworkMode("host");
        } else {
            c.withExposedPorts(InfinispanProperties.DEFAULT_SERVICE_PORT)
                    .waitingFor(Wait.forListeningPort());
        }
        return c;
    }

    @Override
    public void registerProperties() {
        System.setProperty(InfinispanProperties.SERVICE_HOST, host());
        System.setProperty(InfinispanProperties.SERVICE_PORT, String.valueOf(port()));
        System.setProperty(InfinispanProperties.SERVICE_ADDRESS, getServiceAddress());
        System.setProperty(InfinispanProperties.SERVICE_USERNAME, DEFAULT_USERNAME);
        System.setProperty(InfinispanProperties.SERVICE_PASSWORD, DEFAULT_PASSWORD);
        System.setProperty(InfinispanProperties.INFINISPAN_CONTAINER_NETWORK_MODE_HOST, String.valueOf(isNetworkHost));
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

        System.clearProperty(InfinispanProperties.SERVICE_HOST);
        System.clearProperty(InfinispanProperties.SERVICE_PORT);
        System.clearProperty(InfinispanProperties.SERVICE_ADDRESS);
        System.clearProperty(InfinispanProperties.SERVICE_USERNAME);
        System.clearProperty(InfinispanProperties.SERVICE_PASSWORD);
        System.clearProperty(InfinispanProperties.INFINISPAN_CONTAINER_NETWORK_MODE_HOST);
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
        return isNetworkHost
                ? InfinispanProperties.DEFAULT_SERVICE_PORT
                : container.getMappedPort(InfinispanProperties.DEFAULT_SERVICE_PORT);
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

    private boolean isHostNetworkMode() {
        return Boolean.parseBoolean(System.getProperty(InfinispanProperties.INFINISPAN_CONTAINER_NETWORK_MODE_HOST,
                String.valueOf(InfinispanProperties.INFINISPAN_CONTAINER_NETWORK_MODE_HOST_DEFAULT)));
    }
}
