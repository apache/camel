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
package org.apache.camel.component.jgroups.raft;

import org.apache.camel.Exchange;
import org.apache.camel.Producer;
import org.apache.camel.builder.RouteBuilder;
import org.jgroups.JChannel;
import org.jgroups.raft.RaftHandle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The producer and the consumer of an endpoint share the raftHandle the endpoint created: removing the consumer route
 * must not close the channel and the log that the producer route still uses.
 */
class JGroupsRaftRemoveRouteTest extends JGroupsRaftAbstractTest {

    private static final String CLUSTER_NAME = "JGroupsRaftRemoveRouteTest";
    private static final String URI_A
            = "jgroups-raft:" + CLUSTER_NAME + "?raftId=A&channelProperties=raftABC.xml&enableRoleChangeEvents=true";
    private static final String URI_B = "jgroups-raft:" + CLUSTER_NAME + "?raftId=B&channelProperties=raftABC.xml";
    private static final String URI_C = "jgroups-raft:" + CLUSTER_NAME + "?raftId=C&channelProperties=raftABC.xml";

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:a").routeId("producerA").to(URI_A);
                from(URI_A).routeId("consumerA").to("mock:eventsA");
                from("direct:b").to(URI_B);
                from("direct:c").to(URI_C);
            }
        };
    }

    @Test
    void removeConsumerRouteKeepsTheHandleOfTheProducer() throws Exception {
        JGroupsRaftEndpoint a = getMandatoryEndpoint(URI_A, JGroupsRaftEndpoint.class);
        JGroupsRaftEndpoint b = getMandatoryEndpoint(URI_B, JGroupsRaftEndpoint.class);
        JGroupsRaftEndpoint c = getMandatoryEndpoint(URI_C, JGroupsRaftEndpoint.class);
        waitForLeader(30, a.getResolvedRaftHandle(), b.getResolvedRaftHandle(), c.getResolvedRaftHandle());

        Exchange first = template.request("direct:a", exchange -> exchange.getIn().setBody("first".getBytes()));
        assertNull(first.getException());

        context.getRouteController().stopRoute("consumerA");
        context.removeRoute("consumerA");

        assertTrue(a.getResolvedRaftHandle().channel().isConnected(), "the producer still uses the channel");
        waitForLeader(30, a.getResolvedRaftHandle(), b.getResolvedRaftHandle(), c.getResolvedRaftHandle());
        Exchange second = template.request("direct:a", exchange -> exchange.getIn().setBody("second".getBytes()));
        assertNull(second.getException());
        checkHeaders(second);
    }

    @Test
    void removeConsumerRouteThenAddItAgainKeepsTheHandle() throws Exception {
        JGroupsRaftEndpoint a = getMandatoryEndpoint(URI_A, JGroupsRaftEndpoint.class);
        JGroupsRaftEndpoint b = getMandatoryEndpoint(URI_B, JGroupsRaftEndpoint.class);
        JGroupsRaftEndpoint c = getMandatoryEndpoint(URI_C, JGroupsRaftEndpoint.class);
        RaftHandle handle = a.getResolvedRaftHandle();

        context.getRouteController().stopRoute("consumerA");
        context.removeRoute("consumerA");
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(URI_A).routeId("consumerA2").to("mock:eventsA2");
            }
        });

        // the restarted endpoint keeps the handle the producer is still connected to: a new handle would leave the old
        // one in the cluster after both routes stop
        assertSame(handle, a.getResolvedRaftHandle(), "the re-added consumer uses the handle of the producer");
        assertTrue(handle.channel().isConnected(), "the producer and the re-added consumer use the channel");
        // the kept handle still works with the re-added consumer
        waitForLeader(30, handle, b.getResolvedRaftHandle(), c.getResolvedRaftHandle());
        Exchange afterReAdd = template.request("direct:a", exchange -> exchange.getIn().setBody("after".getBytes()));
        assertNull(afterReAdd.getException());
        checkHeaders(afterReAdd);
    }

    @Test
    void stopCamelClosesTheHandleThatAProducerNotStoppedByCamelStillUses() throws Exception {
        JGroupsRaftEndpoint a = getMandatoryEndpoint(URI_A, JGroupsRaftEndpoint.class);
        JChannel channel = a.getResolvedRaftHandle().channel();
        // a producer that the application created and started itself: Camel does not stop it
        Producer producer = a.createProducer();
        producer.start();

        context.stop();

        assertTrue(channel.isClosed(), "the handle is closed when Camel stops, as the node must leave the cluster");
        // the late stop of that producer finds the handle closed
        assertDoesNotThrow(producer::stop);
    }

    @Test
    void removeBothRoutesClosesTheHandle() throws Exception {
        JGroupsRaftEndpoint a = getMandatoryEndpoint(URI_A, JGroupsRaftEndpoint.class);
        JChannel channel = a.getResolvedRaftHandle().channel();

        context.getRouteController().stopRoute("consumerA");
        context.removeRoute("consumerA");
        assertTrue(channel.isConnected(), "the producer still uses the channel");

        context.getRouteController().stopRoute("producerA");
        context.removeRoute("producerA");
        assertTrue(channel.isClosed(), "the last user of the handle closed it");
    }
}
