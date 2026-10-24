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
package org.apache.camel.component.pulsar;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.ConsumerBuilder;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Answers.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * With several consumers (Shared, Failover, Key_Shared), a consumer whose subscribe failed was only logged: the route
 * reported Started with fewer consumers than configured, or with none at all, and nothing ever subscribed again.
 */
class PulsarConsumerSubscribeFailureTest {

    private static final String ROUTE_ID = "pulsar-subscribe-failure";

    @ParameterizedTest
    @ValueSource(strings = { "SHARED", "FAILOVER", "KEY_SHARED" })
    void aFailedSubscribeFailsTheRouteStartAndARetryOpensAllConsumers(String subscriptionType) throws Exception {
        final List<Consumer<byte[]>> subscribed = new ArrayList<>();
        final AtomicInteger subscribeCalls = new AtomicInteger();

        final ConsumerBuilder<byte[]> builder = mock(ConsumerBuilder.class, RETURNS_SELF);
        when(builder.subscribe()).thenAnswer(invocation -> {
            // the second consumer of the first start cannot subscribe, e.g. the broker is unreachable for longer
            // than the operation timeout; every later subscribe succeeds
            if (subscribeCalls.incrementAndGet() == 2) {
                throw new PulsarClientException.TimeoutException("simulated subscribe timeout");
            }
            Consumer<byte[]> consumer = mock(Consumer.class);
            subscribed.add(consumer);
            return consumer;
        });
        final PulsarClient pulsarClient = mock(PulsarClient.class);
        when(pulsarClient.newConsumer()).thenReturn(builder);

        try (DefaultCamelContext context = new DefaultCamelContext()) {
            final PulsarComponent component = new PulsarComponent(context);
            component.setPulsarClient(pulsarClient);
            context.addComponent("pulsar", component);
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("pulsar:persistent://public/default/camel-subscribe-failure?subscriptionName=camel-subscription"
                         + "&subscriptionType=" + subscriptionType + "&numberOfConsumers=2")
                            .routeId(ROUTE_ID).autoStartup(false)
                            .to("mock:result");
                }
            });
            context.start();

            // the route must not report Started while it holds only one of its two consumers: the failure has to
            // reach the caller (or the supervising route controller), which can then start it again
            assertThrows(Exception.class, () -> context.getRouteController().startRoute(ROUTE_ID));
            assertEquals(ServiceStatus.Stopped, context.getRouteController().getRouteStatus(ROUTE_ID));
            assertEquals(1, subscribed.size());
            // the consumer that did subscribe must not be left behind, open and unowned
            verify(subscribed.get(0)).close();

            context.getRouteController().startRoute(ROUTE_ID);
            assertEquals(ServiceStatus.Started, context.getRouteController().getRouteStatus(ROUTE_ID));
            assertEquals(3, subscribed.size());
            verify(subscribed.get(1), never()).close();
            verify(subscribed.get(2), never()).close();
        }
    }
}
