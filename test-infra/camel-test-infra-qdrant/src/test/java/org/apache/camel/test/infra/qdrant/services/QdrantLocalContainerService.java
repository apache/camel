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

import org.apache.camel.test.infra.common.LocalPropertyResolver;
import org.apache.camel.test.infra.common.services.ContainerService;
import org.apache.camel.test.infra.qdrant.common.QdrantProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.utility.DockerImageName;

public class QdrantLocalContainerService implements QdrantService, ContainerService<QdrantContainer> {
    private static final Logger LOG = LoggerFactory.getLogger(QdrantLocalContainerService.class);

    private final QdrantContainer container;

    public QdrantLocalContainerService() {
        this(LocalPropertyResolver.getProperty(QdrantLocalContainerService.class, QdrantProperties.QDRANT_CONTAINER));
    }

    public QdrantLocalContainerService(String imageName) {
        this(new QdrantContainer(DockerImageName.parse(imageName)));
    }

    public QdrantLocalContainerService(QdrantContainer container) {
        this.container = container;
    }

    @Override
    public void registerProperties() {
        System.setProperty(QdrantProperties.QDRANT_HTTP_HOST, getHttpHost());
        System.setProperty(QdrantProperties.QDRANT_HTTP_PORT, String.valueOf(getHttpPort()));
    }

    @Override
    public void initialize() {
        LOG.info("Trying to start the Qdrant container");

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
        return container.getHttpHost();
    }

    @Override
    public int getHttpPort() {
        return container.getHttpPort();
    }

    @Override
    public String getGrpcHost() {
        return container.getGrpcHost();
    }

    @Override
    public int getGrpcPort() {
        return container.getGrpcPort();
    }
}
