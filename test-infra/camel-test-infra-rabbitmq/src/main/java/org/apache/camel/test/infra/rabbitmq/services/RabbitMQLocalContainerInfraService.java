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

package org.apache.camel.test.infra.rabbitmq.services;

import org.apache.camel.spi.annotations.InfraService;
import org.apache.camel.test.infra.common.LocalPropertyResolver;
import org.apache.camel.test.infra.common.services.ContainerService;
import org.apache.camel.test.infra.rabbitmq.common.RabbitMQProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

@InfraService(service = RabbitMQInfraService.class,
              description = "Messaging and streaming broker",
              serviceAlias = { "rabbitmq" })
public class RabbitMQLocalContainerInfraService implements RabbitMQInfraService, ContainerService<RabbitMQContainer> {
    private static final Logger LOG = LoggerFactory.getLogger(RabbitMQLocalContainerInfraService.class);

    private final RabbitMQContainer container;

    public RabbitMQLocalContainerInfraService() {
        this(LocalPropertyResolver.getProperty(RabbitMQLocalContainerInfraService.class,
                RabbitMQProperties.RABBITMQ_CONTAINER));
    }

    public RabbitMQLocalContainerInfraService(String imageName) {
        container = initContainer(imageName);
    }

    public RabbitMQLocalContainerInfraService(RabbitMQContainer container) {
        this.container = container;
    }

    protected RabbitMQContainer initContainer(String imageName) {
        return new RabbitMQContainer(DockerImageName.parse(imageName).asCompatibleSubstituteFor("rabbitmq"));
    }

    @Override
    public RabbitMQContainer getContainer() {
        return container;
    }

    @Override
    public ConnectionProperties connectionProperties() {
        return new ConnectionProperties() {
            @Override
            public String username() {
                return container.getAdminUsername();
            }

            @Override
            public String password() {
                return container.getAdminPassword();
            }

            @Override
            public String hostname() {
                return container.getHost();
            }

            @Override
            public int port() {
                return container.getAmqpPort();
            }
        };
    }

    @Override
    public String getAmqpUrl() {
        return container.getAmqpUrl();
    }

    public int getHttpPort() {
        return container.getHttpPort();
    }

    @Override
    public void registerProperties() {
        ConnectionProperties properties = connectionProperties();

        System.setProperty(RabbitMQProperties.RABBITMQ_USER_NAME, properties.username());
        System.setProperty(RabbitMQProperties.RABBITMQ_USER_PASSWORD, properties.password());
        System.setProperty(RabbitMQProperties.RABBITMQ_CONNECTION_HOSTNAME, properties.hostname());
        System.setProperty(RabbitMQProperties.RABBITMQ_CONNECTION_AMQP, String.valueOf(properties.port()));
    }

    @Override
    public void initialize() {
        LOG.info("Trying to start RabbitMQ container");
        container.withStartupAttempts(5);
        container.start();
        LOG.info("RabbitMQ container running on {}", container.getAmqpUrl());

        registerProperties();
    }

    @Override
    public void shutdown() {
        container.stop();
    }
}
