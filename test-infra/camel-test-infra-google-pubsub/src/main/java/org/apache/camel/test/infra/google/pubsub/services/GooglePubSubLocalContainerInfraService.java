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
package org.apache.camel.test.infra.google.pubsub.services;

import org.apache.camel.spi.annotations.InfraService;
import org.apache.camel.test.infra.common.LocalPropertyResolver;
import org.apache.camel.test.infra.common.services.ContainerEnvironmentUtil;
import org.apache.camel.test.infra.common.services.ContainerService;
import org.apache.camel.test.infra.google.pubsub.common.GooglePubSubProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.PubSubEmulatorContainer;
import org.testcontainers.utility.DockerImageName;

@InfraService(service = GooglePubSubInfraService.class,
              description = "Google Cloud SDK Tool",
              serviceAlias = "google",
              serviceImplementationAlias = "pub-sub")
public class GooglePubSubLocalContainerInfraService
        implements GooglePubSubInfraService, ContainerService<PubSubEmulatorContainer> {

    public static final String PROJECT_ID;
    private static final Logger LOG = LoggerFactory.getLogger(GooglePubSubLocalContainerInfraService.class);
    private static final String DEFAULT_PROJECT_ID = "test-project";
    private static final int PORT = 8085;

    static {
        PROJECT_ID = System.getProperty(GooglePubSubProperties.PROJECT_ID, DEFAULT_PROJECT_ID);
    }

    private final PubSubEmulatorContainer container;

    public GooglePubSubLocalContainerInfraService() {
        this(LocalPropertyResolver.getProperty(
                GooglePubSubLocalContainerInfraService.class,
                GooglePubSubProperties.PUBSUB_CONTAINER));
    }

    public GooglePubSubLocalContainerInfraService(String imageName) {
        container = initContainer(imageName);
    }

    public GooglePubSubLocalContainerInfraService(PubSubEmulatorContainer container) {
        this.container = container;
    }

    protected PubSubEmulatorContainer initContainer(String imageName) {
        class TestInfraPubSubEmulatorContainer extends PubSubEmulatorContainer {
            public TestInfraPubSubEmulatorContainer(boolean fixedPort) {
                super(DockerImageName.parse(imageName));

                if (fixedPort) {
                    addFixedExposedPort(PORT, PORT);
                } else {
                    addExposedPort(PORT);
                }
            }
        }
        return new TestInfraPubSubEmulatorContainer(ContainerEnvironmentUtil.isFixedPort(this.getClass()));
    }

    @Override
    public void registerProperties() {
        System.setProperty(GooglePubSubProperties.SERVICE_ADDRESS, getServiceAddress());
    }

    @Override
    public void initialize() {
        LOG.info("Trying to start the GooglePubSub container");
        container.start();

        registerProperties();

        LOG.info("GooglePubSub instance running at {}", getServiceAddress());
    }

    @Override
    public void shutdown() {
        LOG.info("Stopping the GooglePubSub container");
        container.stop();
    }

    @Override
    public PubSubEmulatorContainer getContainer() {
        return container;
    }

    @Override
    public String getServiceAddress() {
        return String.format("%s:%d", container.getHost(), container.getFirstMappedPort());
    }
}
