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

import org.apache.camel.spi.annotations.InfraService;
import org.apache.camel.test.infra.common.LocalPropertyResolver;
import org.apache.camel.test.infra.common.services.ContainerEnvironmentUtil;
import org.apache.camel.test.infra.common.services.ContainerService;
import org.apache.camel.test.infra.hashicorp.vault.common.HashicorpVaultProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

@InfraService(service = HashicorpVaultInfraService.class,
              description = "Vault is a tool for securely accessing secrets",
              serviceAlias = "hashicorp",
              serviceImplementationAlias = "vault")
public class HashicorpVaultLocalContainerInfraService
        implements HashicorpVaultInfraService, ContainerService<GenericContainer<?>> {
    public static final String CONTAINER_NAME = "hashicorp-vault";
    private static final String DEFAULT_TOKEN = "myToken";

    private static final Logger LOG = LoggerFactory.getLogger(HashicorpVaultLocalContainerInfraService.class);

    private final GenericContainer container;

    public HashicorpVaultLocalContainerInfraService() {
        this(LocalPropertyResolver.getProperty(
                HashicorpVaultLocalContainerInfraService.class,
                HashicorpVaultProperties.HASHICORP_VAULT_CONTAINER));
    }

    public HashicorpVaultLocalContainerInfraService(String containerImage) {
        container = initContainer(containerImage, CONTAINER_NAME);
    }

    public HashicorpVaultLocalContainerInfraService(GenericContainer container) {
        this.container = container;
    }

    protected GenericContainer initContainer(String imageName, String containerName) {
        final Logger containerLog = LoggerFactory.getLogger("container." + containerName);
        final Consumer<OutputFrame> logConsumer = new Slf4jLogConsumer(containerLog);

        class HashicorpVaultContainer extends GenericContainer<HashicorpVaultContainer> {
            public HashicorpVaultContainer(boolean fixedPort) {
                super(DockerImageName.parse(imageName));

                withNetworkAliases(containerName)
                        .withEnv("VAULT_DEV_ROOT_TOKEN_ID", DEFAULT_TOKEN)
                        .withLogConsumer(logConsumer)
                        .waitingFor(Wait.forListeningPort())
                        .waitingFor(Wait.forLogMessage(".*Development.*mode.*should.*", 1));

                if (fixedPort) {
                    addFixedExposedPort(HashicorpVaultProperties.DEFAULT_SERVICE_PORT,
                            HashicorpVaultProperties.DEFAULT_SERVICE_PORT);
                } else {
                    withExposedPorts(HashicorpVaultProperties.DEFAULT_SERVICE_PORT);
                }
            }
        }

        return new HashicorpVaultContainer(ContainerEnvironmentUtil.isFixedPort(this.getClass()));
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
        container.withStartupAttempts(5);
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
