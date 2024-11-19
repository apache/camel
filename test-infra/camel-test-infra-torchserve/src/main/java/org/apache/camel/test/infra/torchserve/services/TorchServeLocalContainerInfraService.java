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
package org.apache.camel.test.infra.torchserve.services;

import org.apache.camel.spi.annotations.InfraService;
import org.apache.camel.test.infra.common.LocalPropertyResolver;
import org.apache.camel.test.infra.common.services.ContainerService;
import org.apache.camel.test.infra.torchserve.common.TorchServeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

@InfraService(service = TorchServeInfraService.class, serviceAlias = { "torch-serve" })
public class TorchServeLocalContainerInfraService implements TorchServeInfraService, ContainerService<GenericContainer<?>> {
    private static final Logger LOG = LoggerFactory.getLogger(TorchServeLocalContainerInfraService.class);

    public static final int INFERENCE_PORT = 8080;
    public static final int MANAGEMENT_PORT = 8081;
    public static final int METRICS_PORT = 8082;

    private static final String CONTAINER_COMMAND
            = "torchserve --ncs --disable-token-auth --enable-model-api --model-store /home/model-server/model-store --models squeezenet1_1.mar";

    private final GenericContainer<?> container;

    public TorchServeLocalContainerInfraService() {
        String imageName = LocalPropertyResolver.getProperty(
                TorchServeLocalContainerInfraService.class,
                TorchServeProperties.TORCHSERVE_CONTAINER);

        container = initContainer(imageName);
    }

    @SuppressWarnings("resource")
    protected GenericContainer<?> initContainer(String imageName) {
        return new GenericContainer<>(DockerImageName.parse(imageName))
                .withExposedPorts(INFERENCE_PORT, MANAGEMENT_PORT, METRICS_PORT)
                .withCopyFileToContainer(
                        MountableFile.forClasspathResource("config.properties"),
                        "/home/model-server/config.properties")
                .withCopyFileToContainer(
                        MountableFile.forClasspathResource("models/squeezenet1_1.mar"),
                        "/home/model-server/model-store/squeezenet1_1.mar")
                .waitingFor(Wait.forListeningPorts(INFERENCE_PORT, MANAGEMENT_PORT, METRICS_PORT))
                .withCommand(CONTAINER_COMMAND);
    }

    @Override
    public void registerProperties() {
        System.setProperty(TorchServeProperties.TORCHSERVE_INFERENCE_PORT, String.valueOf(inferencePort()));
        System.setProperty(TorchServeProperties.TORCHSERVE_MANAGEMENT_PORT, String.valueOf(managementPort()));
        System.setProperty(TorchServeProperties.TORCHSERVE_METRICS_PORT, String.valueOf(metricsPort()));
    }

    @Override
    public void initialize() {
        LOG.info("Trying to start the TorchServe container");

        container.start();
        registerProperties();

        LOG.info("TorchServe instance running at {}, {} and {}", inferencePort(), managementPort(), metricsPort());
    }

    @Override
    public void shutdown() {
        LOG.info("Stopping the TorchServe container");
        container.stop();
    }

    @Override
    public GenericContainer<?> getContainer() {
        return container;
    }

    @Override
    public int inferencePort() {
        return container.getMappedPort(INFERENCE_PORT);
    }

    @Override
    public int managementPort() {
        return container.getMappedPort(MANAGEMENT_PORT);
    }

    @Override
    public int metricsPort() {
        return container.getMappedPort(METRICS_PORT);
    }
}
