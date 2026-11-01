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
import java.util.List;
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
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

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
    void testGetOrCreateRoutingProcessor() throws Exception {
        DynamicRouterConfiguration config = new DynamicRouterConfiguration();
        config.setChannel(DYNAMIC_ROUTER_CHANNEL);
        DynamicRouterEndpoint first = Mockito.mock(DynamicRouterEndpoint.class);
        DynamicRouterEndpoint sameUri = Mockito.mock(DynamicRouterEndpoint.class);
        DynamicRouterEndpoint otherUri = Mockito.mock(DynamicRouterEndpoint.class);
        for (DynamicRouterEndpoint e : List.of(first, sameUri, otherUri)) {
            when(e.getConfiguration()).thenReturn(config);
        }
        when(first.getEndpointUri()).thenReturn("dynamic-router://test");
        when(sameUri.getEndpointUri()).thenReturn("dynamic-router://test");
        when(otherUri.getEndpointUri()).thenReturn("dynamic-router://test?recipientMode=allMatch");
        DynamicRouterProcessor other = Mockito.mock(DynamicRouterProcessor.class);

        assertEquals(processor, component.getOrCreateRoutingProcessor(first, () -> processor));
        // an endpoint with the same uri uses the same processor
        assertEquals(processor, component.getOrCreateRoutingProcessor(sameUri, () -> other));
        // an endpoint with another uri fails while the endpoint that uses the processor is not stopped
        assertThrows(IllegalArgumentException.class, () -> component.getOrCreateRoutingProcessor(otherUri, () -> other));
        assertEquals(processor, component.getRoutingProcessor(DYNAMIC_ROUTER_CHANNEL));
        Mockito.verify(processor, Mockito.never()).stopRecipientList();
        // and replaces the processor once that endpoint is stopped; the channel keeps its processor when the new one
        // cannot be created
        when(sameUri.isProcessorReleased()).thenReturn(true);
        assertThrows(IllegalStateException.class, () -> component.getOrCreateRoutingProcessor(otherUri, () -> {
            throw new IllegalStateException("cannot create");
        }));
        assertEquals(processor, component.getRoutingProcessor(DYNAMIC_ROUTER_CHANNEL));
        Mockito.verify(processor, Mockito.never()).stopRecipientList();
        assertEquals(other, component.getOrCreateRoutingProcessor(otherUri, () -> other));
        assertEquals(other, component.getRoutingProcessor(DYNAMIC_ROUTER_CHANNEL));
        Mockito.verify(processor).stopRecipientList();

        // the component shuts down the processor of the channel and the replaced processor
        component.doShutdown();
        Mockito.verify(processor).shutdownRecipientList();
        Mockito.verify(other).shutdownRecipientList();
        assertNull(component.getRoutingProcessor(DYNAMIC_ROUTER_CHANNEL));
    }

    @Test
    void testDefaultConstruction() {
        DynamicRouterComponent instance = new DynamicRouterComponent();
        Assertions.assertNotNull(instance);
    }
}
