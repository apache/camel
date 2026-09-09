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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

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
import org.apache.camel.dsl.jbang.core.commands.LlmClient;
import org.apache.camel.dsl.jbang.core.common.ExampleHelper;
import org.apache.camel.dsl.jbang.core.common.Printer;
import org.apache.camel.util.json.JsonObject;

/**
 * AI prompt panel for the TUI. Communicates directly with an LLM via {@link LlmClient} and uses TUI-specific tools
 * backed by {@link McpFacade} for observing and interacting with the monitored Camel integrations. Toggled with F8.
 */
class AiPanel {

    private static final int MAX_ITERATIONS = 10;
    /**
     * A tool call repeated this many times with identical arguments in one turn is not executed again; the model gets a
     * note instead, so a stuck loop ends with an explanation rather than at the iteration limit.
     */
    static final int MAX_IDENTICAL_TOOL_CALLS = 3;
    /**
     * Longest tool result handed to the model. The AI log keeps the full text; the model gets the head plus a note,
     * because a single log or table dump can otherwise be larger than the whole system prompt.
     */
    static final int MAX_TOOL_RESULT_CHARS = 16_000;
    /**
     * Tool results from turns before the previous one are shrunk to this many characters once the turn is answered. The
     * model's own answer already summarises them, and the whole history is re-sent (and re-processed by a local model)
     * on every request.
     */
    static final int COMPACT_TOOL_RESULT_CHARS = 400;
    /**
     * Oldest turns are dropped beyond this many user questions in one conversation.
     */
    static final int MAX_HISTORY_TURNS = 20;
    /**
     * With a local endpoint the history is left untouched until it is estimated to exceed this many tokens. A local
     * server (Ollama) keeps the KV cache of the previous request, so a request that merely extends the conversation
     * only pays for its new tokens, whereas rewriting an earlier message forces the whole tail from that point to be
     * processed again (measured at one to two seconds per question with a 35B MoE model on Apple silicon, against 0.2s
     * when the history is untouched). Compacting saves counted tokens, which is what a hosted API bills for, but costs
     * time locally, so it is deferred until the context actually needs the room: half of the 32k window the client
     * requests from Ollama, leaving space for the static prefix and the current turn's tool results.
     */
    static final int LOCAL_HISTORY_BUDGET_TOKENS = 16_000;
    private static final int MAX_LOG_ENTRIES = 200;
    private static final DateTimeFormatter TIME_FMT
            = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final String INPUT_PROMPT = "❯ ";
    static final String TOOL_MODE_AUTO = "auto";
    static final String TOOL_MODE_CORE = "core";
    static final String TOOL_MODE_FULL = "full";
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
    private TuiPromptHistory promptHistory;

    // Reverse-i-search state (Ctrl+R)
    private boolean historySearchActive;
    private final StringBuilder searchTerm = new StringBuilder();
    private int searchIndex = -1;
    private String savedInput;

    // TAB completion cycle state. completionMatches holds the candidate command names for the active cycle;
    // completionSnapshot is the buffer text a TAB press last produced. The cycle continues only while the buffer still
    // equals that snapshot, so any other edit (typing, backspace, cursor move) transparently starts a fresh completion.
    private List<String> completionMatches;
    private int completionCycleIndex = -1;
    private String completionSnapshot;
    // Buffer offset of the token being completed: 1 for a command name (after the slash), or the start of the
    // first argument for commands that complete arguments (/model, /tools).
    private int completionStart = 1;
    // Models offered by TAB after "/model ". Fetched once in the background (a provider round trip) on the first TAB
    // and reset whenever the client or provider changes.
    private volatile List<String> modelCompletionCache;
    private final AtomicBoolean modelCompletionFetch = new AtomicBoolean();

    // Conversation display. CopyOnWriteArrayList because entries are appended from the agent thread and the
    // CLI-command-completion callback while the render thread iterates the list concurrently.
    private final List<ConversationEntry> conversation = new CopyOnWriteArrayList<>();
    private int scrollOffset;

    // LLM state
    private volatile LlmClient client;
    private List<LlmClient.Message> messages;
    private List<LlmClient.ToolDef> tools;
    private final AtomicBoolean thinking = new AtomicBoolean();
    private volatile Thread agentThread;
    private String initError;
    private long thinkingStartTime;
    private volatile String thinkingVerb;
    private volatile int sessionTotalTokens;
    private volatile long sessionToolTimeMs;
    private volatile int sessionToolCalls;
    /** Per answered question: [aiMs, toolMs, toolCalls], in order, for the time chart in the usage view. */
    private final List<long[]> turnTimings = new CopyOnWriteArrayList<>();

    // Slash commands
    private final AiSlashCommandRegistry slashCommands = AiSlashCommandRegistry.defaults();
    private AiSlashCommandContext slashCommandContext = new PanelSlashCommandContext();
    // Lines the LLM client prints while detecting the endpoint or failing a request (HTTP status, provider error
    // message, auto-selected model). The TUI hides stdout, so they are collected here and shown with the error.
    private final Deque<String> clientOutput = new ArrayDeque<>();
    private final Printer clientPrinter = new Printer() {
        @Override
        public void println() {
        }

        @Override
        public void println(String line) {
            print(line);
        }

        @Override
        public void print(String output) {
            if (output == null || output.isBlank()) {
                return;
            }
            synchronized (clientOutput) {
                clientOutput.addLast(output.strip());
                while (clientOutput.size() > 6) {
                    clientOutput.removeFirst();
                }
            }
        }

        @Override
        public void printf(String format, Object... args) {
            print(String.format(format, args));
        }
    };
    // auto | core | full, see useCoreTools(); loaded from camel.tui.ai.tools, null means auto
    private volatile String toolMode;
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

    // MCP facade for TUI tool access from the AI panel
    private McpFacade mcpFacade;
    private TuiToolRegistry toolRegistry;
    private boolean mcpServerActive;
    private int mcpServerPort;

    // Activity log for AI Log popup
    private final List<LogEntry> activityLog = new ArrayList<>();
    private static final int MOUSE_SCROLL_LINES = 3;
    private Rect lastArea;

    // AI usage stats
    private final List<AiUsageEntry> usageHistory = new CopyOnWriteArrayList<>();
    /** Sequence number of the current question; tags the usage entries recorded while answering it. */
    private volatile int questionCounter;
    /** Route usage (GenAI spans) recorded before this instant is left out after a {@code /usage reset}. */
    private volatile Instant usageResetAt = Instant.EPOCH;
    private AtomicReference<List<SpanEntry>> otelSpans = new AtomicReference<>(List.of());
    private final TableState statsTableState = new TableState();
    private boolean statsView;
    private int statsScrollOffset;
    boolean spanRefreshRequested;

    enum AiUsageSource {
        TUI,
        ROUTE
    }

    /**
     * One conversation line. For assistant replies {@code elapsedMs} is the wall-clock time of the whole turn,
     * {@code aiMs} the part spent waiting for the model and {@code toolMs} the part spent executing the
     * {@code toolCalls} TUI tool calls the model made.
     */
    record ConversationEntry(AiRole role, String text, long elapsedMs, long aiMs, long toolMs, int toolCalls,
            int totalTokens) {
        ConversationEntry(AiRole role, String text) {
            this(role, text, -1, 0, 0, 0, 0);
        }

        /** "5.2s" or, when tools were called, "5.2s, ai 4.1s, tools 1.1s/3". */
        String timing() {
            String t = formatSeconds(elapsedMs);
            if (toolCalls > 0) {
                t += ", ai " + formatSeconds(aiMs) + ", tools " + formatSeconds(toolMs) + "/" + toolCalls;
            }
            return t;
        }
    }

    /**
     * One model round trip. {@code question} is the sequence number of the panel question the request was made for (a
     * question takes several round trips when the model calls tools), so usage can be grouped per question; it is 0 for
     * route requests, which belong to no question.
     */
    record AiUsageEntry(String model, String provider, int inputTokens, int outputTokens,
            int totalTokens, long latencyMs, String stopReason, Instant timestamp,
            AiUsageSource source, String routeId, int question) {

        AiUsageEntry(String model, String provider, int inputTokens, int outputTokens,
                     int totalTokens, long latencyMs, String stopReason, Instant timestamp) {
            this(model, provider, inputTokens, outputTokens, totalTokens, latencyMs, stopReason, timestamp,
                 AiUsageSource.TUI, null, 0);
        }

        AiUsageEntry(String model, String provider, int inputTokens, int outputTokens,
                     int totalTokens, long latencyMs, String stopReason, Instant timestamp,
                     AiUsageSource source, String routeId) {
            this(model, provider, inputTokens, outputTokens, totalTokens, latencyMs, stopReason, timestamp,
                 source, routeId, 0);
        }
    }

    void setContext(MonitorContext ctx) {
        this.ctx = ctx;
    }

    void setLaunchManager(LaunchManager launchManager) {
        this.launchManager = launchManager;
        if (toolRegistry != null) {
            toolRegistry.setLaunchManager(launchManager);
        }
    }

    void setMcpFacade(McpFacade mcpFacade) {
        this.mcpFacade = mcpFacade;
        if (mcpFacade != null) {
            this.toolRegistry = new TuiToolRegistry(mcpFacade);
            if (launchManager != null) {
                toolRegistry.setLaunchManager(launchManager);
            }
        }
    }

    void setMcpInfo(boolean active, int port) {
        this.mcpServerActive = active;
        this.mcpServerPort = port;
    }

    void setOtelSpans(AtomicReference<List<SpanEntry>> otelSpans) {
        this.otelSpans = otelSpans != null ? otelSpans : new AtomicReference<>(List.of());
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
        return last.role() == AiRole.ASSISTANT ? last.elapsedMs() : -1;
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
        reloadPromptHistory();
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
        modelCompletionCache = null;
        try {
            LlmClient created = LlmClient.create()
                    .withTemperature(0.3)
                    .withTimeout(120)
                    .withMaxTokens(4096)
                    .withPrinter(clientPrinter);
            if (sessionProviderChoice != null) {
                providerSelector.applyChoice(created, sessionProviderChoice.provider(), sessionProviderChoice.model(),
                        sessionProviderChoice.url());
            } else {
                TuiSettings settings = TuiSettings.load();
                providerSelector.applyChoice(created, settings.getAiProvider(), settings.getAiModel(), settings.getAiUrl());
            }
            if (toolMode == null) {
                toolMode = normalizeToolMode(TuiSettings.load().getAiTools());
            }
            client = created;
            if (!client.detectEndpoint()) {
                initError
                        = "No LLM service reachable. Set ANTHROPIC_API_KEY, AZURE_OPENAI_*, GEMINI_API_KEY, OPENAI_API_KEY, WATSONX_APIKEY, or start Ollama.";
                client = null;
                return;
            }
            initError = null;
            messages = new ArrayList<>();
            tools = buildTuiToolDefinitions();
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
        if (historySearchActive) {
            return handleSearchKeyEvent(ke);
        }
        if (ke.isKey(KeyCode.F8)) {
            close();
            return true;
        }
        if (isFunctionKey(ke)) {
            return false;
        }
        if (!statsView && !thinking.get() && ke.hasCtrl() && ke.isCharIgnoreCase('r')
                && promptHistory != null && promptHistory.isEnabled() && promptHistory.size() > 0) {
            enterHistorySearch();
            return true;
        }
        if (ke.hasCtrl() && ke.isCharIgnoreCase('p') && !thinking.get() && activeCliCommand == null) {
            openProviderSwitch();
            return true;
        }
        if (ke.hasCtrl() && ke.isCharIgnoreCase('u')) {
            toggleUsageView();
            return true;
        }
        if (ke.hasCtrl() && ke.isCharIgnoreCase('y')) {
            copyLastResponseToClipboard();
            return true;
        }
        if (ke.hasCtrl() && ke.isCharIgnoreCase('e')) {
            exportChatToFile();
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
        if (!statsView && ke.isKey(KeyCode.UP) && promptHistory != null && promptHistory.isEnabled()) {
            promptHistory.previous(inputBuffer.toString()).ifPresent(this::replaceInputBuffer);
            return true;
        }
        if (!statsView && ke.isKey(KeyCode.DOWN) && promptHistory != null && promptHistory.isEnabled()) {
            promptHistory.next(inputBuffer.toString()).ifPresent(this::replaceInputBuffer);
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
                if (promptHistory != null) {
                    promptHistory.resetNavigation();
                }
                inputBuffer.deleteCharAt(cursorPos - 1);
                cursorPos--;
            }
            return true;
        }
        if (ke.isKey(KeyCode.DELETE)) {
            if (cursorPos < inputBuffer.length()) {
                if (promptHistory != null) {
                    promptHistory.resetNavigation();
                }
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
            if (promptHistory != null) {
                promptHistory.resetNavigation();
            }
            inputBuffer.insert(cursorPos, ke.character());
            cursorPos++;
            return true;
        }
        return true;
    }

    // ---- Reverse-i-search (Ctrl+R) ----

    private void enterHistorySearch() {
        savedInput = inputBuffer.toString();
        historySearchActive = true;
        searchTerm.setLength(0);
        searchIndex = -1;
    }

    private void exitHistorySearch(boolean accept) {
        historySearchActive = false;
        if (accept && searchIndex >= 0) {
            replaceInputBuffer(promptHistory.get(searchIndex));
        } else {
            replaceInputBuffer(savedInput);
        }
        searchTerm.setLength(0);
        searchIndex = -1;
        savedInput = null;
    }

    private void performSearch(int fromIndex) {
        if (searchTerm.isEmpty()) {
            searchIndex = -1;
            return;
        }
        searchIndex = promptHistory.searchBackward(searchTerm.toString(), fromIndex);
    }

    private boolean handleSearchKeyEvent(KeyEvent ke) {
        if (ke.hasCtrl() && ke.isCharIgnoreCase('r')) {
            if (searchIndex > 0) {
                performSearch(searchIndex - 1);
            }
            return true;
        }
        if (ke.isKey(KeyCode.ENTER)) {
            exitHistorySearch(true);
            return true;
        }
        if (ke.isKey(KeyCode.ESCAPE) || ke.isCtrlC()) {
            exitHistorySearch(false);
            return true;
        }
        if (ke.isKey(KeyCode.BACKSPACE)) {
            if (searchTerm.isEmpty()) {
                exitHistorySearch(false);
            } else {
                searchTerm.deleteCharAt(searchTerm.length() - 1);
                performSearch(promptHistory.size() - 1);
            }
            return true;
        }
        if (ke.code() == KeyCode.CHAR && !ke.hasCtrl() && !ke.hasAlt()) {
            searchTerm.append(ke.character());
            int from = searchIndex >= 0 ? searchIndex : promptHistory.size() - 1;
            performSearch(from);
            return true;
        }
        // Any other key: accept match and let the key be processed normally
        exitHistorySearch(true);
        return handleKeyEvent(ke);
    }

    boolean isHistorySearchActive() {
        return historySearchActive;
    }

    /**
     * Inserts pasted text at the cursor. Line breaks are collapsed to single spaces so a multi-line paste still forms
     * one prompt that can be reviewed and submitted with Enter, rather than submitting on the first newline. While the
     * reverse-i-search is active the text is appended to the search term instead.
     */
    void handlePaste(String text) {
        if (!visible || text == null || text.isEmpty() || providerSwitchPopup.isVisible()) {
            return;
        }
        String flat = text.replace("\r\n", "\n").replace('\r', '\n').replace('\n', ' ');
        if (historySearchActive) {
            searchTerm.append(flat);
            performSearch(searchIndex >= 0 ? searchIndex : promptHistory.size() - 1);
            return;
        }
        if (promptHistory != null) {
            promptHistory.resetNavigation();
        }
        completionMatches = null;
        inputBuffer.insert(cursorPos, flat);
        cursorPos += flat.length();
    }

    String searchTermForTesting() {
        return searchTerm.toString();
    }

    /**
     * Completes the slash command name at the cursor. With multiple matches, TAB first fills in the longest common
     * prefix; once no further prefix can be added it cycles forward through the matches (wrapping so every match is
     * reachable with TAB alone). A single match is completed fully and a trailing space is appended. Shift+TAB cycles
     * backward. TAB is a no-op unless the buffer is a partial command name (starts with {@code /}, no arguments yet).
     */
    /**
     * Completes the slash command name at the cursor, or the first argument of {@code /model} (against the models the
     * provider reports) and {@code /tools} (auto, core, full). With multiple matches, TAB first fills in the longest
     * common prefix; once no further prefix can be added it cycles forward through the matches (wrapping so every match
     * is reachable with TAB alone). A single match is completed fully and a trailing space is appended. Shift+TAB
     * cycles backward.
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

        List<String> names;
        String currentToken;
        ArgumentCompletion argument = argumentCompletion(text);
        if (argument != null) {
            names = argument.candidates();
            currentToken = argument.token();
            completionStart = argument.start();
        } else {
            names = slashCommands.completionsFor(text).stream()
                    .map(AiSlashCommandRegistry.Descriptor::name)
                    .toList();
            currentToken = text.length() > 1 ? text.substring(1) : "";
            completionStart = 1;
        }
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

    private record ArgumentCompletion(int start, String token, List<String> candidates) {
    }

    /**
     * Returns the argument completion for {@code /model <prefix>} or {@code /tools <prefix>} (aliases included), or
     * {@code null} when the buffer is not at the first argument of one of those commands. The model list comes from the
     * provider, so the first TAB kicks off a background fetch and returns nothing; TAB again once it is loaded.
     */
    private ArgumentCompletion argumentCompletion(String text) {
        if (!text.startsWith("/")) {
            return null;
        }
        int separator = -1;
        for (int i = 1; i < text.length(); i++) {
            if (Character.isWhitespace(text.charAt(i))) {
                separator = i;
                break;
            }
        }
        if (separator < 0) {
            return null;
        }
        Optional<AiSlashCommandRegistry.Descriptor> descriptor = slashCommands.lookup(text.substring(1, separator));
        if (descriptor.isEmpty()) {
            return null;
        }
        int start = separator;
        while (start < text.length() && Character.isWhitespace(text.charAt(start))) {
            start++;
        }
        String token = text.substring(start);
        if (token.chars().anyMatch(Character::isWhitespace)) {
            return null;
        }
        List<String> candidates = switch (descriptor.get().name()) {
            case "model" -> modelCompletionCandidates();
            case "tools" -> List.of(TOOL_MODE_AUTO, TOOL_MODE_CORE, TOOL_MODE_FULL);
            default -> null;
        };
        if (candidates == null) {
            return null;
        }
        String lower = token.toLowerCase();
        List<String> matches = candidates.stream()
                .filter(candidate -> candidate.toLowerCase().startsWith(lower))
                .toList();
        return new ArgumentCompletion(start, token, matches);
    }

    private List<String> modelCompletionCandidates() {
        List<String> cached = modelCompletionCache;
        if (cached != null) {
            return cached;
        }
        if (client != null && modelCompletionFetch.compareAndSet(false, true)) {
            conversation.add(new ConversationEntry(AiRole.SYSTEM, "Fetching available models, press TAB again..."));
            Thread worker = new Thread(() -> {
                try {
                    modelCompletionCache = slashCommandContext.availableModels();
                } finally {
                    modelCompletionFetch.set(false);
                }
            }, "tui-ai-model-completion");
            worker.setDaemon(true);
            worker.start();
        }
        return List.of();
    }

    private void applyCompletionToken(String token, boolean trailingSpace) {
        inputBuffer.setLength(completionStart);
        inputBuffer.append(token);
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
        if (promptHistory != null) {
            promptHistory.remember(input);
        }
        inputBuffer.setLength(0);
        cursorPos = 0;
        scrollOffset = 0;
        if (input.startsWith("/")) {
            executeSlashCommand(input);
        } else {
            submitQuestion(input);
        }
    }

    private void reloadPromptHistory() {
        TuiSettings settings = TuiSettings.load();
        promptHistory = TuiPromptHistory.load(settings.getAiPromptHistoryLimit(), TuiHistoryFiles.aiPromptHistoryFile());
    }

    private void replaceInputBuffer(String text) {
        inputBuffer.setLength(0);
        if (text != null) {
            inputBuffer.append(text);
        }
        cursorPos = inputBuffer.length();
        completionMatches = null;
        completionCycleIndex = -1;
        completionSnapshot = null;
    }

    private void executeSlashCommand(String input) {
        Optional<AiSlashCommandRegistry.ParsedCommand> parsed = slashCommands.parse(input);
        if (parsed.isPresent() && (thinking.get() || activeCliCommand != null)) {
            String name = parsed.get().descriptor().name();
            if ("provider".equals(name) || "model".equals(name) || "retry".equals(name)
                    || "compact".equals(name)) {
                conversation.add(new ConversationEntry(
                        AiRole.SYSTEM,
                        "Wait for the current operation to finish before running /" + name + "."));
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
            modelCompletionCache = models;
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
        questionCounter++;
        log(LogLevel.QUESTION, "Question", question);
        thinkingVerb = THINKING_VERBS.get(ThreadLocalRandom.current().nextInt(THINKING_VERBS.size()));
        thinkingStartTime = System.currentTimeMillis();
        thinking.set(true);

        // re-read the tool mode so a change made in F2 -> Settings applies to the next question, and rebuild the
        // tools in case mcpFacade was wired after init
        if (!testingClientInjected) {
            toolMode = normalizeToolMode(TuiSettings.load().getAiTools());
        }
        tools = buildTuiToolDefinitions();
        String systemPrompt = buildSystemPrompt();

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
        messages.add(LlmClient.Message.user(contextualize(question)));

        LlmClient.TokenUsage totalUsage = LlmClient.TokenUsage.EMPTY;
        Map<String, Integer> callCounts = new HashMap<>();
        List<String> recentCalls = new ArrayList<>();
        long turnAiMs = 0;
        long turnToolMs = 0;
        int turnToolCalls = 0;
        for (int i = 0; i < MAX_ITERATIONS; i++) {
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }

            long callStart = System.currentTimeMillis();
            drainClientOutput();
            LlmClient.ChatResponse response = client.chatWithTools(systemPrompt, messages, tools);
            long callLatency = System.currentTimeMillis() - callStart;
            turnAiMs += callLatency;
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
                String detail = drainClientOutput();
                String err = detail.isEmpty()
                        ? "LLM request failed. Check API key and endpoint."
                        : "LLM request failed: " + detail
                          + "\nCheck the endpoint and model (/model lists what the provider offers).";
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
                    String arguments = toolCall.arguments() != null ? toolCall.arguments().toJson() : "{}";
                    log(LogLevel.TOOL, toolCall.name(), arguments);
                    String key = toolCall.name() + " " + arguments;
                    int repeats = callCounts.merge(key, 1, Integer::sum);
                    String result;
                    long toolStart = System.currentTimeMillis();
                    if (repeats > MAX_IDENTICAL_TOOL_CALLS) {
                        result = "You have already called " + toolCall.name() + " with these exact arguments "
                                 + (repeats - 1) + " times in this turn and the result will not change. Stop calling "
                                 + "tools now: tell the user what you found, what failed, and what they could try instead.";
                    } else {
                        result = executeTuiTool(toolCall.name(), toolCall.arguments());
                    }
                    long toolElapsed = System.currentTimeMillis() - toolStart;
                    turnToolMs += toolElapsed;
                    turnToolCalls++;
                    sessionToolTimeMs += toolElapsed;
                    sessionToolCalls++;
                    log(LogLevel.RESULT, toolCall.name() + " (" + formatToolTime(toolElapsed) + ")", result);
                    recentCalls.add(toolCall.name() + " " + summarize(arguments, 80) + " -> " + summarize(result, 120));
                    results.add(new LlmClient.ToolResult(toolCall.id(), truncateToolResult(result)));
                }
                messages.add(LlmClient.Message.toolResults(results));
            } else {
                String text = response.text();
                sessionTotalTokens += totalUsage.totalTokens();
                if (text != null && !text.isBlank()) {
                    long elapsed = System.currentTimeMillis() - thinkingStartTime;
                    ConversationEntry entry = new ConversationEntry(
                            AiRole.ASSISTANT, text, elapsed, turnAiMs, turnToolMs, turnToolCalls,
                            totalUsage.totalTokens());
                    conversation.add(entry);
                    turnTimings.add(new long[] { turnAiMs, turnToolMs, turnToolCalls });
                    String tokenInfo = totalUsage.totalTokens() > 0
                            ? ", " + LlmClient.formatTokens(totalUsage.totalTokens()) + " tokens"
                            : "";
                    log(LogLevel.RESPONSE,
                            "Response (" + entry.timing() + tokenInfo + describeCacheSignal(totalUsage) + ")",
                            text);
                } else {
                    String err = "Empty response from LLM.";
                    conversation.add(new ConversationEntry(AiRole.ERROR, err));
                    log(LogLevel.ERROR, "Error", err);
                }
                scrollOffset = 0;
                messages.add(LlmClient.Message.assistantWithToolCalls(text, List.of()));
                compactHistoryAfterTurn();
                return;
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Reached maximum iterations (").append(MAX_ITERATIONS).append(") without a final answer. ");
        sb.append("The model kept calling tools instead of answering; the last calls were:");
        int from = Math.max(0, recentCalls.size() - 4);
        for (String call : recentCalls.subList(from, recentCalls.size())) {
            sb.append("\n- ").append(call);
        }
        sb.append("\nSee F2 -> AI Log for the full results, then rephrase with more detail (for example the exact ");
        sb.append("endpoint URI or topic) or /retry.");
        sessionTotalTokens += totalUsage.totalTokens();
        conversation.add(new ConversationEntry(AiRole.ERROR, sb.toString()));
        log(LogLevel.ERROR, "Error", sb.toString());
        // keep the history consistent: the turn ends without an answer, so the next question starts fresh from here
        messages.add(LlmClient.Message.assistantWithToolCalls(
                "(no answer: the iteration limit was reached while calling tools)", List.of()));
        compactHistoryAfterTurn();
    }

    /**
     * Compacts the history after a turn unless the endpoint is local and the history is still within
     * {@link #LOCAL_HISTORY_BUDGET_TOKENS}; see there for why rewriting history is the slower choice locally.
     * {@code /compact} bypasses this and always compacts.
     */
    private void compactHistoryAfterTurn() {
        boolean local = client != null && client.isLocalEndpoint();
        if (shouldCompactAfterTurn(local, historyChars(messages))) {
            compactHistory(messages, MAX_HISTORY_TURNS, COMPACT_TOOL_RESULT_CHARS);
        }
    }

    static boolean shouldCompactAfterTurn(boolean localEndpoint, long historyChars) {
        return !localEndpoint || estimateTokens(historyChars) > LOCAL_HISTORY_BUDGET_TOKENS;
    }

    /**
     * What the provider revealed about its prompt cache for the request(s) of a question, for the AI log: with Ollama
     * the time spent on prompt processing versus generation (a cached prompt shows as a near-zero prefill even though
     * the token count always reports the full prompt), with hosted APIs how many input tokens came from the cache.
     * Empty when the provider reported nothing.
     */
    static String describeCacheSignal(LlmClient.TokenUsage usage) {
        if (usage == null || !usage.hasCacheSignal()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (usage.prefillMillis() > 0 || usage.generationMillis() > 0) {
            sb.append(", prefill ").append(formatSeconds(usage.prefillMillis()))
                    .append(", gen ").append(formatSeconds(usage.generationMillis()));
        }
        if (usage.cachedTokens() > 0) {
            sb.append(", cached ").append(LlmClient.formatTokens(usage.cachedTokens()));
        }
        return sb.toString();
    }

    private static String formatSeconds(long millis) {
        return String.format(Locale.ROOT, "%.1fs", millis / 1000.0);
    }

    /**
     * Tool calls are usually fast, so show milliseconds below one second and one decimal second above.
     */
    private static String formatToolTime(long millis) {
        return millis < 1000 ? millis + "ms" : formatSeconds(millis);
    }

    private static String summarize(String text, int max) {
        if (text == null) {
            return "";
        }
        String flat = text.replace('\n', ' ').replace('\r', ' ').strip();
        return flat.length() <= max ? flat : flat.substring(0, max) + "...";
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
                response.stopReason(), Instant.now(),
                AiUsageSource.TUI, null, questionCounter));
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
                    Span.styled("(" + formatSeconds(titleElapsed) + tokenSuffix + ") ", Style.EMPTY.dim()));
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
                .borderStyle(Theme.borderFocused())
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
            if (initError.startsWith("No LLM service reachable")) {
                md.append(buildAiSetupGuide());
            } else {
                md.append("**Error:** ").append(initError).append("\n\n");
            }
        } else if (conversation.isEmpty() && !thinking.get() && !slashHintsVisible) {
            frame.renderWidget(
                    Paragraph.from(Line.from(Span.styled("Ask a question about your Camel application...", Style.EMPTY.dim()))),
                    area);
            return;
        }

        // The user's question is a blockquote: with the chat styles that becomes an accent-coloured gutter bar, which
        // is what the eye picks up when scanning for where the next turn starts. The answer is plain, without a label,
        // as it always directly follows its question.
        for (ConversationEntry entry : conversation) {
            switch (entry.role()) {
                case USER -> md.append("> ").append(entry.text().replace("\n", "\n> ")).append("\n\n");
                case ASSISTANT -> md.append(toHardBreaks(entry.text())).append("\n\n");
                case ERROR -> md.append("**Error:** ").append(entry.text()).append("\n\n");
                case SYSTEM -> md.append(toHardBreaks(entry.text())).append("\n\n");
            }
        }

        // Show elapsed time and token count as a dimmed line below the markdown when at the bottom
        long lastElapsed = -1;
        String lastTiming = "";
        int lastTokens = 0;
        if (!thinking.get() && !conversation.isEmpty()) {
            ConversationEntry last = conversation.get(conversation.size() - 1);
            if (last.role() == AiRole.ASSISTANT && last.elapsedMs() >= 0) {
                lastElapsed = last.elapsedMs();
                lastTiming = last.timing();
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
                .styles(Theme.chatMarkdownStyles());
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
                    Paragraph.from(
                            Line.from(Span.styled("(" + lastTiming + tokenSuffix + ")", Style.EMPTY.dim()))),
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
        if (historySearchActive) {
            renderSearchInput(frame, area);
            return;
        }

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

    private void renderSearchInput(Frame frame, Rect area) {
        boolean failing = !searchTerm.isEmpty() && searchIndex < 0;
        String label = failing ? "failing bck-i-search: " : "bck-i-search: ";
        String query = searchTerm.toString();

        List<Span> spans = new ArrayList<>();
        Style labelStyle = failing
                ? Theme.error()
                : Style.EMPTY.fg(Theme.accent());
        spans.add(Span.styled(label, labelStyle));
        spans.add(Span.raw(query));
        spans.add(Span.styled("_", Style.EMPTY.reversed()));

        if (searchIndex >= 0) {
            String matched = promptHistory.get(searchIndex);
            int maxWidth = area.width() - label.length() - query.length() - 1;
            if (maxWidth > 4) {
                String preview = matched.length() > maxWidth
                        ? matched.substring(0, maxWidth - 1) + "…"
                        : matched;
                spans.add(Span.styled("  " + preview, Style.EMPTY.dim()));
            }
        }

        frame.renderWidget(Paragraph.from(Line.from(spans)), area);
    }

    void renderFooter(List<Span> spans) {
        if (providerSwitchPopup.isVisible()) {
            providerSwitchPopup.renderFooter(spans);
            return;
        }
        TuiHelper.hint(spans, "F8", "close");
        if (statsView) {
            TuiHelper.hint(spans, "Ctrl+U", "chat");
        } else {
            TuiHelper.hint(spans, "Ctrl+U", "usage");
        }
        TuiHelper.hint(spans, "Shift+F8", "resize (" + anim.cyclePercent() + "%)");
        TuiHelper.hint(spans, "PgUp/Dn", "scroll");
        TuiHelper.hint(spans, "Ctrl+Y", "copy");
        TuiHelper.hint(spans, "Ctrl+E", "export");
        if (!statsView) {
            TuiHelper.hint(spans, "Ctrl+P", "provider");
            if (!thinking.get()) {
                TuiHelper.hint(spans, "Enter", "send");
                TuiHelper.hint(spans, "Ctrl+R", "search");
            } else {
                TuiHelper.hint(spans, "Esc/Ctrl+C", "interrupt");
            }
        }
    }

    private void copyLastResponseToClipboard() {
        for (int i = conversation.size() - 1; i >= 0; i--) {
            ConversationEntry entry = conversation.get(i);
            if (entry.role() == AiRole.ASSISTANT && entry.text() != null && !entry.text().isEmpty()) {
                try {
                    copyToSystemClipboard(entry.text());
                    notify("Copied to clipboard", false);
                } catch (Exception e) {
                    notify("Clipboard not available: " + e.getMessage(), true);
                }
                return;
            }
        }
        notify("No AI response to copy", true);
    }

    private static void copyToSystemClipboard(String text) throws IOException {
        TuiHelper.copyToClipboard(text);
    }

    private void exportChatToFile() {
        if (conversation.isEmpty()) {
            notify("No conversation to export", true);
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("# Camel TUI AI Chat\n\n");
        sb.append("_Exported: ").append(LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("_\n");
        for (ConversationEntry entry : conversation) {
            sb.append("\n---\n\n");
            switch (entry.role()) {
                case USER -> sb.append("**You:** ").append(entry.text()).append("\n");
                case ASSISTANT -> sb.append("**AI:** ").append(entry.text()).append("\n");
                case ERROR -> sb.append("**Error:** ").append(entry.text()).append("\n");
                case SYSTEM -> sb.append("_System: ").append(entry.text()).append("_\n");
            }
        }
        try {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            String filename = "camel-ai-chat-" + timestamp + ".md";
            Files.writeString(Path.of(filename), sb.toString());
            notify("Saved: " + filename, false);
        } catch (IOException e) {
            notify("Export failed: " + e.getMessage(), true);
        }
    }

    private void notify(String message, boolean error) {
        if (ctx != null && ctx.notificationCallback != null) {
            ctx.notificationCallback.accept(message, error);
        }
    }

    private void renderStats(Frame frame, Rect area) {
        if (area.height() < 3) {
            return;
        }

        List<AiUsageEntry> entries = combinedUsageEntries();
        if (entries.isEmpty()) {
            frame.renderWidget(
                    Paragraph.from(Line.from(Span.styled(
                            "No AI usage data yet. Ask a question, or run an integration with GenAI observability and --observe.",
                            Style.EMPTY.dim()))),
                    area);
            return;
        }

        // Compute aggregates
        int totalInput = 0;
        int totalOutput = 0;
        int totalTokens = 0;
        long totalLatency = 0;
        int tuiRequests = 0;
        int routeRequests = 0;
        for (AiUsageEntry e : entries) {
            totalInput += e.inputTokens();
            totalOutput += e.outputTokens();
            totalTokens += e.totalTokens();
            totalLatency += e.latencyMs();
            if (e.source() == AiUsageSource.ROUTE) {
                routeRequests++;
            } else {
                tuiRequests++;
            }
        }
        int requestCount = entries.size();

        // Per-model aggregation
        Map<String, long[]> perModel = new LinkedHashMap<>();
        for (AiUsageEntry e : entries) {
            String key = modelTableKey(e);
            long[] stats = perModel.computeIfAbsent(key, k -> new long[5]);
            stats[0]++; // requests
            stats[1] += e.inputTokens();
            stats[2] += e.outputTokens();
            stats[3] += e.totalTokens();
            stats[4] += e.latencyMs();
        }

        // Tokens per question (panel requests only): the round trips made for one question share its sequence
        // number. Grouping by that, rather than by a pause between requests, keeps a follow-up typed right after the
        // previous answer as its own bar.
        List<Integer> turnTokens = new ArrayList<>();
        int lastQuestion = -1;
        for (AiUsageEntry e : usageHistory) {
            if (e.question() != lastQuestion) {
                turnTokens.add(0);
                lastQuestion = e.question();
            }
            int last = turnTokens.size() - 1;
            turnTokens.set(last, turnTokens.get(last) + e.totalTokens());
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
                Span.styled(" (TUI: ", dimStyle),
                Span.styled(String.valueOf(tuiRequests), cyanStyle),
                Span.styled(" / integration: ", dimStyle),
                Span.styled(String.valueOf(routeRequests), cyanStyle),
                Span.styled(")   Total tokens: ", dimStyle),
                Span.styled(LlmClient.formatTokens(totalTokens), cyanStyle),
                Span.styled(" (in: ", dimStyle),
                Span.styled(LlmClient.formatTokens(totalInput), Theme.success()),
                Span.styled(" / out: ", dimStyle),
                Span.styled(LlmClient.formatTokens(totalOutput), Theme.label()),
                Span.styled(")", dimStyle)));
        summaryLines.add(Line.from(
                Span.styled("Avg latency: ", dimStyle),
                Span.styled(formatSeconds(totalLatency / requestCount), cyanStyle),
                Span.styled("   AI time: ", dimStyle),
                Span.styled(formatSeconds(totalLatency), cyanStyle),
                Span.styled("   Tool time: ", dimStyle),
                Span.styled(formatSeconds(sessionToolTimeMs), cyanStyle),
                Span.styled(" (" + sessionToolCalls + " calls)", dimStyle)));
        frame.renderWidget(
                Paragraph.from(new dev.tamboui.text.Text(summaryLines, dev.tamboui.layout.Alignment.LEFT)),
                summaryArea);

        // --- Per-model table ---
        Rect tableArea = sections.get(1);
        List<Row> rows = new ArrayList<>();
        for (Map.Entry<String, long[]> entry : perModel.entrySet()) {
            long[] s = entry.getValue();
            rows.add(Row.from(
                    Cell.from(Span.styled(entry.getKey(), cyanStyle)),
                    Cell.from(String.valueOf(s[0])),
                    Cell.from(LlmClient.formatTokens((int) s[1])),
                    Cell.from(LlmClient.formatTokens((int) s[2])),
                    Cell.from(LlmClient.formatTokens((int) s[3])),
                    Cell.from(formatSeconds(s[4] / s[0]))));
        }
        Table table = Table.builder()
                .rows(rows)
                .header(Row.from(
                        Cell.from(Span.styled("MODEL", Style.EMPTY.bold())),
                        Cell.from(Span.styled("REQS", Style.EMPTY.bold())),
                        Cell.from(Span.styled("INPUT", Style.EMPTY.bold())),
                        Cell.from(Span.styled("OUTPUT", Style.EMPTY.bold())),
                        Cell.from(Span.styled("TOTAL", Style.EMPTY.bold())),
                        Cell.from(Span.styled("AVG", Style.EMPTY.bold()))))
                .widths(
                        Constraint.fill(),
                        Constraint.length(6),
                        Constraint.length(8),
                        Constraint.length(8),
                        Constraint.length(8),
                        Constraint.length(7))
                .build();
        frame.renderStatefulWidget(table, tableArea, statsTableState);

        // --- Charts per question: tokens on the left, AI vs tool time on the right ---
        if (hasChart && (turnTokens.size() > 1 || turnTimings.size() > 1)) {
            Rect chartsArea = sections.get(2);
            List<long[]> timings = new ArrayList<>(turnTimings);
            boolean showTime = timings.size() > 1 && chartsArea.width() >= 40;
            List<Rect> halves = showTime
                    ? Layout.horizontal().constraints(Constraint.fill(), Constraint.length(1), Constraint.fill())
                            .split(chartsArea)
                    : List.of(chartsArea);
            if (turnTokens.size() > 1) {
                renderTokensPerQuestion(frame, halves.get(0), turnTokens);
            }
            if (showTime) {
                renderTimePerQuestion(frame, halves.get(2), timings);
            }
        }
    }

    private void renderTokensPerQuestion(Frame frame, Rect chartArea, List<Integer> turnTokens) {
        {

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

    /**
     * One group of two bars per answered question: time waiting for the model next to time spent in tool calls.
     */
    private void renderTimePerQuestion(Frame frame, Rect chartArea, List<long[]> timings) {
        List<Rect> chartParts = Layout.vertical()
                .constraints(Constraint.length(1), Constraint.fill())
                .split(chartArea);
        Style aiStyle = Style.EMPTY.fg(Theme.accent());
        Style toolStyle = Theme.warning();
        frame.renderWidget(
                Paragraph.from(Line.from(
                        Span.styled("Time per question: ", Style.EMPTY.bold()),
                        Span.styled("\u25a0 ai ", aiStyle),
                        Span.styled("\u25a0 tools", toolStyle))),
                chartParts.get(0));

        Rect barArea = chartParts.get(1);
        long maxMs = 1;
        for (long[] t : timings) {
            maxMs = Math.max(maxMs, Math.max(t[0], t[1]));
        }
        // each question takes two 1-wide bars plus a gap
        int maxGroups = Math.max(1, barArea.width() / 3);
        int startIdx = Math.max(0, timings.size() - maxGroups);
        List<BarGroup> groups = new ArrayList<>();
        for (int i = startIdx; i < timings.size(); i++) {
            long[] t = timings.get(i);
            groups.add(BarGroup.of(
                    Bar.builder().value(t[0]).textValue("").style(aiStyle).build(),
                    Bar.builder().value(t[1]).textValue("").style(toolStyle).build()));
        }
        BarChart barChart = BarChart.builder()
                .data(groups)
                .max(maxMs + maxMs / 20)
                .barWidth(1)
                .barGap(0)
                .groupGap(1)
                .build();
        frame.renderWidget(barChart, barArea);
    }

    private List<AiUsageEntry> combinedUsageEntries() {
        List<AiUsageEntry> combined = new ArrayList<>(usageHistory.size() + 8);
        combined.addAll(usageHistory);
        List<SpanEntry> spans = otelSpans.get();
        if (spans != null && !spans.isEmpty()) {
            Instant since = usageResetAt;
            for (AiUsageEntry entry : GenAiSpanUsageExtractor.extract(spans)) {
                if (!entry.timestamp().isBefore(since)) {
                    combined.add(entry);
                }
            }
        }
        return combined;
    }

    /**
     * Forgets the usage recorded so far: the panel's own requests are dropped, and route usage from spans that were
     * exported before now is hidden (the spans themselves stay, as the Spans tab owns them).
     */
    void resetUsage() {
        usageHistory.clear();
        turnTimings.clear();
        usageResetAt = Instant.now();
        statsScrollOffset = 0;
    }

    private static String modelTableKey(AiUsageEntry entry) {
        String modelProvider = entry.model() + " (" + entry.provider() + ")";
        if (entry.source() == AiUsageSource.ROUTE) {
            String route = entry.routeId() != null && !entry.routeId().isBlank() ? entry.routeId() : "route";
            return "[route:" + route + "] " + modelProvider;
        }
        return "[tui] " + modelProvider;
    }

    boolean isStatsView() {
        return statsView;
    }

    void toggleStatsViewForTesting() {
        statsView = !statsView;
        statsScrollOffset = 0;
        if (statsView) {
            spanRefreshRequested = true;
        }
    }

    List<AiUsageEntry> combinedUsageEntriesForTesting() {
        return combinedUsageEntries();
    }

    void recordUsageForTesting(AiUsageEntry entry) {
        usageHistory.add(entry);
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

    private String buildAiSetupGuide() {
        return """
                ## AI Assistant — Getting Started

                No LLM provider was detected. Choose one of the options below, then press **F8** to reopen this panel.

                > **Tool calling is required.** This panel inspects your Camel process by
                > invoking built-in tools. Models smaller than ~14B do not reliably call
                > tools and will answer from training knowledge instead — use at least 14B.
                > Prefer a mixture-of-experts model such as qwen3.6:35b-a3b: it processes
                > the tool-heavy prompt many times faster than a dense 27B/32B model.

                ---

                ### Option A: Local — Ollama (no API key needed)

                Run models entirely on your machine — no data leaves your host.

                ```
                # macOS
                brew install ollama

                # Linux
                curl -fsSL https://ollama.com/install.sh | sh

                # then on both:
                ollama serve             # start the daemon (skip if auto-started)
                ollama pull qwen3.6:35b-a3b  # recommended
                ```

                Ollama is auto-detected at `localhost:11434` — no configuration needed.

                **Models that work well** (tool-calling capable, ≥14B):

                | Model | RAM | Notes |
                |---|---|---|
                | qwen3.6:35b-a3b | ~23 GB | Recommended: only 3B active per token, fastest prompt processing |
                | qwen2.5:14b | ~9 GB | Minimum for 16 GB machines |
                | qwen3.6:27b | ~18 GB | Strong dense model, several times slower prompt processing |
                | qwen2.5:32b | ~20 GB | Good quality, slow prompt processing |
                | hermes3:70b  | ~43 GB | Excellent tool calling, needs 64 GB+ |
                | llama3.3:70b | ~43 GB | Best open model, needs 64 GB+ |

                **Tip:** Install Ollama natively — `camel infra run ollama` uses Docker and
                loses GPU acceleration (Metal on macOS, CUDA on Linux), making inference
                much slower. Native install is always preferred for development use.

                ---

                ### Option B: Cloud provider (API key required)

                Set one environment variable before starting the TUI:

                | Variable | Provider |
                |---|---|
                | `ANTHROPIC_API_KEY` | Claude |
                | `OPENAI_API_KEY` | OpenAI (GPT-4o etc.) |
                | `GEMINI_API_KEY` | Gemini |
                | `AZURE_OPENAI_API_KEY` + `AZURE_OPENAI_ENDPOINT` | Azure OpenAI |
                | `WATSONX_APIKEY` | IBM watsonx.ai |

                For any **OpenAI-compatible** server (LM Studio, vLLM, llama.cpp, GPT4All, …):

                ```
                export LLM_API_KEY=any-value
                export LLM_BASE_URL=http://localhost:1234
                ```

                `OPENAI_BASE_URL` is also supported as an alternative to `LLM_BASE_URL`.

                Or press **Ctrl+P** to select and configure a provider now.
                """;
    }

    /**
     * The static prefix sent with every request. It deliberately contains nothing that changes between turns (the
     * selected integration travels in the user message instead) so a local model's prompt cache can reuse it, and it
     * does not repeat the tool list because the tool definitions already carry every description.
     */
    private String buildSystemPrompt() {
        StringBuilder sb = new StringBuilder();
        sb.append("You are an Apache Camel assistant running inside the Camel TUI terminal console. ");
        sb.append("You help users understand and troubleshoot their running Camel integrations.\n\n");

        sb.append("You have tui_* tools to observe and interact with the TUI; the tool definitions describe each one. ");
        sb.append("All tui_get_* tools fetch data directly from any tab without changing what the user sees.\n\n");
        sb.append("Guidelines:\n");
        sb.append("- NEVER call tui_navigate just to read data; the tui_get_* tools read any tab without navigating\n");
        sb.append("- Prefer tui_get_table over tui_get_screen for structured data; ");
        sb.append("call tui_get_options only when unsure which tab holds the data\n");
        sb.append("- tui_get_state tells which integration and tab is selected; tui_get_processor_detail explains ");
        sb.append("a route's steps; tui_get_status has data no tab shows (context, runtime, health, properties)\n");
        sb.append("- Your own tool calls are recorded in the AI log (tui_get_ai_log, F2 -> AI Log); ");
        sb.append("the MCP log only records external clients\n");
        sb.append("- Be concise and actionable; when something looks wrong, explain what it means and suggest fixes\n");
        sb.append("- tui_control stops/starts routes and integrations gracefully; its reset-stats action clears ");
        sb.append("statistics without touching the routes\n");
        sb.append("- tui_infra lists infra services (brokers, databases) and reads their logs\n");
        sb.append("- Never restart, stop or kill an integration or infra service unless the user explicitly ");
        sb.append("asked for that\n");
        sb.append("- If a tool call returns an error, do not repeat it with the same arguments; ");
        sb.append("tell the user what failed and what to try\n");
        sb.append("- To feed a route that consumes from a broker (MQTT, Kafka, JMS), tui_send_message can publish ");
        sb.append("to the broker with the route's own component and options\n");
        if (!useCoreTools()) {
            sb.append("- Use tui_locate + tui_draw_shape to visually highlight problems on screen for the user\n");
        }
        if (mcpServerActive) {
            sb.append("\nThe TUI MCP server is available at http://localhost:")
                    .append(mcpServerPort).append("/mcp for external AI agents.");
        }
        return sb.toString();
    }

    /**
     * Prefixes the question with the integration the user is looking at. This used to live in the system prompt, but
     * there it invalidated the model's cached prompt prefix every time the selection changed.
     */
    private String contextualize(String question) {
        String selectedName = mcpFacade != null ? mcpFacade.getSelectedIntegrationName() : null;
        String selectedPid = mcpFacade != null ? mcpFacade.getSelectedPid() : null;
        if (selectedName != null && selectedPid != null) {
            return "[Monitoring " + selectedName + " (PID " + selectedPid + ")]\n" + question;
        }
        return question;
    }

    /**
     * Whether only the {@link TuiToolRegistry#CORE_TOOLS} are sent: always in {@code core} mode, never in {@code full}
     * mode, and for local providers in {@code auto} mode.
     */
    private boolean useCoreTools() {
        String mode = toolMode == null ? TOOL_MODE_AUTO : toolMode;
        if (TOOL_MODE_CORE.equals(mode)) {
            return true;
        }
        if (TOOL_MODE_FULL.equals(mode)) {
            return false;
        }
        return client != null && client.isLocalEndpoint();
    }

    private String describeToolMode() {
        if (toolRegistry == null) {
            return "no tools available";
        }
        int total = toolRegistry.getToolDefinitions().size();
        int active = useCoreTools() ? toolRegistry.getCoreToolDefinitions().size() : total;
        String mode = toolMode == null ? TOOL_MODE_AUTO : toolMode;
        String detail = TOOL_MODE_AUTO.equals(mode)
                ? (useCoreTools() ? " (local provider)" : " (hosted provider)") : "";
        return (useCoreTools() ? "core" : "full") + " (" + active + " of " + total + " tools), mode " + mode + detail;
    }

    private List<LlmClient.ToolDef> buildTuiToolDefinitions() {
        if (toolRegistry == null) {
            return List.of();
        }
        List<LlmClient.ToolDef> defs = new ArrayList<>();
        List<TuiToolRegistry.ToolDef> source
                = useCoreTools() ? toolRegistry.getCoreToolDefinitions() : toolRegistry.getToolDefinitions();
        for (TuiToolRegistry.ToolDef td : source) {
            defs.add(new LlmClient.ToolDef(td.name(), td.description(), td.inputSchema()));
        }
        return defs;
    }

    /**
     * Returns and clears what the LLM client printed since the last drain, joined on one line.
     */
    private String drainClientOutput() {
        synchronized (clientOutput) {
            String joined = String.join(" | ", clientOutput);
            clientOutput.clear();
            return joined;
        }
    }

    /**
     * Caps a tool result before it enters the model history; the AI log keeps the full text.
     */
    static String truncateToolResult(String result) {
        if (result == null || result.length() <= MAX_TOOL_RESULT_CHARS) {
            return result;
        }
        return result.substring(0, MAX_TOOL_RESULT_CHARS)
               + "\n... [truncated, " + (result.length() - MAX_TOOL_RESULT_CHARS)
               + " more characters; narrow the request (filter, limit, section) to see the rest]";
    }

    /**
     * Keeps the model history bounded after a turn is answered: tool results from turns before the previous one are
     * shrunk to their head, and the oldest turns are dropped beyond {@code maxTurns} user questions. The previous turn
     * is kept intact so an immediate follow-up can still refer to what was just fetched. Whole turns are removed (user
     * message through the final answer) so assistant tool calls never lose their matching results.
     */
    static void compactHistory(List<LlmClient.Message> history, int maxTurns, int compactChars) {
        compactHistory(history, maxTurns, compactChars, true);
    }

    /**
     * As {@link #compactHistory(List, int, int)}; with {@code keepPreviousTurn} false the most recent answered turn is
     * compacted as well (used by {@code /compact}).
     */
    static void compactHistory(
            List<LlmClient.Message> history, int maxTurns, int compactChars,
            boolean keepPreviousTurn) {
        if (history == null || history.isEmpty()) {
            return;
        }
        List<Integer> userIndexes = new ArrayList<>();
        for (int i = 0; i < history.size(); i++) {
            LlmClient.Message m = history.get(i);
            if ("user".equals(m.role()) && m.toolCalls() == null && m.toolResults() == null) {
                userIndexes.add(i);
            }
        }
        if (userIndexes.size() > maxTurns) {
            int keepFrom = userIndexes.get(userIndexes.size() - maxTurns);
            history.subList(0, keepFrom).clear();
            int dropped = userIndexes.size() - maxTurns;
            userIndexes = userIndexes.subList(dropped, userIndexes.size()).stream()
                    .map(index -> index - keepFrom).toList();
        }
        // everything before the previous turn (i.e. before the second-last user message) is compacted; /compact
        // also compacts the previous turn itself
        int keep = keepPreviousTurn ? 2 : 1;
        if (userIndexes.size() < keep) {
            return;
        }
        int compactBefore = keepPreviousTurn ? userIndexes.get(userIndexes.size() - 2) : history.size();
        for (int i = 0; i < compactBefore; i++) {
            LlmClient.Message m = history.get(i);
            if (m.toolResults() == null || m.toolResults().isEmpty()) {
                continue;
            }
            boolean changed = false;
            List<LlmClient.ToolResult> compacted = new ArrayList<>(m.toolResults().size());
            for (LlmClient.ToolResult tr : m.toolResults()) {
                String content = tr.content();
                if (content != null && content.length() > compactChars) {
                    content = content.substring(0, compactChars)
                              + "\n... [earlier result compacted; call the tool again for the full data]";
                    changed = true;
                }
                compacted.add(new LlmClient.ToolResult(tr.toolCallId(), content));
            }
            if (changed) {
                history.set(i, LlmClient.Message.toolResults(compacted));
            }
        }
    }

    /**
     * Rough token count for prompt text and JSON: about four characters per token for the mix of English and JSON the
     * panel sends.
     */
    static int estimateTokens(long chars) {
        return (int) ((chars + 3) / 4);
    }

    static long historyChars(List<LlmClient.Message> history) {
        if (history == null) {
            return 0;
        }
        long chars = 0;
        for (LlmClient.Message m : history) {
            if (m.content() != null) {
                chars += m.content().length();
            }
            if (m.toolCalls() != null) {
                for (LlmClient.ToolCall tc : m.toolCalls()) {
                    chars += tc.name().length() + (tc.arguments() != null ? tc.arguments().toJson().length() : 0);
                }
            }
            if (m.toolResults() != null) {
                for (LlmClient.ToolResult tr : m.toolResults()) {
                    chars += tr.content() != null ? tr.content().length() : 0;
                }
            }
        }
        return chars;
    }

    private static long toolResultChars(List<LlmClient.Message> history) {
        long chars = 0;
        if (history != null) {
            for (LlmClient.Message m : history) {
                if (m.toolResults() != null) {
                    for (LlmClient.ToolResult tr : m.toolResults()) {
                        chars += tr.content() != null ? tr.content().length() : 0;
                    }
                }
            }
        }
        return chars;
    }

    private static int countTurns(List<LlmClient.Message> history) {
        int turns = 0;
        if (history != null) {
            for (LlmClient.Message m : history) {
                if ("user".equals(m.role()) && m.toolCalls() == null && m.toolResults() == null) {
                    turns++;
                }
            }
        }
        return turns;
    }

    private long toolSchemaChars() {
        long chars = 0;
        for (LlmClient.ToolDef def : buildTuiToolDefinitions()) {
            chars += 40 + def.name().length() + (def.description() != null ? def.description().length() : 0)
                     + (def.parameters() != null ? def.parameters().toJson().length() : 0);
        }
        return chars;
    }

    String describeContext() {
        StringBuilder sb = new StringBuilder();
        if (client == null) {
            sb.append("Provider: none (").append(initError != null ? initError : "no LLM client").append(")\n");
        } else {
            sb.append("Provider: ").append(client.apiType() != null ? client.apiType().name() : "unknown");
            if (client.endpointUrl() != null) {
                sb.append(" at ").append(client.endpointUrl());
            }
            sb.append(", model ").append(client.model() != null ? client.model() : "auto");
            sb.append(client.isLocalEndpoint() ? " (local)" : " (hosted)").append('\n');
        }
        sb.append("Tools: ").append(describeToolMode()).append('\n');
        int promptTokens = estimateTokens(buildSystemPrompt().length());
        int toolTokens = estimateTokens(toolSchemaChars());
        sb.append("Static prefix: ~").append(LlmClient.formatTokens(promptTokens + toolTokens))
                .append(" tokens (system prompt ~").append(LlmClient.formatTokens(promptTokens))
                .append(", tool schemas ~").append(LlmClient.formatTokens(toolTokens)).append(")\n");
        int historyTokens = estimateTokens(historyChars(messages));
        int resultTokens = estimateTokens(toolResultChars(messages));
        sb.append("History: ").append(countTurns(messages)).append(" turn(s), ")
                .append(messages != null ? messages.size() : 0).append(" message(s), ~")
                .append(LlmClient.formatTokens(historyTokens)).append(" tokens (tool results ~")
                .append(LlmClient.formatTokens(resultTokens)).append("); /compact shrinks it, /clear resets it\n");
        sb.append("Next request: ~").append(LlmClient.formatTokens(promptTokens + toolTokens + historyTokens))
                .append(" tokens before your question; session total so far ")
                .append(LlmClient.formatTokens(sessionTotalTokens)).append(" tokens");
        return sb.toString();
    }

    String compactHistoryNow() {
        if (messages == null || messages.isEmpty()) {
            return "History is empty, nothing to compact";
        }
        int before = estimateTokens(historyChars(messages));
        int messagesBefore = messages.size();
        compactHistory(messages, MAX_HISTORY_TURNS, COMPACT_TOOL_RESULT_CHARS, false);
        int after = estimateTokens(historyChars(messages));
        return "Compacted history: " + messagesBefore + " -> " + messages.size() + " message(s), ~"
               + LlmClient.formatTokens(before) + " -> ~" + LlmClient.formatTokens(after) + " tokens";
    }

    /**
     * Resends the last question. Any messages from the previous attempt (the question and whatever followed it) are
     * removed from the model history first so the retry starts from a clean turn.
     */
    boolean retryLastQuestion() {
        if (client == null || thinking.get()) {
            return false;
        }
        String question = null;
        for (int i = conversation.size() - 1; i >= 0; i--) {
            if (conversation.get(i).role() == AiRole.USER) {
                question = conversation.get(i).text();
                break;
            }
        }
        if (question == null || question.isBlank()) {
            return false;
        }
        if (messages != null) {
            for (int i = messages.size() - 1; i >= 0; i--) {
                LlmClient.Message m = messages.get(i);
                if ("user".equals(m.role()) && m.toolCalls() == null && m.toolResults() == null) {
                    messages.subList(i, messages.size()).clear();
                    break;
                }
            }
        }
        submitQuestion(question);
        return true;
    }

    /**
     * The usage figures of the Ctrl+U view as text for the chat: totals, then one line per model (and per route for
     * GenAI spans from the monitored integration).
     */
    String usageSummary() {
        List<AiUsageEntry> entries = combinedUsageEntries();
        if (entries.isEmpty()) {
            return "No AI usage yet. Ask a question, or run an integration with GenAI observability and --observe to see "
                   + "their usage here.";
        }
        int totalInput = 0;
        int totalOutput = 0;
        int totalTokens = 0;
        long totalLatency = 0;
        int tuiRequests = 0;
        int routeRequests = 0;
        Map<String, long[]> perModel = new LinkedHashMap<>();
        for (AiUsageEntry e : entries) {
            totalInput += e.inputTokens();
            totalOutput += e.outputTokens();
            totalTokens += e.totalTokens();
            totalLatency += e.latencyMs();
            if (e.source() == AiUsageSource.ROUTE) {
                routeRequests++;
            } else {
                tuiRequests++;
            }
            long[] stats = perModel.computeIfAbsent(modelTableKey(e), k -> new long[5]);
            stats[0]++;
            stats[1] += e.inputTokens();
            stats[2] += e.outputTokens();
            stats[3] += e.totalTokens();
            stats[4] += e.latencyMs();
        }
        StringBuilder sb = new StringBuilder();
        sb.append("**AI usage:** ").append(entries.size()).append(" request(s)");
        if (routeRequests > 0) {
            sb.append(" (").append(tuiRequests).append(" from this panel, ").append(routeRequests)
                    .append(" from the integration)");
        }
        sb.append("\n\n");
        sb.append("- **Tokens:** ").append(LlmClient.formatTokens(totalTokens))
                .append(" (in ").append(LlmClient.formatTokens(totalInput))
                .append(", out ").append(LlmClient.formatTokens(totalOutput)).append(")\n");
        sb.append("- **Avg latency:** ").append(formatSeconds(totalLatency / entries.size())).append("\n");
        if (sessionToolCalls > 0) {
            sb.append("- **AI time:** ").append(formatSeconds(totalLatency))
                    .append(", **Tool time:** ").append(formatSeconds(sessionToolTimeMs))
                    .append(" in ").append(sessionToolCalls).append(" tool call(s)\n");
        }
        AiUsageEntry last = usageHistory.isEmpty() ? null : usageHistory.get(usageHistory.size() - 1);
        if (last != null) {
            sb.append("- **Last request:** ").append(LlmClient.formatTokens(last.totalTokens()))
                    .append(" tokens in ").append(formatSeconds(last.latencyMs())).append("\n");
        }
        sb.append("\n| Model | Reqs | In | Out | Total | Avg |\n|---|---|---|---|---|---|\n");
        for (Map.Entry<String, long[]> entry : perModel.entrySet()) {
            long[] stats = entry.getValue();
            sb.append("| ").append(entry.getKey())
                    .append(" | ").append(stats[0])
                    .append(" | ").append(LlmClient.formatTokens((int) stats[1]))
                    .append(" | ").append(LlmClient.formatTokens((int) stats[2]))
                    .append(" | ").append(LlmClient.formatTokens((int) stats[3]))
                    .append(" | ").append(formatSeconds(stats[4] / stats[0]))
                    .append(" |\n");
        }
        sb.append("\n_Ctrl+U opens the full usage view with the per-question charts._");
        return sb.toString().strip();
    }

    private void toggleUsageView() {
        statsView = !statsView;
        statsScrollOffset = 0;
        if (statsView) {
            spanRefreshRequested = true;
        }
    }

    private String executeTuiTool(String name, JsonObject args) {
        if (toolRegistry == null) {
            return "Error: TUI tools not available";
        }
        try {
            return toolRegistry.execute(name, args);
        } catch (IllegalArgumentException e) {
            return "Unknown TUI tool: " + name;
        } catch (Exception e) {
            return "Error executing " + name + ": " + e.getMessage();
        }
    }

    // F8 intentionally excluded — it closes the panel and is handled above
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
        turnTimings.clear();
        statsScrollOffset = 0;
        sessionTotalTokens = 0;
        sessionToolTimeMs = 0;
        sessionToolCalls = 0;
        if (messages != null) {
            messages.clear();
        }
    }

    void setPromptHistoryForTesting(TuiPromptHistory history) {
        this.promptHistory = history;
    }

    List<String> promptHistoryEntriesForTesting() {
        return promptHistory == null ? List.of() : promptHistory.entriesForTesting();
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

    /**
     * Maps a configured tool mode to {@code auto}, {@code core} or {@code full}; blank means {@code auto}, anything
     * else is rejected with {@code null}.
     */
    static String normalizeToolMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return TOOL_MODE_AUTO;
        }
        String value = mode.trim().toLowerCase();
        return switch (value) {
            case TOOL_MODE_AUTO, TOOL_MODE_CORE, TOOL_MODE_FULL -> value;
            default -> null;
        };
    }

    void setToolRegistryForTesting(TuiToolRegistry registry) {
        this.toolRegistry = registry;
    }

    void setToolModeForTesting(String mode) {
        this.toolMode = normalizeToolMode(mode);
    }

    String systemPromptForTesting() {
        return buildSystemPrompt();
    }

    List<LlmClient.ToolDef> toolDefinitionsForTesting() {
        return buildTuiToolDefinitions();
    }

    String describeToolModeForTesting() {
        return describeToolMode();
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
        public void clearHistory() {
            if (promptHistory != null) {
                promptHistory.clear();
            }
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
        public String describeToolMode() {
            return AiPanel.this.describeToolMode();
        }

        @Override
        public boolean switchToolMode(String mode) {
            String normalized = normalizeToolMode(mode);
            if (normalized == null) {
                return false;
            }
            toolMode = normalized;
            TuiSettings settings = TuiSettings.load();
            settings.setAiTools(TOOL_MODE_AUTO.equals(normalized) ? null : normalized);
            settings.save();
            return true;
        }

        @Override
        public String describeContext() {
            return AiPanel.this.describeContext();
        }

        @Override
        public String compactHistoryNow() {
            return AiPanel.this.compactHistoryNow();
        }

        @Override
        public boolean retryLastQuestion() {
            return AiPanel.this.retryLastQuestion();
        }

        @Override
        public String usageSummary() {
            return AiPanel.this.usageSummary();
        }

        @Override
        public void resetUsage() {
            AiPanel.this.resetUsage();
        }

        @Override
        public void copyLastResponse() {
            copyLastResponseToClipboard();
        }

        @Override
        public void exportConversation() {
            exportChatToFile();
        }

        @Override
        public String systemPrompt() {
            return buildSystemPrompt();
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
