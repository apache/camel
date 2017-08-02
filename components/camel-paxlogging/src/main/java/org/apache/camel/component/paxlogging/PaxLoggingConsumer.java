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
package org.apache.camel.component.paxlogging;

import java.util.Dictionary;
import java.util.Hashtable;
import java.util.concurrent.ExecutorService;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultConsumer;
import org.ops4j.pax.logging.spi.PaxAppender;
import org.ops4j.pax.logging.spi.PaxLoggingEvent;
import org.osgi.framework.ServiceRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Paxlogging consumer.
 * This camel consumer will register a paxlogging appender and will
 * receive all logging events and send them into the route.
 * To avoid generating new logging events from sending the message,
 * an MDC attribute is set in the sending thread, and all log events
 * from this thread are ignored.
 * Camel exchanges are actually sent from a specific thread to make
 * sure the log events are cleanly separated. 
 */
public class PaxLoggingConsumer extends DefaultConsumer implements PaxAppender {

    private static final Logger LOG = LoggerFactory.getLogger(PaxLoggingConsumer.class);
    private final PaxLoggingEndpoint endpoint;
    private ExecutorService executor;
    private ServiceRegistration registration;

    public PaxLoggingConsumer(PaxLoggingEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
    }

    public void doAppend(final PaxLoggingEvent paxLoggingEvent) {
        // in order to "force" the copy of properties (especially the MDC ones) in the local thread
        paxLoggingEvent.getProperties();
        sendExchange(paxLoggingEvent);
    }

    protected void sendExchange(final PaxLoggingEvent paxLoggingEvent) {
        Exchange exchange = endpoint.createExchange();
        // TODO: populate exchange headers
        exchange.getIn().setBody(paxLoggingEvent);

        if (LOG.isTraceEnabled()) {
            LOG.trace("PaxLogging {} is firing", endpoint.getAppender());
        }
        try {
            getProcessor().process(exchange);
        } catch (Exception e) {
            exchange.setException(e);
        }
        // log exception if an exception occurred and was not handled
        if (exchange.getException() != null) {
            getExceptionHandler().handleException("Error processing exchange", exchange, exchange.getException());
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        // start the executor before the registration
        executor = endpoint.getCamelContext().getExecutorServiceManager().newSingleThreadExecutor(this, "PaxLoggingEventTask");

        Dictionary<String, String> props = new Hashtable<String, String>();
        props.put("org.ops4j.pax.logging.appender.name", endpoint.getAppender());
        registration = endpoint.getComponent().getBundleContext().registerService(PaxAppender.class.getName(), this, props);
    }

    @Override
    protected void doStop() throws Exception {
        if (registration != null) {
            registration.unregister();
        }
        if (executor != null) {
            endpoint.getCamelContext().getExecutorServiceManager().shutdownNow(executor);
            executor = null;
        }
        super.doStop();
    }
}
