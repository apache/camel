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
package org.apache.camel.test.infra.docling.services;

import java.time.Duration;

import org.apache.camel.spi.annotations.InfraService;
import org.apache.camel.test.infra.common.LocalPropertyResolver;
import org.apache.camel.test.infra.common.services.ContainerEnvironmentUtil;
import org.apache.camel.test.infra.common.services.ContainerService;
import org.apache.camel.test.infra.docling.common.DoclingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

@InfraService(service = DoclingInfraService.class,
              description = "Document processing and conversion service",
              serviceAlias = { "docling" })
public class DoclingLocalContainerInfraService implements DoclingInfraService, ContainerService<GenericContainer<?>> {

    private static final Logger LOG = LoggerFactory.getLogger(DoclingLocalContainerInfraService.class);

    private static final String DEFAULT_DOCLING_CONTAINER = "quay.io/docling-project/docling-serve:latest";
    private static final int DOCLING_PORT = 5001;

    private final GenericContainer<?> container;

    public DoclingLocalContainerInfraService() {
        this(LocalPropertyResolver.getProperty(DoclingLocalContainerInfraService.class,
                DoclingProperties.DOCLING_CONTAINER));
    }

    public DoclingLocalContainerInfraService(String imageName) {
        container = initContainer(imageName);
        String name = ContainerEnvironmentUtil.containerName(this.getClass());
        if (name != null) {
            container.withCreateContainerCmdModifier(cmd -> cmd.withName(name));
        }
    }

    public DoclingLocalContainerInfraService(GenericContainer<?> container) {
        this.container = container;
    }

    protected GenericContainer<?> initContainer(String imageName) {
        String doclingImage = imageName != null ? imageName : DEFAULT_DOCLING_CONTAINER;

        class TestInfraDoclingContainer extends GenericContainer<TestInfraDoclingContainer> {
            public TestInfraDoclingContainer(boolean fixedPort) {
                super(DockerImageName.parse(doclingImage));

                withExposedPorts(DOCLING_PORT)
                        .waitingFor(Wait.forListeningPorts(DOCLING_PORT))
                        .withStartupTimeout(Duration.ofMinutes(3L));

                if (fixedPort) {
                    addFixedExposedPort(DOCLING_PORT, DOCLING_PORT);
                }
            }
        }

        return new TestInfraDoclingContainer(ContainerEnvironmentUtil.isFixedPort(this.getClass()));
    }

    @Override
    public void registerProperties() {
        System.setProperty(DoclingProperties.DOCLING_SERVER_URL, doclingServerUrl());
    }

    @Override
    public void initialize() {
        LOG.info("Trying to start the Docling container");
        container.start();

        registerProperties();
        LOG.info("Docling instance running at {}", doclingServerUrl());
    }

    @Override
    public void shutdown() {
        LOG.info("Stopping the Docling container");
        container.stop();
    }

    @Override
    public GenericContainer<?> getContainer() {
        return container;
    }

    @Override
    public String doclingServerUrl() {
        return String.format("http://%s:%d", container.getHost(), container.getMappedPort(DOCLING_PORT));
    }
}
