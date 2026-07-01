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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.list.ListItem;
import dev.tamboui.widgets.list.ListState;
import dev.tamboui.widgets.list.ListWidget;
import dev.tamboui.widgets.list.ScrollMode;
import dev.tamboui.widgets.paragraph.Paragraph;
import org.apache.camel.dsl.jbang.core.common.PathUtils;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

import static org.apache.camel.dsl.jbang.core.commands.tui.MonitorContext.*;

class ClasspathTab implements MonitorTab {

    private static final Style MATCH_STYLE = Style.EMPTY.fg(Color.YELLOW).bold();

    private final MonitorContext ctx;
    private final ListState listState = new ListState();
    private final FuzzyFilter fuzzyFilter = new FuzzyFilter();
    private final AtomicBoolean loading = new AtomicBoolean(false);

    private List<JarEntry> allEntries = Collections.emptyList();
    private List<FilteredEntry> filteredEntries = Collections.emptyList();
    private String lastPid;
    private String errorMessage;
    private boolean dataLoaded;

    ClasspathTab(MonitorContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void onTabSelected() {
        String pid = ctx.selectedPid;
        if (pid != null && !pid.equals(lastPid)) {
            lastPid = pid;
            allEntries = Collections.emptyList();
            dataLoaded = false;
        }
        if (!dataLoaded) {
            loadClasspath();
        }
    }

    @Override
    public void onIntegrationChanged() {
        allEntries = Collections.emptyList();
        filteredEntries = Collections.emptyList();
        fuzzyFilter.clearFilter();
        lastPid = null;
        errorMessage = null;
        dataLoaded = false;
        loadClasspath();
    }

    @Override
    public boolean handleKeyEvent(KeyEvent ke) {
        if (ke.isDeleteBackward()) {
            if (fuzzyFilter.hasFilter()) {
                fuzzyFilter.deleteChar();
                refilter();
                return true;
            }
        }
        if (ke.isPageUp() || ke.isKey(KeyCode.PAGE_UP)) {
            for (int i = 0; i < 20 && listState.selected() != null && listState.selected() > 0; i++) {
                listState.selectPrevious();
            }
            return true;
        }
        if (ke.isPageDown() || ke.isKey(KeyCode.PAGE_DOWN)) {
            for (int i = 0; i < 20; i++) {
                listState.selectNext(filteredEntries.size());
            }
            return true;
        }
        if (ke.code() == KeyCode.CHAR) {
            fuzzyFilter.appendChar(ke.string().charAt(0));
            refilter();
            return true;
        }
        return false;
    }

    @Override
    public boolean handleEscape() {
        if (fuzzyFilter.hasFilter()) {
            fuzzyFilter.clearFilter();
            refilter();
            return true;
        }
        return false;
    }

    @Override
    public void navigateUp() {
        listState.selectPrevious();
    }

    @Override
    public void navigateDown() {
        listState.selectNext(filteredEntries.size());
    }

    @Override
    public void render(Frame frame, Rect area) {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null) {
            renderNoSelection(frame, area);
            return;
        }

        if (loading.get() && allEntries.isEmpty()) {
            frame.renderWidget(
                    Paragraph.builder()
                            .text(Text.from(Line.from(Span.styled("  Loading classpath...", Style.EMPTY.dim()))))
                            .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                                    .title(" Classpath ").build())
                            .build(),
                    area);
            return;
        }

        if (errorMessage != null && allEntries.isEmpty()) {
            frame.renderWidget(
                    Paragraph.builder()
                            .text(Text.from(Line.from(
                                    Span.styled("  " + errorMessage, Style.EMPTY.fg(Color.LIGHT_RED)))))
                            .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                                    .title(" Classpath ").build())
                            .build(),
                    area);
            return;
        }

        int contentW = Math.max(10, area.width() - 4);
        List<ListItem> items = new ArrayList<>();
        for (FilteredEntry fe : filteredEntries) {
            String displayText = formatEntry(fe.entry(), contentW);
            Style normalStyle = fe.entry().isCamel() ? Style.EMPTY : Style.EMPTY.dim();
            if (fe.matchPositions() != null && fe.matchPositions().length > 0) {
                int[] adjusted = adjustPositions(fe.matchPositions(), fe.entry(), displayText);
                Line line = FuzzyFilter.highlightLine(displayText, adjusted, normalStyle, MATCH_STYLE);
                items.add(ListItem.from(Text.from(line)));
            } else {
                items.add(ListItem.from(displayText).style(normalStyle));
            }
        }

        if (items.isEmpty() && dataLoaded) {
            items.add(ListItem.from("  No classpath entries found").style(Style.EMPTY.dim()));
        }

        String title = " Classpath (" + filteredEntries.size();
        if (fuzzyFilter.hasFilter()) {
            title += "/" + allEntries.size() + ") [" + fuzzyFilter.filter() + "] ";
        } else {
            title += ") ";
        }

        ListWidget list = ListWidget.builder()
                .items(items.toArray(ListItem[]::new))
                .highlightStyle(Style.EMPTY.fg(Color.WHITE).bold().onBlue())
                .highlightSymbol("")
                .scrollMode(ScrollMode.AUTO_SCROLL)
                .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL).title(title).build())
                .build();
        frame.renderStatefulWidget(list, area, listState);
    }

    @Override
    public void renderFooter(List<Span> spans) {
        hint(spans, "Esc", fuzzyFilter.hasFilter() ? "clear" : "back");
        hint(spans, "↑↓", "navigate");
        hintLast(spans, "type", "filter");
    }

    private void loadClasspath() {
        if (ctx.selectedPid == null || ctx.runner == null) {
            return;
        }
        if (!loading.compareAndSet(false, true)) {
            return;
        }

        String pid = ctx.selectedPid;
        ctx.runner.scheduler().execute(() -> {
            try {
                Path outputFile = ctx.getOutputFile(pid);
                PathUtils.deleteFile(outputFile);

                JsonObject action = new JsonObject();
                action.put("action", "jvm");

                Path actionFile = ctx.getActionFile(pid);
                PathUtils.writeTextSafely(action.toJson(), actionFile);

                JsonObject response = pollJsonResponse(outputFile, 5000);
                PathUtils.deleteFile(outputFile);

                if (response == null) {
                    applyResult(Collections.emptyList(), "No response from integration");
                    return;
                }

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
                    applyResult(Collections.emptyList(), "No classpath information available");
                    return;
                }

                List<JarEntry> parsed = new ArrayList<>();
                for (String path : paths) {
                    parsed.add(parseJarEntry(path));
                }
                parsed.sort((a, b) -> a.display().compareToIgnoreCase(b.display()));

                applyResult(parsed, null);
            } catch (Exception e) {
                applyResult(Collections.emptyList(), "Error: " + e.getMessage());
            } finally {
                loading.set(false);
            }
        });
    }

    private void applyResult(List<JarEntry> parsed, String error) {
        if (ctx.runner == null) {
            return;
        }
        ctx.runner.runOnRenderThread(() -> {
            allEntries = parsed;
            errorMessage = error;
            dataLoaded = true;
            refilter();
        });
    }

    private void refilter() {
        List<FilteredEntry> result = new ArrayList<>();
        for (JarEntry entry : allEntries) {
            if (!fuzzyFilter.hasFilter()) {
                result.add(new FilteredEntry(entry, null));
            } else {
                int[] positions = fuzzyFilter.match(entry.display());
                if (positions != null) {
                    result.add(new FilteredEntry(entry, positions));
                }
            }
        }
        filteredEntries = result;
        listState.select(filteredEntries.isEmpty() ? null : 0);
    }

    private int[] adjustPositions(int[] matchPositions, JarEntry entry, String displayText) {
        String searchText = entry.display();
        int offset = displayText.indexOf(searchText.substring(0, Math.min(searchText.length(), 5)));
        if (offset < 0) {
            offset = 2;
        }
        int[] adjusted = new int[matchPositions.length];
        for (int i = 0; i < matchPositions.length; i++) {
            adjusted[i] = matchPositions[i] + offset;
        }
        return adjusted;
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
        String normalized = path.replace('\\', '/');
        int repoIdx = normalized.indexOf("/repository/");
        if (repoIdx >= 0) {
            String relative = normalized.substring(repoIdx + "/repository/".length());
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
        int slash = normalized.lastIndexOf('/');
        String filename = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        return new JarEntry(null, filename, null, path);
    }

    @Override
    public boolean setFilter(String filter) {
        fuzzyFilter.clearFilter();
        if (filter != null && !filter.isEmpty()) {
            for (char c : filter.toCharArray()) {
                fuzzyFilter.appendChar(c);
            }
        }
        refilter();
        return true;
    }

    @Override
    public boolean setInputValue(String field, String value) {
        if ("filter".equals(field)) {
            return setFilter(value);
        }
        return false;
    }

    @Override
    public SelectionContext getSelectionContext() {
        return null;
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

    record FilteredEntry(JarEntry entry, int[] matchPositions) {
    }

    @Override
    public String getHelpText() {
        return """
                # Classpath

                The Classpath tab shows all JAR files on the integration's classpath,
                parsed into Maven coordinates (groupId, artifactId, version). This is
                useful for verifying which dependency versions are in use, finding
                unexpected or duplicate JARs, and understanding the integration's
                dependency footprint.

                ## Table Columns

                - **GROUP:ARTIFACT** — Maven coordinate of the JAR (e.g., `org.apache.camel:camel-core-model`)
                - **VERSION** — The version of the dependency (e.g., `4.12.0`)

                Camel JARs (those with `org.apache.camel` group) are displayed in
                bold. Other dependencies are dimmed for visual distinction.

                ## Example Screen

                ```
                 org.apache.camel:camel-api                 4.12.0
                 org.apache.camel:camel-core-model           4.12.0
                 org.apache.camel:camel-support              4.12.0
                 org.apache.camel:camel-yaml-dsl             4.12.0
                 com.fasterxml.jackson.core:jackson-core     2.18.3
                 org.slf4j:slf4j-api                         2.0.16
                ```

                ## Fuzzy Filter

                Start typing to filter the classpath. The filter uses fuzzy matching
                so you can type partial strings like `kafka` to find all Kafka-related
                JARs, or `jackson` to find Jackson dependencies. Matching characters
                are highlighted in yellow.

                - Type any characters to filter
                - `Backspace` to delete characters from filter
                - `Esc` to clear filter

                ## When To Use

                - **Version conflicts**: Check if the expected version of a library is present. Multiple versions of the same library can cause class loading issues
                - **Missing dependencies**: Verify that a component's dependency JAR is on the classpath
                - **Dependency audit**: Review all transitive dependencies pulled in by the integration
                - **Size analysis**: Get a sense of how many JARs are loaded — large classpaths can slow startup

                ## Keys

                - `Up/Down` — navigate entries
                - `PgUp/PgDn` — scroll by page
                - `type` — fuzzy filter
                - `Backspace` — delete filter character
                - `Esc` — clear filter or back
                """;
    }

    @Override
    public JsonObject getTableDataAsJson() {
        List<FilteredEntry> entries = filteredEntries;
        if (entries.isEmpty()) {
            return null;
        }
        JsonObject result = new JsonObject();
        result.put("tab", "Classpath");
        JsonArray rows = new JsonArray();
        for (FilteredEntry fe : entries) {
            JarEntry j = fe.entry();
            JsonObject row = new JsonObject();
            if (j.groupId() != null) {
                row.put("groupId", j.groupId());
            }
            if (j.artifactId() != null) {
                row.put("artifactId", j.artifactId());
            }
            if (j.version() != null) {
                row.put("version", j.version());
            }
            row.put("path", j.fullPath());
            rows.add(row);
        }
        result.put("rows", rows);
        result.put("totalRows", entries.size());
        Integer sel = listState.selected();
        result.put("selectedIndex", sel != null ? sel : -1);
        return result;
    }
}
