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
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class JGroupsRaftComponentDefaultTest extends CamelTestSupport {

    static final String CLUSTER_NAME = "JGroupsRaftComponentDefaultTest";
    static final String CONFIGURED_ENDPOINT_URI = String.format("my-default-jgroupsraft:%s?raftId=A", CLUSTER_NAME);

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                JGroupsRaftComponent defaultComponent = new JGroupsRaftComponent();
                context().addComponent("my-default-jgroupsraft", defaultComponent);

                from("my-default-jgroupsraft:" + CLUSTER_NAME + "?raftId=A").to("mock:default");
            }
        };
    }

    @Test
    public void shouldCreateHandleWithDefaults() {
        JGroupsRaftEndpoint endpoint = getMandatoryEndpoint(CONFIGURED_ENDPOINT_URI, JGroupsRaftEndpoint.class);
        JGroupsRaftComponent component = (JGroupsRaftComponent) endpoint.getComponent();

        assertNotNull(component);
        assertNotNull(endpoint.getResolvedRaftHandle());
        assertNotNull(endpoint.getResolvedRaftHandle().channel().getProtocolStack().findProtocol("RAFT"));
        assertTrue(component.getStateMachine() instanceof NopStateMachine);
        assertTrue(endpoint.getStateMachine() instanceof NopStateMachine);
        assertNull(component.getChannelProperties());
        assertNull(endpoint.getChannelProperties());
        assertEquals("A", endpoint.getRaftId());
        assertEquals("A", endpoint.getRaftId());
    }

}
