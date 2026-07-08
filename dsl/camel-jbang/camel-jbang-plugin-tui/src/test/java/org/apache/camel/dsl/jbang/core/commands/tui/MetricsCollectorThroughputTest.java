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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link MetricsCollector} throughput formatting and scaling utilities. Verifies that sub-1.0 msg/s rates
 * (common in development workloads) are correctly preserved and displayed.
 */
class MetricsCollectorThroughputTest {

    @Test
    void formatThroughputZero() {
        assertEquals("0", MetricsCollector.formatThroughput(0));
    }

    @Test
    void formatThroughputSubOne() {
        // 0.20 msg/s = 20 when scaled by 100
        assertEquals("0.20", MetricsCollector.formatThroughput(20));
        // 0.05 msg/s = 5 when scaled by 100
        assertEquals("0.05", MetricsCollector.formatThroughput(5));
    }

    @Test
    void formatThroughputBetweenOneAndTen() {
        // 1.0 msg/s = 100 when scaled by 100
        assertEquals("1.0", MetricsCollector.formatThroughput(100));
        // 5.5 msg/s = 550 when scaled by 100
        assertEquals("5.5", MetricsCollector.formatThroughput(550));
    }

    @Test
    void formatThroughputAboveTen() {
        // 10 msg/s = 1000 when scaled by 100
        assertEquals("10", MetricsCollector.formatThroughput(1000));
        // 42 msg/s = 4200 when scaled by 100
        assertEquals("42", MetricsCollector.formatThroughput(4200));
    }

    @Test
    void niceMaxReturnsScaleForZero() {
        assertEquals(MetricsCollector.THROUGHPUT_SCALE, MetricsCollector.niceMax(0));
    }

    @Test
    void niceMaxReturnsScaleForSmallValues() {
        // 0.20 msg/s scaled = 20 -> niceMax should return 100 (= 1.0 msg/s)
        long result = MetricsCollector.niceMax(20);
        assertEquals(100, result);
    }

    @Test
    void niceMaxRoundsUpToNiceNumber() {
        // 3.5 msg/s scaled = 350 -> niceMax should return 500 (= 5.0 msg/s)
        long result = MetricsCollector.niceMax(350);
        assertEquals(500, result);
    }

    @Test
    void niceMaxForLargeValues() {
        // 75 msg/s scaled = 7500 -> niceMax should return 10000 (= 100 msg/s)
        long result = MetricsCollector.niceMax(7500);
        assertEquals(10000, result);
    }

    @Test
    void niceMaxAlwaysGreaterOrEqual() {
        // niceMax should always return a value >= the input
        for (long v : new long[] { 1, 10, 50, 99, 100, 150, 200, 500, 1000, 5000, 10000 }) {
            assertTrue(MetricsCollector.niceMax(v) >= v,
                    "niceMax(" + v + ") = " + MetricsCollector.niceMax(v) + " should be >= " + v);
        }
    }

    @Test
    void throughputScaleIsHundred() {
        // Verify the scale factor — tests and display logic depend on this value
        assertEquals(100, MetricsCollector.THROUGHPUT_SCALE);
    }

    @Test
    void niceMaxDoesNotOverflowForLargeInput() {
        // Very large rawMax should not cause infinite loop or negative values
        long result = MetricsCollector.niceMax(Long.MAX_VALUE / 2);
        assertTrue(result >= Long.MAX_VALUE / 2,
                "niceMax should return at least rawMax for extreme values, got " + result);
    }

    @Test
    void niceMaxHandlesMaxLong() {
        // Long.MAX_VALUE itself should not hang
        long result = MetricsCollector.niceMax(Long.MAX_VALUE);
        assertTrue(result > 0, "niceMax(Long.MAX_VALUE) should return a positive value, got " + result);
    }
}
