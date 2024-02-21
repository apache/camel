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

import java.util.function.BiFunction;
import java.util.function.Supplier;

import org.apache.camel.CamelContext;
import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.dynamicrouter.filter.DynamicRouterFilterService;
import org.apache.camel.component.dynamicrouter.routing.DynamicRouterProcessor.DynamicRouterProcessorFactory;
import org.apache.camel.component.dynamicrouter.routing.DynamicRouterProducer.DynamicRouterProducerFactory;
import org.apache.camel.processor.RecipientList;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.dynamicrouter.routing.DynamicRouterConstants.COMPONENT_SCHEME_ROUTING;
import static org.apache.camel.component.dynamicrouter.routing.DynamicRouterConstants.FIRST_VERSION;
import static org.apache.camel.component.dynamicrouter.routing.DynamicRouterConstants.PROCESSOR_FACTORY_SUPPLIER;
import static org.apache.camel.component.dynamicrouter.routing.DynamicRouterConstants.PRODUCER_FACTORY_SUPPLIER;
import static org.apache.camel.component.dynamicrouter.routing.DynamicRouterConstants.RECIPIENT_LIST_SUPPLIER;
import static org.apache.camel.component.dynamicrouter.routing.DynamicRouterConstants.SYNTAX;
import static org.apache.camel.component.dynamicrouter.routing.DynamicRouterConstants.TITLE;

/**
 * The Dynamic Router component routes exchanges to recipients, and the recipients (and their rules) may change at
 * runtime.
 */
@UriEndpoint(firstVersion = FIRST_VERSION,
             scheme = COMPONENT_SCHEME_ROUTING,
             title = TITLE,
             syntax = SYNTAX,
             producerOnly = true,
             remote = false,
             category = { Category.MESSAGING, Category.CORE })
public class DynamicRouterEndpoint extends DefaultEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(DynamicRouterEndpoint.class);

    /**
     * Creates the {@link DynamicRouterProcessor} instance.
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
     * Channel for the Dynamic Router. For example, if the Dynamic Router URI is "dynamic-router://test", then the
     * channel is "test". Channels are a way of keeping routing participants, their rules, and exchanges logically
     * separate from the participants, rules, and exchanges on other channels. This can be seen as analogous to VLANs in
     * networking.
     */
    @UriPath
    private final String channel;

    /**
     * The configuration for the Dynamic Router.
     */
    @UriParam
    private final DynamicRouterConfiguration configuration;

    /**
     * The service that manages filters for Dynamic Router channels.
     */
    private final DynamicRouterFilterService filterService;

    /**
     * Create the Dynamic Router {@link org.apache.camel.Endpoint} for the given endpoint URI. This includes the
     * creation of a {@link DynamicRouterProcessor} that is registered with the supplied {@link DynamicRouterComponent}.
     *
     * @param uri                      the endpoint URI
     * @param component                the Dynamic Router {@link org.apache.camel.Component}
     * @param configuration            the {@link DynamicRouterConfiguration}
     * @param processorFactorySupplier creates the {@link DynamicRouterProcessor}
     * @param producerFactorySupplier  creates the {@link DynamicRouterProcessor}
     * @param recipientListSupplier    creates the {@link RecipientList}
     * @param filterService            the {@link DynamicRouterFilterService}
     */
    public DynamicRouterEndpoint(final String uri, final DynamicRouterComponent component,
                                 final DynamicRouterConfiguration configuration,
                                 final Supplier<DynamicRouterProcessorFactory> processorFactorySupplier,
                                 final Supplier<DynamicRouterProducerFactory> producerFactorySupplier,
                                 final BiFunction<CamelContext, Expression, RecipientList> recipientListSupplier,
                                 final DynamicRouterFilterService filterService) {
        super(uri, component);
        this.channel = configuration.getChannel();
        this.configuration = configuration;
        this.processorFactorySupplier = processorFactorySupplier;
        this.producerFactorySupplier = producerFactorySupplier;
        this.recipientListSupplier = recipientListSupplier;
        this.configuration.setChannel(channel);
        this.filterService = filterService;
        LOG.debug("Created Dynamic Router endpoint URI: {}", uri);
    }

    /**
     * Create the specialized endpoint for the Dynamic Router Control Channel.
     *
     * @param uri           the endpoint URI
     * @param component     the Dynamic Router {@link org.apache.camel.Component}
     * @param configuration the {@link DynamicRouterConfiguration}
     * @param filterService the {@link DynamicRouterFilterService}
     */
    public DynamicRouterEndpoint(final String uri, final DynamicRouterComponent component,
                                 final DynamicRouterConfiguration configuration,
                                 final DynamicRouterFilterService filterService) {
        super(uri, component);
        this.processorFactorySupplier = PROCESSOR_FACTORY_SUPPLIER;
        this.producerFactorySupplier = PRODUCER_FACTORY_SUPPLIER;
        this.recipientListSupplier = RECIPIENT_LIST_SUPPLIER;
        this.channel = configuration.getChannel();
        this.configuration = configuration;
        this.filterService = filterService;
        LOG.debug("Created Dynamic Router Control Channel endpoint URI: {}", uri);
    }

    /**
     * Initialize the endpoint by creating (and adding) the {@link DynamicRouterProcessor} instance.
     *
     * @throws Exception if there is a problem getting the {@link DynamicRouterProcessor} instance through the
     *                   {@link #processorFactorySupplier}
     */
    @Override
    protected void doInit() throws Exception {
        super.doInit();
        DynamicRouterComponent component = getDynamicRouterComponent();
        CamelContext camelContext = getCamelContext();
        DynamicRouterProcessor processor = processorFactorySupplier.get()
                .getInstance(camelContext, configuration, filterService, recipientListSupplier);
        component.addRoutingProcessor(configuration.getChannel(), processor);
    }

    /**
     * Calls the {@link DynamicRouterProducerFactory} to create a {@link DynamicRouterProducer} instance.
     *
     * @return a {@link DynamicRouterProducer} instance
     */
    @Override
    public Producer createProducer() {
        return producerFactorySupplier.get().getInstance(this, getDynamicRouterComponent(), configuration);
    }

    /**
     * This is a producer-only component.
     *
     * @param  processor not applicable to producer-only component
     * @return           not applicable to producer-only component
     */
    @Override
    public Consumer createConsumer(final Processor processor) {
        throw new IllegalStateException("Dynamic Router is a producer-only component");
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
     * Gets the {@link DynamicRouterConfiguration}.
     *
     * @return the {@link DynamicRouterConfiguration}
     */
    public DynamicRouterConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * Create a {@link DynamicRouterEndpoint} instance.
     */
    public static class DynamicRouterEndpointFactory {

        /**
         * Create the Dynamic Router {@link org.apache.camel.Endpoint} for the given endpoint URI. This includes the
         * creation of a {@link DynamicRouterProcessor} that is registered with the supplied
         * {@link DynamicRouterComponent}.
         *
         * @param  uri                      the endpoint URI
         * @param  component                the Dynamic Router {@link org.apache.camel.Component}
         * @param  configuration            the {@link DynamicRouterConfiguration}
         * @param  processorFactorySupplier creates the {@link DynamicRouterProcessor}
         * @param  producerFactorySupplier  creates the {@link DynamicRouterProcessor}
         * @param  filterService            the {@link DynamicRouterFilterService}
         * @return                          the {@link DynamicRouterEndpoint} for routing exchanges
         */
        public DynamicRouterEndpoint getInstance(
                final String uri,
                final DynamicRouterComponent component,
                final DynamicRouterConfiguration configuration,
                final Supplier<DynamicRouterProcessorFactory> processorFactorySupplier,
                final Supplier<DynamicRouterProducerFactory> producerFactorySupplier,
                final BiFunction<CamelContext, Expression, RecipientList> recipientListSupplier,
                final DynamicRouterFilterService filterService) {
            return new DynamicRouterEndpoint(
                    uri, component, configuration, processorFactorySupplier, producerFactorySupplier,
                    recipientListSupplier, filterService);
        }

        /**
         * Create the endpoint that routing participants use to send messages.
         *
         * @param  uri           the endpoint URI
         * @param  component     the Dynamic Router {@link org.apache.camel.Component}
         * @param  configuration the {@link DynamicRouterConfiguration}
         * @param  filterService the {@link DynamicRouterFilterService}
         * @return               the {@link DynamicRouterEndpoint} for control channel messages
         */
        public DynamicRouterEndpoint getInstance(
                final String uri,
                final DynamicRouterComponent component,
                final DynamicRouterConfiguration configuration,
                final DynamicRouterFilterService filterService) {
            return new DynamicRouterEndpoint(uri, component, configuration, filterService);
        }
    }
}
