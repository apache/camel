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
package org.apache.camel.test.infra.postgres.services;

import org.apache.camel.test.infra.common.services.ContainerService;
import org.apache.camel.test.infra.postgres.common.PostgresProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;

public class PostgresLocalContainerService implements PostgresService, ContainerService<GenericContainer> {
    public static final String DEFAULT_POSTGRES_CONTAINER = "postgres:13.0";
    private static final Logger LOG = LoggerFactory.getLogger(PostgresLocalContainerService.class);
    private PostgreSQLContainer container;

    public PostgresLocalContainerService() {
        String containerName = System.getProperty("postgres.container", DEFAULT_POSTGRES_CONTAINER);

        container = new PostgreSQLContainer(containerName);
    }

    public PostgresLocalContainerService(PostgreSQLContainer container) {
        this.container = container;
    }

    @Override
    public void registerProperties() {
        System.setProperty(PostgresProperties.SERVICE_ADDRESS, getServiceAddress());
        System.setProperty(PostgresProperties.HOST, host());
        System.setProperty(PostgresProperties.PORT, String.valueOf(port()));
        System.setProperty(PostgresProperties.USERNAME, container.getUsername());
        System.setProperty(PostgresProperties.PASSWORD, container.getPassword());
    }

    @Override
    public void initialize() {
        LOG.info("Trying to start the Postgres container");
        container.start();

        registerProperties();
        LOG.info("Postgres instance running at {}", getServiceAddress());
    }

    @Override
    public void shutdown() {
        LOG.info("Stopping the Postgres container");
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
        return container.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT);
    }

    @Override
    public String getServiceAddress() {
        return String.format("%s:%d", host(), port());
    }

    @Override
    public String userName() {
        return container.getUsername();
    }

    @Override
    public String password() {
        return container.getPassword();
    }
}
