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
package org.apache.camel.util;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The provider of {@link TimeSource} is JVM-wide: the tests that install one run isolated from the other tests.
 */
@Isolated
class TimeSourceTest {

    /** A clock that only moves when asked to, and records the sleeps. */
    private static final class ManualProvider implements TimeSource.Provider {
        long millis = 1_000_000L;
        final List<Long> sleeps = new ArrayList<>();

        @Override
        public long currentTimeMillis() {
            return millis;
        }

        @Override
        public long nanoTime() {
            return millis * 1_000_000L;
        }

        @Override
        public void sleep(long millis) {
            sleeps.add(millis);
            this.millis += millis;
        }
    }

    @AfterEach
    void restoreDefault() {
        TimeSource.reset();
    }

    @Test
    void defaultProviderReadsTheSystemClock() {
        assertTrue(TimeSource.isDefault());
        long before = System.currentTimeMillis();
        long millis = TimeSource.currentTimeMillis();
        long after = System.currentTimeMillis();
        assertTrue(before <= millis && millis <= after, before + " <= " + millis + " <= " + after);

        long nanosBefore = System.nanoTime();
        long nanos = TimeSource.nanoTime();
        assertTrue(nanos - nanosBefore >= 0);
        assertTrue(System.nanoTime() - nanos >= 0);

        Instant instantBefore = Instant.now();
        Instant instant = TimeSource.instant();
        assertFalse(instant.isBefore(instantBefore));
        assertFalse(instant.isAfter(Instant.now()));
    }

    @Test
    void defaultSleepReturnsForZero() throws InterruptedException {
        StopWatch watch = new StopWatch();
        TimeSource.sleep(0);
        assertTrue(watch.taken() < 1_000, "took " + watch.taken());
    }

    @Test
    void defaultSleepRejectsANegativeValueAsThreadSleep() {
        assertThrows(IllegalArgumentException.class, () -> TimeSource.sleep(-1));
    }

    @Test
    void defaultSleepOfAnInterruptedThreadThrowsAndClearsTheFlag() {
        Thread.currentThread().interrupt();
        try {
            assertThrows(InterruptedException.class, () -> TimeSource.sleep(10_000));
            assertFalse(Thread.currentThread().isInterrupted(), "the interrupted status is cleared, as Thread.sleep");
        } finally {
            // never leave the flag set for the next test
            Thread.interrupted();
        }
    }

    @Test
    void installedProviderIsUsedUntilReset() throws InterruptedException {
        ManualProvider manual = new ManualProvider();
        TimeSource.Provider previous = TimeSource.install(manual);
        assertNotSame(manual, previous);
        assertFalse(TimeSource.isDefault());

        assertEquals(1_000_000L, TimeSource.currentTimeMillis());
        assertEquals(1_000_000L * 1_000_000L, TimeSource.nanoTime());
        assertEquals(Instant.ofEpochMilli(1_000_000L), TimeSource.instant());

        TimeSource.sleep(2_500);
        assertEquals(List.of(2_500L), manual.sleeps);
        assertEquals(1_002_500L, TimeSource.currentTimeMillis());

        TimeSource.reset();
        assertTrue(TimeSource.isDefault());
        TimeSource.sleep(0);
        assertEquals(List.of(2_500L), manual.sleeps, "the default provider is back");
    }

    @Test
    void installReturnsThePreviousProviderToPutBack() {
        ManualProvider first = new ManualProvider();
        ManualProvider second = new ManualProvider();
        second.millis = 42;
        TimeSource.Provider original = TimeSource.install(first);
        assertSame(first, TimeSource.install(second));
        assertEquals(42, TimeSource.currentTimeMillis());
        assertSame(second, TimeSource.install(first));
        assertEquals(1_000_000L, TimeSource.currentTimeMillis());
        assertSame(first, TimeSource.install(original));
        assertTrue(TimeSource.isDefault());
    }

    @Test
    void installRejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> TimeSource.install(null));
        assertTrue(TimeSource.isDefault());
    }

    @Test
    void stopWatchReadsTheInstalledProvider() {
        ManualProvider manual = new ManualProvider();
        TimeSource.install(manual);
        StopWatch watch = new StopWatch();
        manual.millis += 3_600_000L;
        assertEquals(3_600_000L, watch.taken());
        assertEquals(3_600_000L, watch.takenAndRestart());
        manual.millis += 5;
        assertEquals(5, watch.taken());
    }
}
