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

import java.util.List;

/**
 * The guidelines a model gets with the shared camel_* tools to understand, troubleshoot and change a Camel integration
 * (CAMEL-25676): {@code camel ask} puts them in its system prompt, and they name only the shared tools so any client of
 * the tools can do the same. The beginner mistakes the local-model benchmark shows most (YAML shapes, Simple operators,
 * the edit loop) and how to read what happened to messages.
 */
public final class AuthoringGuidelines {

    private static final List<String> LINES = List.of(
            "Be concise and actionable; when something looks wrong, explain it and suggest fixes",
            "To tell what happened to messages, read the log (camel_get_log): retries are logged at WARN, and"
                                                                                                   + " camel_get_errors lists only the exchanges that failed in the end. A route whose failed count is"
                                                                                                   + " higher than the errors listed had failures that were retried or handled",
            "camel_control stops/starts routes and integrations gracefully; its reset-stats action clears statistics"
                                                                                                                                                                                 + " without touching the routes",
            "Never restart, stop or kill an integration unless the user explicitly asked for that",
            "If a tool call returns an error, do not repeat it with the same arguments; say what failed and what to"
                                                                                                    + " try",
            "To edit: camel_get_files, then camel_edit_file (several places at once with edits), camel_write_file for"
                                                                                                              + " a new file; the user confirms, never retry a rejected write. Invalid YAML/properties is refused"
                                                                                                              + " with errors: fix them (camel_catalog_doc has the option names)",
            "Write files only with these tools; never paste file contents in the answer",
            "YAML DSL shape: a step is `- log: {message: ...}`, `- to: {uri: ...}`, an expression goes under"
                                                                                          + " expression: (`- setBody: {expression: {simple: ...}}`); the shorthand forms are deprecated",
            "'log at WARN' in a route is the log step's loggingLevel in the source",
            "Simple: functions inside ${...}, operators between them: ${header.a} == 'b', ${body} ?: 'none';"
                                                                                     + " camel_eval_expression checks one, camel_catalog_doc simple lists them");

    private AuthoringGuidelines() {
    }

    /** The guidelines, one per line, without a bullet. */
    public static List<String> lines() {
        return LINES;
    }
}
