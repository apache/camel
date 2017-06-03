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
package org.apache.camel.component.ignite.events;

import java.util.Arrays;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultConsumer;
import org.apache.ignite.IgniteEvents;
import org.apache.ignite.events.Event;
import org.apache.ignite.lang.IgnitePredicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ignite Events consumer.
 */
public class IgniteEventsConsumer extends DefaultConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(IgniteEventsConsumer.class);

    private IgniteEventsEndpoint endpoint;
    private IgniteEvents events;
    private int[] eventTypes = new int[0];

    private IgnitePredicate<Event> predicate = new IgnitePredicate<Event>() {
        private static final long serialVersionUID = 6738594728074592726L;

        @Override
        public boolean apply(Event event) {
            Exchange exchange = endpoint.createExchange(ExchangePattern.InOnly);
            Message in = exchange.getIn();
            in.setBody(event);
            try {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Processing Ignite Event: {}.", event);
                }
                getAsyncProcessor().process(exchange, new AsyncCallback() {
                    @Override
                    public void done(boolean doneSync) {
                        // do nothing
                    }
                });
            } catch (Exception e) {
                LOG.error(String.format("Exception while processing Ignite Event: %s.", event), e);
            }
            return true;
        }
    };

    public IgniteEventsConsumer(IgniteEventsEndpoint endpoint, Processor processor, IgniteEvents events) {
        super(endpoint, processor);
        this.endpoint = endpoint;
        this.events = events;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (endpoint.getEvents() != null && endpoint.getEvents().size() > 0) {
            eventTypes = new int[endpoint.getEvents().size()];
            int counter = 0;
            for (Integer i : endpoint.getEvents()) {
                eventTypes[counter++] = i;
            }
        }

        events.localListen(predicate, eventTypes);
        
        LOG.info("Started local Ignite Events consumer for events: {}.", Arrays.asList(eventTypes));
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        events.stopLocalListen(predicate, eventTypes);
        
        LOG.info("Stopped local Ignite Events consumer for events: {}.", Arrays.asList(eventTypes));
    }

}
