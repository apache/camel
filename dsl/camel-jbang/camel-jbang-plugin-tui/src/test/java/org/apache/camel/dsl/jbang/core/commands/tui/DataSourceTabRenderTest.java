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

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Higher-level rendering tests for {@link DataSourceTab}. Renders the datasource table into a virtual terminal buffer
 * and inspects the rendered output (text content, colors, layout).
 */
class DataSourceTabRenderTest {

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
    void renderShowsTableHeaders() {
        addDataSource("myDS", "com.zaxxer.hikari.HikariDataSource", 3, 7, 10, 10);

        DataSourceTab tab = new DataSourceTab(ctx);
        String rendered = TuiTestHelper.renderToString(tab, 140, 20);

        assertTrue(rendered.contains("NAME"), "Should show NAME header");
        assertTrue(rendered.contains("POOL"), "Should show POOL header");
        assertTrue(rendered.contains("ACTIVE"), "Should show ACTIVE header");
        assertTrue(rendered.contains("IDLE"), "Should show IDLE header");
        assertTrue(rendered.contains("TOTAL"), "Should show TOTAL header");
    }

    @Test
    void renderShowsDataSourceData() {
        addDataSource("orderDB", "com.zaxxer.hikari.HikariDataSource", 2, 8, 10, 10);

        DataSourceTab tab = new DataSourceTab(ctx);
        String rendered = TuiTestHelper.renderToString(tab, 140, 20);

        assertTrue(rendered.contains("orderDB"), "Should render datasource name");
        assertTrue(rendered.contains("HikariDataSource"), "Should render datasource type");
    }

    @Test
    void renderNameInCyan() {
        addDataSource("myDS", "com.zaxxer.hikari.HikariDataSource", 3, 7, 10, 10);

        DataSourceTab tab = new DataSourceTab(ctx);

        Rect area = new Rect(0, 0, 140, 20);
        Buffer buffer = Buffer.empty(area);
        Frame frame = Frame.forTesting(buffer);
        tab.render(frame, area);

        boolean foundCyan = TuiTestHelper.findCellWithColor(buffer, "m", Color.CYAN);
        assertTrue(foundCyan, "DataSource name should be rendered in CYAN");
    }

    @Test
    void renderExhaustedPoolInRed() {
        addDataSource("busyDS", "com.zaxxer.hikari.HikariDataSource", 10, 0, 10, 10);

        DataSourceTab tab = new DataSourceTab(ctx);

        Rect area = new Rect(0, 0, 140, 20);
        Buffer buffer = Buffer.empty(area);
        Frame frame = Frame.forTesting(buffer);
        tab.render(frame, area);

        // active (10) >= maxPoolSize (10), so active column should be LIGHT_RED
        boolean foundRed = TuiTestHelper.findCellWithColor(buffer, "1", Theme.error().fg().orElse(Color.LIGHT_RED));
        assertTrue(foundRed, "Exhausted pool active count should be rendered in LIGHT_RED");
    }

    @Test
    void renderMultipleDataSourcesAllAppear() {
        addDataSource("orderDB", "com.zaxxer.hikari.HikariDataSource", 2, 8, 10, 10);
        addDataSource("userDB", "io.agroal.pool.DataSource", 1, 4, 5, 5);

        DataSourceTab tab = new DataSourceTab(ctx);
        String rendered = TuiTestHelper.renderToString(tab, 140, 20);

        assertTrue(rendered.contains("orderDB"), "Should render first datasource name");
        assertTrue(rendered.contains("userDB"), "Should render second datasource name");
    }

    @Test
    void renderEmptyShowsPlaceholder() {
        // No datasources added
        DataSourceTab tab = new DataSourceTab(ctx);
        String rendered = TuiTestHelper.renderToString(tab, 140, 20);

        assertTrue(rendered.contains("No DataSources"), "Should show placeholder when no datasources exist");
    }

    @Test
    void renderNoSelectionShowsPrompt() {
        ctx.selectedPid = null;

        DataSourceTab tab = new DataSourceTab(ctx);
        String rendered = TuiTestHelper.renderToString(tab, 100, 20);

        assertTrue(rendered.contains("No integration selected") || rendered.contains("Select an integration"),
                "Should show selection prompt when no integration selected");
    }

    @Test
    void sortCycleChangesSortIndicator() {
        addDataSource("myDS", "com.zaxxer.hikari.HikariDataSource", 3, 7, 10, 10);

        DataSourceTab tab = new DataSourceTab(ctx);

        // Press 's' to cycle sort from "name" to "pool"
        tab.handleKeyEvent(KeyEvent.ofChar('s', KeyModifiers.NONE));
        String rendered = TuiTestHelper.renderToString(tab, 140, 20);

        assertTrue(rendered.contains("POOL▼"), "Sort should cycle to 'pool' after pressing 's'");
    }

    @Test
    void renderFooterHints() {
        DataSourceTab tab = new DataSourceTab(ctx);
        List<Span> footerSpans = new ArrayList<>();
        tab.renderFooter(footerSpans);

        String footer = footerSpans.stream()
                .map(Span::content)
                .reduce("", String::concat);

        assertTrue(footer.contains("Esc"), "Footer should contain Esc hint");
        assertTrue(footer.contains("sort"), "Footer should contain sort hint");
    }

    // ---- Helper methods ----

    private DataSourceInfo addDataSource(String name, String type, int active, int idle, int total, int maxPoolSize) {
        DataSourceInfo ds = new DataSourceInfo();
        ds.name = name;
        ds.type = type;
        ds.active = active;
        ds.idle = idle;
        ds.total = total;
        ds.maxPoolSize = maxPoolSize;
        info.dataSources.add(ds);
        return ds;
    }
}
