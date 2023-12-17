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
import java.util.stream.IntStream;

import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.Predicate;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.junit5.CamelSpringTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.ContextConfiguration;

import static org.apache.camel.builder.Builder.body;
import static org.apache.camel.component.dynamicrouter.control.DynamicRouterControlConstants.CONTROL_ACTION_SUBSCRIBE;
import static org.apache.camel.component.dynamicrouter.control.DynamicRouterControlConstants.CONTROL_CHANNEL_URI;

/**
 * This test utilizes Spring XML to show the usage of the Dynamic Router, and to test basic functionality.
 */
@CamelSpringTest
@ContextConfiguration
@DirtiesContext(classMode = ClassMode.AFTER_EACH_TEST_METHOD)
class DynamicRouterMultipleRecipientModeIT {

    @Autowired
    CamelContext camelContext;

    @EndpointInject("mock:one")
    MockEndpoint mockOne;

    @EndpointInject("mock:two")
    MockEndpoint mockTwo;

    @EndpointInject("mock:three")
    MockEndpoint mockThree;

    @Produce("direct:start")
    ProducerTemplate start;

    @Produce(CONTROL_CHANNEL_URI + ":" + CONTROL_ACTION_SUBSCRIBE)
    ProducerTemplate subscribe;

    /**
     * This test shows what happens when there are multiple participants that might have overlapping rules. When the
     * dynamic router is in "allMatch" mode, then every participant should receive all exchanges that match their filter
     * predicate.
     *
     * @throws InterruptedException if interrupted while waiting for mocks to be satisfied
     */
    @Test
    void testMultipleMatchingParticipants() throws InterruptedException {
        mockOne.expectedBodiesReceivedInAnyOrder(0, 2, 4, 6, 8, 10);
        mockTwo.expectedBodiesReceivedInAnyOrder(1, 3, 5, 7, 9);
        mockThree.expectedBodiesReceivedInAnyOrder(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10);

        // Create a subscription that accepts an exchange when the message body contains an even number
        // The destination URI is for the endpoint "mockOne"
        Predicate evenPredicate = body().regex("^\\d*[02468]$");
        subscribe.sendBodyAndHeaders("direct:subscribe-no-url-predicate", evenPredicate,
                Map.of("controlAction", "subscribe",
                        "subscribeChannel", "test",
                        "subscriptionId", "evenNumberSubscription",
                        "destinationUri", mockOne.getEndpointUri(),
                        "priority", 2));

        // Create a subscription that accepts an exchange when the message body contains an odd number
        // The destination URI is for the endpoint "mockTwo"
        Predicate oddPredicate = body().regex("^\\d*[13579]$");
        subscribe.sendBodyAndHeaders("direct:subscribe-no-url-predicate", oddPredicate,
                Map.of("controlAction", "subscribe",
                        "subscribeChannel", "test",
                        "subscriptionId", "oddNumberSubscription",
                        "destinationUri", mockTwo.getEndpointUri(),
                        "priority", 2));

        // Create a subscription that accepts an exchange when the message body contains any number
        // The destination URI is for the endpoint "mockThree"
        Predicate allPredicate = body().regex("^\\d+$");
        subscribe.sendBodyAndHeaders("direct:subscribe-no-url-predicate", allPredicate,
                Map.of("controlAction", "subscribe",
                        "subscribeChannel", "test",
                        "subscriptionId", "allNumberSubscription",
                        "destinationUri", mockThree.getEndpointUri(),
                        "priority", 1));

        sendMessagesAndAssert();
    }

    private void sendMessagesAndAssert() throws InterruptedException {
        IntStream.rangeClosed(0, 10).forEach(n -> start.sendBody(String.valueOf(n)));
        MockEndpoint.assertIsSatisfied(camelContext, 2, TimeUnit.SECONDS);
    }
}
