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
package org.apache.camel.component.dynamicrouter;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultAsyncProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link org.apache.camel.Producer} implementation to process exchanges for the Dynamic Router.
 */
public class DynamicRouterProducer extends DefaultAsyncProducer {

    private static final Logger LOG = LoggerFactory.getLogger(DynamicRouterProducer.class);

    /**
     * The {@link org.apache.camel.Endpoint} for the Dynamic Router instance.
     */
    private final DynamicRouterEndpoint endpoint;

    /**
     * The channel of the Dynamic Router. For example, if the Dynamic Router URI is "dynamic-router://test", then the
     * channel is "test".
     */
    private final String channel;

    /**
     * Create the {@link org.apache.camel.Producer} for the Dynamic Router with the supplied
     * {@link org.apache.camel.Endpoint} URI.
     *
     * @param endpoint the {@link DynamicRouterEndpoint}
     */
    public DynamicRouterProducer(final DynamicRouterEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
        this.channel = endpoint.getChannel();
        LOG.debug("Created producer for endpoint '{}', channel '{}'", endpoint.getEndpointUri(), channel);
    }

    /**
     * Check that the {@link org.apache.camel.Consumer} instance is not null. If it is null, and if the
     * {@link org.apache.camel.Endpoint} is set to fail if there are no consumers, then a
     * {@link DynamicRouterConsumerNotAvailableException} is thrown. If this {@link org.apache.camel.Producer} is not
     * set to fail if there are no consumers, then the message is dropped and a warning is logged.
     *
     * @param  exchange                                   the exchange being processed
     * @return                                            true if the consumer is not null, otherwise false
     * @throws DynamicRouterConsumerNotAvailableException if there are no consumers and the producer is set to fail if
     *                                                    there are no consumers
     */
    boolean checkConsumer(final Exchange exchange) throws DynamicRouterConsumerNotAvailableException {
        DynamicRouterConsumer consumer = null;
        try {
            consumer = getConsumer(endpoint.getTimeout());
        } catch (Exception ignored) {
            // No-op -- the next block will handle null consumer
        }
        if (consumer == null) {
            if (endpoint.isFailIfNoConsumers()) {
                throw new DynamicRouterConsumerNotAvailableException(
                        "No consumers available on endpoint: " + endpoint, exchange);
            } else {
                LOG.warn("Message ignored -- no consumers available on endpoint: {}", endpoint.getEndpointUri());
            }
        }
        return consumer != null;
    }

    /**
     * Get the {@link DynamicRouterComponent} from the {@link DynamicRouterEndpoint}.
     *
     * @return the {@link DynamicRouterComponent}
     */
    private DynamicRouterComponent getComponent() {
        return endpoint.getDynamicRouterComponent();
    }

    /**
     * Get the {@link DynamicRouterConsumer}.
     *
     * @return the {@link DynamicRouterConsumer}
     */
    private DynamicRouterConsumer getConsumer() {
        return getComponent().getConsumer(channel);
    }

    /**
     * Get the {@link DynamicRouterConsumer}, and wait a maximum of the supplied timeout (milliseconds) if the consumer
     * is not yet available.
     *
     * @param  timeout              time, in milliseconds, to wait if the consumer is not yet available
     * @return                      the {@link DynamicRouterConsumer}
     *
     * @throws InterruptedException if interrupted while waiting for the {@link DynamicRouterConsumer}
     */
    private DynamicRouterConsumer getConsumer(final long timeout) throws InterruptedException {
        return getComponent().getConsumer(channel, endpoint.isBlock(), timeout);
    }

    /**
     * Process the exchange.
     *
     * @param  exchange  the exchange to process
     * @throws Exception if the consumer has a problem
     */
    @Override
    public void process(final Exchange exchange) throws Exception {
        if (checkConsumer(exchange)) {
            getConsumer().getProcessor().process(exchange);
        }
    }

    /**
     * Process the exchange, and use the {@link AsyncCallback} to signal completion.
     *
     * @param  exchange the exchange to process
     * @param  callback the {@link AsyncCallback} to signal when asynchronous processing has completed
     * @return          true to continue to execute synchronously, or false to continue to execute asynchronously
     */
    @Override
    public boolean process(final Exchange exchange, final AsyncCallback callback) {
        try {
            // we may be forced synchronous
            if (endpoint.isSynchronous()) {
                process(exchange);
            } else {
                return getConsumer().getAsyncProcessor().process(exchange, callback);
            }
        } catch (Exception e) {
            exchange.setException(e);
        } finally {
            callback.done(true);
        }
        return true;
    }

    /**
     * Create a {@link DynamicRouterProducer} instance.
     */
    public static class DynamicRouterProducerFactory {

        /**
         * Create the {@link org.apache.camel.Producer} for the Dynamic Router with the supplied
         * {@link org.apache.camel.Endpoint} URI.
         *
         * @param endpoint the {@link DynamicRouterEndpoint}
         */
        public DynamicRouterProducer getInstance(final DynamicRouterEndpoint endpoint) {
            return new DynamicRouterProducer(endpoint);
        }
    }
}
