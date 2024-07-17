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
package org.apache.camel.test.infra.solr.services;

import org.apache.camel.test.infra.common.services.ContainerService;
import org.apache.camel.test.infra.solr.common.SolrProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;

public class SolrLocalContainerService implements SolrService, ContainerService<GenericContainer> {
    public static final String CONTAINER_IMAGE = "solr:8.11.2";
    public static final String CONTAINER_NAME = "solr";
    private static final int PORT = 8983;

    private static final Logger LOG = LoggerFactory.getLogger(SolrLocalContainerService.class);
    private final GenericContainer container;

    public SolrLocalContainerService() {
        this(System.getProperty(SolrProperties.SOLR_CONTAINER, CONTAINER_IMAGE));
    }

    public SolrLocalContainerService(String imageName) {
        container = initContainer(imageName, CONTAINER_NAME);
    }

    protected GenericContainer initContainer(String imageName, String containerName) {
        return new GenericContainer<>(imageName)
                .withNetworkAliases(containerName)
                .withExposedPorts(PORT)
                .withLogConsumer(new Slf4jLogConsumer(LOG).withPrefix(SolrLocalContainerService.CONTAINER_IMAGE))
                .waitingFor(Wait.forLogMessage(".*Server.*Started.*", 1))
                .withCommand(isCloudMode() ? "-c" : "");
    }

    @Override
    public void registerProperties() {
        System.setProperty(SolrProperties.SERVICE_ADDRESS, getSolrBaseUrl());
    }

    @Override
    public void initialize() {
        LOG.info("Trying to start the {} container", CONTAINER_IMAGE);
        container.start();

        registerProperties();
        LOG.info("{} instance running at {}", CONTAINER_IMAGE, getSolrBaseUrl());
    }

    @Override
    public void shutdown() {
        LOG.info("Stopping the {} container", CONTAINER_IMAGE);
        container.stop();
    }

    @Override
    public GenericContainer getContainer() {
        return container;
    }

    protected String getHost() {
        return container.getHost();
    }

    protected int getPort() {
        return container.getMappedPort(PORT);
    }

    @Override
    public String getSolrBaseUrl() {
        return String.format("http://%s:%d/solr", getHost(), getPort());
    }

    @Override
    public boolean isCloudMode() {
        return false;
    }
}
