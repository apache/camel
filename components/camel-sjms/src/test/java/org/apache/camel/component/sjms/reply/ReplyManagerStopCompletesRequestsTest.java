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
package org.apache.camel.component.sjms.reply;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.TimeoutMap;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.ThreadPoolProfile;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.util.concurrent.ThreadPoolRejectedPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every request registered with a reply manager completes exactly once, also when the reply manager stops while a
 * producer registers it or moves it to its JMSMessageID, when the stop drops the queued timeout tasks, when the
 * executor rejects a timeout task, and when a timeout task reaches the executor after the stop has shut it down. The
 * reply manager is set up as SjmsProducer does it, without a broker: these paths do not involve the listener container.
 */
class ReplyManagerStopCompletesRequestsTest {

    private final Map<String, AtomicInteger> done = new ConcurrentHashMap<>();
    private CamelContext context;

    @BeforeEach
    void startContext() {
        context = new DefaultCamelContext();
        context.start();
    }

    @AfterEach
    void stopContext() {
        context.stop();
    }

    private QueueReplyManager newReplyManager(String name, ExecutorService onTimeout) {
        QueueReplyManager replyManager = new QueueReplyManager(context);
        ScheduledExecutorService checker = context.getExecutorServiceManager()
                .newSingleThreadScheduledExecutor(replyManager, "JmsReplyManagerTimeoutChecker[" + name + "]");
        ExecutorService executor = onTimeout != null
                ? onTimeout
                : context.getExecutorServiceManager().newThreadPool(replyManager, "JmsReplyManagerOnTimeout[" + name + "]",
                        0, 1);
        replyManager.setScheduledExecutorService(checker);
        replyManager.setOnTimeoutExecutorService(executor);
        // what ReplyManagerSupport.doStart does for the correlation map (the listener container needs a broker)
        replyManager.correlation = new CorrelationTimeoutMap(checker, 50, executor);
        replyManager.correlation.start();
        return replyManager;
    }

    private AsyncCallback callback(String name, Runnable onDone) {
        AtomicInteger counter = new AtomicInteger();
        done.put(name, counter);
        return doneSync -> {
            counter.incrementAndGet();
            if (onDone != null) {
                onDone.run();
            }
        };
    }

    private void register(QueueReplyManager replyManager, String correlationId, long timeout, Runnable onDone) {
        replyManager.registerReply(replyManager, new DefaultExchange(context), callback(correlationId, onDone), null,
                correlationId, timeout);
    }

    private int done(String name) {
        return done.get(name).get();
    }

    private static void awaitLatch(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void awaitIgnoringInterrupts(CountDownLatch latch) {
        // as a blocking call that does not react to an interrupt (such as a socket read) would
        boolean interrupted = false;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        try {
            while (latch.getCount() > 0 && System.nanoTime() < deadline) {
                try {
                    latch.await(100, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Test
    void registerAfterStopIsRefused() throws Exception {
        QueueReplyManager replyManager = newReplyManager("after", null);
        replyManager.doStop();

        // the producer completes the exchange with this exception (the request is not sent)
        assertThrows(RejectedExecutionException.class, () -> register(replyManager, "late", 200, null));
        assertEquals(0, replyManager.correlation.size());
        assertEquals(0, done("late"));
    }

    @Test
    void registerWhileStopDrainsIsRefused() throws Exception {
        QueueReplyManager replyManager = newReplyManager("drain", null);
        CountDownLatch draining = new CountDownLatch(1);
        CountDownLatch registered = new CountDownLatch(1);
        replyManager.correlation.addListener((type, key, value) -> {
            if (type == TimeoutMap.Listener.Type.Evict && key.equals("pending")) {
                // the stop has drained the map and is about to clear it
                draining.countDown();
                awaitLatch(registered);
            }
        });
        register(replyManager, "pending", 60000, null);

        AtomicReference<Exception> refused = new AtomicReference<>();
        Thread producer = new Thread(() -> {
            awaitLatch(draining);
            try {
                register(replyManager, "in-flight", 200, null);
            } catch (Exception e) {
                refused.set(e);
            } finally {
                registered.countDown();
            }
        }, "producer");
        producer.start();

        replyManager.doStop();
        producer.join(10000);

        // the request registered during the stop is refused, so the producer completes its exchange; before, it was
        // cleared from the map with no event and never completed
        assertInstanceOf(RejectedExecutionException.class, refused.get());
        assertEquals(0, done("in-flight"));
        assertEquals(0, replyManager.correlation.size());
        await().atMost(5, TimeUnit.SECONDS).until(() -> done("pending") == 1);
    }

    @Test
    void movedToMessageIdAfterTheDrainIsCompleted() throws Exception {
        QueueReplyManager replyManager = newReplyManager("msgid", null);
        CountDownLatch removed = new CountDownLatch(1);
        CountDownLatch stopped = new CountDownLatch(1);
        replyManager.correlation.addListener((type, key, value) -> {
            if (type == TimeoutMap.Listener.Type.Remove && key.equals("provisional")) {
                // the producer is in updateCorrelationId, between the remove and the put
                removed.countDown();
                awaitLatch(stopped);
            }
        });
        register(replyManager, "provisional", 60000, null);

        Thread producer = new Thread(
                () -> replyManager.updateCorrelationId("provisional", "ID:broker-1", 60000), "producer");
        producer.start();
        awaitLatch(removed);
        replyManager.doStop();
        stopped.countDown();
        producer.join(10000);

        // the handler was in no map during the drain: it is completed when it is put into the stopped map
        assertEquals(1, done("provisional"));
        assertEquals(0, replyManager.correlation.size());
    }

    @Test
    void timeoutTasksDroppedByForcedShutdownAreCompleted() throws Exception {
        context.getExecutorServiceManager().setShutdownAwaitTermination(300);
        QueueReplyManager replyManager = newReplyManager("forced", null);
        // the first exchange that continues routing on the single timeout thread is slow, so the other one is still
        // queued when the forced shutdown drops the queue
        AtomicBoolean first = new AtomicBoolean();
        CountDownLatch never = new CountDownLatch(1);
        Runnable slowOnce = () -> {
            if (first.compareAndSet(false, true)) {
                awaitLatch(never);
            }
        };
        register(replyManager, "a", 60000, slowOnce);
        register(replyManager, "b", 60000, slowOnce);

        replyManager.doStop();

        assertEquals(1, done("a"));
        assertEquals(1, done("b"));
    }

    @Test
    void rejectedTimeoutTaskRunsInTheCallingThread() throws Exception {
        // as camel.threadpool.rejectedPolicy=Abort does, with a small queue
        ThreadPoolProfile profile = new ThreadPoolProfile("abort");
        profile.setPoolSize(1);
        profile.setMaxPoolSize(1);
        profile.setMaxQueueSize(1);
        profile.setRejectedPolicy(ThreadPoolRejectedPolicy.Abort);
        ExecutorService executor = context.getExecutorServiceManager().newThreadPool(this, "OnTimeoutAbort", profile);
        QueueReplyManager replyManager = newReplyManager("abort", executor);
        AtomicInteger evicted = new AtomicInteger();
        // added after the map's own listener, so it counts the timeout tasks already handed to the executor
        replyManager.correlation.addListener((type, key, value) -> {
            if (type == TimeoutMap.Listener.Type.Evict) {
                evicted.incrementAndGet();
            }
        });
        // the first timeout task holds the only thread, the second fills the queue, the third is rejected. Only the
        // first task to run on the executor thread blocks: a task that runs in the timeout checker thread (the
        // rejected one) must not block, or the checker could not evict the remaining entries
        AtomicBoolean first = new AtomicBoolean();
        CountDownLatch release = new CountDownLatch(1);
        Runnable blockOnce = () -> {
            if (Thread.currentThread().getName().contains("OnTimeoutAbort") && first.compareAndSet(false, true)) {
                awaitLatch(release);
            }
        };
        register(replyManager, "t1", 100, blockOnce);
        register(replyManager, "t2", 100, blockOnce);
        register(replyManager, "t3", 100, blockOnce);

        await().atMost(10, TimeUnit.SECONDS).until(() -> evicted.get() == 3);
        release.countDown();

        await().atMost(10, TimeUnit.SECONDS)
                .until(() -> done("t1") + done("t2") + done("t3") == 3);
        assertEquals(1, done("t1"));
        assertEquals(1, done("t2"));
        assertEquals(1, done("t3"));
        replyManager.doStop();
    }

    @Test
    void timeoutHandedToTheExecutorAfterTheStopIsCompleted() throws Exception {
        context.getExecutorServiceManager().setShutdownAwaitTermination(200);
        // the default rejected policy (CallerRuns) with one thread and a queue of one
        ThreadPoolProfile profile = new ThreadPoolProfile("callerRuns");
        profile.setPoolSize(1);
        profile.setMaxPoolSize(1);
        profile.setMaxQueueSize(1);
        profile.setRejectedPolicy(ThreadPoolRejectedPolicy.CallerRuns);
        ExecutorService executor = context.getExecutorServiceManager().newThreadPool(this, "OnTimeoutCallerRuns", profile);
        QueueReplyManager replyManager = newReplyManager("late", executor);

        // four requests time out in the same check. The first timeout task holds the executor thread, the second is
        // queued, the third is run by the timeout checker thread (CallerRuns) and its exchange continues routing there
        // slowly, so the checker hands the fourth to the executor only after the stop has shut it down
        AtomicBoolean firstOnExecutor = new AtomicBoolean();
        CountDownLatch inChecker = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Runnable slowRouting = () -> {
            String thread = Thread.currentThread().getName();
            if (thread.contains("JmsReplyManagerTimeoutChecker")) {
                inChecker.countDown();
                awaitIgnoringInterrupts(release);
            } else if (thread.contains("OnTimeoutCallerRuns") && firstOnExecutor.compareAndSet(false, true)) {
                awaitIgnoringInterrupts(release);
            }
        };
        for (String id : List.of("t1", "t2", "t3", "t4")) {
            register(replyManager, id, 100, slowRouting);
        }
        assertTrue(inChecker.await(10, TimeUnit.SECONDS));

        // the stop waits for the checker and the executor, gives up, and completes the dropped queued task
        replyManager.doStop();
        assertTrue(executor.isShutdown());

        // the routing in the checker thread ends, and the checker hands the last timeout task to the shut-down executor
        release.countDown();
        await().atMost(20, TimeUnit.SECONDS)
                .until(() -> done("t1") + done("t2") + done("t3") + done("t4") == 4);
        for (String id : List.of("t1", "t2", "t3", "t4")) {
            assertEquals(1, done(id), id);
        }
    }
}
