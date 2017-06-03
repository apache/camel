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
package org.apache.camel.component.eventadmin;

import java.util.Dictionary;
import java.util.Hashtable;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultConsumer;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventAdminConsumer extends DefaultConsumer implements EventHandler {

    private static final Logger LOG = LoggerFactory.getLogger(EventAdminConsumer.class);
    private final EventAdminEndpoint endpoint;
    private ServiceRegistration<?> registration;

    public EventAdminConsumer(EventAdminEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
    }

    public void handleEvent(Event event) {
        Exchange exchange = endpoint.createExchange();
        // TODO: populate exchange headers
        exchange.getIn().setBody(event);

        LOG.trace("EventAdmin {} is firing", endpoint.getTopic());
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
        Dictionary<String, String> props = new Hashtable<String, String>();
        props.put(EventConstants.EVENT_TOPIC, endpoint.getTopic());
        registration = endpoint.getComponent().getBundleContext().registerService(EventHandler.class.getName(), this, props);
    }

    @Override
    protected void doStop() throws Exception {
        if (registration != null) {
            registration.unregister();
        }
        super.doStop();
    }
}
