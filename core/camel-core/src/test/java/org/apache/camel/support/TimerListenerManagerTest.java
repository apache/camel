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
package org.apache.camel.support;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.TimerListener;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 *
 */
public class TimerListenerManagerTest extends ContextTestSupport {

    private final MyTask task = new MyTask();

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testTimerListenerManager() throws Exception {
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
        TimerListenerManager manager = new TimerListenerManager();
        manager.setCamelContext(context);
        manager.addTimerListener(task);
        manager.start();

        assertTrue(task.await(), "Should be invoked");

        manager.stop();
        manager.removeTimerListener(task);
        executor.shutdown();
    }

    @Test
    public void testAddListenerWhileTimerRuns() throws Exception {
        TimerListenerManager manager = new TimerListenerManager();
        manager.setCamelContext(context);
        manager.setInterval(10);
        BlockingListener blocking = new BlockingListener();
        CountingListener next = new CountingListener(2);
        manager.addTimerListener(blocking);
        manager.addTimerListener(next);
        manager.start();
        try {
            // the timer thread is in onTimer of the first listener, the second listener is still to be called
            assertTrue(blocking.awaitEntered(), "The timer should call the first listener");
            CountingListener added = new CountingListener(1);
            manager.addTimerListener(added);
            blocking.release();

            // the timer must keep running and call every listener on the next runs
            assertTrue(next.await(), "The timer should keep calling the second listener");
            assertTrue(added.await(), "The timer should call the listener added while it was running");
        } finally {
            blocking.release();
            manager.shutdown();
        }
    }

    @Test
    public void testRemoveListenerWhileTimerRuns() throws Exception {
        TimerListenerManager manager = new TimerListenerManager();
        manager.setCamelContext(context);
        manager.setInterval(10);
        BlockingListener blocking = new BlockingListener();
        // the run in progress may still call the removed listener once
        TimerListener removed = new CountingListener(1);
        CountingListener last = new CountingListener(2);
        manager.addTimerListener(blocking);
        manager.addTimerListener(removed);
        manager.addTimerListener(last);
        manager.start();
        try {
            assertTrue(blocking.awaitEntered(), "The timer should call the first listener");
            manager.removeTimerListener(removed);
            blocking.release();

            // the timer must keep running and call the remaining listeners on the next runs
            assertTrue(last.await(), "The timer should keep calling the remaining listeners");
            assertTrue(blocking.awaitCalledAgain(), "The timer should keep calling the first listener");
        } finally {
            blocking.release();
            manager.shutdown();
        }
    }

    @Test
    public void testErrorOfAListenerKeepsTheTimerRunning() throws Exception {
        // before, the Error ended the scheduled task: no listener was called again
        assertTimerKeepsRunningAfter(() -> {
            throw new AssertionError("listener failed");
        });
    }

    @Test
    public void testExceptionOfAListenerKeepsTheTimerRunning() throws Exception {
        // control: an exception was already ignored
        assertTimerKeepsRunningAfter(() -> {
            throw new IllegalStateException("listener failed");
        });
    }

    private void assertTimerKeepsRunningAfter(Runnable failure) throws Exception {
        TimerListenerManager manager = new TimerListenerManager();
        manager.setCamelContext(context);
        manager.setInterval(10);
        FailingListener failing = new FailingListener(failure);
        CountingListener next = new CountingListener(3);
        manager.addTimerListener(failing);
        manager.addTimerListener(next);
        manager.start();
        try {
            // the listener after the failing one is called in the same run, and on the next runs
            assertTrue(next.await(), "The timer should keep calling the other listener");
            assertTrue(failing.awaitCalledAgain(), "The timer should call the failing listener again");
        } finally {
            manager.shutdown();
        }
    }

    /**
     * Fails in its first call.
     */
    private static class FailingListener implements TimerListener {

        private final AtomicBoolean first = new AtomicBoolean(true);
        private final CountDownLatch calledAgain = new CountDownLatch(1);
        private final Runnable failure;

        FailingListener(Runnable failure) {
            this.failure = failure;
        }

        @Override
        public void onTimer() {
            if (first.compareAndSet(true, false)) {
                failure.run();
            } else {
                calledAgain.countDown();
            }
        }

        boolean awaitCalledAgain() throws InterruptedException {
            return calledAgain.await(5, TimeUnit.SECONDS);
        }
    }

    /**
     * Blocks the timer thread in its first call until released.
     */
    private static class BlockingListener implements TimerListener {

        private final AtomicBoolean first = new AtomicBoolean(true);
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch released = new CountDownLatch(1);
        private final CountDownLatch calledAgain = new CountDownLatch(1);

        @Override
        public void onTimer() {
            if (first.compareAndSet(true, false)) {
                entered.countDown();
                try {
                    released.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            } else {
                calledAgain.countDown();
            }
        }

        boolean awaitEntered() throws InterruptedException {
            return entered.await(5, TimeUnit.SECONDS);
        }

        boolean awaitCalledAgain() throws InterruptedException {
            return calledAgain.await(5, TimeUnit.SECONDS);
        }

        void release() {
            released.countDown();
        }
    }

    private static class CountingListener implements TimerListener {

        private final CountDownLatch latch;

        CountingListener(int calls) {
            this.latch = new CountDownLatch(calls);
        }

        @Override
        public void onTimer() {
            latch.countDown();
        }

        boolean await() throws InterruptedException {
            return latch.await(5, TimeUnit.SECONDS);
        }
    }

    private static class MyTask implements TimerListener {

        private final CountDownLatch latch = new CountDownLatch(1);

        @Override
        public void onTimer() {
            latch.countDown();
        }

        public boolean await() throws InterruptedException {
            return latch.await(5, TimeUnit.SECONDS);
        }
    }
}
