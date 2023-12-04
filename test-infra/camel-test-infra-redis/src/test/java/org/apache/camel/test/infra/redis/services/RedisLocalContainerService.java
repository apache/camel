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
package org.apache.camel.test.infra.redis.services;

import org.apache.camel.test.infra.common.LocalPropertyResolver;
import org.apache.camel.test.infra.common.services.ContainerService;
import org.apache.camel.test.infra.redis.common.RedisProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

public class RedisLocalContainerService implements RedisService, ContainerService<GenericContainer> {
    public static final String CONTAINER_NAME = "redis";

    private static final Logger LOG = LoggerFactory.getLogger(RedisLocalContainerService.class);

    private final GenericContainer container;

    public RedisLocalContainerService() {
        this(LocalPropertyResolver.getProperty(RedisLocalContainerService.class, RedisProperties.REDIS_CONTAINER));
    }

    public RedisLocalContainerService(String imageName) {
        container = initContainer(imageName, CONTAINER_NAME);
    }

    public RedisLocalContainerService(GenericContainer container) {
        this.container = container;
    }

    public GenericContainer initContainer(String imageName, String networkAlias) {
        return new GenericContainer<>(DockerImageName.parse(imageName))
                .withNetworkAliases(networkAlias)
                .withExposedPorts(RedisProperties.DEFAULT_PORT)
                .waitingFor(Wait.forListeningPort());
    }

    @Override
    public void registerProperties() {
        System.setProperty(RedisProperties.SERVICE_ADDRESS, getServiceAddress());
        System.setProperty(RedisProperties.PORT, String.valueOf(port()));
        System.setProperty(RedisProperties.HOST, host());
    }

    @Override
    public void initialize() {
        LOG.info("Trying to start the Redis container");
        container.start();

        registerProperties();
        LOG.info("Redis instance running at {}", getServiceAddress());
    }

    @Override
    public void shutdown() {
        LOG.info("Stopping the Redis container");
        container.stop();
    }

    @Override
    public GenericContainer getContainer() {
        return container;
    }

    @Override
    public String host() {
        return container.getHost();
    }

    @Override
    public int port() {
        return container.getMappedPort(RedisProperties.DEFAULT_PORT);
    }
}
