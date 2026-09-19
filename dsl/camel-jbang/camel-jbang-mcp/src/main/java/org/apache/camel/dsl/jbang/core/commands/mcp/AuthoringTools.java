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
          description = "Camel catalog documentation of a component, data format, language, EIP, built-in bean or the "
                        + "Java API (description, options, Maven coordinates), with the URI rules of a component. For "
                        + "simple also its syntax rules, functions and operators: count and names by group, or with "
                        + "optionsFilter the matching ones with parameters and examples. kind=api is the Java API to "
                        + "call from a bean or script (Exchange, Message, CamelContext, AggregationStrategy, ...) and "
                        + "the variables a groovy, js, python or java script sees. endpoint validates a URI: unknown or "
                        + "invalid options, missing path. An EIP alias such as fan-out or dedup finds the EIP. "
                        + "includeHeaders=true adds the message headers of a component, includeDoc=true the AsciiDoc "
                        + "page.")
    public JsonObject camel_catalog_doc(
            @ToolArg(description = "Name, e.g. kafka, json-jackson, simple, timer, choice, split, Exchange",
                     required = false) String name,
            @ToolArg(description = "Endpoint URI to check, e.g. kafka:orders?brokers=host:9092",
                     required = false) String endpoint,
            @ToolArg(description = "component, dataformat, language, eip, bean or api (auto-detected; a bean is a built-in class such as StringAggregationStrategy, with how to declare and use it; api is the Java API to call from a bean or script before writing it: Exchange, Message, CamelContext, Registry, ProducerTemplate, Processor, AggregationStrategy, Predicate, Expression, TypeConverter, or the variables of groovy, js, python, java scripts)",
                     required = false) String kind,
            @ToolArg(description = "common (default: no deprecated or advanced), required, all or false",
                     required = false) String includeOptions,
            @ToolArg(description = "Include the message headers of a component (default false)",
                     required = false) Boolean includeHeaders,
            @ToolArg(description = "Include the full AsciiDoc page (default false)", required = false) Boolean includeDoc,
            @ToolArg(description = "simple doc sub-page to return as text (functions, operators, ognl, advanced)",
                     required = false) String docPage,
            @ToolArg(description = "Keyword to match in option names or descriptions", required = false) String optionsFilter,
            @ToolArg(description = VERSION_DESC, required = false) String camelVersion) {
        return call("camel_catalog_doc", args("name", name, "endpoint", endpoint, "kind", kind,
                "includeOptions", includeOptions, "includeHeaders", includeHeaders, "includeDoc", includeDoc,
                "docPage", docPage,
                "optionsFilter", optionsFilter, "camelVersion", camelVersion));
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = "Finds Camel components, data formats, languages and EIPs by a protocol, product, alias or "
                        + "other term that is not the exact name (mqtt, s3, snowflake, csv, fan-out, dedup): best "
                        + "match first with title and description. camel_catalog_doc then gives the options of one.")
    public JsonObject camel_catalog_find(
            @ToolArg(description = "What to look for, e.g. mqtt, s3, database, csv, fan-out", required = true) String term,
            @ToolArg(description = "component, dataformat, language, eip or bean (default: all); bean with an interface name such as AggregationStrategy lists the built-in implementations",
                     required = false) String kind,
            @ToolArg(description = "Maximum matches per kind (default 10)", required = false) Integer limit,
            @ToolArg(description = VERSION_DESC, required = false) String camelVersion) {
        return call("camel_catalog_find", args("term", term, "kind", kind, "limit", limit,
                "camelVersion", camelVersion));
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = "A validated YAML DSL sample of an EIP or file entry (onException, aggregate, split, rest, beans), "
                        + "a component (kafka, file), a data format (csv) or a language (jq) from the docs, with where "
                        + "it goes (a top-level entry, a step, an endpoint uri, a marshal step, an expression). Use "
                        + "before writing one the first time or after a 'not defined in the schema' error.")
    public JsonObject camel_catalog_sample(
            @ToolArg(description = "EIP, component, data format or language name, or what to do (read file, call "
                                   + "service, retry, batch)",
                     required = true) String name,
            @ToolArg(description = "eip, component, dataformat or language; needed only when a name is in several "
                                   + "(avro, file)",
                     required = false) String kind,
            @ToolArg(description = "Maximum samples to return (default 2, max 5)", required = false) Integer limit,
            @ToolArg(description = VERSION_DESC, required = false) String camelVersion) {
        return call("camel_catalog_sample", args("name", name, "kind", kind, "limit", limit, "camelVersion", camelVersion));
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = "Validates Camel YAML DSL or .properties source without writing: schema (misspelled options "
                        + "such as logLevel instead of loggingLevel), endpoint URIs, simple expressions, camel.* "
                        + "options, and how each bean under beans: is created (the properties a class built through "
                        + "its builder() accepts, the ways to create a class with no constructor). Use on content "
                        + "before writing it, or on an existing file (no content) to explain a reload error.")
    public JsonObject camel_validate_source(
            @ToolArg(description = DIRECTORY_DESC + "; needed when no content is given", required = false) String directory,
            @ToolArg(description = "File name; picks the checks by extension, read when no content",
                     required = true) String file,
            @ToolArg(description = "The source to validate", required = false) String content,
            @ToolArg(description = VERSION_DESC, required = false) String camelVersion) {
        return call("camel_validate_source", args("directory", directory, "file", file, "content", content,
                "camelVersion", camelVersion));
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
            @ToolArg(description = "Validate before writing (default true)", required = false) Boolean validate,
            @ToolArg(description = VERSION_DESC, required = false) String camelVersion) {
        return call("camel_write_file", args("directory", directory, "file", file, "content", content,
                "validate", validate, "camelVersion", camelVersion));
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
          description = "Recent log records of a running integration, newest first, with optional filtering; a "
                        + "stack trace comes as one record with a detail block.")
    public JsonObject camel_get_log(
            @ToolArg(description = NAME_DESC, required = false) String name,
            @ToolArg(description = "Maximum records to return (default 50)", required = false) Integer limit,
            @ToolArg(description = "Case-insensitive substring filter on the message", required = false) String filter,
            @ToolArg(description = "Only this log level (INFO, WARN, ERROR, DEBUG, TRACE)", required = false) String level) {
        return call("camel_get_log", args("name", name, "limit", limit, "filter", filter, "level", level));
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = "The failed exchanges of a running integration: routeId, exchangeId, exception with stack "
                        + "trace, body and headers.")
    public JsonObject camel_get_errors(
            @ToolArg(description = NAME_DESC, required = false) String name) {
        return call("camel_get_errors", args("name", name));
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = "Evaluates an expression: in the running integration when there is one, else locally. "
                        + "Returns the value (true/false for a predicate) or the syntax error, so check simple before "
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
