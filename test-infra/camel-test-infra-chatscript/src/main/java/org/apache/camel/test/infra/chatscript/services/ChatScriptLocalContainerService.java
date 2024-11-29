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
package org.apache.camel.test.infra.chatscript.services;

import org.apache.camel.test.infra.chatscript.common.ChatScriptProperties;
import org.apache.camel.test.infra.common.LocalPropertyResolver;
import org.apache.camel.test.infra.common.services.ContainerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;

public class ChatScriptLocalContainerService implements ChatScriptService, ContainerService<GenericContainer> {
    private static final Logger LOG = LoggerFactory.getLogger(ChatScriptLocalContainerService.class);
    private static final int SERVICE_PORT = 1024;
    private GenericContainer container;

    public ChatScriptLocalContainerService() {
        String containerName = LocalPropertyResolver.getProperty(
                ChatScriptLocalContainerService.class,
                ChatScriptProperties.CHATSCRIPT_CONTAINER);

        container = new GenericContainer<>(containerName)
                .withExposedPorts(SERVICE_PORT)
                .withCreateContainerCmdModifier(createContainerCmd -> createContainerCmd.withTty(true));
    }

    @Override
    public void registerProperties() {
        System.setProperty(ChatScriptProperties.CHATSCRIPT_ADDRESS, serviceAddress());
    }

    @Override
    public void initialize() {
        LOG.info("Trying to start the ChatScript container");
        container.start();
        registerProperties();

        LOG.info("ChatScript instance running at {}", serviceAddress());
    }

    @Override
    public void shutdown() {
        LOG.info("Stopping the ChatScript container");
        container.stop();
    }

    @Override
    public GenericContainer getContainer() {
        return container;
    }

    @Override
    public String serviceAddress() {
        return container.getHost() + ":" + container.getMappedPort(SERVICE_PORT);
    }
}
