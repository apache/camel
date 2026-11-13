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

package org.apache.camel.test.infra.google.firestore.services;

import org.apache.camel.spi.annotations.InfraService;
import org.apache.camel.test.infra.common.LocalPropertyResolver;
import org.apache.camel.test.infra.common.services.ContainerEnvironmentUtil;
import org.apache.camel.test.infra.common.services.ContainerService;
import org.apache.camel.test.infra.google.common.GoogleProperties;
import org.apache.camel.test.infra.google.common.services.FlociGcpContainer;
import org.apache.camel.test.infra.google.firestore.common.GoogleFirestoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A local Firestore service on Floci GCP, which the clients reach as an emulator: in plain text, with any credentials.
 */
@InfraService(service = GoogleFirestoreInfraService.class,
              description = "Local Google Cloud Firestore service with Floci GCP",
              serviceAlias = "google", serviceImplementationAlias = "firestore")
public class GoogleFirestoreLocalContainerInfraService
        implements GoogleFirestoreInfraService, ContainerService<FlociGcpContainer> {

    private static final Logger LOG = LoggerFactory.getLogger(GoogleFirestoreLocalContainerInfraService.class);
    private static final String DEFAULT_PROJECT_ID = "test-project";

    private final FlociGcpContainer container;

    public GoogleFirestoreLocalContainerInfraService() {
        this(LocalPropertyResolver.getProperty(FlociGcpContainer.class, GoogleProperties.GOOGLE_CONTAINER));
    }

    public GoogleFirestoreLocalContainerInfraService(String imageName) {
        container = initContainer(imageName);
        String name = ContainerEnvironmentUtil.containerName(this.getClass());
        if (name != null) {
            container.withCreateContainerCmdModifier(cmd -> cmd.withName(name));
        }
    }

    public GoogleFirestoreLocalContainerInfraService(FlociGcpContainer container) {
        this.container = container;
    }

    protected FlociGcpContainer initContainer(String imageName) {
        return new FlociGcpContainer(imageName, ContainerEnvironmentUtil.isFixedPort(this.getClass()));
    }

    @Override
    public void registerProperties() {
        System.setProperty(GoogleFirestoreProperties.PROJECT_ID, projectId());
        System.setProperty(GoogleFirestoreProperties.ENDPOINT, endpoint());
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
     * The id of the project: {@code test-project}, as Floci GCP accepts any
     */
    @Override
    public String projectId() {
        return DEFAULT_PROJECT_ID;
    }

    @Override
    public String endpoint() {
        return container.grpcAddress();
    }
}
