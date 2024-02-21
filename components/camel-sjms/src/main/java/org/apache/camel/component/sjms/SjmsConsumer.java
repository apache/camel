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
package org.apache.camel.component.sjms;

import jakarta.jms.Connection;

import org.apache.camel.Endpoint;
import org.apache.camel.FailedToCreateConsumerException;
import org.apache.camel.Processor;
import org.apache.camel.Suspendable;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.service.ServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.sjms.SjmsHelper.closeConnection;

public class SjmsConsumer extends DefaultConsumer implements Suspendable {

    private static final Logger LOG = LoggerFactory.getLogger(SjmsConsumer.class);

    private volatile boolean initialized;
    private final MessageListenerContainer listenerContainer;

    public SjmsConsumer(Endpoint endpoint, Processor processor, MessageListenerContainer listenerContainer) {
        super(endpoint, processor);
        this.listenerContainer = listenerContainer;
    }

    @Override
    public SjmsEndpoint getEndpoint() {
        return (SjmsEndpoint) super.getEndpoint();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (getEndpoint().isAsyncStartListener()) {
            getEndpoint().getAsyncStartStopExecutorService().submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        prepareAndStartListenerContainer();
                    } catch (Exception e) {
                        LOG.warn("Error starting listener container on destination: {}. This exception will be ignored.",
                                getDestinationName(), e);
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

    @Override
    protected void doStop() throws Exception {
        if (listenerContainer != null) {

            if (getEndpoint().isAsyncStopListener()) {
                getEndpoint().getAsyncStartStopExecutorService().submit(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            stopAndDestroyListenerContainer();
                        } catch (Exception e) {
                            LOG.warn("Error stopping listener container on destination: {}. This exception will be ignored.",
                                    getDestinationName(), e);
                        }
                    }

                    @Override
                    public String toString() {
                        return "AsyncStopListenerTask[" + getDestinationName() + "]";
                    }
                });
            } else {
                stopAndDestroyListenerContainer();
            }
        }

        super.doStop();
    }

    protected void prepareAndStartListenerContainer() {
        listenerContainer.afterPropertiesConfigured(getEndpoint().getCamelContext());

        // only start listener if auto start is enabled or we are explicit invoking start later
        if (initialized || getEndpoint().isAutoStartup()) {
            // should we pre test connections before starting?
            if (getEndpoint().isTestConnectionOnStartup()) {
                testConnectionOnStartup();
            }
            startListenerContainer();
        }
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
            LOG.debug("Testing JMS Connection on startup for destination: {}", getDestinationName());
            Connection con = listenerContainer.getConnectionFactory().createConnection();
            closeConnection(con);
            LOG.debug("Successfully tested JMS Connection on startup for destination: {}", getDestinationName());
        } catch (Exception e) {
            String msg = "Cannot get JMS Connection on startup for destination " + getDestinationName();
            throw new FailedToCreateConsumerException(getEndpoint(), msg, e);
        }
    }

    private String getDestinationName() {
        return getEndpoint().getDestinationName();
    }

    /**
     * Starts the JMS listener container
     * <p/>
     * Can be used to start this consumer later if it was configured to not auto startup.
     */
    public void startListenerContainer() {
        LOG.trace("Starting listener container {} on destination {}", listenerContainer, getDestinationName());
        ServiceHelper.startService(listenerContainer);
        LOG.debug("Started listener container {} on destination {}", listenerContainer, getDestinationName());
    }

    protected void stopAndDestroyListenerContainer() {
        if (listenerContainer != null) {
            ServiceHelper.stopAndShutdownService(listenerContainer);
        }
        initialized = false;
    }

}
