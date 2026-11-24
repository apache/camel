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
package org.apache.camel.component.mllp;

import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Delayed;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit.rule.mllp.MllpServerResource;
import org.apache.camel.test.mllp.Hl7TestMessageGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The producer keeps one idle connection check when it reconnects, when the check runs while process() reconnects, and
 * across a stop and start; and process() called while the producer stops opens no connection. The idle checks run on a
 * manual executor that the test installs after start(), so the test decides when a check runs.
 */
@Timeout(60)
class MllpTcpClientProducerIdleCheckTest {

    // long enough that only the test runs the checks; the idle time is set through the endpoint's activity ticks
    static final int IDLE_TIMEOUT = 60000;

    @RegisterExtension
    MllpServerResource mllpServer = new MllpServerResource("localhost", 0);

    private CamelContext context;
    private TestProducer producer;

    @BeforeEach
    void startContext() {
        context = new DefaultCamelContext();
        context.start();
    }

    @AfterEach
    void stopProducerAndContext() {
        if (producer != null) {
            producer.stop();
        }
        context.stop();
    }

    @Test
    void reconnectReplacesTheIdleCheckOfThePreviousConnection() throws Exception {
        // the receiver closes the connection instead of acknowledging the second message of each connection: every
        // send after the first one reconnects and resends inside process()
        mllpServer.setCloseSocketBeforeAcknowledgementModulus(2);
        producer = startProducer("?idleTimeout=" + IDLE_TIMEOUT);
        ManualExecutor executor = producer.installManualExecutor();

        Socket previous = null;
        for (int i = 1; i <= 5; i++) {
            assertNull(send().getException(), "message " + i);
            assertNotSame(previous, producer.socket, "message " + i + " is sent on a new connection");
            previous = producer.socket;
            assertEquals(i, executor.scheduled.size(), "one check scheduled per connection");
            assertEquals(1, executor.pending().size(), "pending idle checks after message " + i);
        }

        // the connection is idle: the one check closes it and the chain ends
        producer.getEndpoint().updateLastConnectionActivityTicks(System.currentTimeMillis() - IDLE_TIMEOUT - 1000);
        executor.fire(executor.pending().get(0));
        assertTrue(producer.socket.isClosed());
        assertEquals(0, executor.pending().size());
    }

    @Test
    void idleCheckThatRunsWhileProcessReconnectsDoesNotAddAChain() throws Exception {
        mllpServer.setCloseSocketBeforeAcknowledgementModulus(2);
        producer = startProducer("?idleTimeout=" + IDLE_TIMEOUT);
        ManualExecutor executor = producer.installManualExecutor();
        assertNull(send().getException());
        ManualCheck firstCheck = executor.pending().get(0);

        ReentrantLock lock = producer.serviceLock();
        AtomicReference<Throwable> checkFailure = new AtomicReference<>();
        Thread checkThread = new Thread(() -> {
            try {
                executor.fire(firstCheck);
            } catch (Throwable t) {
                checkFailure.set(t);
            }
        }, "idle-check");
        Exchange exchange;
        lock.lock();
        try {
            // the check of the first connection is due and waits for the lock, held by a process() call
            checkThread.start();
            await().atMost(10, TimeUnit.SECONDS).until(() -> lock.hasQueuedThread(checkThread));
            // process() (the lock is reentrant) reconnects: the receiver closes the connection before the ack
            exchange = send();
        } finally {
            lock.unlock();
        }
        checkThread.join(10000);

        assertFalse(checkThread.isAlive());
        assertNull(checkFailure.get());
        assertNull(exchange.getException());
        assertFalse(producer.socket.isClosed(), "the check does not close the connection that was just used");
        assertEquals(1, executor.pending().size(), "the check found the new connection open and rescheduled itself");
    }

    @Test
    void idleCheckClosingTheConnectionRacesASend() throws Exception {
        // control: the lock orders the two, in either order the message is acknowledged and one check is pending
        producer = startProducer("?idleTimeout=" + IDLE_TIMEOUT);
        ManualExecutor executor = producer.installManualExecutor();
        assertNull(send().getException());
        ManualCheck firstCheck = executor.pending().get(0);
        producer.getEndpoint().updateLastConnectionActivityTicks(System.currentTimeMillis() - IDLE_TIMEOUT - 1000);

        ReentrantLock lock = producer.serviceLock();
        AtomicReference<Exchange> sent = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread checkThread = new Thread(() -> {
            try {
                executor.fire(firstCheck);
            } catch (Throwable t) {
                failure.set(t);
            }
        }, "idle-check");
        Thread sendThread = new Thread(() -> {
            try {
                sent.set(send());
            } catch (Throwable t) {
                failure.set(t);
            }
        }, "send");
        lock.lock();
        try {
            checkThread.start();
            sendThread.start();
            await().atMost(10, TimeUnit.SECONDS)
                    .until(() -> lock.hasQueuedThread(checkThread) && lock.hasQueuedThread(sendThread));
        } finally {
            lock.unlock();
        }
        checkThread.join(10000);
        sendThread.join(10000);

        assertFalse(checkThread.isAlive());
        assertFalse(sendThread.isAlive());
        assertNull(failure.get());
        assertNull(sent.get().getException());
        assertFalse(producer.socket.isClosed());
        assertEquals(1, executor.pending().size());
    }

    @Test
    void idleCheckOfThePreviousStartDoesNotRunAfterARestart() throws Exception {
        producer = startProducer("?idleTimeout=" + IDLE_TIMEOUT);
        ManualExecutor first = producer.installManualExecutor();
        assertNull(send().getException());
        assertEquals(1, first.pending().size());

        producer.stop();
        // the executor of the first start keeps no check to run after the stop
        assertEquals(0, first.runnableAfterShutdown().size(), "checks of the first start left after the stop");
        producer.start();
        ManualExecutor second = producer.installManualExecutor();
        assertNull(send().getException());
        assertEquals(1, second.pending().size());

        // run what the executor of the first start still runs after the stop
        List<ManualCheck> leftOver = first.runnableAfterShutdown();
        for (ManualCheck check : leftOver) {
            first.fire(check);
        }
        assertEquals(1, second.pending().size(), "pending idle checks after the restart");
    }

    @Test
    void idleCheckWaitingForTheLockDuringAStopDoesNotAddAChainAfterTheRestart() throws Exception {
        producer = startProducer("?idleTimeout=" + IDLE_TIMEOUT);
        ManualExecutor first = producer.installManualExecutor();
        assertNull(send().getException());
        ManualCheck firstCheck = first.pending().get(0);

        ReentrantLock lock = producer.serviceLock();
        AtomicReference<Throwable> checkFailure = new AtomicReference<>();
        Thread checkThread = new Thread(() -> {
            try {
                first.fire(firstCheck);
            } catch (Throwable t) {
                checkFailure.set(t);
            }
        }, "idle-check");
        ManualExecutor second;
        lock.lock();
        try {
            // the executor of the first start took the check, which waits for the lock while the producer stops,
            // starts again and sends on a new connection
            checkThread.start();
            await().atMost(10, TimeUnit.SECONDS).until(() -> lock.hasQueuedThread(checkThread));
            producer.stop();
            producer.start();
            second = producer.installManualExecutor();
            assertNull(send().getException());
        } finally {
            lock.unlock();
        }
        checkThread.join(10000);

        assertFalse(checkThread.isAlive());
        assertNull(checkFailure.get());
        assertEquals(1, second.pending().size(), "pending idle checks after the restart");
    }

    @Test
    void processWaitingForTheLockWhileTheProducerStopsOpensNoConnection() throws Exception {
        producer = startProducer("?idleTimeout=" + IDLE_TIMEOUT);
        producer.installManualExecutor();
        processWaitingDuringStopIsRejected();

        // after a start, the next message gets a new connection with its idle check
        producer.start();
        ManualExecutor executor = producer.installManualExecutor();
        assertNull(send().getException());
        assertFalse(producer.socket.isClosed());
        assertEquals(1, executor.pending().size());
    }

    @Test
    void processWaitingForTheLockWhileTheProducerStopsOpensNoConnectionWithoutIdleTimeout() throws Exception {
        producer = startProducer("");
        processWaitingDuringStopIsRejected();
    }

    private void processWaitingDuringStopIsRejected() throws Exception {
        assertNull(send().getException());
        Socket first = producer.socket;

        ReentrantLock lock = producer.serviceLock();
        AtomicReference<Exchange> sent = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread sendThread = new Thread(() -> {
            try {
                sent.set(send());
            } catch (Throwable t) {
                failure.set(t);
            }
        }, "send");
        lock.lock();
        try {
            // the route thread called process() and waits for the lock while the producer stops
            sendThread.start();
            await().atMost(10, TimeUnit.SECONDS).until(() -> lock.hasQueuedThread(sendThread));
            producer.stop();
        } finally {
            lock.unlock();
        }
        sendThread.join(10000);

        assertFalse(sendThread.isAlive());
        assertNull(failure.get(), "process() does not throw");
        assertInstanceOf(RejectedExecutionException.class, sent.get().getException());
        assertSame(first, producer.socket, "no new connection");
        assertTrue(producer.socket.isClosed());
    }

    private TestProducer startProducer(String options) {
        MllpEndpoint endpoint = context.getEndpoint(
                "mllp://" + mllpServer.getListenHost() + ":" + mllpServer.getListenPort() + options, MllpEndpoint.class);
        TestProducer testProducer = new TestProducer(endpoint);
        testProducer.start();
        return testProducer;
    }

    private Exchange send() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody(Hl7TestMessageGenerator.generateMessage());
        producer.process(exchange);
        return exchange;
    }

    static class TestProducer extends MllpTcpClientProducer {
        TestProducer(MllpEndpoint endpoint) {
            super(endpoint);
        }

        ReentrantLock serviceLock() {
            return (ReentrantLock) lock;
        }

        ManualExecutor installManualExecutor() {
            idleTimeoutExecutor.shutdownNow();
            ManualExecutor executor = new ManualExecutor();
            idleTimeoutExecutor = executor;
            return executor;
        }
    }

    /**
     * Keeps the scheduled checks until the test runs them. Like the ScheduledThreadPoolExecutor of
     * Executors.newSingleThreadScheduledExecutor: a check that has started runs to its end when it is cancelled; after
     * shutdown() the checks that are not cancelled still run (executeExistingDelayedTasksAfterShutdown is true by
     * default); after shutdownNow() none of the checks that did not start runs.
     */
    static class ManualExecutor extends AbstractExecutorService implements ScheduledExecutorService {
        final List<ManualCheck> scheduled = new CopyOnWriteArrayList<>();
        volatile boolean shutdown;
        volatile boolean shutdownNow;

        List<ManualCheck> pending() {
            List<ManualCheck> answer = new ArrayList<>();
            for (ManualCheck check : scheduled) {
                if (!check.cancelled && !check.started && !shutdownNow) {
                    answer.add(check);
                }
            }
            return answer;
        }

        List<ManualCheck> runnableAfterShutdown() {
            return shutdown ? pending() : List.of();
        }

        void fire(ManualCheck check) {
            check.started = true;
            try {
                check.command.run();
            } finally {
                check.done = true;
            }
        }

        @Override
        public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
            if (shutdown) {
                throw new RejectedExecutionException("shut down");
            }
            ManualCheck check = new ManualCheck(command, unit.toMillis(delay));
            scheduled.add(check);
            return check;
        }

        @Override
        public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void execute(Runnable command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            List<Runnable> notStarted = new ArrayList<>();
            for (ManualCheck check : pending()) {
                notStarted.add(check.command);
            }
            shutdown = true;
            shutdownNow = true;
            return notStarted;
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return shutdown;
        }
    }

    static class ManualCheck implements ScheduledFuture<Object> {
        final Runnable command;
        final long delayMillis;
        volatile boolean started;
        volatile boolean cancelled;
        volatile boolean done;

        ManualCheck(Runnable command, long delayMillis) {
            this.command = command;
            this.delayMillis = delayMillis;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            // as FutureTask: a check that is running is marked cancelled, and runs to its end
            if (cancelled || done) {
                return false;
            }
            cancelled = true;
            return true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public boolean isDone() {
            return cancelled || done;
        }

        @Override
        public Object get() {
            return null;
        }

        @Override
        public Object get(long timeout, TimeUnit unit) {
            return null;
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return unit.convert(delayMillis, TimeUnit.MILLISECONDS);
        }

        @Override
        public int compareTo(Delayed other) {
            return Long.compare(getDelay(TimeUnit.MILLISECONDS), other.getDelay(TimeUnit.MILLISECONDS));
        }
    }
}
