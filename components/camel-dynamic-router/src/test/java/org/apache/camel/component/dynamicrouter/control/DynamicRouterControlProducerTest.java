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

import java.util.Map;

import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.Message;
import org.apache.camel.Predicate;
import org.apache.camel.component.dynamicrouter.DynamicRouterFilterService;
import org.apache.camel.spi.Language;
import org.apache.camel.support.builder.PredicateBuilder;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.apache.camel.component.dynamicrouter.control.DynamicRouterControlConstants.CONTROL_ACTION_HEADER;
import static org.apache.camel.component.dynamicrouter.control.DynamicRouterControlConstants.CONTROL_DESTINATION_URI;
import static org.apache.camel.component.dynamicrouter.control.DynamicRouterControlConstants.CONTROL_EXPRESSION_LANGUAGE;
import static org.apache.camel.component.dynamicrouter.control.DynamicRouterControlConstants.CONTROL_PREDICATE;
import static org.apache.camel.component.dynamicrouter.control.DynamicRouterControlConstants.CONTROL_PRIORITY;
import static org.apache.camel.component.dynamicrouter.control.DynamicRouterControlConstants.CONTROL_SUBSCRIBE_CHANNEL;
import static org.apache.camel.component.dynamicrouter.control.DynamicRouterControlConstants.CONTROL_SUBSCRIPTION_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;

@ExtendWith(MockitoExtension.class)
class DynamicRouterControlProducerTest {

    @RegisterExtension
    static CamelContextExtension contextExtension = new DefaultCamelContextExtension();

    @Mock
    DynamicRouterFilterService filterService;

    @Mock
    DynamicRouterControlConfiguration configuration;

    @Mock
    DynamicRouterControlEndpoint endpoint;

    @Mock
    Message message;

    @Mock
    AsyncCallback callback;

    CamelContext context;

    DynamicRouterControlProducer producer;

    @BeforeEach
    void setup() {
        context = contextExtension.getContext();
        producer = new DynamicRouterControlProducer(endpoint, filterService, configuration);
        producer.setCamelContext(context);
    }

    @Test
    void obtainPredicateFromBean() {
        String beanName = "testPredicate";
        Predicate expectedPredicate = PredicateBuilder.constant(true);
        context.getRegistry().bind(beanName, Predicate.class, expectedPredicate);
        Predicate actualPredicate = DynamicRouterControlProducer.obtainPredicate(context, "", "", "", beanName);
        assertEquals(expectedPredicate, actualPredicate);
    }

    @Test
    void obtainPredicateFromExpression() {
        String expressionLanguage = "simple";
        String trueExpression = "true";
        Language language = context.resolveLanguage(expressionLanguage);
        Predicate expectedPredicate = language.createPredicate(trueExpression);
        Predicate actualPredicate
                = DynamicRouterControlProducer.obtainPredicate(context, "", expressionLanguage, trueExpression, null);
        assertEquals(expectedPredicate, actualPredicate);
    }

    @Test
    void obtainPredicateFromBody() {
        Predicate expectedPredicate = PredicateBuilder.constant(true);
        Predicate actualPredicate = DynamicRouterControlProducer.obtainPredicate(context, expectedPredicate, "", "", null);
        assertEquals(expectedPredicate, actualPredicate);
    }

    @Test
    void performSubscribeAction() {
        String subscribeChannel = "testChannel";
        Map<String, Object> headers = Map.of(
                CONTROL_ACTION_HEADER, "subscribe",
                CONTROL_SUBSCRIBE_CHANNEL, subscribeChannel,
                CONTROL_SUBSCRIPTION_ID, "testId",
                CONTROL_DESTINATION_URI, "mock://test",
                CONTROL_PREDICATE, "true",
                CONTROL_EXPRESSION_LANGUAGE, "simple",
                CONTROL_PRIORITY, 10);
        Mockito.when(message.getHeaders()).thenReturn(headers);
        Mockito.doNothing().when(filterService).addFilterForChannel(anyString(), anyInt(), any(Predicate.class), anyString(),
                anyString());
        Mockito.doNothing().when(callback).done(false);
        producer.performSubscribe(message, callback);
        Mockito.verify(filterService, Mockito.times(1)).addFilterForChannel(anyString(), anyInt(), any(Predicate.class),
                anyString(),
                anyString());
    }

    @Test
    void performUnsubscribeAction() {
        String subscriptionId = "testId";
        String subscribeChannel = "testChannel";
        Map<String, Object> headers = Map.of(
                CONTROL_ACTION_HEADER, "unsubscribe",
                CONTROL_SUBSCRIBE_CHANNEL, subscribeChannel,
                CONTROL_SUBSCRIPTION_ID, subscriptionId);
        Mockito.when(message.getHeaders()).thenReturn(headers);
        Mockito.doNothing().when(filterService).removeFilterById(subscriptionId, subscribeChannel);
        Mockito.doNothing().when(callback).done(false);
        producer.performUnsubscribe(message, callback);
        Mockito.verify(filterService, Mockito.times(1)).removeFilterById(subscriptionId, subscribeChannel);
    }

    @Test
    void performSubscribeActionWithPredicateInBody() {
        String subscribeChannel = "testChannel";
        Map<String, Object> headers = Map.of(
                CONTROL_ACTION_HEADER, "subscribe",
                CONTROL_SUBSCRIBE_CHANNEL, subscribeChannel,
                CONTROL_SUBSCRIPTION_ID, "testId",
                CONTROL_DESTINATION_URI, "mock://test",
                CONTROL_PRIORITY, 10);
        Language language = context.resolveLanguage("simple");
        Predicate predicate = language.createPredicate("true");
        Mockito.when(message.getBody()).thenReturn(predicate);
        Mockito.when(message.getHeaders()).thenReturn(headers);
        Mockito.doNothing().when(filterService).addFilterForChannel(anyString(), anyInt(), any(Predicate.class), anyString(),
                anyString());
        Mockito.doNothing().when(callback).done(false);
        producer.performSubscribe(message, callback);
        Mockito.verify(filterService, Mockito.times(1)).addFilterForChannel(anyString(), anyInt(), any(Predicate.class),
                anyString(),
                anyString());
    }

    @Test
    void obtainPredicateFromExpressionWithError() {
        String expression = "not a valid expression";
        String language = "simple";
        assertThrows(IllegalArgumentException.class,
                () -> DynamicRouterControlProducer.obtainPredicateFromExpression(context, expression, language));
    }
}
