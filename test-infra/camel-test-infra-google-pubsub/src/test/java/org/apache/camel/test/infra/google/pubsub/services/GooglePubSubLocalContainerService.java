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
package org.apache.camel.test.infra.google.pubsub.services;

import org.apache.camel.test.infra.common.services.ContainerService;
import org.apache.camel.test.infra.google.pubsub.common.GooglePubSubProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy;

public class GooglePubSubLocalContainerService implements GooglePubSubService, ContainerService<GenericContainer> {
    public static final String PROJECT_ID;
    private static final Logger LOG = LoggerFactory.getLogger(GooglePubSubLocalContainerService.class);
    private static final String CONTAINER_NAME = "google/cloud-sdk:latest";
    private static final String DEFAULT_PROJECT_ID = "test-project";
    private static final int DEFAULT_PORT = 8383;

    static {
        PROJECT_ID = System.getProperty(GooglePubSubProperties.PROJECT_ID, DEFAULT_PROJECT_ID);
    }

    private GenericContainer container;

    public GooglePubSubLocalContainerService() {
        String containerName = System.getProperty(GooglePubSubProperties.CONTAINER_NAME, CONTAINER_NAME);
        initContainer(containerName);
    }

    public GooglePubSubLocalContainerService(String containerName) {
        initContainer(containerName);
    }

    protected void initContainer(String containerName) {
        String command = String.format("gcloud beta emulators pubsub start --project %s --host-port=0.0.0.0:%d",
                PROJECT_ID, DEFAULT_PORT);

        container = new GenericContainer<>(containerName)
                .withExposedPorts(DEFAULT_PORT)
                .withCommand("/bin/sh", "-c", command)
                .waitingFor(new LogMessageWaitStrategy().withRegEx("(?s).*started.*$"));
    }

    @Override
    public void registerProperties() {

    }

    @Override
    public void initialize() {
        LOG.info("Trying to start the GooglePubSub container");
        container.start();

        registerProperties();

        LOG.info("GooglePubSub instance running at {}", getServiceAddress());
    }

    @Override
    public void shutdown() {
        LOG.info("Stopping the GooglePubSub container");
        container.stop();
    }

    @Override
    public GenericContainer getContainer() {
        return container;
    }

    @Override
    public String getServiceAddress() {
        return String.format("%s:%d", container.getContainerIpAddress(), container.getFirstMappedPort());
    }
}
