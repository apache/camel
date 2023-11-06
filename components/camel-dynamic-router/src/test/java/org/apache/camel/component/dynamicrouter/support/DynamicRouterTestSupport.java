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

import org.apache.camel.*;
import org.apache.camel.component.dynamicrouter.*;
import org.apache.camel.component.dynamicrouter.DynamicRouterControlChannelProcessor.DynamicRouterControlChannelProcessorFactory;
import org.apache.camel.component.dynamicrouter.DynamicRouterControlProducer.DynamicRouterControlProducerFactory;
import org.apache.camel.component.dynamicrouter.DynamicRouterEndpoint.DynamicRouterEndpointFactory;
import org.apache.camel.component.dynamicrouter.DynamicRouterMulticastProcessor.DynamicRouterRecipientListProcessorFactory;
import org.apache.camel.component.dynamicrouter.DynamicRouterProducer.DynamicRouterProducerFactory;
import org.apache.camel.component.dynamicrouter.PrioritizedFilter.PrioritizedFilterFactory;
import org.apache.camel.language.simple.SimpleLanguage;
import org.apache.camel.spi.*;
import org.apache.camel.support.builder.PredicateBuilder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
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
    protected ProcessorExchangeFactory processorExchangeFactory;

    @Mock
    protected InternalProcessorFactory internalProcessorFactory;

    @Mock
    protected SharedInternalProcessor sharedInternalProcessor;

    @Mock
    protected ExchangeFactory exchangeFactory;

    @Mock
    protected ExecutorServiceManager executorServiceManager;

    @Mock
    protected ExecutorService executorService;

    @Mock
    protected ReactiveExecutor reactiveExecutor;

    @Mock
    protected Future<Boolean> booleanFuture;

    @Mock
    protected ProducerCache producerCache;

    @Mock
    protected DynamicRouterConfiguration configuration;

    @Mock
    protected DynamicRouterComponent component;

    @Mock
    protected DynamicRouterEndpoint endpoint;

    @Mock
    protected DynamicRouterMulticastProcessor processor;

    @Mock
    protected DynamicRouterControlChannelProcessor controlChannelProcessor;

    @Mock
    protected PrioritizedFilter prioritizedFilter;

    @Mock
    protected DynamicRouterProducer producer;

    @Mock
    protected DynamicRouterControlProducer controlProducer;

    @Mock
    protected PrioritizedFilter filterProcessorLowPriority;

    @Mock
    protected PrioritizedFilter filterProcessorLowestPriority;

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
    protected DynamicRouterRecipientListProcessorFactory processorFactory;
    protected DynamicRouterControlChannelProcessorFactory controlChannelProcessorFactory;
    protected PrioritizedFilterFactory prioritizedFilterFactory;
    protected DynamicRouterProducerFactory producerFactory;
    protected DynamicRouterControlProducerFactory controlProducerFactory;
    protected PrioritizedFilterFactory filterProcessorFactory;

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

        lenient().when(prioritizedFilter.getId()).thenReturn("testId");
        lenient().when(prioritizedFilter.getPredicate()).thenReturn(predicate);
        lenient().when(prioritizedFilter.getPriority()).thenReturn(TEST_PRIORITY);

        lenient().doNothing().when(processor).process(any(Exchange.class));

        lenient().when(component.getCamelContext()).thenReturn(context);
        lenient().when(component.getRoutingProcessor(anyString())).thenReturn(processor);

        lenient().when(endpoint.getCamelContext()).thenReturn(context);
        lenient().when(endpoint.getComponent()).thenReturn(component);
        lenient().when(endpoint.getDynamicRouterComponent()).thenReturn(component);
        lenient().when(endpoint.getConfiguration()).thenReturn(configuration);

        lenient().doNothing().when(reactiveExecutor).schedule(any());
        lenient().doNothing().when(reactiveExecutor).scheduleQueue(any());
        lenient().doNothing().when(reactiveExecutor).scheduleMain(any());

        lenient().when(context.getCamelContextExtension()).thenReturn(ecc);
        lenient().when(ecc.getExchangeFactory()).thenReturn(exchangeFactory);
        lenient().when(ecc.getProcessorExchangeFactory()).thenReturn(processorExchangeFactory);
        lenient().when(ecc.getContextPlugin(InternalProcessorFactory.class)).thenReturn(internalProcessorFactory);
        lenient().when(ecc.getReactiveExecutor()).thenReturn(reactiveExecutor);
        lenient().when(internalProcessorFactory.createSharedCamelInternalProcessor(context))
                .thenReturn(sharedInternalProcessor);
        lenient().when(context.resolveLanguage("simple")).thenReturn(simpleLanguage);
        lenient().when(context.getExecutorServiceManager()).thenReturn(executorServiceManager);

        lenient().when(executorServiceManager.newDefaultThreadPool(any(DynamicRouterMulticastProcessor.class), anyString()))
                .thenReturn(executorService);

        lenient().when(executorService.submit(ArgumentMatchers.<Callable<Boolean>> any())).thenReturn(booleanFuture);

        lenient().when(predicate.toString()).thenReturn(TEST_PREDICATE);

        lenient().when(simpleLanguage.createPredicate(anyString())).thenReturn(predicate);

        lenient().when(filterProcessorLowPriority.getId()).thenReturn(TEST_ID);
        lenient().when(filterProcessorLowPriority.getPredicate()).thenReturn(predicate);
        lenient().when(filterProcessorLowPriority.getPriority()).thenReturn(Integer.MAX_VALUE - 1000);

        lenient().when(filterProcessorLowestPriority.getPriority()).thenReturn(Integer.MAX_VALUE);

        lenient().doNothing().when(asyncCallback).done(anyBoolean());

        lenient().when(controlMessage.id()).thenReturn(TEST_ID);
        lenient().when(controlMessage.channel()).thenReturn(DYNAMIC_ROUTER_CHANNEL);
        lenient().when(controlMessage.priority()).thenReturn(1);
        lenient().when(controlMessage.predicate()).thenReturn(PredicateBuilder.constant(true));
        lenient().when(controlMessage.endpoint()).thenReturn("test");

        endpointFactory = new DynamicRouterEndpointFactory() {
            @Override
            public DynamicRouterEndpoint getInstance(
                    String uri, DynamicRouterComponent component, DynamicRouterConfiguration configuration,
                    Supplier<DynamicRouterRecipientListProcessorFactory> processorFactorySupplier,
                    Supplier<DynamicRouterProducerFactory> producerFactorySupplier,
                    Supplier<PrioritizedFilterFactory> filterProcessorFactorySupplier) {
                return endpoint;
            }
        };

        processorFactory = new DynamicRouterRecipientListProcessorFactory() {
            @Override
            public DynamicRouterMulticastProcessor getInstance(
                    String id,
                    CamelContext camelContext, Route route, String recipientMode, boolean warnDroppedMessage,
                    Supplier<PrioritizedFilter.PrioritizedFilterFactory> filterProcessorFactorySupplier,
                    ProducerCache producerCache,
                    AggregationStrategy aggregationStrategy, boolean parallelProcessing,
                    ExecutorService executorService, boolean shutdownExecutorService, boolean streaming,
                    boolean stopOnException, long timeout, Processor onPrepare, boolean shareUnitOfWork,
                    boolean parallelAggregate) {
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

        prioritizedFilterFactory = new PrioritizedFilterFactory() {
            @Override
            public PrioritizedFilter getInstance(String id, int priority, Predicate predicate, String endpoint) {
                return prioritizedFilter;
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

        filterProcessorFactory = new PrioritizedFilterFactory() {
            @Override
            public PrioritizedFilter getInstance(String id, int priority, Predicate predicate, String endpoint) {
                return filterProcessorLowPriority;
            }
        };
    }
}
