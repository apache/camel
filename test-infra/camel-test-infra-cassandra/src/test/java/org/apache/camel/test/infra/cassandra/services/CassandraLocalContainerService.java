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
package org.apache.camel.test.infra.cassandra.services;

import org.apache.camel.test.infra.cassandra.common.CassandraProperties;
import org.apache.camel.test.infra.common.LocalPropertyResolver;
import org.apache.camel.test.infra.common.services.ContainerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.CassandraContainer;

/**
 * A service for a local instance of Apache Cassandra running with TestContainers
 */
public class CassandraLocalContainerService implements CassandraService, ContainerService<CassandraContainer> {
    private static final Logger LOG = LoggerFactory.getLogger(CassandraLocalContainerService.class);

    private final CassandraContainer container;

    public CassandraLocalContainerService() {
        this(LocalPropertyResolver.getProperty(
                CassandraLocalContainerService.class,
                CassandraProperties.CASSANDRA_CONTAINER));
    }

    public CassandraLocalContainerService(String imageName) {
        container = initContainer(imageName);
    }

    public CassandraLocalContainerService(CassandraContainer container) {
        this.container = container;
    }

    protected CassandraContainer initContainer(String imageName) {
        return new CassandraContainer(imageName);
    }

    @Override
    public int getCQL3Port() {
        return container.getMappedPort(CassandraContainer.CQL_PORT);
    }

    @Override
    public String getCassandraHost() {
        return container.getHost();
    }

    @Override
    public void registerProperties() {
        System.setProperty(CassandraProperties.CASSANDRA_HOST, getCassandraHost());
        System.setProperty(CassandraProperties.CASSANDRA_CQL3_PORT, String.valueOf(getCQL3Port()));
    }

    @Override
    public void initialize() {
        container.start();

        registerProperties();
        LOG.info("Cassandra server running at address {}", getCQL3Endpoint());
    }

    @Override
    public void shutdown() {
        LOG.info("Stopping the Cassandra container");
        container.stop();
    }

    @Override
    public CassandraContainer getContainer() {
        return container;
    }
}
