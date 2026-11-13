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

package org.apache.camel.test.infra.google.storage.services;

import org.apache.camel.spi.annotations.InfraService;
import org.apache.camel.test.infra.common.LocalPropertyResolver;
import org.apache.camel.test.infra.common.services.ContainerEnvironmentUtil;
import org.apache.camel.test.infra.common.services.ContainerService;
import org.apache.camel.test.infra.google.common.GoogleProperties;
import org.apache.camel.test.infra.google.common.services.FlociGcpContainer;
import org.apache.camel.test.infra.google.storage.common.GoogleStorageProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A local Cloud Storage service on Floci GCP. Floci GCP accepts any credentials, and gives an access token to a service
 * account of any key at {@code <endpoint>/token}.
 */
@InfraService(service = GoogleStorageInfraService.class,
              description = "Local Google Cloud Storage service with Floci GCP",
              serviceAlias = "google", serviceImplementationAlias = "storage")
public class GoogleStorageLocalContainerInfraService
        implements GoogleStorageInfraService, ContainerService<FlociGcpContainer> {

    private static final Logger LOG = LoggerFactory.getLogger(GoogleStorageLocalContainerInfraService.class);
    private static final String DEFAULT_PROJECT_ID = "test-project";

    private final FlociGcpContainer container;

    public GoogleStorageLocalContainerInfraService() {
        this(LocalPropertyResolver.getProperty(FlociGcpContainer.class, GoogleProperties.GOOGLE_CONTAINER));
    }

    public GoogleStorageLocalContainerInfraService(String imageName) {
        container = initContainer(imageName);
        String name = ContainerEnvironmentUtil.containerName(this.getClass());
        if (name != null) {
            container.withCreateContainerCmdModifier(cmd -> cmd.withName(name));
        }
    }

    public GoogleStorageLocalContainerInfraService(FlociGcpContainer container) {
        this.container = container;
    }

    protected FlociGcpContainer initContainer(String imageName) {
        return new FlociGcpContainer(imageName, ContainerEnvironmentUtil.isFixedPort(this.getClass()));
    }

    @Override
    public void registerProperties() {
        System.setProperty(GoogleStorageProperties.PROJECT_ID, projectId());
        System.setProperty(GoogleStorageProperties.ENDPOINT, endpoint());
    }

    @Override
    public void initialize() {
        container.start();

        LOG.info("Floci GCP running at address {}", container.serviceAddress());

        registerProperties();
    }

    @Override
    public void shutdown() {
        container.stop();
    }

    @Override
    public FlociGcpContainer getContainer() {
        return container;
    }

    /**
     * The id of the project, {@code test-project} unless the {@code google.storage.project.id} system property gives
     * another one: Floci GCP accepts any
     */
    @Override
    public String projectId() {
        return System.getProperty(GoogleStorageProperties.PROJECT_ID, DEFAULT_PROJECT_ID);
    }

    @Override
    public String endpoint() {
        return container.serviceAddress();
    }
}
