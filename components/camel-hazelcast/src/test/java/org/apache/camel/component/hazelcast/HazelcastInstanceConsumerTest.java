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

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.hazelcast.cluster.Cluster;
import com.hazelcast.cluster.Member;
import com.hazelcast.cluster.MembershipEvent;
import com.hazelcast.cluster.MembershipListener;
import com.hazelcast.collection.IList;
import com.hazelcast.core.HazelcastInstance;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class HazelcastInstanceConsumerTest extends HazelcastCamelTestSupport {

    @Mock
    private IList<String> list;

    @Mock
    private Cluster cluster;

    @Mock
    private Member member;

    private ArgumentCaptor<MembershipListener> argument;

    @Override
    protected void trainHazelcastInstance(HazelcastInstance hazelcastInstance) {
        when(hazelcastInstance.getCluster()).thenReturn(cluster);
        argument = ArgumentCaptor.forClass(MembershipListener.class);
        when(cluster.addMembershipListener(any())).thenReturn(UUID.randomUUID());
    }

    @Override
    protected void verifyHazelcastInstance(HazelcastInstance hazelcastInstance) {
        verify(hazelcastInstance).getCluster();
        verify(cluster).addMembershipListener(any(MembershipListener.class));
    }

    @Test
    public void testAddInstance() throws InterruptedException {

        MockEndpoint added = getMockEndpoint("mock:added");
        added.setExpectedMessageCount(1);
        when(member.getSocketAddress()).thenReturn(new InetSocketAddress("foo.bar", 12345));

        verify(cluster).addMembershipListener(argument.capture());
        MembershipEvent event = new MembershipEvent(cluster, member, MembershipEvent.MEMBER_ADDED, null);
        argument.getValue().memberAdded(event);
        assertMockEndpointsSatisfied(5000, TimeUnit.MILLISECONDS);

        // check headers
        Exchange ex = added.getExchanges().get(0);
        Map<String, Object> headers = ex.getIn().getHeaders();

        this.checkHeaders(headers, HazelcastConstants.ADDED);
    }

    @Test
    public void testRemoveInstance() throws InterruptedException {

        MockEndpoint removed = getMockEndpoint("mock:removed");
        removed.setExpectedMessageCount(1);

        when(member.getSocketAddress()).thenReturn(new InetSocketAddress("foo.bar", 12345));

        verify(cluster).addMembershipListener(argument.capture());
        MembershipEvent event = new MembershipEvent(cluster, member, MembershipEvent.MEMBER_REMOVED, null);
        argument.getValue().memberRemoved(event);

        assertMockEndpointsSatisfied(5000, TimeUnit.MILLISECONDS);

        // check headers
        Exchange ex = removed.getExchanges().get(0);
        Map<String, Object> headers = ex.getIn().getHeaders();

        this.checkHeaders(headers, HazelcastConstants.REMOVED);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(String.format("hazelcast-%sfoo", HazelcastConstants.INSTANCE_PREFIX)).log("instance...").choice()
                        .when(header(HazelcastConstants.LISTENER_ACTION).isEqualTo(HazelcastConstants.ADDED)).log("...added").to("mock:added").otherwise().log("...removed").to("mock:removed");
            }
        };
    }

    private void checkHeaders(Map<String, Object> headers, String action) {
        assertEquals(action, headers.get(HazelcastConstants.LISTENER_ACTION));
        assertEquals(HazelcastConstants.INSTANCE_LISTENER, headers.get(HazelcastConstants.LISTENER_TYPE));
        assertNotNull(headers.get(HazelcastConstants.LISTENER_TIME));
        assertNotNull(headers.get(HazelcastConstants.INSTANCE_HOST));
        assertNotNull(headers.get(HazelcastConstants.INSTANCE_PORT));
    }
}
