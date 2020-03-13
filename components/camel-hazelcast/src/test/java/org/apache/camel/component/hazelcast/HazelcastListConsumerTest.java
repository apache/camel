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

import com.hazelcast.collection.IList;
import com.hazelcast.collection.ItemEvent;
import com.hazelcast.collection.ItemListener;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.ItemEventType;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;

import static org.mockito.Mockito.*;

public class HazelcastListConsumerTest extends HazelcastCamelTestSupport {

    @Mock
    private IList<String> list;

    @Captor
    private ArgumentCaptor<ItemListener<String>> argument;

    @Override
    protected void trainHazelcastInstance(HazelcastInstance hazelcastInstance) {
        when(hazelcastInstance.<String>getList("foo")).thenReturn(list);
        when(list.addItemListener(any(), eq(true))).thenReturn(UUID.randomUUID());
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void verifyHazelcastInstance(HazelcastInstance hazelcastInstance) {
        verify(hazelcastInstance).getList("foo");
        verify(list).addItemListener(any(ItemListener.class), eq(true));
    }

    @Test
    public void add() throws InterruptedException {
        MockEndpoint out = getMockEndpoint("mock:added");
        out.expectedMessageCount(1);

        verify(list).addItemListener(argument.capture(), eq(true));
        final ItemEvent<String> event = new ItemEvent<>("mm", ItemEventType.ADDED, "foo", null);
        argument.getValue().itemAdded(event);

        assertMockEndpointsSatisfied(2000, TimeUnit.MILLISECONDS);

        this.checkHeaders(out.getExchanges().get(0).getIn().getHeaders(), HazelcastConstants.ADDED);
    }

    @Test
    public void remove() throws InterruptedException {
        MockEndpoint out = getMockEndpoint("mock:removed");
        out.expectedMessageCount(1);

        verify(list).addItemListener(argument.capture(), eq(true));
        final ItemEvent<String> event = new ItemEvent<>("mm", ItemEventType.REMOVED, "foo", null);
        argument.getValue().itemRemoved(event);

        assertMockEndpointsSatisfied(2000, TimeUnit.MILLISECONDS);
        this.checkHeaders(out.getExchanges().get(0).getIn().getHeaders(), HazelcastConstants.REMOVED);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(String.format("hazelcast-%sfoo", HazelcastConstants.LIST_PREFIX)).log("object...").choice().when(header(HazelcastConstants.LISTENER_ACTION).isEqualTo(HazelcastConstants.ADDED))
                        .log("...added").to("mock:added").when(header(HazelcastConstants.LISTENER_ACTION).isEqualTo(HazelcastConstants.REMOVED)).log("...removed").to("mock:removed").otherwise()
                        .log("fail!");
            }
        };
    }

    private void checkHeaders(Map<String, Object> headers, String action) {
        assertEquals(action, headers.get(HazelcastConstants.LISTENER_ACTION));
        assertEquals(HazelcastConstants.CACHE_LISTENER, headers.get(HazelcastConstants.LISTENER_TYPE));
        assertEquals(null, headers.get(HazelcastConstants.OBJECT_ID));
        assertNotNull(headers.get(HazelcastConstants.LISTENER_TIME));
    }
}
