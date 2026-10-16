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

import org.apache.camel.test.dst.sample.SampleClock;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DstInstrument("org.apache.camel.test.dst.sample")
class DstInstrumentationTest {

    @Test
    void codeUnderTestReadsVirtualTime() {
        Dst dst = new Dst(1);
        try {
            long[] seen = new long[1];
            dst.executor("ex", 1).schedule("read clock", 3_600_000, () -> seen[0] = SampleClock.now());
            dst.runUntilQuiet();
            assertEquals(Dst.EPOCH_MILLIS + 3_600_000, seen[0]);
        } finally {
            dst.close();
        }
        // outside a run the real clock is used
        assertTrue(Math.abs(SampleClock.now() - System.currentTimeMillis()) < 60_000);
    }

    @Test
    void escapePointsAreReported() {
        List<String> found = DstInstrumentation.scan("org.apache.camel.test.dst.sample");
        assertTrue(found.stream().anyMatch(l -> l.startsWith(DstInstrumentation.SLEEP) && l.contains("SampleClock.nap")),
                found.toString());
        assertTrue(found.stream().anyMatch(l -> l.startsWith(DstInstrumentation.VIRTUALIZED) && l.contains("SampleClock.now")),
                found.toString());
    }
}
