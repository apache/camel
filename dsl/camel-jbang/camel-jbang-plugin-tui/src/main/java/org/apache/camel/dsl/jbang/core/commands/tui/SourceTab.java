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
import java.nio.file.attribute.BasicFileAttributes;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
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
import dev.tamboui.tui.event.KeyCode;
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
import org.apache.camel.catalog.ConfigurationPropertiesValidationResult;
import org.apache.camel.catalog.EndpointValidationResult;
import org.apache.camel.catalog.LanguageValidationResult;
import org.apache.camel.dsl.jbang.core.common.CatalogLoader;
import org.apache.camel.tooling.model.BaseOptionModel;
import org.apache.camel.tooling.model.ComponentModel;
import org.apache.camel.tooling.model.DataFormatModel;
import org.apache.camel.tooling.model.EipModel;
import org.apache.camel.tooling.model.LanguageModel;
import org.apache.camel.tooling.model.MainModel;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

/**
 * Source tab showing a file browser in the top panel and a source code viewer in the bottom panel. Replaces the HTTP
 * tab as primary tab 7.
 */
class SourceTab extends AbstractTab {

    private String lastSeenPid;
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
    private Map<String, String> mainGroupsCache;
    private final Map<String, Map<String, BaseOptionModel>> componentOptionsCache = new HashMap<>();
    private final Map<String, Map<String, BaseOptionModel>> languageOptionsCache = new HashMap<>();
    private final Map<String, Map<String, BaseOptionModel>> dataformatOptionsCache = new HashMap<>();

    // Component name completion cache (keyed by catalog version)
    private String componentsCatalogVersion;
    private List<AutocompletePopup.CompletionItem> consumerComponents;
    private List<AutocompletePopup.CompletionItem> producerComponents;

    // YAML DSL completion tree (loaded from generated schema)
    private JsonObject completionTree;
    private boolean completionTreeLoaded;

    // Spring Boot configuration metadata cache (lazy-loaded on-demand via IPC or from local JARs)
    private Map<String, JsonObject> springBootMetadataCache;
    private boolean springBootMetadataLoaded;
    private Map<String, BaseOptionModel> springBootOptionsCache;
    private Map<String, String> springBootGroupsCache;
    private Map<String, List<String>> springBootHintsCache;
    private java.util.concurrent.CompletableFuture<SpringBootMetadataResolver.MetadataResult> springBootMetadataFuture;

    private static final Pattern YAML_URI_PATTERN = Pattern.compile(
            "^\\s*-?\\s*(?:uri|from|to|toD|wireTap|enrich|pollEnrich|deadLetterChannel):\\s*\"?([a-zA-Z][a-zA-Z0-9+.-]*(?::[^\"\\s]*)?)");
    private static final Pattern YAML_KEY_PATTERN = Pattern.compile(
            "^\\s*-?\\s*([a-zA-Z][a-zA-Z0-9]*)\\s*:");

    private static final Set<String> LINKABLE_KEYWORDS = Set.of(
            "to", "toD", "wireTap", "enrich", "pollEnrich", "deadLetterChannel");

    record RouteEntry(String routeId, String fromUri, String filePath, int fromLine) {
    }

    record ToEntry(String routeId, String toUri, String filePath, int toLine) {
    }

    private List<RouteEntry> routeIndex = Collections.emptyList();
    private List<ToEntry> toIndex = Collections.emptyList();
    private final GotoRoutePopup gotoRoutePopup = new GotoRoutePopup();
    private final GotoSourceNodePopup gotoSourceNodePopup = new GotoSourceNodePopup();
    private final FileActionsPopup fileActionsPopup = new FileActionsPopup();

    SourceTab(MonitorContext ctx) {
        super(ctx);
        sourceViewer.setNotificationCallback((msg, error) -> {
            if (ctx.notificationCallback != null) {
                ctx.notificationCallback.accept(msg, error);
            }
        });
        sourceViewer.setValidateOnSave(ctx.validateOnSave);
        sourceViewer.setOnJumpLink(this::handleJumpLink);
    }

    boolean isSourceViewerEditMode() {
        return sourceViewer.isEditMode();
    }

    boolean isSourceViewerTextInputActive() {
        // also treat the file-actions menu as active input so global single-key shortcuts (q, ?, ...)
        // do not fire while the menu, its name prompt, or delete confirmation is open
        return sourceViewer.isTextInputActive() || fileActionsPopup.isVisible();
    }

    void handlePaste(String text) {
        sourceViewer.handlePaste(text);
    }

    // ---- MonitorTab ----

    @Override
    public void onTabSelected() {
        if (ctx.selectedPid != null && !ctx.selectedPid.equals(lastSeenPid)) {
            lastSeenPid = ctx.selectedPid;
            onIntegrationChanged();
        } else {
            lastSeenPid = ctx.selectedPid;
            refreshFiles();
        }
    }

    @Override
    public void onIntegrationChanged() {
        lastSeenPid = ctx.selectedPid;
        rootDir = null;
        currentDir = null;
        entries = Collections.emptyList();
        sourceViewer.reset();
        focusOnViewer = false;
        leftPanelWidth = -1;
        completionTreeLoaded = false;
        completionTree = null;
        routeIndex = Collections.emptyList();
        toIndex = Collections.emptyList();
        refreshFiles();
    }

    @Override
    public boolean handleKeyEvent(KeyEvent ke) {
        if (fileActionsPopup.isVisible()) {
            fileActionsPopup.handleKeyEvent(ke);
            FileActionsPopup.Request req = fileActionsPopup.consumeResult();
            if (req != null) {
                executeFileAction(req);
            }
            return true;
        }

        if (gotoRoutePopup.isVisible()) {
            gotoRoutePopup.handleKeyEvent(ke);
            GotoRoutePopup.RouteItem sel = gotoRoutePopup.consumeSelection();
            if (sel != null) {
                openFileAt(sel.filePath(), sel.fromLine());
            }
            return true;
        }

        if (gotoSourceNodePopup.isVisible()) {
            gotoSourceNodePopup.handleKeyEvent(ke);
            int gotoLine = gotoSourceNodePopup.consumeGotoLineNumber();
            if (gotoLine > 0) {
                sourceViewer.goToLine(gotoLine - 1);
                return true;
            }
            YamlRouteNodeScanner.NodeEntry sel = gotoSourceNodePopup.consumeSelection();
            if (sel != null) {
                openFileAt(sel.filePath(), sel.lineIndex());
            }
            return true;
        }

        if (ke.hasCtrl() && ke.isCharIgnoreCase('g')) {
            gotoSourceNodePopup.open(buildSourceNodeIndex(), sourceViewer.getLineCount());
            return true;
        }

        if (sourceViewer.isEditMode() && sourceViewer.isVisible()) {
            return sourceViewer.handleKeyEvent(ke);
        }

        if (ke.isKey(KeyCode.TAB)) {
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

        if (!routeIndex.isEmpty() && ke.isChar('g')) {
            gotoRoutePopup.open(routeIndex);
            return true;
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
                    openSelectedEntry();
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
        return fileActionsPopup.isVisible() || (focusOnViewer && sourceViewer.isTextInputActive());
    }

    @Override
    public boolean handleEscape() {
        // Esc is routed here from CamelMonitor before tab key handling — cancel overlays locally
        if (fileActionsPopup.isVisible()) {
            fileActionsPopup.handleKeyEvent(KeyEvent.ofKey(KeyCode.ESCAPE));
            return true;
        }
        if (gotoRoutePopup.isVisible()) {
            gotoRoutePopup.close();
            return true;
        }
        if (gotoSourceNodePopup.isVisible()) {
            gotoSourceNodePopup.close();
            return true;
        }
        if (sourceViewer.cancelEdit()) {
            return true;
        }
        if (sourceViewer.isSearchInputActive()) {
            sourceViewer.handleKeyEvent(KeyEvent.ofKey(KeyCode.ESCAPE));
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
        sourceViewer.setValidateOnSave(ctx.validateOnSave);
        if (ctx.selectedPid == null) {
            lastSeenPid = null;
            renderNoSelection(frame, area);
            return;
        }
        // Detect PID change (e.g. after restart) and refresh stale file references
        if (!ctx.selectedPid.equals(lastSeenPid)) {
            lastSeenPid = ctx.selectedPid;
            onIntegrationChanged();
        }

        if (sourceViewer.isPlainMode() && sourceViewer.isVisible()) {
            leftArea = null;
            rightArea = area;
            renderSourcePanel(frame, area);
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

        if (gotoRoutePopup.isVisible()) {
            gotoRoutePopup.render(frame, area);
        }
        if (gotoSourceNodePopup.isVisible()) {
            gotoSourceNodePopup.render(frame, area);
        }
        if (fileActionsPopup.isVisible()) {
            fileActionsPopup.render(frame, area);
        }
    }

    @Override
    public void renderFooter(List<Span> spans) {
        if (fileActionsPopup.isVisible()) {
            fileActionsPopup.renderFooter(spans);
            return;
        }
        if (focusOnViewer && sourceViewer.isVisible()) {
            sourceViewer.renderFooter(spans);
            if (!sourceViewer.isEditMode()) {
                TuiHelper.hint(spans, "Tab", "files");
            }
        } else {
            TuiHelper.hint(spans, TuiIcons.HINT_SCROLL, "navigate");
            TuiHelper.hint(spans, "Enter", "open");
            if (currentDir != null && rootDir != null && !currentDir.equals(rootDir)) {
                TuiHelper.hint(spans, "Bksp", "parent");
            }
            if (sourceViewer.isVisible()) {
                TuiHelper.hint(spans, "Tab", "viewer");
            }
            if (!routeIndex.isEmpty()) {
                TuiHelper.hint(spans, "g", "go to route");
            }
            if (sourceViewer.isVisible()) {
                TuiHelper.hint(spans, "Ctrl+G", "go to");
            }
        }
    }

    @Override
    public void renderFKeyHints(List<Span> spans) {
        // Group the F12 file-actions hint with the global F-keys (next to F10) rather than at the tail. Only shown
        // when the file list is focused (not the viewer) and no dialog is open.
        if (!fileActionsPopup.isVisible() && !(focusOnViewer && sourceViewer.isVisible())) {
            TuiHelper.hint(spans, "F12", "file actions");
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
                - **F4** — open file directly in edit mode
                - **F12** — file actions menu (new file, new folder, rename, duplicate, delete, copy path)
                - **Backspace** — go to parent directory

                ## Source Viewer (right panel)
                - **Up/Down** — scroll through source code
                - **F4** — edit local file (plain text; only when file is writable)
                - **Esc** — cancel edit (in edit mode) or close viewer
                - **Ctrl+S** — save file and continue editing (Camel dev mode auto-reloads)
                - **F5** — save file and close editor
                - **Space** — cycle format (YAML/Java/XML) for Camel routes
                - Quick documentation panel is shown at the bottom for Camel source files
                - **/** — search in source
                - **h** — highlight text
                - **n/N** — next/previous match
                - **w** — toggle word wrap
                - **p** — toggle plain mode (hides line numbers, borders, and file panel for easy copy/paste)
                - **Esc/c** — close source viewer

                ## Edit Mode (Shortcuts)
                - **Ctrl+Z** — undo
                - **Ctrl+Y / Ctrl+Shift+Z** — redo
                - **Alt+Up / Alt+Down** — move YAML list block up/down
                - **Ctrl+D** — duplicate current block
                - **Ctrl+K** — delete current line
                - **Ctrl+Left / Ctrl+Right** — word navigation
                - **Home** — smart home (content indent, then column 0)
                - Quick documentation panel is shown at the bottom (shows doc for current line)
                - **F7** — show diff of unsaved changes
                - **F9** — jump to next validation error

                ## Edit Mode (Tab Completion)
                Press **F4** to enter edit mode, then **Tab** for context-aware completion:

                **application.properties:**
                - Key completion for `camel.main.*`, `camel.component.*`, `camel.dataformat.*`,
                  and `camel.language.*` options from the Camel catalog
                - Spring Boot auto-configuration properties (`server.*`, `spring.*`, `management.*`,
                  etc.) resolved from starter JARs in the local Maven repository — works even when
                  the application is not running (phantom/stopped projects)
                - Value completion with enum choices, boolean values, Spring Boot value hints,
                  and `{{placeholder}}` suggestions

                **YAML DSL routes:**
                - On `uri:` lines (or inline EIPs like `to:`, `from:`), Tab shows a list of
                  Camel component names filtered by role: consumer endpoints (e.g. `from:`)
                  exclude producer-only components, and producer endpoints (e.g. `to:`) exclude
                  consumer-only components. Type to filter by name or label (e.g. "cloud",
                  "messaging"). Selecting a component auto-inserts a `parameters:` block.
                - Inside `parameters:` blocks, key completion shows endpoint options from the
                  Camel catalog, filtered by consumer/producer role. Required options appear
                  first (marked with `*`). Already-specified options are excluded.
                - Inside EIP blocks (e.g. `split:`, `aggregate:`, `filter:`), Tab shows
                  the EIP's configurable options (attribute-type only, excluding structural
                  elements like `steps:` and `expression:`).
                - Value completion shows enum choices, boolean values, and `{{placeholder}}`
                  suggestions from your `.properties` files

                Use **Up/Down** to navigate, **Enter** to accept, **Esc** to dismiss, and
                type to filter the completion list.

                ## Route Jump Links
                Lines with `to:`, `toD:`, `wireTap:`, or similar endpoints that reference
                another route show a **↵ routeId** indicator. Press **Enter** on such a line
                to jump to the target route's definition (within the same file or across files).
                Reverse links are shown on `from:` lines, indicating which route calls this one.
                Jump indicators are hidden in plain mode.

                ## Go to Route
                - **g** — open a filterable popup listing all routes found in the source files.
                  Type to fuzzy-filter by route ID or endpoint URI, then press **Enter** to
                  navigate to the selected route.

                ## Go to Node / Line
                - **Ctrl+G** — open a popup showing routes and their individual
                  processors/EIPs in a tree structure. Type to fuzzy-filter by route ID,
                  EIP type, or label, then press **Enter** to jump directly to the selected
                  node in the source editor. Type a **line number** (e.g. `47`) and press
                  **Enter** to jump directly to that line.

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
        return loadDirectory(dir, null);
    }

    private boolean loadDirectory(Path dir, String selectName) {
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

        // auto-descend through empty middle folders (no files and exactly one sub folder)
        // when navigating forward (not when restoring position while navigating back)
        if (selectName == null && files.isEmpty() && dirs.size() == 1) {
            return loadDirectory(Path.of(dirs.get(0).path()));
        }

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
        buildRouteIndex();
        return true;
    }

    private void navigateBack() {
        if (currentDir == null || currentDir.equals(rootDir)) {
            return;
        }
        Path child = currentDir;
        Path parent = currentDir.getParent();
        // skip back through empty middle folders (parent has no files and only this one sub folder),
        // but never above the root directory
        while (parent != null && !parent.equals(rootDir) && parent.getParent() != null
                && isEmptyMiddleFolder(parent)) {
            child = parent;
            parent = parent.getParent();
        }
        loadDirectory(parent, child.getFileName().toString());
    }

    private boolean isEmptyMiddleFolder(Path dir) {
        int dirCount = 0;
        try (var stream = Files.list(dir)) {
            var it = stream.iterator();
            while (it.hasNext()) {
                Path p = it.next();
                String name = p.getFileName().toString();
                if (Files.isDirectory(p)) {
                    if (name.startsWith(".")) {
                        // hidden directories are not shown
                        continue;
                    }
                    dirCount++;
                    if (dirCount > 1) {
                        return false;
                    }
                } else if (Files.isRegularFile(p)) {
                    // has at least one visible file
                    return false;
                }
            }
        } catch (IOException e) {
            return false;
        }
        return dirCount == 1;
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
            navigateBack();
            return true;
        }
        if (ke.isConfirm()) {
            openSelectedEntry();
            return true;
        }
        if (ke.isKey(KeyCode.F4)) {
            openSelectedEntry();
            if (sourceViewer.isVisible() && sourceViewer.isEditable()) {
                sourceViewer.enterEditMode();
            }
            return true;
        }
        if (ke.isKey(KeyCode.F12)) {
            openFileActionsMenu();
            return true;
        }
        return false;
    }

    private void openFileActionsMenu() {
        if (currentDir == null) {
            return;
        }
        FilesBrowser.FileEntry entry = selectedEntry();
        boolean hasTarget = entry != null && !"..".equals(entry.name());
        fileActionsPopup.open(hasTarget ? entry.name() : null, hasTarget);
    }

    private FilesBrowser.FileEntry selectedEntry() {
        Integer sel = listState.selected();
        if (sel != null && sel >= 0 && sel < entries.size()) {
            return entries.get(sel);
        }
        return null;
    }

    private void executeFileAction(FileActionsPopup.Request req) {
        FilesBrowser.FileEntry entry = selectedEntry();
        try {
            switch (req.action()) {
                case NEW_FILE -> {
                    // TODO: a future template wizard will let the user pick a starter route here
                    Path p = SourceFileOps.createFile(currentDir, req.name());
                    if (loadDirectory(currentDir, p.getFileName().toString())) {
                        openSelectedEntry();
                    }
                    notify("Created " + p.getFileName(), false);
                }
                case NEW_FOLDER -> {
                    Path p = SourceFileOps.createFolder(currentDir, req.name());
                    loadDirectory(currentDir, p.getFileName().toString());
                    notify("Created " + p.getFileName() + "/", false);
                }
                case RENAME -> {
                    if (entry == null) {
                        return;
                    }
                    Path p = SourceFileOps.rename(Path.of(entry.path()), req.name());
                    loadDirectory(currentDir, p.getFileName().toString());
                    notify("Renamed to " + p.getFileName(), false);
                }
                case DUPLICATE -> {
                    if (entry == null) {
                        return;
                    }
                    Path p = SourceFileOps.copy(Path.of(entry.path()), req.name());
                    loadDirectory(currentDir, p.getFileName().toString());
                    notify("Duplicated to " + p.getFileName(), false);
                }
                case DELETE -> {
                    if (entry == null) {
                        return;
                    }
                    String name = entry.name();
                    SourceFileOps.delete(Path.of(entry.path()));
                    loadDirectory(currentDir);
                    notify("Deleted " + name, false);
                }
                case COPY_PATH -> {
                    if (entry == null) {
                        return;
                    }
                    TuiHelper.copyToClipboard(entry.path());
                    notify("Copied path to clipboard", false);
                }
            }
        } catch (Exception e) {
            notify(e.getMessage() != null ? e.getMessage() : e.toString(), true);
        }
    }

    private void notify(String msg, boolean error) {
        if (ctx.notificationCallback != null) {
            ctx.notificationCallback.accept(msg, error);
        }
    }

    private void openSelectedEntry() {
        if (sourceViewer.isEditMode()) {
            return;
        }
        Integer sel = listState.selected();
        if (sel != null && sel < entries.size()) {
            FilesBrowser.FileEntry entry = entries.get(sel);
            if (entry.directory()) {
                if ("..".equals(entry.name())) {
                    navigateBack();
                } else {
                    loadDirectory(Path.of(entry.path()));
                }
            } else {
                Path filePath = Path.of(entry.path());
                if (isCamelSourceFile(filePath)) {
                    sourceViewer.setQuickDocProvider(this::provideCamelQuickDocs);
                    sourceViewer.setDeprecatedLineScanner(null);
                    if (isYamlFile(filePath)) {
                        sourceViewer.setAutocompleteProvider(this::provideYamlKeyCompletions);
                        sourceViewer.setAutocompleteValueProvider(this::provideYamlValueCompletions);
                        sourceViewer.setEndpointValidator(this::validateYamlEndpoints);
                        sourceViewer.setSimpleValidator(this::validateYamlSimple);
                        sourceViewer.setListItemNodeChecker(this::isListChildrenNode);
                        sourceViewer.setEditQuickDocProvider(this::provideEditQuickDoc);
                    } else {
                        sourceViewer.setAutocompleteProvider(null);
                        sourceViewer.setAutocompleteValueProvider(null);
                        sourceViewer.setEditQuickDocProvider(null);
                    }
                } else if (isPropertiesFile(filePath)) {
                    sourceViewer.setQuickDocProvider(this::providePropertiesQuickDocs);
                    sourceViewer.setDeprecatedLineScanner(this::scanDeprecatedProperties);
                    sourceViewer.setAutocompleteProvider(this::providePropertyCompletions);
                    sourceViewer.setAutocompleteValueProvider(this::providePropertyValueCompletions);
                    sourceViewer.setPropertiesValidator(this::validatePropertyLine);
                    sourceViewer.setEditQuickDocProvider(this::provideEditPropertyQuickDoc);
                } else {
                    sourceViewer.setQuickDocProvider(null);
                    sourceViewer.setDeprecatedLineScanner(null);
                    sourceViewer.setAutocompleteProvider(null);
                    sourceViewer.setEditQuickDocProvider(null);
                    sourceViewer.setAutocompleteValueProvider(null);
                }
                sourceViewer.loadFile(filePath);
                if (isCamelSourceFile(filePath)) {
                    sourceViewer.setJumpLinks(computeJumpLinks(filePath));
                }
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

    private List<SourceViewer.DocEntry> provideEditQuickDoc(List<String> lines, int cursorRow) {
        CamelCatalog catalog = getCatalog();
        if (catalog == null || lines == null || cursorRow < 0 || cursorRow >= lines.size()) {
            return List.of();
        }
        String line = lines.get(cursorRow);

        Matcher uriMatcher = YAML_URI_PATTERN.matcher(line);
        if (uriMatcher.find()) {
            String uri = uriMatcher.group(1);
            if (uri.endsWith("\"")) {
                uri = uri.substring(0, uri.length() - 1);
            }
            String component = uri.contains(":") ? uri.substring(0, uri.indexOf(':')) : uri;
            ComponentModel model = catalog.componentModel(component);
            if (model != null) {
                String title = model.getTitle() != null ? model.getTitle() : component;
                String desc = model.getDescription() != null ? model.getDescription() : "";
                return List.of(SourceViewer.DocEntry.of(title + " — " + desc));
            }
        }

        // check if inside a parameters: block — look up component endpoint option doc
        SourceViewer.DocEntry optionDoc = resolveParameterOptionDoc(catalog, lines, cursorRow);
        if (optionDoc != null) {
            return List.of(optionDoc);
        }

        // check if this is an EIP option (e.g., message under log, expression under split)
        SourceViewer.DocEntry eipOptionDoc = resolveEipOptionDoc(catalog, lines, cursorRow);
        if (eipOptionDoc != null) {
            return List.of(eipOptionDoc);
        }

        Matcher keyMatcher = YAML_KEY_PATTERN.matcher(line);
        if (keyMatcher.find()) {
            String key = keyMatcher.group(1);
            EipModel eipModel = catalog.eipModel(key);
            if (eipModel != null) {
                String title = eipModel.getTitle() != null ? eipModel.getTitle() : key;
                String desc = eipModel.getDescription() != null ? eipModel.getDescription() : "";
                return List.of(SourceViewer.DocEntry.of(title + " — " + desc));
            }
        }

        return List.of();
    }

    private SourceViewer.DocEntry resolveEipOptionDoc(CamelCatalog catalog, List<String> lines, int cursorRow) {
        String cursorLine = lines.get(cursorRow);
        String trimmed = cursorLine.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#")) {
            return null;
        }
        if (trimmed.startsWith("- ")) {
            trimmed = trimmed.substring(2).trim();
        }
        int colonIdx = trimmed.indexOf(':');
        if (colonIdx <= 0) {
            return null;
        }
        String optionName = trimmed.substring(0, colonIdx).trim();
        int cursorIndent = countLeadingSpaces(cursorLine);

        // walk up to find the parent EIP
        for (int i = cursorRow - 1; i >= 0; i--) {
            String l = lines.get(i);
            if (l.isBlank()) {
                continue;
            }
            int indent = countLeadingSpaces(l);
            if (indent < cursorIndent) {
                String t = l.trim();
                if (t.startsWith("- ")) {
                    t = t.substring(2).trim();
                }
                int ci = t.indexOf(':');
                if (ci > 0) {
                    String eipName = t.substring(0, ci).trim();
                    EipModel model = catalog.eipModel(eipName);
                    if (model != null) {
                        for (BaseOptionModel opt : model.getOptions()) {
                            if (optionName.equals(opt.getName())) {
                                String desc = formatFullOptionDoc(opt);
                                return desc != null
                                        ? SourceViewer.DocEntry.withTitle(formatOptionTitle(opt), desc)
                                        : null;
                            }
                        }
                    }
                }
                break;
            }
        }
        return null;
    }

    private SourceViewer.DocEntry resolveParameterOptionDoc(CamelCatalog catalog, List<String> lines, int cursorRow) {
        String cursorLine = lines.get(cursorRow);
        String trimmed = cursorLine.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("-")) {
            return null;
        }
        int colonIdx = trimmed.indexOf(':');
        if (colonIdx <= 0) {
            return null;
        }
        String optionName = trimmed.substring(0, colonIdx).trim();
        int cursorIndent = countLeadingSpaces(cursorLine);

        // walk up to find parameters: and then the component URI
        boolean foundParameters = false;
        int parametersIndent = -1;
        for (int i = cursorRow - 1; i >= 0; i--) {
            String l = lines.get(i);
            if (l.isBlank()) {
                continue;
            }
            int indent = countLeadingSpaces(l);
            if (indent < cursorIndent && !foundParameters) {
                String t = l.trim();
                if (t.startsWith("- ")) {
                    t = t.substring(2).trim();
                }
                if (t.equals("parameters:")) {
                    foundParameters = true;
                    parametersIndent = indent;
                    continue;
                }
                break;
            }
            if (foundParameters && indent <= parametersIndent) {
                // look for uri: line at same or lower indent
                String t = l.trim();
                if (t.startsWith("- ")) {
                    t = t.substring(2).trim();
                }
                Matcher m = YAML_URI_PATTERN.matcher(l);
                if (m.find()) {
                    String uri = m.group(1);
                    if (uri.endsWith("\"")) {
                        uri = uri.substring(0, uri.length() - 1);
                    }
                    String comp = uri.contains(":") ? uri.substring(0, uri.indexOf(':')) : uri;
                    ComponentModel model = catalog.componentModel(comp);
                    if (model != null) {
                        for (ComponentModel.EndpointOptionModel opt : model.getEndpointOptions()) {
                            if (optionName.equals(opt.getName())) {
                                String desc = formatFullOptionDoc(opt);
                                return desc != null
                                        ? SourceViewer.DocEntry.withTitle(formatOptionTitle(opt), desc)
                                        : null;
                            }
                        }
                    }
                    break;
                }
                if (indent < parametersIndent) {
                    break;
                }
            }
        }
        return null;
    }

    private static int countLeadingSpaces(String line) {
        int count = 0;
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == ' ') {
                count++;
            } else {
                break;
            }
        }
        return count;
    }

    private List<SourceViewer.DocEntry> provideEditPropertyQuickDoc(List<String> lines, int cursorRow) {
        if (lines == null || cursorRow < 0 || cursorRow >= lines.size()) {
            return List.of();
        }
        String line = lines.get(cursorRow);
        if (line == null) {
            return List.of();
        }
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("!")) {
            return List.of();
        }
        int eq = trimmed.indexOf('=');
        if (eq <= 0) {
            return List.of();
        }
        String key = trimmed.substring(0, eq).trim();

        CamelCatalog catalog = getCatalog();
        if (catalog != null) {
            ensureMainOptionsCache(catalog);
            BaseOptionModel opt = lookupPropertyOption(catalog, key);
            if (opt != null) {
                String desc = formatFullOptionDoc(opt);
                if (desc != null) {
                    String title = formatOptionTitle(opt);
                    return List.of(opt.isDeprecated()
                            ? SourceViewer.DocEntry.deprecated(desc)
                            : SourceViewer.DocEntry.withTitle(title, desc));
                }
            }
        }

        ensureSpringBootMetadataCache();
        if (springBootMetadataCache != null) {
            JsonObject sbProp = springBootMetadataCache.get(key);
            if (sbProp != null) {
                String doc = SpringBootMetadataHelper.formatDoc(sbProp);
                if (doc != null) {
                    boolean deprecated = Boolean.TRUE.equals(sbProp.get("deprecated"));
                    return List.of(deprecated
                            ? SourceViewer.DocEntry.deprecated(doc)
                            : SourceViewer.DocEntry.of(doc));
                }
            }
        }
        return List.of();
    }

    private static boolean isPropertiesFile(Path path) {
        return path.getFileName().toString().toLowerCase().endsWith(".properties");
    }

    private static boolean isYamlFile(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".yaml") || name.endsWith(".yml");
    }

    private List<AutocompletePopup.CompletionItem> providePropertyCompletions(String linePrefix) {
        CamelCatalog catalog = getCatalog();
        if (catalog != null) {
            ensureMainOptionsCache(catalog);
        }
        ensureSpringBootMetadataCache();
        if (catalog == null && (springBootOptionsCache == null || springBootOptionsCache.isEmpty())) {
            return List.of();
        }

        String keyPrefix = linePrefix != null ? linePrefix.trim().toLowerCase() : "";

        List<AutocompletePopup.CompletionItem> items = new ArrayList<>();

        // determine if the prefix matches a specific main group (e.g., camel.main.)
        String matchedGroup = null;
        if (mainGroupsCache != null) {
            for (String groupName : mainGroupsCache.keySet()) {
                String groupPrefix = groupName + ".";
                if (keyPrefix.startsWith(groupPrefix)) {
                    matchedGroup = groupName;
                    break;
                }
            }
        }

        if (matchedGroup != null) {
            // show options within the matched group
            String groupDot = matchedGroup + ".";
            String optFilter = keyPrefix.substring(groupDot.length());
            if (mainOptionsCache != null) {
                for (Map.Entry<String, BaseOptionModel> entry : mainOptionsCache.entrySet()) {
                    if (entry.getKey().startsWith(groupDot)) {
                        String optName = entry.getKey().substring(groupDot.length());
                        if (optFilter.isEmpty() || optName.toLowerCase().contains(optFilter)) {
                            BaseOptionModel opt = entry.getValue();
                            items.add(new AutocompletePopup.CompletionItem(
                                    entry.getKey(), opt.getDescription(), opt.getType(),
                                    opt.getDefaultValue(), opt.isDeprecated(), opt.getDeprecationNote(),
                                    opt.getGroup()));
                        }
                    }
                }
            }
        } else if (keyPrefix.startsWith("camel.component.")) {
            // camel.component.<name>. options
            addPrefixedCompletions(items, catalog, keyPrefix, "camel.component.",
                    catalog.findComponentNames(),
                    name -> {
                        ComponentModel m = catalog.componentModel(name);
                        return m != null ? m.getComponentOptions() : null;
                    });
        } else if (keyPrefix.startsWith("camel.dataformat.")) {
            // camel.dataformat.<name>. options
            addPrefixedCompletions(items, catalog, keyPrefix, "camel.dataformat.",
                    catalog.findDataFormatNames(),
                    name -> {
                        DataFormatModel m = catalog.dataFormatModel(name);
                        return m != null ? m.getOptions() : null;
                    });
        } else if (keyPrefix.startsWith("camel.language.")) {
            // camel.language.<name>. options
            addPrefixedCompletions(items, catalog, keyPrefix, "camel.language.",
                    catalog.findLanguageNames(),
                    name -> {
                        LanguageModel m = catalog.languageModel(name);
                        return m != null ? m.getOptions() : null;
                    });
        } else if (!keyPrefix.isEmpty() && !keyPrefix.startsWith("camel.")
                && springBootOptionsCache != null) {
            // Spring Boot property completions (e.g., server., spring.datasource.)
            addSpringBootCompletions(items, keyPrefix);
        } else {
            // show group-level entries
            if (mainGroupsCache != null) {
                for (Map.Entry<String, String> entry : mainGroupsCache.entrySet()) {
                    String groupKey = entry.getKey() + ".";
                    if (keyPrefix.isEmpty() || groupKey.toLowerCase().contains(keyPrefix)) {
                        items.add(new AutocompletePopup.CompletionItem(
                                groupKey, entry.getValue(), null, null, false, null, null));
                    }
                }
            }
            if (keyPrefix.isEmpty() || "camel.component.".contains(keyPrefix)) {
                items.add(new AutocompletePopup.CompletionItem(
                        "camel.component.", "Component configuration prefix", null, null, false, null, null));
            }
            if (keyPrefix.isEmpty() || "camel.dataformat.".contains(keyPrefix)) {
                items.add(new AutocompletePopup.CompletionItem(
                        "camel.dataformat.", "Data format configuration prefix", null, null, false, null, null));
            }
            if (keyPrefix.isEmpty() || "camel.language.".contains(keyPrefix)) {
                items.add(new AutocompletePopup.CompletionItem(
                        "camel.language.", "Language configuration prefix", null, null, false, null, null));
            }
            // Spring Boot top-level groups
            addSpringBootTopLevelGroups(items, keyPrefix);
        }

        items.sort(Comparator.comparing(AutocompletePopup.CompletionItem::deprecated)
                .thenComparing((a, b) -> {
                    boolean aGroup = a.key().endsWith(".");
                    boolean bGroup = b.key().endsWith(".");
                    if (aGroup != bGroup) {
                        return aGroup ? -1 : 1;
                    }
                    return String.CASE_INSENSITIVE_ORDER.compare(a.key(), b.key());
                }));

        return items;
    }

    private void addPrefixedCompletions(
            List<AutocompletePopup.CompletionItem> items,
            CamelCatalog catalog, String keyPrefix, String prefix,
            List<String> names,
            java.util.function.Function<String, List<? extends BaseOptionModel>> optionsLoader) {
        String rest = keyPrefix.substring(prefix.length());
        int dot = rest.indexOf('.');
        if (dot > 0) {
            String name = rest.substring(0, dot);
            String optPrefix = rest.substring(dot + 1);
            List<? extends BaseOptionModel> options = optionsLoader.apply(name);
            if (options != null) {
                for (BaseOptionModel opt : options) {
                    String fullKey = prefix + name + "." + opt.getName();
                    if (optPrefix.isEmpty() || opt.getName().toLowerCase().contains(optPrefix)) {
                        items.add(new AutocompletePopup.CompletionItem(
                                fullKey, opt.getDescription(), opt.getType(),
                                opt.getDefaultValue(), opt.isDeprecated(), opt.getDeprecationNote(),
                                opt.getGroup()));
                    }
                }
            }
        } else {
            for (String name : names) {
                String fullKey = prefix + name + ".";
                if (rest.isEmpty() || name.toLowerCase().contains(rest)) {
                    items.add(new AutocompletePopup.CompletionItem(
                            fullKey, capitalize(prefix.split("\\.")[1]) + ": " + name,
                            null, null, false, null, null));
                }
            }
        }
    }

    private void addSpringBootCompletions(List<AutocompletePopup.CompletionItem> items, String keyPrefix) {
        // collect next-level sub-groups under this prefix
        Set<String> subGroups = new java.util.TreeSet<>();
        List<Map.Entry<String, BaseOptionModel>> directOptions = new ArrayList<>();

        for (Map.Entry<String, BaseOptionModel> entry : springBootOptionsCache.entrySet()) {
            String key = entry.getKey();
            if (!key.toLowerCase().startsWith(keyPrefix)) {
                continue;
            }
            String rest = key.substring(keyPrefix.length());
            int dot = rest.indexOf('.');
            if (dot > 0) {
                // has sub-group: e.g. "aop.auto" under "spring." → sub-group "aop"
                subGroups.add(keyPrefix + rest.substring(0, dot));
            } else {
                // direct property at this level
                directOptions.add(entry);
            }
        }

        if (subGroups.size() > 1 || (!subGroups.isEmpty() && !directOptions.isEmpty())) {
            // show sub-groups as drill-down entries
            for (String group : subGroups) {
                String groupKey = group + ".";
                items.add(new AutocompletePopup.CompletionItem(
                        groupKey, "Spring Boot configuration", null, null, false, null, null));
            }
            // also show any direct properties at this level
            for (Map.Entry<String, BaseOptionModel> entry : directOptions) {
                BaseOptionModel opt = entry.getValue();
                items.add(new AutocompletePopup.CompletionItem(
                        entry.getKey(), opt.getDescription(), opt.getType(),
                        opt.getDefaultValue(), opt.isDeprecated(), opt.getDeprecationNote(),
                        "Spring Boot"));
            }
        } else {
            // single sub-group or leaf level: show all matching properties
            for (Map.Entry<String, BaseOptionModel> entry : springBootOptionsCache.entrySet()) {
                if (entry.getKey().toLowerCase().startsWith(keyPrefix)) {
                    BaseOptionModel opt = entry.getValue();
                    items.add(new AutocompletePopup.CompletionItem(
                            entry.getKey(), opt.getDescription(), opt.getType(),
                            opt.getDefaultValue(), opt.isDeprecated(), opt.getDeprecationNote(),
                            "Spring Boot"));
                }
            }
        }
    }

    private void addSpringBootTopLevelGroups(
            List<AutocompletePopup.CompletionItem> items, String keyPrefix) {
        if (springBootGroupsCache == null || springBootGroupsCache.isEmpty()) {
            return;
        }
        Set<String> topLevelGroups = new java.util.TreeSet<>();
        for (String group : springBootGroupsCache.keySet()) {
            int dot = group.indexOf('.');
            String topLevel = dot > 0 ? group.substring(0, dot) : group;
            topLevelGroups.add(topLevel);
        }
        for (String group : topLevelGroups) {
            String groupKey = group + ".";
            if (keyPrefix.isEmpty() || groupKey.toLowerCase().contains(keyPrefix)) {
                items.add(new AutocompletePopup.CompletionItem(
                        groupKey, "Spring Boot configuration", null, null, false, null, null));
            }
        }
    }

    private List<AutocompletePopup.CompletionItem> providePropertyValueCompletions(String key) {
        CamelCatalog catalog = getCatalog();
        if (key == null || key.isEmpty()) {
            return loadPropertyPlaceholders();
        }
        if (catalog != null) {
            ensureMainOptionsCache(catalog);
        }
        ensureSpringBootMetadataCache();

        BaseOptionModel opt = lookupOption(catalog, key);
        if (opt == null) {
            // check Spring Boot hints even without a matching option model
            List<AutocompletePopup.CompletionItem> hintItems = lookupSpringBootHints(key);
            if (!hintItems.isEmpty()) {
                hintItems.addAll(loadPropertyPlaceholders());
                return hintItems;
            }
            return loadPropertyPlaceholders();
        }

        String optDesc = opt.getDescription();
        String optType = opt.getType();
        Object optDefault = opt.getDefaultValue();
        String optGroup = opt.getGroup();

        List<AutocompletePopup.CompletionItem> items = new ArrayList<>();
        java.util.function.Predicate<String> valueFilter = null;

        // enum values
        List<String> enums = opt.getEnums();
        if (enums != null && !enums.isEmpty()) {
            java.util.Set<String> validValues = new java.util.HashSet<>();
            for (String value : enums) {
                validValues.add(value.toLowerCase());
                boolean isDefault = value.equals(String.valueOf(optDefault));
                items.add(new AutocompletePopup.CompletionItem(
                        value, optDesc, optType, isDefault ? value : optDefault,
                        false, null, optGroup));
            }
            valueFilter = v -> validValues.contains(v.toLowerCase());
        } else if ("boolean".equalsIgnoreCase(optType) || "java.lang.Boolean".equals(opt.getJavaType())) {
            valueFilter = v -> "true".equalsIgnoreCase(v) || "false".equalsIgnoreCase(v);
            items.add(new AutocompletePopup.CompletionItem(
                    "true", optDesc, "boolean", optDefault, false, null, optGroup));
            items.add(new AutocompletePopup.CompletionItem(
                    "false", optDesc, "boolean", optDefault, false, null, optGroup));
        } else if (isNumericType(optType, opt.getJavaType())) {
            valueFilter = SourceTab::isNumericValue;
        }

        // Spring Boot hints for values without enum metadata
        if (items.isEmpty()) {
            items.addAll(lookupSpringBootHints(key));
        }

        // only include placeholders whose actual value is compatible with the option type
        for (AutocompletePopup.CompletionItem ph : loadPropertyPlaceholders()) {
            if (valueFilter == null || (ph.description() != null && valueFilter.test(ph.description()))) {
                items.add(ph);
            }
        }
        return items;
    }

    private List<AutocompletePopup.CompletionItem> lookupSpringBootHints(String key) {
        List<AutocompletePopup.CompletionItem> items = new ArrayList<>();
        if (springBootHintsCache != null) {
            List<String> hintValues = springBootHintsCache.get(key);
            if (hintValues != null) {
                for (String value : hintValues) {
                    items.add(new AutocompletePopup.CompletionItem(
                            value, null, null, null, false, null, "Spring Boot"));
                }
            }
        }
        return items;
    }

    private BaseOptionModel lookupOption(CamelCatalog catalog, String key) {
        // camel.main.* options
        if (mainOptionsCache != null && mainOptionsCache.containsKey(key)) {
            return mainOptionsCache.get(key);
        }

        if (catalog == null) {
            // no Camel catalog — only Spring Boot options available
            if (springBootOptionsCache != null && springBootOptionsCache.containsKey(key)) {
                return springBootOptionsCache.get(key);
            }
            return null;
        }

        // camel.component.<name>.<option>
        if (key.startsWith("camel.component.")) {
            return lookupPrefixedOption(catalog, key, "camel.component.",
                    name -> {
                        ComponentModel m = catalog.componentModel(name);
                        return m != null ? m.getComponentOptions() : null;
                    });
        }
        // camel.dataformat.<name>.<option>
        if (key.startsWith("camel.dataformat.")) {
            return lookupPrefixedOption(catalog, key, "camel.dataformat.",
                    name -> {
                        DataFormatModel m = catalog.dataFormatModel(name);
                        return m != null ? m.getOptions() : null;
                    });
        }
        // camel.language.<name>.<option>
        if (key.startsWith("camel.language.")) {
            return lookupPrefixedOption(catalog, key, "camel.language.",
                    name -> {
                        LanguageModel m = catalog.languageModel(name);
                        return m != null ? m.getOptions() : null;
                    });
        }
        // Spring Boot options
        if (springBootOptionsCache != null && springBootOptionsCache.containsKey(key)) {
            return springBootOptionsCache.get(key);
        }
        return null;
    }

    private BaseOptionModel lookupPrefixedOption(
            CamelCatalog catalog, String key, String prefix,
            java.util.function.Function<String, List<? extends BaseOptionModel>> optionsLoader) {
        String rest = key.substring(prefix.length());
        int dot = rest.indexOf('.');
        if (dot > 0) {
            String name = rest.substring(0, dot);
            String optName = rest.substring(dot + 1);
            List<? extends BaseOptionModel> options = optionsLoader.apply(name);
            if (options != null) {
                for (BaseOptionModel opt : options) {
                    if (opt.getName().equals(optName)) {
                        return opt;
                    }
                }
            }
        }
        return null;
    }

    private static boolean isNumericType(String type, String javaType) {
        if (type != null) {
            switch (type.toLowerCase()) {
                case "integer":
                case "int":
                case "long":
                case "short":
                case "byte":
                case "float":
                case "double":
                case "number":
                    return true;
            }
        }
        if (javaType != null) {
            switch (javaType) {
                case "int":
                case "long":
                case "short":
                case "byte":
                case "float":
                case "double":
                case "java.lang.Integer":
                case "java.lang.Long":
                case "java.lang.Short":
                case "java.lang.Byte":
                case "java.lang.Float":
                case "java.lang.Double":
                    return true;
            }
        }
        return false;
    }

    private static boolean isNumericValue(String value) {
        try {
            Double.parseDouble(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // ---- YAML DSL completion ----

    private List<AutocompletePopup.CompletionItem> provideYamlKeyCompletions(String context) {
        if (context == null) {
            return List.of();
        }

        // component name completion on uri: lines
        if (context.startsWith("yaml-uri:")) {
            return provideComponentNameCompletions(context.substring(9));
        }

        // tree-driven YAML DSL completion (EIPs, expressions, languages, data formats, route options, top-level)
        if (context.startsWith("yaml-tree:")) {
            return provideTreeCompletions(context.substring(10));
        }

        if (!context.startsWith("yaml:")) {
            return List.of();
        }
        CamelCatalog catalog = getCatalog();
        if (catalog == null) {
            return List.of();
        }

        // context format: "yaml:componentName:consumer|producer[:existingKey1,existingKey2,...][|uri]"
        String contextBody = context.substring(5);
        String uri = null;
        int pipeIdx = contextBody.indexOf('|');
        if (pipeIdx >= 0) {
            uri = contextBody.substring(pipeIdx + 1);
            contextBody = contextBody.substring(0, pipeIdx);
        }
        String[] parts = contextBody.split(":", 3);
        if (parts.length < 2) {
            return List.of();
        }
        String componentName = parts[0];
        String role = parts[1];
        boolean isConsumer = "consumer".equals(role);

        Set<String> existingKeys = new HashSet<>();
        if (parts.length > 2 && !parts[2].isEmpty()) {
            existingKeys.addAll(Arrays.asList(parts[2].split(",")));
        }

        ComponentModel model = catalog.componentModel(componentName);
        if (model == null) {
            return List.of();
        }

        // use the catalog to parse the URI and find parameters already set via the context path
        if (uri != null) {
            try {
                existingKeys.addAll(catalog.endpointProperties(uri).keySet());
            } catch (Exception e) {
                // ignore
            }
        }

        // build a set of multi-valued option names so we can allow duplicates
        Set<String> multiValuedOptions = new HashSet<>();
        for (ComponentModel.EndpointOptionModel opt : model.getEndpointOptions()) {
            if (opt.isMultiValue()) {
                multiValuedOptions.add(opt.getName());
            }
        }

        List<AutocompletePopup.CompletionItem> items = new ArrayList<>();
        for (ComponentModel.EndpointOptionModel opt : model.getEndpointOptions()) {
            if (!includeEndpointOption(opt, isConsumer)) {
                continue;
            }
            if (existingKeys.contains(opt.getName()) && !multiValuedOptions.contains(opt.getName())) {
                continue;
            }
            items.add(new AutocompletePopup.CompletionItem(
                    opt.getName(), opt.getDescription(), opt.getType(),
                    opt.getDefaultValue(), opt.isDeprecated(), opt.getDeprecationNote(),
                    opt.getGroup(), opt.isRequired()));
        }

        items.sort(Comparator.comparing(AutocompletePopup.CompletionItem::deprecated)
                .thenComparing((a, b) -> Boolean.compare(b.required(), a.required()))
                .thenComparing(AutocompletePopup.CompletionItem::key, String.CASE_INSENSITIVE_ORDER));
        return items;
    }

    private List<AutocompletePopup.CompletionItem> provideComponentNameCompletions(String role) {
        CamelCatalog catalog = getCatalog();
        if (catalog == null) {
            return List.of();
        }

        boolean isConsumer = "consumer".equals(role);
        IntegrationInfo info = ctx.findSelectedIntegration();
        String version = info != null ? info.camelVersion : null;

        // rebuild cache if catalog version changed
        if (version != null && !version.equals(componentsCatalogVersion)) {
            componentsCatalogVersion = version;
            consumerComponents = null;
            producerComponents = null;
        }

        List<AutocompletePopup.CompletionItem> cached = isConsumer ? consumerComponents : producerComponents;
        if (cached != null) {
            return cached;
        }

        List<AutocompletePopup.CompletionItem> items = new ArrayList<>();
        for (String name : catalog.findComponentNames()) {
            ComponentModel model = catalog.componentModel(name);
            if (model == null) {
                continue;
            }
            if (isConsumer && model.isProducerOnly()) {
                continue;
            }
            if (!isConsumer && model.isConsumerOnly()) {
                continue;
            }
            String labels = model.getLabel();
            String firstLabel = labels != null && !labels.isEmpty()
                    ? labels.split(",")[0].trim()
                    : "component";
            items.add(new AutocompletePopup.CompletionItem(
                    name, model.getTitle() + " - " + model.getDescription(),
                    firstLabel, null, model.isDeprecated(), model.getDeprecationNote(),
                    labels));
        }
        items.sort(Comparator.comparing(AutocompletePopup.CompletionItem::deprecated)
                .thenComparing(AutocompletePopup.CompletionItem::key, String.CASE_INSENSITIVE_ORDER));

        if (isConsumer) {
            consumerComponents = items;
        } else {
            producerComponents = items;
        }
        return items;
    }

    private boolean isListChildrenNode(String nodeName) {
        JsonObject node = getTreeNode(nodeName);
        return node != null && Boolean.TRUE.equals(node.get("listChildren"));
    }

    private static final Set<String> TREE_BOILERPLATE = Set.of("id", "note", "description", "disabled");

    private JsonObject getCompletionTree() {
        if (!completionTreeLoaded) {
            completionTreeLoaded = true;
            // try bundled resource first
            try (var is = getClass().getResourceAsStream("/schema/camelYamlDsl-model.json")) {
                if (is != null) {
                    String json = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    completionTree = (JsonObject) org.apache.camel.util.json.Jsoner.deserialize(json);
                }
            } catch (Exception e) {
                // ignore
            }
            // fallback: try catalog version manager (may have a different Camel version)
            if (completionTree == null) {
                CamelCatalog cat = getCatalog();
                if (cat != null) {
                    try (var is = cat.getVersionManager()
                            .getResourceAsStream("org/apache/camel/catalog/schemas/camelYamlDsl-model.json")) {
                        if (is != null) {
                            String json = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                            completionTree = (JsonObject) org.apache.camel.util.json.Jsoner.deserialize(json);
                        }
                    } catch (Exception e) {
                        // ignore — tree not available for this Camel version
                    }
                }
            }
        }
        return completionTree;
    }

    private JsonObject getTreeNode(String nodeName) {
        JsonObject tree = getCompletionTree();
        if (tree == null) {
            return null;
        }
        JsonObject nodes = (JsonObject) tree.get("nodes");
        if (nodes == null) {
            return null;
        }
        return (JsonObject) nodes.get(nodeName);
    }

    private List<AutocompletePopup.CompletionItem> provideTreeCompletions(String contextAfterPrefix) {
        // context format: "nodeName" or "nodeName:existingKey1,existingKey2,..."
        String[] parts = contextAfterPrefix.split(":", 2);
        String nodeName = parts[0];

        Set<String> existingKeys = Set.of();
        if (parts.length > 1 && !parts[1].isEmpty()) {
            existingKeys = new HashSet<>(Arrays.asList(parts[1].split(",")));
        }

        JsonObject node = getTreeNode(nodeName);
        if (node == null) {
            return List.of();
        }

        JsonArray children = (JsonArray) node.get("children");
        if (children == null) {
            return List.of();
        }

        List<AutocompletePopup.CompletionItem> items = new ArrayList<>();
        for (Object obj : children) {
            JsonObject child = (JsonObject) obj;
            String name = (String) child.get("name");
            if (name == null) {
                continue;
            }
            if (TREE_BOILERPLATE.contains(name)) {
                continue;
            }
            if (existingKeys.contains(name)) {
                continue;
            }

            String desc = (String) child.get("description");
            String type = (String) child.get("type");
            String group = (String) child.get("group");
            String label = (String) child.get("label");
            Object defVal = child.get("default");
            boolean required = Boolean.TRUE.equals(child.get("required"));
            boolean deprecated = Boolean.TRUE.equals(child.get("deprecated"));
            String depNote = (String) child.get("deprecationNote");

            items.add(new AutocompletePopup.CompletionItem(
                    name, desc, type, defVal, deprecated, depNote, group != null ? group : label, required));
        }

        items.sort(Comparator.comparing(AutocompletePopup.CompletionItem::deprecated)
                .thenComparing((a, b) -> {
                    boolean aIsSteps = "steps".equals(a.key()) || "outputs".equals(a.key());
                    boolean bIsSteps = "steps".equals(b.key()) || "outputs".equals(b.key());
                    return Boolean.compare(aIsSteps, bIsSteps);
                })
                .thenComparing((a, b) -> Boolean.compare(b.required(), a.required())));
        return items;
    }

    private List<AutocompletePopup.CompletionItem> provideTreeValueCompletions(String contextAfterPrefix) {
        // context format: "nodeName:optionName"
        String[] parts = contextAfterPrefix.split(":", 2);
        if (parts.length < 2) {
            return List.of();
        }
        String nodeName = parts[0];
        String optionName = parts[1];

        JsonObject node = getTreeNode(nodeName);
        if (node == null) {
            return loadPropertyPlaceholders();
        }

        JsonArray children = (JsonArray) node.get("children");
        if (children == null) {
            return loadPropertyPlaceholders();
        }

        // find the matching child
        JsonObject matchedChild = null;
        for (Object obj : children) {
            JsonObject child = (JsonObject) obj;
            if (optionName.equals(child.get("name"))) {
                matchedChild = child;
                break;
            }
        }
        if (matchedChild == null) {
            return loadPropertyPlaceholders();
        }

        List<AutocompletePopup.CompletionItem> items = new ArrayList<>();
        String type = (String) matchedChild.get("type");
        String desc = (String) matchedChild.get("description");
        Object defVal = matchedChild.get("default");
        String group = (String) matchedChild.get("group");

        java.util.function.Predicate<String> valueFilter = null;
        JsonArray enumValues = (JsonArray) matchedChild.get("enum");
        if (enumValues != null && !enumValues.isEmpty()) {
            Set<String> validValues = new HashSet<>();
            for (Object e : enumValues) {
                String value = String.valueOf(e);
                validValues.add(value.toLowerCase());
                boolean isDefault = value.equals(String.valueOf(defVal));
                items.add(new AutocompletePopup.CompletionItem(
                        value, desc, type, isDefault ? value : defVal, false, null, group));
            }
            valueFilter = v -> validValues.contains(v.toLowerCase());
        } else if ("boolean".equalsIgnoreCase(type)) {
            valueFilter = v -> "true".equalsIgnoreCase(v) || "false".equalsIgnoreCase(v);
            items.add(new AutocompletePopup.CompletionItem(
                    "true", desc, "boolean", defVal, false, null, group));
            items.add(new AutocompletePopup.CompletionItem(
                    "false", desc, "boolean", defVal, false, null, group));
        } else if ("number".equalsIgnoreCase(type) || "integer".equalsIgnoreCase(type)) {
            valueFilter = SourceTab::isNumericValue;
        }

        for (AutocompletePopup.CompletionItem ph : loadPropertyPlaceholders()) {
            if (valueFilter == null || (ph.description() != null && valueFilter.test(ph.description()))) {
                items.add(ph);
            }
        }
        return items;
    }

    private static boolean includeEndpointOption(ComponentModel.EndpointOptionModel opt, boolean isConsumer) {
        String label = opt.getLabel();
        if (label == null || label.isEmpty()) {
            return true;
        }
        if (label.contains("consumer") && label.contains("producer")) {
            return true;
        }
        if (isConsumer) {
            return !label.contains("producer");
        } else {
            return !label.contains("consumer");
        }
    }

    private List<AutocompletePopup.CompletionItem> provideYamlValueCompletions(String context) {
        if (context == null) {
            return List.of();
        }

        // EIP value completion
        // tree-driven value completion
        if (context.startsWith("yaml-tree-value:")) {
            return provideTreeValueCompletions(context.substring(16));
        }

        if (!context.startsWith("yaml:")) {
            return List.of();
        }
        CamelCatalog catalog = getCatalog();
        if (catalog == null) {
            return List.of();
        }

        // context format: "yaml:componentName:optionName"
        String[] parts = context.substring(5).split(":", 2);
        if (parts.length < 2) {
            return List.of();
        }
        String componentName = parts[0];
        String optionName = parts[1];

        ComponentModel model = catalog.componentModel(componentName);
        if (model == null) {
            return loadPropertyPlaceholders();
        }

        ComponentModel.EndpointOptionModel opt = null;
        for (ComponentModel.EndpointOptionModel o : model.getEndpointOptions()) {
            if (o.getName().equals(optionName)) {
                opt = o;
                break;
            }
        }

        List<AutocompletePopup.CompletionItem> items = new ArrayList<>();
        java.util.function.Predicate<String> valueFilter = null;

        if (opt != null) {
            List<String> enums = opt.getEnums();
            if (enums != null && !enums.isEmpty()) {
                java.util.Set<String> validValues = new java.util.HashSet<>();
                for (String value : enums) {
                    validValues.add(value.toLowerCase());
                    boolean isDefault = value.equals(String.valueOf(opt.getDefaultValue()));
                    items.add(new AutocompletePopup.CompletionItem(
                            value, opt.getDescription(), opt.getType(),
                            isDefault ? value : opt.getDefaultValue(),
                            false, null, opt.getGroup()));
                }
                valueFilter = v -> validValues.contains(v.toLowerCase());
            } else if ("boolean".equalsIgnoreCase(opt.getType())
                    || "java.lang.Boolean".equals(opt.getJavaType())) {
                valueFilter = v -> "true".equalsIgnoreCase(v) || "false".equalsIgnoreCase(v);
                items.add(new AutocompletePopup.CompletionItem(
                        "true", opt.getDescription(), "boolean", opt.getDefaultValue(),
                        false, null, opt.getGroup()));
                items.add(new AutocompletePopup.CompletionItem(
                        "false", opt.getDescription(), "boolean", opt.getDefaultValue(),
                        false, null, opt.getGroup()));
            } else if (isNumericType(opt.getType(), opt.getJavaType())) {
                valueFilter = SourceTab::isNumericValue;
            }
        }

        // only include placeholders whose actual value is compatible with the option type
        for (AutocompletePopup.CompletionItem ph : loadPropertyPlaceholders()) {
            if (valueFilter == null || (ph.description() != null && valueFilter.test(ph.description()))) {
                items.add(ph);
            }
        }
        return items;
    }

    private List<AutocompletePopup.CompletionItem> provideEipValueCompletions(String contextAfterPrefix) {
        CamelCatalog catalog = getCatalog();
        if (catalog == null) {
            return List.of();
        }

        // context format: "eipName:optionName"
        String[] parts = contextAfterPrefix.split(":", 2);
        if (parts.length < 2) {
            return List.of();
        }
        String eipName = parts[0];
        String optionName = parts[1];

        EipModel model = catalog.eipModel(eipName);
        if (model == null) {
            return loadPropertyPlaceholders();
        }

        EipModel.EipOptionModel opt = null;
        for (EipModel.EipOptionModel o : model.getOptions()) {
            if (o.getName().equals(optionName)) {
                opt = o;
                break;
            }
        }

        List<AutocompletePopup.CompletionItem> items = new ArrayList<>();
        java.util.function.Predicate<String> valueFilter = null;
        if (opt != null) {
            List<String> enums = opt.getEnums();
            if (enums != null && !enums.isEmpty()) {
                java.util.Set<String> validValues = new java.util.HashSet<>();
                for (String value : enums) {
                    validValues.add(value.toLowerCase());
                    boolean isDefault = value.equals(String.valueOf(opt.getDefaultValue()));
                    items.add(new AutocompletePopup.CompletionItem(
                            value, opt.getDescription(), opt.getType(),
                            isDefault ? value : opt.getDefaultValue(),
                            false, null, opt.getGroup()));
                }
                valueFilter = v -> validValues.contains(v.toLowerCase());
            } else if ("boolean".equalsIgnoreCase(opt.getType())
                    || "java.lang.Boolean".equals(opt.getJavaType())) {
                valueFilter = v -> "true".equalsIgnoreCase(v) || "false".equalsIgnoreCase(v);
                items.add(new AutocompletePopup.CompletionItem(
                        "true", opt.getDescription(), "boolean", opt.getDefaultValue(),
                        false, null, opt.getGroup()));
                items.add(new AutocompletePopup.CompletionItem(
                        "false", opt.getDescription(), "boolean", opt.getDefaultValue(),
                        false, null, opt.getGroup()));
            } else if (isNumericType(opt.getType(), opt.getJavaType())) {
                valueFilter = SourceTab::isNumericValue;
            }
        }

        // only include placeholders whose actual value is compatible with the option type
        for (AutocompletePopup.CompletionItem ph : loadPropertyPlaceholders()) {
            if (valueFilter == null || (ph.description() != null && valueFilter.test(ph.description()))) {
                items.add(ph);
            }
        }
        return items;
    }

    // ---- Property placeholder loading ----

    private List<AutocompletePopup.CompletionItem> placeholderCache;
    private long placeholderCacheTime;
    private Path placeholderCacheDir;

    private List<AutocompletePopup.CompletionItem> loadPropertyPlaceholders() {
        if (rootDir == null || !java.nio.file.Files.isDirectory(rootDir)) {
            return List.of();
        }

        long now = System.currentTimeMillis();
        if (placeholderCache != null && rootDir.equals(placeholderCacheDir) && (now - placeholderCacheTime) < 5000) {
            return placeholderCache;
        }

        List<AutocompletePopup.CompletionItem> items = new ArrayList<>();
        try (var stream = java.nio.file.Files.list(rootDir)) {
            stream.filter(p -> p.getFileName().toString().endsWith(".properties"))
                    .forEach(p -> {
                        try {
                            for (String line : java.nio.file.Files.readAllLines(p)) {
                                String trimmed = line.trim();
                                if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("!")) {
                                    continue;
                                }
                                int eq = trimmed.indexOf('=');
                                if (eq > 0) {
                                    String key = trimmed.substring(0, eq).trim();
                                    String value = trimmed.substring(eq + 1).trim();
                                    items.add(new AutocompletePopup.CompletionItem(
                                            "{{" + key + "}}", value, "placeholder",
                                            null, false, null, p.getFileName().toString()));
                                }
                            }
                        } catch (IOException e) {
                            // skip unreadable files
                        }
                    });
        } catch (IOException e) {
            return List.of();
        }

        items.sort(Comparator.comparing(AutocompletePopup.CompletionItem::key, String.CASE_INSENSITIVE_ORDER));
        placeholderCache = items;
        placeholderCacheTime = now;
        placeholderCacheDir = rootDir;
        return items;
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

    private String validatePropertyLine(String line) {
        CamelCatalog catalog = getCatalog();
        if (catalog != null) {
            try {
                ConfigurationPropertiesValidationResult result = catalog.validateConfigurationProperty(line);
                if (result.isAccepted()) {
                    if (!result.isSuccess()) {
                        String msg = result.summaryErrorMessage(false);
                        if (msg != null) {
                            return msg.trim();
                        }
                    }
                    return null;
                }
            } catch (Exception e) {
                // ignore validation errors
            }
        }
        // validate Spring Boot properties
        return validateSpringBootPropertyLine(line);
    }

    private String validateSpringBootPropertyLine(String line) {
        ensureSpringBootMetadataCache();
        if (springBootOptionsCache == null || springBootOptionsCache.isEmpty()) {
            return null;
        }
        String trimmed = line.trim();
        int eq = trimmed.indexOf('=');
        if (eq <= 0) {
            return null;
        }
        String key = trimmed.substring(0, eq).trim();
        // only validate keys that look like Spring Boot properties
        if (key.startsWith("camel.") || key.startsWith("#") || key.startsWith("!")) {
            return null;
        }
        // check if the key exists in Spring Boot metadata
        if (!springBootOptionsCache.containsKey(key)) {
            // check if it's a known prefix (partial key) — don't flag those
            for (String known : springBootOptionsCache.keySet()) {
                if (known.startsWith(key + ".")) {
                    return null;
                }
            }
            // check if it belongs to a known Spring Boot group
            boolean inSpringBootNamespace = false;
            for (String group : springBootGroupsCache.keySet()) {
                if (key.startsWith(group + ".")) {
                    inSpringBootNamespace = true;
                    break;
                }
            }
            if (inSpringBootNamespace) {
                return "Unknown Spring Boot property: " + key;
            }
        }
        return null;
    }

    private List<String> validateYamlEndpoints(String content) {
        CamelCatalog catalog = getCatalog();
        if (catalog == null) {
            return List.of();
        }
        return doValidateYamlEndpoints(content, catalog);
    }

    private List<String> validateYamlSimple(String content) {
        CamelCatalog catalog = getCatalog();
        if (catalog == null) {
            return List.of();
        }
        return doValidateYamlSimple(content, catalog);
    }

    private static final Set<String> PREDICATE_EIPS = Set.of(
            "filter", "when", "validate", "onWhen", "on-when",
            "handled", "continued", "retryWhile", "retry-while",
            "completionPredicate", "completion-predicate",
            "completion", "loopDoWhile", "loop-do-while");

    static List<String> doValidateYamlSimple(String content, CamelCatalog catalog) {
        List<String> errors = new ArrayList<>();
        String[] lines = content.split("\n", -1);

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.isBlank()) {
                continue;
            }
            String trimmed = line.trim();
            if (trimmed.startsWith("#")) {
                continue;
            }

            String simpleText = null;
            int lineNum = i + 1;
            int lineIndent = countLeadingSpaces(line);
            boolean isLogMessage = false;

            // Strip YAML list prefix for matching
            String key = trimmed.startsWith("- ") ? trimmed.substring(2) : trimmed;

            // Match "simple: <value>" (inline shorthand)
            if (key.startsWith("simple:") && !key.equals("simple:")) {
                simpleText = extractYamlValue(key, "simple");
            }
            // Match "simple:" followed by "expression: <value>" on next line
            else if (key.equals("simple:")) {
                for (int j = i + 1; j < lines.length; j++) {
                    String next = lines[j].trim();
                    if (next.isBlank()) {
                        continue;
                    }
                    if (next.startsWith("expression:")) {
                        simpleText = extractYamlValue(next, "expression");
                        lineNum = j + 1;
                    }
                    break;
                }
            }
            // Match "message: <value>" under log: EIP
            else if (key.startsWith("message:") && !key.equals("message:")) {
                String parentEip = findParentEip(lines, i, lineIndent);
                if ("log".equals(parentEip)) {
                    simpleText = extractYamlValue(key, "message");
                    isLogMessage = true;
                }
            }

            if (simpleText == null || simpleText.isEmpty()) {
                continue;
            }
            // Skip placeholder-only expressions
            if (simpleText.startsWith("{{") && simpleText.endsWith("}}")) {
                continue;
            }

            // Determine predicate vs expression context
            boolean predicate = false;
            if (!isLogMessage) {
                String parentEip = findParentEip(lines, i, lineIndent);
                predicate = parentEip != null && PREDICATE_EIPS.contains(parentEip);
            }

            try {
                LanguageValidationResult result = predicate
                        ? catalog.validateLanguagePredicate(null, "simple", simpleText)
                        : catalog.validateLanguageExpression(null, "simple", simpleText);
                if (!result.isSuccess()) {
                    String error = result.getShortError() != null ? result.getShortError() : result.getError();
                    if (error != null) {
                        errors.add("Line " + lineNum + ": Simple syntax error: " + error);
                    }
                }
            } catch (Exception e) {
                // best effort
            }
        }
        return errors;
    }

    private static String findParentEip(String[] lines, int lineIdx, int lineIndent) {
        for (int j = lineIdx - 1; j >= 0; j--) {
            String prev = lines[j];
            if (prev.isBlank()) {
                continue;
            }
            int prevIndent = countLeadingSpaces(prev);
            if (prevIndent < lineIndent) {
                return extractEipFromLine(prev.trim());
            }
        }
        return null;
    }

    static List<String> doValidateYamlEndpoints(String content, CamelCatalog catalog) {
        List<String> errors = new ArrayList<>();
        String[] lines = content.split("\n", -1);

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.isBlank()) {
                continue;
            }
            String trimmed = line.trim();
            if (trimmed.startsWith("#")) {
                continue;
            }

            Matcher m = YAML_URI_PATTERN.matcher(line);
            if (!m.find()) {
                continue;
            }

            String uri = m.group(1);
            if (uri.endsWith("\"")) {
                uri = uri.substring(0, uri.length() - 1);
            }
            if (uri.startsWith("{{")) {
                continue;
            }
            // scheme-only URI (e.g., "uri: timer") needs a colon for catalog parsing
            if (!uri.contains(":")) {
                uri = uri + ":";
            }

            String eipName = extractEipFromLine(trimmed);
            int lineIndent = countLeadingSpaces(line);

            // for "uri:" lines, walk backwards to find the parent EIP (from, to, etc.)
            if ("uri".equals(eipName)) {
                for (int j = i - 1; j >= 0; j--) {
                    String prev = lines[j];
                    if (prev.isBlank()) {
                        continue;
                    }
                    int prevIndent = countLeadingSpaces(prev);
                    if (prevIndent < lineIndent) {
                        eipName = extractEipFromLine(prev.trim());
                        break;
                    }
                }
            }

            boolean consumerOnly = eipName != null && SourceViewer.CONSUMER_EIPS.contains(eipName);
            boolean producerOnly = eipName != null && SourceViewer.PRODUCER_EIPS.contains(eipName);

            // look ahead for a parameters: block at the same indent level as uri
            StringBuilder uriBuilder = new StringBuilder(uri);
            boolean hasParams = uri.contains("?");
            Map<String, Integer> optionLineMap = new LinkedHashMap<>();
            for (int j = i + 1; j < lines.length; j++) {
                String next = lines[j];
                if (next.isBlank()) {
                    continue;
                }
                int nextIndent = countLeadingSpaces(next);
                if (nextIndent < lineIndent) {
                    break;
                }
                String nextTrimmed = next.trim();
                if (nextIndent == lineIndent && nextTrimmed.startsWith("parameters:")) {
                    int paramBlockIndent = nextIndent;
                    for (int k = j + 1; k < lines.length; k++) {
                        String paramLine = lines[k];
                        if (paramLine.isBlank()) {
                            continue;
                        }
                        int paramIndent = countLeadingSpaces(paramLine);
                        if (paramIndent <= paramBlockIndent) {
                            break;
                        }
                        String paramTrimmed = paramLine.trim();
                        int colonPos = paramTrimmed.indexOf(':');
                        if (colonPos > 0) {
                            String key = paramTrimmed.substring(0, colonPos).trim();
                            String val = paramTrimmed.substring(colonPos + 1).trim();
                            if (val.startsWith("\"") && val.endsWith("\"") && val.length() > 1) {
                                val = val.substring(1, val.length() - 1);
                            } else if (val.startsWith("'") && val.endsWith("'") && val.length() > 1) {
                                val = val.substring(1, val.length() - 1);
                            }
                            char sep = hasParams ? '&' : '?';
                            uriBuilder.append(sep).append(key).append('=').append(val);
                            hasParams = true;
                            optionLineMap.put(key, k);
                        }
                    }
                    break;
                }
                if (nextIndent == lineIndent) {
                    break;
                }
            }

            String fullUri = uriBuilder.toString();
            try {
                EndpointValidationResult result
                        = catalog.validateEndpointProperties(fullUri, false, consumerOnly, producerOnly);
                if (!result.isSuccess()) {
                    String scheme = fullUri.contains(":") ? fullUri.substring(0, fullUri.indexOf(':')) : fullUri;
                    collectEndpointErrors(errors, result, scheme, i, optionLineMap);
                }
            } catch (Exception e) {
                // ignore validation errors
            }
        }
        return errors;
    }

    private static void collectEndpointErrors(
            List<String> errors, EndpointValidationResult result, String scheme,
            int uriLineIdx, Map<String, Integer> optionLineMap) {
        if (result.getUnknown() != null) {
            for (String name : result.getUnknown()) {
                StringBuilder sb = new StringBuilder(scheme).append(": Unknown option '").append(name).append("'");
                if (result.getUnknownSuggestions() != null) {
                    String[] suggestions = result.getUnknownSuggestions().get(name);
                    if (suggestions != null && suggestions.length > 0) {
                        sb.append(". Did you mean: ").append(Arrays.asList(suggestions));
                    }
                }
                errors.add(linePrefix(optionLineMap.getOrDefault(name, uriLineIdx)) + sb);
            }
        }
        if (result.getInvalidBoolean() != null) {
            for (Map.Entry<String, String> entry : result.getInvalidBoolean().entrySet()) {
                errors.add(linePrefix(optionLineMap.getOrDefault(entry.getKey(), uriLineIdx))
                           + scheme + ": Invalid boolean value '" + entry.getValue() + "' for option '" + entry.getKey() + "'");
            }
        }
        if (result.getInvalidInteger() != null) {
            for (Map.Entry<String, String> entry : result.getInvalidInteger().entrySet()) {
                errors.add(linePrefix(optionLineMap.getOrDefault(entry.getKey(), uriLineIdx))
                           + scheme + ": Invalid integer value '" + entry.getValue() + "' for option '" + entry.getKey() + "'");
            }
        }
        if (result.getInvalidNumber() != null) {
            for (Map.Entry<String, String> entry : result.getInvalidNumber().entrySet()) {
                errors.add(linePrefix(optionLineMap.getOrDefault(entry.getKey(), uriLineIdx))
                           + scheme + ": Invalid number value '" + entry.getValue() + "' for option '" + entry.getKey() + "'");
            }
        }
        if (result.getInvalidEnum() != null) {
            for (Map.Entry<String, String> entry : result.getInvalidEnum().entrySet()) {
                StringBuilder sb = new StringBuilder(scheme)
                        .append(": Invalid enum value '").append(entry.getValue())
                        .append("' for option '").append(entry.getKey()).append("'");
                if (result.getInvalidEnumChoices() != null) {
                    String[] choices = result.getInvalidEnumChoices().get(entry.getKey());
                    if (choices != null) {
                        sb.append(". Possible values: ").append(Arrays.asList(choices));
                    }
                }
                errors.add(linePrefix(optionLineMap.getOrDefault(entry.getKey(), uriLineIdx)) + sb);
            }
        }
        if (result.getNotConsumerOnly() != null) {
            for (String name : result.getNotConsumerOnly()) {
                errors.add(linePrefix(optionLineMap.getOrDefault(name, uriLineIdx))
                           + scheme + ": Option '" + name + "' is not applicable in consumer only mode");
            }
        }
        if (result.getNotProducerOnly() != null) {
            for (String name : result.getNotProducerOnly()) {
                errors.add(linePrefix(optionLineMap.getOrDefault(name, uriLineIdx))
                           + scheme + ": Option '" + name + "' is not applicable in producer only mode");
            }
        }
    }

    private static String linePrefix(int lineIdx) {
        return "Line " + (lineIdx + 1) + ": ";
    }

    private static String extractEipFromLine(String trimmed) {
        if (trimmed.startsWith("- ")) {
            trimmed = trimmed.substring(2).trim();
        }
        int colon = trimmed.indexOf(':');
        if (colon > 0) {
            return trimmed.substring(0, colon).trim();
        }
        return null;
    }

    private static String formatFullOptionDoc(BaseOptionModel opt) {
        if (opt == null) {
            return null;
        }
        return opt.getDescription();
    }

    private static String formatOptionTitle(BaseOptionModel opt) {
        List<String> parts = new ArrayList<>();
        parts.add(opt.getName());
        List<String> meta = new ArrayList<>();
        if (opt.getType() != null) {
            meta.add(opt.getType());
        }
        if (opt.isRequired()) {
            meta.add("required");
        }
        if (opt.getDefaultValue() != null) {
            meta.add("default: " + opt.getDefaultValue());
        }
        if (!meta.isEmpty()) {
            parts.add("(" + String.join(", ", meta) + ")");
        }
        return String.join(" ", parts);
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
            mainGroupsCache = null;
            componentOptionsCache.clear();
            languageOptionsCache.clear();
            dataformatOptionsCache.clear();
            springBootMetadataCache = null;
            springBootMetadataLoaded = false;
            springBootOptionsCache = null;
            springBootGroupsCache = null;
            springBootHintsCache = null;
            springBootMetadataFuture = null;
            propsCatalogVersion = version;
        }
        if (mainOptionsCache == null) {
            mainOptionsCache = new HashMap<>();
            mainGroupsCache = new HashMap<>();
            MainModel mainModel = catalog.mainModel();
            if (mainModel != null) {
                for (MainModel.MainOptionModel opt : mainModel.getOptions()) {
                    if (opt.getName() != null) {
                        mainOptionsCache.put(opt.getName(), opt);
                    }
                }
                for (MainModel.MainGroupModel grp : mainModel.getGroups()) {
                    if (grp.getName() != null) {
                        mainGroupsCache.put(grp.getName(), grp.getDescription());
                    }
                }
            }
        }
    }

    private void ensureSpringBootMetadataCache() {
        if (springBootMetadataLoaded) {
            // check if async loading completed
            if (springBootMetadataFuture != null && springBootMetadataFuture.isDone()) {
                applySpringBootMetadataResult(springBootMetadataFuture.join());
                springBootMetadataFuture = null;
            }
            return;
        }
        springBootMetadataLoaded = true;
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null || !"Spring Boot".equals(info.platform)) {
            return;
        }

        if (!info.phantom && info.pid != null && !info.pid.isEmpty()) {
            springBootMetadataCache = SpringBootMetadataHelper.fetchMetadata(ctx, info.pid);
            if (springBootMetadataCache != null && !springBootMetadataCache.isEmpty()) {
                applySpringBootMetadataResult(
                        new SpringBootMetadataResolver.MetadataResult(springBootMetadataCache, Map.of()));
                return;
            }
        }

        if (info.directory != null) {
            Path pomFile = Path.of(info.directory, "pom.xml");
            if (Files.isRegularFile(pomFile)) {
                String camelVer = info.camelVersion;
                springBootMetadataFuture = java.util.concurrent.CompletableFuture.supplyAsync(
                        () -> SpringBootMetadataResolver.loadFromPom(pomFile, camelVer),
                        ctx.backgroundExecutor);
            }
        }
    }

    private void applySpringBootMetadataResult(SpringBootMetadataResolver.MetadataResult result) {
        if (result == null) {
            return;
        }
        springBootMetadataCache = result.properties();
        if (springBootMetadataCache != null && !springBootMetadataCache.isEmpty()) {
            springBootOptionsCache = new HashMap<>();
            springBootGroupsCache = new HashMap<>();
            springBootHintsCache = result.hints() != null ? result.hints() : Map.of();

            for (Map.Entry<String, JsonObject> entry : springBootMetadataCache.entrySet()) {
                String name = entry.getKey();
                BaseOptionModel model = SpringBootMetadataHelper.toOptionModel(entry.getValue());
                springBootOptionsCache.put(name, model);

                int lastDot = name.lastIndexOf('.');
                if (lastDot > 0) {
                    String group = name.substring(0, lastDot);
                    springBootGroupsCache.putIfAbsent(group, "");
                }
            }
        }
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

    // ---- Route jump links ----

    private void buildRouteIndex() {
        List<RouteEntry> fromEntries = new ArrayList<>();
        List<ToEntry> toEntries = new ArrayList<>();
        for (FilesBrowser.FileEntry entry : entries) {
            if (entry.directory()) {
                continue;
            }
            Path path = Path.of(entry.path());
            if (!isCamelSourceFile(path)) {
                continue;
            }
            if (isYamlFile(path)) {
                scanYamlRoutes(path, fromEntries, toEntries);
            }
        }
        routeIndex = fromEntries;
        toIndex = toEntries;
    }

    private List<YamlRouteNodeScanner.NodeEntry> buildSourceNodeIndex() {
        List<YamlRouteNodeScanner.NodeEntry> nodes = new ArrayList<>();
        for (FilesBrowser.FileEntry entry : entries) {
            if (entry.directory()) {
                continue;
            }
            Path path = Path.of(entry.path());
            if (!isCamelSourceFile(path) || !isYamlFile(path)) {
                continue;
            }
            nodes.addAll(YamlRouteNodeScanner.scanFile(path));
        }
        return nodes;
    }

    private void scanYamlRoutes(Path file, List<RouteEntry> fromEntries, List<ToEntry> toEntries) {
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return;
        }

        String filePath = file.toString();
        String currentRouteId = null;
        int routeIdIndent = -1;
        int pendingFromLine = -1;
        boolean inLinkableBlock = false;
        int linkableBlockIndent = -1;

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }

            int indent = lineIndent(line);

            // reset linkable context when dedented
            if (linkableBlockIndent >= 0 && indent <= linkableBlockIndent) {
                inLinkableBlock = false;
                linkableBlockIndent = -1;
            }

            // detect route id
            if (trimmed.startsWith("id:") && !trimmed.startsWith("id: \"\"")) {
                String val = extractYamlValue(trimmed, "id");
                if (val != null && !val.isEmpty()) {
                    currentRouteId = val;
                    routeIdIndent = indent;
                }
                continue;
            }

            // detect from: with inline URI
            if (trimmed.startsWith("from:") || trimmed.startsWith("- from:")) {
                inLinkableBlock = false;
                linkableBlockIndent = -1;
                String inlineUri = extractInlineUri(trimmed, "from");
                if (inlineUri != null) {
                    emitRouteEntry(fromEntries, currentRouteId, inlineUri, filePath, i);
                } else {
                    pendingFromLine = i;
                }
                continue;
            }

            // detect uri: line following a from: block
            if (pendingFromLine >= 0 && trimmed.startsWith("uri:")) {
                String uri = extractYamlValue(trimmed, "uri");
                if (uri != null) {
                    emitRouteEntry(fromEntries, currentRouteId, uri, filePath, pendingFromLine);
                }
                pendingFromLine = -1;
                continue;
            }

            // reset pending from if we've moved past it
            if (pendingFromLine >= 0 && indent <= lineIndent(lines.get(pendingFromLine))) {
                pendingFromLine = -1;
            }

            // reset route id when a new route block starts
            if (trimmed.startsWith("- route:") || trimmed.equals("route:")) {
                currentRouteId = null;
                routeIdIndent = -1;
            }

            // detect linkable keywords (to, toD, wireTap, etc.) and index their URIs
            for (String kw : LINKABLE_KEYWORDS) {
                String prefix1 = kw + ":";
                String prefix2 = "- " + kw + ":";
                if (trimmed.startsWith(prefix1) || trimmed.startsWith(prefix2)) {
                    String after = trimmed.startsWith(prefix2)
                            ? trimmed.substring(prefix2.length()).trim()
                            : trimmed.substring(prefix1.length()).trim();
                    if (!after.isEmpty() && !after.equals("{") && !after.startsWith("#")) {
                        String toUri = stripQueryParams(unquote(after));
                        if (toUri != null && !toUri.isEmpty()) {
                            toEntries.add(new ToEntry(
                                    currentRouteId != null ? currentRouteId : "", toUri, filePath, i));
                        }
                    } else {
                        inLinkableBlock = true;
                        linkableBlockIndent = indent;
                    }
                    break;
                }
            }

            // uri: under a linkable block → index it as a to entry
            if (inLinkableBlock && trimmed.startsWith("uri:")) {
                String val = extractYamlValue(trimmed, "uri");
                if (val != null && !val.isEmpty()) {
                    String toUri = stripQueryParams(val);
                    if (toUri != null && !toUri.isEmpty()) {
                        toEntries.add(new ToEntry(
                                currentRouteId != null ? currentRouteId : "", toUri, filePath, i));
                    }
                }
            }
        }
    }

    private void emitRouteEntry(List<RouteEntry> index, String routeId, String fromUri, String filePath, int fromLine) {
        String baseUri = stripQueryParams(fromUri);
        if (baseUri == null || baseUri.isEmpty()) {
            return;
        }
        if (routeId == null || routeId.isEmpty()) {
            // derive route id from the from URI
            int colon = baseUri.indexOf(':');
            routeId = colon >= 0 ? baseUri.substring(colon + 1) : baseUri;
            if (routeId.startsWith("//")) {
                routeId = routeId.substring(2);
            }
        }
        index.add(new RouteEntry(routeId, baseUri, filePath, fromLine));
    }

    private Map<Integer, SourceViewer.JumpLink> computeJumpLinks(Path currentFile) {
        if (routeIndex.isEmpty()) {
            return Collections.emptyMap();
        }

        List<String> lines;
        try {
            lines = Files.readAllLines(currentFile, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return Collections.emptyMap();
        }

        String currentFilePath = currentFile.toString();
        Map<Integer, SourceViewer.JumpLink> result = new LinkedHashMap<>();

        Map<String, RouteEntry> fromUriToRoute = new HashMap<>();
        for (RouteEntry re : routeIndex) {
            fromUriToRoute.put(re.fromUri(), re);
        }

        // forward links: to/toD/wireTap → target route's from
        boolean inLinkableBlock = false;
        int linkableBlockIndent = -1;
        String currentRouteId = null;

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }

            int indent = lineIndent(line);

            if (linkableBlockIndent >= 0 && indent <= linkableBlockIndent) {
                inLinkableBlock = false;
                linkableBlockIndent = -1;
            }

            if (trimmed.startsWith("id:") && !trimmed.startsWith("id: \"\"")) {
                String val = extractYamlValue(trimmed, "id");
                if (val != null && !val.isEmpty()) {
                    currentRouteId = val;
                }
            }

            if (trimmed.startsWith("from:") || trimmed.startsWith("- from:")) {
                inLinkableBlock = false;
                linkableBlockIndent = -1;
                continue;
            }

            String uri = null;
            for (String kw : LINKABLE_KEYWORDS) {
                String prefix1 = kw + ":";
                String prefix2 = "- " + kw + ":";
                if (trimmed.startsWith(prefix1) || trimmed.startsWith(prefix2)) {
                    String after = trimmed.startsWith(prefix2)
                            ? trimmed.substring(prefix2.length()).trim()
                            : trimmed.substring(prefix1.length()).trim();
                    if (!after.isEmpty() && !after.equals("{") && !after.startsWith("#")) {
                        uri = unquote(after);
                    } else {
                        inLinkableBlock = true;
                        linkableBlockIndent = indent;
                    }
                    break;
                }
            }

            if (uri == null && inLinkableBlock && trimmed.startsWith("uri:")) {
                String val = extractYamlValue(trimmed, "uri");
                if (val != null && !val.isEmpty()) {
                    uri = val;
                }
            }

            if (uri != null) {
                String baseUri = stripQueryParams(uri);
                RouteEntry target = fromUriToRoute.get(baseUri);
                if (target != null && !target.routeId().equals(currentRouteId)) {
                    result.put(i, new SourceViewer.JumpLink(target.routeId(), target.filePath(), target.fromLine()));
                }
            }
        }

        // reverse links: from URI ← routes that send to it (jump to the caller's to: line)
        for (RouteEntry re : routeIndex) {
            if (!currentFilePath.equals(re.filePath())) {
                continue;
            }
            for (ToEntry te : toIndex) {
                if (te.routeId().equals(re.routeId())) {
                    continue;
                }
                if (re.fromUri().equals(te.toUri())) {
                    // add jump link on the from: line pointing to the caller
                    String callerRouteId = te.routeId().isEmpty() ? "route" : te.routeId();
                    result.putIfAbsent(re.fromLine(),
                            new SourceViewer.JumpLink(callerRouteId, te.filePath(), te.toLine()));
                    break;
                }
            }
        }

        return result;
    }

    private void openFileAt(String targetFilePath, int targetLine) {
        String currentFile = sourceViewer.getCurrentFilePath();
        if (currentFile != null && currentFile.equals(targetFilePath)) {
            sourceViewer.goToLine(targetLine);
            return;
        }
        if (sourceViewer.isEditMode() && sourceViewer.isDirty()) {
            if (ctx.notificationCallback != null) {
                ctx.notificationCallback.accept("Save or discard edits before navigating to another file", false);
            }
            return;
        }
        for (int idx = 0; idx < entries.size(); idx++) {
            FilesBrowser.FileEntry entry = entries.get(idx);
            if (!entry.directory() && entry.path().equals(targetFilePath)) {
                listState.select(idx);
                Path filePath = Path.of(entry.path());
                if (isCamelSourceFile(filePath)) {
                    sourceViewer.setQuickDocProvider(this::provideCamelQuickDocs);
                    sourceViewer.setDeprecatedLineScanner(null);
                    if (isYamlFile(filePath)) {
                        sourceViewer.setAutocompleteProvider(this::provideYamlKeyCompletions);
                        sourceViewer.setAutocompleteValueProvider(this::provideYamlValueCompletions);
                        sourceViewer.setEndpointValidator(this::validateYamlEndpoints);
                        sourceViewer.setSimpleValidator(this::validateYamlSimple);
                        sourceViewer.setListItemNodeChecker(this::isListChildrenNode);
                        sourceViewer.setEditQuickDocProvider(this::provideEditQuickDoc);
                    } else {
                        sourceViewer.setAutocompleteProvider(null);
                        sourceViewer.setAutocompleteValueProvider(null);
                        sourceViewer.setEditQuickDocProvider(null);
                    }
                }
                sourceViewer.loadFile(filePath);
                sourceViewer.setJumpLinks(computeJumpLinks(filePath));
                sourceViewer.goToLine(targetLine);
                focusOnViewer = true;
                break;
            }
        }
    }

    private void handleJumpLink(SourceViewer.JumpLink link) {
        openFileAt(link.filePath(), link.targetLine());
    }

    private static String extractYamlValue(String trimmed, String key) {
        String prefix = key + ":";
        if (!trimmed.startsWith(prefix)) {
            return null;
        }
        String val = trimmed.substring(prefix.length()).trim();
        return unquote(val);
    }

    private static String extractInlineUri(String trimmed, String key) {
        String prefix = trimmed.startsWith("- ") ? "- " + key + ":" : key + ":";
        if (!trimmed.startsWith(prefix)) {
            return null;
        }
        String val = trimmed.substring(prefix.length()).trim();
        if (val.isEmpty() || val.equals("{") || val.startsWith("#")) {
            return null;
        }
        return unquote(val);
    }

    private static String unquote(String val) {
        if (val.length() >= 2 && val.startsWith("\"") && val.endsWith("\"")) {
            return val.substring(1, val.length() - 1);
        }
        if (val.length() >= 2 && val.startsWith("'") && val.endsWith("'")) {
            return val.substring(1, val.length() - 1);
        }
        return val;
    }

    private static String stripQueryParams(String uri) {
        if (uri == null) {
            return null;
        }
        int q = uri.indexOf('?');
        return q >= 0 ? uri.substring(0, q) : uri;
    }

    private static int lineIndent(String line) {
        int indent = 0;
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == ' ') {
                indent++;
            } else {
                break;
            }
        }
        return indent;
    }
}
