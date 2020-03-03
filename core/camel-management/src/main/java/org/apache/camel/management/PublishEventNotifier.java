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
package org.apache.camel.management;

import java.util.EventObject;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExtendedExchange;
import org.apache.camel.Producer;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.support.EventNotifierSupport;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link org.apache.camel.spi.EventNotifier} which publishes the {@link EventObject} to some
 * {@link org.apache.camel.Endpoint}.
 * <p/>
 * This notifier is only enabled when {@link CamelContext} is started. This avoids problems when
 * sending notifications during start/shutdown of {@link CamelContext} which causes problems by
 * sending those events to Camel routes by this notifier.
 */
public class PublishEventNotifier extends EventNotifierSupport implements CamelContextAware {

    private static final Logger LOG = LoggerFactory.getLogger(PublishEventNotifier.class);

    private CamelContext camelContext;
    private Endpoint endpoint;
    private String endpointUri;
    private Producer producer;

    @Override
    public void notify(CamelEvent event) throws Exception {
        // only notify when we are started
        if (!isStarted()) {
            LOG.debug("Cannot publish event as notifier is not started: {}", event);
            return;
        }

        // only notify when camel context is running
        if (!camelContext.getStatus().isStarted()) {
            LOG.debug("Cannot publish event as CamelContext is not started: {}", event);
            return;
        }

        Exchange exchange = producer.getEndpoint().createExchange();
        exchange.getIn().setBody(event);

        // make sure we don't send out events for this as well
        // mark exchange as being published to event, to prevent creating new events
        // for this as well (causing a endless flood of events)
        exchange.adapt(ExtendedExchange.class).setNotifyEvent(true);
        try {
            producer.process(exchange);
        } finally {
            // and remove it when its done
            exchange.adapt(ExtendedExchange.class).setNotifyEvent(false);
        }
    }

    @Override
    public boolean isEnabled(CamelEvent event) {
        return true;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public Endpoint getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(Endpoint endpoint) {
        this.endpoint = endpoint;
    }

    public String getEndpointUri() {
        return endpointUri;
    }

    public void setEndpointUri(String endpointUri) {
        this.endpointUri = endpointUri;
    }

    @Override
    protected void doStart() throws Exception {
        ObjectHelper.notNull(camelContext, "camelContext", this);
        if (endpoint == null && endpointUri == null) {
            throw new IllegalArgumentException("Either endpoint or endpointUri must be configured");
        }

        if (endpoint == null) {
            endpoint = camelContext.getEndpoint(endpointUri);
        }

        producer = endpoint.createProducer();
        ServiceHelper.startService(producer);
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopService(producer);
    }

    @Override
    public String toString() {
        return "PublishEventNotifier[" + (endpoint != null ? endpoint : URISupport.sanitizeUri(endpointUri)) + "]";
    }

}
