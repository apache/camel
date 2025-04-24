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
package org.apache.camel.test.infra.weaviate.services;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;

import org.apache.camel.spi.annotations.InfraService;
import org.apache.camel.test.infra.common.LocalPropertyResolver;
import org.apache.camel.test.infra.common.services.ContainerService;
import org.apache.camel.test.infra.weaviate.common.WeaviateProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.weaviate.WeaviateContainer;

@InfraService(service = WeaviateInfraService.class,
              description = "Weaviate Vector Database",
              serviceAlias = { "weaviate" })
public class WeaviateLocalContainerInfraService implements WeaviateInfraService, ContainerService<WeaviateContainer> {

    private static final Logger LOG = LoggerFactory.getLogger(WeaviateLocalContainerInfraService.class);

    private final WeaviateContainer container;

    public WeaviateLocalContainerInfraService() {
        this(LocalPropertyResolver.getProperty(WeaviateLocalContainerInfraService.class,
                WeaviateProperties.WEAVIATE_CONTAINER));
    }

    public WeaviateLocalContainerInfraService(String imageName) {
        container = initContainer(imageName);
    }

    public WeaviateLocalContainerInfraService(WeaviateContainer container) {
        this.container = container;
    }

    protected WeaviateContainer initContainer(String imageName) {
        return new WeaviateContainer(DockerImageName.parse(imageName).asCompatibleSubstituteFor("semitechnologies/weaviate"))
                .withStartupTimeout(Duration.ofMinutes(3L));
    }

    @Override
    public void registerProperties() {
        System.setProperty(WeaviateProperties.WEAVIATE_ENDPOINT_URL, getWeaviateEndpointUrl());
        System.setProperty(WeaviateProperties.WEAVIATE_ENDPOINT_HOST, getWeaviateHost());
        System.setProperty(WeaviateProperties.WEAVIATE_ENDPOINT_PORT, String.valueOf(getWeaviatePort()));
    }

    @Override
    public void initialize() {
        LOG.info("Trying to start the Weaviate container");
        container.start();

        registerProperties();
        LOG.info("Weaviate instance running at {}", getWeaviateEndpointUrl());
    }

    @Override
    public void shutdown() {
        LOG.info("Stopping the Weaviate container");
        container.stop();
    }

    @Override
    public WeaviateContainer getContainer() {
        return container;
    }

    @Override
    public String getWeaviateEndpointUrl() {
        return container.getHttpHostAddress();
    }

    @Override
    public String getWeaviateHost() {
        URL url = null;
        try {
            url = new URL("http://" + container.getHttpHostAddress());
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
        return url.getHost();
    }

    @Override
    public int getWeaviatePort() {
        URL url = null;
        try {
            url = new URL("http://" + container.getHttpHostAddress());
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
        return url.getPort();
    }
}
