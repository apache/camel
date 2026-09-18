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
package org.apache.camel.test.infra.opa.services;

import org.apache.camel.spi.annotations.InfraService;
import org.apache.camel.test.infra.common.LocalPropertyResolver;
import org.apache.camel.test.infra.common.services.ContainerService;
import org.apache.camel.test.infra.opa.common.OpaProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

/**
 * Runs an Open Policy Agent server with an empty policy set. Tests load the policies they need through the OPA REST
 * API, so that this service stays independent of any particular policy.
 */
@InfraService(service = OpaInfraService.class,
              description = "Open Policy Agent, a policy engine evaluating Rego policies",
              serviceAlias = { "opa" })
public class OpaLocalContainerInfraService implements OpaInfraService, ContainerService<GenericContainer<?>> {
    public static final String CONTAINER_NAME = "opa";
    public static final int OPA_PORT = 8181;

    private static final Logger LOG = LoggerFactory.getLogger(OpaLocalContainerInfraService.class);

    private final GenericContainer<?> container;

    public OpaLocalContainerInfraService() {
        this(LocalPropertyResolver.getProperty(
                OpaLocalContainerInfraService.class,
                OpaProperties.OPA_CONTAINER));
    }

    public OpaLocalContainerInfraService(String containerName) {
        container = initContainer(containerName, CONTAINER_NAME);
    }

    public OpaLocalContainerInfraService(GenericContainer<?> container) {
        this.container = container;
    }

    @SuppressWarnings("resource")
    // NOTE: factoring method. The object must be closed by client.
    protected GenericContainer<?> initContainer(String imageName, String containerName) {
        return new GenericContainer<>(imageName) // NOSONAR
                .withNetworkAliases(containerName)
                .withExposedPorts(OPA_PORT)
                .withCommand("run", "--server", "--addr=0.0.0.0:" + OPA_PORT)
                .waitingFor(Wait.forHttp("/health").forPort(OPA_PORT).forStatusCode(200));
    }

    @Override
    public void registerProperties() {
        System.setProperty(OpaProperties.OPA_URL, getOpaUrl());
        System.setProperty(OpaProperties.OPA_HOST, host());
        System.setProperty(OpaProperties.OPA_PORT, String.valueOf(port()));
    }

    @Override
    public void initialize() {
        LOG.info("Trying to start the OPA container");
        container.start();

        registerProperties();
        LOG.info("OPA instance running at {}", getOpaUrl());
    }

    @Override
    public void shutdown() {
        LOG.info("Stopping the OPA container");
        container.stop();
    }

    @Override
    public GenericContainer<?> getContainer() {
        return container;
    }

    @Override
    public String getOpaUrl() {
        return String.format("http://%s:%d", host(), port());
    }

    @Override
    public String host() {
        return container.getHost();
    }

    @Override
    public int port() {
        return container.getMappedPort(OPA_PORT);
    }
}
