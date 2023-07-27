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

import java.io.DataInput;
import java.io.DataOutput;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jgroups.raft.utils.NopStateMachine;
import org.apache.camel.spi.Registry;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.jgroups.JChannel;
import org.jgroups.raft.RaftHandle;
import org.jgroups.raft.StateMachine;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class JGroupsRaftEndpointTest extends CamelTestSupport {

    static final String CLUSTER_NAME = "JGroupsRaftEndpointTest";
    static final String CONFIGURED_ENDPOINT_URI = "jgroups-raft:" + CLUSTER_NAME + "?raftId=A";
    static final String CLUSTER_NAME1 = "JGroupsraftEndpointTest1";
    static final String CONFIGURED_ENDPOINT_URI1 = "jgroups-raft:" + CLUSTER_NAME1 + "?raftHandle=#rh";
    static final String CLUSTER_NAME2 = "JGroupsraftEndpointTest2";
    static final String CONFIGURED_ENDPOINT_URI2
            = "jgroups-raft:" + CLUSTER_NAME2 + "?stateMachine=#sm&raftId=C&channelProperties=raftC.xml";

    StateMachine sm = new StateMachine() {

        @Override
        public byte[] apply(byte[] data, int offset, int length, boolean serialize_response) throws Exception {
            return new byte[0];
        }

        @Override
        public void readContentFrom(DataInput dataInput) {
        }

        @Override
        public void writeContentTo(DataOutput dataOutput) {
        }
    };

    @Override
    protected void bindToRegistry(Registry registry) throws Exception {
        JChannel ch = new JChannel("raftB.xml").name("B");
        RaftHandle handle = new RaftHandle(ch, new NopStateMachine()).raftId("B");
        registry.bind("rh", handle);
        registry.bind("sm", sm);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(CONFIGURED_ENDPOINT_URI).to("mock:test");
                from(CONFIGURED_ENDPOINT_URI1).to("mock:test1");
                from(CONFIGURED_ENDPOINT_URI2).to("mock:test2");
            }
        };
    }

    @Test
    public void shouldSetClusterNameAndResolveRaftHandle() {
        JGroupsRaftEndpoint endpoint = getMandatoryEndpoint(CONFIGURED_ENDPOINT_URI, JGroupsRaftEndpoint.class);

        assertEquals(CLUSTER_NAME, endpoint.getClusterName());

        JGroupsRaftEndpoint endpoint1 = getMandatoryEndpoint(CONFIGURED_ENDPOINT_URI1, JGroupsRaftEndpoint.class);

        assertNotNull(endpoint1.getRaftHandle());
        assertEquals(endpoint1.getRaftHandle(), endpoint1.getResolvedRaftHandle());

        JGroupsRaftEndpoint endpoint2 = getMandatoryEndpoint(CONFIGURED_ENDPOINT_URI2, JGroupsRaftEndpoint.class);

        assertEquals(sm, endpoint2.getStateMachine());
    }
}
