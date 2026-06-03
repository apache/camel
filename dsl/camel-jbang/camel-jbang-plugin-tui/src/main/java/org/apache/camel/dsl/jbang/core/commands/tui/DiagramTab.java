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

import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Layout;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.paragraph.Paragraph;
import org.apache.camel.util.json.JsonObject;

import static org.apache.camel.dsl.jbang.core.commands.tui.MonitorContext.*;

class DiagramTab implements MonitorTab {

    private final MonitorContext ctx;
    private final DiagramSupport diagram = new DiagramSupport();
    private boolean diagramMetrics = true;
    private boolean showExternal;
    private boolean topologyMode = true;
    private String drillDownRouteId;

    DiagramTab(MonitorContext ctx) {
        this.ctx = ctx;
    }

    boolean isShowDiagram() {
        return diagram.isShowDiagram();
    }

    @Override
    public boolean handleKeyEvent(KeyEvent ke) {
        // Node selection navigation in topology mode
        if (topologyMode && diagram.isShowDiagram() && diagram.hasDiagramData()
                && !diagram.getNodeBoxes().isEmpty()) {
            if (ke.isUp()) {
                diagram.selectNodeUp();
                diagram.scrollToSelectedNode();
                return true;
            }
            if (ke.isDown()) {
                diagram.selectNodeDown();
                diagram.scrollToSelectedNode();
                return true;
            }
            if (ke.isLeft()) {
                diagram.selectNodeLeft();
                diagram.scrollToSelectedNode();
                return true;
            }
            if (ke.isRight()) {
                diagram.selectNodeRight();
                diagram.scrollToSelectedNode();
                return true;
            }
        }

        if (diagram.handleScrollKeys(ke)) {
            return true;
        }

        // Toggle metrics
        if (diagram.isShowDiagram() && ke.isCharIgnoreCase('m')) {
            diagramMetrics = !diagramMetrics;
            diagram.endLoad();
            reloadDiagram();
            return true;
        }

        // Toggle external systems
        if (diagram.isShowDiagram() && topologyMode && ke.isCharIgnoreCase('e')) {
            showExternal = !showExternal;
            diagram.endLoad();
            reloadDiagram();
            return true;
        }

        // Toggle description
        if (diagram.isShowDiagram() && ke.isCharIgnoreCase('n')) {
            diagram.setShowDescription(!diagram.isShowDescription());
            diagram.endLoad();
            reloadDiagram();
            return true;
        }

        // Drill down into route diagram (Enter)
        if (topologyMode && ke.isConfirm()) {
            String selectedRouteId = diagram.getSelectedRouteId();
            if (selectedRouteId != null) {
                IntegrationInfo info = ctx.findSelectedIntegration();
                if (info != null && info.routes.stream().anyMatch(r -> selectedRouteId.equals(r.routeId))) {
                    drillDownRouteId = selectedRouteId;
                    topologyMode = false;
                    diagram.setTopologyMode(false);
                    diagram.endLoad();
                    reloadDiagram();
                }
            }
            return true;
        }

        return false;
    }

    @Override
    public boolean handleEscape() {
        if (!topologyMode) {
            diagram.setPendingSelectionRouteId(drillDownRouteId);
            topologyMode = true;
            diagram.setTopologyMode(true);
            diagram.endLoad();
            reloadDiagram();
            return true;
        }
        return diagram.handleEscape();
    }

    @Override
    public void navigateUp() {
        // Scroll diagram up
    }

    @Override
    public void navigateDown() {
        // Scroll diagram down
    }

    @Override
    public void onTabSelected() {
        if (!diagram.isShowDiagram()) {
            diagram.toggleTextDiagram(this::loadDiagram);
        }
    }

    @Override
    public void onIntegrationChanged() {
        topologyMode = true;
        drillDownRouteId = null;
        diagram.reset();
        diagram.setTopologyMode(true);
    }

    @Override
    public void render(Frame frame, Rect area) {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null) {
            renderNoSelection(frame, area);
            return;
        }

        if (diagram.isShowDiagram() && diagram.hasDiagramData()) {
            String title;
            if (topologyMode) {
                title = " Topology ";
            } else {
                title = " Route [" + drillDownRouteId + "] ";
            }

            String selectedRouteId = topologyMode ? diagram.getSelectedRouteId() : drillDownRouteId;
            if (selectedRouteId != null && area.width() > 60) {
                int panelWidth = 30;
                List<Rect> hChunks = Layout.horizontal()
                        .constraints(Constraint.length(panelWidth), Constraint.fill())
                        .split(area);
                renderInfoPanel(frame, hChunks.get(0), info, selectedRouteId);
                diagram.renderDiagram(frame, hChunks.get(1), title);
            } else {
                diagram.renderDiagram(frame, area, title);
            }
            return;
        }

        // Show placeholder when no diagram is loaded yet
        frame.renderWidget(
                Paragraph.builder()
                        .text(Text.from(Line.from(Span.styled(
                                "Loading diagram...",
                                Style.EMPTY.dim()))))
                        .block(Block.builder().borderType(BorderType.ROUNDED)
                                .title(" Diagram ").build())
                        .build(),
                area);
    }

    private void renderInfoPanel(Frame frame, Rect area, IntegrationInfo info, String routeId) {
        RouteInfo route = null;
        for (RouteInfo r : info.routes) {
            if (routeId.equals(r.routeId)) {
                route = r;
                break;
            }
        }

        List<Line> lines = new ArrayList<>();
        if (route != null) {
            lines.add(Line.from(
                    Span.styled(" Route: ", Style.EMPTY.fg(Color.YELLOW).bold()),
                    Span.styled(route.routeId, Style.EMPTY.fg(Color.WHITE).bold())));
            lines.add(Line.from(
                    Span.styled(" From:  ", Style.EMPTY.dim()),
                    Span.raw(route.from != null ? route.from : "")));
            String stateLabel = route.state != null ? route.state : "";
            Style stateStyle = "Started".equals(route.state) ? Style.EMPTY.fg(Color.GREEN) : Style.EMPTY.fg(Color.LIGHT_RED);
            lines.add(Line.from(
                    Span.styled(" State: ", Style.EMPTY.dim()),
                    Span.styled(stateLabel, stateStyle)));

            lines.add(Line.from(Span.raw("")));
            lines.add(Line.from(
                    Span.styled(" Uptime:     ", Style.EMPTY.dim()),
                    Span.raw(route.uptime != null ? route.uptime : "")));
            lines.add(Line.from(
                    Span.styled(" Throughput: ", Style.EMPTY.dim()),
                    Span.raw(route.throughput != null ? route.throughput : "")));

            lines.add(Line.from(Span.raw("")));
            lines.add(Line.from(
                    Span.styled(" Total:    ", Style.EMPTY.dim()),
                    Span.raw(String.valueOf(route.total))));
            Style failStyle = route.failed > 0 ? Style.EMPTY.fg(Color.LIGHT_RED).bold() : Style.EMPTY;
            lines.add(Line.from(
                    Span.styled(" Failed:   ", Style.EMPTY.dim()),
                    Span.styled(String.valueOf(route.failed), failStyle)));
            lines.add(Line.from(
                    Span.styled(" Inflight: ", Style.EMPTY.dim()),
                    Span.raw(String.valueOf(route.inflight))));

            lines.add(Line.from(Span.raw("")));
            if (route.total > 0) {
                lines.add(Line.from(
                        Span.styled(" Mean: ", Style.EMPTY.dim()),
                        Span.raw(route.meanTime + " ms")));
                lines.add(Line.from(
                        Span.styled(" Max:  ", Style.EMPTY.dim()),
                        Span.raw(route.maxTime + " ms")));
                lines.add(Line.from(
                        Span.styled(" Min:  ", Style.EMPTY.dim()),
                        Span.raw(route.minTime + " ms")));
            }

            if (route.coverage != null) {
                lines.add(Line.from(Span.raw("")));
                lines.add(Line.from(
                        Span.styled(" Coverage: ", Style.EMPTY.dim()),
                        Span.raw(route.coverage)));
            }
        } else {
            // External endpoint — show topology node data
            var topoNode = diagram.getSelectedTopologyNode();
            if (topoNode != null) {
                boolean isInbound = "external-in".equals(topoNode.nodeType);
                lines.add(Line.from(
                        Span.styled(isInbound ? " Inbound" : " Outbound",
                                Style.EMPTY.fg(Color.CYAN).bold())));
                lines.add(Line.from(Span.raw("")));
                lines.add(Line.from(
                        Span.styled(" URI: ", Style.EMPTY.dim()),
                        Span.raw(topoNode.from != null ? topoNode.from : "")));
                if (topoNode.description != null && !topoNode.description.isBlank()) {
                    lines.add(Line.from(
                            Span.styled(" Path: ", Style.EMPTY.dim()),
                            Span.raw(topoNode.description)));
                }
                String connectedRoute = diagram.getConnectedRouteId(routeId);
                if (connectedRoute != null) {
                    lines.add(Line.from(Span.raw("")));
                    lines.add(Line.from(
                            Span.styled(isInbound ? " To route: " : " From route: ", Style.EMPTY.dim()),
                            Span.styled(connectedRoute, Style.EMPTY.fg(Color.WHITE))));
                }
                if (topoNode.exchangesTotal > 0 || topoNode.exchangesFailed > 0) {
                    lines.add(Line.from(Span.raw("")));
                    lines.add(Line.from(
                            Span.styled(" Total:  ", Style.EMPTY.dim()),
                            Span.raw(String.valueOf(topoNode.exchangesTotal))));
                    if (topoNode.exchangesFailed > 0) {
                        lines.add(Line.from(
                                Span.styled(" Failed: ", Style.EMPTY.dim()),
                                Span.styled(String.valueOf(topoNode.exchangesFailed),
                                        Style.EMPTY.fg(Color.LIGHT_RED).bold())));
                    }
                }
            } else {
                lines.add(Line.from(
                        Span.styled(" " + routeId, Style.EMPTY.fg(Color.CYAN).bold())));
                lines.add(Line.from(
                        Span.styled(" (external endpoint)", Style.EMPTY.dim())));
            }
        }

        Paragraph paragraph = Paragraph.builder()
                .text(Text.from(lines))
                .block(Block.builder().borderType(BorderType.ROUNDED)
                        .title(" Info ").build())
                .build();
        frame.renderWidget(paragraph, area);
    }

    @Override
    public void renderFooter(List<Span> spans) {
        if (diagram.isShowDiagram()) {
            if (!topologyMode) {
                hint(spans, "Esc", "back");
                hint(spans, "↑↓←→", "scroll");
                hint(spans, "PgUp/PgDn", "page");
            } else if (!diagram.getNodeBoxes().isEmpty()) {
                hint(spans, "Esc", "close");
                hint(spans, "↑↓←→", "navigate");
                hint(spans, "Enter", "drill-down");
                hint(spans, "PgUp/PgDn", "page");
            } else {
                diagram.renderFooterHints(spans);
            }
            hint(spans, "m", "metrics" + (diagramMetrics ? " [on]" : " [off]"));
            if (topologyMode) {
                hint(spans, "e", "external" + (showExternal ? " [on]" : " [off]"));
            }
            hint(spans, "n", "description" + (diagram.isShowDescription() ? " [on]" : " [off]"));
        }
    }

    void refreshDiagramIfNeeded() {
        if (diagram.isShowDiagram() && diagramMetrics) {
            reloadDiagram();
        }
    }

    private void loadDiagram() {
        loadDiagram(true);
    }

    private void reloadDiagram() {
        loadDiagram(false);
    }

    private void loadDiagram(boolean showPlaceholder) {
        if (ctx.selectedPid == null || ctx.runner == null) {
            return;
        }
        if (!diagram.beginLoad()) {
            return;
        }

        String pid = ctx.selectedPid;
        boolean showMetrics = diagramMetrics;
        boolean external = showExternal;

        if (showPlaceholder) {
            diagram.setLoadingPlaceholder();
        }

        ctx.runner.scheduler().execute(() -> {
            try {
                if (topologyMode) {
                    diagram.setTopologyMode(true);
                    diagram.loadTopologyDiagramInBackground(ctx, pid, true, showMetrics, external);
                } else {
                    diagram.setTopologyMode(false);
                    diagram.loadRouteDiagramInBackground(ctx, pid, true, drillDownRouteId, showMetrics);
                }
            } finally {
                diagram.endLoad();
            }
        });
    }

    @Override
    public String getHelpText() {
        return """
                                # Diagram

                                The Diagram tab shows a visual topology of how routes connect to each other.
                                This helps you understand the overall message flow in your integration.

                                ## Topology View

                                The topology view shows all routes and their connections:
                                - **Trigger routes** (timer, cron, etc.) appear at the top
                                - **Downstream routes** appear below, connected by arrows
                                - Routes that are connected show edges between them

                                ## Example Topology

                                ```
                                ┌──────────────────┐
                                │ order-generator  │
                                │  (timer://gen)   │
                                │                  │
                                │      3748        │
                                └──────────────────┘
                                         │
                                         ▼
                                ┌──────────────────┐
                                │  process-order   │
                                │ (direct:process) │
                                │                  │
                                │    3748/12!      │
                                └──────────────────┘
                                ```

                                Each box represents a route. The first line is the route ID,
                                the second line shows the `from` endpoint, and the bottom line
                                shows metrics when enabled. Arrows show how routes connect.

                                ## Metrics

                                When metrics are enabled, each route box shows exchange counts:
                                - **Green** number — successful exchanges
                                - **Red** number with `!` — failed exchanges
                                - Combined as `3748/12!` means 3748 ok and 12 failed

                                ## External Systems

                                When external systems are enabled, the diagram shows a three-band layout:
                                - **Top band** — external consumers sending messages INTO Camel
                                - **Middle band** — the Camel routes and their internal connections
                                - **Bottom band** — external producers where Camel sends messages OUT

                                External system boxes are drawn with dashed borders to distinguish
                                them from route boxes. Dashed edges connect routes to external systems.

                                ## Navigation

                                In the topology view, use arrow keys to select route boxes:
                                - `↑↓` moves between layers (upstream/downstream routes)
                                - `←→` moves between routes in the same layer

                                When a route is selected, an **Info panel** appears on the left
                                showing key metrics: state, uptime, throughput, exchange counts,
                                and processing times.

                                Press `Enter` on a selected route to **drill down** into its
                                internal EIP structure (the route diagram). Press `Esc` to
                                return to the topology view.

                                ## Keys

                                - `↑↓←→` — navigate between route boxes
                                - `Enter` — drill down into selected route
                                - `Esc` — return to topology / close diagram
                                - `m` — toggle metrics on/off (default: on)
                                - `e` — toggle external systems on/off (default: off)
                                - `n` — toggle description labels on/off (default: off)
                                - `PgUp/PgDn` — page scroll
                                - `Home/End` — top/end
                """;
    }

    @Override
    public JsonObject getTableDataAsJson() {
        List<String> lines = diagram.getLines();
        if (lines == null || lines.isEmpty()) {
            return null;
        }
        JsonObject result = new JsonObject();
        result.put("tab", "Diagram");
        result.put("diagram", String.join("\n", lines));
        result.put("lines", lines.size());
        return result;
    }
}
