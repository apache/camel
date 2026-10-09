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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Layout;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Overflow;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.MouseEvent;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.block.Title;
import dev.tamboui.widgets.input.TextInputState;
import dev.tamboui.widgets.paragraph.Paragraph;
import dev.tamboui.widgets.table.Cell;
import dev.tamboui.widgets.table.Row;
import dev.tamboui.widgets.table.Table;
import dev.tamboui.widgets.table.TableState;
import org.apache.camel.util.json.JsonObject;

import static org.apache.camel.dsl.jbang.core.commands.tui.SemanticDetails.text;
import static org.apache.camel.dsl.jbang.core.commands.tui.TuiHelper.*;

/** Three views of the same published semantic definitions and expert contracts. */
class SemanticTab extends AbstractTableTab {
    private enum View {
        DEFINITIONS("Definitions"),
        EXPERTS("Experts"),
        RELATIONSHIPS("Relationships");

        final String label;

        View(String label) {
            this.label = label;
        }
    }

    private enum Focus {
        LIST,
        DETAIL,
        USES,
        PLAYGROUND
    }

    private JsonObject data;
    private CompletableFuture<JsonObject> pending;
    private String pid;
    private String selectionId;
    private String error;
    private View view = View.DEFINITIONS;
    private Focus focus = Focus.LIST;
    private String definitionName;
    private String expertReference;
    private int detailScroll;
    private String filter = "";
    private TextInputState filterInput;
    private SemanticEvaluationPopup sample;
    private final TableState usesState = new TableState();
    private final SemanticRelationships relationships = new SemanticRelationships();
    private Rect detailArea;
    private Rect usesArea;
    private SemanticPlayground playground;

    private record ExpertOperation(String expert, String operation) {
    }

    private final Map<ExpertOperation, SemanticPlayground> playgrounds = new HashMap<>();
    private final Map<String, SemanticEvaluationPopup> samples = new HashMap<>();
    private final SubViewBar operationBar = new SubViewBar();
    private String operationName;
    private boolean operationExplicit;
    private boolean playgroundExpanded;

    SemanticTab(MonitorContext ctx) {
        super(ctx);
    }

    @Override
    public void onTabSelected() {
        load();
    }

    @Override
    public void onIntegrationChanged() {
        data = null;
        pending = null;
        pid = null;
        selectionId = null;
        error = null;
        sample = null;
        samples.clear();
        playground = null;
        playgrounds.clear();
        playgroundExpanded = false;
        operationName = null;
        operationExplicit = false;
        filterInput = null;
        filter = "";
        definitionName = null;
        expertReference = null;
        detailScroll = 0;
        focus = Focus.LIST;
        tableState.clearSelection();
        usesState.clearSelection();
        relationships.reset();
    }

    private void load() {
        IntegrationInfo info = ctx.findSelectedIntegration();
        String selected = info == null ? null : info.phantom ? info.linkedPid : info.pid;
        if (selected == null && data != null && Objects.equals(selectionId, ctx.selectedPid)) {
            return;
        }
        if (!Objects.equals(pid, selected)) {
            onIntegrationChanged();
            pid = selected;
        }
        selectionId = ctx.selectedPid;
        if (pid == null) {
            error = "Select a running integration to inspect semantic definitions.";
            return;
        }
        if (pending != null && !pending.isDone()) {
            return;
        }
        error = null;
        JsonObject request = new JsonObject();
        request.put("action", "semantic-metadata");
        request.put("overview", true);
        String requestedPid = pid;
        pending = CompletableFuture
                .supplyAsync(() -> ctx.executeIndependentAction(requestedPid, request, 5000), ctx.backgroundExecutor)
                .exceptionally(failure -> null);
    }

    private void refresh() {
        IntegrationInfo info = ctx.findSelectedIntegration();
        String selected = info == null ? null : info.phantom ? info.linkedPid : info.pid;
        if (selected == null && data != null && Objects.equals(selectionId, ctx.selectedPid)) {
            return;
        }
        if (!Objects.equals(pid, selected)) {
            load();
        }
        if (pending != null && pending.isDone()) {
            JsonObject response = pending.getNow(null);
            pending = null;
            if (response == null || !response.containsKey("evaluations")) {
                data = null;
                error = "Semantic inspection is unavailable. The running application needs a matching camel-semantic version.";
            } else {
                data = response;
                error = null;
            }
            restoreSelection();
        }
    }

    @Override
    public boolean ensureDataLoaded() {
        refresh();
        if (data == null && pending == null && error == null) {
            load();
        }
        return pending != null;
    }

    @Override
    public String dataLoadError() {
        refresh();
        return pending != null ? null : error != null ? error : rows().isEmpty() ? "No semantic definitions or experts." : null;
    }

    private List<JsonObject> rows() {
        List<JsonObject> all = objects(data, view == View.EXPERTS ? "experts" : "evaluations");
        String term = filter.toLowerCase(Locale.ROOT);
        var stream = all.stream().filter(row -> term.isEmpty() || row.toJson().toLowerCase(Locale.ROOT).contains(term));
        if (view == View.RELATIONSHIPS) {
            stream = stream.sorted(Comparator.comparing((JsonObject row) -> text(row, "expert"))
                    .thenComparing(row -> text(row, "operation")).thenComparing(row -> text(row, "name")));
        }
        return stream.toList();
    }

    static List<JsonObject> objects(JsonObject value, String key) {
        if (value != null && value.get(key) instanceof List<?> list) {
            return list.stream().filter(JsonObject.class::isInstance).map(JsonObject.class::cast).toList();
        }
        return List.of();
    }

    private JsonObject selectedDefinition() {
        return objects(data, "evaluations").stream().filter(row -> Objects.equals(definitionName, row.get("name")))
                .findFirst().orElse(null);
    }

    private JsonObject selectedExpert() {
        return objects(data, "experts").stream().filter(row -> Objects.equals(expertReference, row.get("reference")))
                .findFirst().orElse(null);
    }

    private List<JsonObject> uses() {
        return objects(data, "evaluations").stream().filter(row -> Objects.equals(expertReference, row.get("expert"))).toList();
    }

    private static void select(TableState state, List<JsonObject> rows, String key, String value) {
        if (rows.isEmpty()) {
            state.clearSelection();
            return;
        }
        state.select(0);
        for (int i = 0; i < rows.size(); i++) {
            if (Objects.equals(value, rows.get(i).get(key))) {
                state.select(i);
                break;
            }
        }
    }

    private void restoreSelection() {
        select(tableState, rows(), view == View.EXPERTS ? "reference" : "name",
                view == View.EXPERTS ? expertReference : definitionName);
        selectionChanged();
    }

    private void selectionChanged() {
        List<JsonObject> rows = rows();
        Integer index = tableState.selected();
        JsonObject selected = index != null && index >= 0 && index < rows.size() ? rows.get(index) : null;
        if (view == View.EXPERTS) {
            String previousExpert = expertReference;
            expertReference = selected == null ? null : selected.getString("reference");
            if (!Objects.equals(previousExpert, expertReference)) {
                operationExplicit = false;
                operationName = null;
            }
            if (operationExplicit) {
                selectUsesForOperation();
            } else {
                select(usesState, uses(), "name", definitionName);
                useChanged();
            }
        } else {
            definitionName = selected == null ? null : selected.getString("name");
            expertReference = selected == null ? null : selected.getString("expert");
            operationName = selected == null ? null : selected.getString("operation");
            operationExplicit = false;
        }
        detailScroll = 0;
        syncPlayground();
    }

    private void useChanged() {
        List<JsonObject> definitions = uses();
        Integer index = usesState.selected();
        JsonObject definition = index != null && index >= 0 && index < definitions.size() ? definitions.get(index) : null;
        definitionName = definition == null ? null : definition.getString("name");
        operationName = definition == null ? null : definition.getString("operation");
        operationExplicit = false;
        detailScroll = 0;
        syncPlayground();
    }

    private void selectOperation(String name) {
        operationName = name;
        operationExplicit = true;
        detailScroll = 0;
        selectUsesForOperation();
        syncPlayground();
    }

    private void selectUsesForOperation() {
        List<JsonObject> definitions = uses();
        int selected = -1;
        for (int i = 0; i < definitions.size(); i++) {
            JsonObject definition = definitions.get(i);
            if (Objects.equals(operationName, definition.get("operation"))) {
                if (selected < 0 || Objects.equals(definitionName, definition.get("name"))) {
                    selected = i;
                }
            }
        }
        if (selected < 0) {
            usesState.clearSelection();
            definitionName = null;
        } else {
            usesState.select(selected);
            definitionName = definitions.get(selected).getString("name");
        }
    }

    private JsonObject selectedOperation() {
        List<JsonObject> operations = objects(selectedExpert(), "operations");
        if (operationName == null) {
            return operations.isEmpty() ? null : operations.get(0);
        }
        return operations.stream().filter(op -> Objects.equals(operationName, op.get("name"))).findFirst().orElse(null);
    }

    private void syncPlayground() {
        JsonObject operation = selectedOperation();
        if (operation != null) {
            operationName = operation.getString("name");
        }
        if (pid == null || operation == null || selectedExpert().get("error") != null) {
            playground = null;
        } else if (playground == null || !playground.matches(pid, expertReference, operation)) {
            ExpertOperation key = new ExpertOperation(expertReference, operationName);
            playground = playgrounds.get(key);
            if (playground == null || !playground.matches(pid, expertReference, operation)) {
                playground = new SemanticPlayground(ctx, pid, expertReference, operation);
                playgrounds.put(key, playground);
            }
        }
    }

    private void view(View selected) {
        view = selected;
        filter = "";
        focus = Focus.LIST;
        tableState.setOffset(0);
        relationships.reset();
        restoreSelection();
    }

    @Override
    public SubViewBar.Spec subViewBar() {
        List<SubViewBar.View> views = new ArrayList<>();
        for (View candidate : View.values()) {
            views.add(new SubViewBar.View(candidate.label, view == candidate, true, () -> view(candidate)));
        }
        return new SubViewBar.Spec("v", views, List.of(), false);
    }

    boolean isInputActive() {
        return filterInput != null || sample != null || focus == Focus.PLAYGROUND;
    }

    void handlePaste(String text) {
        if (sample != null) {
            sample.handlePaste(text);
        } else if (focus == Focus.PLAYGROUND && playground != null) {
            playground.handlePaste(text);
        } else if (filterInput != null) {
            FormHelper.handlePaste(text, filterInput);
        }
    }

    @Override
    public boolean isOverlayActive() {
        return sample != null;
    }

    @Override
    public boolean handleKeyEvent(KeyEvent key) {
        refresh();
        if (sample != null) {
            sample.handleKeyEvent(key);
            return true;
        }
        if (filterInput != null) {
            if (key.isCancel()) {
                filterInput = null;
            } else if (key.isConfirm()) {
                setFilter(filterInput.text());
                filterInput = null;
            } else {
                FormHelper.handleTextInput(key, filterInput);
            }
            return true;
        }
        if (focus == Focus.PLAYGROUND && playground != null) {
            if (key.hasCtrl() && key.isCharIgnoreCase('e')) {
                playgroundExpanded = !playgroundExpanded;
                return true;
            }
            if (key.isCancel() || !playground.handleKeyEvent(key)) {
                focus = Focus.LIST;
            }
            return true;
        }
        if (view == View.EXPERTS && key.hasCtrl() && key.isCharIgnoreCase('e') && playground != null) {
            playgroundExpanded = !playgroundExpanded;
            focus = Focus.PLAYGROUND;
            return true;
        }
        if (view == View.EXPERTS && (key.isChar('[') || key.isChar(']'))) {
            List<JsonObject> operations = objects(selectedExpert(), "operations");
            if (!operations.isEmpty()) {
                int index = operations.indexOf(selectedOperation());
                selectOperation(operations.get(Math.floorMod(index + (key.isChar(']') ? 1 : -1), operations.size()))
                        .getString("name"));
            }
            return true;
        }
        if (key.isChar('v')) {
            view(View.values()[(view.ordinal() + 1) % View.values().length]);
            return true;
        }
        if (key.isChar('r')) {
            load();
            return true;
        }
        if (key.isChar('/')) {
            filterInput = new TextInputState(filter);
            return true;
        }
        if (key.isKey(KeyCode.TAB)) {
            focus = focus == Focus.LIST ? (view == View.RELATIONSHIPS && detailArea == null ? Focus.LIST : Focus.DETAIL)
                    : focus == Focus.DETAIL && view == View.EXPERTS ? Focus.USES
                    : focus == Focus.USES && playground != null ? Focus.PLAYGROUND : Focus.LIST;
            if (focus == Focus.PLAYGROUND) {
                playground.focusFirstField();
            }
            return true;
        }
        if (key.isChar('t') && view == View.EXPERTS) {
            if (playground != null && playground.available()) {
                focus = Focus.PLAYGROUND;
                playground.focusFirstField();
            }
            return true;
        }
        if (key.isChar('p') && view == View.EXPERTS) {
            JsonObject definition = selectedDefinition();
            if (playground != null && definition != null && definition.get("error") == null
                    && Objects.equals(operationName, definition.get("operation"))
                    && playground.loadParameters(definition)) {
                focus = Focus.PLAYGROUND;
                playground.focusFirstField();
            }
            return true;
        }
        if (key.isChar('e')) {
            JsonObject definition = selectedDefinition();
            if (pid != null && definition != null && definition.get("error") == null) {
                sample = samples.computeIfAbsent(definitionName,
                        name -> new SemanticEvaluationPopup(ctx, pid, definition, SemanticDetails.operation(data, definition)));
                sample.update(definition, SemanticDetails.operation(data, definition));
            }
            return true;
        }
        if (key.isConfirm()) {
            view(view == View.EXPERTS ? View.DEFINITIONS : View.EXPERTS);
            return true;
        }
        if (key.isPageUp() || key.isPageDown() || key.isHome() || key.isEnd()) {
            if (focus == Focus.DETAIL) {
                detailScroll = key.isHome() ? 0 : Math.max(0, detailScroll + (key.isPageUp() ? -10 : 10));
            } else {
                TableState state = focus == Focus.USES ? usesState : tableState;
                int count = focus == Focus.USES ? uses().size() : rows().size();
                int index = state.selected() == null ? 0 : state.selected();
                int next = key.isHome() ? 0 : key.isEnd() ? count - 1 : index + (key.isPageUp() ? -10 : 10);
                if (count == 0) {
                    state.clearSelection();
                } else {
                    state.select(Math.max(0, Math.min(count - 1, next)));
                }
                if (focus == Focus.USES) {
                    useChanged();
                } else {
                    selectionChanged();
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean handleEscape() {
        if (focus == Focus.PLAYGROUND) {
            focus = Focus.LIST;
            return true;
        }
        if (sample != null) {
            sample = null;
            return true;
        }
        if (filterInput != null) {
            filterInput = null;
            return true;
        }
        if (!filter.isEmpty()) {
            setFilter("");
            return true;
        }
        return false;
    }

    @Override
    public void navigateUp() {
        navigate(-1);
    }

    @Override
    public void navigateDown() {
        navigate(1);
    }

    private void navigate(int delta) {
        if (sample != null) {
            sample.handleKeyEvent(KeyEvent.ofKey(delta < 0 ? KeyCode.UP : KeyCode.DOWN));
        } else if (focus == Focus.PLAYGROUND && playground != null) {
            playground.handleKeyEvent(KeyEvent.ofKey(delta < 0 ? KeyCode.UP : KeyCode.DOWN));
        } else if (filterInput == null) {
            if (focus == Focus.DETAIL) {
                detailScroll = Math.max(0, detailScroll + delta);
            } else {
                TableState state = focus == Focus.USES ? usesState : tableState;
                int count = focus == Focus.USES ? uses().size() : rows().size();
                if (delta < 0) {
                    state.selectPrevious();
                } else {
                    state.selectNext(count);
                }
                if (focus == Focus.USES) {
                    useChanged();
                } else {
                    selectionChanged();
                }
            }
        }
    }

    @Override
    public boolean handleMouseEvent(MouseEvent event, Rect area) {
        if (sample != null) {
            sample.handleMouseEvent(event);
            return true;
        }
        if (filterInput != null) {
            return true;
        }
        if (!event.isClick()) {
            return false;
        }
        SubViewBar.View operation = view == View.EXPERTS ? operationBar.viewAt(event.x(), event.y()) : null;
        if (operation != null) {
            operation.select().run();
            focus = Focus.DETAIL;
            return true;
        }
        if (view == View.EXPERTS && playground != null && playground.handleMouseEvent(event)) {
            focus = Focus.PLAYGROUND;
            return true;
        }
        if (view == View.EXPERTS && lastTableArea != null && contains(lastTableArea, event.x(), event.y())) {
            int index = tableState.offset() + (event.y() - lastTableArea.y() - 1) / 2;
            if (event.y() >= lastTableArea.y() + 1 && index < rows().size()) {
                tableState.select(index);
                focus = Focus.LIST;
                selectionChanged();
            }
            return true;
        }
        if (view == View.EXPERTS && handleTableClick(event, usesArea, usesState, uses().size())) {
            focus = Focus.USES;
            useChanged();
            return true;
        }
        if (view == View.RELATIONSHIPS) {
            int index = relationships.clicked(event.x(), event.y());
            if (index >= 0) {
                focus = Focus.LIST;
                tableState.select(index);
                selectionChanged();
                return true;
            }
        } else if (super.handleMouseEvent(event, area)) {
            focus = Focus.LIST;
            selectionChanged();
            return true;
        }
        if (detailArea != null && contains(detailArea, event.x(), event.y())) {
            focus = Focus.DETAIL;
            return true;
        }
        return false;
    }

    @Override
    protected int getRowCount() {
        refresh();
        return rows().size();
    }

    @Override
    public void render(Frame frame, Rect area) {
        if (ctx.findSelectedIntegration() == null && data != null && Objects.equals(selectionId, ctx.selectedPid)) {
            // Keep the draft visible while this application's connection is lost.
            renderContent(frame, area, null);
        } else {
            super.render(frame, area);
        }
    }

    @Override
    protected void renderContent(Frame frame, Rect area, IntegrationInfo info) {
        lastTableArea = null;
        detailArea = null;
        usesArea = null;
        operationBar.clear();
        if (area.width() < 50 || area.height() < 12) {
            frame.renderWidget(Paragraph.from(" Enlarge the terminal to inspect semantic definitions."), area);
            return;
        }
        if (view == View.DEFINITIONS) {
            int tableHeight = Math.min(Math.max(8, rows().size() + 5), Math.max(6, area.height() * 40 / 100));
            List<Rect> panels
                    = Layout.vertical().constraints(Constraint.length(tableHeight), Constraint.length(0), Constraint.fill())
                            .split(area);
            renderTable(frame, panels.get(0), rows(), false);
            detailArea = panels.get(2);
            SemanticDetails.renderDefinition(frame, detailArea, data, selectedDefinition(),
                    block("Definition · " + (definitionName == null ? "selection" : definitionName), focus == Focus.DETAIL),
                    detailScroll);
        } else if (view == View.EXPERTS) {
            List<Rect> panels
                    = Layout.horizontal().constraints(Constraint.percentage(36), Constraint.length(1), Constraint.fill())
                            .split(area);
            int expertsHeight = Math.min(rows().size() * 2 + 3 + defaultLines(panels.get(0).width() - 4).size(),
                    Math.max(7, area.height() / 3));
            List<Rect> left = Layout.vertical().constraints(Constraint.length(expertsHeight), Constraint.length(0),
                    Constraint.fill()).split(panels.get(0));
            renderExperts(frame, left.get(0));
            String title = "Expert contract · " + (expertReference == null ? "selection" : expertReference)
                           + (operationName == null ? "" : " / " + operationName);
            renderDetails(frame, left.get(2), SemanticDetails.expert(data, selectedExpert(), selectedOperation()), title);
            int usesHeight = playgroundExpanded ? 0 : Math.max(4, Math.min(uses().size() + 3, 7));
            List<Rect> right = Layout.vertical().constraints(Constraint.length(1), Constraint.length(usesHeight),
                    Constraint.fill()).split(panels.get(2));
            renderOperations(frame, right.get(0));
            if (!playgroundExpanded) {
                renderTable(frame, right.get(1), uses(), true);
            }
            if (playground != null) {
                playground.render(frame, right.get(2), focus == Focus.PLAYGROUND, playgroundExpanded);
            } else {
                frame.renderWidget(Paragraph.from(" No operation available for direct evaluation."), right.get(2));
            }
        } else {
            Rect mapArea = area;
            if (area.width() >= 120) {
                List<Rect> panels = Layout.horizontal()
                        .constraints(Constraint.percentage(31), Constraint.length(1), Constraint.fill()).split(area);
                renderDetails(frame, panels.get(0), SemanticDetails.definition(data, selectedDefinition()),
                        "Definition · " + (definitionName == null ? "selection" : definitionName));
                mapArea = panels.get(2);
            }
            relationships.render(frame, mapArea, data, rows(), tableState.selected(),
                    block("Semantic relationships" + filterLabel(), focus == Focus.LIST), emptyMessage());
        }
        if (sample != null) {
            sample.render(frame, area);
        }
    }

    private void renderOperations(Frame frame, Rect area) {
        List<JsonObject> operations = objects(selectedExpert(), "operations");
        JsonObject operation = selectedOperation();
        int selected = operation == null ? 0 : Math.max(0, operations.indexOf(operation));
        int capacity = Math.max(1, (area.width() - 8) / 24);
        int start = Math.max(0, selected - capacity + 1);
        List<SubViewBar.View> views = new ArrayList<>();
        for (int i = start; i < Math.min(operations.size(), start + capacity); i++) {
            String name = text(operations.get(i), "name");
            long count = uses().stream().filter(d -> Objects.equals(name, d.get("operation"))).count();
            views.add(new SubViewBar.View(
                    truncate(name, 13) + " (" + count + ")", name.equals(operationName), true,
                    () -> selectOperation(name)));
        }
        operationBar.render(frame, area, new SubViewBar.Spec("[ ]", views, List.of(), false));
    }

    private List<Line> defaultLines(int width) {
        String value = data != null && data.get("defaultExpert") != null ? text(data, "defaultExpert") : "none";
        if (data != null && data.get("defaultError") != null) {
            value += " · " + text(data, "defaultError");
        }
        return hangingWrap(List.of(Line.from(Span.styled("Default: ", Theme.label()), Span.raw(value))), Math.max(1, width))
                .stream().limit(3).toList();
    }

    private String filterLabel() {
        return filter.isEmpty() ? "" : " / " + filter;
    }

    private String emptyMessage() {
        return pending != null ? "Loading semantic metadata..." : error != null ? error : "No matching semantic declarations";
    }

    private void renderExperts(Frame frame, Rect area) {
        Block block = block("Experts [" + rows().size() + "]" + filterLabel(), focus == Focus.LIST);
        frame.renderWidget(block, area);
        Rect inner = block.inner(area);
        List<Row> content = new ArrayList<>();
        for (JsonObject expert : rows()) {
            int ops = objects(expert, "operations").size();
            long uses = objects(data, "evaluations").stream()
                    .filter(d -> Objects.equals(d.get("expert"), expert.get("reference")))
                    .count();
            content.add(Row.from(Cell.from(Text.from(
                    Line.from(Span.styled(text(expert, "reference"), Style.EMPTY.bold())),
                    Line.from(Span
                            .styled(ops + (inner.width() < 32 ? " ops" : ops == 1 ? " operation" : " operations") + " · " + uses
                                    + (uses == 1 ? " definition" : " definitions"),
                                    Theme.muted())))))
                    .height(2));
        }
        if (content.isEmpty()) {
            content.add(Row.from(emptyMessage()));
        }
        List<Line> footer = defaultLines(inner.width() - 2);
        lastTableArea = new Rect(
                inner.x() + 1, inner.y(), Math.max(0, inner.width() - 2),
                Math.max(0, inner.height() - footer.size()));
        Table table = Table.builder().rows(content).header(Row.from("EXPERT").style(Theme.label()))
                .widths(Constraint.fill()).highlightSymbol("› ").highlightStyle(Theme.selectionBg())
                .highlightSpacing(Table.HighlightSpacing.ALWAYS).build();
        if (rows().isEmpty()) {
            SemanticDetails.paragraph(frame, lastTableArea, List.of(Line.from(emptyMessage())), 0);
        } else {
            frame.renderStatefulWidget(table, lastTableArea, tableState);
            renderScrollbar(frame, table, content.size());
        }
        SemanticDetails.paragraph(frame, new Rect(
                inner.x() + 1, inner.bottom() - footer.size(),
                Math.max(0, inner.width() - 2), footer.size()), footer, 0);
    }

    private void renderTable(Frame frame, Rect area, List<JsonObject> rows, boolean linked) {
        List<Row> content = new ArrayList<>();
        int stateWidth = Math.max(1, (area.width() - 8) * (linked ? 40 : 30) / 100);
        for (JsonObject row : rows) {
            content.add(linked
                    ? Row.from(text(row, "name"), text(row, "operation"), truncate(text(row, "state"), stateWidth))
                    : Row.from(text(row, "name"), text(row, "expert"), text(row, "operation"),
                            row.get("error") != null ? "unresolved" : text(row, "resultType"),
                            truncate(text(row, "state"), stateWidth)));
        }
        if (content.isEmpty()) {
            content.add(emptyRow(linked ? "No definitions use this expert" : emptyMessage(), linked ? 3 : 5));
        }
        String title = linked
                ? "Definitions using " + (expertReference == null ? "expert" : expertReference) + " [" + rows.size() + "]"
                : view.label + " [" + rows.size() + "]" + filterLabel();
        Table table = Table.builder().rows(content)
                .header((linked
                        ? Row.from("DEFINITION", "OPERATION", "STATE")
                        : Row.from("DEFINITION", "EXPERT", "OPERATION", "RESULT", "STATE")).style(Theme.label()))
                .widths(linked
                        ? List.of(Constraint.percentage(38), Constraint.percentage(22), Constraint.fill())
                        : List.of(Constraint.percentage(22), Constraint.percentage(18), Constraint.percentage(18),
                                Constraint.percentage(12), Constraint.fill()))
                .highlightSymbol("› ").highlightStyle(Theme.selectionBg()).highlightSpacing(Table.HighlightSpacing.ALWAYS)
                .block(block(title, focus == (linked ? Focus.USES : Focus.LIST))).build();
        if (rows.isEmpty()) {
            frame.renderWidget(Paragraph.builder().text(linked ? " No definitions use this expert" : emptyMessage())
                    .overflow(Overflow.WRAP_WORD).block(block(title, focus == (linked ? Focus.USES : Focus.LIST))).build(),
                    area);
        } else {
            frame.renderStatefulWidget(table, area, linked ? usesState : tableState);
        }
        if (linked) {
            usesArea = area;
        } else {
            lastTableArea = area;
            renderScrollbar(frame, table, rows.size());
        }
    }

    private void renderDetails(Frame frame, Rect area, List<Line> lines, String title) {
        detailArea = area;
        Block block = block(truncate(title, area.width() - 4), focus == Focus.DETAIL);
        frame.renderWidget(block, area);
        Rect inner = SemanticDetails.inset(block.inner(area));
        // Clamp scrolling to the wrapped content, so an empty page is never selected.
        detailScroll = Math.min(detailScroll, Math.max(0, hangingWrap(lines, inner.width()).size() - inner.height()));
        SemanticDetails.paragraph(frame, inner, lines, detailScroll);
    }

    private Block block(String title, boolean focused) {
        return Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                .borderStyle(ctx.paneBorder(focused))
                .title(Title.from(Line.from(Span.styled(" " + title + " ", Theme.title())))).build();
    }

    @Override
    public boolean setFilter(String value) {
        filter = value == null ? "" : value.strip();
        restoreSelection();
        relationships.reset();
        return true;
    }

    @Override
    public void renderFooter(List<Span> spans) {
        if (sample != null) {
            sample.renderFooter(spans);
        } else if (focus == Focus.PLAYGROUND && playground != null) {
            playground.renderFooter(spans);
        } else if (filterInput != null) {
            spans.add(Span.raw(" /" + filterInput.text() + "█  "));
            hint(spans, "Enter", "filter");
            hintLast(spans, "Esc", "cancel");
        } else {
            hint(spans, "Esc", filter.isEmpty() ? "back" : "clear filter");
            JsonObject definition = selectedDefinition();
            if (definition != null && definition.get("error") == null) {
                hint(spans, "e", "sample");
            }
            if (view == View.EXPERTS) {
                if (playground != null && playground.available()) {
                    hint(spans, "t", "try directly");
                    if (definition != null && definition.get("error") == null) {
                        hint(spans, "p", "load parameters");
                    }
                }
                if (objects(selectedExpert(), "operations").size() > 1) {
                    hint(spans, "[ ]", "operation");
                }
                if (playgroundExpanded) {
                    hint(spans, "Ctrl+e", "restore");
                }
            }
            hint(spans, "v", "view");
            hint(spans, "Tab", "pane");
            hint(spans, "/", "filter");
            hint(spans, "r", "refresh");
            hintLast(spans, "Enter", view == View.EXPERTS ? "→ Definitions" : "→ Experts");
        }
    }

    @Override
    public Boolean isDetailFocused() {
        return focus == Focus.DETAIL;
    }

    @Override
    public SelectionContext getSelectionContext() {
        List<JsonObject> visible = focus == Focus.USES ? uses() : rows();
        List<String> names
                = visible.stream().map(row -> text(row, view == View.EXPERTS && focus != Focus.USES ? "reference" : "name"))
                        .toList();
        Integer selected = focus == Focus.USES ? usesState.selected() : tableState.selected();
        return new SelectionContext("table", names, selected == null ? -1 : selected, names.size(), "Semantic");
    }

    @Override
    public JsonObject getTableDataAsJson() {
        refresh();
        if (data == null) {
            return null;
        }
        JsonObject result = new JsonObject();
        result.put("tab", "Semantic");
        result.put("view", view.label);
        result.put("rows", rows());
        result.put("totalRows", rows().size());
        result.put("selectedIndex", tableState.selected());
        result.put("selectedDefinition", definitionName);
        result.put("selectedExpert", expertReference);
        result.put("selectedOperation", operationName);
        result.put("focusedPane", sample != null ? "sample" : focus.name().toLowerCase(Locale.ROOT));
        if (view == View.EXPERTS && playground != null) {
            result.put("playground", playground.snapshot());
        }
        if (sample != null) {
            result.put("sample", sample.snapshot());
        }
        return result;
    }

    @Override
    public boolean setInputValue(String field, String value) {
        refresh();
        if (sample != null) {
            return sample.setInputValue(field, value);
        }
        if (view == View.EXPERTS && playground != null && playground.setInputValue(field, value)) {
            focus = Focus.PLAYGROUND;
            return true;
        }
        return false;
    }

    @Override
    public String description() {
        return "Semantic definitions, expert contracts, relationships and sample evaluation";
    }

    @Override
    public String getHelpText() {
        return DocHelper.loadHelpText("semantic");
    }
}
