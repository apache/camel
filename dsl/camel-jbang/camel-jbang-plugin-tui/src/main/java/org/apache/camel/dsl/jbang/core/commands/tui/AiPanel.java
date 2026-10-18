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
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.block.Title;
import dev.tamboui.widgets.paragraph.Paragraph;
import dev.tamboui.widgets.spinner.Spinner;
import dev.tamboui.widgets.spinner.SpinnerState;
import dev.tamboui.widgets.spinner.SpinnerStyle;
import org.apache.camel.dsl.jbang.core.commands.LlmClient;
import org.apache.camel.dsl.jbang.core.commands.ai.AnswerChecks;
import org.apache.camel.dsl.jbang.core.commands.ai.AppFeatures;
import org.apache.camel.dsl.jbang.core.commands.ai.ToolConfirms;
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
     * With a local endpoint the history is left untouched until the prompt exceeds a budget. A local server (Ollama)
     * keeps the KV cache of the previous request, so a request that merely extends the conversation only pays for its
     * new tokens, whereas rewriting an earlier message forces the whole prompt to be processed again: measured at 40
     * seconds for a 21k-token prompt with a 35B MoE model on Apple silicon, against half a second for a cached step.
     * Compacting saves counted tokens, which is what a hosted API bills for, but costs time locally, so it happens
     * rarely and then thoroughly (see {@link #compactLocalHistory}). The budget is half the context window Ollama
     * serves, capped at {@link #LOCAL_HISTORY_MAX_WINDOW_TOKENS}, and is compared with the prompt size Ollama measured
     * for the last request; this constant is the fallback when the window is unknown (another local server) and the
     * decision must rest on the character estimate.
     */
    static final int LOCAL_HISTORY_BUDGET_TOKENS = 16_000;
    /**
     * The panel budgets its history as if the window were at most this large, even when Ollama serves a larger one:
     * every token of history is prefill time again after a cache loss (idle unload, another client, a restart).
     */
    static final int LOCAL_HISTORY_MAX_WINDOW_TOKENS = 65_536;
    /** Prefill speed assumed for the compaction notice until a cold request has been measured. */
    static final double ASSUMED_COLD_PREFILL_TOKENS_PER_SECOND = 600;
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

    /** The question being typed, its history (Up, Ctrl+R) and TAB completion. */
    private final AiInputLine inputLine = new AiInputLine();
    private volatile long lastInputMs;
    // Models offered by TAB after "/model ". Fetched once in the background (a provider round trip) on the first TAB
    // and reset whenever the client or provider changes.
    private volatile List<String> modelCompletionCache;
    private final AtomicBoolean modelCompletionFetch = new AtomicBoolean();

    // Conversation display. CopyOnWriteArrayList because entries are appended from the agent thread and the
    // CLI-command-completion callback while the render thread iterates the list concurrently.
    private final List<ConversationEntry> conversation = new CopyOnWriteArrayList<>();
    private int scrollOffset;
    // the rendered lines of the conversation in the last frame, to keep a scrolled-up view where it is
    private int lastTotalLines;

    // LLM state
    private volatile LlmClient client;
    private List<LlmClient.Message> messages;
    private List<LlmClient.ToolDef> tools;
    private final AtomicBoolean thinking = new AtomicBoolean();
    private volatile Thread agentThread;
    // set while the user asks about a paused live edit (F8 at the pause): input is accepted although a tool call is
    // still running, and Enter hands the question to the waiting call (true) or sends it as a normal question (false)
    private volatile Predicate<String> editQuestionHandler;
    // asking about a paused live edit: the panel stays compact, so the edit in the editor stays in view, until it
    // closes or the user sizes it
    private boolean editCompact;
    // what became of a parked live edit; told to the model with the next question
    private volatile String pendingNote;
    private String initError;
    private long thinkingStartTime;
    private volatile String thinkingVerb;
    private volatile int sessionTotalTokens;
    /** Prompt size Ollama reported for the most recent request, 0 when unknown or after a compaction. */
    private volatile int lastMeasuredPromptTokens;
    /** Context window the current local provider serves, as the client resolved it; 0 when not Ollama. */
    private volatile int knownContextWindow;
    /** Prefill speed measured on requests that hit no cache, for the compaction notice. */
    private volatile double coldPrefillTokensPerSecond;
    // Slash commands
    private final AiSlashCommandRegistry slashCommands = AiSlashCommandRegistry.defaults();
    private final ProjectOverviewAssist projectOverview = new ProjectOverviewAssist();
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
    private final AiCliCommandExecutor cliCommandExecutor = new AiCliCommandExecutor();
    private volatile CompletableFuture<AiCliCommandExecutor.Result> activeCliCommand;
    private Runnable exitCallback;

    private volatile List<JsonObject> exampleCatalog;

    // Provider switch popup
    private final AiProviderSwitchPopup providerSwitchPopup = new AiProviderSwitchPopup();
    private final AiCopyPopup copyPopup = new AiCopyPopup();
    private ClipboardWriter clipboard = TuiHelper::copyToClipboard;
    private final AiProviderSelector providerSelector = new AiProviderSelector();
    private AiProviderSwitchPopup.ProviderChoice sessionProviderChoice;
    private List<AiProviderSwitchPopup.ProviderChoice> providerChoicesForTesting;
    private boolean testingClientInjected;

    private static final String TUI_TOOL_PREFIX = "mcp__camel-tui__";
    private static final Set<String> FILE_OR_SHELL_KINDS = Set.of("edit", "delete", "move", "execute", "fetch");
    private static final String AGENT_COMMAND_PREFIX = "/agent:";
    private final AcpHeaderStrip acpHeader = new AcpHeaderStrip();
    private final AcpSession acpSession = new AcpSession(new AcpSession.Host() {
        @Override
        public void say(String text) {
            conversation.add(new ConversationEntry(AiRole.SYSTEM, text));
        }

        @Override
        public void log(LogLevel level, String message, String detail) {
            AiPanel.this.log(level, message, detail);
        }

        @Override
        public AcpAgentClient.PermissionHandler permissionHandler() {
            return new AcpPanelPermissionHandler();
        }

        @Override
        public Path workingDir() {
            return acpWorkingDir();
        }
    });
    private final AcpPermissionPopup permissionPopup = new AcpPermissionPopup();
    private volatile CompletableFuture<String> pendingPermission;

    /** The selected integration as the tool groups see it; replaced in tests. */
    interface AppStatusSource {
        /** The selected pid, null when none. */
        String selectedPid();

        /** How often it reloaded its routes. */
        int reloadCount();

        /** What it has, read from its status. */
        AppFeatures features();
    }

    // MCP facade for TUI tool access from the AI panel
    private McpFacade mcpFacade;
    // /write: confirm (dialog per write), auto (the model may skip it with confirm=false) or live (the edit is replayed
    // in the source editor and the user saves or discards it)
    private McpFacade.WriteMode writeMode = McpFacade.WriteMode.CONFIRM;
    // Activity log for AI Log popup
    private final List<LogEntry> activityLog = new ArrayList<>();
    private static final int MOUSE_SCROLL_LINES = 3;
    private Rect lastArea;

    /** The system prompt and the tools sent to the model, and the tool registry that runs them. */
    private final AiToolSet toolSet = new AiToolSet();
    /** The usage of the session: tokens, latency and time per question, for /usage and the Ctrl+U view. */
    private final AiUsageStats usage = new AiUsageStats();
    /** Sequence number of the current question; tags the usage entries recorded while answering it. */
    private volatile int questionCounter;
    /** The question the current turn answers, for grouping its requests on the Ollama tab. */
    private volatile String currentQuestion;
    private boolean statsView;
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
            int totalTokens, int requests, boolean limitReached, int contextPercent, int corrections, String note) {
        ConversationEntry(AiRole role, String text) {
            this(role, text, -1, 0, 0, 0, 0, 0, false, 0, 0, null);
        }

        ConversationEntry(AiRole role, String text, long elapsedMs, long aiMs, long toolMs, int toolCalls,
                          int totalTokens) {
            this(role, text, elapsedMs, aiMs, toolMs, toolCalls, totalTokens, 0, false, 0, 0, null);
        }

        ConversationEntry(AiRole role, String text, long elapsedMs, long aiMs, long toolMs, int toolCalls,
                          int totalTokens, int requests, boolean limitReached, int contextPercent) {
            this(role, text, elapsedMs, aiMs, toolMs, toolCalls, totalTokens, requests, limitReached, contextPercent, 0,
                 null);
        }

        /** "5.2s" or, when tools were called, "5.2s, ai 4.1s, tools 1.1s/3". */
        String timing() {
            String t = formatSeconds(elapsedMs);
            if (toolCalls > 0) {
                t += ", ai " + formatSeconds(aiMs) + ", tools " + formatSeconds(toolMs) + "/" + toolCalls;
            }
            return t;
        }

        /**
         * The line under an answer: what the question cost in plain words. "61.1s · 25 tool calls, limit reached · 26
         * requests · 231.3k tokens · ctx 18%". The requests are the model round trips, which is what a local model pays
         * for and what the Ollama tab counts per question; the tool time is named only when it mattered.
         */
        String byline() {
            StringBuilder sb = new StringBuilder(formatSeconds(elapsedMs));
            if (toolCalls > 0) {
                sb.append(" · ").append(toolCalls).append(toolCalls == 1 ? " tool call" : " tool calls");
                if (limitReached) {
                    sb.append(", limit reached");
                }
                if (toolMs >= 1000) {
                    sb.append(" (").append(formatSeconds(toolMs)).append(" in tools)");
                }
            } else if (limitReached) {
                sb.append(" · limit reached");
            }
            if (requests > 1) {
                sb.append(" · ").append(requests).append(" requests");
            }
            if (corrections > 0) {
                sb.append(" · ").append(corrections).append(corrections == 1 ? " correction" : " corrections");
            }
            if (totalTokens > 0) {
                sb.append(" · ").append(LlmClient.formatTokens(totalTokens)).append(" tokens");
            }
            if (contextPercent > 0) {
                sb.append(" · ctx ").append(contextPercent).append('%');
            }
            return sb.toString();
        }
    }

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

    // Detached launches (/run, /infra run) go through the same LaunchManager the F2 Actions menu uses, so they are
    // spawned as tracked background processes instead of blocking in-process. It comes from the facade, the one place
    // the TUI wires it; null in tests that construct the panel directly, where the slash context reports an error.
    private LaunchManager launchManager() {
        return mcpFacade != null ? mcpFacade.getLaunchManager() : null;
    }

    void setMcpFacade(McpFacade mcpFacade) {
        this.mcpFacade = mcpFacade;
        if (mcpFacade != null) {
            mcpFacade.setWriteMode(writeMode);
            toolSet.setRegistry(new TuiToolRegistry(mcpFacade));
        }
    }

    void setMcpInfo(boolean active, int port) {
        toolSet.setMcpInfo(active, port);
    }

    void setOtelSpans(AtomicReference<List<SpanEntry>> otelSpans) {
        usage.setOtelSpans(otelSpans);
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

    /** The height to render at: compact while asking about a paused live edit, else the panel's own height. */
    int panelHeight(int contentHeight) {
        int h = anim.panelHeight();
        return editCompact ? Math.min(h, editQuestionHeight(contentHeight)) : h;
    }

    /** A quarter of the content, at least 10 rows: the question, a short answer and the hints. */
    static int editQuestionHeight(int contentHeight) {
        return Math.min(contentHeight, Math.max(10, contentHeight / 4));
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
        editCompact = false;
        anim.cycleHeight(contentHeight);
    }

    void setPanelHeight(int height) {
        editCompact = false;
        anim.setPanelHeight(height);
    }

    void open() {
        visible = true;
        inputLine.reloadPromptHistory();
        if (client == null) {
            initClient();
        }
        if (!acpSession.isSelected() && client != null) {
            // AI Overview: auto explains the project when its summary is missing or out of date (CAMEL-25143); reading
            // the project is kept off the UI thread
            LlmClient c = client;
            Path dir = projectDirectory();
            // it waits until the panel is idle, so a question asked right away is answered first
            Thread t = new Thread(
                    () -> projectOverview.autoExplain(dir, c, mcpFacade, this::addOverviewEntry,
                            () -> idleFor(OVERVIEW_IDLE_MS, MonotonicClock.millis())),
                    "tui-ai-overview-check");
            t.setDaemon(true);
            t.start();
        }
    }

    /** How long the panel is left alone (nothing typed, no question running) before the summary starts. */
    static final long OVERVIEW_IDLE_MS = 3000;

    /**
     * Whether the panel has been idle for a while: no question or command running, nothing typed. Now is a
     * {@link MonotonicClock} time.
     */
    boolean idleFor(long ms, long now) {
        return !thinking.get() && activeCliCommand == null && inputLine.isEmpty() && now - lastInputMs >= ms;
    }

    /** The project the AI overview is about: the selected integration's sources, else the folder the TUI runs in. */
    private Path projectDirectory() {
        Path dir = mcpFacade != null ? mcpFacade.getSelectedSourceDirectory() : null;
        return dir != null ? dir : Path.of("").toAbsolutePath();
    }

    private void addOverviewEntry(AiRole role, String text) {
        conversation.add(new ConversationEntry(role, text));
    }

    /**
     * Explains the project, as {@code /overview} does, and opens the panel so the user sees it start and the answer
     * arrive: the Diagram tab's ai setting pressed when the project has no summary yet.
     */
    void startProjectOverview() {
        if (client == null && !acpSession.isSelected()) {
            initClient();
        }
        String message = projectOverview("");
        if (message != null && !message.isBlank()) {
            addOverviewEntry(AiRole.SYSTEM, message);
        }
        if (!visible) {
            open();
        }
    }

    String projectOverview(String arguments) {
        if (acpSession.isSelected()) {
            return "Ask the agent to explain the project: it has the camel_project_overview and"
                   + " camel_save_project_summary tools.";
        }
        if ("show".equalsIgnoreCase(arguments == null ? "" : arguments.strip()) && ctx != null
                && ctx.openMarkdownAtCallback != null) {
            // opened like a README, in the markdown viewer
            String error = IntegrationSummaryDoc.open(ctx, projectDirectory(), null);
            return error != null ? error : "";
        }
        return projectOverview.command(arguments, projectDirectory(), client, mcpFacade, this::addOverviewEntry);
    }

    void close() {
        visible = false;
        editQuestionHandler = null;
        editCompact = false;
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
        inputLine.replace(prefill);
        editQuestionHandler = handler;
        editCompact = true;
    }

    /**
     * Opens the panel with a question in its input, for the user to send with Enter or change first: the fix of a
     * problem of the Source editor (Shift+F8). Nothing is sent until the user says so.
     */
    void openWithQuestion(String question) {
        if (!visible) {
            open();
        }
        statsView = false;
        scrollOffset = 0;
        inputLine.replace(question);
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
        acpSession.close();
    }

    private void initClient() {
        modelCompletionCache = null;
        String provider = sessionProviderChoice != null
                ? sessionProviderChoice.provider() : TuiSettings.load().getAiProvider();
        if (AiProviderSelector.isAcp(provider)) {
            try {
                TuiSettings settings = TuiSettings.load();
                acpSession.setPreset(providerSelector.acpPreset(provider, settings));
                initError = null;
            } catch (IllegalArgumentException e) {
                acpSession.setPreset(null);
                initError = e.getMessage();
            }
            client = null;
            return;
        }
        acpSession.setPreset(null);
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
            if (toolSet.mode() == null) {
                toolSet.setMode(normalizeToolMode(TuiSettings.load().getAiTools()));
            }
            client = created;
            initError = AiProviderSelector.detectEndpoint(client);
            if (initError != null) {
                client = null;
                return;
            }
            // know the Ollama window before the first question instead of resolving it in front of the first request
            client.resolveOllamaContextWindowInBackground();
            messages = new ArrayList<>();
            tools = toolSet.definitions(client);
        } catch (Exception e) {
            initError = "Failed to initialize AI: " + e.getMessage();
            client = null;
        }
    }

    private void applyProviderChoice(AiProviderSwitchPopup.ProviderChoice choice) {
        stopAgentThread();
        acpSession.close();
        sessionProviderChoice = choice;
        if (AiProviderSelector.isAcp(choice.provider())) {
            client = null;
            initError = null;
            try {
                TuiSettings settings = TuiSettings.load();
                acpSession.setPreset(providerSelector.acpPreset(choice.provider(), settings));
            } catch (IllegalArgumentException e) {
                acpSession.setPreset(null);
                sessionProviderChoice = null;
                conversation.add(new ConversationEntry(AiRole.ERROR, e.getMessage()));
                return;
            }
            conversation.add(new ConversationEntry(
                    AiRole.SYSTEM,
                    "Selected " + acpSession.preset().label() + ". The agent starts with your first question."));
            return;
        }
        acpSession.setPreset(null);
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
        if (!lastArea.contains(me.x(), me.y())) {
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
        lastInputMs = MonotonicClock.millis();
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
        if (inputLine.isSearching()) {
            // a key the search does not take ends it, and is then handled as usual
            return inputLine.handleSearchKey(ke) || handleKeyEvent(ke);
        }
        if (ke.isKey(KeyCode.F8)) {
            close();
            return true;
        }
        if (isFunctionKey(ke)) {
            return false;
        }
        if (!statsView && !thinking.get() && ke.hasCtrl() && ke.isCharIgnoreCase('r')
                && inputLine.canSearchHistory()) {
            inputLine.enterSearch();
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
            inputLine.insert("\n");
            return true;
        }
        if (ke.isKey(KeyCode.PAGE_UP)) {
            if (statsView) {
                // the usage view scrolls its model table from the top
                usage.scroll(-5);
            } else {
                scrollOffset += 5;
            }
            return true;
        }
        if (ke.isKey(KeyCode.PAGE_DOWN)) {
            if (statsView) {
                usage.scroll(5);
            } else {
                scrollOffset = Math.max(0, scrollOffset - 5);
            }
            return true;
        }
        if (!statsView && ke.isKey(KeyCode.UP) && (inputLine.moveCursorLine(-1) || inputLine.historyPrevious())) {
            return true;
        }
        if (!statsView && ke.isKey(KeyCode.DOWN) && (inputLine.moveCursorLine(1) || inputLine.historyNext())) {
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
            if (!inputLine.isEmpty()) {
                submitInput();
            }
            return true;
        }
        if (ke.isKey(KeyCode.TAB)) {
            inputLine.complete(ke.hasShift(), this::completionFor);
            return true;
        }
        if (inputLine.handleEditKey(ke)) {
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

    boolean isHistorySearchActive() {
        return inputLine.isSearching();
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
        // pasted line breaks stay: a multi-line question is sent as typed
        inputLine.paste(normalized);
    }

    String searchTermForTesting() {
        return inputLine.searchTerm();
    }

    /** What TAB completes in the text: the argument of /model, /tools or /write, else the slash command name. */
    private AiInputLine.Completion completionFor(String text) {
        AiInputLine.Completion argument = argumentCompletion(text);
        if (argument != null) {
            return argument;
        }
        List<String> names = slashCommands.completionsFor(text, acpSession.commandDescriptors()).stream()
                .map(AiSlashCommandRegistry.Descriptor::name)
                .toList();
        return new AiInputLine.Completion(1, text.length() > 1 ? text.substring(1) : "", names);
    }

    /**
     * Returns the argument completion for {@code /model <prefix>} or {@code /tools <prefix>} (aliases included), or
     * {@code null} when the buffer is not at the first argument of one of those commands. The model list comes from the
     * provider, so the first TAB kicks off a background fetch and returns nothing; TAB again once it is loaded.
     */
    private AiInputLine.Completion argumentCompletion(String text) {
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
        return new AiInputLine.Completion(start, token, matches);
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

    private void submitInput() {
        String input = inputLine.text().trim();
        inputLine.remember(input);
        inputLine.clear();
        scrollOffset = 0;
        if (input.startsWith("/")) {
            executeSlashCommand(input);
            return;
        }
        Predicate<String> handler = editQuestionHandler;
        if (handler != null) {
            editQuestionHandler = null;
            if (handler.test(input)) {
                // the waiting camel_write_file call returns with the question and the model answers in this turn
                conversation.add(new ConversationEntry(AiRole.USER, input));
                questionCounter++;
                currentQuestion = input;
                noteQuestionStarted(input);
                log(LogLevel.QUESTION, "Question about the edit", input);
                return;
            }
        }
        submitQuestion(input);
    }

    private void executeSlashCommand(String input) {
        if (acpSession.isSelected() && input.startsWith(AGENT_COMMAND_PREFIX)) {
            String rest = input.substring(AGENT_COMMAND_PREFIX.length()).strip();
            if (rest.isEmpty()) {
                conversation.add(new ConversationEntry(AiRole.SYSTEM, acpSession.commandListing()));
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

        if (parsed.isEmpty() && acpSession.isSelected()) {
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
    }

    private void interruptBusyOperation() {
        if (thinking.get() && projectOverview.stop()) {
            // the question waits behind the project summary: the first Esc stops the summary, so it goes first
            conversation.add(new ConversationEntry(
                    AiRole.SYSTEM,
                    "(project summary stopped, your question goes first; /overview writes it later)"));
            return;
        }
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
            acpSession.cancelTurn();
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
        if (acpSession.isSelected()) {
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
        if (projectOverview.isRunning() && client.apiType() == LlmClient.ApiType.ollama) {
            // Ollama answers one request at a time: say why the answer takes long, and how to go first
            conversation.add(new ConversationEntry(
                    AiRole.SYSTEM,
                    "The project summary (/overview) is being written, and Ollama answers one request at a time:"
                                   + " this question waits for it. Esc stops the summary."));
        }
        questionCounter++;
        currentQuestion = question;
        noteQuestionStarted(question);
        log(LogLevel.QUESTION, "Question", question);
        thinkingVerb = THINKING_VERBS.get(ThreadLocalRandom.current().nextInt(THINKING_VERBS.size()));
        thinkingStartTime = MonotonicClock.millis();
        thinking.set(true);

        // re-read the tool mode so a change made in F2 -> Settings applies to the next question, and rebuild the
        // tools in case mcpFacade was wired after init
        if (!testingClientInjected) {
            toolSet.setMode(normalizeToolMode(TuiSettings.load().getAiTools()));
        }
        toolSet.refreshToolGroups(mcpFacade);
        tools = toolSet.definitions(client);
        String systemPrompt = toolSet.systemPrompt(client);

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
                    noteQuestionFinished();
                }
            }
        }, "tui-ai-agent");
        agentThread.setDaemon(true);
        agentThread.start();
    }

    /**
     * Tells the Ollama tab which question the panel is working on, so it lists it as in progress from the moment it was
     * asked until the turn ends: answered, failed or cancelled. Only for Ollama; the tab is about that server.
     */
    private void noteQuestionStarted(String question) {
        if (ctx != null && ctx.ollamaMonitor != null && client != null
                && client.apiType() == LlmClient.ApiType.ollama) {
            ctx.ollamaMonitor.questionStarted(questionCounter, question);
        }
    }

    private void noteQuestionFinished() {
        if (ctx != null && ctx.ollamaMonitor != null) {
            ctx.ollamaMonitor.questionFinished();
        }
    }

    private void runAgentLoop(String systemPrompt, String question) throws InterruptedException {
        if (messages == null) {
            messages = new ArrayList<>();
        }
        String note = pendingNote;
        pendingNote = null;
        messages.add(LlmClient.Message
                .user(AiToolSet.contextualize(mcpFacade, note == null ? question : "[" + note + "]\n" + question)));

        LlmClient.TokenUsage totalUsage = LlmClient.TokenUsage.EMPTY;
        Map<String, Integer> callCounts = new HashMap<>();
        List<String> recentCalls = new ArrayList<>();
        long turnAiMs = 0;
        long turnToolMs = 0;
        int turnToolCalls = 0;
        int turnRequests = 0;
        int turnCorrections = 0;
        for (int i = 0; i < MAX_ITERATIONS; i++) {
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }

            long callStart = MonotonicClock.millis();
            drainClientOutput();
            LlmClient.ChatResponse response = client.chatWithTools(systemPrompt, messages, tools);
            long callLatency = MonotonicClock.millis() - callStart;
            turnAiMs += callLatency;
            if (response == null) {
                String err = "No response from LLM";
                conversation.add(new ConversationEntry(AiRole.ERROR, err));
                log(LogLevel.ERROR, "Error", err);
                return;
            }
            turnRequests++;
            totalUsage = totalUsage.add(response.usage());
            recordUsage(response, callLatency, null);

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
                    long toolStart = MonotonicClock.millis();
                    if (repeats > MAX_IDENTICAL_TOOL_CALLS) {
                        result = "You have already called " + toolCall.name() + " with these exact arguments "
                                 + (repeats - 1) + " times in this turn and the result will not change. Stop calling "
                                 + "tools now: tell the user what you found, what failed, and what they could try instead.";
                    } else {
                        result = executeTuiTool(toolCall.name(), toolCall.arguments());
                    }
                    long toolElapsed = MonotonicClock.millis() - toolStart;
                    TuiToolRegistry toolRegistry = toolSet.registry();
                    if (toolRegistry != null) {
                        // waiting for the user to confirm a file write is not tool time
                        toolElapsed = Math.max(0, toolElapsed - toolRegistry.consumeConfirmWaitMs());
                    }
                    turnToolMs += toolElapsed;
                    turnToolCalls++;
                    usage.addToolCall(toolElapsed);
                    log(LogLevel.RESULT, toolCall.name() + " (" + formatToolTime(toolElapsed) + ")", result);
                    recentCalls.add(toolCall.name() + " " + summarize(arguments, 80) + " -> " + summarize(result, 120));
                    results.add(new LlmClient.ToolResult(toolCall.id(), truncateToolResult(result)));
                }
                messages.add(LlmClient.Message.toolResults(results));
            } else {
                // what the model says is checked like what it writes: an invalid simple expression in the answer
                // gets one correction turn, since the answer is what the user copies (CAMEL-24805)
                List<AnswerChecks.Problem> problems = AnswerChecks.checkSimple(response.text());
                if (!problems.isEmpty() && turnCorrections == 0 && i < MAX_ITERATIONS - 1) {
                    turnCorrections++;
                    String request = correctionRequest(problems);
                    messages.add(LlmClient.Message.assistantWithToolCalls(response.text(), List.of()));
                    messages.add(LlmClient.Message.user(request));
                    log(LogLevel.RESULT, "Answer check: invalid simple expression, asking for a fix", request);
                    continue;
                }
                completeTurn(response.text(), false, totalUsage, turnAiMs, turnToolMs, turnToolCalls, turnRequests,
                        turnCorrections, problems, messages);
                return;
            }
        }

        // out of round trips: ask the model once more to answer with what it has. The tool definitions stay in the
        // request so the prompt prefix is unchanged and a local server serves it from its cache; dropping them
        // changed the prefix and cost a full re-prefill (13 s for 8k tokens measured). A tool call in the reply is
        // ignored.
        messages.add(LlmClient.Message.user(
                "You have used all " + MAX_ITERATIONS + " tool calls available for this question and cannot call any"
                                            + " more; do not call a tool. Answer now: summarize what you did, what"
                                            + " you found, what failed, and what the user could try next."));
        long wrapUpStart = MonotonicClock.millis();
        drainClientOutput();
        LlmClient.ChatResponse wrapUp = client.chatWithTools(systemPrompt, messages, tools);
        long wrapUpLatency = MonotonicClock.millis() - wrapUpStart;
        turnAiMs += wrapUpLatency;
        if (wrapUp != null) {
            turnRequests++;
            totalUsage = totalUsage.add(wrapUp.usage());
            recordUsage(wrapUp, wrapUpLatency, "limit");
        }
        if (wrapUp == null || wrapUp.text() == null || wrapUp.text().isBlank()) {
            // the model called a tool regardless: once more without any tool to call, at the price of the prefix
            wrapUpStart = MonotonicClock.millis();
            drainClientOutput();
            wrapUp = client.chatWithTools(systemPrompt, messages, List.of());
            wrapUpLatency = MonotonicClock.millis() - wrapUpStart;
            turnAiMs += wrapUpLatency;
            if (wrapUp != null) {
                turnRequests++;
                totalUsage = totalUsage.add(wrapUp.usage());
                recordUsage(wrapUp, wrapUpLatency, "limit");
            }
        }
        if (wrapUp != null && wrapUp.text() != null && !wrapUp.text().isBlank()) {
            // no round trip left for a correction: the answer is shown with what the check found
            completeTurn(wrapUp.text(), true, totalUsage, turnAiMs, turnToolMs, turnToolCalls, turnRequests,
                    turnCorrections, AnswerChecks.checkSimple(wrapUp.text()), messages);
            return;
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
            int turnToolCalls, int turnRequests, int corrections, List<AnswerChecks.Problem> problems,
            List<LlmClient.Message> messages) {
        sessionTotalTokens += totalUsage.totalTokens();
        if (text != null && !text.isBlank()) {
            long elapsed = MonotonicClock.millis() - thinkingStartTime;
            String note = problems.isEmpty() ? null : answerNote(problems);
            ConversationEntry entry = new ConversationEntry(
                    AiRole.ASSISTANT, text, elapsed, turnAiMs, turnToolMs, turnToolCalls,
                    totalUsage.totalTokens(), turnRequests, wrapUp, contextFillPercent(), corrections, note);
            conversation.add(entry);
            usage.addTurn(turnAiMs, turnToolMs, turnToolCalls);
            String label = wrapUp ? "Response after reaching the tool call limit (" : "Response (";
            log(LogLevel.RESPONSE, label + entry.byline() + describeCacheSignal(totalUsage) + ")",
                    note == null ? text : text + "\n\n" + note);
        } else {
            String err = "Empty response from LLM.";
            conversation.add(new ConversationEntry(AiRole.ERROR, err));
            log(LogLevel.ERROR, "Error", err);
        }
        messages.add(LlmClient.Message.assistantWithToolCalls(text, List.of()));
        compactHistoryAfterTurn();
    }

    /**
     * Compacts the history after a turn. Hosted endpoints compact a little every turn (tokens cost money, rewriting is
     * free). A local endpoint waits until the measured prompt passes the budget, then compacts hard once and tells the
     * user what it cost; see {@link #LOCAL_HISTORY_BUDGET_TOKENS} for why. {@code /compact} bypasses the wait.
     */
    private void compactHistoryAfterTurn() {
        boolean local = client != null && client.isLocalEndpoint();
        int budget = compactionBudgetTokens(local, knownContextWindow);
        if (!shouldCompactAfterTurn(local, historyChars(messages), lastMeasuredPromptTokens, budget)) {
            return;
        }
        if (!local) {
            compactHistory(messages, MAX_HISTORY_TURNS, COMPACT_TOOL_RESULT_CHARS);
            return;
        }
        String notice = compactLocalHistoryWithNotice(budget, true);
        conversation.add(new ConversationEntry(AiRole.SYSTEM, notice));
        log(LogLevel.RESPONSE, "History compacted", notice);
    }

    /** Compacts the local history hard and returns the notice describing what it cost; resets the measurement. */
    private String compactLocalHistoryWithNotice(int budget, boolean automatic) {
        int before = lastMeasuredPromptTokens > 0 ? lastMeasuredPromptTokens : promptTokensEstimate();
        compactLocalHistory(messages, budget);
        int after = promptTokensEstimate();
        lastMeasuredPromptTokens = 0;
        return describeCompaction(automatic, before, after, true, coldPrefillTokensPerSecond);
    }

    static boolean shouldCompactAfterTurn(boolean localEndpoint, long historyChars) {
        return shouldCompactAfterTurn(localEndpoint, historyChars, 0, LOCAL_HISTORY_BUDGET_TOKENS);
    }

    /**
     * Hosted endpoints always compact. Local ones compact once the prompt Ollama measured for the last request passes
     * the budget, or, when nothing was measured, once the character estimate of the history does.
     */
    static boolean shouldCompactAfterTurn(
            boolean localEndpoint, long historyChars, int measuredPromptTokens, int budgetTokens) {
        if (!localEndpoint) {
            return true;
        }
        if (measuredPromptTokens > 0) {
            return measuredPromptTokens > budgetTokens;
        }
        return estimateTokens(historyChars) > budgetTokens;
    }

    /** Half of the window the local server serves, capped at {@link #LOCAL_HISTORY_MAX_WINDOW_TOKENS}. */
    static int compactionBudgetTokens(boolean localEndpoint, int windowTokens) {
        if (!localEndpoint || windowTokens <= 0) {
            return LOCAL_HISTORY_BUDGET_TOKENS;
        }
        return Math.min(windowTokens, LOCAL_HISTORY_MAX_WINDOW_TOKENS) / 2;
    }

    /**
     * Compacts as {@code /compact} does (older tool results cut, the previous turn included) and then drops the oldest
     * turns until the history is estimated at half the budget, so that with the static prefix the next prompt lands
     * around a quarter to a third of the window. Every compaction costs a full re-prefill locally, so one compaction
     * has to buy several quiet turns.
     */
    static void compactLocalHistory(List<LlmClient.Message> history, int budgetTokens) {
        compactHistory(history, MAX_HISTORY_TURNS, COMPACT_TOOL_RESULT_CHARS, false);
        int target = Math.max(1, budgetTokens / 2);
        while (countTurns(history) > 1 && estimateTokens(historyChars(history)) > target) {
            dropOldestTurn(history);
        }
    }

    /** Removes the first question with everything up to the next one. */
    static void dropOldestTurn(List<LlmClient.Message> history) {
        int firstUser = -1;
        for (int i = 0; i < history.size(); i++) {
            LlmClient.Message m = history.get(i);
            boolean question = "user".equals(m.role()) && m.toolCalls() == null && m.toolResults() == null;
            if (question) {
                if (firstUser >= 0) {
                    history.subList(0, i).clear();
                    return;
                }
                firstUser = i;
            }
        }
    }

    /**
     * The line shown when the history is compacted. Locally the next request prefills the whole prompt again, so the
     * user is told how long that takes at the speed measured on a cold request (or an assumed speed until one was).
     */
    static String describeCompaction(
            boolean automatic, int beforeTokens, int afterTokens, boolean localEndpoint,
            double coldPrefillTokensPerSecond) {
        StringBuilder sb = new StringBuilder();
        sb.append(automatic ? "History compacted automatically: ~" : "Compacting history: ~")
                .append(LlmClient.formatTokens(beforeTokens)).append(" -> ~")
                .append(LlmClient.formatTokens(afterTokens)).append(" tokens");
        if (!localEndpoint) {
            sb.append(", saving ~").append(LlmClient.formatTokens(Math.max(0, beforeTokens - afterTokens)))
                    .append(" tokens per request.");
            return sb.toString();
        }
        boolean assumed = coldPrefillTokensPerSecond <= 0;
        double rate = assumed ? ASSUMED_COLD_PREFILL_TOKENS_PER_SECOND : coldPrefillTokensPerSecond;
        double seconds = afterTokens / rate;
        sb.append(". The next reply prefills the whole prompt again (about ")
                .append(seconds < 10
                        ? String.format(Locale.ROOT, "%.0f", Math.max(1, seconds))
                        : String.format(Locale.ROOT, "%.0f", seconds))
                .append("s at ").append(String.format(Locale.ROOT, "%.0f", rate)).append(" tok/s")
                .append(assumed ? ", assumed" : ", measured").append(") because the local server's cache resets.");
        return sb.toString();
    }

    /** Estimate of the next prompt: static prefix plus history, in tokens. */
    private int promptTokensEstimate() {
        return estimateTokens(toolSet.systemPrompt(client).length()) + estimateTokens(toolSet.schemaChars(client))
               + estimateTokens(historyChars(messages));
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

    static String formatSeconds(long millis) {
        return String.format(Locale.ROOT, "%.1fs", millis / 1000.0);
    }

    /**
     * Tool calls are usually fast, so show milliseconds below one second and one decimal second above.
     */
    private static String formatToolTime(long millis) {
        return millis < 1000 ? millis + "ms" : formatSeconds(millis);
    }

    /**
     * The one correction turn a model gets when its answer has an invalid simple expression: what is wrong, the rule it
     * broke, and to send the whole answer again. Worded so the fake clients of the tests and the log recognise it.
     */
    static String correctionRequest(List<AnswerChecks.Problem> problems) {
        return AnswerChecks.correctionRequest(problems);
    }

    /** The line under an answer whose simple expressions the model did not fix, so the user is not misled. */
    static String answerNote(List<AnswerChecks.Problem> problems) {
        StringBuilder sb = new StringBuilder("**Simple check:** ");
        if (problems.size() == 1) {
            sb.append("`").append(problems.get(0).expression()).append("` is invalid: ").append(problems.get(0).error());
        } else {
            sb.append(problems.size()).append(" expressions are invalid:");
            for (AnswerChecks.Problem p : problems) {
                sb.append("\n- `").append(p.expression()).append("`: ").append(p.error());
            }
        }
        return sb.toString();
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
        thinkingStartTime = MonotonicClock.millis();
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
        AcpAgentClient agent = acpSession.client();
        boolean dead = agent == null || !agent.isAlive()
                || (acp != null && (acp.code() == AcpAgentClient.CONNECTION || acp.code() == AcpAgentClient.TIMEOUT));
        if (dead) {
            acpSession.close();
        }
    }

    private void runAcpTurn(String question) throws IOException {
        AcpAgentClient agent = acpSession.ensureSession();
        boolean agentCommand = question.startsWith("/");
        String text = acpSession.preambleSent() || agentCommand
                ? question : toolSet.acpPreamble(client) + "\n\n" + question;
        AcpTurnListener listener = new AcpTurnListener();
        String stopReason = agent.prompt(acpSession.sessionId(), text, listener);
        if (!agentCommand) {
            acpSession.markPreambleSent();
        }
        listener.finish(stopReason);
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
        private volatile long contextSize;

        @Override
        public synchronized void onTextChunk(String chunk) {
            text.append(chunk);
            liveIndex = replaceOrAppend(liveIndex, new ConversationEntry(AiRole.ASSISTANT, text.toString()));
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
            contextSize = size;
        }

        synchronized void finish(String stopReason) {
            long elapsed = MonotonicClock.millis() - thinkingStartTime;
            // the agent reports what its context holds now, not what the turn spent: the turn is the growth, and a
            // context that shrank because the agent compacted spends nothing
            long previous = acpSession.recordContext(usedTokens, contextSize);
            int tokens = (int) Math.min(Integer.MAX_VALUE, Math.max(0, usedTokens - previous));
            if (liveIndex >= 0) {
                // the agent runs its own tools, so ACP reports no ai/tool split to fill in
                replaceOrAppend(liveIndex,
                        new ConversationEntry(AiRole.ASSISTANT, text.toString(), elapsed, 0, 0, 0, tokens));
            }
            if (usedTokens > 0) {
                AiProviderSelector.AcpPreset preset = acpSession.preset();
                sessionTotalTokens += tokens;
                usage.add(new AiUsageEntry(
                        acpSession.label(), preset != null ? preset.id() : "acp", 0, 0, tokens, elapsed,
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
     * The TUI's own tools are approved silently (allow-always preferred): Camel TUI is a developer tool, and the TUI
     * asks the user itself before the few calls that need it (a file write in its write mode, and
     * {@link ToolConfirms}). The agent's own tools (its shell commands, file changes and fetches) are put in front of
     * the user. While a tape drives the monitor nothing asks at all (CAMEL-25444). Runs on the ACP request thread and
     * blocks until the user answers or the turn is cancelled.
     */
    private final class AcpPanelPermissionHandler implements AcpAgentClient.PermissionHandler {
        @Override
        public String decide(JsonObject toolCall, List<JsonObject> options) {
            String name = String.valueOf(toolCall.getStringOrDefault("name", ""));
            String title = String.valueOf(toolCall.getStringOrDefault("title", ""));
            String kind = String.valueOf(toolCall.getStringOrDefault("kind", ""));
            String tool = tuiToolName(name, title, kind);
            boolean tuiTool = tool != null && TuiToolRegistry.isTool(tool);
            if (tuiTool || ctx != null && ctx.tapeDriven.getAsBoolean()) {
                String optionId = firstOptionOfKind(options, "allow_always");
                if (optionId == null) {
                    optionId = firstOptionOfKind(options, "allow_once");
                }
                if (optionId == null && !options.isEmpty()) {
                    optionId = options.get(0).getString("optionId");
                }
                log(LogLevel.TOOL, tuiTool
                        ? "Auto-approved TUI tool (the TUI asks itself when it must)"
                        : "Auto-approved while a tape drives the monitor", title);
                return optionId;
            }
            CompletableFuture<String> decision = new CompletableFuture<>();
            pendingPermission = decision;
            permissionPopup.open(toolCall, options, permissionHint(kind));
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
     * The camel-tui tool a permission request is about, or null when it is not one: the name the Claude adapter sends
     * ({@code mcp__camel-tui__tui_get_state}) or a title of the form {@code tui_get_state (camel-tui MCP Server)}.
     * Kinds that touch files or run commands never qualify, whatever the title says: a path containing "camel-tui" is
     * not a tool identity. The caller approves a tool of the TUI silently, as the TUI asks itself when it must;
     * everything else goes to the user.
     */
    private static String tuiToolName(String name, String title, String kind) {
        if (FILE_OR_SHELL_KINDS.contains(kind)) {
            return null;
        }
        if (name.startsWith(TUI_TOOL_PREFIX)) {
            return name.substring(TUI_TOOL_PREFIX.length());
        }
        int paren = title.indexOf("(camel-tui");
        if (paren > 0) {
            return title.substring(0, paren).strip();
        }
        return null;
    }

    /**
     * The line the permission popup shows under the tool: an agent that edits a source file with its own tools bypasses
     * the TUI's diff and live replay, and the user may prefer to reject it and have the agent use camel_write_file, as
     * the preamble told it to.
     */
    private static String permissionHint(String kind) {
        if ("edit".equals(kind) || "delete".equals(kind) || "move".equals(kind)) {
            return "The TUI cannot show or replay this change; Esc rejects it so the agent uses camel_write_file";
        }
        return null;
    }

    private static String firstOptionOfKind(List<JsonObject> options, String kind) {
        for (JsonObject option : options) {
            if (kind.equals(option.getString("kind"))) {
                return option.getString("optionId");
            }
        }
        return null;
    }

    /** {@code reasonOverride} replaces the provider's stop reason on the Ollama tab, e.g. "limit" for the wrap-up. */
    private void recordUsage(LlmClient.ChatResponse response, long latencyMs, String reasonOverride) {
        if (client == null || response.usage().totalTokens() == 0) {
            return;
        }
        String model = client.model() != null ? client.model() : "unknown";
        String provider = client.apiType() != null ? client.apiType().name() : "unknown";
        usage.add(new AiUsageEntry(
                model, provider,
                response.usage().inputTokens(), response.usage().outputTokens(),
                response.usage().totalTokens(), latencyMs,
                response.stopReason(), Instant.now(),
                AiUsageSource.TUI, null, questionCounter));
        noteMeasuredPrompt(response.usage());
        if (ctx != null && ctx.ollamaMonitor != null && client.apiType() == LlmClient.ApiType.ollama) {
            // the Ollama tab shows this request with the timings Ollama returned
            ctx.ollamaMonitor.adoptEndpoint(client.endpointUrl());
            ctx.ollamaMonitor.recordRequest(model, response.usage(), latencyMs,
                    reasonOverride != null ? reasonOverride : response.stopReason(), questionCounter, currentQuestion);
            ctx.ollamaMonitor.setPanelContext(knownContextWindow, compactionBudgetTokens(true, knownContextWindow));
        }
    }

    /** Keeps what the provider measured: the prompt size, the cold prefill speed and, for Ollama, the window. */
    private void noteMeasuredPrompt(LlmClient.TokenUsage usage) {
        if (usage.inputTokens() > 0) {
            lastMeasuredPromptTokens = usage.inputTokens();
        }
        if (usage.cachedTokens() == 0 && usage.inputTokens() >= 1000 && usage.prefillMillis() > 0) {
            coldPrefillTokensPerSecond = usage.inputTokens() * 1000.0 / usage.prefillMillis();
        }
        if (client != null && client.apiType() == LlmClient.ApiType.ollama) {
            knownContextWindow = client.ollamaContextWindow();
        }
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
                    Span.styled("(" + formatSeconds(titleElapsed) + tokenSuffix + describeContextFillSuffix() + ") ",
                            Style.EMPTY.dim()));
        } else if (acpSession.isSelected()) {
            titleLine = Line.from(
                    Span.styled(" AI ", Style.EMPTY.bold()),
                    Span.styled("· " + acpSession.label() + acpSession.contextSuffix() + " ", Style.EMPTY.dim()));
        } else if (sessionTotalTokens > 0) {
            titleLine = Line.from(
                    Span.styled(" AI ", Style.EMPTY.bold()),
                    Span.styled("(total: " + LlmClient.formatTokens(sessionTotalTokens) + " tokens"
                                + describeContextFillSuffix() + ") ",
                            Style.EMPTY.dim()));
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
            usage.render(frame, inner);
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
        if (acpSession.isSelected() && acpSession.sessionId() != null && inner.height() >= 8) {
            List<Rect> top = Layout.vertical()
                    .constraints(Constraint.length(AcpHeaderStrip.ROWS), Constraint.length(1), Constraint.fill())
                    .split(inner);
            acpHeader.render(frame, top.get(0), acpSession.headerModel());
            frame.renderWidget(Paragraph.from(Line.from(Span.styled("─".repeat(top.get(1).width()), Style.EMPTY.dim()))),
                    top.get(1));
            body = top.get(2);
        }

        // Split the body area: conversation (fill) + optional slash hints + separator (1 row) + input (1 row per line)
        List<AiSlashCommandRegistry.Descriptor> slashHints = slashCommandHints();
        int hintRows = slashHints.isEmpty() ? 0 : slashHints.size();
        int inputRows = inputLine.rows();
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
        inputLine.render(frame, inputArea, INPUT_PROMPT, thinking.get() && editQuestionHandler == null,
                slashCommands::placeholderFor);
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
        return slashCommands.completionsFor(inputLine.text(), acpSession.commandDescriptors());
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
            if (initError.startsWith(AiProviderSelector.NO_PROVIDER)) {
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
                case USER -> md.append(quoteQuestion(entry.text())).append("\n\n");
                case ASSISTANT -> {
                    md.append(toHardBreaks(entry.text())).append("\n\n");
                    if (entry.note() != null) {
                        md.append(entry.note()).append("\n\n");
                    }
                }
                case ERROR -> md.append("**Error:** ").append(entry.text()).append("\n\n");
                case SYSTEM -> md.append(toHardBreaks(entry.text())).append("\n\n");
            }
        }

        // Show elapsed time and token count as a dimmed line below the markdown when at the bottom
        long lastElapsed = -1;
        String lastByline = "";
        if (!thinking.get() && !conversation.isEmpty()) {
            ConversationEntry last = conversation.get(conversation.size() - 1);
            if (last.role() == AiRole.ASSISTANT && last.elapsedMs() >= 0) {
                lastElapsed = last.elapsedMs();
                lastByline = last.byline();
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
                .styles(Theme.chatMarkdownStyles())
                .syntaxTheme(Theme.syntaxTheme());
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
        // A user who scrolled up keeps reading the same lines while an answer streams in below them: the offset from
        // the bottom grows by the lines added since the last frame. At the bottom the view follows the new lines.
        if (scrollOffset > 0 && lastTotalLines > 0 && totalLines > lastTotalLines) {
            scrollOffset += totalLines - lastTotalLines;
        }
        lastTotalLines = totalLines;
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
            frame.renderWidget(
                    Paragraph.from(
                            Line.from(Span.styled("(" + lastByline + ")", Style.EMPTY.dim()))),
                    elapsedArea);
        }
        if (statusArea != null) {
            renderThinkingStatus(frame, statusArea);
        }
    }

    private void renderThinkingStatus(Frame frame, Rect area) {
        long elapsed = (MonotonicClock.millis() - thinkingStartTime) / 1000;
        Spinner spinner = Spinner.builder()
                .spinnerStyle(SpinnerStyle.DOTS)
                .style(Style.EMPTY.fg(Theme.accent()).bold())
                .build();
        Rect spinnerArea = new Rect(area.left(), area.top(), 2, 1);
        frame.renderStatefulWidget(spinner, spinnerArea, new SpinnerState(MonotonicClock.millis() / 100));

        long dots = (MonotonicClock.millis() / 500) % 4;
        String text = " " + (thinkingVerb != null ? thinkingVerb : THINKING_VERBS.get(0));
        if (elapsed > 0) {
            text += " (" + elapsed + "s)";
        }
        text += ".".repeat((int) dots + 1);
        frame.renderWidget(Paragraph.from(Line.from(Span.styled(text, Style.EMPTY.fg(Theme.accent())))),
                new Rect(area.left() + 2, area.top(), Math.max(0, area.width() - 2), 1));
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

    /**
     * Forgets the usage recorded so far: the panel's own requests are dropped, and route usage from spans that were
     * exported before now is hidden (the spans themselves stay, as the Spans tab owns them).
     */
    void resetUsage() {
        usage.reset();
    }

    /**
     * The usage figures of the Ctrl+U view as text for the chat: totals, then one line per model (and per route for
     * GenAI spans from the monitored integration).
     */
    String usageSummary() {
        return usage.summary();
    }

    boolean isStatsView() {
        return statsView;
    }

    void toggleStatsViewForTesting() {
        statsView = !statsView;
        usage.resetScroll();
        if (statsView) {
            spanRefreshRequested = true;
        }
    }

    List<AiUsageEntry> combinedUsageEntriesForTesting() {
        return usage.combinedUsageEntries();
    }

    void recordUsageForTesting(AiUsageEntry entry) {
        usage.add(entry);
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

    String describeContext() {
        if (acpSession.isSelected()) {
            return acpSession.describe(estimateTokens(toolSet.acpPreamble(client).length()));
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
        int promptTokens = estimateTokens(toolSet.systemPrompt(client).length());
        int toolTokens = estimateTokens(toolSet.schemaChars(client));
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
        int window = knownContextWindow;
        if (window > 0) {
            int budget = compactionBudgetTokens(true, window);
            sb.append("\nContext window: ").append(LlmClient.formatTokens(window))
                    .append(" tokens (Ollama); the history is compacted once a prompt passes ~")
                    .append(LlmClient.formatTokens(budget));
            if (lastMeasuredPromptTokens > 0) {
                sb.append("; last prompt ").append(LlmClient.formatTokens(lastMeasuredPromptTokens))
                        .append(" tokens (").append(lastMeasuredPromptTokens * 100L / window).append("%)");
            }
        }
        return sb.toString();
    }

    /** {@code , ctx 34%}: the measured prompt against the window, for the title; empty until both are known. */
    private String describeContextFillSuffix() {
        int fill = contextFillPercent();
        return fill > 0 ? ", ctx " + fill + "%" : "";
    }

    /** The measured prompt against the window in percent, 0 until both are known. */
    private int contextFillPercent() {
        int window = knownContextWindow;
        int measured = lastMeasuredPromptTokens;
        if (window <= 0 || measured <= 0) {
            return 0;
        }
        return (int) (measured * 100L / window);
    }

    String compactHistoryNow() {
        if (acpSession.isSelected()) {
            return acpSession.notApplicable("compact", "manages its own conversation history");
        }
        if (messages == null || messages.isEmpty()) {
            return "History is empty, nothing to compact";
        }
        if (client != null && client.isLocalEndpoint()) {
            return compactLocalHistoryWithNotice(compactionBudgetTokens(true, knownContextWindow), false);
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
        if ((client == null && !acpSession.isSelected()) || thinking.get()) {
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
        if (!acpSession.isSelected() && messages != null) {
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

    private void toggleUsageView() {
        statsView = !statsView;
        usage.resetScroll();
        if (statsView) {
            spanRefreshRequested = true;
        }
    }

    private String executeTuiTool(String name, JsonObject args) {
        TuiToolRegistry toolRegistry = toolSet.registry();
        if (toolRegistry == null) {
            return "Error: TUI tools not available";
        }
        try {
            return toolRegistry.execute(name, args);
        } catch (TuiToolRegistry.UnknownToolException e) {
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

    /**
     * The user's question as a blockquote, its lines kept as typed (hard breaks). A fenced code block in it, such as
     * the source the fix with AI question quotes, is put outside the quote, as a code block inside a quote is not
     * rendered.
     */
    static String quoteQuestion(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        boolean inFence = false;
        boolean quoted = false;
        for (String line : text.split("\n", -1)) {
            if (line.strip().startsWith("```")) {
                if (!inFence && quoted) {
                    // end the quote, so the fence starts a code block of its own
                    sb.append('\n');
                    quoted = false;
                }
                sb.append(line.strip()).append('\n');
                if (inFence) {
                    sb.append('\n');
                }
                inFence = !inFence;
            } else if (inFence) {
                sb.append(line).append('\n');
            } else {
                sb.append(line.isBlank() ? ">" : "> " + line + "  ").append('\n');
                quoted = true;
            }
        }
        return sb.toString().stripTrailing();
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
        if (acpSession.isSelected() && thinking.get()) {
            // an abandoned ACP turn would keep writing into the list we are about to clear
            interruptBusyOperation();
        }
        conversation.clear();
        activityLog.clear();
        inputLine.clear();
        scrollOffset = 0;
        usage.clear();
        sessionTotalTokens = 0;
        if (messages != null) {
            messages.clear();
        }
        // the next prompt opens a fresh agent session (context reset), keeping the process
        acpSession.resetSession();
    }

    void setPromptHistoryForTesting(TuiPromptHistory history) {
        inputLine.setPromptHistory(history);
    }

    List<String> promptHistoryEntriesForTesting() {
        TuiPromptHistory history = inputLine.promptHistory();
        return history == null ? List.of() : history.entriesForTesting();
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
        return acpSession.isSelected();
    }

    void setAcpClientFactoryForTesting(AcpSession.AcpClientFactory factory) {
        acpSession.setClientFactory(factory);
    }

    void setMcpUrlSupplierForTestingOrRuntime(Callable<String> supplier) {
        acpSession.setMcpUrlSupplier(supplier);
    }

    String acpSessionIdForTesting() {
        return acpSession.sessionId();
    }

    boolean isPermissionPopupVisible() {
        return permissionPopup.isVisible();
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

    long[] acpContextForTesting() {
        return new long[] { acpSession.contextUsed(), acpSession.contextSize() };
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
        return inputLine.text();
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
        toolSet.setRegistry(registry);
    }

    void setToolModeForTesting(String mode) {
        toolSet.setMode(normalizeToolMode(mode));
    }

    String systemPromptForTesting() {
        return toolSet.systemPrompt(client);
    }

    void setAppStatusSourceForTesting(AppStatusSource source) {
        toolSet.setAppStatusSource(source);
    }

    void refreshToolGroupsForTesting() {
        toolSet.refreshToolGroups(mcpFacade);
    }

    TuiToolGroups.Selection toolGroupsForTesting() {
        return toolSet.groups();
    }

    List<LlmClient.ToolDef> toolDefinitionsForTesting() {
        return toolSet.definitions(client);
    }

    private String describeToolMode() {
        if (acpSession.isSelected()) {
            return acpSession.label() + " reaches the camel-tui tools through the MCP server directly; "
                   + "the tool set only applies to LLM providers";
        }
        return toolSet.describe(client);
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
            inputLine.clearHistory();
        }

        @Override
        public String currentModel() {
            if (acpSession.isSelected()) {
                return acpSession.label();
            }
            return client != null && client.model() != null ? client.model() : "unknown";
        }

        @Override
        public List<String> availableModels() {
            if (acpSession.isSelected()) {
                return List.of();
            }
            return client != null ? client.listModels() : List.of();
        }

        @Override
        public boolean switchModel(String model) {
            if (acpSession.isSelected()) {
                throw new IllegalStateException(
                        "The model is configured in the agent (" + acpSession.label() + "), not in the TUI.");
            }
            if (client == null) {
                return false;
            }
            client.withModel(model);
            client.resolveOllamaContextWindowInBackground();
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
            if (acpSession.isSelected()) {
                throw new IllegalStateException(
                        "The tool set only applies to LLM providers; " + acpSession.label()
                                                + " reaches the camel-tui tools through the MCP server directly.");
            }
            String normalized = normalizeToolMode(mode);
            if (normalized == null) {
                return false;
            }
            toolSet.setMode(normalized);
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
        public String projectOverview(String arguments) {
            return AiPanel.this.projectOverview(arguments);
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
            if (acpSession.isSelected()) {
                return "Sent to " + acpSession.label() + " ahead of the first prompt of each session:\n\n"
                       + toolSet.acpPreamble(client);
            }
            return toolSet.systemPrompt(client);
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
            LaunchManager launchManager = launchManager();
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
            launchManager().launchDetached(spec.displayName(), spec.camelArgs());
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
