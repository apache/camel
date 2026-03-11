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

import java.time.Duration;

import org.apache.camel.test.infra.common.LocalPropertyResolver;
import org.apache.camel.test.infra.common.services.ContainerEnvironmentUtil;
import org.apache.camel.test.infra.common.services.ContainerService;
import org.apache.camel.test.infra.mongodb.common.MongoDBProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

/**
 * A TLS-enabled MongoDB container service using a standalone mongod with --tlsMode requireTLS. Uses pre-generated
 * self-signed certificates mounted from classpath resources.
 */
public class MongoDBLocalContainerTLSService implements MongoDBService, ContainerService<GenericContainer<?>> {

    private static final Logger LOG = LoggerFactory.getLogger(MongoDBLocalContainerTLSService.class);
    private static final int DEFAULT_MONGODB_PORT = 27017;
    private static final String CERT_RESOURCE_PATH = "org/apache/camel/test/infra/mongodb/services/ssl";

    private final GenericContainer<?> container;

    public MongoDBLocalContainerTLSService() {
        this(LocalPropertyResolver.getProperty(
                MongoDBLocalContainerInfraService.class, MongoDBProperties.MONGODB_CONTAINER));
    }

    public MongoDBLocalContainerTLSService(String imageName) {
        container = initContainer(imageName);
    }

    protected GenericContainer<?> initContainer(String imageName) {
        GenericContainer<?> c = new GenericContainer<>(imageName);

        boolean fixedPort = ContainerEnvironmentUtil.isFixedPort(this.getClass());
        ContainerEnvironmentUtil.configurePort(c, fixedPort, DEFAULT_MONGODB_PORT);

        c.withClasspathResourceMapping(CERT_RESOURCE_PATH, "/etc/mongodb/ssl", BindMode.READ_ONLY)
                .withCommand(
                        "mongod",
                        "--tlsMode", "requireTLS",
                        "--tlsCertificateKeyFile", "/etc/mongodb/ssl/server.pem",
                        "--tlsCAFile", "/etc/mongodb/ssl/ca.pem",
                        "--tlsAllowConnectionsWithoutCertificates",
                        "--bind_ip_all",
                        "--port", String.valueOf(DEFAULT_MONGODB_PORT))
                .waitingFor(
                        Wait.forLogMessage(".*Waiting for connections.*", 1)
                                .withStartupTimeout(Duration.ofSeconds(60)));

        return c;
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
        LOG.info("Trying to start the MongoDB TLS service");
        container.start();
        registerProperties();
        LOG.info("MongoDB TLS service running at {}", getReplicaSetUrl());
    }

    @Override
    public void shutdown() {
        LOG.info("Stopping the MongoDB TLS container");
        container.stop();
    }

    @Override
    public GenericContainer<?> getContainer() {
        return container;
    }
}
