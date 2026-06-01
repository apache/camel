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

import dev.tamboui.layout.Rect;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.paragraph.Paragraph;

import static org.apache.camel.dsl.jbang.core.commands.tui.MonitorContext.*;

class DiagramTab implements MonitorTab {

    private final MonitorContext ctx;
    private final DiagramSupport diagram = new DiagramSupport();
    private boolean diagramMetrics = true;
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

        // Drill down into route diagram (Enter)
        if (topologyMode && ke.isConfirm()) {
            // For now, drill-down is a future feature
            return true;
        }

        return false;
    }

    @Override
    public boolean handleEscape() {
        if (!topologyMode) {
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
            diagram.renderDiagram(frame, area, title);
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

    @Override
    public void renderFooter(List<Span> spans) {
        if (diagram.isShowDiagram()) {
            diagram.renderFooterHints(spans);
            hint(spans, "m", "metrics" + (diagramMetrics ? " [on]" : " [off]"));
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

        if (showPlaceholder) {
            diagram.setLoadingPlaceholder();
        }

        ctx.runner.scheduler().execute(() -> {
            try {
                if (topologyMode) {
                    diagram.setTopologyMode(true);
                    diagram.loadTopologyDiagramInBackground(ctx, pid, true, showMetrics);
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

                                ## Keys

                                - `m` — toggle metrics on/off (default: on)
                                - `↑↓←→` — scroll diagram
                                - `PgUp/PgDn` — page scroll
                                - `Home/End` — top/bottom
                                - `Esc` — close diagram
                """;
    }
}
