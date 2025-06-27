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
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

@InfraService(service = MongoDBInfraService.class,
              description = "MongoDB NoSql Database",
              serviceAlias = { "mongodb" })
public class MongoDBLocalContainerInfraService implements MongoDBInfraService, ContainerService<MongoDBContainer> {
    private static final Logger LOG = LoggerFactory.getLogger(MongoDBLocalContainerInfraService.class);
    private static final int DEFAULT_MONGODB_PORT = 27017;
    private final MongoDBContainer container;

    public MongoDBLocalContainerInfraService() {
        this(LocalPropertyResolver.getProperty(MongoDBLocalContainerInfraService.class, MongoDBProperties.MONGODB_CONTAINER));
    }

    public MongoDBLocalContainerInfraService(String imageName) {
        container = initContainer(imageName);
    }

    public MongoDBLocalContainerInfraService(MongoDBContainer container) {
        this.container = container;
    }

    protected MongoDBContainer initContainer(String imageName) {

        class TestInfraMongoDBContainer extends MongoDBContainer {
            public TestInfraMongoDBContainer(boolean fixedPort) {
                super();
                addPort(fixedPort);
            }

            public TestInfraMongoDBContainer(boolean fixedPort, String imageName) {
                super(DockerImageName.parse(imageName).asCompatibleSubstituteFor("mongo"));
                addPort(fixedPort);
            }

            private void addPort(boolean fixedPort) {
                if (fixedPort) {
                    addFixedExposedPort(27017, 27017);
                } else {
                    addExposedPort(27017);
                }
            }
        }

        if (imageName == null || imageName.isEmpty()) {
            return new TestInfraMongoDBContainer(ContainerEnvironmentUtil.isFixedPort(this.getClass()));
        } else {
            return new TestInfraMongoDBContainer(ContainerEnvironmentUtil.isFixedPort(this.getClass()), imageName);
        }
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
    }

    @Override
    public void shutdown() {
        LOG.info("Stopping the MongoDB container");
        container.stop();
    }

    @Override
    public MongoDBContainer getContainer() {
        return container;
    }
}
