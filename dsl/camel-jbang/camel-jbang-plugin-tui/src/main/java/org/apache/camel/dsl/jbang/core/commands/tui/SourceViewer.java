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
import java.util.function.IntConsumer;

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

    record DocEntry(String text, boolean deprecated) {
        static DocEntry of(String text) {
            return new DocEntry(text, false);
        }

        static DocEntry deprecated(String text) {
            return new DocEntry(text, true);
        }
    }

    @FunctionalInterface
    interface QuickDocProvider {
        Map<Integer, List<DocEntry>> provideAll(List<JsonObject> codeData);
    }

    @FunctionalInterface
    interface DeprecatedLineScanner {
        Set<Integer> scan(List<JsonObject> codeData);
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
    private DeprecatedLineScanner deprecatedLineScanner;
    private Set<Integer> deprecatedLines = Collections.emptySet();
    private Style titleStyle;
    private Style borderStyle;
    private boolean focused = true;

    /** Local file path when content was loaded via {@link #loadFile(Path)} and is writable. */
    private Path editableFile;
    private boolean editMode;
    private final TextAreaState editState = new TextAreaState();
    private String saveMessage;
    private boolean saveError;

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

    void hide() {
        exitEditMode(false);
        visible = false;
        onLineSelected = null;
        quickDocEnabled = false;
        quickDocEntries = Collections.emptyMap();
        deprecatedLines = Collections.emptySet();
        editableFile = null;
        saveMessage = null;
        saveError = false;
    }

    void reset() {
        exitEditMode(false);
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
        editableFile = null;
        saveMessage = null;
        saveError = false;
    }

    boolean isEditMode() {
        return editMode;
    }

    boolean isEditable() {
        return editableFile != null && Files.isRegularFile(editableFile) && Files.isWritable(editableFile);
    }

    /**
     * True when the viewer is consuming typed input (search box or plain-text edit mode). Used by the monitor to avoid
     * treating digit/letter keys as global shortcuts.
     */
    boolean isTextInputActive() {
        return editMode || search.isSearchInputActive();
    }

    void setOnLineSelected(IntConsumer callback) {
        this.onLineSelected = callback;
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
            visible = false;
            onLineSelected = null;
            editableFile = null;
            return true;
        }
        if (ke.isChar('c')) {
            visible = false;
            onLineSelected = null;
            editableFile = null;
            return true;
        }
        if (isEditable() && ke.isChar('e')) {
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
        if (ke.isChar('i') && quickDocProvider != null) {
            toggleQuickDoc();
            return true;
        }
        if (ke.isChar('w')) {
            wordWrap = !wordWrap;
            scrollX = 0;
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

    private boolean handleEditKeyEvent(KeyEvent ke) {
        if (ke.isCancel()) {
            exitEditMode(false);
            return true;
        }
        if (ke.isKey(KeyCode.F5)) {
            saveEdit();
            return true;
        }
        if (ke.isConfirm()) {
            editState.insert('\n');
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
            editState.moveCursorToLineStart();
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
            editState.deleteBackward();
            return true;
        }
        if (ke.isDeleteForward()) {
            editState.deleteForward();
            return true;
        }
        if (ke.code() == KeyCode.CHAR) {
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
        markdownMode = false;
        quickDocEnabled = false;
        search.reset();
        saveMessage = null;
        saveError = false;
        editMode = true;
    }

    private void exitEditMode(boolean reloadFromDisk) {
        if (!editMode && !reloadFromDisk) {
            editState.clear();
            return;
        }
        editMode = false;
        editState.clear();
        if (reloadFromDisk && editableFile != null) {
            Path path = editableFile;
            loadFile(path);
        }
    }

    private void saveEdit() {
        if (!editMode || editableFile == null) {
            return;
        }
        try {
            Files.writeString(editableFile, editState.text(), StandardCharsets.UTF_8);
            saveMessage = "Saved";
            saveError = false;
            Path path = editableFile;
            editMode = false;
            editState.clear();
            loadFile(path);
            // Preserve save feedback after reload
            saveMessage = "Saved";
            saveError = false;
        } catch (IOException e) {
            saveMessage = "Save failed: " + e.getMessage();
            saveError = true;
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
            if (text != null && !text.isEmpty()) {
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
                .borderType(BorderType.ROUNDED).borders(Borders.ALL)
                .title(buildTitle());
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

        int visibleLines = inner.height();

        // Reserve bottom row for horizontal scrollbar when content is wider than viewport
        if (!wordWrap) {
            int cursorWidth = 3;
            int maxLineWidth = lines.stream().mapToInt(String::length).max().orElse(0) + cursorWidth;
            if (maxLineWidth > inner.width()) {
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

        int contentWidth = inner.width() - 1;

        // Auto-scroll to keep selected line visible (accounting for word wrap and inline doc lines)
        if (selectedLine >= 0) {
            if (selectedLine < scrollY) {
                scrollY = selectedLine;
            } else if (wordWrap || (quickDocEnabled && !quickDocEntries.isEmpty())) {
                while (scrollY < selectedLine
                        && countVisualRows(scrollY, selectedLine + 1, contentWidth) > visibleLines) {
                    scrollY++;
                }
            } else if (selectedLine >= scrollY + visibleLines) {
                scrollY = selectedLine - visibleLines + 1;
            }
        }

        int maxScroll;
        if (wordWrap || (quickDocEnabled && !quickDocEntries.isEmpty())) {
            maxScroll = 0;
            int visualFromEnd = 0;
            for (int i = lines.size() - 1; i >= 0; i--) {
                visualFromEnd += wrapRowCount(lines.get(i), contentWidth);
                if (quickDocEnabled) {
                    List<DocEntry> docs = quickDocEntries.get(i);
                    if (docs != null) {
                        visualFromEnd += docs.size();
                    }
                }
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
            int maxHScroll = Math.max(0, maxLineWidth - inner.width());
            scrollX = Math.min(scrollX, maxHScroll);
        }

        int currentMatchLine = search.currentMatchLine();

        int gutterWidth = quickDocEnabled && !quickDocEntries.isEmpty() ? computeGutterWidth() : 0;

        List<Line> visible = new ArrayList<>();
        for (int i = scrollY; i < lines.size() && visible.size() < visibleLines; i++) {
            String raw = lines.get(i);
            boolean isSelected = (i == selectedLine);
            Line line = highlightSourceLine(raw, i, hSkip, isSelected, inner.width());
            line = search.applyHighlights(line, i, currentMatchLine);
            visible.add(line);

            List<DocEntry> docLines = quickDocEnabled ? quickDocEntries.get(i) : null;
            if (docLines != null) {
                String code = i < codeData.size() && codeData.get(i).get("code") != null
                        ? codeData.get(i).get("code").toString()
                        : "";
                int si = 0;
                while (si < code.length() && code.charAt(si) == ' ') {
                    si++;
                }
                for (DocEntry docEntry : docLines) {
                    for (Line docLine : renderQuickDocLines(docEntry, si, gutterWidth, inner.width())) {
                        if (visible.size() >= visibleLines) {
                            break;
                        }
                        if (hSkip > 0) {
                            docLine = applyHorizontalSkip(docLine, hSkip);
                        }
                        visible.add(docLine);
                    }
                }
            }
        }

        List<Rect> hChunks = Layout.horizontal()
                .constraints(Constraint.fill(), Constraint.length(1))
                .split(inner);

        Overflow overflow = wordWrap ? Overflow.WRAP_WORD : Overflow.CLIP;
        frame.renderWidget(Paragraph.builder().text(Text.from(visible)).overflow(overflow).build(), hChunks.get(0));

        int totalDocLines = quickDocEnabled ? quickDocEntries.values().stream().mapToInt(List::size).sum() : 0;
        int totalContentLines = lines.size() + totalDocLines;
        if (totalContentLines > visibleLines) {
            vScrollState.contentLength(totalContentLines).viewportContentLength(visibleLines).position(scrollY);
            frame.renderStatefulWidget(Scrollbar.builder().build(), hChunks.get(1), vScrollState);
        }
        if (!wordWrap) {
            int cursorWidth = 3;
            int maxLineWidth = lines.stream().mapToInt(String::length).max().orElse(0) + cursorWidth;
            int maxHScroll = Math.max(0, maxLineWidth - inner.width());
            if (maxHScroll > 0) {
                hScrollState.contentLength(maxLineWidth).viewportContentLength(inner.width()).position(scrollX);
                frame.renderStatefulWidget(Scrollbar.horizontal(), inner, hScrollState);
            }
        }
    }

    private void renderEditMode(Frame frame, Rect area) {
        Style ts = titleStyle != null ? titleStyle : Style.EMPTY;
        List<Span> titleSpans = new ArrayList<>();
        String info = title != null ? title : "";
        titleSpans.add(Span.styled(" Edit [" + info + "] ", ts));
        if (saveMessage != null) {
            titleSpans.add(Span.styled(saveMessage + " ", saveError ? Theme.error() : Theme.success()));
        }
        Block.Builder blockBuilder = Block.builder()
                .borderType(BorderType.ROUNDED).borders(Borders.ALL)
                .title(Title.from(Line.from(titleSpans)));
        if (borderStyle != null) {
            blockBuilder.borderStyle(borderStyle);
        }
        Block block = blockBuilder.build();
        Rect inner = block.inner(area);
        lastInnerArea = inner;
        lastVisibleLines = Math.max(1, inner.height());
        frame.renderWidget(block, area);

        editState.ensureCursorVisible(inner.width(), inner.height());
        TextArea textArea = TextArea.builder()
                .cursorStyle(Style.EMPTY.reversed())
                .showLineNumbers(true)
                .lineNumberStyle(Style.EMPTY.dim())
                .build();
        textArea.renderWithCursor(inner, frame.buffer(), editState, frame);
    }

    void renderFooter(List<Span> spans) {
        if (editMode) {
            TuiHelper.hint(spans, "Esc", "cancel");
            TuiHelper.hint(spans, "F5", "save");
            TuiHelper.hint(spans, TuiIcons.HINT_SCROLL, "move");
            if (saveMessage != null) {
                spans.add(Span.styled("  " + saveMessage, saveError ? Theme.error() : Theme.success()));
            }
            return;
        }
        if (markdownMode) {
            TuiHelper.hint(spans, "Esc/c", "close");
            TuiHelper.hint(spans, TuiIcons.HINT_SCROLL, "scroll");
            TuiHelper.hint(spans, "Space", "format");
            TuiHelper.hint(spans, "PgUp/PgDn", "page");
            if (isEditable()) {
                TuiHelper.hint(spans, "e", "edit");
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
            TuiHelper.hint(spans, "e", "edit");
        }
        if (quickDocProvider != null) {
            TuiHelper.hint(spans, "i", "quick doc" + (quickDocEnabled ? " [on]" : ""));
        }
        TuiHelper.hint(spans, TuiIcons.HINT_SCROLL, "navigate");
        if (isMarkdownFile || currentRouteId != null) {
            TuiHelper.hint(spans, "Space", "format");
        }
        search.renderSearchHints(spans);
        TuiHelper.hint(spans, "w", "wrap" + (wordWrap ? " [on]" : " [off]"));
        if (onLineSelected != null) {
            TuiHelper.hint(spans, "Enter", "select node");
        }
        if (saveMessage != null) {
            spans.add(Span.styled("  " + saveMessage, saveError ? Theme.error() : Theme.success()));
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
        editMode = false;
        editState.clear();
        // Keep existing saveMessage only when caller restores it after save+reload
        String fileName = filePath.getFileName().toString();
        boolean isMd = fileName.toLowerCase().endsWith(".md");
        try {
            List<String> rawLines = Files.readAllLines(filePath, StandardCharsets.UTF_8);
            int lineNumWidth = String.valueOf(rawLines.size()).length();
            List<String> result = new ArrayList<>();
            List<JsonObject> codeLines = new ArrayList<>();
            for (int i = 0; i < rawLines.size(); i++) {
                int lineNum = i + 1;
                String code = rawLines.get(i);
                result.add(String.format("%" + lineNumWidth + "d  %s", lineNum, code));
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
        saveMessage = null;
        saveError = false;

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
        int lineNumWidth = String.valueOf(maxLineNum).length();
        int matchIdx = -1;
        int idx = 0;
        for (JsonObject codeLine : codeLines) {
            Integer lineNum = codeLine.getInteger("line");
            String code = Jsoner.unescape(objToString(codeLine.get("code")));
            String prefix = lineNum != null
                    ? String.format("%" + lineNumWidth + "d  ", lineNum)
                    : String.format("%" + lineNumWidth + "s  ", "");
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
            List<Span> spans = new ArrayList<>();
            spans.add(Span.styled(" Source [" + info + "] ", ts));
            if (saveMessage != null) {
                spans.add(Span.styled(saveMessage + " ", saveError ? Theme.error() : Theme.success()));
            }
            return Title.from(Line.from(spans));
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

        String prefix = raw.substring(0, prefixEnd);
        String code = raw.substring(prefixEnd);

        Line highlighted = SyntaxHighlighter.highlightLine(code, language);
        boolean isDeprecated = deprecatedLines.contains(lineIndex);

        List<Span> spans = new ArrayList<>();
        Style selBg = focused ? Theme.selectionBg() : Theme.selectionBg().dim();
        if (isSelected) {
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
