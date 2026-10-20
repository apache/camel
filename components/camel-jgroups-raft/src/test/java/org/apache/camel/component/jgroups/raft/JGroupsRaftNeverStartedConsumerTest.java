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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jgroups.raft.utils.NopStateMachine;
import org.jgroups.JChannel;
import org.jgroups.raft.RaftHandle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Red-team probe: a consumer of the shared component handle that was never started (autoStartup=false) is stopped
 * when its route is stopped or removed. Its doStop calls endpoint.disconnect() unconditionally, which decrements the
 * count of the running producer and disconnects the shared handle under it.
 */
/**
 * A route whose consumer never started (autoStartup false) is stopped or removed: it must not disconnect the raftHandle
 * of the component, which the producer of another endpoint uses.
 */
class JGroupsRaftNeverStartedConsumerTest extends JGroupsRaftAbstractTest {

    private static final String CLUSTER_NAME = "JGroupsRaftNeverStartedConsumerTest";

    private RaftHandle componentHandle;

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                componentHandle = new RaftHandle(new JChannel("raft.xml").name("A"), new NopStateMachine()).raftId("A");
                JGroupsRaftComponent component = new JGroupsRaftComponent();
                component.setRaftHandle(componentHandle);
                context.addComponent("jgroups-raft", component);

                from("direct:send").routeId("producer").to("jgroups-raft:" + CLUSTER_NAME);
                from("jgroups-raft:" + CLUSTER_NAME + "?enableRoleChangeEvents=true").routeId("lazy").autoStartup(false)
                        .to("mock:events");
            }
        };
    }

    @Test
    void stoppingANeverStartedConsumerRouteKeepsTheHandleConnected() throws Exception {
        assertTrue(componentHandle.channel().isConnected(), "the producer connected the handle");
        context.getRouteController().stopRoute("lazy");
        assertTrue(componentHandle.channel().isConnected(), "after stopRoute of the never started route");
        context.removeRoute("lazy");
        assertTrue(componentHandle.channel().isConnected(), "after removeRoute of the never started route");
    }
}
