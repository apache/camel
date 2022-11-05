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

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.dynamicrouter.DynamicRouterControlMessage;
import org.apache.camel.component.dynamicrouter.DynamicRouterControlMessage.SubscribeMessageBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.junit5.CamelSpringTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.ContextConfiguration;

import static org.apache.camel.builder.Builder.body;
import static org.apache.camel.component.dynamicrouter.DynamicRouterConstants.CONTROL_CHANNEL_URI;

/**
 * This test utilizes Spring XML to show the usage of the Dynamic Router, and to test basic functionality.
 */
@CamelSpringTest
@ContextConfiguration
@DirtiesContext(classMode = ClassMode.AFTER_EACH_TEST_METHOD)
public class DynamicRouterMultipleRecipientModeIT {

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

    @Produce(CONTROL_CHANNEL_URI)
    ProducerTemplate subscribe;

    DynamicRouterControlMessage evenSubscribeMsg;

    DynamicRouterControlMessage oddSubscribeMsg;

    DynamicRouterControlMessage allSubscribeMsg;

    @BeforeEach
    void setup() {
        // Create a subscription that accepts an exchange when the message body contains an even number
        // The destination URI is for the endpoint "mockOne"
        evenSubscribeMsg = new SubscribeMessageBuilder()
                .id("evenNumberSubscription")
                .channel("test")
                .priority(2)
                .endpointUri(mockOne.getEndpointUri())
                .predicate(body().regex("^\\d*[02468]$"))
                .build();

        // Create a subscription that accepts an exchange when the message body contains an odd number
        // The destination URI is for the endpoint "mockTwo"
        oddSubscribeMsg = new SubscribeMessageBuilder()
                .id("oddNumberSubscription")
                .channel("test")
                .priority(2)
                .endpointUri(mockTwo.getEndpointUri())
                .predicate(body().regex("^\\d*[13579]$"))
                .build();

        // Create a subscription that accepts an exchange when the message body contains any number
        // The destination URI is for the endpoint "mockThree"
        allSubscribeMsg = new SubscribeMessageBuilder()
                .id("allNumberSubscription")
                .channel("test")
                .priority(1)
                .endpointUri(mockThree.getEndpointUri())
                .predicate(body().regex("^\\d+$"))
                .build();
    }

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

        // Subscribe for all numeric message content to verify that in "allMatch" mode,
        // every participant receives all messages that pertain to them
        subscribe(Arrays.asList(allSubscribeMsg, evenSubscribeMsg, oddSubscribeMsg));
        sendMessagesAndAssert();
    }

    private void subscribe(List<DynamicRouterControlMessage> messages) {
        messages.forEach(message -> subscribe.sendBody(message));
    }

    private void sendMessagesAndAssert() throws InterruptedException {
        IntStream.rangeClosed(0, 10).forEach(n -> start.sendBody(String.valueOf(n)));
        MockEndpoint.assertIsSatisfied(camelContext, 2, TimeUnit.SECONDS);
    }
}
