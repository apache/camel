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
import java.util.List;

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
import dev.tamboui.tui.TuiRunner;
import dev.tamboui.tui.event.Event;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.TickEvent;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.paragraph.Paragraph;
import org.apache.camel.dsl.jbang.core.commands.CamelCommand;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.core.common.ProcessHelper;
import org.apache.camel.support.PatternHelper;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "log-tui",
         description = "TUI log viewer for Camel integrations",
         sortOptions = false)
public class CamelLogTui extends CamelCommand {

    private static final int MAX_READ_BYTES = 64 * 1024;

    @CommandLine.Parameters(description = "Name or pid of running Camel integration", arity = "0..1")
    String name = "*";

    @CommandLine.Option(names = { "--refresh" },
                        description = "Refresh interval in milliseconds (default: ${DEFAULT-VALUE})",
                        defaultValue = "500")
    long refreshInterval = 500;

    @CommandLine.Option(names = { "--grep" },
                        description = "Filter logs to matching lines")
    String grep;

    @CommandLine.Option(names = { "--level" },
                        description = "Filter by log level (INFO, WARN, ERROR, DEBUG)")
    String level;

    // State
    private final List<String> logLines = new ArrayList<>();
    private final List<String> filteredLines = new ArrayList<>();
    private int scrollOffset;
    private boolean followMode = true;

    // Level toggle filters (all enabled by default)
    private boolean showTrace = true;
    private boolean showDebug = true;
    private boolean showInfo = true;
    private boolean showWarn = true;
    private boolean showError = true;

    // Integration info
    private String resolvedPid;
    private String integrationName;
    private Path logFilePath;

    private volatile long lastRefresh;

    public CamelLogTui(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        // Eagerly load classes used by the input reader thread and picocli
        // post-processing to avoid ClassNotFoundException during shutdown
        try {
            Class.forName("dev.tamboui.tui.event.KeyModifiers");
            Class.forName("dev.tamboui.tui.event.KeyEvent");
            Class.forName("dev.tamboui.tui.event.KeyCode");
            Class.forName("picocli.CommandLine$IExitCodeGenerator");
        } catch (ClassNotFoundException e) {
            // ignore
        }

        // Apply --level as initial level filter
        if (level != null) {
            String lv = level.toUpperCase();
            showTrace = "TRACE".equals(lv);
            showDebug = "DEBUG".equals(lv) || "TRACE".equals(lv);
            showInfo = "INFO".equals(lv) || "DEBUG".equals(lv) || "TRACE".equals(lv);
            showWarn = "WARN".equals(lv) || "INFO".equals(lv) || "DEBUG".equals(lv) || "TRACE".equals(lv);
            showError = true; // always show errors
        }

        // Initial data load
        resolveIntegration();
        refreshLogData();

        try (var tui = TuiRunner.create()) {
            sun.misc.Signal.handle(new sun.misc.Signal("INT"), sig -> tui.quit());
            tui.run(
                    this::handleEvent,
                    this::render);
        }
        return 0;
    }

    // ---- Event Handling ----

    private boolean handleEvent(Event event, TuiRunner runner) {
        if (event instanceof KeyEvent ke) {
            if (ke.isQuit() || ke.isCharIgnoreCase('q') || ke.isKey(KeyCode.ESCAPE)) {
                runner.quit();
                return true;
            }
            if (ke.isUp()) {
                followMode = false;
                scrollOffset = Math.max(0, scrollOffset - 1);
                return true;
            }
            if (ke.isDown()) {
                scrollOffset++;
                return true;
            }
            if (ke.isKey(KeyCode.PAGE_UP)) {
                followMode = false;
                scrollOffset = Math.max(0, scrollOffset - 20);
                return true;
            }
            if (ke.isKey(KeyCode.PAGE_DOWN)) {
                scrollOffset += 20;
                return true;
            }
            if (ke.isChar('g')) {
                followMode = false;
                scrollOffset = 0;
                return true;
            }
            if (ke.isChar('G')) {
                followMode = true;
                return true;
            }
            if (ke.isChar('f')) {
                followMode = !followMode;
                return true;
            }
            // Level toggles: 1=TRACE, 2=DEBUG, 3=INFO, 4=WARN, 5=ERROR
            if (ke.isChar('1')) {
                showTrace = !showTrace;
                applyFilters();
                return true;
            }
            if (ke.isChar('2')) {
                showDebug = !showDebug;
                applyFilters();
                return true;
            }
            if (ke.isChar('3')) {
                showInfo = !showInfo;
                applyFilters();
                return true;
            }
            if (ke.isChar('4')) {
                showWarn = !showWarn;
                applyFilters();
                return true;
            }
            if (ke.isChar('5')) {
                showError = !showError;
                applyFilters();
                return true;
            }
        }
        if (event instanceof TickEvent) {
            long now = System.currentTimeMillis();
            if (now - lastRefresh >= refreshInterval) {
                resolveIntegration();
                refreshLogData();
            }
            return true;
        }
        return false;
    }

    // ---- Rendering ----

    private void render(Frame frame) {
        Rect area = frame.area();

        // Layout: header (3 rows) + log area (fill) + footer (1 row)
        List<Rect> mainChunks = Layout.vertical()
                .constraints(
                        Constraint.length(3),
                        Constraint.fill(),
                        Constraint.length(1))
                .split(area);

        renderHeader(frame, mainChunks.get(0));
        renderLogArea(frame, mainChunks.get(1));
        renderFooter(frame, mainChunks.get(2));
    }

    private void renderHeader(Frame frame, Rect area) {
        String titleText = integrationName != null ? integrationName : name;
        String logInfo = logFilePath != null ? logFilePath.getFileName().toString() : "no log file";
        String lineCount = filteredLines.size() + " lines";

        Line titleLine = Line.from(
                Span.styled(" Log Viewer", Style.create().fg(Color.rgb(0xF6, 0x91, 0x23)).bold()),
                Span.raw("  "),
                Span.styled(titleText, Style.create().fg(Color.CYAN)),
                Span.raw("  "),
                Span.styled(logInfo, Style.create().dim()),
                Span.raw("  "),
                Span.styled(lineCount, Style.create().fg(Color.GREEN)),
                followMode
                        ? Span.styled("  [FOLLOW]", Style.create().fg(Color.YELLOW).bold())
                        : Span.raw(""));

        Block headerBlock = Block.builder()
                .borderType(BorderType.ROUNDED)
                .title(" Camel Log ")
                .build();

        frame.renderWidget(
                Paragraph.builder().text(Text.from(titleLine)).block(headerBlock).build(),
                area);
    }

    private void renderLogArea(Frame frame, Rect area) {
        Block logBlock = Block.builder()
                .borderType(BorderType.ROUNDED)
                .title(buildLevelFilterTitle())
                .build();

        int innerHeight = Math.max(1, area.height() - 2);
        int totalLines = filteredLines.size();

        int startLine;
        if (followMode) {
            startLine = Math.max(0, totalLines - innerHeight);
            scrollOffset = startLine;
        } else {
            scrollOffset = Math.max(0, Math.min(scrollOffset, Math.max(0, totalLines - innerHeight)));
            startLine = scrollOffset;
        }

        List<Line> visibleLines = new ArrayList<>();
        for (int i = startLine; i < Math.min(startLine + innerHeight, totalLines); i++) {
            visibleLines.add(colorizeLogLine(filteredLines.get(i)));
        }

        // Fill remaining space
        while (visibleLines.size() < innerHeight) {
            visibleLines.add(Line.from(Span.raw("")));
        }

        Paragraph logParagraph = Paragraph.builder()
                .text(Text.from(visibleLines))
                .overflow(Overflow.CLIP)
                .block(logBlock)
                .build();

        frame.renderWidget(logParagraph, area);
    }

    private String buildLevelFilterTitle() {
        StringBuilder sb = new StringBuilder(" Levels: ");
        sb.append(showTrace ? "[1:TRACE] " : "[1:trace] ");
        sb.append(showDebug ? "[2:DEBUG] " : "[2:debug] ");
        sb.append(showInfo ? "[3:INFO] " : "[3:info] ");
        sb.append(showWarn ? "[4:WARN] " : "[4:warn] ");
        sb.append(showError ? "[5:ERROR] " : "[5:error] ");
        return sb.toString();
    }

    private void renderFooter(Frame frame, Rect area) {
        Line footer = Line.from(
                Span.styled(" q", Style.create().fg(Color.YELLOW).bold()),
                Span.raw(" quit  "),
                Span.styled("\u2191\u2193", Style.create().fg(Color.YELLOW).bold()),
                Span.raw(" scroll  "),
                Span.styled("g", Style.create().fg(Color.YELLOW).bold()),
                Span.raw("/"),
                Span.styled("G", Style.create().fg(Color.YELLOW).bold()),
                Span.raw(" top/bottom  "),
                Span.styled("f", Style.create().fg(Color.YELLOW).bold()),
                Span.raw(" follow  "),
                Span.styled("PgUp/PgDn", Style.create().fg(Color.YELLOW).bold()),
                Span.raw(" page  "),
                Span.styled("1-5", Style.create().fg(Color.YELLOW).bold()),
                Span.raw(" toggle levels"),
                grep != null
                        ? Span.styled("  grep:" + grep, Style.create().fg(Color.MAGENTA))
                        : Span.raw(""));

        frame.renderWidget(Paragraph.from(footer), area);
    }

    private Line colorizeLogLine(String line) {
        if (line.contains(" ERROR ") || line.contains(" FATAL ")) {
            return Line.from(Span.styled(line, Style.create().fg(Color.RED)));
        } else if (line.contains(" WARN ")) {
            return Line.from(Span.styled(line, Style.create().fg(Color.YELLOW)));
        } else if (line.contains(" DEBUG ")) {
            return Line.from(Span.styled(line, Style.create().dim()));
        } else if (line.contains(" TRACE ")) {
            return Line.from(Span.styled(line, Style.create().dim()));
        }
        return Line.from(Span.raw(line));
    }

    // ---- Data Loading ----

    private void resolveIntegration() {
        if (resolvedPid != null) {
            // Check if still alive
            try {
                ProcessHandle.of(Long.parseLong(resolvedPid)).ifPresentOrElse(
                        ph -> {
                            if (!ph.isAlive()) {
                                resolvedPid = null;
                                integrationName = null;
                                logFilePath = null;
                            }
                        },
                        () -> {
                            resolvedPid = null;
                            integrationName = null;
                            logFilePath = null;
                        });
            } catch (NumberFormatException e) {
                // ignore
            }
        }

        if (resolvedPid == null) {
            List<Long> pids = findPids(name);
            if (!pids.isEmpty()) {
                long pid = pids.get(0);
                resolvedPid = Long.toString(pid);
                logFilePath = CommandLineHelper.getCamelDir().resolve(resolvedPid + ".log");

                // Load integration name
                JsonObject root = loadStatus(pid);
                if (root != null) {
                    JsonObject context = (JsonObject) root.get("context");
                    if (context != null) {
                        integrationName = context.getString("name");
                        if ("CamelJBang".equals(integrationName)) {
                            ProcessHandle.of(pid).ifPresent(
                                    ph -> integrationName = ProcessHelper.extractName(root, ph));
                        }
                    }
                }
            }
        }
    }

    private void refreshLogData() {
        lastRefresh = System.currentTimeMillis();
        readLogFile();
        applyFilters();
    }

    private void readLogFile() {
        logLines.clear();
        if (logFilePath == null || !Files.exists(logFilePath)) {
            return;
        }
        try (RandomAccessFile raf = new RandomAccessFile(logFilePath.toFile(), "r")) {
            long length = raf.length();
            long startPos = Math.max(0, length - MAX_READ_BYTES);
            raf.seek(startPos);
            if (startPos > 0) {
                raf.readLine(); // skip partial line
            }
            byte[] remaining = new byte[(int) (length - raf.getFilePointer())];
            raf.readFully(remaining);
            String content = new String(remaining, StandardCharsets.UTF_8);
            String[] lines = content.split("\n", -1);
            for (String line : lines) {
                if (!line.isEmpty()) {
                    logLines.add(line);
                }
            }
        } catch (IOException e) {
            // ignore
        }
    }

    private void applyFilters() {
        filteredLines.clear();
        for (String line : logLines) {
            if (!matchesLevelFilter(line)) {
                continue;
            }
            if (grep != null && !grep.isEmpty() && !line.contains(grep)) {
                continue;
            }
            filteredLines.add(line);
        }
    }

    private boolean matchesLevelFilter(String line) {
        if (line.contains(" ERROR ") || line.contains(" FATAL ")) {
            return showError;
        } else if (line.contains(" WARN ")) {
            return showWarn;
        } else if (line.contains(" DEBUG ")) {
            return showDebug;
        } else if (line.contains(" TRACE ")) {
            return showTrace;
        }
        // Lines without a recognized level are treated as INFO
        return showInfo;
    }

    // ---- Helpers ----

    private List<Long> findPids(String name) {
        List<Long> pids = new ArrayList<>();
        final long cur = ProcessHandle.current().pid();
        String pattern = name;
        if (!pattern.matches("\\d+") && !pattern.endsWith("*")) {
            pattern = pattern + "*";
        }
        final String pat = pattern;
        ProcessHandle.allProcesses()
                .filter(ph -> ph.pid() != cur)
                .forEach(ph -> {
                    JsonObject root = loadStatus(ph.pid());
                    if (root != null) {
                        String pName = ProcessHelper.extractName(root, ph);
                        pName = FileUtil.onlyName(pName);
                        if (pName != null && !pName.isEmpty() && PatternHelper.matchPattern(pName, pat)) {
                            pids.add(ph.pid());
                        } else {
                            JsonObject context = (JsonObject) root.get("context");
                            if (context != null) {
                                pName = context.getString("name");
                                if ("CamelJBang".equals(pName)) {
                                    pName = null;
                                }
                                if (pName != null && !pName.isEmpty() && PatternHelper.matchPattern(pName, pat)) {
                                    pids.add(ph.pid());
                                }
                            }
                        }
                    }
                });
        return pids;
    }

    private JsonObject loadStatus(long pid) {
        try {
            Path f = getStatusFile(Long.toString(pid));
            if (f != null && Files.exists(f)) {
                String text = Files.readString(f);
                return (JsonObject) Jsoner.deserialize(text);
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }
}
