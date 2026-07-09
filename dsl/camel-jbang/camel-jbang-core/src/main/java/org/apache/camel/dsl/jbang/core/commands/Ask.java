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
package org.apache.camel.dsl.jbang.core.commands;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.camel.dsl.jbang.core.common.EnvironmentHelper;
import org.apache.camel.dsl.jbang.core.common.RuntimeHelper;
import org.apache.camel.util.json.JsonObject;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Ask a question about a running Camel application using AI with tool calling. The LLM can inspect the live runtime
 * (routes, health, traces, etc.) to provide informed answers.
 */
@Command(name = "ask",
         description = "Ask a question about a running Camel application using AI",
         sortOptions = false, showDefaultValues = true,
         footer = {
                 "%nExamples:",
                 "  camel ask \"what routes are running?\"",
                 "  camel ask \"why is my route failing?\" --name=myApp",
                 "  camel ask \"show me the route structure\" --api-type=anthropic",
                 "  camel ask \"are there any blocked exchanges?\" --model=gpt-4",
                 "  camel ask                                   (interactive chat)" })
public class Ask extends CamelCommand {

    private static final String DEFAULT_MODEL = "llama3.2";
    private static final String NO_PROCESS
            = "No running Camel process connected. Start one with: camel run <file>";

    @Parameters(description = "Question to ask (omit for interactive chat mode)", arity = "0..*")
    List<String> question;

    @Option(names = { "--url" },
            description = "LLM API endpoint URL. Auto-detected if not specified.")
    String url;

    @Option(names = { "--api-type" },
            description = "API type: 'ollama', 'openai', or 'anthropic'")
    LlmClient.ApiType apiType;

    @Option(names = { "--api-key" },
            description = "API key. Also reads ANTHROPIC_API_KEY, OPENAI_API_KEY, or LLM_API_KEY env vars")
    String apiKey;

    @Option(names = { "--model" },
            description = "Model to use",
            defaultValue = DEFAULT_MODEL)
    String model = DEFAULT_MODEL;

    @Option(names = { "--timeout" },
            description = "Timeout in seconds for LLM response",
            defaultValue = "120")
    int timeout = 120;

    @Option(names = { "--name" },
            description = "Name or PID of the Camel process. Auto-detected when exactly one process is running")
    String nameOrPid;

    @Option(names = { "--max-iterations" },
            description = "Maximum number of tool-calling rounds",
            defaultValue = "10")
    int maxIterations = 10;

    @Option(names = { "--show-tools" },
            description = "Show tool calls and results as they happen")
    boolean showTools;

    @Option(names = { "--show-stats" },
            description = "Show token usage and elapsed time after response")
    boolean showStats;

    @Option(names = { "--verbose" },
            description = "Print debug information: HTTP requests, responses, and parsed results")
    boolean verbose;

    private long targetPid;
    private AskTools askTools;

    public Ask(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        LlmClient client = LlmClient.create()
                .withUrl(url)
                .withApiType(apiType)
                .withApiKey(apiKey)
                .withModel(model)
                .withTimeout(timeout)
                .withTemperature(0.3)
                .withStream(true)
                .withMaxTokens(4096)
                .withVerbose(verbose)
                .withPrinter(printer());

        if (!client.detectEndpoint()) {
            printer().printErr("LLM service is not reachable.");
            printer()
                    .printErr("Options: --url=<endpoint>, --api-type=anthropic, or start Ollama with: camel infra run ollama");
            return 1;
        }

        RuntimeHelper.ProcessInfo process = findProcess(nameOrPid);
        if (process != null) {
            targetPid = process.pid();
        } else if (nameOrPid != null && !nameOrPid.isBlank()) {
            return 1;
        } else {
            targetPid = -1;
        }

        askTools = new AskTools(targetPid);
        String systemPrompt = buildSystemPrompt(process);
        List<LlmClient.ToolDef> tools = askTools.buildToolDefinitions();

        if (question == null || question.isEmpty()) {
            return runInteractiveChat(client, process, systemPrompt, tools);
        }

        String userQuestion = String.join(" ", question);
        printer().println("Using " + client.model + " (" + client.apiType + ") to answer your question...");
        if (process != null) {
            printer().println("Target: " + process.name() + " (PID " + process.pid() + ")");
        }
        printer().println();

        List<LlmClient.Message> messages = new ArrayList<>();
        return runAgentLoop(client, systemPrompt, tools, messages, userQuestion);
    }

    private int runInteractiveChat(
            LlmClient client, RuntimeHelper.ProcessInfo process,
            String systemPrompt, List<LlmClient.ToolDef> tools)
            throws Exception {
        Terminal terminal = EnvironmentHelper.getActiveTerminal();
        if (terminal == null) {
            terminal = TerminalBuilder.builder().system(true).build();
        }
        LineReader reader = LineReaderBuilder.builder().terminal(terminal).build();

        printer().println("Camel AI Assistant (" + client.model + ", " + client.apiType + ")");
        if (process != null) {
            printer().println("Target: " + process.name() + " (PID " + process.pid() + ")");
        }
        printer().println("Type your question, or 'exit' to quit.");
        printer().println();

        List<LlmClient.Message> messages = new ArrayList<>();

        while (true) {
            String line;
            try {
                line = reader.readLine("ask> ");
            } catch (UserInterruptException | EndOfFileException e) {
                break;
            }
            if (line == null || line.isBlank() || "exit".equalsIgnoreCase(line.strip())) {
                break;
            }

            int result = runAgentLoop(client, systemPrompt, tools, messages, line.strip());
            if (result != 0) {
                printer().printErr("(error processing question, continuing...)");
            }
            printer().println();
        }
        return 0;
    }

    private int runAgentLoop(
            LlmClient client, String systemPrompt,
            List<LlmClient.ToolDef> tools, List<LlmClient.Message> messages,
            String userQuestion) {
        messages.add(LlmClient.Message.user(userQuestion));

        long startTime = System.currentTimeMillis();
        LlmClient.TokenUsage totalUsage = LlmClient.TokenUsage.EMPTY;
        for (int i = 0; i < maxIterations; i++) {
            LlmClient.ChatResponse response = client.chatWithTools(systemPrompt, messages, tools);
            if (response == null) {
                printer().printErr("Failed to get response from LLM");
                return 1;
            }
            totalUsage = totalUsage.add(response.usage());

            if (response.toolCalls() != null && !response.toolCalls().isEmpty()) {
                messages.add(LlmClient.Message.assistantWithToolCalls(response.text(), response.toolCalls()));

                List<LlmClient.ToolResult> results = new ArrayList<>();
                for (LlmClient.ToolCall toolCall : response.toolCalls()) {
                    if (showTools) {
                        printer().println("[tool] " + toolCall.name() + "(" + toolCall.arguments().toJson() + ")");
                    }
                    String result = askTools.executeTool(toolCall.name(), toolCall.arguments());
                    if (showTools) {
                        printer().println("[result] " + truncate(result, 200));
                    }
                    results.add(new LlmClient.ToolResult(toolCall.id(), result));
                }
                messages.add(LlmClient.Message.toolResults(results));
            } else {
                if (!response.streamed() && response.text() != null) {
                    printer().println(response.text());
                }
                messages.add(LlmClient.Message.assistantWithToolCalls(response.text(), List.of()));
                printStats(totalUsage, startTime);
                return 0;
            }
        }

        printStats(totalUsage, startTime);
        printer().printErr("Reached maximum iterations (" + maxIterations + ") without a final answer.");
        return 1;
    }

    private void printStats(LlmClient.TokenUsage usage, long startTime) {
        if (!showStats) {
            return;
        }
        long elapsed = (System.currentTimeMillis() - startTime) / 1000;
        StringBuilder sb = new StringBuilder();
        sb.append("(").append(elapsed).append("s");
        if (usage.totalTokens() > 0) {
            sb.append(", ").append(LlmClient.formatTokens(usage.inputTokens())).append(" input / ")
                    .append(LlmClient.formatTokens(usage.outputTokens())).append(" output / ")
                    .append(LlmClient.formatTokens(usage.totalTokens())).append(" total tokens");
        }
        sb.append(")");
        printer().println();
        printer().println(sb.toString());
    }

    // ---- Process discovery (delegates to RuntimeHelper) ----

    private RuntimeHelper.ProcessInfo findProcess(String nameOrPid) {
        // Fall back to TUI-selected process if no explicit name given
        if ((nameOrPid == null || nameOrPid.isBlank()) && EnvironmentHelper.getSelectedProcess() != null) {
            nameOrPid = EnvironmentHelper.getSelectedProcess();
        }

        RuntimeHelper.ProcessInfo found = RuntimeHelper.findProcess(nameOrPid);
        if (found != null) {
            return found;
        }

        List<RuntimeHelper.ProcessInfo> processes = RuntimeHelper.discoverProcesses();
        if (nameOrPid != null && !nameOrPid.isBlank()) {
            if (processes.isEmpty()) {
                printer().printErr("No running Camel processes found.");
                printer().printErr("Start a Camel application first: camel run myRoute.yaml");
            } else if (processes.size() > 1) {
                printer().printErr("No unique Camel process found matching: " + nameOrPid);
                processes.forEach(p -> printer().printErr("  " + p.name() + " (PID " + p.pid() + ")"));
                printer().printErr("Specify a more specific name or PID with --name");
            } else {
                printer().printErr("No Camel process found matching: " + nameOrPid);
            }
        } else if (processes.size() > 1) {
            printer().println("Multiple Camel processes found. Use --name to specify one:");
            processes.forEach(p -> printer().println("  " + p.name() + " (PID " + p.pid() + ")"));
            printer().println();
        }
        return null;
    }

    // ---- System prompt ----

    private String buildSystemPrompt(RuntimeHelper.ProcessInfo process) {
        return AskTools.buildSystemPrompt(
                process != null ? process.pid() : -1,
                process != null ? process.name() : null);
    }

    // ---- CLI helper methods (used by AskTools) ----

    @SuppressWarnings("unchecked")
    static void collectCommands(List<JsonObject> commands, List<JsonObject> result, String filter) {
        for (JsonObject cmd : commands) {
            String fullName = cmd.getString("fullName");
            String description = cmd.getString("description");
            boolean matches = filter == null || filter.isBlank()
                    || (fullName != null && fullName.toLowerCase().contains(filter.toLowerCase()))
                    || (description != null && description.toLowerCase().contains(filter.toLowerCase()));
            if (matches) {
                JsonObject entry = new JsonObject();
                entry.put("command", fullName);
                entry.put("description", description);
                Object subs = cmd.get("subcommands");
                if (subs instanceof Collection<?> subList && !subList.isEmpty()) {
                    entry.put("hasSubcommands", true);
                    entry.put("subcommandCount", subList.size());
                }
                result.add(entry);
            }
            Object subs = cmd.get("subcommands");
            if (subs instanceof Collection<?>) {
                collectCommands(
                        ((Collection<Object>) subs).stream()
                                .filter(JsonObject.class::isInstance)
                                .map(JsonObject.class::cast)
                                .toList(),
                        result, filter);
            }
        }
    }

    @SuppressWarnings("unchecked")
    static JsonObject findCommand(List<JsonObject> commands, String fullName) {
        for (JsonObject cmd : commands) {
            if (fullName.equals(cmd.getString("fullName"))) {
                return cmd;
            }
            Object subs = cmd.get("subcommands");
            if (subs instanceof Collection<?>) {
                JsonObject found = findCommand(
                        ((Collection<Object>) subs).stream()
                                .filter(JsonObject.class::isInstance)
                                .map(JsonObject.class::cast)
                                .toList(),
                        fullName);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    static String[] tokenizeCommand(String command) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;

        for (int i = 0; i < command.length(); i++) {
            char c = command.charAt(i);
            if (c == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
            } else if (c == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
            } else if (Character.isWhitespace(c) && !inSingleQuote && !inDoubleQuote) {
                if (!current.isEmpty()) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (!current.isEmpty()) {
            tokens.add(current.toString());
        }
        return tokens.toArray(String[]::new);
    }

    static String truncate(String text, int maxLen) {
        if (text == null) {
            return "null";
        }
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }
}
