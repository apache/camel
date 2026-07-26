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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Layout;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Span;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.MouseEvent;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.table.Cell;
import dev.tamboui.widgets.table.Row;
import dev.tamboui.widgets.table.Table;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

import static org.apache.camel.dsl.jbang.core.commands.tui.TuiHelper.*;

class NetworkTab extends AbstractTableTab {

    private static final int CHART_ALL = 0;
    private static final int CHART_SINGLE = 1;
    private static final int CHART_OFF = 2;

    private final Map<String, LinkedList<Long>> serviceInHistory;
    private final Map<String, LinkedList<Long>> serviceOutHistory;
    private final Map<String, LinkedList<Long>> serviceInSizeHistory;
    private final Map<String, LinkedList<Long>> serviceOutSizeHistory;
    private final Map<String, LinkedList<Long>> perEndpointInHistory;
    private final Map<String, LinkedList<Long>> perEndpointOutHistory;
    private final Map<String, LinkedList<Long>> perEndpointInSizeHistory;
    private final Map<String, LinkedList<Long>> perEndpointOutSizeHistory;

    private int chartMode = CHART_ALL;
    private int chartPanelHeight = 16;
    private final DragSplit vSplit = new DragSplit();
    private int flowPanelWidth = 38;
    private final DragSplit hSplit = new DragSplit();

    NetworkTab(MonitorContext ctx, MetricsCollector metrics) {
        super(ctx, "component", "route", "dir", "protocol", "hits", "body", "hdr", "uri");
        sortIndex = 1;
        sort = "route";
        this.serviceInHistory = metrics.getServiceInHistory();
        this.serviceOutHistory = metrics.getServiceOutHistory();
        this.serviceInSizeHistory = metrics.getServiceInSizeHistory();
        this.serviceOutSizeHistory = metrics.getServiceOutSizeHistory();
        this.perEndpointInHistory = metrics.getPerEndpointInHistory();
        this.perEndpointOutHistory = metrics.getPerEndpointOutHistory();
        this.perEndpointInSizeHistory = metrics.getPerEndpointInSizeHistory();
        this.perEndpointOutSizeHistory = metrics.getPerEndpointOutSizeHistory();
    }

    @Override
    protected int getRowCount() {
        IntegrationInfo info = ctx.findSelectedIntegration();
        return info != null ? info.services.size() : 0;
    }

    @Override
    protected boolean handleTabKeyEvent(KeyEvent ke) {
        if (ke.isCharIgnoreCase('a')) {
            chartMode = (chartMode + 1) % 3;
            return true;
        }
        return false;
    }

    @Override
    public boolean handleMouseEvent(MouseEvent me, Rect area) {
        if (vSplit.handleMouse(me, me.y())) {
            if (vSplit.isDragging()) {
                chartPanelHeight = Math.max(5, Math.min(area.y() + area.height() - me.y(), area.height() - 5));
            }
            return true;
        }
        if (chartMode != CHART_OFF && hSplit.handleMouse(me, me.x())) {
            if (hSplit.isDragging()) {
                flowPanelWidth = Math.max(20, Math.min(me.x() - area.x(), area.width() - 20));
            }
            return true;
        }
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info != null) {
            if (handleTableClick(me, lastTableArea, tableState, info.services.size())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void navigateUp() {
        tableState.selectPrevious();
    }

    @Override
    public void navigateDown() {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info != null) {
            tableState.selectNext(info.services.size());
        }
    }

    @Override
    public boolean handleKeyEvent(KeyEvent ke) {
        if (ke.isPageUp() || ke.isKey(KeyCode.PAGE_UP)
                || ke.isPageDown() || ke.isKey(KeyCode.PAGE_DOWN)
                || ke.isHome() || ke.isEnd()) {
            return false;
        }
        return super.handleKeyEvent(ke);
    }

    @Override
    protected void renderContent(Frame frame, Rect area, IntegrationInfo info) {
        List<ServiceInfo> sorted = new ArrayList<>(info.services);
        sorted.sort(this::sortService);

        // Build endpoint lookup by URI to get payload size data
        Map<String, EndpointInfo> epByUri = new java.util.LinkedHashMap<>();
        for (EndpointInfo ep : info.endpoints) {
            if (ep.uri != null) {
                epByUri.put(ep.uri, ep);
            }
        }

        boolean hasSize = info.services.stream().anyMatch(si -> {
            EndpointInfo ep = si.endpointUri != null ? epByUri.get(si.endpointUri) : null;
            return ep != null && (ep.meanBodySize >= 0 || ep.meanHeadersSize >= 0);
        });

        List<Row> rows = new ArrayList<>();
        for (ServiceInfo si : sorted) {
            String dir = si.direction != null ? si.direction : "";
            Style dirStyle = switch (dir) {
                case "in" -> Theme.success();
                case "out" -> Style.EMPTY.fg(Theme.accent());
                default -> Theme.label();
            };
            String arrow = switch (dir) {
                case "in" -> TuiIcons.KEY_RIGHT + " ";
                case "out" -> TuiIcons.KEY_LEFT + " ";
                default -> TuiIcons.ARROW_BOTH + " ";
            };

            List<Cell> cells = new ArrayList<>();
            cells.add(Cell.from(Span.styled(si.component != null ? si.component : "", Style.EMPTY.fg(Theme.accent()))));
            cells.add(Cell.from(si.routeId != null ? si.routeId : ""));
            cells.add(Cell.from(Span.styled(arrow + dir, dirStyle)));
            cells.add(Cell.from(si.protocol != null ? si.protocol : ""));
            cells.add(centerCell(si.hosted ? "x" : "", 8));
            cells.add(rightCell(si.hits > 0 ? String.valueOf(si.hits) : "", 8));
            if (hasSize) {
                EndpointInfo ep = si.endpointUri != null ? epByUri.get(si.endpointUri) : null;
                long bodySize = ep != null ? ep.meanBodySize : -1;
                long hdrSize = ep != null ? ep.meanHeadersSize : -1;
                cells.add(rightCell(FlowHelper.sizeToString(bodySize), 10));
                cells.add(rightCell(FlowHelper.sizeToString(hdrSize), 10));
            }
            cells.add(Cell.from(si.serviceUrl != null ? si.serviceUrl : ""));
            rows.add(Row.from(cells));
        }

        int emptyCols = hasSize ? 9 : 7;
        if (rows.isEmpty()) {
            rows.add(emptyRow("No network services", emptyCols));
        }

        List<Cell> headerCells = new ArrayList<>();
        headerCells.add(Cell.from(Span.styled(sortLabel("COMPONENT", "component"), sortStyle("component"))));
        headerCells.add(Cell.from(Span.styled(sortLabel("ROUTE", "route"), sortStyle("route"))));
        headerCells.add(Cell.from(Span.styled(sortLabel("DIR", "dir"), sortStyle("dir"))));
        headerCells.add(Cell.from(Span.styled(sortLabel("PROTOCOL", "protocol"), sortStyle("protocol"))));
        headerCells.add(centerCell("HOSTED", 8, Style.EMPTY.bold()));
        headerCells.add(rightCell(sortLabel("HITS", "hits"), 8, sortStyle("hits")));
        if (hasSize) {
            headerCells.add(rightCell(sortLabel("BODY", "body"), 10, sortStyle("body")));
            headerCells.add(rightCell(sortLabel("HDR", "hdr"), 10, sortStyle("hdr")));
        }
        headerCells.add(Cell.from(Span.styled(sortLabel("SERVICE URL", "uri"), sortStyle("uri"))));

        List<Constraint> widths = new ArrayList<>();
        widths.add(Constraint.length(18));
        widths.add(Constraint.length(20));
        widths.add(Constraint.length(8));
        widths.add(Constraint.length(10));
        widths.add(Constraint.length(8));
        widths.add(Constraint.length(8));
        if (hasSize) {
            widths.add(Constraint.length(10));
            widths.add(Constraint.length(10));
        }
        widths.add(Constraint.fill());

        Table table = Table.builder()
                .rows(rows)
                .header(Row.from(headerCells))
                .widths(widths.toArray(Constraint[]::new))
                .highlightStyle(Theme.selectionBg())
                .highlightSpacing(Table.HighlightSpacing.ALWAYS)
                .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                        .title(" Network Services ").build())
                .build();

        boolean showChart = chartMode != CHART_OFF && ctx.shellPercent < 50;
        List<Rect> chunks;
        if (showChart) {
            chartPanelHeight = Math.max(5, Math.min(chartPanelHeight, area.height() - 5));
            chunks = Layout.vertical().constraints(Constraint.fill(), Constraint.length(chartPanelHeight)).split(area);
            vSplit.setBorderPos(chunks.get(1).y());
        } else {
            chunks = List.of(area);
            vSplit.clearBorderPos();
            hSplit.clearBorderPos();
        }

        lastTableArea = chunks.get(0);
        frame.renderStatefulWidget(table, chunks.get(0), tableState);
        renderScrollbar(frame, sorted.size());

        if (showChart) {
            String selectedUri = null;
            if (chartMode == CHART_SINGLE) {
                Integer sel = tableState.selected();
                if (sel != null && sel >= 0 && sel < sorted.size()) {
                    selectedUri = sorted.get(sel).endpointUri;
                }
            }

            if (chartMode == CHART_SINGLE && selectedUri != null) {
                renderSingleServiceChart(frame, chunks.get(1), selectedUri, info);
            } else {
                long inTotal = info.services.stream()
                        .filter(s -> "in".equals(s.direction))
                        .mapToLong(s -> s.hits).sum();
                long outTotal = info.services.stream()
                        .filter(s -> "out".equals(s.direction))
                        .mapToLong(s -> s.hits).sum();

                boolean hasSizeHistory;
                try {
                    hasSizeHistory = serviceInSizeHistory.values().stream()
                            .anyMatch(h -> new ArrayList<>(h).stream().anyMatch(v -> v > 0))
                            || serviceOutSizeHistory.values().stream()
                                    .anyMatch(h -> new ArrayList<>(h).stream().anyMatch(v -> v > 0));
                } catch (java.util.ConcurrentModificationException e) {
                    hasSizeHistory = false;
                }

                flowPanelWidth = Math.max(20, Math.min(flowPanelWidth, chunks.get(1).width() - 20));
                List<Rect> hParts = Layout.horizontal()
                        .constraints(Constraint.length(flowPanelWidth), Constraint.fill())
                        .split(chunks.get(1));
                hSplit.setBorderPos(hParts.get(1).x());

                FlowHelper.renderFlowPanel(frame, hParts.get(0), inTotal, outTotal, info.name);

                LinkedList<Long> inHist = serviceInHistory.getOrDefault(info.pid, new LinkedList<>());
                LinkedList<Long> outHist = serviceOutHistory.getOrDefault(info.pid, new LinkedList<>());

                if (hasSizeHistory) {
                    List<Rect> chartSplit = Layout.horizontal()
                            .constraints(Constraint.percentage(50), Constraint.percentage(50))
                            .split(hParts.get(1));
                    FlowHelper.renderThroughputChart(frame, chartSplit.get(0), inHist, outHist);

                    LinkedList<Long> inSizeHist = serviceInSizeHistory.getOrDefault(info.pid, new LinkedList<>());
                    LinkedList<Long> outSizeHist = serviceOutSizeHistory.getOrDefault(info.pid, new LinkedList<>());
                    FlowHelper.renderPayloadSizeChart(frame, chartSplit.get(1), inSizeHist, outSizeHist);
                } else {
                    FlowHelper.renderThroughputChart(frame, hParts.get(1), inHist, outHist);
                }
            }
        }
    }

    private void renderSingleServiceChart(Frame frame, Rect area, String endpointUri, IntegrationInfo info) {
        long inTotal = info.services.stream()
                .filter(s -> "in".equals(s.direction) && endpointUri.equals(s.endpointUri))
                .mapToLong(s -> s.hits).sum();
        long outTotal = info.services.stream()
                .filter(s -> "out".equals(s.direction) && endpointUri.equals(s.endpointUri))
                .mapToLong(s -> s.hits).sum();

        flowPanelWidth = Math.max(20, Math.min(flowPanelWidth, area.width() - 20));
        List<Rect> hParts = Layout.horizontal()
                .constraints(Constraint.length(flowPanelWidth), Constraint.fill())
                .split(area);
        hSplit.setBorderPos(hParts.get(1).x());

        FlowHelper.renderFlowPanel(frame, hParts.get(0), inTotal, outTotal, endpointUri);

        String key = info.pid + "|" + endpointUri;
        LinkedList<Long> inHist = perEndpointInHistory.getOrDefault(key, new LinkedList<>());
        LinkedList<Long> outHist = perEndpointOutHistory.getOrDefault(key, new LinkedList<>());

        LinkedList<Long> inSizeHist = perEndpointInSizeHistory.getOrDefault(key, new LinkedList<>());
        LinkedList<Long> outSizeHist = perEndpointOutSizeHistory.getOrDefault(key, new LinkedList<>());
        boolean hasSizeData;
        try {
            hasSizeData = new ArrayList<>(inSizeHist).stream().anyMatch(v -> v > 0)
                    || new ArrayList<>(outSizeHist).stream().anyMatch(v -> v > 0);
        } catch (java.util.ConcurrentModificationException e) {
            hasSizeData = false;
        }

        if (hasSizeData) {
            List<Rect> chartSplit = Layout.horizontal()
                    .constraints(Constraint.percentage(50), Constraint.percentage(50))
                    .split(hParts.get(1));
            FlowHelper.renderThroughputChart(frame, chartSplit.get(0), inHist, outHist, endpointUri);
            FlowHelper.renderPayloadSizeChart(frame, chartSplit.get(1), inSizeHist, outSizeHist);
        } else {
            FlowHelper.renderThroughputChart(frame, hParts.get(1), inHist, outHist, endpointUri);
        }
    }

    private long lookupBodySize(ServiceInfo si) {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null || si.endpointUri == null) {
            return -1;
        }
        for (EndpointInfo ep : info.endpoints) {
            if (si.endpointUri.equals(ep.uri)) {
                return ep.meanBodySize;
            }
        }
        return -1;
    }

    private long lookupHeadersSize(ServiceInfo si) {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null || si.endpointUri == null) {
            return -1;
        }
        for (EndpointInfo ep : info.endpoints) {
            if (si.endpointUri.equals(ep.uri)) {
                return ep.meanHeadersSize;
            }
        }
        return -1;
    }

    private int sortService(ServiceInfo a, ServiceInfo b) {
        int result = switch (sort) {
            case "component" -> {
                String ca = a.component != null ? a.component : "";
                String cb = b.component != null ? b.component : "";
                yield ca.compareToIgnoreCase(cb);
            }
            case "dir" -> {
                String da = a.direction != null ? a.direction : "";
                String db = b.direction != null ? b.direction : "";
                yield da.compareToIgnoreCase(db);
            }
            case "protocol" -> {
                String pa = a.protocol != null ? a.protocol : "";
                String pb = b.protocol != null ? b.protocol : "";
                yield pa.compareToIgnoreCase(pb);
            }
            case "hits" -> Long.compare(b.hits, a.hits);
            case "body" -> Long.compare(lookupBodySize(b), lookupBodySize(a));
            case "hdr" -> Long.compare(lookupHeadersSize(b), lookupHeadersSize(a));
            case "uri" -> {
                String ua = a.serviceUrl != null ? a.serviceUrl : "";
                String ub = b.serviceUrl != null ? b.serviceUrl : "";
                yield ua.compareToIgnoreCase(ub);
            }
            default -> { // "route"
                String ra = a.routeId != null ? a.routeId : "";
                String rb = b.routeId != null ? b.routeId : "";
                yield ra.compareToIgnoreCase(rb);
            }
        };
        return sortReversed ? -result : result;
    }

    @Override
    public void renderFooter(List<Span> spans) {
        hint(spans, "Esc", "back");
        hint(spans, TuiIcons.HINT_SCROLL, "navigate");
        hint(spans, "s", "sort");
        String chartLabel = switch (chartMode) {
            case CHART_ALL -> "[all]";
            case CHART_SINGLE -> "[single]";
            default -> "[off]";
        };
        hint(spans, "a", "chart " + chartLabel);
    }

    @Override
    public SelectionContext getSelectionContext() {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null || info.services.isEmpty()) {
            return null;
        }
        List<ServiceInfo> sorted = new ArrayList<>(info.services);
        sorted.sort(this::sortService);
        List<String> items = sorted.stream()
                .map(s -> s.serviceUrl != null ? s.serviceUrl : "")
                .toList();
        Integer sel = tableState.selected();
        return new SelectionContext("table", items, sel != null ? sel : -1, items.size(), "Network Services");
    }

    @Override
    public String description() {
        return "Network-facing services (HTTP listeners, Kafka connections, database links)";
    }

    @Override
    public String getHelpText() {
        return """
                # Network Services

                Network Services shows all **network-facing endpoints** — HTTP listeners,
                Kafka connections, database links, messaging brokers — with direction, protocol,
                and hit counts. Unlike the Endpoints tab which includes internal plumbing
                (`direct:`, `seda:`, `log:`), this tab focuses on real network traffic.

                ## Table Columns

                - **COMPONENT** — The Camel component (e.g., `platform-http`, `kafka`, `sql`)
                - **ROUTE** — The route this service belongs to
                - **DIR** — Direction: `in` (consuming/listening) or `out` (producing/calling)
                - **PROTOCOL** — Network protocol: `http`, `https`, `tcp`, `amqp`, etc.
                - **HOSTED** — Whether this is a locally hosted service (e.g., HTTP server) vs a remote client connection
                - **HITS** — Total number of messages processed through this service endpoint
                - **BODY** — Average message body size (shown when payload sizing is active)
                - **HDR** — Average message headers size (shown when payload sizing is active)
                - **SERVICE URL** — The network address or connection URL

                ## Flow Diagram

                The bottom panel shows the same in/out flow diagram and throughput sparkline
                as the Endpoints tab, but scoped to network services only. This gives a
                clearer picture of actual external traffic without internal routing noise.

                ## Keys

                - `Up/Down` — select service
                - `s` — cycle sort column
                - `S` — reverse sort order
                - `a` — toggle chart on/off
                """;
    }

    @Override
    public JsonObject getTableDataAsJson() {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null) {
            return null;
        }
        JsonObject result = new JsonObject();
        result.put("tab", "Network Services");
        Map<String, EndpointInfo> epByUri = new java.util.LinkedHashMap<>();
        for (EndpointInfo ep : info.endpoints) {
            if (ep.uri != null) {
                epByUri.put(ep.uri, ep);
            }
        }
        JsonArray rowsArr = new JsonArray();
        List<ServiceInfo> sorted = new ArrayList<>(info.services);
        sorted.sort(this::sortService);
        for (ServiceInfo si : sorted) {
            JsonObject row = new JsonObject();
            row.put("component", si.component);
            row.put("routeId", si.routeId);
            row.put("direction", si.direction);
            row.put("protocol", si.protocol);
            row.put("hosted", si.hosted);
            row.put("hits", si.hits);
            EndpointInfo ep = si.endpointUri != null ? epByUri.get(si.endpointUri) : null;
            if (ep != null && ep.meanBodySize >= 0) {
                row.put("meanBodySize", ep.meanBodySize);
            }
            if (ep != null && ep.meanHeadersSize >= 0) {
                row.put("meanHeadersSize", ep.meanHeadersSize);
            }
            row.put("serviceUrl", si.serviceUrl);
            row.put("endpointUri", si.endpointUri);
            rowsArr.add(row);
        }
        result.put("rows", rowsArr);
        result.put("totalRows", info.services.size());
        Integer sel = tableState.selected();
        result.put("selectedIndex", sel != null ? sel : -1);
        return result;
    }
}
