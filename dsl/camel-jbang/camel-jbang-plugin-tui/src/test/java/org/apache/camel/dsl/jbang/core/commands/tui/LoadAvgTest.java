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
package org.apache.camel.dsl.jbang.core.commands.tui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoadAvgTest {

    @Test
    void noDataReturnsDash() {
        LoadAvg avg = new LoadAvg();
        assertEquals("-", avg.format("%.2f/%.2f/%.2f"));
    }

    @Test
    void firstUpdateInitializes() {
        LoadAvg avg = new LoadAvg();
        avg.update(10.0);
        String result = avg.format("%.2f/%.2f/%.2f");
        assertNotEquals("-", result);
        // After first update, all three loads should equal the initial value
        assertEquals("10.00/10.00/10.00", result);
    }

    @Test
    void multipleUpdatesConvergeViaEwma() {
        LoadAvg avg = new LoadAvg();
        avg.update(100.0);
        // After initial value, update with 0 multiple times
        // The EWMA should decay toward 0, with load1 decaying fastest
        for (int i = 0; i < 100; i++) {
            avg.update(0.0);
        }
        String result = avg.format("%.2f/%.2f/%.2f");
        String[] parts = result.split("/");
        double load1 = Double.parseDouble(parts[0]);
        double load5 = Double.parseDouble(parts[1]);
        double load15 = Double.parseDouble(parts[2]);

        // load1 should have decayed more than load5, which decayed more than load15
        assertTrue(load1 < load5, "load1 should decay faster than load5");
        assertTrue(load5 < load15, "load5 should decay faster than load15");
        // All should be less than the original 100
        assertTrue(load15 < 100.0);
    }

    @Test
    void steadyStateConverges() {
        LoadAvg avg = new LoadAvg();
        // Feed a constant value; all loads should converge to that value
        for (int i = 0; i < 1000; i++) {
            avg.update(50.0);
        }
        String result = avg.format("%.1f/%.1f/%.1f");
        assertEquals("50.0/50.0/50.0", result);
    }

    @Test
    void formatStringIsUsed() {
        LoadAvg avg = new LoadAvg();
        avg.update(1.23456);
        // Check that the format string controls the output format
        String result = avg.format("%.1f %.1f %.1f");
        assertEquals("1.2 1.2 1.2", result);
    }
}
