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
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
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
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Layout;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.MouseEvent;
import dev.tamboui.tui.event.MouseEventKind;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.input.TextInputState;
import dev.tamboui.widgets.paragraph.Paragraph;
import dev.tamboui.widgets.scrollbar.Scrollbar;
import dev.tamboui.widgets.scrollbar.ScrollbarState;
import dev.tamboui.widgets.table.Cell;
import dev.tamboui.widgets.table.Row;
import dev.tamboui.widgets.table.Table;
import dev.tamboui.widgets.table.TableState;
import org.apache.camel.dsl.jbang.core.common.SemanticAuditFilters;
import org.apache.camel.dsl.jbang.core.common.SemanticMediaInput;
import org.apache.camel.dsl.jbang.core.common.SemanticResultFormatter;
import org.apache.camel.util.json.JsonObject;

import static org.apache.camel.dsl.jbang.core.commands.tui.SemanticDetails.text;
import static org.apache.camel.dsl.jbang.core.commands.tui.TuiHelper.hint;

/** Audit browsing owns its state independently of live definitions and inference drafts. */
final class SemanticAuditView {
    private static final Set<String> FILTERS
            = Stream.concat(SemanticAuditFilters.QUERY_FIELDS.stream(),
                    Stream.of("page.status", "page.result", "page.definition")).collect(Collectors.toUnmodifiableSet());
    private final MonitorContext context;
    private final SemanticImageView imageView;
    private final LongSupplier nanoTime;
    private final Supplier<Instant> now;
    private long lastRefresh;
    private boolean paused;
    private String returnId;
    private int caseIndex;
    private final TableState selection = new TableState();
    private String pid;
    private String filter = "";
    private long sinceSeconds;
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
    private String notice;
    private int scroll;
    private int detailLines;
    private int detailHeight;
    private Rect detailArea;
    private Rect tableArea;
    private final ScrollbarState scrollbar = new ScrollbarState();
    private boolean detailFocused;
    private boolean showInput;
    private int arrivals;
    private JsonObject caseData;
    private CompletableFuture<JsonObject> pendingCase;
    private String requestedCase;
    private JsonObject draft;

    SemanticAuditView(MonitorContext context) {
        this(context, System::nanoTime);
    }

    SemanticAuditView(MonitorContext context, LongSupplier nanoTime) {
        this(context, nanoTime, Instant::now);
    }

    SemanticAuditView(MonitorContext context, LongSupplier nanoTime, Supplier<Instant> now) {
        this.context = context;
        this.imageView = new SemanticImageView(context.backgroundExecutor);
        this.nanoTime = nanoTime;
        this.now = now;
    }

    void reset() {
        imageView.reset();
        if (pending != null) {
            pending.cancel(true);
        }
        if (pendingDetails != null) {
            pendingDetails.cancel(true);
        }
        pid = null;
        paused = false;
        returnId = null;
        caseIndex = 0;
        showInput = false;
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
        notice = null;
        input = null;
        filterChoices = null;
        filterField = null;
        filter = "";
        sinceSeconds = 0;
        arrivals = 0;
        caseData = null;
        pendingCase = null;
        requestedCase = null;
        draft = null;
        filters = Map.of();
        scroll = 0;
        detailFocused = false;
        detailLines = 0;
        detailHeight = 0;
        detailArea = null;
        tableArea = null;
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
        if (cursor == null && sinceSeconds > 0) {
            Map<String, String> rolling = new LinkedHashMap<>(filters);
            rolling.put("since", now.get().minusSeconds(sinceSeconds).toString());
            filters = Map.copyOf(rolling);
            filter = formatFilters(filters);
        }
        JsonObject request = new JsonObject(filters);
        request.put("action", "semantic-audit");
        request.put("limit", 50);
        filters.keySet().stream().filter(key -> key.startsWith("page.")).forEach(request::remove);
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
        if (pendingCase != null && pendingCase.isDone()) {
            JsonObject response = pendingCase.getNow(null);
            pendingCase = null;
            if (Objects.equals(requestedCase, selectedId)) {
                if (response != null && !response.containsKey("error")) {
                    caseData = response;
                } else {
                    notice = "Case retrieval failed; completeness is unknown.";
                }
            }
        }
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
                if (page != null && cursor == null && selectedId != null) {
                    Set<String> oldIds = SemanticTab.objects(page, "records").stream().map(row -> row.getString("eventId"))
                            .collect(Collectors.toSet());
                    arrivals += (int) SemanticTab.objects(response, "records").stream()
                            .filter(row -> !oldIds.contains(row.getString("eventId"))).count();
                }
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
                    notice = "Selected audit details are unavailable.";
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
        return pending != null || pendingCase != null || selectedId != null && pendingDetails != null;
    }

    String error() {
        refresh();
        return error != null ? error : notice;
    }

    List<JsonObject> rows() {
        return SemanticTab.objects(page, "records").stream()
                .filter(row -> !"decision".equals(row.get("category")))
                .filter(row -> !filters.containsKey("page.status") || filters.get("page.status").equals(row.get("status")))
                .filter(row -> !filters.containsKey("page.definition")
                        || text(row, "definition").contains(filters.get("page.definition")))
                .filter(row -> !filters.containsKey("page.result")
                        || SemanticResultFormatter.audit(row).compact().contains(filters.get("page.result")))
                .toList();
    }

    private JsonObject selected() {
        if (details != null && details.get("record") instanceof JsonObject record) {
            return record;
        }
        return rows().stream().filter(r -> Objects.equals(selectedId, r.getString("eventId"))).findFirst().orElse(null);
    }

    private void refreshSelected(String id) {
        // Occurrences are immutable. Poll only summaries; retry detail explicitly or when selection changes.
        if (!Objects.equals(selectedId, id)) {
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
        if (!Objects.equals(selectedId, id) && returnId == null) {
            caseData = null;
            pendingCase = null;
            requestedCase = null;
        }
        notice = null;
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
        caseIndex = 0;
        showInput = false;
        imageView.reset();
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
            scroll = Math.max(0, Math.min(maxScroll(), scroll + delta));
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

    private int maxScroll() {
        return Math.max(0, detailLines - detailHeight);
    }

    boolean mouse(MouseEvent event) {
        if (inputActive()) {
            return true;
        }
        boolean up = event.kind() == MouseEventKind.SCROLL_UP;
        boolean down = event.kind() == MouseEventKind.SCROLL_DOWN;
        if (!event.isClick() && !up && !down) {
            return false;
        }
        if (detailArea != null && detailArea.contains(event.x(), event.y())) {
            detailFocused = true;
            if (up || down) {
                navigate(up ? -3 : 3);
            }
            return true;
        }
        if (tableArea != null && tableArea.contains(event.x(), event.y())) {
            detailFocused = false;
            if (up || down) {
                navigate(up ? -3 : 3);
            } else {
                int index = event.y() - tableArea.y() - 1 + selection.offset();
                if (event.y() > tableArea.y() && index >= 0 && index < rows().size()) {
                    navigate(index - (selection.selected() == null ? 0 : selection.selected()));
                }
            }
            return true;
        }
        return false;
    }

    boolean setFilter(String text) {
        return setFilter(text, 0);
    }

    private boolean setFilter(String text, long presetSeconds) {
        if (!Objects.equals(pid, currentPid())) {
            reset();
            pid = currentPid();
        }
        Map<String, String> parsed;
        try {
            parsed = SemanticAuditFilters.parse(text, FILTERS);
            SemanticAuditFilters.validateQuery(parsed);
            if ("decision".equals(parsed.get("category"))) {
                throw new IllegalArgumentException("Use evaluation or request category");
            }
        } catch (IllegalArgumentException | DateTimeParseException failure) {
            notice = "Invalid filters: use field=value, since < until and a compatible resultType; quote spaces. F1 for filter help.";
            return false;
        }
        caseData = null;
        arrivals = 0;
        queuedDetail = null;
        filters = Map.copyOf(parsed);
        filter = text;
        sinceSeconds = presetSeconds;
        notice = null;
        selection.clearSelection();
        selectedId = null;
        returnId = null;
        details = null;
        page = null;
        query(null);
        return true;
    }

    boolean setInput(String field, String value) {
        if ("audit.case".equals(field)) {
            if (value.isBlank() || value.length() > 256) {
                return false;
            }
            select(value);
            loadCase();
            return true;
        }
        if ("audit.input".equals(field)) {
            showInput = Boolean.parseBoolean(value);
            return true;
        }
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
                    String value = choice.insertText();
                    long preset = sinceSeconds;
                    if ("since".equals(filterField)) {
                        values.remove("until");
                        preset = value.startsWith("last:") ? Long.parseLong(value.substring(5)) : 0;
                        if (preset > 0) {
                            value = now.get().minusSeconds(preset).toString();
                        }
                    }
                    if (value.isEmpty()) {
                        values.remove(filterField);
                    } else {
                        values.put(filterField, value);
                    }
                    setFilter(formatFilters(values), preset);
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
        for (var shortcut : Map.of('c', "category", 'e', "expert", 't', "since").entrySet()) {
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
            notice = null;
            if (selectedId != null && pendingDetails == null) {
                startDetails(selectedId);
            }
            query(queuedQuery == null ? cursor : queuedQuery.getString("cursor"));
            return true;
        }
        if (key.isChar('n')) {
            setInput("audit.page", "older");
            return true;
        }
        if (key.isChar('g')) {
            arrivals = 0;
            selectedId = null;
            details = null;
            caseData = null;
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
        if (key.isChar('u')) {
            showInput = !showInput;
            return true;
        }
        if (key.isChar('p')) {
            JsonObject record = selected();
            if (record != null && record.containsKey("input") && pendingDetails == null) {
                draft = new JsonObject(record);
            } else {
                notice = pendingDetails != null
                        ? "Captured input is still loading." : "No permitted captured input is available for a draft.";
            }
            return true;
        }
        if (key.isChar('[') || key.isChar(']')) {
            int count = SemanticTab.objects(caseData, "records").size();
            if (count > 0) {
                caseIndex = Math.floorMod(caseIndex + (key.isChar('[') ? -1 : 1), count);
                scroll = 0;
            }
            return true;
        }
        if (key.isConfirm()) {
            List<JsonObject> associated = SemanticTab.objects(caseData, "records");
            if (associated.isEmpty()) {
                loadCase();
            } else {
                JsonObject linked = associated.get(Math.min(caseIndex, associated.size() - 1));
                if (!Objects.equals(selectedId, linked.getString("eventId"))) {
                    returnId = selectedId;
                    select(linked.getString("eventId"));
                }
            }
            return true;
        }
        if (key.isKey(KeyCode.TAB)) {
            detailFocused = !detailFocused;
            return true;
        }
        if (detailFocused && (key.isHome() || key.isEnd())) {
            scroll = key.isHome() ? 0 : maxScroll();
            return true;
        }
        if (key.isUp() || key.isDown() || key.isPageUp() || key.isPageDown()) {
            int step = key.isPageUp() || key.isPageDown() ? detailFocused ? Math.max(1, detailHeight - 1) : 10 : 1;
            navigate((key.isUp() || key.isPageUp() ? -1 : 1) * step);
            return true;
        }
        return false;
    }

    private static String formatFilters(Map<String, String> values) {
        return values.entrySet().stream().map(e -> e.getKey() + "=" + SemanticAuditFilters.quote(e.getValue()))
                .collect(Collectors.joining(" "));
    }

    private void chooseFilter(String field) {
        Set<String> values = new TreeSet<>();
        if ("category".equals(field)) {
            values.addAll(List.of("evaluation", "request"));
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
        if ("since".equals(field)) {
            for (var preset : List.of(Map.entry("Last 10 minutes", 600L), Map.entry("Last hour", 3600L),
                    Map.entry("Last 24 hours", 86400L))) {
                items.add(new AutocompletePopup.CompletionItem(
                        preset.getKey(), "Rolling lower bound while viewing the latest page", null, null,
                        false, null, null, false, "last:" + preset.getValue()));
            }
        }
        for (String value : values) {
            if (!value.isBlank() && value.length() <= 256) {
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
        JsonObject selected = selected();
        if (selected != null) {
            selected = new JsonObject(selected);
            selected.remove("input");
            selected.remove("inputRedacted");
        }
        state.put("selectedRecord", selected);
        state.put("inputPolicy", "Captured input is omitted from this table snapshot; use explicit audit detail/export.");
        state.put("inputVisible", showInput);
        state.put("newArrivals", arrivals);
        state.put("case", caseData);
        state.put("filters", filters);
        state.put("filter", filter);
        state.put("paused", paused);
        state.put("live", !paused && cursor == null && error == null);
        state.put("caseIndex", caseIndex);
        state.put("filterField", filterField);
        state.put("pending", pending != null || pendingDetails != null || pendingCase != null);
        state.put("connected", currentPid() != null && Objects.equals(pid, currentPid()));
        state.put("error", error());
        state.put("focusedPane", detailFocused ? "detail" : "list");
        state.put("detailScroll", scroll);
        state.put("detailMaxScroll", maxScroll());
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
        for (String field : List.of("category", "expert")) {
            String shortcut = field.substring(0, 1);
            String label = Character.toUpperCase(field.charAt(0)) + field.substring(1);
            query.add(Span.raw(" ["));
            query.add(Span.styled(shortcut, Theme.label().underlined()));
            query.add(Span.raw(" " + label + ": " + filters.getOrDefault(field, "all") + " ▾] "));
        }
        query.add(Span.styled(sinceSeconds > 0 ? " Last " + sinceSeconds / 60 + " min"
                : filters.containsKey("since") ? " Since " + filters.get("since") : " All retained time",
                Theme.muted()));
        query.add(Span.styled("   Backend: " + text(status, "reader"), Theme.info()));
        query.add(Span.styled("   / edit", Theme.muted()));
        query.add(Span.styled(" · t: 10m/1h/24h/all", Theme.muted()));
        lines.add(Line.from(query));
        if (arrivals > 0) {
            lines.add(Line.from(Span.styled(arrivals + " new arrivals · selection pinned · g latest", Theme.info())));
        }
        if (filters.keySet().stream().anyMatch(key -> key.startsWith("page."))) {
            lines.add(Line.from(Span.styled("Refinements apply only to this loaded page", Theme.warning())));
        }
        List<String> other = new ArrayList<>();
        for (String field : List.of("routeId", "executingRouteId", "exchangeId", "breadcrumbId", "until", "definition",
                "operation",
                "resultType", "resultValue", "resultLabel", "scoreMin", "scoreMax")) {
            if (filters.containsKey(field)) {
                other.add(field + "=" + filters.get(field));
            }
        }
        if (page != null && Boolean.TRUE.equals(page.get("cursorExpired"))) {
            lines.add(Line.from(Span.styled("Older cursor expired; press g for retained history.", Theme.warning())));
        } else if (!other.isEmpty()) {
            lines.add(Line.from(Span.styled(String.join("   ", other), Theme.muted())));
        }
        if (error != null) {
            lines.add(0, Line.from(Span.styled(error + " · r to retry", Theme.error())));
        } else if (notice != null) {
            lines.add(0, Line.from(Span.styled(notice, Theme.warning())));
        }
        SemanticDetails.paragraph(frame, area, lines, 0);
    }

    private void loadCase() {
        if (selectedId != null && pendingCase == null) {
            requestedCase = selectedId;
            pendingCase
                    = execute(new JsonObject(Map.of("action", "semantic-audit", "caseEventId", selectedId, "maxRecords", 200)));
        }
    }

    void draftError(String message) {
        notice = message;
    }

    JsonObject takeDraft() {
        JsonObject selected = draft;
        draft = null;
        return selected;
    }

    private void renderTable(Frame frame, Rect area, boolean wide) {
        List<Row> content = new ArrayList<>();
        for (JsonObject row : rows()) {
            Cell result = Cell.from(Span.styled(SemanticResultFormatter.audit(row).compact(), Theme.info()));
            Cell execution = Cell.from(Span.styled(text(row, "status"), executionStyle(row)));
            String expert = text(row, "expert") + (wide && row.get("provider") != null ? " / " + text(row, "provider") : "");
            content.add(wide
                    ? Row.from(Cell.from(time(row, "MM-dd HH:mm:ss")), Cell.from(executingRoute(row)),
                            Cell.from(text(row, "definition")), Cell.from(expert),
                            Cell.from(SemanticMediaInput.typeLabel(row.get("inputType"))), result, execution,
                            Cell.from(duration(row)), Cell.from(text(row, "breadcrumbId")))
                    : Row.from(Cell.from(time(row, "MM-dd HH:mm:ss")), Cell.from(executingRoute(row)),
                            Cell.from(expert), result, execution));
        }
        Block border = block("Expert evaluations [" + rows().size() + "] · " + mode(), !detailFocused);
        frame.renderWidget(border, area);
        Rect inner = border.inner(area);
        tableArea = inner;
        if (content.isEmpty()) {
            String message = pending != null ? "Loading audit history…" : error != null ? "Reader unavailable · " + error
                    : page != null && page.getJsonObject("audit") != null
                            && Boolean.FALSE.equals(page.getJsonObject("audit").get("capturing"))
                            ? "Evaluation capture is disabled; no retained records match."
                    : "No retained records match this query.";
            frame.renderWidget(Paragraph.builder().text(message).build(), inner);
            return;
        }
        Table table = Table.builder().rows(content)
                .header((wide
                        ? Row.from("TIMESTAMP", "EXECUTING ROUTE", "EVALUATION", "EXPERT / PROVIDER", "INPUT", "RESULT",
                                "EXECUTION",
                                "DURATION", "BREADCRUMB ID")
                        : Row.from("TIMESTAMP", "EXECUTING ROUTE", "EXPERT", "RESULT", "EXECUTION")).style(Theme.label()))
                .widths(wide
                        ? List.of(Constraint.length(14), Constraint.length(15), Constraint.length(12), Constraint.length(17),
                                Constraint.length(14),
                                Constraint.fill(), Constraint.length(11), Constraint.length(9), Constraint.length(24))
                        : List.of(Constraint.length(14), Constraint.length(16), Constraint.length(12), Constraint.fill(),
                                Constraint.length(11)))
                .highlightSymbol("› ").highlightStyle(Theme.selectionBg()).build();
        frame.renderStatefulWidget(table, inner, selection);
    }

    private void renderDetails(Frame frame, Rect area) {
        detailArea = area;
        JsonObject record = selected();
        Block border = block("Expert evaluation · " + (selectedId == null ? "none selected" : selectedId), detailFocused);
        frame.renderWidget(border, area);
        Rect inner = SemanticDetails.inset(border.inner(area));
        detailHeight = inner.height();
        detailLines = 0;
        if (record == null) {
            SemanticDetails.paragraph(frame, inner, List.of(Line.from(selectedId == null ? "Select a record."
                    : pendingDetails != null ? "Loading record…" : "Record unavailable in this backend.")), 0);
            return;
        }
        if ("decision".equals(record.get("category"))) {
            SemanticDetails.paragraph(frame, inner,
                    List.of(Line.from("Legacy record: use explicit event-ID export to inspect stored data.")), 0);
            return;
        }
        List<Line> left = new ArrayList<>();
        var result = SemanticResultFormatter.audit(record);
        left.add(Line.from(Span.styled("Result: " + result.compact(), Theme.info().bold())));
        result.details().forEach(line -> left.add(Line.from(line)));
        SemanticDetails.add(left, "Input type", SemanticMediaInput.typeLabel(record.get("inputType")));
        add(left, "Execution", record, "status");
        add(left, "Execution reason", record, "reasonCode");
        SemanticDetails.add(left, "Duration", duration(record));
        for (String field : List.of("definition", "expert", "provider", "operation", "origin", "model", "revision", "taskId",
                "taskVersion")) {
            add(left, Character.toUpperCase(field.charAt(0)) + field.substring(1), record, field);
        }
        List<Line> right = new ArrayList<>();
        add(right, "Entry route", record, "routeId");
        SemanticDetails.add(right, "Executing route",
                executingRoute(record));
        for (String field : List.of("timestamp", "startedAt", "exchangeId", "breadcrumbId", "invocationId", "batchId",
                "requestId")) {
            add(right, field, record, field);
        }
        right.add(Line.from("Display zone: " + ZoneId.systemDefault()));
        JsonObject health = page == null ? null : page.getJsonObject("audit");
        if (health != null && health.get("readerExtensions") instanceof Collection<?> extensions
                && !extensions.contains("interpretation")) {
            right.add(Line.from("Selected reader does not declare interpretation support"));
        }
        if (health != null && health.get("extensions") instanceof Map<?, ?> extensions) {
            extensions.forEach((sink, supported) -> {
                if (supported instanceof Collection<?> fields && !fields.contains("interpretation")) {
                    right.add(Line.from("Sink " + sink + ": interpretation unsupported"));
                }
            });
        }
        if (record.containsKey("findings")) {
            right.add(Line.from(Span.styled("Expert findings", Theme.label())));
            for (JsonObject finding : SemanticTab.objects(record, "findings")) {
                right.add(Line.from(
                        text(finding, "code") + " · " + text(finding, "meaning") + " · source: " + text(finding, "source")));
                right.add(
                        Line.from("Severity: " + (finding.get("severity") == null ? "not reported" : finding.get("severity"))));
            }
        } else if (record.containsKey("findingsOmitted")) {
            add(right, "Findings omitted", record, "findingsOmitted");
        }
        renderInput(right, record, inner.width() < 110 ? inner.width() : (inner.width() - 2) / 2);
        if (pendingCase != null) {
            right.add(Line.from("Loading associated case…"));
        } else if (caseData != null) {
            right.add(Line.from(Span.styled("Associated case · completeness unknown", Theme.label())));
            right.add(Line.from("Scope: " + text(caseData, "retrievalScope")));
            add(right, "Association", caseData, "association");
            add(right, "Limited", caseData, "retrievalLimited");
            add(right, "Unsupported relationships", caseData, "unsupportedRelationships");
            List<JsonObject> members = SemanticTab.objects(caseData, "records");
            for (int i = 0; i < members.size(); i++) {
                JsonObject member = members.get(i);
                right.add(Line.from((i == caseIndex ? "› " : "  ") + text(member, "eventId") + " · "
                                    + text(member, "category") + " · " + text(member, "status")));
            }
            right.add(Line.from("[ ] choose associated record · Enter inspect · Esc return"));
        } else {
            right.add(Line.from("Enter: inspect the source-local case · completeness unknown"));
        }
        if (inner.width() < 110) {
            left.add(Line.empty());
            left.addAll(right);
            List<Line> wrappedLeft = TuiHelper.hangingWrap(left, inner.width());
            detailLines = wrappedLeft.size();
            scroll = Math.min(scroll, maxScroll());
            SemanticDetails.paragraph(frame, inner, wrappedLeft, scroll);
        } else {
            List<Rect> columns = Layout.horizontal()
                    .constraints(Constraint.percentage(50), Constraint.length(2), Constraint.fill()).split(inner);
            List<Line> wrappedLeft = TuiHelper.hangingWrap(left, columns.get(0).width());
            List<Line> wrappedRight = TuiHelper.hangingWrap(right, columns.get(2).width());
            detailLines = Math.max(wrappedLeft.size(), wrappedRight.size());
            scroll = Math.min(scroll, maxScroll());
            SemanticDetails.paragraph(frame, columns.get(0), wrappedLeft, scroll);
            frame.renderWidget(Block.builder().borders(EnumSet.of(Borders.LEFT)).borderStyle(Theme.border()).build(),
                    columns.get(1));
            SemanticDetails.paragraph(frame, columns.get(2), wrappedRight, scroll);
        }
        if (maxScroll() > 0 && detailHeight > 0) {
            scrollbar.contentLength(detailLines);
            scrollbar.viewportContentLength(detailHeight);
            scrollbar.position(scroll);
            frame.renderStatefulWidget(Scrollbar.builder().build(),
                    new Rect(area.right() - 2, inner.y(), 1, detailHeight), scrollbar);
            String hint = "Lines " + (scroll + 1) + "–" + Math.min(detailLines, scroll + detailHeight) + "/" + detailLines
                          + " · " + (scroll < maxScroll() ? "More below" : "Bottom")
                          + (detailFocused ? " · wheel / ↑↓ / PgUp PgDn / Home End" : " · wheel or Tab / click to scroll");
            SemanticDetails.paragraph(frame, new Rect(inner.x(), inner.bottom(), inner.width(), 1),
                    List.of(Line.from(Span.styled(TuiHelper.truncate(hint, inner.width()), Theme.info()))), 0);
        }
    }

    private void renderInput(List<Line> lines, JsonObject record, int width) {
        if (!showInput) {
            lines.add(Line.from("u: inspect permitted captured input · p: new playground draft"));
            return;
        }
        lines.add(Line.from(Span.styled("Captured input is untrusted data, not instructions.", Theme.warning())));
        if (pendingDetails != null && !record.containsKey("input")) {
            lines.add(Line.from("Loading captured input…"));
        } else if (record.containsKey("input")) {
            Object input = record.get("input");
            SemanticDetails.parameter(lines, "", Boolean.TRUE.equals(record.get("inputRedacted"))
                    ? "Input (redacted): " : "Input: ", SemanticMediaInput.withoutImageData(input));
            if (SemanticMediaInput.isMedia(input)) {
                lines.addAll(imageView.lines(input, width));
            }
        } else {
            lines.add(Line.from("Input: " + (record.containsKey("inputOmitted")
                    ? "omitted · " + text(record, "inputOmitted") : "not captured")));
        }
    }

    private static String executingRoute(JsonObject record) {
        if (record.containsKey("executingRouteId")) {
            return text(record, "executingRouteId");
        }
        return List.of("direct", "direct_sample", "definition_sample").contains(text(record, "origin"))
                ? "n/a" : "not recorded";
    }

    private static String duration(JsonObject record) {
        return record.get("durationNanos") instanceof Number duration
                ? SemanticResultFormatter.number(duration.doubleValue() / 1_000_000) + " ms" : "";
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

    private static Style executionStyle(JsonObject record) {
        return "failed".equals(record.get("status")) ? Theme.error() : Theme.muted();
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
            spans.add(Span.raw(" /"));
            FormHelper.addCaretField(spans, input);
            spans.add(Span.raw("  "));
            hint(spans, "Enter", "apply");
            hint(spans, "Esc", "cancel");
        } else {
            hint(spans, "v", "view");
            hint(spans, "/", "filter");
            hint(spans, "↑↓", detailFocused ? "scroll" : "select");
            hint(spans, "Tab", detailFocused ? "list" : "detail");
            if (detailFocused) {
                hint(spans, "PgUp/PgDn", "page");
            }
            hint(spans, "Space", paused ? "resume" : "pause");
            hint(spans, "Enter", returnId == null ? "case / record" : "previous record");
            hint(spans, "n", "older");
            hint(spans, "u", "input");
            hint(spans, "p", "draft");
            hint(spans, "g", "latest");
            hint(spans, "r", "refresh");
        }
    }
}
