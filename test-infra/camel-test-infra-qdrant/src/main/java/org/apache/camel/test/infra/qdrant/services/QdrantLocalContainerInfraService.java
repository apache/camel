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
package org.apache.camel.test.infra.qdrant.services;

import org.apache.camel.spi.annotations.InfraService;
import org.apache.camel.test.infra.common.LocalPropertyResolver;
import org.apache.camel.test.infra.common.services.ContainerEnvironmentUtil;
import org.apache.camel.test.infra.common.services.ContainerService;
import org.apache.camel.test.infra.qdrant.common.QdrantProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.qdrant.QdrantContainer;
import org.testcontainers.utility.DockerImageName;

@InfraService(service = QdrantInfraService.class,
              description = "Vector Database and Vector Search Engine",
              serviceAlias = { "qdrant" })
public class QdrantLocalContainerInfraService implements QdrantInfraService, ContainerService<QdrantContainer> {
    public static final int HTTP_PORT = 6333;
    public static final int GRPC_PORT = 6334;

    private static final Logger LOG = LoggerFactory.getLogger(QdrantLocalContainerInfraService.class);

    private final QdrantContainer container;

    public QdrantLocalContainerInfraService() {
        this(LocalPropertyResolver.getProperty(QdrantLocalContainerInfraService.class, QdrantProperties.QDRANT_CONTAINER));
    }

    public QdrantLocalContainerInfraService(String imageName) {
        this.container = initContainer(imageName, ContainerEnvironmentUtil.isFixedPort(this.getClass()));
        String name = ContainerEnvironmentUtil.containerName(this.getClass());
        if (name != null) {
            container.withCreateContainerCmdModifier(cmd -> cmd.withName(name));
        }
    }

    public QdrantLocalContainerInfraService(QdrantContainer container) {
        this.container = container;
    }

    private QdrantContainer initContainer(String imageName, boolean fixedPort) {
        class TestInfraQdrantContainer extends QdrantContainer {
            public TestInfraQdrantContainer() {
                super(DockerImageName.parse(imageName)
                        .asCompatibleSubstituteFor("qdrant/qdrant"));

                ContainerEnvironmentUtil.configurePorts(this, fixedPort,
                        ContainerEnvironmentUtil.PortConfig.primary(HTTP_PORT),
                        ContainerEnvironmentUtil.PortConfig.secondary(GRPC_PORT));
            }
        }

        return new TestInfraQdrantContainer();
    }

    @Override
    public void registerProperties() {
        System.setProperty(QdrantProperties.QDRANT_HTTP_HOST, getHttpHost());
        System.setProperty(QdrantProperties.QDRANT_HTTP_PORT, String.valueOf(getHttpPort()));
    }

    @Override
    public void initialize() {
        LOG.info("Trying to start the Qdrant container");

        container.withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger(QdrantContainer.class)));
        container.start();

        registerProperties();

        LOG.info("Qdrant instance running at {}:{}", getHttpHost(), getHttpPort());
    }

    @Override
    public void shutdown() {
        LOG.info("Stopping the Qdrant container");
        container.stop();
    }

    @Override
    public QdrantContainer getContainer() {
        return container;
    }

    @Override
    public String getHttpHost() {
        return container.getHost();
    }

    @Override
    public int getHttpPort() {
        return container.getMappedPort(HTTP_PORT);
    }

    @Override
    public String host() {
        return container.getHost();
    }

    @Override
    public int port() {
        return container.getMappedPort(GRPC_PORT);
    }
}
