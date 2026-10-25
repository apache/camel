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

import java.time.Instant;
import java.util.List;

import org.apache.camel.support.MonotonicClock;
import org.apache.camel.util.StopWatch;
import org.apache.camel.util.TimeSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A run installs its virtual clock as the {@code TimeSource} provider of Camel core and puts the previous provider back
 * when it closes: the core clocks read virtual time without any rewriting.
 */
@DstInstrument("org.apache.camel.test.dst.sample")
class DstTimeSourceTest {

    @Test
    void aRunInstallsTheVirtualClockUntilItIsClosed() {
        assertTrue(TimeSource.isDefault());
        Dst dst = new Dst(1);
        try {
            assertFalse(TimeSource.isDefault());
            assertEquals(dst.currentTimeMillis(), TimeSource.currentTimeMillis());
            assertEquals(Instant.ofEpochMilli(dst.currentTimeMillis()), TimeSource.instant());
            long nanos = TimeSource.nanoTime();
            dst.runFor(1_500);
            assertEquals(1_500_000_000L, TimeSource.nanoTime() - nanos);
        } finally {
            dst.close();
        }
        assertTrue(TimeSource.isDefault(), "the system clock is back after the run");
        long real = System.currentTimeMillis();
        assertTrue(Math.abs(TimeSource.currentTimeMillis() - real) < 60_000);
    }

    @Test
    void coreClocksReadVirtualTimeWithoutRewriting() {
        assertFalse(DstInstrumentation.alsoRewritten().contains("org.apache.camel.util.StopWatch"));
        Dst dst = new Dst(1);
        try {
            StopWatch[] watch = new StopWatch[1];
            MonotonicClock[] clock = new MonotonicClock[1];
            long[] taken = new long[2];
            DstExecutor ex = dst.executor("ex", 1);
            ex.schedule("start", 1_000, () -> {
                watch[0] = new StopWatch();
                clock[0] = new MonotonicClock();
            });
            ex.schedule("read", 3_601_000, () -> {
                taken[0] = watch[0].taken();
                taken[1] = clock[0].elapsed();
            });
            long start = System.nanoTime();
            dst.runUntilQuiet();
            assertEquals(3_600_000L, taken[0]);
            assertEquals(3_600_000L, taken[1]);
            assertEquals(Dst.EPOCH_MILLIS + 1_000, clock[0].getCreated());
            assertTrue(System.nanoTime() - start < 5_000_000_000L, "an hour of virtual time takes no real time");
        } finally {
            dst.close();
        }
        // outside a run the core clocks read the real clock
        assertTrue(new StopWatch().taken() < 60_000);
    }

    @Test
    void aSleepThroughTheTimeSourceIsAVirtualWaitWithItsCallSite() {
        Dst dst = new Dst(1);
        try {
            long[] woke = new long[1];
            dst.executor("sleeper", 1).execute(() -> {
                try {
                    TimeSource.sleep(7_000);
                } catch (InterruptedException e) {
                    throw new IllegalStateException(e);
                }
                woke[0] = dst.now();
            });
            dst.executor("other", 1).schedule("meanwhile", 3_000, () -> dst.trace("other task at " + dst.now()));
            long start = System.nanoTime();
            dst.runUntilQuiet();
            assertEquals(7_000, woke[0]);
            assertTrue(dst.traceLines().stream()
                    .anyMatch(l -> l.contains("block 7000ms sleep at DstTimeSourceTest.lambda$")),
                    String.join("\n", dst.traceLines()));
            assertTrue(dst.traceLines().stream().anyMatch(l -> l.contains("other task at 3000")));
            assertTrue(System.nanoTime() - start < 5_000_000_000L);
        } finally {
            dst.close();
        }
    }

    @Test
    void aSleepThroughTheTimeSourceKeepsTheSemanticsOfThreadSleep() {
        Dst dst = new Dst(1);
        try {
            assertThrows(IllegalArgumentException.class, () -> TimeSource.sleep(-1));
            Thread.currentThread().interrupt();
            assertThrows(InterruptedException.class, () -> TimeSource.sleep(1_000));
            assertFalse(Thread.currentThread().isInterrupted(), "the interrupted status is cleared");
            assertEquals(0, dst.now(), "no virtual time passed");
        } finally {
            Thread.interrupted();
            dst.close();
        }
    }

    @Test
    void aCoreClockReadOffTheLoopIsAnEscape() throws Exception {
        Dst dst = new Dst(1);
        try {
            dst.strict(true, List.of());
            Thread outside = new Thread(StopWatch::new, "outside-watch");
            outside.start();
            outside.join();
            assertTrue(dst.escape().contains("virtual clock read from a thread outside the harness: outside-watch"),
                    dst.escape());
            assertThrows(DstViolation.class, dst::runUntilQuiet);
        } finally {
            dst.close();
        }
    }

    @Test
    void theProviderInstalledBeforeTheRunIsPutBack() {
        TimeSource.Provider fixed = new TimeSource.Provider() {
            @Override
            public long currentTimeMillis() {
                return 42;
            }

            @Override
            public long nanoTime() {
                return 42;
            }

            @Override
            public void sleep(long millis) {
                // no sleep
            }
        };
        TimeSource.Provider previous = TimeSource.install(fixed);
        try {
            Dst dst = new Dst(1);
            try {
                assertEquals(dst.currentTimeMillis(), TimeSource.currentTimeMillis());
            } finally {
                dst.close();
            }
            assertEquals(42, TimeSource.currentTimeMillis());
        } finally {
            assertSame(fixed, TimeSource.install(previous));
        }
        assertTrue(TimeSource.isDefault());
    }
}
