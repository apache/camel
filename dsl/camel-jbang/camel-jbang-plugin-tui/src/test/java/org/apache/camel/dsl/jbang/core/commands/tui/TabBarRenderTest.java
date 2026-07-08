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

import dev.tamboui.text.CharWidth;
import dev.tamboui.text.Line;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Smoke tests for the emoji-decorated primary tab bar (CAMEL-23720). Labels render compact with two spaces between the
 * emoji and the key digit; the Route/Top toggle keeps a constant width so the bar does not shift.
 */
class TabBarRenderTest {

    private static final int TABS_FULL_MIN_WIDTH = 157;

    @Test
    void primaryTabHeadersStartWithIconAndTwoSpaces() {
        String[] names = { "Overview", "Log", "Diagram", "Route", "Endpoint", "HTTP", "Health", "Inspect", "Errors" };
        for (int i = 0; i < names.length; i++) {
            String header = TuiIcons.primaryTabHeader(TuiIcons.PRIMARY_TAB_ICONS.get(i), String.valueOf(i + 1), names[i]);
            assertTrue(header.startsWith(TuiIcons.PRIMARY_TAB_ICONS.get(i) + "  " + (i + 1)),
                    "Tab header should be '<icon>  <key> <name>': " + header);
        }
    }

    @Test
    void moreTabHeaderIncludesIconAndChevron() {
        String header = TuiIcons.primaryTabHeader(TuiIcons.TAB_MORE, "0", TuiIcons.moreTabLabel());
        assertTrue(header.startsWith(TuiIcons.TAB_MORE));
        assertTrue(header.contains(TuiIcons.MORE_CHEVRON));
    }

    @Test
    void routeAndTopLabelsHaveEqualWidth() {
        // toggling Top mode must not shift the tab bar
        int route = CharWidth.of(TuiIcons.primaryTabHeader(TuiIcons.TAB_ROUTES, "4", "Route"));
        int top = CharWidth.of(TuiIcons.primaryTabHeader(TuiIcons.TAB_ROUTES, "4", " Top "));
        assertEquals(route, top, "Route and Top tab cells must be the same width");
    }

    @Test
    void fullTabBarFitsAtTabsFullMinWidth() {
        Line[] labels = fullTabLabels("Route");
        int dividerW = CharWidth.of(" | ");
        int total = 0;
        for (int i = 0; i < labels.length; i++) {
            total += labels[i].width();
            if (i < labels.length - 1) {
                total += dividerW;
            }
        }
        assertTrue(total <= TABS_FULL_MIN_WIDTH,
                "Full tab bar width " + total + " exceeds TABS_FULL_MIN_WIDTH " + TABS_FULL_MIN_WIDTH);
        // the emoji icons widened the bar past the old 126-column budget; guard against a silent revert
        assertTrue(total > 126,
                "Full tab bar width " + total + " should exceed the old 126 threshold once emoji icons are added");
    }

    private static Line[] fullTabLabels(String routesLabel) {
        return new Line[] {
                Line.from(TuiIcons.primaryTabHeader(TuiIcons.TAB_OVERVIEW, "1", "Overview")),
                Line.from(TuiIcons.primaryTabHeader(TuiIcons.TAB_LOG, "2", "Log")),
                Line.from(TuiIcons.primaryTabHeader(TuiIcons.TAB_DIAGRAM, "3", "Diagram")),
                Line.from(TuiIcons.primaryTabHeader(TuiIcons.TAB_ROUTES, "4", routesLabel)),
                Line.from(TuiIcons.primaryTabHeader(TuiIcons.TAB_ENDPOINTS, "5", "Endpoint")),
                Line.from(TuiIcons.primaryTabHeader(TuiIcons.TAB_HTTP, "6", "HTTP")),
                Line.from(TuiIcons.primaryTabHeader(TuiIcons.TAB_HEALTH, "7", "Health")),
                Line.from(TuiIcons.primaryTabHeader(TuiIcons.TAB_INSPECT, "8", "Inspect")),
                Line.from(TuiIcons.primaryTabHeader(TuiIcons.TAB_ERRORS, "9", "Errors")),
                Line.from(TuiIcons.primaryTabHeader(TuiIcons.TAB_MORE, "0", TuiIcons.moreTabLabel())),
        };
    }
}
