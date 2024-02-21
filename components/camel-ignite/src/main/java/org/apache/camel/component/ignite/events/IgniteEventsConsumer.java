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
package org.apache.camel.component.ignite.events;

import java.util.Arrays;
import java.util.List;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.support.DefaultConsumer;
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
            Exchange exchange = createExchange(true);
            Message in = exchange.getIn();
            in.setBody(event);
            try {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Processing Ignite Event: {}.", event);
                }
                // use default consumer callback
                AsyncCallback cb = defaultConsumerCallback(exchange, true);
                getAsyncProcessor().process(exchange, cb);
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

        List<Integer> ids = endpoint.getEventsAsIds();
        eventTypes = new int[ids.size()];
        int counter = 0;
        for (Integer i : ids) {
            eventTypes[counter++] = i;
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
