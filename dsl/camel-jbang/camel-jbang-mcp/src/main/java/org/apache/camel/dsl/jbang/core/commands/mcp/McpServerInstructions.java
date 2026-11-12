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

import java.util.List;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.mcp.server.InitialResponseInfo;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * The {@code instructions} of the MCP {@code initialize} response: what the server is for, the rules an agent needs
 * before its first tool call, and which tool answers which question.
 * <p>
 * Hosts that defer tool schemas (Claude Code, Codex, OpenCode, Pi) give the model this text at session start, before
 * any tool description. Pi shows only the first line and Codex weighs the first 512 characters most, so the first line
 * is a one-sentence summary with the Camel version. The whole text stays under 4096 characters (what Claude Code
 * loads).
 * <p>
 * The text serves everyday work on a Camel project: building, running and fixing its routes (CAMEL-25672). The rules
 * are the mistakes that cost a model most when it writes routes, from a local-model benchmark. The rules of a migration
 * to Camel are a separate text ({@link #migration(String)}), sent by the {@code camel_migrate_to_camel} prompt and the
 * {@code camel_migration_instructions} tool when a migration starts: a migration is a one-time task, and its rules
 * (mail headers, xslt parameters) do not help with the daily work.
 * <p>
 * A value set in {@code quarkus.mcp.server.server-info.instructions} replaces this text: Quarkus asks the
 * {@link InitialResponseInfo} beans before it reads the property, so this bean steps aside when the property is set.
 */
@ApplicationScoped
public class McpServerInstructions implements InitialResponseInfo {

    /** The length the hosts take in full (Claude Code loads up to this many characters). */
    static final int MAX_LENGTH = 4096;

    /** The tool names the text maps questions to; a test checks that each one is an {@code @Tool} method. */
    static final List<String> MAPPED_TOOLS = List.of(
            "camel_get_files", "camel_edit_file", "camel_write_file", "camel_run", "camel_control", "camel_get_log",
            "camel_get_errors", "camel_eval_expression", "camel_validate_source", "camel_project_overview",
            "camel_catalog_doc", "camel_catalog_find", "camel_catalog_sample", "camel_catalog_examples",
            "camel_dependency_for_class", "camel_migration_instructions");

    /** The prompt names the text points to; a test checks that each one is a {@code @Prompt} method. */
    static final List<String> MAPPED_PROMPTS = List.of("camel_migrate_to_camel", "camel_migrate_project");

    /** The text, with the Camel version as its only parameter. Kept here so the whole text reviews in one place. */
    private static final String TEMPLATE = """
            Apache Camel %s tools to build, run and fix the routes of a Camel project: change its files with \
            camel_edit_file, run it with camel_run, and read what happened with camel_get_log.

            Rules for writing routes (YAML DSL):
            - The steps of an EIP (split, filter, loop, doTry, ...) go under its steps, not after it: \
            split: {expression: {simple: {expression: "${body}"}}, steps: [...]}.
            - A choice has one when list, each entry with its expression and steps, and an optional \
            otherwise: {steps: [...]}.
            - onException, errorHandler, routeConfiguration, intercept and onCompletion are top-level entries beside \
            route (or inside routeConfiguration), never steps. handled and continued are expressions: \
            handled: {constant: {expression: "true"}}.
            - A value the route keeps for itself goes in a variable (setVariable, ${variable.name}), not an exchange \
            property, and not a header: headers go out with the message.
            - Simple: functions inside ${...}, operators between them: ${header.a} == 'b', not ${header.a == 'b'}.
            - Components are named by URI scheme: platform-http for an HTTP server, http/https for an HTTP client. \
            camel_catalog_find maps a protocol or product to the scheme.
            - After a write, read its answer: it has the reload of the running integration and what the routes \
            logged after it. Passing the schema is not passing at runtime.

            Daily work:
            - the files of a project: camel_get_files; change one: camel_edit_file (several places at once with \
            edits); a new file: camel_write_file. YAML and properties are validated before they are written.
            - run an integration: camel_run (dev mode reloads each write); stop or restart: camel_control, only when \
            the user asks
            - what happened: camel_get_log (retries are logged at WARN); the exchanges that failed: camel_get_errors
            - check an expression on a sample message: camel_eval_expression
            - validate a source without writing it: camel_validate_source
            - routes, endpoints and mistakes of an existing project: camel_project_overview

            Which tool:
            - options, URI syntax, headers of a component, EIP, data format or language: camel_catalog_doc
            - which component for a protocol or product: camel_catalog_find
            - a validated YAML sample and where it goes: camel_catalog_sample
            - complete example projects: camel_catalog_examples
            - a sample Citrus test (test/<name>.citrus.it.yaml, run with camel test run): the test/ file of an \
            example, camel_catalog_examples with filter citrus
            - Maven coordinates of a Java class: camel_dependency_for_class
            - run a Citrus test (test/<name>.citrus.it.yaml): camel test run <file> in the project directory, not \
            camel_run

            Migrating to Camel, or between Camel versions? Use the camel_migrate_to_camel or camel_migrate_project \
            prompt, or call camel_migration_instructions.
            """;

    /** The tool names the migration text points to; a test checks that each one is an {@code @Tool} method. */
    static final List<String> MIGRATION_TOOLS = List.of(
            "camel_catalog_find", "camel_catalog_doc", "camel_catalog_sample", "camel_dependency_for_class",
            "camel_validate_source", "camel_write_file", "camel_run", "camel_get_log", "camel_get_errors",
            "camel_runtime_send", "camel_migration_analyze");

    /**
     * The rules of a migration of an integration to Camel, with the Camel version as its only parameter: the rules an
     * evaluation of agents migrating legacy integrations to Camel found (CAMEL-25646).
     */
    private static final String MIGRATION_TEMPLATE = """
            Migrating an integration to Apache Camel %s, from another product or from hand-written code. The rules \
            that migrations get wrong most, then the workflow.

            Rules:
            - Components are named by URI scheme: smtp/smtps/imap/pop3 (not mail), platform-http for an HTTP server, \
            http/https for an HTTP client. camel_catalog_find maps a protocol or product to the scheme.
            - mail producer: the Subject, From and To headers are ignored unless useHeaderSubject=true, \
            useHeaderFrom=true and useHeaderRecipients=true; otherwise use the subject, from and to options.
            - Before an http or mail producer, remove the Camel* headers you did not set yourself \
            (removeHeaders: {pattern: "Camel*"} when none are yours), so inbound CamelHttpPath, CamelHttpQuery or \
            CamelHttpUri do not redirect the call and no header leaks into the mail.
            - xslt receives the message headers and variables as stylesheet parameters (xsl:param).
            - camel run: camel.server.port (or camel run --port) sets the HTTP port. Extra jars: a //DEPS line in a \
            source file, camel.jbang.dependencies=group:artifact:version in application.properties, or --dep on the \
            command line (camel_run has no dependencies argument, so use one of the first two). \
            camel_dependency_for_class gives the coordinates of a Java class.
            - Passing the YAML schema is not passing at runtime: run the app (camel_run) and read its log before \
            calling it done.

            Workflow:
            1. List what the integration does: its inbound and outbound endpoints, transformations, error handling \
            and configuration.
            2. For each protocol or product, find the component (camel_catalog_find) and its options \
            (camel_catalog_doc); camel_catalog_sample gives a validated YAML sample of an EIP or component.
            3. Write the routes and their configuration: camel_validate_source checks a source, camel_write_file \
            validates and writes it.
            4. Run it (camel_run), read the log (camel_get_log) and the failed exchanges (camel_get_errors), and \
            send it the messages the old integration handled (camel_runtime_send) to compare the results.

            Upgrading a Camel project to a newer Camel version instead: use the camel_migrate_project prompt, which \
            starts with camel_migration_analyze.
            """;

    @ConfigProperty(name = "quarkus.mcp.server.server-info.instructions")
    Optional<String> configured = Optional.empty();

    private final String text;

    public McpServerInstructions() {
        this(new DefaultCamelCatalog().getCatalogVersion());
    }

    McpServerInstructions(String camelVersion) {
        this.text = build(camelVersion);
    }

    /** The instructions text for the given Camel version. */
    static String build(String camelVersion) {
        return TEMPLATE.formatted(version(camelVersion)).strip();
    }

    /** The rules and workflow of a migration of an integration to Camel, for the given Camel version. */
    static String migration(String camelVersion) {
        return MIGRATION_TEMPLATE.formatted(version(camelVersion)).strip();
    }

    /** The rules and workflow of a migration of an integration to Camel, for the Camel version of the catalog. */
    static String migration() {
        return migration(new DefaultCamelCatalog().getCatalogVersion());
    }

    private static String version(String camelVersion) {
        return camelVersion == null || camelVersion.isBlank() ? "(unknown version)" : camelVersion.trim();
    }

    String text() {
        return text;
    }

    @Override
    public Optional<String> instructions(String serverName) {
        if (configured != null && configured.filter(s -> !s.isBlank()).isPresent()) {
            // an explicit quarkus.mcp.server.server-info.instructions wins: empty lets Quarkus fall back to it
            return Optional.empty();
        }
        return Optional.of(text);
    }
}
