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

package org.apache.camel.test.infra.jdbc.services;

import org.apache.camel.test.infra.common.services.ContainerService;
import org.apache.camel.test.infra.jdbc.common.JDBCProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.JdbcDatabaseContainer;

public class JDBCLocalContainerService<T extends JdbcDatabaseContainer<T>> implements JDBCService, ContainerService<T> {
    private static final Logger LOG = LoggerFactory.getLogger(JDBCLocalContainerService.class);

    private final T container;

    public JDBCLocalContainerService(T container) {
        this.container = container;
    }

    @Override
    public void registerProperties() {
        System.setProperty(JDBCProperties.JDBC_CONNECTION_URL, jdbcUrl());
    }

    @Override
    public String jdbcUrl() {
        return container.getJdbcUrl();
    }

    @Override
    public T getContainer() {
        return container;
    }

    @Override
    public void initialize() {
        LOG.info("Trying to start the database container");
        container.start();

        registerProperties();
        LOG.info("Database instance available via JDBC url {}", jdbcUrl());
    }

    @Override
    public void shutdown() {
        LOG.info("Stopping the database instance");
        container.stop();
    }
}
