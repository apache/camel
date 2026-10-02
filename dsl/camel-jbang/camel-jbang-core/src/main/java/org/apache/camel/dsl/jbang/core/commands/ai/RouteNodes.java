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

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.LineNumberAware;
import org.apache.camel.NamedNode;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.model.EnrichDefinition;
import org.apache.camel.model.ExpressionNode;
import org.apache.camel.model.ExpressionSubElementDefinition;
import org.apache.camel.model.FromDefinition;
import org.apache.camel.model.InterceptFromDefinition;
import org.apache.camel.model.InterceptSendToEndpointDefinition;
import org.apache.camel.model.LogDefinition;
import org.apache.camel.model.LoopDefinition;
import org.apache.camel.model.PollDefinition;
import org.apache.camel.model.PollEnrichDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.RouteConfigurationDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.SendDefinition;
import org.apache.camel.model.SwitchCaseDefinition;
import org.apache.camel.model.SwitchDefinition;
import org.apache.camel.model.SwitchOtherwiseDefinition;
import org.apache.camel.model.ToDynamicDefinition;
import org.apache.camel.model.language.ConstantExpression;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.model.rest.RestDefinition;
import org.apache.camel.tooling.model.EipModel;

/**
 * The nodes of a {@link RouteModel} by line: each step, each endpoint it consumes from or sends to, and each expression
 * with its language. What a tool asks of a line - a quick doc, a check - it asks of these nodes, whatever the DSL the
 * source is written in.
 * <p/>
 * A node without a line of its own (an expression, the endpoint of a rest verb) takes the line of the step it belongs
 * to.
 */
public final class RouteNodes {

    private static final int MAX_DEPTH = 64;

    /** What a node is. */
    public enum Kind {
        /** A step: an EIP such as filter, split, to. */
        STEP,
        /** An endpoint a step consumes from or sends to. */
        ENDPOINT,
        /** An expression or predicate of a step. */
        EXPRESSION
    }

    /**
     * A node of a route.
     *
     * @param kind      what the node is
     * @param line      the line, 1-based; 0 when not known
     * @param eip       the EIP: of the step, of the step an endpoint belongs to (from, to, toD, wireTap...), or of the
     *                  step an expression belongs to
     * @param uri       the endpoint uri (ENDPOINT)
     * @param language  the language (EXPRESSION)
     * @param text      the text of the expression (EXPRESSION)
     * @param option    the option of the step the expression is (expression, completionPredicate, message...)
     * @param predicate whether the step evaluates the expression as a predicate
     * @param parents   the EIPs the node is nested in, outermost first
     */
    public record Node(
            Kind kind, int line, String eip, String uri, String language, String text, String option, boolean predicate,
            List<String> parents) {

        /** Whether the node is nested in the given EIP, at any depth. */
        public boolean isInside(String parentEip) {
            return parents.contains(parentEip);
        }
    }

    private final CamelCatalog catalog;
    private final List<Node> nodes = new ArrayList<>();
    private final Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());

    private RouteNodes(CamelCatalog catalog) {
        this.catalog = catalog;
    }

    /**
     * The nodes of the model, by line.
     *
     * @param catalog to know which expressions are predicates; may be null
     */
    public static List<Node> of(RouteModel model, CamelCatalog catalog) {
        RouteNodes walker = new RouteNodes(catalog);
        for (RouteDefinition route : model.routes()) {
            walker.route(route);
        }
        for (RouteConfigurationDefinition rc : model.routeConfigurations()) {
            walker.walk(rc, lineOf(rc, 0), List.of(), 0);
        }
        for (RestDefinition rest : model.rests()) {
            walker.walk(rest, lineOf(rest, 0), List.of("rest"), 0);
        }
        List<Node> answer = new ArrayList<>(walker.nodes);
        answer.sort(Comparator.comparingInt(Node::line));
        return answer;
    }

    /**
     * The nodes with each expression on the line its text is on, which is often not the line of its step: the
     * {@code <simple>} element under a {@code <when>}, the {@code simple("...")} of a Java call written over several
     * lines. The first line at or after the step's that has the start of the text (or, in XML, the element of its
     * language) is taken; the step's line stays when there is none.
     */
    public static List<Node> withExpressionLines(List<Node> nodes, String content) {
        String[] lines = content.split("\n", -1);
        List<Node> answer = new ArrayList<>(nodes.size());
        for (Node n : nodes) {
            if (n.kind() == Kind.EXPRESSION && n.line() > 0 && n.text() != null && !n.text().isBlank()) {
                int found = expressionLine(lines, n);
                if (found > 0 && found != n.line()) {
                    n = new Node(
                            n.kind(), found, n.eip(), n.uri(), n.language(), n.text(), n.option(), n.predicate(),
                            n.parents());
                }
            }
            answer.add(n);
        }
        answer.sort(Comparator.comparingInt(Node::line));
        return answer;
    }

    private static int expressionLine(String[] lines, Node n) {
        String text = n.text().strip();
        // the start of the text up to what a DSL may write escaped (a quote, a < or & in XML)
        int cut = 0;
        while (cut < text.length() && cut < 20 && "\"'<>&\\\n".indexOf(text.charAt(cut)) < 0) {
            cut++;
        }
        String start = text.substring(0, cut);
        String tag = "<" + n.language();
        for (int i = n.line() - 1; i < lines.length && i < n.line() - 1 + 15; i++) {
            if (start.length() >= 3 && lines[i].contains(start) || lines[i].contains(tag + ">")
                    || lines[i].contains(tag + " ")) {
                return i + 1;
            }
        }
        return 0;
    }

    /** The nodes on a line. */
    public static List<Node> at(List<Node> nodes, int line) {
        List<Node> answer = new ArrayList<>();
        for (Node n : nodes) {
            if (n.line() == line) {
                answer.add(n);
            }
        }
        return answer;
    }

    private void route(RouteDefinition route) {
        if (!seen.add(route)) {
            return;
        }
        int line = lineOf(route, 0);
        FromDefinition from = route.getInput();
        if (from != null) {
            int fromLine = lineOf(from, line);
            nodes.add(new Node(Kind.STEP, fromLine, "from", null, null, null, null, false, List.of()));
            if (from.getUri() != null) {
                nodes.add(new Node(Kind.ENDPOINT, fromLine, "from", from.getUri(), null, null, null, false, List.of()));
            }
            line = line > 0 ? line : fromLine;
        }
        for (ProcessorDefinition<?> p : route.getOutputs()) {
            walk(p, line, List.of(), 0);
        }
    }

    /** Walks a model object: a step, or an object of the model that holds steps and expressions. */
    private void walk(Object o, int ownerLine, List<String> parents, int depth) {
        if (o == null || depth > MAX_DEPTH) {
            return;
        }
        if (o instanceof Collection<?> c) {
            for (Object e : c) {
                walk(e, ownerLine, parents, depth + 1);
            }
            return;
        }
        if (o instanceof Map<?, ?> m) {
            for (Object e : m.values()) {
                walk(e, ownerLine, parents, depth + 1);
            }
            return;
        }
        if (!o.getClass().getName().startsWith("org.apache.camel.model.") || o instanceof RouteDefinition
                || !seen.add(o)) {
            return;
        }
        int line = lineOf(o, ownerLine);
        List<String> inner = parents;
        if (o instanceof ProcessorDefinition<?> p) {
            step(p, line, parents);
            inner = new ArrayList<>(parents);
            inner.add(p.getShortName());
        }
        for (Class<?> c = o.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive() || f.getType() == String.class
                        || "parent".equals(f.getName()) || "blocks".equals(f.getName())) {
                    continue;
                }
                Object v;
                try {
                    f.setAccessible(true);
                    v = f.get(o);
                } catch (ReflectiveOperationException | RuntimeException e) {
                    continue;
                }
                if (v instanceof ExpressionSubElementDefinition sub) {
                    v = sub.getExpressionType();
                }
                if (v instanceof ExpressionDefinition e) {
                    expression(e, o, f.getName(), line, inner);
                } else {
                    walk(v, line, inner, depth + 1);
                }
            }
        }
    }

    /** A step, and the endpoints it names. */
    private void step(ProcessorDefinition<?> p, int line, List<String> parents) {
        String eip = p.getShortName();
        nodes.add(new Node(Kind.STEP, line, eip, null, null, null, null, false, parents));
        if (p instanceof InterceptFromDefinition || p instanceof InterceptSendToEndpointDefinition) {
            // their uri is a pattern of the endpoints they intercept, not an endpoint
            return;
        }
        String uri = null;
        if (p instanceof ToDynamicDefinition d) {
            // toD and wireTap
            uri = d.getUri();
        } else if (p instanceof SendDefinition<?> s) {
            uri = s.getUri();
        } else if (p instanceof PollDefinition poll) {
            uri = poll.getUri();
        } else if ((p instanceof EnrichDefinition || p instanceof PollEnrichDefinition)
                && ((ExpressionNode) p).getExpression() instanceof ConstantExpression c) {
            uri = c.getExpression();
        } else if (p instanceof LogDefinition log && log.getMessage() != null) {
            // the message of a log is a simple template
            nodes.add(new Node(Kind.EXPRESSION, line, eip, null, "simple", log.getMessage(), "message", false, parents));
        }
        if (uri != null) {
            nodes.add(new Node(Kind.ENDPOINT, line, eip, uri, null, null, null, false, parents));
        }
        if (p instanceof SwitchDefinition sw) {
            // the destinations of a switch are its cases and fallback, which are not steps (each keeps a to of its
            // own for the runtime, the same endpoint: not walked)
            for (SwitchCaseDefinition c : sw.getCases()) {
                seen.add(c);
                if (c.getToDefinition() != null) {
                    seen.add(c.getToDefinition());
                }
                if (c.getUri() != null) {
                    nodes.add(new Node(
                            Kind.ENDPOINT, lineOf(c, line), "to", c.getUri(), null, null, null, false,
                            append(parents, eip)));
                }
            }
            SwitchOtherwiseDefinition otherwise = sw.getOtherwise();
            if (otherwise != null) {
                seen.add(otherwise);
                seen.add(otherwise.getToDefinition());
                if (otherwise.getUri() != null) {
                    nodes.add(new Node(
                            Kind.ENDPOINT, lineOf(otherwise, line), "to", otherwise.getUri(), null, null, null,
                            false, append(parents, eip)));
                }
            }
        }
    }

    private void expression(ExpressionDefinition e, Object owner, String option, int line, List<String> parents) {
        if (!seen.add(e) || e.getLanguage() == null) {
            return;
        }
        String eip = owner instanceof NamedNode n ? n.getShortName() : null;
        nodes.add(new Node(
                Kind.EXPRESSION, line, eip, null, e.getLanguage(), e.getExpression(), option,
                isPredicate(owner, eip, option), parents));
    }

    /**
     * Whether the step evaluates the option as a predicate: the expression the model marks with @AsPredicate, which the
     * catalog carries as asPredicate on the option. loop is the one EIP a flag on the option cannot describe: its
     * expression is a predicate only when doWhile is true.
     */
    private boolean isPredicate(Object owner, String eip, String option) {
        if (owner instanceof LoopDefinition loop) {
            return "true".equals(loop.getDoWhile());
        }
        if (catalog == null || eip == null) {
            return false;
        }
        EipModel model = catalog.eipModel(eip);
        return model != null && SimpleChecks.isPredicateOption(model, option);
    }

    private static List<String> append(List<String> parents, String eip) {
        List<String> answer = new ArrayList<>(parents);
        answer.add(eip);
        return answer;
    }

    private static int lineOf(Object o, int fallback) {
        if (o instanceof LineNumberAware la && la.getLineNumber() > 0) {
            return la.getLineNumber();
        }
        return fallback;
    }
}
