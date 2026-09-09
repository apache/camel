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
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import dev.tamboui.layout.Alignment;
import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Layout;
import dev.tamboui.layout.Rect;
import dev.tamboui.markdown.MarkdownView;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
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

    /**
     * Model round trips allowed per question. Local models return one tool call per round trip, so an
     * investigate-then-act task easily takes ten or more; a runaway loop is caught earlier by
     * {@link #MAX_IDENTICAL_TOOL_CALLS}. When the limit is reached the model is asked once more, without tools, to
     * answer with what it has.
     */
    static final int MAX_ITERATIONS = 25;
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
    /** A multi-line question shows up to this many rows; longer ones scroll to keep the cursor visible. */
    static final int MAX_INPUT_ROWS = 6;
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
    // set while the user asks about a paused live edit (F8 at the pause): input is accepted although a tool call is
    // still running, and Enter hands the question to the waiting call (true) or sends it as a normal question (false)
    private volatile Predicate<String> editQuestionHandler;
    // what became of a parked live edit; told to the model with the next question
    private volatile String pendingNote;
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
    private final AiCopyPopup copyPopup = new AiCopyPopup();
    private ClipboardWriter clipboard = TuiHelper::copyToClipboard;
    private final AiProviderSelector providerSelector = new AiProviderSelector();
    private AiProviderSwitchPopup.ProviderChoice sessionProviderChoice;
    private List<AiProviderSwitchPopup.ProviderChoice> providerChoicesForTesting;
    private boolean testingClientInjected;

    // ACP backend: set when the selected provider is an external coding agent (provider id "acp:*"). The agent
    // process is spawned lazily on the first prompt so a slow first npx download never freezes the UI.
    private static final Duration ACP_INIT_TIMEOUT = Duration.ofSeconds(120);
    private static final Duration ACP_SESSION_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration ACP_AUTH_TIMEOUT = Duration.ofSeconds(300);
    private static final String TUI_TOOL_PREFIX = "mcp__camel-tui__";
    private static final Set<String> FILE_OR_SHELL_KINDS = Set.of("edit", "delete", "move", "execute", "fetch");
    private static final Pattern TUI_TOOL_NAME = Pattern.compile("tui_[a-z0-9_]+");
    private static final String AGENT_COMMAND_PREFIX = "/agent:";
    private volatile AiProviderSelector.AcpPreset acpPreset;
    private volatile AcpAgentClient acpClient;
    private volatile AcpAgentClient.AgentInfo acpAgentInfo;
    private volatile String acpSessionId;
    private volatile boolean acpPreambleSent;
    private volatile AcpHeaderStrip.LogoMode acpLogoMode = AcpHeaderStrip.LogoMode.AUTO;
    private AcpHeaderStrip acpHeader = new AcpHeaderStrip();
    private String acpMcpUrl;
    private Path acpCwd;
    private AcpClientFactory acpClientFactory = this::spawnAcpAgent;
    private Callable<String> mcpUrlSupplier;
    private final AcpPermissionPopup permissionPopup = new AcpPermissionPopup();
    private volatile CompletableFuture<String> pendingPermission;

    /** Creates the client for a preset; replaced in tests with one backed by {@code FakeAcpAgent}. */
    interface AcpClientFactory {
        AcpAgentClient create(AiProviderSelector.AcpPreset preset, Path cwd) throws IOException;
    }

    // MCP facade for TUI tool access from the AI panel
    private McpFacade mcpFacade;
    // /write: confirm (dialog per write), auto (the model may skip it with confirm=false) or live (the edit is replayed
    // in the source editor and the user saves or discards it)
    private McpFacade.WriteMode writeMode = McpFacade.WriteMode.CONFIRM;
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
            mcpFacade.setWriteMode(writeMode);
        }
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
        editQuestionHandler = null;
        providerSwitchPopup.close();
    }

    /**
     * Opens the panel to ask about a paused live edit: the input is prefilled and stays usable although the write tool
     * call is still waiting. On Enter the handler gets the question; it returns true when the waiting tool call takes
     * it (the model answers within the same turn), false to send it as a normal question. Esc or F8 close the panel
     * without asking.
     */
    void askAboutEdit(String prefill, Predicate<String> handler) {
        if (client == null) {
            initClient();
        }
        visible = true;
        statsView = false;
        scrollOffset = 0;
        replaceInputBuffer(prefill);
        editQuestionHandler = handler;
    }

    boolean isAskingAboutEdit() {
        return editQuestionHandler != null;
    }

    /** Prepends a note to the next question (what the user did with a parked live edit). */
    void addPendingNote(String note) {
        pendingNote = pendingNote == null ? note : pendingNote + "\n" + note;
        conversation.add(new ConversationEntry(AiRole.SYSTEM, "(" + note + " The AI is told with your next question.)"));
    }

    void destroy() {
        close();
        stopAgentThread();
        closeAcpClient();
    }

    private void initClient() {
        modelCompletionCache = null;
        String provider = sessionProviderChoice != null
                ? sessionProviderChoice.provider() : TuiSettings.load().getAiProvider();
        if (AiProviderSelector.isAcp(provider)) {
            try {
                TuiSettings settings = TuiSettings.load();
                acpPreset = providerSelector.acpPreset(provider, settings);
                acpLogoMode = AcpHeaderStrip.LogoMode.parse(settings.getAiAcpLogos());
                initError = null;
            } catch (IllegalArgumentException e) {
                acpPreset = null;
                initError = e.getMessage();
            }
            client = null;
            return;
        }
        acpPreset = null;
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
        closeAcpClient();
        sessionProviderChoice = choice;
        if (AiProviderSelector.isAcp(choice.provider())) {
            client = null;
            initError = null;
            try {
                TuiSettings settings = TuiSettings.load();
                acpPreset = providerSelector.acpPreset(choice.provider(), settings);
                acpLogoMode = AcpHeaderStrip.LogoMode.parse(settings.getAiAcpLogos());
            } catch (IllegalArgumentException e) {
                acpPreset = null;
                sessionProviderChoice = null;
                conversation.add(new ConversationEntry(AiRole.ERROR, e.getMessage()));
                return;
            }
            conversation.add(new ConversationEntry(
                    AiRole.SYSTEM,
                    "Selected " + acpPreset.label() + ". The agent starts with your first question."));
            return;
        }
        acpPreset = null;
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

    private void closeAcpClient() {
        AcpAgentClient agent = acpClient;
        acpClient = null;
        acpSessionId = null;
        acpAgentInfo = null;
        acpPreambleSent = false;
        if (agent != null) {
            // Do not block the TUI event thread: close() destroys the agent process and waits up to 5 seconds for
            // it to die. Nothing here observes the shutdown, so it runs on a short-lived daemon thread.
            Thread closer = new Thread(agent::close, "tui-acp-close");
            closer.setDaemon(true);
            closer.start();
        }
    }

    private String acpLabel() {
        AcpAgentClient.AgentInfo info = acpAgentInfo;
        AiProviderSelector.AcpPreset preset = acpPreset;
        if (info != null) {
            return info.label();
        }
        return preset != null ? preset.label() : "ACP agent";
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
        if (copyPopup.isVisible()) {
            boolean handled = copyPopup.handleMouseEvent(me);
            copyChoice(copyPopup.consumePendingChoice());
            return handled;
        }
        if (permissionPopup.isVisible()) {
            boolean handled = permissionPopup.handleMouseEvent(me);
            AcpPermissionPopup.Decision decision = permissionPopup.consumeDecision();
            CompletableFuture<String> pending = pendingPermission;
            if (decision != null && pending != null) {
                pending.complete(decision.optionId());
            }
            return handled;
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
        if (permissionPopup.isVisible()) {
            if (ke.isCtrlC()) {
                interruptBusyOperation();
                return true;
            }
            permissionPopup.handleKeyEvent(ke);
            AcpPermissionPopup.Decision decision = permissionPopup.consumeDecision();
            if (decision != null) {
                CompletableFuture<String> pending = pendingPermission;
                if (pending != null) {
                    pending.complete(decision.optionId());
                }
            }
            return true;
        }
        if (providerSwitchPopup.isVisible()) {
            providerSwitchPopup.handleKeyEvent(ke);
            AiProviderSwitchPopup.ProviderChoice choice = providerSwitchPopup.consumePendingChoice();
            if (choice != null) {
                applyProviderChoice(choice);
            }
            return true;
        }
        if (copyPopup.isVisible()) {
            copyPopup.handleKeyEvent(ke);
            copyChoice(copyPopup.consumePendingChoice());
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
        if (ke.hasCtrl() && ke.isCharIgnoreCase('n') && !statsView && (!thinking.get() || editQuestionHandler != null)) {
            // a line break in the question; terminals deliver Shift+Enter and Alt+Enter as plain Enter or nothing
            insertInput("\n");
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
        if (!statsView && ke.isKey(KeyCode.UP) && moveCursorLine(-1)) {
            return true;
        }
        if (!statsView && ke.isKey(KeyCode.DOWN) && moveCursorLine(1)) {
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
        if (editQuestionHandler != null && ke.isKey(KeyCode.ESCAPE)) {
            // back to the paused edit without asking
            close();
            return true;
        }
        if (thinking.get() && editQuestionHandler == null) {
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
            cursorPos = lineStart(cursorPos);
            return true;
        }
        if (ke.isKey(KeyCode.END)) {
            cursorPos = lineEnd(cursorPos);
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
        if (isGlobalShortcut(ke)) {
            // function keys (F2 actions, F3 switch integration, F10 run, ...), Ctrl+F (browse files) and Ctrl+L
            // (log pin) keep working while the panel has the focus
            return false;
        }
        return true;
    }

    private static boolean isGlobalShortcut(KeyEvent ke) {
        if (ke.code() != null && ke.code() != KeyCode.F8 && ke.code().name().matches("F\\d+")) {
            return true;
        }
        return ke.hasCtrl() && (ke.isCharIgnoreCase('f') || ke.isCharIgnoreCase('l'));
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
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        if (historySearchActive) {
            searchTerm.append(normalized.replace('\n', ' '));
            performSearch(searchIndex >= 0 ? searchIndex : promptHistory.size() - 1);
            return;
        }
        // pasted line breaks stay: a multi-line question is sent as typed
        insertInput(normalized);
    }

    private void insertInput(String text) {
        if (promptHistory != null) {
            promptHistory.resetNavigation();
        }
        completionMatches = null;
        inputBuffer.insert(cursorPos, text);
        cursorPos += text.length();
    }

    // ---- multi-line input: the buffer holds '\n', the cursor moves by line with Up/Down, Home/End ----

    private int lineStart(int pos) {
        int nl = inputBuffer.lastIndexOf("\n", Math.max(0, pos - 1));
        return pos > 0 && nl >= 0 && nl < pos ? nl + 1 : 0;
    }

    private int lineEnd(int pos) {
        int nl = inputBuffer.indexOf("\n", pos);
        return nl < 0 ? inputBuffer.length() : nl;
    }

    /** Moves the cursor to the previous (-1) or next (1) line of the input; false when there is no such line. */
    private boolean moveCursorLine(int direction) {
        int start = lineStart(cursorPos);
        int column = cursorPos - start;
        int targetStart;
        if (direction < 0) {
            if (start == 0) {
                return false;
            }
            targetStart = lineStart(start - 1);
        } else {
            int end = lineEnd(cursorPos);
            if (end >= inputBuffer.length()) {
                return false;
            }
            targetStart = end + 1;
        }
        int targetEnd = lineEnd(targetStart);
        cursorPos = Math.min(targetStart + column, targetEnd);
        return true;
    }

    private List<String> inputLines() {
        return List.of(inputBuffer.toString().split("\n", -1));
    }

    /** Rows the input needs: one per line, capped so the conversation keeps most of the panel. */
    private int inputRows() {
        return Math.min(MAX_INPUT_ROWS, inputLines().size());
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
            names = slashCommands.completionsFor(text, agentCommandDescriptors()).stream()
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
            case "write" -> List.of("confirm", "auto", "live");
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
            return;
        }
        Predicate<String> handler = editQuestionHandler;
        if (handler != null) {
            editQuestionHandler = null;
            if (handler.test(input)) {
                // the waiting tui_write_file call returns with the question and the model answers in this turn
                conversation.add(new ConversationEntry(AiRole.USER, input));
                questionCounter++;
                log(LogLevel.QUESTION, "Question about the edit", input);
                return;
            }
        }
        submitQuestion(input);
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
        if (acpPreset != null && input.startsWith(AGENT_COMMAND_PREFIX)) {
            String rest = input.substring(AGENT_COMMAND_PREFIX.length()).strip();
            if (rest.isEmpty()) {
                conversation.add(new ConversationEntry(AiRole.SYSTEM, agentCommandListing()));
                return;
            }
            // explicit escape: reaches the agent even when the name is also a panel command
            submitQuestion(input);
            return;
        }

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

        if (parsed.isEmpty() && acpPreset != null) {
            // not a panel command: hand it to the agent verbatim (its own slash commands and skills)
            submitQuestion(input);
            return;
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
            CompletableFuture<String> permission = pendingPermission;
            if (permission != null) {
                permission.complete(null);
                permissionPopup.close();
            }
            AcpAgentClient agent = acpClient;
            String session = acpSessionId;
            if (agent != null && session != null) {
                // Do not block the TUI event thread: cancel() writes to the agent under the client's writer lock.
                // prompt() sends the same notification on its own interrupt path; a duplicate cancel is harmless.
                Thread canceller = new Thread(() -> agent.cancel(session), "tui-acp-cancel");
                canceller.setDaemon(true);
                canceller.start();
            }
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
        if (acpPreset != null) {
            submitAcpQuestion(question);
            return;
        }
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
        String note = pendingNote;
        pendingNote = null;
        messages.add(LlmClient.Message.user(contextualize(note == null ? question : "[" + note + "]\n" + question)));

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
                    if (toolRegistry != null) {
                        // waiting for the user to confirm a file write is not tool time
                        toolElapsed = Math.max(0, toolElapsed - toolRegistry.consumeConfirmWaitMs());
                    }
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
                completeTurn(response.text(), false, totalUsage, turnAiMs, turnToolMs, turnToolCalls, messages);
                return;
            }
        }

        // out of round trips: ask the model once more, without tools, so the user gets an answer rather than an error
        messages.add(LlmClient.Message.user(
                "You have used all " + MAX_ITERATIONS + " tool calls available for this question and cannot call any"
                                            + " more. Answer now: summarize what you did, what you found, what"
                                            + " failed, and what the user could try next."));
        long wrapUpStart = System.currentTimeMillis();
        drainClientOutput();
        LlmClient.ChatResponse wrapUp = client.chatWithTools(systemPrompt, messages, List.of());
        long wrapUpLatency = System.currentTimeMillis() - wrapUpStart;
        turnAiMs += wrapUpLatency;
        if (wrapUp != null) {
            totalUsage = totalUsage.add(wrapUp.usage());
            recordUsage(wrapUp, wrapUpLatency);
            if (wrapUp.text() != null && !wrapUp.text().isBlank()) {
                completeTurn(wrapUp.text(), true, totalUsage, turnAiMs, turnToolMs, turnToolCalls, messages);
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
     * Ends the turn with the model's answer: records it in the conversation, the usage charts and the AI log.
     * {@code wrapUp} marks an answer that was forced after the tool call limit was reached.
     */
    private void completeTurn(
            String text, boolean wrapUp, LlmClient.TokenUsage totalUsage, long turnAiMs, long turnToolMs,
            int turnToolCalls, List<LlmClient.Message> messages) {
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
            String label = wrapUp ? "Response after reaching the tool call limit (" : "Response (";
            log(LogLevel.RESPONSE, label + entry.timing() + tokenInfo + describeCacheSignal(totalUsage) + ")", text);
        } else {
            String err = "Empty response from LLM.";
            conversation.add(new ConversationEntry(AiRole.ERROR, err));
            log(LogLevel.ERROR, "Error", err);
        }
        scrollOffset = 0;
        messages.add(LlmClient.Message.assistantWithToolCalls(text, List.of()));
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

    private void submitAcpQuestion(String question) {
        conversation.add(new ConversationEntry(AiRole.USER, question));
        log(LogLevel.QUESTION, "Question", question);
        thinkingVerb = THINKING_VERBS.get(ThreadLocalRandom.current().nextInt(THINKING_VERBS.size()));
        thinkingStartTime = System.currentTimeMillis();
        thinking.set(true);
        // The panel keeps showing exactly what was typed (including an explicit /agent: escape), but the agent
        // itself only ever sees its own command name: /agent:clear reaches it as /clear.
        String wireQuestion = question.startsWith(AGENT_COMMAND_PREFIX)
                ? "/" + question.substring(AGENT_COMMAND_PREFIX.length())
                : question;
        agentThread = new Thread(() -> {
            try {
                runAcpTurn(wireQuestion);
            } catch (IOException | RuntimeException e) {
                reportAcpTurnFailure(e);
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

    /**
     * Reports a failed ACP turn. The agent process and its session are only thrown away when the failure means they are
     * gone anyway, so a JSON-RPC error from a healthy agent does not cost the user the conversation context. An
     * interrupted turn is already reported as "(cancelled)" by {@link #interruptBusyOperation()}.
     */
    private void reportAcpTurnFailure(Exception e) {
        AcpAgentClient.AcpException acp = e instanceof AcpAgentClient.AcpException a ? a : null;
        if (acp != null && acp.code() == AcpAgentClient.INTERRUPTED) {
            return;
        }
        String message = e.getMessage() != null ? e.getMessage() : e.toString();
        conversation.add(new ConversationEntry(AiRole.ERROR, message));
        log(LogLevel.ERROR, "ACP error", message);
        AcpAgentClient agent = acpClient;
        boolean dead = agent == null || !agent.isAlive()
                || (acp != null && (acp.code() == AcpAgentClient.CONNECTION || acp.code() == AcpAgentClient.TIMEOUT));
        if (dead) {
            closeAcpClient();
        }
    }

    private void runAcpTurn(String question) throws IOException {
        AcpAgentClient agent = ensureAcpSession();
        boolean agentCommand = question.startsWith("/");
        String text = acpPreambleSent || agentCommand ? question : buildSystemPrompt() + "\n\n" + question;
        AcpTurnListener listener = new AcpTurnListener();
        String stopReason = agent.prompt(acpSessionId, text, listener);
        if (!agentCommand) {
            acpPreambleSent = true;
        }
        listener.finish(stopReason);
        scrollOffset = 0;
    }

    /**
     * The ACP agent's advertised commands as display-only descriptors (null executor: they are forwarded, never run
     * locally). Displayed and completed under the {@code agent:} name so they never collide with a panel command of the
     * same name; the bare name is registered as an alias so typing it still suggests the prefixed form.
     */
    private List<AiSlashCommandRegistry.Descriptor> agentCommandDescriptors() {
        AcpAgentClient agent = acpClient;
        if (acpPreset == null || agent == null) {
            return List.of();
        }
        List<AiSlashCommandRegistry.Descriptor> out = new ArrayList<>();
        for (AcpAgentClient.AgentCommand command : agent.availableCommands()) {
            out.add(new AiSlashCommandRegistry.Descriptor(
                    "agent:" + command.name(), List.of(command.name()), command.description(), command.hint(), null));
        }
        return out;
    }

    /**
     * Formats the {@code /agent:} listing shown when the prefix is typed alone, e.g. {@code /agent:review focus area}.
     */
    private String agentCommandListing() {
        List<AiSlashCommandRegistry.Descriptor> commands = agentCommandDescriptors();
        if (commands.isEmpty()) {
            return "The agent has not advertised any commands yet.";
        }
        List<AiSlashCommandRegistry.Descriptor> sorted = commands.stream()
                .sorted(Comparator.comparing(AiSlashCommandRegistry.Descriptor::name))
                .toList();
        int width = AiSlashCommandRegistry.commandColumnWidth(sorted);
        StringBuilder sb = new StringBuilder("Agent commands (").append(sorted.size()).append(")\n\n```\n");
        for (AiSlashCommandRegistry.Descriptor descriptor : sorted) {
            sb.append(AiSlashCommandRegistry.formatAlignedLine(descriptor, width)).append('\n');
        }
        return sb.append("```").toString();
    }

    /**
     * Spawns the agent and negotiates the protocol on first use, then opens a session when none is active (first
     * prompt, or after /clear). Runs on the agent thread; every failure is reported by the caller.
     */
    private AcpAgentClient ensureAcpSession() throws IOException {
        AiProviderSelector.AcpPreset preset = acpPreset;
        AcpAgentClient agent = acpClient;
        if (agent == null || !agent.isAlive()) {
            closeAcpClient();
            String mcpUrl;
            try {
                mcpUrl = mcpUrlSupplier != null ? mcpUrlSupplier.call() : null;
            } catch (Exception e) {
                throw new IllegalStateException("Could not start the TUI MCP server: " + e.getMessage(), e);
            }
            if (mcpUrl == null) {
                throw new IllegalStateException("The TUI MCP server is not available in this session.");
            }
            Path cwd = acpWorkingDir();
            conversation.add(new ConversationEntry(AiRole.SYSTEM, "Starting " + preset.label() + "…"));
            agent = acpClientFactory.create(preset, cwd);
            agent.setPermissionHandler(new AcpPanelPermissionHandler());
            AcpAgentClient.AgentInfo info;
            try {
                info = agent.initialize(ACP_INIT_TIMEOUT);
                if (info.protocolVersion() != AcpAgentClient.PROTOCOL_VERSION) {
                    String mismatch = preset.label() + " negotiated ACP protocol version " + info.protocolVersion()
                                      + "; the TUI supports version " + AcpAgentClient.PROTOCOL_VERSION + ".";
                    throw new IllegalStateException(mismatch);
                }
                if (!info.httpMcp()) {
                    throw new IllegalStateException(
                            preset.label() + " does not support HTTP MCP servers, so it cannot reach the TUI tools.");
                }
            } catch (RuntimeException e) {
                agent.close();
                throw e;
            }
            acpAgentInfo = info;
            // written before the volatile acpClient write below, which publishes them to the other threads
            acpMcpUrl = mcpUrl;
            acpCwd = cwd;
            acpClient = agent;
            acpSessionId = null;
            log(LogLevel.RESULT, "ACP agent started", info.label() + " cwd=" + cwd);
        }
        if (acpSessionId == null) {
            acpSessionId = openAcpSession(agent, acpAgentInfo, acpMcpUrl, acpCwd);
            acpPreambleSent = false;
            log(LogLevel.RESULT, "ACP session", acpSessionId);
        }
        return agent;
    }

    private String openAcpSession(AcpAgentClient agent, AcpAgentClient.AgentInfo info, String mcpUrl, Path cwd) {
        try {
            return agent.newSession(cwd, mcpUrl, ACP_SESSION_TIMEOUT);
        } catch (AcpAgentClient.AcpException e) {
            if (e.code() != AcpAgentClient.AUTH_REQUIRED) {
                throw e;
            }
            AcpAgentClient.AuthMethod method = info.authMethods().stream()
                    .filter(m -> m.id() != null && (m.type() == null || "agent".equals(m.type())))
                    .findFirst()
                    .orElse(null);
            if (method == null) {
                throw new IllegalStateException("Authentication required. " + acpPreset.loginHint());
            }
            conversation.add(new ConversationEntry(
                    AiRole.SYSTEM, "Authenticating with " + info.label() + " (" + method.name() + ")…"));
            try {
                agent.authenticate(method.id(), ACP_AUTH_TIMEOUT);
                return agent.newSession(cwd, mcpUrl, ACP_SESSION_TIMEOUT);
            } catch (AcpAgentClient.AcpException retry) {
                throw new IllegalStateException(
                        "Authentication failed: " + retry.getMessage() + ". " + acpPreset.loginHint());
            }
        }
    }

    private AcpHeaderStrip.Model acpHeaderModel() {
        AiProviderSelector.AcpPreset preset = acpPreset;
        AcpAgentClient agent = acpClient;
        int commands = agent != null ? agent.availableCommands().size() : 0;
        return new AcpHeaderStrip.Model(
                preset.label(), preset.glyph(), preset.color(), preset.logo(), acpLabel(),
                acpSessionId, acpCwd, commands);
    }

    private Path acpWorkingDir() {
        IntegrationInfo info = ctx != null ? ctx.findSelectedIntegration() : null;
        if (info != null && info.configProperties != null) {
            Path dir = FilesBrowser.resolveSourceDirectory(info);
            if (dir != null && Files.isDirectory(dir)) {
                return dir.toAbsolutePath();
            }
        }
        return Path.of("").toAbsolutePath();
    }

    private int replaceOrAppend(int index, ConversationEntry entry) {
        if (index >= 0 && index < conversation.size()) {
            conversation.set(index, entry);
            return index;
        }
        conversation.add(entry);
        return conversation.size() - 1;
    }

    /**
     * Turns agent updates into conversation entries. The callbacks run on the ACP reader thread while
     * {@link #finish(String)} runs on the agent thread, and cancelling a turn returns from
     * {@link AcpAgentClient#prompt} without waiting for the reader, so every method is synchronized on the listener.
     */
    private final class AcpTurnListener implements AcpAgentClient.Listener {
        private final StringBuilder text = new StringBuilder();
        private int liveIndex = -1;
        private final Map<String, Integer> toolLines = new HashMap<>();
        private final Map<String, String> toolTitles = new HashMap<>();
        private volatile long usedTokens;

        @Override
        public synchronized void onTextChunk(String chunk) {
            text.append(chunk);
            liveIndex = replaceOrAppend(liveIndex, new ConversationEntry(AiRole.ASSISTANT, text.toString()));
            scrollOffset = 0;
        }

        @Override
        public synchronized void onToolCall(String toolCallId, String title, String kind, JsonObject rawInput) {
            conversation.add(new ConversationEntry(AiRole.SYSTEM, TuiIcons.GEAR + " " + title));
            if (toolCallId != null) {
                toolLines.put(toolCallId, conversation.size() - 1);
                toolTitles.put(toolCallId, title);
            }
            // text after a tool call starts a new assistant entry below the tool line
            liveIndex = -1;
            text.setLength(0);
            log(LogLevel.TOOL, title, rawInput != null ? rawInput.toJson() : "");
        }

        @Override
        public synchronized void onToolCallUpdate(String toolCallId, String status, String contentText) {
            Integer index = toolCallId != null ? toolLines.get(toolCallId) : null;
            if (index == null) {
                return;
            }
            String title = toolTitles.getOrDefault(toolCallId, "tool");
            if ("completed".equals(status)) {
                replaceOrAppend(index, new ConversationEntry(AiRole.SYSTEM, TuiIcons.CHECK + " " + title));
                log(LogLevel.RESULT, title, contentText != null ? contentText : "completed");
            } else if ("failed".equals(status)) {
                replaceOrAppend(index, new ConversationEntry(AiRole.SYSTEM, TuiIcons.CROSS + " " + title));
                log(LogLevel.ERROR, title, contentText != null ? contentText : "failed");
            }
        }

        @Override
        public synchronized void onUsage(long used, long size) {
            usedTokens = used;
        }

        synchronized void finish(String stopReason) {
            long elapsed = System.currentTimeMillis() - thinkingStartTime;
            int tokens = (int) Math.min(Integer.MAX_VALUE, usedTokens);
            if (liveIndex >= 0) {
                // the agent runs its own tools, so ACP reports no ai/tool split to fill in
                replaceOrAppend(liveIndex,
                        new ConversationEntry(AiRole.ASSISTANT, text.toString(), elapsed, 0, 0, 0, tokens));
            }
            if (tokens > 0) {
                AiProviderSelector.AcpPreset preset = acpPreset;
                sessionTotalTokens = tokens;
                usageHistory.add(new AiUsageEntry(
                        acpLabel(), preset != null ? preset.id() : "acp", 0, 0, tokens, elapsed,
                        stopReason, Instant.now()));
            }
            switch (stopReason) {
                case "end_turn", "cancelled" -> {
                    // cancelled is reported by interruptBusyOperation()
                }
                case "refusal" -> conversation.add(new ConversationEntry(AiRole.ERROR, "The agent refused to continue."));
                default -> conversation.add(new ConversationEntry(AiRole.SYSTEM, "(stopped: " + stopReason + ")"));
            }
            log(LogLevel.RESPONSE, "Response (" + formatSeconds(elapsed) + ", " + stopReason + ")", text.toString());
        }
    }

    /**
     * Policy A from the design: calls to the camel-tui MCP server are approved silently (allow-always preferred),
     * everything else is put in front of the user. Runs on the ACP request thread and blocks until the user answers or
     * the turn is cancelled.
     */
    private final class AcpPanelPermissionHandler implements AcpAgentClient.PermissionHandler {
        @Override
        public String decide(JsonObject toolCall, List<JsonObject> options) {
            String name = String.valueOf(toolCall.getStringOrDefault("name", ""));
            String title = String.valueOf(toolCall.getStringOrDefault("title", ""));
            String kind = String.valueOf(toolCall.getStringOrDefault("kind", ""));
            if (isTuiTool(name, title, kind)) {
                String optionId = firstOptionOfKind(options, "allow_always");
                if (optionId == null) {
                    optionId = firstOptionOfKind(options, "allow_once");
                }
                if (optionId == null && !options.isEmpty()) {
                    optionId = options.get(0).getString("optionId");
                }
                log(LogLevel.TOOL, "Auto-approved TUI tool", title);
                return optionId;
            }
            CompletableFuture<String> decision = new CompletableFuture<>();
            pendingPermission = decision;
            permissionPopup.open(toolCall, options);
            log(LogLevel.TOOL, "Permission requested", title);
            try {
                return decision.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            } catch (ExecutionException e) {
                return null;
            } finally {
                pendingPermission = null;
                permissionPopup.close();
            }
        }
    }

    /**
     * Only a call to a tool the TUI itself registers counts as a camel-tui tool: the name the Claude adapter sends
     * ({@code mcp__camel-tui__tui_get_state}) or a title of the form {@code tui_get_state (camel-tui MCP Server)}.
     * Kinds that touch files or run commands never qualify, whatever the title says: a path containing "camel-tui" is
     * not a tool identity.
     */
    private boolean isTuiTool(String name, String title, String kind) {
        if (FILE_OR_SHELL_KINDS.contains(kind)) {
            return false;
        }
        String tool = null;
        if (name.startsWith(TUI_TOOL_PREFIX)) {
            tool = name.substring(TUI_TOOL_PREFIX.length());
        } else {
            int paren = title.indexOf("(camel-tui");
            if (paren > 0) {
                tool = title.substring(0, paren).strip();
            }
        }
        return tool != null && isRegisteredTuiTool(tool);
    }

    private boolean isRegisteredTuiTool(String tool) {
        TuiToolRegistry registry = toolRegistry;
        if (registry != null) {
            return registry.getToolDefinitions().stream().anyMatch(td -> td.name().equals(tool));
        }
        // no registry wired yet (tests, or before the MCP facade exists): accept the TUI's own naming scheme
        return TUI_TOOL_NAME.matcher(tool).matches();
    }

    private static String firstOptionOfKind(List<JsonObject> options, String kind) {
        for (JsonObject option : options) {
            if (kind.equals(option.getString("kind"))) {
                return option.getString("optionId");
            }
        }
        return null;
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
        } else if (acpPreset != null) {
            titleLine = Line.from(
                    Span.styled(" AI ", Style.EMPTY.bold()),
                    Span.styled("· " + acpLabel() + " ", Style.EMPTY.dim()));
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
            acpHeader.hide(frame);
            return;
        }

        if (statsView) {
            acpHeader.hide(frame);
            renderStats(frame, inner);
            if (providerSwitchPopup.isVisible()) {
                providerSwitchPopup.render(frame, inner);
            }
            if (copyPopup.isVisible()) {
                copyPopup.render(frame, inner);
            }
            if (permissionPopup.isVisible()) {
                permissionPopup.render(frame, inner);
            }
            return;
        }

        Rect body = inner;
        if (acpPreset != null && acpSessionId != null && inner.height() >= 8) {
            List<Rect> top = Layout.vertical()
                    .constraints(Constraint.length(AcpHeaderStrip.ROWS), Constraint.length(1), Constraint.fill())
                    .split(inner);
            acpHeader.render(frame, top.get(0), acpHeaderModel(), acpLogoMode);
            frame.renderWidget(Paragraph.from(Line.from(Span.styled("─".repeat(top.get(1).width()), Style.EMPTY.dim()))),
                    top.get(1));
            body = top.get(2);
        } else {
            acpHeader.hide(frame);
        }

        // Split the body area: conversation (fill) + optional slash hints + separator (1 row) + input (1 row per line)
        List<AiSlashCommandRegistry.Descriptor> slashHints = slashCommandHints();
        int hintRows = slashHints.isEmpty() ? 0 : slashHints.size();
        int inputRows = historySearchActive ? 1 : inputRows();
        List<Rect> parts;
        if (hintRows == 0) {
            parts = Layout.vertical()
                    .constraints(Constraint.fill(), Constraint.length(1), Constraint.length(inputRows))
                    .split(body);
        } else {
            parts = Layout.vertical()
                    .constraints(Constraint.fill(), Constraint.length(hintRows), Constraint.length(1),
                            Constraint.length(inputRows))
                    .split(body);
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
        if (copyPopup.isVisible()) {
            copyPopup.render(frame, inner);
        }
        if (permissionPopup.isVisible()) {
            permissionPopup.render(frame, inner);
        }
    }

    private List<AiSlashCommandRegistry.Descriptor> slashCommandHints() {
        if (thinking.get() || statsView || providerSwitchPopup.isVisible()) {
            return List.of();
        }
        return slashCommands.completionsFor(inputBuffer.toString(), agentCommandDescriptors());
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
        if (area.height() < 1) {
            return;
        }
        String prompt = INPUT_PROMPT;
        List<String> lines = inputLines();
        boolean locked = thinking.get() && editQuestionHandler == null;
        // the line the cursor is on, and which lines are shown when there are more than rows
        int cursorLine = 0;
        int cursorColumn = cursorPos;
        for (int i = 0, offset = 0; i < lines.size(); i++) {
            int end = offset + lines.get(i).length();
            if (cursorPos <= end || i == lines.size() - 1) {
                cursorLine = i;
                cursorColumn = cursorPos - offset;
                break;
            }
            offset = end + 1;
        }
        int rows = Math.max(1, area.height());
        int firstLine = Math.max(0, Math.min(cursorLine - rows + 1, lines.size() - rows));
        int maxWidth = area.width() - prompt.length();
        if (maxWidth <= 0) {
            return;
        }
        List<Line> rendered = new ArrayList<>();
        for (int i = firstLine; i < Math.min(lines.size(), firstLine + rows); i++) {
            String text = lines.get(i);
            List<Span> spans = new ArrayList<>();
            String gutter = i == 0 ? prompt : " ".repeat(prompt.length());
            spans.add(Span.styled(gutter, Style.EMPTY.fg(Theme.accent()).bold()));
            if (locked) {
                spans.add(Span.styled(text, Style.EMPTY.dim()));
            } else if (i != cursorLine) {
                spans.add(Span.raw(text.length() > maxWidth ? text.substring(0, maxWidth) : text));
            } else {
                // ensure the cursor is visible by adjusting the text window of its line
                int windowStart = cursorColumn > maxWidth - 1 ? cursorColumn - maxWidth + 1 : 0;
                String visible = text.substring(windowStart, Math.min(text.length(), windowStart + maxWidth));
                int cursorInWindow = cursorColumn - windowStart;
                if (cursorInWindow >= 0 && cursorInWindow < visible.length()) {
                    spans.add(Span.raw(visible.substring(0, cursorInWindow)));
                    spans.add(Span.styled(String.valueOf(visible.charAt(cursorInWindow)), Style.EMPTY.reversed()));
                    spans.add(Span.raw(visible.substring(cursorInWindow + 1)));
                } else {
                    spans.add(Span.raw(visible));
                    if (cursorInWindow == visible.length()) {
                        spans.add(Span.styled(" ", Style.EMPTY.reversed()));
                    }
                }
                if (lines.size() == 1 && cursorPos == inputBuffer.length()) {
                    Optional<String> placeholder = slashCommands.placeholderFor(text);
                    if (placeholder.isPresent()) {
                        spans.add(Span.styled(" " + placeholder.get(), Style.EMPTY.dim()));
                    }
                }
            }
            rendered.add(Line.from(spans));
        }
        frame.renderWidget(Paragraph.from(new Text(rendered, Alignment.LEFT)), area);
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
        if (permissionPopup.isVisible()) {
            permissionPopup.renderFooter(spans);
            return;
        }
        if (providerSwitchPopup.isVisible()) {
            providerSwitchPopup.renderFooter(spans);
            return;
        }
        if (copyPopup.isVisible()) {
            copyPopup.renderFooter(spans);
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
            if (editQuestionHandler != null) {
                TuiHelper.hint(spans, "Enter", "ask about the edit");
                TuiHelper.hint(spans, "Esc", "back to the edit");
            } else if (!thinking.get()) {
                TuiHelper.hint(spans, "Enter", "send");
                TuiHelper.hint(spans, "Ctrl+N", "newline");
                TuiHelper.hint(spans, "Ctrl+R", "search");
            } else {
                TuiHelper.hint(spans, "Esc/Ctrl+C", "interrupt");
            }
        }
    }

    /**
     * Copies the last answer: the code alone when the answer has one fenced code block (that is what one usually wants,
     * and selecting it with the mouse drags the panel borders along), a choice of the blocks or the whole answer when
     * there are several, and the whole answer when there is no code.
     */
    private void copyLastResponseToClipboard() {
        for (int i = conversation.size() - 1; i >= 0; i--) {
            ConversationEntry entry = conversation.get(i);
            if (entry.role() == AiRole.ASSISTANT && entry.text() != null && !entry.text().isEmpty()) {
                List<AiCodeBlocks.CodeBlock> blocks = AiCodeBlocks.parse(entry.text());
                if (blocks.isEmpty()) {
                    copyChoice(new AiCopyPopup.Choice("", "response", entry.text()));
                } else if (blocks.size() == 1) {
                    AiCodeBlocks.CodeBlock block = blocks.get(0);
                    copyChoice(new AiCopyPopup.Choice("", block.label(), block.code()));
                } else {
                    copyPopup.open(entry.text(), blocks);
                }
                return;
            }
        }
        notify("No AI response to copy", true);
    }

    private void copyChoice(AiCopyPopup.Choice choice) {
        if (choice == null) {
            return;
        }
        try {
            clipboard.copy(choice.text());
            notify("Copied " + choice.what() + " to clipboard", false);
        } catch (Exception e) {
            notify("Clipboard not available: " + e.getMessage(), true);
        }
    }

    /** Writes to the system clipboard; replaced in tests. */
    interface ClipboardWriter {
        void copy(String text) throws IOException;
    }

    void setClipboardForTesting(ClipboardWriter clipboard) {
        this.clipboard = clipboard;
    }

    boolean isCopyPopupVisibleForTesting() {
        return copyPopup.isVisible();
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
                Paragraph.from(new Text(summaryLines, Alignment.LEFT)),
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
        frame.renderWidget(Paragraph.from(new Text(lines, Alignment.LEFT)), area);
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
        sb.append("call tui_get_options only when unsure which tab has it\n");
        sb.append("- tui_get_state tells which integration and tab is selected; tui_get_processor_detail explains ");
        sb.append("a route's steps; tui_get_status has data no tab shows (context, runtime, health, properties)\n");
        sb.append("- Your own tool calls are in the AI log (tui_get_ai_log); the MCP log only has external clients\n");
        sb.append("- Be concise and actionable; when something looks wrong, explain it and suggest fixes\n");
        sb.append("- tui_control stops/starts routes and integrations gracefully; its reset-stats action clears ");
        sb.append("statistics without touching the routes\n");
        sb.append("- tui_infra lists infra services (brokers, databases) and their logs\n");
        sb.append("- Never restart, stop or kill an integration or infra service unless the user explicitly ");
        sb.append("asked for that\n");
        sb.append("- If a tool call returns an error, do not repeat it with the same arguments; ");
        sb.append("say what failed and what to try\n");
        sb.append("- To feed a route that consumes from a broker (MQTT, Kafka, JMS), tui_send_message can publish ");
        sb.append("to the broker with the route's own component and options\n");
        sb.append("- To edit: tui_get_files, then tui_write_file with the complete file; the user confirms, never retry ");
        sb.append("a rejected write. Invalid YAML/properties is refused with errors: fix them (tui_catalog_doc has the ");
        sb.append("option names)\n");
        sb.append("- tui_set_log_level is the app's root logger, only when asked; 'log at WARN' in a route is the log ");
        sb.append("step's loggingLevel in the source\n");
        sb.append("- Simple: functions inside ${...}, operators between them: ${header.a} == 'b', ");
        sb.append("${body} ?: 'none'; tui_eval_expression checks one, tui_catalog_doc simple lists them\n");
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
        if (acpPreset != null) {
            return acpLabel() + " reaches the camel-tui tools through the MCP server directly; "
                   + "the tool set only applies to LLM providers";
        }
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
        if (acpPreset != null) {
            return describeAcpContext();
        }
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

    /**
     * The {@code /context} answer while an ACP agent is selected. The agent owns the conversation history and runs its
     * own tools, so the interesting figures are its session, the MCP server it was given and the preamble the panel
     * prepends to the first prompt.
     */
    private String describeAcpContext() {
        AiProviderSelector.AcpPreset preset = acpPreset;
        AcpAgentClient agent = acpClient;
        StringBuilder sb = new StringBuilder();
        sb.append("Agent: ").append(acpLabel())
                .append(" (preset ").append(preset != null ? preset.id() : "acp").append(")\n");
        if (acpSessionId != null) {
            sb.append("Session: ").append(acpSessionId).append(" in ").append(acpCwd).append('\n');
            sb.append("MCP: ").append(acpMcpUrl).append(" (camel-tui tools are approved automatically)\n");
        } else {
            sb.append("Session: not started yet; the next prompt opens one and starts the MCP server on demand\n");
        }
        sb.append("Agent commands: ").append(agent != null ? agent.availableCommands().size() : 0)
                .append(" (/agent: lists them)\n");
        sb.append("Preamble: ~").append(LlmClient.formatTokens(estimateTokens(buildSystemPrompt().length())))
                .append(" tokens, sent once per session ahead of the first prompt (/prompt shows it); ")
                .append(acpPreambleSent ? "sent" : "not sent yet").append('\n');
        sb.append("Tokens reported by the agent so far: ").append(LlmClient.formatTokens(sessionTotalTokens))
                .append('\n');
        sb.append("History and tools are managed by the agent: /compact and /tools do not apply here");
        return sb.toString();
    }

    /**
     * Explains that a panel command has no meaning while an agent is selected, pointing at the agent's own command of
     * the same name when it advertises one.
     */
    private String acpNotApplicable(String command, String what) {
        AcpAgentClient agent = acpClient;
        boolean offered = agent != null
                && agent.availableCommands().stream().anyMatch(c -> command.equals(c.name()));
        return "/" + command + " does not apply here: " + acpLabel() + " " + what + "."
               + (offered ? " Use /agent:" + command + "." : "");
    }

    String compactHistoryNow() {
        if (acpPreset != null) {
            return acpNotApplicable("compact", "manages its own conversation history");
        }
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
     * removed from the model history first so the retry starts from a clean turn; an ACP agent keeps its own history,
     * so the question is simply sent again.
     */
    boolean retryLastQuestion() {
        if ((client == null && acpPreset == null) || thinking.get()) {
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
        if (acpPreset == null && messages != null) {
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
        if (acpPreset != null && thinking.get()) {
            // an abandoned ACP turn would keep writing into the list we are about to clear
            interruptBusyOperation();
        }
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
        // the next prompt opens a fresh agent session (context reset), keeping the process
        acpSessionId = null;
        acpPreambleSent = false;
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

    void selectProviderForTesting(String providerId) {
        applyProviderChoice(new AiProviderSwitchPopup.ProviderChoice(providerId, "", "", false));
    }

    boolean isAcpProviderForTesting() {
        return acpPreset != null;
    }

    void setAcpHeaderForTesting(AcpHeaderStrip strip) {
        this.acpHeader = strip;
    }

    void setAcpClientFactoryForTesting(AcpClientFactory factory) {
        this.acpClientFactory = factory;
    }

    void setMcpUrlSupplierForTestingOrRuntime(Callable<String> supplier) {
        this.mcpUrlSupplier = supplier;
    }

    String acpSessionIdForTesting() {
        return acpSessionId;
    }

    boolean isPermissionPopupVisibleForTesting() {
        return permissionPopup.isVisible();
    }

    private AcpAgentClient spawnAcpAgent(AiProviderSelector.AcpPreset preset, Path cwd) throws IOException {
        if (!AiProviderSelector.isOnPath(preset.executable())) {
            throw new IOException(preset.installHint());
        }
        return AcpAgentClient.spawn(preset.command(), cwd, line -> log(LogLevel.ERROR, "ACP", line));
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

    private String describeWriteMode() {
        return switch (writeMode) {
            case AUTO -> "auto (the model may write files without asking, when it passes confirm=false)";
            case LIVE -> "live (the edit is replayed in the Source editor: Enter continues, F4 lets you edit, F8 asks"
                         + " the AI about it, Esc stops; then Ctrl+S saves or Esc discards)";
            default -> "confirm (every file write is confirmed in the TUI; /write auto skips the dialog,"
                       + " /write live replays the edit in the Source editor)";
        };
    }

    String describeWriteModeForTesting() {
        return describeWriteMode();
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
            if (acpPreset != null) {
                return acpLabel();
            }
            return client != null && client.model() != null ? client.model() : "unknown";
        }

        @Override
        public List<String> availableModels() {
            if (acpPreset != null) {
                return List.of();
            }
            return client != null ? client.listModels() : List.of();
        }

        @Override
        public boolean switchModel(String model) {
            if (acpPreset != null) {
                throw new IllegalStateException(
                        "The model is configured in the agent (" + acpLabel() + "), not in the TUI.");
            }
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
            if (acpPreset != null) {
                throw new IllegalStateException(
                        "The tool set only applies to LLM providers; " + acpLabel()
                                                + " reaches the camel-tui tools through the MCP server directly.");
            }
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
        public String describeWriteMode() {
            return AiPanel.this.describeWriteMode();
        }

        @Override
        public boolean switchWriteMode(String mode) {
            String normalized = mode == null ? "" : mode.trim().toLowerCase(Locale.ROOT);
            McpFacade.WriteMode selected = switch (normalized) {
                case "confirm" -> McpFacade.WriteMode.CONFIRM;
                case "auto" -> McpFacade.WriteMode.AUTO;
                case "live" -> McpFacade.WriteMode.LIVE;
                default -> null;
            };
            if (selected == null) {
                return false;
            }
            writeMode = selected;
            if (mcpFacade != null) {
                mcpFacade.setWriteMode(writeMode);
            }
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
            if (acpPreset != null) {
                return "Sent to " + acpLabel() + " ahead of the first prompt of each session:\n\n" + buildSystemPrompt();
            }
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
