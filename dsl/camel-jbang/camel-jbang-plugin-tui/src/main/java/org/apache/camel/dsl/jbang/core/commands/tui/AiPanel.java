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

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Layout;
import dev.tamboui.layout.Rect;
import dev.tamboui.markdown.MarkdownView;
import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.block.Title;
import dev.tamboui.widgets.paragraph.Paragraph;
import org.apache.camel.dsl.jbang.core.commands.AskTools;
import org.apache.camel.dsl.jbang.core.commands.LlmClient;

/**
 * AI prompt panel for the TUI. Communicates directly with an LLM via {@link LlmClient} and uses the same tool
 * definitions as {@code camel ask}. Toggled with F8 when the TUI runs with {@code --mcp} mode.
 */
class AiPanel {

    private static final int[] SPLIT_PERCENTS = { 25, 50, 75, 100 };
    private static final int MAX_ITERATIONS = 10;
    private static final int MAX_LOG_ENTRIES = 200;
    private static final DateTimeFormatter TIME_FMT
            = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    enum LogLevel {
        QUESTION,
        TOOL,
        RESULT,
        RESPONSE,
        ERROR
    }

    record LogEntry(String timestamp, LogLevel level, String message, String detail) {
    }

    private boolean visible;
    private int splitIndex = 1; // default 50%
    private MonitorContext ctx;

    // Input state
    private final StringBuilder inputBuffer = new StringBuilder();
    private int cursorPos;

    // Conversation display
    private final List<ConversationEntry> conversation = new ArrayList<>();
    private int scrollOffset;

    // LLM state
    private LlmClient client;
    private List<LlmClient.Message> messages;
    private List<LlmClient.ToolDef> tools;
    private AskTools askTools;
    private final AtomicBoolean thinking = new AtomicBoolean();
    private volatile Thread agentThread;
    private String initError;

    // Activity log for AI Log popup
    private final List<LogEntry> activityLog = new ArrayList<>();

    record ConversationEntry(String role, String text) {
    }

    void setContext(MonitorContext ctx) {
        this.ctx = ctx;
    }

    synchronized List<LogEntry> getActivityLog() {
        return new ArrayList<>(activityLog);
    }

    private synchronized void log(LogLevel level, String message, String detail) {
        activityLog.add(new LogEntry(TIME_FMT.format(Instant.now()), level, message, detail));
        if (activityLog.size() > MAX_LOG_ENTRIES) {
            activityLog.remove(0);
        }
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
        if (client == null) {
            initClient();
        }
    }

    void close() {
        visible = false;
    }

    void destroy() {
        close();
        Thread t = agentThread;
        if (t != null) {
            t.interrupt();
        }
    }

    private void initClient() {
        try {
            client = LlmClient.create()
                    .withTemperature(0.3)
                    .withTimeout(120)
                    .withMaxTokens(4096);
            if (!client.detectEndpoint()) {
                initError = "No LLM service reachable. Set ANTHROPIC_API_KEY, OPENAI_API_KEY, or start Ollama.";
                client = null;
                return;
            }
            initError = null;
            messages = new ArrayList<>();
            long pid = ctx != null && ctx.selectedPid != null ? Long.parseLong(ctx.selectedPid) : -1;
            String name = ctx != null ? ctx.selectedName() : null;
            askTools = new AskTools(pid);
            tools = askTools.buildToolDefinitions();
        } catch (Exception e) {
            initError = "Failed to initialize AI: " + e.getMessage();
            client = null;
        }
    }

    boolean handleKeyEvent(KeyEvent ke) {
        if (ke.isKey(KeyCode.F8)) {
            close();
            return true;
        }
        if (ke.isKey(KeyCode.PAGE_UP)) {
            scrollOffset += 5;
            return true;
        }
        if (ke.isKey(KeyCode.PAGE_DOWN)) {
            scrollOffset = Math.max(0, scrollOffset - 5);
            return true;
        }
        if (thinking.get()) {
            if (ke.isCtrlC()) {
                Thread t = agentThread;
                if (t != null) {
                    t.interrupt();
                }
                thinking.set(false);
                conversation.add(new ConversationEntry("system", "(cancelled)"));
                return true;
            }
            return true;
        }
        if (ke.isKey(KeyCode.ENTER)) {
            if (!inputBuffer.isEmpty()) {
                submitQuestion();
            }
            return true;
        }
        if (ke.isKey(KeyCode.BACKSPACE)) {
            if (cursorPos > 0) {
                inputBuffer.deleteCharAt(cursorPos - 1);
                cursorPos--;
            }
            return true;
        }
        if (ke.isKey(KeyCode.DELETE)) {
            if (cursorPos < inputBuffer.length()) {
                inputBuffer.deleteCharAt(cursorPos);
            }
            return true;
        }
        if (ke.isKey(KeyCode.LEFT)) {
            if (cursorPos > 0) {
                cursorPos--;
            }
            return true;
        }
        if (ke.isKey(KeyCode.RIGHT)) {
            if (cursorPos < inputBuffer.length()) {
                cursorPos++;
            }
            return true;
        }
        if (ke.isKey(KeyCode.HOME)) {
            cursorPos = 0;
            return true;
        }
        if (ke.isKey(KeyCode.END)) {
            cursorPos = inputBuffer.length();
            return true;
        }
        if (ke.code() == KeyCode.CHAR && !ke.hasCtrl() && !ke.hasAlt()) {
            inputBuffer.insert(cursorPos, ke.character());
            cursorPos++;
            return true;
        }
        return true;
    }

    private void submitQuestion() {
        String question = inputBuffer.toString().trim();
        inputBuffer.setLength(0);
        cursorPos = 0;
        scrollOffset = 0;

        conversation.add(new ConversationEntry("user", question));
        log(LogLevel.QUESTION, "Question", question);
        thinking.set(true);

        // rebuild tools if target process changed
        long pid = ctx != null && ctx.selectedPid != null ? Long.parseLong(ctx.selectedPid) : -1;
        String name = ctx != null ? ctx.selectedName() : null;
        askTools = new AskTools(pid);
        tools = askTools.buildToolDefinitions();
        String systemPrompt = AskTools.buildSystemPrompt(pid, name);

        agentThread = new Thread(() -> {
            try {
                runAgentLoop(systemPrompt, question);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                conversation.add(new ConversationEntry("error", e.getMessage()));
            } finally {
                thinking.set(false);
                agentThread = null;
            }
        }, "tui-ai-agent");
        agentThread.setDaemon(true);
        agentThread.start();
    }

    private void runAgentLoop(String systemPrompt, String question) throws InterruptedException {
        if (messages == null) {
            messages = new ArrayList<>();
        }
        messages.add(LlmClient.Message.user(question));

        for (int i = 0; i < MAX_ITERATIONS; i++) {
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }

            LlmClient.ChatResponse response = client.chatWithTools(systemPrompt, messages, tools);
            if (response == null) {
                String err = "No response from LLM";
                conversation.add(new ConversationEntry("error", err));
                log(LogLevel.ERROR, "Error", err);
                return;
            }

            // check for error response (null text, no tool calls, error stop reason)
            if ("error".equals(response.stopReason())
                    && (response.toolCalls() == null || response.toolCalls().isEmpty())
                    && response.text() == null) {
                String err = "LLM request failed. Check API key and endpoint.";
                conversation.add(new ConversationEntry("error", err));
                log(LogLevel.ERROR, "Error", err);
                return;
            }

            if (response.toolCalls() != null && !response.toolCalls().isEmpty()) {
                messages.add(LlmClient.Message.assistantWithToolCalls(response.text(), response.toolCalls()));

                List<LlmClient.ToolResult> results = new ArrayList<>();
                for (LlmClient.ToolCall toolCall : response.toolCalls()) {
                    if (Thread.interrupted()) {
                        throw new InterruptedException();
                    }
                    log(LogLevel.TOOL, toolCall.name(), toolCall.arguments().toJson());
                    String result = askTools.executeTool(toolCall.name(), toolCall.arguments());
                    log(LogLevel.RESULT, toolCall.name(), result);
                    results.add(new LlmClient.ToolResult(toolCall.id(), result));
                }
                messages.add(LlmClient.Message.toolResults(results));
            } else {
                String text = response.text();
                if (text != null && !text.isBlank()) {
                    conversation.add(new ConversationEntry("assistant", text));
                    log(LogLevel.RESPONSE, "Response", text);
                } else {
                    String err = "Empty response from LLM.";
                    conversation.add(new ConversationEntry("error", err));
                    log(LogLevel.ERROR, "Error", err);
                }
                scrollOffset = 0;
                messages.add(LlmClient.Message.assistantWithToolCalls(text, List.of()));
                return;
            }
        }
        conversation.add(new ConversationEntry(
                "error",
                "Reached maximum iterations (" + MAX_ITERATIONS + ") without a final answer."));
    }

    void render(Frame frame, Rect area) {
        Block block = Block.builder()
                .borders(Borders.ALL)
                .borderType(BorderType.ROUNDED)
                .title(Title.from(Line.from(Span.styled(" AI ", Style.EMPTY.bold()))))
                .build();
        frame.renderWidget(block, area);
        Rect inner = block.inner(area);
        if (inner.height() < 2) {
            return;
        }

        // Split inner area: conversation (fill) + separator (1 row) + input (1 row) + padding (1 row)
        List<Rect> parts = Layout.vertical()
                .constraints(Constraint.fill(), Constraint.length(1), Constraint.length(1), Constraint.length(1))
                .split(inner);
        Rect conversationArea = parts.get(0);
        Rect separatorArea = parts.get(1);
        Rect inputArea = parts.get(2);
        // parts.get(3) is empty padding row above the bottom border

        renderConversation(frame, conversationArea);
        // horizontal line separator
        String line = "─".repeat(separatorArea.width());
        frame.renderWidget(Paragraph.from(Line.from(Span.styled(line, Style.EMPTY.dim()))),
                separatorArea);
        renderInput(frame, inputArea);
    }

    private void renderConversation(Frame frame, Rect area) {
        if (area.height() < 1) {
            return;
        }

        StringBuilder md = new StringBuilder();

        if (initError != null) {
            md.append("**Error:** ").append(initError).append("\n\n");
        } else if (conversation.isEmpty() && !thinking.get()) {
            frame.renderWidget(
                    Paragraph.from(Line.from(Span.styled("Ask a question about your Camel application...", Style.EMPTY.dim()))),
                    area);
            return;
        }

        for (ConversationEntry entry : conversation) {
            switch (entry.role()) {
                case "user" -> md.append("**You:** ").append(entry.text()).append("\n\n");
                case "assistant" -> md.append(toHardBreaks(entry.text())).append("\n\n");
                case "error" -> md.append("**Error:** ").append(entry.text()).append("\n\n");
                case "system" -> md.append("*").append(entry.text()).append("*\n\n");
                default -> {
                }
            }
        }

        if (thinking.get()) {
            long dots = (System.currentTimeMillis() / 500) % 4;
            md.append("*🤔 thinking").append(".".repeat((int) dots + 1)).append("*\n");
        }

        String source = md.toString();

        // Estimate total rendered lines (accounting for word wrap)
        int contentWidth = Math.max(1, area.width());
        int estimatedLines = 0;
        for (String l : source.split("\n", -1)) {
            estimatedLines += Math.max(1, (l.length() / contentWidth) + 1);
        }

        boolean overflow = estimatedLines > area.height();
        Rect contentArea = area;
        Rect scrollbarArea = null;
        if (overflow) {
            List<Rect> hParts = Layout.horizontal()
                    .constraints(Constraint.fill(), Constraint.length(1))
                    .split(area);
            contentArea = hParts.get(0);
            scrollbarArea = hParts.get(1);
        }

        // scrollOffset=0 means auto-scroll to bottom (most recent content visible)
        // scrollOffset>0 means user scrolled up by that many lines
        int scroll;
        if (scrollOffset == 0) {
            scroll = estimatedLines;
        } else {
            scroll = Math.max(0, estimatedLines - contentArea.height() - scrollOffset);
        }

        MarkdownView view = MarkdownView.builder()
                .source(source)
                .scroll(scroll)
                .build();
        frame.renderWidget(view, contentArea);

        if (overflow && scrollbarArea != null) {
            renderScrollbar(frame, scrollbarArea, estimatedLines, contentArea.height(), scroll);
        }
    }

    private void renderInput(Frame frame, Rect area) {
        String prompt = "> ";
        String text = inputBuffer.toString();

        List<Span> spans = new ArrayList<>();
        spans.add(Span.styled(prompt, Style.EMPTY.fg(Color.CYAN).bold()));

        if (thinking.get()) {
            spans.add(Span.styled(text, Style.EMPTY.dim()));
        } else {
            // Render with cursor
            int maxWidth = area.width() - prompt.length();
            if (maxWidth <= 0) {
                return;
            }
            // Ensure cursor is visible by adjusting text window
            int windowStart = 0;
            if (cursorPos > maxWidth - 1) {
                windowStart = cursorPos - maxWidth + 1;
            }
            String visible = text.substring(windowStart,
                    Math.min(text.length(), windowStart + maxWidth));
            int cursorInWindow = cursorPos - windowStart;

            if (cursorInWindow >= 0 && cursorInWindow < visible.length()) {
                spans.add(Span.raw(visible.substring(0, cursorInWindow)));
                spans.add(Span.styled(String.valueOf(visible.charAt(cursorInWindow)),
                        Style.EMPTY.reversed()));
                spans.add(Span.raw(visible.substring(cursorInWindow + 1)));
            } else {
                spans.add(Span.raw(visible));
                if (cursorInWindow == visible.length()) {
                    spans.add(Span.styled(" ", Style.EMPTY.reversed()));
                }
            }
        }

        frame.renderWidget(Paragraph.from(Line.from(spans)), area);
    }

    void renderFooter(List<Span> spans) {
        MonitorContext.hint(spans, "F8", "close");
        MonitorContext.hint(spans, "Shift+F8", panelPercent() + "%");
        MonitorContext.hint(spans, "PgUp/Dn", "scroll");
        if (!thinking.get()) {
            MonitorContext.hint(spans, "Enter", "send");
        } else {
            MonitorContext.hint(spans, "Ctrl+C", "cancel");
        }
    }

    private void renderScrollbar(Frame frame, Rect area, int totalLines, int visibleHeight, int scroll) {
        int thumbSize = Math.max(1, visibleHeight * visibleHeight / Math.max(1, totalLines));
        int maxScroll = Math.max(1, totalLines - visibleHeight);
        int thumbPos = (int) ((long) Math.min(scroll, maxScroll) * (visibleHeight - thumbSize) / maxScroll);

        List<Line> lines = new ArrayList<>();
        for (int i = 0; i < area.height(); i++) {
            if (i >= thumbPos && i < thumbPos + thumbSize) {
                lines.add(Line.from(Span.styled("▐", Style.EMPTY.fg(Color.CYAN))));
            } else {
                lines.add(Line.from(Span.styled("│", Style.EMPTY.dim())));
            }
        }
        frame.renderWidget(Paragraph.from(new dev.tamboui.text.Text(lines, dev.tamboui.layout.Alignment.LEFT)), area);
    }

    private static String toHardBreaks(String text) {
        if (text == null) {
            return "";
        }
        // Convert single newlines to markdown hard breaks (two trailing spaces + newline)
        // so the LLM's line-by-line formatting is preserved in MarkdownView.
        // Double newlines (paragraph breaks) are left as-is.
        return text.replaceAll("(?<!\n)\n(?!\n)", "  \n");
    }

}
