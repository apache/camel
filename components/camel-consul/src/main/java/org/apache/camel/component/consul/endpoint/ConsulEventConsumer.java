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
package org.apache.camel.component.consul.endpoint;

import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.orbitz.consul.Consul;
import com.orbitz.consul.EventClient;
import com.orbitz.consul.async.EventResponseCallback;
import com.orbitz.consul.model.EventResponse;
import com.orbitz.consul.model.event.Event;
import com.orbitz.consul.option.QueryOptions;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.component.consul.ConsulConfiguration;
import org.apache.camel.component.consul.ConsulConstants;
import org.apache.camel.component.consul.ConsulEndpoint;
import org.apache.camel.spi.ExecutorServiceManager;
import org.slf4j.LoggerFactory;

public final class ConsulEventConsumer extends AbstractConsulConsumer<EventClient> {
    private final ExecutorServiceManager executorServiceManager;
    private ScheduledExecutorService scheduledExecutorService;

    public ConsulEventConsumer(ConsulEndpoint endpoint, ConsulConfiguration configuration, Processor processor) {
        super(endpoint, configuration, processor, Consul::eventClient);
        this.executorServiceManager = endpoint.getCamelContext().getExecutorServiceManager();
    }

    @Override
    protected Runnable createWatcher(EventClient client) throws Exception {
        return new EventWatcher(client);
    }

    @Override
    protected void doStart() throws Exception {
        this.scheduledExecutorService = this.executorServiceManager.newSingleThreadScheduledExecutor(this, "ConsulEventConsumer");
        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        if (this.scheduledExecutorService != null) {
            this.executorServiceManager.shutdownNow(scheduledExecutorService);
        }

        super.doStop();
    }

    // *************************************************************************
    // Watch
    // *************************************************************************

    private class EventWatcher extends AbstractWatcher implements EventResponseCallback {

        EventWatcher(EventClient client) {
            super(client);
        }

        @Override
        public void watch(final EventClient client) {
            scheduledExecutorService.schedule(new Runnable() {
                @Override
                public void run() {
                    client.listEvents(key, QueryOptions.blockSeconds(configuration.getBlockSeconds(), index.get()).build(), EventWatcher.this);
                }
            }, configuration.getBlockSeconds(), TimeUnit.SECONDS);
        }

        @Override
        public void onComplete(EventResponse eventResponse) {
            if (isRunAllowed()) {
                List<Event> events = filterEvents(eventResponse.getEvents(), index.get());
                events.forEach(this::onEvent);

                setIndex(eventResponse.getIndex());

                watch();
            }
        }

        @Override
        public void onFailure(Throwable throwable) {
            onError(throwable);
        }

        private void onEvent(Event event) {
            LoggerFactory.getLogger(ConsulEventConsumer.this.getClass()).info("{}", event);

            final Exchange exchange = endpoint.createExchange();
            final Message message = exchange.getIn();

            message.setHeader(ConsulConstants.CONSUL_KEY, key);
            message.setHeader(ConsulConstants.CONSUL_RESULT, true);
            message.setHeader(ConsulConstants.CONSUL_EVENT_ID, event.getId());
            message.setHeader(ConsulConstants.CONSUL_EVENT_NAME, event.getName());
            message.setHeader(ConsulConstants.CONSUL_EVENT_LTIME, event.getLTime());
            message.setHeader(ConsulConstants.CONSUL_VERSION, event.getVersion());

            if (event.getNodeFilter().isPresent()) {
                message.setHeader(ConsulConstants.CONSUL_NODE_FILTER, event.getNodeFilter().get());
            }
            if (event.getServiceFilter().isPresent()) {
                message.setHeader(ConsulConstants.CONSUL_SERVICE_FILTER, event.getServiceFilter().get());
            }
            if (event.getTagFilter().isPresent()) {
                message.setHeader(ConsulConstants.CONSUL_TAG_FILTER, event.getTagFilter().get());
            }

            message.setBody(event.getPayload().orElse(null));

            try {
                getProcessor().process(exchange);
            } catch (Exception e) {
                getExceptionHandler().handleException("Error processing exchange", exchange, e);
            }
        }

        /**
         * from spring-cloud-consul
         * (https://github.com/spring-cloud/spring-cloud-consul):
         * spring-cloud-consul-bus/src/main/java/org/springframework/cloud/consul/bus/EventService.java
         */
        private List<Event> filterEvents(List<Event> toFilter, BigInteger lastIndex) {
            List<Event> events = toFilter;
            if (lastIndex != null) {
                for (int i = 0; i < events.size(); i++) {
                    Event event = events.get(i);
                    BigInteger eventIndex = getEventIndexFromId(event);
                    if (eventIndex.equals(lastIndex)) {
                        events = events.subList(i + 1, events.size());
                        break;
                    }
                }
            }
            return events;
        }

        private BigInteger getEventIndexFromId(Event event) {
            String eventId = event.getId();
            String lower = eventId.substring(0, 8) + eventId.substring(9, 13) + eventId.substring(14, 18);
            String upper = eventId.substring(19, 23) + eventId.substring(24, 36);

            BigInteger lowVal = new BigInteger(lower, 16);
            BigInteger highVal = new BigInteger(upper, 16);

            return lowVal.xor(highVal);
        }
    }
}
