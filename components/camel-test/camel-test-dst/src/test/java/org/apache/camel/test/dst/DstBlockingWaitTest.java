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
package org.apache.camel.test.dst;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.apache.camel.test.dst.sample.SampleWaits;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Blocking waits of the code under test on something that is not a task of the run: they run the loop until the wait
 * can end, or fail the seed at once when nothing can end it. Waits in code that is not rewritten are caught by the
 * watchdog.
 */
@DstInstrument("org.apache.camel.test.dst.sample")
class DstBlockingWaitTest {

    private static final long REAL_LIMIT = 5_000_000_000L;

    @Test
    void futuresCompletedByTasksOfTheRun() {
        Dst dst = new Dst(1);
        try {
            CompletableFuture<String> a = new CompletableFuture<>();
            CompletableFuture<String> b = new CompletableFuture<>();
            FutureTask<String> c = new FutureTask<>(() -> "c");
            DstExecutor server = dst.executor("server", 1);
            server.schedule("complete a", 1_000, () -> a.complete("a"));
            server.schedule("complete b", 2_000, () -> b.complete("b"));
            server.schedule("run c", 3_000, c);
            List<String> seen = new ArrayList<>();
            dst.executor("client", 1).execute(() -> {
                try {
                    seen.add(SampleWaits.get(a) + "@" + dst.now());
                    seen.add(SampleWaits.join(b) + "@" + dst.now());
                    seen.add(SampleWaits.futureGet(c) + "@" + dst.now());
                } catch (Exception e) {
                    seen.add("failed " + e);
                }
            });
            long start = System.nanoTime();
            dst.runUntilQuiet();
            assertEquals(List.of("a@1000", "b@2000", "c@3000"), seen);
            assertTrue(System.nanoTime() - start < REAL_LIMIT);
        } finally {
            dst.close();
        }
    }

    @Test
    void latchSemaphoreAndQueues() {
        Dst dst = new Dst(1);
        try {
            CountDownLatch latch = new CountDownLatch(2);
            Semaphore semaphore = new Semaphore(0);
            LinkedBlockingQueue<String> queue = new LinkedBlockingQueue<>();
            DstExecutor server = dst.executor("server", 1);
            server.schedule("count down", 1_000, latch::countDown);
            server.schedule("count down", 2_000, latch::countDown);
            server.schedule("release", 3_000, () -> semaphore.release());
            server.schedule("offer", 4_000, () -> queue.offer("x"));
            List<String> seen = new ArrayList<>();
            dst.executor("client", 1).execute(() -> {
                try {
                    seen.add("timed await " + SampleWaits.await(latch, 500) + "@" + dst.now());
                    SampleWaits.await(latch);
                    seen.add("await@" + dst.now());
                    SampleWaits.acquire(semaphore);
                    seen.add("acquire@" + dst.now());
                    seen.add("take " + SampleWaits.take(queue) + "@" + dst.now());
                    seen.add("poll " + SampleWaits.poll(queue, 2_000) + "@" + dst.now());
                } catch (Exception e) {
                    seen.add("failed " + e);
                }
            });
            dst.runUntilQuiet();
            assertEquals(List.of("timed await false@500", "await@2000", "acquire@3000", "take x@4000", "poll null@6000"),
                    seen);
        } finally {
            dst.close();
        }
    }

    @Test
    void delayQueueTakeWaitsForTheHeadInVirtualTime() throws Exception {
        Dst dst = new Dst(1);
        try {
            DelayQueue<SampleWaits.Slot> queue = new DelayQueue<>();
            queue.add(new SampleWaits.Slot(3_000));
            long start = System.nanoTime();
            assertTrue(SampleWaits.take(queue) != null);
            assertEquals(3_000, dst.now());
            assertTrue(System.nanoTime() - start < REAL_LIMIT);
        } finally {
            dst.close();
        }
    }

    @Test
    void delayQueueWithRealTimeDelaysFailsFast() {
        Dst dst = new Dst(1);
        try {
            DelayQueue<RealSlot> queue = new DelayQueue<>();
            queue.add(new RealSlot(60_000));
            DstViolation e = assertThrows(DstViolation.class, () -> SampleWaits.take(queue));
            assertTrue(e.getMessage().contains("alsoRewrite"), e.getMessage());
        } finally {
            dst.close();
        }
    }

    @Test
    void aWaitThatNoTaskCanEndFailsTheSeedAtOnce() {
        Dst dst = new Dst(1);
        try {
            dst.executor("ex", 1).schedule("unrelated", 1_000, () -> {
            });
            long start = System.nanoTime();
            DstViolation e = assertThrows(DstViolation.class, () -> SampleWaits.join(new CompletableFuture<>()));
            assertEquals("blocking wait", e.getInvariant());
            assertTrue(e.getMessage().contains("CompletableFuture.join at SampleWaits.join"), e.getMessage());
            assertTrue(System.nanoTime() - start < REAL_LIMIT);
            CountDownLatch latch = new CountDownLatch(1);
            e = assertThrows(DstViolation.class, () -> SampleWaits.await(latch));
            assertTrue(e.getMessage().contains("CountDownLatch.await at SampleWaits.await"), e.getMessage());
        } finally {
            dst.close();
        }
    }

    @Test
    void outsideARunTheWaitIsReal() throws Exception {
        assertEquals("x", SampleWaits.get(CompletableFuture.completedFuture("x")));
        assertFalse(SampleWaits.await(new CountDownLatch(1), 10));
        assertNull(SampleWaits.poll(new LinkedBlockingQueue<>(), 10));
    }

    @Test
    void theWatchdogFailsAWaitInCodeThatIsNotRewritten() {
        Dst dst = new Dst(1);
        try {
            dst.watchdog(300);
            long start = System.nanoTime();
            // this test class is not rewritten: a real wait that nothing ends
            assertThrows(InterruptedException.class, () -> new CountDownLatch(1).await());
            assertTrue(System.nanoTime() - start < REAL_LIMIT);
            assertTrue(dst.stall() != null && dst.stall().contains("CountDownLatch.await"), dst.stall());
            // the next step of the run fails the seed with the report
            DstViolation e = assertThrows(DstViolation.class, dst::runUntilQuiet);
            assertEquals("blocked loop thread", e.getInvariant());
        } finally {
            dst.close();
        }
    }

    /** A delayed element whose delay reads the real clock (this class is not rewritten). */
    private static final class RealSlot implements Delayed {
        private final long due;

        RealSlot(long delayMillis) {
            this.due = System.currentTimeMillis() + delayMillis;
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return unit.convert(due - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
        }

        @Override
        public int compareTo(Delayed o) {
            return Long.compare(getDelay(TimeUnit.MILLISECONDS), o.getDelay(TimeUnit.MILLISECONDS));
        }
    }
}
