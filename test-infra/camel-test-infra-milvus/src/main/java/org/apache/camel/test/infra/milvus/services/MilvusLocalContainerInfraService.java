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
package org.apache.camel.test.infra.milvus.services;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;

import org.apache.camel.spi.annotations.InfraService;
import org.apache.camel.test.infra.common.LocalPropertyResolver;
import org.apache.camel.test.infra.common.services.ContainerService;
import org.apache.camel.test.infra.milvus.common.MilvusProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.milvus.MilvusContainer;
import org.testcontainers.utility.DockerImageName;

@InfraService(service = MilvusInfraService.class, serviceAlias = { "milvus" })
public class MilvusLocalContainerInfraService implements MilvusInfraService, ContainerService<MilvusContainer> {

    private static final Logger LOG = LoggerFactory.getLogger(MilvusLocalContainerInfraService.class);

    private final MilvusContainer container;

    public MilvusLocalContainerInfraService() {
        this(LocalPropertyResolver.getProperty(MilvusLocalContainerInfraService.class, MilvusProperties.MILVUS_CONTAINER));
    }

    public MilvusLocalContainerInfraService(String imageName) {
        container = initContainer(imageName);
    }

    public MilvusLocalContainerInfraService(MilvusContainer container) {
        this.container = container;
    }

    protected MilvusContainer initContainer(String imageName) {
        return new MilvusContainer(DockerImageName.parse(imageName).asCompatibleSubstituteFor("milvusdb/milvus"))
                .withStartupTimeout(Duration.ofMinutes(3L));
    }

    @Override
    public void registerProperties() {
        System.setProperty(MilvusProperties.MILVUS_ENDPOINT_URL, getMilvusEndpointUrl());
        System.setProperty(MilvusProperties.MILVUS_ENDPOINT_HOST, getMilvusHost());
        System.setProperty(MilvusProperties.MILVUS_ENDPOINT_PORT, String.valueOf(getMilvusPort()));
    }

    @Override
    public void initialize() {
        LOG.info("Trying to start the Milvus container");
        container.start();

        registerProperties();
        LOG.info("Milvus instance running at {}", getMilvusEndpointUrl());
    }

    @Override
    public void shutdown() {
        LOG.info("Stopping the Milvus container");
        container.stop();
    }

    @Override
    public MilvusContainer getContainer() {
        return container;
    }

    @Override
    public String getMilvusEndpointUrl() {
        return container.getEndpoint();
    }

    @Override
    public String getMilvusHost() {
        URL url = null;
        try {
            url = new URL(container.getEndpoint());
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
        return url.getHost();
    }

    @Override
    public int getMilvusPort() {
        URL url = null;
        try {
            url = new URL(container.getEndpoint());
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
        return url.getPort();
    }
}
