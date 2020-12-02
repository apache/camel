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
package org.apache.camel.test.infra.nsq.services;

import org.apache.camel.test.infra.common.services.ContainerService;
import org.apache.camel.test.infra.nsq.common.NsqProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.FixedHostPortGenericContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;

public class NsqLocalContainerService implements NsqService, ContainerService<GenericContainer> {
    protected static final String CONTAINER_NSQLOOKUPD_IMAGE = "nsqio/nsq:v1.2.0";
    protected static final String CONTAINER_NSQLOOKUPD_NAME = "nsqlookupd";

    protected static final String CONTAINER_NSQD_IMAGE = "nsqio/nsq:v1.2.0";
    protected static final String CONTAINER_NSQD_NAME = "nsqd";

    private static final Logger LOG = LoggerFactory.getLogger(NsqLocalContainerService.class);

    private GenericContainer nsqContainer;
    private GenericContainer nsqLookupContainer;
    private Network network = Network.newNetwork();

    public NsqLocalContainerService() {
        initContainers();
    }

    protected void initContainers() {
        nsqLookupContainer = new FixedHostPortGenericContainer<>(CONTAINER_NSQLOOKUPD_IMAGE)
                .withFixedExposedPort(4160, 4160)
                .withFixedExposedPort(4161, 4161)
                .withNetworkAliases(CONTAINER_NSQLOOKUPD_NAME)
                .withCommand("/nsqlookupd").withNetwork(network)
                .waitingFor(Wait.forLogMessage(".*TCP: listening on.*", 1));

        nsqContainer = new FixedHostPortGenericContainer<>(CONTAINER_NSQD_IMAGE)
                .withFixedExposedPort(4150, 4150)
                .withFixedExposedPort(4151, 4151)
                .withNetworkAliases(CONTAINER_NSQD_NAME)
                .withCommand(String.format("/nsqd --broadcast-address=%s --lookupd-tcp-address=%s:4160", "localhost",
                        CONTAINER_NSQLOOKUPD_NAME))
                .withNetwork(network)
                .waitingFor(Wait.forLogMessage(".*TCP: listening on.*", 1));
    }

    @Override
    public void registerProperties() {
        System.getProperty(NsqProperties.PRODUCER_URL, getNsqProducerUrl());
        System.getProperty(NsqProperties.CONSUMER_URL, getNsqConsumerUrl());
    }

    @Override
    public void initialize() {
        LOG.info("Trying to start the Nsq container");
        nsqLookupContainer.start();
        nsqContainer.start();

        registerProperties();

        LOG.info("Nsq producer accessible via {}", getNsqProducerUrl());
        LOG.info("Nsq consumer accessible via {}", getNsqConsumerUrl());
    }

    @Override
    public void shutdown() {
        LOG.info("Stopping the Nsq container");
        nsqContainer.stop();
        nsqLookupContainer.stop();
    }

    @Override
    public GenericContainer getContainer() {
        return nsqContainer;
    }

    @Override
    public String getNsqProducerUrl() {
        return String.format("%s:%d", nsqContainer.getHost(), nsqContainer.getMappedPort(4150));
    }

    @Override
    public String getNsqConsumerUrl() {
        return String.format("%s:%d", nsqLookupContainer.getHost(), nsqLookupContainer.getMappedPort(4161));
    }
}
