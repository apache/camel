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
 * Higher-level rendering tests for {@link SqlQueryTab}. Renders the SQL query view into a virtual terminal buffer and
 * inspects the rendered output (text content, colors, layout).
 */
class SqlQueryTabRenderTest {

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

        SqlQueryTab tab = new SqlQueryTab(ctx);
        tab.onTabSelected();
        String rendered = renderToString(tab, 100, 20);

        assertTrue(rendered.contains("No DataSource") || rendered.contains("SQL Query"),
                "Should show SQL area with no datasource message when no integration selected");
    }

    @Test
    void renderShowsBlockTitle() {
        SqlQueryTab tab = new SqlQueryTab(ctx);
        tab.onTabSelected();
        String rendered = renderToString(tab, 100, 20);

        assertTrue(rendered.contains("SQL") || rendered.contains("Query"),
                "Should show SQL or Query in the block title");
    }

    @Test
    void renderNoDataSourceMessage() {
        SqlQueryTab tab = new SqlQueryTab(ctx);
        tab.onTabSelected();
        String rendered = renderToString(tab, 100, 20);

        assertTrue(rendered.contains("No DataSource"),
                "Should show 'No DataSource' message when no datasources are available");
    }

    @Test
    void renderWithDataSourceShowsInput() {
        addDataSource("orderDB", "com.zaxxer.hikari.HikariDataSource");

        SqlQueryTab tab = new SqlQueryTab(ctx);
        tab.onTabSelected();
        String rendered = renderToString(tab, 100, 20);

        assertTrue(rendered.contains("SQL Query") || rendered.contains("F5"),
                "Should show SQL input area with execute hint");
    }

    @Test
    void renderFooterHints() {
        SqlQueryTab tab = new SqlQueryTab(ctx);
        tab.onTabSelected();
        List<Span> footerSpans = new ArrayList<>();
        tab.renderFooter(footerSpans);

        String footer = footerSpans.stream()
                .map(Span::content)
                .reduce("", String::concat);

        assertTrue(footer.contains("F5"), "Footer should contain F5 execute hint");
        assertTrue(footer.contains("execute"), "Footer should contain execute hint");
    }

    @Test
    void renderShowsPlaceholderText() {
        addDataSource("myDS", "com.zaxxer.hikari.HikariDataSource");

        SqlQueryTab tab = new SqlQueryTab(ctx);
        tab.onTabSelected();
        String rendered = renderToString(tab, 100, 20);

        assertTrue(rendered.contains("Type") || rendered.contains("SQL query") || rendered.contains("F5 to execute"),
                "Should show placeholder text for SQL input");
    }

    // ---- Helper methods ----

    private void addDataSource(String name, String type) {
        DataSourceInfo ds = new DataSourceInfo();
        ds.name = name;
        ds.type = type;
        ds.maxPoolSize = 10;
        info.dataSources.add(ds);
    }

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
