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
package org.apache.camel.dsl.jbang.core.commands.action;

import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.NodeInfo;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.RouteInfo;
import org.apache.camel.dsl.jbang.core.commands.ai.RouteAssist;
import org.apache.camel.dsl.jbang.core.commands.ai.SourceValidator;

/**
 * The problems of the source files of a route diagram, on the steps they are of: the checks of {@code camel validate}
 * for a YAML route, and for Java and XML the checks that read the source without compiling it ({@link RouteAssist}), as
 * the route diagram reads Java routes without compiling them.
 */
final class RouteDiagramProblems {

    /** A problem of a line of the source; line 0 for a problem of the file. */
    record Problem(int line, String text) {
    }

    private static final Pattern MESSAGE_LINE = Pattern.compile("^Line (\\d+): (.*)$", Pattern.DOTALL);
    // the step and route a message names first, which the diagram shows already: "setBody: route orders: "
    private static final Pattern STEP_PREFIX = Pattern.compile("^[A-Za-z][\\w-]*: route [^:\\s]+: ");

    private RouteDiagramProblems() {
    }

    /**
     * Puts the problems of the source files on the steps of their routes ({@link NodeInfo#problems}): a problem on the
     * step whose line is the last one before it (the line of the option or expression that is wrong is inside its
     * step), a problem of the file on the first step: also that the file cannot be read to check it.
     */
    static void attach(List<RouteInfo> routes, List<String> files) {
        CamelCatalog catalog = null;
        for (String file : files) {
            Path path = Path.of(file);
            if (!Files.isRegularFile(path)) {
                continue;
            }
            String name = path.getFileName().toString();
            List<NodeInfo> nodes = routes.stream()
                    .filter(r -> sourceOf(r, name))
                    .flatMap(r -> r.nodes.stream())
                    .toList();
            if (nodes.isEmpty()) {
                continue;
            }
            String content;
            try {
                content = Files.readString(path);
            } catch (IOException e) {
                // the diagram shows the routes of the file, so it says why their problems are not there
                add(nodes.get(0), "the file cannot be read to check it: "
                                  + (e instanceof CharacterCodingException ? "it is not UTF-8 text" : e.getMessage()));
                continue;
            }
            if (catalog == null) {
                catalog = new DefaultCamelCatalog();
            }
            for (Problem p : problems(name, content, catalog, path.toAbsolutePath().getParent())) {
                add(stepAt(nodes, p.line()), p.text());
            }
        }
    }

    private static void add(NodeInfo node, String problem) {
        List<String> list = new ArrayList<>(node.problems);
        list.add(problem);
        node.problems = List.copyOf(list);
    }

    /** The problems of a source file. */
    static List<Problem> problems(String name, String content, CamelCatalog catalog, Path directory) {
        String lower = name.toLowerCase(Locale.ROOT);
        List<Problem> answer = new ArrayList<>();
        if (lower.endsWith(".yaml") || lower.endsWith(".yml")) {
            for (String msg : SourceValidator.validate(name, content, catalog, null, directory)) {
                answer.add(problem(msg));
            }
        } else if (RouteAssist.supports(name, content)) {
            for (RouteAssist.Diagnostic d : RouteAssist.diagnostics(name, content, catalog, directory)) {
                if (d.severity() == RouteAssist.Severity.ERROR) {
                    answer.add(new Problem(d.line(), text(d.message())));
                }
            }
        }
        return answer;
    }

    /** A message of the validator, "Line 7: setBody: route orders: groovy reads ...", as a problem. */
    static Problem problem(String message) {
        Matcher m = MESSAGE_LINE.matcher(message);
        if (m.matches()) {
            return new Problem(Integer.parseInt(m.group(1)), text(m.group(2)));
        }
        return new Problem(0, text(message));
    }

    /**
     * The text of a problem for a callout: its first line (more lines are the source to write, which the diagram has no
     * room for), without the step and route it names first, which the callout points at.
     */
    static String text(String message) {
        String text = message.strip();
        int nl = text.indexOf('\n');
        if (nl > 0) {
            text = text.substring(0, nl).strip();
        }
        return STEP_PREFIX.matcher(text).replaceFirst("");
    }

    /** The step of a line: the last step that starts at or before it, or the first step. */
    static NodeInfo stepAt(List<NodeInfo> nodes, int line) {
        NodeInfo answer = nodes.get(0);
        for (NodeInfo n : nodes) {
            if (n.line > 0 && n.line <= line && n.line >= answer.line) {
                answer = n;
            }
        }
        return answer;
    }

    /** Whether the route comes from the file: its source is the file name, or the class name of a Java file. */
    private static boolean sourceOf(RouteInfo route, String fileName) {
        return route.source != null && (route.source.equals(fileName) || fileName.startsWith(route.source + "."));
    }
}
