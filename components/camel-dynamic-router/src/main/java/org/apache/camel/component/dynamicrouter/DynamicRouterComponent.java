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
import org.apache.camel.component.dynamicrouter.DynamicRouterControlChannelProcessor.DynamicRouterControlChannelProcessorFactory;
import org.apache.camel.component.dynamicrouter.DynamicRouterControlProducer.DynamicRouterControlProducerFactory;
import org.apache.camel.component.dynamicrouter.DynamicRouterProcessor.DynamicRouterProcessorFactory;
import org.apache.camel.component.dynamicrouter.DynamicRouterProducer.DynamicRouterProducerFactory;
import org.apache.camel.component.dynamicrouter.PrioritizedFilterProcessor.PrioritizedFilterProcessorFactory;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.service.ServiceHelper;
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
     * The {@link DynamicRouterProcessor}s, mapped by their channel, for the Dynamic Router.
     */
    private final transient Map<String, DynamicRouterProcessor> processors = new HashMap<>();

    private DynamicRouterControlChannelProcessor controlChannelProcessor;

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
     * Creates a {@link DynamicRouterControlProducerFactory} instance.
     */
    private Supplier<DynamicRouterControlProducerFactory> controlProducerFactorySupplier
            = DynamicRouterControlProducerFactory::new;

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
     * @param controlProducerFactorySupplier         creates the {@link DynamicRouterControlProducer}
     * @param filterProcessorFactorySupplier         creates the {@link PrioritizedFilterProcessor}
     */
    public DynamicRouterComponent(
                                  final Supplier<DynamicRouterEndpointFactory> endpointFactorySupplier,
                                  final Supplier<DynamicRouterProcessorFactory> processorFactorySupplier,
                                  final Supplier<DynamicRouterControlChannelProcessorFactory> controlChannelProcessorFactorySupplier,
                                  final Supplier<DynamicRouterProducerFactory> producerFactorySupplier,
                                  final Supplier<DynamicRouterControlProducerFactory> controlProducerFactorySupplier,
                                  final Supplier<PrioritizedFilterProcessorFactory> filterProcessorFactorySupplier) {
        this.endpointFactorySupplier = endpointFactorySupplier;
        this.processorFactorySupplier = processorFactorySupplier;
        this.controlChannelProcessorFactorySupplier = controlChannelProcessorFactorySupplier;
        this.producerFactorySupplier = producerFactorySupplier;
        this.controlProducerFactorySupplier = controlProducerFactorySupplier;
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
        DynamicRouterConfiguration configuration = new DynamicRouterConfiguration();
        configuration.parsePath(remaining);
        DynamicRouterEndpoint endpoint;
        if (remaining == null || remaining.isBlank()) {
            throw new IllegalArgumentException("You must provide a channel for the Dynamic Router");
        }
        if (remaining.startsWith(CONTROL_CHANNEL_NAME)) {
            endpoint = endpointFactorySupplier.get()
                    .getInstance(uri, this, configuration, controlChannelProcessorFactorySupplier,
                            controlProducerFactorySupplier);
        } else {
            endpoint = endpointFactorySupplier.get()
                    .getInstance(uri, this, configuration, processorFactorySupplier, producerFactorySupplier,
                            filterProcessorFactorySupplier);
        }
        setProperties(endpoint, parameters);
        return endpoint;
    }

    /**
     * Perform shutdown on the Dynamic Router.
     *
     * @throws Exception to indicate a problem with shutdown
     */
    @Override
    protected void doShutdown() throws Exception {
        ServiceHelper.stopAndShutdownService(processors);
        processors.clear();
        super.doShutdown();
    }

    /**
     * If no processor has been added for the supplied channel, then add the supplied processor.
     *
     * @param channel   the channel to add the processor to
     * @param processor the processor to add for the channel
     */
    void addRoutingProcessor(final String channel, final DynamicRouterProcessor processor) {
        if (processors.putIfAbsent(channel, processor) != null) {
            throw new IllegalArgumentException(
                    "Dynamic Router can have only one processor per channel; channel '" + channel
                                               + "' already has a processor");
        }
    }

    /**
     * Get the processor for the given channel.
     *
     * @param  channel the channel to get the processor for
     * @return         the processor for the given channel
     */
    public DynamicRouterProcessor getRoutingProcessor(final String channel) {
        return processors.get(channel);
    }

    /**
     * Sets the control channel processor.
     *
     * @param controlChannelProcessor the control channel processor
     */
    void setControlChannelProcessor(final DynamicRouterControlChannelProcessor controlChannelProcessor) {
        this.controlChannelProcessor = controlChannelProcessor;
    }

    /**
     * Gets the control channel processor.
     *
     * @return the control channel processor
     */
    DynamicRouterControlChannelProcessor getControlChannelProcessor() {
        return this.controlChannelProcessor;
    }
}
