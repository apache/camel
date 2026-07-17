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
package org.apache.camel.test.infra.apicurio.registry.services;

import org.apache.camel.spi.annotations.InfraService;
import org.apache.camel.test.infra.apicurio.registry.common.ApicurioRegistryProperties;
import org.apache.camel.test.infra.common.LocalPropertyResolver;
import org.apache.camel.test.infra.common.services.ContainerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

@InfraService(service = ApicurioRegistryInfraService.class,
              description = "Apicurio Registry is an API/Schema registry",
              serviceAlias = { "apicurio-registry" })
public class ApicurioRegistryLocalContainerInfraService
        implements ApicurioRegistryInfraService, ContainerService<GenericContainer<?>> {

    public static final String CONTAINER_NAME = "apicurio-registry";

    private static final Logger LOG = LoggerFactory.getLogger(ApicurioRegistryLocalContainerInfraService.class);

    private final GenericContainer<?> container;

    public ApicurioRegistryLocalContainerInfraService() {
        this(LocalPropertyResolver.getProperty(
                ApicurioRegistryLocalContainerInfraService.class,
                ApicurioRegistryProperties.APICURIO_REGISTRY_CONTAINER));
    }

    public ApicurioRegistryLocalContainerInfraService(String containerName) {
        container = initContainer(containerName, CONTAINER_NAME);
    }

    public ApicurioRegistryLocalContainerInfraService(GenericContainer<?> container) {
        this.container = container;
    }

    @SuppressWarnings("resource")
    protected GenericContainer<?> initContainer(String imageName, String containerName) {
        return new GenericContainer<>(imageName)
                .withNetworkAliases(containerName)
                .withExposedPorts(ApicurioRegistryProperties.DEFAULT_PORT)
                .waitingFor(Wait.forHttp("/health").forPort(ApicurioRegistryProperties.DEFAULT_PORT).forStatusCode(200));
    }

    @Override
    public void registerProperties() {
        System.setProperty(ApicurioRegistryProperties.APICURIO_REGISTRY_URL, getApicurioRegistryUrl());
        System.setProperty(ApicurioRegistryProperties.APICURIO_REGISTRY_HOST, host());
        System.setProperty(ApicurioRegistryProperties.APICURIO_REGISTRY_PORT, String.valueOf(port()));
    }

    @Override
    public void initialize() {
        LOG.info("Trying to start the Apicurio Registry container");
        container.start();

        registerProperties();
        LOG.info("Apicurio Registry instance running at {}", getApicurioRegistryUrl());
    }

    @Override
    public void shutdown() {
        LOG.info("Stopping the Apicurio Registry container");
        container.stop();
    }

    @Override
    public GenericContainer<?> getContainer() {
        return container;
    }

    @Override
    public String getApicurioRegistryUrl() {
        return String.format("http://%s:%d/apis/registry/v3", host(),
                container.getMappedPort(ApicurioRegistryProperties.DEFAULT_PORT));
    }

    @Override
    public String host() {
        return container.getHost();
    }

    @Override
    public int port() {
        return container.getMappedPort(ApicurioRegistryProperties.DEFAULT_PORT);
    }
}
