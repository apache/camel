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
import java.util.function.Consumer;

import com.hazelcast.collection.IQueue;
import com.hazelcast.collection.ItemEvent;
import com.hazelcast.collection.ItemListener;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.ItemEventType;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class HazelcastQueueConsumerTest extends HazelcastCamelTestSupport {

    @Mock
    private IQueue<String> queue;

    private volatile Consumer<ItemListener<String>> consumer;

    @Override
    @SuppressWarnings("unchecked")
    protected void trainHazelcastInstance(HazelcastInstance hazelcastInstance) {
        when(hazelcastInstance.<String> getQueue("foo")).thenReturn(queue);
        when(queue.addItemListener(any(ItemListener.class), eq(true))).thenAnswer(
                invocationOnMock -> {
                    // Wait until the consumer is set
                    while (consumer == null) {
                        Thread.onSpinWait();
                    }
                    consumer.accept(invocationOnMock.getArgument(0, ItemListener.class));
                    return UUID.randomUUID();
                });
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void verifyHazelcastInstance(HazelcastInstance hazelcastInstance) {
        verify(hazelcastInstance).getQueue("foo");
        verify(queue).addItemListener(any(ItemListener.class), eq(true));
    }

    @Test
    public void add() throws InterruptedException {
        this.consumer = listener -> listener.itemAdded(new ItemEvent<>("foo", ItemEventType.ADDED, "foo", null));
        MockEndpoint out = getMockEndpoint("mock:added");
        out.expectedMessageCount(1);

        MockEndpoint.assertIsSatisfied(context, 2, TimeUnit.SECONDS);
        this.checkHeaders(out.getExchanges().get(0).getIn().getHeaders(), HazelcastConstants.ADDED);
    }

    @Test
    public void remove() throws InterruptedException {
        this.consumer = listener -> listener.itemRemoved(new ItemEvent<>("foo", ItemEventType.REMOVED, "foo", null));

        MockEndpoint out = getMockEndpoint("mock:removed");
        out.expectedMessageCount(1);

        MockEndpoint.assertIsSatisfied(context, 2, TimeUnit.SECONDS);
        this.checkHeaders(out.getExchanges().get(0).getIn().getHeaders(), HazelcastConstants.REMOVED);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(String.format("hazelcast-%sfoo", HazelcastConstants.QUEUE_PREFIX)).log("object...").choice()
                        .when(header(HazelcastConstants.LISTENER_ACTION).isEqualTo(HazelcastConstants.ADDED))
                        .log("...added").to("mock:added")
                        .when(header(HazelcastConstants.LISTENER_ACTION).isEqualTo(HazelcastConstants.REMOVED))
                        .log("...removed").to("mock:removed").otherwise()
                        .log("fail!");
            }
        };
    }

    private void checkHeaders(Map<String, Object> headers, String action) {
        assertEquals(action, headers.get(HazelcastConstants.LISTENER_ACTION));
        assertEquals(HazelcastConstants.CACHE_LISTENER, headers.get(HazelcastConstants.LISTENER_TYPE));
        assertNull(headers.get(HazelcastConstants.OBJECT_ID));
        assertNotNull(headers.get(HazelcastConstants.LISTENER_TIME));
    }

}
