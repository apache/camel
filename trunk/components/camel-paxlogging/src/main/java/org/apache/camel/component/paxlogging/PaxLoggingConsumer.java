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

import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

    private static final transient Logger LOG = LoggerFactory.getLogger(PaxLoggingConsumer.class);
    private final PaxLoggingEndpoint endpoint;
    private ExecutorService executor;
    private ServiceRegistration registration;

    public PaxLoggingConsumer(PaxLoggingEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
    }

    public void doAppend(final PaxLoggingEvent paxLoggingEvent) {
        executor.execute(new Runnable() {
            public void run() {
                sendExchange(paxLoggingEvent);
            }
        });
    }

    protected void sendExchange(PaxLoggingEvent paxLoggingEvent) {
        MDC.put(PaxLoggingConsumer.class.getName(), endpoint.getName());
        if (paxLoggingEvent.getProperties().containsKey(PaxLoggingConsumer.class.getName())) {
            return;
        }

        Exchange exchange = endpoint.createExchange();
        // TODO: populate exchange headers
        exchange.getIn().setBody(paxLoggingEvent);

        LOG.trace("PaxLogging {} is firing", endpoint.getName());
        try {
            getProcessor().process(exchange);
            // log exception if an exception occurred and was not handled
            if (exchange.getException() != null) {
                getExceptionHandler().handleException("Error processing exchange", exchange, exchange.getException());
            }
        } catch (Exception e) {
            getExceptionHandler().handleException("Error processing exchange", exchange, exchange.getException());
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        Properties props = new Properties();
        props.put("org.ops4j.pax.logging.appender.name", endpoint.getName());
        registration = endpoint.getComponent().getBundleContext().registerService(PaxAppender.class.getName(), this, props);
        executor = Executors.newSingleThreadExecutor();
    }

    @Override
    protected void doStop() throws Exception {
        if (registration != null) {
            registration.unregister();
        }
        executor.shutdown();
        super.doStop();
    }
}
