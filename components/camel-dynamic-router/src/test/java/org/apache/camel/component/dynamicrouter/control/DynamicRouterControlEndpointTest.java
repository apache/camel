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
package org.apache.camel.component.dynamicrouter.control;

import org.apache.camel.CamelContext;
import org.apache.camel.Processor;
import org.apache.camel.component.dynamicrouter.DynamicRouterFilterService;
import org.apache.camel.component.dynamicrouter.control.DynamicRouterControlProducer.DynamicRouterControlProducerFactory;
import org.apache.camel.component.dynamicrouter.routing.DynamicRouterComponent;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.apache.camel.component.dynamicrouter.routing.DynamicRouterConstants.COMPONENT_SCHEME_ROUTING;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class DynamicRouterControlEndpointTest {

    static final String CONTROL_ENDPOINT_URI = "dynamic-router-control:subscribe?" +
                                               "subscriptionId=testSubscription" +
                                               "&subscribeChannel=testChannel" +
                                               "&destinationUri=testDestination" +
                                               "&priority=10" +
                                               "&predicate=${true}";

    @RegisterExtension
    static CamelContextExtension contextExtension = new DefaultCamelContextExtension();

    @Mock
    DynamicRouterControlComponent component;

    @Mock
    DynamicRouterComponent routerComponent;

    @Mock
    DynamicRouterFilterService filterService;

    @Mock
    DynamicRouterControlConfiguration configuration;

    @Mock
    DynamicRouterControlProducer producer;

    @Mock
    Processor processor;

    CamelContext context;

    DynamicRouterControlEndpoint endpoint;

    DynamicRouterControlProducerFactory producerFactory;

    @BeforeEach
    void setup() {
        context = contextExtension.getContext();
        context.addComponent(COMPONENT_SCHEME_ROUTING, routerComponent);
        endpoint = new DynamicRouterControlEndpoint(
                CONTROL_ENDPOINT_URI, component, "subscribe",
                configuration, () -> producerFactory);
        producerFactory = new DynamicRouterControlProducerFactory() {
            @Override
            public DynamicRouterControlProducer getInstance(
                    DynamicRouterControlEndpoint endpoint, DynamicRouterFilterService filterService,
                    DynamicRouterControlConfiguration configuration) {
                return producer;
            }
        };
        endpoint.setCamelContext(context);
    }

    @AfterEach
    void tearDown() {
        context.removeComponent(COMPONENT_SCHEME_ROUTING);
    }

    @Test
    void createProducer() {
        DynamicRouterControlProducer actualProducer = (DynamicRouterControlProducer) endpoint.createProducer();
        assertEquals(producer, actualProducer);
    }

    @Test
    void testDoStart() throws Exception {
        Mockito.when(routerComponent.getFilterService()).thenReturn(filterService);
        endpoint.doStart();
        Mockito.verify(routerComponent, Mockito.times(1)).getFilterService();
    }

    @Test
    void createConsumerError() {
        assertThrows(IllegalStateException.class, () -> endpoint.createConsumer(processor));
    }
}
