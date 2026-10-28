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

import org.apache.camel.test.dst.sample.SampleSleeper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sleeps of the code under test are virtual on the loop thread: the sleeping task keeps its thread, the other tasks run
 * meanwhile, and no real time passes.
 */
@DstInstrument("org.apache.camel.test.dst.sample")
class DstSleepTest {

    @Test
    void sleepInATaskIsAVirtualBlock() {
        Dst dst = new Dst(1);
        try {
            DstExecutor sleeper = dst.executor("sleeper", 1);
            DstExecutor other = dst.executor("other", 1);
            List<String> events = new ArrayList<>();
            sleeper.execute(() -> {
                events.add("sleep@" + dst.now());
                try {
                    SampleSleeper.sleepMillis(60_000);
                    events.add("ms@" + dst.now());
                    SampleSleeper.sleepSeconds(60);
                    events.add("seconds@" + dst.now());
                    SampleSleeper.sleepMillisNanos(60_000);
                    events.add("nanos@" + dst.now());
                } catch (InterruptedException e) {
                    throw new IllegalStateException(e);
                }
            });
            sleeper.schedule("same thread", 10, () -> events.add("sleeper2@" + dst.now()));
            other.schedule("other thread", 30_000, () -> events.add("other@" + dst.now()));
            long start = System.nanoTime();
            dst.runUntilQuiet();
            assertEquals(List.of("sleep@0", "other@30000", "ms@60000", "seconds@120000", "nanos@180000",
                    "sleeper2@180000"), events);
            assertTrue(System.nanoTime() - start < 5_000_000_000L, "three minutes of sleep take no real time");
            assertTrue(dst.traceLines().stream().anyMatch(l -> l.contains("block 60000ms sleep at SampleSleeper.sleepMillis")),
                    "the trace names the call site");
        } finally {
            dst.close();
        }
    }

    @Test
    void sleepInTheTestBodyLetsTheTimePass() throws Exception {
        Dst dst = new Dst(1);
        try {
            List<Long> ran = new ArrayList<>();
            dst.executor("ex", 1).schedule("task", 1_000, () -> ran.add(dst.now()));
            SampleSleeper.sleepSeconds(5);
            assertEquals(List.of(1_000L), ran);
            assertEquals(5_000, dst.now());
        } finally {
            dst.close();
        }
    }

    @Test
    void outsideARunTheSleepIsReal() throws Exception {
        long start = System.nanoTime();
        SampleSleeper.sleepMillis(20);
        assertTrue(System.nanoTime() - start >= 20_000_000L);
    }
}
