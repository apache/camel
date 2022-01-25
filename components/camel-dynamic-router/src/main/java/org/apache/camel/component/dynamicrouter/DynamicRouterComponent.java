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

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.apache.camel.Endpoint;
import org.apache.camel.component.dynamicrouter.DynamicRouterConsumer.DynamicRouterConsumerFactory;
import org.apache.camel.component.dynamicrouter.DynamicRouterControlChannelProcessor.DynamicRouterControlChannelProcessorFactory;
import org.apache.camel.component.dynamicrouter.DynamicRouterProducer.DynamicRouterProducerFactory;
import org.apache.camel.component.dynamicrouter.processor.DynamicRouterProcessor;
import org.apache.camel.component.dynamicrouter.processor.DynamicRouterProcessor.DynamicRouterProcessorFactory;
import org.apache.camel.component.dynamicrouter.processor.PrioritizedFilterProcessor;
import org.apache.camel.component.dynamicrouter.processor.PrioritizedFilterProcessor.PrioritizedFilterProcessorFactory;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.dynamicrouter.DynamicRouterConstants.CONTROL_CHANNEL_NAME;
import static org.apache.camel.component.dynamicrouter.DynamicRouterEndpoint.DynamicRouterEndpointFactory;

/**
 * The Dynamic Router {@link org.apache.camel.Component}. Manages:
 * <ul>
 * <li>{@link org.apache.camel.Consumer}s: addition and removal</li>
 * <li>Control Channel: manage routing participants and their routing rules</li>
 * <li>Endpoint: creates the {@link Endpoint} for a Dynamic Router URI</li>
 * </ul>
 */
@Component("dynamic-router")
public class DynamicRouterComponent extends DefaultComponent {

    private static final Logger LOG = LoggerFactory.getLogger(DynamicRouterComponent.class);

    /**
     * The {@link DynamicRouterConsumer}s, mapped by their channel, for the Dynamic Router.
     */
    private final Map<String, DynamicRouterConsumer> consumers = new HashMap<>();

    /**
     * Creates a {@link DynamicRouterEndpoint} instance.
     */
    private Supplier<DynamicRouterEndpointFactory> endpointFactorySupplier = DynamicRouterEndpointFactory::new;

    /**
     * Creates a {@link DynamicRouterProcessor} instance.
     */
    private Supplier<DynamicRouterProcessorFactory> processorFactorySupplier = DynamicRouterProcessorFactory::new;

    /**
     * Creates a {@link DynamicRouterControlChannelProcessor} instance.
     */
    private Supplier<DynamicRouterControlChannelProcessorFactory> controlChannelProcessorFactorySupplier
            = DynamicRouterControlChannelProcessorFactory::new;

    /**
     * Creates a {@link DynamicRouterProducer} instance.
     */
    private Supplier<DynamicRouterProducerFactory> producerFactorySupplier = DynamicRouterProducerFactory::new;

    /**
     * Creates a {@link DynamicRouterConsumer} instance.
     */
    private Supplier<DynamicRouterConsumerFactory> consumerFactorySupplier = DynamicRouterConsumerFactory::new;

    /**
     * Creates a {@link PrioritizedFilterProcessor} instance.
     */
    private Supplier<PrioritizedFilterProcessorFactory> filterProcessorFactorySupplier = PrioritizedFilterProcessorFactory::new;

    /**
     * Create an instance of the Dynamic Router component.
     */
    public DynamicRouterComponent() {
        LOG.debug("Created Dynamic Router component");
    }

    /**
     * Create an instance of the Dynamic Router component with custom factories.
     *
     * @param endpointFactorySupplier                creates the {@link DynamicRouterEndpoint}
     * @param processorFactorySupplier               creates the {@link DynamicRouterProcessor}
     * @param controlChannelProcessorFactorySupplier creates the {@link DynamicRouterControlChannelProcessor}
     * @param producerFactorySupplier                creates the {@link DynamicRouterProducer}
     * @param consumerFactorySupplier                creates the {@link DynamicRouterConsumer}
     * @param filterProcessorFactorySupplier         creates the {@link PrioritizedFilterProcessor}
     */
    public DynamicRouterComponent(
                                  final Supplier<DynamicRouterEndpointFactory> endpointFactorySupplier,
                                  final Supplier<DynamicRouterProcessorFactory> processorFactorySupplier,
                                  final Supplier<DynamicRouterControlChannelProcessorFactory> controlChannelProcessorFactorySupplier,
                                  final Supplier<DynamicRouterProducerFactory> producerFactorySupplier,
                                  final Supplier<DynamicRouterConsumerFactory> consumerFactorySupplier,
                                  final Supplier<PrioritizedFilterProcessorFactory> filterProcessorFactorySupplier) {
        this.endpointFactorySupplier = endpointFactorySupplier;
        this.processorFactorySupplier = processorFactorySupplier;
        this.controlChannelProcessorFactorySupplier = controlChannelProcessorFactorySupplier;
        this.producerFactorySupplier = producerFactorySupplier;
        this.consumerFactorySupplier = consumerFactorySupplier;
        this.filterProcessorFactorySupplier = filterProcessorFactorySupplier;
        LOG.debug("Created Dynamic Router component");
    }

    /**
     * Create an endpoint for the supplied URI, and with the supplied parameters. The control channel URI
     *
     * @param  uri        endpoint URI
     * @param  remaining  portion of the URI after the scheme, and before parameters (the channel)
     * @param  parameters URI parameters
     * @return            an endpoint for the supplied URI
     */
    @Override
    protected Endpoint createEndpoint(final String uri, final String remaining, final Map<String, Object> parameters)
            throws Exception {
        DynamicRouterEndpoint endpoint;
        if (remaining == null) {
            throw new IllegalArgumentException("You must provide a channel for the Dynamic Router");
        }
        if (remaining.equals(CONTROL_CHANNEL_NAME)) {
            endpoint = endpointFactorySupplier.get()
                    .getInstance(uri, remaining, this, controlChannelProcessorFactorySupplier,
                            producerFactorySupplier, consumerFactorySupplier);
        } else {
            endpoint = endpointFactorySupplier.get()
                    .getInstance(uri, remaining, this, processorFactorySupplier, producerFactorySupplier,
                            consumerFactorySupplier, filterProcessorFactorySupplier);
            setProperties(endpoint, parameters);
        }
        return endpoint;
    }

    /**
     * Perform shutdown on the Dynamic Router.
     *
     * @throws Exception to indicate a problem with shutdown
     */
    @Override
    protected void doShutdown() throws Exception {
        ServiceHelper.stopAndShutdownService(consumers);
        consumers.clear();
        super.doShutdown();
    }

    /**
     * Adds a consumer to the map. Only one consumer can be registered for a given channel.
     *
     * @param channel  the channel of the consumer to register
     * @param consumer the consumer to register
     */
    public void addConsumer(final String channel, final DynamicRouterConsumer consumer) {
        synchronized (consumers) {
            if (consumers.putIfAbsent(channel, consumer) != null) {
                throw new IllegalArgumentException(
                        String.format(
                                "Cannot add a 2nd consumer to the same endpoint: %s. Dynamic Router only allows one consumer per channel.",
                                channel));
            }
            consumers.notifyAll();
        }
    }

    /**
     * Remove the supplied consumer registered for the supplied channel.
     *
     * @param channel  channel of the consumer to remove
     * @param consumer consumer to remove
     */
    public void removeConsumer(final String channel, final DynamicRouterConsumer consumer) {
        synchronized (consumers) {
            consumers.remove(channel, consumer);
            consumers.notifyAll();
        }
    }

    /**
     * Get the consumer indicated by the supplied channel.
     *
     * @param  channel channel of the consumer to get
     * @return         the consumer indicated by the supplied channel
     */
    public DynamicRouterConsumer getConsumer(final String channel) {
        return consumers.get(channel);
    }

    /**
     * Get the consumer indicated by the supplied channel.
     *
     * @param  channel channel of the consumer to get
     * @param  block   if the call to get the consumer should block if not found
     * @return         the consumer indicated by the supplied channel
     */
    public DynamicRouterConsumer getConsumer(final String channel, final boolean block, final long timeout)
            throws InterruptedException {
        synchronized (consumers) {
            long remaining = timeout;
            StopWatch watch = new StopWatch();
            DynamicRouterConsumer consumer = getConsumer(channel);
            while (consumer == null && remaining > 0 && block) {
                consumers.wait(remaining);
                remaining -= watch.taken();
                consumer = getConsumer(channel);
            }
            return consumer;
        }
    }
}
