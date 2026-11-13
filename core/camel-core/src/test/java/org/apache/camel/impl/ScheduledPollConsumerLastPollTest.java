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
package org.apache.camel.impl;

import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A scheduled poll consumer keeps when it last polled, and when it last polled messages and how many (CAMEL-26715). The
 * times are the system clock, as the other timestamps of management and the developer consoles.
 */
public class ScheduledPollConsumerLastPollTest extends ContextTestSupport {

    private static class MyConsumer extends MockScheduledPollConsumer {

        private int messages;

        MyConsumer(Endpoint endpoint) {
            super(endpoint, null);
        }

        @Override
        protected int poll() throws Exception {
            super.poll();
            return messages;
        }
    }

    @Test
    public void testLastPollAndLastPollWithMessages() {
        MyConsumer consumer = new MyConsumer(getMockEndpoint("mock:foo"));
        consumer.setStartScheduler(false);
        consumer.start();
        try {
            assertEquals(0, consumer.getLastPollTimestamp());
            assertEquals(0, consumer.getLastPollMessagesTimestamp());
            assertEquals(0, consumer.getLastPollMessagesCount());

            // nothing to poll
            long time = poll(consumer, 0);
            assertEquals(time, consumer.getLastPollTimestamp());
            assertEquals(0, consumer.getLastPollMessagesTimestamp());
            assertEquals(0, consumer.getLastPollMessagesCount());

            // 3 messages
            long withMessages = poll(consumer, 3);
            assertEquals(withMessages, consumer.getLastPollTimestamp());
            assertEquals(withMessages, consumer.getLastPollMessagesTimestamp());
            assertEquals(3, consumer.getLastPollMessagesCount());

            // nothing again: the last poll moves on, the last poll with messages stays
            time = poll(consumer, 0);
            assertTrue(time > withMessages);
            assertEquals(time, consumer.getLastPollTimestamp());
            assertEquals(withMessages, consumer.getLastPollMessagesTimestamp());
            assertEquals(3, consumer.getLastPollMessagesCount());

            // a poll that fails is a poll too
            consumer.setExceptionToThrowOnPoll(new IllegalStateException("Forced"));
            time = poll(consumer, 0);
            assertEquals(time, consumer.getLastPollTimestamp());
            assertEquals(withMessages, consumer.getLastPollMessagesTimestamp());
            assertEquals(3, consumer.getLastPollMessagesCount());
            assertEquals(1, consumer.getErrorCounter());
        } finally {
            consumer.stop();
        }
    }

    @Test
    public void testStopClearsTheLastPoll() {
        MyConsumer consumer = new MyConsumer(getMockEndpoint("mock:foo"));
        consumer.setStartScheduler(false);
        consumer.start();
        try {
            long time = poll(consumer, 2);
            assertEquals(time, consumer.getLastPollTimestamp());
            assertEquals(2, consumer.getLastPollMessagesCount());
        } finally {
            consumer.stop();
        }

        // as the counters: a restarted consumer has not polled yet
        assertEquals(0, consumer.getLastPollTimestamp());
        assertEquals(0, consumer.getLastPollMessagesTimestamp());
        assertEquals(0, consumer.getLastPollMessagesCount());
    }

    /**
     * Polls once, in a millisecond of its own (so two polls never share a time), and returns the time of the poll as
     * the consumer recorded it, after checking it is the system clock at the poll.
     */
    private static long poll(MyConsumer consumer, int messages) {
        long previous = consumer.getLastPollTimestamp();
        await().atMost(1, TimeUnit.SECONDS).until(() -> System.currentTimeMillis() > previous);
        consumer.messages = messages;
        long before = System.currentTimeMillis();
        consumer.run();
        long after = System.currentTimeMillis();
        long time = consumer.getLastPollTimestamp();
        assertTrue(time >= before && time <= after, "the poll time " + time + " is not within " + before + ".." + after);
        return time;
    }
}
