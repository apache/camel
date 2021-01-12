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
package org.apache.camel.test.infra.arangodb.services;

import org.apache.camel.test.infra.arangodb.common.ArangoDBProperties;
import org.apache.camel.test.infra.common.services.ContainerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;

public class ArangoDBLocalContainerService implements ArangoDBService, ContainerService<GenericContainer> {
    private static final Logger LOG = LoggerFactory.getLogger(ArangoDBLocalContainerService.class);

    private ArangoDbContainer container;

    public ArangoDBLocalContainerService() {
        String containerName = System.getProperty(ArangoDBProperties.ARANGODB_CONTAINER);

        if (containerName == null) {
            container = new ArangoDbContainer();
        } else {
            container = new ArangoDbContainer(containerName);
        }
    }

    @Override
    public int getPort() {
        return container.getServicePort();
    }

    @Override
    public String getHost() {
        return container.getHost();
    }

    @Override
    public void registerProperties() {
        System.setProperty(ArangoDBProperties.ARANGODB_HOST, container.getHost());
        System.setProperty(ArangoDBProperties.ARANGODB_PORT, String.valueOf(container.getServicePort()));
    }

    @Override
    public void initialize() {
        LOG.info("Trying to start the ArangoDB container");
        container.start();

        registerProperties();
        LOG.info("ArangoDB instance running at {}", getServiceAddress());
    }

    @Override
    public void shutdown() {
        LOG.info("Stopping the ArangoDB container");
        container.stop();
    }

    @Override
    public GenericContainer getContainer() {
        return container;
    }
}
