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

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.apache.camel.dsl.jbang.core.commands.ai.ToolContext;
import org.apache.camel.dsl.jbang.core.commands.ai.ToolDescriptor;
import org.apache.camel.dsl.jbang.core.commands.ai.ToolRegistry;
import org.apache.camel.dsl.jbang.core.common.Printer;
import org.apache.camel.dsl.jbang.core.common.RuntimeHelper;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

/**
 * Shared tool definitions and execution logic for the Camel AI assistant. Used by both the {@code camel ask} CLI
 * command and the TUI AI panel.
 *
 * <p>
 * Delegates shared tool execution (process discovery, runtime inspection, catalog, examples) to
 * {@link ToolRegistry}/{@link ToolContext}. CLI-specific and file-system tools remain here because they depend on
 * {@link CamelJBangMain} and direct filesystem access.
 */
public class AskTools {

    private long targetPid;
    private final ToolContext ctx;
    private volatile List<JsonObject> commandMetadataCache;

    public AskTools(long targetPid) {
        this.targetPid = targetPid;
        this.ctx = new ToolContext();
        if (targetPid >= 0) {
            ctx.selectProcess(targetPid);
        }
    }

    public long getTargetPid() {
        return targetPid;
    }

    public void setTargetPid(long targetPid) {
        this.targetPid = targetPid;
        if (targetPid >= 0) {
            ctx.selectProcess(targetPid);
        }
    }

    // ---- Tool definitions ----

    public List<LlmClient.ToolDef> buildToolDefinitions() {
        List<LlmClient.ToolDef> tools = new ArrayList<>();

        // Build definitions from the shared registry
        for (ToolDescriptor td : ToolRegistry.allTools()) {
            JsonObject params;
            if (td.params().isEmpty()) {
                params = emptyParams();
            } else {
                Map<String, JsonObject> props = new LinkedHashMap<>();
                for (ToolDescriptor.Param p : td.params()) {
                    props.put(p.name(), stringProp(p.description()));
                }
                params = objectParams(props);
            }
            tools.add(new LlmClient.ToolDef(td.name(), td.description(), params));
        }

        // CLI tools (AskTools-specific, depend on CamelJBangMain)
        tools.add(new LlmClient.ToolDef(
                "cli_list_commands",
                "List available Camel CLI commands. Returns command names and descriptions. Use filter to narrow results.",
                objectParams(Map.of(
                        "filter", stringProp("Filter by command name or description (case-insensitive substring)")))));
        tools.add(new LlmClient.ToolDef(
                "cli_command_help",
                "Get detailed help for a specific Camel CLI command, including all options with types and defaults.",
                objectParams(Map.of(
                        "command", stringProp("Full command name (e.g., 'get error', 'catalog component', 'run')")))));
        tools.add(new LlmClient.ToolDef(
                "cli_exec",
                "Execute any Camel CLI command and return its output. Use cli_list_commands and cli_command_help first to discover commands and their options. CAUTION: some commands (stop, cmd stop-route, cmd stop-group) are destructive and will affect running integrations. Always confirm with the user before executing destructive commands.",
                objectParams(Map.of(
                        "command", stringProp(
                                "The full command line to execute (e.g., 'get error --diagram', 'catalog component --filter=kafka')")))));

        // File tools (AskTools-specific, depend on filesystem access)
        tools.add(new LlmClient.ToolDef(
                "list_files",
                "List files in a directory (up to 2 levels deep). Defaults to current working directory.",
                objectParams(Map.of(
                        "path", stringProp("Directory path relative to CWD (default: current directory)")))));
        tools.add(new LlmClient.ToolDef(
                "read_file",
                "Read the content of a file. Useful for inspecting route definitions, configuration, and properties files.",
                objectParams(Map.of(
                        "file", stringProp("File path relative to CWD")))));
        tools.add(new LlmClient.ToolDef(
                "write_file",
                "Write content to a file. Creates parent directories if needed. Use for creating or editing route definitions and configuration files.",
                objectParams(Map.of(
                        "file", stringProp("File path relative to CWD"),
                        "content", stringProp("The content to write to the file")))));

        return tools;
    }

    // ---- Tool execution ----

    public String executeTool(String name, JsonObject args) {
        try {
            // Try shared registry first
            ToolDescriptor td = ToolRegistry.findTool(name);
            if (td != null) {
                // Convert JsonObject args to Map<String, String> for the registry
                Map<String, String> argMap = new LinkedHashMap<>();
                if (args != null) {
                    for (String key : args.keySet()) {
                        Object val = args.get(key);
                        if (val != null) {
                            argMap.put(key, val.toString());
                        }
                    }
                }

                // Special handling: select_process must also update our targetPid
                Object result = ToolRegistry.execute(name, ctx, argMap);
                if ("select_process".equals(name) && ctx.hasProcess()) {
                    this.targetPid = ctx.pid();
                }

                return result != null ? result.toString() : "";
            }

            // Fall through to CLI and file tools
            return switch (name) {
                case "cli_list_commands" -> executeCliListCommands(args);
                case "cli_command_help" -> executeCliCommandHelp(args);
                case "cli_exec" -> executeCliExec(args);
                case "list_files" -> executeListFiles(args);
                case "read_file" -> executeReadFile(args);
                case "write_file" -> executeWriteFile(args);
                default -> "Unknown tool: " + name;
            };
        } catch (Exception e) {
            return "Error executing " + name + ": " + e.getMessage();
        }
    }

    // ---- System prompt ----

    public static String buildSystemPrompt(long targetPid, String processName) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are an Apache Camel assistant. ");
        sb.append("You help users build, understand, and troubleshoot Camel applications.\n\n");

        if (targetPid >= 0 && processName != null) {
            sb.append("You are connected to a running Camel application: ");
            sb.append(processName).append(" (PID ").append(targetPid).append("). ");
            sb.append("Use the runtime inspection tools to gather information about it.\n\n");
        } else {
            List<RuntimeHelper.ProcessInfo> available = RuntimeHelper.discoverProcesses();
            if (!available.isEmpty()) {
                sb.append("No Camel process is currently selected. ");
                sb.append("Use list_processes to see available processes, then select_process to connect to one. ");
                sb.append("Runtime inspection tools will not work until a process is selected.\n\n");
            }
        }

        sb.append("You can search the Camel catalog (components, EIPs), browse examples, ");
        sb.append("read/write files, and execute any Camel CLI command.\n\n");
        sb.append("For CLI commands beyond the built-in tools, use cli_list_commands to discover ");
        sb.append("available commands, cli_command_help to see options, and cli_exec to run them.\n\n");
        sb.append("Guidelines:\n");
        sb.append("- When creating routes, use YAML DSL format (Camel's recommended format for JBang)\n");
        sb.append("- Look at existing files first with list_files/read_file before creating new ones\n");
        sb.append("- Use catalog tools to look up component syntax before writing routes\n");
        sb.append("- Use examples as reference when building new routes\n");
        sb.append("- Be concise and actionable in your answers\n");
        sb.append("- Format output as plain text for terminal display, do not use markdown\n");
        if (targetPid >= 0) {
            sb.append("- Start by gathering relevant information using the available runtime tools\n");
            sb.append("- If something looks wrong, explain what it means and suggest fixes\n");
            sb.append("- To stop routes or the application, always use the provided tools ");
            sb.append("(stop_route, stop_application) for graceful shutdown. Never suggest kill or kill -9.\n");
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

        picocli.CommandLine commandLine = CamelJBangMain.getCommandLine();
        if (commandLine == null) {
            return "Error: CLI not available";
        }

        String[] cmdArgs = Ask.tokenizeCommand(command.trim());

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

    // ---- File tools (AskTools-specific) ----

    private String executeListFiles(JsonObject args) throws IOException {
        String pathStr = args.getString("path");
        Path cwd = Path.of("").toAbsolutePath().normalize();
        Path base = cwd.resolve(pathStr != null && !pathStr.isBlank() ? pathStr : ".").normalize();

        if (!base.startsWith(cwd)) {
            return "Error: path must be within the current working directory";
        }
        if (!Files.isDirectory(base)) {
            return "Error: not a directory: " + cwd.relativize(base);
        }

        try (Stream<Path> stream = Files.walk(base, 2)) {
            List<String> files = stream
                    .filter(p -> !p.equals(base))
                    .map(p -> cwd.relativize(p).toString() + (Files.isDirectory(p) ? "/" : ""))
                    .sorted()
                    .toList();

            JsonObject response = new JsonObject();
            response.put("directory", base.equals(cwd) ? "." : cwd.relativize(base).toString());
            response.put("count", files.size());
            response.put("files", files);
            return response.toJson();
        }
    }

    private String executeReadFile(JsonObject args) throws IOException {
        String fileStr = args.getString("file");
        if (fileStr == null || fileStr.isBlank()) {
            return "Error: file path is required";
        }

        Path cwd = Path.of("").toAbsolutePath().normalize();
        Path filePath = cwd.resolve(fileStr).normalize();

        if (!filePath.startsWith(cwd)) {
            return "Error: file path must be within the current working directory";
        }
        if (!Files.exists(filePath)) {
            return "File not found: " + cwd.relativize(filePath);
        }

        String content = Files.readString(filePath);
        if (content.length() > 32768) {
            content = content.substring(0, 32768) + "\n... (truncated, file is " + content.length() + " bytes)";
        }
        return content;
    }

    private String executeWriteFile(JsonObject args) throws IOException {
        String fileStr = args.getString("file");
        String content = args.getString("content");
        if (fileStr == null || fileStr.isBlank()) {
            return "Error: file path is required";
        }
        if (content == null) {
            return "Error: content is required";
        }

        Path cwd = Path.of("").toAbsolutePath().normalize();
        Path filePath = cwd.resolve(fileStr).normalize();

        if (!filePath.startsWith(cwd)) {
            return "Error: file path must be within the current working directory";
        }

        Files.createDirectories(filePath.getParent());
        Files.writeString(filePath, content);
        return "File written: " + cwd.relativize(filePath);
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
