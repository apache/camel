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

import java.io.Console;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import org.apache.camel.dsl.jbang.core.commands.ai.AnswerChecks;
import org.apache.camel.dsl.jbang.core.commands.ai.ToolConfirms;
import org.apache.camel.dsl.jbang.core.common.EnvironmentHelper;
import org.apache.camel.dsl.jbang.core.common.Printer;
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
            description = "LLM API endpoint URL. Auto-detected if not specified. Also reads AZURE_OPENAI_ENDPOINT or WATSONX_URL env vars")
    String url;

    @Option(names = { "--api-type" },
            description = "API type: 'ollama', 'openai', 'anthropic', or 'watsonx'")
    LlmClient.ApiType apiType;

    @Option(names = { "--api-key" },
            description = "API key. Also reads ANTHROPIC_API_KEY, OPENAI_API_KEY, WATSONX_APIKEY, or LLM_API_KEY env vars")
    String apiKey;

    @Option(names = { "--model" },
            description = "Model to use. Default: the provider's default; with Ollama the installed model best at"
                          + " tool calling, such as qwen3.6")
    String model;

    @Option(names = { "--timeout" },
            description = "Timeout in seconds for LLM response",
            defaultValue = "120")
    int timeout = 120;

    @Option(names = { "--name" },
            description = "Name or PID of the Camel process. Auto-detected when exactly one process is running")
    String nameOrPid;

    @Option(names = { "--max-iterations" },
            description = "Maximum number of tool-calling rounds",
            defaultValue = "25")
    int maxIterations = 25;

    @Option(names = { "--tools" },
            description = "The tools the model gets: core (a small set, for a local model), full, or auto (core for a"
                          + " local model, full for a hosted one)",
            defaultValue = "auto")
    ToolSet tools = ToolSet.auto;

    @Option(names = { "--yes" },
            description = "Run the calls that ask first without asking: file writes, messages to endpoints the"
                          + " integration does not have, SQL that changes data, code expressions, stopping the"
                          + " integration, and camel commands that change something. For scripts, where there is no"
                          + " terminal to ask on")
    boolean yes;

    @Option(names = { "--show-tools" },
            description = "Show tool calls and results as they happen")
    boolean showTools;

    @Option(names = { "--show-stats" },
            description = "Show token usage and elapsed time after response")
    boolean showStats;

    @Option(names = { "--verbose" },
            description = "Print debug information: HTTP requests, responses, and parsed results")
    boolean verbose;

    /** The tools the model gets, see {@code --tools}. */
    enum ToolSet {
        auto,
        core,
        full
    }

    /** Below this size (billions of parameters) a model's answers about a running integration are often wrong. */
    static final double SMALL_MODEL_BILLIONS = 8;

    private long targetPid;
    private AskTools askTools;
    // the chat's reader, or one made on the terminal for the first call to confirm
    private LineReader confirmReader;

    public Ask(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        LlmClient client = LlmClient.create()
                .withUrl(url)
                .withApiType(apiType)
                .withApiKey(apiKey)
                .withTimeout(timeout)
                .withTemperature(0.3)
                .withStream(true)
                .withMaxTokens(4096)
                .withVerbose(verbose)
                .withPrinter(printer());
        if (model != null && !model.isBlank()) {
            client.withExplicitModel(model);
        } else {
            // the placeholder that Ollama replaces with the installed model best at tool calling
            client.withModel(DEFAULT_MODEL);
        }

        if (!client.detectEndpoint()) {
            printer().printErr("LLM service is not reachable.");
            printer().printErr("Options: --url=<endpoint>, --api-type=anthropic, or start Ollama with: camel infra run ollama");
            return 1;
        }
        warnSmallModel(client.model, client.ollamaParameterBillions());

        RuntimeHelper.ProcessInfo process = findProcess(nameOrPid);
        if (process != null) {
            targetPid = process.pid();
        } else if (nameOrPid != null && !nameOrPid.isBlank()) {
            return 1;
        } else {
            targetPid = -1;
        }

        askTools = new AskTools(targetPid, this::confirmCall);
        boolean core = useCoreTools(tools, client.isLocalEndpoint());
        String systemPrompt = AskTools.buildSystemPrompt(
                process != null ? process.pid() : -1,
                process != null ? process.name() : null,
                core, core ? askTools.groupGuidance() : List.of());
        List<LlmClient.ToolDef> toolDefs = askTools.buildToolDefinitions(core);

        if (question == null || question.isEmpty()) {
            return runInteractiveChat(client, process, systemPrompt, toolDefs);
        }

        String userQuestion = String.join(" ", question);
        printer().println("Using " + client.model + " (" + client.apiType + ", " + (core ? "core" : "full")
                          + " tools) to answer your question...");
        if (process != null) {
            printer().println("Target: " + process.name() + " (PID " + process.pid() + ")");
        }
        printer().println();

        List<LlmClient.Message> messages = new ArrayList<>();
        return runAgentLoop(client, systemPrompt, toolDefs, messages, userQuestion);
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
        // a call to confirm is asked at the same prompt
        confirmReader = reader;

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
        boolean corrected = false;
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
                // what the model says is checked like what it writes: an invalid simple expression in the answer
                // gets one correction turn, since the answer is what the user copies (CAMEL-24805)
                List<AnswerChecks.Problem> problems = AnswerChecks.checkSimple(response.text());
                if (!problems.isEmpty() && !corrected && i < maxIterations - 1) {
                    corrected = true;
                    messages.add(LlmClient.Message.assistantWithToolCalls(response.text(), List.of()));
                    messages.add(LlmClient.Message.user(AnswerChecks.correctionRequest(problems)));
                    printer().println();
                    printer().println("(the answer has an invalid simple expression: asking for a corrected answer)");
                    printer().println();
                    continue;
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
                printer().printTip("Start a Camel application first: camel run myRoute.yaml");
            } else if (processes.size() > 1) {
                printer().printErr("No unique Camel process found matching: " + nameOrPid);
                processes.forEach(p -> printer().printDiagnostic("  " + p.name() + " (PID " + p.pid() + ")"));
                printer().printDiagnostic("Specify a more specific name or PID with --name");
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

    // ---- Tools and model ----

    /** The core tools for a local model, which pays for every tool definition in prompt-processing time. */
    static boolean useCoreTools(ToolSet tools, boolean localEndpoint) {
        return switch (tools) {
            case core -> true;
            case full -> false;
            default -> localEndpoint;
        };
    }

    private void warnSmallModel(String name, double billions) {
        String note = smallModelNote(name, billions);
        if (note != null) {
            printer().println(note);
        }
    }

    /**
     * A note about a small model, or null: in a test, the llama3.2 (3B) that the CLI names as its default got three of
     * four questions about a running integration wrong (CAMEL-25676).
     */
    static String smallModelNote(String name, double billions) {
        if (billions <= 0 || billions >= SMALL_MODEL_BILLIONS) {
            return null;
        }
        return "Note: " + name + " is a small model (" + String.format(Locale.ROOT, "%.1f", billions).replace(".0", "")
               + "B parameters): its answers about a running integration are often wrong. A larger model does better,"
               + " such as qwen3.6 (ollama pull qwen3.6).";
    }

    // ---- Confirmations ----

    /**
     * Whether a call that is confirmed first goes ahead (CAMEL-25676): with --yes it does, otherwise the user is asked
     * on the terminal, and without a terminal to ask on it does not run.
     */
    boolean confirmCall(ToolConfirms.Call call) {
        LineReader reader = yes ? null : confirmReader();
        return confirm(call, yes, reader == null ? null : prompt -> {
            try {
                return reader.readLine(prompt);
            } catch (UserInterruptException | EndOfFileException e) {
                return null;
            }
        }, printer());
    }

    /**
     * @param ask asks the user on the terminal: the prompt in, the answer out (null when the input ended); null when
     *            there is no terminal
     */
    static boolean confirm(ToolConfirms.Call call, boolean yes, Function<String, String> ask, Printer printer) {
        if (yes) {
            printer.println("(--yes) " + call.title());
            return true;
        }
        if (ask == null) {
            printer.printErr("Not run, as there is no terminal to ask on: " + call.title()
                             + ". Run camel ask in a terminal, or with --yes to allow such calls.");
            return false;
        }
        printer.println();
        printer.println("The AI wants to " + call.title() + ":");
        if (call.target() != null) {
            call.target().lines().forEach(line -> printer.println("  " + line));
        }
        printer.println(call.reason() + ".");
        String answer = ask.apply("Allow? [y/N] ");
        String a = answer != null ? answer.strip() : "";
        return "y".equalsIgnoreCase(a) || "yes".equalsIgnoreCase(a);
    }

    private LineReader confirmReader() {
        if (confirmReader == null && hasTerminal()) {
            try {
                Terminal terminal = EnvironmentHelper.getActiveTerminal();
                if (terminal == null) {
                    terminal = TerminalBuilder.builder().system(true).build();
                }
                confirmReader = LineReaderBuilder.builder().terminal(terminal).build();
            } catch (IOException e) {
                return null;
            }
        }
        return confirmReader;
    }

    /** Whether there is a terminal to ask on: the one camel ask runs in (the camel shell), or a console. */
    static boolean hasTerminal() {
        if (EnvironmentHelper.getActiveTerminal() != null) {
            return true;
        }
        Console console = System.console();
        if (console == null) {
            return false;
        }
        try {
            // from Java 22 there is a console also when the input is redirected; isTerminal tells
            return (Boolean) Console.class.getMethod("isTerminal").invoke(console);
        } catch (NoSuchMethodException e) {
            return true;
        } catch (ReflectiveOperationException e) {
            return false;
        }
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
