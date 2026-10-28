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
package org.apache.camel.maven;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.LineNumberAware;
import org.apache.camel.NamedNode;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.java.in.JavaParseResult;
import org.apache.camel.java.in.LwJavaParser;
import org.apache.camel.model.ChoiceDefinition;
import org.apache.camel.model.EnrichDefinition;
import org.apache.camel.model.ExpressionNode;
import org.apache.camel.model.ExpressionSubElementDefinition;
import org.apache.camel.model.FromDefinition;
import org.apache.camel.model.InterceptFromDefinition;
import org.apache.camel.model.InterceptSendToEndpointDefinition;
import org.apache.camel.model.LoopDefinition;
import org.apache.camel.model.PollDefinition;
import org.apache.camel.model.PollEnrichDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.SendDefinition;
import org.apache.camel.model.ToDynamicDefinition;
import org.apache.camel.model.language.ConstantExpression;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.parser.model.CamelEndpointDetails;
import org.apache.camel.parser.model.CamelNodeDetails;
import org.apache.camel.parser.model.CamelNodeDetailsFactory;
import org.apache.camel.parser.model.CamelRouteDetails;
import org.apache.camel.parser.model.CamelSimpleExpressionDetails;
import org.apache.camel.tooling.model.EipModel;

/**
 * The routes of a Java source read by the Java DSL parser of camel-java-io (CAMEL-25591), as the details the report
 * goals work with: the node tree of each route (route coverage), and the endpoints, simple expressions and route ids
 * (validate). The source is read into the Camel model without compiling it; a part the parser cannot work out (a value
 * computed at runtime, a lambda) is left out, and an endpoint uri it cannot work out is returned as unparsable.
 * <p/>
 * The node tree follows the rules of the XML DSL tree of camel-route-parser: the EIPs with an input or output, without
 * the route (the tree starts with from) and without when and otherwise (their steps are the outputs of the choice).
 */
final class JavaRouteModelReader {

    private static final String METHOD_NAME = "configure";
    private static final Pattern PACKAGE = Pattern.compile("(?m)^\\s*package\\s+([\\w.]+)\\s*;");
    private static final int MAX_DEPTH = 64;
    /** A part of a value the parser could not work out, as it marks it in the model. */
    private static final Pattern UNRESOLVED = Pattern.compile("\\?\\{([^}]*)}");
    private static final Pattern FIELD_NAME = Pattern.compile("[A-Za-z_$][\\w$]*");
    /** The uri of an endpoint injected into a field, which is not a part of configure(). */
    private static final Pattern ENDPOINT_INJECT
            = Pattern.compile("@EndpointInject\\s*\\(\\s*(?:(?:value|uri)\\s*=\\s*)?\"([^\"]+)\"");

    private final CamelCatalog catalog;
    private final String fileName;
    private final String className;
    private final String content;
    private final JavaParseResult result;

    private JavaRouteModelReader(CamelCatalog catalog, String fileName, String content) {
        this.catalog = catalog;
        this.fileName = fileName;
        this.content = content;
        this.className = className(fileName, content);
        this.result = new LwJavaParser().parse(content);
    }

    /**
     * Reads a Java source.
     *
     * @param catalog  the catalog, to know the EIPs and which expressions are predicates
     * @param fileName the file the details refer to
     * @param content  the Java source
     */
    static JavaRouteModelReader read(CamelCatalog catalog, String fileName, String content) {
        return new JavaRouteModelReader(catalog, fileName, content);
    }

    /** Whether the source has routes the parser could read. */
    boolean hasRoutes() {
        return !result.routes().getRoutes().isEmpty();
    }

    /** The class the details refer to: the package of the source and the name of the file. */
    String className() {
        return className;
    }

    // ---- route coverage ----

    /** The node tree of each route, starting with its from. */
    List<CamelNodeDetails> routeTrees() {
        List<CamelNodeDetails> answer = new ArrayList<>();
        for (RouteDefinition route : result.routes().getRoutes()) {
            FromDefinition input = route.getInput();
            if (input == null) {
                continue;
            }
            CamelNodeDetailsFactory factory = CamelNodeDetailsFactory.newInstance();
            CamelNodeDetails from = factory.newNode(null, "from");
            from.setRouteId(route.getRouteId());
            int line = line(input, line(route, 0));
            describe(from, line);
            answer.add(from);
            for (ProcessorDefinition<?> p : route.getOutputs()) {
                tree(factory, p, from, line, 0);
            }
        }
        return answer;
    }

    private void tree(
            CamelNodeDetailsFactory factory, ProcessorDefinition<?> p, CamelNodeDetails parent, int parentLine,
            int depth) {
        if (p == null || depth > MAX_DEPTH) {
            return;
        }
        String name = p.getShortName();
        int line = line(p, parentLine);
        CamelNodeDetails node = parent;
        if (!"when".equals(name) && !"otherwise".equals(name) && isEip(name)) {
            node = factory.newNode(parent, name);
            node.setRouteId(parent.getRouteId());
            if (p.hasCustomIdAssigned()) {
                node.setNodeId(p.getId());
            }
            describe(node, line);
            parent.addOutput(node);
        }
        // the steps of a choice are those of its when and otherwise
        for (ProcessorDefinition<?> child : p.getOutputs()) {
            tree(factory, child, node, line, depth + 1);
        }
    }

    private void describe(CamelNodeDetails node, int line) {
        node.setFileName(fileName);
        node.setClassName(className);
        node.setMethodName(METHOD_NAME);
        node.setLineNumber(Integer.toString(line));
        node.setLineNumberEnd(Integer.toString(line));
    }

    /** Whether the name is an EIP of the catalog that has an input or output, as the XML tree has it. */
    private boolean isEip(String name) {
        if (name == null) {
            return false;
        }
        try {
            EipModel model = catalog.eipModel(name);
            return model != null && (model.isInput() || model.isOutput());
        } catch (RuntimeException e) {
            return false;
        }
    }

    // ---- validate ----

    /**
     * Adds the endpoints of the routes: the inputs and a poll or pollEnrich (consumers), and a to, toD, wireTap or
     * enrich (producers).
     *
     * @param unparsable the uris the parser could not work out; may be null
     */
    void endpoints(List<CamelEndpointDetails> endpoints, List<String> unparsable) {
        for (Object o : modelObjects()) {
            String uri = null;
            boolean consumer = false;
            if (o instanceof FromDefinition from) {
                uri = from.getUri();
                consumer = true;
            } else if (o instanceof InterceptFromDefinition || o instanceof InterceptSendToEndpointDefinition) {
                // their uri is a pattern of the endpoints they intercept: validated when it names one endpoint, and
                // not a consumer nor a producer of it
                String pattern = o instanceof InterceptFromDefinition f
                        ? f.getUri()
                        : ((InterceptSendToEndpointDefinition) o).getUri();
                String known = pattern != null && !pattern.contains("*") ? known(pattern) : null;
                if (known != null) {
                    endpoints.add(endpoint(known, line(o, 0)));
                }
                continue;
            } else if (o instanceof ToDynamicDefinition d) {
                // toD and wireTap
                uri = d.getUri();
            } else if (o instanceof SendDefinition<?> s) {
                uri = s.getUri();
            } else if (o instanceof PollDefinition poll) {
                uri = poll.getUri();
                consumer = true;
            } else if ((o instanceof EnrichDefinition || o instanceof PollEnrichDefinition)
                    && ((ExpressionNode) o).getExpression() instanceof ConstantExpression c) {
                uri = c.getExpression();
                consumer = o instanceof PollEnrichDefinition;
            }
            if (uri == null) {
                continue;
            }
            String known = known(uri);
            if (known == null) {
                if (unparsable != null) {
                    unparsable.add(uri);
                }
                continue;
            }
            CamelEndpointDetails detail = endpoint(known, line(o, 0));
            detail.setConsumerOnly(consumer);
            detail.setProducerOnly(!consumer);
            endpoints.add(detail);
        }
        // the endpoints injected into fields: @EndpointInject("mock:result")
        Matcher m = ENDPOINT_INJECT.matcher(content);
        while (m.find()) {
            endpoints.add(endpoint(m.group(1), lineOf(m.start())));
        }
    }

    private CamelEndpointDetails endpoint(String uri, int line) {
        CamelEndpointDetails detail = new CamelEndpointDetails();
        detail.setFileName(fileName);
        detail.setClassName(className);
        detail.setMethodName(METHOD_NAME);
        detail.setLineNumber(Integer.toString(line));
        detail.setLineNumberEnd(Integer.toString(line));
        detail.setEndpointUri(uri);
        detail.setEndpointComponentName(uri.contains(":") ? uri.substring(0, uri.indexOf(':')) : null);
        return detail;
    }

    /**
     * The uri with each part the parser could not work out (a value only known at runtime, such as a port in a field)
     * as a property placeholder, so its component and options can still be validated, as camel-route-parser did:
     * {@code "http://localhost:" + port + "/x"} is http://localhost:{{port}}/x. Null when the component itself is not
     * known.
     */
    static String withPlaceholders(String uri) {
        String answer = uri;
        if (uri.startsWith(LwJavaParser.UNRESOLVED_PREFIX) && uri.endsWith("}")) {
            // the whole uri is a value the parser could not work out: build it from its string literals
            answer = concatenation(uri.substring(LwJavaParser.UNRESOLVED_PREFIX.length(), uri.length() - 1));
            if (answer == null) {
                return null;
            }
        } else if (uri.contains(LwJavaParser.UNRESOLVED_PREFIX)) {
            Matcher m = UNRESOLVED.matcher(uri);
            StringBuilder sb = new StringBuilder();
            while (m.find()) {
                m.appendReplacement(sb, Matcher.quoteReplacement(placeholder(m.group(1))));
            }
            m.appendTail(sb);
            answer = sb.toString();
        }
        int colon = answer.indexOf(':');
        if (colon <= 0 || answer.contains(LwJavaParser.UNRESOLVED_PREFIX) || answer.substring(0, colon).contains("{{")) {
            return null;
        }
        return answer;
    }

    /**
     * The value of a string concatenation in Java source, with each term that is not a string literal as a placeholder;
     * null when it has no string literal or is not a concatenation the method can read.
     */
    private static String concatenation(String expression) {
        StringBuilder answer = new StringBuilder();
        StringBuilder term = new StringBuilder();
        boolean literal = false;
        int depth = 0;
        for (int i = 0; i < expression.length(); i++) {
            char c = expression.charAt(i);
            if (c == '"' && depth == 0 && term.toString().isBlank()) {
                // a string literal: up to the closing quote, with its escapes
                int end = i + 1;
                StringBuilder text = new StringBuilder();
                while (end < expression.length() && expression.charAt(end) != '"') {
                    char e = expression.charAt(end);
                    if (e == '\\' && end + 1 < expression.length()) {
                        end++;
                        e = expression.charAt(end);
                    }
                    text.append(e);
                    end++;
                }
                if (end >= expression.length()) {
                    return null;
                }
                answer.append(text);
                literal = true;
                term.setLength(0);
                term.append(' ');
                i = end;
            } else if (c == '+' && depth == 0) {
                if (!term.toString().isBlank()) {
                    answer.append(placeholder(term.toString()));
                }
                term.setLength(0);
            } else {
                if (c == '(') {
                    depth++;
                } else if (c == ')') {
                    depth--;
                }
                if (!Character.isWhitespace(c) || !term.isEmpty()) {
                    term.append(c);
                }
            }
        }
        if (!term.toString().isBlank()) {
            answer.append(placeholder(term.toString()));
        }
        return literal ? answer.toString() : null;
    }

    private static String placeholder(String term) {
        String name = term.strip().replaceAll("[^\\w.]", "");
        return "{{" + (name.isEmpty() ? "value" : name) + "}}";
    }

    /**
     * The uri as {@link #withPlaceholders(String)}; for a uri that is only the name of a field of the class, the
     * endpoint injected into it or its string initializer, as camel-route-parser read them: {@code to(result)} with
     * {@code @EndpointInject("mock:result") MockEndpoint result}, {@code from(ftp)} with
     * {@code private String ftp = "ftp:localhost/inbox"}.
     */
    private String known(String uri) {
        if (uri.startsWith(LwJavaParser.UNRESOLVED_PREFIX) && uri.endsWith("}")) {
            String name = uri.substring(LwJavaParser.UNRESOLVED_PREFIX.length(), uri.length() - 1).strip();
            if (FIELD_NAME.matcher(name).matches()) {
                // an endpoint injected into the field: @EndpointInject("mock:result") MockEndpoint result
                Matcher m = Pattern.compile("@EndpointInject\\s*\\(\\s*(?:(?:value|uri)\\s*=\\s*)?\"([^\"]+)\"\\s*\\)[^;]*?\\b"
                                            + Pattern.quote(name) + "\\s*;")
                        .matcher(content);
                if (m.find()) {
                    return withPlaceholders(m.group(1));
                }
                m = Pattern.compile("\\bString\\s+" + Pattern.quote(name) + "\\s*=\\s*([^;]+);").matcher(content);
                String value = m.find() ? concatenation(m.group(1).strip()) : null;
                return value != null ? withPlaceholders(value) : null;
            }
        }
        return withPlaceholders(uri);
    }

    /** The 1-based line of a position in the source. */
    private int lineOf(int position) {
        int line = 1;
        for (int i = 0; i < position && i < content.length(); i++) {
            if (content.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    /** Adds the simple expressions and predicates of the routes. */
    void simpleExpressions(List<CamelSimpleExpressionDetails> expressions) {
        for (Object o : modelObjects()) {
            // a step, or a when of a choice (which is no processor of its own)
            if (!(o instanceof NamedNode p)) {
                continue;
            }
            forEachExpression(p, (option, e) -> {
                String text = e.getExpression();
                if (!"simple".equals(e.getLanguage()) || text == null || text.contains(LwJavaParser.UNRESOLVED_PREFIX)) {
                    return;
                }
                CamelSimpleExpressionDetails detail = new CamelSimpleExpressionDetails();
                detail.setFileName(fileName);
                detail.setClassName(className);
                detail.setMethodName(METHOD_NAME);
                String line = Integer.toString(line(e, line(p, 0)));
                detail.setLineNumber(line);
                detail.setLineNumberEnd(line);
                detail.setSimple(text);
                boolean predicate = isPredicate(p, option);
                detail.setPredicate(predicate);
                detail.setExpression(!predicate);
                expressions.add(detail);
            });
        }
    }

    /** Adds the ids of the routes that have one. */
    void routeIds(List<CamelRouteDetails> routes) {
        for (RouteDefinition route : result.routes().getRoutes()) {
            String id = route.getRouteId();
            if (id == null || id.contains(LwJavaParser.UNRESOLVED_PREFIX)) {
                continue;
            }
            CamelRouteDetails detail = new CamelRouteDetails();
            detail.setFileName(fileName);
            detail.setClassName(className);
            detail.setMethodName(METHOD_NAME);
            String line = Integer.toString(line(route.getInput(), line(route, 0)));
            detail.setLineNumber(line);
            detail.setLineNumberEnd(line);
            detail.setRouteId(id);
            routes.add(detail);
        }
    }

    private boolean isPredicate(NamedNode p, String option) {
        if (p instanceof LoopDefinition loop) {
            return "true".equals(loop.getDoWhile());
        }
        try {
            EipModel model = catalog.eipModel(p.getShortName());
            return model != null && model.getOptions().stream()
                    .anyMatch(o -> option.equals(o.getName()) && "expression".equals(o.getKind()) && o.isAsPredicate());
        } catch (RuntimeException e) {
            return false;
        }
    }

    // ---- the model ----

    /**
     * The objects of the Camel model the source has: the routes and their steps, and the onException, onCompletion and
     * intercepts of the route builder, at any depth.
     */
    private List<Object> modelObjects() {
        List<Object> answer = new ArrayList<>();
        Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (RouteDefinition route : result.routes().getRoutes()) {
            collect(route.getInput(), answer, seen, 0);
            route.getOutputs().forEach(p -> collect(p, answer, seen, 0));
        }
        result.routes().getOnExceptions().forEach(p -> collect(p, answer, seen, 0));
        result.routes().getOnCompletions().forEach(p -> collect(p, answer, seen, 0));
        result.routes().getIntercepts().forEach(p -> collect(p, answer, seen, 0));
        result.routes().getInterceptFroms().forEach(p -> collect(p, answer, seen, 0));
        result.routes().getInterceptSendTos().forEach(p -> collect(p, answer, seen, 0));
        return answer;
    }

    private static void collect(Object o, List<Object> answer, Set<Object> seen, int depth) {
        if (o == null || depth > MAX_DEPTH || !seen.add(o)) {
            return;
        }
        answer.add(o);
        if (o instanceof ChoiceDefinition choice) {
            // the outputs of a choice are the steps of its when and otherwise, not the when and otherwise
            choice.getWhenClauses().forEach(w -> collect(w, answer, seen, depth + 1));
            collect(choice.getOtherwise(), answer, seen, depth + 1);
        }
        if (o instanceof ProcessorDefinition<?> p) {
            p.getOutputs().forEach(c -> collect(c, answer, seen, depth + 1));
        }
    }

    /** Calls the action with each expression of the step and the name of its option. */
    private static void forEachExpression(Object p, ExpressionAction action) {
        for (Class<?> c = p.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive() || f.getType() == String.class) {
                    continue;
                }
                Object v;
                try {
                    f.setAccessible(true);
                    v = f.get(p);
                } catch (ReflectiveOperationException | RuntimeException e) {
                    continue;
                }
                if (v instanceof ExpressionSubElementDefinition sub) {
                    v = sub.getExpressionType();
                }
                if (v instanceof ExpressionDefinition e) {
                    action.accept(f.getName(), e);
                } else if (v instanceof Collection<?> list) {
                    for (Object item : list) {
                        if (item instanceof ExpressionDefinition e) {
                            action.accept(f.getName(), e);
                        }
                    }
                } else if (v instanceof Map<?, ?> map) {
                    for (Object item : map.values()) {
                        if (item instanceof ExpressionDefinition e) {
                            action.accept(f.getName(), e);
                        }
                    }
                }
            }
        }
    }

    @FunctionalInterface
    private interface ExpressionAction {
        void accept(String option, ExpressionDefinition expression);
    }

    private static int line(Object node, int fallback) {
        if (node instanceof LineNumberAware aware && aware.getLineNumber() > 0) {
            return aware.getLineNumber();
        }
        return fallback;
    }

    private static String className(String fileName, String content) {
        String name = fileName.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        if (name.endsWith(".java")) {
            name = name.substring(0, name.length() - ".java".length());
        }
        Matcher m = PACKAGE.matcher(content);
        return m.find() ? m.group(1) + "." + name : name;
    }
}
