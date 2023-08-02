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
package org.apache.camel.test.infra.hashicorp.vault.services;

import java.util.function.Consumer;

import org.apache.camel.test.infra.common.services.ContainerService;
import org.apache.camel.test.infra.hashicorp.vault.common.HashicorpVaultProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;

public class HashicorpVaultLocalContainerService implements HashicorpVaultService, ContainerService<GenericContainer<?>> {
    public static final String CONTAINER_IMAGE = "hashicorp/vault:1.13.3";
    public static final String CONTAINER_NAME = "hashicorp-vault";
    private static final String DEFAULT_TOKEN = "myToken";

    private static final Logger LOG = LoggerFactory.getLogger(HashicorpVaultLocalContainerService.class);

    private final GenericContainer container;

    public HashicorpVaultLocalContainerService() {
        this(System.getProperty(HashicorpVaultProperties.HASHICORP_VAULT_CONTAINER, CONTAINER_IMAGE));
    }

    public HashicorpVaultLocalContainerService(String containerImage) {
        container = initContainer(containerImage, CONTAINER_NAME);
    }

    public HashicorpVaultLocalContainerService(GenericContainer container) {
        this.container = container;
    }

    protected GenericContainer initContainer(String imageName, String containerName) {
        final Logger containerLog = LoggerFactory.getLogger("container." + containerName);
        final Consumer<OutputFrame> logConsumer = new Slf4jLogConsumer(containerLog);

        return new GenericContainer<>(imageName)
                .withNetworkAliases(containerName)
                .withEnv("VAULT_DEV_ROOT_TOKEN_ID", DEFAULT_TOKEN)
                .withLogConsumer(logConsumer)
                .withExposedPorts(HashicorpVaultProperties.DEFAULT_SERVICE_PORT)
                .waitingFor(Wait.forListeningPort())
                .waitingFor(Wait.forLogMessage(".*Development.*mode.*should.*", 1));
    }

    @Override
    public void registerProperties() {
        System.setProperty(HashicorpVaultProperties.HASHICORP_VAULT_HOST, host());
        System.setProperty(HashicorpVaultProperties.HASHICORP_VAULT_PORT, String.valueOf(port()));
        System.setProperty(HashicorpVaultProperties.HASHICORP_VAULT_TOKEN, DEFAULT_TOKEN);
    }

    @Override
    public void initialize() {
        LOG.info("Trying to start the Hashicorp Vault container");
        container.start();

        registerProperties();
    }

    @Override
    public void shutdown() {
        LOG.info("Stopping the Hashicorp Vault container");
        container.stop();

        System.clearProperty(HashicorpVaultProperties.HASHICORP_VAULT_HOST);
        System.clearProperty(HashicorpVaultProperties.HASHICORP_VAULT_PORT);
        System.clearProperty(HashicorpVaultProperties.HASHICORP_VAULT_TOKEN);
    }

    @Override
    public GenericContainer getContainer() {
        return container;
    }

    @Override
    public String token() {
        return DEFAULT_TOKEN;
    }

    @Override
    public int port() {
        return container.getMappedPort(HashicorpVaultProperties.DEFAULT_SERVICE_PORT);
    }

    @Override
    public String host() {
        return container.getHost();
    }
}
