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

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Span;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Higher-level rendering tests for {@link ProcessTab}. These tests render the tab into a virtual terminal buffer via
 * {@link Frame#forTesting(Buffer)} and inspect the rendered cell content.
 */
class ProcessTabRenderTest {

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

        ProcessTab tab = new ProcessTab(ctx);
        String rendered = renderToString(tab, 120, 30);

        assertTrue(rendered.contains("No integration selected") || rendered.contains("Select an integration"),
                "Should show selection prompt when no integration selected");
    }

    @Test
    void renderShowsPidInfo() {
        ProcessTab tab = new ProcessTab(ctx);
        String rendered = renderToString(tab, 120, 30);
        assertTrue(rendered.contains("1234"), "Should show PID");
    }

    @Test
    void renderShowsNameInfo() {
        ProcessTab tab = new ProcessTab(ctx);
        String rendered = renderToString(tab, 120, 30);
        assertTrue(rendered.contains("test-app"), "Should show integration name");
    }

    @Test
    void renderShowsCamelVersion() {
        info.camelVersion = "4.21.0";
        ProcessTab tab = new ProcessTab(ctx);
        String rendered = renderToString(tab, 120, 30);
        assertTrue(rendered.contains("4.21.0"), "Should show Camel version");
    }

    @Test
    void renderShowsJavaVersion() {
        info.javaVersion = "17.0.1";
        ProcessTab tab = new ProcessTab(ctx);
        String rendered = renderToString(tab, 120, 30);
        assertTrue(rendered.contains("17.0.1"), "Should show Java version");
    }

    @Test
    void renderFooterHints() {
        ProcessTab tab = new ProcessTab(ctx);
        List<Span> footerSpans = new ArrayList<>();
        tab.renderFooter(footerSpans);

        String footer = footerSpans.stream()
                .map(Span::content)
                .reduce("", String::concat);

        assertTrue(footer.contains("scroll"), "Footer should contain scroll hint");
        assertTrue(footer.contains("wrap"), "Footer should contain wrap hint");
        assertTrue(footer.contains("Esc"), "Footer should contain Esc hint");
    }

    // ---- Helper methods ----

    private static String renderToString(MonitorTab tab, int width, int height) {
        Rect area = new Rect(0, 0, width, height);
        Buffer buffer = Buffer.empty(area);
        Frame frame = Frame.forTesting(buffer);
        tab.render(frame, area);
        return HealthTabRenderTest.bufferToString(buffer);
    }

    private static boolean findCellWithColor(Buffer buffer, String symbol, Color expectedFg) {
        for (int y = 0; y < buffer.height(); y++) {
            for (int x = 0; x < buffer.width(); x++) {
                var cell = buffer.get(x, y);
                if (symbol.equals(cell.symbol())) {
                    var fg = cell.style().fg().orElse(null);
                    if (expectedFg.equals(fg)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
