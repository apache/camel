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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import dev.tamboui.layout.Rect;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.CharWidth;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.MouseEvent;
import dev.tamboui.tui.event.MouseEventKind;
import dev.tamboui.widgets.Clear;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.block.Title;
import dev.tamboui.widgets.list.ListItem;
import dev.tamboui.widgets.list.ListState;
import dev.tamboui.widgets.list.ListWidget;
import dev.tamboui.widgets.list.ScrollMode;

class FilesBrowser {

    record FileEntry(String emoji, String name, long size, String path, boolean directory) {
    }

    private boolean visible;
    private String title;
    private Path rootDir;
    private Path currentDir;
    private final ListState listState = new ListState();
    private List<FileEntry> entries = Collections.emptyList();
    private final SourceViewer sourceViewer = new SourceViewer();
    private Rect lastPopup;

    boolean isVisible() {
        return visible;
    }

    boolean isSourceViewerPasteActive() {
        return sourceViewer.isSearchInputActive();
    }

    void handlePaste(String text) {
        sourceViewer.handlePaste(text);
    }

    void reset() {
        visible = false;
        rootDir = null;
        currentDir = null;
        entries = Collections.emptyList();
        sourceViewer.reset();
    }

    void open(IntegrationInfo info) {
        Path dir = resolveSourceDirectory(info);
        if (dir == null || !Files.isDirectory(dir)) {
            return;
        }
        rootDir = dir;
        currentDir = dir;
        title = info.name != null ? info.name : "?";
        sourceViewer.reset();
        if (!loadDirectory(dir)) {
            return;
        }
        visible = true;
    }

    private boolean loadDirectory(Path dir) {
        List<FileEntry> dirs = new ArrayList<>();
        List<FileEntry> files = new ArrayList<>();
        try (var stream = Files.list(dir)) {
            stream.limit(200)
                    .forEach(p -> {
                        String name = p.getFileName().toString();
                        if (Files.isDirectory(p) && !name.startsWith(".")) {
                            dirs.add(new FileEntry(TuiIcons.FOLDER, name, -1, p.toString(), true));
                        } else if (Files.isRegularFile(p)) {
                            String emoji = TuiHelper.fileEmoji(p);
                            long size = 0;
                            try {
                                size = Files.size(p);
                            } catch (IOException e) {
                                // ignore
                            }
                            files.add(new FileEntry(emoji, name, size, p.toString(), false));
                        }
                    });
        } catch (IOException e) {
            return false;
        }
        dirs.sort(Comparator.comparing(FileEntry::name, String.CASE_INSENSITIVE_ORDER));
        files.sort(Comparator.comparing(FileEntry::name, String.CASE_INSENSITIVE_ORDER));

        List<FileEntry> found = new ArrayList<>();
        if (!dir.equals(rootDir)) {
            found.add(new FileEntry(TuiIcons.FOLDER, "..", -1, dir.getParent().toString(), true));
        }
        found.addAll(dirs);
        found.addAll(files);

        if (found.isEmpty()) {
            return false;
        }
        entries = found;
        listState.select(0);
        currentDir = dir;
        return true;
    }

    boolean handleMouseEvent(MouseEvent me) {
        if (sourceViewer.isVisible()) {
            return sourceViewer.handleMouseEvent(me);
        }
        if (me.kind() == MouseEventKind.SCROLL_UP) {
            listState.selectPrevious();
            return true;
        }
        if (me.kind() == MouseEventKind.SCROLL_DOWN) {
            listState.selectNext(entries.size());
            return true;
        }
        if (me.isClick() && lastPopup != null && lastPopup.contains(me.x(), me.y())) {
            int innerTop = lastPopup.top() + 1;
            int clicked = listState.offset() + (me.y() - innerTop);
            if (clicked >= 0 && clicked < entries.size()) {
                listState.select(clicked);
            }
            return true;
        }
        return true;
    }

    boolean handleKeyEvent(KeyEvent ke) {
        if (sourceViewer.isVisible()) {
            if (sourceViewer.handleKeyEvent(ke)) {
                return true;
            }
        }
        if (ke.isCancel()) {
            if (sourceViewer.isVisible()) {
                sourceViewer.hide();
            } else {
                visible = false;
            }
            return true;
        }
        if (!sourceViewer.isVisible()) {
            if (ke.isUp()) {
                listState.selectPrevious();
                return true;
            }
            if (ke.isDown()) {
                listState.selectNext(entries.size());
                return true;
            }
            if (ke.isPageUp()) {
                for (int i = 0; i < 20; i++) {
                    listState.selectPrevious();
                }
                return true;
            }
            if (ke.isPageDown()) {
                for (int i = 0; i < 20; i++) {
                    listState.selectNext(entries.size());
                }
                return true;
            }
            if (ke.isHome()) {
                listState.select(0);
                return true;
            }
            if (ke.isEnd()) {
                listState.select(entries.size() - 1);
                return true;
            }
            if (ke.isDeleteBackward()) {
                if (currentDir != null && !currentDir.equals(rootDir)) {
                    loadDirectory(currentDir.getParent());
                }
                return true;
            }
            if (ke.isConfirm()) {
                Integer sel = listState.selected();
                if (sel != null && sel < entries.size()) {
                    FileEntry entry = entries.get(sel);
                    if (entry.directory()) {
                        loadDirectory(Path.of(entry.path()));
                    } else {
                        sourceViewer.loadFile(Path.of(entry.path()));
                    }
                }
                return true;
            }
        }
        return true;
    }

    void render(Frame frame, Rect area) {
        if (sourceViewer.isVisible()) {
            frame.renderWidget(Clear.INSTANCE, area);
            sourceViewer.render(frame, area);
            return;
        }
        if (entries.isEmpty()) {
            visible = false;
            return;
        }

        String popupTitle = " Files: " + title;
        if (rootDir != null && currentDir != null && !currentDir.equals(rootDir)) {
            popupTitle += "/" + rootDir.relativize(currentDir);
        }
        popupTitle += " ";

        int nameWidth = entries.stream().mapToInt(e -> e.name().length()).max().orElse(10);
        int sizeWidth = entries.stream()
                .filter(e -> !e.directory())
                .mapToInt(e -> formatFileSize(e.size()).length()).max().orElse(4);
        int itemWidth = 4 + nameWidth + 2 + sizeWidth + 2;
        int titleWidth = popupTitle.length() + 4;
        int popupW = Math.min(area.width() - 4, Math.max(50, Math.max(itemWidth + 4, titleWidth)));
        int popupH = Math.min(area.height() - 4, Math.max(12, entries.size() + 2));

        int x = area.left() + Math.max(0, (area.width() - popupW) / 2);
        int y = area.top() + 2;
        Rect popup = new Rect(x, y, Math.min(popupW, area.width()), Math.min(popupH, area.height() - 2));

        lastPopup = popup;
        frame.renderWidget(Clear.INSTANCE, popup);

        int innerWidth = popupW - 2;
        ListItem[] items = new ListItem[entries.size()];
        for (int i = 0; i < entries.size(); i++) {
            FileEntry entry = entries.get(i);
            if (entry.directory()) {
                String label = String.format("  %s %-" + nameWidth + "s", entry.emoji(), entry.name());
                boolean dimDir = entry.name().startsWith(".") || "target".equals(entry.name());
                Style dirStyle = dimDir ? Style.EMPTY.fg(Theme.accent()).dim() : Style.EMPTY.fg(Theme.accent());
                items[i] = ListItem.from(Line.from(Span.styled(label, dirStyle)));
            } else {
                String sizeStr = formatFileSize(entry.size());
                String nameLabel = String.format("  %s %s", entry.emoji(), entry.name());
                int gap = Math.max(1, innerWidth - CharWidth.of(nameLabel) - sizeStr.length() - 1);
                String padding = " ".repeat(gap);
                items[i] = ListItem.from(Line.from(
                        Span.raw(nameLabel + padding),
                        Span.styled(sizeStr + " ", Style.EMPTY.dim())));
            }
        }

        ListWidget list = ListWidget.builder()
                .items(items)
                .highlightStyle(Theme.selectionBg())
                .highlightSymbol("")
                .scrollMode(ScrollMode.AUTO_SCROLL)
                .block(Block.builder()
                        .borderType(BorderType.ROUNDED).borders(Borders.ALL)
                        .title(Title.from(Line
                                .from(Span.styled(popupTitle, Theme.label().bold()))))
                        .build())
                .build();
        frame.renderStatefulWidget(list, popup, listState);
    }

    void renderFooter(List<Span> spans) {
        if (sourceViewer.isVisible()) {
            sourceViewer.renderFooter(spans);
        } else {
            TuiHelper.hint(spans, TuiIcons.HINT_SCROLL, "navigate");
            TuiHelper.hint(spans, "Enter", "open");
            TuiHelper.hint(spans, "Esc", "close");
        }
    }

    static Path resolveSourceDirectory(IntegrationInfo info) {
        for (ConfigurationTab.ConfigProperty cp : info.configProperties) {
            if ("camel.main.routesIncludePattern".equals(cp.key) && cp.value != null) {
                for (String part : cp.value.split(",")) {
                    part = part.trim();
                    if (part.startsWith("file:")) {
                        String filePath = part.substring("file:".length());
                        int q = filePath.indexOf('?');
                        if (q > 0) {
                            filePath = filePath.substring(0, q);
                        }
                        if (filePath.endsWith("/**")) {
                            Path dir = Path.of(filePath.substring(0, filePath.length() - 3));
                            if (Files.isDirectory(dir)) {
                                return dir;
                            }
                        }
                        Path parent = Path.of(filePath).getParent();
                        if (parent != null && Files.isDirectory(parent)) {
                            return parent;
                        }
                    }
                }
            }
        }
        // For exported apps (Quarkus, Spring Boot), MAVEN_PROJECTBASEDIR points to the
        // actual project directory inside .camel-jbang-run, which may differ from info.directory
        for (ConfigurationTab.ConfigProperty cp : info.configProperties) {
            if (("MAVEN_PROJECTBASEDIR".equals(cp.key) || "maven.projectbasedir".equals(cp.key))
                    && cp.value != null && cp.value.contains(".camel-jbang-run")) {
                Path dir = Path.of(cp.value);
                if (Files.isDirectory(dir)) {
                    return dir;
                }
            }
        }
        if (info.directory != null && !info.directory.isEmpty()) {
            return Path.of(info.directory);
        }
        return null;
    }

    static String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        }
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }

    static String fileType(Path path) {
        String emoji = TuiHelper.fileEmoji(path);
        return switch (emoji) {
            case TuiIcons.CAMEL -> "camel";
            case TuiIcons.JAVA -> "java";
            case TuiIcons.DOCUMENT -> "config";
            case TuiIcons.README -> "readme";
            default -> "other";
        };
    }

}
