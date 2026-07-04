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
 * Higher-level rendering tests for {@link HttpTab}. Renders the HTTP services table into a virtual terminal buffer and
 * inspects the rendered output (text content, colors, layout).
 */
class HttpTabRenderTest {

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
        addHttpEndpoint("GET", "/api/users", "http://localhost:8080/api/users");

        HttpTab tab = new HttpTab(ctx);
        String rendered = TuiTestHelper.renderToString(tab, 140, 30);

        assertTrue(rendered.contains("METHOD"), "Should show METHOD header");
        assertTrue(rendered.contains("PATH"), "Should show PATH header");
        assertTrue(rendered.contains("TOTAL"), "Should show TOTAL header");
    }

    @Test
    void renderShowsHttpEndpointData() {
        addHttpEndpoint("GET", "/api/orders", "http://localhost:8080/api/orders");

        HttpTab tab = new HttpTab(ctx);
        String rendered = TuiTestHelper.renderToString(tab, 140, 30);

        assertTrue(rendered.contains("/api/orders"), "Should render endpoint path");
    }

    @Test
    void renderGetMethodInGreen() {
        addHttpEndpoint("GET", "/api/health", "http://localhost:8080/api/health");

        HttpTab tab = new HttpTab(ctx);

        Rect area = new Rect(0, 0, 140, 30);
        Buffer buffer = Buffer.empty(area);
        Frame frame = Frame.forTesting(buffer);
        tab.render(frame, area);

        boolean foundGreen = TuiTestHelper.findCellWithColor(buffer, "G", Color.GREEN);
        assertTrue(foundGreen, "GET method should be rendered in GREEN");
    }

    @Test
    void renderPostMethodInYellow() {
        addHttpEndpoint("POST", "/api/users", "http://localhost:8080/api/users");

        HttpTab tab = new HttpTab(ctx);

        Rect area = new Rect(0, 0, 140, 30);
        Buffer buffer = Buffer.empty(area);
        Frame frame = Frame.forTesting(buffer);
        tab.render(frame, area);

        boolean foundYellow = TuiTestHelper.findCellWithColor(buffer, "P", Color.YELLOW);
        assertTrue(foundYellow, "POST method should be rendered in YELLOW");
    }

    @Test
    void renderDeleteMethodInRed() {
        addHttpEndpoint("DELETE", "/api/users/1", "http://localhost:8080/api/users/1");

        HttpTab tab = new HttpTab(ctx);

        Rect area = new Rect(0, 0, 140, 30);
        Buffer buffer = Buffer.empty(area);
        Frame frame = Frame.forTesting(buffer);
        tab.render(frame, area);

        boolean foundRed = TuiTestHelper.findCellWithColor(buffer, "D", Color.LIGHT_RED);
        assertTrue(foundRed, "DELETE method should be rendered in LIGHT_RED");
    }

    @Test
    void renderMultipleEndpointsAllAppear() {
        addHttpEndpoint("GET", "/api/users", "http://localhost:8080/api/users");
        addHttpEndpoint("POST", "/api/orders", "http://localhost:8080/api/orders");

        HttpTab tab = new HttpTab(ctx);
        String rendered = TuiTestHelper.renderToString(tab, 140, 30);

        assertTrue(rendered.contains("/api/users"), "Should render first endpoint path");
        assertTrue(rendered.contains("/api/orders"), "Should render second endpoint path");
    }

    @Test
    void renderNoSelectionShowsPrompt() {
        ctx.selectedPid = null;

        HttpTab tab = new HttpTab(ctx);
        String rendered = TuiTestHelper.renderToString(tab, 100, 20);

        assertTrue(rendered.contains("No integration selected") || rendered.contains("Select an integration"),
                "Should show selection prompt when no integration selected");
    }

    @Test
    void renderShowsSortColumn() {
        addHttpEndpoint("GET", "/api/users", "http://localhost:8080/api/users");

        HttpTab tab = new HttpTab(ctx);
        String rendered = TuiTestHelper.renderToString(tab, 140, 30);

        assertTrue(rendered.contains("METHOD▼"), "Column header should show sort indicator on default sort column");
    }

    @Test
    void sortCycleChangesSortIndicator() {
        addHttpEndpoint("GET", "/api/users", "http://localhost:8080/api/users");

        HttpTab tab = new HttpTab(ctx);

        // Press 's' to cycle sort from "method" to "path"
        tab.handleKeyEvent(KeyEvent.ofChar('s', KeyModifiers.NONE));
        String rendered = TuiTestHelper.renderToString(tab, 140, 30);

        assertTrue(rendered.contains("PATH▼"), "Sort should cycle to 'path' after pressing 's'");
    }

    @Test
    void renderFooterHints() {
        HttpTab tab = new HttpTab(ctx);
        List<Span> footerSpans = new ArrayList<>();
        tab.renderFooter(footerSpans);

        String footer = footerSpans.stream()
                .map(Span::content)
                .reduce("", String::concat);

        assertTrue(footer.contains("Esc"), "Footer should contain Esc hint");
        assertTrue(footer.contains("sort"), "Footer should contain sort hint");
        assertTrue(footer.contains("probe"), "Footer should contain probe hint");
    }

    @Test
    void renderShowsEndpointCount() {
        addHttpEndpoint("GET", "/api/users", "http://localhost:8080/api/users");

        HttpTab tab = new HttpTab(ctx);
        String rendered = TuiTestHelper.renderToString(tab, 140, 30);

        assertTrue(rendered.contains("[1]"), "Title should contain endpoint count '[1]'");
    }

    // ---- Helper methods ----

    private HttpEndpointInfo addHttpEndpoint(String method, String path, String url) {
        HttpEndpointInfo ep = new HttpEndpointInfo();
        ep.method = method;
        ep.path = path;
        ep.url = url;
        ep.state = "Started";
        info.httpEndpoints.add(ep);
        return ep;
    }
}
