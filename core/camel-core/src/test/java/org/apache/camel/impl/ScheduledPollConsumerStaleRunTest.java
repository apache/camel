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

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.spi.HttpResponseAware;
import org.apache.camel.support.DefaultScheduledPollConsumerScheduler;
import org.apache.camel.support.ScheduledPollConsumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A poll that is still running when the consumer is stopped and started again must not change the state of the
 * restarted consumer.
 */
class ScheduledPollConsumerStaleRunTest extends ContextTestSupport {

    private final CountDownLatch release = new CountDownLatch(1);
    private Thread poller;
    private Thread restarter;
    private StaleConsumer consumer;

    private static void awaitRelease(CountDownLatch latch) {
        try {
            if (!latch.await(20, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Not released");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static class StaleConsumer extends ScheduledPollConsumer {

        private volatile boolean forceReadyInPoll;
        private volatile Exception exceptionToThrowOnPoll;
        private volatile Error errorToThrowOnPoll;
        // set to hold the next poll (the remote call of the poll is in progress)
        private volatile CountDownLatch pollHeld;
        // set to hold the next call of addLastErrorDetail (after the generation check of the poll)
        private volatile CountDownLatch detailHeld;
        // set to hold the next isSuspended() check of a run on the stale-poll thread (when the run has started)
        private volatile CountDownLatch runStartHeld;
        // set to hold the next getEndpoint() call on the stale-poll thread (when run() has started, before doRun())
        private volatile CountDownLatch endpointHeld;
        private final AtomicInteger polls = new AtomicInteger();
        private volatile CountDownLatch release;

        StaleConsumer(Endpoint endpoint) {
            super(endpoint, null);
        }

        @Override
        protected int poll() throws Exception {
            polls.incrementAndGet();
            CountDownLatch held = pollHeld;
            if (held != null) {
                pollHeld = null;
                held.countDown();
                awaitRelease(release);
            }
            if (forceReadyInPoll) {
                // such as camel-aws2-sqs when its receive call returns
                forceConsumerAsReady();
            }
            Error error = errorToThrowOnPoll;
            if (error != null) {
                throw error;
            }
            Exception e = exceptionToThrowOnPoll;
            if (e != null) {
                throw e;
            }
            return 0;
        }

        @Override
        protected void addLastErrorDetail(String key, Object value) {
            CountDownLatch held = detailHeld;
            if (held != null) {
                detailHeld = null;
                held.countDown();
                awaitRelease(release);
            }
            super.addLastErrorDetail(key, value);
        }

        @Override
        public boolean isSuspended() {
            CountDownLatch held = runStartHeld;
            if (held != null && "stale-poll".equals(Thread.currentThread().getName())) {
                runStartHeld = null;
                held.countDown();
                awaitRelease(release);
            }
            return super.isSuspended();
        }

        @Override
        public Endpoint getEndpoint() {
            CountDownLatch held = endpointHeld;
            if (held != null && "stale-poll".equals(Thread.currentThread().getName())) {
                endpointHeld = null;
                held.countDown();
                awaitRelease(release);
            }
            return super.getEndpoint();
        }

        Map<String, Object> lastErrorDetails() {
            return getLastErrorDetails();
        }
    }

    // a scheduler that holds its next unscheduleTask() call before it cancels the tasks
    private static class HoldingScheduler extends DefaultScheduledPollConsumerScheduler {

        private volatile CountDownLatch unscheduleHeld;
        private volatile CountDownLatch release;

        @Override
        public void unscheduleTask() {
            CountDownLatch held = unscheduleHeld;
            if (held != null) {
                unscheduleHeld = null;
                held.countDown();
                awaitRelease(release);
            }
            super.unscheduleTask();
        }
    }

    private static class MyHttpException extends Exception implements HttpResponseAware {
        MyHttpException() {
            super("Service unavailable");
        }

        @Override
        public int getHttpResponseCode() {
            return 503;
        }

        @Override
        public void setHttpResponseCode(int code) {
            // noop
        }

        @Override
        public String getHttpResponseStatus() {
            return "Service Unavailable";
        }

        @Override
        public void setHttpResponseStatus(String status) {
            // noop
        }
    }

    @AfterEach
    void cleanUp() throws Exception {
        release.countDown();
        if (poller != null) {
            poller.join(10000);
            assertFalse(poller.isAlive(), "the poll did not complete");
        }
        if (restarter != null) {
            restarter.join(10000);
            assertFalse(restarter.isAlive(), "the restart did not complete");
        }
        if (consumer != null) {
            consumer.stop();
        }
    }

    private StaleConsumer newConsumer() {
        consumer = new StaleConsumer(getMockEndpoint("mock:foo"));
        consumer.release = release;
        // the polls are run by the test
        consumer.setStartScheduler(false);
        return consumer;
    }

    private void startPoller() {
        startPoller(new AtomicReference<>());
    }

    /**
     * Starts the poll in another thread, and keeps what the run threw (as the scheduler would see it).
     */
    private void startPoller(AtomicReference<Throwable> thrown) {
        poller = new Thread(() -> {
            try {
                consumer.run();
            } catch (Throwable t) {
                thrown.set(t);
            }
        }, "stale-poll");
        poller.start();
    }

    private void joinPoller() throws InterruptedException {
        poller.join(10000);
        assertFalse(poller.isAlive(), "the poll did not complete");
    }

    /**
     * Stops and starts the consumer in another thread, and returns once that thread is done or waits for the poll in
     * progress (with the fix, the stop waits until the poll has updated the state of before the stop).
     */
    private void restartWhilePollIsHeld() {
        CountDownLatch restarted = new CountDownLatch(1);
        restarter = new Thread(() -> {
            consumer.stop();
            consumer.start();
            restarted.countDown();
        }, "restart");
        restarter.start();
        await().atMost(10, TimeUnit.SECONDS)
                .until(() -> restarted.getCount() == 0 || restarter.getState() == Thread.State.WAITING);
    }

    @Test
    void runFromBeforeStopDoesNotPoll() throws Exception {
        newConsumer();
        consumer.errorToThrowOnPoll = new InternalError("simulated");
        CountDownLatch endpointHeld = new CountDownLatch(1);
        consumer.endpointHeld = endpointHeld;
        consumer.start();

        // the run has started, and is held before its poll
        startPoller();
        assertTrue(endpointHeld.await(10, TimeUnit.SECONDS));

        consumer.stop();
        consumer.start();
        release.countDown();
        joinPoller();

        // it does not poll for the restarted consumer, as the fatal error of that poll would be ignored (CAMEL-27515)
        assertEquals(0, consumer.polls.get(), "The run from before the stop polled for the restarted consumer");
        assertNull(consumer.getFatalError());

        // the same error of a poll of the restarted consumer stops it
        consumer.run();
        assertEquals(1, consumer.polls.get());
        assertNotNull(consumer.getFatalError());
    }

    @Test
    void forceReadyOfPollFromBeforeStopIsIgnored() throws Exception {
        newConsumer();
        consumer.forceReadyInPoll = true;
        CountDownLatch pollHeld = new CountDownLatch(1);
        consumer.pollHeld = pollHeld;
        consumer.start();

        startPoller();
        assertTrue(pollHeld.await(10, TimeUnit.SECONDS));

        // stop and start while the remote call of the poll is in progress (such as a long poll)
        consumer.stop();
        consumer.start();
        assertFalse(consumer.isConsumerReady());

        // the remote call returns, and the poll forces the consumer as ready
        release.countDown();
        joinPoller();
        assertFalse(consumer.isConsumerReady(), "the restarted consumer has not polled yet");

        // a poll of the restarted consumer forces it as ready
        consumer.run();
        assertTrue(consumer.isConsumerReady());
    }

    @Test
    void stateOfPollFromBeforeStopIsNotKept() throws Exception {
        newConsumer();
        consumer.exceptionToThrowOnPoll = new MyHttpException();
        CountDownLatch detailHeld = new CountDownLatch(1);
        consumer.detailHeld = detailHeld;
        consumer.start();

        // the poll fails, and is held after it has checked that the consumer was not stopped
        startPoller();
        assertTrue(detailHeld.await(10, TimeUnit.SECONDS));

        restartWhilePollIsHeld();
        release.countDown();
        joinPoller();
        restarter.join(10000);
        assertFalse(restarter.isAlive(), "the restart did not complete");

        assertTrue(consumer.isStarted());
        assertFalse(consumer.isFirstPollDone(), "the restarted consumer has not polled yet");
        assertEquals(0, consumer.getErrorCounter());
        assertNull(consumer.lastErrorDetails());
    }

    @Test
    void errorOfPollFromBeforeStopDoesNotMarkRestartedConsumerDown() throws Exception {
        newConsumer();
        final OutOfMemoryError expectedError = new OutOfMemoryError("Simulated error on poll");
        consumer.errorToThrowOnPoll = expectedError;
        CountDownLatch pollHeld = new CountDownLatch(1);
        consumer.pollHeld = pollHeld;
        consumer.start();

        AtomicReference<Throwable> thrown = new AtomicReference<>();
        startPoller(thrown);
        assertTrue(pollHeld.await(10, TimeUnit.SECONDS));

        // stop and start while the poll is in progress, which then throws the Error
        consumer.stop();
        consumer.start();
        release.countDown();
        joinPoller();

        // the run of the stopped consumer logs the Error, and does not throw it on (CAMEL-27515)
        assertNull(thrown.get());
        // the restarted consumer has not polled yet, so the Error does not stop it
        assertNull(consumer.getFatalError(), "The poll of before the stop stopped the restarted consumer");
        assertTrue(consumer.isRunAllowed());
        assertEquals(0, consumer.getErrorCounter());

        // a poll of the restarted consumer is healthy
        consumer.errorToThrowOnPoll = null;
        consumer.run();
        assertEquals(HealthCheck.State.UP, consumer.getHealthCheck().call().getState());

        // the same error of a poll of the restarted consumer stops it
        consumer.errorToThrowOnPoll = expectedError;
        consumer.run();
        assertSame(expectedError, consumer.getFatalError());
        assertFalse(consumer.isRunAllowed());
        assertEquals(HealthCheck.State.DOWN, consumer.getHealthCheck().call().getState());
    }

    @Test
    void repeatCountOfRunFromBeforeStopDoesNotUnscheduleRestartedConsumer() throws Exception {
        newConsumer();
        HoldingScheduler scheduler = new HoldingScheduler();
        scheduler.release = release;
        // the scheduled tasks do not run during the test
        scheduler.setInitialDelay(TimeUnit.HOURS.toMillis(1));
        consumer.setScheduler(scheduler);
        consumer.setStartScheduler(true);
        consumer.setRepeatCount(1);
        consumer.start();

        // the first run polls
        consumer.run();
        assertEquals(1, consumer.getCounter());
        assertTrue(scheduler.isSchedulerStarted());

        // the second run has used up the repeat count, and is held before it unschedules the task
        CountDownLatch unscheduleHeld = new CountDownLatch(1);
        scheduler.unscheduleHeld = unscheduleHeld;
        startPoller();
        assertTrue(unscheduleHeld.await(10, TimeUnit.SECONDS));

        restartWhilePollIsHeld();
        release.countDown();
        joinPoller();
        restarter.join(10000);
        assertFalse(restarter.isAlive(), "the restart did not complete");

        assertTrue(consumer.isStarted());
        assertTrue(scheduler.isSchedulerStarted(), "the restarted consumer lost its scheduled task");
        assertEquals(0, consumer.getCounter());
    }

    @Test
    void backoffOfPollFromBeforeStopIsNotKept() throws Exception {
        newConsumer();
        consumer.setBackoffMultiplier(2);
        consumer.setBackoffErrorThreshold(1);
        consumer.exceptionToThrowOnPoll = new MyHttpException();
        consumer.start();

        // the consumer is failing
        consumer.run();
        assertEquals(1, consumer.getErrorCounter());

        // the next run has started, and is held before its backoff check
        CountDownLatch runStartHeld = new CountDownLatch(1);
        consumer.runStartHeld = runStartHeld;
        startPoller();
        assertTrue(runStartHeld.await(10, TimeUnit.SECONDS));

        consumer.stop();
        consumer.start();
        assertEquals(0, consumer.getErrorCounter());

        // the first poll of the restarted consumer fails too
        consumer.run();
        assertEquals(1, consumer.getCounter());
        assertEquals(1, consumer.getErrorCounter());
        assertEquals(0, consumer.getBackoffCounter());

        // the run from before the stop goes on
        release.countDown();
        joinPoller();
        assertEquals(0, consumer.getBackoffCounter(), "the run from before the stop changed the backoff counter");
        assertEquals(1, consumer.getCounter());
        assertEquals(1, consumer.getErrorCounter());

        // the next run of the restarted consumer backs off after its first error, as configured (the error base of
        // the backoff is still 0)
        consumer.run();
        assertEquals(1, consumer.getBackoffCounter());
        assertEquals(1, consumer.getCounter(), "the restarted consumer polled instead of backing off");
    }
}
