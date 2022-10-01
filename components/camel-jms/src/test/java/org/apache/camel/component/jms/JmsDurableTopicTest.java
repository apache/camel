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
package org.apache.camel.component.jms;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Timeout(30)
public class JmsDurableTopicTest extends AbstractPersistentJMSTest {
    private MockEndpoint mock;
    private MockEndpoint mock2;

    @BeforeEach
    void setUpMocks() {
        mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        mock2 = getMockEndpoint("mock:result2");
        mock2.expectedBodiesReceived("Hello World");

        Awaitility.await().until(() -> context.getRoute("route-2").getUptimeMillis() > 200);
    }

    @Test
    public void testDurableTopic() {
        final CompletableFuture<Object> future = template.asyncSendBody("activemq:topic:JmsDurableTopicTest", "Hello World");
        final Object request = assertDoesNotThrow(() -> future.get(5, TimeUnit.SECONDS));
        assertNotNull(request);

        Awaitility.await().atMost(6, TimeUnit.SECONDS).untilAsserted(() -> MockEndpoint.assertIsSatisfied(context));
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
