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
import dev.tamboui.widgets.Clear;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.list.ListItem;
import dev.tamboui.widgets.list.ListState;
import dev.tamboui.widgets.list.ListWidget;
import dev.tamboui.widgets.list.ScrollMode;
import dev.tamboui.widgets.paragraph.Paragraph;
import dev.tamboui.widgets.scrollbar.Scrollbar;
import dev.tamboui.widgets.scrollbar.ScrollbarState;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.util.json.JsonObject;

import static org.apache.camel.dsl.jbang.core.commands.tui.MonitorContext.*;

class LogTab implements MonitorTab {

    private static final String[] LOG_LEVELS = { "ERROR", "WARN", "INFO", "DEBUG", "TRACE" };

    private static final Pattern LOG_PATTERN = Pattern.compile(
            "^(\\d{4}-\\d{2}-\\d{2})[T ](\\d{2}:\\d{2}:\\d{2}\\.\\d+)\\S*\\s+"
                                                               + "(TRACE|DEBUG|INFO|WARN|ERROR|FATAL)\\s+"
                                                               + "\\d+\\s+---\\s+"
                                                               + "\\[([^]]*)]\\s+"
                                                               + "(\\S+)\\s*:\\s*(.*)$");

    private final MonitorContext ctx;
    private final ScrollbarState scrollState = new ScrollbarState();
    private final ListState logLevelListState = new ListState();

    volatile List<LogEntry> filteredLogEntries = new ArrayList<>();
    long logFilePos = -1;
    long logTotalLinesRead;
    String logFilePid;
    final StringBuilder logLineBuffer = new StringBuilder();
    final List<LogEntry> mutableFilteredEntries = new ArrayList<>();

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

    LogTab(MonitorContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public boolean handleKeyEvent(KeyEvent ke) {
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
            return true;
        }
        if (ke.isEnd()) {
            followMode = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean handleEscape() {
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

        List<LogEntry> entries = filteredLogEntries;
        int contentHeight = entries.size();

        long totalRead = logTotalLinesRead;
        String chunkSuffix = totalRead > entries.size()
                ? " #" + (totalRead - entries.size() + 1) + "-" + totalRead
                : "";
        String logTitle;
        if (infraSel != null) {
            logTitle = " Log [" + infraSel.alias + "]" + chunkSuffix + " ";
        } else if (info != null && info.rootLogLevel != null) {
            logTitle = " Log level:" + info.rootLogLevel + chunkSuffix + " ";
        } else {
            logTitle = " Log" + chunkSuffix + " ";
        }
        Block block = Block.builder()
                .borderType(BorderType.ROUNDED)
                .title(logTitle)
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

        if (entries != cachedLogEntries || hSkip != cachedLogHSkip) {
            cachedLogEntries = entries;
            cachedLogHSkip = hSkip;
            List<Line> built = new ArrayList<>(entries.size());
            int maxW = 0;
            for (LogEntry entry : entries) {
                String raw = entry.raw != null ? entry.raw : "";
                if (!wordWrap) {
                    maxW = Math.max(maxW, CharWidth.of(TuiHelper.stripAnsi(raw)));
                }
                built.add(TuiHelper.ansiToLine(raw, hSkip));
            }
            cachedLogMaxWidth = maxW;
            cachedLogLines = built;
        }

        if (!wordWrap) {
            int visibleWidth = Math.max(1, inner.width() - 1);
            hScroll = Math.min(hScroll, Math.max(0, cachedLogMaxWidth - visibleWidth));
        }

        List<Line> allLines = cachedLogLines;
        int start = Math.min(scroll, Math.max(0, allLines.size() - visibleHeight));
        List<Line> visibleLines = allLines.subList(start, Math.min(allLines.size(), start + visibleHeight));

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
        if (showLogLevelPopup) {
            hint(spans, "↑↓", "navigate");
            hint(spans, "Enter", "set level");
            hintLast(spans, "Esc", "cancel");
            return;
        }
        hint(spans, "Esc", "back");
        hint(spans, "↑↓", "scroll");
        hint(spans, "PgUp/PgDn", "page");
        hint(spans, "Home/End", "top/end");
        hint(spans, "w", "wrap" + (wordWrap ? " [on]" : " [off]"));
        if (!wordWrap) {
            hint(spans, "←→", "h-scroll");
        }
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
                        ListItem.from("  WARN   ").style(Style.EMPTY.fg(Color.YELLOW)),
                        ListItem.from("  INFO   ").style(Style.EMPTY),
                        ListItem.from("  DEBUG  ").style(Style.EMPTY.fg(Color.CYAN)),
                        ListItem.from("  TRACE  ").style(Style.EMPTY.dim()))
                .highlightStyle(Style.EMPTY.fg(Color.WHITE).bold().onBlue())
                .highlightSymbol("")
                .scrollMode(ScrollMode.NONE)
                .block(Block.builder()
                        .borderType(BorderType.ROUNDED)
                        .title(" Set Log Level ")
                        .build())
                .build();

        frame.renderStatefulWidget(list, popup, logLevelListState);
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
                logFilePos = Math.max(0, length - 1024 * 1024);
                logLineBuffer.setLength(0);
            }
            if (logFilePos >= length) {
                return;
            }
            raf.seek(logFilePos);
            byte[] buf = new byte[(int) Math.min(length - logFilePos, 4 * 1024 * 1024)];
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
    public String getHelpText() {
        return """
                # Log

                The Log tab shows live log output from the running integration.
                Log entries are color-coded by level:

                - **ERROR** (red): Something went wrong — check the message for details
                - **WARN** (yellow): Potential issues that may need attention
                - **INFO** (green): Normal operational messages
                - **DEBUG** (blue): Detailed diagnostic information
                - **TRACE** (dim): Very fine-grained debugging output

                ## Log Level Filter

                Press `l` to open the log level picker. Selecting a level filters the
                display to show only messages at that level and above. For example,
                selecting WARN shows only WARN and ERROR messages.

                ## Keys

                - `Up/Down` — scroll log
                - `PgUp/PgDn` — scroll log by page
                - `l` — change log level filter
                - `Esc` — back
                """;
    }
}
