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
package org.apache.camel.component.dynamicrouter.integration;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Predicate;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.PredicateBuilder;
import org.apache.camel.component.dynamicrouter.control.DynamicRouterControlMessage;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.dynamicrouter.DynamicRouterTestConstants.addRoutes;
import static org.apache.camel.test.infra.core.MockUtils.getMockEndpoint;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * This test verifies basic functionality with the Dynamic Router in synchronous mode. This configuration is entirely
 * manual. For Spring XML, refer to {@link DynamicRouterSingleRouteTwoParticipantsIT}.
 */
public class DynamicRouterBasicSynchronousIT {

    private final Predicate matchAllPredicate = PredicateBuilder.constant(true);

    /**
     * Tests participant subscription, and that messages are received at their registered destination endpoints.
     *
     * @throws Exception if interrupted while waiting for mocks to be satisfied
     */
    @Test
    public void testDynamicRouter() throws Exception {
        CamelContext context = new DefaultCamelContext();
        ProducerTemplate template = context.createProducerTemplate();
        addRoutes.accept(context);
        MockEndpoint mock = getMockEndpoint(context, "mock:result", true);
        mock.expectedMessageCount(1);
        Predicate predicate = spy(matchAllPredicate);
        context.getRegistry().bind("spyPredicate", predicate);

        template.sendBodyAndHeaders("direct:subscribe", "",
                Map.of("subscribeChannel", "test",
                        "subscriptionId", "testSubscription1",
                        "destinationUri", mock.getEndpointUri(),
                        "priority", "1",
                        "predicateBean", "spyPredicate"));

        // Trigger events to subscribers
        template.sendBody("direct:start", "testMessage");

        MockEndpoint.assertIsSatisfied(context, 5, TimeUnit.SECONDS);

        verify(predicate, times(1)).matches(any(Exchange.class));
    }

    /**
     * Tests participant subscription, and that messages are received at their registered destination endpoints.
     *
     * @throws Exception if interrupted while waiting for mocks to be satisfied
     */
    @Test
    public void testSubscribingWithMessage() throws Exception {
        CamelContext context = new DefaultCamelContext();
        ProducerTemplate template = context.createProducerTemplate();
        addRoutes.accept(context);
        MockEndpoint mock = getMockEndpoint(context, "mock:result", true);
        mock.expectedMessageCount(1);
        Predicate predicate = spy(matchAllPredicate);
        context.getRegistry().bind("spyPredicate", predicate);

        DynamicRouterControlMessage controlMessage = DynamicRouterControlMessage.Builder.newBuilder()
                .subscribeChannel("test")
                .subscriptionId("testId")
                .destinationUri(mock.getEndpointUri())
                .priority(5)
                .predicateBean("spyPredicate")
                .build();

        template.sendBody("dynamic-router-control:subscribe", controlMessage);

        // Trigger events to subscribers
        template.sendBody("direct:start", "testMessage");

        MockEndpoint.assertIsSatisfied(context, 5, TimeUnit.SECONDS);

        verify(predicate, times(1)).matches(any(Exchange.class));
    }

    /**
     * Tests participant subscription, and that messages are received at their registered destination endpoints.
     *
     * @throws Exception if interrupted while waiting for mocks to be satisfied
     */
    @Test
    public void testSubscribingWithMessageAndSpelPredicate() throws Exception {
        CamelContext context = new DefaultCamelContext();
        ProducerTemplate template = context.createProducerTemplate();
        addRoutes.accept(context);
        MockEndpoint mock = getMockEndpoint(context, "mock:result", true);
        mock.expectedMessageCount(1);

        DynamicRouterControlMessage controlMessage = DynamicRouterControlMessage.Builder.newBuilder()
                .subscribeChannel("test")
                .subscriptionId("testId")
                .destinationUri(mock.getEndpointUri())
                .priority(5)
                .predicate("#{headers.test == 'testValue'}")
                .expressionLanguage("spel")
                .build();

        template.sendBody("dynamic-router-control:subscribe", controlMessage);

        // Trigger events to subscribers
        template.sendBodyAndHeader("direct:start", "testMessage", "test", "testValue");

        MockEndpoint.assertIsSatisfied(context, 5, TimeUnit.SECONDS);
    }

    /**
     * Tests unsubscribe.
     *
     * @throws Exception if interrupted while waiting for mocks to be satisfied
     */
    @Test
    void testUnsubscribe() throws Exception {
        CamelContext context = new DefaultCamelContext();
        ProducerTemplate template = context.createProducerTemplate();
        addRoutes.accept(context);
        MockEndpoint mock = getMockEndpoint(context, "mock:result", true);
        mock.expectedMessageCount(1);
        Predicate predicate = spy(matchAllPredicate);
        context.getRegistry().bind("spyPredicate", predicate);

        template.sendBodyAndHeaders("direct:subscribe", "",
                Map.of("subscribeChannel", "test",
                        "subscriptionId", "testSubscription1",
                        "destinationUri", mock.getEndpointUri(),
                        "priority", "1",
                        "predicateBean", "spyPredicate"));

        // Trigger events to subscribers
        template.sendBody("direct:start", "testMessage");

        // Subscription should lead to the mock endpoint receiving one message
        MockEndpoint.assertIsSatisfied(context, 5, TimeUnit.SECONDS);

        verify(predicate, times(1)).matches(any(Exchange.class));

        // Reset the interactions for the predicate spy before unsubscribing
        reset(predicate);
        mock.reset();
        mock.expectedMessageCount(0);

        // Now unsubscribe
        template.sendBodyAndHeaders("direct:unsubscribe", "",
                Map.of("subscribeChannel", "test",
                        "subscriptionId", "testSubscription1"));

        // Sends another message that should not be received
        template.sendBody("direct:start", "testMessage");

        // The unsubscribe message should mean that the expected count
        // of received messages to remain unchanged
        MockEndpoint.assertIsSatisfied(context, 5, TimeUnit.SECONDS);

        // The test predicate should not have been called after subscription removal
        verify(predicate, never()).matches(any(Exchange.class));
    }

    @Test
    void testListControlAction() {
        CamelContext context = new DefaultCamelContext();
        ProducerTemplate template = context.createProducerTemplate();
        addRoutes.accept(context);

        DynamicRouterControlMessage controlMessage1 = DynamicRouterControlMessage.Builder.newBuilder()
                .subscribeChannel("test")
                .subscriptionId("testId1")
                .destinationUri("direct:test1")
                .priority(1)
                .predicate("#{headers.test == 'testValue1'}")
                .expressionLanguage("spel")
                .build();
        template.sendBody("dynamic-router-control:subscribe", controlMessage1);

        DynamicRouterControlMessage controlMessage2 = DynamicRouterControlMessage.Builder.newBuilder()
                .subscribeChannel("test")
                .subscriptionId("testId2")
                .destinationUri("direct:test2")
                .priority(2)
                .predicate("#{headers.test == 'testValue2'}")
                .expressionLanguage("spel")
                .build();
        template.sendBody("dynamic-router-control:subscribe", controlMessage2);

        DynamicRouterControlMessage controlMessage3 = DynamicRouterControlMessage.Builder.newBuilder()
                .subscribeChannel("test")
                .subscriptionId("testId3")
                .destinationUri("direct:test3")
                .priority(3)
                .predicate("#{headers.test == 'testValue3'}")
                .expressionLanguage("spel")
                .build();
        template.sendBody("dynamic-router-control:subscribe", controlMessage3);

        String filtersJson = template.requestBodyAndHeader("direct:list", "", "subscribeChannel", "test", String.class);
        Assertions.assertEquals(
                "[{\"id\":\"testId1\",\"priority\":1,\"predicate\":{\"type\":\"java.lang.Boolean\"},\"endpoint\":\"direct:test1\"},"
                                +
                                "{\"id\":\"testId2\",\"priority\":2,\"predicate\":{\"type\":\"java.lang.Boolean\"},\"endpoint\":\"direct:test2\"},"
                                +
                                "{\"id\":\"testId3\",\"priority\":3,\"predicate\":{\"type\":\"java.lang.Boolean\"},\"endpoint\":\"direct:test3\"}]",
                filtersJson);
    }
}
