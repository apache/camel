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

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ShutdownRunningTask;
import org.apache.camel.Suspendable;
import org.apache.camel.spi.ShutdownAware;
import org.apache.camel.support.DefaultConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Dynamic Router {@link org.apache.camel.Consumer}.
 */
public class DynamicRouterConsumer extends DefaultConsumer implements ShutdownAware, Suspendable {

    private static final Logger LOG = LoggerFactory.getLogger(DynamicRouterConsumer.class);

    /**
     * The channel of the Dynamic Router. For example, if the Dynamic Router URI is "dynamic-router://test", then the
     * channel is "test".
     */
    private final String channel;

    /**
     * Create the Dynamic Router {@link org.apache.camel.Consumer} with the supplied {@link org.apache.camel.Endpoint}
     * and {@link Processor}.
     *
     * @param endpoint  the Dynamic Router endpoint
     * @param processor the Dynamic Router processor
     * @param channel   the channel of the Dynamic Router
     */
    public DynamicRouterConsumer(final DynamicRouterEndpoint endpoint, final Processor processor, final String channel) {
        super(endpoint, processor);
        this.channel = channel;
        LOG.debug("Created DynamicRouter consumer for endpoint URI: {}", endpoint.getEndpointUri());
    }

    private DynamicRouterComponent getComponent() {
        return (DynamicRouterComponent) getEndpoint().getComponent();
    }

    /**
     * Get the Dynamic Router {@link org.apache.camel.Endpoint}.
     *
     * @return the {@link DynamicRouterEndpoint}
     */
    @Override
    public DynamicRouterEndpoint getEndpoint() {
        return (DynamicRouterEndpoint) super.getEndpoint();
    }

    /**
     * Manages the "start" lifecycle phase.
     *
     * @throws Exception if there is a problem starting
     */
    @Override
    protected void doStart() throws Exception {
        super.doStart();
        getComponent().addConsumer(channel, this);
    }

    /**
     * Manages the "stop" lifecycle phase.
     *
     * @throws Exception if there is a problem stopping
     */
    @Override
    protected void doStop() throws Exception {
        getComponent().removeConsumer(channel, this);
        super.doStop();
    }

    /**
     * Manages the "suspend" lifecycle phase.
     */
    @Override
    protected void doSuspend() {
        getComponent().removeConsumer(channel, this);
    }

    /**
     * Manages the "resume" lifecycle phase.
     */
    @Override
    protected void doResume() {
        getComponent().addConsumer(channel, this);
    }

    /**
     * To defer shutdown during first phase of shutdown. This allows any pending exchanges to be completed and,
     * therefore, ensure a graceful shutdown without losing messages. At the very end, when there are no more inflight
     * and pending messages, the consumer may safely be shut down.
     *
     * @param  shutdownRunningTask the configured option for how to act when shutting down running tasks
     * @return                     <tt>true</tt> to defer shutdown until the last exchange completes
     */
    @Override
    public boolean deferShutdown(final ShutdownRunningTask shutdownRunningTask) {
        return true;
    }

    /**
     * Gets the number of pending exchanges. Some consumers have internal queues with {@link Exchange}s that are
     * pending. Returning <tt>zero</tt> indicates that there are zero pending exchanges and, therefore, this consumer is
     * ready to be shut down.
     *
     * @return number of pending exchanges
     */
    @Override
    public int getPendingExchangesSize() {
        return 0;
    }

    /**
     * Prepares for stop/shutdown.
     *
     * @param suspendOnly <tt>true</tt> if the intention is to only suspend the service, and not stop/shutdown the
     *                    service.
     * @param forced      <tt>true</tt> to force a more aggressive shutdown, <tt>false</tt> to gracefully prepare to
     *                    shut down
     */
    @Override
    public void prepareShutdown(final boolean suspendOnly, final boolean forced) {
        // noop
    }

    /**
     * Create a {@link DynamicRouterConsumer} instance.
     */
    public static class DynamicRouterConsumerFactory {

        /**
         * Create the Dynamic Router {@link org.apache.camel.Consumer} with the supplied
         * {@link org.apache.camel.Endpoint} and {@link Processor}.
         *
         * @param endpoint  the Dynamic Router endpoint
         * @param processor the Dynamic Router processor
         * @param channel   the channel of the Dynamic Router
         */
        public DynamicRouterConsumer getInstance(
                final DynamicRouterEndpoint endpoint,
                final Processor processor,
                final String channel) {
            return new DynamicRouterConsumer(endpoint, processor, channel);
        }
    }
}
