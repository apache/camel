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
package org.apache.camel.test.infra.etcd.services;

import org.apache.camel.test.infra.common.services.ContainerService;
import org.apache.camel.test.infra.etcd.common.EtcDProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

public class EtcDLocalContainerService implements EtcDService, ContainerService<GenericContainer> {
    public static final String CONTAINER_IMAGE = "quay.io/coreos/etcd:v2.3.8";
    public static final String CONTAINER_NAME = "etcd";
    public static final int ETCD_CLIENT_PORT = 2379;
    public static final int ETCD_PEER_PORT = 2380;

    private static final Logger LOG = LoggerFactory.getLogger(EtcDLocalContainerService.class);

    private GenericContainer container;

    public EtcDLocalContainerService() {
        String containerName = System.getProperty("etcd.container", CONTAINER_IMAGE);

        initContainer(containerName);
    }

    public EtcDLocalContainerService(String containerName) {
        initContainer(containerName);
    }

    protected void initContainer(String containerName) {
        container = new GenericContainer(containerName)
                .withNetworkAliases(CONTAINER_NAME)
                .withExposedPorts(ETCD_CLIENT_PORT, ETCD_PEER_PORT)
                .waitingFor(Wait.forLogMessage(".*etcdserver.*set the initial cluster version.*", 1))
                .withCommand(
                        "-name", CONTAINER_NAME + "-0",
                        "-advertise-client-urls",
                        "http://" + DockerClientFactory.instance().dockerHostIpAddress() + ":" + ETCD_CLIENT_PORT,
                        "-listen-client-urls", "http://0.0.0.0:" + ETCD_CLIENT_PORT);
    }

    @Override
    public void registerProperties() {
        System.setProperty(EtcDProperties.SERVICE_ADDRESS, getServiceAddress());
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
    public GenericContainer getContainer() {
        return container;
    }

    @Override
    public String getServiceAddress() {
        return String.format("http://%s:%d", container.getHost(), container.getMappedPort(ETCD_CLIENT_PORT));
    }
}
