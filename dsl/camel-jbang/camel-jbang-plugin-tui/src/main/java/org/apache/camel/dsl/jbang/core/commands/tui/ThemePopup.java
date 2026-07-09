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
import java.util.List;

import dev.tamboui.layout.Rect;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Span;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.MouseEvent;
import dev.tamboui.tui.event.MouseEventKind;
import dev.tamboui.widgets.Clear;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.list.ListItem;
import dev.tamboui.widgets.list.ListState;
import dev.tamboui.widgets.list.ListWidget;
import dev.tamboui.widgets.list.ScrollMode;

import static org.apache.camel.dsl.jbang.core.commands.tui.TuiHelper.hint;
import static org.apache.camel.dsl.jbang.core.commands.tui.TuiHelper.hintLast;

/**
 * Self-contained theme picker popup with live preview. Shows dark and light themes in separate sections; scrolling
 * applies the theme instantly, Enter confirms and persists, Esc reverts to the original theme.
 */
class ThemePopup {

    private boolean visible;
    private final ListState listState = new ListState();
    private Runnable themeRefreshAction;
    private Rect popupRect;

    void setThemeRefreshAction(Runnable action) {
        this.themeRefreshAction = action;
    }

    void open() {
        visible = true;
        ThemeMode current = ThemeMode.parseOrDefault(Theme.mode());
        listState.select(visualIndexForTheme(current));
    }

    void close() {
        if (visible) {
            Theme.revertPreview();
            refreshTheme();
        }
        visible = false;
    }

    boolean isVisible() {
        return visible;
    }

    boolean handleKeyEvent(KeyEvent ke) {
        if (!visible) {
            return false;
        }
        if (ke.isCancel()) {
            Theme.revertPreview();
            refreshTheme();
            visible = false;
        } else if (ke.isUp()) {
            navigate(-1);
            applyThemePreview();
        } else if (ke.isDown()) {
            navigate(1);
            applyThemePreview();
        } else if (ke.isHome() || ke.isKey(KeyCode.HOME)) {
            listState.select(2);
            applyThemePreview();
        } else if (ke.isEnd() || ke.isKey(KeyCode.END)) {
            listState.select(itemCount() - 1);
            applyThemePreview();
        } else if (ke.isConfirm()) {
            Integer sel = listState.selected();
            if (sel != null && sel == 0) {
                Theme.revertPreview();
                refreshTheme();
                visible = false;
            } else {
                ThemeMode mode = themeAtVisualIndex(sel);
                if (mode != null) {
                    Theme.confirmPreview();
                    refreshTheme();
                    visible = false;
                }
            }
        }
        return true;
    }

    boolean handleMouseEvent(MouseEvent me) {
        if (!visible) {
            return false;
        }
        if (me.kind() == MouseEventKind.SCROLL_UP) {
            handleKeyEvent(KeyEvent.ofKey(KeyCode.UP));
            return true;
        }
        if (me.kind() == MouseEventKind.SCROLL_DOWN) {
            handleKeyEvent(KeyEvent.ofKey(KeyCode.DOWN));
            return true;
        }
        if (me.isClick()) {
            if (popupRect != null && popupRect.contains(me.x(), me.y())) {
                int idx = TuiHelper.listItemAt(popupRect, listState.offset(), itemCount(), me.x(), me.y());
                if (idx >= 0 && !isDivider(idx)) {
                    listState.select(idx);
                    handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER));
                }
                return true;
            }
            handleKeyEvent(KeyEvent.ofKey(KeyCode.ESCAPE));
            return true;
        }
        return true;
    }

    void render(Frame frame, Rect area) {
        if (!visible) {
            return;
        }
        int count = itemCount();
        int popupW = 40;
        int popupH = 2 + count;
        int x = area.left() + Math.max(0, (area.width() - popupW) / 2);
        int y = area.top() + 2;
        Rect popup = new Rect(x, y, Math.min(popupW, area.width()), Math.min(popupH, area.height() - 2));
        this.popupRect = popup;

        frame.renderWidget(Clear.INSTANCE, popup);
        String persisted = Theme.persistedMode();

        List<ListItem> items = new ArrayList<>();
        items.add(ListItem.from("  .."));
        items.add(ListItem.from("  ──── Dark ────────────────────").style(Style.EMPTY.dim()));
        for (ThemeMode m : ThemeMode.darkThemes()) {
            String check = m.id().equals(persisted) ? "  ✔" : "";
            items.add(ListItem.from("  " + m.label() + check));
        }
        items.add(ListItem.from("  ──── Light ───────────────────").style(Style.EMPTY.dim()));
        for (ThemeMode m : ThemeMode.lightThemes()) {
            String check = m.id().equals(persisted) ? "  ✔" : "";
            items.add(ListItem.from("  " + m.label() + check));
        }
        ListWidget list = ListWidget.builder()
                .items(items.toArray(ListItem[]::new))
                .highlightStyle(Theme.selectionBg())
                .highlightSymbol("")
                .scrollMode(ScrollMode.NONE)
                .block(Block.builder()
                        .borderType(BorderType.ROUNDED).borders(Borders.ALL)
                        .title(" Themes ")
                        .build())
                .build();
        frame.renderStatefulWidget(list, popup, listState);
    }

    void renderFooter(List<Span> spans) {
        hint(spans, TuiIcons.HINT_SCROLL, "preview");
        hint(spans, "Enter", "apply");
        hintLast(spans, "Esc", "back");
    }

    List<String> getLabels() {
        List<String> labels = new ArrayList<>();
        labels.add("..");
        labels.add("── Dark ──");
        for (ThemeMode m : ThemeMode.darkThemes()) {
            labels.add(m.label());
        }
        labels.add("── Light ──");
        for (ThemeMode m : ThemeMode.lightThemes()) {
            labels.add(m.label());
        }
        return labels;
    }

    SelectionContext getSelectionContext() {
        List<String> items = getLabels();
        Integer sel = listState.selected();
        return new SelectionContext("popup", items, sel != null ? sel : -1, itemCount(), "Themes");
    }

    // ---- Private helpers ----

    private int itemCount() {
        // BACK + dark-divider + darkThemes + light-divider + lightThemes
        return 2 + ThemeMode.darkThemes().size() + 1 + ThemeMode.lightThemes().size();
    }

    private boolean isDivider(int index) {
        int darkCount = ThemeMode.darkThemes().size();
        return index == 1 || index == 2 + darkCount;
    }

    private void navigate(int direction) {
        int total = itemCount();
        Integer current = listState.selected();
        int next = (current != null ? current : 0) + direction;
        next = Math.max(0, Math.min(next, total - 1));
        while (isDivider(next) && next > 0 && next < total - 1) {
            next += direction;
        }
        next = Math.max(0, Math.min(next, total - 1));
        if (!isDivider(next)) {
            listState.select(next);
        }
    }

    private void applyThemePreview() {
        ThemeMode mode = themeAtVisualIndex(listState.selected());
        if (mode != null) {
            Theme.preview(mode.id());
            refreshTheme();
        }
    }

    private ThemeMode themeAtVisualIndex(Integer sel) {
        if (sel == null) {
            return null;
        }
        List<ThemeMode> dark = ThemeMode.darkThemes();
        List<ThemeMode> light = ThemeMode.lightThemes();
        int darkCount = dark.size();
        if (sel >= 2 && sel < 2 + darkCount) {
            return dark.get(sel - 2);
        }
        int lightStart = 2 + darkCount + 1;
        if (sel >= lightStart && sel < lightStart + light.size()) {
            return light.get(sel - lightStart);
        }
        return null;
    }

    private int visualIndexForTheme(ThemeMode target) {
        List<ThemeMode> dark = ThemeMode.darkThemes();
        int darkIdx = dark.indexOf(target);
        if (darkIdx >= 0) {
            return 2 + darkIdx;
        }
        List<ThemeMode> light = ThemeMode.lightThemes();
        int lightIdx = light.indexOf(target);
        if (lightIdx >= 0) {
            return 2 + dark.size() + 1 + lightIdx;
        }
        return 2;
    }

    private void refreshTheme() {
        if (themeRefreshAction != null) {
            themeRefreshAction.run();
        }
    }
}
