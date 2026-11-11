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

package org.apache.camel.test.infra.azure.common.services;

import org.apache.camel.test.infra.azure.common.AzureConfigs;
import org.apache.camel.test.infra.azure.common.AzureCredentialsHolder;
import org.apache.camel.test.infra.azure.common.AzureProperties;
import org.apache.camel.test.infra.common.LocalPropertyResolver;
import org.apache.camel.test.infra.common.services.ContainerEnvironmentUtil;
import org.apache.camel.test.infra.common.services.ContainerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class of the local Azure services that run on Floci AZ.
 */
public abstract class FlociAzInfraService implements AzureInfraService, ContainerService<FlociAzContainer> {
    private static final Logger LOG = LoggerFactory.getLogger(FlociAzInfraService.class);

    private final FlociAzContainer container;

    protected FlociAzInfraService() {
        this(LocalPropertyResolver.getProperty(FlociAzContainer.class, AzureProperties.AZURE_CONTAINER));
    }

    protected FlociAzInfraService(String imageName) {
        this.container = initContainer(imageName);
        String name = ContainerEnvironmentUtil.containerName(this.getClass());
        if (name != null) {
            container.withCreateContainerCmdModifier(cmd -> cmd.withName(name));
        }
    }

    protected FlociAzInfraService(FlociAzContainer container) {
        this.container = container;
    }

    protected FlociAzContainer initContainer(String imageName) {
        return new FlociAzContainer(imageName, ContainerEnvironmentUtil.isFixedPort(this.getClass()));
    }

    @Override
    public FlociAzContainer getContainer() {
        return container;
    }

    @Override
    public void registerProperties() {
        System.setProperty(AzureConfigs.ACCOUNT_NAME, accountName());
        System.setProperty(AzureConfigs.ACCOUNT_KEY, accessKey());
        System.setProperty(AzureConfigs.HOST, host());
        System.setProperty(AzureConfigs.PORT, String.valueOf(port()));
    }

    @Override
    public void initialize() {
        container.start();

        LOG.info("Floci AZ running at address {}", container.serviceAddress());

        registerProperties();
    }

    @Override
    public void shutdown() {
        container.stop();
    }

    @Override
    public AzureCredentialsHolder azureCredentials() {
        return container.azureCredentials();
    }

    @Override
    public String accountName() {
        return container.azureCredentials().accountName();
    }

    @Override
    public String accessKey() {
        return container.azureCredentials().accountKey();
    }

    @Override
    public String host() {
        return container.getHost();
    }

    @Override
    public int port() {
        return container.getMappedPort(FlociAzContainer.SERVICE_PORT);
    }
}
