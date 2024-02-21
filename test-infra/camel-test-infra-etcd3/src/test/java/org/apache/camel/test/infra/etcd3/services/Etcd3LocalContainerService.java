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
package org.apache.camel.test.infra.etcd3.services;

import java.util.List;
import java.util.UUID;

import io.etcd.jetcd.launcher.EtcdContainer;
import org.apache.camel.test.infra.common.LocalPropertyResolver;
import org.apache.camel.test.infra.common.services.ContainerService;
import org.apache.camel.test.infra.etcd3.common.Etcd3Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.wait.strategy.Wait;

public class Etcd3LocalContainerService implements Etcd3Service, ContainerService<EtcdContainer> {
    public static final String CONTAINER_NAME = "etcd";
    public static final int ETCD_CLIENT_PORT = 2379;
    public static final int ETCD_PEER_PORT = 2380;

    private static final Logger LOG = LoggerFactory.getLogger(Etcd3LocalContainerService.class);

    private final EtcdContainer container;

    public Etcd3LocalContainerService() {
        this(LocalPropertyResolver.getProperty(
                Etcd3LocalContainerService.class,
                Etcd3Properties.ETCD_CONTAINER));
    }

    public Etcd3LocalContainerService(String imageName) {
        container = initContainer(imageName, CONTAINER_NAME);
    }

    public Etcd3LocalContainerService(EtcdContainer container) {
        this.container = container;
    }

    public EtcdContainer initContainer(String imageName, String containerName) {
        return new EtcdContainer(imageName, CONTAINER_NAME, List.of(CONTAINER_NAME))
                .withNetworkAliases(containerName)
                .withClusterToken(UUID.randomUUID().toString())
                .withExposedPorts(ETCD_CLIENT_PORT, ETCD_PEER_PORT)
                .waitingFor(Wait.forListeningPort())
                .waitingFor(Wait.forLogMessage(".*ready to serve client requests.*", 1));
    }

    @Override
    public void registerProperties() {
        System.setProperty(Etcd3Properties.SERVICE_ADDRESS, getServiceAddress());
    }

    @Override
    public void initialize() {
        LOG.info("Trying to start the Etcd container");
        container.start();

        registerProperties();
        LOG.info("Etcd instance running at {}", getServiceAddress());
    }

    @Override
    public void shutdown() {
        LOG.info("Stopping the Etcd container");
        container.stop();
    }

    @Override
    public EtcdContainer getContainer() {
        return container;
    }

    @Override
    public String getServiceAddress() {
        return String.format("http://%s:%d", container.getHost(), container.getMappedPort(ETCD_CLIENT_PORT));
    }
}
