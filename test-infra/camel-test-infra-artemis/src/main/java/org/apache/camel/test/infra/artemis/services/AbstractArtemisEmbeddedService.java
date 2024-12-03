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
import java.util.concurrent.ThreadLocalRandom;
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
import org.apache.camel.test.infra.artemis.common.ArtemisRunException;
import org.apache.camel.test.infra.artemis.common.ConnectionFactoryHelper;
import org.apache.camel.test.infra.messaging.services.ConnectionFactoryAware;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractArtemisEmbeddedService implements ArtemisService, ConnectionFactoryAware {

    protected static final Logger LOG = LoggerFactory.getLogger(AbstractArtemisEmbeddedService.class);
    private static final LongAdder BROKER_COUNT = new LongAdder();

    protected final EmbeddedActiveMQ embeddedBrokerService;
    private final Configuration artemisConfiguration;
    private Consumer<Configuration> customConfigurator;
    private final int port;

    public AbstractArtemisEmbeddedService() {
        this(AvailablePortFinder.getNextAvailable());
    }

    /**
     * This is needed for some tests that check reliability of the components by defining the port in advance, trying to
     * connect first starting the service later
     *
     * @param port the port to use
     */
    protected AbstractArtemisEmbeddedService(int port) {
        embeddedBrokerService = new EmbeddedActiveMQ();
        artemisConfiguration = new ConfigurationImpl();

        this.port = port;
    }

    private synchronized Configuration configure(int port) {
        final int brokerId = computeBrokerId();

        // Base configuration
        artemisConfiguration.setSecurityEnabled(false);

        final File instanceDir = createInstance(brokerId);

        artemisConfiguration.setBrokerInstance(instanceDir);
        artemisConfiguration.setJMXManagementEnabled(false);
        artemisConfiguration.setMaxDiskUsage(98);

        final Configuration config = configure(artemisConfiguration, port, brokerId);
        if (customConfigurator != null) {
            customConfigurator.accept(config);
        }

        return config;
    }

    /**
     * Computes the current broker ID to use.
     *
     * @return the broker ID to use
     */
    protected int computeBrokerId() {
        final int brokerId = BROKER_COUNT.intValue();
        BROKER_COUNT.increment();
        return brokerId;
    }

    private static File createInstance(int brokerId) {
        File instanceDir = null;
        final File target = new File("target");
        final File brokerDir = new File(target, "artemis");
        do {
            final String subPath = getRandomSubPath();

            instanceDir = new File(brokerDir, brokerId + "-" + subPath);
        } while (instanceDir.exists());

        return instanceDir;
    }

    private static String getRandomSubPath() {
        final int size = 12;

        return ThreadLocalRandom.current().ints(97, 122)
                .limit(size)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }

    protected abstract Configuration configure(Configuration artemisConfiguration, int port, int brokerId);

    public void customConfiguration(Consumer<Configuration> configurator) {
        this.customConfigurator = configurator;
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
    public synchronized void initialize() {
        try {
            if (embeddedBrokerService.getActiveMQServer() == null || !embeddedBrokerService.getActiveMQServer().isStarted()) {
                embeddedBrokerService.setConfiguration(configure(port));

                embeddedBrokerService.start();

                embeddedBrokerService.getActiveMQServer().waitForActivation(20, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            LOG.warn("Unable to start embedded Artemis broker: {}", e.getMessage(), e);
            throw new ArtemisRunException(e);
        }
    }

    @Override
    public void shutdown() {
        try {
            embeddedBrokerService.stop();
        } catch (Exception e) {
            LOG.warn("Unable to start embedded Artemis broker: {}", e.getMessage(), e);
            throw new ArtemisRunException(e);
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
        return embeddedBrokerService.getActiveMQServer().queueQuery(SimpleString.of(queueQuery));
    }
}
