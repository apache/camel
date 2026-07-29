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
import java.nio.file.attribute.BasicFileAttributes;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Layout;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.MouseEvent;
import dev.tamboui.tui.event.MouseEventKind;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.block.Title;
import dev.tamboui.widgets.list.ListItem;
import dev.tamboui.widgets.list.ListState;
import dev.tamboui.widgets.list.ListWidget;
import dev.tamboui.widgets.list.ScrollMode;
import dev.tamboui.widgets.paragraph.Paragraph;
import dev.tamboui.widgets.scrollbar.Scrollbar;
import dev.tamboui.widgets.scrollbar.ScrollbarState;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.dsl.jbang.core.common.CatalogLoader;
import org.apache.camel.tooling.model.BaseOptionModel;
import org.apache.camel.tooling.model.ComponentModel;
import org.apache.camel.tooling.model.DataFormatModel;
import org.apache.camel.tooling.model.LanguageModel;
import org.apache.camel.tooling.model.MainModel;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

/**
 * Source tab showing a file browser in the top panel and a source code viewer in the bottom panel. Replaces the HTTP
 * tab as primary tab 7.
 */
class SourceTab extends AbstractTab {

    private Path rootDir;
    private Path currentDir;
    private final ListState listState = new ListState();
    private final ScrollbarState listScrollState = new ScrollbarState();
    private List<FilesBrowser.FileEntry> entries = Collections.emptyList();
    private final SourceViewer sourceViewer = new SourceViewer();
    private boolean focusOnViewer;

    private int leftPanelWidth = -1;
    private final DragSplit hSplit = new DragSplit();
    private Rect leftArea;
    private Rect rightArea;

    private final Map<String, CamelCatalog> catalogCache = new HashMap<>();

    // Properties quick-doc caches (invalidated when catalog version changes)
    private String propsCatalogVersion;
    private Map<String, BaseOptionModel> mainOptionsCache;
    private final Map<String, Map<String, BaseOptionModel>> componentOptionsCache = new HashMap<>();
    private final Map<String, Map<String, BaseOptionModel>> languageOptionsCache = new HashMap<>();
    private final Map<String, Map<String, BaseOptionModel>> dataformatOptionsCache = new HashMap<>();

    // Spring Boot configuration metadata cache (lazy-loaded on-demand via IPC)
    private Map<String, JsonObject> springBootMetadataCache;
    private boolean springBootMetadataLoaded;

    private static final Pattern YAML_URI_PATTERN = Pattern.compile(
            "^\\s*-?\\s*(?:uri|from|to|toD|wireTap|enrich|pollEnrich|deadLetterChannel):\\s*\"?([a-zA-Z][a-zA-Z0-9+.-]*(?::[^\"\\s]*)?)");
    private static final Pattern YAML_KEY_PATTERN = Pattern.compile(
            "^\\s*-?\\s*([a-zA-Z][a-zA-Z0-9]*)\\s*:");

    SourceTab(MonitorContext ctx) {
        super(ctx);
    }

    boolean isSourceViewerSearchActive() {
        return sourceViewer.isSearchInputActive();
    }

    boolean isSourceViewerEditMode() {
        return sourceViewer.isEditMode();
    }

    boolean isSourceViewerTextInputActive() {
        return sourceViewer.isTextInputActive();
    }

    void handlePaste(String text) {
        sourceViewer.handlePaste(text);
    }

    // ---- MonitorTab ----

    @Override
    public void onTabSelected() {
        refreshFiles();
    }

    @Override
    public void onIntegrationChanged() {
        rootDir = null;
        currentDir = null;
        entries = Collections.emptyList();
        sourceViewer.reset();
        focusOnViewer = false;
        leftPanelWidth = -1;
        refreshFiles();
    }

    @Override
    public boolean handleKeyEvent(KeyEvent ke) {
        if (ke.isKey(dev.tamboui.tui.event.KeyCode.TAB)) {
            // Do not steal focus or insert focus-toggle while editing
            if (sourceViewer.isEditMode()) {
                return true;
            }
            if (sourceViewer.isVisible()) {
                focusOnViewer = !focusOnViewer;
            }
            return true;
        }

        if (focusOnViewer && sourceViewer.isVisible()) {
            boolean wasVisible = sourceViewer.isVisible();
            if (sourceViewer.handleKeyEvent(ke)) {
                if (wasVisible && !sourceViewer.isVisible()) {
                    focusOnViewer = false;
                }
                return true;
            }
            if (ke.isCancel()) {
                focusOnViewer = false;
                return true;
            }
        }

        if (!focusOnViewer) {
            return handleFileListKey(ke);
        }

        return false;
    }

    @Override
    public boolean handleMouseEvent(MouseEvent me, Rect area) {
        if (hSplit.handleMouse(me, me.x())) {
            if (hSplit.isDragging() && me.kind() == MouseEventKind.DRAG) {
                leftPanelWidth = Math.max(15, Math.min(me.x() - area.x(), area.width() - 20));
            }
            return true;
        }

        if (leftArea != null && TuiHelper.contains(leftArea, me.x(), me.y())) {
            focusOnViewer = false;
            if (me.kind() == MouseEventKind.SCROLL_UP) {
                listState.selectPrevious();
                return true;
            }
            if (me.kind() == MouseEventKind.SCROLL_DOWN) {
                listState.selectNext(entries.size());
                return true;
            }
            if (me.isClick()) {
                int innerTop = leftArea.top() + 1;
                int clicked = listState.offset() + (me.y() - innerTop);
                if (clicked >= 0 && clicked < entries.size()) {
                    listState.select(clicked);
                }
                return true;
            }
            return true;
        }

        if (rightArea != null && TuiHelper.contains(rightArea, me.x(), me.y())) {
            if (sourceViewer.isVisible()) {
                focusOnViewer = true;
                return sourceViewer.handleMouseEvent(me);
            }
            return true;
        }

        return false;
    }

    @Override
    public boolean isOverlayActive() {
        return focusOnViewer && sourceViewer.isTextInputActive();
    }

    @Override
    public boolean handleEscape() {
        // Esc is routed here from CamelMonitor before tab key handling — cancel overlays locally
        if (sourceViewer.cancelEdit()) {
            return true;
        }
        if (sourceViewer.isSearchInputActive()) {
            sourceViewer.handleKeyEvent(KeyEvent.ofKey(dev.tamboui.tui.event.KeyCode.ESCAPE));
            return true;
        }
        if (focusOnViewer) {
            focusOnViewer = false;
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
        listState.selectNext(entries.size());
    }

    @Override
    public void render(Frame frame, Rect area) {
        if (ctx.selectedPid == null) {
            renderNoSelection(frame, area);
            return;
        }

        if (leftPanelWidth < 0) {
            leftPanelWidth = Math.max(25, Math.min(35, area.width() * 25 / 100));
        }
        leftPanelWidth = Math.max(15, Math.min(leftPanelWidth, area.width() - 20));

        List<Rect> chunks = Layout.horizontal()
                .constraints(Constraint.length(leftPanelWidth), Constraint.fill())
                .split(area);

        leftArea = chunks.get(0);
        rightArea = chunks.get(1);

        int infoHeight = 6;
        List<Rect> leftChunks = Layout.vertical()
                .constraints(Constraint.fill(), Constraint.length(infoHeight))
                .split(leftArea);

        renderFileList(frame, leftChunks.get(0));
        renderInfoPanel(frame, leftChunks.get(1));
        hSplit.setBorderPos(rightArea.x());
        renderSourcePanel(frame, rightArea);
    }

    @Override
    public void renderFooter(List<Span> spans) {
        if (focusOnViewer && sourceViewer.isVisible()) {
            sourceViewer.renderFooter(spans);
            TuiHelper.hint(spans, "Tab", "files");
        } else {
            TuiHelper.hint(spans, TuiIcons.HINT_SCROLL, "navigate");
            TuiHelper.hint(spans, "Enter", "open");
            if (currentDir != null && rootDir != null && !currentDir.equals(rootDir)) {
                TuiHelper.hint(spans, "Bksp", "parent");
            }
            if (sourceViewer.isVisible()) {
                TuiHelper.hint(spans, "Tab", "viewer");
            }
        }
    }

    @Override
    public String description() {
        return "Browse and view source files of the integration";
    }

    @Override
    public String getHelpText() {
        return """
                # Source

                Browse the source files of the selected integration and view their content
                with syntax highlighting. The tab has a left-right split layout with a file
                explorer on the left and a source viewer on the right.

                ## File List (left panel)
                - **Up/Down** — navigate files
                - **Enter** — open file or directory
                - **Backspace** — go to parent directory

                ## Source Viewer (right panel)
                - **Up/Down** — scroll through source code
                - **e** — edit local file (plain text; only when file is writable)
                - **Esc** — cancel edit (in edit mode) or close viewer
                - **F5** — save file (in edit mode; Camel dev mode auto-reloads)
                - **Space** — cycle format (YAML/Java/XML) for Camel routes
                - **i** — toggle inline Camel documentation for Camel source files
                - **/** — search in source
                - **h** — highlight text
                - **n/N** — next/previous match
                - **w** — toggle word wrap
                - **Esc/c** — close source viewer

                ## General
                - **Tab** — toggle focus between file list and source viewer
                - The focused panel title is highlighted; the unfocused panel dims
                - Drag the split border with the mouse to resize panels
                """;
    }

    @Override
    public JsonObject getTableDataAsJson() {
        JsonObject json = new JsonObject();
        json.put("currentDir", currentDir != null ? currentDir.toString() : null);

        var filesArray = new JsonArray();
        for (FilesBrowser.FileEntry entry : entries) {
            JsonObject file = new JsonObject();
            file.put("name", entry.name());
            file.put("directory", entry.directory());
            if (!entry.directory()) {
                file.put("size", entry.size());
                file.put("sizeFormatted", FilesBrowser.formatFileSize(entry.size()));
            }
            file.put("path", entry.path());
            filesArray.add(file);
        }
        json.put("files", filesArray);

        Integer sel = listState.selected();
        if (sel != null && sel < entries.size()) {
            FilesBrowser.FileEntry selected = entries.get(sel);
            json.put("selectedFile", selected.name());
            json.put("selectedIndex", sel);
            Path path = Path.of(selected.path());
            json.put("isCamelSource", isCamelSourceFile(path));
            try {
                BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
                if (!selected.directory()) {
                    json.put("fileSize", attrs.size());
                    json.put("fileSizeFormatted", FilesBrowser.formatFileSize(attrs.size()));
                    int dot = selected.name().lastIndexOf('.');
                    if (dot > 0) {
                        json.put("fileType", selected.name().substring(dot + 1));
                    }
                }
                json.put("lastModified", attrs.lastModifiedTime().toInstant()
                        .atZone(ZoneId.systemDefault()).format(DATE_FMT));
            } catch (IOException e) {
                // ignore
            }
        }
        json.put("totalFiles", entries.size());
        json.put("focusOnViewer", focusOnViewer);
        json.put("viewerOpen", sourceViewer.isVisible());
        return json;
    }

    // ---- Internals ----

    private void refreshFiles() {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null) {
            return;
        }
        Path dir = FilesBrowser.resolveSourceDirectory(info);
        if (dir == null || !Files.isDirectory(dir)) {
            return;
        }
        if (rootDir == null || !rootDir.equals(dir)) {
            rootDir = dir;
            currentDir = dir;
        }
        if (currentDir != null) {
            loadDirectory(currentDir);
        }
    }

    private boolean loadDirectory(Path dir) {
        List<FilesBrowser.FileEntry> dirs = new ArrayList<>();
        List<FilesBrowser.FileEntry> files = new ArrayList<>();
        try (var stream = Files.list(dir)) {
            stream.limit(200)
                    .forEach(p -> {
                        String name = p.getFileName().toString();
                        if (Files.isDirectory(p) && !name.startsWith(".")) {
                            dirs.add(new FilesBrowser.FileEntry(TuiIcons.FOLDER, name, -1, p.toString(), true));
                        } else if (Files.isRegularFile(p)) {
                            String emoji = TuiHelper.fileEmoji(p);
                            long size = 0;
                            try {
                                size = Files.size(p);
                            } catch (IOException e) {
                                // ignore
                            }
                            files.add(new FilesBrowser.FileEntry(emoji, name, size, p.toString(), false));
                        }
                    });
        } catch (IOException e) {
            return false;
        }
        dirs.sort(Comparator.comparing(FilesBrowser.FileEntry::name, String.CASE_INSENSITIVE_ORDER));
        files.sort(Comparator.comparing(FilesBrowser.FileEntry::name, String.CASE_INSENSITIVE_ORDER));

        List<FilesBrowser.FileEntry> found = new ArrayList<>();
        if (!dir.equals(rootDir)) {
            found.add(new FilesBrowser.FileEntry(TuiIcons.FOLDER, "..", -1, dir.getParent().toString(), true));
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

    private boolean handleFileListKey(KeyEvent ke) {
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
            openSelectedEntry();
            return true;
        }
        return false;
    }

    private void openSelectedEntry() {
        if (sourceViewer.isEditMode()) {
            return;
        }
        Integer sel = listState.selected();
        if (sel != null && sel < entries.size()) {
            FilesBrowser.FileEntry entry = entries.get(sel);
            if (entry.directory()) {
                loadDirectory(Path.of(entry.path()));
            } else {
                Path filePath = Path.of(entry.path());
                if (isCamelSourceFile(filePath)) {
                    sourceViewer.setQuickDocProvider(this::provideCamelQuickDocs);
                    sourceViewer.setDeprecatedLineScanner(null);
                } else if (isPropertiesFile(filePath)) {
                    sourceViewer.setQuickDocProvider(this::providePropertiesQuickDocs);
                    sourceViewer.setDeprecatedLineScanner(this::scanDeprecatedProperties);
                } else {
                    sourceViewer.setQuickDocProvider(null);
                    sourceViewer.setDeprecatedLineScanner(null);
                }
                sourceViewer.loadFile(filePath);
                focusOnViewer = true;
            }
        }
    }

    private boolean isCamelSourceFile(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        if (name.endsWith(".camel.yaml") || name.endsWith(".camel.yml")
                || name.endsWith(".kamelet.yaml") || name.endsWith(".kamelet.yml")) {
            return true;
        }
        if (name.endsWith(".yaml") || name.endsWith(".yml")) {
            return TuiHelper.isCamelYaml(path);
        }
        if (name.endsWith(".xml")) {
            return TuiHelper.isCamelXml(path);
        }
        return false;
    }

    private CamelCatalog getCatalog() {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null || info.camelVersion == null) {
            return null;
        }
        String version = info.camelVersion;
        CamelCatalog cached = catalogCache.get(version);
        if (cached != null) {
            return cached;
        }
        try {
            cached = CatalogLoader.loadCatalog(null, version, true);
            if (cached != null) {
                catalogCache.put(version, cached);
            }
            return cached;
        } catch (Exception e) {
            return null;
        }
    }

    private Map<Integer, List<SourceViewer.DocEntry>> provideCamelQuickDocs(List<JsonObject> codeData) {
        CamelCatalog catalog = getCatalog();
        if (catalog == null || codeData.isEmpty()) {
            return Map.of();
        }

        Map<Integer, List<SourceViewer.DocEntry>> result = new LinkedHashMap<>();
        for (int i = 0; i < codeData.size(); i++) {
            String code = codeData.get(i).getString("code");
            if (code == null) {
                continue;
            }

            Matcher uriMatcher = YAML_URI_PATTERN.matcher(code);
            if (uriMatcher.find()) {
                String uri = uriMatcher.group(1);
                if (uri.endsWith("\"")) {
                    uri = uri.substring(0, uri.length() - 1);
                }
                RoutesTab.buildEndpointInlineDoc(result, codeData, catalog, uri, i);
                continue;
            }

            Matcher keyMatcher = YAML_KEY_PATTERN.matcher(code);
            if (keyMatcher.find()) {
                String key = keyMatcher.group(1);
                if (catalog.eipModel(key) != null) {
                    RoutesTab.buildEipInlineDoc(result, codeData, catalog, key, null, i);
                }
            }
        }
        return result;
    }

    private static boolean isPropertiesFile(Path path) {
        return path.getFileName().toString().toLowerCase().endsWith(".properties");
    }

    private Map<Integer, List<SourceViewer.DocEntry>> providePropertiesQuickDocs(List<JsonObject> codeData) {
        CamelCatalog catalog = getCatalog();
        if (catalog == null || codeData.isEmpty()) {
            return Map.of();
        }
        ensureMainOptionsCache(catalog);

        Map<Integer, List<SourceViewer.DocEntry>> result = new LinkedHashMap<>();
        for (int i = 0; i < codeData.size(); i++) {
            String code = codeData.get(i).getString("code");
            if (code == null) {
                continue;
            }
            String trimmed = code.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("!")) {
                continue;
            }
            int eq = trimmed.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = trimmed.substring(0, eq).trim();
            BaseOptionModel opt = lookupPropertyOption(catalog, key);
            if (opt != null) {
                String doc = RoutesTab.formatOptionDoc(opt);
                if (doc != null) {
                    result.put(i, List.of(opt.isDeprecated()
                            ? SourceViewer.DocEntry.deprecated(doc)
                            : SourceViewer.DocEntry.of(doc)));
                }
                continue;
            }
            // fallback to Spring Boot configuration metadata for non-camel properties
            ensureSpringBootMetadataCache();
            if (springBootMetadataCache != null) {
                JsonObject sbProp = springBootMetadataCache.get(key);
                if (sbProp != null) {
                    String doc = SpringBootMetadataHelper.formatDoc(sbProp);
                    if (doc != null) {
                        boolean deprecated = Boolean.TRUE.equals(sbProp.get("deprecated"));
                        result.put(i, List.of(deprecated
                                ? SourceViewer.DocEntry.deprecated(doc)
                                : SourceViewer.DocEntry.of(doc)));
                    }
                }
            }
        }
        return result;
    }

    private Set<Integer> scanDeprecatedProperties(List<JsonObject> codeData) {
        CamelCatalog catalog = getCatalog();
        if (catalog == null || codeData.isEmpty()) {
            return Set.of();
        }
        ensureMainOptionsCache(catalog);

        Set<Integer> result = new HashSet<>();
        for (int i = 0; i < codeData.size(); i++) {
            String code = codeData.get(i).getString("code");
            if (code == null) {
                continue;
            }
            String trimmed = code.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("!")) {
                continue;
            }
            int eq = trimmed.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = trimmed.substring(0, eq).trim();
            BaseOptionModel opt = lookupPropertyOption(catalog, key);
            if (opt != null) {
                if (opt.isDeprecated()) {
                    result.add(i);
                }
                continue;
            }
            ensureSpringBootMetadataCache();
            if (springBootMetadataCache != null) {
                JsonObject sbProp = springBootMetadataCache.get(key);
                if (sbProp != null && Boolean.TRUE.equals(sbProp.get("deprecated"))) {
                    result.add(i);
                }
            }
        }
        return result;
    }

    private BaseOptionModel lookupPropertyOption(CamelCatalog catalog, String key) {
        if (mainOptionsCache != null) {
            BaseOptionModel opt = mainOptionsCache.get(key);
            if (opt != null) {
                return opt;
            }
        }
        if (key.startsWith("camel.component.")) {
            return lookupPrefixedOption(key, "camel.component.", componentOptionsCache,
                    name -> {
                        ComponentModel m = catalog.componentModel(name);
                        return m != null ? m.getComponentOptions() : null;
                    });
        }
        if (key.startsWith("camel.language.")) {
            return lookupPrefixedOption(key, "camel.language.", languageOptionsCache,
                    name -> {
                        LanguageModel m = catalog.languageModel(name);
                        return m != null ? m.getOptions() : null;
                    });
        }
        if (key.startsWith("camel.dataformat.")) {
            return lookupPrefixedOption(key, "camel.dataformat.", dataformatOptionsCache,
                    name -> {
                        DataFormatModel m = catalog.dataFormatModel(name);
                        return m != null ? m.getOptions() : null;
                    });
        }
        return null;
    }

    private BaseOptionModel lookupPrefixedOption(
            String key, String prefix,
            Map<String, Map<String, BaseOptionModel>> cache,
            java.util.function.Function<String, List<? extends BaseOptionModel>> optionsLoader) {
        String rest = key.substring(prefix.length());
        int dot = rest.indexOf('.');
        if (dot <= 0) {
            return null;
        }
        String name = rest.substring(0, dot);
        String optionName = rest.substring(dot + 1);
        Map<String, BaseOptionModel> opts = cache.computeIfAbsent(name, n -> {
            List<? extends BaseOptionModel> options = optionsLoader.apply(n);
            if (options == null) {
                return Map.of();
            }
            Map<String, BaseOptionModel> map = new HashMap<>();
            for (BaseOptionModel o : options) {
                if (o.getName() != null) {
                    map.put(o.getName(), o);
                }
            }
            return map;
        });
        return opts.get(optionName);
    }

    private void ensureMainOptionsCache(CamelCatalog catalog) {
        IntegrationInfo info = ctx.findSelectedIntegration();
        String version = info != null ? info.camelVersion : null;
        if (version != null && !version.equals(propsCatalogVersion)) {
            mainOptionsCache = null;
            componentOptionsCache.clear();
            languageOptionsCache.clear();
            dataformatOptionsCache.clear();
            springBootMetadataCache = null;
            springBootMetadataLoaded = false;
            propsCatalogVersion = version;
        }
        if (mainOptionsCache == null) {
            mainOptionsCache = new HashMap<>();
            MainModel mainModel = catalog.mainModel();
            if (mainModel != null) {
                for (MainModel.MainOptionModel opt : mainModel.getOptions()) {
                    if (opt.getName() != null) {
                        mainOptionsCache.put(opt.getName(), opt);
                    }
                }
            }
        }
    }

    private void ensureSpringBootMetadataCache() {
        if (springBootMetadataLoaded) {
            return;
        }
        springBootMetadataLoaded = true;
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null || !"Spring Boot".equals(info.platform)) {
            return;
        }
        springBootMetadataCache = SpringBootMetadataHelper.fetchMetadata(ctx, info.pid);
    }

    private void renderFileList(Frame frame, Rect area) {
        Style fileBorderStyle = focusOnViewer ? Theme.muted() : Style.EMPTY.fg(Theme.accent());
        if (entries.isEmpty()) {
            String noFilesMsg = rootDir == null ? "No source directory found" : "No files found";
            frame.renderWidget(
                    Paragraph.builder()
                            .text(Text.from(Line.from(Span.styled("   " + noFilesMsg, Style.EMPTY.dim()))))
                            .block(Block.builder()
                                    .borderType(BorderType.ROUNDED).borders(Borders.ALL)
                                    .borderStyle(fileBorderStyle)
                                    .title(Title.from(Line.from(
                                            Span.styled(" Files ",
                                                    focusOnViewer ? Style.EMPTY.fg(Theme.accent()) : Theme.title()))))
                                    .build())
                            .build(),
                    area);
            return;
        }

        String panelTitle = " Files";
        if (rootDir != null && currentDir != null && !currentDir.equals(rootDir)) {
            panelTitle += ": " + rootDir.relativize(currentDir);
        }
        panelTitle += " ";

        ListItem[] items = new ListItem[entries.size()];
        for (int i = 0; i < entries.size(); i++) {
            FilesBrowser.FileEntry entry = entries.get(i);
            String label = " " + entry.emoji() + " " + entry.name();
            if (entry.directory()) {
                boolean dimDir = entry.name().startsWith(".") || "target".equals(entry.name());
                Style dirStyle = dimDir ? Style.EMPTY.fg(Theme.accent()).dim() : Style.EMPTY.fg(Theme.accent());
                items[i] = ListItem.from(Line.from(Span.styled(label, dirStyle)));
            } else {
                items[i] = ListItem.from(Line.from(Span.raw(label)));
            }
        }

        Style titleStyle = focusOnViewer ? Style.EMPTY.fg(Theme.accent()) : Theme.title();
        ListWidget list = ListWidget.builder()
                .items(items)
                .highlightStyle(focusOnViewer ? Theme.selectionBg().dim() : Theme.selectionBg())
                .highlightSymbol("")
                .scrollMode(ScrollMode.AUTO_SCROLL)
                .block(Block.builder()
                        .borderType(BorderType.ROUNDED).borders(Borders.ALL)
                        .borderStyle(fileBorderStyle)
                        .title(Title.from(Line.from(Span.styled(panelTitle, titleStyle))))
                        .build())
                .build();
        frame.renderStatefulWidget(list, area, listState);

        int visibleRows = area.height() - 2;
        if (entries.size() > visibleRows) {
            Rect scrollRect = new Rect(
                    area.x() + area.width() - 1,
                    area.y() + 1,
                    1,
                    visibleRows);
            listScrollState.contentLength(entries.size());
            listScrollState.viewportContentLength(visibleRows);
            listScrollState.position(listState.offset());
            frame.renderStatefulWidget(Scrollbar.builder().build(), scrollRect, listScrollState);
        }
    }

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private void renderInfoPanel(Frame frame, Rect area) {
        List<Line> lines = new ArrayList<>();
        Integer sel = listState.selected();
        if (sel != null && sel < entries.size()) {
            FilesBrowser.FileEntry entry = entries.get(sel);
            Path path = Path.of(entry.path());
            try {
                BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
                if (!entry.directory()) {
                    lines.add(Line.from(
                            Span.styled(" Size: ", Style.EMPTY.dim()),
                            Span.raw(FilesBrowser.formatFileSize(attrs.size()))));
                }
                String modified = attrs.lastModifiedTime().toInstant()
                        .atZone(ZoneId.systemDefault()).format(DATE_FMT);
                lines.add(Line.from(
                        Span.styled(" Modified: ", Style.EMPTY.dim()),
                        Span.raw(modified)));
                if (!entry.directory()) {
                    String ext = "";
                    int dot = entry.name().lastIndexOf('.');
                    if (dot > 0) {
                        ext = entry.name().substring(dot + 1);
                    }
                    if (!ext.isEmpty()) {
                        lines.add(Line.from(
                                Span.styled(" Type: ", Style.EMPTY.dim()),
                                Span.raw(ext)));
                    }
                }
            } catch (IOException e) {
                // ignore
            }
        }

        Style infoBorderStyle = focusOnViewer ? Theme.muted() : Style.EMPTY.fg(Theme.accent());
        frame.renderWidget(
                Paragraph.builder()
                        .text(Text.from(lines))
                        .block(Block.builder()
                                .borderType(BorderType.ROUNDED).borders(Borders.ALL)
                                .borderStyle(infoBorderStyle)
                                .title(Title.from(Line.from(
                                        Span.styled(" Info ", focusOnViewer ? Style.EMPTY.fg(Theme.accent()) : Theme.title()))))
                                .build())
                        .build(),
                area);
    }

    private void renderSourcePanel(Frame frame, Rect area) {
        Style sourceTitleStyle = focusOnViewer ? Theme.title() : Style.EMPTY.fg(Theme.accent());
        Style sourceBorderStyle = focusOnViewer ? Style.EMPTY.fg(Theme.accent()) : Theme.muted();
        if (sourceViewer.isVisible()) {
            sourceViewer.setTitleStyle(sourceTitleStyle);
            sourceViewer.setBorderStyle(sourceBorderStyle);
            sourceViewer.setFocused(focusOnViewer);
            sourceViewer.render(frame, area);
        } else {
            List<Line> lines = new ArrayList<>();
            lines.add(Line.from(Span.raw("")));
            lines.add(Line.from(Span.styled("   Select a file and press Enter to view source", Style.EMPTY.dim())));

            frame.renderWidget(
                    Paragraph.builder()
                            .text(Text.from(lines))
                            .block(Block.builder()
                                    .borderType(BorderType.ROUNDED).borders(Borders.ALL)
                                    .borderStyle(sourceBorderStyle)
                                    .title(Title.from(Line.from(
                                            Span.styled(" Source ", sourceTitleStyle))))
                                    .build())
                            .build(),
                    area);
        }
    }
}
