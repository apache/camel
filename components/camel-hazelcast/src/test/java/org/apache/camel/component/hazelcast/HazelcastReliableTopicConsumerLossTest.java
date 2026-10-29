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

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.hazelcast.config.Config;
import com.hazelcast.config.RingbufferConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.topic.ITopic;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.ExceptionHandler;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A reliable topic keeps its messages in a ringbuffer of limited capacity. A consumer that falls behind by more than
 * that capacity loses the messages that were overwritten; it must go on with the messages published afterwards.
 */
class HazelcastReliableTopicConsumerLossTest extends CamelTestSupport {

    private static final int CAPACITY = 10;

    private final CountDownLatch slowMessageStarted = new CountDownLatch(1);
    private final CountDownLatch releaseSlowMessage = new CountDownLatch(1);
    private HazelcastInstance member;
    // what the consumer reported to its exception handler
    private final List<String> reported = new CopyOnWriteArrayList<>();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        Config config = new Config();
        config.setClusterName("reliable-topic-loss-" + UUID.randomUUID());
        config.getMetricsConfig().setEnabled(false);
        config.getNetworkConfig().getJoin().getMulticastConfig().setEnabled(false);
        config.getNetworkConfig().getJoin().getAutoDetectionConfig().setEnabled(false);
        config.getNetworkConfig().getJoin().getTcpIpConfig().setEnabled(false);
        // the ringbuffer of the reliable topic (only one in this member)
        config.addRingBufferConfig(new RingbufferConfig("default").setCapacity(CAPACITY));
        member = Hazelcast.newHazelcastInstance(config);

        CamelContext context = super.createCamelContext();
        context.getRegistry().bind("hazelcastInstance", member);
        context.getRegistry().bind("exceptionHandler", new ExceptionHandler() {
            @Override
            public void handleException(Throwable exception) {
                handleException(null, null, exception);
            }

            @Override
            public void handleException(String message, Throwable exception) {
                handleException(message, null, exception);
            }

            @Override
            public void handleException(String message, Exchange exchange, Throwable exception) {
                reported.add(message + ": " + exception.getMessage());
            }
        });
        return context;
    }

    @AfterEach
    void shutdownMember() {
        releaseSlowMessage.countDown();
        // stop the route before the member, so its listener does not read from a member that is shut down
        context.stop();
        if (member != null) {
            member.shutdown();
        }
    }

    @Test
    void consumerGoesOnAfterFallingBehindTheRingbuffer() throws Exception {
        ITopic<String> topic = member.getReliableTopic("news");
        MockEndpoint mock = getMockEndpoint("mock:news");

        topic.publish("slow");
        assertTrue(slowMessageStarted.await(10, TimeUnit.SECONDS), "The first message was not consumed");

        // while the route is busy with the first message, more messages than the ringbuffer holds are published
        for (int i = 0; i < 3 * CAPACITY; i++) {
            topic.publish("burst-" + i);
        }
        releaseSlowMessage.countDown();

        // the overwritten messages are lost, but the consumer must receive the messages published afterwards
        mock.expectedBodiesReceived("after");
        mock.setResultWaitTime(15000);
        topic.publish("after");
        mock.assertIsSatisfied();
    }

    @Test
    void listenerCancelledByHazelcastIsReported() throws Exception {
        // Hazelcast cancels the listener when the topic is destroyed: the consumer receives nothing more
        member.getReliableTopic("news").destroy();

        await().atMost(10, TimeUnit.SECONDS).until(() -> !reported.isEmpty());
        assertEquals(1, reported.size(), "Reported: " + reported);
        assertTrue(reported.get(0).contains("Hazelcast cancelled the listener of the reliable topic news"),
                "Reported: " + reported);
    }

    @Test
    void listenerRemovedWhenTheConsumerStopsIsNotReported() throws Exception {
        // removing the listener when the route stops cancels it too
        context.getRouteController().stopRoute("news");

        assertEquals(List.of(), reported);
    }

    @Test
    void listenerCancelledWhenTheMemberShutsDownIsNotReported() throws Exception {
        // an ordinary shutdown where the member stops before the route (Hazelcast's shutdown hook runs at the same time
        // as Camel's): the runner fails with HazelcastInstanceNotActiveException and Hazelcast cancels the listener
        member.shutdown();

        await().during(1, TimeUnit.SECONDS).atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(List.of(), reported));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("hazelcast-topic:news?reliable=true&hazelcastInstance=#hazelcastInstance"
                     + "&exceptionHandler=#exceptionHandler").routeId("news")
                        .choice()
                        // the body is the Hazelcast message
                        .when(simple("${body.messageObject} == 'slow'"))
                        .process(exchange -> {
                            slowMessageStarted.countDown();
                            releaseSlowMessage.await(10, TimeUnit.SECONDS);
                        })
                        .when(simple("${body.messageObject} == 'after'"))
                        .setBody(simple("${body.messageObject}"))
                        .to("mock:news")
                        .end();
            }
        };
    }
}
