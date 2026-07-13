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
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Layout;
import dev.tamboui.layout.Rect;
import dev.tamboui.markdown.MarkdownView;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.MouseEvent;
import dev.tamboui.tui.event.MouseEventKind;
import dev.tamboui.widgets.barchart.Bar;
import dev.tamboui.widgets.barchart.BarChart;
import dev.tamboui.widgets.barchart.BarGroup;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.block.Title;
import dev.tamboui.widgets.paragraph.Paragraph;
import dev.tamboui.widgets.spinner.Spinner;
import dev.tamboui.widgets.spinner.SpinnerState;
import dev.tamboui.widgets.spinner.SpinnerStyle;
import dev.tamboui.widgets.table.Cell;
import dev.tamboui.widgets.table.Row;
import dev.tamboui.widgets.table.Table;
import dev.tamboui.widgets.table.TableState;
import org.apache.camel.dsl.jbang.core.commands.AskTools;
import org.apache.camel.dsl.jbang.core.commands.LlmClient;
import org.apache.camel.dsl.jbang.core.common.ExampleHelper;
import org.apache.camel.util.json.JsonObject;

/**
 * AI prompt panel for the TUI. Communicates directly with an LLM via {@link LlmClient} and uses the same tool
 * definitions as {@code camel ask}. Toggled with F8 when the TUI runs with {@code --mcp} mode.
 */
class AiPanel {

    private static final int MAX_ITERATIONS = 10;
    private static final int MAX_LOG_ENTRIES = 200;
    private static final DateTimeFormatter TIME_FMT
            = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final String INPUT_PROMPT = "❯ ";
    private static final List<String> THINKING_VERBS = List.of(
            "Herding thoughts", "Chewing the cud", "Crossing the desert", "Loading the caravan",
            "Sniffing out an oasis", "Trekking onward", "Kicking up sand", "Grazing on context",
            "Following the trail", "Navigating dunes", "Unpacking the saddlebags", "Warming up the hump");

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
    private final PanelAnimation anim = new PanelAnimation();
    private MonitorContext ctx;

    // Input state
    private final StringBuilder inputBuffer = new StringBuilder();
    private int cursorPos;

    // TAB completion cycle state. completionMatches holds the candidate command names for the active cycle;
    // completionSnapshot is the buffer text a TAB press last produced. The cycle continues only while the buffer still
    // equals that snapshot, so any other edit (typing, backspace, cursor move) transparently starts a fresh completion.
    private List<String> completionMatches;
    private int completionCycleIndex = -1;
    private String completionSnapshot;

    // Conversation display. CopyOnWriteArrayList because entries are appended from the agent thread and the
    // CLI-command-completion callback while the render thread iterates the list concurrently.
    private final List<ConversationEntry> conversation = new CopyOnWriteArrayList<>();
    private int scrollOffset;

    // LLM state
    private volatile LlmClient client;
    private List<LlmClient.Message> messages;
    private List<LlmClient.ToolDef> tools;
    private AskTools askTools;
    private final AtomicBoolean thinking = new AtomicBoolean();
    private volatile Thread agentThread;
    private String initError;
    private long thinkingStartTime;
    private volatile String thinkingVerb;
    private volatile int sessionTotalTokens;

    // Slash commands
    private final AiSlashCommandRegistry slashCommands = AiSlashCommandRegistry.defaults();
    private AiSlashCommandContext slashCommandContext = new PanelSlashCommandContext();
    private final AiCliCommandExecutor cliCommandExecutor = new AiCliCommandExecutor();
    private volatile CompletableFuture<AiCliCommandExecutor.Result> activeCliCommand;
    private Runnable exitCallback;

    // Detached launches (/run, /infra run) go through the same LaunchManager the F2 Actions menu uses, so they are
    // spawned as tracked background processes instead of blocking in-process. Null in tests that construct the panel
    // directly; the slash context reports an error in that case.
    private LaunchManager launchManager;
    private volatile List<JsonObject> exampleCatalog;

    // Provider switch popup
    private final AiProviderSwitchPopup providerSwitchPopup = new AiProviderSwitchPopup();
    private final AiProviderSelector providerSelector = new AiProviderSelector();
    private AiProviderSwitchPopup.ProviderChoice sessionProviderChoice;
    private List<AiProviderSwitchPopup.ProviderChoice> providerChoicesForTesting;
    private boolean testingClientInjected;

    // Activity log for AI Log popup
    private final List<LogEntry> activityLog = new ArrayList<>();
    private static final int MOUSE_SCROLL_LINES = 3;
    private Rect lastArea;

    // AI usage stats
    private final List<AiUsageEntry> usageHistory = new CopyOnWriteArrayList<>();
    private final TableState statsTableState = new TableState();
    private boolean statsView;
    private int statsScrollOffset;

    record ConversationEntry(AiRole role, String text, long elapsedSeconds, int totalTokens) {
        ConversationEntry(AiRole role, String text) {
            this(role, text, -1, 0);
        }
    }

    record AiUsageEntry(String model, String provider, int inputTokens, int outputTokens,
            int totalTokens, long latencyMs, String stopReason, Instant timestamp) {
    }

    void setContext(MonitorContext ctx) {
        this.ctx = ctx;
    }

    void setLaunchManager(LaunchManager launchManager) {
        this.launchManager = launchManager;
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

    private long lastResponseElapsed() {
        if (thinking.get() || conversation.isEmpty()) {
            return -1;
        }
        ConversationEntry last = conversation.get(conversation.size() - 1);
        return last.role() == AiRole.ASSISTANT ? last.elapsedSeconds() : -1;
    }

    private int lastResponseTokens() {
        if (thinking.get() || conversation.isEmpty()) {
            return 0;
        }
        ConversationEntry last = conversation.get(conversation.size() - 1);
        return last.role() == AiRole.ASSISTANT ? last.totalTokens() : 0;
    }

    void cycleHeight(int contentHeight) {
        anim.cycleHeight(contentHeight);
    }

    void setPanelHeight(int height) {
        anim.setPanelHeight(height);
    }

    void open() {
        visible = true;
        if (client == null) {
            initClient();
        }
    }

    void close() {
        visible = false;
        providerSwitchPopup.close();
    }

    void destroy() {
        close();
        stopAgentThread();
    }

    private void initClient() {
        try {
            LlmClient created = LlmClient.create()
                    .withTemperature(0.3)
                    .withTimeout(120)
                    .withMaxTokens(4096);
            if (sessionProviderChoice != null) {
                providerSelector.applyChoice(created, sessionProviderChoice.provider(), sessionProviderChoice.model(),
                        sessionProviderChoice.url());
            } else {
                TuiSettings settings = TuiSettings.load();
                providerSelector.applyChoice(created, settings.getAiProvider(), settings.getAiModel(), settings.getAiUrl());
            }
            client = created;
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

    private void applyProviderChoice(AiProviderSwitchPopup.ProviderChoice choice) {
        stopAgentThread();
        sessionProviderChoice = choice;
        if (testingClientInjected && client != null) {
            // Keep tests independent of the locally installed camel-jbang-core artifact.
        } else {
            client = null;
            initClient();
        }
        if (client != null) {
            conversation.add(new ConversationEntry(
                    AiRole.SYSTEM,
                    "Switched to " + displayModel(choice) + " (" + choice.provider() + ")"));
        } else {
            sessionProviderChoice = null;
            conversation.add(new ConversationEntry(
                    AiRole.ERROR,
                    "Failed to switch to " + choice.provider() + ": " + initError));
        }
    }

    private String displayModel(AiProviderSwitchPopup.ProviderChoice choice) {
        return choice.model() == null || choice.model().isBlank() ? "auto" : choice.model();
    }

    /**
     * Persists a model chosen via {@code /model} so it survives a TUI restart, matching the Settings popup. The
     * in-session provider choice is kept in sync so re-initialising the client (for example reopening the panel) does
     * not revert to the previously selected model.
     */
    private void persistModelSelection(String model) {
        TuiSettings settings = TuiSettings.load();
        settings.setAiModel(model);
        settings.save();
        if (sessionProviderChoice != null) {
            sessionProviderChoice = new AiProviderSwitchPopup.ProviderChoice(
                    sessionProviderChoice.provider(), model, sessionProviderChoice.url(),
                    sessionProviderChoice.persistedDefault());
        }
    }

    void openProviderSwitch() {
        providerSwitchPopup.open(providerChoicesForTesting != null
                ? providerChoicesForTesting
                : providerSelector.buildChoices());
    }

    boolean handleMouseEvent(MouseEvent me) {
        if (!visible || lastArea == null) {
            return false;
        }
        if (providerSwitchPopup.isVisible()) {
            return providerSwitchPopup.handleMouseEvent(me);
        }
        if (!TuiHelper.contains(lastArea, me.x(), me.y())) {
            return false;
        }
        if (me.kind() == MouseEventKind.SCROLL_UP) {
            scrollOffset += MOUSE_SCROLL_LINES;
            return true;
        }
        if (me.kind() == MouseEventKind.SCROLL_DOWN) {
            scrollOffset = Math.max(0, scrollOffset - MOUSE_SCROLL_LINES);
            return true;
        }
        return false;
    }

    boolean handleKeyEvent(KeyEvent ke) {
        if (providerSwitchPopup.isVisible()) {
            providerSwitchPopup.handleKeyEvent(ke);
            AiProviderSwitchPopup.ProviderChoice choice = providerSwitchPopup.consumePendingChoice();
            if (choice != null) {
                applyProviderChoice(choice);
            }
            return true;
        }
        if (ke.isKey(KeyCode.F8)) {
            close();
            return true;
        }
        if (isFunctionKey(ke)) {
            return false;
        }
        if (ke.hasCtrl() && ke.isCharIgnoreCase('p') && !thinking.get() && activeCliCommand == null) {
            openProviderSwitch();
            return true;
        }
        if (ke.hasCtrl() && ke.isCharIgnoreCase('u')) {
            statsView = !statsView;
            statsScrollOffset = 0;
            return true;
        }
        if (ke.isKey(KeyCode.PAGE_UP)) {
            if (statsView) {
                statsScrollOffset += 5;
            } else {
                scrollOffset += 5;
            }
            return true;
        }
        if (ke.isKey(KeyCode.PAGE_DOWN)) {
            if (statsView) {
                statsScrollOffset = Math.max(0, statsScrollOffset - 5);
            } else {
                scrollOffset = Math.max(0, scrollOffset - 5);
            }
            return true;
        }
        if (thinking.get()) {
            if (ke.isCtrlC() || ke.isKey(KeyCode.ESCAPE)) {
                interruptBusyOperation();
                return true;
            }
            return true;
        }
        // A background CLI command (e.g. /send) does not put the panel into the thinking state, so handle its
        // cancellation here while the panel stays usable for typing.
        if ((ke.isCtrlC() || ke.isKey(KeyCode.ESCAPE)) && activeCliCommand != null) {
            interruptBusyOperation();
            return true;
        }
        if (ke.isKey(KeyCode.ENTER)) {
            if (!inputBuffer.isEmpty()) {
                submitInput();
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
        if (ke.isKey(KeyCode.TAB)) {
            handleTabCompletion(ke.hasShift());
            return true;
        }
        if (ke.code() == KeyCode.CHAR && !ke.hasCtrl() && !ke.hasAlt()) {
            inputBuffer.insert(cursorPos, ke.character());
            cursorPos++;
            return true;
        }
        return true;
    }

    /**
     * Completes the slash command name at the cursor. With multiple matches, TAB first fills in the longest common
     * prefix; once no further prefix can be added it cycles forward through the matches (wrapping so every match is
     * reachable with TAB alone). A single match is completed fully and a trailing space is appended. Shift+TAB cycles
     * backward. TAB is a no-op unless the buffer is a partial command name (starts with {@code /}, no arguments yet).
     */
    private void handleTabCompletion(boolean backward) {
        String text = inputBuffer.toString();
        boolean continuing = text.equals(completionSnapshot) && completionMatches != null && completionMatches.size() > 1;
        if (continuing) {
            int size = completionMatches.size();
            if (completionCycleIndex < 0) {
                completionCycleIndex = backward ? size - 1 : 0;
            } else {
                completionCycleIndex = backward
                        ? (completionCycleIndex - 1 + size) % size
                        : (completionCycleIndex + 1) % size;
            }
            applyCompletionToken(completionMatches.get(completionCycleIndex), false);
            return;
        }

        List<String> names = slashCommands.completionsFor(text).stream()
                .map(AiSlashCommandRegistry.Descriptor::name)
                .toList();
        if (names.isEmpty()) {
            completionMatches = null;
            completionCycleIndex = -1;
            completionSnapshot = null;
            return;
        }
        if (names.size() == 1) {
            applyCompletionToken(names.get(0), true);
            // A single completion ends with a trailing space, so there is nothing left to cycle.
            completionMatches = null;
            completionCycleIndex = -1;
            completionSnapshot = null;
            return;
        }
        String currentToken = text.substring(1);
        String prefix = longestCommonPrefix(names);
        completionMatches = names;
        if (prefix.length() > currentToken.length()) {
            applyCompletionToken(prefix, false);
            completionCycleIndex = -1;
        } else {
            completionCycleIndex = backward ? names.size() - 1 : 0;
            applyCompletionToken(names.get(completionCycleIndex), false);
        }
    }

    private void applyCompletionToken(String token, boolean trailingSpace) {
        inputBuffer.setLength(0);
        inputBuffer.append('/').append(token);
        if (trailingSpace) {
            inputBuffer.append(' ');
        }
        cursorPos = inputBuffer.length();
        completionSnapshot = inputBuffer.toString();
    }

    private static String longestCommonPrefix(List<String> values) {
        String prefix = values.get(0);
        for (int i = 1; i < values.size() && !prefix.isEmpty(); i++) {
            String value = values.get(i);
            int max = Math.min(prefix.length(), value.length());
            int j = 0;
            while (j < max && prefix.charAt(j) == value.charAt(j)) {
                j++;
            }
            prefix = prefix.substring(0, j);
        }
        return prefix;
    }

    private void submitInput() {
        String input = inputBuffer.toString().trim();
        inputBuffer.setLength(0);
        cursorPos = 0;
        scrollOffset = 0;
        if (input.startsWith("/")) {
            executeSlashCommand(input);
        } else {
            submitQuestion(input);
        }
    }

    private void executeSlashCommand(String input) {
        Optional<AiSlashCommandRegistry.ParsedCommand> parsed = slashCommands.parse(input);
        if (parsed.isPresent() && (thinking.get() || activeCliCommand != null)) {
            String name = parsed.get().descriptor().name();
            if ("provider".equals(name) || "model".equals(name)) {
                conversation.add(new ConversationEntry(
                        AiRole.SYSTEM,
                        "Wait for the current operation to finish before changing provider or model."));
                return;
            }
        }

        AiSlashCommandRegistry.CommandResult result = slashCommands.execute(input, slashCommandContext);
        if (result.cliRequest() != null) {
            AiCliCommandExecutor.Request request = result.cliRequest();
            conversation.add(new ConversationEntry(AiRole.SYSTEM, "Running " + request.displayText()));
            // Runs in the background without entering the panel's "thinking" state, so the user can keep typing
            // and asking questions while the command runs. Esc still cancels it via interruptBusyOperation().
            CompletableFuture<AiCliCommandExecutor.Result> future = slashCommandContext.executeCli(request);
            activeCliCommand = future;
            future.whenComplete(this::handleCliCompletion);
            return;
        }
        if (result.modelListing()) {
            listModelsAsync();
            return;
        }
        if (result.text() != null && !result.text().isBlank()) {
            conversation.add(new ConversationEntry(result.role(), result.text()));
        }
    }

    /**
     * Fetches the available models off the TUI event thread. Model discovery reaches a blocking HTTP call, so running
     * it inline from key-event handling would freeze rendering and input if the provider became slow or unreachable.
     */
    private void listModelsAsync() {
        conversation.add(new ConversationEntry(AiRole.SYSTEM, "Fetching available models..."));
        Thread worker = new Thread(() -> {
            List<String> models = slashCommandContext.availableModels();
            conversation.add(new ConversationEntry(
                    AiRole.SYSTEM,
                    AiSlashCommandRegistry.formatModelListing(slashCommandContext.currentModel(), models)));
        }, "tui-ai-model-list");
        worker.setDaemon(true);
        worker.start();
    }

    private void handleCliCompletion(AiCliCommandExecutor.Result cliResult, Throwable error) {
        CompletableFuture<AiCliCommandExecutor.Result> future = activeCliCommand;
        synchronized (this) {
            if (future == null || activeCliCommand != future) {
                return;
            }
            activeCliCommand = null;
        }
        if (error != null) {
            String displayText = cliResult != null ? cliResult.displayText() : "command";
            conversation.add(new ConversationEntry(
                    AiRole.ERROR, "Failed to run " + displayText + ": " + error.getMessage()));
            scrollOffset = 0;
            return;
        }

        if (cliResult.exitCode() == 0) {
            conversation.add(new ConversationEntry(
                    AiRole.SYSTEM,
                    cliResult.displayText() + " completed in " + cliResult.elapsedMs() + " ms\n\n" + cliResult.output()));
        } else {
            conversation.add(new ConversationEntry(
                    AiRole.ERROR,
                    cliResult.displayText() + " exit code " + cliResult.exitCode() + "\n\n" + cliResult.output()));
        }
        scrollOffset = 0;
    }

    private void interruptBusyOperation() {
        // A background CLI command and an LLM request can be in flight at the same time, so cancel each one
        // independently. Cancelling the CLI must not touch the LLM's thinking state (that belongs to the agent
        // thread, which clears it in its own finally block) and vice versa.
        if (activeCliCommand != null) {
            activeCliCommand = null;
            slashCommandContext.cancelCli();
            conversation.add(new ConversationEntry(AiRole.SYSTEM, "(command cancelled)"));
        }
        if (thinking.get()) {
            stopAgentThread();
            conversation.add(new ConversationEntry(AiRole.SYSTEM, "(cancelled)"));
        }
    }

    private void stopAgentThread() {
        Thread t = agentThread;
        if (t == null) {
            return;
        }
        if (t != Thread.currentThread()) {
            t.interrupt();
            // Do not block the TUI event thread if an HTTP client ignores interruption. The agent thread clears the
            // thinking state in its finally block; the watcher only bounds the wait off the caller's thread.
            Thread watcher = new Thread(() -> awaitAgentThreadStop(t), "tui-ai-agent-cancel-watcher");
            watcher.setDaemon(true);
            watcher.start();
        }
    }

    private void awaitAgentThreadStop(Thread agent) {
        try {
            agent.join(30_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void submitQuestion(String question) {
        stopAgentThread();
        if (client == null) {
            conversation.add(new ConversationEntry(
                    AiRole.ERROR,
                    initError != null ? initError : "No LLM client available. Press Ctrl+P to pick a provider."));
            return;
        }
        conversation.add(new ConversationEntry(AiRole.USER, question));
        log(LogLevel.QUESTION, "Question", question);
        thinkingVerb = THINKING_VERBS.get(ThreadLocalRandom.current().nextInt(THINKING_VERBS.size()));
        thinkingStartTime = System.currentTimeMillis();
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
                conversation.add(new ConversationEntry(AiRole.ERROR, e.getMessage()));
            } finally {
                if (agentThread == Thread.currentThread()) {
                    thinking.set(false);
                    agentThread = null;
                }
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

        LlmClient.TokenUsage totalUsage = LlmClient.TokenUsage.EMPTY;
        for (int i = 0; i < MAX_ITERATIONS; i++) {
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }

            long callStart = System.currentTimeMillis();
            LlmClient.ChatResponse response = client.chatWithTools(systemPrompt, messages, tools);
            long callLatency = System.currentTimeMillis() - callStart;
            if (response == null) {
                String err = "No response from LLM";
                conversation.add(new ConversationEntry(AiRole.ERROR, err));
                log(LogLevel.ERROR, "Error", err);
                return;
            }
            totalUsage = totalUsage.add(response.usage());
            recordUsage(response, callLatency);

            // check for error response (null text, no tool calls, error stop reason)
            if ("error".equals(response.stopReason())
                    && (response.toolCalls() == null || response.toolCalls().isEmpty())
                    && response.text() == null) {
                String err = "LLM request failed. Check API key and endpoint.";
                conversation.add(new ConversationEntry(AiRole.ERROR, err));
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
                sessionTotalTokens += totalUsage.totalTokens();
                if (text != null && !text.isBlank()) {
                    long elapsed = (System.currentTimeMillis() - thinkingStartTime) / 1000;
                    conversation.add(new ConversationEntry(AiRole.ASSISTANT, text, elapsed, totalUsage.totalTokens()));
                    String tokenInfo = totalUsage.totalTokens() > 0
                            ? ", " + LlmClient.formatTokens(totalUsage.totalTokens()) + " tokens"
                            : "";
                    log(LogLevel.RESPONSE, "Response (" + elapsed + "s" + tokenInfo + ")", text);
                } else {
                    String err = "Empty response from LLM.";
                    conversation.add(new ConversationEntry(AiRole.ERROR, err));
                    log(LogLevel.ERROR, "Error", err);
                }
                scrollOffset = 0;
                messages.add(LlmClient.Message.assistantWithToolCalls(text, List.of()));
                return;
            }
        }
        conversation.add(new ConversationEntry(
                AiRole.ERROR,
                "Reached maximum iterations (" + MAX_ITERATIONS + ") without a final answer."));
    }

    private void recordUsage(LlmClient.ChatResponse response, long latencyMs) {
        if (client == null || response.usage().totalTokens() == 0) {
            return;
        }
        String model = client.model() != null ? client.model() : "unknown";
        String provider = client.apiType() != null ? client.apiType().name() : "unknown";
        usageHistory.add(new AiUsageEntry(
                model, provider,
                response.usage().inputTokens(), response.usage().outputTokens(),
                response.usage().totalTokens(), latencyMs,
                response.stopReason(), Instant.now()));
    }

    void render(Frame frame, Rect area) {
        lastArea = area;
        // At 25% show elapsed and tokens in the title bar to save space
        long titleElapsed = lastResponseElapsed();
        int titleTokens = lastResponseTokens();
        Line titleLine;
        if (statsView) {
            titleLine = Line.from(Span.styled(" AI Usage ", Style.EMPTY.bold()));
        } else if (anim.cyclePercent() == 25 && titleElapsed >= 0) {
            String tokenSuffix = titleTokens > 0 ? ", " + LlmClient.formatTokens(titleTokens) + " tokens" : "";
            titleLine = Line.from(
                    Span.styled(" AI ", Style.EMPTY.bold()),
                    Span.styled("(" + titleElapsed + "s" + tokenSuffix + ") ", Style.EMPTY.dim()));
        } else if (sessionTotalTokens > 0) {
            titleLine = Line.from(
                    Span.styled(" AI ", Style.EMPTY.bold()),
                    Span.styled("(total: " + LlmClient.formatTokens(sessionTotalTokens) + " tokens) ", Style.EMPTY.dim()));
        } else {
            titleLine = Line.from(Span.styled(" AI ", Style.EMPTY.bold()));
        }

        Block block = Block.builder()
                .borders(Borders.ALL)
                .borderType(BorderType.ROUNDED)
                .title(Title.from(titleLine))
                .build();
        frame.renderWidget(block, area);
        Rect inner = block.inner(area);
        if (inner.height() < 2) {
            return;
        }

        if (statsView) {
            renderStats(frame, inner);
            if (providerSwitchPopup.isVisible()) {
                providerSwitchPopup.render(frame, inner);
            }
            return;
        }

        // Split inner area: conversation (fill) + optional slash hints + separator (1 row) + input (1 row)
        List<AiSlashCommandRegistry.Descriptor> slashHints = slashCommandHints();
        int hintRows = slashHints.isEmpty() ? 0 : slashHints.size();
        List<Rect> parts;
        if (hintRows == 0) {
            parts = Layout.vertical()
                    .constraints(Constraint.fill(), Constraint.length(1), Constraint.length(1))
                    .split(inner);
        } else {
            parts = Layout.vertical()
                    .constraints(Constraint.fill(), Constraint.length(hintRows), Constraint.length(1), Constraint.length(1))
                    .split(inner);
        }
        Rect conversationArea = parts.get(0);
        Rect separatorArea = parts.get(hintRows == 0 ? 1 : 2);
        Rect inputArea = parts.get(hintRows == 0 ? 2 : 3);

        renderConversation(frame, conversationArea, !slashHints.isEmpty());
        if (hintRows > 0) {
            renderSlashCommandHints(frame, parts.get(1), slashHints);
        }
        // horizontal line separator
        String line = "─".repeat(separatorArea.width());
        frame.renderWidget(Paragraph.from(Line.from(Span.styled(line, Style.EMPTY.dim()))),
                separatorArea);
        renderInput(frame, inputArea);
        if (providerSwitchPopup.isVisible()) {
            providerSwitchPopup.render(frame, inner);
        }
    }

    private List<AiSlashCommandRegistry.Descriptor> slashCommandHints() {
        if (thinking.get() || statsView || providerSwitchPopup.isVisible()) {
            return List.of();
        }
        return slashCommands.completionsFor(inputBuffer.toString());
    }

    private void renderSlashCommandHints(Frame frame, Rect area, List<AiSlashCommandRegistry.Descriptor> hints) {
        if (area.height() < 1 || hints.isEmpty()) {
            return;
        }
        int commandWidth = AiSlashCommandRegistry.commandColumnWidth(hints);
        int rows = Math.min(area.height(), hints.size());
        for (int i = 0; i < rows; i++) {
            AiSlashCommandRegistry.Descriptor descriptor = hints.get(i);
            String command = AiSlashCommandRegistry.commandLabel(descriptor);
            String description = AiSlashCommandRegistry.descriptionLabel(descriptor);
            List<Span> spans = new ArrayList<>();
            spans.add(Span.styled(command, Style.EMPTY.fg(Theme.accent())));
            if (!description.isEmpty()) {
                int padding = Math.max(2, commandWidth - command.length() + 2);
                spans.add(Span.raw(" ".repeat(padding)));
                spans.add(Span.styled(description, Style.EMPTY.dim()));
            }
            frame.renderWidget(Paragraph.from(Line.from(spans)), new Rect(area.left(), area.top() + i, area.width(), 1));
        }
    }

    private void renderConversation(Frame frame, Rect area, boolean slashHintsVisible) {
        if (area.height() < 1) {
            return;
        }

        StringBuilder md = new StringBuilder();

        if (initError != null) {
            md.append("**Error:** ").append(initError).append("\n\n");
        } else if (conversation.isEmpty() && !thinking.get() && !slashHintsVisible) {
            frame.renderWidget(
                    Paragraph.from(Line.from(Span.styled("Ask a question about your Camel application...", Style.EMPTY.dim()))),
                    area);
            return;
        }

        for (ConversationEntry entry : conversation) {
            switch (entry.role()) {
                case USER -> md.append("**You:** ").append(entry.text()).append("\n\n");
                case ASSISTANT -> md.append("**TUI:** ").append(toHardBreaks(entry.text())).append("\n\n");
                case ERROR -> md.append("**Error:** ").append(entry.text()).append("\n\n");
                case SYSTEM -> md.append(toHardBreaks(entry.text())).append("\n\n");
            }
        }

        // Show elapsed time and token count as a dimmed line below the markdown when at the bottom
        long lastElapsed = -1;
        int lastTokens = 0;
        if (!thinking.get() && !conversation.isEmpty()) {
            ConversationEntry last = conversation.get(conversation.size() - 1);
            if (last.role() == AiRole.ASSISTANT && last.elapsedSeconds() >= 0) {
                lastElapsed = last.elapsedSeconds();
                lastTokens = last.totalTokens();
            }
        }

        // Reserve 1 row for dimmed elapsed time (skip at 25% — shown in title bar instead)
        Rect mdArea = area;
        Rect statusArea = null;
        Rect elapsedArea = null;
        if (thinking.get() && area.height() > 2) {
            List<Rect> vParts = Layout.vertical()
                    .constraints(Constraint.fill(), Constraint.length(1))
                    .split(area);
            mdArea = vParts.get(0);
            statusArea = vParts.get(1);
        } else if (lastElapsed >= 0 && anim.cyclePercent() > 25 && area.height() > 2) {
            List<Rect> vParts = Layout.vertical()
                    .constraints(Constraint.fill(), Constraint.length(1))
                    .split(area);
            mdArea = vParts.get(0);
            elapsedArea = vParts.get(1);
        }

        String source = md.toString();

        // Measure the exact rendered height with MarkdownView's own word-wrap accounting rather than estimating from
        // character counts. A rough estimate under-counts wrapped lines, so auto-scrolling to the bottom left the last
        // couple of lines hidden below the visible area.
        MarkdownView.Builder viewBuilder = MarkdownView.builder()
                .source(source)
                .styles(Theme.markdownStyles());
        MarkdownView measure = viewBuilder.build();
        int totalLines = measure.computeHeight(mdArea.width());

        boolean overflow = totalLines > mdArea.height();
        Rect contentArea = mdArea;
        Rect scrollbarArea = null;
        if (overflow) {
            List<Rect> hParts = Layout.horizontal()
                    .constraints(Constraint.fill(), Constraint.length(1))
                    .split(mdArea);
            contentArea = hParts.get(0);
            scrollbarArea = hParts.get(1);
            // The scrollbar column narrows the content, which can change the wrapping, so re-measure at that width.
            totalLines = measure.computeHeight(contentArea.width());
        }

        // scrollOffset=0 means auto-scroll to bottom (most recent content visible)
        // scrollOffset>0 means user scrolled up by that many lines
        // Clamp so PgDn always has immediate effect after scrolling past the top
        int maxScrollOffset = Math.max(0, totalLines - contentArea.height());
        scrollOffset = Math.min(scrollOffset, maxScrollOffset);

        int scroll = Math.max(0, maxScrollOffset - scrollOffset);

        MarkdownView view = viewBuilder.scroll(scroll).build();
        frame.renderWidget(view, contentArea);

        if (overflow && scrollbarArea != null) {
            renderScrollbar(frame, scrollbarArea, totalLines, contentArea.height(), scroll);
        }

        if (elapsedArea != null && lastElapsed >= 0) {
            String tokenSuffix = lastTokens > 0 ? ", " + LlmClient.formatTokens(lastTokens) + " tokens" : "";
            frame.renderWidget(
                    Paragraph.from(Line.from(Span.styled("(" + lastElapsed + "s" + tokenSuffix + ")", Style.EMPTY.dim()))),
                    elapsedArea);
        }
        if (statusArea != null) {
            renderThinkingStatus(frame, statusArea);
        }
    }

    private void renderThinkingStatus(Frame frame, Rect area) {
        long elapsed = (System.currentTimeMillis() - thinkingStartTime) / 1000;
        Spinner spinner = Spinner.builder()
                .spinnerStyle(SpinnerStyle.DOTS)
                .style(Style.EMPTY.fg(Theme.accent()).bold())
                .build();
        Rect spinnerArea = new Rect(area.left(), area.top(), 2, 1);
        frame.renderStatefulWidget(spinner, spinnerArea, new SpinnerState(System.currentTimeMillis() / 100));

        long dots = (System.currentTimeMillis() / 500) % 4;
        String text = " " + (thinkingVerb != null ? thinkingVerb : THINKING_VERBS.get(0));
        if (elapsed > 0) {
            text += " (" + elapsed + "s)";
        }
        text += ".".repeat((int) dots + 1);
        frame.renderWidget(Paragraph.from(Line.from(Span.styled(text, Style.EMPTY.fg(Theme.accent())))),
                new Rect(area.left() + 2, area.top(), Math.max(0, area.width() - 2), 1));
    }

    private void renderInput(Frame frame, Rect area) {
        String prompt = INPUT_PROMPT;
        String text = inputBuffer.toString();

        List<Span> spans = new ArrayList<>();
        spans.add(Span.styled(prompt, Style.EMPTY.fg(Theme.accent()).bold()));

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
            if (cursorPos == text.length()) {
                Optional<String> placeholder = slashCommands.placeholderFor(text);
                if (placeholder.isPresent()) {
                    spans.add(Span.styled(" " + placeholder.get(), Style.EMPTY.dim()));
                }
            }
        }

        frame.renderWidget(Paragraph.from(Line.from(spans)), area);
    }

    void renderFooter(List<Span> spans) {
        TuiHelper.hint(spans, "F8", "close");
        if (statsView) {
            TuiHelper.hint(spans, "Ctrl+U", "chat");
        } else {
            TuiHelper.hint(spans, "Ctrl+U", "usage");
        }
        TuiHelper.hint(spans, "Shift+F8", "resize (" + anim.cyclePercent() + "%)");
        TuiHelper.hint(spans, "PgUp/Dn", "scroll");
        if (!statsView) {
            TuiHelper.hint(spans, "Ctrl+P", "provider");
            if (!thinking.get()) {
                TuiHelper.hint(spans, "Enter", "send");
            } else {
                TuiHelper.hint(spans, "Esc/Ctrl+C", "interrupt");
            }
        }
    }

    private void renderStats(Frame frame, Rect area) {
        if (area.height() < 3) {
            return;
        }

        if (usageHistory.isEmpty()) {
            frame.renderWidget(
                    Paragraph.from(Line.from(Span.styled("No AI usage data yet. Ask a question first.", Style.EMPTY.dim()))),
                    area);
            return;
        }

        // Compute aggregates
        int totalInput = 0;
        int totalOutput = 0;
        int totalTokens = 0;
        long totalLatency = 0;
        for (AiUsageEntry e : usageHistory) {
            totalInput += e.inputTokens();
            totalOutput += e.outputTokens();
            totalTokens += e.totalTokens();
            totalLatency += e.latencyMs();
        }
        int requestCount = usageHistory.size();

        // Per-model aggregation
        Map<String, int[]> perModel = new LinkedHashMap<>();
        for (AiUsageEntry e : usageHistory) {
            String key = e.model() + " (" + e.provider() + ")";
            int[] stats = perModel.computeIfAbsent(key, k -> new int[4]);
            stats[0]++; // requests
            stats[1] += e.inputTokens();
            stats[2] += e.outputTokens();
            stats[3] += e.totalTokens();
        }

        // Per-conversation token totals (group by consecutive runs between user questions)
        List<Integer> turnTokens = new ArrayList<>();
        int currentTurn = 0;
        int turnIndex = 0;
        for (AiUsageEntry e : usageHistory) {
            if (turnIndex > 0) {
                AiUsageEntry prev = usageHistory.get(turnIndex - 1);
                long gap = e.timestamp().toEpochMilli() - prev.timestamp().toEpochMilli();
                if (gap > 30_000) {
                    turnTokens.add(currentTurn);
                    currentTurn = 0;
                }
            }
            currentTurn += e.totalTokens();
            turnIndex++;
        }
        if (currentTurn > 0) {
            turnTokens.add(currentTurn);
        }

        // Layout: summary (2 rows) + model table (header + models + 1 blank) + chart (fill)
        int tableRows = perModel.size() + 1;
        int summaryRows = 2;
        int chartMinRows = 4;
        boolean hasChart = area.height() > summaryRows + tableRows + chartMinRows + 1;

        List<Constraint> constraints = new ArrayList<>();
        constraints.add(Constraint.length(summaryRows));
        constraints.add(Constraint.length(tableRows + 1));
        if (hasChart) {
            constraints.add(Constraint.fill());
        }
        List<Rect> sections = Layout.vertical()
                .constraints(constraints)
                .split(area);

        // --- Summary ---
        Rect summaryArea = sections.get(0);
        Style dimStyle = Theme.muted();
        Style cyanStyle = Style.EMPTY.fg(Theme.accent());
        List<Line> summaryLines = new ArrayList<>();
        summaryLines.add(Line.from(
                Span.styled("Requests: ", dimStyle),
                Span.styled(String.valueOf(requestCount), cyanStyle),
                Span.styled("   Total tokens: ", dimStyle),
                Span.styled(LlmClient.formatTokens(totalTokens), cyanStyle),
                Span.styled(" (in: ", dimStyle),
                Span.styled(LlmClient.formatTokens(totalInput), Theme.success()),
                Span.styled(" / out: ", dimStyle),
                Span.styled(LlmClient.formatTokens(totalOutput), Theme.label()),
                Span.styled(")", dimStyle)));
        summaryLines.add(Line.from(
                Span.styled("Avg latency: ", dimStyle),
                Span.styled((totalLatency / requestCount / 1000) + "s", cyanStyle),
                Span.styled("   Total time: ", dimStyle),
                Span.styled((totalLatency / 1000) + "s", cyanStyle)));
        frame.renderWidget(
                Paragraph.from(new dev.tamboui.text.Text(summaryLines, dev.tamboui.layout.Alignment.LEFT)),
                summaryArea);

        // --- Per-model table ---
        Rect tableArea = sections.get(1);
        List<Row> rows = new ArrayList<>();
        for (Map.Entry<String, int[]> entry : perModel.entrySet()) {
            int[] s = entry.getValue();
            rows.add(Row.from(
                    Cell.from(Span.styled(entry.getKey(), cyanStyle)),
                    Cell.from(String.valueOf(s[0])),
                    Cell.from(LlmClient.formatTokens(s[1])),
                    Cell.from(LlmClient.formatTokens(s[2])),
                    Cell.from(LlmClient.formatTokens(s[3]))));
        }
        Table table = Table.builder()
                .rows(rows)
                .header(Row.from(
                        Cell.from(Span.styled("MODEL", Style.EMPTY.bold())),
                        Cell.from(Span.styled("REQS", Style.EMPTY.bold())),
                        Cell.from(Span.styled("INPUT", Style.EMPTY.bold())),
                        Cell.from(Span.styled("OUTPUT", Style.EMPTY.bold())),
                        Cell.from(Span.styled("TOTAL", Style.EMPTY.bold()))))
                .widths(
                        Constraint.fill(),
                        Constraint.length(6),
                        Constraint.length(8),
                        Constraint.length(8),
                        Constraint.length(8))
                .build();
        frame.renderStatefulWidget(table, tableArea, statsTableState);

        // --- Token bar chart per turn ---
        if (hasChart && turnTokens.size() > 1) {
            Rect chartArea = sections.get(2);

            // Title row + chart
            List<Rect> chartParts = Layout.vertical()
                    .constraints(Constraint.length(1), Constraint.fill())
                    .split(chartArea);
            frame.renderWidget(
                    Paragraph.from(Line.from(Span.styled("Tokens per question:", Style.EMPTY.bold()))),
                    chartParts.get(0));

            Rect barArea = chartParts.get(1);
            int maxTokensInTurn = turnTokens.stream().mapToInt(Integer::intValue).max().orElse(1);

            // Limit bars to available width
            int maxBars = Math.max(1, barArea.width() / 2);
            int startIdx = Math.max(0, turnTokens.size() - maxBars);
            List<BarGroup> groups = new ArrayList<>();
            for (int i = startIdx; i < turnTokens.size(); i++) {
                groups.add(BarGroup.of(
                        Bar.builder()
                                .value(turnTokens.get(i))
                                .textValue("")
                                .style(Style.EMPTY.fg(Theme.accent()))
                                .build()));
            }

            BarChart barChart = BarChart.builder()
                    .data(groups)
                    .max(maxTokensInTurn + 2)
                    .barWidth(1)
                    .barGap(1)
                    .groupGap(0)
                    .build();
            frame.renderWidget(barChart, barArea);
        }
    }

    private void renderScrollbar(Frame frame, Rect area, int totalLines, int visibleHeight, int scroll) {
        int thumbSize = Math.max(1, visibleHeight * visibleHeight / Math.max(1, totalLines));
        int maxScroll = Math.max(1, totalLines - visibleHeight);
        int thumbPos = (int) ((long) Math.min(scroll, maxScroll) * (visibleHeight - thumbSize) / maxScroll);

        List<Line> lines = new ArrayList<>();
        for (int i = 0; i < area.height(); i++) {
            if (i >= thumbPos && i < thumbPos + thumbSize) {
                lines.add(Line.from(Span.styled("▐", Style.EMPTY.fg(Theme.accent()))));
            } else {
                lines.add(Line.from(Span.styled("│", Style.EMPTY.dim())));
            }
        }
        frame.renderWidget(Paragraph.from(new dev.tamboui.text.Text(lines, dev.tamboui.layout.Alignment.LEFT)), area);
    }

    private static boolean isFunctionKey(KeyEvent ke) {
        KeyCode code = ke.code();
        return code == KeyCode.F1 || code == KeyCode.F2 || code == KeyCode.F3
                || code == KeyCode.F4 || code == KeyCode.F5 || code == KeyCode.F6
                || code == KeyCode.F7 || code == KeyCode.F9 || code == KeyCode.F10
                || code == KeyCode.F11 || code == KeyCode.F12;
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

    void executeSlashCommandForTesting(String input) {
        executeSlashCommand(input);
    }

    void clearConversation() {
        conversation.clear();
        activityLog.clear();
        inputBuffer.setLength(0);
        cursorPos = 0;
        scrollOffset = 0;
        usageHistory.clear();
        statsScrollOffset = 0;
        sessionTotalTokens = 0;
        if (messages != null) {
            messages.clear();
        }
    }

    void setClientForTesting(LlmClient client) {
        this.client = client;
        this.initError = null;
        this.messages = new ArrayList<>();
        this.testingClientInjected = true;
    }

    void setSlashCommandContextForTesting(AiSlashCommandContext context) {
        this.slashCommandContext = context;
    }

    AiSlashCommandRegistry slashCommandRegistryForTesting() {
        return slashCommands;
    }

    List<ConversationEntry> conversationForTesting() {
        return List.copyOf(conversation);
    }

    int sessionTotalTokensForTesting() {
        return sessionTotalTokens;
    }

    int messageCountForTesting() {
        return messages == null ? 0 : messages.size();
    }

    boolean isThinkingForTesting() {
        return thinking.get();
    }

    String thinkingVerbForTesting() {
        return thinkingVerb;
    }

    String inputPromptForTesting() {
        return INPUT_PROMPT;
    }

    String inputBufferForTesting() {
        return inputBuffer.toString();
    }

    void setExitCallbackForTestingOrRuntime(Runnable callback) {
        this.exitCallback = callback;
    }

    void setProviderChoicesForTesting(List<AiProviderSwitchPopup.ProviderChoice> choices) {
        this.providerChoicesForTesting = choices;
    }

    boolean isAgentThreadRunningForTesting() {
        Thread t = agentThread;
        return t != null && t.isAlive();
    }

    boolean isProviderSwitchVisibleForTesting() {
        return providerSwitchPopup.isVisible();
    }

    List<AiProviderSwitchPopup.ProviderChoice> buildProviderChoicesForTesting() {
        return providerSelector.buildChoices();
    }

    private final class PanelSlashCommandContext implements AiSlashCommandContext {

        @Override
        public void closePanel() {
            close();
        }

        @Override
        public void requestExit() {
            if (exitCallback != null) {
                exitCallback.run();
            }
        }

        @Override
        public void openProviderSwitch() {
            AiPanel.this.openProviderSwitch();
        }

        @Override
        public void clearConversation() {
            AiPanel.this.clearConversation();
        }

        @Override
        public String currentModel() {
            return client != null && client.model() != null ? client.model() : "unknown";
        }

        @Override
        public List<String> availableModels() {
            return client != null ? client.listModels() : List.of();
        }

        @Override
        public boolean switchModel(String model) {
            if (client == null) {
                return false;
            }
            client.withModel(model);
            persistModelSelection(model);
            return true;
        }

        @Override
        public String selectedProcessName() {
            return ctx != null ? ctx.selectedName() : null;
        }

        @Override
        public CompletableFuture<AiCliCommandExecutor.Result> executeCli(AiCliCommandExecutor.Request request) {
            return cliCommandExecutor.executeAsync(request);
        }

        @Override
        public void cancelCli() {
            cliCommandExecutor.cancel();
        }

        @Override
        public String launchDetached(AiSlashCommandRegistry.LaunchSpec spec) {
            if (launchManager == null) {
                throw new IllegalStateException("Launching commands is not available in this session.");
            }
            JsonObject example = spec.exampleName() != null ? findExample(spec.exampleName()) : null;
            if (example != null) {
                List<String> missing = launchManager.findMissingInfraServices(example);
                if (!missing.isEmpty()) {
                    if (!LaunchManager.isContainerRuntimeAvailable()) {
                        throw new IllegalStateException(
                                "Docker/Podman required for infra services: " + String.join(", ", missing));
                    }
                    launchManager.startMissingInfraAndDefer(
                            missing, spec.displayName(), () -> launchDetachedQuietly(spec));
                    return "Starting infra: " + String.join(", ", missing) + " → then: " + spec.displayName();
                }
            }
            try {
                launchManager.launchDetached(spec.displayName(), spec.camelArgs());
            } catch (IOException e) {
                throw new IllegalStateException(
                        "Failed to start: " + spec.displayName() + " - " + e.getMessage(), e);
            }
            return "Started: " + spec.displayName();
        }
    }

    /**
     * Launches a deferred spec (after its infra services have started) without propagating failures, since this runs
     * from {@link LaunchManager#tick(long)} where there is no slash-command result to surface. Errors are reported in
     * the conversation instead.
     */
    private void launchDetachedQuietly(AiSlashCommandRegistry.LaunchSpec spec) {
        try {
            launchManager.launchDetached(spec.displayName(), spec.camelArgs());
        } catch (IOException e) {
            conversation.add(new ConversationEntry(
                    AiRole.ERROR, "Failed to start: " + spec.displayName() + " - " + e.getMessage()));
        }
    }

    /**
     * Looks up a catalog example by its full name (e.g. {@code beginner/timer-log}) so its required infra services can
     * be determined before launching. The catalog is loaded lazily and cached. Returns {@code null} when the example is
     * unknown or the catalog cannot be loaded, in which case the launch proceeds without infra auto-start.
     */
    private JsonObject findExample(String name) {
        try {
            List<JsonObject> catalog = exampleCatalog;
            if (catalog == null) {
                catalog = ExampleHelper.loadCatalog();
                exampleCatalog = catalog;
            }
            return catalog.stream()
                    .filter(example -> name.equals(example.getStringOrDefault("name", "")))
                    .findFirst()
                    .orElse(null);
        } catch (RuntimeException e) {
            return null;
        }
    }

}
