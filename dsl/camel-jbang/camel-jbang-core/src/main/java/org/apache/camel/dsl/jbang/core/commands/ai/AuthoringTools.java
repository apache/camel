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
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;

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
    static final String FILE_PATH_DESC = "File path relative to the directory, e.g. src/main/resources/camel/foo.camel.yaml";
    private static final int MAX_FILES = 99;
    /** How many files a listing looks at before it stops; the route and configuration files are found among them. */
    private static final int SCAN_LIMIT = 2000;
    private static final int MAX_DEPTH = 8;
    /** Build output, tooling and VCS directories: never sources. */
    private static final Set<String> SKIPPED_DIRS = Set.of(
            "target", "build", "out", "node_modules", ".git", ".mvn", ".idea", ".vscode", ".gradle", ".settings",
            ".camel-jbang");
    private static final Pattern YAML_ROUTE = Pattern.compile(
            "(?m)^\\s*-\\s*(route|from|rest|routeTemplate|route-template|templatedRoute|templated-route"
                                                              + "|routeConfiguration|route-configuration|kamelet)\\s*:");

    private AuthoringTools() {
    }

    /** Registers the authoring tools; called once by the {@link ToolRegistry}. */
    static void register(Consumer<ToolDescriptor> registry) {
        registry.accept(tool("camel_catalog_doc",
                "Catalog documentation of a component, data format, language, EIP, built-in bean or the Java API: description, options, Maven coordinates, the URI rules of a component; for simple its functions and operators (optionsFilter narrows them). endpoint validates a URI.")
                .param("name", "string", "Name, e.g. kafka, json-jackson, simple, timer, choice, split, Exchange", false)
                .param("endpoint", "string", "Endpoint URI to check, e.g. kafka:orders?brokers=host:9092", false)
                .param("kind", "string",
                        "component, dataformat, language, eip, bean or api (auto-detected; a bean is a built-in class such as StringAggregationStrategy, with how to declare and use it; api is the Java API to call from a bean or script before writing it: Exchange, Message, CamelContext, Registry, ProducerTemplate, Processor, AggregationStrategy, Predicate, Expression, TypeConverter, or the variables of groovy, js, python, java scripts)",
                        false)
                .param("includeOptions", "string",
                        "common (default: no deprecated or advanced), required, all or false", false)
                .param("includeHeaders", "boolean", "Include the message headers of a component (default false)", false)
                .param("includeDoc", "boolean", "Include the full AsciiDoc page (default false)", false)
                .param("docPage", "string", "simple doc sub-page to return as text (functions, operators, ognl, advanced)",
                        false)
                .param("optionsFilter", "string", "Keyword to match in option names or descriptions", false)
                .param("camelVersion", "string", VERSION_DESC, false)
                .core(true)
                .executor((ctx, args) -> {
                    applyVersion(ctx, args);
                    return CatalogDocs.catalogDoc(ctx.catalog(), args.get("name"), args.get("endpoint"),
                            args.get("kind"), args.get("optionsFilter"), args.get("includeOptions"),
                            bool(args, "includeHeaders", false), bool(args, "includeDoc", false),
                            args.get("docPage")).toJson();
                }));

        registry.accept(tool("camel_catalog_find",
                "Finds Camel components, data formats, languages and EIPs by a protocol, product, alias or other term "
                                                   + "that is not the exact name (mqtt, s3, snowflake, csv, fan-out, dedup): best "
                                                   + "match first with title and description. camel_catalog_doc then gives the "
                                                   + "options of one.")
                .param("term", "string", "What to look for, e.g. mqtt, s3, database, csv, fan-out", true)
                .param("kind", "string",
                        "component, dataformat, language, eip or bean (default: all); bean with an interface name such as AggregationStrategy lists the built-in implementations",
                        false)
                .param("limit", "integer", "Maximum matches per kind (default 10)", false)
                .param("camelVersion", "string", VERSION_DESC, false)
                .core(true)
                .executor((ctx, args) -> {
                    applyVersion(ctx, args);
                    return CatalogDocs.find(ctx.catalog(), args.get("term"), args.get("kind"),
                            integer(args, "limit", 10)).toJson();
                }));

        registry.accept(tool("camel_catalog_sample",
                "A validated YAML DSL sample of an EIP or file entry (onException, aggregate, split, rest, beans), a "
                                                     + "component (kafka, file), a data format (csv) or a language (jq) from the "
                                                     + "docs, with where it goes (a top-level entry, a step, an endpoint uri, a "
                                                     + "marshal step, an expression). Use before writing one the first time or "
                                                     + "after a 'not defined in the schema' error.")
                .param("name", "string",
                        "EIP, component, data format or language name, or what to do (read file, call service, retry, batch)",
                        true)
                .param("kind", "string", "eip, component, dataformat or language; needed only when a name is in several "
                                         + "(avro, file)",
                        false)
                .param("limit", "integer", "Maximum samples to return (default 2, max 5)", false)
                .param("camelVersion", "string", VERSION_DESC, false)
                .core(true)
                .executor((ctx, args) -> {
                    applyVersion(ctx, args);
                    return CatalogSamples.sample(ctx.catalog(), args.get("kind"), args.get("name"),
                            integer(args, "limit", CatalogSamples.DEFAULT_LIMIT));
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
                    String directory = args.get("directory");
                    // the directory is needed to read the file, and used for the bean reference check when given
                    Path dir = content == null || (directory != null && !directory.isBlank())
                            ? ctx.resolveDirectory(directory) : null;
                    if (content == null) {
                        Path path = resolveFile(dir, file);
                        if (!Files.isRegularFile(path)) {
                            throw new ToolExecutionException("No such file in the directory: " + file);
                        }
                        content = read(path);
                    }
                    return validate(ctx, dir, file, content).toJson();
                }));

        registry.accept(tool("camel_get_files",
                "The source files of a project directory, subdirectories included: without file the list, with "
                                                + "the route and configuration files named first (routeFiles, configFiles) "
                                                + "and, for a running integration, which file and line each route comes "
                                                + "from; with file (a path relative to the directory, as listed) its content.")
                .param("directory", "string", DIRECTORY_DESC, false)
                .param("file", "string", FILE_PATH_DESC + " to read; omitted lists the files", false)
                .core(true)
                .executor((ctx, args) -> {
                    Path dir = ctx.resolveDirectory(args.get("directory"));
                    String file = args.get("file");
                    if (file != null && !file.isBlank()) {
                        return readFile(dir, file).toJson();
                    }
                    JsonObject result = listFiles(dir);
                    if (ctx.hasProcess()) {
                        // the selected integration's routes, when they come from this directory
                        JsonObject status = ctx.readFullStatus();
                        JsonArray routes = routeSources(
                                status != null && status.get("routes") instanceof Collection<?> c ? c : null, dir);
                        if (routes.stream().anyMatch(r -> !((JsonObject) r).containsKey("missing"))) {
                            result.put("routes", routes);
                        }
                    }
                    return result.toJson();
                }));

        registry.accept(tool("camel_write_file",
                "Writes the complete content of a file in the project directory. YAML and .properties content is "
                                                 + "validated first; invalid content is not written and the errors are returned. "
                                                 + "An integration running in dev mode reloads the change, otherwise restart it "
                                                 + "with camel_control.")
                .param("directory", "string", DIRECTORY_DESC, false)
                .param("file", "string", FILE_PATH_DESC + " (subdirectories are created)", true)
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
                "Starts an integration with camel run in a separate process, in dev mode by default (files reload when written). Returns the pid and log file; camel_get_log, camel_get_errors and camel_control follow it.")
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

        registry.accept(tool("camel_dependency_for_class",
                "Which Maven dependency provides a class, and how to declare it: the known dependencies camel run "
                                                           + "downloads by itself (nothing to declare there), a Camel component's "
                                                           + "artifact per runtime, or with mavenCentral=true a Maven Central search "
                                                           + "by class name (a guess, marked as such). Answers camel.jbang.dependencies, "
                                                           + "--dep, and the pom.xml dependency for Camel Main, Spring Boot and Quarkus.")
                .param("className", "string", "Fully qualified class name, e.g. org.postgresql.ds.PGSimpleDataSource",
                        true)
                .param("runtime", "string", "main, spring-boot or quarkus (default: all three pom forms)", false)
                .param("mavenCentral", "boolean",
                        "Search Maven Central when the class is not in the known dependencies (default false; needs network, can take up to 40 s)",
                        false)
                .param("camelVersion", "string", VERSION_DESC, false)
                .executor((ctx, args) -> {
                    applyVersion(ctx, args);
                    return DependencyLookup.lookup(ctx, required(args, "className"), args.get("runtime"),
                            bool(args, "mavenCentral", false));
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
        return validate(ctx, null, file, content);
    }

    /** As {@link #validate(ToolContext, String, String)}, with the directory for the bean reference check. */
    public static JsonObject validate(ToolContext ctx, Path dir, String file, String content) {
        if (!SourceValidator.isValidatableFile(file)) {
            throw new ToolExecutionException(
                    "No validation for " + file + ": YAML routes, .properties, .java, .xsl and .xml files are validated");
        }
        List<String> errors = SourceValidator.validate(file, content, ctx.catalog(), ctx.propertyLineValidator(), dir);
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
            List<String> errors = SourceValidator.validate(file, content, ctx.catalog(), ctx.propertyLineValidator(), dir);
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
            Files.createDirectories(path.getParent());
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

    /**
     * The files of a project directory, subdirectories included, as {@code camel_get_files} lists them. A human answers
     * "which file has the route" with one {@code ls -R}; this gives a model the same in one call: the layout
     * ({@code maven} when the directory is a Maven project, else {@code flat}), the route files and the configuration
     * files named up front, then every file with its path relative to the directory (build output and tooling
     * directories skipped), capped at {@value #MAX_FILES} entries.
     */
    public static JsonObject listFiles(Path dir) {
        List<Path> all = projectFiles(dir);
        boolean maven = Files.isRegularFile(dir.resolve("pom.xml")) && Files.isDirectory(dir.resolve("src/main"));
        JsonArray routeFiles = new JsonArray();
        JsonArray configFiles = new JsonArray();
        JsonArray files = new JsonArray();
        for (Path p : all) {
            String rel = relativePath(dir, p);
            String type = fileType(rel);
            String kind = fileKind(p, rel, type);
            if ("route".equals(kind)) {
                routeFiles.add(rel);
            } else if ("config".equals(kind)) {
                configFiles.add(rel);
            }
            if (files.size() < MAX_FILES) {
                JsonObject entry = new JsonObject();
                entry.put("name", rel);
                entry.put("size", formatSize(size(p)));
                entry.put("type", type);
                if (kind != null) {
                    entry.put("kind", kind);
                }
                files.add(entry);
            }
        }
        JsonObject result = new JsonObject();
        result.put("directory", dir.toString());
        result.put("layout", maven ? "maven" : "flat");
        result.put("routeFiles", routeFiles);
        result.put("configFiles", configFiles);
        result.put("files", files);
        result.put("totalFiles", all.size());
        if (all.isEmpty()) {
            result.put("message", "The directory has no files");
        } else if (all.size() > MAX_FILES) {
            result.put("message", "Listing the first " + MAX_FILES + " of " + all.size()
                                  + " files; routeFiles and configFiles name every route and configuration file");
        }
        if (maven) {
            result.put("hint", "Maven project: routes live under src/main/resources/camel or src/main/java and the"
                               + " configuration under src/main/resources; pass file as the path listed here");
        }
        return result;
    }

    /** One file of a project directory with its content, as {@code camel_get_files} reads it. */
    public static JsonObject readFile(Path dir, String file) {
        Path path = resolveFile(dir, file);
        if (!Files.isRegularFile(path)) {
            throw new ToolExecutionException(
                    "No such file: " + file + " in " + dir
                                             + "; call camel_get_files without file to list them (routeFiles names the routes)");
        }
        JsonObject result = new JsonObject();
        result.put("file", file);
        result.put("directory", dir.toString());
        result.put("size", formatSize(size(path)));
        result.put("type", fileType(file));
        result.put("content", read(path));
        return result;
    }

    /**
     * Where each route of a running integration comes from, mapped onto the files of the project directory. The status
     * document's {@code routes[].source} names what the runtime loaded: a jar entry for an exported project
     * ({@code nested:.../target/app.jar/!BOOT-INF/classes/!/camel/foo.camel.yaml:4}), a {@code classpath:} or
     * {@code file:} resource, or a Java class; the answer is the path a model can read and edit
     * ({@code src/main/resources/camel/foo.camel.yaml}) with the line, or the location as given with {@code missing}
     * when no such file exists under the directory.
     *
     * @param routes the status document's routes (maps with {@code routeId} and {@code source}), may be null
     */
    public static JsonArray routeSources(Collection<?> routes, Path dir) {
        JsonArray out = new JsonArray();
        if (routes == null) {
            return out;
        }
        for (Object o : routes) {
            if (!(o instanceof Map<?, ?> r) || !(r.get("source") instanceof String source) || source.isBlank()) {
                continue;
            }
            SourceLocation loc = sourceLocation(source, dir);
            JsonObject e = new JsonObject();
            if (r.get("routeId") != null) {
                e.put("routeId", String.valueOf(r.get("routeId")));
            }
            e.put("file", loc.file());
            if (loc.line() > 0) {
                e.put("line", loc.line());
            }
            if (!loc.exists()) {
                e.put("missing", true);
            }
            out.add(e);
        }
        return out;
    }

    record SourceLocation(String file, int line, boolean exists) {
    }

    /** Maps one route source location onto a file under the directory; see {@link #routeSources}. */
    static SourceLocation sourceLocation(String source, Path dir) {
        String s = source.trim();
        int line = 0;
        int colon = s.lastIndexOf(':');
        if (colon > 0 && colon < s.length() - 1) {
            String suffix = s.substring(colon + 1);
            if (suffix.chars().allMatch(Character::isDigit)) {
                try {
                    line = Integer.parseInt(suffix);
                    s = s.substring(0, colon);
                } catch (NumberFormatException e) {
                    // too many digits for a line number: leave the location as it is
                }
            }
        }
        String rel;
        int bang = s.lastIndexOf("!/");
        if (bang >= 0) {
            rel = s.substring(bang + 2);
        } else if (s.startsWith("classpath:")) {
            rel = s.substring("classpath:".length());
        } else if (s.startsWith("file:")) {
            rel = s.substring("file:".length());
        } else {
            rel = s;
        }
        rel = rel.replace('\\', '/');
        try {
            Path abs = Path.of(rel);
            if (abs.isAbsolute() && Files.isRegularFile(abs)) {
                return new SourceLocation(abs.startsWith(dir) ? relativePath(dir, abs) : abs.toString(), line, true);
            }
        } catch (InvalidPathException e) {
            // not a path on this system; try it as a relative location below
        }
        while (rel.startsWith("/")) {
            rel = rel.substring(1);
        }
        List<String> candidates = new ArrayList<>();
        candidates.add("src/main/resources/" + rel);
        candidates.add("src/main/java/" + rel);
        if (!rel.contains("/") && !rel.contains(".java") && rel.contains(".")) {
            // a Java route named by its class: org.acme.MyRoute
            candidates.add("src/main/java/" + rel.replace('.', '/') + ".java");
        }
        candidates.add(rel);
        for (String c : candidates) {
            try {
                Path p = dir.resolve(c).normalize();
                if (p.startsWith(dir) && Files.isRegularFile(p)) {
                    return new SourceLocation(c, line, true);
                }
            } catch (InvalidPathException e) {
                // skip
            }
        }
        // last resort: a file of that name anywhere in the project (a flat layout, a file that moved)
        String leaf = rel.substring(rel.lastIndexOf('/') + 1);
        if (!leaf.isEmpty()) {
            for (Path p : projectFiles(dir)) {
                if (p.getFileName().toString().equals(leaf)) {
                    return new SourceLocation(relativePath(dir, p), line, true);
                }
            }
        }
        return new SourceLocation(rel, line, false);
    }

    /** The regular files under the directory, sorted by path, build and tooling directories skipped. */
    private static List<Path> projectFiles(Path dir) {
        List<Path> files = new ArrayList<>();
        try {
            Files.walkFileTree(dir, EnumSet.noneOf(FileVisitOption.class), MAX_DEPTH, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path d, BasicFileAttributes attrs) {
                    if (d.equals(dir)) {
                        return FileVisitResult.CONTINUE;
                    }
                    String name = d.getFileName().toString();
                    return SKIPPED_DIRS.contains(name) || name.startsWith(".")
                            ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path f, BasicFileAttributes attrs) {
                    if (attrs.isRegularFile() && !f.getFileName().toString().startsWith(".")) {
                        files.add(f);
                    }
                    return files.size() >= SCAN_LIMIT ? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path f, IOException e) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new ToolExecutionException("Cannot list " + dir + ": " + e.getMessage());
        }
        files.sort(Comparator.comparing((Path p) -> relativePath(dir, p), String.CASE_INSENSITIVE_ORDER));
        return files;
    }

    static String relativePath(Path dir, Path p) {
        return dir.relativize(p).toString().replace('\\', '/');
    }

    /**
     * What a file is to Camel: {@code route} (a YAML, XML or Java file that defines routes, judged by its first lines),
     * {@code config} ({@code application*.properties} or {@code .yaml}), {@code pom}, or null for anything else.
     */
    static String fileKind(Path p, String rel, String type) {
        String leaf = rel.substring(rel.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
        if (leaf.equals("pom.xml")) {
            return "pom";
        }
        if (leaf.startsWith("application") && ("properties".equals(type) || "yaml".equals(type))) {
            return "config";
        }
        if (leaf.endsWith(".camel.yaml") || leaf.endsWith(".camel.xml")) {
            return "route";
        }
        if ("yaml".equals(type) || "xml".equals(type) || "java".equals(type)) {
            String head = head(p);
            if (head != null && isRouteSource(type, head)) {
                return "route";
            }
        }
        return null;
    }

    static boolean isRouteSource(String type, String head) {
        return switch (type) {
            case "yaml" -> YAML_ROUTE.matcher(head).find();
            case "xml" -> head.contains("<routes") || head.contains("<route ") || head.contains("<route>")
                    || head.contains("<camelContext") || head.contains("<routeTemplates") || head.contains("<rests")
                    || head.contains("<routeConfigurations");
            case "java" -> head.contains("RouteBuilder");
            default -> false;
        };
    }

    /** The first bytes of a file, enough to tell what it defines; null when it cannot be read. */
    private static String head(Path p) {
        try (var in = Files.newInputStream(p)) {
            return new String(in.readNBytes(8192), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * A file path relative to the directory (subdirectories allowed, as {@code camel_get_files} lists them); an
     * absolute path or one that escapes the directory is refused.
     */
    public static Path resolveFile(Path dir, String file) {
        if (file == null || file.isBlank()) {
            throw new ToolExecutionException("file is required");
        }
        Path path;
        try {
            if (Path.of(file).isAbsolute()) {
                throw new ToolExecutionException("file must be a path relative to the directory " + dir + ", not " + file);
            }
            path = dir.resolve(file).normalize();
        } catch (InvalidPathException e) {
            throw new ToolExecutionException("Not a valid file path: " + file);
        }
        if (!path.startsWith(dir) || path.equals(dir)) {
            throw new ToolExecutionException("file must stay inside the directory " + dir + ": " + file);
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
