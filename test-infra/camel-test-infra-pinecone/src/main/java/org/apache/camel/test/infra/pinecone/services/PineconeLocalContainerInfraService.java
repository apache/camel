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

package org.apache.camel.test.infra.pinecone.services;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.time.Duration;

import org.apache.camel.spi.annotations.InfraService;
import org.apache.camel.test.infra.common.LocalPropertyResolver;
import org.apache.camel.test.infra.common.services.ContainerEnvironmentUtil;
import org.apache.camel.test.infra.common.services.ContainerService;
import org.apache.camel.test.infra.pinecone.common.PineconeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.pinecone.PineconeLocalContainer;
import org.testcontainers.utility.DockerImageName;

@InfraService(
        service = PineconeInfraService.class,
        description = "Pinecone Vector Database",
        serviceAlias = {"pinecone"})
public class PineconeLocalContainerInfraService
        implements PineconeInfraService, ContainerService<PineconeLocalContainer> {

    private static final Logger LOG = LoggerFactory.getLogger(PineconeLocalContainerInfraService.class);

    private final PineconeLocalContainer container;

    public PineconeLocalContainerInfraService() {
        this(LocalPropertyResolver.getProperty(
                PineconeLocalContainerInfraService.class, PineconeProperties.PINECONE_CONTAINER));
    }

    public PineconeLocalContainerInfraService(String imageName) {
        container = initContainer(imageName);
    }

    public PineconeLocalContainerInfraService(PineconeLocalContainer container) {
        this.container = container;
    }

    protected PineconeLocalContainer initContainer(String imageName) {
        class TestInfraPineconeLocalContainer extends PineconeLocalContainer {
            public TestInfraPineconeLocalContainer(boolean fixedPort) {
                super(DockerImageName.parse(imageName).asCompatibleSubstituteFor("pinecone-io/pinecone-local"));

                withStartupTimeout(Duration.ofMinutes(3L));

                if (fixedPort) {
                    addFixedExposedPort(5080, 5080);
                }
            }
        }

        return new TestInfraPineconeLocalContainer(ContainerEnvironmentUtil.isFixedPort(this.getClass()));
    }

    @Override
    public void registerProperties() {
        System.setProperty(PineconeProperties.PINECONE_ENDPOINT_URL, getPineconeEndpointUrl());
        System.setProperty(PineconeProperties.PINECONE_ENDPOINT_HOST, getPineconeHost());
        System.setProperty(PineconeProperties.PINECONE_ENDPOINT_PORT, String.valueOf(getPineconePort()));
    }

    @Override
    public void initialize() {
        LOG.info("Trying to start the Pinecone container");
        container.start();

        registerProperties();
        LOG.info("Pinecone instance running at {}", getPineconeEndpointUrl());
    }

    @Override
    public void shutdown() {
        LOG.info("Stopping the Pinecone container");
        container.stop();
    }

    @Override
    public PineconeLocalContainer getContainer() {
        return container;
    }

    @Override
    public String getPineconeEndpointUrl() {
        return container.getEndpoint();
    }

    @Override
    public String getPineconeHost() {
        URL url = null;
        try {
            url = URI.create(container.getEndpoint()).toURL();
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
        return url.getHost();
    }

    @Override
    public int getPineconePort() {
        URL url = null;
        try {
            url = URI.create(container.getEndpoint()).toURL();
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
        return url.getPort();
    }
}
