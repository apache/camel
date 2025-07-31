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
package org.apache.camel.test.infra.iggy.services;

import org.apache.camel.spi.annotations.InfraService;
import org.apache.camel.test.infra.common.LocalPropertyResolver;
import org.apache.camel.test.infra.common.services.ContainerEnvironmentUtil;
import org.apache.camel.test.infra.common.services.ContainerService;
import org.apache.camel.test.infra.iggy.common.IggyProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@InfraService(service = IggyInfraService.class,
              description = "Iggy distributed message streaming platform",
              serviceAlias = { "iggy" })
public class IggyLocalContainerInfraService implements IggyInfraService, ContainerService<IggyContainer> {
    private static final Logger LOG = LoggerFactory.getLogger(IggyLocalContainerInfraService.class);

    private final IggyContainer container;

    public IggyLocalContainerInfraService() {
        container = IggyContainer.initContainer(LocalPropertyResolver
                .getProperty(IggyLocalContainerInfraService.class, IggyProperties.IGGY_CONTAINER), IggyContainer.CONTAINER_NAME,
                ContainerEnvironmentUtil.isFixedPort(this.getClass()));
    }

    public IggyLocalContainerInfraService(String imageName) {
        container = IggyContainer.initContainer(imageName, IggyContainer.CONTAINER_NAME,
                ContainerEnvironmentUtil.isFixedPort(this.getClass()));
    }

    @Override
    public void registerProperties() {
        System.setProperty(IggyProperties.DEFAULT_USERNAME, username());
        System.setProperty(IggyProperties.DEFAULT_PASSWORD, password());
        System.setProperty(IggyProperties.IGGY_SERVICE_PORT, String.valueOf(port()));
        System.setProperty(IggyProperties.IGGY_SERVICE_HOST, host());
    }

    @Override
    public void initialize() {
        LOG.info("Trying to start the Iggy container");
        container.start();

        registerProperties();
        LOG.info("Iggy instance running at {}:{}", host(), port());
    }

    @Override
    public void shutdown() {
        LOG.info("Stopping the Iggy container");
        container.stop();
    }

    @Override
    public IggyContainer getContainer() {
        return container;
    }

    @Override
    public String host() {
        return container.getHost();
    }

    @Override
    public int port() {
        return container.getMappedPort(IggyProperties.DEFAULT_TCP_PORT);
    }

    @Override
    public String username() {
        return IggyProperties.DEFAULT_USERNAME;
    }

    @Override
    public String password() {
        return IggyProperties.DEFAULT_PASSWORD;
    }
}
