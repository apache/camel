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
package org.apache.camel.component.jgroups;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultCamelContext;
import org.jgroups.JChannel;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The producers and consumers of an endpoint share the channel the endpoint created: it is disconnected when the last
 * of them that connected it stops.
 */
class JGroupsEndpointOwnChannelTest {

    private static final String CLUSTER = "endpointOwnChannel";

    @Test
    void aProducerWhoseConnectFailedDoesNotKeepTheChannelConnected() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            context.start();
            JChannel channel = new FailingFirstConnectChannel();
            JGroupsEndpoint endpoint = endpointWithChannel(context, channel);

            Producer producer = endpoint.createProducer();
            assertThrows(Exception.class, producer::start);
            // Camel stops a service whose start failed
            producer.stop();

            Consumer consumer = endpoint.createConsumer(exchange -> {
            });
            consumer.start();
            assertTrue(channel.isConnected());

            // the only producer or consumer that connected the channel stops
            consumer.stop();
            assertFalse(channel.isConnected(), "the failed connect of the producer is not counted");

            endpoint.stop();
        }
    }

    @Test
    void aConsumerThatStartsWhileTheProducerStopsKeepsTheChannelConnected() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            context.start();
            BlockingDisconnectChannel channel = new BlockingDisconnectChannel();
            JGroupsEndpoint endpoint = endpointWithChannel(context, channel);

            Producer producer = endpoint.createProducer();
            producer.start();
            Consumer consumer = endpoint.createConsumer(exchange -> {
            });

            // the producer stops: it is the last user of the channel, so it disconnects it
            Thread stopping = new Thread(producer::stop, "stopping-producer");
            stopping.start();
            assertTrue(channel.disconnecting.await(20, TimeUnit.SECONDS));

            // the consumer starts while the channel is being disconnected
            Thread starting = new Thread(consumer::start, "starting-consumer");
            starting.start();
            // it waits until the disconnect is done (or, without the lock, finds the channel still connected and returns)
            await().atMost(20, TimeUnit.SECONDS).until(
                    () -> starting.getState() == Thread.State.WAITING || starting.getState() == Thread.State.TERMINATED);
            channel.release.countDown();
            stopping.join(20000);
            starting.join(20000);
            assertFalse(stopping.isAlive());
            assertFalse(starting.isAlive());

            assertTrue(channel.isConnected(), "connected for the consumer");

            consumer.stop();
            assertFalse(channel.isConnected());
            endpoint.stop();
        }
    }

    private static JGroupsEndpoint endpointWithChannel(CamelContext context, JChannel channel) throws Exception {
        JGroupsComponent component = context.getComponent("jgroups", JGroupsComponent.class);
        JGroupsEndpoint endpoint = new JGroupsEndpoint("jgroups:" + CLUSTER, component, null, CLUSTER, null, false) {
            @Override
            JChannel resolveChannel() {
                // the channel that the endpoint creates (no channel option)
                return channel;
            }
        };
        endpoint.setAcceptAllObjects(true);
        endpoint.setCamelContext(context);
        endpoint.start();
        return endpoint;
    }

    /**
     * A channel whose first connect fails, as when its transport cannot bind.
     */
    private static final class FailingFirstConnectChannel extends JChannel {

        private final AtomicInteger connects = new AtomicInteger();

        FailingFirstConnectChannel() throws Exception {
        }

        @Override
        public JChannel connect(String clusterName) throws Exception {
            if (connects.incrementAndGet() == 1) {
                throw new IllegalStateException("first connect fails");
            }
            return super.connect(clusterName);
        }
    }

    /**
     * A channel whose disconnect waits until the test releases it.
     */
    private static final class BlockingDisconnectChannel extends JChannel {

        private final CountDownLatch disconnecting = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        BlockingDisconnectChannel() throws Exception {
        }

        @Override
        public JChannel disconnect() {
            disconnecting.countDown();
            try {
                release.await(20, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return super.disconnect();
        }
    }
}
