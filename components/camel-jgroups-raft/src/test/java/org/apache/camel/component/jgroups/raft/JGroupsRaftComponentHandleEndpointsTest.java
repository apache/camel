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

import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jgroups.raft.utils.NopStateMachine;
import org.apache.camel.spi.Registry;
import org.jgroups.JChannel;
import org.jgroups.raft.RaftHandle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The raftHandle of the component is used by every endpoint of the component: an endpoint whose producer or consumer
 * stops, or that is stopped itself, must not disconnect or close the handle while the other endpoints use it.
 */
class JGroupsRaftComponentHandleEndpointsTest extends JGroupsRaftAbstractTest {

    private static final String CLUSTER_NAME = "JGroupsRaftComponentHandleEndpointsTest";
    private static final String PRODUCER_URI = "jgroups-raft:" + CLUSTER_NAME;
    private static final String CONSUMER_URI = "jgroups-raft:" + CLUSTER_NAME + "?enableRoleChangeEvents=true";

    private RaftHandle componentHandle;
    private RaftHandle endpointHandle;

    @Override
    protected void bindToRegistry(Registry registry) throws Exception {
        // a handle given to one endpoint (raftHandle=#endpointHandle) is not created by the endpoint either
        endpointHandle = new RaftHandle(new JChannel("raftB.xml").name("B"), new NopStateMachine()).raftId("B");
        registry.bind("endpointHandle", endpointHandle);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // a single member cluster (raft.xml), so the handle becomes leader on its own and can commit
                componentHandle = new RaftHandle(new JChannel("raft.xml").name("A"), new NopStateMachine()).raftId("A");
                JGroupsRaftComponent component = new JGroupsRaftComponent();
                component.setRaftHandle(componentHandle);
                context.addComponent("jgroups-raft", component);

                // two endpoints of the same cluster: the consumer has an option the producer has not
                from("direct:send").routeId("producer").to(PRODUCER_URI);
                from(CONSUMER_URI).routeId("consumer").to("mock:events");

                from("jgroups-raft:" + CLUSTER_NAME + "B?raftHandle=#endpointHandle").to("mock:endpointHandle");
            }
        };
    }

    @BeforeEach
    void endpointsShareTheHandleOfTheComponent() {
        JGroupsRaftEndpoint producerEndpoint = getMandatoryEndpoint(PRODUCER_URI, JGroupsRaftEndpoint.class);
        JGroupsRaftEndpoint consumerEndpoint = getMandatoryEndpoint(CONSUMER_URI, JGroupsRaftEndpoint.class);
        assertNotSame(producerEndpoint, consumerEndpoint);
        assertSame(componentHandle, producerEndpoint.getResolvedRaftHandle());
        assertSame(componentHandle, consumerEndpoint.getResolvedRaftHandle());
        waitForLeader(30, componentHandle);
    }

    @Test
    void stoppingTheProducerKeepsTheHandleConnected() throws Exception {
        context.getRouteController().stopRoute("producer");

        assertTrue(componentHandle.channel().isConnected(), "the consumer route still uses the handle");
        assertHandleCommits();
    }

    @Test
    void removingTheProducerRouteKeepsTheHandleOpen() throws Exception {
        context.getRouteController().stopRoute("producer");
        context.removeRoute("producer");

        assertFalse(componentHandle.channel().isClosed(), "the consumer route still uses the handle");
        assertTrue(componentHandle.channel().isConnected(), "the consumer route still uses the handle");
        assertHandleCommits();
    }

    @Test
    void stoppingTheCamelContextClosesTheHandles() {
        assertTrue(endpointHandle.channel().isConnected());

        context.stop();

        assertTrue(componentHandle.channel().isClosed());
        assertTrue(endpointHandle.channel().isClosed());
    }

    private void assertHandleCommits() throws Exception {
        waitForLeader(30, componentHandle);
        long commitIndex = componentHandle.commitIndex();
        componentHandle.set(new byte[] { 1 }, 0, 1, 10, TimeUnit.SECONDS);
        assertTrue(componentHandle.commitIndex() > commitIndex);
    }
}
