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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import dev.tamboui.layout.Rect;
import dev.tamboui.text.Span;
import dev.tamboui.tui.event.MouseEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rendering tests for {@link LogTab}. These tests render the tab into a virtual terminal buffer and inspect the
 * rendered cell content.
 */
class LogTabRenderTest {

    private MonitorContext ctx;
    private IntegrationInfo info;

    @BeforeEach
    void setUp() {
        info = new IntegrationInfo();
        info.pid = "1234";
        info.name = "test-app";

        AtomicReference<List<IntegrationInfo>> data = new AtomicReference<>(List.of(info));
        AtomicReference<List<InfraInfo>> infraData = new AtomicReference<>(List.of());
        ctx = new MonitorContext(data, infraData);
        ctx.selectedPid = "1234";
    }

    @Test
    void renderNoSelectionShowsPrompt() {
        ctx.selectedPid = null;
        LogTab tab = new LogTab(ctx);
        String rendered = TuiTestHelper.renderToString(tab, 120, 20);
        assertTrue(rendered.contains("No integration selected") || rendered.contains("Select an integration"),
                "Should show selection prompt when no integration selected");
    }

    @Test
    void renderShowsBlockTitle() {
        LogTab tab = new LogTab(ctx);
        String rendered = TuiTestHelper.renderToString(tab, 120, 20);
        assertTrue(rendered.contains("Log"), "Should show Log in the block title");
    }

    @Test
    void renderShowsLoadingOrEmpty() {
        LogTab tab = new LogTab(ctx);
        String rendered = TuiTestHelper.renderToString(tab, 120, 20);
        assertTrue(rendered.contains("Loading") || rendered.contains("Log"),
                "Should show loading state or Log title");
    }

    @Test
    void renderFooterHints() {
        LogTab tab = new LogTab(ctx);
        List<Span> footerSpans = new ArrayList<>();
        tab.renderFooter(footerSpans);
        String footer = footerSpans.stream().map(Span::content).reduce("", String::concat);
        assertTrue(footer.contains("find") || footer.contains("/"),
                "Footer should contain find hint");
    }

    // Scrolling the log with the wheel breaks follow mode so the pinned view stays put while the user reads history.
    // The footer "follow [on]"/"follow [off]" hint reflects that state.

    @Test
    void scrollUpTurnsFollowModeOff() {
        LogTab tab = new LogTab(ctx);
        Rect area = new Rect(0, 0, 120, 20);

        assertTrue(footer(tab).contains("follow [on]"), "follow mode is on by default");

        assertTrue(tab.handleMouseEvent(MouseEvent.scrollUp(10, 10), area), "a wheel scroll inside the log is consumed");
        assertTrue(footer(tab).contains("follow [off]"), "scrolling up disables follow mode");
    }

    @Test
    void scrollOutsideLogAreaLeavesFollowModeOn() {
        LogTab tab = new LogTab(ctx);
        Rect area = new Rect(0, 0, 120, 20);

        assertFalse(tab.handleMouseEvent(MouseEvent.scrollUp(500, 500), area), "a scroll outside the log area is ignored");
        assertTrue(footer(tab).contains("follow [on]"), "follow mode stays on when the scroll misses the log area");
    }

    private static String footer(LogTab tab) {
        List<Span> spans = new ArrayList<>();
        tab.renderFooter(spans);
        return spans.stream().map(Span::content).reduce("", String::concat);
    }

}
