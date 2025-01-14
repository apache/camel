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
package org.apache.camel.test.infra.ollama.services;

import java.io.IOException;

import org.apache.camel.spi.annotations.InfraService;
import org.apache.camel.test.infra.common.LocalPropertyResolver;
import org.apache.camel.test.infra.common.services.ContainerService;
import org.apache.camel.test.infra.ollama.commons.OllamaProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.ollama.OllamaContainer;
import org.testcontainers.utility.DockerImageName;

@InfraService(service = OllamaInfraService.class,
              description = "Build and run LLMs with Ollama",
              serviceAlias = { "ollama" })
public class OllamaLocalContainerInfraService implements OllamaInfraService, ContainerService<OllamaContainer> {
    private static class DefaultServiceConfiguration implements OllamaServiceConfiguration {

        @Override
        public String modelName() {
            return LocalPropertyResolver.getProperty(OllamaLocalContainerInfraService.class, OllamaProperties.MODEL);
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(OllamaLocalContainerInfraService.class);

    public static final String CONTAINER_NAME = LocalPropertyResolver.getProperty(
            OllamaLocalContainerInfraService.class, OllamaProperties.CONTAINER);

    private final OllamaContainer container;
    private final OllamaServiceConfiguration configuration;

    public OllamaLocalContainerInfraService() {
        container = initContainer();

        configuration = new DefaultServiceConfiguration();
    }

    public OllamaLocalContainerInfraService(OllamaServiceConfiguration serviceConfiguration) {
        configuration = serviceConfiguration;
        container = initContainer();
    }

    protected OllamaContainer initContainer() {
        return new OllamaContainer(
                DockerImageName.parse(CONTAINER_NAME)
                        .asCompatibleSubstituteFor("ollama/ollama"));
    }

    @Override
    public String getEndpoint() {
        return container.getEndpoint();
    }

    @Override
    public String getModel() {
        return configuration.modelName();
    }

    @Override
    public void registerProperties() {
        System.setProperty(OllamaProperties.ENDPOINT, container.getEndpoint());
    }

    @Override
    public void initialize() {
        LOG.info("Trying to start the Ollama container");
        container.start();

        LOG.info("Pulling the model {}", getModel());
        try {
            container.execInContainer("ollama", "pull", getModel());
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }

        registerProperties();
        LOG.info("Ollama instance running at {}", getEndpoint());
    }

    @Override
    public void shutdown() {
        LOG.info("Stopping the Ollama container");
        container.stop();
    }

    @Override
    public OllamaContainer getContainer() {
        return container;
    }
}
