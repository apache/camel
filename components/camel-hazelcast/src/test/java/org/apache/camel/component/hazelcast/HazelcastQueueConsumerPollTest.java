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
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import com.hazelcast.collection.IQueue;
import com.hazelcast.core.HazelcastInstance;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class HazelcastQueueConsumerPollTest extends HazelcastCamelTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(HazelcastQueueConsumerPollTest.class);

    @Mock
    private IQueue<String> queue;

    // Thread-safe backing queue: the mock's poll() delegates here,
    // so stubbing is set up before the consumer starts polling.
    private final LinkedBlockingQueue<String> mockItems = new LinkedBlockingQueue<>();

    @Override
    protected void trainHazelcastInstance(HazelcastInstance hazelcastInstance) {
        when(hazelcastInstance.<String> getQueue("foo")).thenReturn(queue);
        try {
            when(queue.poll(anyLong(), any(TimeUnit.class))).thenAnswer(invocation -> {
                long timeout = invocation.getArgument(0);
                TimeUnit unit = invocation.getArgument(1);
                return mockItems.poll(timeout, unit);
            });
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void verifyHazelcastInstance(HazelcastInstance hazelcastInstance) {
        verify(hazelcastInstance).getQueue("foo");
        try {
            verify(queue, atLeast(1)).poll(anyLong(), any(TimeUnit.class));
        } catch (InterruptedException e) {
            LOG.debug("Interrupted during test execution", e);
        }
    }

    @Test
    public void add() throws InterruptedException {
        mockItems.add("foo");

        MockEndpoint out = getMockEndpoint("mock:result");
        out.expectedMessageCount(1);

        MockEndpoint.assertIsSatisfied(context, 5000, TimeUnit.MILLISECONDS);

        this.checkHeadersAbsence(out.getExchanges().get(0).getIn().getHeaders(), HazelcastConstants.ADDED);
    }

    @Test
    public void pollTimeout() throws InterruptedException {
        // Don't add anything — the backing queue is empty so poll() will
        // block for the configured timeout and return null.
        // The consumer should NOT send a null message.
        MockEndpoint out = getMockEndpoint("mock:result");
        out.expectedMessageCount(0);

        out.setSleepForEmptyTest(2_000);
        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(String.format("hazelcast-%sfoo?queueConsumerMode=Poll", HazelcastConstants.QUEUE_PREFIX))
                        .to("mock:result");
            }
        };
    }

    private void checkHeadersAbsence(Map<String, Object> headers, String action) {
        assertNotEquals(action, headers.get(HazelcastConstants.LISTENER_ACTION));
        assertNotEquals(HazelcastConstants.CACHE_LISTENER, headers.get(HazelcastConstants.LISTENER_TYPE));
        assertNull(headers.get(HazelcastConstants.LISTENER_TIME));
    }

}
