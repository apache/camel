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
 * Tests for {@link TabRegistry} constants and tab index mapping.
 */
class TabRegistryTest {

    @Test
    void tabConstantsAreSequential() {
        assertEquals(0, TabRegistry.TAB_OVERVIEW, "TAB_OVERVIEW should be 0");
        assertEquals(1, TabRegistry.TAB_LOG, "TAB_LOG should be 1");
        assertEquals(2, TabRegistry.TAB_DIAGRAM, "TAB_DIAGRAM should be 2");
        assertEquals(3, TabRegistry.TAB_ROUTES, "TAB_ROUTES should be 3");
        assertEquals(4, TabRegistry.TAB_ENDPOINTS, "TAB_ENDPOINTS should be 4");
        assertEquals(5, TabRegistry.TAB_HTTP, "TAB_HTTP should be 5");
        assertEquals(6, TabRegistry.TAB_HEALTH, "TAB_HEALTH should be 6");
        assertEquals(7, TabRegistry.TAB_HISTORY, "TAB_HISTORY should be 7");
        assertEquals(8, TabRegistry.TAB_ERRORS, "TAB_ERRORS should be 8");
        assertEquals(9, TabRegistry.TAB_MORE, "TAB_MORE should be 9");
    }

    @Test
    void numTabsMatchesLastTabPlusOne() {
        assertEquals(TabRegistry.TAB_MORE + 1, TabRegistry.NUM_TABS,
                "NUM_TABS should be one more than the last tab index");
    }

    @Test
    void numTabsIsTen() {
        assertEquals(10, TabRegistry.NUM_TABS, "NUM_TABS should be 10");
    }

    @Test
    void tabConstantsAreUnique() {
        int[] tabs = {
                TabRegistry.TAB_OVERVIEW, TabRegistry.TAB_LOG, TabRegistry.TAB_DIAGRAM,
                TabRegistry.TAB_ROUTES, TabRegistry.TAB_ENDPOINTS, TabRegistry.TAB_HTTP,
                TabRegistry.TAB_HEALTH, TabRegistry.TAB_HISTORY, TabRegistry.TAB_ERRORS,
                TabRegistry.TAB_MORE
        };
        for (int i = 0; i < tabs.length; i++) {
            for (int j = i + 1; j < tabs.length; j++) {
                assertTrue(tabs[i] != tabs[j],
                        "Tab constants should be unique, but index " + i + " and " + j + " are both " + tabs[i]);
            }
        }
    }

    @Test
    void allTabIndicesAreNonNegative() {
        assertTrue(TabRegistry.TAB_OVERVIEW >= 0, "TAB_OVERVIEW should be non-negative");
        assertTrue(TabRegistry.TAB_LOG >= 0, "TAB_LOG should be non-negative");
        assertTrue(TabRegistry.TAB_DIAGRAM >= 0, "TAB_DIAGRAM should be non-negative");
        assertTrue(TabRegistry.TAB_ROUTES >= 0, "TAB_ROUTES should be non-negative");
        assertTrue(TabRegistry.TAB_ENDPOINTS >= 0, "TAB_ENDPOINTS should be non-negative");
        assertTrue(TabRegistry.TAB_HTTP >= 0, "TAB_HTTP should be non-negative");
        assertTrue(TabRegistry.TAB_HEALTH >= 0, "TAB_HEALTH should be non-negative");
        assertTrue(TabRegistry.TAB_HISTORY >= 0, "TAB_HISTORY should be non-negative");
        assertTrue(TabRegistry.TAB_ERRORS >= 0, "TAB_ERRORS should be non-negative");
        assertTrue(TabRegistry.TAB_MORE >= 0, "TAB_MORE should be non-negative");
    }

    @Test
    void allTabIndicesAreLessThanNumTabs() {
        assertTrue(TabRegistry.TAB_OVERVIEW < TabRegistry.NUM_TABS);
        assertTrue(TabRegistry.TAB_LOG < TabRegistry.NUM_TABS);
        assertTrue(TabRegistry.TAB_DIAGRAM < TabRegistry.NUM_TABS);
        assertTrue(TabRegistry.TAB_ROUTES < TabRegistry.NUM_TABS);
        assertTrue(TabRegistry.TAB_ENDPOINTS < TabRegistry.NUM_TABS);
        assertTrue(TabRegistry.TAB_HTTP < TabRegistry.NUM_TABS);
        assertTrue(TabRegistry.TAB_HEALTH < TabRegistry.NUM_TABS);
        assertTrue(TabRegistry.TAB_HISTORY < TabRegistry.NUM_TABS);
        assertTrue(TabRegistry.TAB_ERRORS < TabRegistry.NUM_TABS);
        assertTrue(TabRegistry.TAB_MORE < TabRegistry.NUM_TABS);
    }
}
