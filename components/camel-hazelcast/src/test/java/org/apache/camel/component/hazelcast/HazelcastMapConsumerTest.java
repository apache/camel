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

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.hazelcast.core.EntryEvent;
import com.hazelcast.core.EntryEventType;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.hazelcast.listener.MapEntryListener;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class HazelcastMapConsumerTest extends HazelcastCamelTestSupport {

    @Mock
    private IMap<Object, Object> map;

    @Captor
    private ArgumentCaptor<MapEntryListener<Object, Object>> argument;

    @Override
    protected void trainHazelcastInstance(HazelcastInstance hazelcastInstance) {
        when(hazelcastInstance.getMap("foo")).thenReturn(map);
        when(map.addEntryListener(any(), eq(true))).thenReturn(UUID.randomUUID());
    }

    @Override
    protected void verifyHazelcastInstance(HazelcastInstance hazelcastInstance) {
        verify(hazelcastInstance).getMap("foo");
        verify(map).addEntryListener(any(MapEntryListener.class), eq(true));
    }

    @Test
    public void testAdd() throws InterruptedException {
        MockEndpoint out = getMockEndpoint("mock:added");
        out.expectedMessageCount(1);

        verify(map).addEntryListener(argument.capture(), eq(true));
        EntryEvent<Object, Object> event = new EntryEvent<>("foo", null, EntryEventType.ADDED.getType(), "4711", "my-foo");
        argument.getValue().entryAdded(event);
        assertMockEndpointsSatisfied(5000, TimeUnit.MILLISECONDS);

        this.checkHeaders(out.getExchanges().get(0).getIn().getHeaders(), HazelcastConstants.ADDED);
    }

    @Test
    public void testEnict() throws InterruptedException {
        MockEndpoint out = super.getMockEndpoint("mock:evicted");
        out.expectedMessageCount(1);

        verify(map).addEntryListener(argument.capture(), eq(true));
        EntryEvent<Object, Object> event = new EntryEvent<>("foo", null, EntryEventType.EVICTED.getType(), "4711", "my-foo");
        argument.getValue().entryEvicted(event);

        assertMockEndpointsSatisfied(30000, TimeUnit.MILLISECONDS);
    }

    @Test
    public void testUpdate() throws InterruptedException {
        MockEndpoint out = getMockEndpoint("mock:updated");
        out.expectedMessageCount(1);

        verify(map).addEntryListener(argument.capture(), eq(true));
        EntryEvent<Object, Object> event = new EntryEvent<>("foo", null, EntryEventType.UPDATED.getType(), "4711", "my-foo");
        argument.getValue().entryUpdated(event);

        assertMockEndpointsSatisfied(5000, TimeUnit.MILLISECONDS);

        this.checkHeaders(out.getExchanges().get(0).getIn().getHeaders(), HazelcastConstants.UPDATED);
    }
    
    @Test
    public void testEvict() throws InterruptedException {
        MockEndpoint out = getMockEndpoint("mock:evicted");
        out.expectedMessageCount(1);

        verify(map).addEntryListener(argument.capture(), eq(true));
        EntryEvent<Object, Object> event = new EntryEvent<>("foo", null, EntryEventType.EVICTED.getType(), "4711", "my-foo");
        argument.getValue().entryEvicted(event);

        assertMockEndpointsSatisfied(5000, TimeUnit.MILLISECONDS);

        this.checkHeaders(out.getExchanges().get(0).getIn().getHeaders(), HazelcastConstants.EVICTED);
    }

    @Test
    public void testRemove() throws InterruptedException {
        MockEndpoint out = getMockEndpoint("mock:removed");
        out.expectedMessageCount(1);

        verify(map).addEntryListener(argument.capture(), eq(true));
        EntryEvent<Object, Object> event = new EntryEvent<>("foo", null, EntryEventType.REMOVED.getType(), "4711", "my-foo");
        argument.getValue().entryRemoved(event);

        assertMockEndpointsSatisfied(5000, TimeUnit.MILLISECONDS);
        this.checkHeaders(out.getExchanges().get(0).getIn().getHeaders(), HazelcastConstants.REMOVED);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(String.format("hazelcast-%sfoo", HazelcastConstants.MAP_PREFIX)).log("object...").choice().when(header(HazelcastConstants.LISTENER_ACTION).isEqualTo(HazelcastConstants.ADDED))
                        .log("...added").to("mock:added").when(header(HazelcastConstants.LISTENER_ACTION).isEqualTo(HazelcastConstants.EVICTED)).log("...evicted").to("mock:evicted")
                        .when(header(HazelcastConstants.LISTENER_ACTION).isEqualTo(HazelcastConstants.UPDATED)).log("...updated").to("mock:updated")
                        .when(header(HazelcastConstants.LISTENER_ACTION).isEqualTo(HazelcastConstants.REMOVED)).log("...removed").to("mock:removed").otherwise().log("fail!");
            }
        };
    }

    private void checkHeaders(Map<String, Object> headers, String action) {
        assertEquals(action, headers.get(HazelcastConstants.LISTENER_ACTION));
        assertEquals(HazelcastConstants.CACHE_LISTENER, headers.get(HazelcastConstants.LISTENER_TYPE));
        assertEquals("4711", headers.get(HazelcastConstants.OBJECT_ID));
        assertNotNull(headers.get(HazelcastConstants.LISTENER_TIME));
    }
}
