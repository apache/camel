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

package org.apache.camel.test.infra.mongodb.services;

import org.apache.camel.spi.annotations.InfraService;
import org.apache.camel.test.infra.common.LocalPropertyResolver;
import org.apache.camel.test.infra.common.services.ContainerEnvironmentUtil;
import org.apache.camel.test.infra.common.services.ContainerService;
import org.apache.camel.test.infra.mongodb.common.MongoDBProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

@InfraService(service = MongoDBInfraService.class,
              description = "MongoDB is a document-oriented NoSQL database",
              serviceAlias = { "mongodb" }, uiSupported = true)
public class MongoDBLocalContainerInfraService implements MongoDBInfraService, ContainerService<MongoDBContainer> {
    private static final Logger LOG = LoggerFactory.getLogger(MongoDBLocalContainerInfraService.class);
    private static final int DEFAULT_MONGODB_PORT = 27017;
    private static final String MONGO_EXPRESS_CONTAINER_IMAGE = "mongo-express.container.image";
    private static final int MONGO_EXPRESS_PORT = 8081;

    private final MongoDBContainer container;
    private GenericContainer<?> uiContainer;

    public MongoDBLocalContainerInfraService() {
        this(LocalPropertyResolver.getProperty(MongoDBLocalContainerInfraService.class, MongoDBProperties.MONGODB_CONTAINER));
    }

    public MongoDBLocalContainerInfraService(String imageName) {
        container = initContainer(imageName);
        String name = ContainerEnvironmentUtil.containerName(this.getClass());
        if (name != null) {
            container.withCreateContainerCmdModifier(cmd -> cmd.withName(name));
        }
    }

    public MongoDBLocalContainerInfraService(MongoDBContainer container) {
        this.container = container;
    }

    protected MongoDBContainer initContainer(String imageName) {

        class TestInfraMongoDBContainer extends MongoDBContainer {

            public TestInfraMongoDBContainer(boolean fixedPort, String imageName) {
                super(DockerImageName.parse(imageName).asCompatibleSubstituteFor("mongo"));
                ContainerEnvironmentUtil.configurePort(this, fixedPort, DEFAULT_MONGODB_PORT);
                withReplicaSet();
            }
        }
        return new TestInfraMongoDBContainer(ContainerEnvironmentUtil.isFixedPort(this.getClass()), imageName);
    }

    @Override
    public String getReplicaSetUrl() {
        return String.format("mongodb://%s:%s", container.getHost(),
                container.getMappedPort(DEFAULT_MONGODB_PORT));
    }

    @Override
    public String getConnectionAddress() {
        return container.getHost() + ":" + container.getMappedPort(DEFAULT_MONGODB_PORT);
    }

    @Override
    public void registerProperties() {
        System.setProperty(MongoDBProperties.MONGODB_URL, getReplicaSetUrl());
        System.setProperty(MongoDBProperties.MONGODB_CONNECTION_ADDRESS, getConnectionAddress());
    }

    @Override
    public void initialize() {
        LOG.info("Trying to start the MongoDB service");
        container.start();
        registerProperties();
        LOG.info("MongoDB service running at {}", container.getReplicaSetUrl());

        if (ContainerEnvironmentUtil.isWithUi()) {
            try {
                String uiImage = LocalPropertyResolver.getProperty(
                        MongoDBLocalContainerInfraService.class, MONGO_EXPRESS_CONTAINER_IMAGE);
                int mongoPort = ContainerEnvironmentUtil.getConfiguredPort(DEFAULT_MONGODB_PORT);
                Testcontainers.exposeHostPorts(mongoPort);
                uiContainer = new GenericContainer<>(uiImage)
                        .withEnv("ME_CONFIG_MONGODB_URL",
                                "mongodb://host.testcontainers.internal:" + mongoPort + "/?directConnection=true")
                        .withEnv("ME_CONFIG_BASICAUTH", "false")
                        .withAccessToHost(true);
                ContainerEnvironmentUtil.configurePort(uiContainer, true, MONGO_EXPRESS_PORT);
                uiContainer.start();
                LOG.info("Mongo Express running at http://{}:{}", uiContainer.getHost(),
                        uiContainer.getMappedPort(MONGO_EXPRESS_PORT));
            } catch (Exception e) {
                LOG.warn("Failed to start Mongo Express UI container: {}", e.getMessage());
            }
        }
    }

    @Override
    public void shutdown() {
        if (uiContainer != null) {
            LOG.info("Stopping the Mongo Express container");
            uiContainer.stop();
        }
        LOG.info("Stopping the MongoDB container");
        container.stop();
    }

    @Override
    public String uiUrl() {
        if (uiContainer != null && uiContainer.isRunning()) {
            return String.format("http://%s:%d", uiContainer.getHost(), uiContainer.getMappedPort(MONGO_EXPRESS_PORT));
        }
        return null;
    }

    @Override
    public MongoDBContainer getContainer() {
        return container;
    }
}
