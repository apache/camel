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
package org.apache.camel.component.springrabbit;

import org.apache.camel.Endpoint;
import org.apache.camel.FailedToCreateConsumerException;
import org.apache.camel.Processor;
import org.apache.camel.Suspendable;
import org.apache.camel.support.DefaultConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.RabbitUtils;
import org.springframework.amqp.rabbit.listener.AbstractMessageListenerContainer;

public class SpringRabbitMQConsumer extends DefaultConsumer implements Suspendable {

    private static final Logger LOG = LoggerFactory.getLogger(SpringRabbitMQConsumer.class);

    private AbstractMessageListenerContainer listenerContainer;
    private volatile EndpointMessageListener messageListener;
    private volatile boolean initialized;

    public SpringRabbitMQConsumer(Endpoint endpoint, Processor processor, AbstractMessageListenerContainer listenerContainer) {
        super(endpoint, processor);
        this.listenerContainer = listenerContainer;
        this.listenerContainer.setMessageListener(getEndpointMessageListener());
    }

    @Override
    public SpringRabbitMQEndpoint getEndpoint() {
        return (SpringRabbitMQEndpoint) super.getEndpoint();
    }

    public EndpointMessageListener getEndpointMessageListener() {
        if (messageListener == null) {
            createMessageListener(getEndpoint(), getProcessor());
        }
        return messageListener;
    }

    protected void createMessageListener(SpringRabbitMQEndpoint endpoint, Processor processor) {
        messageListener = new EndpointMessageListener(this, endpoint, processor);
        endpoint.configureMessageListener(messageListener);
    }

    /**
     * Starts the listener container
     * <p/>
     * Can be used to start this consumer later if it was configured to not auto startup.
     */
    public void startListenerContainer() {
        LOG.trace("Starting listener container {} on queues: {}", listenerContainer, getEndpoint().getQueues());
        listenerContainer.start();
        LOG.debug("Started listener container {} on queues: {}", listenerContainer, getEndpoint().getQueues());
    }

    /**
     * Pre tests the connection before starting the listening.
     * <p/>
     * In case of connection failure the exception is thrown which prevents Camel from starting.
     *
     * @throws FailedToCreateConsumerException is thrown if testing the connection failed
     */
    protected void testConnectionOnStartup() throws FailedToCreateConsumerException {
        Connection conn = null;
        try {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Testing RabbitMQ Connection on startup for: {}", getEndpoint().getConnectionFactory().getHost());
            }
            conn = listenerContainer.getConnectionFactory().createConnection();

            LOG.debug("Successfully tested RabbitMQ Connection on startup for: {}",
                    getEndpoint().getConnectionFactory().getHost());
        } catch (Exception e) {
            throw new FailedToCreateConsumerException(getEndpoint(), e);
        } finally {
            RabbitUtils.closeConnection(conn);
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        // create listener container
        if (listenerContainer == null) {
            createMessageListenerContainer();
        }

        prepareAndStartListenerContainer();

        // mark as initialized for the first time
        initialized = true;
    }

    @Override
    protected void doStop() throws Exception {
        if (listenerContainer != null) {
            stopAndDestroyListenerContainer();
        }

        super.doStop();
    }

    @Override
    protected void doSuspend() throws Exception {
        if (listenerContainer != null) {
            listenerContainer.stop();
        }
    }

    @Override
    protected void doResume() throws Exception {
        // we may not have been started before, and now the end user calls resume, so lets handle that and start it first
        if (!initialized) {
            doStart();
        } else {
            if (listenerContainer != null) {
                startListenerContainer();
            } else {
                LOG.warn(
                        "The listenerContainer is not instantiated. Probably there was a timeout during the Suspend operation. Please restart your consumer route.");
            }
        }
    }

    protected void createMessageListenerContainer() {
        listenerContainer = getEndpoint().createMessageListenerContainer();
        listenerContainer.setMessageListener(getEndpointMessageListener());
    }

    protected void prepareAndStartListenerContainer() {
        listenerContainer.afterPropertiesSet();

        if (getEndpoint().isAutoDeclare()) {
            // auto declare but without spring
            getEndpoint().declareElements(listenerContainer);
        }

        // only start listener if auto start is enabled or we are explicit invoking start later
        if (initialized || getEndpoint().isAutoStartup()) {
            // should we pre test connections before starting?
            if (getEndpoint().isTestConnectionOnStartup()) {
                testConnectionOnStartup();
            }
            startListenerContainer();
        }
    }

    protected void stopAndDestroyListenerContainer() {
        if (listenerContainer != null) {
            listenerContainer.stop();
            listenerContainer.destroy();
        }

        // null container and listener so they are fully re created if this consumer is restarted
        // then we will use updated configuration from jms endpoint that may have been managed using JMX
        listenerContainer = null;
        messageListener = null;
        initialized = false;
    }

}
