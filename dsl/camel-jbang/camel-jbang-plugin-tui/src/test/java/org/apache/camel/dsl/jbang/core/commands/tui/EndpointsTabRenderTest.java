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
 * Higher-level rendering tests for {@link EndpointsTab}. Renders the endpoints table into a virtual terminal buffer and
 * inspects the rendered output (text content, colors, layout).
 */
class EndpointsTabRenderTest {

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
        addEndpoint("timer", "timer://tick?period=1000", "in", "route1", 10);

        EndpointsTab tab = new EndpointsTab(ctx, new MetricsCollector());
        String rendered = TuiTestHelper.renderToString(tab, 140, 30);

        assertTrue(rendered.contains("COMPONENT"), "Should show COMPONENT header");
        assertTrue(rendered.contains("ROUTE"), "Should show ROUTE header");
        assertTrue(rendered.contains("DIR"), "Should show DIR header");
        assertTrue(rendered.contains("TOTAL"), "Should show TOTAL header");
        assertTrue(rendered.contains("URI"), "Should show URI header");
    }

    @Test
    void renderShowsEndpointData() {
        addEndpoint("kafka", "kafka://my-topic", "in", "kafka-route", 42);

        EndpointsTab tab = new EndpointsTab(ctx, new MetricsCollector());
        String rendered = TuiTestHelper.renderToString(tab, 140, 30);

        assertTrue(rendered.contains("kafka://my-topic"), "Should render endpoint URI");
        assertTrue(rendered.contains("kafka"), "Should render component name");
        assertTrue(rendered.contains("kafka-route"), "Should render route ID");
    }

    @Test
    void renderComponentNameInCyan() {
        addEndpoint("http", "http://example.com/api", "out", "http-route", 5);

        EndpointsTab tab = new EndpointsTab(ctx, new MetricsCollector());

        Rect area = new Rect(0, 0, 140, 30);
        Buffer buffer = Buffer.empty(area);
        Frame frame = Frame.forTesting(buffer);
        tab.render(frame, area);

        boolean foundCyan = TuiTestHelper.findCellWithColor(buffer, "h", Color.CYAN);
        assertTrue(foundCyan, "Component name should be rendered in CYAN");
    }

    @Test
    void renderInDirectionColor() {
        addEndpoint("timer", "timer://tick", "in", "route1", 10);

        EndpointsTab tab = new EndpointsTab(ctx, new MetricsCollector());

        Rect area = new Rect(0, 0, 140, 30);
        Buffer buffer = Buffer.empty(area);
        Frame frame = Frame.forTesting(buffer);
        tab.render(frame, area);

        boolean foundGreen
                = TuiTestHelper.findCellWithColor(buffer, TuiIcons.KEY_RIGHT, Color.GREEN);
        assertTrue(foundGreen, "In-direction arrow should be rendered in GREEN");
    }

    @Test
    void renderOutDirectionInCyan() {
        addEndpoint("log", "log://output", "out", "route1", 10);

        EndpointsTab tab = new EndpointsTab(ctx, new MetricsCollector());

        Rect area = new Rect(0, 0, 140, 30);
        Buffer buffer = Buffer.empty(area);
        Frame frame = Frame.forTesting(buffer);
        tab.render(frame, area);

        boolean foundCyanArrow = TuiTestHelper.findCellWithColor(buffer, TuiIcons.KEY_LEFT, Color.CYAN);
        assertTrue(foundCyanArrow, "Out-direction arrow should be rendered in CYAN");
    }

    @Test
    void renderMultipleEndpointsAllAppear() {
        addEndpoint("timer", "timer://tick", "in", "route1", 10);
        addEndpoint("log", "log://output", "out", "route1", 10);

        EndpointsTab tab = new EndpointsTab(ctx, new MetricsCollector());
        String rendered = TuiTestHelper.renderToString(tab, 140, 30);

        assertTrue(rendered.contains("timer://tick"), "Should render first endpoint URI");
        assertTrue(rendered.contains("log://output"), "Should render second endpoint URI");
    }

    @Test
    void renderEmptyShowsPlaceholder() {
        // No endpoints added
        EndpointsTab tab = new EndpointsTab(ctx, new MetricsCollector());
        String rendered = TuiTestHelper.renderToString(tab, 140, 30);

        assertTrue(rendered.contains("No endpoints"), "Should show placeholder when no endpoints exist");
    }

    @Test
    void renderNoSelectionShowsPrompt() {
        ctx.selectedPid = null;

        EndpointsTab tab = new EndpointsTab(ctx, new MetricsCollector());
        String rendered = TuiTestHelper.renderToString(tab, 100, 20);

        assertTrue(rendered.contains("No integration selected") || rendered.contains("Select an integration"),
                "Should show selection prompt when no integration selected");
    }

    @Test
    void renderShowsSortColumn() {
        addEndpoint("timer", "timer://tick", "in", "route1", 10);

        EndpointsTab tab = new EndpointsTab(ctx, new MetricsCollector());
        String rendered = TuiTestHelper.renderToString(tab, 140, 30);

        assertTrue(rendered.contains("ROUTE▼"), "Column header should show sort indicator on default sort column");
    }

    @Test
    void sortCycleChangesSortIndicator() {
        addEndpoint("timer", "timer://tick", "in", "route1", 10);

        EndpointsTab tab = new EndpointsTab(ctx, new MetricsCollector());

        // Press 's' to cycle sort from "route" to "dir"
        tab.handleKeyEvent(KeyEvent.ofChar('s', KeyModifiers.NONE));
        String rendered = TuiTestHelper.renderToString(tab, 140, 30);

        assertTrue(rendered.contains("DIR▼"), "Sort should cycle to 'dir' after pressing 's'");
    }

    @Test
    void renderFooterHints() {
        EndpointsTab tab = new EndpointsTab(ctx, new MetricsCollector());
        List<Span> footerSpans = new ArrayList<>();
        tab.renderFooter(footerSpans);

        String footer = footerSpans.stream()
                .map(Span::content)
                .reduce("", String::concat);

        assertTrue(footer.contains("Esc"), "Footer should contain Esc hint");
        assertTrue(footer.contains("sort"), "Footer should contain sort hint");
        assertTrue(footer.contains("filter"), "Footer should contain filter hint");
    }

    // ---- Helper methods ----

    private EndpointInfo addEndpoint(String component, String uri, String direction, String routeId, long hits) {
        EndpointInfo ep = new EndpointInfo();
        ep.component = component;
        ep.uri = uri;
        ep.direction = direction;
        ep.routeId = routeId;
        ep.hits = hits;
        info.endpoints.add(ep);
        return ep;
    }
}
