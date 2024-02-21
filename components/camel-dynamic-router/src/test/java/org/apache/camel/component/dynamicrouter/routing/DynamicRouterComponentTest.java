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

import java.util.Collections;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.component.dynamicrouter.filter.DynamicRouterFilterService;
import org.apache.camel.component.dynamicrouter.filter.DynamicRouterFilterService.DynamicRouterFilterServiceFactory;
import org.apache.camel.component.dynamicrouter.filter.PrioritizedFilter;
import org.apache.camel.component.dynamicrouter.filter.PrioritizedFilter.PrioritizedFilterFactory;
import org.apache.camel.component.dynamicrouter.filter.PrioritizedFilterStatistics;
import org.apache.camel.component.dynamicrouter.routing.DynamicRouterEndpoint.DynamicRouterEndpointFactory;
import org.apache.camel.component.dynamicrouter.routing.DynamicRouterProcessor.DynamicRouterProcessorFactory;
import org.apache.camel.component.dynamicrouter.routing.DynamicRouterProducer.DynamicRouterProducerFactory;
import org.apache.camel.processor.RecipientList;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class DynamicRouterComponentTest {

    static final String DYNAMIC_ROUTER_CHANNEL = "test";

    @RegisterExtension
    static CamelContextExtension contextExtension = new DefaultCamelContextExtension();

    @Mock
    protected DynamicRouterProducer producer;

    @Mock
    DynamicRouterEndpoint endpoint;

    @Mock
    DynamicRouterProcessor processor;

    @Mock
    PrioritizedFilter prioritizedFilter;

    @Mock
    DynamicRouterFilterService filterService;

    DynamicRouterComponent component;

    CamelContext context;

    DynamicRouterEndpointFactory endpointFactory;

    DynamicRouterProcessorFactory processorFactory;

    DynamicRouterProducerFactory producerFactory;

    BiFunction<CamelContext, Expression, RecipientList> recipientListSupplier;

    PrioritizedFilterFactory prioritizedFilterFactory;

    DynamicRouterFilterServiceFactory filterServiceFactory;

    @BeforeEach
    void setup() {
        context = contextExtension.getContext();
        endpointFactory = new DynamicRouterEndpointFactory() {
            @Override
            public DynamicRouterEndpoint getInstance(
                    final String uri,
                    final DynamicRouterComponent component,
                    final DynamicRouterConfiguration configuration,
                    final Supplier<DynamicRouterProcessorFactory> processorFactorySupplier,
                    final Supplier<DynamicRouterProducerFactory> producerFactorySupplier,
                    final BiFunction<CamelContext, Expression, RecipientList> recipientListSupplier,
                    final DynamicRouterFilterService filterService) {
                return endpoint;
            }
        };
        processorFactory = new DynamicRouterProcessorFactory() {
            @Override
            public DynamicRouterProcessor getInstance(
                    CamelContext camelContext, DynamicRouterConfiguration configuration,
                    DynamicRouterFilterService filterService,
                    final BiFunction<CamelContext, Expression, RecipientList> recipientListSupplier) {
                return processor;
            }
        };
        producerFactory = new DynamicRouterProducerFactory() {
            @Override
            public DynamicRouterProducer getInstance(
                    DynamicRouterEndpoint endpoint, DynamicRouterComponent component,
                    DynamicRouterConfiguration configuration) {
                return producer;
            }
        };
        prioritizedFilterFactory = new PrioritizedFilterFactory() {
            @Override
            public PrioritizedFilter getInstance(
                    String id, int priority, Predicate predicate, String endpoint, PrioritizedFilterStatistics statistics) {
                return prioritizedFilter;
            }
        };
        filterServiceFactory = new DynamicRouterFilterServiceFactory() {
            @Override
            public DynamicRouterFilterService getInstance(Supplier<PrioritizedFilterFactory> filterFactory) {
                return filterService;
            }
        };
        component = new DynamicRouterComponent(
                () -> endpointFactory, () -> processorFactory, () -> producerFactory, recipientListSupplier,
                () -> prioritizedFilterFactory, () -> filterServiceFactory);
    }

    @Test
    void testCreateEndpoint() throws Exception {
        component.setCamelContext(context);
        Endpoint actualEndpoint = component.createEndpoint("dynamic-router:testname", "remaining", Collections.emptyMap());
        assertEquals(endpoint, actualEndpoint);
    }

    @Test
    void testCreateEndpointWithEmptyRemainingError() {
        component.setCamelContext(context);
        assertThrows(IllegalArgumentException.class,
                () -> component.createEndpoint("dynamic-router:testname", "", Collections.emptyMap()));
    }

    @Test
    void testAddRoutingProcessor() {
        component.addRoutingProcessor(DYNAMIC_ROUTER_CHANNEL, processor);
        assertEquals(processor, component.getRoutingProcessor(DYNAMIC_ROUTER_CHANNEL));
    }

    @Test
    void testAddRoutingProcessorWithSecondProcessorForChannelError() {
        component.addRoutingProcessor(DYNAMIC_ROUTER_CHANNEL, processor);
        assertEquals(processor, component.getRoutingProcessor(DYNAMIC_ROUTER_CHANNEL));
        assertThrows(IllegalArgumentException.class, () -> component.addRoutingProcessor(DYNAMIC_ROUTER_CHANNEL, processor));
    }

    @Test
    void testDefaultConstruction() {
        DynamicRouterComponent instance = new DynamicRouterComponent();
        Assertions.assertNotNull(instance);
    }
}
