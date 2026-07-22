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
package org.apache.camel.test.infra.clickhouse.services;

import org.apache.camel.spi.annotations.InfraService;
import org.apache.camel.test.infra.clickhouse.common.ClickHouseProperties;
import org.apache.camel.test.infra.common.LocalPropertyResolver;
import org.apache.camel.test.infra.common.services.ContainerEnvironmentUtil;
import org.apache.camel.test.infra.common.services.ContainerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.clickhouse.ClickHouseContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * A service for a local instance of ClickHouse running with TestContainers
 */
@InfraService(service = ClickHouseInfraService.class,
              description = "ClickHouse is a high-performance columnar OLAP database",
              serviceAlias = { "clickhouse" })
public class ClickHouseLocalContainerInfraService implements ClickHouseInfraService, ContainerService<ClickHouseContainer> {
    private static final Logger LOG = LoggerFactory.getLogger(ClickHouseLocalContainerInfraService.class);

    private final ClickHouseContainer container;

    public ClickHouseLocalContainerInfraService() {
        this(LocalPropertyResolver.getProperty(
                ClickHouseLocalContainerInfraService.class,
                ClickHouseProperties.CLICKHOUSE_CONTAINER));
    }

    public ClickHouseLocalContainerInfraService(String imageName) {
        container = initContainer(imageName);
        String name = ContainerEnvironmentUtil.containerName(ClickHouseLocalContainerInfraService.this.getClass());
        if (name != null) {
            container.withCreateContainerCmdModifier(cmd -> cmd.withName(name));
        }
    }

    public ClickHouseLocalContainerInfraService(ClickHouseContainer container) {
        this.container = container;
    }

    protected ClickHouseContainer initContainer(String imageName) {
        return new ClickHouseContainer(
                DockerImageName.parse(imageName).asCompatibleSubstituteFor("clickhouse/clickhouse-server"));
    }

    @Override
    public String getHttpUrl() {
        return container.getHttpUrl();
    }

    @Override
    public String getUsername() {
        return container.getUsername();
    }

    @Override
    public String getPassword() {
        return container.getPassword();
    }

    @Override
    public String getDatabaseName() {
        return container.getDatabaseName();
    }

    @Override
    public void registerProperties() {
        System.setProperty(ClickHouseProperties.CLICKHOUSE_HTTP_URL, getHttpUrl());
        System.setProperty(ClickHouseProperties.CLICKHOUSE_USERNAME, getUsername());
        System.setProperty(ClickHouseProperties.CLICKHOUSE_PASSWORD, getPassword());
        System.setProperty(ClickHouseProperties.CLICKHOUSE_DATABASE, getDatabaseName());
    }

    @Override
    public void initialize() {
        container.start();

        registerProperties();
        LOG.info("ClickHouse server running at address {}", getHttpUrl());
    }

    @Override
    public void shutdown() {
        LOG.info("Stopping the ClickHouse container");
        container.stop();
    }

    @Override
    public ClickHouseContainer getContainer() {
        return container;
    }
}
