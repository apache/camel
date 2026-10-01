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
package org.apache.camel.test.infra.openfga.services;

import org.apache.camel.spi.annotations.InfraService;
import org.apache.camel.test.infra.common.LocalPropertyResolver;
import org.apache.camel.test.infra.common.services.ContainerService;
import org.apache.camel.test.infra.openfga.common.OpenFgaProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

/**
 * Runs an OpenFGA server with the in-memory datastore and no stores in it. Tests create the store, the authorization
 * model and the relationship tuples they need through the OpenFGA API, so that this service stays independent of any
 * particular model.
 */
@InfraService(service = OpenFgaInfraService.class,
              description = "OpenFGA, a relationship-based fine-grained authorization engine",
              serviceAlias = { "openfga" })
public class OpenFgaLocalContainerInfraService implements OpenFgaInfraService, ContainerService<GenericContainer<?>> {
    public static final String CONTAINER_NAME = "openfga";
    public static final int OPENFGA_HTTP_PORT = 8080;

    private static final Logger LOG = LoggerFactory.getLogger(OpenFgaLocalContainerInfraService.class);

    private final GenericContainer<?> container;

    public OpenFgaLocalContainerInfraService() {
        this(LocalPropertyResolver.getProperty(
                OpenFgaLocalContainerInfraService.class,
                OpenFgaProperties.OPENFGA_CONTAINER));
    }

    public OpenFgaLocalContainerInfraService(String containerName) {
        container = initContainer(containerName, CONTAINER_NAME);
    }

    public OpenFgaLocalContainerInfraService(GenericContainer<?> container) {
        this.container = container;
    }

    @SuppressWarnings("resource")
    // NOTE: factoring method. The object must be closed by client.
    protected GenericContainer<?> initContainer(String imageName, String containerName) {
        return new GenericContainer<>(imageName) // NOSONAR
                .withNetworkAliases(containerName)
                .withExposedPorts(OPENFGA_HTTP_PORT)
                .withCommand("run")
                // /healthz is the gRPC health service behind the HTTP gateway and answers {"status":"SERVING"}; a 200
                // on its own arrives slightly before the server is ready to serve the Check API. The value is matched
                // whole, because the other states it reports include NOT_SERVING, which a search for SERVING finds too
                .waitingFor(Wait.forHttp("/healthz").forPort(OPENFGA_HTTP_PORT).forResponsePredicate(
                        body -> body != null && body.contains("\"SERVING\"")));
    }

    @Override
    public void registerProperties() {
        System.setProperty(OpenFgaProperties.OPENFGA_URL, getOpenFgaUrl());
        System.setProperty(OpenFgaProperties.OPENFGA_HOST, host());
        System.setProperty(OpenFgaProperties.OPENFGA_PORT, String.valueOf(port()));
    }

    @Override
    public void initialize() {
        LOG.info("Trying to start the OpenFGA container");
        container.start();

        registerProperties();
        LOG.info("OpenFGA instance running at {}", getOpenFgaUrl());
    }

    @Override
    public void shutdown() {
        LOG.info("Stopping the OpenFGA container");
        container.stop();
    }

    @Override
    public GenericContainer<?> getContainer() {
        return container;
    }

    @Override
    public String getOpenFgaUrl() {
        return String.format("http://%s:%d", host(), port());
    }

    @Override
    public String host() {
        return container.getHost();
    }

    @Override
    public int port() {
        return container.getMappedPort(OPENFGA_HTTP_PORT);
    }
}
