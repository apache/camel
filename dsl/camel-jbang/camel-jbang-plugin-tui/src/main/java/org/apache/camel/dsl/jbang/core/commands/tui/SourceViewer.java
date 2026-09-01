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
import java.util.List;
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
import org.apache.camel.support.LoggerHelper;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

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
    interface QuickDocProvider {
        Map<Integer, List<DocEntry>> provideAll(List<JsonObject> codeData);
    }

    @FunctionalInterface
    interface EditQuickDocProvider {
        List<DocEntry> provideForLine(List<String> lines, int cursorRow);
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

    record JumpLink(String routeId, String filePath, int targetLine) {
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
    private QuickDocProvider quickDocProvider;
    private boolean quickDocEnabled;
    private Map<Integer, List<DocEntry>> quickDocEntries = Collections.emptyMap();
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
    /** Markdown render mode prior to entering edit; restored on cancel. */
    private boolean markdownModeBeforeEdit;
    private boolean dirty;
    private boolean pendingDiscard;
    private String originalEditText;
    private EditDiff.LineStatus[] lineStatuses;
    private boolean diffOverlay;
    private int diffScrollY;
    private BiConsumer<String, Boolean> notificationCallback;
    private AutocompletePopup.AutocompleteProvider autocompleteProvider;
    private AutocompletePopup.ValueProvider autocompleteValueProvider;
    private java.util.function.Predicate<String> listItemNodeChecker;
    private AutocompletePopup autocompletePopup;
    private boolean validateOnSave = true;
    private org.apache.camel.dsl.yaml.validator.YamlValidator yamlValidator;
    private PropertiesValidator propertiesValidator;
    private EndpointValidator endpointValidator;
    private EndpointValidator simpleValidator;
    private List<String> validationErrors;
    private int validationErrorScroll;
    private Map<Integer, String> inlineErrors = Collections.emptyMap();
    private boolean editInitialScroll;
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

    void setAutocompleteProvider(AutocompletePopup.AutocompleteProvider provider) {
        this.autocompleteProvider = provider;
    }

    void setAutocompleteValueProvider(AutocompletePopup.ValueProvider provider) {
        this.autocompleteValueProvider = provider;
    }

    void setListItemNodeChecker(java.util.function.Predicate<String> checker) {
        this.listItemNodeChecker = checker;
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

    void hide() {
        exitEditMode();
        visible = false;
        onLineSelected = null;
        quickDocEnabled = false;
        quickDocEntries = Collections.emptyMap();
        deprecatedLines = Collections.emptySet();
        editableFile = null;
        propertiesValidator = null;
        endpointValidator = null;
        simpleValidator = null;
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
        quickDocProvider = null;
        quickDocEnabled = false;
        quickDocEntries = Collections.emptyMap();
        deprecatedLineScanner = null;
        deprecatedLines = Collections.emptySet();
        jumpLinks = Collections.emptyMap();
        loadedFilePath = null;
        autocompleteProvider = null;
        autocompleteValueProvider = null;
        autocompletePopup = null;
        editableFile = null;
        propertiesValidator = null;
        endpointValidator = null;
        simpleValidator = null;
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

    /** Package-private for tests that drive the edit buffer directly. */
    TextAreaState editState() {
        return editState;
    }

    boolean isEditable() {
        return editableFile != null;
    }

    boolean isPlainMode() {
        return plainMode;
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
        if (autocompletePopup != null) {
            autocompletePopup = null;
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

    void toggleQuickDoc() {
        if (quickDocProvider != null) {
            quickDocEnabled = !quickDocEnabled;
            if (quickDocEnabled) {
                quickDocEntries = quickDocProvider.provideAll(codeData);
                if (quickDocEntries == null) {
                    quickDocEntries = Collections.emptyMap();
                }
            } else {
                quickDocEntries = Collections.emptyMap();
            }
        }
    }

    private void refreshQuickDoc() {
        if (quickDocEnabled && quickDocProvider != null && !codeData.isEmpty()) {
            quickDocEntries = quickDocProvider.provideAll(codeData);
            if (quickDocEntries == null) {
                quickDocEntries = Collections.emptyMap();
            }
        }
    }

    void setQuickDocProvider(QuickDocProvider provider) {
        this.quickDocProvider = provider;
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
        if (isMarkdownFile && ke.isChar(' ')) {
            markdownMode = !markdownMode;
            return true;
        }
        if (markdownMode) {
            if (ke.isUp() || ke.isChar('k')) {
                markdownScroll = Math.max(0, markdownScroll - 1);
            } else if (ke.isDown() || ke.isChar('j')) {
                markdownScroll++;
            } else if (ke.isPageUp() || ke.isKey(KeyCode.PAGE_UP)) {
                markdownScroll = Math.max(0, markdownScroll - 10);
            } else if (ke.isPageDown() || ke.isKey(KeyCode.PAGE_DOWN)) {
                markdownScroll += 10;
            } else if (ke.isHome() || ke.isKey(KeyCode.HOME)) {
                markdownScroll = 0;
            } else if (ke.isEnd() || ke.isKey(KeyCode.END)) {
                markdownScroll = Integer.MAX_VALUE;
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
            quickDocEntries = Collections.emptyMap();
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
        List<String> answer = new ArrayList<>(editState.lineCount());
        for (int i = 0; i < editState.lineCount(); i++) {
            answer.add(editState.getLine(i));
        }
        return answer;
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

    private boolean handleEditKeyEvent(KeyEvent ke) {
        if (validationErrors != null) {
            if (ke.isCancel() || ke.isKey(KeyCode.ENTER)) {
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
        if (autocompletePopup != null) {
            boolean wasValueMode = autocompletePopup.isValueMode();
            boolean wasListItem = autocompletePopup.isListItemInsertion();
            AutocompletePopup.Result result = autocompletePopup.handleKeyEvent(ke);
            if (result == AutocompletePopup.Result.CLOSED) {
                AutocompletePopup.CompletionItem item = autocompletePopup.consumeSelectedItem();
                autocompletePopup = null;
                if (item != null) {
                    insertCompletion(item, wasValueMode, wasListItem);
                }
            } else if (result == AutocompletePopup.Result.CURSOR_RIGHT) {
                editState.moveCursorRight();
            } else if (result == AutocompletePopup.Result.CURSOR_LEFT) {
                editState.moveCursorLeft();
            }
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
                dirty = true;
                lineStatuses = null;
                refreshEditFindMatches();
            }
            return true;
        }
        if (ke.hasCtrl() && (ke.isCharIgnoreCase('y') || (ke.isCharIgnoreCase('z') && ke.hasShift()))) {
            if (editHistory.redo(editState)) {
                dirty = true;
                lineStatuses = null;
                refreshEditFindMatches();
            }
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
                if (listItemNodeChecker != null && key != null
                        && listItemNodeChecker.test(dashToCamelCase(key))) {
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
            moveCursorToPreviousIndentStop();
            return true;
        }
        if (ke.isKey(KeyCode.TAB) && autocompleteProvider != null) {
            openAutocomplete();
            return true;
        }
        if (ke.code() == KeyCode.CHAR && !ke.hasCtrl() && !ke.hasAlt()) {
            recordEditChange();
            editState.insert(ke.character());
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
        markdownModeBeforeEdit = markdownMode;
        markdownMode = false;
        quickDocEnabled = false;
        search.closeInputOnly();
        dirty = false;
        validationErrors = null;
        originalEditText = editState.text();
        lineStatuses = null;
        diffOverlay = false;
        editMode = true;
        editHistory.seedInitial(editState);
        refreshEditFindMatches();
    }

    private void exitEditMode() {
        boolean wasEditing = editMode;
        editMode = false;
        editState.clear();
        editHistory.clear();
        autocompletePopup = null;
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
    }

    private boolean isPropertiesFile() {
        return editableFile != null
                && editableFile.getFileName().toString().toLowerCase().endsWith(".properties");
    }

    private boolean isCamelYamlFile() {
        if (editableFile == null) {
            return false;
        }
        String name = editableFile.getFileName().toString().toLowerCase();
        return name.endsWith(".yaml") || name.endsWith(".yml");
    }

    record YamlEndpointContext(String component, boolean consumer, String uri, boolean needsParameters) {
        YamlEndpointContext(String component, boolean consumer, String uri) {
            this(component, consumer, uri, false);
        }
    }

    static final java.util.Set<String> CONSUMER_EIPS
            = java.util.Set.of("from", "pollEnrich", "poll-enrich", "poll", "interceptFrom", "intercept-from");
    static final java.util.Set<String> PRODUCER_EIPS
            = java.util.Set.of("to", "toD", "to-d", "wireTap", "wire-tap", "enrich",
                    "interceptSendToEndpoint", "intercept-send-to-endpoint");

    YamlEndpointContext findEnclosingComponent(int fromRow) {
        String cursorLine = editState.getLine(fromRow);
        // a blank line's own leading whitespace can be stale after a Shift+Tab dedent (see
        // effectiveBlankIndent) — use the cursor's real column so a cursor dedented back out of
        // an endpoint's parameters: block isn't mistaken for still being inside it
        int cursorIndent = cursorLine.isBlank() ? effectiveBlankIndent(fromRow) : countLeadingSpaces(cursorLine);

        // list items (- key:) are inside steps, not inside parameters
        if (!cursorLine.isBlank() && cursorLine.trim().startsWith("- ")) {
            return null;
        }

        // blank line positioned as a sibling of a uri: line with no parameters: block yet —
        // offer to create one. Scan siblings at exactly cursorIndent; a shallower line ends it.
        if (cursorLine.isBlank()) {
            for (int i = fromRow - 1; i >= 0; i--) {
                String line = editState.getLine(i);
                if (line.isBlank()) {
                    continue;
                }
                int indent = countLeadingSpaces(line);
                if (indent < cursorIndent) {
                    break;
                }
                if (indent == cursorIndent) {
                    String trimmed = line.trim();
                    if (trimmed.startsWith("parameters:")) {
                        // a parameters: block already exists as a sibling here — nothing to
                        // auto-create, and the cursor isn't inside it either (it's a sibling,
                        // not a child); fall through to generic EIP-field completion instead
                        return null;
                    }
                    if (trimmed.startsWith("uri:") || trimmed.startsWith("- uri:")) {
                        return findComponentFromUriSibling(i);
                    }
                }
            }
        }

        int parametersRow = -1;
        int parametersIndent = -1;

        for (int i = fromRow; i >= 0; i--) {
            String line = editState.getLine(i);
            if (line.isBlank()) {
                continue;
            }
            int indent = countLeadingSpaces(line);
            String trimmed = line.trim();

            if (trimmed.startsWith("parameters:") && indent < cursorIndent) {
                parametersRow = i;
                parametersIndent = indent;
                break;
            }
            if (i < fromRow && indent < cursorIndent && !trimmed.startsWith("#")) {
                // stop if we hit a structural boundary (steps:, from:, etc.)
                break;
            }
            // also stop if we hit a list item at a shallower or equal indent — we've left the parameters scope
            if (i < fromRow && indent <= cursorIndent) {
                String key = extractEipName(trimmed);
                if (key != null && ("steps".equals(key) || "from".equals(key)
                        || trimmed.startsWith("- "))) {
                    break;
                }
            }
        }

        if (parametersRow < 0) {
            return null;
        }

        String foundScheme = null;
        String foundUri = null;
        for (int i = parametersRow - 1; i >= 0; i--) {
            String line = editState.getLine(i);
            if (line.isBlank()) {
                continue;
            }
            int indent = countLeadingSpaces(line);
            String trimmed = line.trim();

            if (indent == parametersIndent) {
                if (foundScheme == null && (trimmed.startsWith("uri:") || trimmed.startsWith("- uri:"))) {
                    foundScheme = extractSchemeFromUriLine(trimmed);
                    foundUri = extractUriValue(trimmed);
                }
            }

            if (indent < parametersIndent) {
                String eipName = extractEipName(trimmed);
                if (foundScheme == null) {
                    foundScheme = extractInlineUri(trimmed);
                    foundUri = foundScheme;
                }
                if (foundScheme != null) {
                    boolean consumer = eipName != null && CONSUMER_EIPS.contains(eipName);
                    return new YamlEndpointContext(foundScheme, consumer, foundUri);
                }
                break;
            }
        }

        if (foundScheme != null) {
            return new YamlEndpointContext(foundScheme, false, foundUri);
        }
        return null;
    }

    /**
     * When cursor is below a uri: line (no parameters: block), find the uri: among siblings and build the endpoint
     * context. Walks up to find the parent EIP to determine consumer vs producer.
     */
    private YamlEndpointContext findComponentFromUriSibling(int uriOrSiblingRow) {
        int indent = countLeadingSpaces(editState.getLine(uriOrSiblingRow));

        // find the uri: line among siblings at the same indent
        String uriValue = null;
        String scheme = null;
        for (int i = uriOrSiblingRow; i >= 0; i--) {
            String line = editState.getLine(i);
            if (line.isBlank()) {
                continue;
            }
            int li = countLeadingSpaces(line);
            if (li < indent) {
                break;
            }
            if (li == indent && line.trim().startsWith("uri:")) {
                scheme = extractSchemeFromUriLine(line.trim());
                uriValue = extractUriValue(line.trim());
                break;
            }
        }
        if (scheme == null) {
            return null;
        }

        // find the parent EIP to determine consumer vs producer
        boolean consumer = false;
        for (int i = uriOrSiblingRow; i >= 0; i--) {
            String line = editState.getLine(i);
            if (line.isBlank()) {
                continue;
            }
            int li = countLeadingSpaces(line);
            if (li < indent) {
                String eipName = extractEipName(line.trim());
                if (eipName != null) {
                    consumer = CONSUMER_EIPS.contains(eipName);
                }
                break;
            }
        }
        return new YamlEndpointContext(scheme, consumer, uriValue, true);
    }

    java.util.Set<String> collectExistingParameters(int fromRow) {
        java.util.Set<String> keys = new java.util.LinkedHashSet<>();
        // find the parameters: row by walking up
        int parametersRow = -1;
        int parametersIndent = -1;
        String cursorLine = editState.getLine(fromRow);
        int cursorIndent = countLeadingSpaces(cursorLine);

        // blank lines: derive indent from nearest preceding non-blank line
        if (cursorLine.isBlank()) {
            for (int i = fromRow - 1; i >= 0; i--) {
                String prev = editState.getLine(i);
                if (!prev.isBlank()) {
                    if (prev.trim().startsWith("parameters:")) {
                        parametersRow = i;
                        parametersIndent = countLeadingSpaces(prev);
                    } else {
                        cursorIndent = countLeadingSpaces(prev);
                    }
                    break;
                }
            }
        }

        if (parametersRow < 0) {
            for (int i = fromRow; i >= 0; i--) {
                String line = editState.getLine(i);
                if (line.isBlank()) {
                    continue;
                }
                String trimmed = line.trim();
                int indent = countLeadingSpaces(line);
                if (trimmed.startsWith("parameters:") && indent < cursorIndent) {
                    parametersRow = i;
                    parametersIndent = indent;
                    break;
                }
                if (i < fromRow && indent < cursorIndent && !trimmed.startsWith("#")) {
                    break;
                }
            }
        }
        if (parametersRow < 0) {
            return keys;
        }
        int childIndent = parametersIndent + 2;
        for (int i = parametersRow + 1; i < editState.lineCount(); i++) {
            if (i == fromRow) {
                continue;
            }
            String line = editState.getLine(i);
            if (line.isBlank()) {
                continue;
            }
            int indent = countLeadingSpaces(line);
            if (indent < childIndent) {
                break;
            }
            if (indent == childIndent) {
                String trimmed = line.trim();
                int colonIdx = trimmed.indexOf(':');
                if (colonIdx > 0) {
                    keys.add(trimmed.substring(0, colonIdx).trim());
                }
            }
        }
        return keys;
    }

    record YamlUriContext(boolean consumer, String prefix) {
    }

    YamlUriContext findUriContext(int row) {
        String lineText = editState.getLine(row);
        String trimmed = lineText.trim();
        if (trimmed.startsWith("- ")) {
            trimmed = trimmed.substring(2).trim();
        }

        // Check if cursor is on a "uri:" line (possibly with partial value)
        if (trimmed.startsWith("uri:")) {
            String value = trimmed.substring(4).trim();
            if (value.startsWith("\"") || value.startsWith("'")) {
                value = value.substring(1);
            }
            if (value.endsWith("\"") || value.endsWith("'")) {
                value = value.substring(0, value.length() - 1);
            }
            // if value already contains a colon, scheme is already typed
            if (value.contains(":")) {
                return null;
            }
            // walk up to find the parent EIP
            int indent = countLeadingSpaces(lineText);
            for (int i = row - 1; i >= 0; i--) {
                String prev = editState.getLine(i);
                if (prev.isBlank()) {
                    continue;
                }
                int prevIndent = countLeadingSpaces(prev);
                if (prevIndent < indent) {
                    String eipName = extractEipName(prev.trim());
                    if (eipName != null) {
                        boolean consumer = CONSUMER_EIPS.contains(eipName);
                        return new YamlUriContext(consumer, value);
                    }
                    break;
                }
            }
            return null;
        }

        // Check if cursor is on an inline EIP line: "to: " or "from: kafka" (no colon in value)
        int colonIdx = trimmed.indexOf(':');
        if (colonIdx > 0) {
            String eipName = trimmed.substring(0, colonIdx).trim();
            if (CONSUMER_EIPS.contains(eipName) || PRODUCER_EIPS.contains(eipName)) {
                String value = trimmed.substring(colonIdx + 1).trim();
                if (value.startsWith("\"") || value.startsWith("'")) {
                    value = value.substring(1);
                }
                if (value.endsWith("\"") || value.endsWith("'")) {
                    value = value.substring(0, value.length() - 1);
                }
                if (value.contains(":")) {
                    return null;
                }
                boolean consumer = CONSUMER_EIPS.contains(eipName);
                return new YamlUriContext(consumer, value);
            }
        }
        return null;
    }

    record YamlEipContext(String eipName) {
    }

    int deriveBlankLineIndent(int fromRow) {
        return deriveIndentFromPredecessor(fromRow);
    }

    private int deriveInsertionIndent(int fromRow) {
        // use the scope line (parent EIP) to derive indent for correct nesting
        int scopeRow = findScopeLineRow(fromRow);
        if (scopeRow >= 0) {
            String scopeLine = editState.getLine(scopeRow);
            int scopeIndent = countLeadingSpaces(scopeLine);
            String scopeTrimmed = scopeLine.trim();
            if (scopeTrimmed.startsWith("- ")) {
                return scopeIndent + 4;
            }
            return scopeIndent + 2;
        }
        // on a blank line with whitespace, walk up to find the parent EIP at lower indent
        String cursorLine = editState.getLine(fromRow);
        if (cursorLine.isBlank()) {
            int wsIndent = cursorLine.length();
            if (wsIndent > 0) {
                for (int i = fromRow - 1; i >= 0; i--) {
                    String line = editState.getLine(i);
                    if (line.isBlank()) {
                        continue;
                    }
                    int indent = countLeadingSpaces(line);
                    if (indent < wsIndent) {
                        String t = line.trim();
                        if (t.startsWith("- ")) {
                            t = t.substring(2).trim();
                        }
                        if (t.endsWith(":") && !STRUCTURAL_KEYS.contains(extractEipName(t))) {
                            return indent + (line.trim().startsWith("- ") ? 4 : 2);
                        }
                        wsIndent = indent;
                    }
                }
            }
        }
        return deriveIndentFromPredecessor(fromRow);
    }

    private int deriveIndentFromPredecessor(int fromRow) {
        for (int i = fromRow - 1; i >= 0; i--) {
            String prev = editState.getLine(i);
            if (prev.isBlank()) {
                continue;
            }
            int indent = countLeadingSpaces(prev);
            String pt = prev.trim();
            if (pt.endsWith(":")) {
                return indent + (pt.startsWith("- ") ? 4 : 2);
            }
            return indent;
        }
        return 0;
    }

    /**
     * Shift+Tab: move the cursor left, within the current line's leading whitespace, to the nearest enclosing
     * structural indent level — the same indent a completion inserted at this position would have used one level up.
     * Only acts while the cursor sits inside leading whitespace (nothing typed yet on the line); otherwise it is a
     * no-op.
     */
    void moveCursorToPreviousIndentStop() {
        int row = editState.cursorRow();
        String line = editState.getLine(row);
        int col = Math.min(editState.cursorCol(), line.length());
        if (col <= 0 || !line.substring(0, col).isBlank()) {
            return;
        }

        int target = 0;
        for (int i = row - 1; i >= 0; i--) {
            String prev = editState.getLine(i);
            if (prev.isBlank()) {
                continue;
            }
            int indent = countLeadingSpaces(prev);
            if (indent < col) {
                target = indent;
                break;
            }
        }
        SourceEditorNavigation.positionCursor(editState, row, target);
    }

    /**
     * Effective indent for a possibly-blank line. On the live cursor row, the line's own leading whitespace can be
     * stale — Shift+Tab (see {@link #moveCursorToPreviousIndentStop()}) repositions the cursor within existing
     * whitespace without trimming it — so the cursor's column is the source of truth there. For any other row (e.g.
     * tests resolving an arbitrary row without moving the live cursor there), trust the row's own real whitespace when
     * it has any, and only derive from the preceding line when it is truly empty.
     */
    private int effectiveBlankIndent(int row) {
        if (row == editState.cursorRow()) {
            return editState.cursorCol();
        }
        int literal = countLeadingSpaces(editState.getLine(row));
        return literal > 0 ? literal : deriveBlankLineIndent(row);
    }

    String findParentYamlKey(int fromRow) {
        String cursorLine = editState.getLine(fromRow);

        // a blank line has no real indentation yet — derive the intended nesting level so a
        // cursor nested under e.g. "expression:" resolves to that key, not to the enclosing EIP
        // (which would otherwise re-offer already-set fields like name/expression)
        int cursorIndent = cursorLine.isBlank() ? effectiveBlankIndent(fromRow) : countLeadingSpaces(cursorLine);

        // walk up to find parent key at lower indent
        for (int i = fromRow; i >= 0; i--) {
            String line = editState.getLine(i);
            if (line.isBlank()) {
                continue;
            }
            int indent = countLeadingSpaces(line);
            if (indent < cursorIndent) {
                String key = extractEipName(line.trim());
                if (key != null) {
                    return dashToCamelCase(key);
                }
                break;
            }
        }
        return "root";
    }

    private static final java.util.Set<String> STRUCTURAL_KEYS
            = java.util.Set.of("steps", "uri", "parameters", "from", "expression", "routeConfiguration",
                    "routeTemplate", "templatedRoute", "rest", "beans");

    private static final java.util.Set<String> BREADCRUMB_SKIP_KEYS
            = java.util.Set.of("steps", "uri", "expression",
                    "routeConfiguration", "routeTemplate", "templatedRoute", "rest", "beans");

    YamlEipContext findEnclosingEip(int fromRow) {
        String cursorLine = editState.getLine(fromRow);
        int cursorIndent = cursorLine.isBlank() ? effectiveBlankIndent(fromRow) : countLeadingSpaces(cursorLine);

        // if cursor is inside a parameters: block, defer to component completion
        for (int i = fromRow; i >= 0; i--) {
            String line = editState.getLine(i);
            if (line.isBlank()) {
                continue;
            }
            int indent = countLeadingSpaces(line);
            String trimmed = line.trim();
            if (trimmed.startsWith("parameters:") && indent < cursorIndent) {
                return null;
            }
            if (i < fromRow && indent < cursorIndent) {
                break;
            }
        }

        // walk up to find the parent EIP
        boolean skippedStructural = false;
        for (int i = fromRow; i >= 0; i--) {
            String line = editState.getLine(i);
            if (line.isBlank()) {
                continue;
            }
            int indent = countLeadingSpaces(line);
            if (indent < cursorIndent) {
                String eipName = extractEipName(line.trim());
                if (eipName != null && !STRUCTURAL_KEYS.contains(eipName)) {
                    if (skippedStructural) {
                        return null;
                    }
                    String camelName = dashToCamelCase(eipName);
                    return new YamlEipContext(camelName);
                }
                // keep walking up if we hit a structural key
                skippedStructural = true;
                cursorIndent = indent;
            }
        }
        return null;
    }

    java.util.Set<String> collectExistingSiblingKeys(int fromRow) {
        java.util.Set<String> keys = new java.util.LinkedHashSet<>();
        String cursorLine = editState.getLine(fromRow);
        int cursorIndent = cursorLine.isBlank() ? effectiveBlankIndent(fromRow) : countLeadingSpaces(cursorLine);

        // scan upward for siblings at same indent
        for (int i = fromRow - 1; i >= 0; i--) {
            String line = editState.getLine(i);
            if (line.isBlank()) {
                continue;
            }
            int indent = countLeadingSpaces(line);
            if (indent < cursorIndent) {
                break;
            }
            if (indent == cursorIndent) {
                String trimmed = line.trim();
                int colonIdx = trimmed.indexOf(':');
                if (colonIdx > 0) {
                    keys.add(trimmed.substring(0, colonIdx).trim());
                }
            }
        }
        // scan downward for siblings at same indent
        int lineCount = editState.lineCount();
        for (int i = fromRow + 1; i < lineCount; i++) {
            String line = editState.getLine(i);
            if (line.isBlank()) {
                continue;
            }
            int indent = countLeadingSpaces(line);
            if (indent < cursorIndent) {
                break;
            }
            if (indent == cursorIndent) {
                String trimmed = line.trim();
                int colonIdx = trimmed.indexOf(':');
                if (colonIdx > 0) {
                    keys.add(trimmed.substring(0, colonIdx).trim());
                }
            }
        }
        return keys;
    }

    static String dashToCamelCase(String text) {
        if (text == null || !text.contains("-")) {
            return text;
        }
        StringBuilder sb = new StringBuilder(text.length());
        boolean upper = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '-') {
                upper = true;
            } else {
                sb.append(upper ? Character.toUpperCase(c) : c);
                upper = false;
            }
        }
        return sb.toString();
    }

    int findScopeLineRow(int cursorRow) {
        if (cursorRow < 0 || cursorRow >= editState.lineCount()) {
            return -1;
        }
        String cursorLine = editState.getLine(cursorRow);
        String trimmed = cursorLine.trim();
        if (trimmed.startsWith("- ")) {
            trimmed = trimmed.substring(2).trim();
        }

        // bare list item (- ) with no key yet — no scope
        if (cursorLine.trim().startsWith("- ") && trimmed.isEmpty()) {
            return -1;
        }

        // if cursor is on a uri: line, scope is this row
        if (trimmed.startsWith("uri:")) {
            return cursorRow;
        }
        int colonIdx = trimmed.indexOf(':');
        if (colonIdx > 0) {
            String key = trimmed.substring(0, colonIdx).trim();
            // inline producer/consumer EIP (to:, enrich:) — exclude structural keys like from:
            if (!STRUCTURAL_KEYS.contains(key)
                    && (CONSUMER_EIPS.contains(key) || PRODUCER_EIPS.contains(key))) {
                return cursorRow;
            }
            // EIP definition line in a list (e.g., "- split:", "- log:")
            if (!STRUCTURAL_KEYS.contains(key) && cursorLine.trim().startsWith("- ")) {
                return cursorRow;
            }
        }

        int cursorIndent = countLeadingSpaces(cursorLine);

        if (cursorLine.isBlank()) {
            cursorIndent = editState.cursorCol();
        }

        // walk up looking for the scope line
        int parametersRow = -1;
        int parametersIndent = -1;
        for (int i = cursorRow; i >= 0; i--) {
            String line = editState.getLine(i);
            if (line.isBlank()) {
                continue;
            }
            int indent = countLeadingSpaces(line);
            String t = line.trim();

            if (t.startsWith("parameters:") && indent < cursorIndent) {
                parametersRow = i;
                parametersIndent = indent;
                break;
            }
            if (i < cursorRow && indent < cursorIndent) {
                String eipName = extractEipName(t);
                if (eipName != null && !STRUCTURAL_KEYS.contains(eipName)) {
                    // for from:/to: blocks, scope to the uri: line if cursor is below it
                    if ("from".equals(eipName) || CONSUMER_EIPS.contains(eipName)
                            || PRODUCER_EIPS.contains(eipName)) {
                        for (int j = i + 1; j < cursorRow; j++) {
                            String jl = editState.getLine(j);
                            if (!jl.isBlank() && jl.trim().startsWith("uri:")) {
                                return j;
                            }
                        }
                    }
                    return i;
                }
                cursorIndent = indent;
            }
        }

        // inside parameters: block — find the uri: line at the same indent
        if (parametersRow >= 0) {
            for (int i = parametersRow - 1; i >= 0; i--) {
                String line = editState.getLine(i);
                if (line.isBlank()) {
                    continue;
                }
                int indent = countLeadingSpaces(line);
                String t = line.trim();
                if (indent == parametersIndent && (t.startsWith("uri:") || t.startsWith("- uri:"))) {
                    return i;
                }
                if (indent < parametersIndent) {
                    // check for inline uri on the EIP line itself
                    String eipName = extractEipName(t);
                    if (eipName != null && (CONSUMER_EIPS.contains(eipName) || PRODUCER_EIPS.contains(eipName))) {
                        return i;
                    }
                    break;
                }
            }
        }
        return -1;
    }

    private String buildBreadcrumb(int cursorRow) {
        if (cursorRow < 0 || cursorRow >= editState.lineCount()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        String cursorLine = editState.getLine(cursorRow);
        // a blank line's own leading whitespace can be stale (see effectiveBlankIndent), so use
        // the cursor's real column rather than assuming the deepest nesting implied by the line
        // above — otherwise dedenting with Shift+Tab wouldn't be reflected in the breadcrumb
        int cursorIndent = cursorLine.isBlank() ? effectiveBlankIndent(cursorRow) : countLeadingSpaces(cursorLine);

        int prevIndent = cursorIndent;
        for (int i = cursorRow - 1; i >= 0; i--) {
            String line = editState.getLine(i);
            if (line.isBlank()) {
                continue;
            }
            int indent = countLeadingSpaces(line);
            if (indent < prevIndent) {
                String trimmed = line.trim();
                if (trimmed.startsWith("- ")) {
                    trimmed = trimmed.substring(2).trim();
                }
                // only include structural parent keys (lines ending with ":" with no value)
                if (!trimmed.endsWith(":")) {
                    prevIndent = indent;
                    continue;
                }
                String key = extractEipName(line.trim());
                if (key != null) {
                    if (!BREADCRUMB_SKIP_KEYS.contains(key)
                            && !key.endsWith("Configuration")) {
                        parts.add(key);
                    }
                    if ("route".equals(key)) {
                        break;
                    }
                }
                prevIndent = indent;
            }
        }

        if (parts.isEmpty()) {
            return "";
        }
        Collections.reverse(parts);
        return String.join(" > ", parts);
    }

    private String adjustPasteIndent(String text, int cursorRow) {
        int targetIndent = editState.cursorCol();
        // when cursor is at col 0, infer indent from the previous non-blank line
        if (targetIndent == 0) {
            for (int i = cursorRow - 1; i >= 0; i--) {
                String l = editState.getLine(i);
                if (!l.isBlank()) {
                    targetIndent = countLeadingSpaces(l);
                    String trimmed = l.trim();
                    if (trimmed.startsWith("- ")) {
                        trimmed = trimmed.substring(2).trim();
                    }
                    // if previous line is a parent key, indent children deeper
                    if (trimmed.endsWith(":")) {
                        targetIndent += 2;
                    }
                    break;
                }
            }
        }
        return reindentBlock(text, targetIndent);
    }

    static String reindentBlock(String text, int targetIndent) {
        text = text.replace("\t", "  ");
        String[] pasteLines = text.split("\n", -1);
        int minIndent = Integer.MAX_VALUE;
        for (String pl : pasteLines) {
            if (!pl.isBlank()) {
                minIndent = Math.min(minIndent, countLeadingSpaces(pl));
            }
        }
        if (minIndent == Integer.MAX_VALUE) {
            minIndent = 0;
        }
        int delta = targetIndent - minIndent;
        if (delta == 0) {
            return text;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < pasteLines.length; i++) {
            if (i > 0) {
                sb.append('\n');
            }
            String pl = pasteLines[i];
            if (pl.isBlank()) {
                sb.append(pl);
            } else if (delta > 0) {
                sb.append(" ".repeat(delta)).append(pl);
            } else {
                int strip = Math.min(-delta, countLeadingSpaces(pl));
                sb.append(pl.substring(strip));
            }
        }
        return sb.toString();
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

    private static String extractSchemeFromUriLine(String trimmed) {
        String value = extractUriValue(trimmed);
        if (value == null) {
            return null;
        }
        int schemeEnd = value.indexOf(':');
        if (schemeEnd > 0) {
            return value.substring(0, schemeEnd);
        }
        return value;
    }

    static String extractUriValue(String trimmed) {
        int colonIdx = trimmed.indexOf(':');
        if (colonIdx < 0) {
            return null;
        }
        String value = trimmed.substring(colonIdx + 1).trim();
        if (value.startsWith("\"") || value.startsWith("'")) {
            value = value.substring(1);
        }
        if (value.endsWith("\"") || value.endsWith("'")) {
            value = value.substring(0, value.length() - 1);
        }
        if (!value.isEmpty()) {
            return value;
        }
        return null;
    }

    private static String extractEipName(String trimmed) {
        String line = trimmed;
        if (line.startsWith("- ")) {
            line = line.substring(2).trim();
        }
        int colonIdx = line.indexOf(':');
        if (colonIdx > 0) {
            return line.substring(0, colonIdx).trim();
        }
        return null;
    }

    private static String extractInlineUri(String trimmed) {
        String line = trimmed;
        if (line.startsWith("- ")) {
            line = line.substring(2).trim();
        }
        int colonIdx = line.indexOf(':');
        if (colonIdx <= 0) {
            return null;
        }
        String eipPart = line.substring(0, colonIdx).trim();
        if (!CONSUMER_EIPS.contains(eipPart) && !PRODUCER_EIPS.contains(eipPart)) {
            return null;
        }
        String uriPart = line.substring(colonIdx + 1).trim();
        if (uriPart.isEmpty()) {
            return null;
        }
        if (uriPart.startsWith("\"") || uriPart.startsWith("'")) {
            uriPart = uriPart.substring(1);
        }
        if (uriPart.endsWith("\"") || uriPart.endsWith("'")) {
            uriPart = uriPart.substring(0, uriPart.length() - 1);
        }
        int schemeEnd = uriPart.indexOf(':');
        if (schemeEnd > 0) {
            return uriPart.substring(0, schemeEnd);
        }
        return null;
    }

    private void openAutocomplete() {
        if (isCamelYamlFile()) {
            openYamlAutocomplete();
        } else {
            openPropertiesAutocomplete();
        }
    }

    private void openPropertiesAutocomplete() {
        String lineText = editState.getLine(editState.cursorRow());
        int col = editState.cursorCol();
        String textBeforeCursor = col <= lineText.length() ? lineText.substring(0, col) : lineText;

        int eq = textBeforeCursor.indexOf('=');
        if (eq >= 0 && autocompleteValueProvider != null) {
            // cursor is after '=' — try value completion
            String key = textBeforeCursor.substring(0, eq).trim();
            String valuePrefix = textBeforeCursor.substring(eq + 1).trim();
            List<AutocompletePopup.CompletionItem> values = autocompleteValueProvider.provide(key);
            if (values != null && !values.isEmpty()) {
                autocompletePopup = new AutocompletePopup(values, valuePrefix, valuePrefix, true);
            }
            return;
        }

        // key completion
        String prefix = textBeforeCursor.trim();

        // load all options for the group (up to last dot) so the full list is available
        int lastDot = prefix.lastIndexOf('.');
        String groupPrefix = lastDot >= 0 ? prefix.substring(0, lastDot + 1) : prefix;

        // extract full key text for left/right cursor navigation
        String fullKey = lineText;
        int eqFull = fullKey.indexOf('=');
        if (eqFull >= 0) {
            fullKey = fullKey.substring(0, eqFull);
        }
        fullKey = fullKey.trim();

        List<AutocompletePopup.CompletionItem> items = autocompleteProvider.provide(groupPrefix);
        if (items != null && !items.isEmpty()) {
            autocompletePopup = new AutocompletePopup(items, prefix, fullKey);
        }
    }

    private void openYamlAutocomplete() {
        int row = editState.cursorRow();
        if (findScopeLineRow(row) < 0) {
            return;
        }
        String lineText = editState.getLine(row);
        String trimmed = lineText.trim();
        if (trimmed.startsWith("- ")) {
            trimmed = trimmed.substring(2).trim();
        } else if (trimmed.equals("-")) {
            trimmed = "";
        }

        // try component name completion on uri: lines first
        YamlUriContext uriCtx = findUriContext(row);
        if (uriCtx != null && autocompleteProvider != null) {
            String role = uriCtx.consumer() ? "consumer" : "producer";
            String context = "yaml-uri:" + role;
            List<AutocompletePopup.CompletionItem> items = autocompleteProvider.provide(context);
            if (items != null && !items.isEmpty()) {
                autocompletePopup = new AutocompletePopup(items, uriCtx.prefix(), uriCtx.prefix(), true);
                autocompletePopup.setTitlePrefix("Components");
            }
            return;
        }

        YamlEndpointContext ctx = findEnclosingComponent(row);
        if (ctx != null) {
            int colonIdx = trimmed.indexOf(':');
            if (colonIdx > 0) {
                String optionName = trimmed.substring(0, colonIdx).trim();
                String valueText = trimmed.substring(colonIdx + 1).trim();
                if (valueText.startsWith("\"") || valueText.startsWith("'")) {
                    valueText = valueText.substring(1);
                }
                if (valueText.endsWith("\"") || valueText.endsWith("'")) {
                    valueText = valueText.substring(0, valueText.length() - 1);
                }
                if (autocompleteValueProvider != null) {
                    String context = "yaml:" + ctx.component() + ":" + optionName;
                    List<AutocompletePopup.CompletionItem> values = autocompleteValueProvider.provide(context);
                    if (values != null && !values.isEmpty()) {
                        autocompletePopup = new AutocompletePopup(values, "", valueText, true);
                    }
                }
            } else {
                // auto-insert parameters: block if cursor is below uri: without one
                if (ctx.needsParameters() && lineText.isBlank()) {
                    // parameters: must be a sibling of uri:, so use the cursor's real column
                    // (matching uri:'s indent via Enter-key auto-indent) rather than
                    // deriveInsertionIndent's EIP-scope heuristic, which resolves the scope to
                    // the uri: line itself here and then adds a level, nesting parameters: one
                    // level too deep and breaking findEnclosingComponent's uri-sibling lookup
                    int indent = effectiveBlankIndent(row);
                    String indentStr = " ".repeat(indent);
                    editState.moveCursorToLineStart();
                    editState.insert(indentStr + "parameters:");
                    editState.insert('\n');
                    editState.insert(indentStr + "  ");
                    dirty = true;
                    trimmed = "";
                }

                String filter = trimmed;
                String role = ctx.consumer() ? "consumer" : "producer";
                java.util.Set<String> existing = collectExistingParameters(editState.cursorRow());
                String context = "yaml:" + ctx.component() + ":" + role;
                if (!existing.isEmpty()) {
                    context += ":" + String.join(",", existing);
                }
                if (ctx.uri() != null) {
                    context += "|" + ctx.uri();
                }
                List<AutocompletePopup.CompletionItem> items = autocompleteProvider.provide(context);
                if (items != null && !items.isEmpty()) {
                    autocompletePopup = new AutocompletePopup(items, filter, filter);
                    autocompletePopup.setTitlePrefix(ctx.component() + " options");
                }
            }
            return;
        }

        // tree-driven completion — walk up to find parent key, use completion tree
        if (autocompleteProvider != null) {
            String parentKey = findParentYamlKey(row);
            int colonIdx = trimmed.indexOf(':');

            if (colonIdx > 0) {
                // value completion
                String optionName = trimmed.substring(0, colonIdx).trim();
                String valueText = trimmed.substring(colonIdx + 1).trim();
                if (valueText.startsWith("\"") || valueText.startsWith("'")) {
                    valueText = valueText.substring(1);
                }
                if (valueText.endsWith("\"") || valueText.endsWith("'")) {
                    valueText = valueText.substring(0, valueText.length() - 1);
                }
                if (autocompleteValueProvider != null) {
                    String context = "yaml-tree-value:" + parentKey + ":" + optionName;
                    List<AutocompletePopup.CompletionItem> values = autocompleteValueProvider.provide(context);
                    if (values != null && !values.isEmpty()) {
                        autocompletePopup = new AutocompletePopup(values, "", valueText, true);
                    }
                }
            } else {
                // key completion
                String filter = trimmed;
                java.util.Set<String> existing = collectExistingSiblingKeys(row);
                String context = "yaml-tree:" + parentKey;
                if (!existing.isEmpty()) {
                    context += ":" + String.join(",", existing);
                }
                List<AutocompletePopup.CompletionItem> items = autocompleteProvider.provide(context);
                if (items != null && !items.isEmpty()) {
                    autocompletePopup = new AutocompletePopup(items, filter, filter);
                    autocompletePopup.setTitlePrefix(parentKey);
                    if (listItemNodeChecker != null && listItemNodeChecker.test(parentKey)) {
                        autocompletePopup.setListItemInsertion(true);
                    }
                }
            }
        }
    }

    private void insertCompletion(AutocompletePopup.CompletionItem item, boolean valueMode, boolean listItem) {
        recordEditChange();
        String currentLine = editState.getLine(editState.cursorRow());
        if (isCamelYamlFile()) {
            insertYamlCompletion(item, valueMode, currentLine, listItem);
        } else {
            insertPropertiesCompletion(item, valueMode, currentLine);
        }
    }

    private void insertPropertiesCompletion(AutocompletePopup.CompletionItem item, boolean valueMode, String currentLine) {
        if (valueMode) {
            int eq = currentLine.indexOf('=');
            if (eq >= 0) {
                String keyPart = currentLine.substring(0, eq + 1);
                editState.moveCursorToLineStart();
                for (int i = 0; i < currentLine.length(); i++) {
                    editState.deleteForward();
                }
                editState.insert(keyPart + item.key());
            }
        } else {
            editState.moveCursorToLineStart();
            for (int i = 0; i < currentLine.length(); i++) {
                editState.deleteForward();
            }
            boolean isGroup = item.key().endsWith(".");
            String insertText = isGroup ? item.key() : item.key() + "=";
            editState.insert(insertText);
            if (isGroup && autocompleteProvider != null) {
                openAutocomplete();
            }
        }
    }

    void insertYamlCompletion(AutocompletePopup.CompletionItem item, boolean valueMode, String currentLine) {
        // no explicit cursor column given (e.g. direct test calls) — assume the cursor sits at
        // the end of the given line, matching this method's original, column-agnostic behavior
        insertYamlCompletion(item, valueMode, currentLine, false, currentLine.length());
    }

    private void insertYamlCompletion(
            AutocompletePopup.CompletionItem item, boolean valueMode, String currentLine, boolean listItem) {
        insertYamlCompletion(item, valueMode, currentLine, listItem, editState.cursorCol());
    }

    /** Package-private (rather than private) so tests can exercise an explicit cursor column. */
    void insertYamlCompletion(
            AutocompletePopup.CompletionItem item, boolean valueMode, String currentLine, boolean listItem,
            int cursorCol) {
        int indent;
        if (currentLine.isEmpty()) {
            // truly empty line (no auto-inserted whitespace yet) — derive from the enclosing EIP
            indent = deriveInsertionIndent(editState.cursorRow());
        } else if (currentLine.isBlank()) {
            // whitespace-only line: the cursor's column is the real, intended nesting depth —
            // Shift+Tab (see moveCursorToPreviousIndentStop()) can dedent it within the existing
            // whitespace without trimming the line, so the line's own length would be stale here
            indent = Math.min(cursorCol, currentLine.length());
        } else {
            indent = countLeadingSpaces(currentLine);
        }
        String indentStr = " ".repeat(indent);

        editState.moveCursorToLineStart();
        for (int i = 0; i < currentLine.length(); i++) {
            editState.deleteForward();
        }

        if (valueMode) {
            String trimmed = currentLine.trim();
            if (trimmed.startsWith("- ")) {
                trimmed = trimmed.substring(2).trim();
            }
            int colonIdx = trimmed.indexOf(':');
            String value = item.key();
            if (value.contains("{{")) {
                value = "\"" + value + "\"";
            }
            if (colonIdx > 0) {
                String keyPart = trimmed.substring(0, colonIdx);
                editState.insert(indentStr + keyPart + ": " + value);
            } else {
                editState.insert(indentStr + value);
            }
            // for component names, add parameters: block if not already present
            if ("component".equals(item.type())) {
                int nextRow = editState.cursorRow() + 1;
                boolean hasParameters = nextRow < editState.lineCount()
                        && editState.getLine(nextRow).trim().startsWith("parameters:");
                if (!hasParameters) {
                    editState.insert('\n');
                    editState.insert(indentStr + "parameters:");
                    editState.insert('\n');
                    editState.insert(indentStr + "  ");
                }
            }
        } else {
            String prefix = listItem ? "- " : "";
            editState.insert(indentStr + prefix + item.key() + ":");
            if ("array".equals(item.type())) {
                editState.insert('\n');
                int childIndent = indent + (listItem ? 4 : 2);
                editState.insert(" ".repeat(childIndent) + "- ");
            } else if ("object".equals(item.type())) {
                editState.insert('\n');
                editState.insert(indentStr + (listItem ? "    " : "  "));
            } else {
                editState.insert(' ');
            }
        }
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
            Path path = editableFile;
            boolean restoreMarkdownMode = markdownModeBeforeEdit;
            notifySave("Saved: " + editableFile.getFileName(), false);
            editMode = false;
            editState.clear();
            markdownModeBeforeEdit = false;
            loadFile(path);
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
            notifySave("Saved: " + editableFile.getFileName(), false);
        } catch (IOException e) {
            notifySave("Save failed: " + e.getMessage(), true);
        }
    }

    private void validateAndNotify(String content) {
        if (validateOnSave && isCamelYamlFile()) {
            List<String> msgs = new ArrayList<>();
            List<Error> errors = validateYaml(content);
            if (errors != null && !errors.isEmpty()) {
                for (Error error : errors) {
                    String msg = error.getMessage();
                    if (msg != null) {
                        String loc = error.getInstanceLocation() != null
                                ? error.getInstanceLocation().toString() : null;
                        String node = extractNodeName(loc);
                        String clean = cleanValidationMessage(msg);
                        if (node != null) {
                            msgs.add(node + ": " + clean);
                        } else {
                            msgs.add(clean);
                        }
                    }
                }
            }
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
            if (!msgs.isEmpty()) {
                validationErrors = msgs;
                validationErrorScroll = 0;
                inlineErrors = buildInlineErrors(msgs, content);
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
        }
        inlineErrors = Collections.emptyMap();
    }

    private void jumpToNextError() {
        List<Integer> errorLines = new ArrayList<>(inlineErrors.keySet());
        Collections.sort(errorLines);
        int cursorRow = editState.cursorRow();
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
        long now = System.currentTimeMillis();
        if (now - lastBackgroundValidationTime < BACKGROUND_VALIDATION_INTERVAL_MS) {
            return;
        }
        String content = editState.text();
        if (content.equals(lastBackgroundValidationContent)) {
            return;
        }
        lastBackgroundValidationTime = now;
        lastBackgroundValidationContent = content;

        List<String> msgs = new ArrayList<>();
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
        } else if (isPropertiesFile() && propertiesValidator != null) {
            msgs.addAll(validateProperties(content));
        }
        inlineErrors = msgs.isEmpty() ? Collections.emptyMap() : buildInlineErrors(msgs, content);
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

    /** Package-private (rather than private) so tests can exercise it directly. */
    static boolean isEmptyValueLine(String line) {
        String trimmed = line.trim();
        if (trimmed.startsWith("- ")) {
            trimmed = trimmed.substring(2).trim();
        }
        int colon = trimmed.indexOf(':');
        if (colon < 0) {
            return trimmed.isEmpty();
        }
        return trimmed.substring(colon + 1).trim().isEmpty();
    }

    static Map<Integer, String> buildInlineErrors(List<String> errors, String content) {
        Map<Integer, String> result = new java.util.LinkedHashMap<>();
        java.util.regex.Pattern linePattern = java.util.regex.Pattern.compile("^Line (\\d+): (.*)");
        for (String error : errors) {
            java.util.regex.Matcher m = linePattern.matcher(error);
            if (m.matches()) {
                int lineNum = Integer.parseInt(m.group(1)) - 1;
                result.putIfAbsent(lineNum, m.group(2));
            }
        }
        return result;
    }

    private List<String> validateProperties(String content) {
        List<String> msgs = new ArrayList<>();
        String[] lines = content.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("!")) {
                continue;
            }
            if (!line.contains("=")) {
                continue;
            }
            String error = propertiesValidator.validate(lines[i]);
            if (error != null) {
                msgs.add("Line " + (i + 1) + ": " + error);
            }
        }
        return msgs;
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

    private static String cleanValidationMessage(String msg) {
        // strip FQCN prefix like "com.fasterxml...MarkedYAMLException: "
        int colonSpace = msg.indexOf(": ");
        if (colonSpace > 0) {
            String prefix = msg.substring(0, colonSpace);
            if (prefix.contains(".") && !prefix.contains(" ")) {
                msg = msg.substring(colonSpace + 2);
            }
        }
        // strip "at [Source: (StringReader); line: N, column: N]"
        int atSource = msg.indexOf("at [Source:");
        if (atSource > 0) {
            msg = msg.substring(0, atSource).stripTrailing();
        }
        // strip "in 'reader', " prefix from snakeyaml messages
        msg = msg.replace("in 'reader', ", "");
        return msg;
    }

    private static String extractNodeName(String instanceLocation) {
        if (instanceLocation == null || instanceLocation.isEmpty()) {
            return null;
        }
        int slash = instanceLocation.lastIndexOf('/');
        String last = slash >= 0 ? instanceLocation.substring(slash + 1) : instanceLocation;
        if (last.isEmpty()) {
            return null;
        }
        // skip pure numeric segments (array indices)
        try {
            Integer.parseInt(last);
            return null;
        } catch (NumberFormatException e) {
            return last;
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
            if (autocompletePopup != null) {
                boolean wasValueMode = autocompletePopup.isValueMode();
                boolean wasListItem = autocompletePopup.isListItemInsertion();
                AutocompletePopup.Result result = autocompletePopup.handleMouseEvent(me);
                if (result == AutocompletePopup.Result.CLOSED) {
                    AutocompletePopup.CompletionItem item = autocompletePopup.consumeSelectedItem();
                    autocompletePopup = null;
                    if (item != null) {
                        insertCompletion(item, wasValueMode, wasListItem);
                    }
                }
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
                markdownScroll = Math.max(0, markdownScroll - 3);
                return true;
            }
            if (me.kind() == MouseEventKind.SCROLL_DOWN) {
                markdownScroll += 3;
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
        if (editMode) {
            if (search.isSearchInputActive()) {
                search.handlePaste(text);
                return;
            }
            if (text != null && !text.isEmpty()) {
                recordEditChange();
                editState.insert(text);
            }
            return;
        }
        search.handlePaste(text);
    }

    void render(Frame frame, Rect area) {
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
            MarkdownView view = MarkdownView.builder()
                    .source(rawMarkdownContent)
                    .scroll(markdownScroll)
                    .block(block)
                    .styles(Theme.markdownStyles())
                    .build();
            frame.renderWidget(view, area);
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
                while (scrollY < selectedLine
                        && countVisualRows(scrollY, selectedLine + 1, contentWidth) > visibleLines) {
                    scrollY++;
                }
            } else if (selectedLine >= scrollY + visibleLines) {
                scrollY = selectedLine - visibleLines + 1;
            }
        }

        int maxScroll;
        if (wordWrap) {
            maxScroll = 0;
            int visualFromEnd = 0;
            for (int i = lines.size() - 1; i >= 0; i--) {
                visualFromEnd += wrapRowCount(lines.get(i), contentWidth);
                if (visualFromEnd >= visibleLines) {
                    maxScroll = i;
                    break;
                }
            }
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
            if (titleText != null) {
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
            if (viewDocEntries != null && !viewDocEntries.isEmpty()) {
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
                String breadcrumb = buildBreadcrumb(editState.cursorRow());
                if (!breadcrumb.isEmpty()) {
                    titleSpans.add(Span.styled(" " + breadcrumb + " ", Style.EMPTY.dim().italic()));
                }
            }
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
            if (!visibleErrors.isEmpty()) {
                Style errorStyle = Style.EMPTY.fg(dev.tamboui.style.Color.rgb(0xFF, 0x66, 0x66));
                blockBuilder.title(Title.from(Line.from(
                        Span.styled(" errors: " + visibleErrors.size() + " ", errorStyle))).right());
            }
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
            editDocEntries = editQuickDocProvider.provideForLine(editLines(), editState.cursorRow());
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
        // on first render, position cursor at 2/3 of viewport before TextArea renders
        if (editInitialScroll) {
            editInitialScroll = false;
            int viewportH = textAreaRect.height();
            int twoThirds = viewportH * 2 / 3;
            int targetScroll = Math.max(0, editState.cursorRow() - twoThirds);
            if (targetScroll > 0) {
                editState.scrollDown(targetScroll, viewportH);
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
            int scopeRow = findScopeLineRow(editState.cursorRow());
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
                    Style errorBg = Style.EMPTY.fg(dev.tamboui.style.Color.WHITE)
                            .bg(dev.tamboui.style.Color.rgb(0x8B, 0x00, 0x00));
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
                titleText = "Error";
            } else if (editDocEntries != null && !editDocEntries.isEmpty()) {
                titleText = editDocEntries.get(0).title();
            }
            if (cursorError != null) {
                String prefix = "─── ";
                String suffix = " ";
                int remaining = Math.max(0, docArea.width() - prefix.length() - titleText.length() - suffix.length());
                Style errorDim = Style.EMPTY.fg(dev.tamboui.style.Color.rgb(0xFF, 0x66, 0x66));
                docLines.add(Line.from(
                        Span.styled(prefix, errorDim),
                        Span.styled(titleText, errorDim.bold()),
                        Span.styled(suffix + "─".repeat(remaining), errorDim)));
                docLines.add(Line.from(Span.styled(cursorError, errorDim)));
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

        if (autocompletePopup != null) {
            int cursorRow = editState.cursorRow() - editState.scrollRow();
            int cursorCol = editState.cursorCol() - editState.scrollCol();
            autocompletePopup.render(frame, editorArea, cursorRow, cursorCol);
        }

        if (validationErrors != null) {
            renderValidationPopup(frame, area);
        }
        if (pendingDiscard) {
            renderDiscardPopup(frame, area);
        }
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
        if (entries.isEmpty()) {
            entries = List.of(new EditDiff.DiffEntry(' ', "(no changes)", 0));
        }

        int maxLineNum = entries.stream().mapToInt(EditDiff.DiffEntry::lineNum).max().orElse(1);
        int lineDigits = Math.max(2, String.valueOf(maxLineNum).length());
        int gutterWidth = lineDigits + 2;

        diffScrollY = Math.max(0, Math.min(diffScrollY, Math.max(0, entries.size() - inner.height())));
        for (int r = 0; r < inner.height(); r++) {
            int idx = diffScrollY + r;
            if (idx >= entries.size()) {
                break;
            }
            int screenY = inner.top() + r;
            EditDiff.DiffEntry entry = entries.get(idx);
            Style lineStyle;
            Style gutterStyle;
            if (entry.type() == '-') {
                lineStyle = Style.EMPTY.fg(dev.tamboui.style.Color.WHITE).bg(dev.tamboui.style.Color.rgb(0x6E, 0x1B, 0x1B));
                gutterStyle = lineStyle;
            } else if (entry.type() == '+') {
                lineStyle = Style.EMPTY.fg(dev.tamboui.style.Color.WHITE).bg(dev.tamboui.style.Color.rgb(0x1B, 0x4D, 0x1B));
                gutterStyle = lineStyle;
            } else if (entry.type() == '~') {
                lineStyle = Style.EMPTY.dim();
                gutterStyle = Style.EMPTY.dim();
            } else {
                lineStyle = Style.EMPTY;
                gutterStyle = Style.EMPTY.dim();
            }

            // fill entire row with background for changed lines
            if (entry.type() == '-' || entry.type() == '+') {
                Rect rowRect = new Rect(inner.left(), screenY, inner.width(), 1);
                frame.buffer().setStyle(rowRect, lineStyle);
            }

            // line number from original file (for -) or current file (for + and context)
            String lineNum = entry.lineNum() > 0
                    ? String.format("%" + lineDigits + "d ", entry.lineNum())
                    : " ".repeat(lineDigits + 1);
            frame.buffer().setString(inner.left(), screenY, lineNum, gutterStyle);
            frame.buffer().set(inner.left() + gutterWidth - 1, screenY,
                    new dev.tamboui.buffer.Cell("│", gutterStyle));

            int textX = inner.left() + gutterWidth;
            int maxWidth = Math.max(0, inner.width() - gutterWidth);
            String prefix = entry.type() == ' ' ? "  " : entry.type() + " ";
            String text = prefix + entry.text();
            if (text.length() > maxWidth) {
                text = text.substring(0, maxWidth);
            }
            frame.buffer().setString(textX, screenY, text, lineStyle);
        }
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
        allLines.add(Line.from(Span.raw("  "),
                Span.styled("Esc", Style.EMPTY.bold()), Span.raw(" close")));

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
        int popupW = Math.min(40, area.width() - 4);
        int popupH = 6;
        int x = area.left() + Math.max(0, (area.width() - popupW) / 2);
        int y = area.top() + Math.max(0, (area.height() - popupH) / 2);
        Rect popup = new Rect(x, y, popupW, popupH);

        frame.renderWidget(Clear.INSTANCE, popup);

        Block block = Block.builder()
                .borderType(BorderType.ROUNDED).borders(Borders.ALL)
                .title(Title.from(Line.from(Span.styled(" Discard Changes? ", Theme.warning().bold()))))
                .build();
        frame.renderWidget(block, popup);
        Rect inner = block.inner(popup);

        frame.renderWidget(
                Paragraph.builder().text(Text.from(
                        Line.empty(),
                        Line.from(Span.raw(" Unsaved changes will be lost.")),
                        Line.empty(),
                        Line.from(Span.raw("  "),
                                Span.styled("Enter", Style.EMPTY.bold()), Span.raw(" confirm  "),
                                Span.styled("Esc", Style.EMPTY.bold()), Span.raw(" cancel"))))
                        .build(),
                inner);
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
        if (editMode && validationErrors != null) {
            TuiHelper.hint(spans, TuiIcons.HINT_SCROLL, "scroll");
            TuiHelper.hintLast(spans, "Esc", "close");
            return;
        }
        if (editMode && diffOverlay) {
            TuiHelper.hint(spans, "Esc/F7", "close diff");
            TuiHelper.hint(spans, TuiIcons.HINT_SCROLL, "scroll");
            return;
        }
        if (editMode) {
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
            if (autocompleteProvider != null) {
                TuiHelper.hint(spans, "Tab", "complete");
            }
            TuiHelper.hint(spans, "Shift+Tab", "dedent");
            if (!inlineErrors.isEmpty()) {
                TuiHelper.hint(spans, "F9", "next error");
            }
            TuiHelper.hint(spans, TuiIcons.HINT_SCROLL, "move");
            return;
        }
        if (markdownMode) {
            TuiHelper.hint(spans, "Esc/c", "close");
            TuiHelper.hint(spans, TuiIcons.HINT_SCROLL, "scroll");
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
        TuiHelper.hint(spans, TuiIcons.HINT_SCROLL, "navigate");
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
            } else {
                rawMarkdownContent = null;
                markdownMode = false;
            }
            editableFile = Files.isWritable(filePath) ? filePath : null;
            scanDeprecatedLines();
            jumpLinks = Collections.emptyMap();
        } catch (IOException e) {
            title = fileName;
            lines = List.of("(Failed to read file: " + e.getMessage() + ")");
            codeData = Collections.emptyList();
            visible = true;
            isMarkdownFile = false;
            markdownMode = false;
            rawMarkdownContent = null;
            editableFile = null;
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
            refreshQuickDoc();
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
            refreshQuickDoc();
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
                spans.add(Span.styled(prefix, (focused ? Theme.label().bold() : Theme.label().dim()).patch(selBg)));
            }
            for (Span s : highlighted.spans()) {
                spans.add(Span.styled(s.content(), s.style().patch(selBg)));
            }
        } else {
            spans.add(isDeprecated
                    ? Span.styled(" ⚠ ", Theme.warning())
                    : Span.raw("   "));
            if (!prefix.isEmpty()) {
                spans.add(Span.styled(prefix, Style.EMPTY.dim()));
            }
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

    private static Line applyHorizontalSkip(Line line, int hSkip) {
        List<Span> scrolled = new ArrayList<>();
        int skipped = 0;
        for (Span span : line.spans()) {
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
        return scrolled.isEmpty() ? Line.from(List.of(Span.raw(""))) : Line.from(scrolled);
    }

    private int computeGutterWidth() {
        for (String line : lines) {
            int w = 0;
            for (int i = 0; i < line.length(); i++) {
                char c = line.charAt(i);
                if (c != ' ' && !Character.isDigit(c)) {
                    break;
                }
                w++;
            }
            if (w > 0) {
                return w;
            }
        }
        return 0;
    }

    private List<Line> renderQuickDocLines(DocEntry entry, int sourceIndent, int gutterWidth, int viewportWidth) {
        String text = entry.text();
        String prefix = " ".repeat(gutterWidth + 5 + sourceIndent);
        String marker = "ℹ ";
        Style docStyle = Style.EMPTY.dim().italic();
        int prefixWidth = prefix.length() + marker.length();

        if (!wordWrap || viewportWidth <= 0 || prefixWidth + text.length() <= viewportWidth) {
            return List.of(Line.from(List.of(
                    Span.raw(prefix),
                    Span.styled(marker, docStyle),
                    Span.styled(text, docStyle))));
        }

        int textWidth = viewportWidth - prefixWidth;
        if (textWidth <= 10) {
            return List.of(Line.from(List.of(
                    Span.raw(prefix),
                    Span.styled(marker, docStyle),
                    Span.styled(text, docStyle))));
        }

        String contPrefix = " ".repeat(prefixWidth);
        List<Line> result = new ArrayList<>();
        int pos = 0;
        boolean first = true;
        while (pos < text.length()) {
            int end = Math.min(pos + textWidth, text.length());
            if (end < text.length()) {
                int space = text.lastIndexOf(' ', end);
                if (space > pos) {
                    end = space + 1;
                }
            }
            String chunk = text.substring(pos, end).stripTrailing();
            if (first) {
                result.add(Line.from(List.of(
                        Span.raw(prefix),
                        Span.styled(marker, docStyle),
                        Span.styled(chunk, docStyle))));
                first = false;
            } else {
                result.add(Line.from(List.of(
                        Span.raw(contPrefix),
                        Span.styled(chunk, docStyle))));
            }
            pos = end;
        }
        return result;
    }

    private int countVisualRows(int fromLine, int toLine, int contentWidth) {
        int count = 0;
        for (int i = fromLine; i < toLine && i < lines.size(); i++) {
            count += wrapRowCount(lines.get(i), contentWidth);
            if (quickDocEnabled) {
                List<DocEntry> docs = quickDocEntries.get(i);
                if (docs != null) {
                    count += docs.size();
                }
            }
        }
        return count;
    }

    private int wrapRowCount(String line, int contentWidth) {
        if (!wordWrap || contentWidth <= 0 || line.length() <= contentWidth) {
            return 1;
        }
        return (line.length() + contentWidth - 1) / contentWidth;
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
