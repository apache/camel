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

import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.component.dynamicrouter.filter.DynamicRouterFilterService;
import org.apache.camel.component.dynamicrouter.routing.DynamicRouterProducer.DynamicRouterProducerFactory;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DynamicRouterProducerTest {

    @RegisterExtension
    static CamelContextExtension contextExtension = new DefaultCamelContextExtension();

    @Mock
    DynamicRouterComponent component;

    @Mock
    DynamicRouterProducer producer;

    @Mock
    DynamicRouterEndpoint endpoint;

    @Mock
    DynamicRouterProcessor processor;

    @Mock
    DynamicRouterConfiguration configuration;

    @Mock
    DynamicRouterFilterService filterService;

    @Mock
    Exchange exchange;

    @Mock
    AsyncCallback asyncCallback;

    CamelContext context;

    @BeforeEach
    void localSetup() {
        context = contextExtension.getContext();
        producer = new DynamicRouterProducer(endpoint, component, configuration);
    }

    @Test
    void testProcessSynchronous() throws Exception {
        when(component.getRoutingProcessor("testChannel")).thenReturn(processor);
        doNothing().when(processor).process(exchange);
        when(configuration.getChannel()).thenReturn("testChannel");
        assertDoesNotThrow(() -> producer.process(exchange));
    }

    @Test
    void testProcessAsynchronous() {
        when(configuration.getChannel()).thenReturn("testChannel");
        when(component.getRoutingProcessor(anyString())).thenReturn(processor);
        boolean result = producer.process(exchange, asyncCallback);
        assertFalse(result);
    }

    @Test
    void testProcessAsynchronousWithException() {
        when(configuration.getChannel()).thenReturn("testChannel");
        when(configuration.isSynchronous()).thenReturn(true);
        when(component.getRoutingProcessor(anyString())).thenReturn(processor);
        doNothing().when(exchange).setException(any(Throwable.class));
        doThrow(new IllegalArgumentException("Catch me, since I am a test exception!"))
                .when(processor).process(exchange, asyncCallback);
        boolean result = producer.process(exchange, asyncCallback);
        assertTrue(result);
    }

    @Test
    void testGetInstance() {
        DynamicRouterProducer instance = new DynamicRouterProducerFactory()
                .getInstance(endpoint, component, configuration);
        Assertions.assertNotNull(instance);
    }
}
