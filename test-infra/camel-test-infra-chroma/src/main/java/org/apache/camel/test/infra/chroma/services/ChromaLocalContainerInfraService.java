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
package org.apache.camel.test.infra.chroma.services;

import org.apache.camel.spi.annotations.InfraService;
import org.apache.camel.test.infra.chroma.common.ChromaProperties;
import org.apache.camel.test.infra.common.LocalPropertyResolver;
import org.apache.camel.test.infra.common.services.ContainerEnvironmentUtil;
import org.apache.camel.test.infra.common.services.ContainerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.chromadb.ChromaDBContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.utility.DockerImageName;

@InfraService(service = ChromaInfraService.class,
              description = "Chroma Vector Database",
              serviceAlias = { "chroma" })
public class ChromaLocalContainerInfraService implements ChromaInfraService, ContainerService<ChromaDBContainer> {
    public static final int CHROMA_PORT = 8000;

    private static final Logger LOG = LoggerFactory.getLogger(ChromaLocalContainerInfraService.class);

    private final ChromaDBContainer container;

    public ChromaLocalContainerInfraService() {
        this(LocalPropertyResolver.getProperty(ChromaLocalContainerInfraService.class, ChromaProperties.CHROMA_CONTAINER));
    }

    public ChromaLocalContainerInfraService(String imageName) {
        this.container = initContainer(imageName, ContainerEnvironmentUtil.isFixedPort(this.getClass()));
        String name = ContainerEnvironmentUtil.containerName(this.getClass());
        if (name != null) {
            container.withCreateContainerCmdModifier(cmd -> cmd.withName(name));
        }
    }

    public ChromaLocalContainerInfraService(ChromaDBContainer container) {
        this.container = container;
    }

    private ChromaDBContainer initContainer(String imageName, boolean fixedPort) {
        class TestInfraChromaContainer extends ChromaDBContainer {
            public TestInfraChromaContainer() {
                super(DockerImageName.parse(imageName)
                        .asCompatibleSubstituteFor("chromadb/chroma"));

                if (fixedPort) {
                    addFixedExposedPort(CHROMA_PORT, CHROMA_PORT);
                }
            }
        }

        return new TestInfraChromaContainer();
    }

    @Override
    public void registerProperties() {
        System.setProperty(ChromaProperties.CHROMA_HOST, getHost());
        System.setProperty(ChromaProperties.CHROMA_PORT, String.valueOf(getPort()));
    }

    @Override
    public void initialize() {
        LOG.info("Trying to start the Chroma container");

        container.withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger(ChromaDBContainer.class)));
        container.start();

        registerProperties();

        LOG.info("Chroma instance running at {}:{}", getHost(), getPort());
    }

    @Override
    public void shutdown() {
        LOG.info("Stopping the Chroma container");
        container.stop();
    }

    @Override
    public ChromaDBContainer getContainer() {
        return container;
    }

    @Override
    public String getHost() {
        return container.getHost();
    }

    @Override
    public int getPort() {
        return container.getMappedPort(CHROMA_PORT);
    }
}
