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
package org.apache.camel.component.direct;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.support.DefaultAsyncProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The direct producer.
 */
public class DirectProducer extends DefaultAsyncProducer {

    private static final Logger LOG = LoggerFactory.getLogger(DirectProducer.class);

    // the consumer and the state counter of the component when the consumer was looked up, kept together as several
    // threads can send with this producer at the same time
    private volatile CachedConsumer cachedConsumer;

    private final DirectEndpoint endpoint;
    private final DirectComponent component;
    private final String key;
    private final boolean block;
    private final long timeout;

    public DirectProducer(DirectEndpoint endpoint, String key) {
        super(endpoint);
        this.endpoint = endpoint;
        this.component = (DirectComponent) endpoint.getComponent();
        this.key = key;
        this.block = endpoint.isBlock();
        this.timeout = endpoint.getTimeout();
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        DirectConsumer consumer = getConsumer();
        if (consumer == null) {
            if (endpoint.isFailIfNoConsumers()) {
                throw new DirectConsumerNotAvailableException("No consumers available on endpoint: " + endpoint, exchange);
            } else {
                LOG.debug("message ignored, no consumers available on endpoint: {}", endpoint);
            }
        } else {
            consumer.getProcessor().process(exchange);
        }
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        try {
            // we may be forced synchronous
            if (endpoint.isSynchronous()) {
                process(exchange);
                callback.done(true);
                return true;
            }
            DirectConsumer consumer = getConsumer();
            if (consumer == null) {
                if (endpoint.isFailIfNoConsumers()) {
                    exchange.setException(new DirectConsumerNotAvailableException(
                            "No consumers available on endpoint: " + endpoint, exchange));
                } else {
                    LOG.debug("message ignored, no consumers available on endpoint: {}", endpoint);
                }
                callback.done(true);
                return true;
            } else {
                // the consumer may be forced synchronous
                if (consumer.getEndpoint().isSynchronous()) {
                    consumer.getProcessor().process(exchange);
                    callback.done(true);
                    return true;
                } else {
                    //Ensure we can close the CLIENT Scope created by this DirectProducer
                    //in the same thread
                    // Deprecated in 4.19.0
                    if (exchange.getProperty(ExchangePropertyKey.OTEL_ACTIVE_SPAN) != null) {
                        exchange.setProperty(ExchangePropertyKey.OTEL_CLOSE_CLIENT_SCOPE, Boolean.TRUE);
                    }
                    return consumer.getAsyncProcessor().process(exchange, callback);
                }
            }
        } catch (InterruptedException e) {
            // the only wait here is for a consumer to appear (block=true), and what interrupts it is a forced shutdown,
            // such as a dev mode reload that adds the consumer in the same edit (CAMEL-25365)
            LOG.info("Interrupted while waiting for a consumer on {}: the route is being stopped or reloaded",
                    endpoint.getEndpointUri());
            Thread.currentThread().interrupt();
            DirectConsumerNotAvailableException cause = new DirectConsumerNotAvailableException(
                    "No consumers available on endpoint: " + endpoint
                                                                                                + " (interrupted while waiting for one, as the route is being stopped or reloaded)",
                    exchange);
            // keep the interruption as the cause, so onException(InterruptedException.class) still matches
            cause.initCause(e);
            exchange.setException(cause);
            // stay marked as interrupted, as setException(InterruptedException) did, so the error handler stops
            // routing instead of handling a failure (onException, redelivery, dead letter channel, the ERROR log)
            exchange.getExchangeExtension().setInterrupted(true);
            callback.done(true);
            return true;
        } catch (Exception e) {
            exchange.setException(e);
            callback.done(true);
            return true;
        }
    }

    /**
     * Gets the consumer, which is looked up again when it has been added or removed (such as when its route is
     * suspended or stopped) since it was looked up last.
     */
    private DirectConsumer getConsumer() throws InterruptedException {
        CachedConsumer cached = cachedConsumer;
        // read the counter before the lookup, so a change during the lookup makes the next exchange look up again
        int stateCounter = component.getStateCounter();
        if (cached == null || cached.consumer() == null || cached.stateCounter() != stateCounter) {
            DirectConsumer consumer = component.getConsumer(key, block, timeout);
            cachedConsumer = new CachedConsumer(consumer, stateCounter);
            return consumer;
        }
        return cached.consumer();
    }

    private record CachedConsumer(DirectConsumer consumer, int stateCounter) {
    }
}
