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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheck.State;
import org.apache.camel.support.ScheduledPollConsumerHealthCheck;
import org.apache.camel.util.TimeSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The health check of a polling consumer sees a poll that hangs (CAMEL-26998): with healthCheckMaxPollDuration it is
 * DOWN while a poll has been running longer than that, and as before when the poll is done.
 * <p/>
 * The poll duration is measured with a {@code StopWatch}, which follows {@link TimeSource}, so these tests move a
 * manual clock instead of waiting. The clock is JVM-wide, so these tests run isolated from the other tests.
 */
@Isolated
public class ScheduledPollConsumerHangHealthCheckTest extends ContextTestSupport {

    private static final long MAX = 60000;
    private static final String HANG_MESSAGE
            = "Consumer has been polling for %s, longer than healthCheckMaxPollDuration (1m0s) route: hang (mock://foo)";

    private final ManualClock clock = new ManualClock();
    private final List<Thread> pollers = new ArrayList<>();
    private TimeSource.Provider previous;
    private HangingConsumer consumer;

    @BeforeEach
    void installClock() {
        previous = TimeSource.install(clock);
    }

    @AfterEach
    void releaseAndRestoreClock() throws Exception {
        try {
            if (consumer != null) {
                consumer.release.countDown();
                for (Thread poller : pollers) {
                    poller.join(10000);
                }
            }
        } finally {
            TimeSource.install(previous);
        }
        if (consumer != null) {
            consumer.stop();
        }
    }

    @Test
    public void testDownWhileAPollRunsLongerThanTheMaxAndUpWhenItIsDone() throws Exception {
        consumer = startConsumer(MAX);

        // a poll that is done: UP
        consumer.run();
        assertEquals(State.UP, readiness().getState());
        assertFalse(consumer.isPolling());
        assertEquals(0, consumer.getPollingDuration());

        // a poll that hangs: UP up to the max
        startHangingPoll();
        assertEquals(0, consumer.getPollingDuration());
        assertEquals(State.UP, readiness().getState());
        clock.advance(MAX);
        assertEquals(MAX, consumer.getPollingDuration());
        assertEquals(State.UP, readiness().getState());

        // and DOWN once it runs longer, with a message that says so
        clock.advance(1);
        HealthCheck.Result result = readiness();
        assertEquals(State.DOWN, result.getState());
        assertEquals(String.format(HANG_MESSAGE, "1m0s"), result.getMessage().orElse(null));
        assertEquals(MAX + 1, result.getDetails().get(ScheduledPollConsumerHealthCheck.POLL_DURATION));
        assertFalse(result.getError().isPresent());

        // the poll is done: UP again
        endHangingPoll();
        result = readiness();
        assertEquals(State.UP, result.getState());
        assertFalse(result.getDetails().containsKey(ScheduledPollConsumerHealthCheck.POLL_DURATION));
        assertEquals(0, consumer.getPollingDuration());
    }

    @Test
    public void testAPollThatFailsAfterAHangIsReportedAsBefore() throws Exception {
        consumer = startConsumer(MAX);
        consumer.run();

        startHangingPoll();
        clock.advance(MAX + 1);
        assertEquals(State.DOWN, readiness().getState());

        // the poll ends with an error: DOWN for that error, as without the option
        consumer.failure = new IllegalStateException("Connection reset");
        endHangingPoll();
        HealthCheck.Result result = readiness();
        assertEquals(State.DOWN, result.getState());
        assertEquals("Consumer failed polling 1 times route: hang (mock://foo)", result.getMessage().orElse(null));
        assertEquals("Connection reset", result.getError().map(Throwable::getMessage).orElse(null));
        assertFalse(result.getDetails().containsKey(ScheduledPollConsumerHealthCheck.POLL_DURATION));
    }

    @Test
    public void testOffByDefault() throws Exception {
        consumer = startConsumer(0);
        consumer.run();

        startHangingPoll();
        clock.advance(TimeUnit.HOURS.toMillis(1));
        assertEquals(TimeUnit.HOURS.toMillis(1), consumer.getPollingDuration());
        // as before: the check does not see the poll
        assertEquals(State.UP, readiness().getState());
    }

    @ParameterizedTest
    @EnumSource(State.class)
    public void testFirstPollThatHangs(State initialState) throws Exception {
        consumer = createConsumer(MAX);
        consumer.start();
        ((ScheduledPollConsumerHealthCheck) consumer.getHealthCheck()).setInitialState(initialState);

        // before the first poll is done the check reports the initial state, also while that poll runs
        startHangingPoll();
        clock.advance(MAX);
        assertEquals(initialState, readiness().getState());

        // but not when that poll runs longer than the max
        clock.advance(TimeUnit.MINUTES.toMillis(1));
        HealthCheck.Result result = readiness();
        assertEquals(State.DOWN, result.getState());
        assertEquals(String.format(HANG_MESSAGE, "2m0s"), result.getMessage().orElse(null));

        endHangingPoll();
        assertEquals(State.UP, readiness().getState());
    }

    @Test
    public void testConsumerReadyBeforeItsPollIsDone() throws Exception {
        consumer = startConsumer(MAX);
        // as the sql and ftp consumers, which are ready once they got the rows or the listing, before they route them
        consumer.readyBeforeDone = true;

        startHangingPoll();
        assertTrue(consumer.isConsumerReady());
        assertEquals(State.UP, readiness().getState());

        clock.advance(MAX + 1);
        assertEquals(State.DOWN, readiness().getState());
    }

    @Test
    public void testEachGreedyRunIsAPollOfItsOwn() throws Exception {
        consumer = startConsumer(MAX);
        consumer.setGreedy(true);
        // three polls of 40 seconds that poll a message each, so greedy polls again, and then a poll that hangs
        consumer.quickPolls.set(3);
        consumer.quickPollDuration = TimeUnit.SECONDS.toMillis(40);

        startHangingPoll();
        assertEquals(0, consumer.quickPolls.get());
        assertEquals(0, consumer.getPollingDuration());
        assertEquals(State.UP, readiness().getState());

        clock.advance(MAX + 1);
        assertEquals(State.DOWN, readiness().getState());
    }

    @Test
    public void testConcurrentPolls() throws Exception {
        consumer = startConsumer(MAX);
        consumer.run();

        // one poll hangs
        startHangingPoll();
        clock.advance(TimeUnit.SECONDS.toMillis(50));

        // another poll (the concurrentConsumers option of the scheduler) starts and is done
        consumer.hangs = false;
        consumer.run();
        consumer.hangs = true;

        // the duration is from the most recent poll that started, so the check is UP while polls keep starting
        clock.advance(TimeUnit.SECONDS.toMillis(20));
        assertTrue(consumer.isPolling());
        assertEquals(TimeUnit.SECONDS.toMillis(20), consumer.getPollingDuration());
        assertEquals(State.UP, readiness().getState());

        // and DOWN when none has started for longer than the max
        clock.advance(TimeUnit.SECONDS.toMillis(41));
        assertEquals(State.DOWN, readiness().getState());
    }

    private HangingConsumer createConsumer(long maxPollDuration) {
        HangingConsumer answer = new HangingConsumer(getMockEndpoint("mock:foo"), clock);
        answer.setRouteId("hang");
        answer.setStartScheduler(false);
        answer.setHealthCheckMaxPollDuration(maxPollDuration);
        return answer;
    }

    private HangingConsumer startConsumer(long maxPollDuration) {
        HangingConsumer answer = createConsumer(maxPollDuration);
        answer.start();
        return answer;
    }

    private HealthCheck.Result readiness() {
        return consumer.getHealthCheck().callReadiness();
    }

    /**
     * Runs a poll on a thread of its own, as the scheduler does, and returns when that poll waits.
     */
    private void startHangingPoll() throws InterruptedException {
        consumer.hangs = true;
        CountDownLatch entered = new CountDownLatch(1);
        consumer.entered = entered;
        Thread poller = new Thread(consumer::run, "hanging-poll-" + pollers.size());
        pollers.add(poller);
        poller.start();
        assertTrue(entered.await(10, TimeUnit.SECONDS), "the poll did not start");
    }

    /**
     * Lets the poll that waits go on, and returns when it is done.
     */
    private void endHangingPoll() throws InterruptedException {
        consumer.release.countDown();
        Thread poller = pollers.get(pollers.size() - 1);
        poller.join(10000);
        assertFalse(poller.isAlive(), "the poll did not end");
        consumer.release = new CountDownLatch(1);
    }

    private static class HangingConsumer extends MockScheduledPollConsumer {

        private final ManualClock clock;
        private final AtomicInteger quickPolls = new AtomicInteger();
        private volatile long quickPollDuration;
        private volatile boolean hangs;
        private volatile boolean readyBeforeDone;
        private volatile CountDownLatch entered = new CountDownLatch(1);
        private volatile CountDownLatch release = new CountDownLatch(1);
        private volatile Exception failure;

        HangingConsumer(Endpoint endpoint, ManualClock clock) {
            super(endpoint, null);
            this.clock = clock;
        }

        @Override
        protected int poll() throws Exception {
            if (readyBeforeDone) {
                forceConsumerAsReady();
            }
            if (quickPolls.getAndUpdate(n -> n > 0 ? n - 1 : 0) > 0) {
                clock.advance(quickPollDuration);
                return 1;
            }
            if (hangs) {
                entered.countDown();
                release.await();
                hangs = false;
            }
            Exception e = failure;
            if (e != null) {
                failure = null;
                throw e;
            }
            return 0;
        }
    }

    /**
     * A clock whose monotonic time moves only when the test moves it.
     */
    private static final class ManualClock implements TimeSource.Provider {

        private final AtomicLong nanos = new AtomicLong(TimeUnit.HOURS.toNanos(1));

        void advance(long millis) {
            nanos.addAndGet(TimeUnit.MILLISECONDS.toNanos(millis));
        }

        @Override
        public long currentTimeMillis() {
            return System.currentTimeMillis();
        }

        @Override
        public long nanoTime() {
            return nanos.get();
        }

        @Override
        public void sleep(long millis) throws InterruptedException {
            new CountDownLatch(1).await(millis, TimeUnit.MILLISECONDS);
        }
    }
}
