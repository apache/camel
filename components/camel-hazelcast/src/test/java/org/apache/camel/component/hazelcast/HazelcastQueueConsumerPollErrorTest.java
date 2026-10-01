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
import com.hazelcast.core.HazelcastException;
import com.hazelcast.core.HazelcastInstance;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The queue consumer in poll mode must keep polling after a poll fails.
 */
public class HazelcastQueueConsumerPollErrorTest extends HazelcastCamelTestSupport {

    @Mock
    private IQueue<String> queue;

    private final LinkedBlockingQueue<String> items = new LinkedBlockingQueue<>();
    private final AtomicBoolean failed = new AtomicBoolean();

    @Override
    protected void trainHazelcastInstance(HazelcastInstance hazelcastInstance) {
        when(hazelcastInstance.<String> getQueue("foo")).thenReturn(queue);
        try {
            when(queue.poll(anyLong(), any(TimeUnit.class))).thenAnswer(invocation -> {
                // the first poll fails, as when the client is disconnected from the cluster
                if (failed.compareAndSet(false, true)) {
                    throw new HazelcastException("Simulated poll failure");
                }
                return items.poll(invocation.getArgument(0), invocation.getArgument(1));
            });
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void verifyHazelcastInstance(HazelcastInstance hazelcastInstance) {
        verify(hazelcastInstance).getQueue("foo");
    }

    @Test
    public void testKeepsPollingAfterError() throws Exception {
        MockEndpoint out = getMockEndpoint("mock:result");
        out.expectedBodiesReceived("bar");

        items.add("bar");

        MockEndpoint.assertIsSatisfied(context, 5, TimeUnit.SECONDS);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(String.format("hazelcast-%sfoo?queueConsumerMode=Poll&pollingTimeout=100",
                        HazelcastConstants.QUEUE_PREFIX))
                        .to("mock:result");
            }
        };
    }
}
