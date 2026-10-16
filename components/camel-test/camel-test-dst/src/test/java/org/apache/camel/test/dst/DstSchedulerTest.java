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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DstSchedulerTest {

    @Test
    void virtualTimeJumpsToTheNextTask() {
        Dst dst = new Dst(1);
        try {
            DstExecutor ex = dst.executor("ex", 1);
            List<Long> times = new ArrayList<>();
            ex.schedule("late", 3_600_000, () -> times.add(dst.now()));
            ex.schedule("early", 1_000, () -> times.add(dst.now()));
            long start = System.nanoTime();
            dst.runUntilQuiet();
            assertEquals(List.of(1_000L, 3_600_000L), times);
            assertTrue(System.nanoTime() - start < 5_000_000_000L, "an hour of virtual time takes no real time");
        } finally {
            dst.close();
        }
    }

    @Test
    void singleThreadExecutorKeepsFifoOrder() {
        for (long seed = 1; seed <= 50; seed++) {
            Dst dst = new Dst(seed);
            try {
                DstExecutor ex = dst.executor("single", 1);
                List<Integer> order = new ArrayList<>();
                for (int i = 0; i < 5; i++) {
                    int n = i;
                    ex.execute(() -> order.add(n));
                }
                dst.runUntilQuiet();
                assertEquals(List.of(0, 1, 2, 3, 4), order);
            } finally {
                dst.close();
            }
        }
    }

    @Test
    void sameSeedSameOrderOtherSeedOtherOrder() {
        List<Integer> a = interleaving(42);
        assertEquals(a, interleaving(42));
        boolean differs = false;
        for (long seed = 1; seed <= 20 && !differs; seed++) {
            differs = !a.equals(interleaving(seed));
        }
        assertTrue(differs, "the seed changes the interleaving");
    }

    private static List<Integer> interleaving(long seed) {
        Dst dst = new Dst(seed);
        try {
            List<Integer> order = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                int n = i;
                dst.executor("ex" + i, 1).execute(() -> order.add(n));
            }
            dst.runUntilQuiet();
            return order;
        } finally {
            dst.close();
        }
    }

    @Test
    void blockingCallLetsOtherThreadsRunButKeepsItsOwnThreadBusy() {
        Dst dst = new Dst(7);
        try {
            DstExecutor poller = dst.executor("poller", 1);
            DstExecutor other = dst.executor("other", 1);
            List<String> events = new ArrayList<>();
            poller.execute(() -> {
                events.add("call@" + dst.now());
                dst.block(100, "remote call");
                events.add("answer@" + dst.now());
            });
            poller.schedule("same thread", 10, () -> events.add("poller2@" + dst.now()));
            other.schedule("other thread", 50, () -> events.add("other@" + dst.now()));
            dst.runUntilQuiet();
            assertEquals(List.of("call@0", "other@50", "answer@100", "poller2@100"), events);
        } finally {
            dst.close();
        }
    }

    @Test
    void traceHashIsStable() {
        assertEquals(traceHash(3), traceHash(3));
        assertNotEquals(traceHash(3), traceHash(4));
    }

    private static String traceHash(long seed) {
        Dst dst = new Dst(seed);
        try {
            for (int i = 0; i < 4; i++) {
                dst.executor("ex" + i, 1).schedule("t" + i, dst.between(0, 10), () -> {
                });
            }
            dst.runUntilQuiet();
            return dst.traceHash();
        } finally {
            dst.close();
        }
    }
}
