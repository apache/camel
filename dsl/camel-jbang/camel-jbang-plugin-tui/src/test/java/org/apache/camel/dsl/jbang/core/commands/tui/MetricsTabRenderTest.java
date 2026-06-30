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
 * Higher-level rendering tests for {@link MetricsTab}. Renders the metrics tab into a virtual terminal buffer and
 * inspects the rendered output.
 */
class MetricsTabRenderTest {

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

        MetricsTab tab = new MetricsTab(ctx);
        String rendered = TuiTestHelper.renderToString(tab, 100, 10);

        assertTrue(rendered.contains("No integration selected") || rendered.contains("Select an integration"),
                "Should show selection prompt when no integration selected");
    }

    @Test
    void renderEmptyShowsPlaceholder() {
        MetricsTab tab = new MetricsTab(ctx);
        String rendered = TuiTestHelper.renderToString(tab, 140, 10);

        assertTrue(rendered.contains("No metrics") || rendered.contains("--observe"),
                "Should show placeholder when no metrics exist");
    }

    @Test
    void renderShowsMetricData() {
        addMeter("counter", "camel.exchanges.total", 42L);

        MetricsTab tab = new MetricsTab(ctx);
        // Switch to table mode to see individual metrics
        tab.handleKeyEvent(KeyEvent.ofChar('d', KeyModifiers.NONE));
        String rendered = TuiTestHelper.renderToString(tab, 140, 20);

        assertTrue(rendered.contains("camel.exchanges.total"),
                "Should render the metric name");
    }

    @Test
    void renderShowsTableHeaders() {
        addMeter("counter", "camel.exchanges.total", 10L);

        MetricsTab tab = new MetricsTab(ctx);
        // Switch to table mode
        tab.handleKeyEvent(KeyEvent.ofChar('d', KeyModifiers.NONE));
        String rendered = TuiTestHelper.renderToString(tab, 140, 20);

        assertTrue(rendered.contains("TYPE"), "Should show TYPE header");
        assertTrue(rendered.contains("NAME"), "Should show NAME header");
    }

    @Test
    void renderMetricNameInCyan() {
        // Add two metrics: the first will be auto-selected (highlighted with WHITE fg),
        // but the second row retains its original CYAN color for the metric name.
        addMeter("counter", "some.other.metric", 1L);
        addMeter("counter", "camel.exchanges.total", 5L);

        MetricsTab tab = new MetricsTab(ctx);
        // Switch to table mode
        tab.handleKeyEvent(KeyEvent.ofChar('d', KeyModifiers.NONE));

        Rect area = new Rect(0, 0, 140, 20);
        Buffer buffer = Buffer.empty(area);
        Frame frame = Frame.forTesting(buffer);
        tab.render(frame, area);

        // Use "." which only appears in the metric name (e.g., "camel.exchanges.total"),
        // not in the type label "counter".
        boolean foundCyan = TuiTestHelper.findCellWithColor(buffer, ".", Color.CYAN);
        assertTrue(foundCyan, "Metric name should use CYAN color");
    }

    @Test
    void renderMultipleMetricsAllAppear() {
        addMeter("counter", "camel.exchanges.total", 100L);
        addMeter("gauge", "jvm.memory.used", 50L);

        MetricsTab tab = new MetricsTab(ctx);
        // Switch to table mode
        tab.handleKeyEvent(KeyEvent.ofChar('d', KeyModifiers.NONE));
        String rendered = TuiTestHelper.renderToString(tab, 140, 20);

        assertTrue(rendered.contains("camel.exchanges.total"), "Should render first metric name");
        assertTrue(rendered.contains("jvm.memory.used"), "Should render second metric name");
    }

    @Test
    void renderFooterHints() {
        MetricsTab tab = new MetricsTab(ctx);
        List<Span> footerSpans = new ArrayList<>();
        tab.renderFooter(footerSpans);

        String footer = footerSpans.stream()
                .map(Span::content)
                .reduce("", String::concat);

        assertTrue(footer.contains("Esc"), "Footer should contain Esc hint");
        assertTrue(footer.contains("d"), "Footer should contain 'd' toggle hint");
    }

    @Test
    void renderShowsMetricType() {
        addMeter("counter", "camel.exchanges.total", 10L);

        MetricsTab tab = new MetricsTab(ctx);
        // Switch to table mode
        tab.handleKeyEvent(KeyEvent.ofChar('d', KeyModifiers.NONE));
        String rendered = TuiTestHelper.renderToString(tab, 140, 20);

        assertTrue(rendered.contains("counter"), "Should render the metric type 'counter'");
    }

    // ---- Helper methods ----

    private MicrometerMeterInfo addMeter(String type, String name, Long count) {
        MicrometerMeterInfo m = new MicrometerMeterInfo();
        m.type = type;
        m.name = name;
        m.count = count;
        info.meters.add(m);
        return m;
    }

}
