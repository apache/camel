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
package org.apache.camel.component.disruptor;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 * Tests that a Disruptor producer blocks when a message is sent while the ring buffer is full.
 */
public class DisruptorBlockWhenFullTest extends CamelTestSupport {
    private static final int QUEUE_SIZE = 8;

    private static final int DELAY = 100;

    private static final String MOCK_URI = "mock:blockWhenFullOutput";

    private static final String DEFAULT_URI = "disruptor:foo?size=" + QUEUE_SIZE;
    private static final String EXCEPTION_WHEN_FULL_URI = "disruptor:foo?blockWhenFull=false&size="
            + QUEUE_SIZE;

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(DEFAULT_URI).delay(DELAY).to(MOCK_URI);
            }
        };
    }

    @Test
    public void testDisruptorBlockingWhenFull() throws Exception {
        getMockEndpoint(MOCK_URI).setExpectedMessageCount(QUEUE_SIZE + 20);

        final DisruptorEndpoint disruptor = context.getEndpoint(DEFAULT_URI, DisruptorEndpoint.class);
        assertEquals(QUEUE_SIZE, disruptor.getRemainingCapacity());

        sendSoManyOverCapacity(DEFAULT_URI, QUEUE_SIZE, 20);
        assertMockEndpointsSatisfied();
    }

    @Test(expected = CamelExecutionException.class)
    public void testDisruptorExceptionWhenFull() throws Exception {
        getMockEndpoint(MOCK_URI).setExpectedMessageCount(QUEUE_SIZE + 20);

        final DisruptorEndpoint disruptor = context.getEndpoint(DEFAULT_URI, DisruptorEndpoint.class);
        assertEquals(QUEUE_SIZE, disruptor.getRemainingCapacity());

        sendSoManyOverCapacity(EXCEPTION_WHEN_FULL_URI, QUEUE_SIZE, 20);
        assertMockEndpointsSatisfied();
    }

    /**
     * This method make sure that we hit the limit by sending 'soMany' messages over the given capacity which allows the
     * delayer to kick in.
     */
    private void sendSoManyOverCapacity(final String uri, final int capacity, final int soMany) {
        for (int i = 0; i < (capacity + soMany); i++) {
            template.sendBody(uri, "Message " + i);
        }
    }

}
