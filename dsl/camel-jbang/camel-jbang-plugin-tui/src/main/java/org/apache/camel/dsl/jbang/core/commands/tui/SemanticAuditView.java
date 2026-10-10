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

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Layout;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.input.TextInputState;
import dev.tamboui.widgets.paragraph.Paragraph;
import dev.tamboui.widgets.table.Row;
import dev.tamboui.widgets.table.Table;
import dev.tamboui.widgets.table.TableState;
import org.apache.camel.util.json.JsonObject;

import static org.apache.camel.dsl.jbang.core.commands.tui.SemanticDetails.text;
import static org.apache.camel.dsl.jbang.core.commands.tui.TuiHelper.hint;

/** Audit browsing owns its state independently of live definitions and inference drafts. */
final class SemanticAuditView {
    private static final Set<String> FILTERS
            = Set.of("category", "action", "expert", "routeId", "namespace", "correlationId", "since");
    private final MonitorContext context;
    private final TableState selection = new TableState();
    private String pid;
    private String filter = "";
    private Map<String, String> filters = Map.of();
    private TextInputState input;
    private JsonObject page;
    private JsonObject details;
    private CompletableFuture<JsonObject> pending;
    private CompletableFuture<JsonObject> pendingDetails;
    private JsonObject requestedQuery;
    private JsonObject queuedQuery;
    private String cursor;
    private String requestedDetail;
    private String queuedDetail;
    private String selectedId;
    private String error;
    private int scroll;
    private boolean detailFocused;

    SemanticAuditView(MonitorContext context) {
        this.context = context;
    }

    void reset() {
        if (pending != null) {
            pending.cancel(true);
        }
        if (pendingDetails != null) {
            pendingDetails.cancel(true);
        }
        pid = null;
        pending = null;
        pendingDetails = null;
        requestedQuery = null;
        queuedQuery = null;
        cursor = null;
        requestedDetail = null;
        queuedDetail = null;
        page = null;
        details = null;
        selectedId = null;
        error = null;
        input = null;
        filter = "";
        filters = Map.of();
        scroll = 0;
        detailFocused = false;
        selection.clearSelection();
    }

    private String currentPid() {
        IntegrationInfo info = context.findSelectedIntegration();
        return info == null ? null : info.phantom ? info.linkedPid : info.pid;
    }

    void load() {
        query(null);
    }

    private void query(String cursor) {
        String selected = currentPid();
        if (!Objects.equals(pid, selected)) {
            reset();
            pid = selected;
        }
        if (pid == null) {
            error = "Select a connected integration to browse audit history.";
            return;
        }
        JsonObject request = new JsonObject(filters);
        request.put("action", "semantic-audit");
        request.put("limit", 50);
        // The connector action and the decision-action filter use different transport names.
        if (filters.containsKey("action")) {
            request.put("auditAction", filters.get("action"));
        }
        if (cursor != null) {
            request.put("cursor", cursor);
        }
        error = null;
        if (pending != null) {
            queuedQuery = request.equals(requestedQuery) ? null : request;
        } else {
            startQuery(request);
        }
    }

    private void startQuery(JsonObject request) {
        requestedQuery = request;
        cursor = request.getString("cursor");
        pending = execute(request);
    }

    private CompletableFuture<JsonObject> execute(JsonObject request) {
        String target = pid;
        return CompletableFuture
                .supplyAsync(() -> context.executeIndependentAction(target, request, 10000), context.backgroundExecutor)
                .exceptionally(failure -> new JsonObject(Map.of("error", "audit_query_failed")));
    }

    void refresh() {
        if (pending != null && pending.isDone()) {
            JsonObject response = pending.getNow(null);
            pending = null;
            if (queuedQuery != null) {
                JsonObject next = queuedQuery;
                queuedQuery = null;
                startQuery(next);
                return;
            }
            if (response == null || response.containsKey("error") || !response.containsKey("records")) {
                error = response != null && response.containsKey("error")
                        ? response.getString("error") : "Audit history is unavailable or disconnected.";
            } else {
                page = response;
                error = null;
                List<JsonObject> rows = rows();
                int index = -1;
                for (int i = 0; i < rows.size(); i++) {
                    if (Objects.equals(selectedId, rows.get(i).getString("eventId"))) {
                        index = i;
                        break;
                    }
                }
                if (index < 0 && selectedId == null && !rows.isEmpty()) {
                    index = 0;
                }
                if (index < 0) {
                    selection.clearSelection();
                    if (selectedId != null) {
                        select(selectedId);
                    } else {
                        details = null;
                    }
                } else {
                    selection.select(index);
                    select(rows.get(index).getString("eventId"));
                }
            }
        }
        if (pendingDetails != null && pendingDetails.isDone()) {
            JsonObject response = pendingDetails.getNow(null);
            pendingDetails = null;
            if (Objects.equals(requestedDetail, selectedId)) {
                if (response != null && !response.containsKey("error")) {
                    details = response;
                } else {
                    error = "Selected audit details are unavailable.";
                }
            }
            if (queuedDetail != null) {
                String next = queuedDetail;
                queuedDetail = null;
                startDetails(next);
            }
        }
    }

    boolean ensureLoaded() {
        refresh();
        if (page == null && pending == null && error == null) {
            load();
        }
        return pending != null || selectedId != null && pendingDetails != null;
    }

    String error() {
        refresh();
        return error;
    }

    List<JsonObject> rows() {
        return SemanticTab.objects(page, "records");
    }

    private JsonObject selected() {
        if (details != null && details.get("record") instanceof JsonObject record) {
            return record;
        }
        return rows().stream().filter(r -> Objects.equals(selectedId, r.getString("eventId"))).findFirst().orElse(null);
    }

    void select(String id) {
        if (pid == null) {
            error = "Select a connected integration.";
            return;
        }
        selectedId = id;
        details = null;
        scroll = 0;
        if (pendingDetails != null) {
            queuedDetail = id.equals(requestedDetail) ? null : id;
        } else {
            startDetails(id);
        }
    }

    private void startDetails(String id) {
        requestedDetail = id;
        JsonObject request = new JsonObject(Map.of("action", "semantic-audit", "eventId", id));
        pendingDetails = execute(request);
    }

    void navigate(int delta) {
        refresh();
        if (detailFocused) {
            scroll = Math.max(0, scroll + delta);
            return;
        }
        if (rows().isEmpty()) {
            return;
        }
        int index = selection.selected() == null ? 0 : selection.selected();
        selection.select(Math.max(0, Math.min(rows().size() - 1, index + delta)));
        select(rows().get(selection.selected()).getString("eventId"));
    }

    boolean setFilter(String text) {
        if (!Objects.equals(pid, currentPid())) {
            reset();
            pid = currentPid();
        }
        Map<String, String> parsed = new LinkedHashMap<>();
        if (!text.isBlank()) {
            for (String token : text.trim().split("\\s+")) {
                String[] pair = token.split("=", 2);
                if (pair.length != 2 || !FILTERS.contains(pair[0]) || pair[1].isBlank() || pair[1].length() > 256) {
                    error = "Use field=value filters: category action expert routeId namespace correlationId since";
                    return false;
                }
                if ("since".equals(pair[0])) {
                    try {
                        Instant.parse(pair[1]);
                    } catch (Exception e) {
                        error = "since requires a UTC ISO-8601 timestamp";
                        return false;
                    }
                }
                parsed.put(pair[0], pair[1]);
            }
        }
        queuedDetail = null;
        filters = Map.copyOf(parsed);
        filter = text;
        selection.clearSelection();
        selectedId = null;
        details = null;
        page = null;
        query(null);
        return true;
    }

    boolean setInput(String field, String value) {
        if ("audit.filter".equals(field)) {
            return setFilter(value);
        }
        if ("audit.eventId".equals(field) && !value.isBlank() && value.length() <= 256) {
            select(value);
            return true;
        }
        if ("audit.page".equals(field)) {
            if ("latest".equals(value)) {
                query(null);
                return true;
            }
            if ("older".equals(value) && page != null && page.getString("nextCursor") != null) {
                query(page.getString("nextCursor"));
                return true;
            }
        }
        return false;
    }

    boolean inputActive() {
        return input != null;
    }

    void paste(String value) {
        if (input != null) {
            FormHelper.handlePaste(value, input);
        }
    }

    boolean escape() {
        if (input != null) {
            input = null;
            return true;
        }
        if (detailFocused) {
            detailFocused = false;
            return true;
        }
        if (!filter.isEmpty()) {
            setFilter("");
            return true;
        }
        return false;
    }

    boolean key(KeyEvent key) {
        refresh();
        if (input != null) {
            if (key.isCancel()) {
                input = null;
            } else if (key.isConfirm()) {
                if (setFilter(input.text())) {
                    input = null;
                }
            } else {
                FormHelper.handleTextInput(key, input);
            }
            return true;
        }
        if (key.isChar('/')) {
            input = new TextInputState(filter);
            return true;
        }
        if (key.isChar('r')) {
            query(queuedQuery == null ? cursor : queuedQuery.getString("cursor"));
            return true;
        }
        if (key.isChar('n')) {
            setInput("audit.page", "older");
            return true;
        }
        if (key.isChar('g')) {
            query(null);
            return true;
        }
        if (key.isKey(KeyCode.TAB) || key.isConfirm()) {
            detailFocused = !detailFocused;
            return true;
        }
        if (key.isUp() || key.isDown() || key.isPageUp() || key.isPageDown()) {
            navigate((key.isUp() || key.isPageUp() ? -1 : 1) * (key.isPageUp() || key.isPageDown() ? 10 : 1));
            return true;
        }
        return false;
    }

    boolean detailFocused() {
        return detailFocused;
    }

    SelectionContext selectionContext() {
        return new SelectionContext(
                "table", rows().stream().map(r -> r.getString("eventId")).toList(),
                selection.selected() == null ? -1 : selection.selected(), rows().size(), "Semantic Audit");
    }

    JsonObject snapshot() {
        refresh();
        JsonObject state = new JsonObject();
        state.put("tab", "Semantic");
        state.put("view", "Audit");
        state.put("rows", rows());
        state.put("totalRows", rows().size());
        state.put("selectedIndex", selection.selected());
        state.put("selectedEventId", selectedId);
        state.put("selectedRecord", selected());
        state.put("evidence", SemanticTab.objects(details, "evidence"));
        state.put("filters", filters);
        state.put("filter", filter);
        state.put("pending", pending != null || pendingDetails != null);
        state.put("connected", currentPid() != null && Objects.equals(pid, currentPid()));
        state.put("error", error);
        state.put("focusedPane", detailFocused ? "detail" : "list");
        for (String key : List.of("audit", "nextCursor", "evicted", "cursorExpired")) {
            state.put(key, page == null ? null : page.get(key));
        }
        return state;
    }

    void render(Frame frame, Rect area) {
        refresh();
        List<Rect> panels
                = Layout.vertical().constraints(Constraint.length(3), Constraint.percentage(42), Constraint.fill()).split(area);
        JsonObject status = page == null ? null : page.getJsonObject("audit");
        String statusLine = status == null
                ? "Audit history" : "Master: " + status.get("enabled") + "   Experts: " + status.get("experts")
                                    + "   OpenTelemetry: " + status.get("openTelemetry") + "   Reader: " + status.get("reader")
                                    + "   Dropped: " + status.get("dropped") + "   Sink errors: " + status.get("sinkErrors");
        String note = error != null ? error : pending != null ? "Loading..."
                : "Evicted: " + (page == null ? 0 : page.get("evicted"))
                  + (page != null && Boolean.TRUE.equals(page.get("cursorExpired"))
                          ? "   Older cursor expired; refresh for retained history." : "")
                  + (filter.isBlank() ? "" : "   " + filter);
        frame.renderWidget(Paragraph.builder().text(statusLine + "\n" + note).build(), panels.get(0));
        boolean wide = area.width() >= 140;
        List<Row> content = new ArrayList<>();
        for (JsonObject row : rows()) {
            String timestamp = text(row, "timestamp");
            String time = timestamp.length() > 23 ? timestamp.substring(11, 23) + "Z" : timestamp;
            content.add(wide
                    ? Row.from(time, text(row, "action"), text(row, "category"), text(row, "operation"), text(row, "target"),
                            text(row, "namespace"), text(row, "reasonCode"), text(row, "correlationId"))
                    : Row.from(time, text(row, "action"), text(row, "category"), text(row, "expert"), text(row, "reasonCode")));
        }
        Block tableBlock = block("Audit history [" + rows().size() + "]", !detailFocused);
        if (content.isEmpty()) {
            frame.renderWidget(Paragraph.builder().text("No retained records match this query. Capture may be disabled.")
                    .block(tableBlock).build(), panels.get(1));
        } else {
            Table table = Table.builder().rows(content)
                    .header((wide
                            ? Row.from("TIMESTAMP", "ACTION", "CATEGORY", "OPERATION", "TARGET", "NAMESPACE", "REASON CODE",
                                    "CORRELATION")
                            : Row.from("TIMESTAMP", "ACTION", "CATEGORY", "EXPERT", "REASON CODE")).style(Theme.label()))
                    .widths(wide
                            ? List.of(Constraint.length(14), Constraint.length(8), Constraint.length(11), Constraint.length(13),
                                    Constraint.percentage(15), Constraint.percentage(10), Constraint.fill(),
                                    Constraint.length(13))
                            : List.of(Constraint.length(14), Constraint.length(8), Constraint.length(11),
                                    Constraint.percentage(20), Constraint.fill()))
                    .block(tableBlock).highlightSymbol("› ").highlightStyle(Theme.selectionBg()).build();
            frame.renderStatefulWidget(table, panels.get(1), selection);
        }
        List<Line> lines = new ArrayList<>();
        JsonObject record = selected();
        if (record == null) {
            lines.add(Line.from("Select an audit record."));
        } else {
            for (String key : List.of("eventId", "category", "timestamp", "action", "operation", "target", "namespace",
                    "reasonCode", "correlationId", "policyId", "policyVersion", "rule", "expert", "definition", "origin",
                    "routeId", "invocationId", "batchId", "status")) {
                if (record.get(key) != null) {
                    lines.add(Line.from(Span.styled(key + "  ", Theme.label()), Span.raw(String.valueOf(record.get(key)))));
                }
            }
            resultLines(lines, record);
            for (JsonObject linked : SemanticTab.objects(details, "evidence")) {
                lines.add(Line.empty());
                lines.add(Line.from(Span.styled("Evidence: " + text(linked, "eventId"), Style.EMPTY.fg(Theme.accent()))));
                if (Boolean.TRUE.equals(linked.get("unavailable"))) {
                    lines.add(Line.from("Unavailable in this backend (disabled, evicted, or not delivered)."));
                } else {
                    lines.add(Line
                            .from(text(linked, "expert") + " / " + text(linked, "operation") + " · " + text(linked, "status")));
                    resultLines(lines, linked);
                }
            }
            lines.add(Line.empty());
            lines.add(Line.from("Input, headers, arbitrary metadata and submitted score descriptions are omitted."));
        }
        Block detailsBlock = block("Record details", detailFocused);
        frame.renderWidget(detailsBlock, panels.get(2));
        SemanticDetails.paragraph(frame, detailsBlock.inner(panels.get(2)), lines, scroll);
    }

    private static void resultLines(List<Line> lines, JsonObject record) {
        for (String key : List.of("startedAt", "provider", "model", "revision")) {
            if (record.get(key) != null) {
                lines.add(Line.from(Span.styled(key + "  ", Theme.label()), Span.raw(String.valueOf(record.get(key)))));
            }
        }
        if (record.get("resultOmitted") != null) {
            lines.add(Line.from("Result unavailable: " + text(record, "resultOmitted")));
        }
        if (!(record.get("result") instanceof JsonObject result)) {
            return;
        }
        JsonObject response = new JsonObject(result);
        response.put("status", "success");
        response.put("elapsedMillis", record.get("durationNanos") instanceof Number n ? n.longValue() / 1000000 : 0);
        JsonObject semantics = record.getJsonObject("semantics");
        JsonObject contract = new JsonObject();
        if (semantics != null) {
            contract.put("resultMeaning", semantics.get("meaning"));
            contract.put("probabilityMeaning", semantics.get("probabilityMeaning"));
        }
        JsonObject operation = new JsonObject();
        operation.put("contract", contract);
        operation.put("resultType", semantics == null ? "" : semantics.get("resultType"));
        lines.addAll(SemanticResultView.lines(response, operation, List.of()));
    }

    private static Block block(String title, boolean focused) {
        return Block.builder().title(title).borders(Borders.ALL).borderType(BorderType.ROUNDED)
                .borderStyle(focused ? Style.EMPTY.fg(Theme.accent()) : Theme.border()).build();
    }

    void footer(List<Span> spans) {
        if (input != null) {
            spans.add(Span.raw(" /" + input.text() + "█  "));
            hint(spans, "Enter", "apply");
            hint(spans, "Esc", "cancel");
        } else {
            hint(spans, "v", "view");
            hint(spans, "/", "filter");
            hint(spans, "Tab", "pane");
            hint(spans, "n", "older");
            hint(spans, "g", "latest");
            hint(spans, "r", "refresh");
        }
    }
}
