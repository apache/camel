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
package org.apache.camel.test.infra.couchdb.services;

import org.apache.camel.test.infra.common.LocalPropertyResolver;
import org.apache.camel.test.infra.common.services.ContainerService;
import org.apache.camel.test.infra.couchdb.common.CouchDbProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

public class CouchDbLocalContainerService implements CouchDbService, ContainerService<GenericContainer> {
    public static final String CONTAINER_NAME = "couchdb";

    private static final Logger LOG = LoggerFactory.getLogger(CouchDbLocalContainerService.class);

    private final GenericContainer container;

    public CouchDbLocalContainerService() {
        this(LocalPropertyResolver.getProperty(
                CouchDbLocalContainerService.class,
                CouchDbProperties.COUCHDB_CONTAINER));
    }

    public CouchDbLocalContainerService(String imageName) {
        container = initContainer(imageName, CONTAINER_NAME);
    }

    public CouchDbLocalContainerService(GenericContainer container) {
        this.container = container;
    }

    protected GenericContainer initContainer(String imageName, String containerName) {
        return new GenericContainer<>(DockerImageName.parse(imageName))
                .withNetworkAliases(containerName)
                .withExposedPorts(CouchDbProperties.DEFAULT_PORT)
                .waitingFor(Wait.forListeningPort());
    }

    @Override
    public void registerProperties() {
        System.setProperty(CouchDbProperties.SERVICE_ADDRESS, getServiceAddress());
        System.setProperty(CouchDbProperties.PORT, String.valueOf(port()));
        System.setProperty(CouchDbProperties.HOST, host());
    }

    @Override
    public void initialize() {
        LOG.info("Trying to start the CouchDb container");
        container.start();

        registerProperties();
        LOG.info("CouchDb instance running at {}", getServiceAddress());
    }

    @Override
    public void shutdown() {
        LOG.info("Stopping the CouchDb container");
        container.stop();
    }

    @Override
    public GenericContainer getContainer() {
        return container;
    }

    @Override
    public String host() {
        return container.getHost();
    }

    @Override
    public int port() {
        return container.getMappedPort(CouchDbProperties.DEFAULT_PORT);
    }
}
