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

import java.util.HashMap;
import java.util.Map;
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
import org.apache.camel.support.service.ServiceHelper;
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
    private final Map<String, DynamicRouterProcessor> processors = new HashMap<>();

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
                    "Dynamic Router can have only one processor per channel; channel '"
                                               + channel + "' already has a processor");
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
