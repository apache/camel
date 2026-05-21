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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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
import dev.tamboui.widgets.block.Title;
import dev.tamboui.widgets.list.ListItem;
import dev.tamboui.widgets.list.ListState;
import dev.tamboui.widgets.list.ListWidget;
import dev.tamboui.widgets.list.ScrollMode;
import org.apache.camel.dsl.jbang.core.common.PathUtils;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

import static org.apache.camel.dsl.jbang.core.commands.tui.MonitorContext.hint;
import static org.apache.camel.dsl.jbang.core.commands.tui.MonitorContext.hintLast;

class ClasspathPopup {

    private boolean visible;
    private final ListState listState = new ListState();
    private List<JarEntry> entries;
    private String title;
    private String errorMessage;

    boolean isVisible() {
        return visible;
    }

    void open(MonitorContext ctx, String pid, String integrationName) {
        if (ctx == null || pid == null) {
            errorMessage = "No integration selected";
            return;
        }

        try {
            Path outputFile = ctx.getOutputFile(pid);
            Files.deleteIfExists(outputFile);
            JsonObject action = new JsonObject();
            action.put("action", "jvm");
            PathUtils.writeTextSafely(action.toJson(), ctx.getActionFile(pid));
            JsonObject response = MonitorContext.pollJsonResponse(outputFile, 5000);
            if (response != null) {
                Object cp = response.get("classpath");
                List<String> paths = new ArrayList<>();
                if (cp instanceof JsonArray arr) {
                    for (Object item : arr) {
                        paths.add(String.valueOf(item));
                    }
                } else if (cp instanceof String[] arr) {
                    for (String s : arr) {
                        paths.add(s);
                    }
                }
                if (paths.isEmpty()) {
                    errorMessage = "No classpath information available";
                    return;
                }
                entries = new ArrayList<>();
                for (String path : paths) {
                    entries.add(parseJarEntry(path));
                }
                entries.sort((a, b) -> a.display().compareToIgnoreCase(b.display()));
                title = (integrationName != null ? integrationName : pid) + " - Classpath (" + entries.size() + " JARs)";
                listState.select(0);
                visible = true;
            } else {
                errorMessage = "No response from integration";
            }
        } catch (Exception e) {
            errorMessage = "Error fetching classpath: " + e.getMessage();
        }
    }

    void close() {
        visible = false;
    }

    String consumeError() {
        String msg = errorMessage;
        errorMessage = null;
        return msg;
    }

    boolean handleKeyEvent(KeyEvent ke) {
        if (!visible) {
            return false;
        }
        if (ke.isCancel()) {
            visible = false;
        } else if (ke.isUp()) {
            listState.selectPrevious();
        } else if (ke.isDown()) {
            listState.selectNext(entries != null ? entries.size() : 0);
        } else if (ke.isPageUp() || ke.isKey(KeyCode.PAGE_UP)) {
            for (int i = 0; i < 10; i++) {
                listState.selectPrevious();
            }
        } else if (ke.isPageDown() || ke.isKey(KeyCode.PAGE_DOWN)) {
            if (entries != null) {
                for (int i = 0; i < 10; i++) {
                    listState.selectNext(entries.size());
                }
            }
        }
        return true;
    }

    void render(Frame frame, Rect area) {
        if (entries == null || entries.isEmpty()) {
            return;
        }
        int popupW = Math.min(100, area.width() - 4);
        int popupH = Math.min(entries.size() + 2, Math.min(30, area.height() - 4));
        int x = area.left() + Math.max(0, (area.width() - popupW) / 2);
        int y = area.top() + Math.max(0, (area.height() - popupH) / 2);
        Rect popup = new Rect(x, y, Math.min(popupW, area.width()), Math.min(popupH, area.height()));

        frame.renderWidget(Clear.INSTANCE, popup);

        int contentW = popupW - 4;
        List<ListItem> items = new ArrayList<>();
        for (JarEntry entry : entries) {
            String line = formatEntry(entry, contentW);
            items.add(ListItem.from(line).style(entry.isCamel() ? Style.EMPTY : Style.EMPTY.dim()));
        }

        ListWidget list = ListWidget.builder()
                .items(items.toArray(ListItem[]::new))
                .highlightStyle(Style.EMPTY.fg(Color.WHITE).bold().onBlue())
                .highlightSymbol("")
                .scrollMode(ScrollMode.AUTO_SCROLL)
                .block(Block.builder()
                        .borderType(BorderType.ROUNDED)
                        .title(" " + title + " ")
                        .titleBottom(Title.from(Line.from(
                                Span.styled(" ↑↓", MonitorContext.HINT_KEY_STYLE), Span.raw(" navigate │"),
                                Span.styled(" Esc", MonitorContext.HINT_KEY_STYLE), Span.raw(" back "))))
                        .build())
                .build();
        frame.renderStatefulWidget(list, popup, listState);
    }

    void renderFooter(List<Span> spans) {
        hint(spans, "↑↓", "navigate");
        hintLast(spans, "Esc", "back");
    }

    private String formatEntry(JarEntry entry, int width) {
        if (entry.groupId() != null) {
            String gav = entry.groupId() + ":" + entry.artifactId();
            String ver = entry.version() != null ? entry.version() : "";
            int gavCol = Math.min(60, width - ver.length() - 4);
            return String.format("  %-" + gavCol + "s  %s", TuiHelper.truncate(gav, gavCol), ver);
        }
        return "  " + TuiHelper.truncate(entry.display(), width - 2);
    }

    static JarEntry parseJarEntry(String path) {
        // try to extract Maven GAV from path like: .m2/repository/org/apache/camel/camel-core/4.x/camel-core-4.x.jar
        String normalized = path.replace('\\', '/');
        int repoIdx = normalized.indexOf("/repository/");
        if (repoIdx >= 0) {
            String relative = normalized.substring(repoIdx + "/repository/".length());
            // relative: org/apache/camel/camel-core/4.x.0/camel-core-4.x.0.jar
            int lastSlash = relative.lastIndexOf('/');
            if (lastSlash > 0) {
                String parentPath = relative.substring(0, lastSlash);
                int versionSlash = parentPath.lastIndexOf('/');
                if (versionSlash > 0) {
                    String version = parentPath.substring(versionSlash + 1);
                    String remaining = parentPath.substring(0, versionSlash);
                    int artifactSlash = remaining.lastIndexOf('/');
                    if (artifactSlash > 0) {
                        String artifactId = remaining.substring(artifactSlash + 1);
                        String groupId = remaining.substring(0, artifactSlash).replace('/', '.');
                        return new JarEntry(groupId, artifactId, version, path);
                    }
                }
            }
        }
        // fallback: just use filename
        int slash = normalized.lastIndexOf('/');
        String filename = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        return new JarEntry(null, filename, null, path);
    }

    record JarEntry(String groupId, String artifactId, String version, String fullPath) {
        String display() {
            if (groupId != null) {
                return groupId + ":" + artifactId + ":" + version;
            }
            return fullPath;
        }

        boolean isCamel() {
            return groupId != null && groupId.startsWith("org.apache.camel");
        }
    }
}
