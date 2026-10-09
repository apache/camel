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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import dev.tamboui.layout.Rect;
import dev.tamboui.style.Overflow;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.input.TextArea;
import dev.tamboui.widgets.input.TextAreaState;
import dev.tamboui.widgets.input.TextInputState;
import org.apache.camel.util.json.DeserializationException;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

import static org.apache.camel.dsl.jbang.core.commands.tui.SemanticDetails.*;

/** Parameter controls derived solely from the expert's published contract. */
final class SemanticParameterForm {
    private static final int SCALAR = -1;
    private static final int ADD = -2;

    private static final class Field {
        final JsonObject contract;
        final TextAreaState text = new TextAreaState();
        final List<Entry> entries = new ArrayList<>();
        final List<String> choices;
        int choice = -1;

        Field(JsonObject contract) {
            this.contract = contract;
            choices = "Boolean".equals(type()) ? List.of("false", "true")
                    : contract.get("values") instanceof List<?> values ? values.stream().map(Object::toString).toList()
                    : List.of();
            if (collection()) {
                entries.add(new Entry(new TextInputState(), new TextAreaState()));
            }
        }

        String name() {
            return contract.getString("name");
        }

        String type() {
            return contract.getString("type");
        }

        boolean collection() {
            return "Map".equals(type()) || "List".equals(type());
        }

        boolean required() {
            return contract.getBooleanOrDefault("required", false);
        }
    }

    private record Entry(TextInputState keyInput, TextAreaState valueInput) {
    }

    private record Control(Field field, int row, boolean key) {
    }

    private record Row(Line label, Control left, Control right, int height) {
    }

    private record Hit(Rect area, Control control) {
    }

    private final List<Field> fields;
    private final List<Hit> hits = new ArrayList<>();
    private Control selected;
    private int scroll;
    private long revision;

    SemanticParameterForm(JsonObject operation) {
        this(SemanticTab.objects(contract(operation), "parameters"));
    }

    SemanticParameterForm(List<JsonObject> contracts) {
        fields = contracts.stream().map(Field::new).toList();
        focus(false);
    }

    void load(Map<?, ?> values) {
        for (Field field : fields) {
            load(field, values.get(field.name()));
        }
        revision++;
        focus(false);
    }

    private void load(Field field, Object value) {
        field.text.clear();
        field.choice = -1;
        field.entries.clear();
        if (!field.choices.isEmpty()) {
            field.choice = value == null ? -1 : field.choices.indexOf(value.toString());
        } else if (value instanceof Map<?, ?> map && "Map".equals(field.type())) {
            map.forEach((key, item) -> field.entries.add(new Entry(
                    new TextInputState(key.toString()),
                    new TextAreaState(
                            "String".equals(field.contract.get("itemType")) && item instanceof String text
                                    ? text : Jsoner.serialize(item)))));
        } else if (value instanceof List<?> list && "List".equals(field.type())) {
            list.forEach(item -> field.entries.add(new Entry(
                    new TextInputState(),
                    new TextAreaState(
                            "String".equals(field.contract.get("itemType")) && item instanceof String text
                                    ? text : Jsoner.serialize(item)))));
        } else if (value != null) {
            field.text.insert(value instanceof String text ? text : Jsoner.serialize(value));
        }
        if (field.collection() && field.entries.isEmpty()) {
            field.entries.add(new Entry(new TextInputState(), new TextAreaState()));
        }
    }

    boolean setInputValue(String name, String text) {
        Field field = fields.stream().filter(f -> f.name().equals(name)).findFirst().orElse(null);
        if (field == null) {
            return false;
        }
        if (!field.collection() && field.choices.isEmpty()) {
            field.text.setText(text);
        } else {
            Object value = null;
            if (!text.isBlank()) {
                value = typed(text, field.type());
                validate(field, value);
                if (!field.choices.isEmpty() && !field.choices.contains(value.toString())) {
                    throw new IllegalArgumentException(name + ": choose one of " + field.choices);
                }
            }
            load(field, value);
        }
        selected = controls().stream().filter(c -> c.field() == field).findFirst().orElse(null);
        revision++;
        return true;
    }

    List<JsonObject> snapshot() {
        List<JsonObject> result = new ArrayList<>();
        for (Field field : fields) {
            JsonObject item = new JsonObject();
            item.put("name", field.name());
            item.put("type", field.type());
            item.put("required", field.required());
            if (field.collection()) {
                item.put("entries", field.entries.stream().map(entry -> Map.of(
                        "key", entry.keyInput().text(), "value", entry.valueInput().text())).toList());
            } else {
                item.put("text", field.choices.isEmpty() ? field.text.text()
                        : field.choice < 0 ? "" : field.choices.get(field.choice));
            }
            try {
                Object value = value(field);
                item.put("value", value);
                if (value == null && field.required()) {
                    throw new IllegalArgumentException("is required");
                }
                if (value != null) {
                    validate(field, value);
                }
            } catch (IllegalArgumentException invalid) {
                item.put("error", invalid.getMessage());
            }
            result.add(item);
        }
        return result;
    }

    void clearHits() {
        hits.clear();
    }

    boolean isEmpty() {
        return fields.isEmpty();
    }

    long revision() {
        return revision;
    }

    void focus(boolean last) {
        List<Control> controls = controls();
        selected = controls.isEmpty() ? null : controls.get(last ? controls.size() - 1 : 0);
    }

    boolean next(boolean backwards) {
        List<Control> controls = controls();
        int next = controls.indexOf(selected) + (backwards ? -1 : 1);
        if (next < 0 || next >= controls.size()) {
            return false;
        }
        selected = controls.get(next);
        return true;
    }

    private List<Control> controls() {
        List<Control> controls = new ArrayList<>();
        for (Field field : fields) {
            if (field.collection()) {
                for (int i = 0; i < field.entries.size(); i++) {
                    if ("Map".equals(field.type())) {
                        controls.add(new Control(field, i, true));
                    }
                    controls.add(new Control(field, i, false));
                }
                controls.add(new Control(field, ADD, false));
            } else {
                controls.add(new Control(field, SCALAR, false));
            }
        }
        return controls;
    }

    void handleKeyEvent(KeyEvent key) {
        if (selected == null) {
            return;
        }
        List<?> before = draft();
        Field field = selected.field();
        if (field.collection() && ((key.hasCtrl() && key.isCharIgnoreCase('n'))
                || selected.row() == ADD && key.isConfirm())) {
            int row = selected.row() >= 0 ? selected.row() + 1 : field.entries.size();
            field.entries.add(row, new Entry(new TextInputState(), new TextAreaState()));
            selected = new Control(field, row, "Map".equals(field.type()));
        } else if (selected.row() >= 0 && key.hasCtrl() && key.isCharIgnoreCase('d')) {
            field.entries.remove(selected.row());
            selected = new Control(
                    field, selected.row() < field.entries.size() ? selected.row() : ADD,
                    selected.row() < field.entries.size() && "Map".equals(field.type()));
        } else if (selected.row() >= 0 && key.hasCtrl() && (key.isKey(KeyCode.UP) || key.isKey(KeyCode.DOWN))) {
            int target = selected.row() + (key.isKey(KeyCode.UP) ? -1 : 1);
            if (target >= 0 && target < field.entries.size()) {
                Collections.swap(field.entries, selected.row(), target);
                selected = new Control(field, target, selected.key());
            }
        } else if (key.hasCtrl() && key.isCharIgnoreCase('l')) {
            if (selected.row() >= 0) {
                Entry entry = field.entries.get(selected.row());
                if (selected.key()) {
                    entry.keyInput().setText("");
                } else {
                    entry.valueInput().clear();
                }
            } else if (selected.row() == SCALAR) {
                field.text.clear();
                field.choice = -1;
            }
        } else if (selected.row() >= 0 && !key.hasCtrl()) {
            Entry entry = field.entries.get(selected.row());
            if (selected.key()) {
                FormHelper.handleTextInput(key, entry.keyInput());
            } else {
                FormHelper.handleTextArea(key, entry.valueInput());
            }
        } else if (selected.row() == SCALAR && !field.choices.isEmpty()) {
            if (key.isConfirm() || key.isChar(' ') || key.isLeft() || key.isRight()) {
                field.choice = Math.floorMod(field.choice + (key.isLeft() ? -1 : 1) + 1, field.choices.size() + 1) - 1;
            }
        } else if (selected.row() == SCALAR && (!key.isConfirm() || "String".equals(field.type()))) {
            FormHelper.handleTextArea(key, field.text);
        }
        if (!before.equals(draft())) {
            revision++;
        }
    }

    void handlePaste(String value) {
        if (selected == null || value == null) {
            return;
        }
        List<?> before = draft();
        if (selected.row() >= 0) {
            Entry entry = selected.field().entries.get(selected.row());
            if (selected.key()) {
                FormHelper.handlePaste(value, entry.keyInput());
            } else {
                entry.valueInput().insert(value);
            }
        } else if (selected.row() == SCALAR && selected.field().choices.isEmpty()) {
            selected.field().text.insert(value);
        }
        if (!before.equals(draft())) {
            revision++;
        }
    }

    private List<?> draft() {
        return fields.stream().map(field -> List.of(field.choice, field.text.text(), field.entries.stream()
                .map(entry -> List.of(entry.keyInput().text(), entry.valueInput().text())).toList())).toList();
    }

    boolean click(int x, int y) {
        for (Hit hit : hits) {
            if (TuiHelper.contains(hit.area(), x, y)) {
                selected = hit.control();
                if (selected.row() == ADD) {
                    handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER));
                }
                return true;
            }
        }
        return false;
    }

    JsonObject values() {
        return values(true);
    }

    JsonObject values(boolean focusInvalid) {
        JsonObject values = new JsonObject();
        for (Field field : fields) {
            try {
                Object value = value(field);
                if (value == null) {
                    if (field.required()) {
                        throw new IllegalArgumentException("is required");
                    }
                } else {
                    validate(field, value);
                    values.put(field.name(), value);
                }
            } catch (IllegalArgumentException invalid) {
                if (focusInvalid) {
                    selected = controls().stream().filter(control -> control.field() == field).findFirst().orElse(null);
                }
                throw new IllegalArgumentException(field.name() + ": " + invalid.getMessage());
            }
        }
        return values;
    }

    private Object value(Field field) {
        if (!field.choices.isEmpty()) {
            return field.choice < 0 ? null : typed(field.choices.get(field.choice), field.type());
        }
        if (!field.collection()) {
            String value = field.text.text();
            return value.isBlank() && !(field.required() && "String".equals(field.type())) ? null : typed(value, field.type());
        }
        JsonObject map = new JsonObject();
        List<Object> list = new ArrayList<>();
        for (Entry entry : field.entries) {
            String key = entry.keyInput().text();
            String value = entry.valueInput().text();
            if (key.isBlank() && value.isBlank()) {
                continue;
            }
            if (value.isBlank() || "Map".equals(field.type()) && key.isBlank()) {
                throw new IllegalArgumentException("complete each entry or remove its row");
            }
            Object item = typed(value, text(field.contract, "itemType"));
            if ("Map".equals(field.type())) {
                if (map.containsKey(key)) {
                    throw new IllegalArgumentException("duplicate key '" + key + "'");
                }
                map.put(key, item);
            } else {
                list.add(item);
            }
        }
        if ("Map".equals(field.type())) {
            return map.isEmpty() && !field.required() ? null : map;
        }
        return list.isEmpty() && !field.required() ? null : list;
    }

    private static Object typed(String value, String type) {
        try {
            return switch (type) {
                case "String" -> value;
                case "Number" -> new BigDecimal(value.strip());
                case "Boolean" -> {
                    if (!"true".equals(value) && !"false".equals(value)) {
                        throw new IllegalArgumentException("enter true or false");
                    }
                    yield Boolean.valueOf(value);
                }
                default -> {
                    Object parsed = Jsoner.deserialize(value);
                    if ("Map".equals(type) && !(parsed instanceof Map<?, ?>)
                            || "List".equals(type) && !(parsed instanceof List<?>)) {
                        throw new IllegalArgumentException("enter a JSON " + ("Map".equals(type) ? "object" : "array"));
                    }
                    yield parsed;
                }
            };
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("enter a number");
        } catch (DeserializationException invalid) {
            throw new IllegalArgumentException("this nested value must be valid JSON");
        }
    }

    private static void validate(Field field, Object value) {
        JsonObject contract = field.contract;
        if (value instanceof BigDecimal number) {
            if (contract.getBooleanOrDefault("integer", false) && number.stripTrailingZeros().scale() > 0) {
                throw new IllegalArgumentException("enter a whole number");
            }
            for (String bound : List.of("minimum", "maximum")) {
                if (contract.get(bound) instanceof Number limit) {
                    int comparison = number.compareTo(new BigDecimal(limit.toString()));
                    if ("minimum".equals(bound) ? comparison < 0 : comparison > 0) {
                        throw new IllegalArgumentException(bound + " is " + limit);
                    }
                }
            }
        }
        int size = value instanceof String text ? text.strip().length()
                : value instanceof Map<?, ?> map ? map.size() : value instanceof List<?> list ? list.size() : -1;
        if (size >= 0) {
            if (contract.get("minSize") instanceof Number min && size < min.intValue()) {
                throw new IllegalArgumentException("minimum size is " + min);
            }
            if (contract.get("maxSize") instanceof Number max && size > max.intValue()) {
                throw new IllegalArgumentException("maximum size is " + max);
            }
        }
    }

    void renderInstructions(Frame frame, Rect area, boolean focused, boolean editable, Style borderStyle) {
        hits.clear();
        Field field = fields.get(0);
        Block block = Block.builder().borders(Borders.ALL).borderType(BorderType.ROUNDED).borderStyle(borderStyle)
                .title(" " + TuiHelper.truncate("Instructions · text" + (field.required() ? " *" : ""), area.width() - 4) + " ")
                .build();
        frame.renderWidget(block, area);
        Rect inner = block.inner(area);
        hits.add(new Hit(inner, new Control(field, SCALAR, false)));
        renderTextArea(frame, inner, field.text, focused && editable, text(field.contract, "description"));
    }

    void render(Frame frame, Rect area, boolean focused, boolean editable, Style borderStyle) {
        hits.clear();
        Block block = Block.builder().borders(Borders.ALL).borderType(BorderType.ROUNDED).borderStyle(borderStyle)
                .title(" "
                       + TuiHelper.truncate("Parameters" + (focused && selected != null ? " · " + selected.field().name() : ""),
                               area.width() - 4)
                       + " ")
                .build();
        frame.renderWidget(block, area);
        Rect inner = block.inner(area);
        if (inner.width() < 12 || inner.height() < 1) {
            return;
        }
        List<Row> rows = rows(Math.max(1, inner.width() - 2), inner.height());
        int y = 0;
        for (Row row : rows) {
            if (focused && (selected != null && (selected.equals(row.left()) || selected.equals(row.right())))) {
                scroll = Math.max(0, Math.min(scroll, y));
                scroll = Math.max(scroll, y + Math.min(row.height(), inner.height()) - inner.height());
            }
            y += row.height();
        }
        scroll = Math.min(scroll, Math.max(0, y - inner.height()));
        y = -scroll;
        for (Row row : rows) {
            if (y >= 0 && y < inner.height()) {
                Rect slot = new Rect(
                        inner.x() + 1, inner.y() + y, inner.width() - 2,
                        Math.min(row.height(), inner.height() - y));
                if (row.label() != null) {
                    paragraph(frame, slot, List.of(row.label()), 0);
                } else if (row.right() != null) {
                    int width = Math.max(5, slot.width() / 3);
                    renderControl(frame, new Rect(slot.x(), slot.y(), width, slot.height()), row.left(), focused, editable);
                    renderControl(frame, new Rect(slot.x() + width + 1, slot.y(), slot.width() - width - 1, slot.height()),
                            row.right(), focused, editable);
                } else {
                    renderControl(frame, slot, row.left(), focused, editable);
                }
            }
            y += row.height();
        }
    }

    private List<Row> rows(int width, int height) {
        List<Row> rows = new ArrayList<>();
        if (fields.isEmpty()) {
            rows.add(new Row(Line.from("No parameters"), null, null, 1));
        }
        for (Field field : fields) {
            addLabel(rows, Line.from(Span.styled(field.name(), Theme.label()),
                    Span.styled(" · " + field.type() + (field.required() ? " * required" : " · optional"), Theme.muted())),
                    width);
            String help = text(field.contract, "description");
            if (!field.required() && field.contract.get("omission") != null) {
                help += " · blank: " + text(field.contract, "omission");
            }
            if (field.contract.get("minimum") != null || field.contract.get("maximum") != null) {
                help += " · " + text(field.contract, "minimum") + " … " + text(field.contract, "maximum");
            }
            if (field.contract.get("minSize") != null && field.contract.getInteger("minSize") > 0) {
                help += " · min size " + field.contract.get("minSize");
            }
            if (field.contract.get("maxSize") != null && field.contract.getInteger("maxSize") < Integer.MAX_VALUE) {
                help += " · max size " + field.contract.get("maxSize");
            }
            addLabel(rows, Line.from(Span.styled(help, Theme.muted())), width);
            if (field.collection()) {
                rows.add(new Row(
                        Line.from(Span.styled("Map".equals(field.type()) ? "Key / value" : "Ordered entries [0…]",
                                Theme.muted())),
                        null, null, 1));
                for (int i = 0; i < field.entries.size(); i++) {
                    Control value = new Control(field, i, false);
                    rows.add("Map".equals(field.type())
                            ? new Row(
                                    null, new Control(field, i, true), value,
                                    entryHeight(field.entries.get(i).valueInput(), width - Math.max(5, width / 3) - 1, height))
                            : new Row(
                                    null, value, null,
                                    entryHeight(field.entries.get(i).valueInput(), width - ("[" + i + "] ").length(), height)));
                }
                rows.add(new Row(null, new Control(field, ADD, false), null, 1));
            } else {
                rows.add(new Row(
                        null, new Control(field, SCALAR, false), null,
                        field.choices.isEmpty() && "String".equals(field.type()) ? entryHeight(field.text, width, height) : 1));
            }
            rows.add(new Row(Line.empty(), null, null, 1));
        }
        return rows;
    }

    private static void addLabel(List<Row> rows, Line label, int width) {
        TuiHelper.hangingWrap(List.of(label), width).forEach(line -> rows.add(new Row(line, null, null, 1)));
    }

    private static int entryHeight(TextAreaState state, int width, int height) {
        int wrapped = TuiHelper.hangingWrap(state.text().lines().map(Line::from).toList(), Math.max(1, width)).size();
        return Math.min(Math.max(1, height), Math.max(2, wrapped + 1));
    }

    private void renderControl(Frame frame, Rect area, Control control, boolean focused, boolean editable) {
        hits.add(new Hit(area, control));
        Field field = control.field();
        boolean active = focused && editable && control.equals(selected);
        if (control.row() == ADD) {
            paragraph(frame, area, List.of(Line.from(Span.styled("[ + Add entry ]", active ? Theme.hintKey() : Theme.info()))),
                    0);
        } else if (control.row() >= 0) {
            Entry entry = field.entries.get(control.row());
            if ("List".equals(field.type())) {
                String index = "[" + control.row() + "] ";
                paragraph(frame, area, List.of(Line.from(Span.styled(index, Theme.label()))), 0);
                area = new Rect(area.x() + index.length(), area.y(), Math.max(0, area.width() - index.length()), area.height());
            }
            String placeholder = control.key() ? "Key" : "String".equals(field.contract.get("itemType"))
                    ? "Value" : "Value (" + text(field.contract, "itemType") + ")";
            if (control.key()) {
                FormHelper.renderTextField(frame, new Rect(area.x(), area.y(), area.width(), 1), entry.keyInput(), active,
                        placeholder);
            } else {
                renderTextArea(frame, area, entry.valueInput(), active, placeholder);
            }
        } else if (!field.choices.isEmpty()) {
            String value
                    = field.choice < 0 ? field.required() ? "Select value" : "Expert default" : field.choices.get(field.choice);
            paragraph(frame, area,
                    List.of(Line.from(Span.styled("‹ " + value + " ›", active ? Theme.hintKey() : Theme.info()))), 0);
        } else {
            renderTextArea(frame, area, field.text, active, field.required() ? "Enter " + field.name() : "Expert default");
        }
    }

    private static void renderTextArea(Frame frame, Rect area, TextAreaState state, boolean active, String placeholder) {
        TextArea editor = TextArea.builder().overflow(Overflow.WRAP_WORD).placeholder(placeholder)
                .cursorStyle(Style.EMPTY.reversed()).build();
        if (active) {
            editor.renderWithCursor(area, frame.buffer(), state, frame);
        } else {
            List<Line> lines = TuiHelper.hangingWrap((state.text().isEmpty() ? placeholder : state.text()).lines()
                    .map(Line::from).toList(), area.width());
            paragraph(frame, area, lines, 0);
            if (lines.size() > area.height() && area.height() > 0) {
                paragraph(frame, new Rect(area.right() - 1, area.bottom() - 1, 1, 1), List.of(Line.from("↓")), 0);
            }
        }
    }

    void renderFooter(List<Span> spans) {
        if (selected != null && selected.field().collection()) {
            TuiHelper.hint(spans, "Ctrl+n", "add row");
            TuiHelper.hint(spans, "Ctrl+d", "remove row");
            TuiHelper.hint(spans, "Ctrl+↑↓", "move row");
        } else if (selected != null && !selected.field().choices.isEmpty()) {
            TuiHelper.hint(spans, "←→", "choose value");
        }
    }
}
