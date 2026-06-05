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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Layout;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import dev.tamboui.style.Overflow;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.input.TextInputState;
import dev.tamboui.widgets.paragraph.Paragraph;
import dev.tamboui.widgets.scrollbar.Scrollbar;
import dev.tamboui.widgets.scrollbar.ScrollbarState;
import org.apache.camel.dsl.jbang.core.common.PathUtils;
import org.apache.camel.support.LoggerHelper;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

import static org.apache.camel.dsl.jbang.core.commands.tui.MonitorContext.pollJsonResponse;

/**
 * Reusable source code viewer with syntax highlighting, scrolling, and line-number display. Can be used by any tab that
 * needs to show route source code.
 */
class SourceViewer {

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

    // Find mode
    private boolean findInputActive;
    private boolean highlightInputActive;
    private TextInputState searchInputState = new TextInputState("");
    private String findTerm;
    private Pattern findPattern;
    private int findMatchIndex = -1;
    private List<Integer> findMatches = Collections.emptyList();

    // Highlight mode
    private String highlightTerm;
    private Pattern highlightPattern;

    private static final Style HIGHLIGHT_STYLE = Style.EMPTY.fg(Color.BLACK).bg(Color.YELLOW);
    private static final Style FIND_MATCH_STYLE = Style.EMPTY.fg(Color.BLACK).bg(Color.YELLOW);
    private static final Style FIND_CURRENT_STYLE = Style.EMPTY.fg(Color.BLACK).bg(Color.LIGHT_GREEN);

    private record CachedSource(
            List<String> lines, List<JsonObject> codeData,
            String sourceLocation, SyntaxHighlighter.Language language) {
    }

    boolean isVisible() {
        return visible;
    }

    void hide() {
        visible = false;
        onLineSelected = null;
    }

    void reset() {
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
        findInputActive = false;
        highlightInputActive = false;
        findTerm = null;
        findPattern = null;
        findMatchIndex = -1;
        findMatches = Collections.emptyList();
        highlightTerm = null;
        highlightPattern = null;
    }

    void setOnLineSelected(IntConsumer callback) {
        this.onLineSelected = callback;
    }

    boolean handleKeyEvent(KeyEvent ke) {
        if (!visible) {
            return false;
        }
        if (findInputActive || highlightInputActive) {
            return handleSearchInput(ke);
        }
        if (ke.isCancel()) {
            if (findTerm != null) {
                findTerm = null;
                findPattern = null;
                findMatches = Collections.emptyList();
                findMatchIndex = -1;
                return true;
            }
            visible = false;
            onLineSelected = null;
            return true;
        }
        if (ke.isChar('c')) {
            visible = false;
            onLineSelected = null;
            return true;
        }
        if (ke.isChar('/')) {
            findInputActive = true;
            searchInputState = new TextInputState("");
            return true;
        }
        if (ke.isChar('h')) {
            highlightInputActive = true;
            searchInputState = new TextInputState("");
            return true;
        }
        if (ke.isChar('n') && findTerm != null) {
            navigateToNextMatch();
            return true;
        }
        if (ke.isChar('N') && findTerm != null) {
            navigateToPrevMatch();
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
            scrollX = Math.max(0, scrollX - 1);
        } else if (!wordWrap && ke.isRight()) {
            scrollX++;
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

    private boolean handleSearchInput(KeyEvent ke) {
        if (ke.isKey(KeyCode.ESCAPE)) {
            findInputActive = false;
            highlightInputActive = false;
            return true;
        }
        if (ke.isConfirm()) {
            String text = searchInputState.text().trim();
            if (findInputActive) {
                if (text.isEmpty()) {
                    findTerm = null;
                    findPattern = null;
                    findMatches = Collections.emptyList();
                    findMatchIndex = -1;
                } else {
                    findTerm = text;
                    findPattern = Pattern.compile(Pattern.quote(text), Pattern.CASE_INSENSITIVE);
                    buildFindMatches();
                    jumpToNearestMatch();
                }
                findInputActive = false;
            } else if (highlightInputActive) {
                if (text.isEmpty()) {
                    highlightTerm = null;
                    highlightPattern = null;
                } else {
                    highlightTerm = text;
                    highlightPattern = Pattern.compile(Pattern.quote(text), Pattern.CASE_INSENSITIVE);
                }
                highlightInputActive = false;
            }
            return true;
        }
        FormHelper.handleTextInput(ke, searchInputState);
        return true;
    }

    boolean isSearchInputActive() {
        return findInputActive || highlightInputActive;
    }

    void handlePaste(String text) {
        if (findInputActive || highlightInputActive) {
            FormHelper.handlePaste(text, searchInputState);
        }
    }

    void render(Frame frame, Rect area) {
        Block block = Block.builder()
                .borderType(BorderType.ROUNDED)
                .title(" Source [" + (title != null ? title : "") + "] ")
                .build();
        Rect inner = block.inner(area);
        frame.renderWidget(block, area);

        if (lines.isEmpty()) {
            return;
        }

        int visibleLines = inner.height();
        lastVisibleLines = visibleLines;
        int maxScroll = Math.max(0, lines.size() - visibleLines);

        // On initial load, position selected line at 2/3 of viewport
        if (pendingScroll && selectedLine >= 0) {
            int twoThirds = visibleLines * 2 / 3;
            scrollY = Math.max(0, selectedLine - twoThirds);
            pendingScroll = false;
        }

        // Auto-scroll to keep selected line visible
        if (selectedLine >= 0) {
            if (selectedLine < scrollY) {
                scrollY = selectedLine;
            } else if (selectedLine >= scrollY + visibleLines) {
                scrollY = selectedLine - visibleLines + 1;
            }
        }
        scrollY = Math.min(scrollY, maxScroll);

        int hSkip = wordWrap ? 0 : scrollX;
        if (!wordWrap) {
            int cursorWidth = 3;
            int maxLineWidth = lines.stream().mapToInt(String::length).max().orElse(0) + cursorWidth;
            int maxHScroll = Math.max(0, maxLineWidth - inner.width());
            scrollX = Math.min(scrollX, maxHScroll);
        }

        int currentMatchLine = findMatchIndex >= 0 && findMatchIndex < findMatches.size()
                ? findMatches.get(findMatchIndex) : -1;

        int end = Math.min(scrollY + visibleLines, lines.size());
        List<Line> visible = new ArrayList<>();
        for (int i = scrollY; i < end; i++) {
            String raw = lines.get(i);
            boolean isSelected = (i == selectedLine);
            Line line = highlightSourceLine(raw, hSkip, isSelected, inner.width());
            if (highlightPattern != null || findPattern != null) {
                line = applySearchHighlights(line, i, currentMatchLine);
            }
            visible.add(line);
        }

        List<Rect> hChunks = Layout.horizontal()
                .constraints(Constraint.fill(), Constraint.length(1))
                .split(inner);

        Overflow overflow = wordWrap ? Overflow.WRAP_WORD : Overflow.CLIP;
        frame.renderWidget(Paragraph.builder().text(Text.from(visible)).overflow(overflow).build(), hChunks.get(0));

        if (lines.size() > visibleLines) {
            vScrollState.contentLength(lines.size()).viewportContentLength(visibleLines).position(scrollY);
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

    void renderFooter(List<Span> spans) {
        if (findInputActive) {
            spans.add(Span.styled(" /", MonitorContext.HINT_KEY_STYLE));
            spans.add(Span.raw(searchInputState.text() + "█  "));
            MonitorContext.hint(spans, "Enter", "search");
            MonitorContext.hintLast(spans, "Esc", "cancel");
            return;
        }
        if (highlightInputActive) {
            spans.add(Span.styled(" h:", MonitorContext.HINT_KEY_STYLE));
            spans.add(Span.raw(searchInputState.text() + "█  "));
            MonitorContext.hint(spans, "Enter", "set");
            MonitorContext.hintLast(spans, "Esc", "cancel");
            return;
        }
        if (findTerm != null) {
            MonitorContext.hint(spans, "Esc", "clear find");
            MonitorContext.hint(spans, "n", "next");
            MonitorContext.hint(spans, "N", "prev");
            String pos = findMatches.isEmpty()
                    ? "0/0"
                    : (findMatchIndex + 1) + "/" + findMatches.size();
            spans.add(Span.styled("  /", MonitorContext.HINT_KEY_STYLE));
            spans.add(Span.raw("\"" + findTerm + "\" [" + pos + "]  "));
        } else {
            MonitorContext.hint(spans, "Esc/c", "close");
        }
        MonitorContext.hint(spans, "↑↓", "navigate");
        MonitorContext.hint(spans, "/", "find");
        MonitorContext.hint(spans, "h", "highlight" + (highlightTerm != null ? " [" + highlightTerm + "]" : ""));
        MonitorContext.hint(spans, "w", "wrap" + (wordWrap ? " [on]" : " [off]"));
        if (!wordWrap) {
            MonitorContext.hint(spans, "←→", "horizontal");
        }
        MonitorContext.hint(spans, "PgUp/PgDn", "page");
        if (onLineSelected != null) {
            MonitorContext.hint(spans, "Enter", "select node");
        }
    }

    /**
     * Load source for a route, scrolling to the given source line number.
     */
    void loadFile(Path filePath) {
        String fileName = filePath.getFileName().toString();
        try {
            List<String> rawLines = java.nio.file.Files.readAllLines(filePath, java.nio.charset.StandardCharsets.UTF_8);
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
        } catch (java.io.IOException e) {
            title = fileName;
            lines = List.of("(Failed to read file: " + e.getMessage() + ")");
            codeData = Collections.emptyList();
            visible = true;
        }
    }

    /**
     * Load source for a route, scrolling to the given source line number.
     */
    void loadSource(MonitorContext ctx, String routeId, int targetLine) {
        loadSource(ctx, routeId, targetLine, null);
    }

    void loadSource(MonitorContext ctx, String routeId, int targetLine, String sourceLocationHint) {
        if (ctx.selectedPid == null || ctx.runner == null) {
            return;
        }

        String pid = ctx.selectedPid;
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

        ctx.runner.scheduler().execute(() -> {
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
    }

    private void loadInBackground(MonitorContext ctx, String pid, String routeId, int targetLine) {
        Path outputFile = ctx.getOutputFile(pid);
        PathUtils.deleteFile(outputFile);

        JsonObject root = new JsonObject();
        root.put("action", "source");
        root.put("filter", routeId);

        Path actionFile = ctx.getActionFile(pid);
        PathUtils.writeTextSafely(root.toJson(), actionFile);

        JsonObject jo = pollJsonResponse(outputFile, 5000);
        PathUtils.deleteFile(outputFile);

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
        });
    }

    private Line highlightSourceLine(String raw, int hSkip, boolean isSelected, int viewportWidth) {
        int prefixEnd = 0;
        while (prefixEnd < raw.length() && (raw.charAt(prefixEnd) == ' ' || Character.isDigit(raw.charAt(prefixEnd)))) {
            prefixEnd++;
        }

        String prefix = raw.substring(0, prefixEnd);
        String code = raw.substring(prefixEnd);

        Line highlighted = SyntaxHighlighter.highlightLine(code, language);

        List<Span> spans = new ArrayList<>();
        Style selBg = Style.EMPTY.bg(Color.rgb(30, 45, 70));
        if (isSelected) {
            spans.add(Span.styled(">> ", Style.EMPTY.fg(Color.YELLOW).bold()));
            if (!prefix.isEmpty()) {
                spans.add(Span.styled(prefix, Style.EMPTY.fg(Color.YELLOW).bold().patch(selBg)));
            }
            for (Span s : highlighted.spans()) {
                spans.add(Span.styled(s.content(), s.style().patch(selBg)));
            }
        } else {
            spans.add(Span.raw("   "));
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

    private void buildFindMatches() {
        List<Integer> matches = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            if (findPattern.matcher(lines.get(i)).find()) {
                matches.add(i);
            }
        }
        findMatches = matches;
    }

    private void jumpToNearestMatch() {
        if (findMatches.isEmpty()) {
            findMatchIndex = -1;
            return;
        }
        for (int i = 0; i < findMatches.size(); i++) {
            if (findMatches.get(i) >= selectedLine) {
                findMatchIndex = i;
                scrollToMatch();
                return;
            }
        }
        findMatchIndex = 0;
        scrollToMatch();
    }

    private void navigateToNextMatch() {
        if (findMatches.isEmpty()) {
            return;
        }
        findMatchIndex = (findMatchIndex + 1) % findMatches.size();
        scrollToMatch();
    }

    private void navigateToPrevMatch() {
        if (findMatches.isEmpty()) {
            return;
        }
        findMatchIndex = findMatchIndex <= 0 ? findMatches.size() - 1 : findMatchIndex - 1;
        scrollToMatch();
    }

    private void scrollToMatch() {
        if (findMatchIndex >= 0 && findMatchIndex < findMatches.size()) {
            selectedLine = findMatches.get(findMatchIndex);
        }
    }

    private Line applySearchHighlights(Line line, int lineIndex, int currentMatchLine) {
        String fullText = line.rawContent();
        if (fullText.isEmpty()) {
            return line;
        }

        List<int[]> ranges = new ArrayList<>();
        List<Style> rangeStyles = new ArrayList<>();
        if (highlightPattern != null) {
            Matcher m = highlightPattern.matcher(fullText);
            while (m.find()) {
                ranges.add(new int[] { m.start(), m.end() });
                rangeStyles.add(HIGHLIGHT_STYLE);
            }
        }
        if (findPattern != null) {
            boolean isCurrentLine = lineIndex == currentMatchLine;
            Matcher m = findPattern.matcher(fullText);
            while (m.find()) {
                ranges.add(new int[] { m.start(), m.end() });
                rangeStyles.add(isCurrentLine ? FIND_CURRENT_STYLE : FIND_MATCH_STYLE);
            }
        }
        if (ranges.isEmpty()) {
            return line;
        }

        List<Span> original = line.spans();
        List<Span> result = new ArrayList<>();
        int charPos = 0;
        for (Span span : original) {
            String content = span.content();
            Style baseStyle = span.style();
            int spanStart = charPos;
            int spanEnd = charPos + content.length();
            int cursor = 0;
            for (int r = 0; r < ranges.size(); r++) {
                int matchStart = ranges.get(r)[0];
                int matchEnd = ranges.get(r)[1];
                if (matchEnd <= spanStart || matchStart >= spanEnd) {
                    continue;
                }
                int localStart = Math.max(0, matchStart - spanStart);
                int localEnd = Math.min(content.length(), matchEnd - spanStart);
                if (localStart > cursor) {
                    result.add(Span.styled(content.substring(cursor, localStart), baseStyle));
                }
                result.add(Span.styled(content.substring(localStart, localEnd), rangeStyles.get(r)));
                cursor = localEnd;
            }
            if (cursor < content.length()) {
                result.add(Span.styled(content.substring(cursor), baseStyle));
            }
            charPos = spanEnd;
        }
        return Line.from(result);
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

    private static String objToString(Object o) {
        return o != null ? o.toString() : "";
    }
}
