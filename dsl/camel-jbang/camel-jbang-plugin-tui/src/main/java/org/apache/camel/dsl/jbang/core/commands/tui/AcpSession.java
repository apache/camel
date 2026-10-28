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
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;

import org.apache.camel.dsl.jbang.core.commands.LlmClient;

/**
 * The external coding agent of the AI panel, when the selected provider is one (provider id {@code acp:*}): its preset,
 * the agent process, spawned on the first question so a slow first npx download never freezes the UI, its session,
 * opened again after /clear, and what it last reported of its context window. {@link AcpAgentClient} speaks the
 * protocol; the panel runs the turns, and asks the user before a tool call ({@link Host#permissionHandler()}).
 */
class AcpSession {

    /** Creates the client for a preset; replaced in tests with one backed by {@code FakeAcpAgent}. */
    interface AcpClientFactory {
        AcpAgentClient create(AiProviderSelector.AcpPreset preset, Path cwd) throws IOException;
    }

    /** What the session needs of the AI panel. */
    interface Host {
        /** Adds a line of the panel to the conversation: the agent starting, authenticating. */
        void say(String text);

        void log(AiPanel.LogLevel level, String message, String detail);

        /** Asks the user before a tool call of the agent, unless the call needs no asking. */
        AcpAgentClient.PermissionHandler permissionHandler();

        /** The directory the agent works in: the sources of the selected integration, else the current one. */
        Path workingDir();
    }

    private static final Duration ACP_INIT_TIMEOUT = Duration.ofSeconds(120);
    private static final Duration ACP_SESSION_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration ACP_AUTH_TIMEOUT = Duration.ofSeconds(300);

    private final Host host;
    private volatile AiProviderSelector.AcpPreset acpPreset;
    private volatile AcpAgentClient acpClient;
    private volatile AcpAgentClient.AgentInfo acpAgentInfo;
    private volatile String acpSessionId;
    private volatile boolean acpPreambleSent;
    private String acpMcpUrl;
    private Path acpCwd;
    private AcpClientFactory acpClientFactory = this::spawn;
    private Callable<String> mcpUrlSupplier;
    /** What the ACP agent last reported in its context window, and how big that window is (0 = not reported). */
    private volatile long acpContextUsed;
    private volatile long acpContextSize;

    AcpSession(Host host) {
        this.host = host;
    }

    /** Whether the selected provider is an ACP agent. */
    boolean isSelected() {
        return acpPreset != null;
    }

    /** The preset of the selected agent; null when the provider is no ACP agent. */
    AiProviderSelector.AcpPreset preset() {
        return acpPreset;
    }

    /** Selects the agent of the preset (null for none); it starts with the next question. */
    void setPreset(AiProviderSelector.AcpPreset preset) {
        this.acpPreset = preset;
    }

    /** The running agent, null before the first question. */
    AcpAgentClient client() {
        return acpClient;
    }

    String sessionId() {
        return acpSessionId;
    }

    /** Whether the preamble went out with a prompt of this session: it is sent once per session. */
    boolean preambleSent() {
        return acpPreambleSent;
    }

    void markPreambleSent() {
        acpPreambleSent = true;
    }

    /** Notes what the agent reported of its context window at the end of a turn; returns what it reported before. */
    long recordContext(long used, long size) {
        long previous = acpContextUsed;
        acpContextUsed = used;
        acpContextSize = size;
        return previous;
    }

    long contextUsed() {
        return acpContextUsed;
    }

    long contextSize() {
        return acpContextSize;
    }

    /** The next question opens a fresh session of the agent, its context empty; the process keeps running. */
    void resetSession() {
        acpSessionId = null;
        acpPreambleSent = false;
        acpContextUsed = 0;
        acpContextSize = 0;
    }

    /** Asks the agent to stop the turn of its session, without waiting for it. */
    void cancelTurn() {
        AcpAgentClient agent = acpClient;
        String session = acpSessionId;
        if (agent != null && session != null) {
            // Do not block the TUI event thread: cancel() writes to the agent under the client's writer lock.
            // prompt() sends the same notification on its own interrupt path; a duplicate cancel is harmless.
            Thread canceller = new Thread(() -> agent.cancel(session), "tui-acp-cancel");
            canceller.setDaemon(true);
            canceller.start();
        }
    }

    void setClientFactory(AcpClientFactory factory) {
        this.acpClientFactory = factory;
    }

    /** Where the agent reaches the tools of the TUI: the URL of its MCP server, started on the first call. */
    void setMcpUrlSupplier(Callable<String> supplier) {
        this.mcpUrlSupplier = supplier;
    }

    /** Closes the agent process, in the background, and forgets its session: the next question starts it again. */
    void close() {
        AcpAgentClient agent = acpClient;
        acpClient = null;
        acpSessionId = null;
        acpAgentInfo = null;
        acpPreambleSent = false;
        acpContextUsed = 0;
        acpContextSize = 0;
        if (agent != null) {
            // Do not block the TUI event thread: close() destroys the agent process and waits up to 5 seconds for
            // it to die. Nothing here observes the shutdown, so it runs on a short-lived daemon thread.
            Thread closer = new Thread(agent::close, "tui-acp-close");
            closer.setDaemon(true);
            closer.start();
        }
    }

    /** The name of the agent: as it introduced itself, else the preset's. */
    String label() {
        AcpAgentClient.AgentInfo info = acpAgentInfo;
        AiProviderSelector.AcpPreset preset = acpPreset;
        if (info != null) {
            return info.label();
        }
        return preset != null ? preset.label() : "ACP agent";
    }

    /**
     * Spawns the agent and negotiates the protocol on first use, then opens a session when none is active (first
     * prompt, or after /clear). Runs on the agent thread; every failure is reported by the caller.
     */
    AcpAgentClient ensureSession() throws IOException {
        AiProviderSelector.AcpPreset preset = acpPreset;
        AcpAgentClient agent = acpClient;
        if (agent == null || !agent.isAlive()) {
            close();
            String mcpUrl;
            try {
                mcpUrl = mcpUrlSupplier != null ? mcpUrlSupplier.call() : null;
            } catch (Exception e) {
                throw new IllegalStateException("Could not start the TUI MCP server: " + e.getMessage(), e);
            }
            if (mcpUrl == null) {
                throw new IllegalStateException("The TUI MCP server is not available in this session.");
            }
            Path cwd = host.workingDir();
            host.say("Starting " + preset.label() + "…");
            agent = acpClientFactory.create(preset, cwd);
            agent.setPermissionHandler(host.permissionHandler());
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
            host.log(AiPanel.LogLevel.RESULT, "ACP agent started", info.label() + " cwd=" + cwd);
        }
        if (acpSessionId == null) {
            acpSessionId = openSession(agent, acpAgentInfo, acpMcpUrl, acpCwd);
            acpPreambleSent = false;
            acpContextUsed = 0;
            acpContextSize = 0;
            host.log(AiPanel.LogLevel.RESULT, "ACP session", acpSessionId);
        }
        return agent;
    }

    private String openSession(AcpAgentClient agent, AcpAgentClient.AgentInfo info, String mcpUrl, Path cwd) {
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
            host.say("Authenticating with " + info.label() + " (" + method.name() + ")…");
            try {
                agent.authenticate(method.id(), ACP_AUTH_TIMEOUT);
                return agent.newSession(cwd, mcpUrl, ACP_SESSION_TIMEOUT);
            } catch (AcpAgentClient.AcpException retry) {
                throw new IllegalStateException(
                        "Authentication failed: " + retry.getMessage() + ". " + acpPreset.loginHint());
            }
        }
    }

    private AcpAgentClient spawn(AiProviderSelector.AcpPreset preset, Path cwd) throws IOException {
        // ProcessBuilder only tries .exe on Windows, so the resolved file (npx.cmd and friends) has to be launched
        String resolved = AiProviderSelector.resolveExecutable(preset.executable());
        if (resolved == null) {
            throw new IOException(preset.installHint());
        }
        List<String> command = new ArrayList<>(preset.command());
        if (!command.isEmpty() && command.get(0).equals(preset.executable())) {
            command.set(0, resolved);
        }
        return AcpAgentClient.spawn(command, cwd, line -> host.log(AiPanel.LogLevel.ERROR, "ACP", line));
    }

    /** What the header strip of the panel shows of the agent and its session. */
    AcpHeaderStrip.Model headerModel() {
        AiProviderSelector.AcpPreset preset = acpPreset;
        AcpAgentClient agent = acpClient;
        int commands = agent != null ? agent.availableCommands().size() : 0;
        return new AcpHeaderStrip.Model(
                preset.label(), preset.glyph(), preset.color(), label(), acpSessionId, acpCwd, commands);
    }

    /**
     * The ACP agent's advertised commands as display-only descriptors (null executor: they are forwarded, never run
     * locally). Displayed and completed under the {@code agent:} name so they never collide with a panel command of the
     * same name; the bare name is registered as an alias so typing it still suggests the prefixed form.
     */
    List<AiSlashCommandRegistry.Descriptor> commandDescriptors() {
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
    String commandListing() {
        List<AiSlashCommandRegistry.Descriptor> commands = commandDescriptors();
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

    /** The title's context figure, empty until the agent reports one. */
    String contextSuffix() {
        long used = acpContextUsed;
        if (used <= 0) {
            return "";
        }
        long size = acpContextSize;
        return " (context: " + formatTokens(used) + (size > 0 ? "/" + formatTokens(size) : "") + " tokens)";
    }

    private static String formatTokens(long tokens) {
        return LlmClient.formatTokens((int) Math.min(Integer.MAX_VALUE, tokens));
    }

    /**
     * The {@code /context} answer while an ACP agent is selected. The agent owns the conversation history and runs its
     * own tools, so the interesting figures are its session, the MCP server it was given and the preamble the panel
     * prepends to the first prompt.
     *
     * @param preambleTokens the size of the preamble, in tokens
     */
    String describe(int preambleTokens) {
        AiProviderSelector.AcpPreset preset = acpPreset;
        AcpAgentClient agent = acpClient;
        StringBuilder sb = new StringBuilder();
        sb.append("Agent: ").append(label())
                .append(" (preset ").append(preset != null ? preset.id() : "acp").append(")\n");
        if (acpSessionId != null) {
            sb.append("Session: ").append(acpSessionId).append(" in ").append(acpCwd).append('\n');
            sb.append("MCP: ").append(acpMcpUrl).append(" (read-only camel-tui tools are approved automatically)\n");
        } else {
            sb.append("Session: not started yet; the next prompt opens one and starts the MCP server on demand\n");
        }
        sb.append("Agent commands: ").append(agent != null ? agent.availableCommands().size() : 0)
                .append(" (/agent: lists them)\n");
        sb.append("Preamble: ~").append(LlmClient.formatTokens(preambleTokens))
                .append(" tokens, sent once per session ahead of the first prompt (/prompt shows it); ")
                .append(acpPreambleSent ? "sent" : "not sent yet").append('\n');
        if (acpContextUsed > 0) {
            sb.append("Context: ").append(formatTokens(acpContextUsed));
            if (acpContextSize > 0) {
                sb.append(" of ").append(formatTokens(acpContextSize));
            }
            sb.append(" tokens in the agent's window\n");
        } else {
            sb.append("Context: not reported yet\n");
        }
        sb.append("History and tools are managed by the agent: /compact and /tools do not apply here");
        return sb.toString();
    }

    /**
     * Explains that a panel command has no meaning while an agent is selected, pointing at the agent's own command of
     * the same name when it advertises one.
     */
    String notApplicable(String command, String what) {
        AcpAgentClient agent = acpClient;
        boolean offered = agent != null
                && agent.availableCommands().stream().anyMatch(c -> command.equals(c.name()));
        return "/" + command + " does not apply here: " + label() + " " + what + "."
               + (offered ? " Use /agent:" + command + "." : "");
    }
}
