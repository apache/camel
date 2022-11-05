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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.ContextConfiguration;

import static org.apache.camel.builder.Builder.body;
import static org.apache.camel.component.dynamicrouter.DynamicRouterConstants.CONTROL_CHANNEL_URI;

/**
 * Tests two routes, where each route uses a separate Dynamic Router channel. Utilizes Spring XML.
 */
@CamelSpringTest
@ContextConfiguration
@DirtiesContext(classMode = ClassMode.AFTER_EACH_TEST_METHOD)
public class DynamicRouterTwoRoutesIT {

    @Autowired
    CamelContext camelContext;

    @EndpointInject("mock:one")
    MockEndpoint mockOne;

    @EndpointInject("mock:two")
    MockEndpoint mockTwo;

    @Produce("direct:start1")
    ProducerTemplate start1;

    @Produce("direct:start2")
    ProducerTemplate start2;

    @Produce(CONTROL_CHANNEL_URI)
    ProducerTemplate subscribe;

    /**
     * This test demonstrates how two different Dynamic Router channels are, indeed, separate. We send both routing
     * channels ("test1" and "test2") the same content. Because the "test1" channel has a predicate that accepts message
     * bodies with even numbers, and the "test2" channel has a predicate that accepts message bodies with odd numbers,
     * the expected message bodies are received correctly by the subscribing participants on both channels.
     *
     * @throws InterruptedException if interrupted while waiting for mocks to be satisfied
     */
    @Test
    void testConsumersWithNonConflictingRules() throws InterruptedException {
        mockOne.expectedBodiesReceived(0, 2, 4, 6, 8, 10);
        mockTwo.expectedBodiesReceived(1, 3, 5, 7, 9);

        // Subscribe for message content on the "test1" channel
        // The predicate specifies that the message body should contain an even number
        // The destination endpoint URI is the "mockOne" endpoint
        DynamicRouterControlMessage evenSubscribeMsg = new SubscribeMessageBuilder()
                .id("evenNumberSubscription")
                .channel("test1")
                .priority(1)
                .endpointUri(mockOne.getEndpointUri())
                .predicate(body().regex("^\\d*[02468]$"))
                .build();
        subscribe.sendBody(evenSubscribeMsg);

        // Subscribe for message content on the "test2" channel
        // The predicate specifies that the message body should contain an odd number
        // The destination endpoint URI is the "mockTwo" endpoint
        DynamicRouterControlMessage oddSubscribeMsg = new SubscribeMessageBuilder()
                .id("oddNumberSubscription")
                .channel("test2")
                .priority(1)
                .endpointUri(mockTwo.getEndpointUri())
                .predicate(body().regex("^\\d*[13579]$"))
                .build();
        subscribe.sendBody(oddSubscribeMsg);

        // Send both channels the same content: numbers from 0 to 10, inclusive
        IntStream.rangeClosed(0, 10).forEach(n -> {
            start1.sendBody(String.valueOf(n));
            start2.sendBody(String.valueOf(n));
        });

        // Verify that both mocks received the expected messages
        MockEndpoint.assertIsSatisfied(camelContext, 2, TimeUnit.SECONDS);
    }
}
