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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
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

class FilesBrowser {

    record FileEntry(String emoji, String name, long size, String path) {
    }

    private static final String[] CAMEL_YAML_MARKERS = {
            "- from:", "- route:",
            "- routeTemplate:", "- route-template:",
            "- templatedRoute:", "- templated-route:",
            "- routeConfiguration:", "- route-configuration:",
            "- rest:", "- beans:"
    };

    private static final String[] CAMEL_XML_MARKERS = {
            "<route", "<routes", "<routeTemplate", "<routeTemplates",
            "<templatedRoute", "<templatedRoutes",
            "<rest", "<rests", "<routeConfiguration",
            "<beans", "<blueprint", "<camel"
    };

    private boolean visible;
    private String title;
    private final ListState listState = new ListState();
    private List<FileEntry> entries = Collections.emptyList();
    private final SourceViewer sourceViewer = new SourceViewer();

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
        entries = Collections.emptyList();
        sourceViewer.reset();
    }

    void open(IntegrationInfo info) {
        Path dir = resolveSourceDirectory(info);
        if (dir == null || !Files.isDirectory(dir)) {
            return;
        }
        List<FileEntry> found = new ArrayList<>();
        try (var stream = Files.list(dir)) {
            stream.filter(Files::isRegularFile)
                    .limit(99)
                    .forEach(p -> {
                        String name = p.getFileName().toString();
                        String emoji = fileEmoji(p);
                        long size = 0;
                        try {
                            size = Files.size(p);
                        } catch (IOException e) {
                            // ignore
                        }
                        found.add(new FileEntry(emoji, name, size, p.toString()));
                    });
        } catch (IOException e) {
            return;
        }
        if (found.isEmpty()) {
            return;
        }
        found.sort(Comparator.comparing(FileEntry::name, String.CASE_INSENSITIVE_ORDER));
        entries = found;
        title = info.name != null ? info.name : "?";
        listState.select(0);
        visible = true;
        sourceViewer.reset();
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
            if (ke.isConfirm()) {
                Integer sel = listState.selected();
                if (sel != null && sel < entries.size()) {
                    FileEntry entry = entries.get(sel);
                    sourceViewer.loadFile(Path.of(entry.path()));
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

        int nameWidth = entries.stream().mapToInt(e -> e.name().length()).max().orElse(10);
        int sizeWidth = entries.stream().mapToInt(e -> formatFileSize(e.size()).length()).max().orElse(4);
        int itemWidth = 4 + nameWidth + 2 + sizeWidth + 2;
        int popupW = Math.min(area.width() - 4, Math.max(30, itemWidth + 4));
        int popupH = Math.min(area.height() - 4, entries.size() + 2);

        int x = area.left() + Math.max(0, (area.width() - popupW) / 2);
        int y = area.top() + 2;
        Rect popup = new Rect(x, y, Math.min(popupW, area.width()), Math.min(popupH, area.height() - 2));

        frame.renderWidget(Clear.INSTANCE, popup);

        ListItem[] items = new ListItem[entries.size()];
        for (int i = 0; i < entries.size(); i++) {
            FileEntry entry = entries.get(i);
            String sizeStr = formatFileSize(entry.size());
            String label = String.format("  %s %-" + nameWidth + "s  %s", entry.emoji(), entry.name(), sizeStr);
            items[i] = ListItem.from(label);
        }

        ListWidget list = ListWidget.builder()
                .items(items)
                .highlightStyle(Style.EMPTY.fg(Color.WHITE).bold().onBlue())
                .highlightSymbol("")
                .scrollMode(ScrollMode.NONE)
                .block(Block.builder()
                        .borderType(BorderType.ROUNDED).borders(Borders.ALL)
                        .title(Title.from(Line
                                .from(Span.styled(" Files: " + title + " ", Style.EMPTY.fg(Color.YELLOW).bold()))))
                        .build())
                .build();
        frame.renderStatefulWidget(list, popup, listState);
    }

    void renderFooter(List<Span> spans) {
        if (sourceViewer.isVisible()) {
            sourceViewer.renderFooter(spans);
        } else {
            MonitorContext.hint(spans, "Up/Down", "navigate");
            MonitorContext.hint(spans, "Enter", "open");
            MonitorContext.hint(spans, "Esc", "close");
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
        String name = path.getFileName().toString();
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".kamelet.yaml") || lower.endsWith(".kamelet.yml")) {
            return "camel";
        }
        if (lower.endsWith(".yaml") || lower.endsWith(".yml")) {
            return isCamelYaml(path) ? "camel" : "other";
        }
        if (lower.endsWith(".xml")) {
            return isCamelXml(path) ? "camel" : "other";
        }
        if (lower.endsWith(".java")) {
            return isCamelJava(path) ? "camel" : "java";
        }
        if (lower.endsWith(".properties") || lower.endsWith(".cfg")) {
            return "config";
        }
        if (lower.startsWith("readme")) {
            return "readme";
        }
        return "other";
    }

    private static String fileEmoji(Path path) {
        String name = path.getFileName().toString();
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".kamelet.yaml") || lower.endsWith(".kamelet.yml")) {
            return "🐪";
        }
        if (lower.endsWith(".yaml") || lower.endsWith(".yml")) {
            return isCamelYaml(path) ? "🐪" : "📋";
        }
        if (lower.endsWith(".xml")) {
            return isCamelXml(path) ? "🐪" : "📋";
        }
        if (lower.endsWith(".java")) {
            return isCamelJava(path) ? "🐪" : "☕";
        }
        if (lower.endsWith(".properties") || lower.endsWith(".cfg")) {
            return "📄";
        }
        if (lower.startsWith("readme")) {
            return "📖";
        }
        return "📋";
    }

    private static boolean isCamelYaml(Path path) {
        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            for (String marker : CAMEL_YAML_MARKERS) {
                if (content.contains(marker)) {
                    return true;
                }
            }
        } catch (IOException e) {
            // ignore
        }
        return false;
    }

    private static boolean isCamelXml(Path path) {
        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            for (String marker : CAMEL_XML_MARKERS) {
                if (content.contains(marker)) {
                    return true;
                }
            }
        } catch (IOException e) {
            // ignore
        }
        return false;
    }

    private static boolean isCamelJava(Path path) {
        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            return content.contains("RouteBuilder")
                    || content.contains("EndpointRouteBuilder");
        } catch (IOException e) {
            // ignore
        }
        return false;
    }
}
