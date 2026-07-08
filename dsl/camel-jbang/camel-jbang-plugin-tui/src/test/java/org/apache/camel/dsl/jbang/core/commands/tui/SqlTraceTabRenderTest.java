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
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.KeyModifiers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlTraceTabRenderTest {

    private MonitorContext ctx;
    private IntegrationInfo info;

    @BeforeEach
    void setUp() {
        Theme.resetForTesting();
        info = new IntegrationInfo();
        info.pid = "1234";
        info.name = "test-app";

        AtomicReference<List<IntegrationInfo>> data = new AtomicReference<>(List.of(info));
        AtomicReference<List<InfraInfo>> infraData = new AtomicReference<>(List.of());
        ctx = new MonitorContext(data, infraData);
        ctx.selectedPid = "1234";
    }

    @Test
    void renderShowsKpiStrip() {
        info.sqlTraceTotal = 42;
        info.sqlTraceAvgTime = 15;
        info.sqlTraceSlowestTime = 120;
        info.sqlTraceSlowCount = 3;
        info.sqlTraceFailedCount = 1;

        SqlTraceTab tab = new SqlTraceTab(ctx);
        String rendered = TuiTestHelper.renderToString(tab, 140, 25);

        assertTrue(rendered.contains("SQL Trace"), "Should show SQL Trace title");
        assertTrue(rendered.contains("Total:"), "Should show Total KPI");
        assertTrue(rendered.contains("42"), "Should show total count");
        assertTrue(rendered.contains("Avg:"), "Should show Avg KPI");
    }

    @Test
    void renderShowsTableHeaders() {
        addSqlTrace("SELECT * FROM users", "SELECT", "route1", 25, 10, false);

        SqlTraceTab tab = new SqlTraceTab(ctx);
        String rendered = TuiTestHelper.renderToString(tab, 140, 25);

        assertTrue(rendered.contains("TIME"), "Should show TIME header");
        assertTrue(rendered.contains("TYPE"), "Should show TYPE header");
        assertTrue(rendered.contains("SQL"), "Should show SQL header");
        assertTrue(rendered.contains("ROUTE"), "Should show ROUTE header");
        assertTrue(rendered.contains("DURATION"), "Should show DURATION header");
    }

    @Test
    void renderShowsSqlData() {
        addSqlTrace("SELECT * FROM orders", "SELECT", "orderRoute", 15, 5, false);

        SqlTraceTab tab = new SqlTraceTab(ctx);
        String rendered = TuiTestHelper.renderToString(tab, 140, 25);

        assertTrue(rendered.contains("SELECT * FROM orders"), "Should render SQL query text");
        assertTrue(rendered.contains("orderRoute"), "Should render route ID");
    }

    @Test
    void renderSlowQueryHighlighted() {
        addSqlTrace("SELECT * FROM big_table", "SELECT", "route1", 150, 1000, false);

        SqlTraceTab tab = new SqlTraceTab(ctx);

        Rect area = new Rect(0, 0, 140, 25);
        Buffer buffer = Buffer.empty(area);
        Frame frame = Frame.forTesting(buffer);
        tab.render(frame, area);

        Color warningColor = Theme.warning().fg().orElse(Color.YELLOW);
        boolean foundWarning = TuiTestHelper.findCellWithColor(buffer, "1", warningColor);
        assertTrue(foundWarning, "Slow query duration should be highlighted in warning color");
    }

    @Test
    void renderFailedQueryInRed() {
        addSqlTrace("INSERT INTO bad_table", "INSERT", "route1", 5, 0, true);

        SqlTraceTab tab = new SqlTraceTab(ctx);

        Rect area = new Rect(0, 0, 140, 25);
        Buffer buffer = Buffer.empty(area);
        Frame frame = Frame.forTesting(buffer);
        tab.render(frame, area);

        boolean foundRed = TuiTestHelper.findCellWithColor(buffer, "F", Color.LIGHT_RED);
        assertTrue(foundRed, "Failed status should be rendered in LIGHT_RED");
    }

    @Test
    void renderEmptyShowsPlaceholder() {
        SqlTraceTab tab = new SqlTraceTab(ctx);
        String rendered = TuiTestHelper.renderToString(tab, 140, 25);

        assertTrue(rendered.contains("No SQL statements"), "Should show placeholder when no traces exist");
    }

    @Test
    void renderNoSelectionShowsPrompt() {
        ctx.selectedPid = null;

        SqlTraceTab tab = new SqlTraceTab(ctx);
        String rendered = TuiTestHelper.renderToString(tab, 100, 25);

        assertTrue(rendered.contains("No integration selected") || rendered.contains("Select an integration"),
                "Should show selection prompt when no integration selected");
    }

    @Test
    void sortCycleChangesSortIndicator() {
        addSqlTrace("SELECT 1", "SELECT", "route1", 10, 1, false);

        SqlTraceTab tab = new SqlTraceTab(ctx);

        tab.handleKeyEvent(KeyEvent.ofChar('s', KeyModifiers.NONE));
        String rendered = TuiTestHelper.renderToString(tab, 140, 25);

        assertTrue(rendered.contains("TYPE▼"), "Sort should cycle to 'type' after pressing 's'");
    }

    @Test
    void renderFooterHints() {
        SqlTraceTab tab = new SqlTraceTab(ctx);
        List<Span> footerSpans = new ArrayList<>();
        tab.renderFooter(footerSpans);

        String footer = footerSpans.stream()
                .map(Span::content)
                .reduce("", String::concat);

        assertTrue(footer.contains("Esc"), "Footer should contain Esc hint");
        assertTrue(footer.contains("sort"), "Footer should contain sort hint");
    }

    @Test
    void helpTextIsAvailable() {
        SqlTraceTab tab = new SqlTraceTab(ctx);
        String help = tab.getHelpText();
        assertNotNull(help, "Help text should not be null");
        assertTrue(help.contains("SQL Trace"), "Help text should mention SQL Trace");
        assertTrue(help.contains("KPI Strip"), "Help text should describe KPI strip");
    }

    // ---- Helper methods ----

    private void addSqlTrace(
            String query, String category, String routeId, long duration, int rowCount,
            boolean failed) {
        SqlTraceInfo si = new SqlTraceInfo();
        si.query = query;
        si.category = category;
        si.routeId = routeId;
        si.duration = duration;
        si.rowCount = rowCount;
        si.failed = failed;
        si.exchangeId = "ID-test-1234";
        si.endpoint = "sql:" + query;
        si.timestamp = System.currentTimeMillis();
        info.sqlTraceStatements.add(si);
    }
}
