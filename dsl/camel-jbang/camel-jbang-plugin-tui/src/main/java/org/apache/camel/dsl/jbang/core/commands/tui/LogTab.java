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
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Layout;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import dev.tamboui.style.Overflow;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.CharWidth;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.MouseEvent;
import dev.tamboui.tui.event.MouseEventKind;
import dev.tamboui.widgets.Clear;
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
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

import static org.apache.camel.dsl.jbang.core.commands.tui.TuiHelper.*;

class LogTab extends AbstractTab {

    private static final int MAX_LOG_LINES = 3000;
    private static final int MOUSE_SCROLL_LINES = 3;
    private static final String[] LOG_LEVELS = { "ERROR", "WARN", "INFO", "DEBUG", "TRACE" };

    private static final Pattern LOG_PATTERN = Pattern.compile(
            "^(\\d{4}-\\d{2}-\\d{2})[T ](\\d{2}:\\d{2}:\\d{2}\\.\\d+)\\S*\\s+"
                                                               + "(TRACE|DEBUG|INFO|WARN|ERROR|FATAL)\\s+"
                                                               + "\\d+\\s+---\\s+"
                                                               + "\\[([^]]*)]\\s+"
                                                               + "(\\S+)\\s*:\\s*(.*)$");

    private final ScrollbarState scrollState = new ScrollbarState();
    private final ListState logLevelListState = new ListState();

    private volatile List<LogEntry> filteredLogEntries = new ArrayList<>();
    private volatile boolean logLoading;
    private volatile boolean loadAllRequested;
    private long logFilePos = -1;
    private long logFileStartPos = -1;
    private long logTotalLinesRead;
    private String logFilePid;
    private final StringBuilder logLineBuffer = new StringBuilder();
    private final List<LogEntry> mutableFilteredEntries = new ArrayList<>();

    private List<LogEntry> cachedLogEntries;
    private int cachedLogHSkip = -1;
    private int cachedLogMaxWidth;
    private List<Line> cachedLogLines = Collections.emptyList();
    private int scroll;
    private long evictedSeen;
    private boolean followMode = true;
    private boolean wordWrap = true;
    private int hScroll;
    private boolean showLogLevelPopup;

    private final SearchHighlighter search = new SearchHighlighter();

    LogTab(MonitorContext ctx) {
        super(ctx);
    }

    @Override
    public void onIntegrationChanged() {
        filteredLogEntries = new ArrayList<>();
        logLoading = true;
        loadAllRequested = false;
        logFilePid = null;
        logFilePos = -1;
        logFileStartPos = -1;
        logTotalLinesRead = 0;
        logLineBuffer.setLength(0);
        mutableFilteredEntries.clear();
        cachedLogEntries = null;
        cachedLogLines = Collections.emptyList();
    }

    @Override
    public boolean handleKeyEvent(KeyEvent ke) {
        if (search.isSearchInputActive()) {
            boolean handled = search.handleKeyEvent(ke);
            if (handled && !search.isSearchInputActive() && search.hasFindTerm()) {
                List<String> plainLines = new ArrayList<>(filteredLogEntries.size());
                for (LogEntry e : filteredLogEntries) {
                    plainLines.add(TuiHelper.stripAnsi(e.raw != null ? e.raw : ""));
                }
                search.buildFindMatches(plainLines);
                int newPos = search.jumpToNearestMatch(scroll);
                if (newPos != scroll) {
                    followMode = false;
                    scroll = newPos;
                }
            }
            return handled;
        }

        if (showLogLevelPopup) {
            if (ke.isUp()) {
                logLevelListState.selectPrevious();
                return true;
            }
            if (ke.isDown()) {
                logLevelListState.selectNext(LOG_LEVELS.length);
                return true;
            }
            if (ke.isConfirm()) {
                Integer sel = logLevelListState.selected();
                if (sel != null && sel >= 0 && sel < LOG_LEVELS.length && ctx.selectedPid != null) {
                    sendLoggerLevelAction(ctx.selectedPid, LOG_LEVELS[sel]);
                }
                showLogLevelPopup = false;
                return true;
            }
            return true;
        }

        if (search.handleKeyEvent(ke)) {
            int matchLine = search.currentMatchLine();
            if (matchLine >= 0) {
                followMode = false;
                scroll = matchLine;
            }
            return true;
        }
        if (ke.isChar('l') && !ctx.isInfraSelected()) {
            showLogLevelPopup = true;
            logLevelListState.select(2);
            return true;
        }
        if (ke.isCharIgnoreCase('f')) {
            followMode = !followMode;
            return true;
        }
        if (ke.isCharIgnoreCase('w')) {
            wordWrap = !wordWrap;
            hScroll = 0;
            return true;
        }
        if (!wordWrap) {
            if (ke.isLeft()) {
                followMode = false;
                hScroll = Math.max(0, hScroll - 4);
                return true;
            }
            if (ke.isRight()) {
                followMode = false;
                hScroll += 4;
                return true;
            }
        }
        if (ke.isPageUp()) {
            pageUp();
            return true;
        }
        if (ke.isPageDown()) {
            pageDown();
            return true;
        }
        if (ke.isHome()) {
            followMode = false;
            scroll = 0;
            hScroll = 0;
            loadAllRequested = true;
            return true;
        }
        if (ke.isEnd()) {
            followMode = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean handleMouseEvent(MouseEvent me, Rect area) {
        if (me.kind() == MouseEventKind.SCROLL_UP) {
            followMode = false;
            scroll = Math.max(0, scroll - MOUSE_SCROLL_LINES);
            return true;
        }
        if (me.kind() == MouseEventKind.SCROLL_DOWN) {
            followMode = false;
            scroll += MOUSE_SCROLL_LINES;
            return true;
        }
        return false;
    }

    @Override
    public boolean handleEscape() {
        if (search.handleEscape()) {
            return true;
        }
        if (showLogLevelPopup) {
            showLogLevelPopup = false;
            return true;
        }
        return false;
    }

    @Override
    public void navigateUp() {
        followMode = false;
        scroll = Math.max(0, scroll - 1);
    }

    @Override
    public void navigateDown() {
        scroll++;
    }

    void pageUp() {
        followMode = false;
        scroll = Math.max(0, scroll - 20);
    }

    void pageDown() {
        scroll += 20;
    }

    boolean isShowLogLevelPopup() {
        return showLogLevelPopup;
    }

    @Override
    public void render(Frame frame, Rect area) {
        IntegrationInfo info = ctx.findSelectedIntegration();
        InfraInfo infraSel = info == null ? ctx.findSelectedInfra() : null;
        if (info == null && infraSel == null) {
            renderNoSelection(frame, area);
            return;
        }

        if (logLoading && filteredLogEntries.isEmpty()) {
            Block loadingBlock = Block.builder()
                    .borderType(BorderType.ROUNDED).borders(Borders.ALL)
                    .title(" Log ")
                    .build();
            frame.renderWidget(loadingBlock, area);
            Rect loadingInner = loadingBlock.inner(area);
            frame.renderWidget(
                    Paragraph.builder().text(Text.from(Line.from(Span.raw("(Loading logs...)"))))
                            .build(),
                    loadingInner);
            return;
        }

        List<LogEntry> entries = filteredLogEntries;
        int contentHeight = entries.size();

        boolean hasNew = !followMode && scroll < contentHeight - Math.max(1, area.height() - 2);
        String logLabel;
        if (infraSel != null) {
            logLabel = " Log [" + infraSel.alias + "]";
        } else if (info != null && info.rootLogLevel != null) {
            logLabel = " Log level:" + info.rootLogLevel;
        } else {
            logLabel = " Log";
        }
        Line titleLine;
        if (hasNew) {
            titleLine = Line.from(
                    Span.raw(logLabel + " "),
                    Span.styled("(*)", Theme.label()),
                    Span.raw(" "));
        } else {
            titleLine = Line.from(Span.raw(logLabel + " "));
        }
        Block block = Block.builder()
                .borderType(BorderType.ROUNDED).borders(Borders.ALL)
                .title(Title.from(titleLine))
                .build();
        frame.renderWidget(block, area);

        Rect inner = block.inner(area);
        int visibleHeight = Math.max(1, inner.height());

        long evictedNow = Math.max(0L, logTotalLinesRead - entries.size());
        if (!followMode && evictedNow > evictedSeen) {
            scroll = (int) Math.max(0, scroll - (evictedNow - evictedSeen));
        }
        evictedSeen = evictedNow;

        if (followMode) {
            scroll = Math.max(0, contentHeight - visibleHeight);
        }
        scroll = Math.min(scroll, Math.max(0, contentHeight - visibleHeight));

        int hSkip = wordWrap ? 0 : hScroll;

        boolean entriesChanged = entries != cachedLogEntries;
        if (entriesChanged || hSkip != cachedLogHSkip) {
            cachedLogEntries = entries;
            cachedLogHSkip = hSkip;
            List<Line> built = new ArrayList<>(entries.size());
            int maxW = 0;
            for (int i = 0; i < entries.size(); i++) {
                LogEntry entry = entries.get(i);
                String raw = entry.raw != null ? entry.raw : "";
                if (!wordWrap) {
                    maxW = Math.max(maxW, CharWidth.of(TuiHelper.stripAnsi(raw)));
                }
                if (raw.indexOf('\u001B') >= 0) {
                    built.add(TuiHelper.ansiToLine(raw, hSkip));
                } else {
                    built.add(colorizePlainLog(raw, entry));
                }
            }
            cachedLogMaxWidth = maxW;
            cachedLogLines = built;
        }

        if (!wordWrap) {
            int visibleWidth = Math.max(1, inner.width() - 1);
            hScroll = Math.min(hScroll, Math.max(0, cachedLogMaxWidth - visibleWidth));
        }

        if (search.hasFindTerm() && entriesChanged) {
            List<String> plainLines = new ArrayList<>(entries.size());
            for (LogEntry e : entries) {
                plainLines.add(TuiHelper.stripAnsi(e.raw != null ? e.raw : ""));
            }
            search.buildFindMatches(plainLines);
        }

        List<Line> allLines = cachedLogLines;
        int start = Math.min(scroll, Math.max(0, allLines.size() - visibleHeight));
        List<Line> visibleLines = allLines.subList(start, Math.min(allLines.size(), start + visibleHeight));

        int currentMatchLine = search.currentMatchLine();
        if (currentMatchLine >= 0 || search.hasFindTerm() || search.hasHighlightTerm()) {
            List<Line> highlighted = new ArrayList<>(visibleLines.size());
            for (int i = 0; i < visibleLines.size(); i++) {
                highlighted.add(search.applyHighlights(visibleLines.get(i), start + i, currentMatchLine));
            }
            visibleLines = highlighted;
        }

        List<Rect> hChunks = Layout.horizontal()
                .constraints(Constraint.fill(), Constraint.length(1))
                .split(inner);

        Overflow overflow = wordWrap ? Overflow.WRAP_WORD : Overflow.CLIP;
        Paragraph para = Paragraph.builder()
                .text(Text.from(visibleLines))
                .overflow(overflow)
                .build();
        frame.renderWidget(para, hChunks.get(0));

        if (contentHeight > visibleHeight) {
            scrollState.contentLength(contentHeight);
            scrollState.viewportContentLength(visibleHeight);
            scrollState.position(scroll);
            frame.renderStatefulWidget(Scrollbar.builder().build(), hChunks.get(1), scrollState);
        }

        if (showLogLevelPopup) {
            renderLogLevelPopup(frame, area);
        }
    }

    @Override
    public void renderFooter(List<Span> spans) {
        search.renderFooterHints(spans);
        if (search.isSearchInputActive()) {
            return;
        }
        if (showLogLevelPopup) {
            hint(spans, TuiIcons.HINT_SCROLL, "navigate");
            hint(spans, "Enter", "set level");
            hintLast(spans, "Esc", "cancel");
            return;
        }

        if (search.hasFindTerm()) {
            search.renderFindStatus(spans);
        } else {
            hint(spans, "Esc", "back");
        }
        hint(spans, TuiIcons.HINT_SCROLL, "scroll");
        search.renderSearchHints(spans);
        hint(spans, "w", "wrap" + (wordWrap ? " [on]" : " [off]"));
        if (!ctx.isInfraSelected()) {
            hint(spans, "l", "level");
        }
        hint(spans, "f", "follow" + (followMode ? " [on]" : " [off]"));
    }

    private void renderLogLevelPopup(Frame frame, Rect area) {
        int popupW = 24;
        int popupH = 7;
        int x = area.left() + Math.max(0, (area.width() - popupW) / 2);
        int y = area.top() + Math.max(0, (area.height() - popupH) / 2);
        Rect popup = new Rect(x, y, Math.min(popupW, area.width()), Math.min(popupH, area.height()));

        frame.renderWidget(Clear.INSTANCE, popup);

        ListWidget list = ListWidget.builder()
                .items(
                        ListItem.from("  ERROR  ").style(Style.EMPTY.fg(Color.LIGHT_RED)),
                        ListItem.from("  WARN   ").style(Theme.warning()),
                        ListItem.from("  INFO   ").style(Style.EMPTY),
                        ListItem.from("  DEBUG  ").style(Style.EMPTY.fg(Color.CYAN)),
                        ListItem.from("  TRACE  ").style(Style.EMPTY.dim()))
                .highlightStyle(Theme.selectionBg())
                .highlightSymbol("")
                .scrollMode(ScrollMode.NONE)
                .block(Block.builder()
                        .borderType(BorderType.ROUNDED).borders(Borders.ALL)
                        .title(" Set Log Level ")
                        .build())
                .build();

        frame.renderStatefulWidget(list, popup, logLevelListState);
    }

    void setLogLevel(String level) {
        if (ctx.selectedPid != null) {
            sendLoggerLevelAction(ctx.selectedPid, level);
        }
    }

    private void sendLoggerLevelAction(String pid, String level) {
        JsonObject root = new JsonObject();
        root.put("action", "logger");
        root.put("command", "set-logging-level");
        root.put("logger-name", "root");
        root.put("logging-level", level);
        Path actionFile = ctx.getActionFile(pid);
        org.apache.camel.dsl.jbang.core.common.PathUtils.writeTextSafely(root.toJson(), actionFile);
    }

    boolean isSearchInputActive() {
        return search.isSearchInputActive();
    }

    void handlePaste(String text) {
        search.handlePaste(text);
    }

    void refreshFromFile(String pid, String fileName) {
        if (!pid.equals(logFilePid)) {
            mutableFilteredEntries.clear();
            logFilePos = -1;
            logTotalLinesRead = 0;
            logLineBuffer.setLength(0);
            logLoading = true;
        }
        boolean changed = false;
        boolean loadAll = loadAllRequested;
        if (logFileStartPos > 0
                && (loadAll || (!followMode && scroll == 0))) {
            loadAllRequested = false;
            List<String> olderLines = new ArrayList<>();
            readOlderLogLines(fileName, loadAll, olderLines);
            if (!olderLines.isEmpty()) {
                changed = true;
                List<LogEntry> olderEntries = new ArrayList<>();
                for (String line : olderLines) {
                    olderEntries.add(parseLogLine(line));
                }
                mutableFilteredEntries.addAll(0, olderEntries);
                logTotalLinesRead += olderLines.size();
                scroll = olderEntries.size();
            }
        }
        List<String> newRawLines = new ArrayList<>();
        readNewLogLinesFromFile(pid, fileName, newRawLines);
        changed |= !newRawLines.isEmpty();
        if (changed) {
            logTotalLinesRead += newRawLines.size();
            for (String line : newRawLines) {
                mutableFilteredEntries.add(parseLogLine(line));
            }
            if (mutableFilteredEntries.size() > MAX_LOG_LINES) {
                mutableFilteredEntries.subList(0, mutableFilteredEntries.size() - MAX_LOG_LINES)
                        .clear();
            }
        }
        if (changed || logLoading) {
            filteredLogEntries = new ArrayList<>(mutableFilteredEntries);
        }
        logLoading = false;
    }

    void readNewLogLines(String pid, List<String> newLines) {
        readNewLogLinesFromFile(pid, pid + ".log", newLines);
    }

    void readNewLogLinesFromFile(String pid, String fileName, List<String> newLines) {
        Path logFile = CommandLineHelper.getCamelDir().resolve(fileName);
        if (!Files.exists(logFile)) {
            logFilePid = pid;
            logFilePos = -1;
            return;
        }
        logFilePid = pid;
        try (RandomAccessFile raf = new RandomAccessFile(logFile.toFile(), "r")) {
            long length = raf.length();
            if (logFilePos < 0 || logFilePos > length) {
                // Initial load: only read last 8KB for fast first render
                logFilePos = Math.max(0, length - 8 * 1024);
                logFileStartPos = logFilePos;
                logLineBuffer.setLength(0);
            }
            if (logFilePos >= length) {
                return;
            }
            raf.seek(logFilePos);
            // Cap per-tick read to 64KB to avoid blocking the refresh cycle
            byte[] buf = new byte[(int) Math.min(length - logFilePos, 64 * 1024)];
            raf.readFully(buf);
            logFilePos += buf.length;

            String chunk = logLineBuffer + new String(buf, StandardCharsets.UTF_8);
            logLineBuffer.setLength(0);
            int start = 0;
            int end;
            while ((end = chunk.indexOf('\n', start)) >= 0) {
                String line = TuiHelper.fixControlChars(chunk.substring(start, end));
                if (!line.isEmpty()) {
                    newLines.add(line);
                }
                start = end + 1;
            }
            if (start < chunk.length()) {
                logLineBuffer.append(chunk, start, chunk.length());
            }
        } catch (IOException e) {
            // ignore
        }
    }

    void readOlderLogLines(String fileName, boolean loadAll, List<String> olderLines) {
        if (logFilePid == null || logFileStartPos <= 0) {
            return;
        }
        Path logFile = CommandLineHelper.getCamelDir().resolve(fileName);
        if (!Files.exists(logFile)) {
            return;
        }
        try (RandomAccessFile raf = new RandomAccessFile(logFile.toFile(), "r")) {
            long readEnd = logFileStartPos;
            // Load all: read from start of file (cap at 2MB), otherwise 64KB chunk
            long readStart;
            if (loadAll) {
                readStart = Math.max(0, readEnd - 2 * 1024 * 1024);
            } else {
                readStart = Math.max(0, readEnd - 64 * 1024);
            }
            if (readStart >= readEnd) {
                return;
            }
            raf.seek(readStart);
            byte[] buf = new byte[(int) (readEnd - readStart)];
            raf.readFully(buf);
            logFileStartPos = readStart;

            String chunk = new String(buf, StandardCharsets.UTF_8);
            // If we didn't read from the start of the file, skip the first partial line
            int start = 0;
            if (readStart > 0) {
                int firstNewline = chunk.indexOf('\n');
                if (firstNewline >= 0) {
                    start = firstNewline + 1;
                }
            }
            int end;
            while ((end = chunk.indexOf('\n', start)) >= 0) {
                String line = TuiHelper.fixControlChars(chunk.substring(start, end));
                if (!line.isEmpty()) {
                    olderLines.add(line);
                }
                start = end + 1;
            }
        } catch (IOException e) {
            // ignore
        }
    }

    private static final Style DIM = Style.EMPTY.dim();
    private static final Style CYAN = Style.EMPTY.fg(Color.CYAN);
    private static final Style MAGENTA = Style.EMPTY.fg(Color.MAGENTA);

    private static final Pattern PID_PATTERN = Pattern.compile(
            "^(\\d{4}-\\d{2}-\\d{2})[T ](\\d{2}:\\d{2}:\\d{2}\\.\\d+)\\S*\\s+"
                                                               + "(TRACE|DEBUG|INFO|WARN|ERROR|FATAL)\\s+"
                                                               + "(\\d+)\\s+---\\s+"
                                                               + "\\[([^]]*)]\\s+"
                                                               + "(\\S+)\\s*:\\s*(.*)$");

    private static Line colorizePlainLog(String raw, LogEntry entry) {
        String plain = TuiHelper.stripAnsi(raw);
        Matcher m = PID_PATTERN.matcher(plain);
        if (!m.matches()) {
            return Line.from(Span.raw(plain));
        }
        String date = m.group(1) + " " + m.group(2);
        String level = m.group(3);
        String pid = m.group(4);
        String thread = "[" + m.group(5) + "]";
        String logger = m.group(6);
        String message = m.group(7);
        Style levelStyle = switch (level) {
            case "ERROR", "FATAL" -> Style.EMPTY.fg(Color.RED);
            case "WARN" -> Theme.warning();
            case "INFO" -> Style.EMPTY.fg(Color.GREEN);
            case "DEBUG" -> Style.EMPTY.fg(Color.CYAN);
            case "TRACE" -> Style.EMPTY.dim();
            default -> Style.EMPTY;
        };
        return Line.from(
                Span.styled(date, DIM),
                Span.raw(" "),
                Span.styled(String.format("%5s", level), levelStyle),
                Span.raw(" "),
                Span.styled(pid, MAGENTA),
                Span.styled(" --- ", DIM),
                Span.styled(thread, DIM),
                Span.raw(" "),
                Span.styled(logger, CYAN),
                Span.styled(" :", DIM),
                Span.raw(" " + message));
    }

    static LogEntry parseLogLine(String line) {
        LogEntry entry = new LogEntry();
        entry.raw = line;
        try {
            String plain = TuiHelper.stripAnsi(line);
            Matcher m = LOG_PATTERN.matcher(plain);
            if (m.matches()) {
                entry.time = m.group(2);
                if (entry.time.length() > 12) {
                    entry.time = entry.time.substring(0, 12);
                }
                entry.level = m.group(3);
                entry.logger = m.group(5);
                int lastDot = entry.logger.lastIndexOf('.');
                if (lastDot > 0) {
                    entry.logger = entry.logger.substring(lastDot + 1);
                }
                entry.message = m.group(6);
            } else {
                entry.time = "";
                entry.level = "INFO";
                entry.message = plain;
            }
        } catch (Exception e) {
            entry.time = "";
            entry.level = "INFO";
            entry.message = TuiHelper.stripAnsi(line);
        }
        return entry;
    }

    @Override
    public String description() {
        return "Live application log with ANSI color support and filtering";
    }

    @Override
    public String getHelpText() {
        return """
                # Log

                The Log tab shows live log output from the running integration, similar
                to `tail -f` on a log file. Log entries are color-coded by level for
                quick visual scanning.

                ## Log Levels

                Each log message has a severity level:

                - **ERROR** (red) — Something went wrong that needs attention. Check the message and stack trace. Errors often correspond to entries on the Errors tab
                - **WARN** (yellow) — Potential issues that may need attention but are not immediately critical. Examples: deprecated features, connection retries, configuration warnings
                - **INFO** (green) — Normal operational messages: routes starting, endpoints connecting, messages processed. This is the default level
                - **DEBUG** (blue) — Detailed diagnostic information for troubleshooting. Shows internal decision-making, message transformations, and routing logic
                - **TRACE** (dim) — Very fine-grained output showing every step of processing. Generates a lot of output — use only when debugging a specific problem

                ## Example Screen

                ```
                 10:29:32 INFO  [main] CamelContext started in 1s234ms
                 10:29:33 INFO  [Camel (camel-demo) thread #2] HIGH: Hello from Camel at 10:29:33
                 10:29:34 INFO  [Camel (camel-demo) thread #2] LOW: Hello from Camel at 10:29:34
                 10:29:35 WARN  [Camel (camel-demo) thread #3] Connection retry 1 of 3
                 10:29:38 ERROR [Camel (camel-demo) thread #3] Connection refused: localhost:9092
                ```

                ## Log Level Filter

                Press `l` to open the log level picker. Selecting a level filters the
                display to show only messages at that level and above:

                - Select **ERROR** — shows only ERROR messages
                - Select **WARN** — shows WARN and ERROR
                - Select **INFO** — shows INFO, WARN, and ERROR (default)
                - Select **DEBUG** — shows everything except TRACE
                - Select **TRACE** — shows all messages

                Filtering is useful when the log is noisy with INFO messages and you
                want to focus on warnings and errors.

                ## Find and Highlight

                **Find** (`/`) — search for text in the log. Type a search term and
                press Enter to jump to the first match. Use `n` to go to the next
                match and `N` for the previous match. The current match is shown
                with a green background, other matches with yellow. Press `Esc`
                to clear the search.

                **Highlight** (`h`) — persistently highlight all occurrences of a
                word in the log. Type a word and press Enter — all occurrences
                are highlighted with a yellow background while the log continues
                scrolling in follow mode. Press `h` again and submit an empty
                term to clear the highlight. Both find and highlight can be
                active at the same time.

                Both find and highlight are case-insensitive.

                ## Thread Names

                The thread name in square brackets (e.g., `[Camel (camel-demo) thread #2]`)
                tells you which thread produced the log message. This helps correlate
                log entries with specific routes when multiple routes run concurrently.

                ## Keys

                - `Up/Down` — scroll log
                - `PgUp/PgDn` — scroll by page
                - `Home/End` — jump to beginning/end of log
                - `/` — find (search for text)
                - `n` — next match
                - `N` — previous match
                - `h` — highlight a word
                - `l` — change log level filter
                - `f` — toggle follow mode
                - `w` — toggle word wrap
                - `Esc` — clear find / back
                """;
    }

    JsonObject getLogDataAsJson(int limit, String filter, String level) {
        List<LogEntry> entries = filteredLogEntries;
        if (entries == null || entries.isEmpty()) {
            JsonObject result = new JsonObject();
            result.put("lines", new JsonArray());
            result.put("totalLines", 0);
            result.put("returnedLines", 0);
            return result;
        }

        List<LogEntry> filtered = new ArrayList<>();
        for (int i = entries.size() - 1; i >= 0 && filtered.size() < limit; i--) {
            LogEntry e = entries.get(i);
            if (level != null && !level.isBlank() && !level.equalsIgnoreCase(e.level)) {
                continue;
            }
            if (filter != null && !filter.isBlank()
                    && !e.message.toLowerCase().contains(filter.toLowerCase())) {
                continue;
            }
            filtered.add(e);
        }

        JsonObject result = new JsonObject();
        JsonArray rows = new JsonArray();
        for (LogEntry e : filtered) {
            JsonObject row = new JsonObject();
            row.put("time", e.time);
            row.put("level", e.level);
            if (e.logger != null) {
                row.put("logger", e.logger);
            }
            row.put("message", e.message);
            rows.add(row);
        }
        result.put("lines", rows);
        result.put("totalLines", entries.size());
        result.put("returnedLines", filtered.size());
        return result;
    }
}
