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

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.ErrorHandlerFactory;
import org.apache.camel.NamedNode;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.dsl.jbang.core.commands.ai.ProjectRoutes.Endpoint;
import org.apache.camel.dsl.jbang.core.commands.ai.ProjectRoutes.Route;
import org.apache.camel.java.in.ConstantResolver;
import org.apache.camel.java.in.JavaParseResult;
import org.apache.camel.java.in.LwJavaParser;
import org.apache.camel.model.CatchDefinition;
import org.apache.camel.model.ChoiceDefinition;
import org.apache.camel.model.DynamicRouterDefinition;
import org.apache.camel.model.EnrichDefinition;
import org.apache.camel.model.FinallyDefinition;
import org.apache.camel.model.KameletDefinition;
import org.apache.camel.model.LogDefinition;
import org.apache.camel.model.OnExceptionDefinition;
import org.apache.camel.model.PollDefinition;
import org.apache.camel.model.PollEnrichDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.RecipientListDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RouteTemplateDefinition;
import org.apache.camel.model.RoutingSlipDefinition;
import org.apache.camel.model.SendDefinition;
import org.apache.camel.model.SwitchCaseDefinition;
import org.apache.camel.model.SwitchDefinition;
import org.apache.camel.model.ToDynamicDefinition;
import org.apache.camel.model.TryDefinition;
import org.apache.camel.model.errorhandler.DeadLetterChannelDefinition;
import org.apache.camel.model.language.ConstantExpression;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.model.rest.RestDefinition;
import org.apache.camel.model.rest.VerbDefinition;

/**
 * The routes of a Java RouteBuilder for the project overview, read into the Camel model by the Java DSL parser of
 * camel-java-io (CAMEL-25148) without compiling it: nesting, error paths, constants and the endpoint DSL are seen as in
 * a compiled route. A route with a part the parser could not work out (a lambda, a value from a helper method) is
 * marked heuristic, as its endpoints may be incomplete.
 */
final class JavaRouteReader {

    private static final int MAX_DEPTH = 50;

    private final String file;
    private final CamelCatalog catalog;
    private final List<Route> routes = new ArrayList<>();

    private JavaRouteReader(String file, CamelCatalog catalog) {
        this.file = file;
        this.catalog = catalog;
    }

    /** The routes of the source, or an empty list when the parser finds none. */
    static List<Route> read(String file, String content, CamelCatalog catalog, ConstantResolver constants) {
        JavaParseResult result = parse(content, catalog, constants);
        JavaRouteReader reader = new JavaRouteReader(file, catalog);
        List<Integer> starts = new ArrayList<>();
        result.routes().getRoutes().forEach(r -> starts.add(r.getLineNumber()));
        for (RouteDefinition r : result.routes().getRoutes()) {
            reader.route(r, null, partial(result, r.getLineNumber(), starts));
        }
        for (RouteTemplateDefinition t : result.routeTemplates().getRouteTemplates()) {
            if (t.getRoute() != null) {
                reader.route(t.getRoute(), t, false);
            }
        }
        for (RestDefinition rest : result.rests().getRests()) {
            reader.rest(rest);
        }
        reader.errorHandlers(result);
        return reader.routes;
    }

    /** The source read by the Java DSL parser, with the endpoint DSL and constants resolved through the catalog. */
    static JavaParseResult parse(String content, CamelCatalog catalog, ConstantResolver constants) {
        LwJavaParser parser = new LwJavaParser();
        if (catalog != null) {
            parser.setEndpointDslResolver(new CatalogEndpointDslResolver(catalog));
        }
        parser.setConstantResolver(constants != null
                ? constants : new ProjectConstantResolver(Map.of(), catalog));
        return parser.parse(content);
    }

    /** Whether something the parser did not work out lies between this route's line and the next route's. */
    private static boolean partial(JavaParseResult result, int line, List<Integer> starts) {
        int end = Integer.MAX_VALUE;
        for (int s : starts) {
            if (s > line && s < end) {
                end = s;
            }
        }
        for (JavaParseResult.Unresolved u : result.unresolved()) {
            if (u.line() >= line && u.line() < end && !JavaParseResult.configuresTheContext(u)) {
                return true;
            }
        }
        return false;
    }

    private void route(RouteDefinition r, RouteTemplateDefinition template, boolean partial) {
        String id = template != null ? template.getId() : r.getRouteId();
        String description = template != null && template.getDescription() != null
                ? template.getDescription() : r.getDescription();
        String note = template != null && template.getNote() != null ? template.getNote() : r.getNote();
        Endpoint from = r.getInput() != null ? ProjectRoutes.endpoint(r.getInput().getUri(), null, false, catalog) : null;
        List<Endpoint> consumes = new ArrayList<>();
        List<Endpoint> produces = new ArrayList<>();
        add(consumes, from);
        Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (ProcessorDefinition<?> p : r.getOutputs()) {
            walk(p, false, produces, consumes, seen, 0);
        }
        if (template != null && id != null) {
            consumes.add(ProjectRoutes.endpoint("kamelet:" + id, null, false, catalog));
        }
        int line = template != null && template.getLineNumber() > 0 ? template.getLineNumber() : r.getLineNumber();
        List<RouteDecisions.DecisionPoint> decisions = new ArrayList<>();
        Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        RouteDecisions.Scope scope = RouteDecisions.Scope.route();
        for (ProcessorDefinition<?> p : r.getOutputs()) {
            decisions(p, scope, decisions, visited, 0);
        }
        routes.add(new Route(
                id, template != null ? "routeTemplate" : "route", description, r.getGroup(), file, Math.max(1, line),
                "java", partial, from, consumes, produces, r.getOutputs().size(), -1, 0, null, null,
                logOnly(r, produces), note, decisions));
    }

    /** The decision points below a step, with their paths (see {@link RouteDecisions}). */
    private static void decisions(
            NamedNode node, RouteDecisions.Scope scope, List<RouteDecisions.DecisionPoint> found, Set<Object> visited,
            int depth) {
        if (node == null || depth > MAX_DEPTH || !visited.add(node)) {
            return;
        }
        String type = node.getShortName();
        RouteDecisions.Scope below = scope;
        if (RouteDecisions.TYPES.contains(type)) {
            below = scope.child(type);
            int line = node.getLineNumber();
            RouteDecisions.add(found, below, type, decisionText(node), line);
        }
        // the children as the running route tree has them: the when and otherwise of a choice, not their steps
        List<NamedNode> children = new ArrayList<>();
        if (node instanceof ChoiceDefinition choice) {
            children.addAll(choice.getWhenClauses());
            if (choice.getOtherwise() != null) {
                children.add(choice.getOtherwise());
            }
        } else if (node.getChildren() != null) {
            children.addAll(node.getChildren());
        }
        for (NamedNode c : children) {
            decisions(c, below, found, visited, depth + 1);
        }
    }

    /** What a decision point decides on: the part of its label in brackets, such as simple{${header.x} > 5}. */
    private static String decisionText(NamedNode node) {
        if (!RouteDecisions.hasExpression(node.getShortName())) {
            // a choice's label lists its branches
            return null;
        }
        String label = node.getLabel();
        int start = label != null ? label.indexOf('[') : -1;
        if (start < 0 || !label.endsWith("]")) {
            return null;
        }
        String text = label.substring(start + 1, label.length() - 1);
        // an expression the parser could not work out is marked, not text to show
        if (text.contains(LwJavaParser.UNRESOLVED_PREFIX)) {
            return null;
        }
        // simple{${body} > 5} as the YAML and XML readers give it: simple: ${body} > 5
        Matcher m = LANGUAGE_TEXT.matcher(text);
        return m.matches() ? m.group(1) + ": " + m.group(2) : text;
    }

    private static final Pattern LANGUAGE_TEXT = Pattern.compile("(\\w+)\\{(.*)\\}", Pattern.DOTALL);

    /** The endpoints below a step, whatever EIP nests them; those in doCatch or onException carry a failure. */
    private void walk(
            ProcessorDefinition<?> p, boolean onError, List<Endpoint> produces, List<Endpoint> consumes, Set<Object> seen,
            int depth) {
        if (p == null || depth > MAX_DEPTH || !seen.add(p)) {
            return;
        }
        boolean error = onError || p instanceof CatchDefinition || p instanceof OnExceptionDefinition;
        if (p instanceof ToDynamicDefinition d) {
            // toD and wireTap
            add(produces, mark(ProjectRoutes.endpoint(d.getUri(), null, true, catalog), error));
        } else if (p instanceof SendDefinition<?> s) {
            add(produces, mark(ProjectRoutes.endpoint(s.getUri(), null, false, catalog), error));
        } else if (p instanceof PollEnrichDefinition pe) {
            add(consumes, expressionEndpoint(pe.getExpression(), "pollEnrich"));
        } else if (p instanceof EnrichDefinition e) {
            add(produces, mark(expressionEndpoint(e.getExpression(), "enrich"), error));
        } else if (p instanceof PollDefinition poll) {
            add(consumes, ProjectRoutes.endpoint(poll.getUri(), null, true, catalog));
        } else if (p instanceof KameletDefinition k && k.getName() != null) {
            add(produces, mark(ProjectRoutes.endpoint("kamelet:" + k.getName(), null, false, catalog), error));
        } else if (p instanceof SwitchDefinition sw) {
            // the fixed destinations of a switch: its cases and fallback, which are not outputs
            for (SwitchCaseDefinition c : sw.getCases()) {
                add(produces, mark(ProjectRoutes.endpoint(c.getUri(), null, false, catalog), error));
            }
            if (sw.getOtherwiseDefinition() != null) {
                add(produces, mark(ProjectRoutes.endpoint(sw.getOtherwiseDefinition().getUri(), null, false, catalog), error));
            }
        } else if (p instanceof RecipientListDefinition || p instanceof RoutingSlipDefinition
                || p instanceof DynamicRouterDefinition) {
            String eip = p.getShortName();
            produces.add(mark(new Endpoint("(" + eip + ")", eip, "dynamic:" + eip, true, null), error));
        }
        for (ProcessorDefinition<?> child : p.getOutputs()) {
            walk(child, error, produces, consumes, seen, depth + 1);
        }
        if (p instanceof TryDefinition t) {
            for (CatchDefinition c : t.getCatchClauses()) {
                walk(c, true, produces, consumes, seen, depth + 1);
            }
            FinallyDefinition f = t.getFinallyClause();
            walk(f, onError, produces, consumes, seen, depth + 1);
        }
    }

    /** The endpoint of enrich/pollEnrich: its URI when a constant, else a dynamic one. */
    private Endpoint expressionEndpoint(ExpressionDefinition e, String eip) {
        if (e instanceof ConstantExpression c && c.getExpression() != null) {
            return ProjectRoutes.endpoint(c.getExpression(), null, true, catalog);
        }
        return new Endpoint("(" + eip + ")", eip, "dynamic:" + eip, true, null);
    }

    private static Endpoint mark(Endpoint e, boolean onError) {
        return e != null && onError ? e.asOnError() : e;
    }

    private static void add(List<Endpoint> list, Endpoint e) {
        if (e != null && list.stream().noneMatch(x -> x.key().equals(e.key()))) {
            list.add(e);
        }
    }

    /** Whether the route's steps only log. */
    private static boolean logOnly(RouteDefinition r, List<Endpoint> produces) {
        if (r.getOutputs().isEmpty()) {
            return false;
        }
        for (ProcessorDefinition<?> p : r.getOutputs()) {
            boolean logs = p instanceof LogDefinition
                    || p instanceof SendDefinition<?> s && s.getUri() != null && s.getUri().startsWith("log:");
            if (!logs) {
                return false;
            }
        }
        return produces.stream().allMatch(e -> "log".equals(e.scheme()));
    }

    private void rest(RestDefinition rest) {
        for (VerbDefinition v : rest.getVerbs()) {
            List<Endpoint> produces = new ArrayList<>();
            if (v.getTo() != null) {
                add(produces, ProjectRoutes.endpoint(v.getTo().getUri(), null, false, catalog));
            }
            String path = ProjectRoutes.joinPath(rest.getPath(), v.getPath());
            routes.add(new Route(
                    v.getRouteId(), "rest", v.getDescription(), null, file, Math.max(1, v.getLineNumber()), "java",
                    false, null, List.of(), produces, 1, -1, 0, v.asVerb().toUpperCase(Locale.ROOT), path, false,
                    null));
        }
    }

    /** onException and dead letter channels outside a route, as one error handler route of the file. */
    private void errorHandlers(JavaParseResult result) {
        List<Endpoint> produces = new ArrayList<>();
        int line = Integer.MAX_VALUE;
        Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (OnExceptionDefinition oe : result.routes().getOnExceptions()) {
            walk(oe, true, produces, new ArrayList<>(), seen, 0);
            if (oe.getLineNumber() > 0) {
                line = Math.min(line, oe.getLineNumber());
            }
        }
        for (RouteDefinition r : result.routes().getRoutes()) {
            ErrorHandlerFactory eh = r.getErrorHandlerFactory();
            if (eh instanceof DeadLetterChannelDefinition dlc && dlc.getDeadLetterUri() != null) {
                add(produces, ProjectRoutes.endpoint(dlc.getDeadLetterUri(), null, false, catalog).asOnError());
                line = Math.min(line, Math.max(1, r.getLineNumber()));
            }
        }
        if (!produces.isEmpty()) {
            int at = line == Integer.MAX_VALUE ? 1 : line;
            routes.add(new Route(
                    ProjectRoutes.ERROR_HANDLER_PREFIX + file + ":" + at, "errorHandler", null, null, file, at, "java",
                    false, null, List.of(), produces.stream().map(Endpoint::asOnError).toList(), 0, -1, 0, null, null,
                    false, null));
        }
    }
}
