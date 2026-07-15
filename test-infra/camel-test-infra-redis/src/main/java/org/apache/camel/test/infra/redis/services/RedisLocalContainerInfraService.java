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

import org.apache.camel.spi.annotations.InfraService;
import org.apache.camel.test.infra.common.LocalPropertyResolver;
import org.apache.camel.test.infra.common.services.ContainerEnvironmentUtil;
import org.apache.camel.test.infra.common.services.ContainerService;
import org.apache.camel.test.infra.redis.common.RedisProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.GenericContainer;

@InfraService(service = RedisInfraService.class,
              description = "Redis is an open source in-memory data store",
              serviceAlias = { "redis" }, uiSupported = true)
public class RedisLocalContainerInfraService implements RedisInfraService, ContainerService<RedisContainer> {
    private static final Logger LOG = LoggerFactory.getLogger(RedisLocalContainerInfraService.class);
    private static final String REDIS_COMMANDER_CONTAINER_IMAGE = "redis-commander.container.image";
    private static final int REDIS_COMMANDER_PORT = 8082;

    private final RedisContainer container;
    private GenericContainer<?> uiContainer;

    public RedisLocalContainerInfraService() {
        container = initContainer();
        String name = ContainerEnvironmentUtil.containerName(this.getClass());
        if (name != null) {
            container.withCreateContainerCmdModifier(cmd -> cmd.withName(name));
        }
    }

    private RedisContainer initContainer() {
        RedisContainer container = new RedisContainer();
        if (ContainerEnvironmentUtil.isFixedPort(this.getClass())) {
            int port = ContainerEnvironmentUtil.getConfiguredPort(RedisProperties.DEFAULT_PORT);
            container.withFixedPort(port, RedisProperties.DEFAULT_PORT);
        }
        return container;
    }

    public RedisLocalContainerInfraService(String imageName) {
        container = RedisContainer.initContainer(imageName, RedisContainer.CONTAINER_NAME,
                ContainerEnvironmentUtil.isFixedPort(this.getClass()));
        String name = ContainerEnvironmentUtil.containerName(this.getClass());
        if (name != null) {
            container.withCreateContainerCmdModifier(cmd -> cmd.withName(name));
        }
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

        if (ContainerEnvironmentUtil.isWithUi()) {
            try {
                String uiImage = LocalPropertyResolver.getProperty(
                        RedisLocalContainerInfraService.class, REDIS_COMMANDER_CONTAINER_IMAGE);
                int redisPort = ContainerEnvironmentUtil.getConfiguredPort(RedisProperties.DEFAULT_PORT);
                Testcontainers.exposeHostPorts(redisPort);
                uiContainer = new GenericContainer<>(uiImage)
                        .withEnv("REDIS_HOSTS", "local:host.testcontainers.internal:" + redisPort)
                        .withEnv("PORT", String.valueOf(REDIS_COMMANDER_PORT))
                        .withAccessToHost(true);
                ContainerEnvironmentUtil.configurePort(uiContainer, true, REDIS_COMMANDER_PORT);
                uiContainer.start();
                LOG.info("Redis Commander running at http://{}:{}", uiContainer.getHost(),
                        uiContainer.getMappedPort(REDIS_COMMANDER_PORT));
            } catch (Exception e) {
                LOG.warn("Failed to start Redis Commander UI container: {}", e.getMessage());
            }
        }
    }

    @Override
    public void shutdown() {
        if (uiContainer != null) {
            LOG.info("Stopping the Redis Commander container");
            uiContainer.stop();
        }
        LOG.info("Stopping the Redis container");
        container.stop();
    }

    @Override
    public String uiUrl() {
        if (uiContainer != null && uiContainer.isRunning()) {
            return String.format("http://%s:%d", uiContainer.getHost(), uiContainer.getMappedPort(REDIS_COMMANDER_PORT));
        }
        return null;
    }

    @Override
    public RedisContainer getContainer() {
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
