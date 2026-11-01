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

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.spi.SupervisingRouteController;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.EventNotifierSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Removing a route that the supervising route controller is restarting cancels its restart task while the CamelContext
 * holds its route lock, and the task completes in the removing thread. A thread that holds the lock of the controller
 * and waits for the route lock (the restart attempt of that route, or a manual stop of another route) must not deadlock
 * with it.
 * <p/>
 * The interleaving is forced: the removal is held while it shuts down the endpoint of its route (under the route lock),
 * until the other thread holds the controller lock and waits for the route lock.
 */
@Timeout(120)
class DefaultSupervisingRouteControllerRemoveRouteDeadlockTest {

    private final ThreadMXBean threads = ManagementFactory.getThreadMXBean();
    private final CountDownLatch shuttingDown = new CountDownLatch(1);
    private final CountDownLatch continueShutdown = new CountDownLatch(1);
    private final AtomicBoolean holdShutdown = new AtomicBoolean(true);
    private final AtomicBoolean failingA = new AtomicBoolean();
    private final AtomicBoolean failingB = new AtomicBoolean();
    private final AtomicReference<Throwable> removeFailure = new AtomicReference<>();
    private CamelContext context;
    private boolean deadlocked;

    @AfterEach
    void stopContext() {
        // the threads of a deadlock cannot be released, and stopping the context would wait for them forever
        continueShutdown.countDown();
        if (context != null && !deadlocked) {
            context.stop();
        }
    }

    @Test
    void testRemoveStoppedRouteWhileAnotherRouteIsBeingStopped() throws Exception {
        // a route that is not restarting has no restart task to cancel
        createContext(3600_000);
        context.start();
        assertEquals(ServiceStatus.Started, context.getRouteController().getRouteStatus("b"));
        context.getRouteController().stopRoute("a");

        Thread remove = removeRouteA();
        // stopping route b holds the controller lock and then waits for the route lock that the removal holds
        Thread stop = startThread("stop-b", () -> context.getRouteController().stopRoute("b"));
        awaitBlockedOnRouteLock("stop-b");
        continueShutdown.countDown();

        assertCompletes(remove, stop);
        assertNull(removeFailure.get(), "removeRoute failed");
        assertNull(context.getRoute("a"), "route a is not removed");
        assertEquals(ServiceStatus.Stopped, context.getRouteController().getRouteStatus("b"));
    }

    @Test
    void testRemoveRestartingRouteWhileItsRestartAttemptRuns() throws Exception {
        // route a fails to start, and is restarted every 10 millis
        failingA.set(true);
        createContext(10);
        context.start();
        SupervisingRouteController src = context.getRouteController().supervising();
        await().atMost(10, TimeUnit.SECONDS).until(() -> src.getRestartingRouteState("a") != null);

        Thread remove = removeRouteA();
        // the next restart attempt of route a holds the controller lock and then waits for the route lock
        String attempt = awaitBlockedOnRouteLock("SupervisingRouteController");
        continueShutdown.countDown();

        assertCompletes(remove, findThread(attempt), false);
        assertNull(removeFailure.get(), "removeRoute failed");
        assertNull(context.getRoute("a"), "route a is not removed");
        await().atMost(10, TimeUnit.SECONDS).until(() -> src.getRestartingRouteState("a") == null);
        assertTrue(src.getRestartingRoutes().isEmpty(), "routes are still restarting");
        assertTrue(src.getExhaustedRoutes().isEmpty(), "a route is reported as exhausted");
        assertFalse(src.hasUnhealthyRoutes(), "the controller reports unhealthy routes");

        // the restart attempt has released the lock of the controller: another route can be stopped
        Thread stop = startThread("stop-b", () -> context.getRouteController().stopRoute("b"));
        stop.join(10_000);
        assertFalse(stop.isAlive(), "stopping route b did not complete");
        assertEquals(ServiceStatus.Stopped, context.getRouteController().getRouteStatus("b"));
    }

    @Test
    void testRemoveRestartingRouteWhileAnotherRouteIsBeingStopped() throws Exception {
        // route a fails to start, and waits (for long) for its next restart attempt
        failingA.set(true);
        createContext(3600_000);
        context.start();
        SupervisingRouteController src = context.getRouteController().supervising();
        await().atMost(10, TimeUnit.SECONDS).until(() -> src.getRestartingRouteState("a") != null);
        assertEquals(ServiceStatus.Stopped, context.getRouteController().getRouteStatus("a"));

        // removing route a cancels its restart task, and the task completes in the removing thread
        Thread remove = removeRouteA();
        Thread stop = startThread("stop-b", () -> context.getRouteController().stopRoute("b"));
        awaitBlockedOnRouteLock("stop-b");
        continueShutdown.countDown();

        assertCompletes(remove, stop);
        assertNull(removeFailure.get(), "removeRoute failed");
        assertNull(context.getRoute("a"), "route a is not removed");
        assertNull(src.getRestartingRouteState("a"), "route a still has a restart task");
        assertTrue(src.getRestartingRoutes().isEmpty(), "routes are still restarting");
        assertFalse(src.hasUnhealthyRoutes(), "the controller reports unhealthy routes");
        assertEquals(ServiceStatus.Stopped, context.getRouteController().getRouteStatus("b"));
    }

    @Test
    void testStopRouteWhileItsLastRestartAttemptFails() throws Exception {
        // the restart task of route a becomes exhausted, and route a is stopped manually before the completion of the
        // exhausted task takes the lock of the controller: the route is then no longer supervised by the task, so it
        // must not be reported as exhausted (and unhealthy)
        failingA.set(true);
        CountDownLatch inFailure = new CountDownLatch(1);
        CountDownLatch proceed = new CountDownLatch(1);
        AtomicReference<Thread> timer = new AtomicReference<>();
        createContext(50);
        DefaultSupervisingRouteController src = (DefaultSupervisingRouteController) context.getRouteController();
        src.setBackOffMaxAttempts(1);
        context.getManagementStrategy().addEventNotifier(new EventNotifierSupport() {
            @Override
            public void notify(CamelEvent event) throws Exception {
                if (event instanceof CamelEvent.RouteRestartingFailureEvent e && !e.isExhausted()
                        && "a".equals(e.getRoute().getRouteId())
                        && Thread.currentThread().getName().contains("SupervisingRouteController")) {
                    timer.compareAndSet(null, Thread.currentThread());
                    inFailure.countDown();
                    assertTrue(proceed.await(20, TimeUnit.SECONDS), "the test did not let the restart attempt go on");
                }
            }
        });
        context.start();
        assertTrue(inFailure.await(10, TimeUnit.SECONDS), "the restart attempt of route a did not fail");

        Lock lock = src.getLock();
        lock.lock();
        try {
            proceed.countDown();
            // the timer thread waits for the lock of the controller
            String me = Thread.currentThread().getName();
            await().atMost(10, TimeUnit.SECONDS).until(() -> {
                ThreadInfo info = threads.getThreadInfo(timer.get().threadId());
                return info != null && me.equals(info.getLockOwnerName());
            });
            context.getRouteController().stopRoute("a");
        } finally {
            lock.unlock();
        }
        // the timer thread is done with the task
        await().atMost(10, TimeUnit.SECONDS).until(() -> {
            ThreadInfo info = threads.getThreadInfo(timer.get().threadId(), 50);
            return info != null && Arrays.stream(info.getStackTrace())
                    .noneMatch(s -> s.getClassName().contains("DefaultSupervisingRouteController"));
        });
        assertEquals(ServiceStatus.Stopped, context.getRouteController().getRouteStatus("a"));
        assertTrue(src.getExhaustedRoutes().isEmpty(), "a route is reported as exhausted");
        assertFalse(src.hasUnhealthyRoutes(), "the controller reports unhealthy routes");
    }

    private void createContext(long backOffDelay) throws Exception {
        context = new DefaultCamelContext();
        context.addComponent("hook", new HookComponent());
        SupervisingRouteController src = context.getRouteController().supervising();
        src.setBackOffDelay(backOffDelay);
        src.setInitialDelay(0);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("hook:a").routeId("a").to("log:a?level=OFF");
                from("hook:b").routeId("b").to("log:b?level=OFF");
            }
        });
    }

    private Thread removeRouteA() throws InterruptedException {
        Thread remove = startThread("remove-a", () -> {
            try {
                assertTrue(context.removeRoute("a"), "route a was not removed");
            } catch (Throwable e) {
                removeFailure.set(e);
            }
        });
        // the removal holds the route lock while it shuts down the endpoint of route a
        assertTrue(shuttingDown.await(10, TimeUnit.SECONDS), "removeRoute did not shut down the endpoint of route a");
        return remove;
    }

    private Thread startThread(String name, ThrowingRunnable task) {
        Thread t = new Thread(() -> {
            try {
                task.run();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, name);
        t.setDaemon(true);
        t.start();
        return t;
    }

    /**
     * Waits until a thread whose name contains the given text waits for a lock that the removing thread holds.
     */
    private String awaitBlockedOnRouteLock(String namePart) {
        AtomicReference<String> name = new AtomicReference<>();
        await().atMost(10, TimeUnit.SECONDS).until(() -> {
            for (ThreadInfo info : threads.dumpAllThreads(true, true)) {
                if (info.getThreadName().contains(namePart) && "remove-a".equals(info.getLockOwnerName())) {
                    name.set(info.getThreadName());
                    return true;
                }
            }
            return false;
        });
        return name.get();
    }

    private static Thread findThread(String name) {
        return Thread.getAllStackTraces().keySet().stream().filter(t -> t.getName().equals(name)).findFirst()
                .orElseThrow();
    }

    private void assertCompletes(Thread remove, Thread other) throws InterruptedException {
        assertCompletes(remove, other, true);
    }

    private void assertCompletes(Thread remove, Thread other, boolean otherEnds) throws InterruptedException {
        remove.join(10_000);
        if (remove.isAlive()) {
            long[] ids = threads.findDeadlockedThreads();
            deadlocked = ids != null;
            StringBuilder sb = new StringBuilder("removeRoute did not complete");
            if (ids != null) {
                sb.append(", deadlock:");
                for (ThreadInfo info : threads.getThreadInfo(ids, true, true)) {
                    sb.append("\n  ").append(info.getThreadName()).append(" waits for ").append(info.getLockName())
                            .append(" held by ").append(info.getLockOwnerName());
                }
            }
            fail(sb.toString());
        }
        if (otherEnds) {
            other.join(10_000);
            assertFalse(other.isAlive(), other.getName() + " did not complete");
        } else {
            // a pooled thread does not end, but must no longer wait for the route lock
            await().atMost(10, TimeUnit.SECONDS).until(() -> {
                ThreadInfo info = threads.getThreadInfo(other.threadId());
                return info == null || !"remove-a".equals(info.getLockOwnerName());
            });
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private final class HookComponent extends DefaultComponent {

        @Override
        protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) {
            boolean isA = "a".equals(remaining);
            return new DefaultEndpoint(uri, this) {
                @Override
                public Producer createProducer() {
                    throw new UnsupportedOperationException();
                }

                @Override
                public Consumer createConsumer(Processor processor) {
                    return new DefaultConsumer(this, processor) {
                        @Override
                        protected void doStart() throws Exception {
                            if (isA ? failingA.get() : failingB.get()) {
                                throw new IllegalStateException("Cannot connect");
                            }
                            super.doStart();
                        }
                    };
                }

                @Override
                protected void doShutdown() throws Exception {
                    // removeRoute shuts down the endpoint of the removed route while it holds the route lock
                    if (isA && holdShutdown.compareAndSet(true, false)) {
                        shuttingDown.countDown();
                        assertTrue(continueShutdown.await(20, TimeUnit.SECONDS), "the test did not let the removal go on");
                    }
                    super.doShutdown();
                }
            };
        }
    }
}
