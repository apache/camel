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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Higher-level rendering tests for {@link ErrorsTab}. Renders the errors table into a virtual terminal buffer and
 * inspects the rendered output.
 */
class ErrorsTabRenderTest {

    private MonitorContext ctx;
    private IntegrationInfo info;

    @BeforeEach
    void setUp() {
        Theme.resetForTesting();
        info = new IntegrationInfo();
        info.pid = "9999";
        info.name = "error-test-app";

        AtomicReference<List<IntegrationInfo>> data = new AtomicReference<>(List.of(info));
        AtomicReference<List<InfraInfo>> infraData = new AtomicReference<>(List.of());
        ctx = new MonitorContext(data, infraData);
        ctx.selectedPid = "9999";
    }

    @Test
    void renderErrorsShowsTableHeaders() {
        addError("ID-001", "route1", "to1", "IOException", "Connection refused", false);

        ErrorsTab tab = new ErrorsTab(ctx);
        String rendered = renderToString(tab, 160, 30);

        assertTrue(rendered.contains("ID"), "Should show ID header");
        assertTrue(rendered.contains("ROUTE"), "Should show ROUTE header");
        assertTrue(rendered.contains("NODE"), "Should show NODE header");
        assertTrue(rendered.contains("HANDLED"), "Should show HANDLED header");
        assertTrue(rendered.contains("EXCEPTION"), "Should show EXCEPTION header");
        assertTrue(rendered.contains("MESSAGE"), "Should show MESSAGE header");
    }

    @Test
    void renderErrorShowsExchangeId() {
        addError("ID-myhost-1234", "route1", "to1", "IOException", "timeout", false);

        ErrorsTab tab = new ErrorsTab(ctx);
        String rendered = renderToString(tab, 160, 30);

        assertTrue(rendered.contains("ID-myhost-1234"), "Should render the exchange ID");
    }

    @Test
    void renderErrorShowsShortExceptionName() {
        addError("ID-001", "route1", "to1", "java.io.IOException", "Connection refused", false);

        ErrorsTab tab = new ErrorsTab(ctx);
        String rendered = renderToString(tab, 160, 30);

        assertTrue(rendered.contains("IOException"), "Should show short exception type name");
    }

    @Test
    void renderErrorRouteIdInCyan() {
        addError("ID-001", "my-route", "to1", "Exception", "fail", false);

        ErrorsTab tab = new ErrorsTab(ctx);

        Rect area = new Rect(0, 0, 160, 30);
        Buffer buffer = Buffer.empty(area);
        Frame frame = Frame.forTesting(buffer);
        tab.render(frame, area);

        boolean foundCyan = findCellWithColor(buffer, "m", Theme.accent());
        assertTrue(foundCyan, "Route ID should use CYAN color");
    }

    @Test
    void renderHandledTrueUsesGreenColor() {
        addError("ID-001", "route1", "to1", "Exception", "handled", true);

        ErrorsTab tab = new ErrorsTab(ctx);

        Rect area = new Rect(0, 0, 160, 30);
        Buffer buffer = Buffer.empty(area);
        Frame frame = Frame.forTesting(buffer);
        tab.render(frame, area);

        boolean foundGreen = findCellWithColor(buffer, "t", Theme.success().fg().orElse(Color.GREEN));
        assertTrue(foundGreen, "handled=true should be rendered in GREEN");
    }

    @Test
    void renderHandledFalseUsesRedColor() {
        addError("ID-001", "route1", "to1", "Exception", "unhandled", false);

        ErrorsTab tab = new ErrorsTab(ctx);

        Rect area = new Rect(0, 0, 160, 30);
        Buffer buffer = Buffer.empty(area);
        Frame frame = Frame.forTesting(buffer);
        tab.render(frame, area);

        boolean foundRed = findCellWithColor(buffer, "f", Theme.error().fg().orElse(Color.LIGHT_RED));
        assertTrue(foundRed, "handled=false should be rendered in LIGHT_RED");
    }

    @Test
    void renderMultipleErrorsAllAppear() {
        addError("ID-AAA", "route-a", "to1", "IOException", "fail1", false);
        addError("ID-BBB", "route-b", "to2", "NullPointerException", "fail2", true);

        ErrorsTab tab = new ErrorsTab(ctx);
        String rendered = renderToString(tab, 160, 30);

        assertTrue(rendered.contains("ID-AAA"), "Should render first error");
        assertTrue(rendered.contains("ID-BBB"), "Should render second error");
        assertTrue(rendered.contains("IOException"), "Should render first exception type");
        assertTrue(rendered.contains("NullPointerException"), "Should render second exception type");
    }

    @Test
    void renderEmptyErrorsShowsPlaceholder() {
        ErrorsTab tab = new ErrorsTab(ctx);
        String rendered = renderToString(tab, 160, 20);

        assertTrue(rendered.contains("No errors captured"),
                "Should show placeholder when no errors exist");
    }

    @Test
    void renderShowsErrorCount() {
        addError("ID-001", "r1", "n1", "Ex", "m1", false);
        addError("ID-002", "r2", "n2", "Ex", "m2", false);

        ErrorsTab tab = new ErrorsTab(ctx);
        String rendered = renderToString(tab, 160, 30);

        assertTrue(rendered.contains("Errors [2]"), "Title should show error count");
    }

    @Test
    void renderNoSelectionShowsPrompt() {
        ctx.selectedPid = null;

        ErrorsTab tab = new ErrorsTab(ctx);
        String rendered = renderToString(tab, 120, 15);

        assertTrue(rendered.contains("No integration selected") || rendered.contains("Select an integration"),
                "Should show selection prompt when no integration selected");
    }

    @Test
    void renderShowsSortColumn() {
        addError("ID-001", "r1", "n1", "Ex", "msg", false);

        ErrorsTab tab = new ErrorsTab(ctx);
        String rendered = renderToString(tab, 160, 30);

        assertTrue(rendered.contains("ID▼"), "Column header should show sort indicator on default sort column");
    }

    @Test
    void sortCycleChangesSortIndicator() {
        addError("ID-001", "r1", "n1", "Ex", "msg", false);

        ErrorsTab tab = new ErrorsTab(ctx);

        // Press 's' to cycle sort
        tab.handleKeyEvent(KeyEvent.ofChar('s', KeyModifiers.NONE));

        String rendered = renderToString(tab, 160, 30);
        assertTrue(rendered.contains("AGO▼"), "Sort should cycle to 'age'");
    }

    @Test
    void handledFilterCycleFiltersErrors() {
        addError("ID-001", "r1", "n1", "Ex", "unhandled", false);
        addError("ID-002", "r2", "n2", "Ex", "handled", true);

        ErrorsTab tab = new ErrorsTab(ctx);

        // Default shows all
        String all = renderToString(tab, 160, 30);
        assertTrue(all.contains("ID-001"), "All filter should show unhandled errors");
        assertTrue(all.contains("ID-002"), "All filter should show handled errors");

        // Press 'f' to filter to handled=true
        tab.handleKeyEvent(KeyEvent.ofChar('f', KeyModifiers.NONE));
        String handledOnly = renderToString(tab, 160, 30);
        assertTrue(handledOnly.contains("ID-002"), "handled filter should show handled error");
        assertFalse(handledOnly.contains("ID-001"), "handled filter should hide unhandled error");
    }

    @Test
    void renderFooterHintsContainExpectedKeys() {
        ErrorsTab tab = new ErrorsTab(ctx);
        List<Span> footerSpans = new ArrayList<>();
        tab.renderFooter(footerSpans);

        String footer = footerSpans.stream()
                .map(Span::content)
                .reduce("", String::concat);

        assertTrue(footer.contains("Esc"), "Footer should contain Esc hint");
        assertTrue(footer.contains("sort"), "Footer should contain sort hint");
        assertTrue(footer.contains("handled"), "Footer should contain handled filter hint");
        assertTrue(footer.contains("wrap"), "Footer should contain wrap hint");
    }

    @Test
    void renderErrorMessageContent() {
        addError("ID-001", "route1", "to1", "Exception", "Connection refused to host:8080", false);

        ErrorsTab tab = new ErrorsTab(ctx);
        String rendered = renderToString(tab, 160, 30);

        assertTrue(rendered.contains("Connection refused"), "Should render the error message");
    }

    // ---- Helper methods ----

    private void addError(
            String exchangeId, String routeId, String nodeId,
            String exceptionType, String message, boolean handled) {
        ErrorInfo ei = new ErrorInfo();
        ei.exchangeId = exchangeId;
        ei.routeId = routeId;
        ei.nodeId = nodeId;
        ei.exceptionType = exceptionType;
        ei.exceptionMessage = message;
        ei.handled = handled;
        ei.timestamp = System.currentTimeMillis();
        info.errors.add(ei);
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
