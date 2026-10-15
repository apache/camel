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

import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import org.apache.camel.dsl.jbang.core.commands.ai.AppFeatures;
import org.apache.camel.dsl.jbang.core.commands.ai.AuthoringGuidelines;
import org.apache.camel.dsl.jbang.core.commands.ai.ToolConfirms;
import org.apache.camel.dsl.jbang.core.commands.ai.ToolContext;
import org.apache.camel.dsl.jbang.core.commands.ai.ToolDescriptor;
import org.apache.camel.dsl.jbang.core.commands.ai.ToolGroups;
import org.apache.camel.dsl.jbang.core.commands.ai.ToolRegistry;
import org.apache.camel.dsl.jbang.core.common.Printer;
import org.apache.camel.dsl.jbang.core.common.RuntimeHelper;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

/**
 * The tools of {@code camel ask} and how it runs them: the shared tools of {@link ToolRegistry}/{@link ToolContext},
 * and the Camel CLI.
 * <p>
 * A local model gets the core tools and the tool groups of the integration, a hosted model the full set without the
 * tools the shared camel_* tools have replaced. A call that reaches beyond the integration, may destroy data or runs
 * code is confirmed first, by the shared rules ({@link ToolConfirms}): the confirmer is asked, and a call it does not
 * confirm does not run (CAMEL-25676).
 */
public class AskTools {

    /** The tools the shared camel_* tools have replaced: a model given both picks either. */
    static final Set<String> SUPERSEDED = Set.of(
            "get_errors", "eval_expression",
            "catalog_components", "catalog_eips", "catalog_dataformats", "catalog_languages",
            "catalog_component_doc", "catalog_eip_doc", "catalog_dataformat_doc", "catalog_language_doc");

    /** The shared tools that the tool groups (by their camel-jbang-mcp names) stand for. */
    static final Map<String, String> GROUP_TOOLS = Map.of(
            ToolGroups.SQL_TOOL, "execute_sql",
            ToolGroups.DATASOURCES_TOOL, "get_datasources",
            ToolGroups.SQL_TRACE_TOOL, "get_sql_trace",
            ToolGroups.SPANS_TOOL, "get_spans",
            ToolGroups.TRACE_TOOL, "trace_control",
            ToolGroups.METRICS_TOOL, "get_metrics",
            ToolGroups.CIRCUIT_BREAKERS_TOOL, "get_circuit_breakers",
            ToolGroups.HTTP_ENDPOINTS_TOOL, "get_http_endpoints",
            ToolGroups.HTTP_REQUEST_TOOL, "http_request");

    private static final String CLI_LIST_COMMANDS = "cli_list_commands";
    private static final String CLI_COMMAND_HELP = "cli_command_help";
    private static final String CLI_EXEC = "cli_exec";

    private long targetPid;
    private final ToolContext ctx;
    private volatile List<JsonObject> commandMetadataCache;

    public AskTools(long targetPid) {
        this(targetPid, null);
    }

    /**
     * @param confirmer asked before a call the user confirms first; null asks nobody and runs every call
     */
    public AskTools(long targetPid, Predicate<ToolConfirms.Call> confirmer) {
        this.ctx = new ToolContext();
        this.ctx.setConfirmer(confirmer);
        setTargetPid(targetPid);
    }

    public long getTargetPid() {
        return targetPid;
    }

    public void setTargetPid(long targetPid) {
        this.targetPid = targetPid;
        if (targetPid >= 0) {
            ctx.selectProcess(targetPid);
        }
        ctx.setDefaultDirectory(projectDirectory());
    }

    ToolContext context() {
        return ctx;
    }

    /**
     * The directory the file tools work in when a call names none: the one the selected integration runs from, else the
     * current directory.
     */
    private Path projectDirectory() {
        String dir = runtimeDirectory();
        if (dir != null && !dir.isBlank() && Files.isDirectory(Path.of(dir))) {
            return Path.of(dir).toAbsolutePath().normalize();
        }
        return Path.of("").toAbsolutePath().normalize();
    }

    /** The working directory of the selected integration, as its status has it, or null. */
    private String runtimeDirectory() {
        if (!ctx.hasProcess()) {
            return null;
        }
        try {
            JsonObject status = ctx.readFullStatus();
            Object runtime = status != null ? status.get("runtime") : null;
            return runtime instanceof JsonObject r ? r.getString("directory") : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** The endpoint URIs of the selected integration, as its status lists them. */
    private List<String> endpointUris() {
        if (!ctx.hasProcess()) {
            return List.of();
        }
        try {
            JsonObject status = ctx.readFullStatus();
            Object endpoints = status != null ? status.get("endpoints") : null;
            Object list = endpoints instanceof JsonObject e ? e.get("endpoints") : null;
            List<String> uris = new ArrayList<>();
            if (list instanceof Collection<?> c) {
                for (Object o : c) {
                    if (o instanceof JsonObject e && e.getString("uri") != null) {
                        uris.add(e.getString("uri"));
                    }
                }
            }
            return uris;
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    private String processName() {
        RuntimeHelper.ProcessInfo p = ctx.hasProcess() ? RuntimeHelper.findProcess(Long.toString(ctx.pid())) : null;
        return p != null ? p.name() + " (PID " + p.pid() + ")" : null;
    }

    /** The features of the selected integration, as its status shows them. */
    private AppFeatures features() {
        if (!ctx.hasProcess()) {
            return AppFeatures.none();
        }
        try {
            return AppFeatures.fromStatus(ctx.readFullStatus());
        } catch (RuntimeException e) {
            return AppFeatures.none();
        }
    }

    // ---- Tool definitions ----

    /** The full set, as a hosted model gets it. */
    public List<LlmClient.ToolDef> buildToolDefinitions() {
        return buildToolDefinitions(false);
    }

    /**
     * The tools for the model: the core set (core) for a local model, which pays for every tool definition in
     * prompt-processing time, else the full set.
     */
    public List<LlmClient.ToolDef> buildToolDefinitions(boolean core) {
        List<String> names = toolNames(core);
        List<LlmClient.ToolDef> tools = new ArrayList<>();
        for (ToolDescriptor td : ToolRegistry.allTools()) {
            if (names.contains(td.name())) {
                tools.add(new LlmClient.ToolDef(td.name(), td.description(), parameters(td)));
            }
        }
        if (names.contains(CLI_LIST_COMMANDS)) {
            tools.add(new LlmClient.ToolDef(
                    CLI_LIST_COMMANDS,
                    "List available Camel CLI commands. Returns command names and descriptions. Use filter to narrow results.",
                    objectParams(Map.of(
                            "filter", stringProp("Filter by command name or description (case-insensitive substring)")))));
        }
        if (names.contains(CLI_COMMAND_HELP)) {
            tools.add(new LlmClient.ToolDef(
                    CLI_COMMAND_HELP,
                    "Get detailed help for a specific Camel CLI command, including all options with types and defaults.",
                    objectParams(Map.of(
                            "command", stringProp("Full command name (e.g., 'get error', 'catalog component', 'run')")))));
        }
        tools.add(new LlmClient.ToolDef(
                CLI_EXEC,
                "Execute a Camel CLI command and return its output, such as 'get route' or 'ps'. A command that changes"
                          + " something (run, stop, cmd, infra run, ...) asks the user first.",
                objectParams(Map.of(
                        "command", stringProp(
                                "The command line without camel (e.g., 'get error --diagram', 'catalog component --filter=kafka')")))));
        return tools;
    }

    /** The names of the tools the model gets, in the order of the definitions. */
    List<String> toolNames(boolean core) {
        List<String> names = new ArrayList<>();
        List<String> groups = core ? groupTools() : List.of();
        for (ToolDescriptor td : ToolRegistry.allTools()) {
            boolean wanted = core ? td.isCore() || groups.contains(td.name()) : !SUPERSEDED.contains(td.name());
            if (wanted) {
                names.add(td.name());
            }
        }
        if (!core) {
            names.add(CLI_LIST_COMMANDS);
            names.add(CLI_COMMAND_HELP);
        }
        names.add(CLI_EXEC);
        return names;
    }

    /** The shared tools of the tool groups the selected integration calls for (SQL, tracing, resilience, HTTP). */
    private List<String> groupTools() {
        return ToolGroups.select(features()).mcpTools().stream()
                .map(GROUP_TOOLS::get)
                .filter(name -> name != null)
                .toList();
    }

    /** What the tool groups of the selected integration say about it, with the names of the shared tools. */
    public List<String> groupGuidance() {
        return ToolGroups.select(features()).guidance().stream().map(AskTools::sharedToolNames).toList();
    }

    static String sharedToolNames(String text) {
        String answer = text;
        for (Map.Entry<String, String> e : GROUP_TOOLS.entrySet()) {
            answer = answer.replace(e.getKey(), e.getValue());
        }
        return answer;
    }

    private static JsonObject parameters(ToolDescriptor td) {
        if (td.params().isEmpty()) {
            return emptyParams();
        }
        Map<String, JsonObject> props = new LinkedHashMap<>();
        for (ToolDescriptor.Param p : td.params()) {
            JsonObject prop = stringProp(p.description());
            if (p.items() != null) {
                prop.put("type", "array");
                prop.put("items", p.items());
            }
            props.put(p.name(), prop);
        }
        return objectParams(props);
    }

    // ---- Tool execution ----

    public String executeTool(String name, JsonObject args) {
        try {
            ToolDescriptor td = ToolRegistry.findTool(name);
            if (td != null) {
                // Convert JsonObject args to Map<String, String> for the registry
                Map<String, String> argMap = new LinkedHashMap<>();
                if (args != null) {
                    for (String key : args.keySet()) {
                        Object val = args.get(key);
                        if (val instanceof Map<?, ?> || val instanceof List<?>) {
                            // e.g. the edits of camel_edit_file: the shared tools read JSON
                            argMap.put(key, Jsoner.serialize(val));
                        } else if (val != null) {
                            argMap.put(key, val.toString());
                        }
                    }
                }
                ToolConfirms.Call call = confirmation(name, argMap);
                if (!ctx.confirm(call)) {
                    return rejected(call);
                }
                Object result = ToolRegistry.execute(name, ctx, argMap);
                if ("select_process".equals(name) && ctx.hasProcess()) {
                    // the file tools follow the integration
                    setTargetPid(ctx.pid());
                }
                return result != null ? result.toString() : "";
            }
            return switch (name) {
                case CLI_LIST_COMMANDS -> executeCliListCommands(args);
                case CLI_COMMAND_HELP -> executeCliCommandHelp(args);
                case CLI_EXEC -> executeCliExec(args);
                default -> "Unknown tool: " + name;
            };
        } catch (Exception e) {
            return "Error executing " + name + ": " + e.getMessage();
        }
    }

    /**
     * The call of a shared tool the user confirms first, or null. A file write is asked by the write tools themselves,
     * with the change (the context's confirmer).
     */
    ToolConfirms.Call confirmation(String name, Map<String, String> args) {
        return switch (name) {
            case "send_message" -> ToolConfirms.sendMessage(args.getOrDefault("endpoint", ""), endpointUris(),
                    ToolConfirms.fileOutsideProject(args.get("body"), runtimeDirectory(), ctx.defaultDirectory()));
            case "browse_endpoint" -> ToolConfirms.browse(args.get("endpoint"), endpointUris());
            case "execute_sql" -> ToolConfirms.sql(args.get("query"));
            case "eval_expression", "camel_eval_expression" ->
                ToolConfirms.evalExpression(args.get("language"), args.get("expression"));
            case "stop_application" -> ToolConfirms.stopApplication(processName());
            case "camel_control" -> ToolConfirms.control(args.get("action"),
                    args.get("name") != null ? args.get("name") : processName());
            case "camel_run" -> ToolConfirms.run(args.get("files") != null
                    ? args.get("files") : args.getOrDefault("directory", ctx.defaultDirectory().toString()));
            case "write_heap_dump" -> ToolConfirms.heapDump(
                    args.get("name") != null ? args.get("name") : processName());
            default -> null;
        };
    }

    /** The answer to a call the user did not confirm. */
    static String rejected(ToolConfirms.Call call) {
        JsonObject result = new JsonObject();
        result.put("status", "rejected");
        result.put("message", "It did not run (" + call.title() + "): camel ask asks the user first, and the user"
                              + " did not confirm it. Do not retry it unless the user asks for it: tell the user what"
                              + " you wanted to do, and why.");
        return result.toJson();
    }

    // ---- System prompt ----

    public static String buildSystemPrompt(long targetPid, String processName) {
        return buildSystemPrompt(targetPid, processName, false, List.of());
    }

    /**
     * The system prompt: the integration, the tools (the core set for a local model), the calls the user confirms, and
     * the guidelines the shared tools are written for ({@link AuthoringGuidelines}).
     *
     * @param groupGuidance what the tool groups of the integration say, see {@link #groupGuidance()}
     */
    public static String buildSystemPrompt(long targetPid, String processName, boolean core, List<String> groupGuidance) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are an Apache Camel assistant in a terminal (camel ask). ");
        sb.append("You help users understand, troubleshoot and change their Camel integrations.\n\n");

        if (targetPid >= 0 && processName != null) {
            sb.append("You are connected to a running Camel integration: ");
            sb.append(processName).append(" (PID ").append(targetPid).append("). ");
            sb.append("Look at it with the tools before you answer.\n\n");
        } else {
            List<RuntimeHelper.ProcessInfo> available = RuntimeHelper.discoverProcesses();
            if (!available.isEmpty()) {
                sb.append("No Camel process is currently selected. ");
                sb.append("Use list_processes to see available processes, then select_process to connect to one. ");
                sb.append("Runtime inspection tools will not work until a process is selected.\n\n");
            }
        }

        if (core) {
            sb.append("You have camel_* tools for the project's files, the catalog and the integration's log, errors");
            sb.append(" and control; get_* tools for its state (context, routes, health, endpoints, properties,");
            sb.append(" history, topology, processors); send_message for a test message; and cli_exec for the other");
            sb.append(" camel commands, such as get or ps.\n\n");
        } else {
            sb.append("You have camel_* tools for the project's files, the catalog and the integration's log, errors");
            sb.append(" and control; get_* and other tools for its runtime state; and the Camel CLI: cli_list_commands");
            sb.append(" and cli_command_help find a command, cli_exec runs it.\n\n");
        }
        sb.append("Some calls ask the user first: a file write (the user sees the change), a message to an endpoint the");
        sb.append(" integration does not have, SQL that changes data, an expression that runs code, stopping or");
        sb.append(" restarting the integration, and a camel command that changes something. A call the user did not");
        sb.append(" confirm did not run.\n\n");

        sb.append("Guidelines:\n");
        AuthoringGuidelines.lines().forEach(line -> sb.append("- ").append(line).append('\n'));
        sb.append("- The answer is shown in a terminal: keep it short\n");
        if (groupGuidance != null && !groupGuidance.isEmpty()) {
            sb.append("\nThe integration:\n");
            groupGuidance.forEach(line -> sb.append("- ").append(line).append('\n'));
        }
        return sb.toString();
    }

    // ---- CLI tools (AskTools-specific) ----

    @SuppressWarnings("unchecked")
    private List<JsonObject> loadCommandMetadata() {
        if (commandMetadataCache != null) {
            return commandMetadataCache;
        }
        try (InputStream is = getClass().getClassLoader()
                .getResourceAsStream("META-INF/camel-jbang-commands-metadata.json")) {
            if (is == null) {
                return List.of();
            }
            String json = IOHelper.loadText(is);
            JsonObject root = (JsonObject) Jsoner.deserialize(json);
            Object commands = root.get("commands");
            if (commands instanceof Collection<?>) {
                commandMetadataCache = ((Collection<Object>) commands).stream()
                        .filter(JsonObject.class::isInstance)
                        .map(JsonObject.class::cast)
                        .toList();
                return commandMetadataCache;
            }
        } catch (Exception e) {
            // ignore
        }
        return List.of();
    }

    private String executeCliListCommands(JsonObject args) {
        String filter = args.getString("filter");
        List<JsonObject> commands = loadCommandMetadata();
        List<JsonObject> result = new ArrayList<>();
        Ask.collectCommands(commands, result, filter);

        JsonObject response = new JsonObject();
        response.put("count", result.size());
        response.put("commands", result);
        return response.toJson();
    }

    @SuppressWarnings("unchecked")
    private String executeCliCommandHelp(JsonObject args) {
        String command = args.getString("command");
        if (command == null || command.isBlank()) {
            return "Error: command name is required";
        }

        List<JsonObject> commands = loadCommandMetadata();
        JsonObject cmd = Ask.findCommand(commands, command);
        if (cmd == null) {
            return "Command not found: " + command + ". Use cli_list_commands to see available commands.";
        }

        JsonObject response = new JsonObject();
        response.put("command", cmd.getString("fullName"));
        response.put("description", cmd.getString("description"));

        Object options = cmd.get("options");
        if (options instanceof Collection<?>) {
            List<JsonObject> opts = ((Collection<Object>) options).stream()
                    .filter(JsonObject.class::isInstance)
                    .map(JsonObject.class::cast)
                    .map(opt -> {
                        JsonObject o = new JsonObject();
                        o.put("names", opt.getString("names"));
                        o.put("description", opt.getString("description"));
                        o.put("type", opt.getString("type"));
                        String dv = opt.getString("defaultValue");
                        if (dv != null) {
                            o.put("defaultValue", dv);
                        }
                        return o;
                    })
                    .toList();
            response.put("options", opts);
        }

        Object subs = cmd.get("subcommands");
        if (subs instanceof Collection<?> subList && !subList.isEmpty()) {
            List<JsonObject> subSummaries = ((Collection<Object>) subList).stream()
                    .filter(JsonObject.class::isInstance)
                    .map(JsonObject.class::cast)
                    .map(sub -> {
                        JsonObject s = new JsonObject();
                        s.put("command", sub.getString("fullName"));
                        s.put("description", sub.getString("description"));
                        return s;
                    })
                    .toList();
            response.put("subcommands", subSummaries);
        }

        return response.toJson();
    }

    private String executeCliExec(JsonObject args) {
        String command = args.getString("command");
        if (command == null || command.isBlank()) {
            return "Error: command is required";
        }

        String[] cmdArgs = Ask.tokenizeCommand(command.trim());
        if (cmdArgs.length > 0 && "camel".equals(cmdArgs[0])) {
            // the command line without camel, as the description says; a model often writes it anyway
            cmdArgs = Arrays.copyOfRange(cmdArgs, 1, cmdArgs.length);
        }
        ToolConfirms.Call call = ToolConfirms.cliCommand(cmdArgs);
        if (!ctx.confirm(call)) {
            return rejected(call);
        }

        picocli.CommandLine commandLine = CamelJBangMain.getCommandLine();
        if (commandLine == null) {
            return "Error: CLI not available";
        }

        StringBuilder captured = new StringBuilder();
        Printer capturingPrinter = new Printer() {
            @Override
            public void println() {
                captured.append('\n');
            }

            @Override
            public void println(String line) {
                captured.append(line).append('\n');
            }

            @Override
            public void print(String output) {
                captured.append(output);
            }

            @Override
            public void printf(String format, Object... fmtArgs) {
                captured.append(String.format(format, fmtArgs));
            }
        };

        CamelJBangMain main = (CamelJBangMain) commandLine.getCommand();
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        synchronized (CamelJBangMain.class) {
            PrintWriter originalOut = commandLine.getOut();
            PrintWriter originalErr = commandLine.getErr();
            Printer originalPrinter = main.getOut();
            commandLine.setOut(pw);
            commandLine.setErr(pw);
            main.setOut(capturingPrinter);
            try {
                int exitCode = commandLine.execute(cmdArgs);
                pw.flush();
                String output = captured.toString() + sw.toString();
                if (output.isBlank() && exitCode != 0) {
                    return "Command exited with code " + exitCode;
                }
                if (output.length() > 32768) {
                    output = output.substring(0, 32768) + "\n... (truncated)";
                }
                return output;
            } catch (Exception e) {
                return "Error executing command: " + e.getMessage();
            } finally {
                main.setOut(originalPrinter);
                commandLine.setOut(originalOut);
                commandLine.setErr(originalErr);
            }
        }
    }

    // ---- JSON schema helpers ----

    public static JsonObject emptyParams() {
        JsonObject schema = new JsonObject();
        schema.put("type", "object");
        schema.put("properties", new JsonObject());
        return schema;
    }

    public static JsonObject objectParams(Map<String, JsonObject> properties) {
        JsonObject props = new JsonObject();
        Map<String, JsonObject> ordered = new LinkedHashMap<>(properties);
        for (Map.Entry<String, JsonObject> entry : ordered.entrySet()) {
            props.put(entry.getKey(), entry.getValue());
        }
        JsonObject schema = new JsonObject();
        schema.put("type", "object");
        schema.put("properties", props);
        return schema;
    }

    public static JsonObject stringProp(String description) {
        JsonObject prop = new JsonObject();
        prop.put("type", "string");
        prop.put("description", description);
        return prop;
    }
}
