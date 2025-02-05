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
package org.apache.camel.test.infra.neo4j.services;

import java.time.Duration;

import org.apache.camel.spi.annotations.InfraService;
import org.apache.camel.test.infra.common.LocalPropertyResolver;
import org.apache.camel.test.infra.common.services.ContainerService;
import org.apache.camel.test.infra.neo4j.common.Neo4jProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.utility.DockerImageName;

@InfraService(service = Neo4jInfraService.class,
              description = "Neo4j Database",
              serviceAlias = { "neo4j" })
public class Neo4jLocalContainerInfraService implements Neo4jInfraService, ContainerService<Neo4jContainer> {

    private static final Logger LOG = LoggerFactory.getLogger(Neo4jLocalContainerInfraService.class);

    private static final String ADMIN_USER = "neo4j";

    private final Neo4jContainer container;

    public Neo4jLocalContainerInfraService() {
        this(LocalPropertyResolver.getProperty(Neo4jLocalContainerInfraService.class, Neo4jProperties.NEO4J_CONTAINER));
    }

    public Neo4jLocalContainerInfraService(String imageName) {
        container = initContainer(imageName);
    }

    public Neo4jLocalContainerInfraService(Neo4jContainer container) {
        this.container = container;
    }

    protected Neo4jContainer initContainer(String imageName) {
        return new Neo4jContainer<>(DockerImageName.parse(imageName).asCompatibleSubstituteFor("neo4j"))
                .withStartupTimeout(Duration.ofMinutes(3L))
                .withRandomPassword();
    }

    @Override
    public void registerProperties() {
        System.setProperty(Neo4jProperties.NEO4J_DATABASE_URI, getNeo4jDatabaseUri());
        System.setProperty(Neo4jProperties.NEO4J_DATABASE_USER, getNeo4jDatabaseUser());
        System.setProperty(Neo4jProperties.NEO4J_DATABASE_PASSWORD, getNeo4jDatabasePassword());
    }

    @Override
    public void initialize() {
        LOG.info("Trying to start the Neo4j container");
        container.start();

        registerProperties();
        LOG.info("Neo4j instance running at {}", getNeo4jDatabaseUri());
    }

    @Override
    public void shutdown() {
        LOG.info("Stopping the Neo4j container");
        container.stop();
    }

    @Override
    public Neo4jContainer getContainer() {
        return container;
    }

    @Override
    public String getNeo4jDatabaseUri() {
        return container.getBoltUrl();
    }

    @Override
    public String getNeo4jDatabaseUser() {
        return ADMIN_USER;
    }

    @Override
    public String getNeo4jDatabasePassword() {
        return container.getAdminPassword();
    }
}
