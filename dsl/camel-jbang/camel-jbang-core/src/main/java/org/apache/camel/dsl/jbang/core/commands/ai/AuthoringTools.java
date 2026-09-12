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
package org.apache.camel.dsl.jbang.core.commands.ai;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.apache.camel.dsl.jbang.core.common.RuntimeHelper;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

import static org.apache.camel.dsl.jbang.core.commands.ai.ToolDescriptor.tool;

/**
 * The neutral set of Camel authoring tools for AI agents, defined once and exposed by every Camel MCP server
 * ({@code camel mcp} and {@code camel tui --mcp}) under the same {@code camel_} names, so an agent gets the same Camel
 * through either door: catalog documentation with the URI rules and the simple syntax, source validation, reading and
 * writing the source files of a project, running an integration in dev mode and reading its log and errors, evaluating
 * an expression and diagnosing an error.
 * <p>
 * The tools are self-contained: a file tool takes the project {@code directory} as an argument (a server that has a
 * selected integration fills it in through {@link ToolContext#setDefaultDirectory}), and a runtime tool takes the
 * integration {@code name} (or uses the selected or the only running one). Writing a file is validated first and
 * refused when the content is invalid; confirming a write with a human is the job of the client or of the TUI, not of
 * these tools.
 */
public final class AuthoringTools {

    static final String NAME_DESC = "Integration name or pid (default: the selected one, or the only one running)";
    static final String VERSION_DESC = "Camel version to answer for (default: the CLI's own, or the selected integration's)";
    static final String DIRECTORY_DESC = "Project directory with the source files (default: the selected integration's)";

    /** Files listed and read by the file tools; more than that and a directory is not an integration's sources. */
    private static final int MAX_FILES = 99;

    private AuthoringTools() {
    }

    /** Registers the authoring tools; called once by the {@link ToolRegistry}. */
    static void register(Consumer<ToolDescriptor> registry) {
        registry.accept(tool("camel_catalog_doc",
                "Camel catalog documentation of a component, data format, language or EIP (description, options, "
                                                  + "Maven coordinates), with the URI rules of a component. For simple also its "
                                                  + "syntax rules, functions and operators: count and names by group, or with "
                                                  + "optionsFilter the matching ones with parameters and examples. endpoint "
                                                  + "validates a URI: unknown or invalid options, missing path.")
                .param("name", "string", "Name, e.g. kafka, json-jackson, simple, timer, choice, split", false)
                .param("endpoint", "string", "Endpoint URI to check, e.g. kafka:orders?brokers=host:9092", false)
                .param("kind", "string", "component, dataformat, language or eip (auto-detected)", false)
                .param("includeOptions", "boolean", "Include the options (default true)", false)
                .param("includeDoc", "boolean", "Include the full AsciiDoc page (default false)", false)
                .param("docPage", "string", "A language doc sub-page (simple: functions, operators, ognl, advanced)"
                                            + " to return as text",
                        false)
                .param("optionsFilter", "string", "Keyword to match in option names or descriptions", false)
                .param("camelVersion", "string", VERSION_DESC, false)
                .core(true)
                .executor((ctx, args) -> {
                    applyVersion(ctx, args);
                    return CatalogDocs.catalogDoc(ctx.catalog(), args.get("name"), args.get("endpoint"),
                            args.get("kind"), args.get("optionsFilter"), bool(args, "includeOptions", true),
                            bool(args, "includeDoc", false), args.get("docPage")).toJson();
                }));

        registry.accept(tool("camel_catalog_find",
                "Finds Camel components, data formats and languages by a protocol, product or other term that is not "
                                                   + "the exact name (mqtt, s3, snowflake, csv): best match first with title and "
                                                   + "description. camel_catalog_doc then gives the options of one.")
                .param("term", "string", "What to look for, e.g. mqtt, s3, database, csv", true)
                .param("kind", "string", "component, dataformat or language (default: all)", false)
                .param("limit", "integer", "Maximum matches per kind (default 10)", false)
                .param("camelVersion", "string", VERSION_DESC, false)
                .executor((ctx, args) -> {
                    applyVersion(ctx, args);
                    return CatalogDocs.find(ctx.catalog(), args.get("term"), args.get("kind"),
                            integer(args, "limit", 10)).toJson();
                }));

        registry.accept(tool("camel_validate_source",
                "Validates Camel YAML DSL or .properties source without writing: schema (misspelled options such as "
                                                      + "logLevel instead of loggingLevel), endpoint URIs, simple expressions, "
                                                      + "camel.* options. Use on content before writing it, or on an existing "
                                                      + "file (no content) to explain a reload error.")
                .param("directory", "string", DIRECTORY_DESC, false)
                .param("file", "string", "File name; picks the checks by extension, read when no content", true)
                .param("content", "string", "The source to validate", false)
                .param("camelVersion", "string", VERSION_DESC, false)
                .core(true)
                .executor((ctx, args) -> {
                    applyVersion(ctx, args);
                    String file = required(args, "file");
                    String content = args.get("content");
                    if (content == null) {
                        Path path = resolveFile(ctx.resolveDirectory(args.get("directory")), file);
                        if (!Files.isRegularFile(path)) {
                            throw new ToolExecutionException("No such file in the directory: " + file);
                        }
                        content = read(path);
                    }
                    return validate(ctx, file, content).toJson();
                }));

        registry.accept(tool("camel_get_files",
                "The source files of a project directory: without file the list (name, size, type), with file its "
                                                + "content. Use before editing to see the routes, configuration and other "
                                                + "files of the integration.")
                .param("directory", "string", DIRECTORY_DESC, false)
                .param("file", "string", "File name to read; omitted lists the files", false)
                .core(true)
                .executor((ctx, args) -> {
                    Path dir = ctx.resolveDirectory(args.get("directory"));
                    String file = args.get("file");
                    if (file != null && !file.isBlank()) {
                        return readFile(dir, file).toJson();
                    }
                    return listFiles(dir).toJson();
                }));

        registry.accept(tool("camel_write_file",
                "Writes the complete content of a file in the project directory. YAML and .properties content is "
                                                 + "validated first; invalid content is not written and the errors are returned. "
                                                 + "An integration running in dev mode reloads the change, otherwise restart it "
                                                 + "with camel_control.")
                .param("directory", "string", DIRECTORY_DESC, false)
                .param("file", "string", "File name, no path", true)
                .param("content", "string", "The complete new content", true)
                .param("validate", "boolean", "Validate before writing (default true)", false)
                .param("camelVersion", "string", VERSION_DESC, false)
                .readOnly(false)
                .core(true)
                .executor((ctx, args) -> {
                    applyVersion(ctx, args);
                    Path dir = ctx.resolveDirectory(args.get("directory"));
                    return writeFile(ctx, dir, required(args, "file"), required(args, "content"),
                            bool(args, "validate", true)).toJson();
                }));

        registry.accept(tool("camel_run",
                "Starts an integration from a project directory with camel run in a separate process, in dev mode by "
                                          + "default (route files reload when written). Returns the pid and log file once it is "
                                          + "up; camel_get_log and camel_get_errors then tell how it does, camel_control stops it.")
                .param("directory", "string", "Project directory to run in", true)
                .param("files", "string", "Source files to run, comma-separated (default: every route file in the"
                                          + " directory)",
                        false)
                .param("name", "string", "Integration name (default: from the first file)", false)
                .param("dev", "boolean", "Dev mode with reload on file change (default true)", false)
                .readOnly(false)
                .executor((ctx, args) -> {
                    Path dir = ctx.resolveDirectory(args.get("directory"));
                    List<String> files = new ArrayList<>();
                    String list = args.get("files");
                    if (list != null && !list.isBlank()) {
                        for (String f : list.split(",")) {
                            if (!f.isBlank()) {
                                files.add(f.trim());
                            }
                        }
                    }
                    JsonObject result = IntegrationLauncher.run(dir, files, args.get("name"), bool(args, "dev", true),
                            List.of());
                    if (result.get("pid") instanceof Long pid) {
                        ctx.selectProcess(pid);
                    }
                    return result.toJson();
                }));

        registry.accept(tool("camel_control",
                "Controls a running integration: stop (graceful), kill, restart (picks up edited files without dev "
                                              + "mode), stop-routes, start-routes, reset-stats (clears statistics, routes "
                                              + "untouched). Never stop, kill or restart unless the user asked for it.")
                .param("action", "string", "stop, kill, restart, stop-routes, start-routes or reset-stats", true)
                .param("name", "string", NAME_DESC, false)
                .readOnly(false)
                .destructive(true)
                .core(true)
                .executor((ctx, args) -> {
                    selectProcess(ctx, args);
                    return IntegrationLauncher.control(ctx, required(args, "action"));
                }));

        registry.accept(tool("camel_get_log",
                "Recent log records of a running integration, newest first, with optional filtering; a stack trace "
                                              + "comes as one record with a detail block.")
                .param("name", "string", NAME_DESC, false)
                .param("limit", "integer", "Maximum records to return (default 50)", false)
                .param("filter", "string", "Case-insensitive substring filter on the message", false)
                .param("level", "string", "Only this log level (INFO, WARN, ERROR, DEBUG, TRACE)", false)
                .core(true)
                .executor((ctx, args) -> {
                    RuntimeHelper.ProcessInfo p = selectProcess(ctx, args);
                    return LogFileReader.read(ctx.pid(), p != null ? p.name() : null, integer(args, "limit", 50),
                            args.get("filter"), args.get("level")).toJson();
                }));

        registry.accept(tool("camel_get_errors",
                "The failed exchanges of a running integration: routeId, exchangeId, exception with stack trace, "
                                                 + "body and headers.")
                .param("name", "string", NAME_DESC, false)
                .core(true)
                .executor((ctx, args) -> {
                    selectProcess(ctx, args);
                    JsonObject errors = ctx.readErrorFile();
                    return errors != null ? errors.toJson() : "No errors captured.";
                }));

        registry.accept(tool("camel_eval_expression",
                "Evaluates an expression: in the running integration when there is one, else locally. Returns the "
                                                      + "value (true/false for a predicate) or the syntax error, so check simple "
                                                      + "before answering or writing it.")
                .param("expression", "string", "e.g. ${random(1,10)} or ${body} ?: 'none'", true)
                .param("language", "string", "simple (default), jsonpath, xpath, jq", false)
                .param("body", "string", "Message body", false)
                .param("name", "string", NAME_DESC, false)
                .core(true)
                .executor((ctx, args) -> {
                    String name = args.get("name");
                    if (name != null && !name.isBlank()) {
                        ctx.selectProcess(name);
                    } else {
                        ctx.selectSingleProcessIfNone();
                    }
                    return ExpressionEvaluator.evaluate(ctx, args.get("language"), required(args, "expression"),
                            args.get("body")).toJson();
                }));

        registry.accept(tool("camel_error_diagnose",
                "Diagnoses a Camel error from a stack trace or error message: the known exceptions in it with common "
                                                     + "causes and suggested fixes, the components and EIPs it mentions with "
                                                     + "documentation links, and the route id.")
                .param("error", "string", "The stack trace or error message", true)
                .param("camelVersion", "string", VERSION_DESC, false)
                .core(true)
                .executor((ctx, args) -> {
                    applyVersion(ctx, args);
                    return ErrorDiagnoser.diagnose(required(args, "error"), ctx.catalog()).toJson();
                }));
    }

    // ---- shared logic, also used by the TUI over its own selection ----

    /** Validates source content for the context's Camel version, as {@code camel_validate_source} answers it. */
    public static JsonObject validate(ToolContext ctx, String file, String content) {
        if (!SourceValidator.isValidatableFile(file)) {
            throw new ToolExecutionException(
                    "No validation for " + file + ": only YAML routes and .properties files are validated");
        }
        List<String> errors = SourceValidator.validate(file, content, ctx.catalog(), ctx.propertyLineValidator());
        JsonObject result = new JsonObject();
        result.put("valid", errors.isEmpty());
        result.put("file", file);
        result.put("errors", new JsonArray(errors));
        result.put("message", errors.isEmpty()
                ? "The source is valid"
                : errors.size() + " problem(s) found; fix them before writing the file");
        return result;
    }

    /** Writes a file after validating it, as {@code camel_write_file} does; no confirmation is asked here. */
    public static JsonObject writeFile(ToolContext ctx, Path dir, String file, String content, boolean validate) {
        Path path = resolveFile(dir, file);
        boolean exists = Files.exists(path);
        if (exists && !Files.isRegularFile(path)) {
            throw new ToolExecutionException(file + " is not a regular file");
        }
        if (validate && SourceValidator.isValidatableFile(file)) {
            List<String> errors = SourceValidator.validate(file, content, ctx.catalog(), ctx.propertyLineValidator());
            if (!errors.isEmpty()) {
                JsonObject result = new JsonObject();
                result.put("status", "invalid");
                result.put("file", file);
                result.put("errors", new JsonArray(errors));
                result.put("message", "The file was not written: the content has validation errors. Fix them and"
                                      + " call camel_write_file again (validate=false writes it anyway).");
                return result;
            }
        }
        try {
            Files.writeString(path, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ToolExecutionException("Failed to write " + path + ": " + e.getMessage());
        }
        JsonObject result = new JsonObject();
        result.put("status", exists ? "overwritten" : "created");
        result.put("file", file);
        result.put("directory", dir.toString());
        result.put("lines", content.isEmpty() ? 0 : (int) content.lines().count());
        result.put("bytes", content.getBytes(StandardCharsets.UTF_8).length);
        result.put("message", "An integration running the file in dev mode reloads it now; otherwise restart the"
                              + " integration for the change to take effect.");
        return result;
    }

    /** The files of a project directory, as {@code camel_get_files} lists them. */
    public static JsonObject listFiles(Path dir) {
        JsonArray files = new JsonArray();
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(Files::isRegularFile)
                    .sorted((a, b) -> a.getFileName().toString().compareToIgnoreCase(b.getFileName().toString()))
                    .limit(MAX_FILES)
                    .forEach(p -> {
                        JsonObject entry = new JsonObject();
                        entry.put("name", p.getFileName().toString());
                        entry.put("size", formatSize(size(p)));
                        entry.put("type", fileType(p.getFileName().toString()));
                        files.add(entry);
                    });
        } catch (IOException e) {
            throw new ToolExecutionException("Cannot list " + dir + ": " + e.getMessage());
        }
        JsonObject result = new JsonObject();
        result.put("directory", dir.toString());
        result.put("files", files);
        result.put("totalFiles", files.size());
        if (files.isEmpty()) {
            result.put("message", "The directory has no files");
        }
        return result;
    }

    /** One file of a project directory with its content, as {@code camel_get_files} reads it. */
    public static JsonObject readFile(Path dir, String file) {
        Path path = resolveFile(dir, file);
        if (!Files.isRegularFile(path)) {
            throw new ToolExecutionException("No such file in the directory: " + file);
        }
        JsonObject result = new JsonObject();
        result.put("file", file);
        result.put("directory", dir.toString());
        result.put("size", formatSize(size(path)));
        result.put("type", fileType(file));
        result.put("content", read(path));
        return result;
    }

    /** A plain file name inside the directory; anything else (a path, a parent reference) is refused. */
    static Path resolveFile(Path dir, String file) {
        if (file == null || file.isBlank()) {
            throw new ToolExecutionException("file is required");
        }
        Path path = dir.resolve(file).normalize();
        if (!path.startsWith(dir) || !dir.equals(path.getParent())) {
            throw new ToolExecutionException("file must be a plain file name in the directory " + dir);
        }
        return path;
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ToolExecutionException("Cannot read " + path + ": " + e.getMessage());
        }
    }

    private static long size(Path p) {
        try {
            return Files.size(p);
        } catch (IOException e) {
            return 0;
        }
    }

    static String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0);
        }
        return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024));
    }

    static String fileType(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        int dot = lower.lastIndexOf('.');
        if (dot < 0) {
            return "other";
        }
        return switch (lower.substring(dot + 1)) {
            case "yaml", "yml" -> "yaml";
            case "xml" -> "xml";
            case "java" -> "java";
            case "properties" -> "properties";
            case "groovy", "js", "kts", "py" -> "script";
            case "json" -> "json";
            case "md", "adoc", "txt" -> "text";
            default -> "other";
        };
    }

    // ---- argument helpers ----

    private static void applyVersion(ToolContext ctx, Map<String, String> args) {
        String version = args.get("camelVersion");
        if (version != null && !version.isBlank()) {
            ctx.setCamelVersion(version.trim());
        }
    }

    /**
     * Selects the process the call names, else keeps the selected one, else the only one running.
     *
     * @throws ToolExecutionException when no process can be selected
     */
    private static RuntimeHelper.ProcessInfo selectProcess(ToolContext ctx, Map<String, String> args) {
        String name = args.get("name");
        if (name != null && !name.isBlank()) {
            ctx.selectProcess(name);
        } else if (!ctx.selectSingleProcessIfNone()) {
            throw new ToolExecutionException(
                    "No integration selected: pass name (an integration name or pid); list_processes shows"
                                             + " what runs, camel_run starts one");
        }
        for (RuntimeHelper.ProcessInfo p : RuntimeHelper.discoverProcesses()) {
            if (p.pid() == ctx.pid()) {
                return p;
            }
        }
        return null;
    }

    private static String required(Map<String, String> args, String key) {
        String value = args.get(key);
        if (value == null || value.isBlank()) {
            throw new ToolExecutionException(key + " is required");
        }
        return value;
    }

    static boolean bool(Map<String, String> args, String key, boolean defaultValue) {
        String value = args.get(key);
        return value == null || value.isBlank() ? defaultValue : Boolean.parseBoolean(value.trim());
    }

    static int integer(Map<String, String> args, String key, int defaultValue) {
        String value = args.get(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
