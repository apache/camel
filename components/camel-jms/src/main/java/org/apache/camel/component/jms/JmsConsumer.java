/**
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
package org.apache.camel.component.jms;

import javax.jms.Connection;

import org.apache.camel.FailedToCreateConsumerException;
import org.apache.camel.Processor;
import org.apache.camel.SuspendableService;
import org.apache.camel.impl.DefaultConsumer;
import org.springframework.jms.listener.AbstractMessageListenerContainer;
import org.springframework.jms.support.JmsUtils;

/**
 * A {@link org.apache.camel.Consumer} which uses Spring's {@link AbstractMessageListenerContainer} implementations
 * to consume JMS messages.
 *
 * @version
 * @see DefaultJmsMessageListenerContainer
 * @see SimpleJmsMessageListenerContainer
 */
public class JmsConsumer extends DefaultConsumer implements SuspendableService {
    private volatile AbstractMessageListenerContainer listenerContainer;
    private volatile EndpointMessageListener messageListener;
    private volatile boolean initialized;

    public JmsConsumer(JmsEndpoint endpoint, Processor processor, AbstractMessageListenerContainer listenerContainer) {
        super(endpoint, processor);
        this.listenerContainer = listenerContainer;
        this.listenerContainer.setMessageListener(getEndpointMessageListener());
    }

    public JmsEndpoint getEndpoint() {
        return (JmsEndpoint) super.getEndpoint();
    }

    public AbstractMessageListenerContainer getListenerContainer() throws Exception {
        if (listenerContainer == null) {
            createMessageListenerContainer();
        }
        return listenerContainer;
    }

    public EndpointMessageListener getEndpointMessageListener() {
        if (messageListener == null) {
            createMessageListener(getEndpoint(), getProcessor());
        }
        return messageListener;
    }

    protected void createMessageListener(JmsEndpoint endpoint, Processor processor) {
        messageListener = new EndpointMessageListener(endpoint, processor);
        getEndpoint().getConfiguration().configureMessageListener(messageListener);
        messageListener.setBinding(endpoint.getBinding());
        messageListener.setAsync(endpoint.getConfiguration().isAsyncConsumer());
    }

    protected void createMessageListenerContainer() throws Exception {
        listenerContainer = getEndpoint().createMessageListenerContainer();
        getEndpoint().configureListenerContainer(listenerContainer, this);
        listenerContainer.setMessageListener(getEndpointMessageListener());
    }

    /**
     * Starts the JMS listener container
     * <p/>
     * Can be used to start this consumer later if it was configured to not auto startup.
     */
    public void startListenerContainer() {
        log.trace("Starting listener container {} on destination {}", listenerContainer, getDestinationName());
        listenerContainer.start();
        log.debug("Started listener container {} on destination {}", listenerContainer, getDestinationName());
    }

    /**
     * Pre tests the connection before starting the listening.
     * <p/>
     * In case of connection failure the exception is thrown which prevents Camel from starting.
     *
     * @throws FailedToCreateConsumerException is thrown if testing the connection failed
     */
    protected void testConnectionOnStartup() throws FailedToCreateConsumerException {
        try {
            log.debug("Testing JMS Connection on startup for destination: {}", getDestinationName());
            Connection con = listenerContainer.getConnectionFactory().createConnection();
            JmsUtils.closeConnection(con);
            log.debug("Successfully tested JMS Connection on startup for destination: {}", getDestinationName());
        } catch (Exception e) {
            String msg = "Cannot get JMS Connection on startup for destination " + getDestinationName();
            throw new FailedToCreateConsumerException(getEndpoint(), msg, e);
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        // create listener container
        if (listenerContainer == null) {
            createMessageListenerContainer();
        }
        
        if (getEndpoint().getConfiguration().isAsyncStartListener()) {
            getEndpoint().getAsyncStartExecutorService().submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        prepareAndStartListenerContainer();
                    } catch (Throwable e) {
                        log.warn("Error starting listener container on destination: " + getDestinationName() + ". This exception will be ignored.", e);
                    }
                }

                @Override
                public String toString() {
                    return "AsyncStartListenerTask[" + getDestinationName() + "]";
                }
            });
        } else {
            prepareAndStartListenerContainer();
        }

        // mark as initialized for the first time
        initialized = true;
    }
    
    protected void prepareAndStartListenerContainer() {
        listenerContainer.afterPropertiesSet();

        // only start listener if auto start is enabled or we are explicit invoking start later
        if (initialized || getEndpoint().isAutoStartup()) {
            // should we pre test connections before starting?
            if (getEndpoint().isTestConnectionOnStartup()) {
                testConnectionOnStartup();
            }
            startListenerContainer();
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (listenerContainer != null) {
            listenerContainer.stop();
            listenerContainer.destroy();
            // TODO: The async destroy code does not work well see https://issues.apache.org/jira/browse/CAMEL-4309
            // getEndpoint().destroyMessageListenerContainer(listenerContainer);
        }

        // null container and listener so they are fully re created if this consumer is restarted
        // then we will use updated configuration from jms endpoint that may have been managed using JMX
        listenerContainer = null;
        messageListener = null;
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
        if (listenerContainer != null) {
            startListenerContainer();
        }
    }

    private String getDestinationName() {
        if (listenerContainer.getDestination() != null) {
            return listenerContainer.getDestination().toString();
        } else {
            return listenerContainer.getDestinationName();
        }
    }

}
