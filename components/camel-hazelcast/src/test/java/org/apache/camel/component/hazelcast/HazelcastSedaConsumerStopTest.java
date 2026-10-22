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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.hazelcast.collection.IQueue;
import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastException;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Stopping a route that consumes a Hazelcast SEDA queue. A poll removes the item from the queue, so the consumer must
 * not stop its processor while its threads can still take an item, and its threads must end also when they wait in the
 * delay after a failed poll.
 */
class HazelcastSedaConsumerStopTest extends CamelTestSupport {

    private HazelcastInstance member;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        member = Hazelcast.newHazelcastInstance(memberConfig("seda-stop-" + UUID.randomUUID()));
        CamelContext context = super.createCamelContext();
        context.getRegistry().bind("hazelcastInstance", member);
        // an instance whose queue fails every poll, so the consumer waits in its delay after a failed poll
        IQueue<Object> failingQueue = mock(IQueue.class);
        when(failingQueue.poll(anyLong(), any(TimeUnit.class))).thenThrow(new HazelcastException("poll failed"));
        HazelcastInstance failingInstance = mock(HazelcastInstance.class);
        when(failingInstance.getQueue("failing")).thenReturn(failingQueue);
        context.getRegistry().bind("failingInstance", failingInstance);
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
    void itemOfferedAfterTheRouteStoppedStaysInTheQueue() throws Exception {
        // stop the route while the consumer waits in its poll (the usual state of an idle consumer)
        await().atMost(10, TimeUnit.SECONDS).until(() -> Thread.getAllStackTraces().entrySet().stream()
                .anyMatch(e -> e.getKey().getName().contains("hazelcast-seda://idle")
                        && Arrays.stream(e.getValue()).anyMatch(f -> "poll".equals(f.getMethodName()))));
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
    void longPollTimeoutStillStopsPromptlyAndLeavesTheItemInTheQueue() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:long");
        mock.expectedBodiesReceived("a");
        member.getQueue("long").offer("a");
        mock.assertIsSatisfied();

        // with a pollTimeout of a minute the stop does not wait for the poll (nor for the graceful shutdown timeout, which
        // would interrupt it; an interrupt does not end a poll on the member, so the poll would take the next item)
        await().atMost(10, TimeUnit.SECONDS).until(() -> Thread.getAllStackTraces().entrySet().stream()
                .anyMatch(e -> e.getKey().getName().contains("hazelcast-seda://long")
                        && Arrays.stream(e.getValue()).anyMatch(f -> "poll".equals(f.getMethodName()))));
        long start = System.nanoTime();
        context.getRouteController().stopRoute("long");
        long stopMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        assertTrue(stopMillis < 5000, "Stopping the route took " + stopMillis + " ms");

        member.getQueue("long").offer("b");
        await().during(3, TimeUnit.SECONDS).atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(1, member.getQueue("long").size(),
                        "The item offered after the route stopped was taken from the queue"));
        assertEquals(1, mock.getReceivedCounter());
    }

    @Test
    void threadInTheDelayAfterAFailedPollEndsWhenTheRouteStops() throws Exception {
        // the consumer thread waits in the delay after a failed poll, which runs on the error-delay scheduler
        await().atMost(10, TimeUnit.SECONDS).until(() -> !failingConsumerThreads(true).isEmpty());

        long start = System.nanoTime();
        context.getRouteController().stopRoute("failing");
        long stopMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        // the scheduler is shut down after the consumer threads ended, so the delay ends and the thread sees the stop
        // (shut down first, the delay never ends: the stop waits for the graceful timeout of 10 s, or the thread is left
        // waiting for ever)
        assertTrue(stopMillis < 8000, "Stopping the route took " + stopMillis + " ms");
        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertTrue(failingConsumerThreads(false).isEmpty(),
                        "A consumer thread is left: " + failingConsumerThreads(false)));
    }

    /**
     * The threads of the consumer of the failing route, only those waiting in the error delay if asked.
     */
    private static List<Thread> failingConsumerThreads(boolean inErrorDelay) {
        return Thread.getAllStackTraces().entrySet().stream()
                .filter(e -> e.getKey().getName().contains("hazelcast-seda://failing"))
                .filter(e -> !inErrorDelay || Arrays.stream(e.getValue())
                        .anyMatch(f -> f.getClassName().endsWith("BackgroundTask")))
                .map(Map.Entry::getKey)
                .toList();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("hazelcast-seda:idle?hazelcastInstance=#hazelcastInstance").routeId("idle")
                        .to("mock:idle");

                from("hazelcast-seda:long?hazelcastInstance=#hazelcastInstance&pollTimeout=60000").routeId("long")
                        .to("mock:long");

                from("hazelcast-seda:failing?hazelcastInstance=#failingInstance&onErrorDelay=3000").routeId("failing")
                        .to("mock:failing");
            }
        };
    }
}
