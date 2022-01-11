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

import java.util.function.Supplier;

import org.apache.camel.Category;
import org.apache.camel.Processor;
import org.apache.camel.component.dynamicrouter.DynamicRouterConsumer.DynamicRouterConsumerFactory;
import org.apache.camel.component.dynamicrouter.DynamicRouterControlChannelProcessor.DynamicRouterControlChannelProcessorFactory;
import org.apache.camel.component.dynamicrouter.DynamicRouterProducer.DynamicRouterProducerFactory;
import org.apache.camel.component.dynamicrouter.message.DynamicRouterControlMessage;
import org.apache.camel.component.dynamicrouter.processor.DynamicRouterProcessor;
import org.apache.camel.component.dynamicrouter.processor.DynamicRouterProcessor.DynamicRouterProcessorFactory;
import org.apache.camel.component.dynamicrouter.processor.PrioritizedFilterProcessor;
import org.apache.camel.component.dynamicrouter.processor.PrioritizedFilterProcessor.PrioritizedFilterProcessorFactory;
import org.apache.camel.processor.DynamicRouter;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.dynamicrouter.DynamicRouterConstants.COMPONENT_SCHEME;

/**
 * The Dynamic Router component routes exchanges to recipients, and the recipients (and their rules) may change at
 * runtime.
 */
@UriEndpoint(firstVersion = DynamicRouterConstants.FIRST_VERSION,
             scheme = COMPONENT_SCHEME,
             title = DynamicRouterConstants.TITLE,
             syntax = DynamicRouterConstants.SYNTAX,
             category = { Category.ENDPOINT, Category.JAVA })
public class DynamicRouterEndpoint extends DefaultEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(DynamicRouterEndpoint.class);

    /**
     * Creates a {@link DynamicRouterProducer} instance.
     */
    private final Supplier<DynamicRouterProducerFactory> producerFactorySupplier;

    /**
     * Creates a {@link DynamicRouterConsumer} instance.
     */
    private final Supplier<DynamicRouterConsumerFactory> consumerFactorySupplier;

    /**
     * Channel for the Dynamic Router. For example, if the Dynamic Router URI is "dynamic-router://test", then the
     * channel is "test". Channels are a way of keeping routing participants, their rules, and exchanges logically
     * separate from the participants, rules, and exchanges on other channels. This can be seen as analogous to VLANs in
     * networking.
     */
    @UriPath(label = "common", description = "Channel of the Dynamic Router")
    @Metadata(required = true)
    private String channel;

    /**
     * Flag to ensure synchronous processing.
     */
    @UriParam(label = "advanced", defaultValue = "false")
    private boolean synchronous;

    /**
     * Flag that determines if the producer should block while waiting for a consumer.
     */
    @UriParam(label = "producer", defaultValue = "true")
    private boolean block = true;

    /**
     * The time limit, in milliseconds, if/when the producer blocks while waiting for a consumer.
     */
    @UriParam(label = "producer", defaultValue = "30000")
    private long timeout = 30000L;

    /**
     * Flag to fail if there are no consumers.
     */
    @UriParam(label = "producer", defaultValue = "true")
    private boolean failIfNoConsumers = true;

    /**
     * Flag to log a warning if no predicates match for an exchange.
     */
    @UriParam(label = "producer", defaultValue = "false")
    private boolean warnDroppedMessage;

    /**
     * Create the Dynamic Router {@link org.apache.camel.Endpoint} for the given endpoint URI. This includes the
     * creation of a {@link DynamicRouterProcessor} to instantiate a {@link DynamicRouterConsumer} that is registered
     * with the supplied {@link DynamicRouterComponent}.
     *
     * @param uri                            the endpoint URI
     * @param channel                        the Dynamic Router channel
     * @param component                      the Dynamic Router {@link org.apache.camel.Component}
     * @param processorFactorySupplier       creates the {@link DynamicRouterProcessor}
     * @param producerFactorySupplier        creates the {@link DynamicRouterProcessor}
     * @param consumerFactorySupplier        creates the {@link DynamicRouterConsumer}
     * @param filterProcessorFactorySupplier creates the {@link PrioritizedFilterProcessor}
     */
    public DynamicRouterEndpoint(
                                 final String uri, final String channel, final DynamicRouterComponent component,
                                 final Supplier<DynamicRouterProcessorFactory> processorFactorySupplier,
                                 final Supplier<DynamicRouterProducerFactory> producerFactorySupplier,
                                 final Supplier<DynamicRouterConsumerFactory> consumerFactorySupplier,
                                 final Supplier<PrioritizedFilterProcessorFactory> filterProcessorFactorySupplier) {
        super(uri, component);
        this.channel = channel;
        this.producerFactorySupplier = producerFactorySupplier;
        this.consumerFactorySupplier = consumerFactorySupplier;
        final DynamicRouterProcessor processor = processorFactorySupplier.get()
                .getInstance("dynamicRouterProcessor-" + channel, getCamelContext(), isWarnDroppedMessage(),
                        filterProcessorFactorySupplier);
        try {
            final DynamicRouterConsumer consumer = createConsumer(processor);
            component.addConsumer(channel, consumer);
        } catch (Exception e) {
            throw new IllegalStateException("Could not create Dynamic Router endpoint", e);
        }
        LOG.debug("Created Dynamic Router endpoint URI: {}", uri);
    }

    /**
     * Create the specialized endpoint for the Dynamic Router Control Channel.
     *
     * @param uri                      the endpoint URI
     * @param channel                  the Dynamic Router channel
     * @param component                the Dynamic Router {@link org.apache.camel.Component}
     * @param processorFactorySupplier creates the {@link DynamicRouterControlChannelProcessor}
     * @param producerFactorySupplier  creates the {@link DynamicRouterProcessor}
     * @param consumerFactorySupplier  creates the {@link DynamicRouterConsumer}
     */
    public DynamicRouterEndpoint(
                                 final String uri, final String channel, final DynamicRouterComponent component,
                                 final Supplier<DynamicRouterControlChannelProcessorFactory> processorFactorySupplier,
                                 final Supplier<DynamicRouterProducerFactory> producerFactorySupplier,
                                 final Supplier<DynamicRouterConsumerFactory> consumerFactorySupplier) {
        super(uri, component);
        this.channel = channel;
        this.producerFactorySupplier = producerFactorySupplier;
        this.consumerFactorySupplier = consumerFactorySupplier;
        final DynamicRouterControlChannelProcessor processor = processorFactorySupplier.get()
                .getInstance(component);
        try {
            final DynamicRouterConsumer consumer = createConsumer(processor);
            component.addConsumer(channel, consumer);
        } catch (Exception e) {
            throw new IllegalStateException("Could not create Dynamic Router endpoint", e);
        }
        LOG.debug("Created Dynamic Router Control Channel endpoint URI: {}", uri);
    }

    /**
     * Calls the {@link DynamicRouterProducerFactory} to create a {@link DynamicRouterProducer} instance.
     *
     * @return a {@link DynamicRouterProducer} instance
     */
    @Override
    public DynamicRouterProducer createProducer() {
        return producerFactorySupplier.get().getInstance(this);
    }

    /**
     * Calls the {@link DynamicRouterConsumerFactory} to create a {@link DynamicRouterConsumer} instance.
     *
     * @param  processor the {@link DynamicRouterProcessor} needed to create the {@link DynamicRouterConsumer}
     * @return           a {@link DynamicRouterConsumer} instance
     * @throws Exception if there is a problem creating the consumer
     */
    @Override
    public DynamicRouterConsumer createConsumer(final Processor processor) throws Exception {
        DynamicRouterConsumer consumer = consumerFactorySupplier.get().getInstance(this, processor, channel);
        configureConsumer(consumer);
        return consumer;
    }

    /**
     * Gets the channel of the dynamic router. For example, if the Dynamic Router URI is "dynamic-router://test", then
     * the channel is "test". Channels are a way of keeping routing participants, their rules, and exchanges logically
     * separate from the participants, rules, and exchanges on other channels. This can be seen as analogous to VLANs in
     * networking.
     *
     * @return the channel of the dynamic router
     */
    public String getChannel() {
        return channel;
    }

    /**
     * Sets the channel of the dynamic router. For example, if the Dynamic Router URI is "dynamic-router://test", then
     * the channel is "test". Channels are a way of keeping routing participants, their rules, and exchanges logically
     * separate from the participants, rules, and exchanges on other channels. This can be seen as analogous to VLANs in
     * networking.
     *
     * @param channel the channel of the dynamic router
     */
    public void setChannel(final String channel) {
        this.channel = channel;
    }

    /**
     * Whether synchronous processing is forced.
     * <p>
     * If enabled then the producer thread, will be forced to wait until the message has been completed before the same
     * thread will continue processing.
     * <p>
     * If disabled (default) then the producer thread may be freed and can do other work while the message is continued
     * processed by other threads (reactive).
     *
     * @return true, if flag is set to force synchronous processing, otherwise false
     */
    public boolean isSynchronous() {
        return synchronous;
    }

    /**
     * Whether synchronous processing is forced.
     * <p>
     * If enabled then the producer thread, will be forced to wait until the message has been completed before the same
     * thread will continue processing.
     * <p>
     * If disabled (default) then the producer thread may be freed and can do other work while the message is continued
     * processed by other threads (reactive).
     *
     * @param synchronous flag to force synchronous processing when true
     */
    public void setSynchronous(boolean synchronous) {
        this.synchronous = synchronous;
    }

    /**
     * If sending a message to a direct endpoint which has no active consumer, then we can tell the producer to block
     * and wait for the consumer to become active.
     *
     * @return true, if the producer will block
     */
    public boolean isBlock() {
        return block;
    }

    /**
     * If sending a message to a direct endpoint which has no active consumer, then we can tell the producer to block
     * and wait for the consumer to become active.
     *
     * @param block "true" to block, false otherwise
     */
    public void setBlock(boolean block) {
        this.block = block;
    }

    /**
     * The timeout value to use if block is enabled.
     *
     * @return time, in milliseconds, to block
     */
    public long getTimeout() {
        return timeout;
    }

    /**
     * The timeout value to use if block is enabled.
     *
     * @param timeout time, in milliseconds, to block
     */
    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    /**
     * Whether the producer should fail by throwing an exception, when sending to a Dynamic Router endpoint with no
     * active consumers.
     *
     * @return true if we should fail if no consumers are registered
     */
    public boolean isFailIfNoConsumers() {
        return failIfNoConsumers;
    }

    /**
     * Whether the producer should fail by throwing an exception, when sending to a Dynamic Router endpoint with no
     * active consumers.
     *
     * @param failIfNoConsumers flag to fail if there are no consumers registered to the {@link DynamicRouter}
     */
    public void setFailIfNoConsumers(boolean failIfNoConsumers) {
        this.failIfNoConsumers = failIfNoConsumers;
    }

    /**
     * Gets the flag to log a warning if no predicates match for an exchange.
     *
     * @return true if a warning will be logged if no predicates match for an exchange, otherwise false
     */
    public boolean isWarnDroppedMessage() {
        return warnDroppedMessage;
    }

    /**
     * Sets the flag to log a warning if no predicates match for an exchange.
     *
     * @param warnDroppedMessage flag to log a warning if no predicates match for an exchange
     */
    public void setWarnDroppedMessage(boolean warnDroppedMessage) {
        this.warnDroppedMessage = warnDroppedMessage;
    }

    /**
     * A convenience method that wraps the parent method and casts to the {@link DynamicRouterComponent} implementation.
     *
     * @return the {@link DynamicRouterComponent}
     */
    public DynamicRouterComponent getDynamicRouterComponent() {
        return (DynamicRouterComponent) getComponent();
    }

    /**
     * Create a {@link DynamicRouterEndpoint} instance.
     */
    public static class DynamicRouterEndpointFactory {

        /**
         * Create the Dynamic Router {@link org.apache.camel.Endpoint} for the given endpoint URI. This includes the
         * creation of a {@link DynamicRouterProcessor} to instantiate a {@link DynamicRouterConsumer} that is
         * registered with the supplied {@link DynamicRouterComponent}.
         *
         * @param  uri                            the endpoint URI
         * @param  channel                        the Dynamic Router channel
         * @param  component                      the Dynamic Router {@link org.apache.camel.Component}
         * @param  processorFactorySupplier       creates the {@link DynamicRouterProcessor}
         * @param  producerFactorySupplier        creates the {@link DynamicRouterProcessor}
         * @param  consumerFactorySupplier        creates the {@link DynamicRouterConsumer}
         * @param  filterProcessorFactorySupplier creates the {@link PrioritizedFilterProcessor}
         * @return                                the {@link DynamicRouterEndpoint} for routing exchanges
         */
        public DynamicRouterEndpoint getInstance(
                final String uri,
                final String channel,
                final DynamicRouterComponent component,
                final Supplier<DynamicRouterProcessorFactory> processorFactorySupplier,
                final Supplier<DynamicRouterProducerFactory> producerFactorySupplier,
                final Supplier<DynamicRouterConsumerFactory> consumerFactorySupplier,
                final Supplier<PrioritizedFilterProcessorFactory> filterProcessorFactorySupplier) {
            return new DynamicRouterEndpoint(
                    uri, channel, component, processorFactorySupplier, producerFactorySupplier,
                    consumerFactorySupplier, filterProcessorFactorySupplier);
        }

        /**
         * Create a specialized Dynamic Router {@link org.apache.camel.Endpoint} for the control channel endpoint URI.
         * This includes the creation of a {@link DynamicRouterControlChannelProcessor} to instantiate a
         * {@link DynamicRouterConsumer} that is registered with the supplied {@link DynamicRouterComponent}. Routing
         * participants use this endpoint to supply {@link DynamicRouterControlMessage}s to subscribe or unsubscribe.
         *
         * @param  uri                      the endpoint URI
         * @param  channel                  the Dynamic Router channel
         * @param  component                the Dynamic Router {@link org.apache.camel.Component}
         * @param  processorFactorySupplier creates the {@link DynamicRouterControlChannelProcessor}
         * @param  producerFactorySupplier  creates the {@link DynamicRouterProcessor}
         * @param  consumerFactorySupplier  creates the {@link DynamicRouterConsumer}
         * @return                          the {@link DynamicRouterEndpoint} for control channel messages
         */
        public DynamicRouterEndpoint getInstance(
                final String uri,
                final String channel,
                final DynamicRouterComponent component,
                final Supplier<DynamicRouterControlChannelProcessorFactory> processorFactorySupplier,
                final Supplier<DynamicRouterProducerFactory> producerFactorySupplier,
                final Supplier<DynamicRouterConsumerFactory> consumerFactorySupplier) {
            return new DynamicRouterEndpoint(
                    uri, channel, component, processorFactorySupplier, producerFactorySupplier, consumerFactorySupplier);
        }
    }
}
