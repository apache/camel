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
package org.apache.camel.management;

import org.apache.camel.management.mbean.LoadThroughput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisabledOnOs(OS.AIX)
public class LoadThroughputTest {

    @Test
    public void testInitialValueIsZero() {
        LoadThroughput t = new LoadThroughput();
        assertEquals(0.0, t.getThroughput());
    }

    @Test
    public void testConvergesToSteadyRate() throws Exception {
        LoadThroughput t = new LoadThroughput();

        // simulate 1 exchange per second for 120 seconds (well past 1-minute EWMA window)
        long total = 0;
        t.update(total);
        Thread.sleep(10);
        for (int i = 0; i < 120; i++) {
            total++;
            Thread.sleep(10);
            t.update(total);
        }

        // should converge close to the steady rate
        assertTrue(t.getThroughput() > 0, "Throughput should be positive for steady input");
    }

    @Test
    public void testSmoothing() throws Exception {
        LoadThroughput t = new LoadThroughput();

        // simulate a timer that fires every 5th update (like a 5s timer with 1s sampling)
        // with 10ms sleep, effective rate is 1 exchange per 50ms ≈ 20 exchanges/sec
        long total = 0;
        t.update(total);
        Thread.sleep(10);
        for (int i = 1; i <= 100; i++) {
            if (i % 5 == 0) {
                total++;
            }
            Thread.sleep(10);
            t.update(total);
        }

        double thp = t.getThroughput();
        // the smoothed value should be positive and converging toward the average rate (~20/s)
        // rather than oscillating between 0 and ~100 (the instantaneous spike)
        assertTrue(thp > 1.0, "Smoothed throughput should be well above zero: " + thp);
        assertTrue(thp < 80.0, "Smoothed throughput should be below the instantaneous spike: " + thp);
    }

    @Test
    public void testReset() throws Exception {
        LoadThroughput t = new LoadThroughput();

        t.update(0);
        Thread.sleep(10);
        t.update(10);

        assertTrue(t.getThroughput() > 0);

        t.reset();
        assertEquals(0.0, t.getThroughput());
    }

}
