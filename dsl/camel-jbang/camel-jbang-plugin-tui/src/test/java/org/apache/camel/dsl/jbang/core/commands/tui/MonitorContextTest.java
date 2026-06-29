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

import dev.tamboui.style.Color;
import dev.tamboui.style.Modifier;
import dev.tamboui.style.Style;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MonitorContextTest {

    // ---- formatSinceLast tests ----

    @Test
    void formatSinceLastAllThreePresent() {
        String result = MonitorContext.formatSinceLast("1s", "2s", "3s");
        assertEquals("1s/2s/3s", result);
    }

    @Test
    void formatSinceLastOnlyStarted() {
        String result = MonitorContext.formatSinceLast("5s", null, null);
        assertEquals("5s", result);
    }

    @Test
    void formatSinceLastOnlyCompleted() {
        String result = MonitorContext.formatSinceLast(null, "10s", null);
        assertEquals("10s", result);
    }

    @Test
    void formatSinceLastOnlyFailed() {
        String result = MonitorContext.formatSinceLast(null, null, "7s");
        assertEquals("7s", result);
    }

    @Test
    void formatSinceLastNonePresent() {
        String result = MonitorContext.formatSinceLast(null, null, null);
        assertEquals("", result);
    }

    @Test
    void formatSinceLastStartedAndFailed() {
        String result = MonitorContext.formatSinceLast("1s", null, "3s");
        assertEquals("1s/3s", result);
    }

    // ---- formatLoad tests ----

    @Test
    void formatLoadNonZeroValues() {
        String result = MonitorContext.formatLoad("1.23", "4.56", "7.89");
        assertEquals("1.23/4.56/7.89", result);
    }

    @Test
    void formatLoadZeroCollapse() {
        String result = MonitorContext.formatLoad("0.00", "0.00", "0.00");
        assertEquals("0/0/0", result);
    }

    @Test
    void formatLoadNullHandling() {
        String result = MonitorContext.formatLoad(null, null, null);
        assertEquals("0/0/0", result);
    }

    @Test
    void formatLoadMixedZeroAndNonZero() {
        String result = MonitorContext.formatLoad("1.50", "0.00", "0.75");
        assertEquals("1.50/0/0.75", result);
    }

    // ---- formatMemory tests ----

    @Test
    void formatMemoryBothPositive() {
        // 512MB used, 1GB max
        String result = MonitorContext.formatMemory(536870912L, 1073741824L);
        assertEquals("512M/1024M", result);
    }

    @Test
    void formatMemoryMaxZeroShowsUsedOnly() {
        // 1048576 bytes = 1M (1024 * 1024)
        String result = MonitorContext.formatMemory(1048576L, 0L);
        assertEquals("1M", result);
    }

    @Test
    void formatMemoryUsedZeroReturnsEmpty() {
        String result = MonitorContext.formatMemory(0L, 1048576L);
        assertEquals("", result);
    }

    // ---- formatBytes tests ----

    @Test
    void formatBytesRange() {
        assertEquals("500B", MonitorContext.formatBytes(500));
    }

    @Test
    void formatBytesKilobytes() {
        assertEquals("1K", MonitorContext.formatBytes(1024));
        assertEquals("10K", MonitorContext.formatBytes(10240));
    }

    @Test
    void formatBytesMegabytes() {
        assertEquals("1M", MonitorContext.formatBytes(1048576));
        assertEquals("100M", MonitorContext.formatBytes(104857600));
    }

    // ---- buildBar tests ----

    @Test
    void buildBarFull() {
        String bar = MonitorContext.buildBar(100, 100, 10);
        assertEquals(10, bar.length());
    }

    @Test
    void buildBarPartial() {
        String bar = MonitorContext.buildBar(50, 100, 10);
        assertEquals(5, bar.length());
    }

    @Test
    void buildBarZeroValue() {
        String bar = MonitorContext.buildBar(0, 100, 10);
        assertEquals("", bar);
    }

    @Test
    void buildBarZeroMax() {
        String bar = MonitorContext.buildBar(50, 0, 10);
        assertEquals("", bar);
    }

    @Test
    void buildBarSmallValueRoundsCorrectly() {
        // 1/1000 * 10 rounds to 0, buildBar returns empty for zero-length
        String bar = MonitorContext.buildBar(1, 1000, 10);
        assertEquals("", bar);

        // A value large enough to produce at least one block (>= 1/maxWidth ratio)
        String bar2 = MonitorContext.buildBar(100, 1000, 10);
        assertTrue(bar2.length() >= 1);
    }

    // ---- topTimeStyle tests ----

    @Test
    void topTimeStyleOver1000ms() {
        Style style = MonitorContext.topTimeStyle(1000);
        assertTrue(style.effectiveModifiers().contains(Modifier.BOLD));
        assertEquals(Color.LIGHT_RED, style.fg().orElse(null));
    }

    @Test
    void topTimeStyleOver100ms() {
        Style style = MonitorContext.topTimeStyle(500);
        assertEquals(Color.YELLOW, style.fg().orElse(null));
    }

    @Test
    void topTimeStyleUnder100ms() {
        Style style = MonitorContext.topTimeStyle(50);
        assertEquals(Style.EMPTY, style);
    }

    // ---- topDeltaStyle tests ----

    @Test
    void topDeltaStylePositive() {
        Style style = MonitorContext.topDeltaStyle(10);
        assertEquals(Color.LIGHT_RED, style.fg().orElse(null));
    }

    @Test
    void topDeltaStyleNegative() {
        Style style = MonitorContext.topDeltaStyle(-5);
        assertEquals(Color.GREEN, style.fg().orElse(null));
    }

    @Test
    void topDeltaStyleZero() {
        Style style = MonitorContext.topDeltaStyle(0);
        assertEquals(Style.EMPTY, style);
    }

    // ---- compareStr tests ----

    @Test
    void compareStrBothNull() {
        assertEquals(0, MonitorContext.compareStr(null, null));
    }

    @Test
    void compareStrFirstNull() {
        assertEquals(1, MonitorContext.compareStr(null, "b"));
    }

    @Test
    void compareStrSecondNull() {
        assertEquals(-1, MonitorContext.compareStr("a", null));
    }

    @Test
    void compareStrCaseInsensitive() {
        assertEquals(0, MonitorContext.compareStr("ABC", "abc"));
        assertTrue(MonitorContext.compareStr("abc", "xyz") < 0);
        assertTrue(MonitorContext.compareStr("xyz", "abc") > 0);
    }
}
