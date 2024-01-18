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

import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Message;
import org.apache.camel.component.dynamicrouter.filter.DynamicRouterFilterService;
import org.apache.camel.component.dynamicrouter.routing.DynamicRouterProcessor.DynamicRouterProcessorFactory;
import org.apache.camel.processor.RecipientList;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.mockito.internal.verification.Times;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.apache.camel.component.dynamicrouter.routing.DynamicRouterConstants.MODE_FIRST_MATCH;
import static org.apache.camel.component.dynamicrouter.routing.DynamicRouterConstants.RECIPIENT_LIST_HEADER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DynamicRouterProcessorTest {

    static final String TEST_CHANNEL = "testChannel";

    static final String MOCK_ENDPOINT = "mock://test";

    @RegisterExtension
    static CamelContextExtension contextExtension = new DefaultCamelContextExtension();

    CamelContext context;

    DynamicRouterProcessor processor;

    @Mock
    DynamicRouterConfiguration configuration;

    @Mock
    RecipientList recipientList;

    @Mock
    DynamicRouterFilterService filterService;

    @Mock
    BiFunction<CamelContext, Expression, RecipientList> recipientListSupplier;

    @Mock
    Exchange exchange;

    @Mock
    Message message;

    @Mock
    AsyncCallback asyncCallback;

    @BeforeEach
    void localSetup() {
        context = contextExtension.getContext();
        processor = new DynamicRouterProcessor(MODE_FIRST_MATCH, false, TEST_CHANNEL, recipientList, filterService);
    }

    @Test
    void testMatchFilters() {
        when(filterService.getMatchingEndpointsForExchangeByChannel(exchange, TEST_CHANNEL, true, false))
                .thenReturn(MOCK_ENDPOINT);
        String result = processor.matchFilters(exchange);
        assertEquals(MOCK_ENDPOINT, result);
    }

    @Test
    void testPrepareExchange() {
        when(exchange.getMessage()).thenReturn(message);
        when(filterService.getMatchingEndpointsForExchangeByChannel(exchange, TEST_CHANNEL, true, false))
                .thenReturn(MOCK_ENDPOINT);
        processor.prepareExchange(exchange);
        verify(message, new Times(1)).setHeader(RECIPIENT_LIST_HEADER, MOCK_ENDPOINT);
    }

    @Test
    void testProcess() throws Exception {
        when(exchange.getMessage()).thenReturn(message);
        when(filterService.getMatchingEndpointsForExchangeByChannel(exchange, TEST_CHANNEL, true, false))
                .thenReturn(MOCK_ENDPOINT);
        processor.process(exchange);
        verify(message, new Times(1)).setHeader(RECIPIENT_LIST_HEADER, MOCK_ENDPOINT);
        verify(recipientList, new Times(1)).process(exchange);
    }

    @Test
    void testProcessAsync() {
        when(exchange.getMessage()).thenReturn(message);
        when(filterService.getMatchingEndpointsForExchangeByChannel(exchange, TEST_CHANNEL, true, false))
                .thenReturn(MOCK_ENDPOINT);
        processor.process(exchange, asyncCallback);
        verify(recipientList, new Times(1)).process(exchange, asyncCallback);
    }

    @Test
    void testGetInstance() {
        when(recipientListSupplier.apply(eq(context), any(Expression.class))).thenReturn(recipientList);
        DynamicRouterProcessor instance = new DynamicRouterProcessorFactory()
                .getInstance(context, configuration, filterService, recipientListSupplier);
        Assertions.assertNotNull(instance);
    }
}
