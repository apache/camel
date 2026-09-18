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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    private final SourceEditAssist assist = new SourceEditAssist(ctx);

    SourceEditAssist editAssist() {
        return assist;
    }

    /**
     * Opens the file in the editor for an AI edit replay: the files list is refreshed, the file loaded and edit mode
     * entered with the focus on the viewer. Returns null when the file cannot be edited (or unsaved edits are open).
     */
    EditReplay.Editor openFileForReplay(Path file) {
        if (sourceViewer.isEditMode() && sourceViewer.isDirty()) {
            if (ctx.notificationCallback != null) {
                ctx.notificationCallback.accept("Save or discard your edits first; the AI edit was not applied", true);
            }
            return null;
        }
        refreshFiles();
        for (int idx = 0; idx < entries.size(); idx++) {
            FilesBrowser.FileEntry entry = entries.get(idx);
            if (!entry.directory() && entry.path().equals(file.toString())) {
                listState.select(idx);
                break;
            }
        }
        configureEditAssist(file);
        sourceViewer.loadFile(file);
        if (isCamelSourceFile(file)) {
            sourceViewer.setJumpLinks(computeJumpLinks(file));
        }
        if (!sourceViewer.isEditable()) {
            return null;
        }
        sourceViewer.enterEditMode();
        focusOnViewer = true;
        return sourceViewer.replayEditor();
    }

    boolean isViewerEditing() {
        return sourceViewer.isEditMode();
    }

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
        sourceViewer.setOnFileCreated(this::refreshFiles);
        sourceViewer.setOnFileLoaded(p -> {
            if (isCamelSourceFile(p)) {
                sourceViewer.setJumpLinks(computeJumpLinks(p));
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
        assist.setRootDir(null);
        currentDir = null;
        entries = Collections.emptyList();
        sourceViewer.reset();
        focusOnViewer = false;
        leftPanelWidth = -1;
        assist.reset();
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
        return DocHelper.loadHelpText("source");
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
            assist.setRootDir(dir);
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
        // Recompute jump links for the currently viewed file so forward/reverse links
        // are immediately usable after the route index is rebuilt (e.g. after extraction).
        String viewedPath = sourceViewer.getCurrentFilePath();
        if (viewedPath != null) {
            Path viewedFile = Path.of(viewedPath);
            if (isCamelSourceFile(viewedFile)) {
                sourceViewer.setJumpLinks(computeJumpLinks(viewedFile));
            }
        }
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
                configureEditAssist(filePath);
                sourceViewer.loadFile(filePath);
                if (isCamelSourceFile(filePath)) {
                    sourceViewer.setJumpLinks(computeJumpLinks(filePath));
                }
                focusOnViewer = true;
            }
        }
    }

    /**
     * Points the viewer's quick-doc, autocomplete, validation and deprecation hooks at the providers matching the file
     * type (Camel YAML, other Camel source, properties, or none).
     */
    private void configureEditAssist(Path filePath) {
        if (isCamelSourceFile(filePath)) {
            sourceViewer.setQuickDocProvider(assist::provideCamelQuickDocs);
            sourceViewer.setDeprecatedLineScanner(null);
            if (SourceEditAssist.isYamlFile(filePath)) {
                sourceViewer.setAutocompleteProvider(assist::provideYamlKeyCompletions);
                sourceViewer.setAutocompleteValueProvider(assist::provideYamlValueCompletions);
                sourceViewer.setEndpointValidator(assist::validateYamlEndpoints);
                sourceViewer.setSimpleValidator(assist::validateYamlSimple);
                sourceViewer.setListItemNodeChecker(assist::isListChildrenNode);
                sourceViewer.setEditQuickDocProvider(assist::provideEditQuickDoc);
            } else {
                sourceViewer.setAutocompleteProvider(null);
                sourceViewer.setAutocompleteValueProvider(null);
                sourceViewer.setEditQuickDocProvider(null);
            }
        } else if (SourceEditAssist.isPropertiesFile(filePath)) {
            sourceViewer.setQuickDocProvider(assist::providePropertiesQuickDocs);
            sourceViewer.setDeprecatedLineScanner(assist::scanDeprecatedProperties);
            sourceViewer.setAutocompleteProvider(assist::providePropertyCompletions);
            sourceViewer.setAutocompleteValueProvider(assist::providePropertyValueCompletions);
            sourceViewer.setPropertiesValidator(assist::validatePropertyLine);
            sourceViewer.setEditQuickDocProvider(assist::provideEditPropertyQuickDoc);
        } else {
            sourceViewer.setQuickDocProvider(null);
            sourceViewer.setDeprecatedLineScanner(null);
            sourceViewer.setAutocompleteProvider(null);
            sourceViewer.setEditQuickDocProvider(null);
            sourceViewer.setAutocompleteValueProvider(null);
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

    private void renderFileList(Frame frame, Rect area) {
        Style fileBorderStyle = ctx.paneBorder(!focusOnViewer);
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

        Style infoBorderStyle = ctx.paneBorder(!focusOnViewer);
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
        Style sourceBorderStyle = ctx.paneBorder(focusOnViewer);
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
            if (SourceEditAssist.isYamlFile(path)) {
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
            if (!isCamelSourceFile(path) || !SourceEditAssist.isYamlFile(path)) {
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
                configureEditAssist(filePath);
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

    static String extractYamlValue(String trimmed, String key) {
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
