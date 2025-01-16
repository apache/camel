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
package org.apache.camel.test.infra.triton.services;

import org.apache.camel.test.infra.common.LocalPropertyResolver;
import org.apache.camel.test.infra.common.services.ContainerService;
import org.apache.camel.test.infra.triton.common.TritonProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

public class TritonLocalContainerInfraService implements TritonInfraService, ContainerService<GenericContainer<?>> {
    private static final Logger LOG = LoggerFactory.getLogger(TritonLocalContainerInfraService.class);

    public static final int HTTP_PORT = 8000;
    public static final int GRPC_PORT = 8001;
    public static final int METRICS_PORT = 8002;

    private static final String CONTAINER_COMMAND = "tritonserver --model-repository=/models";

    private final GenericContainer<?> container;

    public TritonLocalContainerInfraService() {
        String imageName = LocalPropertyResolver.getProperty(
                TritonLocalContainerInfraService.class,
                TritonProperties.TRITON_CONTAINER);

        container = initContainer(imageName);
    }

    @SuppressWarnings("resource")
    protected GenericContainer<?> initContainer(String imageName) {
        return new GenericContainer<>(DockerImageName.parse(imageName))
                .withExposedPorts(HTTP_PORT, GRPC_PORT, METRICS_PORT)
                .withCopyFileToContainer(MountableFile.forClasspathResource("models"), "/models")
                .waitingFor(Wait.forListeningPorts(HTTP_PORT, GRPC_PORT, METRICS_PORT))
                .withCommand(CONTAINER_COMMAND);
    }

    @Override
    public void registerProperties() {
        System.setProperty(TritonProperties.TRITON_HTTP_PORT, String.valueOf(httpPort()));
        System.setProperty(TritonProperties.TRITON_GPRC_PORT, String.valueOf(grpcPort()));
        System.setProperty(TritonProperties.TRITON_METRICS_PORT, String.valueOf(metricsPort()));
    }

    @Override
    public void initialize() {
        LOG.info("Trying to start the Triton Inference Server container");

        container.start();
        registerProperties();

        LOG.info("Triton Inference Server instance running at {}, {} and {}", httpPort(), grpcPort(), metricsPort());
    }

    @Override
    public void shutdown() {
        LOG.info("Stopping the Triton Inference Server container");
        container.stop();
    }

    @Override
    public GenericContainer<?> getContainer() {
        return container;
    }

    @Override
    public int httpPort() {
        return container.getMappedPort(HTTP_PORT);
    }

    @Override
    public int grpcPort() {
        return container.getMappedPort(GRPC_PORT);
    }

    @Override
    public int metricsPort() {
        return container.getMappedPort(METRICS_PORT);
    }
}
