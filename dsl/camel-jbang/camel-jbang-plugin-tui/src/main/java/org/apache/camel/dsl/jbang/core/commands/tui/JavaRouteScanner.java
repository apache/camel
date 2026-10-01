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

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.dsl.jbang.core.commands.ai.ProjectRoutes;
import org.apache.camel.model.CatchDefinition;
import org.apache.camel.model.EnrichDefinition;
import org.apache.camel.model.ExpressionNode;
import org.apache.camel.model.PollEnrichDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.SendDefinition;
import org.apache.camel.model.SwitchCaseDefinition;
import org.apache.camel.model.SwitchDefinition;
import org.apache.camel.model.ToDynamicDefinition;
import org.apache.camel.model.TryDefinition;
import org.apache.camel.model.language.ConstantExpression;

/**
 * The routes of a Java DSL source for the jump links of the Source tab: each route's from endpoint and the endpoints it
 * sends to, with their lines. The source is read into the model by the Java DSL parser of camel-java-io, without
 * compiling or running it, so the endpoint DSL and constants are resolved as in the project overview.
 */
final class JavaRouteScanner {

    private static final int MAX_DEPTH = 50;

    private JavaRouteScanner() {
    }

    /** Whether the file is a Java source with a route builder. */
    static boolean isJavaRoutes(String fileName, String content) {
        return fileName.endsWith(".java") && content != null && content.contains("RouteBuilder");
    }

    /**
     * The routes of a Java source.
     *
     * @param javaSources the Java sources of the project by path, for the constants of other classes
     */
    static List<ScannedRoute> scan(String content, Map<String, Supplier<String>> javaSources, CamelCatalog catalog) {
        List<ScannedRoute> answer = new ArrayList<>();
        for (RouteDefinition r : ProjectRoutes.parseJava(content, javaSources, catalog).routes().getRoutes()) {
            if (r.getInput() == null || r.getInput().getUri() == null) {
                continue;
            }
            int line = r.getInput().getLineNumber() > 0 ? r.getInput().getLineNumber() : r.getLineNumber();
            List<ScannedRoute.To> tos = new ArrayList<>();
            Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
            for (ProcessorDefinition<?> p : r.getOutputs()) {
                walk(p, tos, seen, 0);
            }
            answer.add(new ScannedRoute(r.getRouteId(), uri(r.getInput().getUri()), Math.max(0, line - 1), tos));
        }
        return answer;
    }

    private static void walk(ProcessorDefinition<?> p, List<ScannedRoute.To> tos, Set<Object> seen, int depth) {
        if (p == null || depth > MAX_DEPTH || !seen.add(p)) {
            return;
        }
        String uri = null;
        if (p instanceof ToDynamicDefinition d) {
            // toD and wireTap
            uri = d.getUri();
        } else if (p instanceof SendDefinition<?> s) {
            uri = s.getUri();
        } else if ((p instanceof EnrichDefinition || p instanceof PollEnrichDefinition)
                && ((ExpressionNode) p).getExpression() instanceof ConstantExpression c) {
            uri = c.getExpression();
        }
        if (uri != null && p.getLineNumber() > 0) {
            tos.add(new ScannedRoute.To(uri(uri), p.getLineNumber() - 1));
        }
        if (p instanceof SwitchDefinition sw) {
            // the destinations of a switch are its cases and fallback, not outputs
            for (SwitchCaseDefinition c : sw.getCases()) {
                add(tos, c.getUri(), c.getLineNumber());
            }
            if (sw.getOtherwiseDefinition() != null) {
                add(tos, sw.getOtherwiseDefinition().getUri(), sw.getOtherwiseDefinition().getLineNumber());
            }
        }
        for (ProcessorDefinition<?> child : p.getOutputs()) {
            walk(child, tos, seen, depth + 1);
        }
        if (p instanceof TryDefinition t) {
            for (CatchDefinition c : t.getCatchClauses()) {
                walk(c, tos, seen, depth + 1);
            }
            walk(t.getFinallyClause(), tos, seen, depth + 1);
        }
    }

    private static void add(List<ScannedRoute.To> tos, String uri, int line) {
        if (uri != null && line > 0) {
            tos.add(new ScannedRoute.To(uri(uri), line - 1));
        }
    }

    /** direct://orders from the endpoint DSL as direct:orders, as a route written as text has it. */
    static String uri(String uri) {
        int colon = uri.indexOf(':');
        if (colon > 0 && uri.startsWith("//", colon + 1)) {
            return uri.substring(0, colon + 1) + uri.substring(colon + 3);
        }
        return uri;
    }
}
