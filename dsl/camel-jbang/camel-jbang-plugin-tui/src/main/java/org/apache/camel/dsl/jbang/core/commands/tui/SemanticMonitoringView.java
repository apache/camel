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

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
import dev.tamboui.tui.event.MouseEvent;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.table.Cell;
import dev.tamboui.widgets.table.Row;
import dev.tamboui.widgets.table.Table;
import dev.tamboui.widgets.table.TableState;
import org.apache.camel.util.json.JsonObject;

import static org.apache.camel.dsl.jbang.core.commands.tui.SemanticDetails.text;
import static org.apache.camel.dsl.jbang.core.commands.tui.SemanticMonitoringDetails.*;
import static org.apache.camel.dsl.jbang.core.commands.tui.TuiHelper.*;

/** A read-only view of the same producer-owned snapshots exposed through JMX and the CLI. */
final class SemanticMonitoringView {
    private final MonitorContext ctx;
    private final TableState table = new TableState();
    private CompletableFuture<JsonObject> pending;
    private JsonObject data;
    private boolean reloadRequested;
    private String pid;
    private String error;
    private String aggregation;
    private String source = "";
    private final Map<String, String> filters = new LinkedHashMap<>();
    private final Map<String, Rect> filterAreas = new LinkedHashMap<>();
    private Rect tableArea;
    private Rect detailArea;
    private boolean expanded;
    private int scroll;
    private boolean detail;
    private boolean paused;
    private long refreshed;

    SemanticMonitoringView(MonitorContext ctx) {
        this.ctx = ctx;
    }

    void reset() {
        if (pending != null) {
            pending.cancel(true);
        }
        pending = null;
        source = "";
        filters.clear();
        aggregation = null;
        reloadRequested = false;
        data = null;
        pid = null;
        error = null;
        scroll = 0;
        refreshed = 0;
        paused = false;
        detail = false;
        expanded = false;
        table.clearSelection();
    }

    private String currentPid() {
        IntegrationInfo info = ctx.findSelectedIntegration();
        return info == null ? null : info.phantom ? info.linkedPid : info.pid;
    }

    void load() {
        String selected = currentPid();
        if (!Objects.equals(pid, selected)) {
            reset();
            pid = selected;
        }
        if (pid == null) {
            error = "Select a running integration to inspect monitoring.";
            return;
        }
        if (pending != null) {
            reloadRequested = true;
            return;
        }
        JsonObject request = new JsonObject(Map.of("action", "semantic-monitoring"));
        if (!source.isEmpty()) {
            request.put("source", source);
        }
        String requestedPid = pid;
        pending = CompletableFuture.supplyAsync(() -> ctx.executeIndependentAction(requestedPid, request, 5000),
                ctx.backgroundExecutor).exceptionally(failure -> null);
        refreshed = System.nanoTime();
    }

    boolean ensureLoaded() {
        if (!Objects.equals(pid, currentPid())) {
            reset();
        }
        if (!paused && pending != null && pending.isDone()) {
            JsonObject response = pending.getNow(null);
            pending = null;
            if (reloadRequested) {
                reloadRequested = false;
                load();
                return true;
            }
            if (response == null || response.get("error") != null) {
                error = response == null ? "Monitoring unavailable; r to retry" : text(response, "error");
            } else {
                String selected = selectedKey();
                data = response;
                if (aggregation == null) {
                    List<String> scopes = SemanticTab.objects(data, "snapshots").stream()
                            .map(SemanticMonitoringDetails::aggregation).distinct().toList();
                    aggregation = scopes.contains("expert") && scopes.stream()
                            .allMatch(scope -> List.of("expert", "definition").contains(scope)) ? "expert" : "all";
                }
                error = null;
                List<JsonObject> rows = rows();
                int index = 0;
                for (int i = 0; i < rows.size(); i++) {
                    if (Objects.equals(key(rows.get(i)), selected)) {
                        index = i;
                        break;
                    }
                }
                if (rows.isEmpty()) {
                    table.clearSelection();
                } else {
                    table.select(index);
                }
            }
        }
        if (pending == null && !paused && (refreshed == 0 || System.nanoTime() - refreshed >= 2_000_000_000L)) {
            load();
        }
        return !paused && pending != null;
    }

    String error() {
        return error;
    }

    private List<JsonObject> rows() {
        return SemanticTab.objects(data, "snapshots").stream()
                .filter(row -> aggregation == null || "all".equals(aggregation)
                        || aggregation.equals(SemanticMonitoringDetails.aggregation(row)))
                .filter(row -> filters.entrySet().stream().allMatch(
                        filter -> filter.getValue().isEmpty() || filter.getValue().equals(dimension(row, filter.getKey()))))
                .toList();
    }

    private String key(JsonObject row) {
        return text(row, "source") + "/" + text(row, "id");
    }

    private JsonObject selected() {
        Integer index = table.selected();
        List<JsonObject> rows = rows();
        return index == null || index < 0 || index >= rows.size() ? null : rows.get(index);
    }

    private String selectedKey() {
        JsonObject row = selected();
        return row == null ? null : key(row);
    }

    void navigate(int delta) {
        if (detail) {
            scroll = Math.max(0, scroll + delta);
        } else if (!rows().isEmpty()) {
            int selected = table.selected() == null ? 0 : table.selected();
            table.select(Math.max(0, Math.min(rows().size() - 1, selected + delta)));
            scroll = 0;
        }
    }

    boolean key(KeyEvent key) {
        if (key.isChar('r')) {
            load();
        } else if (key.isChar(' ')) {
            paused = !paused;
        } else if (key.isChar('g')) {
            List<String> groups = List.of("expert", "definition", "all");
            setInput("monitoring.aggregation", groups.get((groups.indexOf(aggregation) + 1) % groups.size()));
        } else if (key.isChar('i') || key.isConfirm()) {
            expanded = !expanded;
            detail = expanded;
            scroll = 0;
        } else if (key.code() == KeyCode.TAB) {
            detail = !detail;
        } else if (key.isPageUp() || key.isPageDown()) {
            navigate(key.isPageUp() ? -8 : 8);
        } else if (key.isHome()) {
            scroll = 0;
            if (!detail && !rows().isEmpty()) {
                table.select(0);
            }
        } else if (key.isEnd()) {
            if (detail) {
                scroll = Integer.MAX_VALUE;
            } else if (!rows().isEmpty()) {
                table.select(rows().size() - 1);
            }
        } else if (key.isChar('a') || key.isChar('x') || key.isChar('s')) {
            cycleFilter(key.isChar('a') ? "definition" : key.isChar('x') ? "expert" : "availability");
        } else {
            return false;
        }
        return true;
    }

    boolean escape() {
        if (detail || expanded) {
            detail = false;
            expanded = false;
            scroll = 0;
            return true;
        }
        return false;
    }

    private void cycleFilter(String field) {
        List<String> values = new ArrayList<>();
        values.add("");
        SemanticTab.objects(data, "snapshots").stream().map(row -> dimension(row, field))
                .filter(value -> !"not provided".equals(value)).distinct().sorted().forEach(values::add);
        String current = filters.getOrDefault(field, "");
        setInput("monitoring." + field, values.get((values.indexOf(current) + 1) % values.size()));
    }

    boolean mouse(MouseEvent event) {
        if (!event.isClick()) {
            return false;
        }
        for (var entry : filterAreas.entrySet()) {
            if (entry.getValue().contains(event.x(), event.y())) {
                if ("aggregation".equals(entry.getKey())) {
                    return key(KeyEvent.ofChar('g'));
                }
                cycleFilter(entry.getKey());
                return true;
            }
        }
        if (tableArea != null && tableArea.contains(event.x(), event.y())) {
            int index = event.y() - tableArea.y() - 2 + table.offset();
            if (index >= 0 && index < rows().size()) {
                table.select(index);
                detail = false;
                scroll = 0;
            }
            return true;
        }
        if (detailArea != null && detailArea.contains(event.x(), event.y())) {
            detail = true;
            return true;
        }
        return false;
    }

    boolean setInput(String field, String value) {
        if ("monitoring.aggregation".equals(field) && List.of("expert", "definition", "all").contains(value)) {
            aggregation = value;
        } else if ("monitoring.source".equals(field) && value.length() <= 256) {
            source = value;
            load();
        } else if (List.of("monitoring.definition", "monitoring.expert", "monitoring.availability").contains(field)
                && value.length() <= 256) {
            filters.put(field.substring("monitoring.".length()), value);
        } else {
            return false;
        }
        if (rows().isEmpty()) {
            table.clearSelection();
        } else {
            table.select(0);
        }
        scroll = 0;
        return true;
    }

    JsonObject snapshot() {
        ensureLoaded();
        JsonObject result = data == null ? new JsonObject() : new JsonObject(data);
        result.put("tab", "Semantic");
        result.put("view", "Monitoring");
        result.put("rows", rows());
        result.put("selected", selected());
        result.put("selectedIndex", table.selected());
        result.put("aggregation", aggregation == null ? "all" : aggregation);
        result.put("paused", paused);
        result.put("filters", Map.copyOf(filters));
        result.put("expanded", expanded);
        result.put("error", error);
        result.put("scopeNote",
                "Current runtime measurements; view filters do not change collection. History and comparisons belong to the monitoring backend.");
        return result;
    }

    void render(Frame frame, Rect area) {
        ensureLoaded();
        filterAreas.clear();
        tableArea = null;
        detailArea = null;
        List<JsonObject> supplied = rows();
        int tableHeight = expanded ? 0 : Math.min(Math.max(5, supplied.size() + 3), Math.max(5, area.height() / 3));
        List<Rect> panels = Layout.vertical().constraints(Constraint.length(3), Constraint.length(tableHeight),
                Constraint.length(expanded ? 0 : 1), Constraint.fill(), Constraint.length(1)).split(area);
        JsonObject selected = selected();
        String status = selected == null ? "no selection" : display(selected.get("availability"));
        String header = error != null
                ? error + (data == null ? "" : " · showing last successful response")
                : "Monitoring · Source: " + (selected == null ? "all" : display(selected.get("source")))
                  + " · " + status + " · " + (paused ? "DISPLAY PAUSED" : "LIVE DISPLAY")
                  + " · Refreshed: " + time(data == null ? null : data.get("lastSample"));
        SemanticDetails.paragraph(frame, new Rect(area.x(), area.y(), area.width(), 1),
                List.of(Line.from(Span.styled(truncate(header, area.width()), error == null ? Theme.info() : Theme.error()))),
                0);
        renderFilters(frame, new Rect(area.x(), area.y() + 1, area.width(), 1));
        String scope = supplied.size() + " current measurements · "
                       + (paused ? "Display paused; collection unchanged" : "View filters do not change collection")
                       + " · — = not provided · " + ZoneId.systemDefault();
        SemanticDetails.paragraph(frame, new Rect(area.x(), area.y() + 2, area.width(), 1),
                List.of(Line.from(Span.styled(truncate(scope, area.width()), Theme.muted()))), 0);
        if (!expanded) {
            tableArea = panels.get(1);
            renderTable(frame, tableArea, supplied);
        }
        detailArea = panels.get(3);
        Block block = block(" Observed measurements ", detail);
        frame.renderWidget(block, detailArea);
        Rect inner = block.inner(detailArea);
        List<Line> lines = new ArrayList<>(SemanticMonitoringDetails.lines(selected, inner.width(), expanded));
        if (selected == null && data != null && !"all".equals(aggregation)) {
            lines.add(Line
                    .from("No matching " + aggregation + " aggregates supplied. Use g for All sources or adjust the filters."));
        }
        if (expanded && data != null) {
            lines.add(Line.from(Span.styled("Collection health", Theme.title())));
            lines.add(Line.from("Refresh: " + text(data, "sampleIntervalSeconds") + " s · cache: latest values for up to "
                                + text(data, "capacity") + " series"));
            for (var entry : map(data.get("sources")).entrySet()) {
                Map<?, ?> fields = map(entry.getValue());
                lines.add(Line.from(display(entry.getKey()) + " · " + display(fields.get("state"))
                                    + " · last success: " + time(fields.get("lastSuccess"))
                                    + " · last failure: " + time(fields.get("lastFailure"))));
            }
        }
        lines = hangingWrap(lines, inner.width());
        scroll = Math.min(scroll, Math.max(0, lines.size() - inner.height()));
        SemanticDetails.paragraph(frame, inner, lines, scroll);
        String health = "Source errors: " + text(data, "sourceErrors") + " · Evicted series: " + text(data, "evicted")
                        + " · Omitted sources: " + text(data, "omittedSources") + " · Current values only";
        boolean unhealthy = data != null && List.of("sourceErrors", "omittedSources", "evicted")
                .stream().anyMatch(field -> data.get(field) instanceof Number count && count.longValue() > 0);
        SemanticDetails.paragraph(frame, panels.get(4),
                List.of(Line.from(Span.styled(truncate(health, area.width()), unhealthy ? Theme.warning() : Theme.muted()))),
                0);
    }

    private void renderFilters(Frame frame, Rect area) {
        Map<String, String> available = new LinkedHashMap<>();
        available.put("aggregation", "Group (g)");
        Map<String, String> labels
                = Map.of("definition", "Definition (a)", "expert", "Expert (x)", "availability", "Availability (s)");
        for (String field : List.of("definition", "expert", "availability")) {
            if (!filters.getOrDefault(field, "").isEmpty() || SemanticTab.objects(data, "snapshots").stream()
                    .anyMatch(row -> !"not provided".equals(dimension(row, field)))) {
                available.put(field, labels.get(field));
            }
        }
        List<String> fields = new ArrayList<>(available.keySet());
        int x = area.x();
        int groupWidth = Math.min(26, area.width());
        int otherWidth = Math.max(1, (area.width() - groupWidth) / Math.max(1, fields.size() - 1));
        for (int i = 0; i < fields.size(); i++) {
            String value = "aggregation".equals(fields.get(i))
                    ? "expert".equals(aggregation) ? "Expert" : "definition".equals(aggregation) ? "Definition" : "All sources"
                    : filters.getOrDefault(fields.get(i), "");
            int width = i == 0 ? groupWidth : otherWidth;
            Rect button = new Rect(x, area.y(), Math.min(width, area.right() - x), 1);
            filterAreas.put(fields.get(i), button);
            String label = "[" + available.get(fields.get(i)) + ": " + (value.isEmpty() ? "all" : value) + "]";
            SemanticDetails.paragraph(frame, button,
                    List.of(Line.from(Span.styled(truncate(label, button.width()), Theme.label()))), 0);
            x += width;
        }
    }

    private void renderTable(Frame frame, Rect area, List<JsonObject> supplied) {
        boolean wide = area.width() >= 120;
        boolean byDefinition = "definition".equals(aggregation) || "all".equals(aggregation)
                && supplied.stream().anyMatch(row -> "definition".equals(SemanticMonitoringDetails.aggregation(row)));
        boolean evaluations = supplied.stream().allMatch(row -> map(row.get("values")).containsKey("completed"));
        List<String> headers = new ArrayList<>();
        List<Constraint> widths = new ArrayList<>();
        if (wide || byDefinition) {
            headers.add(byDefinition
                    ? "all".equals(aggregation) ? "EXPERT / DEFINITION" : "DEFINITION"
                    : "EXPERT / OPERATION");
            widths.add(Constraint.percentage(wide ? 22 : 34));
        }
        if (wide || !byDefinition) {
            headers.add("MEASUREMENT");
            widths.add(Constraint.percentage(wide ? 26 : 34));
        }
        headers.add(evaluations ? "ANSWERS" : "SAMPLES");
        widths.add(Constraint.length(9));
        headers.add(evaluations ? "FAILED" : "VALUE");
        widths.add(Constraint.length(8));
        if (evaluations) {
            headers.add("MEAN TIME");
            widths.add(Constraint.length(16));
        }
        headers.add("AVAILABILITY");
        widths.add(Constraint.fill());
        List<Row> rendered = new ArrayList<>();
        for (JsonObject row : supplied) {
            Map<?, ?> values = map(row.get("values"));
            List<Cell> cells = new ArrayList<>();
            if (wide || byDefinition) {
                String identity = "definition".equals(SemanticMonitoringDetails.aggregation(row))
                        ? cell(dimension(row, "definition"))
                        : cell(dimension(row, "expert")) + " / " + cell(dimension(row, "operation"));
                cells.add(Cell.from(truncate(identity, area.width() * (wide ? 22 : 34) / 100 - 2)));
            }
            if (wide || !byDefinition) {
                cells.add(Cell.from(truncate(display(row.get("name")), area.width() * (wide ? 26 : 34) / 100 - 2)));
            }
            Object samples = evaluations ? values.get("answers") : row.get("denominator");
            Object value = evaluations ? values.get("failed") : values.get("value");
            cells.add(Cell.from(cell(samples)));
            cells.add(Cell.from(cell(value)));
            if (evaluations) {
                cells.add(Cell.from(truncate(cell(duration(values.get("meanDurationNanos"))), 15)));
            }
            cells.add(Cell.from(cell(row.get("availability"))));
            rendered.add(Row.from(cells));
        }
        Table widget = Table.builder().rows(rendered).header(Row.from(headers.toArray(String[]::new)))
                .widths(widths.toArray(Constraint[]::new))
                .block(block(" Reported measurements [" + supplied.size() + "] ", !detail))
                .highlightSymbol("› ").highlightStyle(Theme.selectionBg()).build();
        frame.renderStatefulWidget(widget, area, table);
    }

    private static String cell(Object value) {
        return value == null || "not provided".equals(value) ? "—" : display(value);
    }

    private static Block block(String title, boolean focused) {
        return Block.builder().title(title).borders(Borders.ALL).borderType(BorderType.ROUNDED)
                .borderStyle(focused ? Style.EMPTY.fg(Theme.accent()) : Theme.border()).build();
    }

    void footer(List<Span> spans) {
        hint(spans, "v", "view");
        hint(spans, "Tab", "pane");
        hint(spans, "g", "group");
        hint(spans, "i", expanded ? "restore" : "expand");
        hint(spans, "Space", paused ? "resume" : "pause");
        hintLast(spans, "r", "refresh");
    }
}
