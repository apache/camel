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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.camel.support.DefaultTimeoutMap;
import org.apache.camel.support.MonotonicClock;
import org.apache.camel.test.dst.sample.SampleClock;
import org.apache.camel.util.StopWatch;
import org.apache.camel.util.TimeSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A run can move its wall clock forward or backward apart from the monotonic clock: the wall clock reads of the code
 * under test and of Camel core (through {@code TimeSource}) see the jump, the monotonic clock and the due times of the
 * tasks do not, and a jump chosen by the seed replays exactly.
 */
@DstInstrument("org.apache.camel.test.dst.sample")
class DstWallClockJumpTest {

    @Test
    void aBackwardJumpMovesOnlyTheWallClock() {
        Dst dst = new Dst(1);
        try {
            long[] wall = new long[4];
            long[] mono = new long[2];
            StopWatch[] watch = new StopWatch[1];
            MonotonicClock[] clock = new MonotonicClock[1];
            DstExecutor ex = dst.executor("ex", 1);
            ex.schedule("before", 1_000, () -> {
                wall[0] = TimeSource.currentTimeMillis();
                mono[0] = TimeSource.nanoTime();
                watch[0] = new StopWatch();
                clock[0] = new MonotonicClock();
                dst.jumpWallClock(-3_600_000);
                wall[1] = TimeSource.currentTimeMillis();
            });
            ex.schedule("after", 11_000, () -> {
                wall[2] = TimeSource.currentTimeMillis();
                wall[3] = TimeSource.instant().toEpochMilli();
                mono[1] = TimeSource.nanoTime();
            });
            dst.runUntilQuiet();
            assertEquals(Dst.EPOCH_MILLIS + 1_000, wall[0]);
            assertEquals(Dst.EPOCH_MILLIS + 1_000 - 3_600_000, wall[1], "the wall clock went back an hour at once");
            assertEquals(Dst.EPOCH_MILLIS + 11_000 - 3_600_000, wall[2], "and moves on from there");
            assertEquals(wall[2], wall[3]);
            assertEquals(10_000_000_000L, mono[1] - mono[0], "the monotonic clock did not jump");
            assertEquals(11_000, dst.now(), "the task was due 10 s after the jump, as scheduled");
            assertEquals(10_000, watch[0].taken());
            assertEquals(10_000, clock[0].elapsed());
            assertEquals(Dst.EPOCH_MILLIS + 1_000, clock[0].getCreated(), "its created time is a wall clock time");
            assertEquals(-3_600_000, dst.wallClockOffset());
            assertEquals(dst.currentTimeMillis(), Dst.EPOCH_MILLIS + dst.now() + dst.wallClockOffset());
            assertTrue(dst.traceLines().stream().anyMatch(l -> l.contains("wall clock jump -3600000ms")),
                    String.join("\n", dst.traceLines()));
        } finally {
            dst.close();
        }
    }

    @Test
    void theRewrittenWallClockReadsSeeAForwardJump() {
        Dst dst = new Dst(1);
        try {
            List<List<Long>> seen = new ArrayList<>();
            long[] millis = new long[1];
            DstExecutor ex = dst.executor("ex", 1);
            ex.schedule("jump", 1_000, () -> dst.jumpWallClock(86_400_000));
            ex.schedule("read", 2_000, () -> {
                seen.add(SampleClock.javaTimeReads());
                millis[0] = SampleClock.now();
            });
            dst.runUntilQuiet();
            long expected = Dst.EPOCH_MILLIS + 2_000 + 86_400_000;
            assertEquals(expected, millis[0]);
            for (long read : seen.get(0)) {
                assertEquals(expected, read, seen.toString());
            }
        } finally {
            dst.close();
        }
    }

    @Test
    void anExpiringEntryUnderABackwardJump() throws Exception {
        // the example of the guide: an entry with a timeout of 30 s in a DefaultTimeoutMap, which reads the wall clock
        // (size() does not refresh the expiry of an entry, get() would)
        Dst dst = new Dst(1);
        try {
            DefaultTimeoutMap<String, String> map = new DefaultTimeoutMap<>(dst.executor("purge", 1), 1_000);
            map.start();
            map.put("order-1", "pending", 30_000);
            dst.runFor(10_000);
            dst.jumpWallClock(-3_600_000);
            dst.runFor(60_000);
            assertEquals(1, map.size(), "70 s after the put the entry is still there: it now expires an hour late");
            dst.runFor(3_600_000 - 70_000 + 30_000);
            assertEquals(1, map.size(), "still there when its expiry, an hour late, is reached");
            dst.runFor(1_000);
            assertEquals(0, map.size(), "evicted by the next purge, an hour late");
            map.stop();
        } finally {
            dst.close();
        }
    }

    @Test
    void anExpiringEntryUnderAForwardJump() throws Exception {
        Dst dst = new Dst(1);
        try {
            DefaultTimeoutMap<String, String> map = new DefaultTimeoutMap<>(dst.executor("purge", 1), 1_000);
            map.start();
            map.put("order-1", "pending", 30_000);
            dst.runFor(10_000);
            assertEquals(1, map.size(), "the purge at t=10 s ran before the jump");
            dst.jumpWallClock(3_600_000);
            dst.runFor(999);
            assertEquals(1, map.size());
            dst.runFor(1);
            assertEquals(0, map.size(), "evicted by the next purge after the jump, at t=11 s");
            map.stop();
        } finally {
            dst.close();
        }
    }

    @Test
    void theSeedChoosesWhenAndHowFarTheWallClockJumps() throws Exception {
        List<String> first = jumpRun(42);
        List<String> again = jumpRun(42);
        assertEquals(first, again, "the same seed makes the same jump at the same time");
        Set<String> differing = new HashSet<>();
        for (long seed = 1; seed <= 20; seed++) {
            List<String> run = jumpRun(seed);
            differing.add(run.get(0));
            long at = Long.parseLong(run.get(1));
            long delta = Long.parseLong(run.get(2));
            assertTrue(at >= 5_000 && at <= 65_000, "jump at " + at);
            assertTrue(delta >= -7_200_000 && delta <= 7_200_000, "jump of " + delta);
        }
        assertTrue(differing.size() > 15, "consecutive seeds draw different jumps: " + differing);
    }

    /** One run with a seeded jump; returns the trace hash, the time of the jump and its size. */
    private static List<String> jumpRun(long seed) throws Exception {
        Dst dst = new Dst(seed);
        try {
            DstTask<Long> jump = dst.jumpWallClockBetween(5_000, 65_000, -7_200_000, 7_200_000);
            dst.executor("tick", 1).scheduleAtFixedRate(() -> dst.trace("tick wall=" + dst.currentTimeMillis()),
                    0, 1_000, TimeUnit.MILLISECONDS);
            dst.runFor(70_000);
            assertTrue(jump.isDone());
            long delta = jump.get();
            assertEquals(delta, dst.wallClockOffset());
            assertEquals(Dst.EPOCH_MILLIS + dst.now() + delta, dst.currentTimeMillis());
            String at = dst.traceLines().stream()
                    .filter(l -> l.contains("run wall clock jump"))
                    .map(l -> l.substring(l.indexOf("t=") + 2, l.indexOf(' ', l.indexOf("t="))))
                    .findFirst().orElseThrow();
            return List.of(dst.traceHash(), at, String.valueOf(delta));
        } finally {
            dst.close();
        }
    }

    @Test
    void aDrawnDeltaOfZeroMakesNoJumpAndAOneValueRangeIsExact() throws Exception {
        Dst dst = new Dst(7);
        try {
            DstTask<Long> none = dst.jumpWallClockBetween(1_000, 1_000, 0, 0);
            DstTask<Long> exact = dst.jumpWallClockBetween(2_000, 2_000, -5_000, -5_000);
            dst.runFor(1_500);
            assertTrue(none.isDone());
            assertEquals(0L, none.get());
            assertEquals(0, dst.wallClockOffset(), "no jump");
            dst.runFor(1_000);
            assertEquals(-5_000L, exact.get());
            assertEquals(-5_000, dst.wallClockOffset());
            assertEquals(1, dst.traceLines().stream().filter(l -> l.contains("(wall clock offset")).count(),
                    String.join("\n", dst.traceLines()));
        } finally {
            dst.close();
        }
    }

    @Test
    void eachRunStartsWithoutAJump() {
        Dst dst = new Dst(1);
        try {
            dst.jumpWallClock(-60_000);
        } finally {
            dst.close();
        }
        Dst next = new Dst(1);
        try {
            assertEquals(0, next.wallClockOffset());
            assertEquals(Dst.EPOCH_MILLIS, next.currentTimeMillis());
            assertEquals(Dst.EPOCH_MILLIS, TimeSource.currentTimeMillis());
        } finally {
            next.close();
        }
        assertTrue(Math.abs(TimeSource.currentTimeMillis() - System.currentTimeMillis()) < 60_000,
                "the system clock is back after the run");
    }

    @Test
    void aJumpIsMadeOnTheLoopThreadOnly() throws Exception {
        Dst dst = new Dst(1);
        try {
            Throwable[] failed = new Throwable[1];
            Thread outside = new Thread(() -> {
                try {
                    dst.jumpWallClock(1_000);
                } catch (Throwable e) {
                    failed[0] = e;
                }
            }, "outside-jump");
            outside.start();
            outside.join();
            assertNotNull(failed[0]);
            assertInstanceOf(IllegalStateException.class, failed[0]);
            assertEquals(0, dst.wallClockOffset());
            assertThrows(IllegalArgumentException.class, () -> dst.jumpWallClockBetween(10, 5, 0, 1));
            assertThrows(IllegalArgumentException.class, () -> dst.jumpWallClockBetween(0, 5, 1, 0));
            assertThrows(IllegalArgumentException.class, () -> dst.jumpWallClockBetween(-1, 5, 0, 1));
            assertThrows(IllegalArgumentException.class, () -> dst.jumpWallClockBetween(0, Long.MAX_VALUE, 0, 1));
            assertThrows(IllegalArgumentException.class, () -> dst.jumpWallClockBetween(0, 5, Long.MIN_VALUE, 0));
            assertThrows(IllegalArgumentException.class, () -> dst.jumpWallClock(Long.MAX_VALUE));
            assertThrows(IllegalArgumentException.class, () -> dst.jumpWallClock(Long.MIN_VALUE));
            dst.jumpWallClock(Dst.MAX_WALL_CLOCK_JUMP_MILLIS);
            assertThrows(IllegalArgumentException.class, () -> dst.jumpWallClock(1), "the offset is bounded too");
            assertEquals(Dst.MAX_WALL_CLOCK_JUMP_MILLIS, dst.wallClockOffset());
            assertEquals(1, dst.traceLines().stream().filter(l -> l.contains("wall clock jump")).count(),
                    "only the jump that was made is traced");
            assertEquals(Instant.ofEpochMilli(Dst.EPOCH_MILLIS + Dst.MAX_WALL_CLOCK_JUMP_MILLIS), TimeSource.instant());
        } finally {
            dst.close();
        }
    }
}
