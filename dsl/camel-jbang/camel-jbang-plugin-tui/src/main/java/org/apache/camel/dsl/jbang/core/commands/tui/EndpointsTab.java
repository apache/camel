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
import java.util.Locale;
import java.util.Map;

import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Layout;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.AnsiColor;
import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.CharWidth;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Title;
import dev.tamboui.widgets.paragraph.Paragraph;
import dev.tamboui.widgets.sparkline.DualSparkline;
import dev.tamboui.widgets.table.Cell;
import dev.tamboui.widgets.table.Row;
import dev.tamboui.widgets.table.Table;
import dev.tamboui.widgets.table.TableState;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

import static org.apache.camel.dsl.jbang.core.commands.tui.MonitorContext.*;

class EndpointsTab implements MonitorTab {

    private static final String[] SORT_COLUMNS = { "component", "route", "dir", "total", "body", "hdr", "uri" };
    private static final int MAX_CHART_POINTS = 60;
    private static final int CHART_ALL = 0;
    private static final int CHART_SINGLE = 1;
    private static final int CHART_OFF = 2;

    private final MonitorContext ctx;
    private final TableState tableState = new TableState();
    private final Map<String, LinkedList<Long>> endpointInHistory;
    private final Map<String, LinkedList<Long>> endpointOutHistory;
    private final Map<String, LinkedList<Long>> endpointRemoteInHistory;
    private final Map<String, LinkedList<Long>> endpointRemoteOutHistory;
    private final Map<String, LinkedList<Long>> endpointRemoteStubInHistory;
    private final Map<String, LinkedList<Long>> endpointRemoteStubOutHistory;
    private final Map<String, LinkedList<Long>> endpointInSizeHistory;
    private final Map<String, LinkedList<Long>> endpointOutSizeHistory;
    private final Map<String, LinkedList<Long>> perEndpointInHistory;
    private final Map<String, LinkedList<Long>> perEndpointOutHistory;

    private String sort = "route";
    private int sortIndex = 1;
    private boolean sortReversed;
    private int filter;
    private int chartMode = CHART_ALL;

    EndpointsTab(MonitorContext ctx, MetricsCollector metrics) {
        this.ctx = ctx;
        this.endpointInHistory = metrics.getEndpointInHistory();
        this.endpointOutHistory = metrics.getEndpointOutHistory();
        this.endpointRemoteInHistory = metrics.getEndpointRemoteInHistory();
        this.endpointRemoteOutHistory = metrics.getEndpointRemoteOutHistory();
        this.endpointRemoteStubInHistory = metrics.getEndpointRemoteStubInHistory();
        this.endpointRemoteStubOutHistory = metrics.getEndpointRemoteStubOutHistory();
        this.endpointInSizeHistory = metrics.getEndpointInSizeHistory();
        this.endpointOutSizeHistory = metrics.getEndpointOutSizeHistory();
        this.perEndpointInHistory = metrics.getPerEndpointInHistory();
        this.perEndpointOutHistory = metrics.getPerEndpointOutHistory();
    }

    @Override
    public boolean handleKeyEvent(KeyEvent ke) {
        if (ke.isChar('s')) {
            sortIndex = (sortIndex + 1) % SORT_COLUMNS.length;
            sort = SORT_COLUMNS[sortIndex];
            sortReversed = false;
            return true;
        }
        if (ke.isChar('S')) {
            sortReversed = !sortReversed;
            return true;
        }
        if (ke.isCharIgnoreCase('f')) {
            filter = (filter + 1) % 3;
            return true;
        }
        if (ke.isCharIgnoreCase('a')) {
            chartMode = (chartMode + 1) % 3;
            return true;
        }
        return false;
    }

    @Override
    public boolean handleEscape() {
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
            List<EndpointInfo> filtered = new ArrayList<>(info.endpoints);
            if (filter == 1) {
                filtered.removeIf(ep -> !ep.remote);
            } else if (filter == 2) {
                filtered.removeIf(ep -> !ep.remote && !ep.stub);
            }
            tableState.selectNext(filtered.size());
        }
    }

    @Override
    public void render(Frame frame, Rect area) {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null) {
            renderNoSelection(frame, area);
            return;
        }

        List<EndpointInfo> sortedEndpoints = new ArrayList<>(info.endpoints);
        if (filter == 1) {
            sortedEndpoints.removeIf(ep -> !ep.remote);
        } else if (filter == 2) {
            sortedEndpoints.removeIf(ep -> !ep.remote && !ep.stub);
        }
        sortedEndpoints.sort(this::sortEndpoint);

        boolean hasSize = info.endpoints.stream()
                .anyMatch(ep -> ep.meanBodySize >= 0 || ep.meanHeadersSize >= 0);

        List<Row> rows = new ArrayList<>();
        for (EndpointInfo ep : sortedEndpoints) {
            String dir = ep.direction != null ? ep.direction : "";
            Style dirStyle = switch (dir) {
                case "in" -> Style.EMPTY.fg(Color.ansi(AnsiColor.BRIGHT_GREEN));
                case "out" -> Style.EMPTY.fg(Color.CYAN);
                default -> Style.EMPTY.fg(Color.YELLOW);
            };
            String arrow = switch (dir) {
                case "in" -> "→ ";
                case "out" -> "← ";
                default -> "↔ ";
            };

            List<Cell> cells = new ArrayList<>();
            cells.add(Cell.from(Span.styled(ep.component != null ? ep.component : "", Style.EMPTY.fg(Color.CYAN))));
            cells.add(Cell.from(ep.routeId != null ? ep.routeId : ""));
            cells.add(Cell.from(Span.styled(arrow + dir, dirStyle)));
            cells.add(rightCell(ep.hits > 0 ? String.valueOf(ep.hits) : "", 8));
            if (hasSize) {
                cells.add(rightCell(sizeToString(ep.meanBodySize), 10));
                cells.add(rightCell(sizeToString(ep.meanHeadersSize), 10));
            }
            cells.add(centerCell(ep.stub ? "x" : "", 6));
            cells.add(centerCell(ep.remote ? "x" : "", 8));
            cells.add(Cell.from(ep.uri != null ? ep.uri : ""));
            rows.add(Row.from(cells));
        }

        int emptyCols = hasSize ? 9 : 7;
        if (rows.isEmpty()) {
            List<Cell> emptyCells = new ArrayList<>();
            emptyCells.add(Cell.from(Span.styled("No endpoints", Style.EMPTY.dim())));
            for (int i = 1; i < emptyCols; i++) {
                emptyCells.add(Cell.from(""));
            }
            rows.add(Row.from(emptyCells));
        }

        List<Cell> headerCells = new ArrayList<>();
        headerCells.add(Cell.from(Span.styled(sortLabel("COMPONENT", "component"), sortStyle("component"))));
        headerCells.add(Cell.from(Span.styled(sortLabel("ROUTE", "route"), sortStyle("route"))));
        headerCells.add(Cell.from(Span.styled(sortLabel("DIR", "dir"), sortStyle("dir"))));
        headerCells.add(rightCell(sortLabel("TOTAL", "total"), 8, sortStyle("total")));
        if (hasSize) {
            headerCells.add(rightCell(sortLabel("BODY", "body"), 10, sortStyle("body")));
            headerCells.add(rightCell(sortLabel("HDR", "hdr"), 10, sortStyle("hdr")));
        }
        headerCells.add(centerCell("STUB", 6, Style.EMPTY.bold()));
        headerCells.add(centerCell("REMOTE", 8, Style.EMPTY.bold()));
        headerCells.add(Cell.from(Span.styled(sortLabel("URI", "uri"), sortStyle("uri"))));

        List<Constraint> widths = new ArrayList<>();
        widths.add(Constraint.length(15));
        widths.add(Constraint.length(20));
        widths.add(Constraint.length(8));
        widths.add(Constraint.length(8));
        if (hasSize) {
            widths.add(Constraint.length(10));
            widths.add(Constraint.length(10));
        }
        widths.add(Constraint.length(6));
        widths.add(Constraint.length(8));
        widths.add(Constraint.fill());

        Table table = Table.builder()
                .rows(rows)
                .header(Row.from(headerCells))
                .widths(widths.toArray(Constraint[]::new))
                .highlightStyle(Style.EMPTY.fg(Color.WHITE).bold().onBlue())
                .highlightSpacing(Table.HighlightSpacing.ALWAYS)
                .block(Block.builder().borderType(BorderType.ROUNDED)
                        .title(" Endpoints sort:" + sort
                               + (filter == 1 ? " filter:remote" : filter == 2 ? " filter:remote+stub" : "")
                               + " ")
                        .build())
                .build();

        boolean hasSizeHistory = !endpointInSizeHistory.isEmpty()
                && endpointInSizeHistory.values().stream().anyMatch(h -> h.stream().anyMatch(v -> v > 0));

        boolean showChart = chartMode != CHART_OFF && ctx.shellPercent < 50;
        List<Rect> chunks = showChart
                ? Layout.vertical().constraints(Constraint.fill(), Constraint.length(16)).split(area)
                : List.of(area);

        frame.renderStatefulWidget(table, chunks.get(0), tableState);

        if (showChart) {
            // Determine selected endpoint URI for single-endpoint chart
            String selectedUri = null;
            if (chartMode == CHART_SINGLE) {
                Integer sel = tableState.selected();
                if (sel != null && sel >= 0 && sel < sortedEndpoints.size()) {
                    selectedUri = sortedEndpoints.get(sel).uri;
                }
            }

            if (chartMode == CHART_SINGLE && selectedUri != null) {
                renderSingleEndpointChart(frame, chunks.get(1), selectedUri, info);
            } else {
                long inTotal = info.endpoints.stream()
                        .filter(ep -> "in".equals(ep.direction) && matchesFilter(ep))
                        .mapToLong(ep -> ep.hits)
                        .sum();
                long outTotal = info.endpoints.stream()
                        .filter(ep -> "out".equals(ep.direction) && matchesFilter(ep))
                        .mapToLong(ep -> ep.hits)
                        .sum();

                if (hasSizeHistory) {
                    List<Rect> chartSplit = Layout.horizontal()
                            .constraints(Constraint.percentage(50), Constraint.percentage(50))
                            .split(chunks.get(1));
                    renderEndpointFlow(frame, chartSplit.get(0), inTotal, outTotal, info.name, info.pid);
                    renderPayloadSizeChart(frame, chartSplit.get(1), info.pid);
                } else {
                    renderEndpointFlow(frame, chunks.get(1), inTotal, outTotal, info.name, info.pid);
                }
            }
        }
    }

    @Override
    public void renderFooter(List<Span> spans) {
        hint(spans, "Esc", "back");
        hint(spans, "↑↓", "navigate");
        hint(spans, "s", "sort");
        String[] filterLabels = { "all", "remote", "remote+stub" };
        hint(spans, "f", "filter [" + filterLabels[filter] + "]");
        String chartLabel = switch (chartMode) {
            case CHART_ALL -> "[all]";
            case CHART_SINGLE -> "[single]";
            default -> "[off]";
        };
        hint(spans, "a", "chart " + chartLabel);
    }

    int getFilter() {
        return filter;
    }

    boolean matchesFilter(EndpointInfo ep) {
        return switch (filter) {
            case 1 -> ep.remote;
            case 2 -> ep.remote || ep.stub;
            default -> true;
        };
    }

    private String sortLabel(String label, String column) {
        return MonitorContext.sortLabel(label, column, sort, sortReversed);
    }

    private Style sortStyle(String column) {
        return MonitorContext.sortStyle(column, sort);
    }

    private int sortEndpoint(EndpointInfo a, EndpointInfo b) {
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
            case "total" -> Long.compare(b.hits, a.hits);
            case "body" -> Long.compare(b.meanBodySize, a.meanBodySize);
            case "hdr" -> Long.compare(b.meanHeadersSize, a.meanHeadersSize);
            case "uri" -> {
                String ua = a.uri != null ? a.uri : "";
                String ub = b.uri != null ? b.uri : "";
                yield ua.compareToIgnoreCase(ub);
            }
            default -> { // "route"
                String ra = a.routeId != null ? a.routeId : "";
                String rb = b.routeId != null ? b.routeId : "";
                yield ra.compareToIgnoreCase(rb);
            }
        };
        if (result == 0) {
            result = directionOrder(a.direction) - directionOrder(b.direction);
        }
        return sortReversed ? -result : result;
    }

    private static int directionOrder(String direction) {
        if ("in".equals(direction)) {
            return 0;
        }
        if ("out".equals(direction)) {
            return 1;
        }
        return 2;
    }

    private void renderEndpointFlow(
            Frame frame, Rect area, long inTotal, long outTotal, String name, String pid) {
        List<Rect> hSplit = Layout.horizontal()
                .constraints(Constraint.length(38), Constraint.fill())
                .split(area);

        int w = Math.max(10, hSplit.get(0).width() - 2);

        String label = name != null ? name : "INTEGRATION";
        if (CharWidth.of(label) > 20) {
            label = CharWidth.truncateWithEllipsis(label, 20, CharWidth.TruncatePosition.END);
        }
        String box = "[ " + label + " ]";
        int boxLen = CharWidth.of(box);

        int sideLen = Math.max(4, (w - boxLen - 2) / 2);
        String arm = "─".repeat(Math.max(1, sideLen - 1));
        String arrowStr = arm + "►";

        String inStr = String.valueOf(inTotal);
        String outStr = String.valueOf(outTotal);

        int inPad = Math.max(0, sideLen - inStr.length());
        int centerGap = boxLen + 2;
        int outPad = Math.max(0, sideLen - outStr.length());

        int inLabelPad = (sideLen - 2) / 2;
        int outLabelPad = (sideLen - 3) / 2;
        String inLabelStr = " ".repeat(inLabelPad) + "in" + " ".repeat(sideLen - inLabelPad - 2);
        String outLabelStr = " ".repeat(outLabelPad) + "out";

        Style inStyle = Style.EMPTY.fg(Color.ansi(AnsiColor.BRIGHT_GREEN));
        Style outStyle = Style.EMPTY.fg(Color.CYAN);
        Style dimStyle = Style.EMPTY.dim();

        List<Line> flowLines = new ArrayList<>();
        flowLines.add(Line.from(Span.raw("")));
        flowLines.add(Line.from(Span.raw("")));
        flowLines.add(Line.from(
                Span.styled(" ".repeat(inPad) + inStr, inTotal > 0 ? inStyle : dimStyle),
                Span.raw(" ".repeat(centerGap)),
                Span.styled(outStr + " ".repeat(outPad), outTotal > 0 ? outStyle : dimStyle)));
        flowLines.add(Line.from(
                Span.styled(arrowStr, inStyle),
                Span.raw(" "),
                Span.styled(box, Style.EMPTY.fg(Color.YELLOW).bold()),
                Span.raw(" "),
                Span.styled(arrowStr, outStyle)));
        flowLines.add(Line.from(
                Span.styled(inLabelStr, inStyle.dim()),
                Span.raw(" ".repeat(centerGap)),
                Span.styled(outLabelStr, outStyle.dim())));

        frame.renderWidget(Paragraph.builder()
                .text(dev.tamboui.text.Text.from(flowLines))
                .block(Block.builder().borderType(BorderType.ROUNDED).title(" Flow ").build())
                .build(), hSplit.get(0));

        Map<String, LinkedList<Long>> inHistMap = switch (filter) {
            case 1 -> endpointRemoteInHistory;
            case 2 -> endpointRemoteStubInHistory;
            default -> endpointInHistory;
        };
        Map<String, LinkedList<Long>> outHistMap = switch (filter) {
            case 1 -> endpointRemoteOutHistory;
            case 2 -> endpointRemoteStubOutHistory;
            default -> endpointOutHistory;
        };
        LinkedList<Long> inHist = inHistMap.getOrDefault(pid, new LinkedList<>());
        LinkedList<Long> outHist = outHistMap.getOrDefault(pid, new LinkedList<>());

        int renderPoints = MAX_CHART_POINTS;
        long[] inArr = new long[renderPoints];
        long[] outArr = new long[renderPoints];
        for (int i = 0; i < renderPoints; i++) {
            int idx = inHist.size() - renderPoints + i;
            if (idx >= 0) {
                inArr[i] = unbox(inHist.get(idx));
            }
            idx = outHist.size() - renderPoints + i;
            if (idx >= 0) {
                outArr[i] = unbox(outHist.get(idx));
            }
        }
        long curIn = inArr[renderPoints - 1];
        long curOut = outArr[renderPoints - 1];

        Line chartTitle = Line.from(
                Span.styled("▬", Style.EMPTY.fg(Color.ansi(AnsiColor.BRIGHT_GREEN))),
                Span.raw(String.format(" in:%-4d ", curIn)),
                Span.styled("▬", Style.EMPTY.fg(Color.CYAN)),
                Span.raw(String.format(" out:%-4d msg/s", curOut)));

        Rect rightArea = hSplit.get(1);
        frame.renderWidget(DualSparkline.builder()
                .topData(inArr)
                .bottomData(outArr)
                .topStyle(Style.EMPTY.fg(Color.ansi(AnsiColor.BRIGHT_GREEN)))
                .bottomStyle(Style.EMPTY.fg(Color.CYAN))
                .showYAxis(true)
                .xLabels("-" + renderPoints + "s", "-" + (renderPoints * 3 / 4) + "s",
                        "-" + (renderPoints / 2) + "s", "-" + (renderPoints / 4) + "s", "now")
                .block(Block.builder().borderType(BorderType.ROUNDED)
                        .title(Title.from(chartTitle)).build())
                .build(), rightArea);
    }

    private void renderSingleEndpointChart(Frame frame, Rect area, String selectedUri, IntegrationInfo info) {
        long inTotal = info.endpoints.stream()
                .filter(ep -> "in".equals(ep.direction) && selectedUri.equals(ep.uri))
                .mapToLong(ep -> ep.hits)
                .sum();
        long outTotal = info.endpoints.stream()
                .filter(ep -> "out".equals(ep.direction) && selectedUri.equals(ep.uri))
                .mapToLong(ep -> ep.hits)
                .sum();

        List<Rect> hSplit = Layout.horizontal()
                .constraints(Constraint.length(38), Constraint.fill())
                .split(area);

        // Flow diagram with endpoint URI as label
        int w = Math.max(10, hSplit.get(0).width() - 2);
        String label = selectedUri;
        if (CharWidth.of(label) > 20) {
            label = CharWidth.truncateWithEllipsis(label, 20, CharWidth.TruncatePosition.END);
        }
        String box = "[ " + label + " ]";
        int boxLen = CharWidth.of(box);
        int sideLen = Math.max(4, (w - boxLen - 2) / 2);
        String arm = "─".repeat(Math.max(1, sideLen - 1));
        String arrowStr = arm + "►";
        String inStr = String.valueOf(inTotal);
        String outStr = String.valueOf(outTotal);
        int inPad = Math.max(0, sideLen - inStr.length());
        int centerGap = boxLen + 2;
        int outPad = Math.max(0, sideLen - outStr.length());
        int inLabelPad = (sideLen - 2) / 2;
        int outLabelPad = (sideLen - 3) / 2;
        String inLabelStr = " ".repeat(inLabelPad) + "in" + " ".repeat(sideLen - inLabelPad - 2);
        String outLabelStr = " ".repeat(outLabelPad) + "out";

        Style inStyle = Style.EMPTY.fg(Color.ansi(AnsiColor.BRIGHT_GREEN));
        Style outStyle = Style.EMPTY.fg(Color.CYAN);
        Style dimStyle = Style.EMPTY.dim();

        List<Line> flowLines = new ArrayList<>();
        flowLines.add(Line.from(Span.raw("")));
        flowLines.add(Line.from(Span.raw("")));
        flowLines.add(Line.from(
                Span.styled(" ".repeat(inPad) + inStr, inTotal > 0 ? inStyle : dimStyle),
                Span.raw(" ".repeat(centerGap)),
                Span.styled(outStr + " ".repeat(outPad), outTotal > 0 ? outStyle : dimStyle)));
        flowLines.add(Line.from(
                Span.styled(arrowStr, inStyle),
                Span.raw(" "),
                Span.styled(box, Style.EMPTY.fg(Color.YELLOW).bold()),
                Span.raw(" "),
                Span.styled(arrowStr, outStyle)));
        flowLines.add(Line.from(
                Span.styled(inLabelStr, inStyle.dim()),
                Span.raw(" ".repeat(centerGap)),
                Span.styled(outLabelStr, outStyle.dim())));

        frame.renderWidget(Paragraph.builder()
                .text(dev.tamboui.text.Text.from(flowLines))
                .block(Block.builder().borderType(BorderType.ROUNDED).title(" Flow ").build())
                .build(), hSplit.get(0));

        // Per-endpoint sparkline
        String key = info.pid + "|" + selectedUri;
        LinkedList<Long> inHist = perEndpointInHistory.getOrDefault(key, new LinkedList<>());
        LinkedList<Long> outHist = perEndpointOutHistory.getOrDefault(key, new LinkedList<>());

        int renderPoints = MAX_CHART_POINTS;
        long[] inArr = new long[renderPoints];
        long[] outArr = new long[renderPoints];
        for (int i = 0; i < renderPoints; i++) {
            int idx = inHist.size() - renderPoints + i;
            if (idx >= 0) {
                inArr[i] = unbox(inHist.get(idx));
            }
            idx = outHist.size() - renderPoints + i;
            if (idx >= 0) {
                outArr[i] = unbox(outHist.get(idx));
            }
        }
        long curIn = inArr[renderPoints - 1];
        long curOut = outArr[renderPoints - 1];

        String uriLabel = selectedUri;
        if (CharWidth.of(uriLabel) > 30) {
            uriLabel = CharWidth.truncateWithEllipsis(uriLabel, 30, CharWidth.TruncatePosition.END);
        }

        Line chartTitle = Line.from(
                Span.raw(" ["),
                Span.styled(uriLabel, Style.EMPTY.fg(Color.YELLOW)),
                Span.raw("] "),
                Span.styled("▬", Style.EMPTY.fg(Color.ansi(AnsiColor.BRIGHT_GREEN))),
                Span.raw(String.format(" in:%-4d ", curIn)),
                Span.styled("▬", Style.EMPTY.fg(Color.CYAN)),
                Span.raw(String.format(" out:%-4d msg/s", curOut)));

        frame.renderWidget(DualSparkline.builder()
                .topData(inArr)
                .bottomData(outArr)
                .topStyle(Style.EMPTY.fg(Color.ansi(AnsiColor.BRIGHT_GREEN)))
                .bottomStyle(Style.EMPTY.fg(Color.CYAN))
                .showYAxis(true)
                .xLabels("-" + renderPoints + "s", "-" + (renderPoints * 3 / 4) + "s",
                        "-" + (renderPoints / 2) + "s", "-" + (renderPoints / 4) + "s", "now")
                .block(Block.builder().borderType(BorderType.ROUNDED)
                        .title(Title.from(chartTitle)).build())
                .build(), hSplit.get(1));
    }

    private void renderPayloadSizeChart(Frame frame, Rect area, String pid) {
        LinkedList<Long> inHist = endpointInSizeHistory.getOrDefault(pid, new LinkedList<>());
        LinkedList<Long> outHist = endpointOutSizeHistory.getOrDefault(pid, new LinkedList<>());

        int renderPoints = MAX_CHART_POINTS;
        long[] inArr = new long[renderPoints];
        long[] outArr = new long[renderPoints];
        for (int i = 0; i < renderPoints; i++) {
            int idx = inHist.size() - renderPoints + i;
            if (idx >= 0) {
                inArr[i] = unbox(inHist.get(idx));
            }
            idx = outHist.size() - renderPoints + i;
            if (idx >= 0) {
                outArr[i] = unbox(outHist.get(idx));
            }
        }
        long curIn = inArr[renderPoints - 1];
        long curOut = outArr[renderPoints - 1];

        Line chartTitle = Line.from(
                Span.styled("▬", Style.EMPTY.fg(Color.YELLOW)),
                Span.raw(String.format(" in:%-8s ", sizeToString(curIn))),
                Span.styled("▬", Style.EMPTY.fg(Color.MAGENTA)),
                Span.raw(String.format(" out:%-8s avg body", sizeToString(curOut))));

        frame.renderWidget(DualSparkline.builder()
                .topData(inArr)
                .bottomData(outArr)
                .topStyle(Style.EMPTY.fg(Color.YELLOW))
                .bottomStyle(Style.EMPTY.fg(Color.MAGENTA))
                .showYAxis(true)
                .xLabels("-" + renderPoints + "s", "-" + (renderPoints * 3 / 4) + "s",
                        "-" + (renderPoints / 2) + "s", "-" + (renderPoints / 4) + "s", "now")
                .block(Block.builder().borderType(BorderType.ROUNDED)
                        .title(Title.from(chartTitle)).build())
                .build(), area);
    }

    private static long unbox(Long value) {
        return value != null ? value : 0L;
    }

    static String sizeToString(long size) {
        if (size < 0) {
            return "-";
        }
        if (size == 0) {
            return "0 B";
        }
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format(Locale.US, "%.1f KB", size / 1024.0);
        } else {
            return String.format(Locale.US, "%.1f MB", size / (1024.0 * 1024.0));
        }
    }

    @Override
    public SelectionContext getSelectionContext() {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null || info.endpoints.isEmpty()) {
            return null;
        }
        List<EndpointInfo> sorted = new ArrayList<>(info.endpoints);
        if (filter == 1) {
            sorted.removeIf(ep -> !ep.remote);
        } else if (filter == 2) {
            sorted.removeIf(ep -> !ep.remote && !ep.stub);
        }
        sorted.sort(this::sortEndpoint);
        List<String> items = sorted.stream().map(ep -> ep.uri != null ? ep.uri : "").toList();
        Integer sel = tableState.selected();
        return new SelectionContext("table", items, sel != null ? sel : -1, items.size(), "Endpoints");
    }

    @Override
    public String getHelpText() {
        return """
                # Endpoints

                Endpoints are the addresses that messages flow to and from. Every Camel
                component (kafka, http, file, log, etc.) provides endpoints that routes
                use to receive and send data. An endpoint URI like `kafka://my-topic`
                identifies both the component and the specific resource.

                ## Table Columns

                - **COMPONENT** — The Camel component name (e.g., `kafka`, `http`, `file`, `log`, `seda`). This is the scheme part of the endpoint URI
                - **ROUTE** — Which route uses this endpoint
                - **DIR** — Direction of message flow: `in` (consuming/receiving from this endpoint), `out` (producing/sending to this endpoint), or `both` (used in both directions)
                - **TOTAL** — Hit count: how many times a message has passed through this endpoint in the given direction
                - **BODY** — Average message body size in bytes passing through this endpoint. Helps identify which endpoints handle large payloads
                - **HDR** — Average message headers size in bytes. Large headers may indicate excessive metadata being passed
                - **STUB** — Marked `x` if the endpoint has been replaced by a stub component (useful during testing to avoid connecting to real external systems)
                - **REMOTE** — Marked `x` if the endpoint connects to an external system outside the JVM (e.g., `kafka`, `http`, `ftp`). Local endpoints like `seda`, `direct`, `log` are not remote
                - **URI** — Full endpoint URI with parameters (e.g., `timer://hello?period=2000`)

                ## Example Screen

                ```
                 COMPONENT  ROUTE          DIR  TOTAL  BODY  HDR  STUB  REMOTE  URI
                 timer      timer-to-log   in   21     0     0                  timer://hello?period=2000
                 timer      timer-to-seda  in   14     0     0                  timer://pump?period=3000
                 seda       seda-consumer  in   14     14    0                  seda://queue
                 seda       timer-to-seda  out  14     14    0                  seda://queue
                ```

                Notice that `seda://queue` appears twice — once as `in` (the
                `seda-consumer` route reads from it) and once as `out` (the
                `timer-to-seda` route writes to it). The BODY column shows 14
                bytes for seda because the message `Pumped message` is 14 bytes.

                ## Flow Chart

                The top-left panel shows a visual flow of messages through the
                integration:

                ```
                ┌─────────┐                      ┌──────────┐
                │  kafka  │ ──▸ [integration] ──▸ │   http   │
                │  file   │                      │   log    │
                └─────────┘                      └──────────┘
                 inbound                          outbound
                ```

                The left box lists all inbound components (consumers), the right
                box lists all outbound components (producers). The integration
                name sits in the middle, showing the overall message flow direction.

                ## Throughput Chart

                A mirrored sparkline chart shows message rates over time:

                - **Top half (green)**: inbound messages per second
                - **Bottom half (cyan)**: outbound messages per second

                This helps you see if input and output rates are balanced. If
                inbound consistently exceeds outbound, messages may be queuing up.

                ## Payload Size Chart

                When message size tracking is available, a separate chart shows
                body sizes over time. Sudden increases in message size may indicate
                unexpected large payloads that could cause memory pressure.

                ## Filter Modes

                Use `f` to cycle between filter modes:
                - **all** — show all endpoints
                - **remote** — show only remote endpoints (external connections)
                - **stub** — show only stubbed endpoints

                ## Keys

                - `Up/Down` — select endpoint
                - `s` — cycle sort column
                - `S` — reverse sort order
                - `f` — cycle filter mode
                """;
    }

    @Override
    public JsonObject getTableDataAsJson() {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null) {
            return null;
        }
        JsonObject result = new JsonObject();
        result.put("tab", "Endpoints");
        JsonArray rows = new JsonArray();
        for (EndpointInfo ei : info.endpoints) {
            JsonObject row = new JsonObject();
            row.put("uri", ei.uri);
            row.put("component", ei.component);
            row.put("direction", ei.direction);
            row.put("routeId", ei.routeId);
            row.put("hits", ei.hits);
            row.put("remote", ei.remote);
            row.put("stub", ei.stub);
            if (ei.meanBodySize >= 0) {
                row.put("meanBodySize", ei.meanBodySize);
            }
            if (ei.maxBodySize >= 0) {
                row.put("maxBodySize", ei.maxBodySize);
            }
            if (ei.meanHeadersSize >= 0) {
                row.put("meanHeadersSize", ei.meanHeadersSize);
            }
            rows.add(row);
        }
        result.put("rows", rows);
        result.put("totalRows", info.endpoints.size());
        Integer sel = tableState.selected();
        result.put("selectedIndex", sel != null ? sel : -1);
        return result;
    }
}
