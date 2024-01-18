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

import org.apache.camel.CamelContext;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.Producer;
import org.apache.camel.component.dynamicrouter.filter.DynamicRouterFilterService;
import org.apache.camel.component.dynamicrouter.filter.PrioritizedFilter;
import org.apache.camel.component.dynamicrouter.filter.PrioritizedFilter.PrioritizedFilterFactory;
import org.apache.camel.component.dynamicrouter.filter.PrioritizedFilterStatistics;
import org.apache.camel.component.dynamicrouter.routing.DynamicRouterEndpoint.DynamicRouterEndpointFactory;
import org.apache.camel.component.dynamicrouter.routing.DynamicRouterProcessor.DynamicRouterProcessorFactory;
import org.apache.camel.component.dynamicrouter.routing.DynamicRouterProducer.DynamicRouterProducerFactory;
import org.apache.camel.processor.RecipientList;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.apache.camel.component.dynamicrouter.routing.DynamicRouterConstants.COMPONENT_SCHEME_ROUTING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class DynamicRouterEndpointTest {

    public static final String DYNAMIC_ROUTER_CHANNEL = "test";

    public static final String BASE_URI = String.format("%s:%s", COMPONENT_SCHEME_ROUTING, DYNAMIC_ROUTER_CHANNEL);

    @RegisterExtension
    static CamelContextExtension contextExtension = new DefaultCamelContextExtension();

    @Mock
    protected DynamicRouterConfiguration configuration;

    @Mock
    protected DynamicRouterProducer producer;

    @Mock
    DynamicRouterComponent component;

    @Mock
    DynamicRouterProcessor processor;

    @Mock
    PrioritizedFilter prioritizedFilter;

    @Mock
    DynamicRouterFilterService filterService;

    @Mock
    RecipientList recipientList;

    @Mock
    BiFunction<CamelContext, Expression, RecipientList> recipientListSupplier;

    DynamicRouterEndpoint endpoint;

    CamelContext context;

    DynamicRouterProcessorFactory processorFactory;

    DynamicRouterProducerFactory producerFactory;

    PrioritizedFilterFactory prioritizedFilterFactory;

    @BeforeEach
    void setup() {
        context = contextExtension.getContext();
        processorFactory = new DynamicRouterProcessorFactory() {
            @Override
            public DynamicRouterProcessor getInstance(
                    CamelContext camelContext, DynamicRouterConfiguration configuration,
                    DynamicRouterFilterService filterService,
                    BiFunction<CamelContext, Expression, RecipientList> recipientListSupplier) {
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
        endpoint = new DynamicRouterEndpoint(
                BASE_URI, component, configuration, () -> processorFactory, () -> producerFactory, recipientListSupplier,
                filterService);
    }

    @Test
    void testCreateProducer() {
        Producer actualProducer = endpoint.createProducer();
        assertEquals(producer, actualProducer);
    }

    @Test
    void testCreateConsumerException() {
        assertThrows(IllegalStateException.class, () -> endpoint.createConsumer(processor));
    }

    @Test
    void testGetInstanceWithDefaults() {
        DynamicRouterEndpoint endpoint = new DynamicRouterEndpointFactory()
                .getInstance(BASE_URI, component, configuration, filterService);
        assertNotNull(endpoint);
    }

    @Test
    void testGetInstance() {
        DynamicRouterEndpoint endpoint = new DynamicRouterEndpointFactory()
                .getInstance(BASE_URI, component, configuration, () -> processorFactory, () -> producerFactory,
                        recipientListSupplier, filterService);
        assertNotNull(endpoint);
    }
}
