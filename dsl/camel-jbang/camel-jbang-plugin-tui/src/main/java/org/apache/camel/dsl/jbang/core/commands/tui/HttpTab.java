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
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Layout;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.MouseEvent;
import dev.tamboui.tui.event.MouseEventKind;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.block.Title;
import dev.tamboui.widgets.paragraph.Paragraph;
import dev.tamboui.widgets.table.Cell;
import dev.tamboui.widgets.table.Row;
import dev.tamboui.widgets.table.Table;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

import static org.apache.camel.dsl.jbang.core.commands.tui.TuiHelper.*;

class HttpTab extends AbstractTableTab {

    private static final int MOUSE_SCROLL_LINES = 3;
    private static final Set<String> OPENAPI_HTTP_VERBS
            = Set.of("get", "post", "put", "delete", "patch", "options", "head", "trace");

    private final HttpProbe probe;
    private final AtomicBoolean specLoading = new AtomicBoolean(false);

    private int filter;
    private boolean showManagement = true;

    private boolean showSpec;
    private List<String> specLines = Collections.emptyList();
    private String specTitle;
    private int specScroll;

    private int detailPanelHeight = 10;
    private final DragSplit vSplit = new DragSplit();

    HttpTab(MonitorContext ctx) {
        super(ctx, "method", "path", "total", "consumes", "produces", "source");
        this.probe = new HttpProbe(ctx);
    }

    @Override
    protected int getRowCount() {
        return sortedVisibleEndpoints(ctx.findSelectedIntegration()).size();
    }

    boolean isProbeMode() {
        return probe.isActive();
    }

    @Override
    public boolean handleKeyEvent(KeyEvent ke) {
        if (probe.isActive()) {
            return probe.handleKeyEvent(ke);
        }

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

        return super.handleKeyEvent(ke);
    }

    @Override
    protected boolean handleTabKeyEvent(KeyEvent ke) {
        if (ke.isConfirm()) {
            enterProbeModeFromTable();
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
    public boolean handleMouseEvent(MouseEvent me, Rect area) {
        if (!probe.isActive() && !showSpec && vSplit.handleMouse(me, me.y())) {
            if (vSplit.isDragging() && me.kind() == MouseEventKind.DRAG) {
                detailPanelHeight = Math.max(3, Math.min(area.y() + area.height() - me.y(), area.height() - 5));
            }
            return true;
        }
        if (!probe.isActive() && !showSpec) {
            List<HttpEndpointInfo> visible = sortedVisibleEndpoints(ctx.findSelectedIntegration());
            if (handleTableClick(me, lastTableArea, tableState, visible.size())) {
                return true;
            }
        }
        if (showSpec) {
            if (me.kind() == MouseEventKind.SCROLL_UP) {
                specScroll = Math.max(0, specScroll - MOUSE_SCROLL_LINES);
                return true;
            }
            if (me.kind() == MouseEventKind.SCROLL_DOWN) {
                specScroll += MOUSE_SCROLL_LINES;
                return true;
            }
        }
        if (probe.isActive()) {
            return probe.handleMouseScroll(me);
        }
        return false;
    }

    @Override
    public boolean handleEscape() {
        if (probe.isActive()) {
            probe.exit();
            return true;
        }
        if (showSpec) {
            showSpec = false;
            return true;
        }
        return false;
    }

    @Override
    public void navigateUp() {
        if (probe.isActive()) {
            return;
        }
        tableState.selectPrevious();
    }

    @Override
    public void navigateDown() {
        if (probe.isActive()) {
            return;
        }
        List<HttpEndpointInfo> visible = sortedVisibleEndpoints(ctx.findSelectedIntegration());
        tableState.selectNext(visible.size());
    }

    @Override
    public void onIntegrationChanged() {
        showSpec = false;
        specLines = Collections.emptyList();
        specTitle = null;
        specScroll = 0;
        probe.exit();
    }

    @Override
    protected void renderContent(Frame frame, Rect area, IntegrationInfo info) {
        if (probe.isActive()) {
            probe.render(frame, area);
            return;
        }

        if (showSpec) {
            renderSpec(frame, area);
            return;
        }

        List<HttpEndpointInfo> visible = sortedVisibleEndpoints(info);

        detailPanelHeight = Math.max(3, Math.min(detailPanelHeight, area.height() - 5));
        List<Rect> chunks = Layout.vertical()
                .constraints(Constraint.fill(), Constraint.length(detailPanelHeight))
                .split(area);

        renderTable(frame, chunks.get(0), visible, info);
        vSplit.setBorderPos(chunks.get(1).y());
        renderDetail(frame, chunks.get(1), visible);
    }

    @Override
    public void renderFooter(List<Span> spans) {
        if (probe.isActive()) {
            probe.renderFooter(spans);
            return;
        }
        if (showSpec) {
            hint(spans, "c/Esc", "close");
            hint(spans, TuiIcons.HINT_SCROLL, "scroll");
            hintLast(spans, "PgUp/PgDn", "page");
            return;
        }
        hint(spans, "Esc", "back");
        hint(spans, "Enter", "probe");
        hint(spans, "s", "sort");
        String[] filterLabels = { "all", "rest", "http" };
        hint(spans, "f", "filter [" + filterLabels[filter] + "]");
        hint(spans, "m", "management" + (showManagement ? " [on]" : " [off]"));
        List<HttpEndpointInfo> hVisible = sortedVisibleEndpoints(ctx.findSelectedIntegration());
        Integer hSel = tableState.selected();
        if (hSel != null && hSel >= 0 && hSel < hVisible.size() && hVisible.get(hSel).specificationUri != null) {
            hintLast(spans, "c", "spec");
        }
    }

    void handlePaste(String text) {
        if (probe.isActive()) {
            probe.handlePaste(text);
        }
    }

    private void enterProbeModeFromTable() {
        List<HttpEndpointInfo> visible = sortedVisibleEndpoints(ctx.findSelectedIntegration());
        Integer sel = tableState.selected();
        if (sel == null || sel < 0 || sel >= visible.size()) {
            return;
        }
        probe.enter(visible.get(sel));
    }

    // ---- Table rendering ----

    List<HttpEndpointInfo> sortedVisibleEndpoints(IntegrationInfo info) {
        List<HttpEndpointInfo> visible = visibleEndpoints(info);
        visible.sort((a, b) -> {
            int result = switch (sort) {
                case "path" -> compareStr(a.path, b.path);
                case "total" -> Long.compare(b.hits, a.hits);
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

    static Style methodStyle(String method) {
        if (method == null) {
            return Style.EMPTY;
        }
        String m = method.split(",")[0].trim().toUpperCase(Locale.ENGLISH);
        return switch (m) {
            case "GET" -> Theme.success();
            case "POST" -> Theme.label();
            case "PUT" -> Style.EMPTY.fg(Theme.accent());
            case "DELETE" -> Theme.error();
            case "PATCH" -> Theme.warning();
            default -> Style.EMPTY.dim();
        };
    }

    private Title buildTableTitle(IntegrationInfo info, List<HttpEndpointInfo> visible) {
        List<Span> spans = new ArrayList<>();
        spans.add(Span.raw(" HTTP Services [" + visible.size() + "]"));
        if (info.httpServer != null) {
            spans.add(Span.raw("  "));
            spans.add(Span.styled("Server: ", Theme.muted()));
            spans.add(Span.styled(info.httpServer, Style.EMPTY.fg(Theme.accent())));
        }
        long restCount = info.httpEndpoints.stream().filter(e -> e.fromRest && !e.specification).count();
        long specCount = info.httpEndpoints.stream().filter(e -> e.specification).count();
        long httpCount = info.httpEndpoints.stream().filter(e -> !e.fromRest && !e.management).count();
        long mgmtCount = info.httpEndpoints.stream().filter(e -> e.management).count();
        if (restCount > 0) {
            spans.add(Span.raw("  "));
            spans.add(Span.styled("REST: ", Theme.success()));
            spans.add(Span.raw(restCount + ""));
        }
        if (specCount > 0) {
            spans.add(Span.raw("  "));
            spans.add(Span.styled("Spec: ", Theme.notice()));
            spans.add(Span.raw(specCount + ""));
        }
        if (httpCount > 0) {
            spans.add(Span.raw("  "));
            spans.add(Span.styled("HTTP: ", Style.EMPTY.fg(Theme.accent())));
            spans.add(Span.raw(httpCount + ""));
        }
        if (mgmtCount > 0) {
            spans.add(Span.raw("  "));
            spans.add(Span.styled("Management: ", Theme.label().dim()));
            spans.add(Span.raw(mgmtCount + ""));
        }
        spans.add(Span.raw(" "));
        return Title.from(Line.from(spans));
    }

    private void renderTable(Frame frame, Rect area, List<HttpEndpointInfo> visible, IntegrationInfo info) {
        List<Row> rows = new ArrayList<>();
        for (HttpEndpointInfo ep : visible) {
            String method = ep.method != null ? ep.method : "";
            String path = ep.path != null ? ep.path : (ep.url != null ? ep.url : "");
            String consumes = ep.consumes != null ? ep.consumes : "";
            String produces = ep.produces != null ? ep.produces : "";
            String source;
            if (ep.management) {
                source = "Management";
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
                    Cell.from(Span.styled(path,
                            ep.url != null ? Theme.info().hyperlink(ep.url) : Style.EMPTY)),
                    rightCell(hitsStr, 8),
                    Cell.from(consumes),
                    Cell.from(produces),
                    Cell.from(Span.styled(source,
                            ep.specification ? Theme.notice()
                                    : ep.fromRest ? Theme.success()
                                    : Style.EMPTY.fg(Theme.accent()))),
                    Cell.from(Span.styled(state,
                            "Stopped".equals(state) ? Theme.error() : Style.EMPTY))));
        }

        Title title = buildTableTitle(info, visible);

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
                        Constraint.length(9))
                .highlightStyle(Theme.selectionBg())
                .highlightSpacing(Table.HighlightSpacing.ALWAYS)
                .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL).title(title).build())
                .build();

        lastTableArea = area;
        frame.renderStatefulWidget(table, area, tableState);
        renderScrollbar(frame, visible.size());
    }

    // ---- Detail panel ----

    private void renderDetail(Frame frame, Rect area, List<HttpEndpointInfo> visible) {
        Integer sel = tableState.selected();
        if (sel == null || sel < 0 || sel >= visible.size()) {
            frame.renderWidget(
                    Paragraph.builder()
                            .text(Text.from(Line.from(
                                    Span.styled(" Select an endpoint to view details",
                                            Style.EMPTY.dim()))))
                            .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
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
        if (ep.url != null && !ep.url.isEmpty()) {
            lines.add(Line.from(
                    Span.styled(String.format("  %-10s ", "URL:"), Theme.muted()),
                    Span.styled(ep.url, Theme.info().hyperlink(ep.url))));
        }
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
                        .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL).title(detailTitle).build())
                        .build(),
                area);
    }

    private static void addDetailLine(List<Line> lines, String label, String value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        lines.add(Line.from(
                Span.styled(String.format("  %-10s ", label + ":"), Theme.muted()),
                Span.raw(value)));
    }

    // ---- Spec viewer ----

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

        ctx.backgroundExecutor.execute(() -> {
            try {
                loadSpecInBackground(pid, specUri, operationId);
            } finally {
                specLoading.set(false);
            }
        });
    }

    private void loadSpecInBackground(String pid, String specUri, String operationId) {
        JsonObject root = new JsonObject();
        root.put("action", "rest-spec");
        root.put("filter", specUri);

        JsonObject jo = ctx.executeAction(pid, root, 5000);

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
                        .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL).title(title).build())
                        .build(),
                area);
    }

    // ---- Selection / MCP ----

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

    @Override
    public String description() {
        return "HTTP endpoint probe — lightweight Postman for testing REST/HTTP endpoints";
    }

    @Override
    public String getHelpText() {
        return """
                # HTTP

                The HTTP tab shows all HTTP endpoints exposed by this integration and
                lets you send test requests interactively — a lightweight Postman built
                into the terminal. This includes REST API endpoints, management endpoints
                (health, metrics), and any other HTTP routes.

                ## Endpoint List

                - **METHOD** — HTTP method: `GET`, `POST`, `PUT`, `DELETE`, `PATCH`, etc.
                - **PATH** — URL path for this endpoint
                - **TOTAL** — Number of HTTP requests received since startup
                - **CONSUMES** — Content-Type this endpoint accepts
                - **PRODUCES** — Content-Type this endpoint returns
                - **SOURCE** — How the endpoint was registered: REST(code), REST(contract), HTTP, Management
                - **STATE** — Endpoint state: `Started` or `Stopped`

                ## HTTP Probe

                Press `Enter` on an endpoint to open the interactive HTTP probe.

                The probe has these sections:

                - **Method**: `Left/Right` arrows to cycle through HTTP methods
                - **URL**: Read-only full URL (with resolved placeholders). Clickable as hyperlink
                - **Path**: Editable URL path template (e.g., `/api/users/{id}`)
                - **Path Params**: Auto-detected from `{xxx}` placeholders in the path. Fill in values and they get substituted in the URL
                - **Query Params**: Key-value pairs appended to the URL as `?key=value`. Press `+` to add
                - **Content-Type**: Cycle through common content types with `Left/Right` arrows
                - **Accept**: Cycle through common accept types with `Left/Right` arrows
                - **Headers**: Custom request headers. Press `+` to add
                - **Body**: Multi-line request body (Enter for newline). Supports `file:payload.json` to load from disk
                - **Response**: HTTP status code, elapsed time, response headers, and body
                - **History**: Recent requests with replay support

                Press `F5` to send the request. Press `p` to toggle pretty-print for JSON responses.

                ## Keys

                ### Endpoint List
                - `Up/Down` — select endpoint
                - `Enter` — open HTTP probe for selected endpoint
                - `s` — cycle sort column
                - `S` — reverse sort order
                - `f` — cycle filter (all / rest / http)
                - `m` — toggle management endpoints
                - `c` — view OpenAPI spec (when available)

                ### HTTP Probe
                - `F5` — send request
                - `Tab/Down` — next field
                - `Up` — previous field
                - `Enter` — newline in body, advance in other fields
                - `Left/Right` — cycle method, content-type, accept; cursor in text fields
                - `+` — add query param or header
                - `p` — toggle pretty-print
                - `PgUp/PgDn` — scroll response
                - `Esc` — close probe
                """;
    }

    @Override
    public boolean setInputValue(String field, String value) {
        if (probe.isActive()) {
            return probe.setInputValue(field, value);
        }
        return false;
    }

    @Override
    public JsonObject getTableDataAsJson() {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null) {
            return null;
        }

        if (probe.isActive()) {
            return probe.toJson();
        }

        JsonObject result = new JsonObject();
        result.put("tab", "HTTP");

        JsonArray rows = new JsonArray();
        for (HttpEndpointInfo hi : info.httpEndpoints) {
            JsonObject row = new JsonObject();
            row.put("method", hi.method);
            row.put("path", hi.path);
            row.put("url", hi.url);
            row.put("hits", hi.hits);
            row.put("routeId", hi.routeId);
            row.put("state", hi.state);
            row.put("fromRest", hi.fromRest);
            row.put("contractFirst", hi.contractFirst);
            if (hi.consumes != null) {
                row.put("consumes", hi.consumes);
            }
            if (hi.produces != null) {
                row.put("produces", hi.produces);
            }
            if (hi.description != null) {
                row.put("description", hi.description);
            }
            if (hi.operationId != null) {
                row.put("operationId", hi.operationId);
            }
            rows.add(row);
        }
        result.put("rows", rows);
        result.put("totalRows", info.httpEndpoints.size());
        if (info.httpServer != null) {
            result.put("httpServer", info.httpServer);
        }
        Integer sel = tableState.selected();
        result.put("selectedIndex", sel != null ? sel : -1);
        return result;
    }
}
