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
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.dynamicrouter.DynamicRouterControlChannelProcessor.DynamicRouterControlChannelProcessorFactory;
import org.apache.camel.component.dynamicrouter.DynamicRouterControlProducer.DynamicRouterControlProducerFactory;
import org.apache.camel.component.dynamicrouter.DynamicRouterProcessor.DynamicRouterProcessorFactory;
import org.apache.camel.component.dynamicrouter.DynamicRouterProducer.DynamicRouterProducerFactory;
import org.apache.camel.component.dynamicrouter.PrioritizedFilterProcessor.PrioritizedFilterProcessorFactory;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.service.ServiceHelper;
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

    /**
     * Creates the {@link DynamicRouterProcessor} instance.
     */
    private Supplier<DynamicRouterProcessorFactory> processorFactorySupplier = DynamicRouterProcessorFactory::new;

    /**
     * Creates a {@link DynamicRouterProducer} instance.
     */
    private Supplier<DynamicRouterProducerFactory> producerFactorySupplier = DynamicRouterProducerFactory::new;

    /**
     * Creates the {@link PrioritizedFilterProcessor} instance.
     */
    private Supplier<PrioritizedFilterProcessorFactory> filterProcessorFactorySupplier = PrioritizedFilterProcessorFactory::new;

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

    /**
     * Create the Dynamic Router {@link org.apache.camel.Endpoint} for the given endpoint URI. This includes the
     * creation of a {@link DynamicRouterProcessor} that is registered with the supplied {@link DynamicRouterComponent}.
     *
     * @param uri                            the endpoint URI
     * @param component                      the Dynamic Router {@link org.apache.camel.Component}
     * @param configuration                  the {@link DynamicRouterConfiguration}
     * @param processorFactorySupplier       creates the {@link DynamicRouterProcessor}
     * @param producerFactorySupplier        creates the {@link DynamicRouterProcessor}
     * @param filterProcessorFactorySupplier creates the {@link PrioritizedFilterProcessor}
     */
    public DynamicRouterEndpoint(
                                 final String uri, final DynamicRouterComponent component,
                                 final DynamicRouterConfiguration configuration,
                                 final Supplier<DynamicRouterProcessorFactory> processorFactorySupplier,
                                 final Supplier<DynamicRouterProducerFactory> producerFactorySupplier,
                                 final Supplier<PrioritizedFilterProcessorFactory> filterProcessorFactorySupplier) {
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
     * @param controlProducerFactorySupplier creates the {@link DynamicRouterProcessor}
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
            final DynamicRouterControlChannelProcessor processor = controlChannelProcessorFactorySupplier.get()
                    .getInstance(component);
            processor.setConfiguration(configuration);
            try {
                // There can be multiple control actions, but we do not want to
                // create another consumer on the control channel, so check to
                // see if the consumer has already been created, and skip the
                // creation of another consumer if one already exists
                if (component.getControlChannelProcessor() == null) {
                    component.setControlChannelProcessor(processor);
                }
            } catch (Exception e) {
                throw new IllegalStateException("Could not create Dynamic Router endpoint", e);
            }
        } else {
            final DynamicRouterProcessor processor = processorFactorySupplier.get()
                    .getInstance("dynamicRouterProcessor-" + configuration.getChannel(), getCamelContext(),
                            configuration.getRecipientMode(), configuration.isWarnDroppedMessage(),
                            filterProcessorFactorySupplier);
            ServiceHelper.startService(processor);
            component.addRoutingProcessor(configuration.getChannel(), processor);
        }
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
         * creation of a {@link DynamicRouterProcessor} that is registered with the supplied
         * {@link DynamicRouterComponent}.
         *
         * @param  uri                            the endpoint URI
         * @param  component                      the Dynamic Router {@link org.apache.camel.Component}
         * @param  configuration                  the {@link DynamicRouterConfiguration}
         * @param  processorFactorySupplier       creates the {@link DynamicRouterProcessor}
         * @param  producerFactorySupplier        creates the {@link DynamicRouterProcessor}
         * @param  filterProcessorFactorySupplier creates the {@link PrioritizedFilterProcessor}
         * @return                                the {@link DynamicRouterEndpoint} for routing exchanges
         */
        public DynamicRouterEndpoint getInstance(
                final String uri,
                final DynamicRouterComponent component,
                final DynamicRouterConfiguration configuration,
                final Supplier<DynamicRouterProcessorFactory> processorFactorySupplier,
                final Supplier<DynamicRouterProducerFactory> producerFactorySupplier,
                final Supplier<PrioritizedFilterProcessorFactory> filterProcessorFactorySupplier) {
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
         * @param  producerFactorySupplier  creates the {@link DynamicRouterProcessor}
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
