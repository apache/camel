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
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;

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
import dev.tamboui.widgets.table.Cell;
import dev.tamboui.widgets.table.Row;
import dev.tamboui.widgets.table.Table;
import dev.tamboui.widgets.table.TableState;
import org.apache.camel.util.json.JsonObject;

import static org.apache.camel.dsl.jbang.core.commands.tui.SemanticDetails.text;
import static org.apache.camel.dsl.jbang.core.commands.tui.TuiHelper.hint;

/** Audit browsing owns its state independently of live definitions and inference drafts. */
final class SemanticAuditView {
    private static final Set<String> FILTERS
            = Set.of("category", "action", "expert", "routeId", "correlationId", "since");
    private final MonitorContext context;
    private final LongSupplier nanoTime;
    private long lastRefresh;
    private boolean paused;
    private String returnId;
    private int evidenceIndex;
    private final TableState selection = new TableState();
    private String pid;
    private String filter = "";
    private Map<String, String> filters = Map.of();
    private TextInputState input;
    private AutocompletePopup filterChoices;
    private String filterField;
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
        this(context, System::nanoTime);
    }

    SemanticAuditView(MonitorContext context, LongSupplier nanoTime) {
        this.context = context;
        this.nanoTime = nanoTime;
    }

    void reset() {
        if (pending != null) {
            pending.cancel(true);
        }
        if (pendingDetails != null) {
            pendingDetails.cancel(true);
        }
        pid = null;
        paused = false;
        returnId = null;
        evidenceIndex = 0;
        lastRefresh = 0;
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
        filterChoices = null;
        filterField = null;
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
            lastRefresh = nanoTime.getAsLong();
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
                        refreshSelected(selectedId);
                    } else {
                        details = null;
                    }
                } else {
                    selection.select(index);
                    refreshSelected(rows.get(index).getString("eventId"));
                }
            }
        }
        if (pendingDetails != null && pendingDetails.isDone()) {
            JsonObject response = pendingDetails.getNow(null);
            pendingDetails = null;
            lastRefresh = nanoTime.getAsLong();
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
        if (!Objects.equals(pid, currentPid())) {
            reset();
            pid = currentPid();
        }
        refresh();
        if (pending == null && error == null && (page == null
                || pendingDetails == null && !paused && cursor == null && !inputActive()
                        && nanoTime.getAsLong() - lastRefresh >= 1_000_000_000L)) {
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

    private void refreshSelected(String id) {
        if (Objects.equals(selectedId, id) && details != null) {
            // Keep the inspector and scroll position while refreshing delivery/eviction of linked evidence.
            if (pendingDetails == null) {
                startDetails(id);
            }
        } else {
            select(id);
        }
    }

    void select(String id) {
        if (pid == null) {
            error = "Select a connected integration.";
            return;
        }
        if (Objects.equals(selectedId, id) && details != null) {
            return;
        }
        selectedId = id;
        int index = -1;
        for (int i = 0; i < rows().size(); i++) {
            if (Objects.equals(id, rows().get(i).getString("eventId"))) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            selection.clearSelection();
        } else {
            selection.select(index);
        }
        evidenceIndex = 0;
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
        returnId = null;
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
                    error = "Use field=value filters: category action expert routeId correlationId since";
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
        returnId = null;
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
            returnId = null;
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
        return input != null || filterChoices != null;
    }

    void paste(String value) {
        if (input != null) {
            FormHelper.handlePaste(value, input);
        }
    }

    boolean escape() {
        if (filterChoices != null) {
            filterChoices = null;
            filterField = null;
            return true;
        }
        if (input != null) {
            input = null;
            return true;
        }
        if (returnId != null) {
            String previous = returnId;
            returnId = null;
            select(previous);
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
        if (filterChoices != null) {
            if (filterChoices.handleKeyEvent(key) == AutocompletePopup.Result.CLOSED) {
                var choice = filterChoices.consumeSelectedItem();
                if (choice != null) {
                    Map<String, String> values = new LinkedHashMap<>(filters);
                    if (choice.insertText().isEmpty()) {
                        values.remove(filterField);
                    } else {
                        values.put(filterField, choice.insertText());
                    }
                    setFilter(values.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue())
                            .collect(Collectors.joining(" ")));
                }
                filterChoices = null;
                filterField = null;
            }
            return true;
        }
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
        for (var shortcut : Map.of('c', "category", 'a', "action", 'e', "expert").entrySet()) {
            if (key.isChar(shortcut.getKey())) {
                chooseFilter(shortcut.getValue());
                return true;
            }
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
        if (key.isChar(' ')) {
            paused = !paused;
            if (!paused && cursor == null) {
                query(null);
            }
            return true;
        }
        if (key.isChar('[') || key.isChar(']')) {
            int count = SemanticTab.objects(details, "evidence").size();
            if (count > 0) {
                evidenceIndex = Math.floorMod(evidenceIndex + (key.isChar('[') ? -1 : 1), count);
                scroll = 0;
            }
            return true;
        }
        if (key.isConfirm()) {
            List<JsonObject> evidence = SemanticTab.objects(details, "evidence");
            if (!evidence.isEmpty()) {
                JsonObject linked = evidence.get(Math.min(evidenceIndex, evidence.size() - 1));
                if (!Boolean.TRUE.equals(linked.get("unavailable"))) {
                    returnId = selectedId;
                    select(linked.getString("eventId"));
                }
            } else if (returnId != null) {
                escape();
            }
            return true;
        }
        if (key.isKey(KeyCode.TAB)) {
            detailFocused = !detailFocused;
            return true;
        }
        if (key.isUp() || key.isDown() || key.isPageUp() || key.isPageDown()) {
            navigate((key.isUp() || key.isPageUp() ? -1 : 1) * (key.isPageUp() || key.isPageDown() ? 10 : 1));
            return true;
        }
        return false;
    }

    private void chooseFilter(String field) {
        Set<String> values = new TreeSet<>();
        if ("category".equals(field)) {
            values.addAll(List.of("decision", "evaluation", "request"));
        } else if ("action".equals(field)) {
            values.addAll(List.of("allow", "block", "review"));
        } else if ("expert".equals(field) && page != null
                && page.get("audit") instanceof JsonObject audit
                && audit.get("experts") instanceof Map<?, ?> experts) {
            experts.keySet().forEach(name -> values.add(name.toString()));
        }
        for (JsonObject row : rows()) {
            if (row.get(field) != null) {
                values.add(text(row, field));
            }
        }
        if (filters.containsKey(field)) {
            values.add(filters.get(field));
        }
        List<AutocompletePopup.CompletionItem> items = new ArrayList<>();
        items.add(new AutocompletePopup.CompletionItem(
                "All", "Remove the " + field + " filter.", null, null,
                false, null, null, false, ""));
        for (String value : values) {
            if (!value.isBlank() && value.length() <= 256 && value.chars().noneMatch(Character::isWhitespace)) {
                items.add(new AutocompletePopup.CompletionItem(
                        value,
                        "Filter by exact " + field + ". Suggestions include values on this page; / accepts any exact value.",
                        null, null, false, null, null));
            }
        }
        filterChoices = new AutocompletePopup(items, "", "", true);
        filterChoices.setTitlePrefix("Audit " + field);
        filterChoices.setFullKeys(true);
        filterField = field;
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
        state.put("paused", paused);
        state.put("live", !paused && cursor == null && error == null);
        state.put("evidenceIndex", evidenceIndex);
        state.put("filterField", filterField);
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
        ensureLoaded();
        boolean wide = area.width() >= 140;
        List<Rect> panels = Layout.vertical().constraints(Constraint.length(wide ? 4 : 6),
                Constraint.length(Math.max(6, Math.min(14, area.height() * 42 / 100))),
                Constraint.fill(), Constraint.length(1)).split(area);
        JsonObject status = page == null ? null : page.getJsonObject("audit");
        renderHeader(frame, panels.get(0), status);
        renderTable(frame, panels.get(1), wide);
        renderDetails(frame, panels.get(2));
        renderHealth(frame, panels.get(3), status);
        if (filterChoices != null) {
            filterChoices.render(frame, area, 2, 0);
        }
    }

    private void renderHeader(Frame frame, Rect area, JsonObject status) {
        List<Span> indicators = new ArrayList<>();
        indicators.add(Span.styled("Audit default: ", Theme.label()));
        indicators.add(toggle(status == null ? null : status.get("enabled")));
        if (status != null && status.get("experts") instanceof Map<?, ?> experts) {
            experts.forEach((expert, enabled) -> {
                indicators.add(Span.styled("   " + expert + ": ", Theme.label()));
                indicators.add(toggle(enabled));
                indicators.add(Span.styled(" (override)", Theme.muted()));
            });
        }
        String otel = text(status, "openTelemetry");
        indicators.add(Span.styled("   OpenTelemetry: ", Theme.label()));
        indicators.add(toggle(switch (otel) {
            case "active" -> Boolean.TRUE;
            case "inactive" -> Boolean.FALSE;
            default -> null;
        }));
        List<Line> lines = new ArrayList<>();
        lines.add(Line.from(indicators));
        lines.add(Line.empty());
        List<Span> query = new ArrayList<>();
        query.add(Span.styled("Filters: ", Theme.label()));
        for (String field : List.of("category", "action", "expert")) {
            String shortcut = field.substring(0, 1);
            String label = Character.toUpperCase(field.charAt(0)) + field.substring(1);
            query.add(Span.raw(" ["));
            query.add(Span.styled(shortcut, Theme.label().underlined()));
            query.add(Span.raw(" " + label + ": " + filters.getOrDefault(field, "all") + " ▾] "));
        }
        query.add(Span.styled(filters.containsKey("since") ? " Since " + filters.get("since") : " All retained time",
                Theme.muted()));
        query.add(Span.styled("   Backend: " + text(status, "reader"), Theme.info()));
        query.add(Span.styled("   / edit", Theme.muted()));
        lines.add(Line.from(query));
        List<String> other = new ArrayList<>();
        for (String field : List.of("routeId", "correlationId")) {
            if (filters.containsKey(field)) {
                other.add(field + "=" + filters.get(field));
            }
        }
        if (error != null) {
            lines.add(Line.from(Span.styled(error + " · r to retry", Theme.error())));
        } else if (page != null && Boolean.TRUE.equals(page.get("cursorExpired"))) {
            lines.add(Line.from(Span.styled("Older cursor expired; press g for retained history.", Theme.warning())));
        } else if (!other.isEmpty()) {
            lines.add(Line.from(Span.styled(String.join("   ", other), Theme.muted())));
        }
        SemanticDetails.paragraph(frame, area, lines, 0);
    }

    private void renderTable(Frame frame, Rect area, boolean wide) {
        List<Row> content = new ArrayList<>();
        for (JsonObject row : rows()) {
            Cell action = Cell.from(Span.styled(text(row, "action").toUpperCase(Locale.ROOT), actionStyle(row)));
            Cell reason = Cell.from(Span.styled(text(row, "reasonCode"),
                    "failed".equals(row.get("status")) || "timeout".equals(row.get("status"))
                            ? Theme.error() : Theme.muted()));
            content.add(wide
                    ? Row.from(Cell.from(time(row, "HH:mm:ss.SSS")), action, Cell.from(text(row, "category")),
                            Cell.from(text(row, "operation")), Cell.from(text(row, "target")),
                            reason, Cell.from(text(row, "correlationId")))
                    : Row.from(Cell.from(time(row, "HH:mm:ss.SSS")), action, Cell.from(text(row, "category")),
                            Cell.from(text(row, "expert")), reason));
        }
        Block tableBlock = block("Audit records [" + rows().size() + "] · " + mode(), !detailFocused);
        frame.renderWidget(tableBlock, area);
        Rect inner = tableBlock.inner(area);
        List<Rect> parts = Layout.vertical().constraints(Constraint.fill(), Constraint.length(1)).split(inner);
        if (content.isEmpty()) {
            frame.renderWidget(Paragraph.builder().text(pending != null
                    ? "Loading audit history…"
                    : "No retained records match this query. Capture may be disabled.").build(), parts.get(0));
        } else {
            Table table = Table.builder().rows(content)
                    .header((wide
                            ? Row.from("TIMESTAMP", "DECISION", "CATEGORY", "OPERATION", "TARGET", "REASON CODE",
                                    "CORRELATION ID")
                            : Row.from("TIMESTAMP", "DECISION", "CATEGORY", "EXPERT", "REASON CODE")).style(Theme.label()))
                    .widths(wide
                            ? List.of(Constraint.length(14), Constraint.length(9), Constraint.length(11), Constraint.length(13),
                                    Constraint.percentage(15), Constraint.fill(),
                                    Constraint.length(16))
                            : List.of(Constraint.length(14), Constraint.length(9), Constraint.length(11),
                                    Constraint.percentage(20), Constraint.fill()))
                    .highlightSymbol("› ").highlightStyle(Theme.selectionBg()).build();
            frame.renderStatefulWidget(table, parts.get(0), selection);
        }
        String date = selected() == null ? "" : time(selected(), "dd MMM uuuu · XXX") + " · ";
        SemanticDetails.paragraph(frame, parts.get(1), List.of(Line.from(Span.styled(
                "Newest first · " + date + ZoneId.systemDefault() + (wide ? "    ↑↓ select · Enter follows linked record" : ""),
                Theme.muted()))), 0);
    }

    private void renderDetails(Frame frame, Rect area) {
        JsonObject record = selected();
        Block border = block("Record · " + (selectedId == null ? "none selected" : selectedId), detailFocused);
        frame.renderWidget(border, area);
        Rect inner = SemanticDetails.inset(border.inner(area));
        if (record == null) {
            SemanticDetails.paragraph(frame, inner, List.of(Line.from(selectedId == null ? "Select an audit record."
                    : pendingDetails != null ? "Loading record…" : "Record unavailable in this backend.")), 0);
            return;
        }
        boolean decision = "decision".equals(record.get("category"));
        Line heading = Line.from(Span.styled(text(record, decision ? "action" : "status").toUpperCase(Locale.ROOT) + "   ",
                actionStyle(record).bold()), Span.styled(text(record, "target"), Theme.title()),
                Span.styled("   " + text(record, "operation") + " · " + text(record, "correlationId"),
                        Theme.muted()));
        List<Line> left = new ArrayList<>();
        String section = switch (text(record, "category")) {
            case "decision" -> "Route decision";
            case "request" -> "Evaluation request";
            default -> "Expert evaluation";
        };
        left.add(Line.from(Span.styled(section, Theme.title())));
        if (decision) {
            add(left, "Policy", record, "policyId");
            add(left, "Policy version", record, "policyVersion");
            add(left, "Rule", record, "rule");
            add(left, "Route", record, "routeId");
            add(left, "Reason", record, "reasonCode");
            SemanticDetails.add(left, "Action", text(record, "action") + " · reported by the route");
        } else {
            evaluationLines(left, record);
        }
        left.add(Line.empty());
        for (String field : List.of("timestamp", "startedAt", "exchangeId", "correlationId", "invocationId", "batchId")) {
            add(left, switch (field) {
                case "startedAt" -> "Started";
                case "exchangeId" -> "Exchange";
                case "correlationId" -> "Correlation";
                case "invocationId" -> "Invocation";
                case "batchId" -> "Batch";
                default -> "Recorded";
            }, record, field);
        }
        List<Line> right = new ArrayList<>();
        List<JsonObject> evidence = SemanticTab.objects(details, "evidence");
        if (!evidence.isEmpty()) {
            JsonObject linked = evidence.get(Math.min(evidenceIndex, evidence.size() - 1));
            right.add(Line.from(Span.styled("Linked evaluation · " + text(linked, "eventId"), Theme.title())));
            if (evidence.size() > 1) {
                right.add(Line.from(Span.styled((evidenceIndex + 1) + " / " + evidence.size() + " · [ ] change evidence",
                        Theme.muted())));
            }
            if (Boolean.TRUE.equals(linked.get("unavailable"))) {
                right.add(Line.from(Span.styled("Unavailable (disabled, evicted, or not delivered).", Theme.warning())));
            } else {
                evaluationLines(right, linked);
            }
        } else if (decision) {
            right.add(Line.from(Span.styled(pendingDetails != null ? "Loading linked evidence…" : "No linked evidence.",
                    Theme.muted())));
        }
        if (returnId != null) {
            right.add(Line.from(Span.styled("Esc / Enter returns to the decision.", Theme.info())));
        }
        Line omitted = Line.from(Span.styled(
                "Input captured only by expert opt-in · Exchange headers and arbitrary metadata omitted", Theme.muted()));
        if (inner.width() < 100) {
            List<Line> all = new ArrayList<>(List.of(heading, Line.empty()));
            all.addAll(left);
            all.add(Line.empty());
            all.addAll(right);
            all.add(omitted);
            SemanticDetails.paragraph(frame, inner, all, scroll);
        } else {
            List<Rect> parts = Layout.vertical().constraints(Constraint.length(2), Constraint.fill(), Constraint.length(1))
                    .split(inner);
            SemanticDetails.paragraph(frame, parts.get(0), List.of(heading), 0);
            List<Rect> columns = Layout.horizontal().constraints(Constraint.percentage(48), Constraint.length(3),
                    Constraint.fill()).split(parts.get(1));
            SemanticDetails.paragraph(frame, columns.get(0), left, scroll);
            frame.renderWidget(Block.builder().borders(EnumSet.of(Borders.LEFT)).borderStyle(Theme.border()).build(),
                    columns.get(1));
            SemanticDetails.paragraph(frame, columns.get(2), right, scroll);
            SemanticDetails.paragraph(frame, parts.get(2), List.of(omitted), 0);
        }
    }

    private static void evaluationLines(List<Line> lines, JsonObject record) {
        add(lines, "Definition", record, "definition");
        SemanticDetails.add(lines, "Expert", text(record, "expert") + " · " + text(record, "operation"));
        if (record.containsKey("input")) {
            SemanticDetails.parameter(lines, "",
                    Boolean.TRUE.equals(record.get("inputRedacted")) ? "Input (redacted): " : "Input: ",
                    record.get("input"));
        } else {
            SemanticDetails.add(lines, "Input", record.containsKey("inputOmitted")
                    ? "omitted · " + text(record, "inputOmitted") : "not captured");
        }
        JsonObject semantics = record.getJsonObject("semantics");
        JsonObject result = record.getJsonObject("result");
        if (result != null) {
            lines.add(Line.from(Span.styled("Result: ", Theme.label()), Span.styled(text(result, "value"), Theme.info().bold()),
                    Span.styled(" · " + text(semantics, "resultType").toLowerCase(Locale.ROOT), Theme.muted())));
            add(lines, "Meaning", semantics, "meaning");
            add(lines, "Probability", result, "probability");
            if (result.get("probabilities") instanceof Map<?, ?> probabilities) {
                probabilities.forEach((label, value) -> SemanticDetails.add(lines, "P(" + label + ")", String.valueOf(value)));
            }
            add(lines, "Probability meaning", semantics, "probabilityMeaning");
            add(lines, "Confidence", result, "confidence");
            add(lines, "Confidence meaning", semantics, "confidenceMeaning");
        }
        add(lines, "Result unavailable", record, "resultOmitted");
        add(lines, "Status", record, "status");
        if (!"success".equals(record.get("status"))) {
            add(lines, "Reason", record, "reasonCode");
        }
        if (record.get("durationNanos") instanceof Number duration) {
            SemanticDetails.add(lines, "Duration", SemanticResultView.number(duration.doubleValue() / 1_000_000) + " ms");
        }
        for (String field : List.of("origin", "provider", "model", "revision")) {
            add(lines, Character.toUpperCase(field.charAt(0)) + field.substring(1), record, field);
        }
    }

    private static void add(List<Line> lines, String label, JsonObject record, String field) {
        if (record != null && record.get(field) != null) {
            SemanticDetails.add(lines, label, text(record, field));
        }
    }

    private void renderHealth(Frame frame, Rect area, JsonObject status) {
        if (status == null) {
            return;
        }
        List<Span> health = new ArrayList<>();
        if ("memory".equals(status.get("reader"))) {
            health.add(Span.styled("Memory: " + text(status, "retained") + " / " + text(status, "capacity") + " retained · ",
                    Theme.muted()));
        }
        health.add(Span.styled(text(page, "evicted") + " evicted · " + text(status, "dropped") + " dropped   ", Theme.muted()));
        List<String> failures = new ArrayList<>();
        if (status.get("sinkErrors") instanceof Map<?, ?> sinks) {
            sinks.forEach((name, count) -> {
                if (count instanceof Number n && n.longValue() > 0) {
                    failures.add(name + ": " + n + " errors");
                }
            });
        }
        for (String field : List.of("dropped", "observerErrors")) {
            if (status.get(field) instanceof Number n && n.longValue() > 0) {
                failures.add(n + ("dropped".equals(field) ? " dropped" : " observer errors"));
            }
        }
        if (error != null) {
            failures.add("query failed · showing last response");
        }
        health.add(Span.styled(failures.isEmpty() ? "● No delivery errors reported" : "● " + String.join(" · ", failures),
                failures.isEmpty() ? Theme.success() : Theme.warning()));
        health.add(Span.styled(" · " + mode() + (pending != null ? " · refreshing…" : ""), Theme.muted()));
        SemanticDetails.paragraph(frame, area, List.of(Line.from(health)), 0);
    }

    private String mode() {
        if (paused) {
            return "paused";
        }
        if (cursor != null) {
            return "older page";
        }
        return error == null ? "live · refresh 1 s" : "refresh stopped";
    }

    private static Span toggle(Object value) {
        return Span.styled(value == null ? "UNKNOWN" : Boolean.TRUE.equals(value) ? "ON" : "OFF",
                Boolean.TRUE.equals(value) ? Theme.success() : Theme.muted());
    }

    private static Style actionStyle(JsonObject record) {
        return switch (text(record, "action")) {
            case "allow" -> Theme.success();
            case "block", "deny", "reject" -> Theme.error();
            case "review" -> Theme.warning();
            default -> Theme.muted();
        };
    }

    private static String time(JsonObject record, String pattern) {
        try {
            return DateTimeFormatter.ofPattern(pattern, Locale.ROOT).withZone(ZoneId.systemDefault())
                    .format(Instant.parse(text(record, "timestamp")));
        } catch (RuntimeException e) {
            return text(record, "timestamp");
        }
    }

    private static Block block(String title, boolean focused) {
        return Block.builder().title(title).borders(Borders.ALL).borderType(BorderType.ROUNDED)
                .borderStyle(focused ? Style.EMPTY.fg(Theme.accent()) : Theme.border()).build();
    }

    void footer(List<Span> spans) {
        if (filterChoices != null) {
            hint(spans, "↑↓", "select");
            hint(spans, "Enter", "apply");
            hint(spans, "Esc", "cancel");
        } else if (input != null) {
            spans.add(Span.raw(" /" + input.text() + "█  "));
            hint(spans, "Enter", "apply");
            hint(spans, "Esc", "cancel");
        } else {
            hint(spans, "v", "view");
            hint(spans, "/", "filter");
            hint(spans, "↑↓", "select");
            hint(spans, "Tab", "detail");
            hint(spans, "Space", paused ? "resume" : "pause");
            hint(spans, "Enter", returnId == null ? "related record" : "decision");
            hint(spans, "n", "older");
            hint(spans, "g", "latest");
            hint(spans, "r", "refresh");
        }
    }
}
