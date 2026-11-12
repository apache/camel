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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.util.TimeSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A scheduled poll consumer keeps when it last polled, and when it last polled messages and how many (CAMEL-26715),
 * read from {@link TimeSource}.
 */
public class ScheduledPollConsumerLastPollTest extends ContextTestSupport {

    private final AtomicLong now = new AtomicLong();
    private TimeSource.Provider previous;

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

    @BeforeEach
    void installClock() {
        previous = TimeSource.install(new TimeSource.Provider() {
            @Override
            public long currentTimeMillis() {
                return now.get();
            }

            @Override
            public long nanoTime() {
                return System.nanoTime();
            }

            @Override
            public void sleep(long millis) throws InterruptedException {
                new CountDownLatch(1).await(millis, TimeUnit.MILLISECONDS);
            }
        });
    }

    @AfterEach
    void restoreClock() {
        TimeSource.install(previous);
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
            poll(consumer, 1000, 0);
            assertEquals(1000, consumer.getLastPollTimestamp());
            assertEquals(0, consumer.getLastPollMessagesTimestamp());
            assertEquals(0, consumer.getLastPollMessagesCount());

            // 3 messages
            poll(consumer, 2000, 3);
            assertEquals(2000, consumer.getLastPollTimestamp());
            assertEquals(2000, consumer.getLastPollMessagesTimestamp());
            assertEquals(3, consumer.getLastPollMessagesCount());

            // nothing again: the last poll moves on, the last poll with messages stays
            poll(consumer, 3000, 0);
            assertEquals(3000, consumer.getLastPollTimestamp());
            assertEquals(2000, consumer.getLastPollMessagesTimestamp());
            assertEquals(3, consumer.getLastPollMessagesCount());

            // a poll that fails is a poll too
            consumer.setExceptionToThrowOnPoll(new IllegalStateException("Forced"));
            poll(consumer, 4000, 0);
            assertEquals(4000, consumer.getLastPollTimestamp());
            assertEquals(2000, consumer.getLastPollMessagesTimestamp());
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
            poll(consumer, 5000, 2);
            assertEquals(5000, consumer.getLastPollTimestamp());
            assertEquals(2, consumer.getLastPollMessagesCount());
        } finally {
            consumer.stop();
        }

        // as the counters: a restarted consumer has not polled yet
        assertEquals(0, consumer.getLastPollTimestamp());
        assertEquals(0, consumer.getLastPollMessagesTimestamp());
        assertEquals(0, consumer.getLastPollMessagesCount());
    }

    private void poll(MyConsumer consumer, long time, int messages) {
        now.set(time);
        consumer.messages = messages;
        consumer.run();
    }
}
