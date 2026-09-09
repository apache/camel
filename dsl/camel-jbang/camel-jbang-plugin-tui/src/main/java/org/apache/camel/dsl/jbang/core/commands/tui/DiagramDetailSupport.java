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
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.tamboui.layout.Rect;
import dev.tamboui.markdown.MarkdownView;
import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.CharWidth;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.paragraph.Paragraph;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.dsl.jbang.core.commands.tui.diagram.DiagramColors;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonObject;

import static org.apache.camel.dsl.jbang.core.commands.tui.EipDocSupport.*;
import static org.apache.camel.dsl.jbang.core.commands.tui.TuiHelper.*;

/**
 * The side panels that accompany a route diagram: the route / topology-node info panel, the selected EIP node info
 * panel, and the scrollable EIP detail panel backed by the catalog.
 * <p>
 * Owns the per-route processor-detail cache (fetched over IPC), the catalog cache and the detail panel scroll state so
 * that {@link RoutesTab} and {@link DiagramTab} share one implementation.
 */
final class DiagramDetailSupport {

    private final MonitorContext ctx;
    private final DiagramSupport diagram;
    private final CatalogCache catalogCache = new CatalogCache();

    private int detailScroll;
    private Rect lastDetailArea;
    private String lastDetailNodeId;
    private volatile JsonObject cachedRouteDetail;
    private volatile String cachedRouteDetailId;
    private volatile boolean detailLoading;
    private volatile String detailLoadingRouteId;

    DiagramDetailSupport(MonitorContext ctx, DiagramSupport diagram) {
        this.ctx = ctx;
        this.diagram = diagram;
    }

    /**
     * Clears the cached processor detail and scroll position, e.g. when the detail panel is toggled or the selected
     * integration or route changes.
     */
    void reset() {
        detailScroll = 0;
        lastDetailNodeId = null;
        cachedRouteDetail = null;
        cachedRouteDetailId = null;
        detailLoadingRouteId = null;
        detailLoading = false;
    }

    /**
     * Scrolls the detail panel by the given number of lines (negative scrolls up).
     */
    void scrollBy(int delta) {
        detailScroll = Math.max(0, detailScroll + delta);
    }

    /**
     * Whether the given screen coordinate is inside the most recently rendered detail panel.
     */
    boolean containsMouse(int x, int y) {
        return lastDetailArea != null && lastDetailArea.contains(x, y);
    }

    /**
     * Returns the catalog for the Camel version of the given integration, or null when unavailable.
     */
    CamelCatalog getCatalog(IntegrationInfo info) {
        return catalogCache.get(info);
    }

    void ensureProcessorDetailLoaded(String routeId) {
        if (routeId != null && cachedRouteDetail == null
                && !"*".equals(detailLoadingRouteId)) {
            detailLoadingRouteId = "*";
            detailLoading = true;
            if (ctx.runner != null) {
                ctx.backgroundExecutor.execute(() -> {
                    JsonObject result = requestRouteProcessorDetail("*");
                    cachedRouteDetail = result;
                    cachedRouteDetailId = "*";
                    detailLoading = false;
                });
            }
        }
    }

    private JsonObject requestRouteProcessorDetail(String routeId) {
        if (ctx.selectedPid == null) {
            return null;
        }
        try {
            JsonObject root = new JsonObject();
            root.put("action", "processor-detail");
            root.put("routeId", "*");
            return ctx.executeAction(ctx.selectedPid, root, 5000);
        } catch (Exception e) {
            return null;
        }
    }

    JsonObject findProcessorEntry(String nodeId) {
        if (cachedRouteDetail == null || nodeId == null) {
            return null;
        }
        for (JsonObject p : getAllProcessors(cachedRouteDetail)) {
            if (nodeId.equals(p.getString("id"))) {
                return p;
            }
        }
        return null;
    }

    Map<Integer, List<SourceViewer.DocEntry>> provideAllQuickDocs(List<JsonObject> cd) {
        if (cachedRouteDetail == null || cd.isEmpty()) {
            return Map.of();
        }

        List<JsonObject> processors = getAllProcessors(cachedRouteDetail);
        if (processors.isEmpty()) {
            return Map.of();
        }

        IntegrationInfo info = ctx.findSelectedIntegration();
        CamelCatalog catalog = info != null ? getCatalog(info) : null;

        Map<Integer, List<SourceViewer.DocEntry>> result = new LinkedHashMap<>();
        for (JsonObject proc : processors) {
            Integer line = proc.getInteger("line");
            if (line == null || line <= 0) {
                continue;
            }

            int eipIdx = findCodeDataIndex(cd, line, -1);
            if (eipIdx < 0 || result.containsKey(eipIdx)) {
                continue;
            }
            String endpointUri = proc.getString("endpointUri");
            String type = proc.getString("type");

            if (endpointUri != null && catalog != null) {
                buildEndpointInlineDoc(result, cd, catalog, endpointUri, eipIdx);
            } else if (type != null) {
                buildEipInlineDoc(result, cd, catalog, type, proc.getMap("options"), eipIdx);
            }
        }
        return result;
    }

    static Line buildBreadcrumbTitle(Deque<String> routeNavigationStack, String drillDownRouteId) {
        Style nameStyle = Theme.label().bold();
        List<Span> spans = new ArrayList<>();
        spans.add(Span.raw(" Route ["));
        if (routeNavigationStack.isEmpty()) {
            spans.add(Span.styled(drillDownRouteId, nameStyle));
        } else {
            for (var it = routeNavigationStack.descendingIterator(); it.hasNext();) {
                spans.add(Span.styled(it.next(), nameStyle));
                spans.add(Span.raw(" → "));
            }
            spans.add(Span.styled(drillDownRouteId, nameStyle));
        }
        spans.add(Span.raw("] "));
        return Line.from(spans);
    }

    void renderRouteInfoPanel(Frame frame, Rect area, IntegrationInfo info, String routeId) {
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
                    Span.styled(" Route: ", Theme.muted()),
                    Span.styled(route.routeId, Style.EMPTY.fg(Theme.baseFg()).bold())));
            lines.add(Line.from(
                    Span.styled(" From:  ", Theme.muted()),
                    Span.raw(route.from != null ? route.from : "")));
            String stateLabel = route.state != null ? route.state : "";
            Style stateStyle = "Started".equals(route.state) ? Theme.success() : Theme.error();
            lines.add(Line.from(
                    Span.styled(" State: ", Theme.muted()),
                    Span.styled(stateLabel, stateStyle)));

            lines.add(Line.from(Span.raw("")));
            lines.add(Line.from(
                    Span.styled(" Uptime:     ", Theme.muted()),
                    Span.raw(route.uptime != null ? route.uptime : "")));
            String tpUnit = ctx.ratePerMinute ? " msg/m" : " msg/s";
            String tpValue = route.throughput != null
                    ? (ctx.ratePerMinute ? TuiHelper.throughputPerMinute(route.throughput) : route.throughput)
                    : "";
            lines.add(Line.from(
                    Span.styled(" Rate:       ", Theme.muted()),
                    Span.raw(tpValue),
                    Span.styled(tpUnit, Theme.muted())));
            if (route.coverage != null) {
                lines.add(Line.from(
                        Span.styled(" Coverage:   ", Theme.muted()),
                        Span.raw(route.coverage)));
            }

            lines.add(Line.from(Span.raw("")));
            int w = numWidth(route.total, route.failed, route.inflight);
            lines.add(Line.from(
                    Span.styled(" Total:    ", Theme.muted()),
                    Span.raw(String.format("%" + w + "d", route.total))));
            Style failStyle = route.failed > 0 ? Theme.error().bold() : Style.EMPTY;
            lines.add(Line.from(
                    Span.styled(" Failed:   ", Theme.muted()),
                    Span.styled(String.format("%" + w + "d", route.failed), failStyle)));
            lines.add(Line.from(
                    Span.styled(" Inflight: ", Theme.muted()),
                    Span.raw(String.format("%" + w + "d", route.inflight))));

            lines.add(Line.from(Span.raw("")));
            if (route.total > 0) {
                lines.add(Line.from(
                        Span.styled(" Mean: ", Theme.muted()),
                        Span.raw(formatDurationMs(route.meanTime))));
                lines.add(Line.from(
                        Span.styled(" Max:  ", Theme.muted()),
                        Span.raw(formatDurationMs(route.maxTime))));
                lines.add(Line.from(
                        Span.styled(" Min:  ", Theme.muted()),
                        Span.raw(formatDurationMs(route.minTime))));
                if (route.p50Time >= 0) {
                    lines.add(Line.from(Span.raw("")));
                    lines.add(Line.from(
                            Span.styled(" p50:  ", Theme.muted()),
                            Span.raw(formatDurationMs(route.p50Time))));
                    lines.add(Line.from(
                            Span.styled(" p95:  ", Theme.muted()),
                            Span.raw(formatDurationMs(route.p95Time))));
                    lines.add(Line.from(
                            Span.styled(" p99:  ", Theme.muted()),
                            Span.raw(formatDurationMs(route.p99Time))));
                    lines.add(buildPercentileBarLine(
                            route.p50Time, route.p95Time, route.p99Time, area.width() - 3));
                }
            }

            if (route.sinceLastCompleted != null || route.sinceLastFailed != null) {
                lines.add(Line.from(Span.raw("")));
                lines.add(Line.from(
                        Span.styled(" Since last:", Theme.muted())));
                if (route.sinceLastCompleted != null) {
                    lines.add(Line.from(
                            Span.styled("   ok:   ", Theme.muted()),
                            Span.raw(route.sinceLastCompleted)));
                }
                if (route.sinceLastFailed != null) {
                    lines.add(Line.from(
                            Span.styled("   fail: ", Theme.muted()),
                            Span.styled(route.sinceLastFailed,
                                    Theme.error())));
                }
            }

        } else {
            // External endpoint — show topology node data
            var topoNode = diagram.getSelectedTopologyNode();
            if (topoNode != null) {
                boolean isInbound = "external-in".equals(topoNode.nodeType);
                boolean isBridge = "external".equals(topoNode.nodeType);
                String label = isBridge ? " External" : isInbound ? " Inbound" : " Outbound";
                lines.add(Line.from(
                        Span.styled(label, Style.EMPTY.fg(Theme.accent()).bold())));
                lines.add(Line.from(Span.raw("")));
                lines.add(Line.from(
                        Span.styled(" URI: ", Theme.muted()),
                        Span.raw(topoNode.from != null ? topoNode.from : "")));
                if (topoNode.description != null && !topoNode.description.isBlank()) {
                    lines.add(Line.from(
                            Span.styled(" Path: ", Theme.muted()),
                            Span.raw(topoNode.description)));
                }
                if (!isBridge) {
                    String connectedRoute = diagram.getConnectedRouteId(routeId);
                    if (connectedRoute != null) {
                        lines.add(Line.from(Span.raw("")));
                        lines.add(Line.from(
                                Span.styled(isInbound ? " To route: " : " From route: ", Theme.muted()),
                                Span.styled(connectedRoute, Style.EMPTY.fg(Theme.baseFg()))));
                    }
                }
                if (topoNode.exchangesTotal > 0 || topoNode.exchangesFailed > 0) {
                    lines.add(Line.from(Span.raw("")));
                    lines.add(Line.from(
                            Span.styled(" Total:  ", Theme.muted()),
                            Span.raw(String.valueOf(topoNode.exchangesTotal))));
                    if (topoNode.exchangesFailed > 0) {
                        lines.add(Line.from(
                                Span.styled(" Failed: ", Theme.muted()),
                                Span.styled(String.valueOf(topoNode.exchangesFailed),
                                        Theme.error().bold())));
                    }
                }
            } else {
                lines.add(Line.from(
                        Span.styled(" " + routeId, Style.EMPTY.fg(Theme.accent()).bold())));
                lines.add(Line.from(
                        Span.styled(" (external endpoint)", Style.EMPTY.dim())));
            }
        }

        Paragraph paragraph = Paragraph.builder()
                .text(Text.from(lines))
                .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                        .title(" Info ").build())
                .build();
        frame.renderWidget(paragraph, area);
    }

    void renderEipInfoPanel(Frame frame, Rect area, String drillDownRouteId) {
        List<Line> lines = new ArrayList<>();
        var selected = diagram.getSelectedEipNodeBox();
        if (selected != null && selected.layoutNode() != null) {
            var ln = selected.layoutNode();

            String typeLabel = ln.type != null ? ln.type : "unknown";
            Color eipColor = DiagramColors.getEipColor(typeLabel);
            lines.add(Line.from(
                    Span.styled(" [" + typeLabel + "]", Style.EMPTY.fg(eipColor).bold())));

            String label = String.join("", ln.wrappedLines);
            if (!label.isBlank()) {
                lines.add(Line.from(
                        Span.styled(" ", Style.EMPTY.dim()),
                        Span.raw(label)));
            }

            if (ln.id != null) {
                lines.add(Line.from(
                        Span.styled(" ID: ", Theme.muted()),
                        Span.raw(ln.id)));
            }

            String linkedRoute = diagram.findLinkedRouteId(drillDownRouteId);
            if (linkedRoute != null && diagram.getRouteLayout(linkedRoute) != null) {
                lines.add(Line.from(Span.raw("")));
                lines.add(Line.from(
                        Span.styled(" ↵ ", Theme.label().bold()),
                        Span.styled(linkedRoute, Style.EMPTY.fg(Theme.baseFg()))));
            } else if (ln.treeNode != null && ln.treeNode.info.remote) {
                lines.add(Line.from(Span.raw("")));
                String arrow = "from".equals(ln.type) ? " external → " : " → external";
                lines.add(Line.from(
                        Span.styled(arrow, Theme.muted())));
            }

            if (ln.treeNode != null && ln.treeNode.info.stat != null) {
                var stat = ln.treeNode.info.stat;
                lines.add(Line.from(Span.raw("")));
                int w = numWidth(stat.exchangesTotal, stat.exchangesFailed, stat.exchangesInflight);
                lines.add(Line.from(
                        Span.styled(" Total:    ", Theme.muted()),
                        Span.raw(String.format("%" + w + "d", stat.exchangesTotal))));
                Style failStyle = stat.exchangesFailed > 0
                        ? Theme.error().bold() : Style.EMPTY;
                lines.add(Line.from(
                        Span.styled(" Failed:   ", Theme.muted()),
                        Span.styled(String.format("%" + w + "d", stat.exchangesFailed), failStyle)));
                lines.add(Line.from(
                        Span.styled(" Inflight: ", Theme.muted()),
                        Span.raw(String.format("%" + w + "d", stat.exchangesInflight))));
                if (stat.exchangesThroughput != null && !stat.exchangesThroughput.isEmpty()) {
                    String tpUnit = ctx.ratePerMinute ? " msg/m" : " msg/s";
                    String tpValue = ctx.ratePerMinute
                            ? TuiHelper.throughputPerMinute(stat.exchangesThroughput)
                            : stat.exchangesThroughput;
                    lines.add(Line.from(
                            Span.styled(" Rate:     ", Theme.muted()),
                            Span.raw(tpValue),
                            Span.styled(tpUnit, Theme.muted())));
                }

                if (stat.exchangesTotal > 0) {
                    lines.add(Line.from(Span.raw("")));
                    lines.add(Line.from(
                            Span.styled(" Mean: ", Theme.muted()),
                            Span.raw(formatDurationMs(stat.meanProcessingTime))));
                    lines.add(Line.from(
                            Span.styled(" Max:  ", Theme.muted()),
                            Span.raw(formatDurationMs(stat.maxProcessingTime))));
                    lines.add(Line.from(
                            Span.styled(" Min:  ", Theme.muted()),
                            Span.raw(formatDurationMs(stat.minProcessingTime))));
                    lines.add(Line.from(
                            Span.styled(" Last: ", Theme.muted()),
                            Span.raw(formatDurationMs(stat.lastProcessingTime))));
                    if (stat.p50ProcessingTime >= 0) {
                        lines.add(Line.from(Span.raw("")));
                        lines.add(Line.from(
                                Span.styled(" p50:  ", Theme.muted()),
                                Span.raw(formatDurationMs(stat.p50ProcessingTime))));
                        lines.add(Line.from(
                                Span.styled(" p95:  ", Theme.muted()),
                                Span.raw(formatDurationMs(stat.p95ProcessingTime))));
                        lines.add(Line.from(
                                Span.styled(" p99:  ", Theme.muted()),
                                Span.raw(formatDurationMs(stat.p99ProcessingTime))));
                        lines.add(buildPercentileBarLine(
                                stat.p50ProcessingTime, stat.p95ProcessingTime,
                                stat.p99ProcessingTime, area.width() - 3));
                    }

                    if (stat.lastCompletedExchangeTimestamp > 0 || stat.lastFailedExchangeTimestamp > 0) {
                        long now = System.currentTimeMillis();
                        lines.add(Line.from(Span.raw("")));
                        lines.add(Line.from(
                                Span.styled(" Since last:", Theme.muted())));
                        if (stat.lastCompletedExchangeTimestamp > 0) {
                            long ago = now - stat.lastCompletedExchangeTimestamp;
                            lines.add(Line.from(
                                    Span.styled("   ok:   ", Theme.muted()),
                                    Span.raw(TimeUtils.printDuration(ago, false))));
                        }
                        if (stat.lastFailedExchangeTimestamp > 0) {
                            long ago = now - stat.lastFailedExchangeTimestamp;
                            lines.add(Line.from(
                                    Span.styled("   fail: ", Theme.muted()),
                                    Span.styled(TimeUtils.printDuration(ago, false),
                                            Theme.error())));
                        }
                    }
                }
            }
        } else {
            lines.add(Line.from(Span.styled(" (no node selected)", Style.EMPTY.dim())));
        }

        Paragraph paragraph = Paragraph.builder()
                .text(Text.from(lines))
                .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                        .title(" Info ").build())
                .build();
        frame.renderWidget(paragraph, area);
    }

    void renderDetail(Frame frame, Rect area, IntegrationInfo info, String drillDownRouteId) {
        lastDetailArea = area;
        var selected = diagram.getSelectedEipNodeBox();
        if (selected == null || selected.layoutNode() == null) {
            frame.renderWidget(
                    MarkdownView.builder()
                            .source("*Select an EIP node*")
                            .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                                    .title(" EIP Detail ").build())
                            .styles(Theme.markdownStyles())
                            .build(),
                    area);
            return;
        }

        String nodeId = selected.layoutNode().id;
        String eipType = selected.layoutNode().type;
        if (nodeId == null || eipType == null) {
            frame.renderWidget(
                    MarkdownView.builder()
                            .source("*No detail available*")
                            .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                                    .title(" EIP Detail ").build())
                            .styles(Theme.markdownStyles())
                            .build(),
                    area);
            return;
        }

        // reset scroll when navigating to a different node
        if (!nodeId.equals(lastDetailNodeId)) {
            lastDetailNodeId = nodeId;
            detailScroll = 0;
        }

        // load route detail once (covers all routes and processors)
        ensureProcessorDetailLoaded(drillDownRouteId);

        // find the selected processor in the cached route data
        JsonObject processorEntry = findProcessorEntry(nodeId);

        StringBuilder md = new StringBuilder();
        CamelCatalog catalog = getCatalog(info);

        if (processorEntry != null) {
            String type = processorEntry.getString("type");
            String endpointUri = processorEntry.getString("endpointUri");

            if ("from".equals(type) && endpointUri != null && catalog != null) {
                renderEndpointDetail(md, catalog, endpointUri);
            } else if (type != null) {
                renderEipDetail(md, catalog, type, processorEntry.getMap("options"));
            } else {
                md.append("*No detail available*\n");
            }
        } else if (detailLoading) {
            md.append("*Loading...*\n");
        } else {
            md.append("*No detail available*\n");
        }

        String title = " " + eipType + " [" + nodeId + "] ";
        if (CharWidth.of(title) > area.width() - 4) {
            title = " " + CharWidth.truncateWithEllipsis(
                    eipType + " [" + nodeId + "]", area.width() - 6, CharWidth.TruncatePosition.MIDDLE) + " ";
        }

        frame.renderWidget(
                MarkdownView.builder()
                        .source(md.toString())
                        .scroll(detailScroll)
                        .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                                .title(title).build())
                        .styles(Theme.markdownStyles())
                        .build(),
                area);
    }
}
