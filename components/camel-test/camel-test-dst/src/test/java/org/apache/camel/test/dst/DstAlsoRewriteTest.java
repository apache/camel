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

import org.apache.camel.support.MonotonicClock;
import org.apache.camel.util.StopWatch;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The stop-gap for time read outside the code under test: core classes listed in {@link DstInstrument#alsoRewrite()}
 * read virtual time too.
 */
@DstInstrument(value = "org.apache.camel.test.dst.sample",
               alsoRewrite = { "org.apache.camel.util.StopWatch", "org.apache.camel.support.MonotonicClock" })
class DstAlsoRewriteTest {

    @Test
    void listedCoreClocksReadVirtualTime() {
        assertTrue(DstInstrumentation.alsoRewritten().contains("org.apache.camel.util.StopWatch"),
                DstInstrumentation.alsoRewritten().toString());
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
        // outside a run the rewritten classes read the real clock
        assertTrue(new StopWatch().taken() < 60_000);
    }
}
