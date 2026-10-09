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
package org.apache.camel.dsl.jbang.core.commands.mcp;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.MetaField;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolCallException;
import org.apache.camel.dsl.jbang.core.commands.ai.ToolContext;
import org.apache.camel.dsl.jbang.core.commands.ai.ToolExecutionException;
import org.apache.camel.dsl.jbang.core.commands.ai.ToolRegistry;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

/**
 * The neutral Camel authoring tools shared with {@code camel tui --mcp} (CAMEL-24695): thin MCP wrappers over the
 * {@link ToolRegistry}, so an AI agent gets the same tools, names and answers through either server. The logic lives in
 * camel-jbang-core ({@code AuthoringTools} there); these methods only carry the MCP annotations the security
 * interceptor and access filter work on, and pass the arguments through.
 * <p>
 * Writing a file is validated first and refused when the content is invalid, but nobody is asked before the write: the
 * MCP client (Claude Code, Cursor, and the others ask before a non read-only tool runs) is where the human sits. The
 * {@code read-only} access level hides the tools that write, run and control.
 */
@ApplicationScoped
public class AuthoringTools {

    private static final String NAME_DESC = "Integration name or pid (default: the only one running)";
    private static final String VERSION_DESC = "Camel version to answer for (default: the CLI's own)";
    private static final String DIRECTORY_DESC = "Project directory with the source files (absolute path)";

    @Inject
    RepeatedCallSessions repeatedCalls;

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = "Camel catalog documentation of a component, data format, language, EIP, Kamelet, built-in bean or the "
                        + "Java API (description, options, Maven coordinates), with the URI rules of a component. For "
                        + "simple also its syntax rules, functions and operators: count and names by group, or with "
                        + "optionsFilter the matching ones with parameters and examples. kind=api is the Java API to "
                        + "call from a bean or script (Exchange, Message, CamelContext, AggregationStrategy, ...) and "
                        + "the variables a groovy, js, python or java script sees. endpoint validates a URI: unknown or "
                        + "invalid options, missing path. An EIP alias such as fan-out or dedup finds the EIP. "
                        + "includeHeaders=true adds the message headers of a component, includeDoc=true the AsciiDoc "
                        + "page.")
    @MetaField(prefix = "camel.apache.org/", name = "deterministic", type = MetaField.Type.BOOLEAN, value = "true")
    public JsonObject camel_catalog_doc(
            McpConnection connection,
            @ToolArg(description = "Name, e.g. kafka, json (a data format by its YAML name or artifact), simple, timer, choice, split, Exchange",
                     required = false) String name,
            @ToolArg(description = "Endpoint URI to check, e.g. kafka:orders?brokers=host:9092",
                     required = false) String endpoint,
            @ToolArg(description = "component, dataformat, language, eip, kamelet, bean or api (auto-detected; a bean is a built-in class such as StringAggregationStrategy, with how to declare and use it; api is the Java API to call from a bean or script before writing it: Exchange, Message, CamelContext, Registry, ProducerTemplate, Processor, AggregationStrategy, Predicate, Expression, TypeConverter, or the variables of groovy, js, python, java scripts)",
                     required = false) String kind,
            @ToolArg(description = "common (default: no deprecated or advanced), required, all or false",
                     required = false) String includeOptions,
            @ToolArg(description = "Include the message headers of a component (default false)",
                     required = false) Boolean includeHeaders,
            @ToolArg(description = "Include the full AsciiDoc page (default false)", required = false) Boolean includeDoc,
            @ToolArg(description = "simple doc sub-page to return as text (functions, operators, ognl, advanced)",
                     required = false) String docPage,
            @ToolArg(description = "Keyword to match in option names or descriptions; for an EIP whose own options do not match, the options of its elements are searched (nestedOptions)",
                     required = false) String optionsFilter,
            @ToolArg(description = VERSION_DESC, required = false) String camelVersion) {
        return callDeterministic(connection, "camel_catalog_doc", args("name", name, "endpoint", endpoint, "kind", kind,
                "includeOptions", includeOptions, "includeHeaders", includeHeaders, "includeDoc", includeDoc,
                "docPage", docPage,
                "optionsFilter", optionsFilter, "camelVersion", camelVersion));
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = "Finds Camel components, data formats, languages, EIPs and Kamelets by a protocol, product, alias or "
                        + "other term that is not the exact name (mqtt, s3, snowflake, csv, fan-out, dedup): best "
                        + "match first with title and description. camel_catalog_doc then gives the options of one.")
    @MetaField(prefix = "camel.apache.org/", name = "deterministic", type = MetaField.Type.BOOLEAN, value = "true")
    public JsonObject camel_catalog_find(
            McpConnection connection,
            @ToolArg(description = "What to look for, e.g. mqtt, s3, database, csv, fan-out", required = true) String term,
            @ToolArg(description = "component, dataformat, language, eip, kamelet or bean (default: all); bean with an interface name such as AggregationStrategy lists the built-in implementations",
                     required = false) String kind,
            @ToolArg(description = "Maximum matches per kind (default 10)", required = false) Integer limit,
            @ToolArg(description = VERSION_DESC, required = false) String camelVersion) {
        return callDeterministic(connection, "camel_catalog_find", args("term", term, "kind", kind, "limit", limit,
                "camelVersion", camelVersion));
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = "A validated YAML DSL sample of an EIP or file entry (onException, aggregate, split, rest, beans), "
                        + "a component (kafka, file), a data format (csv) or a language (jq) from the docs, with where "
                        + "it goes (a top-level entry, a step, an endpoint uri, a marshal step, an expression). Use "
                        + "before writing one the first time or after a 'not defined in the schema' error.")
    @MetaField(prefix = "camel.apache.org/", name = "deterministic", type = MetaField.Type.BOOLEAN, value = "true")
    public JsonObject camel_catalog_sample(
            McpConnection connection,
            @ToolArg(description = "EIP, component, data format or language name, or what to do (read file, call "
                                   + "service, retry, batch)",
                     required = true) String name,
            @ToolArg(description = "eip, component, dataformat or language; needed only when a name is in several "
                                   + "(avro, file)",
                     required = false) String kind,
            @ToolArg(description = "Maximum samples to return (default 2, max 5)", required = false) Integer limit,
            @ToolArg(description = VERSION_DESC, required = false) String camelVersion) {
        return callDeterministic(connection, "camel_catalog_sample",
                args("name", name, "kind", kind, "limit", limit, "camelVersion", camelVersion));
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = "Validates Camel YAML/Java/XML DSL or .properties source without writing: schema (misspelled options "
                        + "such as logLevel instead of loggingLevel), endpoint URIs, simple expressions, camel.* "
                        + "options, and how each bean under beans: is created (the properties a class built through "
                        + "its builder() accepts, the ways to create a class with no constructor). Use on content "
                        + "before writing it, or on an existing file (no content) to explain a reload error.")
    public JsonObject camel_validate_source(
            McpConnection connection,
            @ToolArg(description = DIRECTORY_DESC + "; needed when no content is given", required = false) String directory,
            @ToolArg(description = "File name; picks the checks by extension, read when no content",
                     required = true) String file,
            @ToolArg(description = "The source to validate", required = false) String content,
            @ToolArg(description = VERSION_DESC, required = false) String camelVersion) {
        // the same content without a directory gets the same answer: the third identical call gets a short note
        // (CAMEL-25371)
        return callDeterministic(connection, "camel_validate_source", args("directory", directory, "file", file,
                "content", content, "camelVersion", camelVersion));
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = "The source files of a project directory, subdirectories included: without file the list, "
                        + "with the route and configuration files named first (routeFiles, configFiles) and, for a "
                        + "running integration, which file and line each route comes from; with file (a path "
                        + "relative to the directory, as listed) its content.")
    public JsonObject camel_get_files(
            @ToolArg(description = DIRECTORY_DESC, required = false) String directory,
            @ToolArg(description = "File path relative to the directory, e.g. src/main/resources/camel/foo.camel.yaml,"
                                   + " to read; omitted lists the files",
                     required = false) String file) {
        return call("camel_get_files", args("directory", directory, "file", file));
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = false, destructiveHint = false, openWorldHint = false),
          description = "Writes the complete content of a file in the project directory. YAML and .properties "
                        + "content is validated first; invalid content is not written and the errors are returned. "
                        + "An integration running in dev mode reloads the change, otherwise restart it with "
                        + "camel_control.")
    public JsonObject camel_write_file(
            @ToolArg(description = DIRECTORY_DESC, required = false) String directory,
            @ToolArg(description = "File path relative to the directory (subdirectories are created)",
                     required = true) String file,
            @ToolArg(description = "The complete new content", required = true) String content,
            @ToolArg(description = VERSION_DESC, required = false) String camelVersion) {
        return call("camel_write_file", args("directory", directory, "file", file, "content", content,
                "camelVersion", camelVersion));
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = false, destructiveHint = false, openWorldHint = false),
          description = "Changes a file by replacing a snippet: the exact text to find (it must occur once) and "
                        + "what to put there. Validated and reloaded as a write is. Use it to change an existing file, "
                        + "camel_write_file for a new one. A change to several places of the file is given at once in "
                        + "edits, so the file is written and reloaded once, not half done.")
    public JsonObject camel_edit_file(
            @ToolArg(description = DIRECTORY_DESC, required = false) String directory,
            @ToolArg(description = "File path relative to the directory", required = true) String file,
            @ToolArg(description = "The lines to replace as they stand in the file; other indentation is fine when "
                                   + "the lines name one place (or use edits)",
                     required = false) String find,
            @ToolArg(description = "The text to put there; empty removes it", required = false) String replace,
            @ToolArg(description = org.apache.camel.dsl.jbang.core.commands.ai.AuthoringTools.EDITS_DESC,
                     required = false) List<FileEdit> edits,
            @ToolArg(description = VERSION_DESC, required = false) String camelVersion) {
        return call("camel_edit_file", args("directory", directory, "file", file, "find", find, "replace", replace,
                "edits", editsJson(edits), "camelVersion", camelVersion));
    }

    /** One change of camel_edit_file's edits: the text to find as it stands in the file, and what to put there. */
    public record FileEdit(String find, String replace) {
    }

    /** The edits as the JSON list the shared tool reads, or null when there are none. */
    static String editsJson(List<FileEdit> edits) {
        if (edits == null || edits.isEmpty()) {
            return null;
        }
        JsonArray list = new JsonArray();
        for (FileEdit edit : edits) {
            // a null element stays in the list as {}, on purpose: the shared tool then answers with the shape edits
            // must have, so a malformed call is told rather than half applied
            JsonObject jo = new JsonObject();
            if (edit != null) {
                jo.put("find", edit.find());
                jo.put("replace", edit.replace());
            }
            list.add(jo);
        }
        return list.toJson();
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = false, destructiveHint = false, openWorldHint = true),
          description = "Starts an integration with camel run in a separate process, in dev mode by default (files reload when written). Returns the pid and log file; camel_get_log, camel_get_errors and camel_control follow it.")
    public JsonObject camel_run(
            @ToolArg(description = "Project directory to run in (absolute path)", required = true) String directory,
            @ToolArg(description = "Source files to run, comma-separated (default: every route file in the"
                                   + " directory)",
                     required = false) String files,
            @ToolArg(description = "Integration name (default: from the first file)", required = false) String name,
            @ToolArg(description = "Dev mode with reload on file change (default true)", required = false) Boolean dev) {
        return call("camel_run", args("directory", directory, "files", files, "name", name, "dev", dev));
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = false, destructiveHint = true, openWorldHint = false),
          description = "Controls a running integration: stop (graceful), kill, restart (picks up edited files "
                        + "without dev mode), stop-routes, start-routes, reset-stats (clears statistics, routes "
                        + "untouched). Never stop, kill or restart unless the user asked for it.")
    public JsonObject camel_control(
            @ToolArg(description = "stop, kill, restart, stop-routes, start-routes or reset-stats",
                     required = true) String action,
            @ToolArg(description = NAME_DESC, required = false) String name) {
        return call("camel_control", args("action", action, "name", name));
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = "Recent log records of a running integration, newest first, with optional filtering; stack "
                        + "traces only with details.")
    public JsonObject camel_get_log(
            @ToolArg(description = NAME_DESC, required = false) String name,
            @ToolArg(description = "Maximum records to return (default 50)", required = false) Integer limit,
            @ToolArg(description = "Case-insensitive substring filter on the message", required = false) String filter,
            @ToolArg(description = "Only this log level (INFO, WARN, ERROR, DEBUG, TRACE)", required = false) String level,
            @ToolArg(description = "Include the stack traces (default false)", required = false) Boolean details) {
        return call("camel_get_log", args("name", name, "limit", limit, "filter", filter, "level", level,
                "details", details));
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = "The failed exchanges of a running integration: routeId, exchangeId, exception with stack "
                        + "trace, body and headers.")
    public JsonObject camel_get_errors(
            @ToolArg(description = NAME_DESC, required = false) String name) {
        return call("camel_get_errors", args("name", name));
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = "Evaluates an expression: in the running integration when there is one, else locally, in any "
                        + "language (jsonpath, jq, xpath, groovy: its component is downloaded when needed). Returns "
                        + "the value (true/false for a predicate) or the syntax error, so check an expression before "
                        + "answering or writing it.")
    public JsonObject camel_eval_expression(
            @ToolArg(description = "e.g. ${random(1,10)} or ${body} ?: 'none'", required = true) String expression,
            @ToolArg(description = "simple (default), jsonpath, xpath, jq", required = false) String language,
            @ToolArg(description = "Message body", required = false) String body,
            @ToolArg(description = NAME_DESC, required = false) String name) {
        return call("camel_eval_expression", args("expression", expression, "language", language, "body", body,
                "name", name));
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = true),
          description = "Which Maven dependency provides a class, and how to declare it: the known dependencies "
                        + "camel run downloads by itself (nothing to declare there), a Camel component's artifact per "
                        + "runtime, or with mavenCentral=true a Maven Central search by class name (a guess, marked as "
                        + "such). Answers camel.jbang.dependencies, --dep, and the pom.xml dependency for Camel Main, "
                        + "Spring Boot and Quarkus.")
    public JsonObject camel_dependency_for_class(
            @ToolArg(description = "Fully qualified class name, e.g. org.postgresql.ds.PGSimpleDataSource",
                     required = true) String className,
            @ToolArg(description = "main, spring-boot or quarkus (default: all three pom forms)",
                     required = false) String runtime,
            @ToolArg(description = "Search Maven Central when the class is not in the known dependencies (default "
                                   + "false; needs network, can take up to 40 s)",
                     required = false) Boolean mavenCentral,
            @ToolArg(description = VERSION_DESC, required = false) String camelVersion) {
        return call("camel_dependency_for_class", args("className", className, "runtime", runtime, "mavenCentral",
                mavenCentral, "camelVersion", camelVersion));
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = "A high-level overview of a project's integrations from its route sources (no running "
                        + "integration needed): routes, entry points, how routes connect (call, hand-off, event), "
                        + "external systems by category, findings (missing routes, cycles, routes without a "
                        + "description), and the AI-assisted parts of its camel-summary.md (fields starting "
                        + "with ai). Use it to explain a project, then camel_save_project_summary to keep the "
                        + "explanation.")
    public JsonObject camel_project_overview(
            @ToolArg(description = DIRECTORY_DESC, required = false) String directory,
            @ToolArg(description = VERSION_DESC, required = false) String camelVersion) {
        return call("camel_project_overview", args("directory", directory, "camelVersion", camelVersion));
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = false, destructiveHint = false, openWorldHint = false),
          description = "Saves what you wrote about a project into its camel-summary.md, marked as "
                        + "AI-assisted, beside the facts derived from the sources, so people and tools can tell them "
                        + "apart. Call camel_project_overview first. Omitted parts keep what the file has. Route "
                        + "sources are not changed; to put a description into a route, edit the route.")
    public JsonObject camel_save_project_summary(
            @ToolArg(description = DIRECTORY_DESC, required = false) String directory,
            @ToolArg(description = "Two to four sentences on what the project does as a whole",
                     required = false) String overview,
            @ToolArg(description = "One line per capability: name: route ids comma separated | one sentence",
                     required = false) String capabilities,
            @ToolArg(description = "One line per route without a description: route id: short label (two to six words) |"
                                   + " one sentence on what it does and why",
                     required = false) String descriptions,
            @ToolArg(description = "Route ids of plumbing with little business meaning (logging, dead letter, retries),"
                                   + " comma separated",
                     required = false) String utility,
            @ToolArg(description = "One line per decision point of a route (the decisions camel_project_overview"
                                   + " lists): route id / path: short label (two to five words) | one sentence on why"
                                   + " the route decides there",
                     required = false) String steps,
            @ToolArg(description = "The model writing this, recorded in the file", required = false) String model,
            @ToolArg(description = VERSION_DESC, required = false) String camelVersion) {
        return call("camel_save_project_summary", args("directory", directory, "overview", overview,
                "capabilities", capabilities, "descriptions", descriptions, "utility", utility, "steps", steps,
                "model", model, "camelVersion", camelVersion));
    }

    /**
     * As {@link #call(String, Map)} for a deterministic tool: from the third identical call of the connection the
     * answer is a short note that it was already answered (CAMEL-25075).
     */
    JsonObject callDeterministic(McpConnection connection, String tool, Map<String, String> args) {
        JsonObject repeat = repeatedCalls != null ? repeatedCalls.repeatOf(connection, tool, args) : null;
        return repeat != null ? repeat : call(tool, args);
    }

    /** Runs the registry tool of the same name and hands its JSON back; a tool error becomes an MCP tool error. */
    static JsonObject call(String tool, Map<String, String> args) {
        try {
            return RuntimeTools.toJsonObject(ToolRegistry.execute(tool, new ToolContext(), args));
        } catch (ToolExecutionException e) {
            throw new ToolCallException(e.getMessage(), e);
        }
    }

    /**
     * Name and value pairs into the string arguments of the registry. A null or blank value is left out, so an argument
     * the client did not provide, or sent as an empty string, gets the tool's default.
     */
    static Map<String, String> args(Object... pairs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            Object value = pairs[i + 1];
            if (value != null && !(value instanceof String str && str.isBlank())) {
                map.put(String.valueOf(pairs[i]), String.valueOf(value));
            }
        }
        return map;
    }
}
