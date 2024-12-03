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
package org.apache.camel.component.sjms.support;

import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.camel.test.infra.artemis.services.ArtemisTestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A support class that builds up and tears down an ActiveMQ instance to be used for unit testing. This is meant for
 * tests that need exclusive access to the broker, so that they can manage their lifecycle and do other harmful
 * operations.
 */
public abstract class JmsExclusiveTestSupport extends JmsCommonTestSupport {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected abstract ArtemisTestService getService();

    @Override
    protected String getBrokerUri() {
        return getService().serviceAddress();
    }

    /**
     * Set up the Broker
     */
    @Override
    protected void doPreSetup() throws Exception {
        preTestCleanup();
        loadTestProperties();

        brokerUri = getBrokerUri();
    }

    @Override
    protected void setupFactoryExternal(ActiveMQConnectionFactory factory) {
        setupFactoryExternal(factory, getService());
    }

    public void reconnect() throws Exception {
        reconnect(0);
    }

    public void reconnect(int waitingMillis) throws Exception {
        log.info("Closing JMS Session");
        getSession().close();
        log.info("Closing JMS Connection");
        getConnection().stop();
        log.info("Stopping the ActiveMQ Broker");
        getService().restart();
        brokerUri = getService().serviceAddress();
        Thread.sleep(waitingMillis);
        ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(brokerUri);
        setupFactoryExternal(connectionFactory);

        connect();
    }

}
