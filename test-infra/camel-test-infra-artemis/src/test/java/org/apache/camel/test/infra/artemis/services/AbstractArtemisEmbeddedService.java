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

import java.io.File;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;

import jakarta.jms.ConnectionFactory;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.management.QueueControl;
import org.apache.activemq.artemis.api.core.management.ResourceNames;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.server.QueueQueryResult;
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.infra.artemis.common.ConnectionFactoryHelper;
import org.apache.camel.test.infra.messaging.services.ConnectionFactoryAware;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.fail;

public abstract class AbstractArtemisEmbeddedService implements ArtemisService, ConnectionFactoryAware {

    protected static final Logger LOG = LoggerFactory.getLogger(AbstractArtemisEmbeddedService.class);
    protected static final LongAdder BROKER_COUNT = new LongAdder();

    protected int tcpPort = AvailablePortFinder.getNextAvailable();
    protected EmbeddedActiveMQ embeddedBrokerService;
    private Configuration artemisConfiguration;

    public AbstractArtemisEmbeddedService() {
        defaultConfiguration();

        embeddedBrokerService.setConfiguration(getConfiguration(artemisConfiguration, AvailablePortFinder.getNextAvailable()));
    }

    public AbstractArtemisEmbeddedService(int port) {
        defaultConfiguration();

        embeddedBrokerService.setConfiguration(getConfiguration(artemisConfiguration, port));
    }

    private void defaultConfiguration() {
        embeddedBrokerService = new EmbeddedActiveMQ();

        // Base configuration
        artemisConfiguration = new ConfigurationImpl();
        artemisConfiguration.setSecurityEnabled(false);
        BROKER_COUNT.increment();
        artemisConfiguration.setBrokerInstance(new File("target", "artemis-" + BROKER_COUNT.intValue()));
        artemisConfiguration.setJMXManagementEnabled(false);
        artemisConfiguration.setMaxDiskUsage(98);
    }

    protected abstract Configuration getConfiguration(Configuration artemisConfiguration, int port);

    public void customConfiguration(Consumer<Configuration> configuration) {
        configuration.accept(artemisConfiguration);
    }

    @Override
    public long countMessages(String queue) throws Exception {
        QueueControl coreQueueControl = (QueueControl) embeddedBrokerService.getActiveMQServer().getManagementService()
                .getResource(ResourceNames.QUEUE + queue);

        return coreQueueControl.countMessages();
    }

    @Override
    public String userName() {
        return null;
    }

    @Override
    public String password() {
        return null;
    }

    @Override
    public void restart() {

    }

    @Override
    public void initialize() {
        try {
            embeddedBrokerService.start();
            embeddedBrokerService.getActiveMQServer().waitForActivation(20, TimeUnit.SECONDS);
        } catch (Exception e) {
            LOG.warn("Unable to start embedded Artemis broker: {}", e.getMessage(), e);
            fail(e.getMessage());
        }
    }

    @Override
    public void shutdown() {
        try {
            embeddedBrokerService.stop();
        } catch (Exception e) {
            LOG.warn("Unable to start embedded Artemis broker: {}", e.getMessage(), e);
            fail(e.getMessage());
        }
    }

    public EmbeddedActiveMQ getEmbeddedBrokerService() {
        return embeddedBrokerService;
    }

    @Override
    public ConnectionFactory createConnectionFactory() {
        return ConnectionFactoryHelper.createConnectionFactory(this);
    }

    @Override
    public ConnectionFactory createConnectionFactory(Integer maximumRedeliveries) {
        return ConnectionFactoryHelper.createConnectionFactory(this, maximumRedeliveries);
    }

    @Override
    public QueueQueryResult getQueueQueryResult(String queueQuery) throws Exception {
        return embeddedBrokerService.getActiveMQServer().queueQuery(new SimpleString(queueQuery));
    }
}
