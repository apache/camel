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
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolCallException;
import org.apache.camel.dsl.jbang.core.commands.ai.ToolContext;
import org.apache.camel.dsl.jbang.core.commands.ai.ToolExecutionException;
import org.apache.camel.dsl.jbang.core.commands.ai.ToolRegistry;
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

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = "Camel catalog documentation of a component, data format, language or EIP (description, "
                        + "options, Maven coordinates), with the URI rules of a component. For simple also its syntax "
                        + "rules, functions and operators: count and names by group, or with optionsFilter the "
                        + "matching ones with parameters and examples. endpoint validates a URI: unknown or invalid "
                        + "options, missing path. Replaces the former AsciiDoc-only camel_catalog_doc: includeDoc=true "
                        + "adds the AsciiDoc page.")
    public JsonObject camel_catalog_doc(
            @ToolArg(description = "Name, e.g. kafka, json-jackson, simple, timer, choice, split") String name,
            @ToolArg(description = "Endpoint URI to check, e.g. kafka:orders?brokers=host:9092") String endpoint,
            @ToolArg(description = "component, dataformat, language or eip (auto-detected)") String kind,
            @ToolArg(description = "Include the options (default true)") Boolean includeOptions,
            @ToolArg(description = "Include the full AsciiDoc page (default false)") Boolean includeDoc,
            @ToolArg(description = "A language doc sub-page (simple: functions, operators, ognl, advanced) to return"
                                   + " as text") String docPage,
            @ToolArg(description = "Keyword to match in option names or descriptions") String optionsFilter,
            @ToolArg(description = VERSION_DESC) String camelVersion) {
        return call("camel_catalog_doc", args("name", name, "endpoint", endpoint, "kind", kind,
                "includeOptions", includeOptions, "includeDoc", includeDoc, "docPage", docPage,
                "optionsFilter", optionsFilter, "camelVersion", camelVersion));
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = "Finds Camel components, data formats and languages by a protocol, product or other term that "
                        + "is not the exact name (mqtt, s3, snowflake, csv): best match first with title and "
                        + "description. camel_catalog_doc then gives the options of one.")
    public JsonObject camel_catalog_find(
            @ToolArg(description = "What to look for, e.g. mqtt, s3, database, csv") String term,
            @ToolArg(description = "component, dataformat or language (default: all)") String kind,
            @ToolArg(description = "Maximum matches per kind (default 10)") Integer limit,
            @ToolArg(description = VERSION_DESC) String camelVersion) {
        return call("camel_catalog_find", args("term", term, "kind", kind, "limit", limit,
                "camelVersion", camelVersion));
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = "Validates Camel YAML DSL or .properties source without writing: schema (misspelled options "
                        + "such as logLevel instead of loggingLevel), endpoint URIs, simple expressions, camel.* "
                        + "options. Use on content before writing it, or on an existing file (no content) to explain "
                        + "a reload error.")
    public JsonObject camel_validate_source(
            @ToolArg(description = DIRECTORY_DESC + "; needed when no content is given") String directory,
            @ToolArg(description = "File name; picks the checks by extension, read when no content") String file,
            @ToolArg(description = "The source to validate") String content,
            @ToolArg(description = VERSION_DESC) String camelVersion) {
        return call("camel_validate_source", args("directory", directory, "file", file, "content", content,
                "camelVersion", camelVersion));
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = "The source files of a project directory: without file the list (name, size, type), with "
                        + "file its content. Use before editing to see the routes, configuration and other files of "
                        + "the integration.")
    public JsonObject camel_get_files(
            @ToolArg(description = DIRECTORY_DESC) String directory,
            @ToolArg(description = "File name to read; omitted lists the files") String file) {
        return call("camel_get_files", args("directory", directory, "file", file));
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = false, destructiveHint = false, openWorldHint = false),
          description = "Writes the complete content of a file in the project directory. YAML and .properties "
                        + "content is validated first; invalid content is not written and the errors are returned. "
                        + "An integration running in dev mode reloads the change, otherwise restart it with "
                        + "camel_control.")
    public JsonObject camel_write_file(
            @ToolArg(description = DIRECTORY_DESC) String directory,
            @ToolArg(description = "File name, no path") String file,
            @ToolArg(description = "The complete new content") String content,
            @ToolArg(description = "Validate before writing (default true)") Boolean validate,
            @ToolArg(description = VERSION_DESC) String camelVersion) {
        return call("camel_write_file", args("directory", directory, "file", file, "content", content,
                "validate", validate, "camelVersion", camelVersion));
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = false, destructiveHint = false, openWorldHint = true),
          description = "Starts an integration from a project directory with camel run in a separate process, in dev "
                        + "mode by default (route files reload when written). Returns the pid and log file once it is "
                        + "up; camel_get_log and camel_get_errors then tell how it does, camel_control stops it.")
    public JsonObject camel_run(
            @ToolArg(description = "Project directory to run in (absolute path)") String directory,
            @ToolArg(description = "Source files to run, comma-separated (default: every route file in the"
                                   + " directory)") String files,
            @ToolArg(description = "Integration name (default: from the first file)") String name,
            @ToolArg(description = "Dev mode with reload on file change (default true)") Boolean dev) {
        return call("camel_run", args("directory", directory, "files", files, "name", name, "dev", dev));
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = false, destructiveHint = true, openWorldHint = false),
          description = "Controls a running integration: stop (graceful), kill, restart (picks up edited files "
                        + "without dev mode), stop-routes, start-routes, reset-stats (clears statistics, routes "
                        + "untouched). Never stop, kill or restart unless the user asked for it.")
    public JsonObject camel_control(
            @ToolArg(description = "stop, kill, restart, stop-routes, start-routes or reset-stats") String action,
            @ToolArg(description = NAME_DESC) String name) {
        return call("camel_control", args("action", action, "name", name));
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = "Recent log records of a running integration, newest first, with optional filtering; a "
                        + "stack trace comes as one record with a detail block.")
    public JsonObject camel_get_log(
            @ToolArg(description = NAME_DESC) String name,
            @ToolArg(description = "Maximum records to return (default 50)") Integer limit,
            @ToolArg(description = "Case-insensitive substring filter on the message") String filter,
            @ToolArg(description = "Only this log level (INFO, WARN, ERROR, DEBUG, TRACE)") String level) {
        return call("camel_get_log", args("name", name, "limit", limit, "filter", filter, "level", level));
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = "The failed exchanges of a running integration: routeId, exchangeId, exception with stack "
                        + "trace, body and headers.")
    public JsonObject camel_get_errors(
            @ToolArg(description = NAME_DESC) String name) {
        return call("camel_get_errors", args("name", name));
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = "Evaluates an expression: in the running integration when there is one, else locally. "
                        + "Returns the value (true/false for a predicate) or the syntax error, so check simple before "
                        + "answering or writing it.")
    public JsonObject camel_eval_expression(
            @ToolArg(description = "e.g. ${random(1,10)} or ${body} ?: 'none'") String expression,
            @ToolArg(description = "simple (default), jsonpath, xpath, jq") String language,
            @ToolArg(description = "Message body") String body,
            @ToolArg(description = NAME_DESC) String name) {
        return call("camel_eval_expression", args("expression", expression, "language", language, "body", body,
                "name", name));
    }

    /** Runs the registry tool of the same name and hands its JSON back; a tool error becomes an MCP tool error. */
    static JsonObject call(String tool, Map<String, String> args) {
        try {
            return RuntimeTools.toJsonObject(ToolRegistry.execute(tool, new ToolContext(), args));
        } catch (ToolExecutionException e) {
            throw new ToolCallException(e.getMessage(), e);
        }
    }

    /** Name and value pairs into the string arguments of the registry; a null value is left out. */
    static Map<String, String> args(Object... pairs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            Object value = pairs[i + 1];
            if (value != null) {
                map.put(String.valueOf(pairs[i]), String.valueOf(value));
            }
        }
        return map;
    }
}
