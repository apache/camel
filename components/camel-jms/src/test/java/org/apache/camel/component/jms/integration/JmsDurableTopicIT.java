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
package org.apache.camel.component.jms.integration;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.AbstractPersistentJMSTest;
import org.apache.camel.component.jms.JmsTestHelper;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Timeout(30)
public class JmsDurableTopicIT extends AbstractPersistentJMSTest {

    // topic subscriptions take a little longer to register than queue consumers, so keep the longer 200ms threshold
    private static final long TOPIC_ROUTE_UPTIME_MILLIS = 200;

    private MockEndpoint mock;
    private MockEndpoint mock2;

    @BeforeEach
    void setUpMocks() {
        mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        mock2 = getMockEndpoint("mock:result2");
        mock2.expectedBodiesReceived("Hello World");

        // Wait for BOTH durable topic consumer routes to be ready before sending.
        // The original code only waited for route-2, so route-1's subscriber could
        // still be registering when the message was published, causing mock:result
        // to miss the message intermittently.
        JmsTestHelper.waitForJmsConsumerRoutes(context, TOPIC_ROUTE_UPTIME_MILLIS, "route-1", "route-2");
    }

    @Test
    public void testDurableTopic() throws Exception {
        final CompletableFuture<Object> future = template.asyncSendBody("activemq:topic:JmsDurableTopicTest", "Hello World");
        final Object request = assertDoesNotThrow(() -> future.get(5, TimeUnit.SECONDS));
        assertNotNull(request);

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("activemq:topic:JmsDurableTopicTest?clientId=123&durableSubscriptionName=bar")
                        .routeId("route-1")
                        .to("mock:result");

                from("activemq:topic:JmsDurableTopicTest?clientId=456&durableSubscriptionName=bar")
                        .routeId("route-2")
                        .to("mock:result2");
            }
        };
    }
}
