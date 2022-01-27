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

import org.apache.camel.Message;
import org.apache.camel.NoSuchLanguageException;
import org.apache.camel.Predicate;
import org.apache.camel.component.dynamicrouter.DynamicRouterControlMessage.SubscribeMessageBuilder;
import org.apache.camel.component.dynamicrouter.DynamicRouterControlMessage.UnsubscribeMessageBuilder;
import org.apache.camel.component.dynamicrouter.support.DynamicRouterTestSupport;
import org.apache.camel.spi.Language;
import org.apache.camel.support.builder.PredicateBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.dynamicrouter.DynamicRouterConstants.CONTROL_ACTION_SUBSCRIBE;
import static org.apache.camel.component.dynamicrouter.DynamicRouterConstants.CONTROL_ACTION_UNSUBSCRIBE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DynamicRouterControlChannelProcessorTest extends DynamicRouterTestSupport {

    @BeforeEach
    void localSetup() throws Exception {
        super.setup();
        controlChannelProcessor = new DynamicRouterControlChannelProcessor(component);
        controlChannelProcessor.setConfiguration(configuration);
    }

    @Test
    void testObtainPredicateFromUri() {
        String language = "simple";
        String expression = "${body} regex '^\\d*[02468]$'";
        when(configuration.getExpressionLanguage()).thenReturn(language);
        when(configuration.getPredicate()).thenReturn(expression);

        final Predicate result = controlChannelProcessor.obtainPredicate(null);

        assertNotNull(result, "Predicate was null");
    }

    @Test
    void testObtainPredicateFromBody() {
        String exLang = "simple";
        String expression = "${body} regex '^\\d*[02468]$'";
        Language language = component.getCamelContext().resolveLanguage(exLang);
        Predicate predicate = language.createPredicate(expression);
        when(configuration.getExpressionLanguage()).thenReturn(null);
        when(configuration.getPredicate()).thenReturn(null);

        final Predicate result = controlChannelProcessor.obtainPredicate(predicate);

        assertEquals(predicate, result, "The expected predicate was not returned");
    }

    @Test
    void testObtainPredicateWithLanguageResolutionError() {
        String language = "bad language";
        String expression = "bad expression";
        when(configuration.getExpressionLanguage()).thenReturn(language);
        when(configuration.getPredicate()).thenReturn(expression);
        when(configuration.getPredicateBean()).thenReturn(null);
        when(context.resolveLanguage(language)).thenThrow(new NoSuchLanguageException("test"));

        assertThrows(IllegalArgumentException.class, () -> controlChannelProcessor.obtainPredicate(null));
    }

    @Test
    void testObtainPredicateWithNoPredicateError() {
        assertThrows(IllegalArgumentException.class, () -> controlChannelProcessor.obtainPredicate("oops"));
    }

    @Test
    void testHandleSubscribeMessageFromUri() {
        String language = "simple";
        String expression = "${body} regex '^\\d*[02468]$'";
        when(configuration.getControlAction()).thenReturn(CONTROL_ACTION_SUBSCRIBE);
        when(configuration.getSubscribeChannel()).thenReturn("test");
        when(configuration.getSubscriptionId()).thenReturn(TEST_ID);
        when(configuration.getDestinationUri()).thenReturn("testUri");
        when(configuration.getPriority()).thenReturn(10);
        when(configuration.getExpressionLanguage()).thenReturn(language);
        when(configuration.getPredicate()).thenReturn(expression);
        Message message = mock(Message.class);
        when(message.getBody()).thenReturn(null);
        when(exchange.getIn()).thenReturn(message);

        final DynamicRouterControlMessage result = controlChannelProcessor.handleControlMessage(exchange);

        assertNotNull(result);
        assertEquals(CONTROL_ACTION_SUBSCRIBE, result.getMessageType().name().toLowerCase());
        assertEquals(DYNAMIC_ROUTER_CHANNEL, result.getChannel());
        assertEquals(TEST_ID, result.getId());
        assertEquals("testUri", result.getEndpoint());
        assertEquals(10, result.getPriority());
    }

    @Test
    void testHandleUnsubscribeMessageFromUri() {
        when(configuration.getControlAction()).thenReturn(CONTROL_ACTION_UNSUBSCRIBE);
        when(configuration.getSubscribeChannel()).thenReturn("test");
        when(configuration.getSubscriptionId()).thenReturn(TEST_ID);
        Message message = mock(Message.class);
        when(message.getBody()).thenReturn(null);
        when(exchange.getIn()).thenReturn(message);

        final DynamicRouterControlMessage result = controlChannelProcessor.handleControlMessage(exchange);

        assertNotNull(result);
        assertEquals(CONTROL_ACTION_UNSUBSCRIBE, result.getMessageType().name().toLowerCase());
        assertEquals(DYNAMIC_ROUTER_CHANNEL, result.getChannel());
        assertEquals(TEST_ID, result.getId());
    }

    @Test
    void testHandleSubscribeMessageFromBody() {
        when(configuration.getControlAction()).thenReturn(null);
        final DynamicRouterControlMessage body = new SubscribeMessageBuilder()
                .id(TEST_ID)
                .channel(DYNAMIC_ROUTER_CHANNEL)
                .priority(10)
                .endpointUri("testUri")
                .predicate(PredicateBuilder.constant(true))
                .build();
        Message message = mock(Message.class);
        when(message.getBody()).thenReturn(body);
        when(exchange.getIn()).thenReturn(message);

        final DynamicRouterControlMessage result = controlChannelProcessor.handleControlMessage(exchange);

        assertEquals(body, result, "Did not receive expected subscribe message");
    }

    @Test
    void testHandleUnsubscribeMessageFromBody() {
        when(configuration.getControlAction()).thenReturn(null);
        final DynamicRouterControlMessage body = new UnsubscribeMessageBuilder()
                .id(TEST_ID)
                .channel(DYNAMIC_ROUTER_CHANNEL)
                .build();
        Message message = mock(Message.class);
        when(message.getBody()).thenReturn(body);
        when(exchange.getIn()).thenReturn(message);

        final DynamicRouterControlMessage result = controlChannelProcessor.handleControlMessage(exchange);

        assertEquals(body, result, "Did not receive expected unsubscribe message");
    }

    @Test
    void testHandleControlMessageWithIllegalControlActionError() {
        when(configuration.getControlAction()).thenReturn("oops");
        Message message = mock(Message.class);
        when(exchange.getIn()).thenReturn(message);

        assertThrows(IllegalArgumentException.class, () -> controlChannelProcessor.handleControlMessage(exchange));
    }

    @Test
    void testHandleControlMessageWithNoControlMessageError() {
        when(configuration.getControlAction()).thenReturn(null);
        Message message = mock(Message.class);
        when(message.getBody()).thenReturn("oops");
        when(exchange.getIn()).thenReturn(message);

        assertThrows(IllegalArgumentException.class, () -> controlChannelProcessor.handleControlMessage(exchange));
    }

    @Test
    void testProcessSubscribe() {
        when(configuration.getControlAction()).thenReturn(null);
        final DynamicRouterControlMessage body = new SubscribeMessageBuilder()
                .id(TEST_ID)
                .channel(DYNAMIC_ROUTER_CHANNEL)
                .priority(10)
                .endpointUri("testUri")
                .predicate(PredicateBuilder.constant(true))
                .build();
        Message message = mock(Message.class);
        when(message.getBody()).thenReturn(body);
        when(exchange.getIn()).thenReturn(message);
        doNothing().when(processor).addFilter(any(DynamicRouterControlMessage.class));

        boolean result = controlChannelProcessor.process(exchange, asyncCallback);

        assertTrue(result, "Expected 'true' result");
        verify(processor, times(1)).addFilter(body);
    }

    @Test
    void testProcessUnsubscribe() {
        when(configuration.getControlAction()).thenReturn(null);
        final DynamicRouterControlMessage body = new UnsubscribeMessageBuilder()
                .id(TEST_ID)
                .channel(DYNAMIC_ROUTER_CHANNEL)
                .build();
        Message message = mock(Message.class);
        when(message.getBody()).thenReturn(body);
        when(exchange.getIn()).thenReturn(message);
        doNothing().when(processor).removeFilter(TEST_ID);

        boolean result = controlChannelProcessor.process(exchange, asyncCallback);

        assertTrue(result, "Expected 'true' result");
        verify(processor, times(1)).removeFilter(TEST_ID);
    }
}
