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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Layout;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Title;
import dev.tamboui.widgets.paragraph.Paragraph;
import dev.tamboui.widgets.table.Cell;
import dev.tamboui.widgets.table.Row;
import dev.tamboui.widgets.table.Table;
import dev.tamboui.widgets.table.TableState;
import org.apache.camel.dsl.jbang.core.common.PathUtils;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

import static org.apache.camel.dsl.jbang.core.commands.tui.MonitorContext.*;

class HttpTab implements MonitorTab {

    private static final String[] SORT_COLUMNS = { "method", "path", "total", "consumes", "produces", "source" };
    private static final Set<String> OPENAPI_HTTP_VERBS
            = Set.of("get", "post", "put", "delete", "patch", "options", "head", "trace");

    private final MonitorContext ctx;
    private final TableState tableState = new TableState();
    private final AtomicBoolean specLoading = new AtomicBoolean(false);

    private String sort = "method";
    private int sortIndex;
    private boolean sortReversed;
    private int filter;
    private boolean showManagement;

    private boolean showSpec;
    private List<String> specLines = Collections.emptyList();
    private String specTitle;
    private int specScroll;

    HttpTab(MonitorContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public boolean handleKeyEvent(KeyEvent ke) {
        if (showSpec) {
            if (ke.isChar('c') || ke.isCancel()) {
                showSpec = false;
            } else if (ke.isUp()) {
                specScroll = Math.max(0, specScroll - 1);
            } else if (ke.isDown()) {
                specScroll++;
            } else if (ke.isPageUp() || ke.isKey(KeyCode.PAGE_UP)) {
                specScroll = Math.max(0, specScroll - 20);
            } else if (ke.isPageDown() || ke.isKey(KeyCode.PAGE_DOWN)) {
                specScroll += 20;
            } else if (ke.isHome()) {
                specScroll = 0;
            } else if (ke.isEnd()) {
                specScroll = Integer.MAX_VALUE;
            } else {
                return false;
            }
            return true;
        }

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
        if (ke.isCharIgnoreCase('m')) {
            showManagement = !showManagement;
            return true;
        }
        if (ke.isChar('c')) {
            loadSpecForSelectedEndpoint();
            return true;
        }
        return false;
    }

    @Override
    public boolean handleEscape() {
        if (showSpec) {
            showSpec = false;
            return true;
        }
        return false;
    }

    @Override
    public void navigateUp() {
        tableState.selectPrevious();
    }

    @Override
    public void navigateDown() {
        List<HttpEndpointInfo> visible = sortedVisibleEndpoints(ctx.findSelectedIntegration());
        tableState.selectNext(visible.size());
    }

    @Override
    public void onIntegrationChanged() {
        showSpec = false;
        specLines = Collections.emptyList();
        specTitle = null;
        specScroll = 0;
    }

    @Override
    public void render(Frame frame, Rect area) {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null) {
            renderNoSelection(frame, area);
            return;
        }

        if (showSpec) {
            renderSpec(frame, area);
            return;
        }

        List<HttpEndpointInfo> visible = sortedVisibleEndpoints(info);

        List<Rect> chunks = Layout.vertical()
                .constraints(Constraint.length(1), Constraint.fill(), Constraint.length(10))
                .split(area);

        renderServerInfo(frame, chunks.get(0), info, visible);
        renderTable(frame, chunks.get(1), visible);
        renderDetail(frame, chunks.get(2), visible);
    }

    @Override
    public void renderFooter(List<Span> spans) {
        if (showSpec) {
            hint(spans, "c/Esc", "close");
            hint(spans, "↑↓", "scroll");
            hintLast(spans, "PgUp/PgDn", "page");
            return;
        }
        hint(spans, "Esc", "back");
        hint(spans, "↑↓", "navigate");
        hint(spans, "s", "sort");
        String[] filterLabels = { "all", "rest", "http" };
        hint(spans, "f", "filter [" + filterLabels[filter] + "]");
        hint(spans, "m", "management" + (showManagement ? " [on]" : " [off]"));
        List<HttpEndpointInfo> hVisible = sortedVisibleEndpoints(ctx.findSelectedIntegration());
        Integer hSel = tableState.selected();
        if (hSel != null && hSel >= 0 && hSel < hVisible.size() && hVisible.get(hSel).specificationUri != null) {
            hintLast(spans, "c", "spec");
        } else {
            hintLast(spans, "1-9", "tabs");
        }
    }

    List<HttpEndpointInfo> sortedVisibleEndpoints(IntegrationInfo info) {
        List<HttpEndpointInfo> visible = visibleEndpoints(info);
        visible.sort((a, b) -> {
            int result = switch (sort) {
                case "path" -> compareStr(a.path, b.path);
                case "total" -> Long.compare(a.hits, b.hits);
                case "source" -> Boolean.compare(b.fromRest, a.fromRest);
                case "consumes" -> compareStr(a.consumes, b.consumes);
                case "produces" -> compareStr(a.produces, b.produces);
                default -> compareStr(a.method, b.method);
            };
            return sortReversed ? -result : result;
        });
        return visible;
    }

    private List<HttpEndpointInfo> visibleEndpoints(IntegrationInfo info) {
        if (info == null) {
            return Collections.emptyList();
        }
        List<HttpEndpointInfo> result = new ArrayList<>();
        for (HttpEndpointInfo ep : info.httpEndpoints) {
            if (ep.management && !showManagement) {
                continue;
            }
            if (filter == 1 && !ep.fromRest) {
                continue;
            }
            if (filter == 2 && ep.fromRest) {
                continue;
            }
            result.add(ep);
        }
        return result;
    }

    private void renderServerInfo(Frame frame, Rect area, IntegrationInfo info, List<HttpEndpointInfo> visible) {
        List<Span> spans = new ArrayList<>();
        if (info.httpServer != null) {
            spans.add(Span.styled(" Server: ", Style.EMPTY.fg(Color.YELLOW).bold()));
            spans.add(Span.styled(info.httpServer, Style.EMPTY.fg(Color.CYAN)));
            spans.add(Span.raw("    "));
        }
        long restCount = info.httpEndpoints.stream().filter(e -> e.fromRest && !e.specification).count();
        long specCount = info.httpEndpoints.stream().filter(e -> e.specification).count();
        long httpCount = info.httpEndpoints.stream().filter(e -> !e.fromRest && !e.management).count();
        long mgmtCount = info.httpEndpoints.stream().filter(e -> e.management).count();
        if (restCount > 0) {
            spans.add(Span.styled("REST: ", Style.EMPTY.fg(Color.GREEN)));
            spans.add(Span.raw(restCount + "  "));
        }
        if (specCount > 0) {
            spans.add(Span.styled("Spec: ", Style.EMPTY.fg(Color.MAGENTA)));
            spans.add(Span.raw(specCount + "  "));
        }
        if (httpCount > 0) {
            spans.add(Span.styled("HTTP: ", Style.EMPTY.fg(Color.CYAN)));
            spans.add(Span.raw(httpCount + "  "));
        }
        if (mgmtCount > 0) {
            spans.add(Span.styled("Mgmt: ", Style.EMPTY.fg(Color.YELLOW).dim()));
            spans.add(Span.raw(mgmtCount + ""));
        }
        frame.renderWidget(Paragraph.from(Line.from(spans)), area);
    }

    private static Style methodStyle(String method) {
        if (method == null) {
            return Style.EMPTY;
        }
        String m = method.split(",")[0].trim().toUpperCase(Locale.ENGLISH);
        return switch (m) {
            case "GET" -> Style.EMPTY.fg(Color.GREEN);
            case "POST" -> Style.EMPTY.fg(Color.YELLOW);
            case "PUT" -> Style.EMPTY.fg(Color.CYAN);
            case "DELETE" -> Style.EMPTY.fg(Color.LIGHT_RED);
            case "PATCH" -> Style.EMPTY.fg(Color.rgb(0xFF, 0x80, 0x00));
            default -> Style.EMPTY.dim();
        };
    }

    private void renderTable(Frame frame, Rect area, List<HttpEndpointInfo> visible) {
        List<Row> rows = new ArrayList<>();
        for (HttpEndpointInfo ep : visible) {
            String method = ep.method != null ? ep.method : "";
            String path = ep.path != null ? ep.path : (ep.url != null ? ep.url : "");
            String consumes = ep.consumes != null ? ep.consumes : "";
            String produces = ep.produces != null ? ep.produces : "";
            String source;
            if (ep.management) {
                source = "Mgmt";
            } else if (ep.specification) {
                source = "API Spec";
            } else if (ep.fromRest) {
                source = ep.contractFirst ? "REST(contract)" : "REST(code)";
            } else {
                source = "HTTP";
            }
            String state = ep.state != null ? ep.state : "";
            String hitsStr = ep.hits > 0 ? String.valueOf(ep.hits) : "";
            rows.add(Row.from(
                    Cell.from(Span.styled(method, methodStyle(method))),
                    Cell.from(path),
                    rightCell(hitsStr, 8),
                    Cell.from(consumes),
                    Cell.from(produces),
                    Cell.from(Span.styled(source,
                            ep.specification ? Style.EMPTY.fg(Color.MAGENTA)
                                    : ep.fromRest ? Style.EMPTY.fg(Color.GREEN)
                                    : Style.EMPTY.fg(Color.CYAN))),
                    Cell.from(Span.styled(state,
                            "Stopped".equals(state) ? Style.EMPTY.fg(Color.LIGHT_RED) : Style.EMPTY))));
        }

        String title = String.format(" HTTP Services [%d] sort:%s ", visible.size(), sort);

        Row header = Row.from(
                Cell.from(Span.styled(sortLabel("METHOD", "method"), sortStyle("method"))),
                Cell.from(Span.styled(sortLabel("PATH", "path"), sortStyle("path"))),
                rightCell(sortLabel("TOTAL", "total"), 8, sortStyle("total")),
                Cell.from(Span.styled(sortLabel("CONSUMES", "consumes"), sortStyle("consumes"))),
                Cell.from(Span.styled(sortLabel("PRODUCES", "produces"), sortStyle("produces"))),
                Cell.from(Span.styled(sortLabel("SOURCE", "source"), sortStyle("source"))),
                Cell.from(Span.styled("STATE", Style.EMPTY.bold())));

        Table table = Table.builder()
                .rows(rows)
                .header(header)
                .widths(
                        Constraint.length(12),
                        Constraint.fill(),
                        Constraint.length(8),
                        Constraint.length(30),
                        Constraint.length(30),
                        Constraint.length(15),
                        Constraint.length(8))
                .highlightStyle(Style.EMPTY.fg(Color.WHITE).bold().onBlue())
                .highlightSpacing(Table.HighlightSpacing.ALWAYS)
                .block(Block.builder().borderType(BorderType.ROUNDED).title(title).build())
                .build();

        frame.renderStatefulWidget(table, area, tableState);
    }

    private void renderDetail(Frame frame, Rect area, List<HttpEndpointInfo> visible) {
        Integer sel = tableState.selected();
        if (sel == null || sel < 0 || sel >= visible.size()) {
            frame.renderWidget(
                    Paragraph.builder()
                            .text(Text.from(Line.from(
                                    Span.styled(" Select an endpoint to view details",
                                            Style.EMPTY.dim()))))
                            .block(Block.builder().borderType(BorderType.ROUNDED)
                                    .title(" Detail ").build())
                            .build(),
                    area);
            return;
        }

        HttpEndpointInfo ep = visible.get(sel);
        List<Span> titleSpans = new ArrayList<>();
        if (ep.method != null) {
            titleSpans.add(Span.raw(" "));
            titleSpans.add(Span.styled(ep.method, methodStyle(ep.method).bold()));
            titleSpans.add(Span.raw(" "));
        }
        if (ep.path != null) {
            titleSpans.add(Span.raw(ep.path + " "));
        }
        Title detailTitle = Title.from(Line.from(titleSpans));

        List<Line> lines = new ArrayList<>();
        addDetailLine(lines, "URL", ep.url);
        addDetailLine(lines, "Consumes", ep.consumes);
        addDetailLine(lines, "Produces", ep.produces);
        String sourceStr;
        if (ep.management) {
            sourceStr = "Platform-HTTP (management)";
        } else if (ep.specification) {
            sourceStr = "REST DSL (API specification - " + (ep.contractFirst ? "contract-first" : "code-first") + ")";
        } else if (ep.fromRest) {
            sourceStr = "REST DSL (" + (ep.contractFirst ? "contract-first" : "code-first") + ")";
        } else {
            sourceStr = "Platform-HTTP";
        }
        addDetailLine(lines, "Source", sourceStr);
        if (ep.routeId != null) {
            addDetailLine(lines, "Route", ep.routeId);
        }
        if (ep.operationId != null) {
            addDetailLine(lines, "Operation", ep.operationId);
        }
        if (ep.specificationUri != null) {
            addDetailLine(lines, "Spec", ep.specificationUri);
        }
        if (ep.state != null) {
            addDetailLine(lines, "State", ep.state);
        }
        if (ep.inType != null) {
            addDetailLine(lines, "In type", ep.inType);
        }
        if (ep.outType != null) {
            addDetailLine(lines, "Out type", ep.outType);
        }
        if (ep.description != null) {
            addDetailLine(lines, "Desc", ep.description);
        }

        frame.renderWidget(
                Paragraph.builder()
                        .text(Text.from(lines))
                        .block(Block.builder().borderType(BorderType.ROUNDED).title(detailTitle).build())
                        .build(),
                area);
    }

    private static void addDetailLine(List<Line> lines, String label, String value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        lines.add(Line.from(
                Span.styled(String.format("  %-10s ", label + ":"), Style.EMPTY.fg(Color.YELLOW).bold()),
                Span.raw(value)));
    }

    private void loadSpecForSelectedEndpoint() {
        if (ctx.selectedPid == null || ctx.runner == null) {
            return;
        }
        List<HttpEndpointInfo> visible = sortedVisibleEndpoints(ctx.findSelectedIntegration());
        Integer sel = tableState.selected();
        if (sel == null || sel < 0 || sel >= visible.size()) {
            return;
        }
        HttpEndpointInfo ep = visible.get(sel);
        if (ep.specificationUri == null) {
            return;
        }
        if (!specLoading.compareAndSet(false, true)) {
            return;
        }

        specLines = List.of("(Loading spec...)");
        specTitle = ep.specificationUri;
        specScroll = 0;
        showSpec = true;

        String pid = ctx.selectedPid;
        String specUri = ep.specificationUri;
        String operationId = ep.operationId;

        ctx.runner.scheduler().execute(() -> {
            try {
                loadSpecInBackground(pid, specUri, operationId);
            } finally {
                specLoading.set(false);
            }
        });
    }

    private void loadSpecInBackground(String pid, String specUri, String operationId) {
        Path outputFile = ctx.getOutputFile(pid);
        PathUtils.deleteFile(outputFile);

        JsonObject root = new JsonObject();
        root.put("action", "rest-spec");
        root.put("filter", specUri);

        Path actionFile = ctx.getActionFile(pid);
        org.apache.camel.dsl.jbang.core.common.PathUtils.writeTextSafely(root.toJson(), actionFile);

        JsonObject jo = pollJsonResponse(outputFile, 5000);
        PathUtils.deleteFile(outputFile);

        if (jo == null) {
            applySpecResult(specUri, List.of("(No response from integration)"), 0);
            return;
        }

        JsonArray specs = (JsonArray) jo.get("specs");
        if (specs == null || specs.isEmpty()) {
            applySpecResult(specUri, List.of("(No spec content available for: " + specUri + ")"), 0);
            return;
        }

        JsonObject specObj = (JsonObject) specs.get(0);
        String content = specObj.getString("content");
        if (content == null || content.isBlank()) {
            applySpecResult(specUri, List.of("(Empty spec content for: " + specUri + ")"), 0);
            return;
        }

        List<String> lines = List.of(content.split("\n", -1));

        int scrollTo = 0;
        if (operationId != null) {
            int opIdLine = -1;
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line.contains("operationId") && line.contains(operationId)) {
                    opIdLine = i;
                    break;
                }
            }
            if (opIdLine >= 0) {
                scrollTo = findOperationDeclarationLine(lines, opIdLine);
            }
        }

        applySpecResult(specUri, lines, scrollTo);
    }

    private static int findOperationDeclarationLine(List<String> lines, int opIdLine) {
        int opIdIndent = leadingSpaces(lines.get(opIdLine));
        for (int i = opIdLine - 1; i >= 0; i--) {
            String raw = lines.get(i);
            String trimmed = raw.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int indent = leadingSpaces(raw);
            if (indent >= opIdIndent) {
                continue;
            }
            String lower = trimmed.toLowerCase(Locale.ENGLISH);
            for (String verb : OPENAPI_HTTP_VERBS) {
                if (lower.equals(verb + ":") || lower.startsWith(verb + ": ")
                        || lower.equals("\"" + verb + "\":") || lower.startsWith("\"" + verb + "\": ")) {
                    return Math.max(0, i - 1);
                }
            }
            break;
        }
        return Math.max(0, opIdLine - 2);
    }

    private static int leadingSpaces(String line) {
        int count = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == ' ') {
                count++;
            } else if (c == '\t') {
                count += 2;
            } else {
                break;
            }
        }
        return count;
    }

    private void applySpecResult(String specUri, List<String> lines, int scrollTo) {
        if (ctx.runner == null) {
            return;
        }
        ctx.runner.runOnRenderThread(() -> {
            if (!showSpec) {
                return;
            }
            specTitle = specUri;
            specLines = lines;
            specScroll = scrollTo;
        });
    }

    private void renderSpec(Frame frame, Rect area) {
        String title = " Spec [" + (specTitle != null ? specTitle : "") + "] ";

        int visibleLines = area.height() - 2;
        if (visibleLines < 1) {
            visibleLines = 1;
        }
        int maxScroll = Math.max(0, specLines.size() - visibleLines);
        specScroll = Math.min(specScroll, maxScroll);

        int end = Math.min(specScroll + visibleLines, specLines.size());
        List<Line> visible = new ArrayList<>();
        for (int i = specScroll; i < end; i++) {
            visible.add(Line.from(Span.raw(specLines.get(i))));
        }

        frame.renderWidget(
                Paragraph.builder()
                        .text(Text.from(visible))
                        .block(Block.builder().borderType(BorderType.ROUNDED).title(title).build())
                        .build(),
                area);
    }

    private String sortLabel(String label, String column) {
        return MonitorContext.sortLabel(label, column, sort, sortReversed);
    }

    private Style sortStyle(String column) {
        return MonitorContext.sortStyle(column, sort);
    }

    @Override
    public SelectionContext getSelectionContext() {
        IntegrationInfo info = ctx.findSelectedIntegration();
        List<HttpEndpointInfo> visible = sortedVisibleEndpoints(info);
        if (visible.isEmpty()) {
            return null;
        }
        List<String> items = visible.stream()
                .map(ep -> (ep.method != null ? ep.method : "") + " " + (ep.path != null ? ep.path : ""))
                .toList();
        Integer sel = tableState.selected();
        return new SelectionContext("table", items, sel != null ? sel : -1, items.size(), "HTTP");
    }
}
