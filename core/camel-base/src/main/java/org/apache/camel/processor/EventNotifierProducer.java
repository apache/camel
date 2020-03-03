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
package org.apache.camel.processor;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProducer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Producer;
import org.apache.camel.support.AsyncProcessorConverterHelper;
import org.apache.camel.support.DefaultAsyncProducer;
import org.apache.camel.support.EventHelper;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ensures a {@link Producer} do send {@link org.apache.camel.spi.EventNotifier} notifications when
 * sending.
 */
public final class EventNotifierProducer extends DefaultAsyncProducer {

    private static final Logger LOG = LoggerFactory.getLogger(EventNotifierProducer.class);

    private final AsyncProducer producer;

    /**
     * The producer which should be executed and emit {@link org.apache.camel.spi.EventNotifier} notifications.
     *
     * @param producer the producer
     */
    public EventNotifierProducer(Producer producer) {
        super(producer.getEndpoint());
        this.producer = AsyncProcessorConverterHelper.convert(producer);
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        if (!isStarted()) {
            exchange.setException(new IllegalStateException("Producer has not been started: " + this));
            callback.done(true);
            return true;
        }

        final boolean sending = EventHelper.notifyExchangeSending(exchange.getContext(), exchange, getEndpoint());
        // record timing for sending the exchange using the producer
        StopWatch watch;
        if (sending) {
            watch = new StopWatch();
        } else {
            watch = null;
        }

        try {
            LOG.debug(">>>> {} {}", getEndpoint(), exchange);
            return producer.process(exchange, new AsyncCallback() {
                @Override
                public void done(boolean doneSync) {
                    try {
                        // emit event that the exchange was sent to the endpoint
                        if (watch != null) {
                            long timeTaken = watch.taken();
                            EventHelper.notifyExchangeSent(exchange.getContext(), exchange, getEndpoint(), timeTaken);
                        }
                    } finally {
                        callback.done(doneSync);
                    }
                }
            });
        } catch (Throwable throwable) {
            exchange.setException(throwable);
            callback.done(true);
        }

        return true;
    }

    @Override
    public Endpoint getEndpoint() {
        return producer.getEndpoint();
    }

    @Override
    public boolean isSingleton() {
        return producer.isSingleton();
    }

    @Override
    protected void doStart() throws Exception {
        ServiceHelper.startService(producer);
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopService(producer);
    }
}
