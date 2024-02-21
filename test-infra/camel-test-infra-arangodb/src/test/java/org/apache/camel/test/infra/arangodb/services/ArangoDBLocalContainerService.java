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
import org.apache.camel.test.infra.common.LocalPropertyResolver;
import org.apache.camel.test.infra.common.services.ContainerEnvironmentUtil;
import org.apache.camel.test.infra.common.services.ContainerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ArangoDBLocalContainerService implements ArangoDBService, ContainerService<ArangoDbContainer> {
    private static final Logger LOG = LoggerFactory.getLogger(ArangoDBLocalContainerService.class);

    private final ArangoDbContainer container;

    public ArangoDBLocalContainerService() {
        this(LocalPropertyResolver.getProperty(ArangoDBLocalContainerService.class, ArangoDBProperties.ARANGODB_CONTAINER));
    }

    public ArangoDBLocalContainerService(String imageName) {
        container = initContainer(imageName);
    }

    public ArangoDBLocalContainerService(ArangoDbContainer container) {
        this.container = container;
    }

    protected ArangoDbContainer initContainer(String imageName) {
        return new ArangoDbContainer(imageName);
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
        ContainerEnvironmentUtil.configureContainerStartup(container, ArangoDBProperties.ARANGODB_CONTAINER, 2);

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
    public ArangoDbContainer getContainer() {
        return container;
    }
}
