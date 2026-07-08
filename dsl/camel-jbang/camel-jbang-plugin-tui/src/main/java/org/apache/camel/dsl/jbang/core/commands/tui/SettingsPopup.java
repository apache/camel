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
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.Clear;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.input.TextInput;
import dev.tamboui.widgets.input.TextInputState;
import dev.tamboui.widgets.paragraph.Paragraph;

import static org.apache.camel.dsl.jbang.core.commands.tui.TuiHelper.hint;
import static org.apache.camel.dsl.jbang.core.commands.tui.TuiHelper.hintLast;

/**
 * Central settings dialog for the Camel TUI. Exposes user preferences (theme, starting tab, default run-from-folder)
 * backed by {@link TuiSettings}. Loading, mutating and saving go through a single {@code TuiSettings} object so all
 * {@code camel.tui.*} keys are written together. Theme changes are applied live on save; the starting tab is applied on
 * the next TUI launch.
 */
class SettingsPopup {

    private static final int ROW_THEME = 0;
    private static final int ROW_START_TAB = 1;
    private static final int ROW_FOLDER = 2;
    private static final int ROW_COUNT = 3;

    private boolean visible;
    private int selectedRow;

    private TuiSettings settings;
    private int themeIndex;
    private int startTabIndex;
    private TextInputState folderInput;
    private List<String> tabNames = new ArrayList<>();

    private List<TabRegistry.TabEntry> tabEntries;
    private Runnable clearScreen;

    void setTabEntries(List<TabRegistry.TabEntry> tabEntries) {
        this.tabEntries = tabEntries;
    }

    void setClearScreen(Runnable clearScreen) {
        this.clearScreen = clearScreen;
    }

    boolean isVisible() {
        return visible;
    }

    void open() {
        settings = TuiSettings.load();

        List<String> themeIds = ThemeMode.ids();
        String currentTheme = settings.getThemeId() != null ? settings.getThemeId() : Theme.mode();
        themeIndex = Math.max(0, themeIds.indexOf(currentTheme));

        tabNames = new ArrayList<>();
        if (tabEntries != null) {
            for (TabRegistry.TabEntry entry : tabEntries) {
                tabNames.add(entry.name());
            }
        }
        String currentStartTab = settings.getStartTab() != null ? settings.getStartTab() : "Overview";
        int idx = tabNames.indexOf(currentStartTab);
        startTabIndex = idx >= 0 ? idx : 0;

        folderInput = new TextInputState(settings.getDefaultFolder() != null ? settings.getDefaultFolder() : "");
        selectedRow = ROW_THEME;
        visible = true;
    }

    void close() {
        visible = false;
    }

    boolean handleKeyEvent(KeyEvent ke) {
        if (!visible) {
            return false;
        }
        if (ke.isCancel()) {
            visible = false;
            return true;
        }
        if (ke.isConfirm()) {
            save();
            return true;
        }
        if (ke.isUp() || ke.isFocusPrevious()) {
            selectedRow = (selectedRow - 1 + ROW_COUNT) % ROW_COUNT;
            return true;
        }
        if (ke.isDown() || ke.isFocusNext()) {
            selectedRow = (selectedRow + 1) % ROW_COUNT;
            return true;
        }
        if (selectedRow == ROW_THEME) {
            int count = ThemeMode.ids().size();
            if (ke.isChar(' ') || ke.isRight()) {
                themeIndex = (themeIndex + 1) % count;
            } else if (ke.isLeft()) {
                themeIndex = (themeIndex - 1 + count) % count;
            }
            return true;
        }
        if (selectedRow == ROW_START_TAB) {
            if (!tabNames.isEmpty()) {
                if (ke.isChar(' ') || ke.isRight()) {
                    startTabIndex = (startTabIndex + 1) % tabNames.size();
                } else if (ke.isLeft()) {
                    startTabIndex = (startTabIndex - 1 + tabNames.size()) % tabNames.size();
                }
            }
            return true;
        }
        if (selectedRow == ROW_FOLDER) {
            handleTextInput(ke, folderInput);
            return true;
        }
        return true;
    }

    private void save() {
        settings.setThemeId(ThemeMode.ids().get(themeIndex));
        if (!tabNames.isEmpty()) {
            settings.setStartTab(tabNames.get(startTabIndex));
        }
        settings.setDefaultFolder(stripControlChars(folderInput.text().trim()));
        settings.save();
        Theme.applyStartupMode(settings.getThemeId());
        if (clearScreen != null) {
            clearScreen.run();
        }
        visible = false;
    }

    void render(Frame frame, Rect area) {
        int popupW = Math.min(64, area.width() - 4);
        int popupH = 2 + ROW_COUNT;
        int x = area.left() + Math.max(0, (area.width() - popupW) / 2);
        int y = area.top() + Math.max(0, (area.height() - popupH) / 4);
        Rect popup = new Rect(x, y, Math.min(popupW, area.width()), Math.min(popupH, area.height()));

        frame.renderWidget(Clear.INSTANCE, popup);

        Block block = Block.builder()
                .borderType(BorderType.ROUNDED).borders(Borders.ALL)
                .title(" Settings ")
                .build();
        frame.renderWidget(block, popup);

        int innerX = popup.left() + 2;
        int innerW = popup.width() - 4;
        int labelW = 16;
        int fieldW = innerW - labelW;
        int rowY = popup.top() + 1;

        renderLabel(frame, innerX, rowY, labelW, "Theme:", selectedRow == ROW_THEME);
        renderCycler(frame, innerX + labelW, rowY, fieldW, ThemeMode.ids(), themeIndex, selectedRow == ROW_THEME);
        rowY++;

        renderLabel(frame, innerX, rowY, labelW, "Starting Tab:", selectedRow == ROW_START_TAB);
        renderValue(frame, innerX + labelW, rowY, fieldW, currentTabLabel(), selectedRow == ROW_START_TAB);
        rowY++;

        renderLabel(frame, innerX, rowY, labelW, "Default Folder:", selectedRow == ROW_FOLDER);
        renderFolder(frame, innerX + labelW, rowY, fieldW, selectedRow == ROW_FOLDER);
    }

    void renderFooter(List<Span> spans) {
        hint(spans, TuiIcons.HINT_SCROLL, "navigate");
        if (selectedRow == ROW_THEME || selectedRow == ROW_START_TAB) {
            hint(spans, "Space", "cycle");
        }
        hint(spans, "Enter", "save");
        hintLast(spans, "Esc", "cancel");
    }

    // ---- Helpers ----

    private String currentTabLabel() {
        if (tabEntries == null || tabEntries.isEmpty()) {
            return "Overview";
        }
        TabRegistry.TabEntry entry = tabEntries.get(Math.min(startTabIndex, tabEntries.size() - 1));
        return entry.icon() + " " + entry.name();
    }

    private void handleTextInput(KeyEvent ke, TextInputState active) {
        if (ke.isDeleteBackward()) {
            active.deleteBackward();
        } else if (ke.isDeleteForward()) {
            active.deleteForward();
        } else if (ke.isLeft()) {
            active.moveCursorLeft();
        } else if (ke.isRight()) {
            active.moveCursorRight();
        } else if (ke.isHome()) {
            active.moveCursorToStart();
        } else if (ke.isEnd()) {
            active.moveCursorToEnd();
        } else if (ke.code() == KeyCode.CHAR) {
            char ch = ke.string().charAt(0);
            if (ch >= 0x20 && ch != 0x7F) {
                active.insert(ch);
            }
        }
    }

    private static String stripControlChars(String s) {
        if (s == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch >= 0x20 && ch != 0x7F) {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    private void renderLabel(Frame frame, int x, int y, int w, String label, boolean selected) {
        Style style = selected ? Style.EMPTY.bold() : Style.EMPTY.dim();
        frame.renderWidget(Paragraph.from(Line.from(Span.styled(label, style))), new Rect(x, y, w, 1));
    }

    private void renderCycler(Frame frame, int x, int y, int w, List<String> labels, int active, boolean selected) {
        List<Span> spans = new ArrayList<>();
        for (int i = 0; i < labels.size(); i++) {
            if (i > 0) {
                spans.add(Span.styled(" ", Style.EMPTY));
            }
            if (i == active) {
                spans.add(Span.styled("[" + labels.get(i) + "]", selected ? Style.EMPTY.bold() : Style.EMPTY));
            } else {
                spans.add(Span.styled(" " + labels.get(i) + " ", Style.EMPTY.dim()));
            }
        }
        frame.renderWidget(Paragraph.from(Line.from(spans)), new Rect(x, y, w, 1));
    }

    private void renderValue(Frame frame, int x, int y, int w, String text, boolean selected) {
        Style style = selected ? Style.EMPTY.bold() : Style.EMPTY;
        frame.renderWidget(Paragraph.from(Line.from(Span.styled("[" + text + "]", style))), new Rect(x, y, w, 1));
    }

    private void renderFolder(Frame frame, int x, int y, int w, boolean active) {
        Rect area = new Rect(x, y, w, 1);
        if (active) {
            TextInput textInput = TextInput.builder()
                    .cursorStyle(Style.EMPTY.reversed())
                    .placeholder("/path/to/folder")
                    .build();
            frame.renderStatefulWidget(textInput, area, folderInput);
        } else {
            String text = folderInput.text();
            Style style = text.isEmpty() ? Style.EMPTY.dim() : Style.EMPTY;
            frame.renderWidget(Paragraph.from(Line.from(
                    Span.styled(text.isEmpty() ? "(none)" : text, style))), area);
        }
    }

    // ---- Test accessors ----

    int selectedRow() {
        return selectedRow;
    }

    String selectedThemeId() {
        return ThemeMode.ids().get(themeIndex);
    }

    String selectedStartTab() {
        return tabNames.isEmpty() ? null : tabNames.get(startTabIndex);
    }

    String folderText() {
        return folderInput != null ? folderInput.text() : "";
    }
}
