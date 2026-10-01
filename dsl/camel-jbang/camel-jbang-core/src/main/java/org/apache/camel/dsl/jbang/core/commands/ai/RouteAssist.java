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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.catalog.CamelCatalog;

/**
 * Camel assistance for the routes of a Java or XML DSL source, for any tool: the TUI Source tab, the MCP tools, the
 * camel validate CLI, or an editor. The operations are those of a language server - the problems of a source, what is
 * on a line - without the protocol: the source is read into the Camel model ({@link RouteModel}) and its nodes by line
 * ({@link RouteNodes}), so the answers are the same whatever the DSL (CAMEL-25208).
 */
public final class RouteAssist {

    /** How sure a diagnostic is. */
    public enum Severity {
        /** The source is wrong: the route fails to start, or does not do what it says. */
        ERROR,
        /** Something to know, such as a part of the source that was not checked. */
        INFO
    }

    /**
     * A problem of a source.
     *
     * @param line     the line, 1-based; 0 when it is about the whole source
     * @param severity how sure it is
     * @param message  what is wrong, and what to write instead
     */
    public record Diagnostic(int line, Severity severity, String message) {

        /** The diagnostic as the validators write it: "Line N: message". */
        public String format() {
            return line > 0 ? "Line " + line + ": " + message : message;
        }
    }

    /**
     * The parts the parser does not read that are the application's own code (a lambda, an object it creates, one of
     * its classes): nothing of Camel's is left unchecked there, so they are not reported.
     */
    private static final Set<String> CODE
            = Set.of("a lambda or method reference", "an object created in the route", "an anonymous class",
                    "a class of the project");

    private static final Pattern LINE_PREFIX = Pattern.compile("(?s)^Line (\\d+): (.*)");

    private RouteAssist() {
    }

    /** Whether the file is a Java or XML DSL source the assistance reads. */
    public static boolean supports(String fileName, String content) {
        return RouteModel.dslOf(fileName, content) != null;
    }

    /**
     * The problems of a Java or XML DSL source: errors (an endpoint option the component does not have, a simple
     * expression that does not parse, a to that needs toD, a direct: endpoint no route consumes) and what was not
     * checked (a part the parser could not read). Empty for a file that is not a Java or XML DSL source.
     *
     * @param directory the directory of the application's files, for the constants of the other Java classes and the
     *                  endpoints the other route files consume; null to check the source on its own
     */
    public static List<Diagnostic> diagnostics(String fileName, String content, CamelCatalog catalog, Path directory) {
        return diagnostics(fileName, content, catalog, directory, javaSources(directory), directory != null);
    }

    /**
     * As {@link #diagnostics(String, String, CamelCatalog, Path)} with the Java sources of the project given, and
     * whether to check the direct: and seda: endpoints no route consumes (a tool that writes one file after another
     * leaves it out: the consumer is often a file not written yet).
     */
    public static List<Diagnostic> diagnostics(
            String fileName, String content, CamelCatalog catalog, Path directory,
            Map<String, Supplier<String>> javaSources, boolean checkConsumers) {
        RouteModel model = RouteModel.read(fileName, content, catalog, javaSources);
        if (model == null) {
            return List.of();
        }
        List<Diagnostic> answer = new ArrayList<>();
        for (RouteModel.Unread u : model.unread()) {
            if (u.error()) {
                answer.add(new Diagnostic(u.line(), Severity.ERROR, u.reason()));
            }
        }
        List<RouteNodes.Node> nodes = RouteNodes.withExpressionLines(RouteNodes.of(model, catalog), content);
        Set<String> consumed = checkConsumers && directory != null
                ? EndpointConsumerChecks.consumed(directory, fileName, catalog) : null;
        for (String msg : ModelChecks.check(model, nodes, content, catalog, consumed)) {
            answer.add(parse(msg, Severity.ERROR));
        }
        for (RouteModel.Unread u : model.unread()) {
            if (!u.error() && !CODE.contains(u.reason())) {
                answer.add(new Diagnostic(u.line(), Severity.INFO, "not checked: " + describe(u)));
            }
        }
        answer.sort((a, b) -> Integer.compare(a.line(), b.line()));
        return answer;
    }

    /**
     * The nodes of a Java or XML DSL source by line - steps, endpoints, expressions - for a quick doc or a hover; empty
     * for a file that is not one.
     */
    public static List<RouteNodes.Node> nodes(
            String fileName, String content, CamelCatalog catalog, Map<String, Supplier<String>> javaSources) {
        RouteModel model = RouteModel.read(fileName, content, catalog, javaSources);
        return model != null ? RouteNodes.withExpressionLines(RouteNodes.of(model, catalog), content) : List.of();
    }

    /**
     * The Java sources of the project under the directory, read when first needed, for the constants a route refers to
     * in another class.
     */
    public static Map<String, Supplier<String>> javaSources(Path directory) {
        Map<String, Supplier<String>> answer = new LinkedHashMap<>();
        if (directory == null || !Files.isDirectory(directory)) {
            return answer;
        }
        for (Path p : AuthoringTools.projectFiles(directory)) {
            String rel = AuthoringTools.relativePath(directory, p);
            if (rel.endsWith(".java")) {
                answer.put(rel, () -> {
                    try {
                        return Files.readString(p, StandardCharsets.UTF_8);
                    } catch (IOException e) {
                        return null;
                    }
                });
            }
        }
        return answer;
    }

    private static String describe(RouteModel.Unread u) {
        String text = u.text();
        if (text == null || text.isBlank()) {
            return u.reason();
        }
        text = text.strip().replaceAll("\\s+", " ");
        if (text.length() > 60) {
            text = text.substring(0, 57) + "...";
        }
        return text + " (" + u.reason() + ")";
    }

    private static Diagnostic parse(String msg, Severity severity) {
        Matcher m = LINE_PREFIX.matcher(msg);
        if (m.matches()) {
            return new Diagnostic(Integer.parseInt(m.group(1)), severity, m.group(2));
        }
        return new Diagnostic(0, severity, msg);
    }
}
