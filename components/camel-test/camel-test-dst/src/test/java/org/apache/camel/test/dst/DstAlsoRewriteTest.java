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

import org.apache.camel.support.ResetableClock;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Time read outside the code under test and outside {@code TimeSource}: core classes listed in
 * {@link DstInstrument#alsoRewrite()} read virtual time too. {@code ResetableClock} (the clock of an exchange) reads
 * {@code System} directly, as event timestamps do.
 */
@DstInstrument(value = "org.apache.camel.test.dst.sample", alsoRewrite = "org.apache.camel.support.ResetableClock")
class DstAlsoRewriteTest {

    @Test
    void listedCoreClocksReadVirtualTime() {
        assertTrue(DstInstrumentation.alsoRewritten().contains("org.apache.camel.support.ResetableClock"),
                DstInstrumentation.alsoRewritten().toString());
        Dst dst = new Dst(1);
        try {
            ResetableClock[] clock = new ResetableClock[1];
            long[] taken = new long[2];
            DstExecutor ex = dst.executor("ex", 1);
            ex.schedule("start", 1_000, () -> clock[0] = new ResetableClock());
            ex.schedule("read", 3_601_000, () -> {
                taken[0] = clock[0].elapsed();
                clock[0].reset();
            });
            ex.schedule("read again", 3_601_500, () -> taken[1] = clock[0].elapsed());
            long start = System.nanoTime();
            dst.runUntilQuiet();
            assertEquals(3_600_000L, taken[0]);
            assertEquals(500L, taken[1]);
            assertEquals(Dst.EPOCH_MILLIS + 3_601_000, clock[0].getCreated());
            assertTrue(System.nanoTime() - start < 5_000_000_000L, "an hour of virtual time takes no real time");
        } finally {
            dst.close();
        }
        // outside a run the rewritten class reads the real clock
        assertTrue(new ResetableClock().elapsed() < 60_000);
    }
}
