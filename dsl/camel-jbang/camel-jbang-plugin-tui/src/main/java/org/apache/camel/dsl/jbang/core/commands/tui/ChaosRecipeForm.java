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
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
import dev.tamboui.widgets.Clear;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.input.TextInputState;
import dev.tamboui.widgets.paragraph.Paragraph;

/**
 * Adding a chaos rule from the known rules ({@link ChaosRecipes}): a list of what can go wrong, grouped, and for the
 * chosen one a field per placeholder of its rule. A field with values to pick from (the endpoints, routes or steps of
 * the integration) cycles through them with Left and Right, and can still be typed; the other fields start with the
 * default of the recipe. The rule is shown as it is built, and Enter takes it to the confirmation of
 * {@link ChaosControl}. The last entry of the list types the whole rule instead.
 */
final class ChaosRecipeForm {

    /** What the form gives back after a key. */
    enum Outcome {
        // the key was used; nothing to do
        NONE,
        // Esc on the list: close
        CLOSED,
        // the last entry of the list: type the rule as one line
        TYPE_RULE,
        // the rule is complete: confirm it
        RULE
    }

    /** A field of the form: the placeholder, its value, and the values to pick from. */
    static final class Field {
        final ChaosRecipes.Placeholder placeholder;
        final TextInputState input;
        final List<String> choices;
        int choice = -1;

        Field(ChaosRecipes.Placeholder placeholder, List<String> choices) {
            this.placeholder = placeholder;
            this.choices = choices;
            String start = placeholder.defaultValue() != null ? placeholder.defaultValue() : "";
            if (start.isEmpty() && !choices.isEmpty()) {
                choice = 0;
                start = choices.get(0);
            }
            this.input = new TextInputState(start);
            input.moveCursorToEnd();
        }

        void cycle(int step) {
            if (choices.isEmpty()) {
                return;
            }
            choice = Math.floorMod(choice + step, choices.size());
            input.clear();
            input.insert(choices.get(choice));
        }

        String value() {
            return input.text().strip();
        }
    }

    private static final String TYPE_IT = "Write the rule yourself …";

    private final List<ChaosRecipes.Recipe> recipes;
    private final IntegrationInfo info;
    // the index in the list (the recipes, then the entry that types the rule)
    private int selected;
    private boolean inForm;
    private final List<Field> fields = new ArrayList<>();
    private int focus;
    private String rule;

    ChaosRecipeForm(ChaosRecipes recipes, IntegrationInfo info) {
        this.recipes = recipes.recipes();
        this.info = info;
    }

    boolean inForm() {
        return inForm;
    }

    /** The rule of the form, with the values filled in. */
    String rule() {
        return rule;
    }

    ChaosRecipes.Recipe selectedRecipe() {
        return selected < recipes.size() ? recipes.get(selected) : null;
    }

    List<Field> fields() {
        return fields;
    }

    Outcome handleKey(KeyEvent ke) {
        return inForm ? handleFormKey(ke) : handleListKey(ke);
    }

    private Outcome handleListKey(KeyEvent ke) {
        if (ke.isCancel()) {
            return Outcome.CLOSED;
        }
        if (ke.isUp()) {
            selected = Math.max(0, selected - 1);
        } else if (ke.isDown()) {
            selected = Math.min(recipes.size(), selected + 1);
        } else if (ke.isConfirm() || ke.isRight()) {
            if (selected == recipes.size()) {
                return Outcome.TYPE_RULE;
            }
            openForm(recipes.get(selected));
        }
        return Outcome.NONE;
    }

    private void openForm(ChaosRecipes.Recipe recipe) {
        fields.clear();
        for (ChaosRecipes.Placeholder p : recipe.placeholders()) {
            fields.add(new Field(p, ChaosRecipes.candidates(p, info)));
        }
        focus = 0;
        inForm = true;
    }

    private Outcome handleFormKey(KeyEvent ke) {
        if (ke.isCancel()) {
            // back to the list
            inForm = false;
            return Outcome.NONE;
        }
        Field field = fields.isEmpty() ? null : fields.get(focus);
        if (ke.isUp()) {
            focus = Math.max(0, focus - 1);
        } else if (ke.isDown() || ke.isKey(KeyCode.TAB)) {
            focus = Math.min(fields.size() - 1, focus + 1);
        } else if (field != null && !field.choices.isEmpty() && (ke.isLeft() || ke.isRight())) {
            field.cycle(ke.isRight() ? 1 : -1);
        } else if (ke.isConfirm()) {
            String built = build();
            if (missing() == null) {
                rule = built;
                return Outcome.RULE;
            }
        } else if (field != null) {
            FormHelper.handleTextInput(ke, field.input);
            field.choice = field.choices.indexOf(field.value());
        }
        return Outcome.NONE;
    }

    /** Pastes into the field in focus. */
    void paste(String text) {
        if (inForm && !fields.isEmpty() && text != null) {
            fields.get(focus).input.insert(text.replace('\n', ' ').replace('\r', ' '));
        }
    }

    /** The rule with the values of the fields so far. */
    String build() {
        ChaosRecipes.Recipe recipe = selectedRecipe();
        if (recipe == null) {
            return "";
        }
        Map<String, String> values = new LinkedHashMap<>();
        fields.forEach(f -> values.put(f.placeholder.name(), f.value()));
        return recipe.fill(values);
    }

    /** The first field without a value, or null when the rule is complete. */
    String missing() {
        for (Field f : fields) {
            if (f.value().isEmpty()) {
                return f.placeholder.name();
            }
        }
        return null;
    }

    // ---- rendering ----

    void render(Frame frame, Rect area) {
        int width = Math.min(area.width() - 4, 150);
        int height = Math.min(area.height() - 2, 30);
        Rect popup = DialogHelper.centered(area, width, height);
        frame.renderWidget(Clear.INSTANCE, popup);
        Block outer = Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL).borderStyle(Theme.warning())
                .title(" Add a chaos rule to " + info.name + " ").build();
        frame.renderWidget(outer, popup);
        Rect inner = outer.inner(popup);
        List<Rect> cols = Layout.horizontal()
                .constraints(Constraint.length(Math.min(46, inner.width() / 3)), Constraint.fill())
                .split(inner);
        renderList(frame, cols.get(0));
        renderRight(frame, cols.get(1));
    }

    private void renderList(Frame frame, Rect area) {
        List<Line> lines = new ArrayList<>();
        lines.add(Line.from(Span.styled(" What should go wrong?", Style.EMPTY.bold())));
        String group = null;
        int selectedLine = 0;
        for (int i = 0; i <= recipes.size(); i++) {
            String title;
            if (i < recipes.size()) {
                ChaosRecipes.Recipe r = recipes.get(i);
                if (!r.group().equals(group)) {
                    group = r.group();
                    lines.add(Line.from(Span.styled(" " + group, Theme.muted())));
                }
                title = r.title();
            } else {
                title = TYPE_IT;
            }
            boolean sel = i == selected;
            if (sel) {
                selectedLine = lines.size();
            }
            Style style = sel ? (inForm ? Style.EMPTY.bold() : Theme.selectionBg().bold()) : Style.EMPTY;
            lines.add(Line.from(Span.styled((sel ? " > " : "   ") + title, style)));
        }
        // keep the selected recipe in sight
        int scroll = Math.max(0, selectedLine - (area.height() - 2));
        frame.renderWidget(Paragraph.builder().text(Text.from(lines)).scroll(scroll)
                .block(Block.builder().borders(EnumSet.of(Borders.RIGHT)).borderStyle(Theme.border()).build()).build(), area);
    }

    private void renderRight(Frame frame, Rect area) {
        ChaosRecipes.Recipe recipe = selectedRecipe();
        Rect pad = new Rect(area.x() + 1, area.y(), Math.max(1, area.width() - 2), area.height());
        if (recipe == null) {
            frame.renderWidget(Paragraph.builder().overflow(Overflow.WRAP_WORD).text(Text.from(
                    Line.from(Span.styled(" Type the rule as one line, such as", Style.EMPTY)),
                    Line.from(Span.styled(" endpoint=kafka:orders* error rate=0.1 until=5m", Theme.muted()))))
                    .build(), pad);
            return;
        }
        List<Line> top = new ArrayList<>();
        top.add(Line.from(Span.styled(recipe.title(), Theme.warning().bold())));
        top.add(Line.from(Span.styled(recipe.description() != null ? recipe.description() : "", Theme.muted())));
        top.add(Line.empty());
        int topHeight = 4 + (recipe.description() != null ? recipe.description().length() / Math.max(1, pad.width()) : 0);
        frame.renderWidget(Paragraph.builder().overflow(Overflow.WRAP_WORD).text(Text.from(top)).build(),
                new Rect(pad.x(), pad.y(), pad.width(), Math.min(topHeight, pad.height())));
        int y = pad.y() + topHeight;
        if (!inForm) {
            frame.renderWidget(Paragraph.builder().overflow(Overflow.WRAP_WORD).text(Text.from(
                    Line.from(Span.styled(recipe.rule(), Theme.muted())))).build(),
                    new Rect(pad.x(), y, pad.width(), Math.max(1, pad.bottom() - y)));
            return;
        }
        int labelWidth = 12;
        for (int i = 0; i < fields.size() && y < pad.bottom() - 4; i++, y++) {
            Field f = fields.get(i);
            boolean focused = i == focus;
            frame.renderWidget(Paragraph.builder().text(Text.from(Line.from(Span.styled(
                    String.format("%-" + labelWidth + "s", f.placeholder.name()),
                    focused ? Theme.warning().bold() : Theme.muted())))).build(),
                    new Rect(pad.x(), y, labelWidth, 1));
            String hint = f.choices.isEmpty()
                    ? ""
                    : "  ◂ ▸ " + (f.choice >= 0 ? (f.choice + 1) + " of " + f.choices.size() : f.choices.size() + " to pick");
            int fieldWidth = Math.max(10, pad.width() - labelWidth - hint.length() - 1);
            FormHelper.renderTextField(frame, new Rect(pad.x() + labelWidth, y, fieldWidth, 1), f.input, focused,
                    f.placeholder.example());
            if (!hint.isEmpty()) {
                frame.renderWidget(Paragraph.builder().text(Text.from(Line.from(Span.styled(hint, Theme.muted())))).build(),
                        new Rect(pad.x() + labelWidth + fieldWidth, y, hint.length(), 1));
            }
        }
        Field focusedField = fields.isEmpty() ? null : fields.get(focus);
        List<Line> bottom = new ArrayList<>();
        if (focusedField != null && focusedField.placeholder.description() != null) {
            bottom.add(Line.from(Span.styled(focusedField.placeholder.description(), Theme.muted())));
        }
        bottom.add(Line.empty());
        bottom.add(Line.from(Span.styled(build(), Theme.info())));
        String missing = missing();
        bottom.add(missing == null
                ? Line.from(Span.styled("Enter to add it (asks to confirm)", Theme.success()))
                : Line.from(Span.styled(missing + " needs a value", Theme.warning())));
        int bottomY = y + 1;
        frame.renderWidget(Paragraph.builder().overflow(Overflow.WRAP_WORD).text(Text.from(bottom)).build(),
                new Rect(pad.x(), bottomY, pad.width(), Math.max(1, pad.bottom() - bottomY)));
    }

    /** The keys of the form, for the footer. */
    void renderFooter(List<Span> spans) {
        if (inForm) {
            TuiHelper.hint(spans, "↑↓", "field");
            TuiHelper.hint(spans, "←→", "pick");
            TuiHelper.hint(spans, "Enter", "add");
            TuiHelper.hint(spans, "Esc", "back");
        } else {
            TuiHelper.hint(spans, "↑↓", "choose");
            TuiHelper.hint(spans, "Enter", "fill in");
            TuiHelper.hint(spans, "Esc", "cancel");
        }
    }
}
