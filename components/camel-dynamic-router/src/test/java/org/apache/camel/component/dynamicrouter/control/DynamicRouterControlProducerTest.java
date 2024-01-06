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
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Predicate;
import org.apache.camel.component.dynamicrouter.control.DynamicRouterControlProducer.DynamicRouterControlProducerFactory;
import org.apache.camel.spi.Language;
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

import static org.apache.camel.component.dynamicrouter.control.DynamicRouterControlConstants.CONTROL_ACTION_HEADER;
import static org.apache.camel.component.dynamicrouter.control.DynamicRouterControlConstants.CONTROL_ACTION_LIST;
import static org.apache.camel.component.dynamicrouter.control.DynamicRouterControlConstants.CONTROL_ACTION_STATS;
import static org.apache.camel.component.dynamicrouter.control.DynamicRouterControlConstants.CONTROL_ACTION_SUBSCRIBE;
import static org.apache.camel.component.dynamicrouter.control.DynamicRouterControlConstants.CONTROL_ACTION_UNSUBSCRIBE;
import static org.apache.camel.component.dynamicrouter.control.DynamicRouterControlConstants.CONTROL_ACTION_UPDATE;
import static org.apache.camel.component.dynamicrouter.control.DynamicRouterControlConstants.CONTROL_DESTINATION_URI;
import static org.apache.camel.component.dynamicrouter.control.DynamicRouterControlConstants.CONTROL_EXPRESSION_LANGUAGE;
import static org.apache.camel.component.dynamicrouter.control.DynamicRouterControlConstants.CONTROL_PREDICATE;
import static org.apache.camel.component.dynamicrouter.control.DynamicRouterControlConstants.CONTROL_PREDICATE_BEAN;
import static org.apache.camel.component.dynamicrouter.control.DynamicRouterControlConstants.CONTROL_PRIORITY;
import static org.apache.camel.component.dynamicrouter.control.DynamicRouterControlConstants.CONTROL_SUBSCRIBE_CHANNEL;
import static org.apache.camel.component.dynamicrouter.control.DynamicRouterControlConstants.CONTROL_SUBSCRIPTION_ID;

@ExtendWith(MockitoExtension.class)
class DynamicRouterControlProducerTest {

    @RegisterExtension
    static CamelContextExtension contextExtension = new DefaultCamelContextExtension();

    @Mock
    DynamicRouterControlService controlService;

    @Mock
    DynamicRouterControlConfiguration configuration;

    @Mock
    DynamicRouterControlEndpoint endpoint;

    @Mock
    Exchange exchange;

    @Mock
    Message message;

    @Mock
    AsyncCallback callback;

    CamelContext context;

    DynamicRouterControlProducer producer;

    @BeforeEach
    void setup() {
        context = contextExtension.getContext();
        producer = new DynamicRouterControlProducer(endpoint, controlService, configuration);
        producer.setCamelContext(context);
    }

    @Test
    void performSubscribeAction() {
        String subscribeChannel = "testChannel";
        Map<String, Object> headers = Map.of(
                CONTROL_ACTION_HEADER, CONTROL_ACTION_SUBSCRIBE,
                CONTROL_SUBSCRIBE_CHANNEL, subscribeChannel,
                CONTROL_SUBSCRIPTION_ID, "testId",
                CONTROL_DESTINATION_URI, "mock://test",
                CONTROL_PREDICATE, "true",
                CONTROL_EXPRESSION_LANGUAGE, "simple",
                CONTROL_PRIORITY, 10);
        Mockito.when(message.getHeaders()).thenReturn(headers);
        Mockito.doNothing().when(callback).done(false);
        producer.performSubscribe(message, callback);
        Mockito.verify(controlService, Mockito.times(1)).subscribeWithPredicateExpression(
                subscribeChannel, "testId", "mock://test", 10, "true", "simple", false);
    }

    @Test
    void performUnsubscribeAction() {
        String subscriptionId = "testId";
        String subscribeChannel = "testChannel";
        Map<String, Object> headers = Map.of(
                CONTROL_ACTION_HEADER, CONTROL_ACTION_UNSUBSCRIBE,
                CONTROL_SUBSCRIBE_CHANNEL, subscribeChannel,
                CONTROL_SUBSCRIPTION_ID, subscriptionId);
        Mockito.when(message.getHeaders()).thenReturn(headers);
        Mockito.doNothing().when(callback).done(false);
        producer.performUnsubscribe(message, callback);
        Mockito.verify(controlService, Mockito.times(1))
                .removeSubscription(subscribeChannel, subscriptionId);
    }

    @Test
    void performUpdateAction() {
        // First, perform initial subscription
        String subscribeChannel = "testChannel";
        Map<String, Object> headers = Map.of(
                CONTROL_ACTION_HEADER, CONTROL_ACTION_SUBSCRIBE,
                CONTROL_SUBSCRIBE_CHANNEL, subscribeChannel,
                CONTROL_SUBSCRIPTION_ID, "testId",
                CONTROL_DESTINATION_URI, "mock://test",
                CONTROL_PREDICATE, "true",
                CONTROL_EXPRESSION_LANGUAGE, "simple",
                CONTROL_PRIORITY, 10);
        Mockito.when(message.getHeaders()).thenReturn(headers);
        Mockito.doNothing().when(callback).done(false);
        producer.performSubscribe(message, callback);
        Mockito.verify(controlService, Mockito.times(1))
                .subscribeWithPredicateExpression(
                        subscribeChannel, "testId", "mock://test", 10, "true", "simple", false);

        // Then, perform update
        headers = Map.of(
                CONTROL_ACTION_HEADER, CONTROL_ACTION_UPDATE,
                CONTROL_SUBSCRIBE_CHANNEL, subscribeChannel,
                CONTROL_SUBSCRIPTION_ID, "testId",
                CONTROL_DESTINATION_URI, "mock://testUpdate",
                CONTROL_PREDICATE, "true",
                CONTROL_EXPRESSION_LANGUAGE, "simple",
                CONTROL_PRIORITY, 100);
        Mockito.when(message.getHeaders()).thenReturn(headers);
        Mockito.doNothing().when(callback).done(false);
        producer.performUpdate(message, callback);
        Mockito.verify(controlService, Mockito.times(1))
                .subscribeWithPredicateExpression(
                        subscribeChannel, "testId", "mock://testUpdate", 100, "true", "simple", true);
    }

    @Test
    void performSubscribeActionWithPredicateInBody() {
        String subscribeChannel = "testChannel";
        Map<String, Object> headers = Map.of(
                CONTROL_ACTION_HEADER, CONTROL_ACTION_SUBSCRIBE,
                CONTROL_SUBSCRIBE_CHANNEL, subscribeChannel,
                CONTROL_SUBSCRIPTION_ID, "testId",
                CONTROL_DESTINATION_URI, "mock://test",
                CONTROL_PRIORITY, 10);
        Language language = context.resolveLanguage("simple");
        Predicate predicate = language.createPredicate("true");
        Mockito.when(message.getBody()).thenReturn(predicate);
        Mockito.when(message.getHeaders()).thenReturn(headers);
        Mockito.doNothing().when(callback).done(false);
        producer.performSubscribe(message, callback);
        Mockito.verify(controlService, Mockito.times(1))
                .subscribeWithPredicateInstance(
                        subscribeChannel, "testId", "mock://test", 10, predicate, false);
    }

    @Test
    void performSubscribeActionWithPredicateBean() {
        String subscribeChannel = "testChannel";
        Map<String, Object> headers = Map.of(
                CONTROL_ACTION_HEADER, CONTROL_ACTION_SUBSCRIBE,
                CONTROL_SUBSCRIBE_CHANNEL, subscribeChannel,
                CONTROL_SUBSCRIPTION_ID, "testId",
                CONTROL_DESTINATION_URI, "mock://test",
                CONTROL_PRIORITY, 10,
                CONTROL_PREDICATE_BEAN, "testPredicate");
        Mockito.when(message.getHeaders()).thenReturn(headers);
        Mockito.doNothing().when(callback).done(false);
        producer.performSubscribe(message, callback);
        Mockito.verify(controlService, Mockito.times(1))
                .subscribeWithPredicateBean(
                        subscribeChannel, "testId", "mock://test", 10, "testPredicate", false);
    }

    @Test
    void performSubscribeActionWithMessageInBody() {
        String subscribeChannel = "testChannel";
        DynamicRouterControlMessage subMsg = DynamicRouterControlMessage.Builder.newBuilder()
                .subscribeChannel(subscribeChannel)
                .subscriptionId("testId")
                .destinationUri("mock://test")
                .priority(10)
                .predicate("true")
                .expressionLanguage("simple")
                .build();
        Mockito.when(message.getBody()).thenReturn(subMsg);
        Mockito.when(message.getBody(DynamicRouterControlMessage.class)).thenReturn(subMsg);
        Mockito.doNothing().when(callback).done(false);
        producer.performSubscribe(message, callback);
        Mockito.verify(controlService, Mockito.times(1))
                .subscribeWithPredicateExpression(
                        subscribeChannel, "testId", "mock://test", 10, "true", "simple", false);
    }

    @Test
    void performSubscribeActionWithMessageInBodyAndPredicateBean() {
        String subscribeChannel = "testChannel";
        DynamicRouterControlMessage subMsg = DynamicRouterControlMessage.Builder.newBuilder()
                .subscribeChannel(subscribeChannel)
                .subscriptionId("testId")
                .destinationUri("mock://test")
                .priority(10)
                .predicateBean("testPredicate")
                .build();
        Mockito.when(message.getBody()).thenReturn(subMsg);
        Mockito.when(message.getBody(DynamicRouterControlMessage.class)).thenReturn(subMsg);
        Mockito.doNothing().when(callback).done(false);
        producer.performSubscribe(message, callback);
        Mockito.verify(controlService, Mockito.times(1))
                .subscribeWithPredicateBean(
                        subscribeChannel, "testId", "mock://test", 10, "testPredicate", false);
    }

    @Test
    void performUpdateActionWithMessageInBody() {
        String subscribeChannel = "testChannel";
        DynamicRouterControlMessage subMsg = DynamicRouterControlMessage.Builder.newBuilder()
                .subscribeChannel(subscribeChannel)
                .subscriptionId("testId")
                .destinationUri("mock://test")
                .priority(10)
                .predicate("true")
                .expressionLanguage("simple")
                .build();
        Mockito.when(message.getBody()).thenReturn(subMsg);
        Mockito.when(message.getBody(DynamicRouterControlMessage.class)).thenReturn(subMsg);
        Mockito.doNothing().when(callback).done(false);
        producer.performUpdate(message, callback);
        Mockito.verify(controlService, Mockito.times(1))
                .subscribeWithPredicateExpression(
                        subscribeChannel, "testId", "mock://test", 10, "true", "simple", true);
    }

    @Test
    void testPerformListAction() {
        String subscribeChannel = "testChannel";
        String filterString = "PrioritizedFilterProcessor [id: test, priority: 1, predicate: test, endpoint: test]";
        Map<String, Object> headers = Map.of(
                CONTROL_ACTION_HEADER, CONTROL_ACTION_LIST,
                CONTROL_SUBSCRIBE_CHANNEL, subscribeChannel);
        Mockito.when(exchange.getMessage()).thenReturn(message);
        Mockito.when(message.getHeaders()).thenReturn(headers);
        Mockito.when(controlService.getSubscriptionsForChannel(subscribeChannel)).thenReturn("[" + filterString + "]");
        Mockito.doNothing().when(callback).done(false);
        producer.performList(exchange, callback);
        Mockito.verify(message, Mockito.times(1)).setBody("[" + filterString + "]", String.class);
    }

    @Test
    void testPerformStatsAction() {
        String subscribeChannel = "testChannel";
        String statString = "PrioritizedFilterStatistics [id: testId, count: 1, first:12345, last: 23456]";
        Map<String, Object> headers = Map.of(
                CONTROL_ACTION_HEADER, CONTROL_ACTION_STATS,
                CONTROL_SUBSCRIBE_CHANNEL, subscribeChannel);
        Mockito.when(exchange.getMessage()).thenReturn(message);
        Mockito.when(message.getHeaders()).thenReturn(headers);
        Mockito.when(controlService.getStatisticsForChannel(subscribeChannel)).thenReturn("[" + statString + "]");
        Mockito.doNothing().when(callback).done(false);
        producer.performStats(exchange, callback);
        Mockito.verify(message, Mockito.times(1)).setBody("[" + statString + "]", String.class);
    }

    @Test
    void testGetInstance() {
        DynamicRouterControlProducer instance = new DynamicRouterControlProducerFactory()
                .getInstance(endpoint, controlService, configuration);
        Assertions.assertNotNull(instance);
    }
}
