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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The queue consumer in listen mode must remove its item listener when it stops, like the other Hazelcast consumers
 * (CAMEL-15899).
 */
public class HazelcastQueueConsumerRestartTest extends HazelcastCamelTestSupport {

    @Mock
    private IQueue<String> queue;

    // the item listeners registered on the queue, as Hazelcast keeps them
    private final Map<UUID, ItemListener<String>> listeners = new ConcurrentHashMap<>();

    @Override
    @SuppressWarnings("unchecked")
    protected void trainHazelcastInstance(HazelcastInstance hazelcastInstance) {
        when(hazelcastInstance.<String> getQueue("foo")).thenReturn(queue);
        when(queue.addItemListener(any(ItemListener.class), eq(true))).thenAnswer(invocation -> {
            UUID id = UUID.randomUUID();
            listeners.put(id, invocation.getArgument(0));
            return id;
        });
        when(queue.removeItemListener(any(UUID.class))).thenAnswer(invocation -> {
            return listeners.remove(invocation.<UUID> getArgument(0)) != null;
        });
    }

    @Override
    protected void verifyHazelcastInstance(HazelcastInstance hazelcastInstance) {
        verify(hazelcastInstance, atLeastOnce()).getQueue("foo");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testStopRemovesListener() throws Exception {
        verify(queue, timeout(5000)).addItemListener(any(ItemListener.class), eq(true));
        assertEquals(1, listeners.size());

        context.getRouteController().stopRoute("queue");

        assertEquals(0, listeners.size());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testItemReceivedOnceAfterRestart() throws Exception {
        context.getRouteController().stopRoute("queue");
        context.getRouteController().startRoute("queue");
        verify(queue, timeout(5000).times(2)).addItemListener(any(ItemListener.class), eq(true));

        MockEndpoint added = getMockEndpoint("mock:added");
        added.expectedMessageCount(1);
        added.expectedHeaderReceived(HazelcastConstants.LISTENER_ACTION, HazelcastConstants.ADDED);

        // Hazelcast notifies every item listener registered on the queue
        ItemEvent<String> event = new ItemEvent<>("foo", ItemEventType.ADDED, "bar", null);
        listeners.values().forEach(listener -> listener.itemAdded(event));

        MockEndpoint.assertIsSatisfied(context, 5, TimeUnit.SECONDS);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(String.format("hazelcast-%sfoo", HazelcastConstants.QUEUE_PREFIX)).routeId("queue")
                        .to("mock:added");
            }
        };
    }
}
