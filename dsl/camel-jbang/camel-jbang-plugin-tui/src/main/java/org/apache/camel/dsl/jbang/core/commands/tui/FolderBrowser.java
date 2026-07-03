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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;

import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
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
import dev.tamboui.widgets.block.Title;
import dev.tamboui.widgets.list.ListItem;
import dev.tamboui.widgets.list.ListState;
import dev.tamboui.widgets.list.ListWidget;
import dev.tamboui.widgets.list.ScrollMode;

class FolderBrowser {

    record DirEntry(String name, String path) {
    }

    private boolean visible;
    private Path currentDir;
    private final ListState listState = new ListState();
    private final Deque<Integer> offsetStack = new ArrayDeque<>();
    private List<DirEntry> entries = Collections.emptyList();
    private Consumer<String> onSelect;
    private char lastJumpChar;
    private int lastJumpIndex = -1;

    boolean isVisible() {
        return visible;
    }

    void setOnSelect(Consumer<String> onSelect) {
        this.onSelect = onSelect;
    }

    void open(String startPath) {
        Path start = null;
        if (startPath != null && !startPath.isEmpty()) {
            String resolved = startPath;
            if (resolved.startsWith("~")) {
                resolved = System.getProperty("user.home") + resolved.substring(1);
            }
            Path p = Path.of(resolved);
            if (Files.isDirectory(p)) {
                start = p;
            }
        }
        if (start == null) {
            start = Path.of(System.getProperty("user.dir"));
        }
        if (loadDirectory(start)) {
            visible = true;
            offsetStack.clear();
        }
    }

    private boolean loadDirectory(Path dir) {
        return loadDirectory(dir, null);
    }

    private boolean loadDirectory(Path dir, String selectName) {
        List<DirEntry> dirs = new ArrayList<>();
        try (var stream = Files.list(dir)) {
            stream.filter(Files::isDirectory)
                    .filter(p -> !p.getFileName().toString().startsWith("."))
                    .limit(200)
                    .forEach(p -> dirs.add(new DirEntry(p.getFileName().toString(), p.toString())));
        } catch (IOException e) {
            return false;
        }
        dirs.sort(Comparator.comparing(DirEntry::name, String.CASE_INSENSITIVE_ORDER));

        List<DirEntry> found = new ArrayList<>();
        Path parent = dir.getParent();
        if (parent != null) {
            found.add(new DirEntry("..", parent.toString()));
        }
        found.addAll(dirs);

        if (found.isEmpty()) {
            return false;
        }
        entries = found;
        int sel = 0;
        if (selectName != null) {
            for (int i = 0; i < found.size(); i++) {
                if (found.get(i).name().equals(selectName)) {
                    sel = i;
                    break;
                }
            }
        }
        listState.select(sel);
        currentDir = dir;
        lastJumpChar = 0;
        lastJumpIndex = -1;
        return true;
    }

    private void navigateBack() {
        String childName = currentDir.getFileName().toString();
        if (loadDirectory(currentDir.getParent(), childName)) {
            int savedOffset = offsetStack.isEmpty() ? 0 : offsetStack.pop();
            listState.setOffset(savedOffset);
        }
    }

    boolean handleKeyEvent(KeyEvent ke) {
        if (ke.isCancel()) {
            visible = false;
            return true;
        }
        if (ke.isUp()) {
            listState.selectPrevious();
            lastJumpChar = 0;
            lastJumpIndex = -1;
            return true;
        }
        if (ke.isDown()) {
            listState.selectNext(entries.size());
            lastJumpChar = 0;
            lastJumpIndex = -1;
            return true;
        }
        if (ke.isPageUp()) {
            for (int i = 0; i < 20; i++) {
                listState.selectPrevious();
            }
            lastJumpChar = 0;
            lastJumpIndex = -1;
            return true;
        }
        if (ke.isPageDown()) {
            for (int i = 0; i < 20; i++) {
                listState.selectNext(entries.size());
            }
            lastJumpChar = 0;
            lastJumpIndex = -1;
            return true;
        }
        if (ke.isHome()) {
            listState.select(0);
            lastJumpChar = 0;
            lastJumpIndex = -1;
            return true;
        }
        if (ke.isEnd()) {
            listState.select(entries.size() - 1);
            lastJumpChar = 0;
            lastJumpIndex = -1;
            return true;
        }
        if (ke.isDeleteBackward()) {
            if (currentDir != null && currentDir.getParent() != null) {
                navigateBack();
            }
            return true;
        }
        if (ke.isConfirm()) {
            Integer sel = listState.selected();
            if (sel != null && sel < entries.size()) {
                DirEntry entry = entries.get(sel);
                if ("..".equals(entry.name()) && currentDir != null) {
                    navigateBack();
                } else {
                    offsetStack.push(listState.offset());
                    loadDirectory(Path.of(entry.path()));
                }
            }
            return true;
        }
        if (ke.isKey(KeyCode.TAB)) {
            visible = false;
            if (onSelect != null && currentDir != null) {
                onSelect.accept(currentDir.toString());
            }
            return true;
        }
        if (ke.code() == KeyCode.CHAR) {
            boolean reverse = Character.isUpperCase(ke.string().charAt(0));
            char c = Character.toLowerCase(ke.string().charAt(0));
            int found = -1;
            if (reverse) {
                int startFrom = c == lastJumpChar && lastJumpIndex >= 0 ? lastJumpIndex - 1 : entries.size() - 1;
                for (int i = startFrom; i >= 0; i--) {
                    String name = entries.get(i).name();
                    if (!name.isEmpty() && Character.toLowerCase(name.charAt(0)) == c) {
                        found = i;
                        break;
                    }
                }
                if (found < 0) {
                    for (int i = entries.size() - 1; i > startFrom; i--) {
                        String name = entries.get(i).name();
                        if (!name.isEmpty() && Character.toLowerCase(name.charAt(0)) == c) {
                            found = i;
                            break;
                        }
                    }
                }
            } else {
                int startFrom = c == lastJumpChar && lastJumpIndex >= 0 ? lastJumpIndex + 1 : 0;
                for (int i = startFrom; i < entries.size(); i++) {
                    String name = entries.get(i).name();
                    if (!name.isEmpty() && Character.toLowerCase(name.charAt(0)) == c) {
                        found = i;
                        break;
                    }
                }
                if (found < 0) {
                    for (int i = 0; i < startFrom && i < entries.size(); i++) {
                        String name = entries.get(i).name();
                        if (!name.isEmpty() && Character.toLowerCase(name.charAt(0)) == c) {
                            found = i;
                            break;
                        }
                    }
                }
            }
            if (found >= 0) {
                listState.select(found);
                lastJumpChar = c;
                lastJumpIndex = found;
            }
            return true;
        }
        return true;
    }

    void render(Frame frame, Rect area) {
        if (entries.isEmpty()) {
            visible = false;
            return;
        }

        String dirLabel = currentDir != null ? currentDir.toString() : "";
        String popupTitle = " 📂 " + dirLabel + " ";

        int nameWidth = entries.stream().mapToInt(e -> e.name().length()).max().orElse(10);
        int itemWidth = 6 + nameWidth;
        int titleWidth = popupTitle.length() + 4;
        int popupW = Math.min(area.width() - 4, Math.max(50, Math.max(itemWidth + 4, titleWidth)));
        int popupH = Math.min(area.height() - 4, Math.max(12, entries.size() + 2));

        int x = area.left() + Math.max(0, (area.width() - popupW) / 2);
        int y = area.top() + 2;
        Rect popup = new Rect(x, y, Math.min(popupW, area.width()), Math.min(popupH, area.height() - 2));

        frame.renderWidget(Clear.INSTANCE, popup);

        ListItem[] items = new ListItem[entries.size()];
        for (int i = 0; i < entries.size(); i++) {
            DirEntry entry = entries.get(i);
            String label = "  📁 " + entry.name();
            items[i] = ListItem.from(Line.from(Span.styled(label, Style.EMPTY.fg(Color.CYAN))));
        }

        ListWidget list = ListWidget.builder()
                .items(items)
                .highlightStyle(Theme.selectionBg())
                .highlightSymbol("")
                .scrollMode(ScrollMode.AUTO_SCROLL)
                .block(Block.builder()
                        .borderType(BorderType.ROUNDED).borders(Borders.ALL)
                        .title(Title.from(Line
                                .from(Span.styled(popupTitle, Style.EMPTY.fg(Color.YELLOW).bold()))))
                        .build())
                .build();
        frame.renderStatefulWidget(list, popup, listState);
    }

    void renderFooter(List<Span> spans) {
        TuiHelper.hint(spans, "↑↓", "navigate");
        TuiHelper.hint(spans, "Enter", "open");
        TuiHelper.hint(spans, "Tab", "select");
        TuiHelper.hintLast(spans, "Esc", "close");
    }
}
