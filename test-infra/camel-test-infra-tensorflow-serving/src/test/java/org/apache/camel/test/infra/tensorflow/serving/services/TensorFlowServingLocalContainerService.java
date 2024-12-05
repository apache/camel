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
package org.apache.camel.test.infra.tensorflow.serving.services;

import org.apache.camel.test.infra.common.LocalPropertyResolver;
import org.apache.camel.test.infra.common.services.ContainerService;
import org.apache.camel.test.infra.tensorflow.serving.common.TensorFlowServingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

public class TensorFlowServingLocalContainerService implements TensorFlowServingService, ContainerService<GenericContainer<?>> {
    private static final Logger LOG = LoggerFactory.getLogger(TensorFlowServingLocalContainerService.class);

    public static final int GRPC_PORT = 8500;
    public static final int REST_PORT = 8501;

    private final GenericContainer<?> container;

    public TensorFlowServingLocalContainerService() {
        container = initContainer();
    }

    @SuppressWarnings("resource")
    protected GenericContainer<?> initContainer() {
        boolean isArm64 = System.getProperty("os.arch").equals("aarch64");
        String containerProp = isArm64
                ? TensorFlowServingProperties.TENSORFLOW_SERVING_CONTAINER_ARM64
                : TensorFlowServingProperties.TENSORFLOW_SERVING_CONTAINER;
        String imageName = LocalPropertyResolver.getProperty(TensorFlowServingLocalContainerService.class, containerProp);
        if (isArm64) {
            // Bitnami's TF Serving image supports ARM64
            return new GenericContainer<>(DockerImageName.parse(imageName))
                    .withExposedPorts(GRPC_PORT, REST_PORT)
                    .withCopyFileToContainer(
                            MountableFile.forClasspathResource("testdata/saved_model_half_plus_two_cpu"), "/bitnami/model-data")
                    .withEnv("TENSORFLOW_SERVING_MODEL_NAME", "half_plus_two")
                    .waitingFor(Wait.forListeningPorts(GRPC_PORT, REST_PORT));
        } else {
            return new GenericContainer<>(DockerImageName.parse(imageName))
                    .withExposedPorts(GRPC_PORT, REST_PORT)
                    .withCopyFileToContainer(
                            MountableFile.forClasspathResource("testdata/saved_model_half_plus_two_cpu"),
                            "/models/half_plus_two")
                    .withEnv("MODEL_NAME", "half_plus_two")
                    .waitingFor(Wait.forListeningPorts(GRPC_PORT, REST_PORT));
        }
    }

    @Override
    public void registerProperties() {
        System.setProperty(TensorFlowServingProperties.TENSORFLOW_SERVING_GRPC_PORT, String.valueOf(grpcPort()));
        System.setProperty(TensorFlowServingProperties.TENSORFLOW_SERVING_REST_PORT, String.valueOf(restPort()));
    }

    @Override
    public void initialize() {
        LOG.info("Trying to start the TensorFlow Serving container");

        container.start();
        registerProperties();

        LOG.info("TensorFlow Serving instance running at {} and {}", grpcPort(), restPort());
    }

    @Override
    public void shutdown() {
        LOG.info("Stopping the TensorFlow Serving container");
        container.stop();
    }

    @Override
    public GenericContainer<?> getContainer() {
        return container;
    }

    @Override
    public int grpcPort() {
        return container.getMappedPort(GRPC_PORT);
    }

    @Override
    public int restPort() {
        return container.getMappedPort(REST_PORT);
    }
}
