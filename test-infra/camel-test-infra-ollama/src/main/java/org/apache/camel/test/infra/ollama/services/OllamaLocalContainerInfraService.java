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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import com.github.dockerjava.api.model.DeviceRequest;
import org.apache.camel.spi.annotations.InfraService;
import org.apache.camel.test.infra.common.LocalPropertyResolver;
import org.apache.camel.test.infra.common.services.ContainerEnvironmentUtil;
import org.apache.camel.test.infra.common.services.ContainerService;
import org.apache.camel.test.infra.ollama.commons.OllamaProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.BindMode;
import org.testcontainers.ollama.OllamaContainer;
import org.testcontainers.utility.DockerImageName;

@InfraService(
        service = OllamaInfraService.class,
        description = "Build and run LLMs with Ollama",
        serviceAlias = {"ollama"})
public class OllamaLocalContainerInfraService implements OllamaInfraService, ContainerService<OllamaContainer> {
    private static class DefaultServiceConfiguration implements OllamaServiceConfiguration {

        @Override
        public String modelName() {
            return LocalPropertyResolver.getProperty(OllamaLocalContainerInfraService.class, OllamaProperties.MODEL);
        }

        @Override
        public String apiKey() {
            return LocalPropertyResolver.getProperty(OllamaLocalContainerInfraService.class, OllamaProperties.API_KEY);
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(OllamaLocalContainerInfraService.class);

    public static final String CONTAINER_NAME =
            LocalPropertyResolver.getProperty(OllamaLocalContainerInfraService.class, OllamaProperties.CONTAINER);

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
        class TestInfraOllamaContainer extends OllamaContainer {
            public TestInfraOllamaContainer(boolean fixedPort) {
                super(DockerImageName.parse(CONTAINER_NAME).asCompatibleSubstituteFor("ollama/ollama"));

                // Add file system bind for Ollama data persistence
                String homeDir = System.getenv("HOME");
                if (homeDir != null) {
                    try {
                        Path ollamaDir = Paths.get(homeDir, ".camel-test", "ollama");
                        Files.createDirectories(ollamaDir);
                        withFileSystemBind(ollamaDir.toString(), "/root/.ollama", BindMode.READ_WRITE);
                        LOG.info("Binding host directory {} to container path /root/.ollama", ollamaDir);
                    } catch (IOException e) {
                        LOG.warn("Failed to create Ollama data directory, continuing without bind mount", e);
                    }
                }

                // Conditionally enable GPU support based on configuration
                String enableGpu = LocalPropertyResolver.getProperty(
                        OllamaLocalContainerInfraService.class, OllamaProperties.ENABLE_GPU);

                if ("enabled".equalsIgnoreCase(enableGpu)) {
                    LOG.info("Enabling GPU support for Ollama container");
                    withCreateContainerCmdModifier(cmd -> cmd.getHostConfig()
                            .withDeviceRequests(Arrays.asList(new DeviceRequest()
                                    .withCount(-1) // -1 means all GPUs
                                    .withCapabilities(Arrays.asList(Arrays.asList("gpu"))))));
                } else {
                    LOG.info("GPU support disabled");
                }

                if (fixedPort) {
                    addFixedExposedPort(11434, 11434);
                }
                String name = ContainerEnvironmentUtil.containerName(OllamaLocalContainerInfraService.this.getClass());
                if (name != null) {
                    withCreateContainerCmdModifier(cmd -> cmd.withName(name));
                }
            }
        }

        return new TestInfraOllamaContainer(ContainerEnvironmentUtil.isFixedPort(this.getClass()));
    }

    @Override
    public String getEndpoint() {
        return container.getEndpoint();
    }

    @Override
    public String getModel() {
        return modelName();
    }

    @Override
    public String modelName() {
        return configuration.modelName();
    }

    @Override
    public String baseUrl() {
        return container.getEndpoint();
    }

    @Override
    public String baseUrlV1() {
        return container.getEndpoint() + "/v1";
    }

    @Override
    public String apiKey() {
        return configuration.apiKey();
    }

    @Override
    public void registerProperties() {
        System.setProperty(OllamaProperties.ENDPOINT, getEndpoint());
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
