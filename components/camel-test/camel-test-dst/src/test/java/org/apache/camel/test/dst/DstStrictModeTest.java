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

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.camel.test.dst.sample.SampleClock;
import org.apache.camel.test.dst.sample.SampleEscapes;
import org.apache.camel.test.dst.sample.SampleSleeper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Strict mode: reaching an escape point the harness does not control fails the seed, also when the code under test
 * swallows the violation.
 */
@DstInstrument("org.apache.camel.test.dst.sample")
class DstStrictModeTest {

    @Test
    void reachingAnEscapeFailsTheSeed() {
        Dst dst = new Dst(1);
        try {
            dst.strict(true, List.of());
            DstViolation e = assertThrows(DstViolation.class, SampleEscapes::ownThread);
            assertEquals("escape", e.getInvariant());
            assertTrue(e.getMessage().contains("SampleEscapes.ownThread"), e.getMessage());
            assertTrue(e.getMessage().contains("new Thread"), e.getMessage());
        } finally {
            dst.close();
        }
    }

    @Test
    void aSwallowedEscapeStillFailsTheSeed() {
        Dst dst = new Dst(1);
        try {
            dst.strict(true, List.of());
            DstExecutor ex = dst.executor("ex", 1);
            // the task fails with the violation, which nobody looks at
            ex.execute(SampleEscapes::ownThread);
            ex.schedule("later", 1_000, () -> {
            });
            DstViolation e = assertThrows(DstViolation.class, dst::runUntilQuiet);
            assertEquals("escape", e.getInvariant());
            assertNotNull(dst.pendingFailure(), "the extension fails the seed at the end as well");
        } finally {
            dst.close();
        }
    }

    @Test
    void escapesFromAnotherThreadAreReportedOnTheLoop() throws Exception {
        Dst dst = new Dst(1);
        try {
            dst.strict(true, List.of());
            DstExecutor ex = dst.executor("ex", 1);
            // a thread outside the harness submits a task
            Thread outside = new Thread(() -> ex.execute(() -> {
            }), "outside");
            outside.start();
            outside.join();
            assertTrue(dst.escape().contains("task submitted from a thread outside the harness"), dst.escape());
            assertThrows(DstViolation.class, dst::runUntilQuiet);
        } finally {
            dst.close();
        }
    }

    @Test
    void allowedEscapesAndNonStrictRunsOnlyRecord() {
        Dst dst = new Dst(1);
        try {
            dst.strict(true, List.of("SampleEscapes.ownThread"));
            SampleEscapes.ownThread();
            assertNull(dst.escape());
            dst.strict(false, List.of());
            SampleEscapes.commonPool(() -> {
            }).join();
            assertNull(dst.escape());
            assertTrue(DstEscapes.runtime().stream().anyMatch(l -> l.contains("SampleEscapes.commonPool")),
                    DstEscapes.runtime().toString());
        } finally {
            dst.close();
        }
    }

    private static final String CLOCK_READ = "virtual clock read from a thread outside the harness: outside-clock";
    private static final String SLEEP = "real sleep on a thread outside the harness: outside-sleep";

    /** Code under test reads the clock and sleeps on threads the harness does not run (a Reactor or Netty thread). */
    private static long outside() throws Exception {
        AtomicLong read = new AtomicLong();
        Thread clock = new Thread(() -> read.set(SampleClock.now()), "outside-clock");
        Thread sleep = new Thread(() -> {
            try {
                SampleSleeper.sleepMillis(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "outside-sleep");
        clock.start();
        clock.join();
        sleep.start();
        sleep.join();
        return read.get();
    }

    @Test
    void aClockReadOffTheLoopFailsTheSeed() throws Exception {
        Dst dst = new Dst(1);
        try {
            dst.strict(true, List.of());
            AtomicLong read = new AtomicLong();
            Thread clock = new Thread(() -> read.set(SampleClock.now()), "outside-clock");
            clock.start();
            clock.join();
            assertEquals(dst.currentTimeMillis(), read.get(), "the read still returns the virtual time");
            assertTrue(dst.escape().contains(CLOCK_READ), dst.escape());
            DstViolation e = assertThrows(DstViolation.class, dst::runUntilQuiet);
            assertEquals("escape", e.getInvariant());
            assertNotNull(dst.pendingFailure(), "the extension fails the seed at the end as well");
        } finally {
            dst.close();
        }
    }

    @Test
    void aSleepOffTheLoopFailsTheSeed() throws Exception {
        Dst dst = new Dst(1);
        try {
            dst.strict(true, List.of());
            Thread sleep = new Thread(() -> {
                try {
                    SampleSleeper.sleepMillis(1);
                    SampleSleeper.sleepMillisNanos(1);
                    SampleSleeper.sleepSeconds(0);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "outside-sleep");
            sleep.start();
            sleep.join();
            assertTrue(dst.escape().contains(SLEEP), dst.escape());
            assertThrows(DstViolation.class, dst::runUntilQuiet);
        } finally {
            dst.close();
        }
    }

    @Test
    void allowedClockReadsAndSleepsOffTheLoopOnlyRecord() throws Exception {
        Dst dst = new Dst(1);
        try {
            dst.strict(true, List.of(CLOCK_READ, SLEEP));
            assertEquals(dst.currentTimeMillis(), outside());
            assertNull(dst.escape());
            dst.runUntilQuiet();
        } finally {
            dst.close();
        }
    }

    @Test
    void clockReadsAndSleepsOffTheLoopAreRecordedWhenNotStrict() throws Exception {
        Dst dst = new Dst(1);
        try {
            dst.strict(false, List.of());
            assertEquals(dst.currentTimeMillis(), outside());
            assertNull(dst.escape());
            dst.runUntilQuiet();
            List<String> runtime = DstEscapes.runtime();
            assertTrue(runtime.stream().anyMatch(l -> l.contains(CLOCK_READ)), runtime.toString());
            assertTrue(runtime.stream().anyMatch(l -> l.contains(SLEEP)), runtime.toString());
        } finally {
            dst.close();
        }
    }
}
