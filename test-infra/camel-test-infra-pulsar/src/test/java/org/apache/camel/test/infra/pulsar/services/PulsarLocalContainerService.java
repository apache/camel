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
package org.apache.camel.test.infra.pulsar.services;

import org.apache.camel.test.infra.common.services.ContainerService;
import org.apache.camel.test.infra.pulsar.common.PulsarProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.PulsarContainer;
import org.testcontainers.utility.DockerImageName;

public class PulsarLocalContainerService implements PulsarService, ContainerService<PulsarContainer> {
    protected static final String CONTAINER_IMAGE = "apachepulsar/pulsar:2.7.0";

    private static final Logger LOG = LoggerFactory.getLogger(PulsarLocalContainerService.class);

    private PulsarContainer container;

    public PulsarLocalContainerService() {
        String imageName = System.getProperty("pulsar.container", CONTAINER_IMAGE);

        initContainer(imageName);
    }

    public PulsarLocalContainerService(String imageName) {
        initContainer(imageName);
    }

    protected void initContainer(String imageName) {
        container = new PulsarContainer(DockerImageName.parse(imageName));
    }

    @Override
    public void registerProperties() {
        System.setProperty(PulsarProperties.PULSAR_ADMIN_URL, getPulsarAdminUrl());
        System.setProperty(PulsarProperties.PULSAR_BROKER_URL, getPulsarBrokerUrl());
    }

    @Override
    public void initialize() {
        LOG.info("Trying to start the Pulsar container");
        container.start();

        registerProperties();
        LOG.info("Pulsar instance running at {}", getPulsarAdminUrl());
        LOG.info("Pulsar admin URL available at {}", getPulsarAdminUrl());
    }

    @Override
    public void shutdown() {
        LOG.info("Stopping the Pulsar container");
        container.stop();
    }

    @Override
    public PulsarContainer getContainer() {
        return container;
    }

    @Override
    public String getPulsarAdminUrl() {
        return container.getHttpServiceUrl();
    }

    @Override
    public String getPulsarBrokerUrl() {
        return container.getPulsarBrokerUrl();
    }
}
