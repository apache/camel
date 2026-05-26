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
import dev.tamboui.widgets.table.Cell;
import dev.tamboui.widgets.table.Row;
import dev.tamboui.widgets.table.Table;
import dev.tamboui.widgets.table.TableState;

import static org.apache.camel.dsl.jbang.core.commands.tui.MonitorContext.*;

class EndpointsTab implements MonitorTab {

    private static final String[] SORT_COLUMNS = { "component", "route", "dir", "total", "uri" };
    private static final int MAX_CHART_POINTS = 60;

    private final MonitorContext ctx;
    private final TableState tableState = new TableState();
    private final Map<String, LinkedList<Long>> endpointInHistory;
    private final Map<String, LinkedList<Long>> endpointOutHistory;
    private final Map<String, LinkedList<Long>> endpointRemoteInHistory;
    private final Map<String, LinkedList<Long>> endpointRemoteOutHistory;
    private final Map<String, LinkedList<Long>> endpointRemoteStubInHistory;
    private final Map<String, LinkedList<Long>> endpointRemoteStubOutHistory;

    private String sort = "route";
    private int sortIndex = 1;
    private boolean sortReversed;
    private int filter;
    private boolean showChart = true;

    EndpointsTab(MonitorContext ctx,
                 Map<String, LinkedList<Long>> endpointInHistory,
                 Map<String, LinkedList<Long>> endpointOutHistory,
                 Map<String, LinkedList<Long>> endpointRemoteInHistory,
                 Map<String, LinkedList<Long>> endpointRemoteOutHistory,
                 Map<String, LinkedList<Long>> endpointRemoteStubInHistory,
                 Map<String, LinkedList<Long>> endpointRemoteStubOutHistory) {
        this.ctx = ctx;
        this.endpointInHistory = endpointInHistory;
        this.endpointOutHistory = endpointOutHistory;
        this.endpointRemoteInHistory = endpointRemoteInHistory;
        this.endpointRemoteOutHistory = endpointRemoteOutHistory;
        this.endpointRemoteStubInHistory = endpointRemoteStubInHistory;
        this.endpointRemoteStubOutHistory = endpointRemoteStubOutHistory;
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
            showChart = !showChart;
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
    }

    @Override
    public void navigateDown() {
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

            rows.add(Row.from(
                    Cell.from(Span.styled(ep.component != null ? ep.component : "", Style.EMPTY.fg(Color.CYAN))),
                    Cell.from(ep.routeId != null ? ep.routeId : ""),
                    Cell.from(Span.styled(arrow + dir, dirStyle)),
                    rightCell(ep.hits > 0 ? String.valueOf(ep.hits) : "", 8),
                    centerCell(ep.stub ? "x" : "", 6),
                    centerCell(ep.remote ? "x" : "", 8),
                    Cell.from(ep.uri != null ? ep.uri : "")));
        }

        if (rows.isEmpty()) {
            rows.add(Row.from(
                    Cell.from(Span.styled("No endpoints", Style.EMPTY.dim())),
                    Cell.from(""),
                    Cell.from(""),
                    Cell.from(""),
                    Cell.from(""),
                    Cell.from(""),
                    Cell.from("")));
        }

        Table table = Table.builder()
                .rows(rows)
                .header(Row.from(
                        Cell.from(Span.styled(sortLabel("COMPONENT", "component"), sortStyle("component"))),
                        Cell.from(Span.styled(sortLabel("ROUTE", "route"), sortStyle("route"))),
                        Cell.from(Span.styled(sortLabel("DIR", "dir"), sortStyle("dir"))),
                        rightCell(sortLabel("TOTAL", "total"), 8, sortStyle("total")),
                        centerCell("STUB", 6, Style.EMPTY.bold()),
                        centerCell("REMOTE", 8, Style.EMPTY.bold()),
                        Cell.from(Span.styled(sortLabel("URI", "uri"), sortStyle("uri")))))
                .widths(
                        Constraint.length(15),
                        Constraint.length(20),
                        Constraint.length(8),
                        Constraint.length(8),
                        Constraint.length(6),
                        Constraint.length(8),
                        Constraint.fill())
                .block(Block.builder().borderType(BorderType.ROUNDED)
                        .title(" Endpoints sort:" + sort
                               + (filter == 1 ? " filter:remote" : filter == 2 ? " filter:remote+stub" : "")
                               + " ")
                        .build())
                .build();

        List<Rect> chunks = showChart
                ? Layout.vertical().constraints(Constraint.fill(), Constraint.length(16)).split(area)
                : List.of(area);

        frame.renderStatefulWidget(table, chunks.get(0), tableState);

        if (showChart) {
            long inTotal = info.endpoints.stream()
                    .filter(ep -> "in".equals(ep.direction) && matchesFilter(ep))
                    .mapToLong(ep -> ep.hits)
                    .sum();
            long outTotal = info.endpoints.stream()
                    .filter(ep -> "out".equals(ep.direction) && matchesFilter(ep))
                    .mapToLong(ep -> ep.hits)
                    .sum();
            renderEndpointFlow(frame, chunks.get(1), inTotal, outTotal, info.name, info.pid);
        }
    }

    @Override
    public void renderFooter(List<Span> spans) {
        hint(spans, "Esc", "back");
        hint(spans, "s", "sort");
        String[] filterLabels = { "all", "remote", "remote+stub" };
        hint(spans, "f", "filter [" + filterLabels[filter] + "]");
        hint(spans, "a", "chart " + (showChart ? "[all]" : "[off]"));
        hint(spans, "1-9", "tabs");
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
        return sortReversed ? -result : result;
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
                inArr[i] = inHist.get(idx);
            }
            idx = outHist.size() - renderPoints + i;
            if (idx >= 0) {
                outArr[i] = outHist.get(idx);
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
        frame.renderWidget(MirroredSparkline.builder()
                .topData(inArr)
                .bottomData(outArr)
                .topStyle(Style.EMPTY.fg(Color.ansi(AnsiColor.BRIGHT_GREEN)))
                .bottomStyle(Style.EMPTY.fg(Color.CYAN))
                .xLabels("-" + renderPoints + "s", "-" + (renderPoints * 3 / 4) + "s",
                        "-" + (renderPoints / 2) + "s", "-" + (renderPoints / 4) + "s", "now")
                .block(Block.builder().borderType(BorderType.ROUNDED)
                        .title(Title.from(chartTitle)).build())
                .build(), rightArea);
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
}
