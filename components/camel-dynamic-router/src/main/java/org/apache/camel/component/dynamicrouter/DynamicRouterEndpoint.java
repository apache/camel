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

import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

import org.apache.camel.*;
import org.apache.camel.component.dynamicrouter.DynamicRouterControlChannelProcessor.DynamicRouterControlChannelProcessorFactory;
import org.apache.camel.component.dynamicrouter.DynamicRouterControlProducer.DynamicRouterControlProducerFactory;
import org.apache.camel.component.dynamicrouter.DynamicRouterMulticastProcessor.DynamicRouterRecipientListProcessorFactory;
import org.apache.camel.component.dynamicrouter.DynamicRouterProducer.DynamicRouterProducerFactory;
import org.apache.camel.component.dynamicrouter.PrioritizedFilter.PrioritizedFilterFactory;
import org.apache.camel.processor.aggregate.UseLatestAggregationStrategy;
import org.apache.camel.processor.errorhandler.NoErrorHandler;
import org.apache.camel.spi.ErrorHandler;
import org.apache.camel.spi.ProducerCache;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.cache.DefaultProducerCache;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.dynamicrouter.DynamicRouterConstants.COMPONENT_SCHEME;
import static org.apache.camel.component.dynamicrouter.DynamicRouterConstants.CONTROL_CHANNEL_NAME;
import static org.apache.camel.component.dynamicrouter.DynamicRouterConstants.CONTROL_SYNTAX;
import static org.apache.camel.component.dynamicrouter.DynamicRouterConstants.FIRST_VERSION;
import static org.apache.camel.component.dynamicrouter.DynamicRouterConstants.SYNTAX;
import static org.apache.camel.component.dynamicrouter.DynamicRouterConstants.TITLE;

/**
 * The Dynamic Router component routes exchanges to recipients, and the recipients (and their rules) may change at
 * runtime.
 */
@UriEndpoint(firstVersion = FIRST_VERSION,
             scheme = COMPONENT_SCHEME,
             title = TITLE,
             syntax = SYNTAX,
             alternativeSyntax = CONTROL_SYNTAX,
             producerOnly = true,
             category = { Category.MESSAGING, Category.CORE })
public class DynamicRouterEndpoint extends DefaultEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(DynamicRouterEndpoint.class);

    private static final Processor NOOP_PROCESSOR = exchange -> {
    };

    /**
     * Creates the {@link DynamicRouterMulticastProcessor} instance.
     */
    private Supplier<DynamicRouterRecipientListProcessorFactory> processorFactorySupplier
            = DynamicRouterRecipientListProcessorFactory::new;

    /**
     * Creates a {@link DynamicRouterProducer} instance.
     */
    private Supplier<DynamicRouterProducerFactory> producerFactorySupplier = DynamicRouterProducerFactory::new;

    /**
     * Creates the {@link PrioritizedFilter} instance.
     */
    private Supplier<PrioritizedFilterFactory> filterProcessorFactorySupplier = PrioritizedFilterFactory::new;

    /**
     * Creates the {@link DynamicRouterControlChannelProcessor} instance.
     */
    private Supplier<DynamicRouterControlChannelProcessorFactory> controlChannelProcessorFactorySupplier
            = DynamicRouterControlChannelProcessorFactory::new;

    /**
     * Creates a {@link DynamicRouterControlProducer} instance.
     */
    private Supplier<DynamicRouterControlProducerFactory> controlProducerFactorySupplier
            = DynamicRouterControlProducerFactory::new;

    /**
     * The configuration for the Dynamic Router.
     */
    @UriParam
    private DynamicRouterConfiguration configuration;

    private ProducerCache producerCache;

    /**
     * Create the Dynamic Router {@link org.apache.camel.Endpoint} for the given endpoint URI. This includes the
     * creation of a {@link DynamicRouterMulticastProcessor} that is registered with the supplied
     * {@link DynamicRouterComponent}.
     *
     * @param uri                            the endpoint URI
     * @param component                      the Dynamic Router {@link org.apache.camel.Component}
     * @param configuration                  the {@link DynamicRouterConfiguration}
     * @param processorFactorySupplier       creates the {@link DynamicRouterMulticastProcessor}
     * @param producerFactorySupplier        creates the {@link DynamicRouterMulticastProcessor}
     * @param filterProcessorFactorySupplier creates the {@link PrioritizedFilter}
     */
    public DynamicRouterEndpoint(
                                 final String uri, final DynamicRouterComponent component,
                                 final DynamicRouterConfiguration configuration,
                                 final Supplier<DynamicRouterRecipientListProcessorFactory> processorFactorySupplier,
                                 final Supplier<DynamicRouterProducerFactory> producerFactorySupplier,
                                 final Supplier<PrioritizedFilterFactory> filterProcessorFactorySupplier) {
        super(uri, component);
        this.configuration = configuration;
        this.processorFactorySupplier = processorFactorySupplier;
        this.producerFactorySupplier = producerFactorySupplier;
        this.filterProcessorFactorySupplier = filterProcessorFactorySupplier;
        LOG.debug("Created Dynamic Router endpoint URI: {}", uri);
    }

    /**
     * Create the specialized endpoint for the Dynamic Router Control Channel.
     *
     * @param uri                            the endpoint URI
     * @param component                      the Dynamic Router {@link org.apache.camel.Component}
     * @param configuration                  the {@link DynamicRouterConfiguration}
     * @param processorFactorySupplier       creates the {@link DynamicRouterControlChannelProcessor}
     * @param controlProducerFactorySupplier creates the {@link DynamicRouterMulticastProcessor}
     */
    public DynamicRouterEndpoint(
                                 final String uri, final DynamicRouterComponent component,
                                 final DynamicRouterConfiguration configuration,
                                 final Supplier<DynamicRouterControlChannelProcessorFactory> processorFactorySupplier,
                                 final Supplier<DynamicRouterControlProducerFactory> controlProducerFactorySupplier) {
        super(uri, component);
        this.configuration = configuration;
        this.controlChannelProcessorFactorySupplier = processorFactorySupplier;
        this.controlProducerFactorySupplier = controlProducerFactorySupplier;
        LOG.debug("Created Dynamic Router Control Channel endpoint URI: {}", uri);
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();
        DynamicRouterComponent component = getDynamicRouterComponent();
        if (CONTROL_CHANNEL_NAME.equals(configuration.getChannel())) {
            try {
                // There can be multiple control actions, but we do not want to
                // create another consumer on the control channel, so check to
                // see if the consumer has already been created, and skip the
                // creation of another consumer if one already exists
                if (component.getControlChannelProcessor() == null) {
                    DynamicRouterControlChannelProcessor processor = controlChannelProcessorFactorySupplier.get()
                            .getInstance(component);
                    processor.setConfiguration(configuration);
                    component.setControlChannelProcessor(processor);
                }
            } catch (Exception e) {
                throw new IllegalStateException("Could not create Dynamic Router endpoint", e);
            }
        } else {
            CamelContext camelContext = getCamelContext();
            String routeId = configuration.getRouteId();
            long timeout = configuration.getTimeout();
            ErrorHandler errorHandler = new NoErrorHandler(null);
            if (producerCache == null) {
                producerCache = new DefaultProducerCache(this, camelContext, 1000);
            }
            ExecutorService aggregateExecutorService = camelContext.getExecutorServiceManager()
                    .newScheduledThreadPool(this, "DynamicRouter-AggregateTask", 0);
            AggregationStrategy aggregationStrategy = determineAggregationStrategy(camelContext);
            DynamicRouterMulticastProcessor processor = createProcessor(camelContext, aggregationStrategy, timeout,
                    errorHandler, aggregateExecutorService, routeId);
            ServiceHelper.startService(aggregationStrategy, producerCache, processor);
            component.addRoutingProcessor(configuration.getChannel(), processor);
        }
    }

    protected DynamicRouterMulticastProcessor createProcessor(
            CamelContext camelContext, AggregationStrategy aggregationStrategy, long timeout, ErrorHandler errorHandler,
            ExecutorService aggregateExecutorService, String routeId) {
        DynamicRouterMulticastProcessor processor = processorFactorySupplier.get()
                .getInstance("DynamicRouterMulticastProcessor-" + configuration.getChannel(), camelContext, null,
                        configuration.getRecipientMode(),
                        configuration.isWarnDroppedMessage(), filterProcessorFactorySupplier, producerCache,
                        aggregationStrategy, configuration.isParallelProcessing(),
                        determineExecutorService(camelContext), false,
                        configuration.isStreaming(), configuration.isStopOnException(), timeout,
                        determineOnPrepare(camelContext), configuration.isShareUnitOfWork(),
                        configuration.isParallelAggregate());
        processor.setErrorHandler(errorHandler);
        processor.setAggregateExecutorService(aggregateExecutorService);
        processor.setIgnoreInvalidEndpoints(configuration.isIgnoreInvalidEndpoints());
        processor.setId(getId());
        processor.setRouteId(routeId);
        return processor;
    }

    protected ExecutorService determineExecutorService(CamelContext camelContext) {
        ExecutorService executorService = null;
        if (ObjectHelper.isNotEmpty(configuration.getExecutorService())) {
            executorService = camelContext.getExecutorServiceManager()
                    .newThreadPool(this, "@RecipientList", configuration.getExecutorService());
        }
        if (configuration.isParallelProcessing() && configuration.getExecutorService() == null) {
            // we are running in parallel, so we need a thread pool
            executorService = camelContext.getExecutorServiceManager()
                    .newDefaultThreadPool(this, "@RecipientList");
        }
        return executorService;
    }

    protected AggregationStrategy determineAggregationStrategy(CamelContext camelContext) {
        String cfgStrategy = configuration.getAggregationStrategy();
        return ObjectHelper.isNotEmpty(cfgStrategy)
                ? CamelContextHelper.mandatoryLookup(camelContext, cfgStrategy, AggregationStrategy.class)
                : new UseLatestAggregationStrategy();
    }

    protected Processor determineOnPrepare(CamelContext camelContext) {
        String onPrepare = configuration.getOnPrepare();
        return ObjectHelper.isNotEmpty(onPrepare)
                ? CamelContextHelper.mandatoryLookup(camelContext, onPrepare, Processor.class) : NOOP_PROCESSOR;
    }

    /**
     * Calls the {@link DynamicRouterProducerFactory} to create a {@link DynamicRouterProducer} instance.
     *
     * @return a {@link DynamicRouterProducer} instance
     */
    @Override
    public Producer createProducer() {
        return CONTROL_CHANNEL_NAME.equals(configuration.getChannel())
                ? controlProducerFactorySupplier.get().getInstance(this)
                : producerFactorySupplier.get().getInstance(this);
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
     * Set the {@link DynamicRouterConfiguration}.
     *
     * @param configuration the {@link DynamicRouterConfiguration}
     */
    public void setConfiguration(final DynamicRouterConfiguration configuration) {
        this.configuration = configuration;
    }

    /**
     * Create a {@link DynamicRouterEndpoint} instance.
     */
    public static class DynamicRouterEndpointFactory {

        /**
         * Create the Dynamic Router {@link org.apache.camel.Endpoint} for the given endpoint URI. This includes the
         * creation of a {@link DynamicRouterMulticastProcessor} that is registered with the supplied
         * {@link DynamicRouterComponent}.
         *
         * @param  uri                            the endpoint URI
         * @param  component                      the Dynamic Router {@link org.apache.camel.Component}
         * @param  configuration                  the {@link DynamicRouterConfiguration}
         * @param  processorFactorySupplier       creates the {@link DynamicRouterMulticastProcessor}
         * @param  producerFactorySupplier        creates the {@link DynamicRouterMulticastProcessor}
         * @param  filterProcessorFactorySupplier creates the {@link PrioritizedFilter}
         * @return                                the {@link DynamicRouterEndpoint} for routing exchanges
         */
        public DynamicRouterEndpoint getInstance(
                final String uri,
                final DynamicRouterComponent component,
                final DynamicRouterConfiguration configuration,
                final Supplier<DynamicRouterRecipientListProcessorFactory> processorFactorySupplier,
                final Supplier<DynamicRouterProducerFactory> producerFactorySupplier,
                final Supplier<PrioritizedFilterFactory> filterProcessorFactorySupplier) {
            return new DynamicRouterEndpoint(
                    uri, component, configuration, processorFactorySupplier, producerFactorySupplier,
                    filterProcessorFactorySupplier);
        }

        /**
         * Create a specialized Dynamic Router {@link org.apache.camel.Endpoint} for the control channel endpoint URI.
         * This includes the creation of a {@link DynamicRouterControlChannelProcessor} to instantiate a that is
         * registered with the supplied {@link DynamicRouterComponent}. Routing participants use this endpoint to supply
         * {@link DynamicRouterControlMessage}s to subscribe or unsubscribe.
         *
         * @param  uri                      the endpoint URI
         * @param  component                the Dynamic Router {@link org.apache.camel.Component}
         * @param  configuration            the {@link DynamicRouterConfiguration}
         * @param  processorFactorySupplier creates the {@link DynamicRouterControlChannelProcessor}
         * @param  producerFactorySupplier  creates the {@link DynamicRouterMulticastProcessor}
         * @return                          the {@link DynamicRouterEndpoint} for control channel messages
         */
        public DynamicRouterEndpoint getInstance(
                final String uri,
                final DynamicRouterComponent component,
                final DynamicRouterConfiguration configuration,
                final Supplier<DynamicRouterControlChannelProcessorFactory> processorFactorySupplier,
                final Supplier<DynamicRouterControlProducerFactory> producerFactorySupplier) {
            return new DynamicRouterEndpoint(
                    uri, component, configuration, processorFactorySupplier, producerFactorySupplier);
        }
    }
}
