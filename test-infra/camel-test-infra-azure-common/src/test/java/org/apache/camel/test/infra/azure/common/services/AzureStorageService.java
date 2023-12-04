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
import org.apache.camel.test.infra.azure.common.AzureProperties;
import org.apache.camel.test.infra.common.LocalPropertyResolver;
import org.apache.camel.test.infra.common.services.ContainerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AzureStorageService implements AzureService, ContainerService<AzuriteContainer> {
    private static final Logger LOG = LoggerFactory.getLogger(AzureStorageService.class);
    private final AzuriteContainer container;

    public AzureStorageService() {
        this(LocalPropertyResolver.getProperty(AzureStorageService.class, AzureProperties.AZURE_CONTAINER));
    }

    public AzureStorageService(String imageName) {
        this.container = initContainer(imageName);
    }

    public AzureStorageService(AzuriteContainer container) {
        this.container = container;
    }

    protected AzuriteContainer initContainer(String imageName) {
        return new AzuriteContainer(imageName);
    }

    public AzuriteContainer getContainer() {
        return container;
    }

    public void registerProperties() {
        System.setProperty(AzureConfigs.ACCOUNT_NAME, container.azureCredentials().accountName());
        System.setProperty(AzureConfigs.ACCOUNT_KEY, container.azureCredentials().accountKey());
        System.setProperty(AzureConfigs.HOST, container.getHost());
    }

    @Override
    public void initialize() {
        container.start();

        LOG.info("Azurite local blob service running at address {}:{}", container.getHost(),
                container.getMappedPort(AzureServices.BLOB_SERVICE));
        LOG.info("Azurite local queue service running at address {}:{}", container.getHost(),
                container.getMappedPort(AzureServices.QUEUE_SERVICE));

        registerProperties();
    }

    @Override
    public void shutdown() {
        container.stop();
    }
}
