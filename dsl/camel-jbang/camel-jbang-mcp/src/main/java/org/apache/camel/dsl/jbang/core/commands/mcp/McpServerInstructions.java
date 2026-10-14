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
 * The {@code instructions} of the MCP {@code initialize} response: the rules an agent needs before its first tool call,
 * and which tool answers which question.
 * <p>
 * Hosts that defer tool schemas (Claude Code, Codex, OpenCode, Pi) give the model this text at session start, before
 * any tool description. Pi shows only the first line and Codex weighs the first 512 characters most, so the first line
 * is a one-sentence summary with the Camel version. The whole text stays under 4096 characters (what Claude Code
 * loads).
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
            "camel_catalog_doc", "camel_catalog_find", "camel_catalog_sample", "camel_catalog_examples",
            "camel_dependency_for_class", "camel_validate_source", "camel_run", "camel_get_log", "camel_get_errors",
            "camel_project_overview");

    /** The text, with the Camel version as its only parameter. Kept here so the whole text reviews in one place. */
    private static final String TEMPLATE = """
            Apache Camel %s catalog, validation and run tools: look components up by URI scheme (smtp, \
            platform-http) and validate with camel_validate_source before finishing.

            Rules:
            - Components are named by URI scheme: smtp/smtps/imap/pop3 (not mail), platform-http for an HTTP server, \
            http/https for an HTTP client. camel_catalog_find maps a protocol or product to the scheme.
            - YAML DSL: onException, errorHandler, routeConfiguration, intercept and onCompletion are top-level \
            entries beside route (or inside routeConfiguration), never steps. handled and continued are \
            expressions: handled: {constant: "true"}.
            - camel run: camel.server.port (or camel run --port) sets the HTTP port. Extra jars: a //DEPS line in a \
            source file, camel.jbang.dependencies=group:artifact:version in application.properties, or --dep on the \
            command line (camel_run has no dependencies argument, so use one of the first two).
            - mail producer: the Subject, From and To headers are ignored unless useHeaderSubject=true, \
            useHeaderFrom=true and useHeaderRecipients=true; otherwise use the subject, from and to options.
            - Before an http or mail producer, remove the Camel* headers you did not set yourself \
            (removeHeaders: {pattern: "Camel*"} when none are yours), so inbound CamelHttpPath, CamelHttpQuery or \
            CamelHttpUri do not redirect the call and no header leaks into the mail.
            - xslt receives the message headers and variables as stylesheet parameters (xsl:param).
            - Passing the YAML schema is not passing at runtime: run the app (camel_run) and read its log before \
            calling it done.

            Which tool:
            - options, URI syntax, headers of a component, EIP, data format or language: camel_catalog_doc
            - which component for a protocol or product: camel_catalog_find
            - a validated YAML sample and where it goes: camel_catalog_sample
            - complete example projects: camel_catalog_examples
            - Maven coordinates (the --dep value) of a Java class: camel_dependency_for_class
            - validate YAML/Java/XML/properties before writing: camel_validate_source
            - run an integration: camel_run; its log: camel_get_log; its errors: camel_get_errors
            - routes, endpoints and mistakes of an existing project: camel_project_overview
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
        String version = camelVersion == null || camelVersion.isBlank() ? "(unknown version)" : camelVersion.trim();
        return TEMPLATE.formatted(version).strip();
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
