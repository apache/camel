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
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
import dev.tamboui.widgets.block.Title;
import dev.tamboui.widgets.paragraph.Paragraph;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.EnvironmentHelper;
import org.apache.camel.dsl.jbang.core.common.Printer;
import org.apache.camel.dsl.jbang.core.common.VersionHelper;
import org.jline.builtins.InteractiveCommandGroup;
import org.jline.builtins.PosixCommandGroup;
import org.jline.builtins.ScreenTerminal;
import org.jline.builtins.ScreenTerminalOutputStream;
import org.jline.picocli.PicocliCommandRegistry;
import org.jline.reader.LineReader;
import org.jline.shell.Shell;
import org.jline.shell.impl.DefaultCommandDispatcher;
import org.jline.terminal.Size;
import org.jline.terminal.impl.LineDisciplineTerminal;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

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

    private static final int[] SPLIT_PERCENTS = { 25, 50, 75 };

    private boolean visible;
    private int splitIndex = 1; // default 50%
    private MonitorContext ctx;

    private ScreenTerminal screenTerminal;
    private LineDisciplineTerminal virtualTerminal;
    private Thread shellThread;

    private int lastWidth;
    private int lastHeight;
    private int scrollOffset;
    private int lastHistorySize;
    private volatile boolean shellExited;

    void setContext(MonitorContext ctx) {
        this.ctx = ctx;
    }

    boolean isOpen() {
        return visible;
    }

    int panelPercent() {
        return SPLIT_PERCENTS[splitIndex];
    }

    void cycleHeight() {
        splitIndex = (splitIndex + 1) % SPLIT_PERCENTS.length;
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

        // Shift+PageUp/Down for scrollback through history
        if (ke.isKey(KeyCode.PAGE_UP) && ke.hasShift()) {
            int histSize = screenTerminal != null ? getHistorySize(screenTerminal) : 0;
            scrollOffset = Math.min(scrollOffset + lastHeight, histSize);
            return true;
        }
        if (ke.isKey(KeyCode.PAGE_DOWN) && ke.hasShift()) {
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
            } catch (IOException | ArrayIndexOutOfBoundsException e) {
                // terminal closed or buffer resized concurrently
            }
        }
        return true;
    }

    void render(Frame frame, Rect area) {
        if (!visible) {
            return;
        }

        if (shellExited) {
            close();
            return;
        }

        // Render border matching other tabs
        Block block = Block.builder()
                .borderType(BorderType.ROUNDED)
                .title(Title.from(Line.from(Span.styled(" Shell ", Style.EMPTY.bold()))))
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
            screenTerminal.setSize(innerWidth, innerHeight);
            if (virtualTerminal != null) {
                virtualTerminal.setSize(new Size(innerWidth, innerHeight));
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
            return;
        }

        // Auto-follow: reset scroll when new history appears
        int histSize = getHistorySize(screenTerminal);
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

        frame.renderWidget(
                Paragraph.builder()
                        .text(Text.from(lines))
                        .overflow(Overflow.CLIP)
                        .build(),
                inner);
    }

    void renderFooter(List<Span> spans) {
        MonitorContext.hint(spans, "F6", "close");
        int nextPct = SPLIT_PERCENTS[(splitIndex + 1) % SPLIT_PERCENTS.length];
        MonitorContext.hint(spans, "Shift+F6", nextPct + "%");
        MonitorContext.hint(spans, "Shift+PgUp/Dn", "scroll");
    }

    private List<Line> renderLiveView(long[] screen, int width, int height) {
        List<Line> lines = new ArrayList<>(height);
        for (int row = 0; row < height; row++) {
            lines.add(convertRow(screen, row * width, width));
        }
        return lines;
    }

    private List<Line> renderScrolledView(long[] screen, int width, int height) {
        List<long[]> history = getHistory(screenTerminal);
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

    private static Line convertRow(long[] buffer, int offset, int width) {
        List<Span> spans = new ArrayList<>();
        int col = 0;
        while (col < width) {
            long cell = buffer[offset + col];
            int ch = (int) (cell & 0xffffffffL);
            long attr = cell >>> 32;
            Style style = convertAttrToStyle(attr);

            StringBuilder sb = new StringBuilder();
            sb.appendCodePoint(ch == 0 ? ' ' : ch);
            int nextCol = col + 1;
            while (nextCol < width) {
                long nextCell = buffer[offset + nextCol];
                long nextAttr = nextCell >>> 32;
                if (nextAttr != attr) {
                    break;
                }
                int nextCh = (int) (nextCell & 0xffffffffL);
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
            virtualTerminal.setSize(new Size(width, height));

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
            PicocliCommandRegistry registry = new PicocliCommandRegistry(CamelJBangMain.getCommandLine());
            String camelVersion = VersionHelper.extractCamelVersion();

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
                    .prompt(() -> buildPrompt(camelVersion))
                    .groups(registry, new PosixCommandGroup(), new InteractiveCommandGroup())
                    .historyCommands(true)
                    .helpCommands(true)
                    .commandHighlighter(true)
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

    private static String buildPrompt(String camelVersion) {
        AttributedStringBuilder sb = new AttributedStringBuilder();
        sb.append("camel", AttributedStyle.DEFAULT.bold().foregroundRgb(0xF69123));
        if (camelVersion != null) {
            sb.append(" ");
            sb.append(camelVersion, AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN));
        }
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
                // ignore
            }
            virtualTerminal = null;
        }
        screenTerminal = null;
    }

    // Attribute mask from ScreenTerminal:
    //   0xYXFFFBBB00000000L
    //   X: Bit 0=Underline, Bit 1=Negative, Bit 2=Concealed, Bit 3=Bold
    //   Y: Bit 0=FG set, Bit 1=BG set, Bit 2=Dim, Bit 3=Italic
    //   F: Foreground r-g-b (3 hex nibbles)
    //   B: Background r-g-b (3 hex nibbles)
    private static Style convertAttrToStyle(long attr) {
        Style style = Style.EMPTY;

        int x = (int) ((attr >> 24) & 0xF);
        int y = (int) ((attr >> 28) & 0xF);

        if ((x & 0x8) != 0) {
            style = style.bold();
        }
        if ((x & 0x1) != 0) {
            style = style.underlined();
        }
        if ((x & 0x2) != 0) {
            style = style.reversed();
        }
        if ((y & 0x4) != 0) {
            style = style.dim();
        }
        if ((y & 0x8) != 0) {
            style = style.italic();
        }

        // Foreground color (if set)
        if ((y & 0x1) != 0) {
            int fg = (int) ((attr >> 12) & 0xFFF);
            int r = ((fg >> 8) & 0xF) * 17;
            int g = ((fg >> 4) & 0xF) * 17;
            int b = (fg & 0xF) * 17;
            style = style.fg(Color.rgb(r, g, b));
        }

        // Background color (if set)
        if ((y & 0x2) != 0) {
            int bg = (int) (attr & 0xFFF);
            int r = ((bg >> 8) & 0xF) * 17;
            int g = ((bg >> 4) & 0xF) * 17;
            int b = (bg & 0xF) * 17;
            style = style.bg(Color.rgb(r, g, b));
        }

        return style;
    }

    private static byte[] encodeKeyEvent(KeyEvent ke) {
        if (ke.code() == KeyCode.CHAR) {
            char ch = ke.character();
            if (ke.hasCtrl()) {
                // Ctrl+letter → control character
                if (ch >= 'a' && ch <= 'z') {
                    return new byte[] { (byte) (ch - 'a' + 1) };
                }
                if (ch >= 'A' && ch <= 'Z') {
                    return new byte[] { (byte) (ch - 'A' + 1) };
                }
            }
            return Character.toString(ch).getBytes(StandardCharsets.UTF_8);
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

    @SuppressWarnings("unchecked")
    private static List<long[]> getHistory(ScreenTerminal st) {
        try {
            Method m = ScreenTerminal.class.getMethod("getHistory");
            return (List<long[]>) m.invoke(st);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private static int getHistorySize(ScreenTerminal st) {
        try {
            Method m = ScreenTerminal.class.getMethod("getHistorySize");
            return (int) m.invoke(st);
        } catch (Exception e) {
            return 0;
        }
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
