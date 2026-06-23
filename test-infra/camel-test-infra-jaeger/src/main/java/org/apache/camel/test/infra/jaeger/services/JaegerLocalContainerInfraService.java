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
package org.apache.camel.test.infra.jaeger.services;

import org.apache.camel.spi.annotations.InfraService;
import org.apache.camel.test.infra.common.services.ContainerEnvironmentUtil;
import org.apache.camel.test.infra.common.services.ContainerService;
import org.apache.camel.test.infra.jaeger.common.JaegerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @since 4.21
 */
@InfraService(service = JaegerInfraService.class,
              description = "Jaeger is a distributed tracing backend with OTLP collector and UI",
              serviceAlias = { "jaeger" })
public class JaegerLocalContainerInfraService implements JaegerInfraService, ContainerService<JaegerContainer> {
    private static final Logger LOG = LoggerFactory.getLogger(JaegerLocalContainerInfraService.class);

    private final JaegerContainer container;

    public JaegerLocalContainerInfraService() {
        container = new JaegerContainer();
        initContainer();
    }

    public JaegerLocalContainerInfraService(String imageName) {
        container = JaegerContainer.initContainer(imageName, JaegerContainer.CONTAINER_NAME);
        initContainer();
    }

    private void initContainer() {
        String name = ContainerEnvironmentUtil.containerName(this.getClass());
        if (name != null) {
            container.withCreateContainerCmdModifier(cmd -> cmd.withName(name));
        }
        boolean fixedPort = ContainerEnvironmentUtil.isFixedPort(this.getClass());
        if (fixedPort) {
            ContainerEnvironmentUtil.configurePorts(container, true,
                    ContainerEnvironmentUtil.PortConfig.primary(JaegerProperties.DEFAULT_COLLECTOR_HTTP_PORT),
                    ContainerEnvironmentUtil.PortConfig.secondary(JaegerProperties.DEFAULT_COLLECTOR_GRPC_PORT),
                    ContainerEnvironmentUtil.PortConfig.secondary(JaegerProperties.DEFAULT_QUERY_UI_PORT));
        }
    }

    @Override
    public void registerProperties() {
        System.setProperty(JaegerProperties.HOST, host());
        System.setProperty(JaegerProperties.COLLECTOR_GRPC_PORT, String.valueOf(collectorGrpcPort()));
        System.setProperty(JaegerProperties.COLLECTOR_HTTP_PORT, String.valueOf(collectorHttpPort()));
        System.setProperty(JaegerProperties.QUERY_UI_PORT, String.valueOf(queryUiPort()));
    }

    @Override
    public void initialize() {
        LOG.info("Trying to start the Jaeger container");
        container.start();

        registerProperties();
        LOG.info("Jaeger instance running at {} (OTLP gRPC: {}, OTLP HTTP: {}, UI: {})",
                host(), collectorGrpcEndpoint(), collectorHttpEndpoint(), queryUiUrl());
    }

    @Override
    public void shutdown() {
        LOG.info("Stopping the Jaeger container");
        container.stop();
    }

    @Override
    public JaegerContainer getContainer() {
        return container;
    }

    @Override
    public String host() {
        return container.getHost();
    }

    @Override
    public int collectorGrpcPort() {
        return container.getMappedPort(JaegerProperties.DEFAULT_COLLECTOR_GRPC_PORT);
    }

    @Override
    public int collectorHttpPort() {
        return container.getMappedPort(JaegerProperties.DEFAULT_COLLECTOR_HTTP_PORT);
    }

    @Override
    public int queryUiPort() {
        return container.getMappedPort(JaegerProperties.DEFAULT_QUERY_UI_PORT);
    }
}
