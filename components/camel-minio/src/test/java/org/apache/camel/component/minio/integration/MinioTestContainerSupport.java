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
package org.apache.camel.component.minio.integration;

import java.io.IOException;
import java.time.Duration;
import java.util.Properties;

import org.apache.camel.component.minio.MinioTestUtils;
import org.apache.camel.test.testcontainers.junit5.ContainerAwareTestSupport;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;

class MinioTestContainerSupport extends ContainerAwareTestSupport {
    static Properties properties;

    static {
        try {
            properties = MinioTestUtils.loadMinioPropertiesFile();
        } catch (IOException e) {
            LoggerFactory.getLogger(MinioTestContainerSupport.class)
                    .warn("I/O exception loading minio properties file: {}", e.getMessage(), e);
        }
    }

    static final String CONTAINER_IMAGE = "minio/minio:latest";
    static final String CONTAINER_NAME = "minio";
    static final String ACCESS_KEY = properties.getProperty("accessKey");
    static final String SECRET_KEY = properties.getProperty("secretKey");
    static final int BROKER_PORT = 9000;
    static final GenericContainer CONTAINER;

    static {
        CONTAINER = new GenericContainer<>(CONTAINER_IMAGE).withNetworkAliases(CONTAINER_NAME)
                .withEnv("MINIO_ACCESS_KEY", ACCESS_KEY)
                .withEnv("MINIO_SECRET_KEY", SECRET_KEY)
                .withCommand("server /data")
                .withExposedPorts(BROKER_PORT)
                .waitingFor(new HttpWaitStrategy()
                        .forPath("/minio/health/ready")
                        .forPort(BROKER_PORT)
                        .withStartupTimeout(Duration.ofSeconds(10)));

        CONTAINER.start();
    }
}
