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
package org.apache.camel.impl.engine;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.spi.PeriodTaskScheduler;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.TimerListenerManager;
import org.apache.camel.support.service.ServiceSupport;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class PeriodTaskSchedulerTest extends ContextTestSupport {

    private final AtomicInteger counter = new AtomicInteger();

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testScheduler() {
        counter.set(0);

        PeriodTaskScheduler scheduler = PluginHelper.getPeriodTaskScheduler(context);
        if (scheduler instanceof TimerListenerManager timerListenerManager) {
            // speedup unit test
            timerListenerManager.setInterval(10);
        }
        scheduler.schedulePeriodTask(counter::incrementAndGet, 10);
        context.start();

        Awaitility.waitAtMost(5, TimeUnit.SECONDS).until(() -> counter.get() > 0);
    }

    @Test
    public void testSchedulerLifecycle() {
        counter.set(0);

        MyTask task = new MyTask();

        PeriodTaskScheduler scheduler = PluginHelper.getPeriodTaskScheduler(context);
        if (scheduler instanceof TimerListenerManager timerListenerManager) {
            // speedup unit test
            timerListenerManager.setInterval(10);
        }
        scheduler.schedulePeriodTask(task, 10);
        context.start();

        Awaitility.waitAtMost(5, TimeUnit.SECONDS).until(() -> counter.get() > 1);

        Assertions.assertTrue(task.getEvent().startsWith("start"));
        Assertions.assertTrue(task.getEvent().endsWith("run"));

        context.stop();

        Assertions.assertTrue(task.getEvent().endsWith("stop"));
    }

    @Test
    public void testSchedulerKeepsRunningAfterAnErrorOfATask() {
        counter.set(0);
        AtomicInteger failingCalls = new AtomicInteger();

        PeriodTaskScheduler scheduler = PluginHelper.getPeriodTaskScheduler(context);
        if (scheduler instanceof TimerListenerManager timerListenerManager) {
            // speedup unit test
            timerListenerManager.setInterval(10);
        }
        // such as a NoClassDefFoundError of a periodic task: the other tasks, and this one, must still run
        scheduler.schedulePeriodTask(() -> {
            if (failingCalls.incrementAndGet() == 1) {
                throw new AssertionError("task failed");
            }
        }, 10);
        scheduler.schedulePeriodTask(counter::incrementAndGet, 10);
        context.start();

        Awaitility.waitAtMost(5, TimeUnit.SECONDS).until(() -> counter.get() > 2 && failingCalls.get() > 1);
    }

    private class MyTask extends ServiceSupport implements Runnable {

        private volatile String event = "";

        public String getEvent() {
            return event;
        }

        @Override
        protected void doStart() {
            event += "start";
        }

        @Override
        protected void doStop() {
            event += "stop";
        }

        @Override
        public void run() {
            counter.incrementAndGet();
            event += "run";
        }
    }

    @Test
    public void testSchedulerRun() {
        final AtomicInteger taskCounter = new AtomicInteger();
        counter.set(0);

        PeriodTaskScheduler scheduler = PluginHelper.getPeriodTaskScheduler(context);
        if (scheduler instanceof TimerListenerManager timerListenerManager) {
            // speedup unit test
            timerListenerManager.setInterval(10);
        }
        // this task is only executed once (it can keep looping forever if needed)
        scheduler.scheduledTask(() -> {
            counter.incrementAndGet();
            for (int i = 0; i < 10; i++) {
                try {
                    Thread.sleep(10);
                } catch (Exception e) {
                    // ignore
                }
                taskCounter.incrementAndGet();
            }
        });
        context.start();

        Awaitility.waitAtMost(5, TimeUnit.SECONDS).until(() -> counter.get() == 1 && taskCounter.get() == 10);
    }

    @Test
    public void testSchedulePeriodTaskWhileTimerRuns() throws Exception {
        final AtomicBoolean first = new AtomicBoolean(true);
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch released = new CountDownLatch(1);
        final AtomicInteger secondCounter = new AtomicInteger();
        final AtomicInteger addedCounter = new AtomicInteger();

        PeriodTaskScheduler scheduler = PluginHelper.getPeriodTaskScheduler(context);
        if (scheduler instanceof TimerListenerManager timerListenerManager) {
            // speedup unit test
            timerListenerManager.setInterval(10);
        }
        // the first task blocks the timer thread in its first run (like a task calling a remote service)
        scheduler.schedulePeriodTask(() -> {
            if (first.compareAndSet(true, false)) {
                entered.countDown();
                try {
                    released.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }, 1);
        scheduler.schedulePeriodTask(secondCounter::incrementAndGet, 1);
        context.start();
        try {
            Assertions.assertTrue(entered.await(5, TimeUnit.SECONDS), "The first task should run");
            // a task scheduled while the timer runs the first task (like a consumer started at runtime)
            scheduler.schedulePeriodTask(addedCounter::incrementAndGet, 1);
            released.countDown();

            // the timer must keep running the tasks
            Awaitility.waitAtMost(5, TimeUnit.SECONDS).until(() -> secondCounter.get() > 1 && addedCounter.get() > 1);
        } finally {
            released.countDown();
        }
    }

    @Test
    public void testSchedulePeriodTaskFromRunningTask() {
        final AtomicBoolean first = new AtomicBoolean(true);
        final AtomicInteger secondCounter = new AtomicInteger();
        final AtomicInteger addedCounter = new AtomicInteger();

        PeriodTaskScheduler scheduler = PluginHelper.getPeriodTaskScheduler(context);
        if (scheduler instanceof TimerListenerManager timerListenerManager) {
            // speedup unit test
            timerListenerManager.setInterval(10);
        }
        // the first task schedules a task from its run, on the timer thread (like a vault refresh task that
        // reloads the routes, whose consumers schedule their own tasks when they start)
        scheduler.schedulePeriodTask(() -> {
            if (first.compareAndSet(true, false)) {
                scheduler.schedulePeriodTask(addedCounter::incrementAndGet, 1);
            }
        }, 1);
        scheduler.schedulePeriodTask(secondCounter::incrementAndGet, 1);
        context.start();

        // the timer must keep running the tasks
        Awaitility.waitAtMost(5, TimeUnit.SECONDS).until(() -> secondCounter.get() > 1 && addedCounter.get() > 1);
    }
}
