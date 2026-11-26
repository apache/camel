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

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.hazelcast.collection.IQueue;
import com.hazelcast.core.HazelcastInstance;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.DefaultConsumer;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The queue consumer in poll mode stops polling after a VirtualMachineError of its poll, until the route is restarted
 * (CAMEL-27515).
 */
public class HazelcastQueueConsumerPollFatalErrorTest extends HazelcastCamelTestSupport {

    @Mock
    private IQueue<String> queue;

    private final LinkedBlockingQueue<String> items = new LinkedBlockingQueue<>();
    private final AtomicBoolean failed = new AtomicBoolean();

    @Override
    protected void trainHazelcastInstance(HazelcastInstance hazelcastInstance) {
        when(hazelcastInstance.<String> getQueue("foo")).thenReturn(queue);
        try {
            when(queue.poll(anyLong(), any(TimeUnit.class))).thenAnswer(invocation -> {
                // the first poll fails with a JVM-level error
                if (failed.compareAndSet(false, true)) {
                    throw new OutOfMemoryError("Simulated poll failure");
                }
                return items.poll(invocation.getArgument(0), invocation.getArgument(1));
            });
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void verifyHazelcastInstance(HazelcastInstance hazelcastInstance) {
        verify(hazelcastInstance, atLeastOnce()).getQueue("foo");
    }

    @Test
    public void testStopsPollingAfterFatalErrorUntilRestarted() throws Exception {
        DefaultConsumer consumer = (DefaultConsumer) context.getRoute("foo").getConsumer();
        await().atMost(10, TimeUnit.SECONDS).until(() -> consumer.getFatalError() != null);

        // the consumer no longer polls: the item stays in the queue
        items.add("bar");
        await().during(1, TimeUnit.SECONDS).atMost(5, TimeUnit.SECONDS)
                .until(() -> items.size() == 1);
        assertEquals(0, getMockEndpoint("mock:result").getReceivedCounter());

        // a restart brings it back
        MockEndpoint out = getMockEndpoint("mock:result");
        out.expectedBodiesReceived("bar");
        context.getRouteController().stopRoute("foo");
        context.getRouteController().startRoute("foo");
        MockEndpoint.assertIsSatisfied(context, 5, TimeUnit.SECONDS);
        assertNull(((DefaultConsumer) context.getRoute("foo").getConsumer()).getFatalError());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(String.format("hazelcast-%sfoo?queueConsumerMode=Poll&pollingTimeout=100",
                        HazelcastConstants.QUEUE_PREFIX)).routeId("foo")
                        .to("mock:result");
            }
        };
    }
}
