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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import com.networknt.schema.Error;
import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Layout;
import dev.tamboui.layout.Rect;
import dev.tamboui.markdown.MarkdownView;
import dev.tamboui.style.Overflow;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.MouseEvent;
import dev.tamboui.tui.event.MouseEventKind;
import dev.tamboui.widgets.Clear;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.block.Title;
import dev.tamboui.widgets.input.TextArea;
import dev.tamboui.widgets.input.TextAreaState;
import dev.tamboui.widgets.paragraph.Paragraph;
import dev.tamboui.widgets.scrollbar.Scrollbar;
import dev.tamboui.widgets.scrollbar.ScrollbarState;
import org.apache.camel.dsl.jbang.core.commands.ai.QuickFixes;
import org.apache.camel.support.LoggerHelper;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

import static org.apache.camel.dsl.jbang.core.commands.tui.SourceRefactorings.*;
import static org.apache.camel.dsl.jbang.core.commands.tui.SourceValidationSupport.*;
import static org.apache.camel.dsl.jbang.core.commands.tui.YamlSourceContext.*;

/**
 * Reusable source code viewer with syntax highlighting, scrolling, and line-number display. Can be used by any tab that
 * needs to show route source code. Supports a plain-text edit mode for local files (dev mode / local folder).
 */
class SourceViewer {

    record DocEntry(String text, boolean deprecated, String title) {
        DocEntry(String text, boolean deprecated) {
            this(text, deprecated, null);
        }

        static DocEntry of(String text) {
            return new DocEntry(text, false, null);
        }

        static DocEntry deprecated(String text) {
            return new DocEntry(text, true, null);
        }

        static DocEntry withTitle(String title, String text) {
            return new DocEntry(text, false, title);
        }
    }

    @FunctionalInterface
    interface EditQuickDocProvider {
        List<DocEntry> provideForLine(List<String> lines, int cursorRow);
    }

    /** The quick doc of what the cursor is on within the line; col is -1 in the view, which has no cursor. */
    @FunctionalInterface
    interface CursorQuickDocProvider {
        List<DocEntry> provideAt(List<String> lines, int row, int col);
    }

    @FunctionalInterface
    interface PropertiesValidator {
        String validate(String line);
    }

    @FunctionalInterface
    interface EndpointValidator {
        List<String> validate(String content);
    }

    @FunctionalInterface
    interface DeprecatedLineScanner {
        Set<Integer> scan(List<JsonObject> codeData);
    }

    /**
     * A link on a line to where it leads; callersOf is the endpoint of a route sent to from more than one place, whose
     * link opens a popup to choose one (CAMEL-25411), else null.
     */
    record JumpLink(String routeId, String filePath, int targetLine, String callersOf) {

        JumpLink(String routeId, String filePath, int targetLine) {
            this(routeId, filePath, targetLine, null);
        }
    }

    private boolean visible;
    private List<String> lines = Collections.emptyList();
    private List<JsonObject> codeData = Collections.emptyList();
    private String title;
    private SyntaxHighlighter.Language language = SyntaxHighlighter.Language.PLAIN;
    private int scrollY;
    private int scrollX;
    private int selectedLine = -1;
    private int lastVisibleLines;
    private boolean pendingScroll;
    private final ScrollbarState vScrollState = new ScrollbarState();
    private final ScrollbarState hScrollState = new ScrollbarState();
    private final AtomicBoolean loading = new AtomicBoolean(false);
    private IntConsumer onLineSelected;
    private final Map<String, CachedSource> sourceCache = new ConcurrentHashMap<>();
    private boolean wordWrap;
    private final SearchHighlighter search = new SearchHighlighter();
    private String currentFormat;
    private String originalFormat;
    private String currentRouteId;
    private MonitorContext currentCtx;
    private String currentPid;
    private Rect lastInnerArea;
    private boolean isMarkdownFile;
    private boolean markdownMode;
    private String rawMarkdownContent;
    private int markdownScroll;
    // the furthest the markdown view scrolls, from the last render (unknown before it)
    private int markdownMaxScroll = Integer.MAX_VALUE;
    private EditQuickDocProvider editQuickDocProvider;
    private boolean editQuickDocEnabled = true;
    private DeprecatedLineScanner deprecatedLineScanner;
    private Set<Integer> deprecatedLines = Collections.emptySet();
    private Map<Integer, JumpLink> jumpLinks = Collections.emptyMap();
    private Consumer<JumpLink> onJumpLink;
    private String loadedFilePath;
    private Style titleStyle;
    private Style borderStyle;
    private boolean focused = true;
    private boolean plainMode;

    /** Local file path when content was loaded via {@link #loadFile(Path)} and is writable. */
    private Path editableFile;
    private boolean editMode;
    private final TextAreaState editState = new TextAreaState();
    private final YamlSourceContext yaml = new YamlSourceContext(editState);
    private final SourceCompletion completion = new SourceCompletion(editState, yaml, new SourceCompletion.Editor() {
        @Override
        public boolean isEditMode() {
            return editMode;
        }

        @Override
        public boolean isCamelYamlFile() {
            return SourceViewer.this.isCamelYamlFile();
        }

        @Override
        public void recordEditChange() {
            SourceViewer.this.recordEditChange();
        }

        @Override
        public void markDirty() {
            dirty = true;
        }
    });
    private final SourceLiveColumn liveColumn = new SourceLiveColumn(() -> this.loadedFilePath, () -> this.dirty);
    private final SourceRefactorActions refactor = new SourceRefactorActions(editState, new SourceRefactorActions.Editor() {
        @Override
        public Path editableFile() {
            return editableFile;
        }

        @Override
        public boolean isCamelYamlFile() {
            return SourceViewer.this.isCamelYamlFile();
        }

        @Override
        public String routeDsl() {
            return completion.routeDsl();
        }

        @Override
        public void recordEditChange() {
            SourceViewer.this.recordEditChange();
        }

        @Override
        public void markSaved() {
            dirty = false;
            originalEditText = editState.text();
            lineStatuses = null;
        }

        @Override
        public void report(String message, boolean error) {
            notifySave(message, error);
        }

        @Override
        public void fileCreated() {
            if (onFileCreated != null) {
                onFileCreated.run();
            }
        }
    });
    /** Markdown render mode prior to entering edit; restored on cancel. */
    private boolean markdownModeBeforeEdit;
    private boolean dirty;
    private boolean pendingDiscard;
    private String originalEditText;
    private EditDiff.LineStatus[] lineStatuses;
    private boolean diffOverlay;
    private int diffScrollY;
    private BiConsumer<String, Boolean> notificationCallback;
    private Runnable onFileCreated;
    private Consumer<Path> onFileLoaded;
    private boolean validateOnSave = true;
    // the tree of the route the cursor is in, at the top right (Ctrl+T); the setting gives the default
    private boolean routeTreeShown;
    private final RouteTreePanel routeTree = new RouteTreePanel();
    private org.apache.camel.dsl.yaml.validator.YamlValidator yamlValidator;
    private PropertiesValidator propertiesValidator;
    private EndpointValidator endpointValidator;
    private EndpointValidator simpleValidator;
    // the Kamelet checks of a YAML file (CAMEL-25411): its problems, and the notes that do not block a save
    private EndpointValidator kameletValidator;
    private EndpointValidator kameletNotes;
    // the lines whose problem is a note, not an error: marked as a warning and counted apart (CAMEL-25411)
    private Set<Integer> noteLines = Set.of();
    private EndpointValidator routeValidator;
    private MonitorContext.AskAi askAi;
    private CursorQuickDocProvider cursorQuickDocProvider;
    private List<String> routeProblems = List.of();
    private List<String> validationErrors;
    private int validationErrorScroll;
    private Map<Integer, String> inlineErrors = Collections.emptyMap();
    /**
     * The problems of the file shown, found when it was loaded: marked in the view, and the editor starts with them.
     */
    private Map<Integer, String> viewErrors = Collections.emptyMap();
    private boolean editInitialScroll;
    /** The top line of the view when F4 was pressed, so the editor opens on the same screen; -1 when not known. */
    private int editStartTop = -1;
    private long lastBackgroundValidationTime;
    private String lastBackgroundValidationContent;
    private static final long BACKGROUND_VALIDATION_INTERVAL_MS = 2000;
    private final SourceEditHistory editHistory = new SourceEditHistory();

    private record CachedSource(
            List<String> lines, List<JsonObject> codeData,
            String sourceLocation, SyntaxHighlighter.Language language) {
    }

    boolean isVisible() {
        return visible;
    }

    void setTitleStyle(Style style) {
        this.titleStyle = style;
    }

    void setBorderStyle(Style style) {
        this.borderStyle = style;
    }

    void setFocused(boolean focused) {
        this.focused = focused;
    }

    void setNotificationCallback(BiConsumer<String, Boolean> callback) {
        this.notificationCallback = callback;
    }

    void setOnFileCreated(Runnable callback) {
        this.onFileCreated = callback;
    }

    void setOnFileLoaded(Consumer<Path> callback) {
        this.onFileLoaded = callback;
    }

    void setRouteTreeShown(boolean routeTreeShown) {
        this.routeTreeShown = routeTreeShown;
    }

    boolean isRouteTreeShown() {
        return routeTreeShown;
    }

    void setValidateOnSave(boolean validateOnSave) {
        this.validateOnSave = validateOnSave;
    }

    void setPropertiesValidator(PropertiesValidator propertiesValidator) {
        this.propertiesValidator = propertiesValidator;
    }

    void setEndpointValidator(EndpointValidator endpointValidator) {
        this.endpointValidator = endpointValidator;
    }

    void setSimpleValidator(EndpointValidator simpleValidator) {
        this.simpleValidator = simpleValidator;
    }

    /**
     * The Kamelet checks of a YAML file (CAMEL-25411): the shape of a Kamelet file, and the kamelet: endpoints of a
     * route against the catalog and the project's own Kamelets. Their problems are marked and keep the file from being
     * saved, as the other problems of a YAML file.
     */
    void setKameletValidator(EndpointValidator kameletValidator) {
        this.kameletValidator = kameletValidator;
    }

    /**
     * What a Kamelet file does that works but is not right (a camel: dependency its template does not use): marked and
     * said when saved, but not blocking the save.
     */
    void setKameletNotes(EndpointValidator kameletNotes) {
        this.kameletNotes = kameletNotes;
    }

    /**
     * The Camel checks of a Java or XML DSL route file (CAMEL-25208): its problems are marked on their lines while
     * editing. An XML file with problems is not saved, as a YAML file; a Java file is saved and the problems are said,
     * as a Java file is the application's code, and what the checks cannot know must never keep it from being saved.
     */
    void setRouteValidator(EndpointValidator routeValidator) {
        this.routeValidator = routeValidator;
    }

    /**
     * The quick doc of the simple function the cursor is on (CAMEL-25219), shown before the doc of the line in edit
     * mode, and the functions of the selected line after it in the view; null for a file without routes.
     */
    void setCursorQuickDocProvider(CursorQuickDocProvider provider) {
        this.cursorQuickDocProvider = provider;
    }

    /** Opens the AI panel to fix the problem of the cursor line (Shift+F8); null when there is no AI panel. */
    void setAskAi(MonitorContext.AskAi askAi) {
        this.askAi = askAi;
    }

    void hide() {
        exitEditMode();
        visible = false;
        onLineSelected = null;
        deprecatedLines = Collections.emptySet();
        editableFile = null;
        propertiesValidator = null;
        endpointValidator = null;
        simpleValidator = null;
        kameletValidator = null;
        kameletNotes = null;
        noteLines = Set.of();
        routeValidator = null;
        cursorQuickDocProvider = null;
        completion.clearLanguageProviders();
    }

    void reset() {
        exitEditMode();
        visible = false;
        lines = Collections.emptyList();
        codeData = Collections.emptyList();
        title = null;
        scrollY = 0;
        scrollX = 0;
        selectedLine = -1;
        pendingScroll = false;
        onLineSelected = null;
        sourceCache.clear();
        wordWrap = false;
        search.reset();
        currentFormat = null;
        originalFormat = null;
        currentRouteId = null;
        currentCtx = null;
        currentPid = null;
        isMarkdownFile = false;
        markdownMode = false;
        rawMarkdownContent = null;
        markdownScroll = 0;
        markdownMaxScroll = Integer.MAX_VALUE;
        deprecatedLineScanner = null;
        deprecatedLines = Collections.emptySet();
        jumpLinks = Collections.emptyMap();
        loadedFilePath = null;
        completion.reset();
        editableFile = null;
        propertiesValidator = null;
        endpointValidator = null;
        simpleValidator = null;
        kameletValidator = null;
        kameletNotes = null;
        noteLines = Set.of();
        routeValidator = null;
        cursorQuickDocProvider = null;
    }

    boolean isMarkdownMode() {
        return markdownMode;
    }

    boolean isEditMode() {
        return editMode;
    }

    boolean isDirty() {
        return dirty;
    }

    /** Whether the text differs from what was last loaded or saved. */
    private boolean changedSinceSave() {
        return originalEditText == null || !originalEditText.equals(editState.text());
    }

    /** Package-private for tests that drive the edit buffer directly. */
    TextAreaState editState() {
        return editState;
    }

    /** The YAML structure analysis over the edit buffer; package-private for tests. */
    YamlSourceContext yamlContext() {
        return yaml;
    }

    /** The Tab completion of the editor: SourceTab gives it the completion providers of the file it opens. */
    SourceCompletion completion() {
        return completion;
    }

    /** The live run data at the end of the lines in the view: SourceTab gives it where to read it from. */
    SourceLiveColumn liveColumn() {
        return liveColumn;
    }

    boolean isEditable() {
        return editableFile != null;
    }

    boolean isPlainMode() {
        return plainMode;
    }

    /** Whether a dialog over the editor has the keys (discard, the problems of a save, the diff, refactor). */
    boolean showsDialog() {
        return pendingDiscard || editMode && (validationErrors != null || diffOverlay || refactor.isPopupOpen());
    }

    /**
     * True when the viewer is consuming typed input (search box or plain-text edit mode). Used by the monitor to avoid
     * treating digit/letter keys as global shortcuts.
     */
    boolean isTextInputActive() {
        return editMode || search.isSearchInputActive();
    }

    /**
     * Cancel edit mode without saving. Returns {@code true} if edit mode was active.
     */
    boolean cancelEdit() {
        completion.dropSemanticCompletion();
        if (!editMode) {
            return false;
        }
        if (diffOverlay) {
            diffOverlay = false;
            return true;
        }
        if (validationErrors != null) {
            validationErrors = null;
            return true;
        }
        if (completion.closePopup()) {
            return true;
        }
        if (refactor.closePopup()) {
            return true;
        }
        if (pendingDiscard) {
            pendingDiscard = false;
            exitEditMode();
            return true;
        }
        if (dirty) {
            pendingDiscard = true;
            return true;
        }
        exitEditMode();
        return true;
    }

    void setOnLineSelected(IntConsumer callback) {
        this.onLineSelected = callback;
    }

    void setJumpLinks(Map<Integer, JumpLink> links) {
        this.jumpLinks = links != null ? links : Collections.emptyMap();
    }

    JumpLink getJumpLink(int lineIndex) {
        return jumpLinks.get(lineIndex);
    }

    void setOnJumpLink(Consumer<JumpLink> callback) {
        this.onJumpLink = callback;
    }

    int getSelectedLine() {
        return selectedLine;
    }

    /**
     * Shows the file again, at the same line, when it is the file shown and it is not being edited: a file an AI tool
     * wrote while the viewer showed the old content. An edit in progress is left alone, as it holds the user's work.
     */
    void reloadIfShowing(Path file) {
        if (editMode || editableFile == null || file == null
                || !editableFile.toAbsolutePath().normalize().equals(file.toAbsolutePath().normalize())) {
            return;
        }
        int line = selectedLine;
        loadFile(editableFile);
        goToLine(Math.min(line, Math.max(0, getLineCount() - 1)));
    }

    int getLineCount() {
        if (editMode) {
            return editState.lineCount();
        }
        return lines != null ? lines.size() : 0;
    }

    void goToLine(int lineIndex) {
        int maxLine = editMode ? editState.lineCount() : lines.size();
        if (lineIndex >= 0 && lineIndex < maxLine) {
            selectedLine = lineIndex;
            pendingScroll = true;
            if (editMode) {
                editState.moveCursorToStart();
                int targetRow = Math.max(0, lineIndex);
                for (int i = 0; i < targetRow && i < editState.lineCount() - 1; i++) {
                    editState.moveCursorDown();
                }
                editState.moveCursorToLineStart();
            }
        }
    }

    String getCurrentFilePath() {
        return loadedFilePath;
    }

    void setEditQuickDocProvider(EditQuickDocProvider provider) {
        this.editQuickDocProvider = provider;
    }

    void setDeprecatedLineScanner(DeprecatedLineScanner scanner) {
        this.deprecatedLineScanner = scanner;
    }

    boolean handleKeyEvent(KeyEvent ke) {
        if (!visible) {
            return false;
        }
        if (ke.hasCtrl() && ke.isCharIgnoreCase('t') && !markdownMode) {
            routeTreeShown = !routeTreeShown;
            return true;
        }
        if (editMode) {
            return handleEditKeyEvent(ke);
        }
        if (search.isSearchInputActive()) {
            boolean handled = search.handleKeyEvent(ke);
            if (handled && !search.isSearchInputActive() && search.hasFindTerm()) {
                search.buildFindMatches(lines);
                selectedLine = search.jumpToNearestMatch(selectedLine);
            }
            return handled;
        }
        if (ke.isCancel()) {
            if (search.handleEscape()) {
                return true;
            }
            hide();
            return true;
        }
        if (ke.isChar('c')) {
            hide();
            return true;
        }
        if (isEditable() && ke.isKey(KeyCode.F4)) {
            enterEditMode();
            return true;
        }
        if (ke.isKey(KeyCode.F9) && !ke.hasShift() && !viewErrors.isEmpty()) {
            goToNextProblem(viewErrors, selectedLine);
            return true;
        }
        if (ke.isKey(KeyCode.F8) && ke.hasShift()) {
            // fix with AI: the problem of the selected line, or what fails on it at runtime
            askAiToFix();
            return true;
        }
        if (isMarkdownFile && ke.isChar(' ')) {
            markdownMode = !markdownMode;
            return true;
        }
        if (markdownMode) {
            if (ke.isUp() || ke.isChar('k')) {
                scrollMarkdown(-1);
            } else if (ke.isDown() || ke.isChar('j')) {
                scrollMarkdown(1);
            } else if (ke.isPageUp() || ke.isKey(KeyCode.PAGE_UP)) {
                scrollMarkdown(-10);
            } else if (ke.isPageDown() || ke.isKey(KeyCode.PAGE_DOWN)) {
                scrollMarkdown(10);
            } else if (ke.isHome() || ke.isKey(KeyCode.HOME)) {
                markdownScroll = 0;
            } else if (ke.isEnd() || ke.isKey(KeyCode.END)) {
                markdownScroll = markdownMaxScroll;
            }
            return true;
        }
        if (currentRouteId != null && ke.isChar(' ')) {
            String[] formats = { "yaml", "java", "xml" };
            int idx = 0;
            for (int i = 0; i < formats.length; i++) {
                if (formats[i].equals(currentFormat)) {
                    idx = i;
                    break;
                }
            }
            idx = (idx + 1) % formats.length;
            switchFormat(formats[idx]);
            return true;
        }
        if (search.handleKeyEvent(ke)) {
            int matchLine = search.currentMatchLine();
            if (matchLine >= 0) {
                selectedLine = matchLine;
            }
            return true;
        }
        if (ke.isChar('w')) {
            wordWrap = !wordWrap;
            scrollX = 0;
            return true;
        }
        if (ke.isChar('p')) {
            plainMode = !plainMode;
            return true;
        }
        if (ke.isKey(KeyCode.UP) && ke.hasCtrl()) {
            scrollY = Math.max(0, scrollY - 1);
        } else if (ke.isKey(KeyCode.DOWN) && ke.hasCtrl()) {
            scrollY++;
        } else if (ke.isUp()) {
            selectedLine = Math.max(0, selectedLine - 1);
        } else if (ke.isDown()) {
            if (!lines.isEmpty()) {
                selectedLine = Math.min(lines.size() - 1, selectedLine + 1);
            }
        } else if (ke.isPageUp() || ke.isKey(KeyCode.PAGE_UP)) {
            int page = Math.max(1, lastVisibleLines);
            selectedLine = Math.max(0, selectedLine - page);
        } else if (ke.isPageDown() || ke.isKey(KeyCode.PAGE_DOWN)) {
            int page = Math.max(1, lastVisibleLines);
            if (!lines.isEmpty()) {
                selectedLine = Math.min(lines.size() - 1, selectedLine + page);
            }
        } else if (!wordWrap && ke.isLeft()) {
            scrollX = Math.max(0, scrollX - 8);
        } else if (!wordWrap && ke.isRight()) {
            scrollX += 8;
        } else if (ke.isHome()) {
            selectedLine = 0;
            scrollX = 0;
        } else if (ke.isEnd()) {
            if (!lines.isEmpty()) {
                selectedLine = lines.size() - 1;
            }
        } else if (ke.isConfirm() && onJumpLink != null && jumpLinks.containsKey(selectedLine)) {
            onJumpLink.accept(jumpLinks.get(selectedLine));
            return true;
        } else if (ke.isConfirm() && onLineSelected != null) {
            if (selectedLine >= 0 && selectedLine < codeData.size()) {
                Integer lineNum = codeData.get(selectedLine).getInteger("line");
                if (lineNum != null) {
                    onLineSelected.accept(lineNum);
                }
            }
            return true;
        } else {
            return false;
        }
        return true;
    }

    private void recordEditChange() {
        editHistory.beforeChange(editState);
        dirty = true;
        lineStatuses = null;
    }

    private List<String> editLines() {
        return SourceEditorNavigation.lines(editState);
    }

    private void applyBlockEdit(YamlBlockEditor.EditResult result) {
        if (result == null) {
            return;
        }
        recordEditChange();
        int prevScroll = editState.scrollRow();
        editState.setText(YamlBlockEditor.fromLines(result.lines()));
        SourceEditorNavigation.positionCursor(editState, result.cursorRow(), result.cursorCol());
        // Restore scroll so the viewport doesn't jump; ensureCursorVisible during
        // rendering will adjust if the cursor ended up off-screen.
        int maxScroll = Math.max(0, editState.lineCount() - Math.max(1, lastVisibleLines));
        editState.scrollDown(Math.min(prevScroll, maxScroll), lastVisibleLines);
    }

    /** Scrolls the markdown view by lines, within the document as far as the last render measured it. */
    private void scrollMarkdown(int delta) {
        long next = (long) Math.min(markdownScroll, markdownMaxScroll) + delta;
        markdownScroll = (int) Math.max(0, Math.min(markdownMaxScroll, next));
    }

    /** Ctrl+/: comments out the block at the cursor, or brings it back when it is commented out. */
    private void toggleComment(boolean yamlListBlocks) {
        String marker = lineCommentMarker();
        if (marker == null) {
            return;
        }
        List<String> lines = editLines();
        int row = editState.cursorRow();
        YamlBlockEditor.BlockRange block = YamlBlockEditor.findCommentBlock(lines, row, yamlListBlocks, marker);
        if (block.isEmpty()) {
            return;
        }
        List<String> toggled = YamlBlockEditor.toggleComment(lines, block, marker);
        applyBlockEdit(new YamlBlockEditor.EditResult(toggled, row, YamlBlockEditor.leadingSpaces(toggled.get(row))));
    }

    /** The line comment marker of the file in the editor, or null when it has none the editor can toggle (XML). */
    private String lineCommentMarker() {
        if (editableFile == null) {
            return null;
        }
        String name = editableFile.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".yaml") || name.endsWith(".yml") || name.endsWith(".properties")) {
            return "#";
        }
        if (name.endsWith(".java") || name.endsWith(".groovy") || name.endsWith(".kt") || name.endsWith(".js")) {
            return "//";
        }
        return null;
    }

    private void refreshEditFindMatches() {
        search.buildFindMatches(editLines());
    }

    private void jumpEditToCurrentFindMatch() {
        int line = search.jumpToNearestMatch(editState.cursorRow());
        if (line >= 0) {
            SourceEditorNavigation.positionCursor(editState, line, 0);
        }
    }

    /** Package-private for tests that assert on edit buffer content. */
    String editText() {
        return editState.text();
    }

    /** Package-private for tests: the problems marked on the lines of the edit buffer, by 0-based line. */
    Map<Integer, String> inlineErrors() {
        return inlineErrors;
    }

    private boolean handleEditKeyEvent(KeyEvent ke) {
        completion.dropSemanticCompletion();
        if (validationErrors != null) {
            if (ke.isKey(KeyCode.F9) && ke.hasShift()) {
                // the fix the popup offers: go to its line and apply it, as Shift+F9 does there
                int row = popupFixRow();
                if (row >= 0) {
                    validationErrors = null;
                    SourceEditorNavigation.positionCursor(editState, row, 0);
                    applyQuickFix();
                }
            } else if (ke.isCancel() || ke.isKey(KeyCode.ENTER)) {
                validationErrors = null;
            } else if (ke.isUp()) {
                validationErrorScroll = Math.max(0, validationErrorScroll - 1);
            } else if (ke.isDown()) {
                validationErrorScroll++;
            }
            return true;
        }
        if (diffOverlay) {
            if (ke.isCancel() || ke.isKey(KeyCode.F7)) {
                diffOverlay = false;
            } else if (ke.isUp()) {
                diffScrollY = Math.max(0, diffScrollY - 1);
            } else if (ke.isDown()) {
                diffScrollY++;
            } else if (ke.isPageUp() || ke.isKey(KeyCode.PAGE_UP)) {
                diffScrollY = Math.max(0, diffScrollY - Math.max(1, lastVisibleLines));
            } else if (ke.isPageDown() || ke.isKey(KeyCode.PAGE_DOWN)) {
                diffScrollY += Math.max(1, lastVisibleLines);
            }
            return true;
        }
        if (refactor.handleKeyEvent(ke)) {
            return true;
        }
        if (completion.handleKeyEvent(ke)) {
            return true;
        }
        if (search.handleEditFindKeyEvent(ke)) {
            if (!search.isSearchInputActive()) {
                refreshEditFindMatches();
                jumpEditToCurrentFindMatch();
            }
            return true;
        }
        if (ke.hasCtrl() && ke.isCharIgnoreCase('z') && !ke.hasShift()) {
            if (editHistory.undo(editState)) {
                // undone back to the saved text: not modified any more
                dirty = changedSinceSave();
                lineStatuses = null;
                refreshEditFindMatches();
            }
            return true;
        }
        if (ke.hasCtrl() && (ke.isCharIgnoreCase('y') || (ke.isCharIgnoreCase('z') && ke.hasShift()))) {
            if (editHistory.redo(editState)) {
                dirty = changedSinceSave();
                lineStatuses = null;
                refreshEditFindMatches();
            }
            return true;
        }
        if (ke.isKey(KeyCode.F8) && ke.hasShift()) {
            askAiToFix();
            return true;
        }
        if (ke.isKey(KeyCode.F9) && ke.hasShift()) {
            // Shift+F9 fixes the problem F9 jumps to (F3, F6, F8 are global: switch integration, shell, AI panel)
            applyQuickFix();
            return true;
        }
        if (ke.isKey(KeyCode.F9) && !inlineErrors.isEmpty()) {
            jumpToNextError();
            return true;
        }
        boolean yamlListBlocks = isCamelYamlFile();
        if (ke.isKey(KeyCode.UP) && ke.hasAlt() && !ke.hasShift()) {
            applyBlockEdit(YamlBlockEditor.moveBlockUp(editLines(), editState.cursorRow(), yamlListBlocks));
            return true;
        }
        if (ke.isKey(KeyCode.DOWN) && ke.hasAlt() && !ke.hasShift()) {
            applyBlockEdit(YamlBlockEditor.moveBlockDown(editLines(), editState.cursorRow(), yamlListBlocks));
            return true;
        }
        if (ke.hasCtrl() && ke.isCharIgnoreCase('d') && !ke.hasShift()) {
            applyBlockEdit(YamlBlockEditor.duplicateBlock(editLines(), editState.cursorRow(), yamlListBlocks));
            return true;
        }
        if (ke.hasCtrl() && ke.isCharIgnoreCase('k') && !ke.hasShift()) {
            applyBlockEdit(YamlBlockEditor.deleteLine(editLines(), editState.cursorRow()));
            return true;
        }
        if (ke.hasCtrl() && ke.isChar('/')) {
            // terminals send Ctrl+/ as 0x1F, which TamboUI reads as Ctrl+/ since 0.5.0
            toggleComment(yamlListBlocks);
            return true;
        }
        if (ke.hasCtrl() && ke.isCharIgnoreCase('r') && (isCamelYamlFile() || completion.routeDsl() != null)) {
            refactor.openRefactorPopup();
            return true;
        }
        if (ke.isKey(KeyCode.LEFT) && ke.hasCtrl()) {
            SourceEditorNavigation.moveWordLeft(editState);
            return true;
        }
        if (ke.isKey(KeyCode.RIGHT) && ke.hasCtrl()) {
            SourceEditorNavigation.moveWordRight(editState);
            return true;
        }
        if (pendingDiscard) {
            if (ke.isConfirm()) {
                pendingDiscard = false;
                exitEditMode();
            } else if (ke.isCancel()) {
                pendingDiscard = false;
            }
            return true;
        }
        if (ke.isCancel()) {
            if (search.handleEscape()) {
                return true;
            }
            if (dirty) {
                pendingDiscard = true;
                return true;
            }
            exitEditMode();
            return true;
        }
        if (ke.isKey(KeyCode.F7) && dirty && originalEditText != null) {
            diffOverlay = true;
            diffScrollY = 0;
            return true;
        }
        if (ke.hasCtrl() && ke.isCharIgnoreCase('s')) {
            saveContinueEdit();
            return true;
        }
        if (ke.isKey(KeyCode.F5)) {
            saveEdit();
            return true;
        }
        if (ke.isConfirm()) {
            recordEditChange();
            int prevRow = editState.cursorRow();
            String prevLine = editState.getLine(prevRow);
            int indent = countLeadingSpaces(prevLine);
            String pt = prevLine.trim();
            editState.insert('\n');
            if (isCamelYamlFile() && pt.endsWith(":")) {
                // parent key with no value — indent deeper for children
                String key = extractEipName(pt);
                if (key != null && completion.isListItemNode(dashToCamelCase(key))) {
                    // children are list items (e.g., steps:, root)
                    int childIndent = indent + (pt.startsWith("- ") ? 4 : 2);
                    editState.insert(" ".repeat(childIndent) + "- ");
                } else {
                    int childIndent = indent + (pt.startsWith("- ") ? 4 : 2);
                    editState.insert(" ".repeat(childIndent));
                }
            } else if (indent > 0) {
                editState.insert(" ".repeat(indent));
            }
            return true;
        }
        if (ke.isUp()) {
            editState.moveCursorUp();
            return true;
        }
        if (ke.isDown()) {
            editState.moveCursorDown();
            return true;
        }
        if (ke.isLeft()) {
            editState.moveCursorLeft();
            return true;
        }
        if (ke.isRight()) {
            editState.moveCursorRight();
            return true;
        }
        if (ke.isHome() || ke.isKey(KeyCode.HOME)) {
            SourceEditorNavigation.smartHome(editState, false);
            return true;
        }
        if (ke.isEnd() || ke.isKey(KeyCode.END)) {
            editState.moveCursorToLineEnd();
            return true;
        }
        if (ke.isPageUp() || ke.isKey(KeyCode.PAGE_UP)) {
            int page = Math.max(1, lastVisibleLines);
            for (int i = 0; i < page; i++) {
                editState.moveCursorUp();
            }
            return true;
        }
        if (ke.isPageDown() || ke.isKey(KeyCode.PAGE_DOWN)) {
            int page = Math.max(1, lastVisibleLines);
            for (int i = 0; i < page; i++) {
                editState.moveCursorDown();
            }
            return true;
        }
        if (ke.isDeleteBackward()) {
            recordEditChange();
            editState.deleteBackward();
            return true;
        }
        if (ke.isDeleteForward()) {
            recordEditChange();
            editState.deleteForward();
            return true;
        }
        if (ke.isKey(KeyCode.TAB) && ke.hasShift()) {
            yaml.moveCursorToPreviousIndentStop();
            return true;
        }
        if (ke.isKey(KeyCode.TAB) && completion.isEnabled()) {
            completion.openAutocomplete();
            return true;
        }
        String typed = FormHelper.typedText(ke);
        if (typed != null) {
            recordEditChange();
            editState.insert(typed);
            return true;
        }
        return true;
    }

    void enterEditMode() {
        if (!isEditable() || editMode) {
            return;
        }
        editState.setText(buildEditableText());
        // Position cursor on the currently selected source line
        editState.moveCursorToStart();
        int targetRow = Math.max(0, selectedLine);
        for (int i = 0; i < targetRow && i < editState.lineCount() - 1; i++) {
            editState.moveCursorDown();
        }
        editState.moveCursorToLineStart();
        editInitialScroll = true;
        editStartTop = markdownMode ? -1 : scrollY;
        markdownModeBeforeEdit = markdownMode;
        markdownMode = false;
        search.closeInputOnly();
        dirty = false;
        validationErrors = null;
        originalEditText = editState.text();
        lineStatuses = null;
        diffOverlay = false;
        // the problems found when the file was loaded are marked at once, before the first change
        inlineErrors = viewErrors;
        lastBackgroundValidationContent = originalEditText;
        editMode = true;
        editHistory.seedInitial(editState);
        refreshEditFindMatches();
    }

    /** The editor operations the AI edit replay drives; only meaningful while in edit mode. */
    EditReplay.Editor replayEditor() {
        return new EditReplay.Editor() {
            @Override
            public List<String> lines() {
                return editLines();
            }

            @Override
            public void moveToRow(int targetRow) {
                editState.moveCursorToStart();
                int max = Math.max(0, editState.lineCount() - 1);
                for (int i = 0; i < Math.min(targetRow, max); i++) {
                    editState.moveCursorDown();
                }
                editState.moveCursorToLineStart();
                if (targetRow > max) {
                    // append: the row does not exist yet, so the text goes after the last line
                    editState.moveCursorToLineEnd();
                    editState.insert('\n');
                }
                editInitialScroll = true;
            }

            @Override
            public void insertAtCursor(String text) {
                recordEditChange();
                editState.insert(text);
            }

            @Override
            public void deleteCurrentLine() {
                recordEditChange();
                int current = editState.cursorRow();
                SourceEditorNavigation.clearLine(editState);
                if (current < editState.lineCount() - 1) {
                    editState.deleteForward();
                } else if (current > 0) {
                    editState.deleteBackward();
                }
            }

            @Override
            public boolean isEditing() {
                return editMode;
            }
        };
    }

    private void exitEditMode() {
        completion.dropSemanticCompletion();
        boolean wasEditing = editMode;
        int cursorRow = editState.cursorRow();
        int top = editState.scrollRow();
        editMode = false;
        editState.clear();
        editHistory.clear();
        completion.closePopup();
        refactor.closePopup();
        validationErrors = null;
        inlineErrors = Collections.emptyMap();
        lastBackgroundValidationTime = 0;
        lastBackgroundValidationContent = null;
        pendingDiscard = false;
        originalEditText = null;
        lineStatuses = null;
        diffOverlay = false;
        if (wasEditing && isMarkdownFile) {
            markdownMode = markdownModeBeforeEdit;
        }
        markdownModeBeforeEdit = false;
        editStartTop = -1;
        if (wasEditing) {
            keepEditorPosition(cursorRow, top);
        }
    }

    /** The top line the editor opens on: the top line of the view, moved only as far as the cursor must stay seen. */
    static int editorTopKeepingCursor(int viewTop, int cursorRow, int viewportHeight) {
        int top = Math.min(Math.max(0, viewTop), cursorRow);
        return Math.max(top, cursorRow - Math.max(1, viewportHeight) + 1);
    }

    /**
     * The view continues where the editor was: the cursor line is selected and the same line is at the top, so leaving
     * the editor does not move the code on the screen.
     */
    private void keepEditorPosition(int cursorRow, int top) {
        if (lines.isEmpty()) {
            return;
        }
        selectedLine = Math.min(Math.max(0, cursorRow), lines.size() - 1);
        scrollY = Math.min(Math.max(0, top), selectedLine);
        pendingScroll = false;
    }

    private boolean isPropertiesFile() {
        return editableFile != null
                && editableFile.getFileName().toString().toLowerCase().endsWith(".properties");
    }

    private boolean isXmlFile() {
        return editableFile != null && editableFile.getFileName().toString().toLowerCase().endsWith(".xml");
    }

    private boolean isCamelYamlFile() {
        if (editableFile == null) {
            return false;
        }
        String name = editableFile.getFileName().toString().toLowerCase();
        return name.endsWith(".yaml") || name.endsWith(".yml");
    }

    private void saveEdit() {
        if (!editMode || editableFile == null) {
            return;
        }
        try {
            String content = editState.text();
            validateAndNotify(content);
            if (validationErrors != null) {
                return;
            }
            Files.writeString(editableFile, content, StandardCharsets.UTF_8);
            dirty = false;
            originalEditText = content;
            Path path = editableFile;
            boolean restoreMarkdownMode = markdownModeBeforeEdit;
            int cursorRow = editState.cursorRow();
            int top = editState.scrollRow();
            notifySaved(editableFile);
            editMode = false;
            editState.clear();
            markdownModeBeforeEdit = false;
            editStartTop = -1;
            loadFile(path);
            keepEditorPosition(cursorRow, top);
            if (isMarkdownFile) {
                markdownMode = restoreMarkdownMode;
            }
        } catch (IOException e) {
            notifySave("Save failed: " + e.getMessage(), true);
        }
    }

    private void saveContinueEdit() {
        if (!editMode || editableFile == null) {
            return;
        }
        try {
            String content = editState.text();
            validateAndNotify(content);
            if (validationErrors != null) {
                return;
            }
            Files.writeString(editableFile, content, StandardCharsets.UTF_8);
            dirty = false;
            originalEditText = content;
            lineStatuses = null;
            notifySaved(editableFile);
        } catch (IOException e) {
            notifySave("Save failed: " + e.getMessage(), true);
        }
    }

    private void validateAndNotify(String content) {
        routeProblems = List.of();
        if (validateOnSave && isCamelYamlFile()) {
            List<String> msgs = new ArrayList<>();
            msgs.addAll(SourceValidationSupport.formatSchemaErrors(validateYaml(content), content));
            if (endpointValidator != null) {
                List<String> endpointErrors = endpointValidator.validate(content);
                if (endpointErrors != null) {
                    msgs.addAll(endpointErrors);
                }
            }
            if (simpleValidator != null) {
                List<String> simpleErrors = simpleValidator.validate(content);
                if (simpleErrors != null) {
                    msgs.addAll(simpleErrors);
                }
            }
            addAll(msgs, kameletValidator, content);
            noteLines = Set.of();
            if (!msgs.isEmpty()) {
                validationErrors = msgs;
                validationErrorScroll = 0;
                inlineErrors = buildInlineErrors(msgs, content);
                return;
            }
            List<String> notes = new ArrayList<>();
            addAll(notes, kameletNotes, content);
            if (!notes.isEmpty()) {
                // marked, and said when saved, but not blocking the save
                routeProblems = notes;
                inlineErrors = buildInlineErrors(notes, content);
                noteLines = new HashSet<>(inlineErrors.keySet());
                return;
            }
        } else if (validateOnSave && isPropertiesFile() && propertiesValidator != null) {
            List<String> msgs = validateProperties(content);
            if (!msgs.isEmpty()) {
                validationErrors = msgs;
                validationErrorScroll = 0;
                inlineErrors = buildInlineErrors(msgs, content);
                return;
            }
        } else if (validateOnSave && routeValidator != null) {
            List<String> msgs = routeValidator.validate(content);
            msgs = msgs != null ? msgs : List.of();
            inlineErrors = msgs.isEmpty() ? Collections.emptyMap() : buildInlineErrors(msgs, content);
            if (isXmlFile() && !msgs.isEmpty()) {
                // XML routes as YAML routes: the problems are shown and the file is not saved
                validationErrors = msgs;
                validationErrorScroll = 0;
            } else {
                // Java: marked, and said when saved, but not blocking the save
                routeProblems = msgs;
            }
            return;
        }
        inlineErrors = Collections.emptyMap();
    }

    /** The notice of a save: the file, and the Camel problems of a Java or XML route file when it has some. */
    private void notifySaved(Path file) {
        if (routeProblems.isEmpty()) {
            notifySave("Saved: " + file.getFileName(), false);
        } else {
            notifySave("Saved: " + file.getFileName() + " with " + routeProblems.size() + " Camel problem"
                       + (routeProblems.size() > 1 ? "s" : "") + ": " + routeProblems.get(0),
                    true);
        }
    }

    /** The first line with a problem that has a fix, for the popup of a save it blocked; -1 for none. */
    private int popupFixRow() {
        for (Map.Entry<Integer, String> e : new java.util.TreeMap<>(visibleInlineErrors()).entrySet()) {
            int row = e.getKey();
            if (row >= 0 && row < editState.lineCount()
                    && QuickFixes.fixFor(e.getValue(), editState.getLine(row)) != null) {
                return row;
            }
        }
        return -1;
    }

    /** The fix of the problem on the line of the cursor, when the problem says it; null otherwise. */
    private QuickFixes.Fix cursorFix() {
        int row = editState.cursorRow();
        String error = visibleInlineErrors().get(row);
        return error != null ? QuickFixes.fixFor(error, editState.getLine(row)) : null;
    }

    /**
     * Asks the AI to fix the problem of the cursor line (Shift+F8), or when it has none, the line whose processors fail
     * at runtime: the file is saved as it is in the editor, which the AI is about to change, edit mode left so the
     * editor shows what the AI sees, and the AI panel opened with the question in its input, for the user to send with
     * Enter or change first.
     */
    private void askAiToFix() {
        if (askAi == null || editableFile == null) {
            return;
        }
        if (!editMode) {
            askAiToFixInView();
            return;
        }
        int row = editState.cursorRow();
        String problem = visibleInlineErrors().get(row);
        // a runtime failure is only known for the saved file (null while dirty), so it needs no save
        String failure = problem == null ? liveColumn.runtimeFailure(row) : null;
        if (problem == null && failure == null) {
            return;
        }
        String lineText = editState.getLine(row);
        Path file = editableFile;
        if (dirty) {
            try {
                Files.writeString(file, editState.text(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                notifySave("Save failed: " + e.getMessage(), true);
                return;
            }
            dirty = false;
        }
        exitEditMode();
        loadFile(file);
        goToLine(row);
        if (problem != null) {
            askAi.fixProblem(file, row + 1, problem, lineText);
        } else {
            askAi.fixFailure(file, row + 1, failure, lineText);
        }
    }

    /**
     * Shift+F8 in the view, as in the editor: the problem of the selected line, or else its runtime failure, while the
     * view shows the file's own source (not the route converted to another DSL).
     */
    private void askAiToFixInView() {
        int row = selectedLine;
        if (row < 0 || row >= lines.size() || !showsOwnSource()) {
            return;
        }
        // the view renders the lines (line numbers, live data), so the text comes from the file, which the view shows
        String lineText = RuntimeFailures.lineText(editableFile, row);
        String problem = viewErrors.get(row);
        if (problem != null) {
            askAi.fixProblem(editableFile, row + 1, problem, lineText);
            return;
        }
        String failure = liveColumn.runtimeFailure(row);
        if (failure != null) {
            askAi.fixFailure(editableFile, row + 1, failure, lineText);
        }
    }

    /** Whether the view shows the file's own source, and not its route converted to another DSL (Space). */
    private boolean showsOwnSource() {
        return !markdownMode && (currentFormat == null || currentFormat.equals(originalFormat));
    }

    /**
     * Whether the view shows the fix with AI hint (Shift+F8) for the selected line: the Source tab puts it with the
     * global F-keys.
     */
    boolean showsFixWithAiHint() {
        return visible && !editMode && showsOwnSource() && canAskAiToFix(selectedLine, viewErrors);
    }

    /** Whether Shift+F8 (fix with AI) has something to ask about on a 0-based line: a problem, or a runtime failure. */
    private boolean canAskAiToFix(int row, Map<Integer, String> problems) {
        return askAi != null && editableFile != null && row >= 0
                && (problems.containsKey(row) || liveColumn.failsAtRuntime(row));
    }

    /** Applies the fix of the problem on the line of the cursor (Shift+F9), and checks the content again right away. */
    private void applyQuickFix() {
        QuickFixes.Fix fix = cursorFix();
        if (fix == null) {
            return;
        }
        int row = editState.cursorRow();
        int col = editState.cursorCol();
        String line = editState.getLine(row);
        String fixed = fix.apply(line);
        if (fixed == null) {
            return;
        }
        if (fix.removesLine()) {
            // an unused dependency of a Kamelet: the line goes, as with Ctrl+K, and the dependencies: key above it
            // when it was the last one
            int[] range = QuickFixes.linesToRemove(editLines(), row);
            List<String> kept = new ArrayList<>(editLines());
            kept.subList(range[0], range[1] + 1).clear();
            applyBlockEdit(new YamlBlockEditor.EditResult(kept, Math.min(range[0], Math.max(0, kept.size() - 1)), 0));
            dirty = true;
            lineStatuses = null;
            lastBackgroundValidationTime = 0;
            notifySave("Fixed: " + fix.label(), false);
            return;
        }
        recordEditChange();
        SourceEditorNavigation.clearLine(editState);
        editState.insert(fixed);
        editState.moveCursorToLineStart();
        SourceEditorNavigation.moveToColumn(editState, col);
        dirty = true;
        lineStatuses = null;
        // the problem is gone: check again now, not at the next interval
        lastBackgroundValidationTime = 0;
        notifySave("Fixed: " + fix.label(), false);
    }

    private void jumpToNextError() {
        goToNextProblem(inlineErrors, editState.cursorRow());
    }

    /** Goes to the first problem after the row, or wraps around to the first one. */
    private void goToNextProblem(Map<Integer, String> problems, int cursorRow) {
        List<Integer> errorLines = new ArrayList<>(problems.keySet());
        Collections.sort(errorLines);
        // find the first error line after the cursor
        for (int line : errorLines) {
            if (line > cursorRow) {
                goToLine(line);
                return;
            }
        }
        // wrap around to the first error
        if (!errorLines.isEmpty()) {
            goToLine(errorLines.get(0));
        }
    }

    private void runBackgroundValidation() {
        if (!dirty || validationErrors != null) {
            return;
        }
        long now = MonotonicClock.millis();
        if (now - lastBackgroundValidationTime < BACKGROUND_VALIDATION_INTERVAL_MS) {
            return;
        }
        String content = editState.text();
        if (content.equals(lastBackgroundValidationContent)) {
            return;
        }
        lastBackgroundValidationTime = now;
        lastBackgroundValidationContent = content;

        List<String> msgs = validateContent(content);
        inlineErrors = msgs.isEmpty() ? Collections.emptyMap() : buildInlineErrors(msgs, content);
    }

    /** The problems the checks of the file type (Camel YAML, properties, Java or XML routes) find in the content. */
    private List<String> validateContent(String content) {
        List<String> msgs = new ArrayList<>();
        noteLines = Set.of();
        if (isCamelYamlFile()) {
            if (endpointValidator != null) {
                List<String> endpointErrors = endpointValidator.validate(content);
                if (endpointErrors != null) {
                    msgs.addAll(endpointErrors);
                }
            }
            if (simpleValidator != null) {
                List<String> simpleErrors = simpleValidator.validate(content);
                if (simpleErrors != null) {
                    msgs.addAll(simpleErrors);
                }
            }
            addAll(msgs, kameletValidator, content);
            List<String> notes = new ArrayList<>();
            addAll(notes, kameletNotes, content);
            if (!notes.isEmpty()) {
                // a line with an error is marked as an error
                Set<Integer> lines = new HashSet<>(buildInlineErrors(notes, content).keySet());
                if (!msgs.isEmpty()) {
                    lines.removeAll(buildInlineErrors(msgs, content).keySet());
                }
                noteLines = lines;
                msgs.addAll(notes);
            }
        } else if (isPropertiesFile() && propertiesValidator != null) {
            msgs.addAll(validateProperties(content));
        } else if (routeValidator != null) {
            List<String> routeErrors = routeValidator.validate(content);
            if (routeErrors != null) {
                msgs.addAll(routeErrors);
            }
        }
        return msgs;
    }

    /** The title spans counting the problems of the lines: the errors, and apart from them the notes. */
    private List<Span> problemCounts(Map<Integer, String> problems, Style errorStyle) {
        List<Span> spans = new ArrayList<>();
        int notes = 0;
        for (Integer line : problems.keySet()) {
            if (noteLines.contains(line)) {
                notes++;
            }
        }
        int errors = problems.size() - notes;
        if (errors > 0) {
            spans.add(Span.styled(" errors: " + errors + " ", errorStyle));
        }
        if (notes > 0) {
            spans.add(Span.styled(" notes: " + notes + " ", Theme.warning()));
        }
        return spans;
    }

    /** Package-private for tests: the lines, from 0, whose problem is a note. */
    Set<Integer> noteLines() {
        return noteLines;
    }

    private static void addAll(List<String> msgs, EndpointValidator validator, String content) {
        if (validator != null) {
            List<String> found = validator.validate(content);
            if (found != null) {
                msgs.addAll(found);
            }
        }
    }

    /** The problems of the file just loaded, by line; none when the checks fail or do not apply. */
    private Map<Integer, String> problemsOnLoad(String content) {
        if (isMarkdownFile) {
            return Collections.emptyMap();
        }
        try {
            List<String> msgs = validateContent(content);
            return msgs.isEmpty() ? Collections.emptyMap() : buildInlineErrors(msgs, content);
        } catch (RuntimeException e) {
            return Collections.emptyMap();
        }
    }

    /** Package-private for tests: the problems marked in the view, by line from 0. */
    Map<Integer, String> viewErrors() {
        return viewErrors;
    }

    /**
     * Inline errors to actually display right now. A line with no value typed yet (e.g. a field just added via
     * Tab-completion, "key:" with nothing after it) is still being filled in, so its error — which is really just "you
     * haven't finished this" — is suppressed. This is value-based rather than cursor-based: a genuinely wrong,
     * non-empty value (e.g. a Simple language typo) still flags immediately, without needing to move the cursor away
     * first.
     */
    private Map<Integer, String> visibleInlineErrors() {
        if (inlineErrors.isEmpty()) {
            return inlineErrors;
        }
        Map<Integer, String> visible = null;
        for (Integer line : inlineErrors.keySet()) {
            if (line >= 0 && line < editState.lineCount() && isEmptyValueLine(editState.getLine(line))) {
                if (visible == null) {
                    visible = new java.util.LinkedHashMap<>(inlineErrors);
                }
                visible.remove(line);
            }
        }
        return visible != null ? visible : inlineErrors;
    }

    private List<String> validateProperties(String content) {
        return SourceValidationSupport.validatePropertiesLines(content, propertiesValidator::validate);
    }

    private List<Error> validateYaml(String content) {
        try {
            if (yamlValidator == null) {
                yamlValidator = new org.apache.camel.dsl.yaml.validator.YamlValidator();
            }
            return yamlValidator.validate(content);
        } catch (Exception e) {
            return List.of();
        }
    }

    private void notifySave(String message, boolean error) {
        if (notificationCallback != null) {
            notificationCallback.accept(message, error);
        }
    }

    private String buildEditableText() {
        if (codeData.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < codeData.size(); i++) {
            if (i > 0) {
                sb.append('\n');
            }
            Object code = codeData.get(i).get("code");
            sb.append(code != null ? code.toString() : "");
        }
        return sb.toString();
    }

    boolean handleMouseEvent(MouseEvent me) {
        if (!visible) {
            return false;
        }
        if (editMode) {
            if (completion.handleMouseEvent(me)) {
                return true;
            }
            if (me.kind() == MouseEventKind.SCROLL_UP) {
                editState.scrollUp(3);
                return true;
            }
            if (me.kind() == MouseEventKind.SCROLL_DOWN) {
                int viewport = Math.max(1, lastVisibleLines);
                editState.scrollDown(3, viewport);
                return true;
            }
            return true;
        }
        if (markdownMode) {
            if (me.kind() == MouseEventKind.SCROLL_UP) {
                scrollMarkdown(-3);
                return true;
            }
            if (me.kind() == MouseEventKind.SCROLL_DOWN) {
                scrollMarkdown(3);
                return true;
            }
            return true;
        }
        if (me.kind() == MouseEventKind.SCROLL_UP) {
            selectedLine = Math.max(0, selectedLine - 3);
            return true;
        }
        if (me.kind() == MouseEventKind.SCROLL_DOWN) {
            if (!lines.isEmpty()) {
                selectedLine = Math.min(lines.size() - 1, selectedLine + 3);
            }
            return true;
        }
        if (me.isClick() && lastInnerArea != null && lastInnerArea.contains(me.x(), me.y())) {
            int clickedLine = scrollY + (me.y() - lastInnerArea.top());
            if (clickedLine >= 0 && clickedLine < lines.size()) {
                selectedLine = clickedLine;
            }
            return true;
        }
        return true;
    }

    boolean isSearchInputActive() {
        return search.isSearchInputActive();
    }

    void handlePaste(String text) {
        completion.dropSemanticCompletion();
        if (editMode) {
            if (search.isSearchInputActive()) {
                search.handlePaste(text);
                return;
            }
            if (text != null && !text.isEmpty()) {
                recordEditChange();
                int row = editState.cursorRow();
                String current = editState.getLine(row);
                String adjusted = yaml.adjustPasteIndent(text, row);
                if (current != null && current.isBlank() && !current.isEmpty()) {
                    // strip the blank line's leading whitespace (e.g. from ENTER auto-indent) so the
                    // reindented block's own indent is not stacked on top of it
                    SourceEditorNavigation.clearLine(editState);
                }
                editState.insert(adjusted);
            }
            return;
        }
        search.handlePaste(text);
    }

    void render(Frame frame, Rect area) {
        completion.refreshSemanticCompletion();
        renderContent(frame, area);
        if (routeTreeShown && !markdownMode && !diffOverlay) {
            if (editMode) {
                List<String> text = Arrays.asList(editState.text().split("\n", -1));
                String name = editableFile != null ? editableFile.getFileName().toString() : title;
                routeTree.render(frame, area, text, name, editState.cursorRow());
            } else {
                String name = currentRouteId != null ? "route." + currentFormat : title;
                // the code without the line numbers of the view
                List<String> code = codeData != null
                        ? codeData.stream().map(c -> c.getStringOrDefault("code", "")).toList() : List.of();
                routeTree.render(frame, area, code, name, selectedLine);
            }
        }
    }

    private void renderContent(Frame frame, Rect area) {
        if (editMode) {
            renderEditMode(frame, area);
            return;
        }
        if (markdownMode && rawMarkdownContent != null) {
            Block.Builder bb = Block.builder()
                    .borderType(BorderType.ROUNDED).borders(Borders.ALL)
                    .title(buildTitle());
            if (borderStyle != null) {
                bb.borderStyle(borderStyle);
            }
            Block block = bb.build();
            MarkdownView.Builder viewBuilder = MarkdownView.builder()
                    .source(rawMarkdownContent)
                    .styles(Theme.markdownStyles())
                    .syntaxTheme(Theme.syntaxTheme());
            // the scroll stays within the document, so End, then Up or PgUp, move from the end at once
            Rect inner = block.inner(area);
            markdownMaxScroll = Math.max(0, viewBuilder.build().computeHeight(inner.width()) - inner.height());
            markdownScroll = Math.max(0, Math.min(markdownScroll, markdownMaxScroll));
            frame.renderWidget(viewBuilder.scroll(markdownScroll).block(block).build(), area);
            return;
        }

        Block.Builder blockBuilder = Block.builder()
                .borderType(BorderType.ROUNDED);
        if (plainMode) {
            int lineNum = selectedLine + 1;
            Title posTitle = Title.from(
                    Line.from(Span.styled(" line:" + lineNum + " ", Style.EMPTY.dim()))).right();
            blockBuilder.borders(java.util.EnumSet.of(Borders.TOP, Borders.BOTTOM))
                    .titleBottom(posTitle);
        } else {
            blockBuilder.borders(Borders.ALL).title(buildTitle());
        }
        if (borderStyle != null) {
            blockBuilder.borderStyle(borderStyle);
        }
        Block block = blockBuilder.build();
        Rect inner = block.inner(area);
        lastInnerArea = inner;
        frame.renderWidget(block, area);

        if (lines.isEmpty()) {
            return;
        }

        // quick doc panel at the bottom (same as edit mode)
        Rect contentArea = inner;
        Rect viewDocArea = null;
        List<DocEntry> viewDocEntries = null;
        int docPanelHeight = 4;
        if (editQuickDocEnabled && editQuickDocProvider != null && inner.height() > 10) {
            contentArea = new Rect(inner.left(), inner.top(), inner.width(), inner.height() - docPanelHeight);
            viewDocArea = new Rect(inner.left(), inner.top() + inner.height() - docPanelHeight, inner.width(), docPanelHeight);
            if (selectedLine >= 0 && selectedLine < codeData.size()) {
                List<String> rawLines = new ArrayList<>(codeData.size());
                for (JsonObject jo : codeData) {
                    rawLines.add(jo.getString("code") != null ? jo.getString("code") : "");
                }
                viewDocEntries = editQuickDocProvider.provideForLine(rawLines, selectedLine);
                if (cursorQuickDocProvider != null) {
                    List<DocEntry> used = cursorQuickDocProvider.provideAt(rawLines, selectedLine, -1);
                    if (used != null && !used.isEmpty()) {
                        List<DocEntry> merged = new ArrayList<>();
                        if (viewDocEntries != null) {
                            merged.addAll(viewDocEntries);
                        }
                        merged.addAll(used);
                        viewDocEntries = merged;
                    }
                }
            }
        }

        int visibleLines = contentArea.height();

        // Reserve bottom row for horizontal scrollbar when content is wider than viewport
        if (!wordWrap) {
            int cursorWidth = 3;
            int maxLineWidth = lines.stream().mapToInt(String::length).max().orElse(0) + cursorWidth;
            if (maxLineWidth > contentArea.width()) {
                visibleLines = Math.max(1, visibleLines - 1);
            }
        }
        lastVisibleLines = visibleLines;

        // On initial load, position selected line at 2/3 of viewport
        if (pendingScroll && selectedLine >= 0) {
            int twoThirds = visibleLines * 2 / 3;
            scrollY = Math.max(0, selectedLine - twoThirds);
            pendingScroll = false;
        }

        int contentWidth = contentArea.width() - 1;

        // Auto-scroll to keep selected line visible
        if (selectedLine >= 0) {
            if (selectedLine < scrollY) {
                scrollY = selectedLine;
            } else if (wordWrap) {
                scrollY = Math.max(scrollY, firstLineThatFits(selectedLine, visibleLines, contentWidth));
            } else if (selectedLine >= scrollY + visibleLines) {
                scrollY = selectedLine - visibleLines + 1;
            }
        }

        int maxScroll;
        if (wordWrap) {
            maxScroll = firstLineThatFits(lines.size() - 1, visibleLines, contentWidth);
        } else {
            maxScroll = Math.max(0, lines.size() - visibleLines);
        }
        scrollY = Math.min(scrollY, maxScroll);

        int hSkip = wordWrap ? 0 : scrollX;
        if (!wordWrap) {
            int cursorWidth = 3;
            int maxLineWidth = lines.stream().mapToInt(String::length).max().orElse(0) + cursorWidth;
            int maxHScroll = Math.max(0, maxLineWidth - contentArea.width());
            scrollX = Math.min(scrollX, maxHScroll);
        }

        int currentMatchLine = search.currentMatchLine();

        List<Line> visible = new ArrayList<>();
        for (int i = scrollY; i < lines.size() && visible.size() < visibleLines; i++) {
            String raw = lines.get(i);
            boolean isSelected = (i == selectedLine);
            Line line = highlightSourceLine(raw, i, hSkip, isSelected, contentArea.width());
            line = search.applyHighlights(line, i, currentMatchLine);
            visible.add(line);
        }

        List<Rect> hChunks = Layout.horizontal()
                .constraints(Constraint.fill(), Constraint.length(1))
                .split(contentArea);

        Overflow overflow = wordWrap ? Overflow.WRAP_WORD : Overflow.CLIP;
        frame.renderWidget(Paragraph.builder().text(Text.from(visible)).overflow(overflow).build(), hChunks.get(0));

        if (plainMode && selectedLine >= scrollY && selectedLine < scrollY + visibleLines) {
            int relRow = selectedLine - scrollY;
            int screenY = contentArea.top() + relRow;
            Rect lineRect = new Rect(contentArea.left(), screenY, contentArea.width(), 1);
            Style selBg = focused ? Theme.selectionBg() : Theme.selectionBg().dim();
            frame.buffer().setStyle(lineRect, selBg);
        }

        if (lines.size() > visibleLines) {
            vScrollState.contentLength(lines.size()).viewportContentLength(visibleLines).position(scrollY);
            frame.renderStatefulWidget(Scrollbar.builder().build(), hChunks.get(1), vScrollState);
        }
        if (!wordWrap) {
            int cursorWidth = 3;
            int maxLineWidth = lines.stream().mapToInt(String::length).max().orElse(0) + cursorWidth;
            int maxHScroll = Math.max(0, maxLineWidth - contentArea.width());
            if (maxHScroll > 0) {
                hScrollState.contentLength(maxLineWidth).viewportContentLength(contentArea.width()).position(scrollX);
                frame.renderStatefulWidget(Scrollbar.horizontal(), contentArea, hScrollState);
            }
        }

        // quick doc panel at the bottom
        if (viewDocArea != null) {
            List<Line> docLines = new ArrayList<>();
            String titleText = null;
            if (viewDocEntries != null && !viewDocEntries.isEmpty()) {
                titleText = viewDocEntries.get(0).title();
            }
            String problem = viewErrors.get(selectedLine);
            if (problem != null) {
                // the problem of the line goes before its documentation, as the Error panel of the editor shows it
                boolean note = noteLines.contains(selectedLine);
                Style problemStyle = note ? Theme.warning() : Theme.error();
                String kind = note ? " Note " : " Error ";
                int remaining = Math.max(0, viewDocArea.width() - kind.length() - 3);
                docLines.add(Line.from(
                        Span.styled("───", problemStyle),
                        Span.styled(kind, problemStyle.bold()),
                        Span.styled("─".repeat(remaining), problemStyle)));
                docLines.add(Line.from(Span.styled(problem, problemStyle)));
                docLines.add(Line.from(Span.styled("F4 edit   F9 next problem", Style.EMPTY.dim())));
            } else if (titleText != null) {
                String prefix = "─── ";
                String suffix = " ";
                int remaining = Math.max(0, viewDocArea.width() - prefix.length() - titleText.length() - suffix.length());
                docLines.add(Line.from(
                        Span.styled(prefix, Style.EMPTY.dim()),
                        Span.styled(titleText, Style.EMPTY.dim().bold()),
                        Span.styled(suffix + "─".repeat(remaining), Style.EMPTY.dim())));
            } else {
                docLines.add(Line.from(Span.styled("─".repeat(Math.max(1, viewDocArea.width())), Style.EMPTY.dim())));
            }
            if (problem == null && viewDocEntries != null && !viewDocEntries.isEmpty()) {
                for (int d = 0; d < viewDocEntries.size() && d < viewDocArea.height() - 1; d++) {
                    DocEntry entry = viewDocEntries.get(d);
                    Style docStyle = entry.deprecated() ? Style.EMPTY.dim().italic() : Style.EMPTY.dim();
                    docLines.add(Line.from(Span.styled(entry.text(), docStyle)));
                }
            }
            frame.renderWidget(
                    Paragraph.builder().text(Text.from(docLines)).overflow(Overflow.WRAP_WORD).build(),
                    viewDocArea);
        }
    }

    private void renderEditMode(Frame frame, Rect area) {
        Map<Integer, String> visibleErrors = visibleInlineErrors();
        Style ts = titleStyle != null ? titleStyle : Style.EMPTY;
        List<Span> titleSpans = new ArrayList<>();
        String info = title != null ? title : "";
        if (diffOverlay) {
            titleSpans.add(Span.styled(" Diff [" + info + "] ", ts));
        } else {
            titleSpans.add(Span.styled(" Edit [" + info + (dirty ? " *" : "") + "] ", ts));
            if (isCamelYamlFile()) {
                String breadcrumb = yaml.buildBreadcrumb(editState.cursorRow());
                if (!breadcrumb.isEmpty()) {
                    titleSpans.add(Span.styled(" " + breadcrumb + " ", Style.EMPTY.dim().italic()));
                }
            }
        }
        if (!diffOverlay && !visibleErrors.isEmpty()) {
            // a block has one title at the top: the error count goes on the line of the file name, not instead of it
            Style errorStyle = Style.EMPTY.fg(dev.tamboui.style.Color.rgb(0xFF, 0x66, 0x66));
            titleSpans.addAll(problemCounts(visibleErrors, errorStyle));
        }
        Title posTitle;
        if (diffOverlay) {
            posTitle = Title.from(
                    Line.from(Span.styled(" F7/Esc close  ↑↓ scroll ", Style.EMPTY.dim()))).right();
        } else {
            int row = editState.cursorRow() + 1;
            int col = editState.cursorCol() + 1;
            posTitle = Title.from(
                    Line.from(Span.styled(" row:" + row + " col:" + col + " ", Style.EMPTY.dim()))).right();
        }
        Block.Builder blockBuilder = Block.builder()
                .borderType(BorderType.ROUNDED);
        if (plainMode) {
            blockBuilder.borders(java.util.EnumSet.of(Borders.TOP, Borders.BOTTOM))
                    .titleBottom(posTitle);
        } else {
            blockBuilder.borders(Borders.ALL)
                    .title(Title.from(Line.from(titleSpans)))
                    .titleBottom(posTitle);
        }
        if (borderStyle != null) {
            blockBuilder.borderStyle(borderStyle);
        }
        Block block = blockBuilder.build();
        Rect inner = block.inner(area);
        lastInnerArea = inner;
        lastVisibleLines = Math.max(1, inner.height());
        frame.renderWidget(block, area);

        if (diffOverlay) {
            renderDiffContent(frame, inner);
            return;
        }

        runBackgroundValidation();

        // split inner area for quick doc panel at the bottom (fixed height to avoid flicker)
        List<DocEntry> editDocEntries = null;
        Rect editorArea = inner;
        Rect docArea = null;
        int docPanelHeight = 4;
        if (editQuickDocEnabled && editQuickDocProvider != null && inner.height() > 10) {
            editorArea = new Rect(inner.left(), inner.top(), inner.width(), inner.height() - docPanelHeight);
            docArea = new Rect(inner.left(), inner.top() + inner.height() - docPanelHeight, inner.width(), docPanelHeight);
            lastVisibleLines = Math.max(1, editorArea.height());
            List<String> textLines = editLines();
            editDocEntries = editQuickDocProvider.provideForLine(textLines, editState.cursorRow());
            if (cursorQuickDocProvider != null) {
                List<DocEntry> here = cursorQuickDocProvider.provideAt(textLines, editState.cursorRow(),
                        editState.cursorCol());
                if (here != null && !here.isEmpty()) {
                    // the function the cursor is on is more to the point than the step of the line
                    List<DocEntry> merged = new ArrayList<>(here);
                    if (editDocEntries != null) {
                        merged.addAll(editDocEntries);
                    }
                    editDocEntries = merged;
                }
            }
        }

        int prefixWidth = plainMode ? 0 : 3;
        Rect textAreaRect = plainMode
                ? editorArea
                : new Rect(
                        editorArea.left() + prefixWidth, editorArea.top(),
                        editorArea.width() - prefixWidth, editorArea.height());

        TextArea textArea = TextArea.builder()
                .cursorStyle(Style.EMPTY.reversed())
                .showLineNumbers(!plainMode)
                .lineNumberStyle(Style.EMPTY.dim())
                .build();
        // on first render, keep the top line of the view (F4), else position the cursor at 2/3 of the viewport
        if (editInitialScroll) {
            editInitialScroll = false;
            int viewportH = textAreaRect.height();
            int targetScroll = editStartTop >= 0
                    ? editorTopKeepingCursor(editStartTop, editState.cursorRow(), viewportH)
                    : Math.max(0, editState.cursorRow() - viewportH * 2 / 3);
            editStartTop = -1;
            int delta = targetScroll - editState.scrollRow();
            if (delta > 0) {
                editState.scrollDown(delta, viewportH);
            } else if (delta < 0) {
                editState.scrollUp(-delta);
            }
        }

        textArea.renderWithCursor(textAreaRect, frame.buffer(), editState, frame);

        applySyntaxHighlightOverlay(frame, textAreaRect);

        // cursor line highlight with >> marker
        int cursorRelRow = editState.cursorRow() - editState.scrollRow();
        if (cursorRelRow >= 0 && cursorRelRow < editorArea.height()) {
            int screenY = editorArea.top() + cursorRelRow;
            Rect lineRect = new Rect(editorArea.left(), screenY, editorArea.width(), 1);
            frame.buffer().setStyle(lineRect, Style.EMPTY.bg(Theme.zebra()));
            if (!plainMode) {
                Style markerStyle = Theme.label().bold().bg(Theme.zebra());
                frame.buffer().set(editorArea.left(), screenY,
                        new dev.tamboui.buffer.Cell(">", markerStyle));
                frame.buffer().set(editorArea.left() + 1, screenY,
                        new dev.tamboui.buffer.Cell(">", markerStyle));
            }
        }

        // scope line highlight — shows which EIP or uri: line the cursor belongs to
        if (isCamelYamlFile()) {
            int scopeRow = yaml.findScopeLineRow(editState.cursorRow());
            if (scopeRow >= 0 && scopeRow != editState.cursorRow()) {
                int relativeRow = scopeRow - editState.scrollRow();
                if (relativeRow >= 0 && relativeRow < editorArea.height()) {
                    int screenY = editorArea.top() + relativeRow;
                    Rect lineRect = new Rect(editorArea.left(), screenY, editorArea.width(), 1);
                    frame.buffer().setStyle(lineRect, Style.EMPTY.bold().fg(Theme.accent()));
                }
            }
        }

        // gutter change markers — background color on the gutter area
        if (dirty && !plainMode && originalEditText != null) {
            if (lineStatuses == null) {
                List<String> orig = YamlBlockEditor.toLines(originalEditText);
                lineStatuses = EditDiff.diff(orig, editLines());
            }
            int gutterWidth = Math.max(2, String.valueOf(editState.lineCount()).length()) + 2;
            for (int r = 0; r < editorArea.height(); r++) {
                int lineIdx = editState.scrollRow() + r;
                if (lineIdx >= 0 && lineIdx < lineStatuses.length) {
                    EditDiff.LineStatus status = lineStatuses[lineIdx];
                    if (status != EditDiff.LineStatus.UNCHANGED) {
                        Style bg = Style.EMPTY.fg(dev.tamboui.style.Color.WHITE)
                                .bg(dev.tamboui.style.Color.rgb(0x1B, 0x4D, 0x1B));
                        int screenY = editorArea.top() + r;
                        for (int x = textAreaRect.left(); x < textAreaRect.left() + gutterWidth; x++) {
                            dev.tamboui.buffer.Cell cell = frame.buffer().get(x, screenY);
                            frame.buffer().set(x, screenY,
                                    new dev.tamboui.buffer.Cell(cell.symbol(), bg));
                        }
                    }
                }
            }
        }

        // error gutter markers — red line number for lines with validation errors
        if (!visibleErrors.isEmpty() && !plainMode) {
            int gutterWidth = Math.max(2, String.valueOf(editState.lineCount()).length()) + 2;
            for (int r = 0; r < editorArea.height(); r++) {
                int lineIdx = editState.scrollRow() + r;
                if (visibleErrors.containsKey(lineIdx)) {
                    // a note on dark amber, an error on dark red
                    Style errorBg = Style.EMPTY.fg(dev.tamboui.style.Color.WHITE)
                            .bg(noteLines.contains(lineIdx)
                                    ? dev.tamboui.style.Color.rgb(0x80, 0x60, 0x00)
                                    : dev.tamboui.style.Color.rgb(0x8B, 0x00, 0x00));
                    int screenY = editorArea.top() + r;
                    for (int x = textAreaRect.left(); x < textAreaRect.left() + gutterWidth; x++) {
                        dev.tamboui.buffer.Cell cell = frame.buffer().get(x, screenY);
                        frame.buffer().set(x, screenY,
                                new dev.tamboui.buffer.Cell(cell.symbol(), errorBg));
                    }
                }
            }
        }

        // quick doc panel at the bottom — errors take priority over doc
        if (docArea != null) {
            String cursorError = visibleErrors.get(editState.cursorRow());
            List<Line> docLines = new ArrayList<>();
            String titleText = null;
            if (cursorError != null) {
                titleText = noteLines.contains(editState.cursorRow()) ? "Note" : "Error";
            } else if (editDocEntries != null && !editDocEntries.isEmpty()) {
                titleText = editDocEntries.get(0).title();
            }
            if (cursorError != null) {
                String prefix = "─── ";
                String suffix = " ";
                int remaining = Math.max(0, docArea.width() - prefix.length() - titleText.length() - suffix.length());
                Style errorDim = noteLines.contains(editState.cursorRow())
                        ? Theme.warning()
                        : Style.EMPTY.fg(dev.tamboui.style.Color.rgb(0xFF, 0x66, 0x66));
                docLines.add(Line.from(
                        Span.styled(prefix, errorDim),
                        Span.styled(titleText, errorDim.bold()),
                        Span.styled(suffix + "─".repeat(remaining), errorDim)));
                docLines.add(Line.from(Span.styled(cursorError, errorDim)));
                QuickFixes.Fix fix = cursorFix();
                List<Span> actions = new ArrayList<>();
                if (fix != null) {
                    actions.add(Span.styled("Shift+F9", Style.EMPTY.bold()));
                    actions.add(Span.styled(" fix: " + fix.label() + "   ", Style.EMPTY.dim()));
                }
                if (askAi != null) {
                    actions.add(Span.styled("Shift+F8", Style.EMPTY.bold()));
                    actions.add(Span.styled(" fix with AI", Style.EMPTY.dim()));
                }
                if (!actions.isEmpty()) {
                    docLines.add(Line.from(actions));
                }
            } else if (titleText != null) {
                String prefix = "─── ";
                String suffix = " ";
                int remaining = Math.max(0, docArea.width() - prefix.length() - titleText.length() - suffix.length());
                docLines.add(Line.from(
                        Span.styled(prefix, Style.EMPTY.dim()),
                        Span.styled(titleText, Style.EMPTY.dim().bold()),
                        Span.styled(suffix + "─".repeat(remaining), Style.EMPTY.dim())));
            } else {
                docLines.add(Line.from(Span.styled("─".repeat(Math.max(1, docArea.width())), Style.EMPTY.dim())));
            }
            if (cursorError == null && editDocEntries != null && !editDocEntries.isEmpty()) {
                for (int d = 0; d < editDocEntries.size() && d < docArea.height() - 1; d++) {
                    DocEntry entry = editDocEntries.get(d);
                    Style docStyle = entry.deprecated() ? Style.EMPTY.dim().italic() : Style.EMPTY.dim();
                    docLines.add(Line.from(Span.styled(entry.text(), docStyle)));
                }
            }
            frame.renderWidget(
                    Paragraph.builder().text(Text.from(docLines)).overflow(Overflow.WRAP_WORD).build(),
                    docArea);
        }

        completion.render(frame, editorArea);

        if (validationErrors != null) {
            renderValidationPopup(frame, area);
        }
        if (pendingDiscard) {
            renderDiscardPopup(frame, area);
        }
        refactor.render(frame, area);
    }

    private void applySyntaxHighlightOverlay(Frame frame, Rect editorArea) {
        if (language == SyntaxHighlighter.Language.PLAIN) {
            return;
        }
        int gutterWidth = plainMode
                ? 0
                : Math.max(2, String.valueOf(editState.lineCount()).length()) + 2;
        int contentStartX = editorArea.left() + gutterWidth;
        int scrollCol = editState.scrollCol();
        int rightEdge = editorArea.right();

        for (int row = 0; row < editorArea.height(); row++) {
            int lineIdx = editState.scrollRow() + row;
            if (lineIdx >= editState.lineCount()) {
                break;
            }
            String lineText = editState.getLine(lineIdx);
            if (lineText.isEmpty()) {
                continue;
            }
            Line highlighted = SyntaxHighlighter.highlightLine(lineText, language);
            int screenY = editorArea.top() + row;
            int textCol = 0;
            for (Span span : highlighted.spans()) {
                Style spanStyle = span.style();
                boolean hasStyle = spanStyle != null && !Style.EMPTY.equals(spanStyle);
                String content = span.content();
                for (int c = 0; c < content.length(); c++) {
                    int col = textCol + c;
                    if (col < scrollCol) {
                        continue;
                    }
                    int screenX = contentStartX + (col - scrollCol);
                    if (screenX >= rightEdge) {
                        break;
                    }
                    if (hasStyle) {
                        dev.tamboui.buffer.Cell cell = frame.buffer().get(screenX, screenY);
                        if (cell != null && !cell.isContinuation()) {
                            frame.buffer().set(screenX, screenY, cell.patchStyle(spanStyle));
                        }
                    }
                }
                textCol += content.length();
                if (contentStartX + (textCol - scrollCol) >= rightEdge) {
                    break;
                }
            }
        }
    }

    private void renderDiffContent(Frame frame, Rect inner) {
        List<String> orig = YamlBlockEditor.toLines(originalEditText);
        List<EditDiff.DiffEntry> entries = EditDiff.unifiedDiff(orig, editLines(), 3);
        diffScrollY = EditDiff.render(frame, inner, entries, diffScrollY);
    }

    private void renderValidationPopup(Frame frame, Rect area) {
        int popupW = Math.min(80, area.width() - 4);
        int innerW = popupW - 2;

        List<Line> allLines = new ArrayList<>();
        for (int i = 0; i < validationErrors.size(); i++) {
            if (i > 0) {
                allLines.add(Line.from(Span.raw("")));
            }
            String msg = validationErrors.get(i);
            wrapText(msg, innerW, allLines);
        }
        allLines.add(Line.empty());
        int fixRow = popupFixRow();
        if (fixRow >= 0) {
            // the fix the editor knows, offered here too: the panel at the bottom says it only after the popup
            QuickFixes.Fix fix = QuickFixes.fixFor(visibleInlineErrors().get(fixRow), editState.getLine(fixRow));
            allLines.add(TuiHelper.hintLine("Shift+F9", "fix line " + (fixRow + 1) + ": " + fix.label(),
                    "Esc", "close"));
        } else {
            allLines.add(TuiHelper.hintLine("Esc", "close"));
        }

        int contentH = allLines.size();
        int popupH = Math.min(contentH + 2, area.height() - 4);
        int x = area.left() + Math.max(0, (area.width() - popupW) / 2);
        int y = area.top() + 2;
        Rect popup = new Rect(x, y, popupW, popupH);

        frame.renderWidget(Clear.INSTANCE, popup);

        String titleText = " " + validationErrors.size() + " Validation Error"
                           + (validationErrors.size() > 1 ? "s" : "") + " ";
        Block block = Block.builder()
                .borderType(BorderType.ROUNDED).borders(Borders.ALL)
                .borderStyle(Theme.error())
                .title(Title.from(Line.from(Span.styled(titleText, Theme.error().bold()))))
                .build();
        frame.renderWidget(block, popup);
        Rect inner = block.inner(popup);

        int visibleLines = inner.height();
        int clampedScroll = Math.min(validationErrorScroll, Math.max(0, contentH - visibleLines));
        validationErrorScroll = clampedScroll;
        int end = Math.min(clampedScroll + visibleLines, contentH);

        if (clampedScroll < end) {
            List<Line> visible = allLines.subList(clampedScroll, end);
            frame.renderWidget(
                    Paragraph.builder().text(Text.from(visible.toArray(Line[]::new))).build(),
                    inner);
        }
    }

    private void renderDiscardPopup(Frame frame, Rect area) {
        DialogHelper.renderConfirm(frame, area, "Discard Changes?", "Unsaved changes will be lost.", false);
    }

    private static void wrapText(String text, int width, List<Line> out) {
        if (width <= 0) {
            width = 40;
        }
        int pos = 0;
        while (pos < text.length()) {
            int end = Math.min(pos + width, text.length());
            out.add(Line.from(Span.styled(text.substring(pos, end), Theme.error())));
            pos = end;
        }
        if (text.isEmpty()) {
            out.add(Line.from(Span.styled(text, Theme.error())));
        }
    }

    void renderFooter(List<Span> spans) {
        if (pendingDiscard) {
            TuiHelper.hint(spans, "Enter", "confirm");
            TuiHelper.hintLast(spans, "Esc", "cancel");
            return;
        }
        if (editMode && validationErrors != null) {
            TuiHelper.hintLast(spans, "Esc", "close");
            return;
        }
        if (editMode && diffOverlay) {
            TuiHelper.hint(spans, "Esc/F7", "close diff");
            return;
        }
        if (editMode) {
            if (refactor.renderFooter(spans)) {
                return;
            }
            TuiHelper.hint(spans, "Esc", "cancel");
            TuiHelper.hint(spans, "Ctrl+S", "save");
            TuiHelper.hint(spans, "F5", "save & close");
            if (dirty) {
                TuiHelper.hint(spans, "F7", "diff");
            }
            TuiHelper.hint(spans, "Ctrl+Z", "undo");
            TuiHelper.hint(spans, "Ctrl+Y", "redo");
            TuiHelper.hint(spans, "Alt+↑/↓", "move block");
            TuiHelper.hint(spans, "Ctrl+D", "duplicate");
            TuiHelper.hint(spans, "Ctrl+K", "delete line");
            if (lineCommentMarker() != null) {
                TuiHelper.hint(spans, "Ctrl+/", "comment");
            }
            if (completion.isEnabled()) {
                TuiHelper.hint(spans, "Tab", "complete");
            }
            TuiHelper.hint(spans, "Shift+Tab", "dedent");
            if (!inlineErrors.isEmpty()) {
                TuiHelper.hint(spans, "F9", "next error");
            }
            if (cursorFix() != null) {
                TuiHelper.hint(spans, "Shift+F9", "fix");
            }
            if (canAskAiToFix(editState.cursorRow(), visibleInlineErrors())) {
                TuiHelper.hint(spans, "Shift+F8", "fix with AI");
            }
            if (isCamelYamlFile()) {
                TuiHelper.hint(spans, "Ctrl+R", "refactor");
            }
            return;
        }
        if (markdownMode) {
            TuiHelper.hint(spans, "Esc/c", "close");
            TuiHelper.hint(spans, "Space", "format");
            TuiHelper.hint(spans, "PgUp/PgDn", "page");
            if (isEditable()) {
                TuiHelper.hint(spans, "F4", "edit");
            }
            return;
        }
        search.renderFooterHints(spans);
        if (search.isSearchInputActive()) {
            return;
        }
        if (search.hasFindTerm()) {
            search.renderFindStatus(spans);
        } else {
            TuiHelper.hint(spans, "Esc/c", "close");
        }
        if (isEditable()) {
            TuiHelper.hint(spans, "F4", "edit");
        }
        if (!viewErrors.isEmpty()) {
            TuiHelper.hint(spans, "F9", "next problem");
        }
        if (isMarkdownFile || currentRouteId != null) {
            TuiHelper.hint(spans, "Space", "format");
        }
        search.renderSearchHints(spans);
        TuiHelper.hint(spans, "w", "wrap" + (wordWrap ? " [on]" : " [off]"));
        TuiHelper.hint(spans, "p", "plain" + (plainMode ? " [on]" : " [off]"));
        if (onLineSelected != null) {
            TuiHelper.hint(spans, "Enter", "select node");
        }
    }

    /**
     * Load source for a route, scrolling to the given source line number.
     */
    void loadFile(Path filePath) {
        completion.dropSemanticCompletion();
        currentRouteId = null;
        currentFormat = null;
        originalFormat = null;
        currentCtx = null;
        currentPid = null;
        loadedFilePath = filePath.toString();
        editMode = false;
        editState.clear();
        markdownModeBeforeEdit = false;
        String fileName = filePath.getFileName().toString();
        boolean isMd = fileName.toLowerCase().endsWith(".md");
        try {
            List<String> rawLines = Files.readAllLines(filePath, StandardCharsets.UTF_8);
            int lineNumWidth = Math.max(2, String.valueOf(rawLines.size()).length());
            List<String> result = new ArrayList<>();
            List<JsonObject> codeLines = new ArrayList<>();
            for (int i = 0; i < rawLines.size(); i++) {
                int lineNum = i + 1;
                String code = rawLines.get(i);
                result.add(String.format("%" + lineNumWidth + "d |%s", lineNum, code));
                JsonObject jo = new JsonObject();
                jo.put("line", lineNum);
                jo.put("code", code);
                codeLines.add(jo);
            }
            title = fileName;
            language = SyntaxHighlighter.detectLanguage(fileName);
            lines = result;
            codeData = codeLines;
            selectedLine = findLicenseHeaderEnd(codeLines);
            scrollY = 0;
            scrollX = 0;
            pendingScroll = true;
            visible = true;
            isMarkdownFile = isMd;
            if (isMd) {
                rawMarkdownContent = String.join("\n", rawLines);
                markdownMode = true;
                markdownScroll = 0;
                markdownMaxScroll = Integer.MAX_VALUE;
            } else {
                rawMarkdownContent = null;
                markdownMode = false;
            }
            editableFile = Files.isWritable(filePath) ? filePath : null;
            scanDeprecatedLines();
            viewErrors = problemsOnLoad(String.join("\n", rawLines));
            jumpLinks = Collections.emptyMap();
            if (onFileLoaded != null) {
                onFileLoaded.accept(filePath);
            }
        } catch (IOException e) {
            title = fileName;
            lines = List.of("(Failed to read file: " + e.getMessage() + ")");
            codeData = Collections.emptyList();
            visible = true;
            isMarkdownFile = false;
            markdownMode = false;
            rawMarkdownContent = null;
            editableFile = null;
            viewErrors = Collections.emptyMap();
        }
    }

    /**
     * Load source for a route, scrolling to the given source line number.
     */
    void loadSource(MonitorContext ctx, String routeId, int targetLine) {
        loadSource(ctx, routeId, targetLine, null);
    }

    void loadSource(MonitorContext ctx, String routeId, int targetLine, String sourceLocationHint) {
        // Process-sourced views are never editable (may be remote / not a local file)
        editableFile = null;
        viewErrors = Collections.emptyMap();
        editMode = false;
        editState.clear();

        if (ctx.selectedPid == null || ctx.runner == null) {
            return;
        }

        String pid = ctx.selectedPid;
        currentRouteId = routeId;
        currentCtx = ctx;
        currentPid = pid;
        String cacheKey = pid + ":" + routeId;
        CachedSource cached = sourceCache.get(cacheKey);
        if (cached == null && sourceLocationHint != null) {
            String locKey = pid + ":loc:" + sourceLocationHint;
            cached = sourceCache.get(locKey);
            if (cached != null) {
                sourceCache.put(cacheKey, cached);
            }
        }
        if (cached != null) {
            applyCached(ctx, routeId, cached, targetLine);
            return;
        }

        if (!loading.compareAndSet(false, true)) {
            return;
        }

        lines = List.of("(Loading source...)");
        title = routeId;
        scrollY = 0;
        scrollX = 0;
        visible = true;

        ctx.backgroundExecutor.execute(() -> {
            try {
                loadInBackground(ctx, pid, routeId, targetLine);
            } finally {
                loading.set(false);
            }
        });
    }

    private void applyCached(MonitorContext ctx, String routeId, CachedSource cached, int targetLine) {
        int matchIdx = -1;
        for (int i = 0; i < cached.codeData.size(); i++) {
            Integer lineNum = cached.codeData.get(i).getInteger("line");
            if (targetLine > 0 && lineNum != null && lineNum == targetLine) {
                matchIdx = i;
                break;
            }
            Boolean match = cached.codeData.get(i).getBoolean("match");
            if (targetLine <= 0 && Boolean.TRUE.equals(match) && matchIdx < 0) {
                matchIdx = i;
            }
        }

        int cursorLine;
        if (matchIdx >= 0) {
            cursorLine = matchIdx;
        } else {
            cursorLine = findLicenseHeaderEnd(cached.codeData);
        }

        String displayLoc = cached.sourceLocation != null
                ? FileUtil.stripPath(LoggerHelper.sourceNameOnly(cached.sourceLocation)) : null;
        title = displayLoc != null ? routeId + "  " + displayLoc : routeId;
        language = cached.language;
        lines = cached.lines;
        codeData = cached.codeData;
        selectedLine = Math.max(0, cursorLine);
        scrollY = 0;
        scrollX = 0;
        pendingScroll = true;
        visible = true;
        String fmt = languageToFormat(cached.language);
        if (fmt != null) {
            originalFormat = fmt;
            currentFormat = fmt;
        }
    }

    private void loadInBackground(MonitorContext ctx, String pid, String routeId, int targetLine) {
        JsonObject root = new JsonObject();
        root.put("action", "source");
        root.put("filter", routeId);

        JsonObject jo = ctx.executeAction(pid, root, 5000);

        if (jo == null) {
            applyResult(ctx, routeId, null, List.of("(No response from integration)"), Collections.emptyList(), 0, -1);
            return;
        }

        JsonArray routes = (JsonArray) jo.get("routes");
        if (routes == null || routes.isEmpty()) {
            applyResult(ctx, routeId, null, List.of("(No source available for route: " + routeId + ")"),
                    Collections.emptyList(), 0, -1);
            return;
        }

        JsonObject routeObj = (JsonObject) routes.get(0);
        String sourceLocation = objToString(routeObj.get("source"));
        List<JsonObject> codeLines = routeObj.getCollection("code");
        if (codeLines == null || codeLines.isEmpty()) {
            applyResult(ctx, routeId, sourceLocation, List.of("(No source code available)"),
                    Collections.emptyList(), 0, -1);
            return;
        }

        List<String> result = new ArrayList<>();
        int maxLineNum = 0;
        for (JsonObject codeLine : codeLines) {
            Integer lineNum = codeLine.getInteger("line");
            if (lineNum != null && lineNum > maxLineNum) {
                maxLineNum = lineNum;
            }
        }
        int lineNumWidth = Math.max(2, String.valueOf(maxLineNum).length());
        int matchIdx = -1;
        int idx = 0;
        for (JsonObject codeLine : codeLines) {
            Integer lineNum = codeLine.getInteger("line");
            String code = Jsoner.unescape(objToString(codeLine.get("code")));
            String prefix = lineNum != null
                    ? String.format("%" + lineNumWidth + "d |", lineNum)
                    : String.format("%" + lineNumWidth + "s |", "");
            result.add(prefix + code);
            if (targetLine > 0 && lineNum != null && lineNum == targetLine && matchIdx < 0) {
                matchIdx = idx;
            }
            Boolean match = codeLine.getBoolean("match");
            if (targetLine <= 0 && Boolean.TRUE.equals(match) && matchIdx < 0) {
                matchIdx = idx;
            }
            idx++;
        }

        int scrollTo;
        int cursorLine;
        if (matchIdx >= 0) {
            cursorLine = matchIdx;
            scrollTo = matchIdx;
        } else {
            cursorLine = findLicenseHeaderEnd(codeLines);
            scrollTo = cursorLine;
        }

        SyntaxHighlighter.Language lang = SyntaxHighlighter.detectLanguage(sourceLocation);
        CachedSource cached = new CachedSource(result, codeLines, sourceLocation, lang);
        String cacheKey = pid + ":" + routeId;
        sourceCache.put(cacheKey, cached);
        if (sourceLocation != null) {
            sourceCache.put(pid + ":loc:" + sourceLocation, cached);
        }
        String fmt = languageToFormat(lang);
        if (fmt != null) {
            sourceCache.put(pid + ":" + routeId + ":" + fmt, cached);
        }

        applyResult(ctx, routeId, sourceLocation, result, codeLines, scrollTo, cursorLine);
    }

    private void applyResult(
            MonitorContext ctx, String routeId, String location,
            List<String> resultLines, List<JsonObject> codeLines, int scrollTo, int cursorLine) {
        if (ctx.runner == null) {
            return;
        }
        ctx.runner.runOnRenderThread(() -> {
            if (!visible) {
                return;
            }
            String displayLoc = location != null ? FileUtil.stripPath(LoggerHelper.sourceNameOnly(location)) : null;
            title = displayLoc != null ? routeId + "  " + displayLoc : routeId;
            language = SyntaxHighlighter.detectLanguage(location);
            lines = resultLines;
            codeData = codeLines;
            selectedLine = Math.max(0, cursorLine);
            scrollY = 0;
            pendingScroll = true;
            if (currentRouteId != null) {
                String fmt = languageToFormat(language);
                if (fmt != null) {
                    originalFormat = fmt;
                    currentFormat = fmt;
                }
            }
        });
    }

    private Title buildTitle() {
        String info = title != null ? title : "";
        Style ts = titleStyle != null ? titleStyle : Style.EMPTY;
        if (isMarkdownFile) {
            List<Span> spans = new ArrayList<>();
            spans.add(Span.styled(" Source [" + info + "]  ", ts));
            String mdLabel = "Markdown*";
            if (markdownMode) {
                spans.add(Span.styled(mdLabel, Style.EMPTY.bold()));
            } else {
                spans.add(Span.styled(mdLabel, Style.EMPTY.dim()));
            }
            spans.add(Span.styled(" │ ", Style.EMPTY.dim()));
            if (!markdownMode) {
                spans.add(Span.styled("Raw", Style.EMPTY.bold()));
            } else {
                spans.add(Span.styled("Raw", Style.EMPTY.dim()));
            }
            spans.add(Span.raw(" "));
            return Title.from(Line.from(spans));
        }
        if (currentRouteId == null) {
            if (!viewErrors.isEmpty()) {
                List<Span> spans = new ArrayList<>();
                spans.add(Span.styled(" Source [" + info + "] ", ts));
                spans.addAll(problemCounts(viewErrors, Theme.error()));
                return Title.from(Line.from(spans));
            }
            return Title.from(Span.styled(" Source [" + info + "] ", ts));
        }

        List<Span> spans = new ArrayList<>();
        spans.add(Span.styled(" Source [" + info + "]  ", ts));

        String[] formats = { "yaml", "java", "xml" };
        String[] labels = { "YAML", "Java", "XML" };

        for (int i = 0; i < formats.length; i++) {
            if (i > 0) {
                spans.add(Span.styled(" │ ", Style.EMPTY.dim()));
            }
            String label = labels[i];
            if (formats[i].equals(originalFormat)) {
                label += "*";
            }
            if (formats[i].equals(currentFormat)) {
                spans.add(Span.styled(label, Style.EMPTY.bold()));
            } else {
                spans.add(Span.styled(label, Style.EMPTY.dim()));
            }
        }
        spans.add(Span.raw(" "));

        return Title.from(Line.from(spans));
    }

    private void switchFormat(String format) {
        if (format.equals(currentFormat)) {
            return;
        }
        if (currentPid == null || currentRouteId == null || currentCtx == null) {
            return;
        }

        String cacheKey = currentPid + ":" + currentRouteId + ":" + format;
        CachedSource cached = sourceCache.get(cacheKey);
        if (cached != null) {
            lines = cached.lines;
            codeData = cached.codeData;
            language = cached.language;
            currentFormat = format;
            selectedLine = findLicenseHeaderEnd(codeData);
            scrollY = 0;
            scrollX = 0;
            pendingScroll = true;
            search.reset();
            return;
        }

        if (!loading.compareAndSet(false, true)) {
            return;
        }

        lines = List.of("(Loading " + format + " format...)");
        currentFormat = format;
        scrollY = 0;
        scrollX = 0;

        MonitorContext ctx = currentCtx;
        String pid = currentPid;
        String routeId = currentRouteId;
        ctx.backgroundExecutor.execute(() -> {
            try {
                if (format.equals(originalFormat)) {
                    loadInBackground(ctx, pid, routeId, 0);
                } else {
                    loadFormatInBackground(ctx, pid, routeId, format);
                }
            } finally {
                loading.set(false);
            }
        });
    }

    private void loadFormatInBackground(MonitorContext ctx, String pid, String routeId, String format) {
        JsonObject root = new JsonObject();
        root.put("action", "route-dump");
        root.put("filter", routeId);
        root.put("format", format);
        root.put("uriAsParameters", "yaml".equals(format) ? "true" : "false");

        JsonObject jo = ctx.executeAction(pid, root, 5000);

        if (jo == null) {
            applyFormatResult(ctx, format, List.of("(No response from integration)"), Collections.emptyList());
            return;
        }

        JsonArray routes = (JsonArray) jo.get("routes");
        if (routes == null || routes.isEmpty()) {
            applyFormatResult(ctx, format,
                    List.of("(No dump available for route: " + routeId + ")"), Collections.emptyList());
            return;
        }

        JsonObject routeObj = (JsonObject) routes.get(0);
        List<JsonObject> codeLines = routeObj.getCollection("code");
        if (codeLines == null || codeLines.isEmpty()) {
            applyFormatResult(ctx, format, List.of("(No code available)"), Collections.emptyList());
            return;
        }

        List<String> result = new ArrayList<>();
        int lineNumWidth = String.valueOf(codeLines.size()).length();
        for (int i = 0; i < codeLines.size(); i++) {
            String code = Jsoner.unescape(objToString(codeLines.get(i).get("code")));
            result.add(String.format("%" + lineNumWidth + "d  %s", i + 1, code));
        }

        SyntaxHighlighter.Language lang = formatToLanguage(format);
        CachedSource cached = new CachedSource(result, codeLines, null, lang);
        sourceCache.put(pid + ":" + routeId + ":" + format, cached);

        applyFormatResult(ctx, format, result, codeLines);
    }

    private void applyFormatResult(
            MonitorContext ctx, String format,
            List<String> resultLines, List<JsonObject> codeLines) {
        if (ctx.runner == null) {
            return;
        }
        ctx.runner.runOnRenderThread(() -> {
            if (!visible) {
                return;
            }
            language = formatToLanguage(format);
            lines = resultLines;
            codeData = codeLines;
            currentFormat = format;
            selectedLine = findLicenseHeaderEnd(codeLines);
            scrollY = 0;
            scrollX = 0;
            pendingScroll = true;
            search.reset();
        });
    }

    private static String languageToFormat(SyntaxHighlighter.Language lang) {
        return switch (lang) {
            case YAML -> "yaml";
            case JAVA -> "java";
            case XML -> "xml";
            default -> null;
        };
    }

    private static SyntaxHighlighter.Language formatToLanguage(String format) {
        return switch (format) {
            case "yaml" -> SyntaxHighlighter.Language.YAML;
            case "java" -> SyntaxHighlighter.Language.JAVA;
            case "xml" -> SyntaxHighlighter.Language.XML;
            default -> SyntaxHighlighter.Language.PLAIN;
        };
    }

    private Line highlightSourceLine(String raw, int lineIndex, int hSkip, boolean isSelected, int viewportWidth) {
        int prefixEnd = 0;
        while (prefixEnd < raw.length() && (raw.charAt(prefixEnd) == ' ' || Character.isDigit(raw.charAt(prefixEnd)))) {
            prefixEnd++;
        }
        // include the | separator after the line number
        if (prefixEnd < raw.length() && raw.charAt(prefixEnd) == '|') {
            prefixEnd++;
        }

        String prefix = raw.substring(0, prefixEnd);
        String code = raw.substring(prefixEnd);

        Line highlighted = SyntaxHighlighter.highlightLine(code, language);
        boolean isDeprecated = deprecatedLines.contains(lineIndex);

        List<Span> spans = new ArrayList<>();
        Style selBg = focused ? Theme.selectionBg() : Theme.selectionBg().dim();
        if (plainMode) {
            // strip line-number prefix (spaces, digits, space, pipe separator) but keep code indentation
            int pos = 0;
            while (pos < raw.length() && raw.charAt(pos) == ' ') {
                pos++;
            }
            while (pos < raw.length() && Character.isDigit(raw.charAt(pos))) {
                pos++;
            }
            if (pos < raw.length() && raw.charAt(pos) == ' ') {
                pos++;
            }
            if (pos < raw.length() && raw.charAt(pos) == '|') {
                pos++;
            }
            String plainCode = raw.substring(pos);
            spans.addAll(SyntaxHighlighter.highlightLine(plainCode, language).spans());
        } else if (isSelected) {
            spans.add(Span.styled(">> ", focused ? Theme.label().bold() : Theme.label().dim()));
            if (!prefix.isEmpty()) {
                Style numberStyle = viewErrors.containsKey(lineIndex)
                        ? (noteLines.contains(lineIndex) ? Theme.warning().bold() : Theme.error().bold())
                        : focused ? Theme.label().bold() : Theme.label().dim();
                spans.add(Span.styled(prefix, numberStyle.patch(selBg)));
            }
            liveColumn.addLiveColumn(spans, lineIndex, selBg);
            for (Span s : highlighted.spans()) {
                spans.add(Span.styled(s.content(), s.style().patch(selBg)));
            }
        } else {
            if (viewErrors.containsKey(lineIndex)) {
                spans.add(noteLines.contains(lineIndex)
                        ? Span.styled(" ⚠ ", Theme.warning().bold())
                        : Span.styled(" ✗ ", Theme.error().bold()));
            } else {
                spans.add(isDeprecated
                        ? Span.styled(" ⚠ ", Theme.warning())
                        : Span.raw("   "));
            }
            if (!prefix.isEmpty()) {
                spans.add(Span.styled(prefix, Style.EMPTY.dim()));
            }
            liveColumn.addLiveColumn(spans, lineIndex, null);
            spans.addAll(highlighted.spans());
        }

        JumpLink jl = plainMode ? null : jumpLinks.get(lineIndex);
        if (jl != null) {
            Style linkStyle = Theme.label().bold();
            if (isSelected) {
                linkStyle = linkStyle.patch(selBg);
            }
            spans.add(Span.styled(" ↵ " + jl.routeId(), linkStyle));
        }
        Line full = Line.from(spans);

        if (hSkip > 0) {
            List<Span> scrolled = new ArrayList<>();
            int skipped = 0;
            for (Span span : full.spans()) {
                String content = span.content();
                if (skipped >= hSkip) {
                    scrolled.add(span);
                } else if (skipped + content.length() > hSkip) {
                    int offset = hSkip - skipped;
                    scrolled.add(Span.styled(content.substring(offset), span.style()));
                    skipped = hSkip;
                } else {
                    skipped += content.length();
                }
            }
            full = scrolled.isEmpty() ? Line.from(List.of(Span.raw(""))) : Line.from(scrolled);
        }

        if (isSelected && viewportWidth > 0) {
            int contentWidth = full.width();
            if (contentWidth < viewportWidth) {
                List<Span> padded = new ArrayList<>(full.spans());
                padded.add(Span.styled(" ".repeat(viewportWidth - contentWidth), selBg));
                full = Line.from(padded);
            }
        }

        return full;
    }

    static int findLicenseHeaderEnd(List<JsonObject> codeLines) {
        boolean inBlock = false;
        int lastCommentLine = -1;
        for (int i = 0; i < codeLines.size(); i++) {
            String code = objToString(codeLines.get(i).get("code")).trim();
            if (i == 0 && code.isEmpty()) {
                continue;
            }
            if (!inBlock && code.startsWith("/*")) {
                inBlock = true;
            }
            if (inBlock) {
                lastCommentLine = i;
                if (code.contains("*/")) {
                    inBlock = false;
                }
                continue;
            }
            if (code.startsWith("#") || code.startsWith("##") || code.startsWith("<!--")) {
                lastCommentLine = i;
                continue;
            }
            if (lastCommentLine >= 0 && code.isEmpty()) {
                lastCommentLine = i;
                continue;
            }
            break;
        }
        return lastCommentLine >= 0 ? lastCommentLine + 1 : 0;
    }

    /**
     * The first line from which the lines down to {@code last} fit the rows when word wrapped, or {@code last} when it
     * is taller than the rows on its own.
     */
    private int firstLineThatFits(int last, int rows, int contentWidth) {
        if (last < 0 || last >= lines.size()) {
            return 0;
        }
        int first = last;
        int used = wrapRowCount(last, contentWidth);
        while (first > 0) {
            int r = wrapRowCount(first - 1, contentWidth);
            if (used + r > rows) {
                break;
            }
            used += r;
            first--;
        }
        return first;
    }

    /** The rows a line takes when word wrapped: as it is drawn, with its marker, line number and live column. */
    private int wrapRowCount(int lineIndex, int contentWidth) {
        Line shown = highlightSourceLine(lines.get(lineIndex), lineIndex, 0, lineIndex == selectedLine, contentWidth + 1);
        return TuiHelper.wrappedRows(shown, contentWidth);
    }

    private void scanDeprecatedLines() {
        if (deprecatedLineScanner != null && !codeData.isEmpty()) {
            Set<Integer> result = deprecatedLineScanner.scan(codeData);
            deprecatedLines = result != null ? result : Collections.emptySet();
        } else {
            deprecatedLines = Collections.emptySet();
        }
    }

    private static String objToString(Object o) {
        return o != null ? o.toString() : "";
    }
}
