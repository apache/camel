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
package org.apache.camel.component.hazelcast;

import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stopping a route that consumes a Hazelcast queue in poll mode. A poll removes the item from the queue, so the
 * consumer must neither interrupt the exchange it is routing nor leave a poll waiting on the member that takes an item
 * nobody routes.
 */
class HazelcastQueueConsumerPollStopTest extends CamelTestSupport {

    private final CountDownLatch routing = new CountDownLatch(1);
    private HazelcastInstance member;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        member = Hazelcast.newHazelcastInstance(memberConfig("queue-poll-stop-" + UUID.randomUUID()));
        CamelContext context = super.createCamelContext();
        context.getRegistry().bind("hazelcastInstance", member);
        return context;
    }

    private static Config memberConfig(String clusterName) {
        Config config = new Config();
        config.setClusterName(clusterName);
        config.getMetricsConfig().setEnabled(false);
        config.getNetworkConfig().getJoin().getMulticastConfig().setEnabled(false);
        config.getNetworkConfig().getJoin().getAutoDetectionConfig().setEnabled(false);
        config.getNetworkConfig().getJoin().getTcpIpConfig().setEnabled(false);
        return config;
    }

    @AfterEach
    void shutdownMember() {
        // stop the routes before the member, so the consumers do not poll a member that is shut down
        context.stop();
        if (member != null) {
            member.shutdown();
        }
    }

    @Test
    void exchangeInFlightCompletesWhenTheRouteStops() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:slow");
        mock.expectedBodiesReceived("a");

        member.getQueue("slow").offer("a");
        assertTrue(routing.await(10, TimeUnit.SECONDS), "The item was not polled");

        // graceful stop while the exchange is being routed: the item is no longer in the queue, so the exchange
        // must be allowed to complete
        context.getRouteController().stopRoute("slow");

        mock.assertIsSatisfied();
        assertEquals(0, member.getQueue("slow").size());
    }

    @Test
    void itemOfferedAfterTheRouteStoppedStaysInTheQueue() throws Exception {
        // stop the route while the consumer waits in its poll (the usual state of an idle consumer)
        await().atMost(10, TimeUnit.SECONDS).until(HazelcastQueueConsumerPollStopTest::consumersWaitInPoll);
        context.getRouteController().stopRoute("idle");

        member.getQueue("idle").offer("b");

        // nothing consumes the queue any more: the item must stay there (for the next start or another consumer)
        await().during(4, TimeUnit.SECONDS).atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(1, member.getQueue("idle").size(),
                        "The item offered after the route stopped was taken from the queue"));
        assertEquals(0, getMockEndpoint("mock:idle").getReceivedCounter());

        // and the next start routes it
        MockEndpoint mock = getMockEndpoint("mock:idle");
        mock.expectedBodiesReceived("b");
        context.getRouteController().startRoute("idle");
        mock.assertIsSatisfied();
    }

    @Test
    void longPollingTimeoutWaitsForAnItemAndStillStopsPromptly() throws Exception {
        await().atMost(10, TimeUnit.SECONDS).until(HazelcastQueueConsumerPollStopTest::consumersWaitInPoll);

        // an item offered after several seconds of waiting, within the pollingTimeout of a minute, is routed
        MockEndpoint mock = getMockEndpoint("mock:long");
        await().during(3, TimeUnit.SECONDS).atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(0, mock.getReceivedCounter()));
        mock.expectedBodiesReceived("c");
        member.getQueue("long").offer("c");
        mock.assertIsSatisfied();

        // the consumer waits for its pollingTimeout of a minute, but a stop does not wait for it (nor for the graceful
        // shutdown timeout), and leaves the item offered afterwards in the queue
        await().atMost(10, TimeUnit.SECONDS).until(HazelcastQueueConsumerPollStopTest::consumersWaitInPoll);
        long start = System.nanoTime();
        context.getRouteController().stopRoute("long");
        long stopMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        assertTrue(stopMillis < 5000, "Stopping the route took " + stopMillis + " ms");

        member.getQueue("long").offer("d");
        await().during(3, TimeUnit.SECONDS).atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(1, member.getQueue("long").size(),
                        "The item offered after the route stopped was taken from the queue"));
        assertEquals(1, mock.getReceivedCounter());
    }

    private static boolean consumersWaitInPoll() {
        // the consumer threads of the routes that are started, each waiting in IQueue.poll
        return Thread.getAllStackTraces().entrySet().stream()
                .filter(e -> e.getKey().getName().contains("QueueConsumer"))
                .filter(e -> e.getKey().getState() != Thread.State.RUNNABLE
                        && Arrays.stream(e.getValue()).anyMatch(f -> "poll".equals(f.getMethodName())))
                .count()
               == 3;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("hazelcast-queue:slow?queueConsumerMode=Poll&pollingTimeout=3000&hazelcastInstance=#hazelcastInstance")
                        .routeId("slow")
                        .process(exchange -> {
                            routing.countDown();
                            // any blocking call that ends when its thread is interrupted
                            new CountDownLatch(1).await(1, TimeUnit.SECONDS);
                        })
                        .to("mock:slow");

                from("hazelcast-queue:idle?queueConsumerMode=Poll&pollingTimeout=3000&hazelcastInstance=#hazelcastInstance")
                        .routeId("idle")
                        .to("mock:idle");

                from("hazelcast-queue:long?queueConsumerMode=Poll&pollingTimeout=60000&hazelcastInstance=#hazelcastInstance")
                        .routeId("long")
                        .to("mock:long");
            }
        };
    }
}
