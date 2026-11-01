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
package org.apache.camel.component.dynamicrouter.routing;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Expression;
import org.apache.camel.component.dynamicrouter.filter.DynamicRouterFilterService;
import org.apache.camel.component.dynamicrouter.filter.DynamicRouterFilterService.DynamicRouterFilterServiceFactory;
import org.apache.camel.component.dynamicrouter.filter.PrioritizedFilter;
import org.apache.camel.component.dynamicrouter.filter.PrioritizedFilter.PrioritizedFilterFactory;
import org.apache.camel.component.dynamicrouter.routing.DynamicRouterProcessor.DynamicRouterProcessorFactory;
import org.apache.camel.component.dynamicrouter.routing.DynamicRouterProducer.DynamicRouterProducerFactory;
import org.apache.camel.processor.RecipientList;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.dynamicrouter.routing.DynamicRouterConstants.COMPONENT_SCHEME_ROUTING;
import static org.apache.camel.component.dynamicrouter.routing.DynamicRouterConstants.ENDPOINT_FACTORY_SUPPLIER;
import static org.apache.camel.component.dynamicrouter.routing.DynamicRouterConstants.FILTER_FACTORY_SUPPLIER;
import static org.apache.camel.component.dynamicrouter.routing.DynamicRouterConstants.FILTER_SERVICE_FACTORY_SUPPLIER;
import static org.apache.camel.component.dynamicrouter.routing.DynamicRouterConstants.PROCESSOR_FACTORY_SUPPLIER;
import static org.apache.camel.component.dynamicrouter.routing.DynamicRouterConstants.PRODUCER_FACTORY_SUPPLIER;
import static org.apache.camel.component.dynamicrouter.routing.DynamicRouterConstants.RECIPIENT_LIST_SUPPLIER;
import static org.apache.camel.component.dynamicrouter.routing.DynamicRouterEndpoint.DynamicRouterEndpointFactory;

/**
 * The Dynamic Router {@link org.apache.camel.Component}. Manages:
 * <ul>
 * <li>{@link org.apache.camel.Consumer}s: addition and removal</li>
 * <li>Control Channel: manage routing participants and their routing rules</li>
 * <li>Endpoint: creates the {@link Endpoint} for a Dynamic Router URI</li>
 * </ul>
 */
@Component(COMPONENT_SCHEME_ROUTING)
public class DynamicRouterComponent extends DefaultComponent {

    private static final Logger LOG = LoggerFactory.getLogger(DynamicRouterComponent.class);

    /**
     * The {@link DynamicRouterProcessor}s, mapped by their channel, for the Dynamic Router.
     */
    private final Map<String, DynamicRouterProcessor> processors = new ConcurrentHashMap<>();

    /**
     * The endpoint that uses the processor of each channel, mapped by their channel.
     */
    private final Map<String, DynamicRouterEndpoint> processorEndpoints = new ConcurrentHashMap<>();

    /**
     * The processors that were replaced by a processor for an endpoint with other options. Their recipient lists are
     * only stopped when they are replaced, as another route can still have exchanges in flight in them, and are shut
     * down with the component. The threads of a thread pool of the default profile end after its keep-alive time.
     */
    private final List<DynamicRouterProcessor> replacedProcessors = new CopyOnWriteArrayList<>();

    private final Lock processorsLock = new ReentrantLock();

    /**
     * Creates a {@link DynamicRouterEndpoint} instance.
     */
    private final Supplier<DynamicRouterEndpointFactory> endpointFactorySupplier;

    /**
     * Creates a {@link DynamicRouterProcessor} instance.
     */
    private final Supplier<DynamicRouterProcessorFactory> processorFactorySupplier;

    /**
     * Creates a {@link DynamicRouterProducer} instance.
     */
    private final Supplier<DynamicRouterProducerFactory> producerFactorySupplier;

    /**
     * Creates a {@link RecipientList} instance.
     */
    private final BiFunction<CamelContext, Expression, RecipientList> recipientListSupplier;

    /**
     * Service that manages {@link PrioritizedFilter}s for the Dynamic Router channels.
     */
    private final DynamicRouterFilterService filterService;

    /**
     * Create an instance of the Dynamic Router component with default factories.
     */
    public DynamicRouterComponent() {
        this.endpointFactorySupplier = ENDPOINT_FACTORY_SUPPLIER;
        this.processorFactorySupplier = PROCESSOR_FACTORY_SUPPLIER;
        this.producerFactorySupplier = PRODUCER_FACTORY_SUPPLIER;
        this.recipientListSupplier = RECIPIENT_LIST_SUPPLIER;
        this.filterService = FILTER_SERVICE_FACTORY_SUPPLIER.get().getInstance(FILTER_FACTORY_SUPPLIER);
        LOG.debug("Created Dynamic Router component");
    }

    /**
     * Create an instance of the Dynamic Router component with custom factories.
     *
     * @param endpointFactorySupplier      creates the {@link DynamicRouterEndpoint}
     * @param processorFactorySupplier     creates the {@link DynamicRouterProcessor}
     * @param producerFactorySupplier      creates the {@link DynamicRouterProducer}
     * @param recipientListSupplier        creates the {@link RecipientList}
     * @param filterFactorySupplier        creates the {@link PrioritizedFilter}
     * @param filterServiceFactorySupplier creates the {@link DynamicRouterFilterService}
     */
    public DynamicRouterComponent(
                                  final Supplier<DynamicRouterEndpointFactory> endpointFactorySupplier,
                                  final Supplier<DynamicRouterProcessorFactory> processorFactorySupplier,
                                  final Supplier<DynamicRouterProducerFactory> producerFactorySupplier,
                                  final BiFunction<CamelContext, Expression, RecipientList> recipientListSupplier,
                                  final Supplier<PrioritizedFilterFactory> filterFactorySupplier,
                                  final Supplier<DynamicRouterFilterServiceFactory> filterServiceFactorySupplier) {
        this.endpointFactorySupplier = endpointFactorySupplier;
        this.processorFactorySupplier = processorFactorySupplier;
        this.producerFactorySupplier = producerFactorySupplier;
        this.recipientListSupplier = recipientListSupplier;
        this.filterService = filterServiceFactorySupplier.get().getInstance(filterFactorySupplier);
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
        if (ObjectHelper.isEmpty(remaining)) {
            throw new IllegalArgumentException("You must provide a channel for the Dynamic Router");
        }
        DynamicRouterConfiguration configuration = new DynamicRouterConfiguration();
        configuration.setChannel(remaining);
        filterService.initializeChannelFilters(configuration.getChannel());
        DynamicRouterEndpoint endpoint = endpointFactorySupplier.get()
                .getInstance(uri, this, configuration, processorFactorySupplier, producerFactorySupplier, recipientListSupplier,
                        filterService);
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
        processors.values().forEach(DynamicRouterProcessor::shutdownRecipientList);
        replacedProcessors.forEach(DynamicRouterProcessor::shutdownRecipientList);
        processors.clear();
        processorEndpoints.clear();
        replacedProcessors.clear();
        super.doShutdown();
    }

    /**
     * Get the processor of the channel of the endpoint, and create it if the channel has none.
     * <ul>
     * <li>An endpoint with the same URI as the endpoint that uses the processor uses the same processor, such as when
     * the route that used the endpoint is removed and added again (also by a route reload), or when a {@code toD}
     * creates the endpoint again after the endpoint was removed or evicted.</li>
     * <li>An endpoint with another URI for the channel fails while the endpoint that uses the processor is not stopped,
     * as there can be only one processor per channel. When that endpoint is stopped (such as with the route that used
     * it), the processor is replaced by a processor for the new endpoint, and the recipient list of the old processor
     * is stopped (its thread pool is only shut down with the component, so that the exchanges that another route still
     * has in flight in it are not interrupted or dropped).</li>
     * </ul>
     *
     * @param  endpoint the endpoint
     * @param  factory  creates the processor for the endpoint
     * @return          the processor of the channel of the endpoint
     */
    DynamicRouterProcessor getOrCreateRoutingProcessor(
            final DynamicRouterEndpoint endpoint, final Supplier<DynamicRouterProcessor> factory) {
        final String channel = endpoint.getConfiguration().getChannel();
        processorsLock.lock();
        try {
            DynamicRouterProcessor current = processors.get(channel);
            if (current != null) {
                DynamicRouterEndpoint user = processorEndpoints.get(channel);
                if (user != null && (user == endpoint || user.getEndpointUri().equals(endpoint.getEndpointUri()))) {
                    processorEndpoints.put(channel, endpoint);
                    return current;
                }
                if (user == null || !user.isProcessorReleased()) {
                    throw new IllegalArgumentException(
                            "Dynamic Router can have only one processor per channel; channel '"
                                                       + channel + "' already has a processor");
                }
                LOG.debug("Replacing the processor of channel '{}' of the stopped endpoint {} for the endpoint {}",
                        channel, user.getEndpointUri(), endpoint.getEndpointUri());
            }
            // create the new processor first, so that the channel keeps its processor when that fails
            DynamicRouterProcessor created = factory.get();
            processors.put(channel, created);
            processorEndpoints.put(channel, endpoint);
            if (current != null) {
                replacedProcessors.add(current);
                current.stopRecipientList();
            }
            return created;
        } finally {
            processorsLock.unlock();
        }
    }

    /**
     * Get the {@link DynamicRouterProcessor} for the given channel.
     *
     * @param  channel the channel to get the processor for
     * @return         the processor for the given channel
     */
    public DynamicRouterProcessor getRoutingProcessor(final String channel) {
        return processors.get(channel);
    }

    /**
     * Get the {@link DynamicRouterFilterService} for the component.
     *
     * @return the {@link DynamicRouterFilterService} for the component
     */
    public DynamicRouterFilterService getFilterService() {
        return filterService;
    }
}
