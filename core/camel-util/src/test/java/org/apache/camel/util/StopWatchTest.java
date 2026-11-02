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

import java.time.Duration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StopWatchTest {
    @Test
    void testDurationMatchesExpect() throws InterruptedException {
        StopWatch stopWatch = new StopWatch();

        Thread.sleep(Duration.ofSeconds(1).toMillis());

        long taken = stopWatch.taken();
        assertTrue(taken >= 1000, "Elapsed time should be equal to or greater than 1000 ms but was " + taken);
        assertTrue(taken < 1500, "Elapsed time should be smaller than 1500 ms but was " + taken);
    }

    @Test
    void testDurationMatchesExpectWithRestart() throws InterruptedException {
        StopWatch stopWatch = new StopWatch();

        Thread.sleep(Duration.ofSeconds(1).toMillis());

        long taken = stopWatch.taken();
        assertTrue(taken >= 1000, "Elapsed time should be equal to or greater than 1000 ms but was " + taken);
        assertTrue(taken < 1500, "Elapsed time should be smaller than 1500 ms but was " + taken);

        stopWatch.restart();

        Thread.sleep(Duration.ofSeconds(1).toMillis());

        taken = stopWatch.taken();
        assertTrue(taken >= 1000, "Elapsed time should be equal to or greater than 1000 ms but was " + taken);
        assertTrue(taken < 1500, "Elapsed time should be smaller than 1500 ms but was " + taken);
    }

    @Test
    void testDurationMatchesExpectLateStart() throws InterruptedException {
        StopWatch stopWatch = new StopWatch(false);

        Thread.sleep(Duration.ofSeconds(1).toMillis());

        long taken = stopWatch.taken();
        assertEquals(0, taken);

        stopWatch.restart();

        Thread.sleep(Duration.ofSeconds(1).toMillis());

        taken = stopWatch.taken();
        assertTrue(taken >= 1000, "Elapsed time should be equal to or greater than 1000 ms but was " + taken);
        assertTrue(taken < 1500, "Elapsed time should be smaller than 1500 ms but was " + taken);
    }

    @Test
    void testTakenTruncatesNanosToMillis() {
        // task timings are compared with timeouts, so a partial milli must not be rounded up
        long[] nanos = { 1_000_000_000L };
        TimeSource.Provider previous = TimeSource.install(new TimeSource.Provider() {
            @Override
            public long currentTimeMillis() {
                return 0;
            }

            @Override
            public long nanoTime() {
                return nanos[0];
            }

            @Override
            public void sleep(long millis) {
                // not used
            }
        });
        try {
            StopWatch stopWatch = new StopWatch();

            nanos[0] += 1_999_999;
            assertEquals(1, stopWatch.taken());

            nanos[0] += 1;
            assertEquals(2, stopWatch.taken());

            // a clock going backwards truncates toward zero too
            nanos[0] -= 3_999_999;
            assertEquals(-1, stopWatch.taken());
        } finally {
            TimeSource.install(previous);
        }
    }
}
