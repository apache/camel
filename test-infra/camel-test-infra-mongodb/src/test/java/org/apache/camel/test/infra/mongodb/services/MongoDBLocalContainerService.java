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

import org.apache.camel.test.infra.common.LocalPropertyResolver;
import org.apache.camel.test.infra.common.services.ContainerService;
import org.apache.camel.test.infra.mongodb.common.MongoDBProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

public class MongoDBLocalContainerService implements MongoDBService, ContainerService<MongoDBContainer> {
    private static final Logger LOG = LoggerFactory.getLogger(MongoDBLocalContainerService.class);
    private static final int DEFAULT_MONGODB_PORT = 27017;
    private final MongoDBContainer container;

    public MongoDBLocalContainerService() {
        this(LocalPropertyResolver.getProperty(MongoDBLocalContainerService.class, MongoDBProperties.MONGODB_CONTAINER));
    }

    public MongoDBLocalContainerService(String imageName) {
        container = initContainer(imageName);
    }

    public MongoDBLocalContainerService(MongoDBContainer container) {
        this.container = container;
    }

    protected MongoDBContainer initContainer(String imageName) {
        if (imageName == null || imageName.isEmpty()) {
            return new MongoDBContainer();
        } else {
            return new MongoDBContainer(
                    DockerImageName.parse(imageName).asCompatibleSubstituteFor("mongo"));
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
