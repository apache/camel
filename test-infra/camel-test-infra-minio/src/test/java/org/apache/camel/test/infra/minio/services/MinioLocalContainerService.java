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
package org.apache.camel.test.infra.minio.services;

import java.time.Duration;

import org.apache.camel.test.infra.common.LocalPropertyResolver;
import org.apache.camel.test.infra.common.services.ContainerService;
import org.apache.camel.test.infra.minio.common.MinioProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;

public class MinioLocalContainerService implements MinioService, ContainerService<GenericContainer> {
    public static final String CONTAINER_NAME = "minio";
    private static final String ACCESS_KEY;
    private static final String SECRET_KEY;
    private static final int BROKER_PORT = 9000;

    private static final Logger LOG = LoggerFactory.getLogger(MinioLocalContainerService.class);

    static {
        ACCESS_KEY = System.getProperty(MinioProperties.ACCESS_KEY, "testAccessKey");
        SECRET_KEY = System.getProperty(MinioProperties.SECRET_KEY, "testSecretKey");
    }

    private final GenericContainer container;

    public MinioLocalContainerService() {
        this(LocalPropertyResolver.getProperty(MinioLocalContainerService.class, MinioProperties.MINIO_CONTAINER));
    }

    public MinioLocalContainerService(String containerName) {
        container = initContainer(containerName, CONTAINER_NAME);
    }

    public MinioLocalContainerService(GenericContainer container) {
        this.container = container;
    }

    protected GenericContainer initContainer(String imageName, String containerName) {
        return new GenericContainer(imageName)
                .withNetworkAliases(containerName)
                .withEnv("MINIO_ACCESS_KEY", accessKey())
                .withEnv("MINIO_SECRET_KEY", secretKey())
                .withCommand("server /data")
                .withExposedPorts(BROKER_PORT)
                .waitingFor(new HttpWaitStrategy()
                        .forPath("/minio/health/live")
                        .forPort(BROKER_PORT)
                        .withStartupTimeout(Duration.ofSeconds(10)));
    }

    @Override
    public void registerProperties() {
        System.setProperty(MinioProperties.ACCESS_KEY, accessKey());
        System.setProperty(MinioProperties.SECRET_KEY, secretKey());
        System.setProperty(MinioProperties.SERVICE_HOST, host());
        System.setProperty(MinioProperties.SERVICE_PORT, String.valueOf(port()));
    }

    @Override
    public void initialize() {
        LOG.info("Trying to start the Minio container");
        container.start();

        registerProperties();

        LOG.info("Minio instance running at {}:{}", host(), port());
    }

    @Override
    public void shutdown() {
        LOG.info("Stopping the Minio container");
        container.stop();
    }

    @Override
    public GenericContainer getContainer() {
        return container;
    }

    @Override
    public String secretKey() {
        return SECRET_KEY;
    }

    @Override
    public String accessKey() {
        return ACCESS_KEY;
    }

    @Override
    public int port() {
        return container.getMappedPort(BROKER_PORT);
    }

    @Override
    public String host() {
        return container.getHost();
    }
}
