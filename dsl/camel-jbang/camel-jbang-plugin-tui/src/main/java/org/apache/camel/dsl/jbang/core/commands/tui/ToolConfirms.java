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
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Locale;
import java.util.Set;

import org.apache.camel.util.URISupport;

/**
 * The calls of the AI tools that the user confirms first. Camel TUI is a developer tool: the AI panel's model, an ACP
 * agent and an MCP client run its tools without asking, so the AI can show and tell at once (see "Developer tools" in
 * the security model). The exceptions are the calls that reach beyond the integration, may destroy data or run code:
 * <ul>
 * <li>tui_send_message to an endpoint the integration does not have; a route id, a pattern and an internal endpoint
 * (direct:, seda:, log:, mock: and the like) stay inside it;</li>
 * <li>tui_execute_sql with a statement that is not a single SELECT (or SHOW, DESCRIBE), and tui_update_row;</li>
 * <li>camel_eval_expression in a language that runs code, such as Groovy.</li>
 * </ul>
 * A file write has its own confirmation (the write mode), and tui_http_request reaches only the integration's own
 * server. While a tape drives the monitor nothing asks ({@link McpFacade#confirm}).
 */
final class ToolConfirms {

    /** A call the user confirms: what the AI wants to do, the call (an endpoint, a statement, an expression), why. */
    record Call(String title, String target, String reason) {
    }

    /** The expression languages that run code, in the integration (or the TUI when none is selected). */
    static final Set<String> CODE_LANGUAGES
            = Set.of("groovy", "java", "joor", "js", "javascript", "python", "mvel", "ognl", "spel");

    /**
     * The components that stay inside the integration: its routes and in-memory queues, its log and the test endpoints.
     * Not every component the catalog marks remote=false: exec, class, bean, language and the components that load a
     * template or a stylesheet by URI are not remote either, but run code, or read a file or a URL.
     */
    static final Set<String> INTERNAL_SCHEMES = Set.of(
            "direct", "seda", "disruptor", "disruptor-vm", "log", "mock", "stub", "browse", "dataset", "dataset-test");

    private ToolConfirms() {
    }

    /** A message to an endpoint the integration does not have, or null when it stays inside the integration. */
    static Call sendMessage(String endpoint, Collection<String> endpointUris) {
        if (staysInside(endpoint, endpointUris)) {
            return null;
        }
        return new Call(
                "send a message to an endpoint the integration does not have", endpoint,
                "The message may leave the integration: to a file, a broker or a service");
    }

    /**
     * Whether a send stays inside the integration: a route id or a pattern (the send console picks one of the routes),
     * an internal endpoint, or an endpoint the integration has (its endpoint registry, as its status lists it).
     */
    static boolean staysInside(String endpoint, Collection<String> endpointUris) {
        // the same tests as SendDevConsole: without a scheme, or ending with *, it names a route of the integration
        if (!endpoint.contains(":") || endpoint.endsWith("*")) {
            return true;
        }
        // an option can still reach out: #class:...(args) runs any constructor (a FileOutputStream truncates a file),
        // and a {{placeholder}} can put a secret in the log
        String decoded = decode(endpoint);
        if (INTERNAL_SCHEMES.contains(endpoint.substring(0, endpoint.indexOf(':')))
                && !decoded.contains("#") && !decoded.contains("{{")) {
            return true;
        }
        String uri = normalize(endpoint);
        return uri != null && endpointUris.stream().anyMatch(e -> uri.equals(normalize(e)));
    }

    /**
     * The normalized URI, decoded: normalizing keeps an option as it is written, localhost%3A9092 or localhost:9092.
     */
    private static String normalize(String uri) {
        try {
            return uri != null ? decode(URISupport.normalizeUri(uri)) : null;
        } catch (URISyntaxException | RuntimeException e) {
            return null;
        }
    }

    private static String decode(String uri) {
        try {
            return URLDecoder.decode(uri.replace("+", "%2B"), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            // a lone % in an option
            return uri;
        }
    }

    /** A statement that is not a single SELECT (or SHOW, DESCRIBE), or null for one. */
    static Call sql(String query) {
        if (readsOnly(query)) {
            return null;
        }
        return new Call("run SQL that is not a SELECT", query, "It may change or drop data in the integration's database");
    }

    static Call updateRow(String table, String columnValues) {
        return new Call("update a row of " + table, columnValues, "It changes data in the integration's database");
    }

    /**
     * Whether a statement only reads: a single SELECT, SHOW or DESCRIBE, with no INTO (which creates a table or writes
     * a file). EXPLAIN is not one, as EXPLAIN ANALYZE runs the statement. Comments and the text of literals do not
     * count, so {@code SELECT ';'} is one statement, and {@code SELECT 1; DROP TABLE t} is two.
     */
    static boolean readsOnly(String sql) {
        String code = codeOnly(sql).strip();
        while (code.endsWith(";")) {
            code = code.substring(0, code.length() - 1).strip();
        }
        while (code.startsWith("(")) {
            code = code.substring(1).strip();
        }
        String lower = code.toLowerCase(Locale.ROOT);
        return lower.matches("(select|show|describe|desc)\\b[\\s\\S]*") && !lower.contains(";")
                && !lower.matches("[\\s\\S]*\\binto\\b[\\s\\S]*");
    }

    /** The statement without comments, and with each literal or quoted name as a blank. */
    private static String codeOnly(String sql) {
        StringBuilder code = new StringBuilder();
        int i = 0;
        int n = sql == null ? 0 : sql.length();
        while (i < n) {
            char c = sql.charAt(i);
            if (c == '-' && i + 1 < n && sql.charAt(i + 1) == '-') {
                i = sql.indexOf('\n', i);
                i = i < 0 ? n : i;
            } else if (c == '/' && i + 1 < n && sql.charAt(i + 1) == '*') {
                int end = sql.indexOf("*/", i + 2);
                i = end < 0 ? n : end + 2;
                code.append(' ');
            } else if (c == '\'' || c == '"' || c == '`') {
                // to the closing quote; a doubled quote is part of the text
                int j = i + 1;
                while (j < n && (sql.charAt(j) != c || j + 1 < n && sql.charAt(j + 1) == c)) {
                    j += sql.charAt(j) == c ? 2 : 1;
                }
                i = j + 1;
                code.append(' ');
            } else {
                code.append(c);
                i++;
            }
        }
        return code.toString();
    }

    /** An expression in a language that runs code, or null. */
    static Call evalExpression(String language, String expression) {
        String lang = language == null ? "" : language.strip();
        if (!CODE_LANGUAGES.contains(lang.toLowerCase(Locale.ROOT))) {
            return null;
        }
        return new Call("evaluate a " + lang + " expression", expression, "It runs code on this machine");
    }
}
