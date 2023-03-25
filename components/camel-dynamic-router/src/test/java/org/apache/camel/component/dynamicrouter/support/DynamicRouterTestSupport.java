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
package org.apache.camel.component.dynamicrouter.support;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.component.dynamicrouter.DynamicRouterComponent;
import org.apache.camel.component.dynamicrouter.DynamicRouterConfiguration;
import org.apache.camel.component.dynamicrouter.DynamicRouterControlChannelProcessor;
import org.apache.camel.component.dynamicrouter.DynamicRouterControlChannelProcessor.DynamicRouterControlChannelProcessorFactory;
import org.apache.camel.component.dynamicrouter.DynamicRouterControlMessage;
import org.apache.camel.component.dynamicrouter.DynamicRouterControlProducer;
import org.apache.camel.component.dynamicrouter.DynamicRouterControlProducer.DynamicRouterControlProducerFactory;
import org.apache.camel.component.dynamicrouter.DynamicRouterEndpoint;
import org.apache.camel.component.dynamicrouter.DynamicRouterEndpoint.DynamicRouterEndpointFactory;
import org.apache.camel.component.dynamicrouter.DynamicRouterProcessor;
import org.apache.camel.component.dynamicrouter.DynamicRouterProcessor.DynamicRouterProcessorFactory;
import org.apache.camel.component.dynamicrouter.DynamicRouterProducer;
import org.apache.camel.component.dynamicrouter.DynamicRouterProducer.DynamicRouterProducerFactory;
import org.apache.camel.component.dynamicrouter.PrioritizedFilterProcessor;
import org.apache.camel.component.dynamicrouter.PrioritizedFilterProcessor.PrioritizedFilterProcessorFactory;
import org.apache.camel.language.simple.SimpleLanguage;
import org.apache.camel.spi.ExchangeFactory;
import org.apache.camel.spi.ExecutorServiceManager;
import org.apache.camel.support.builder.PredicateBuilder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.apache.camel.component.dynamicrouter.DynamicRouterConstants.COMPONENT_SCHEME;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

/**
 * This support class facilitates testing the Dynamic Router component code. It provides convenient mocking to help to
 * make the tests cleaner and easier to follow.
 */
@ExtendWith(MockitoExtension.class)
public class DynamicRouterTestSupport extends CamelTestSupport {

    public static final String DYNAMIC_ROUTER_CHANNEL = "test";
    public static final String BASE_URI = String.format("%s:%s", COMPONENT_SCHEME, DYNAMIC_ROUTER_CHANNEL);
    public static final String PROCESSOR_ID = "testProcessorId";
    public static final String TEST_ID = "testId";
    public static final int TEST_PRIORITY = 10;
    public static final String TEST_PREDICATE = "testPredicate";

    @Mock
    protected CamelContext context;

    @Mock
    protected ExtendedCamelContext ecc;

    @Mock
    protected ExchangeFactory exchangeFactory;

    @Mock
    protected ExecutorServiceManager executorServiceManager;

    @Mock
    protected ExecutorService executorService;

    @Mock
    protected Future<?> booleanFuture;

    @Mock
    protected DynamicRouterConfiguration configuration;

    @Mock
    protected DynamicRouterComponent component;

    @Mock
    protected DynamicRouterEndpoint endpoint;

    @Mock
    protected DynamicRouterProcessor processor;

    @Mock
    protected DynamicRouterControlChannelProcessor controlChannelProcessor;

    @Mock
    protected PrioritizedFilterProcessor prioritizedFilterProcessor;

    @Mock
    protected DynamicRouterProducer producer;

    @Mock
    protected DynamicRouterControlProducer controlProducer;

    @Mock
    protected PrioritizedFilterProcessor filterProcessorLowPriority;

    @Mock
    protected PrioritizedFilterProcessor filterProcessorLowestPriority;

    @Mock
    protected DynamicRouterControlMessage controlMessage;

    @Mock
    protected AsyncCallback asyncCallback;

    @Mock
    protected Exchange exchange;

    @Mock
    protected SimpleLanguage simpleLanguage;

    @Mock
    protected Predicate predicate;

    // Since most pieces of the Dynamic Router are instantiated by calling factories,
    // this provides greatly simplified testing of all components without extensive
    // mocking or entangling of external units
    protected DynamicRouterEndpointFactory endpointFactory;
    protected DynamicRouterProcessorFactory processorFactory;
    protected DynamicRouterControlChannelProcessorFactory controlChannelProcessorFactory;
    protected PrioritizedFilterProcessorFactory prioritizedFilterProcessorFactory;
    protected DynamicRouterProducerFactory producerFactory;
    protected DynamicRouterControlProducerFactory controlProducerFactory;
    protected PrioritizedFilterProcessorFactory filterProcessorFactory;

    /**
     * Sets up lenient mocking so that regular behavior "just happens" in tests, but each test can customize behavior
     * for any of these mocks by resetting them, or by redefining the instance, and then creating the desired
     * interactions.
     *
     * @throws Exception if there is a problem with setting up via the superclass
     */
    @BeforeEach
    protected void setup() throws Exception {
        super.setUp();

        lenient().when(configuration.getChannel()).thenReturn(DYNAMIC_ROUTER_CHANNEL);

        lenient().when(exchangeFactory.newExchangeFactory(any(Consumer.class))).thenReturn(exchangeFactory);

        lenient().when(prioritizedFilterProcessor.getId()).thenReturn("testId");
        lenient().when(prioritizedFilterProcessor.getPriority()).thenReturn(TEST_PRIORITY);

        lenient().doNothing().when(processor).process(any(Exchange.class));

        lenient().when(component.getCamelContext()).thenReturn(context);
        lenient().when(component.getRoutingProcessor(anyString())).thenReturn(processor);

        lenient().when(endpoint.getCamelContext()).thenReturn(context);
        lenient().when(endpoint.getComponent()).thenReturn(component);
        lenient().when(endpoint.getDynamicRouterComponent()).thenReturn(component);
        lenient().when(endpoint.getConfiguration()).thenReturn(configuration);

        lenient().when(context.getCamelContextExtension()).thenReturn(ecc);
        lenient().when(ecc.getExchangeFactory()).thenReturn(exchangeFactory);
        lenient().when(context.resolveLanguage("simple")).thenReturn(simpleLanguage);
        lenient().when(context.getExecutorServiceManager()).thenReturn(executorServiceManager);

        lenient().when(executorServiceManager.newDefaultThreadPool(any(DynamicRouterProcessor.class), anyString()))
                .thenReturn(executorService);

        lenient().when(executorService.submit(any(Callable.class))).thenReturn(booleanFuture);

        lenient().when(predicate.toString()).thenReturn(TEST_PREDICATE);

        lenient().when(simpleLanguage.createPredicate(anyString())).thenReturn(predicate);

        lenient().when(filterProcessorLowPriority.getId()).thenReturn(TEST_ID);
        lenient().when(filterProcessorLowPriority.getPriority()).thenReturn(Integer.MAX_VALUE - 1000);

        lenient().when(filterProcessorLowestPriority.getPriority()).thenReturn(Integer.MAX_VALUE);

        lenient().doNothing().when(asyncCallback).done(anyBoolean());

        lenient().when(controlMessage.getId()).thenReturn(TEST_ID);
        lenient().when(controlMessage.getChannel()).thenReturn(DYNAMIC_ROUTER_CHANNEL);
        lenient().when(controlMessage.getPriority()).thenReturn(1);
        lenient().when(controlMessage.getPredicate()).thenReturn(PredicateBuilder.constant(true));
        lenient().when(controlMessage.getEndpoint()).thenReturn("test");

        endpointFactory = new DynamicRouterEndpointFactory() {
            @Override
            public DynamicRouterEndpoint getInstance(
                    String uri, DynamicRouterComponent component, DynamicRouterConfiguration configuration,
                    Supplier<DynamicRouterProcessorFactory> processorFactorySupplier,
                    Supplier<DynamicRouterProducerFactory> producerFactorySupplier,
                    Supplier<PrioritizedFilterProcessorFactory> filterProcessorFactorySupplier) {
                return endpoint;
            }
        };

        processorFactory = new DynamicRouterProcessorFactory() {
            @Override
            public DynamicRouterProcessor getInstance(
                    String id, CamelContext camelContext, String recipientMode, boolean warnDroppedMessage,
                    Supplier<PrioritizedFilterProcessorFactory> filterProcessorFactorySupplier) {
                return processor;
            }
        };

        controlChannelProcessorFactory
                = new DynamicRouterControlChannelProcessorFactory() {
                    @Override
                    public DynamicRouterControlChannelProcessor getInstance(DynamicRouterComponent component) {
                        return controlChannelProcessor;
                    }
                };

        prioritizedFilterProcessorFactory = new PrioritizedFilterProcessorFactory() {
            @Override
            public PrioritizedFilterProcessor getInstance(
                    String id, int priority, CamelContext context, Predicate predicate,
                    Processor processor) {
                return prioritizedFilterProcessor;
            }
        };

        producerFactory = new DynamicRouterProducerFactory() {
            @Override
            public DynamicRouterProducer getInstance(DynamicRouterEndpoint endpoint) {
                return producer;
            }
        };

        controlProducerFactory = new DynamicRouterControlProducerFactory() {
            @Override
            public DynamicRouterControlProducer getInstance(DynamicRouterEndpoint endpoint) {
                return controlProducer;
            }
        };

        filterProcessorFactory = new PrioritizedFilterProcessorFactory() {
            @Override
            public PrioritizedFilterProcessor getInstance(
                    String id, int priority, CamelContext context, Predicate predicate, Processor processor) {
                return filterProcessorLowPriority;
            }
        };
    }
}
