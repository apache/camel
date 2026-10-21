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

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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

/**
 * A transacted Hazelcast SEDA consumer whose route continues on another thread (here {@code threads()}, as with any
 * asynchronous producer). A Hazelcast transaction can only be committed or rolled back by the thread that began it, so
 * the consumer must end the transaction on its own thread once the exchange is complete.
 */
class HazelcastSedaTransactedAsyncRouteTest extends CamelTestSupport {

    private final AtomicInteger attempts = new AtomicInteger();
    private HazelcastInstance member;
    private HazelcastInstance otherMember;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        String clusterName = "seda-transacted-async-" + UUID.randomUUID();
        otherMember = Hazelcast.newHazelcastInstance(memberConfig(clusterName));
        member = Hazelcast.newHazelcastInstance(memberConfig(clusterName));
        await().atMost(30, TimeUnit.SECONDS).until(() -> member.getCluster().getMembers().size() == 2);

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
        config.getNetworkConfig().getJoin().getTcpIpConfig().setEnabled(true).addMember("127.0.0.1");
        return config;
    }

    @AfterEach
    void shutdownMembers() {
        // JUnit runs this before the extension of CamelTestSupport stops the context: stop it first, so that the
        // consumers do not keep polling members that are shut down
        context.stop();
        if (member != null) {
            member.shutdown();
        }
        if (otherMember != null) {
            otherMember.shutdown();
        }
    }

    @Test
    void testCompletedExchangesAreCommitted() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:done");
        mock.expectedBodiesReceivedInAnyOrder("a", "b", "c");

        member.getQueue("done").offer("a");
        member.getQueue("done").offer("b");
        member.getQueue("done").offer("c");

        mock.assertIsSatisfied();

        // the application leaves the cluster (a restart or a redeploy): the other member rolls back the transactions
        // the application left open, which puts their messages back into the queue
        context.stop();
        member.shutdown();
        member = null;

        await().during(3, TimeUnit.SECONDS).atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(0, otherMember.getQueue("done").size(),
                        "The processed messages were not removed from the queue"));
    }

    @Test
    void testFailedExchangeIsRolledBack() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:failed");
        mock.expectedBodiesReceived("x");

        member.getQueue("failed").offer("x");

        // the first attempt fails and must be rolled back, so the message is taken again
        mock.assertIsSatisfied();
        assertEquals(2, attempts.get());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("hazelcast-seda:done?transacted=true&hazelcastInstance=#hazelcastInstance").routeId("done")
                        .threads(1)
                        .to("mock:done");

                from("hazelcast-seda:failed?transacted=true&hazelcastInstance=#hazelcastInstance").routeId("failed")
                        .threads(1)
                        .process(exchange -> {
                            if (attempts.incrementAndGet() == 1) {
                                throw new IllegalStateException("Forced failure of the first attempt");
                            }
                        })
                        .to("mock:failed");
            }
        };
    }
}
