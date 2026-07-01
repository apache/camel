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
import java.io.OutputStream;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Layout;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.AnsiColor;
import dev.tamboui.style.Color;
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
import dev.tamboui.widgets.paragraph.Paragraph;
import dev.tamboui.widgets.scrollbar.Scrollbar;
import dev.tamboui.widgets.scrollbar.ScrollbarState;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.EnvironmentHelper;
import org.apache.camel.dsl.jbang.core.common.Printer;
import org.jline.builtins.InteractiveCommandGroup;
import org.jline.builtins.PosixCommandGroup;
import org.jline.picocli.PicocliCommandRegistry;
import org.jline.reader.LineReader;
import org.jline.shell.Shell;
import org.jline.shell.impl.DefaultCommandDispatcher;
import org.jline.terminal.Size;
import org.jline.terminal.impl.LineDisciplineTerminal;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import org.jline.utils.ScreenTerminal;
import org.jline.utils.ScreenTerminalOutputStream;

/**
 * Embeds a JLine interactive shell inside the TUI using a virtual terminal.
 * <p>
 * The wiring follows the same pattern as JLine's own {@code WebTerminal}:
 * <ul>
 * <li>{@link LineDisciplineTerminal} provides the master/slave virtual terminal</li>
 * <li>{@link ScreenTerminal} acts as a VT100 emulator with a readable screen buffer</li>
 * <li>{@link ScreenTerminalOutputStream} bridges terminal output to the screen buffer</li>
 * </ul>
 * The shell runs in a background thread. On each TUI render frame, the screen buffer is dumped and converted to TamboUI
 * widgets. Key events from TamboUI are encoded as ANSI escape sequences and forwarded to the virtual terminal.
 */
class ShellPanel {

    private static final Logger LOG = System.getLogger(ShellPanel.class.getName());
    private static final int MOUSE_SCROLL_LINES = 3;

    private boolean visible;
    private final PanelAnimation anim = new PanelAnimation();
    private MonitorContext ctx;

    private ScreenTerminal screenTerminal;
    private LineDisciplineTerminal virtualTerminal;
    private Thread shellThread;

    private final ScrollbarState scrollbarState = new ScrollbarState();

    private int lastWidth;
    private int lastHeight;
    private int scrollOffset;
    private int lastHistorySize;
    private int lastCursorX = -1;
    private int lastCursorY = -1;
    private Rect lastArea;
    private volatile boolean shellExited;

    void setContext(MonitorContext ctx) {
        this.ctx = ctx;
    }

    boolean isOpen() {
        return visible;
    }

    int panelHeight() {
        return anim.panelHeight();
    }

    boolean isAnimating() {
        return anim.isAnimating();
    }

    void tickAnimation() {
        anim.tickAnimation();
    }

    void initHeight(int contentHeight) {
        anim.initHeight(contentHeight);
    }

    void cycleHeight(int contentHeight) {
        anim.cycleHeight(contentHeight);
    }

    void setPanelHeight(int height) {
        anim.setPanelHeight(height);
    }

    void open() {
        visible = true;
        if (startError != null || shellExited) {
            startError = null;
            shellExited = false;
            screenTerminal = null;
            virtualTerminal = null;
        }
    }

    void close() {
        visible = false;
    }

    void destroy() {
        visible = false;
        stopShell();
    }

    boolean handleKeyEvent(KeyEvent ke) {
        if (!visible) {
            return false;
        }

        // F6 hides the shell panel
        if (ke.isKey(KeyCode.F6)) {
            close();
            return true;
        }

        // PageUp/Down for scrollback through history
        if (ke.isKey(KeyCode.PAGE_UP)) {
            int histSize = screenTerminal != null ? screenTerminal.getHistorySize() : 0;
            scrollOffset = Math.min(scrollOffset + lastHeight, histSize);
            return true;
        }
        if (ke.isKey(KeyCode.PAGE_DOWN)) {
            scrollOffset = Math.max(0, scrollOffset - lastHeight);
            return true;
        }

        // Any regular key input resets scrollback to live view
        scrollOffset = 0;

        // Forward everything else to the virtual terminal
        if (virtualTerminal != null) {
            try {
                byte[] bytes = encodeKeyEvent(ke);
                if (bytes != null && bytes.length > 0) {
                    virtualTerminal.processInputBytes(bytes);
                }
            } catch (IOException e) {
                // terminal closed — expected during shutdown
                LOG.log(Level.DEBUG, "Terminal I/O error forwarding key event", e);
            } catch (ArrayIndexOutOfBoundsException e) {
                // ScreenTerminal buffer resized concurrently
                LOG.log(Level.DEBUG, "Buffer resize race during key forwarding", e);
            }
        }
        return true;
    }

    boolean handleMouseEvent(MouseEvent me) {
        if (!visible || lastArea == null) {
            return false;
        }
        if (!AbstractTab.contains(lastArea, me.x(), me.y())) {
            return false;
        }
        if (me.kind() == MouseEventKind.SCROLL_UP) {
            int histSize = screenTerminal != null ? screenTerminal.getHistorySize() : 0;
            scrollOffset = Math.min(scrollOffset + MOUSE_SCROLL_LINES, histSize);
            return true;
        }
        if (me.kind() == MouseEventKind.SCROLL_DOWN) {
            scrollOffset = Math.max(0, scrollOffset - MOUSE_SCROLL_LINES);
            return true;
        }
        return false;
    }

    void render(Frame frame, Rect area) {
        if (!visible) {
            return;
        }

        if (shellExited) {
            close();
            return;
        }

        lastArea = area;

        // Focused pane: orange border + themed title (an open shell holds input focus)
        Block block = Block.builder()
                .borderType(BorderType.ROUNDED).borders(Borders.ALL)
                .borderStyle(Theme.borderFocused())
                .title(Title.from(Line.from(Span.styled(" Shell ", Theme.title()))))
                .build();
        frame.renderWidget(block, area);
        Rect inner = block.inner(area);

        int innerWidth = inner.width();
        int innerHeight = inner.height();

        // Start shell on first render (we now know the size)
        if (screenTerminal == null && innerWidth > 2 && innerHeight > 2) {
            startShell(innerWidth, innerHeight);
        }

        // Handle resize
        if (screenTerminal != null && (innerWidth != lastWidth || innerHeight != lastHeight)) {
            screenTerminal.setSize(Size.of(innerWidth, innerHeight));
            if (virtualTerminal != null) {
                virtualTerminal.setSize(Size.of(innerWidth, innerHeight));
            }
            lastWidth = innerWidth;
            lastHeight = innerHeight;
        }

        // Show error from shell thread crash
        if (startError != null) {
            frame.renderWidget(
                    Paragraph.from(Line.from(
                            Span.styled(startError, Style.EMPTY.fg(Color.LIGHT_RED)))),
                    inner);
            return;
        }

        if (screenTerminal == null) {
            return;
        }

        // Dump screen buffer (may race with shell thread writing)
        long[] screen = new long[innerWidth * innerHeight];
        int[] cursor = new int[2];
        try {
            screenTerminal.dump(screen, cursor);
        } catch (ArrayIndexOutOfBoundsException e) {
            // buffer resized concurrently — skip this frame
            LOG.log(Level.DEBUG, "Buffer resize race during screen dump — skipping frame", e);
            return;
        }

        // Auto-follow: reset scroll when new history appears
        int histSize = screenTerminal.getHistorySize();
        if (histSize > lastHistorySize && scrollOffset > 0) {
            scrollOffset = 0;
        }
        lastHistorySize = histSize;

        List<Line> lines;
        if (scrollOffset > 0) {
            lines = renderScrolledView(screen, innerWidth, innerHeight);
        } else {
            lines = renderLiveView(screen, innerWidth, innerHeight);
        }

        // Split the inner area: content (fill) + scrollbar (1 col) when history exists
        int totalLines = histSize + innerHeight;
        boolean showScrollbar = totalLines > innerHeight;
        Rect contentArea;
        if (showScrollbar) {
            List<Rect> hChunks = Layout.horizontal()
                    .constraints(Constraint.fill(), Constraint.length(1))
                    .split(inner);
            contentArea = hChunks.get(0);

            // Map scrollOffset (lines-from-bottom) to top-down position for ScrollbarState
            int viewStart = Math.max(0, totalLines - scrollOffset - innerHeight);
            scrollbarState
                    .contentLength(totalLines)
                    .viewportContentLength(innerHeight)
                    .position(viewStart);
            frame.renderStatefulWidget(Scrollbar.builder().build(), hChunks.get(1), scrollbarState);
        } else {
            contentArea = inner;
        }

        frame.renderWidget(
                Paragraph.builder()
                        .text(Text.from(lines))
                        .overflow(Overflow.CLIP)
                        .build(),
                contentArea);

        // Position the hardware cursor only when it has moved, so the terminal's
        // blink timer is not reset on every frame.
        if (scrollOffset == 0 && cursor[1] >= 0 && cursor[1] < innerHeight
                && cursor[0] >= 0 && cursor[0] < innerWidth) {
            int cx = contentArea.x() + cursor[0];
            int cy = contentArea.y() + cursor[1];
            if (cx != lastCursorX || cy != lastCursorY) {
                frame.setCursorPosition(cx, cy);
                lastCursorX = cx;
                lastCursorY = cy;
            }
        } else {
            lastCursorX = -1;
            lastCursorY = -1;
        }
    }

    void renderFooter(List<Span> spans) {
        MonitorContext.hint(spans, "F6", "close");
        MonitorContext.hint(spans, "Shift+F6", "resize (" + anim.cyclePercent() + "%)");
        MonitorContext.hint(spans, "PgUp/Dn", "scroll");
    }

    private List<Line> renderLiveView(long[] screen, int width, int height) {
        List<Line> lines = new ArrayList<>(height);
        for (int row = 0; row < height; row++) {
            lines.add(convertRow(screen, row * width, width));
        }
        return lines;
    }

    private List<Line> renderScrolledView(long[] screen, int width, int height) {
        List<long[]> history = screenTerminal.getHistory();
        if (history.isEmpty()) {
            return renderLiveView(screen, width, height);
        }

        int totalLines = history.size() + height;
        int viewStart = Math.max(0, totalLines - scrollOffset - height);

        List<Line> lines = new ArrayList<>(height);
        for (int i = 0; i < height; i++) {
            int lineIdx = viewStart + i;
            if (lineIdx < history.size()) {
                long[] histLine = history.get(lineIdx);
                lines.add(convertRow(histLine, 0, Math.min(histLine.length, width)));
            } else {
                int screenRow = lineIdx - history.size();
                if (screenRow >= 0 && screenRow < height) {
                    lines.add(convertRow(screen, screenRow * width, width));
                } else {
                    lines.add(Line.from(Span.raw("")));
                }
            }
        }
        return lines;
    }

    static Line convertRow(long[] buffer, int offset, int width) {
        List<Span> spans = new ArrayList<>();
        int col = 0;
        while (col < width) {
            long cell = buffer[offset + col];
            int ch = ScreenTerminal.cellCodePoint(cell);
            long attr = ScreenTerminal.cellAttr(cell);
            Style style = convertCellToStyle(cell);

            StringBuilder sb = new StringBuilder();
            sb.appendCodePoint(ch == 0 ? ' ' : ch);
            int nextCol = col + 1;
            while (nextCol < width) {
                long nextCell = buffer[offset + nextCol];
                if (ScreenTerminal.cellAttr(nextCell) != attr) {
                    break;
                }
                int nextCh = ScreenTerminal.cellCodePoint(nextCell);
                sb.appendCodePoint(nextCh == 0 ? ' ' : nextCh);
                nextCol++;
            }
            spans.add(Span.styled(sb.toString(), style));
            col = nextCol;
        }
        return Line.from(spans);
    }

    private String startError;

    private void startShell(int width, int height) {
        try {
            screenTerminal = new ScreenTerminal(width, height);
            lastWidth = width;
            lastHeight = height;

            // Delegate OutputStream to break the circular dependency:
            // LineDisciplineTerminal needs masterOutput at construction,
            // but ScreenTerminalOutputStream needs the terminal for feedback.
            DelegateOutputStream delegateOut = new DelegateOutputStream();
            virtualTerminal = new LineDisciplineTerminal(
                    "tui-shell", "screen-256color", delegateOut, StandardCharsets.UTF_8);
            virtualTerminal.setSize(Size.of(width, height));

            // Feedback loop: VT100 responses go back as terminal input
            OutputStream feedbackOutput = new OutputStream() {
                @Override
                public void write(int b) throws IOException {
                    virtualTerminal.processInputByte(b);
                }
            };
            delegateOut.delegate = new ScreenTerminalOutputStream(
                    screenTerminal, StandardCharsets.UTF_8, feedbackOutput);

            shellThread = new Thread(() -> runShell(virtualTerminal), "tui-shell");
            shellThread.setDaemon(true);
            shellThread.start();
        } catch (Exception e) {
            startError = e.getClass().getSimpleName() + ": " + e.getMessage();
            screenTerminal = null;
            virtualTerminal = null;
        }
    }

    private void runShell(LineDisciplineTerminal terminal) {
        try {
            // TODO: replace with new PicocliCommandRegistry(commandLine, "Camel") when JLine merges #1947
            PicocliCommandRegistry registry = new PicocliCommandRegistry(CamelJBangMain.getCommandLine()) {
                @Override
                public String name() {
                    return "Camel";
                }
            };
            // Redirect command output (printer()) through the virtual terminal
            // so it renders in the shell panel instead of the TUI's real terminal
            CamelJBangMain main = (CamelJBangMain) CamelJBangMain.getCommandLine().getCommand();
            Printer originalPrinter = main.getOut();
            Printer terminalPrinter = new Printer() {
                @Override
                public void println() {
                    terminal.writer().println();
                    terminal.writer().flush();
                }

                @Override
                public void println(String line) {
                    terminal.writer().println(line);
                    terminal.writer().flush();
                }

                @Override
                public void print(String output) {
                    terminal.writer().print(output);
                    terminal.writer().flush();
                }

                @Override
                public void printf(String format, Object... args) {
                    terminal.writer().printf(format, args);
                    terminal.writer().flush();
                }
            };
            main.setOut(terminalPrinter);

            // Propagate TUI's selected integration so ask auto-targets it
            if (ctx != null && ctx.selectedPid != null) {
                EnvironmentHelper.setSelectedProcess(ctx.selectedName());
            }

            try (Shell shell = Shell.builder()
                    .terminal(terminal)
                    .prompt(ShellPanel::buildPrompt)
                    .groups(registry, new PosixCommandGroup(), new InteractiveCommandGroup())
                    .historyCommands(true)
                    .helpCommands(true)
                    .commandHighlighter(false)
                    .variable(LineReader.LIST_MAX, 50)
                    .onReaderReady((reader, dispatcher) -> {
                        if (dispatcher instanceof DefaultCommandDispatcher dcd) {
                            dcd.session().setWorkingDirectory(Path.of("").toAbsolutePath());
                        }
                    })
                    .build()) {
                EnvironmentHelper.setActiveTerminal(terminal);
                shell.run();
                shellExited = true;
            } finally {
                EnvironmentHelper.setActiveTerminal(null);
                EnvironmentHelper.setSelectedProcess(null);
                main.setOut(originalPrinter);
            }
        } catch (Exception e) {
            startError = "Shell crashed: " + e.getClass().getSimpleName() + ": " + e.getMessage();
        }
    }

    private static String buildPrompt() {
        AttributedStringBuilder sb = new AttributedStringBuilder();
        sb.append("camel", AttributedStyle.DEFAULT.bold().foregroundRgb(0xF69123));
        sb.append("> ", AttributedStyle.DEFAULT);
        return sb.toAnsi();
    }

    private void stopShell() {
        if (shellThread != null) {
            shellThread.interrupt();
            shellThread = null;
        }
        if (virtualTerminal != null) {
            try {
                virtualTerminal.close();
            } catch (IOException e) {
                LOG.log(Level.DEBUG, "Error closing virtual terminal during shutdown", e);
            }
            virtualTerminal = null;
        }
        screenTerminal = null;
    }

    /**
     * Converts a {@link ScreenTerminal} 64-bit cell value into a TamboUI {@link Style}, using JLine's public
     * cell-decoding helpers.
     */
    static Style convertCellToStyle(long cell) {
        Style style = Style.EMPTY;

        if (ScreenTerminal.cellBold(cell)) {
            style = style.bold();
        }
        if (ScreenTerminal.cellUnderline(cell)) {
            style = style.underlined();
        }
        if (ScreenTerminal.cellInverse(cell)) {
            style = style.reversed();
        }
        if (ScreenTerminal.cellDim(cell)) {
            style = style.dim();
        }
        if (ScreenTerminal.cellItalic(cell)) {
            style = style.italic();
        }

        if (ScreenTerminal.cellHasForeground(cell)) {
            style = style.fg(resolveColor(ScreenTerminal.cellForeground(cell)));
        }
        if (ScreenTerminal.cellHasBackground(cell)) {
            style = style.bg(resolveColor(ScreenTerminal.cellBackground(cell)));
        }

        return style;
    }

    /**
     * Converts a 12-bit (4-bit-per-channel) color value from the {@link ScreenTerminal} attribute word into a TamboUI
     * {@link Color}.
     * <p>
     * When the value matches one of the 16 standard ANSI palette colors, a themed {@link Color#ansi(AnsiColor)} is
     * returned so the host terminal applies its own scheme (keeping, for example, red error output and the command
     * highlighter legible on dark backgrounds). Any other value is a true-color cell (such as the orange shell prompt)
     * and is expanded to its literal RGB value.
     */
    static Color resolveColor(int rgb12) {
        AnsiColor ansi = ansiColorFor(rgb12);
        if (ansi != null) {
            return Color.ansi(ansi);
        }
        // Expand each 4-bit channel back to 8 bits (0xN -> 0xNN, i.e. * 17).
        int r = ((rgb12 >> 8) & 0xF) * 17;
        int g = ((rgb12 >> 4) & 0xF) * 17;
        int b = (rgb12 & 0xF) * 17;
        return Color.rgb(r, g, b);
    }

    /**
     * Maps a 12-bit color value to the standard ANSI palette color it encodes, or {@code null} if it does not match one
     * of the 16 ANSI colors. The values are {@link ScreenTerminal}'s palette (xterm defaults) reduced to the top nibble
     * of each channel, matching how {@code ScreenTerminal} stores them in the cell attribute.
     */
    static AnsiColor ansiColorFor(int rgb12) {
        return switch (rgb12) {
            case 0x000 -> AnsiColor.BLACK;
            case 0x800 -> AnsiColor.RED;
            case 0x080 -> AnsiColor.GREEN;
            case 0x880 -> AnsiColor.YELLOW;
            case 0x008 -> AnsiColor.BLUE;
            case 0x808 -> AnsiColor.MAGENTA;
            case 0x088 -> AnsiColor.CYAN;
            case 0xccc -> AnsiColor.WHITE;
            case 0x888 -> AnsiColor.BRIGHT_BLACK;
            case 0xf00 -> AnsiColor.BRIGHT_RED;
            case 0x0f0 -> AnsiColor.BRIGHT_GREEN;
            case 0xff0 -> AnsiColor.BRIGHT_YELLOW;
            case 0x00f -> AnsiColor.BRIGHT_BLUE;
            case 0xf0f -> AnsiColor.BRIGHT_MAGENTA;
            case 0x0ff -> AnsiColor.BRIGHT_CYAN;
            case 0xfff -> AnsiColor.BRIGHT_WHITE;
            default -> null;
        };
    }

    static byte[] encodeKeyEvent(KeyEvent ke) {
        if (ke.code() == KeyCode.CHAR) {
            String s = ke.string();
            if (ke.hasCtrl() && s.length() == 1) {
                // Ctrl+letter → control character
                char ch = s.charAt(0);
                if (ch >= 'a' && ch <= 'z') {
                    return new byte[] { (byte) (ch - 'a' + 1) };
                }
                if (ch >= 'A' && ch <= 'Z') {
                    return new byte[] { (byte) (ch - 'A' + 1) };
                }
            }
            return s.getBytes(StandardCharsets.UTF_8);
        }

        return switch (ke.code()) {
            case ENTER -> new byte[] { '\r' };
            case BACKSPACE -> new byte[] { 0x7f };
            case TAB -> new byte[] { '\t' };
            case UP -> "\033OA".getBytes(StandardCharsets.UTF_8);
            case DOWN -> "\033OB".getBytes(StandardCharsets.UTF_8);
            case RIGHT -> "\033OC".getBytes(StandardCharsets.UTF_8);
            case LEFT -> "\033OD".getBytes(StandardCharsets.UTF_8);
            case HOME -> "\033OH".getBytes(StandardCharsets.UTF_8);
            case END -> "\033OF".getBytes(StandardCharsets.UTF_8);
            case PAGE_UP -> "\033[5~".getBytes(StandardCharsets.UTF_8);
            case PAGE_DOWN -> "\033[6~".getBytes(StandardCharsets.UTF_8);
            case INSERT -> "\033[2~".getBytes(StandardCharsets.UTF_8);
            case DELETE -> "\033[3~".getBytes(StandardCharsets.UTF_8);
            case F1 -> "\033OP".getBytes(StandardCharsets.UTF_8);
            case F2 -> "\033OQ".getBytes(StandardCharsets.UTF_8);
            case F3 -> "\033OR".getBytes(StandardCharsets.UTF_8);
            case F4 -> "\033OS".getBytes(StandardCharsets.UTF_8);
            case F5 -> "\033[15~".getBytes(StandardCharsets.UTF_8);
            case F6 -> "\033[17~".getBytes(StandardCharsets.UTF_8);
            case F7 -> "\033[18~".getBytes(StandardCharsets.UTF_8);
            case F8 -> "\033[19~".getBytes(StandardCharsets.UTF_8);
            case F9 -> "\033[20~".getBytes(StandardCharsets.UTF_8);
            case F10 -> "\033[21~".getBytes(StandardCharsets.UTF_8);
            case F12 -> "\033[24~".getBytes(StandardCharsets.UTF_8);
            default -> null;
        };
    }

    private static class DelegateOutputStream extends OutputStream {
        volatile OutputStream delegate;

        @Override
        public void write(int b) throws IOException {
            if (delegate != null) {
                try {
                    delegate.write(b);
                } catch (ArrayIndexOutOfBoundsException e) {
                    // ScreenTerminal buffer resized concurrently — safe to ignore
                    LOG.log(Level.TRACE, "Buffer resize race in write(int)", e);
                }
            }
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            if (delegate != null) {
                try {
                    delegate.write(b, off, len);
                } catch (ArrayIndexOutOfBoundsException e) {
                    // ScreenTerminal buffer resized concurrently — safe to ignore
                    LOG.log(Level.TRACE, "Buffer resize race in write(byte[])", e);
                }
            }
        }

        @Override
        public void flush() throws IOException {
            if (delegate != null) {
                try {
                    delegate.flush();
                } catch (ArrayIndexOutOfBoundsException e) {
                    // ScreenTerminal buffer resized concurrently — safe to ignore
                    LOG.log(Level.TRACE, "Buffer resize race in flush()", e);
                }
            }
        }

        @Override
        public void close() throws IOException {
            if (delegate != null) {
                delegate.close();
            }
        }
    }
}
