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

package org.apache.camel.test.infra.artemis.services;

import org.apache.activemq.artemis.core.server.QueueQueryResult;
import org.apache.camel.spi.annotations.InfraService;
import org.apache.camel.test.infra.common.services.ContainerEnvironmentUtil;
import org.apache.camel.test.infra.common.services.ContainerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@InfraService(
        service = ArtemisInfraService.class,
        description = "Apache Artemis is an open source message broker",
        serviceAlias = "artemis")
public class ArtemisAllInfraService implements ArtemisInfraService, ContainerService<ArtemisContainer> {
    private static final Logger LOG = LoggerFactory.getLogger(ArtemisAllInfraService.class);

    private final ArtemisContainer container;

    public ArtemisAllInfraService() {
        container = initContainer();
        String name = ContainerEnvironmentUtil.containerName(this.getClass());
        if (name != null) {
            container.withCreateContainerCmdModifier(cmd -> cmd.withName(name));
        }
    }

    protected ArtemisContainer initContainer() {
        return ArtemisContainer.withFixedPort();
    }

    @Override
    public String serviceAddress() {
        return "tcp://localhost:" + brokerPort();
    }

    @Override
    public String userName() {
        return "artemis";
    }

    @Override
    public String password() {
        return "artemis";
    }

    @Override
    public int brokerPort() {
        return container.getMappedPort(61616);
    }

    @Override
    public void restart() {}

    @Override
    public long countMessages(String queue) throws Exception {
        return 0;
    }

    @Override
    public QueueQueryResult getQueueQueryResult(String queueQuery) throws Exception {
        return null;
    }

    @Override
    public void initialize() {
        LOG.info("Trying to start the Artemis container");
        container.start();

        registerProperties();
        LOG.info("Artemis instance running at {}", serviceAddress());
    }

    @Override
    public void shutdown() {
        LOG.info("Stopping the Artemis container");
        container.stop();
    }

    @Override
    public ArtemisContainer getContainer() {
        return container;
    }

    @Override
    public String remoteURI() {
        return serviceAddress();
    }
}
