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

package org.apache.camel.test.infra.messaging.services;

import java.util.function.Function;

import org.apache.camel.test.infra.common.services.ContainerService;
import org.apache.camel.test.infra.messaging.common.MessagingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;

/**
 * A specialized container that can be used to create message broker instances.
 */
public class MessagingLocalContainerService<T extends GenericContainer<T>> implements MessagingService, ContainerService<T> {
    private static final Logger LOG = LoggerFactory.getLogger(MessagingLocalContainerService.class);

    private final T container;
    private final Function<T, String> endpointFunction;

    public MessagingLocalContainerService(T container, Function<T, String> endpointFunction) {
        this.container = container;
        this.endpointFunction = endpointFunction;
    }

    @Override
    public T getContainer() {
        return container;
    }

    @Override
    public String defaultEndpoint() {
        return endpointFunction.apply(container);
    }

    @Override
    public void registerProperties() {
        System.setProperty(MessagingProperties.MESSAGING_BROKER_ADDRESS, defaultEndpoint());
    }

    @Override
    public void initialize() {
        LOG.info("Trying to start the message broker container");
        container.start();

        registerProperties();
        LOG.info("Message broker running at address {}", defaultEndpoint());
    }

    @Override
    public void shutdown() {
        LOG.info("Stopping message broker container");
        container.stop();
    }
}
