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

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import dev.tamboui.widgets.block.BorderType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validates that TUI tabs render with proper rounded borders separating panes. These tests render each tab into a
 * virtual terminal buffer and verify that the expected border box-drawing characters (╭ ╮ ╰ ╯ │ ─) appear in the
 * correct positions.
 */
class BorderRenderTest {

    // Rounded border characters used by BorderType.ROUNDED
    private static final String TOP_LEFT = "╭";
    private static final String TOP_RIGHT = "╮";
    private static final String BOTTOM_LEFT = "╰";
    private static final String BOTTOM_RIGHT = "╯";
    private static final String VERTICAL = "│";
    private static final String HORIZONTAL = "─";

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

    // ---- Border character verification for HealthTab ----

    @Test
    void healthTabRendersTopBorderCorners() {
        addHealthCheck("camel", "context", "UP");
        HealthTab tab = new HealthTab(ctx);

        Buffer buffer = renderToBuffer(tab, 120, 20);

        assertTrue(containsSymbol(buffer, TOP_LEFT),
                "HealthTab should render top-left rounded border corner ╭");
        assertTrue(containsSymbol(buffer, TOP_RIGHT),
                "HealthTab should render top-right rounded border corner ╮");
    }

    @Test
    void healthTabRendersBottomBorderCorners() {
        addHealthCheck("camel", "context", "UP");
        HealthTab tab = new HealthTab(ctx);

        Buffer buffer = renderToBuffer(tab, 120, 20);

        assertTrue(containsSymbol(buffer, BOTTOM_LEFT),
                "HealthTab should render bottom-left rounded border corner ╰");
        assertTrue(containsSymbol(buffer, BOTTOM_RIGHT),
                "HealthTab should render bottom-right rounded border corner ╯");
    }

    @Test
    void healthTabRendersVerticalBorders() {
        addHealthCheck("camel", "context", "UP");
        HealthTab tab = new HealthTab(ctx);

        Buffer buffer = renderToBuffer(tab, 120, 20);

        assertTrue(containsSymbol(buffer, VERTICAL),
                "HealthTab should render vertical border │");
    }

    @Test
    void healthTabRendersHorizontalBorders() {
        addHealthCheck("camel", "context", "UP");
        HealthTab tab = new HealthTab(ctx);

        Buffer buffer = renderToBuffer(tab, 120, 20);

        assertTrue(containsSymbol(buffer, HORIZONTAL),
                "HealthTab should render horizontal border ─");
    }

    @Test
    void healthTabBorderFormsCompleteBox() {
        addHealthCheck("camel", "context", "UP");
        HealthTab tab = new HealthTab(ctx);

        Buffer buffer = renderToBuffer(tab, 120, 20);

        // Top row should have ╭ on the left and ╮ on the right
        assertTrue(rowContainsSymbol(buffer, 0, TOP_LEFT),
                "Top row should contain ╭");
        assertTrue(rowContainsSymbol(buffer, 0, TOP_RIGHT),
                "Top row should contain ╮");

        // Bottom row should have ╰ and ╯
        int lastRow = buffer.height() - 1;
        assertTrue(rowContainsSymbol(buffer, lastRow, BOTTOM_LEFT),
                "Bottom row should contain ╰");
        assertTrue(rowContainsSymbol(buffer, lastRow, BOTTOM_RIGHT),
                "Bottom row should contain ╯");

        // Middle rows should have │ on both sides
        assertTrue(rowContainsSymbol(buffer, 1, VERTICAL),
                "Content rows should have vertical borders │");
    }

    @Test
    void healthTabLeftAndRightBordersAligned() {
        addHealthCheck("camel", "context", "UP");
        HealthTab tab = new HealthTab(ctx);

        Buffer buffer = renderToBuffer(tab, 120, 20);

        // Find the x-coordinates of the top-left corner
        int leftX = findSymbolX(buffer, 0, TOP_LEFT);
        int rightX = findSymbolX(buffer, 0, TOP_RIGHT);
        assertTrue(leftX >= 0, "Should find top-left corner");
        assertTrue(rightX >= 0, "Should find top-right corner");

        // The left vertical border should be at the same X as top-left corner
        for (int y = 1; y < buffer.height() - 1; y++) {
            String sym = buffer.get(leftX, y).symbol();
            assertTrue(VERTICAL.equals(sym),
                    "Left border at row " + y + " should be │ but was '" + sym + "'");
        }

        // The right vertical border should be at the same X as top-right corner
        for (int y = 1; y < buffer.height() - 1; y++) {
            String sym = buffer.get(rightX, y).symbol();
            assertTrue(VERTICAL.equals(sym),
                    "Right border at row " + y + " should be │ but was '" + sym + "'");
        }
    }

    // ---- Border character verification for RoutesTab ----

    @Test
    void routesTabRendersRoundedBorderBox() {
        addRoute("route1", "timer://tick", "Started");
        RoutesTab tab = new RoutesTab(ctx);

        Buffer buffer = renderToBuffer(tab, 140, 30);

        assertTrue(containsSymbol(buffer, TOP_LEFT), "RoutesTab should render ╭");
        assertTrue(containsSymbol(buffer, TOP_RIGHT), "RoutesTab should render ╮");
        assertTrue(containsSymbol(buffer, BOTTOM_LEFT), "RoutesTab should render ╰");
        assertTrue(containsSymbol(buffer, BOTTOM_RIGHT), "RoutesTab should render ╯");
        assertTrue(containsSymbol(buffer, VERTICAL), "RoutesTab should render │");
        assertTrue(containsSymbol(buffer, HORIZONTAL), "RoutesTab should render ─");
    }

    @Test
    void routesTabWithProcessorsHasTwoBorderBoxes() {
        RouteInfo route = addRoute("route1", "timer://tick", "Started");
        ProcessorInfo proc = new ProcessorInfo();
        proc.id = "log1";
        proc.processor = "log";
        proc.level = 1;
        proc.total = 50;
        route.processors.add(proc);

        RoutesTab tab = new RoutesTab(ctx);
        Buffer buffer = renderToBuffer(tab, 140, 30);

        // Count the number of ╭ corners — should be at least 2 (Routes block + Processors block)
        int topLeftCount = countSymbol(buffer, TOP_LEFT);
        assertTrue(topLeftCount >= 2,
                "RoutesTab with processors should have at least 2 border boxes, found " + topLeftCount + " ╭ corners");
    }

    @Test
    void routesTabProcessorsSectionHasSeparateBorder() {
        RouteInfo route = addRoute("route1", "timer://tick", "Started");
        ProcessorInfo proc = new ProcessorInfo();
        proc.id = "log1";
        proc.processor = "log";
        proc.level = 1;
        route.processors.add(proc);

        RoutesTab tab = new RoutesTab(ctx);
        String rendered = renderToString(tab, 140, 30);

        // The Processors section should have its own bordered block with title
        assertTrue(rendered.contains("Processors"),
                "Should render Processors section with border title");

        // Count ╰ (bottom-left corners) — at least 2 blocks
        Buffer buffer = renderToBuffer(tab, 140, 30);
        int bottomLeftCount = countSymbol(buffer, BOTTOM_LEFT);
        assertTrue(bottomLeftCount >= 2,
                "Should have at least 2 bottom-left corners (Routes + Processors), found " + bottomLeftCount);
    }

    // ---- Border character verification for ErrorsTab ----

    @Test
    void errorsTabRendersRoundedBorderBox() {
        addError("ID-001", "route1", "to1", "IOException", "Connection refused", false);
        ErrorsTab tab = new ErrorsTab(ctx);

        Buffer buffer = renderToBuffer(tab, 160, 30);

        assertTrue(containsSymbol(buffer, TOP_LEFT), "ErrorsTab should render ╭");
        assertTrue(containsSymbol(buffer, TOP_RIGHT), "ErrorsTab should render ╮");
        assertTrue(containsSymbol(buffer, BOTTOM_LEFT), "ErrorsTab should render ╰");
        assertTrue(containsSymbol(buffer, BOTTOM_RIGHT), "ErrorsTab should render ╯");
    }

    @Test
    void errorsTabEmptyStateHasBorder() {
        // No errors added
        ErrorsTab tab = new ErrorsTab(ctx);

        Buffer buffer = renderToBuffer(tab, 160, 20);

        assertTrue(containsSymbol(buffer, TOP_LEFT),
                "ErrorsTab empty state should still have border ╭");
        assertTrue(containsSymbol(buffer, VERTICAL),
                "ErrorsTab empty state should still have vertical border │");
    }

    // ---- Border character verification for no-selection state ----

    @Test
    void noSelectionRendersRoundedBorder() {
        ctx.selectedPid = null;

        HealthTab tab = new HealthTab(ctx);
        Buffer buffer = renderToBuffer(tab, 100, 10);

        // The "No integration selected" block should have rounded borders
        assertTrue(containsSymbol(buffer, VERTICAL),
                "No-selection block should have vertical borders │");
        assertTrue(containsSymbol(buffer, HORIZONTAL),
                "No-selection block should have horizontal borders ─");
    }

    // ---- Border type validation ----

    @Test
    void roundedBorderTypeUsesCorrectCharacters() {
        // Verify the border characters match what ROUNDED type produces
        var borderSet = BorderType.ROUNDED.set();

        assertTrue(borderSet.topLeft().equals(TOP_LEFT),
                "ROUNDED topLeft should be ╭, got: " + borderSet.topLeft());
        assertTrue(borderSet.topRight().equals(TOP_RIGHT),
                "ROUNDED topRight should be ╮, got: " + borderSet.topRight());
        assertTrue(borderSet.bottomLeft().equals(BOTTOM_LEFT),
                "ROUNDED bottomLeft should be ╰, got: " + borderSet.bottomLeft());
        assertTrue(borderSet.bottomRight().equals(BOTTOM_RIGHT),
                "ROUNDED bottomRight should be ╯, got: " + borderSet.bottomRight());
        assertTrue(borderSet.leftVertical().equals(VERTICAL),
                "ROUNDED leftVertical should be │, got: " + borderSet.leftVertical());
        assertTrue(borderSet.topHorizontal().equals(HORIZONTAL),
                "ROUNDED topHorizontal should be ─, got: " + borderSet.topHorizontal());
    }

    // ---- ShellPanel border test ----

    @Test
    void shellPanelRendersWithBorder() {
        ShellPanel panel = new ShellPanel();
        panel.setContext(ctx);
        panel.open(); // Must open before render — render returns early if !visible

        Rect area = new Rect(0, 0, 80, 10);
        Buffer buffer = Buffer.empty(area);
        Frame frame = Frame.forTesting(buffer);
        panel.render(frame, area);
        panel.destroy(); // Cleanup shell process

        assertTrue(containsSymbol(buffer, TOP_LEFT),
                "ShellPanel should render with rounded border ╭");
        assertTrue(containsSymbol(buffer, VERTICAL),
                "ShellPanel should render with vertical border │");

        String rendered = HealthTabRenderTest.bufferToString(buffer);
        assertTrue(rendered.contains("Shell"),
                "ShellPanel should show 'Shell' in border title");
    }

    // ---- Multiple adjacent panes maintain borders ----

    @Test
    void routesTabSplitPanesEachHaveOwnBorder() {
        RouteInfo route = addRoute("route1", "timer://tick", "Started");
        ProcessorInfo proc = new ProcessorInfo();
        proc.id = "log1";
        proc.processor = "log";
        proc.level = 1;
        route.processors.add(proc);

        RoutesTab tab = new RoutesTab(ctx);
        Buffer buffer = renderToBuffer(tab, 140, 30);

        // Find all rows that have ╭ (top border of a pane)
        int topBorderRowCount = 0;
        for (int y = 0; y < buffer.height(); y++) {
            if (rowContainsSymbol(buffer, y, TOP_LEFT)) {
                topBorderRowCount++;
            }
        }
        assertTrue(topBorderRowCount >= 2,
                "Adjacent panes should each start with their own top border row, found " + topBorderRowCount);

        // Find all rows that have ╰ (bottom border of a pane)
        int bottomBorderRowCount = 0;
        for (int y = 0; y < buffer.height(); y++) {
            if (rowContainsSymbol(buffer, y, BOTTOM_LEFT)) {
                bottomBorderRowCount++;
            }
        }
        assertTrue(bottomBorderRowCount >= 2,
                "Adjacent panes should each end with their own bottom border row, found " + bottomBorderRowCount);
    }

    @Test
    void routesTabPaneBordersDoNotOverlap() {
        RouteInfo route = addRoute("route1", "timer://tick", "Started");
        ProcessorInfo proc = new ProcessorInfo();
        proc.id = "log1";
        proc.processor = "log";
        proc.level = 1;
        route.processors.add(proc);

        RoutesTab tab = new RoutesTab(ctx);
        Buffer buffer = renderToBuffer(tab, 140, 30);

        // Find rows of the first ╰ (end of first pane) and second ╭ (start of second pane)
        int firstBottomRow = -1;
        int secondTopRow = -1;
        int topCount = 0;
        for (int y = 0; y < buffer.height(); y++) {
            if (rowContainsSymbol(buffer, y, TOP_LEFT)) {
                topCount++;
                if (topCount == 2) {
                    secondTopRow = y;
                }
            }
            if (firstBottomRow < 0 && rowContainsSymbol(buffer, y, BOTTOM_LEFT)) {
                firstBottomRow = y;
            }
        }

        if (firstBottomRow >= 0 && secondTopRow >= 0) {
            assertTrue(secondTopRow >= firstBottomRow,
                    "Second pane's top border (row " + secondTopRow
                                                       + ") should not start before first pane's bottom border (row "
                                                       + firstBottomRow + ")");
        }
    }

    @Test
    void allBorderCornersAppearOnBoundaryRows() {
        addHealthCheck("camel", "context", "UP");
        HealthTab tab = new HealthTab(ctx);

        Buffer buffer = renderToBuffer(tab, 120, 20);

        // ╭ and ╮ should be on the same row (top border)
        int topLeftRow = findSymbolRow(buffer, TOP_LEFT);
        int topRightRow = findSymbolRow(buffer, TOP_RIGHT);
        assertTrue(topLeftRow == topRightRow,
                "╭ (row " + topLeftRow + ") and ╮ (row " + topRightRow + ") should be on the same row");

        // ╰ and ╯ should be on the same row (bottom border)
        int bottomLeftRow = findSymbolRow(buffer, BOTTOM_LEFT);
        int bottomRightRow = findSymbolRow(buffer, BOTTOM_RIGHT);
        assertTrue(bottomLeftRow == bottomRightRow,
                "╰ (row " + bottomLeftRow + ") and ╯ (row " + bottomRightRow + ") should be on the same row");
    }

    // ---- Helper methods ----

    private void addHealthCheck(String group, String name, String state) {
        HealthCheckInfo hc = new HealthCheckInfo();
        hc.group = group;
        hc.name = name;
        hc.state = state;
        hc.readiness = true;
        info.healthChecks.add(hc);
    }

    private RouteInfo addRoute(String routeId, String from, String state) {
        RouteInfo route = new RouteInfo();
        route.routeId = routeId;
        route.from = from;
        route.state = state;
        route.uptime = "10s";
        info.routes.add(route);
        return route;
    }

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

    private static Buffer renderToBuffer(MonitorTab tab, int width, int height) {
        Rect area = new Rect(0, 0, width, height);
        Buffer buffer = Buffer.empty(area);
        Frame frame = Frame.forTesting(buffer);
        tab.render(frame, area);
        return buffer;
    }

    private static String renderToString(MonitorTab tab, int width, int height) {
        Buffer buffer = renderToBuffer(tab, width, height);
        return HealthTabRenderTest.bufferToString(buffer);
    }

    /**
     * Check if the buffer contains a cell with the given symbol anywhere.
     */
    private static boolean containsSymbol(Buffer buffer, String symbol) {
        for (int y = 0; y < buffer.height(); y++) {
            for (int x = 0; x < buffer.width(); x++) {
                if (symbol.equals(buffer.get(x, y).symbol())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Count occurrences of a symbol in the buffer.
     */
    private static int countSymbol(Buffer buffer, String symbol) {
        int count = 0;
        for (int y = 0; y < buffer.height(); y++) {
            for (int x = 0; x < buffer.width(); x++) {
                if (symbol.equals(buffer.get(x, y).symbol())) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * Check if a specific row contains the given symbol.
     */
    private static boolean rowContainsSymbol(Buffer buffer, int row, String symbol) {
        for (int x = 0; x < buffer.width(); x++) {
            if (symbol.equals(buffer.get(x, row).symbol())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Find the X coordinate of the first occurrence of a symbol in a row. Returns -1 if not found.
     */
    private static int findSymbolX(Buffer buffer, int row, String symbol) {
        for (int x = 0; x < buffer.width(); x++) {
            if (symbol.equals(buffer.get(x, row).symbol())) {
                return x;
            }
        }
        return -1;
    }

    /**
     * Find the first row containing the given symbol. Returns -1 if not found.
     */
    private static int findSymbolRow(Buffer buffer, String symbol) {
        for (int y = 0; y < buffer.height(); y++) {
            for (int x = 0; x < buffer.width(); x++) {
                if (symbol.equals(buffer.get(x, y).symbol())) {
                    return y;
                }
            }
        }
        return -1;
    }
}
