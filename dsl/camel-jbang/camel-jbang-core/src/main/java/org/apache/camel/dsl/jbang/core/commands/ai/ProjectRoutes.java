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

import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.xml.sax.InputSource;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.java.in.ConstantResolver;
import org.apache.camel.java.in.JavaParseResult;
import org.apache.camel.util.URISupport;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;

/**
 * Reads the routes of a project from their source files, without starting Camel: which endpoint each route consumes
 * from, which endpoints it sends to, its id, description and group, and where it is written. This is what an overview
 * of a project needs to show how its routes connect; it is not a full model of the DSL.
 * <p/>
 * The YAML DSL (canonical and Kaoto style, route templates, templated routes, REST DSL, Integration and Kamelet
 * resources) and the XML DSL are read structurally. The Java DSL is read into the model by the Java DSL parser of
 * camel-java-io ({@link JavaRouteReader}): constants, nesting and the endpoint DSL are seen; a route with a part the
 * parser could not work out (a lambda, a value from a helper method) is marked {@code heuristic}. Only when the parser
 * finds no route are string literals in {@code from}, {@code to} and friends looked for.
 */
public final class ProjectRoutes {

    /** Keys whose value is an endpoint the route sends to. */
    private static final Set<String> PRODUCER_KEYS = Set.of("to", "toD", "wireTap", "enrich");
    /** Keys whose value is an endpoint the route polls from: the route consumes it, too. */
    private static final Set<String> POLL_KEYS = Set.of("poll", "pollEnrich");
    /** EIPs whose destinations are only known at runtime. */
    private static final Set<String> DYNAMIC_KEYS = Set.of("recipientList", "routingSlip", "dynamicRouter");
    private static final Set<String> REST_VERBS = Set.of("get", "post", "put", "patch", "delete", "head");
    private static final Set<String> STEP_CONTAINERS = Set.of("steps", "when", "doCatch", "doFinally");
    private static final int MAX_DEPTH = 64;
    private static final int MAX_PATH = 60;
    /** The id given to an onException or error handler outside a route, followed by where it is written. */
    public static final String ERROR_HANDLER_PREFIX = "error-handler@";

    private static final Pattern JAVA_FROM = Pattern.compile("(?<![.\\w])from\\s*\\(\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern JAVA_CALL = Pattern.compile(
            "\\.(to|toD|wireTap|enrich|pollEnrich|poll|routeId|routeDescription|routeNote|routeGroup|kamelet|recipientList|routingSlip"
                                                             + "|dynamicRouter)\\s*\\(\\s*(\"((?:[^\"\\\\]|\\\\.)*)\")?");
    private static final Pattern JAVA_STEP = Pattern.compile("\\.(\\w+)\\s*\\(");
    private static final Pattern JAVA_ON_EXCEPTION = Pattern.compile("(?<![.\\w])onException\\s*\\(");
    private static final Pattern JAVA_DEAD_LETTER = Pattern.compile(
            "deadLetterChannel\\s*\\(\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern JAVA_TO = Pattern.compile("\\.to\\s*\\(\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
    /** Java DSL calls that do not change what a route does: naming and describing it. */
    private static final Set<String> JAVA_NAMING
            = Set.of("routeId", "routeDescription", "routeNote", "routeGroup", "id", "description", "note");

    private ProjectRoutes() {
    }

    /** An endpoint a route uses, as written, with its identity for matching the other end. */
    public record Endpoint(String uri, String scheme, String key, boolean dynamic, String label, boolean onError) {

        public Endpoint(String uri, String scheme, String key, boolean dynamic, String label) {
            this(uri, scheme, key, dynamic, label, false);
        }

        /** The same endpoint, sent to while handling a failure (doCatch, onException, a dead letter channel). */
        Endpoint asOnError() {
            return onError ? this : new Endpoint(uri, scheme, key, dynamic, label, true);
        }
    }

    /**
     * A route of the project.
     *
     * @param id        the route id, null when the source gives none
     * @param kind      route, routeTemplate, templatedRoute, rest, or errorHandler (an onException or error handler
     *                  outside a route, with the endpoints it sends failed messages to)
     * @param file      the file, relative to the project directory
     * @param line      the 1-based line the route starts at
     * @param format    yaml, xml or java
     * @param heuristic whether the route was found by pattern matching (Java DSL) and may be incomplete
     * @param from      what the route consumes from, null for a REST operation without a route
     * @param consumes  every endpoint the route consumes: its from, what it polls, and the kamelet name of a template
     * @param produces  the endpoints the route sends to, in order
     * @param steps     roughly how many steps the route has
     * @param insertAt  where a description line can be inserted (YAML), or -1
     * @param indent    the indentation of that line (YAML)
     * @param restVerb  the HTTP verb of a REST operation
     * @param restPath  the path of a REST operation
     * @param logOnly   whether every step of the route only logs: plumbing, not business logic
     * @param note      the route's note: a longer explanation beside the short description, may be null
     */
    public record Route(String id, String kind, String description, String group, String file, int line, String format,
            boolean heuristic, Endpoint from, List<Endpoint> consumes, List<Endpoint> produces, int steps,
            int insertAt, int indent, String restVerb, String restPath, boolean logOnly, String note) {

        /** The route id, or where the route is written when it has none. */
        public String key() {
            if (id != null && !id.isBlank()) {
                return id;
            }
            return restVerb != null ? restVerb + " " + restPath : file + ":" + line;
        }

        public boolean hasDescription() {
            return description != null && !description.isBlank();
        }

        public boolean hasNote() {
            return note != null && !note.isBlank();
        }
    }

    /**
     * The routes a source file defines; an empty list when it defines none or cannot be read, a file that does not
     * parse is not an error for an overview.
     */
    public static List<Route> parse(String file, String content, CamelCatalog catalog) {
        return parse(file, content, catalog, null);
    }

    /**
     * @param constants the constants a Java route refers to in other classes (other project files, component header
     *                  constants); may be null
     */
    static List<Route> parse(String file, String content, CamelCatalog catalog, ConstantResolver constants) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        String lower = file.toLowerCase(Locale.ROOT);
        try {
            if (lower.endsWith(".yaml") || lower.endsWith(".yml")) {
                return new YamlReader(file, catalog).read(content);
            }
            if (lower.endsWith(".xml")) {
                return new XmlReader(file, content, catalog).read();
            }
            if (lower.endsWith(".java")) {
                // the Java DSL parser gives the model of the routes; the patterns only when it finds none
                List<Route> routes = JavaRouteReader.read(file, content, catalog, constants);
                return !routes.isEmpty() ? routes : readJava(file, content, catalog);
            }
        } catch (RuntimeException e) {
            // an overview skips what it cannot read; the validator reports what is wrong with the file
        }
        return List.of();
    }

    /**
     * A Java DSL source read into the Camel model by the Java DSL parser, without compiling or running it: the endpoint
     * DSL and the constants of the project's Java sources (by path, read when first needed) are resolved as in the
     * project overview. For tools that need the steps and their lines, such as the TUI Source tab.
     */
    public static JavaParseResult parseJava(
            String content, Map<String, Supplier<String>> javaSources, CamelCatalog catalog) {
        return JavaRouteReader.parse(content, catalog, new ProjectConstantResolver(javaSources, catalog));
    }

    // ---- endpoints ----

    /**
     * An endpoint from a URI and optional parameters (the Kaoto style keeps the path in the parameters). The key drops
     * the query and joins the brokers that are one system (jms, activemq, sjms, amqp), so a producer and a consumer of
     * the same destination match.
     */
    public static Endpoint endpoint(String uri, Map<String, Object> parameters, boolean dynamic, CamelCatalog catalog) {
        if (uri == null || uri.isBlank()) {
            return null;
        }
        uri = uri.strip();
        int colon = uri.indexOf(':');
        String scheme = colon > 0 ? uri.substring(0, colon) : uri;
        String path = colon > 0 ? uri.substring(colon + 1) : "";
        if (path.isEmpty() && parameters != null && !parameters.isEmpty() && catalog != null) {
            // Kaoto style: uri: kafka with parameters topic: orders
            Map<String, String> props = new LinkedHashMap<>();
            parameters.forEach((k, v) -> {
                if (v != null && !(v instanceof Map) && !(v instanceof List)) {
                    props.put(k, String.valueOf(v));
                }
            });
            try {
                String full = catalog.asEndpointUri(scheme, props, false);
                if (full != null && full.startsWith(scheme + ":")) {
                    path = full.substring(scheme.length() + 1);
                }
            } catch (Exception e) {
                // keep the scheme alone
            }
        }
        int q = path.indexOf('?');
        String query = q >= 0 ? path.substring(q + 1) : null;
        if (q >= 0) {
            path = path.substring(0, q);
        }
        if (path.startsWith("//")) {
            path = path.substring(2);
        }
        boolean dyn = dynamic && path.contains("${");
        String family = family(scheme.toLowerCase(Locale.ROOT));
        String keyPath = path;
        if ("jms".equals(family) && keyPath.startsWith("queue:")) {
            keyPath = keyPath.substring("queue:".length());
        }
        // a SQL statement or a script in the path is not needed to name the system
        String shown = path.length() > MAX_PATH ? path.substring(0, MAX_PATH - 3) + "..." : path;
        String display = shown.isEmpty() ? scheme : scheme + ":" + shown;
        return new Endpoint(display, scheme, family + ":" + keyPath, dyn, triggerLabel(scheme, query, parameters));
    }

    private static Endpoint dynamicEndpoint(String eip) {
        return new Endpoint("(" + eip + ")", eip, "dynamic:" + eip, true, null);
    }

    /** The brokers several components talk to under one name. */
    static String family(String scheme) {
        return switch (scheme) {
            case "activemq", "activemq6", "sjms", "sjms2", "amqp", "jms" -> "jms";
            case "direct-vm" -> "direct";
            case "vm", "disruptor", "disruptor-vm" -> "seda";
            default -> scheme;
        };
    }

    /**
     * When a timer, scheduler, quartz or cron fires, from the options that say so and never the others (a query may
     * carry credentials).
     */
    private static String triggerLabel(String scheme, String query, Map<String, Object> parameters) {
        Set<String> safe = switch (scheme) {
            case "timer" -> Set.of("period", "delay", "repeatCount", "fixedRate");
            case "scheduler" -> Set.of("delay", "initialDelay", "repeatCount");
            case "quartz", "cron" -> Set.of("cron", "schedule", "trigger.repeatInterval");
            default -> Set.of();
        };
        if (safe.isEmpty()) {
            return null;
        }
        Map<String, String> found = new LinkedHashMap<>();
        if (query != null) {
            for (String pair : query.split("&")) {
                int eq = pair.indexOf('=');
                if (eq > 0 && safe.contains(pair.substring(0, eq))) {
                    found.put(pair.substring(0, eq), pair.substring(eq + 1));
                }
            }
        }
        if (parameters != null) {
            parameters.forEach((k, v) -> {
                if (safe.contains(k) && v != null) {
                    found.put(k, String.valueOf(v));
                }
            });
        }
        if (found.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        found.forEach((k, v) -> sb.append(sb.isEmpty() ? "" : ", ").append(k).append('=').append(v));
        return sb.toString();
    }

    // ---- YAML ----

    private static final class YamlReader {
        private final String file;
        private final CamelCatalog catalog;
        private final List<Route> routes = new ArrayList<>();

        YamlReader(String file, CamelCatalog catalog) {
            this.file = file;
            this.catalog = catalog;
        }

        List<Route> read(String content) {
            Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
            for (org.yaml.snakeyaml.nodes.Node doc : yaml.composeAll(new StringReader(content))) {
                if (doc instanceof SequenceNode seq) {
                    for (org.yaml.snakeyaml.nodes.Node item : seq.getValue()) {
                        topLevel(item);
                    }
                } else if (doc instanceof MappingNode map) {
                    resource(map);
                }
            }
            return routes;
        }

        /** An Integration's flows, a Kamelet's template or a Pipe's source, steps and sink. */
        private void resource(MappingNode map) {
            Object kind = value(map, "kind");
            MappingNode spec = child(map, "spec") instanceof MappingNode m ? m : null;
            if (spec == null) {
                return;
            }
            if ("Integration".equals(kind) && child(spec, "flows") instanceof SequenceNode flows) {
                for (org.yaml.snakeyaml.nodes.Node item : flows.getValue()) {
                    topLevel(item);
                }
            } else if ("Kamelet".equals(kind) && child(spec, "template") instanceof MappingNode template) {
                Object name = child(map, "metadata") instanceof MappingNode meta ? value(meta, "name") : null;
                route("routeTemplate", template, name != null ? name.toString() : null, line(map), -1, 0);
            } else if ("Pipe".equals(kind) || "KameletBinding".equals(kind)) {
                pipe(map, spec);
            }
        }

        private void pipe(MappingNode map, MappingNode spec) {
            Object name = child(map, "metadata") instanceof MappingNode meta ? value(meta, "name") : null;
            Endpoint from = pipeEndpoint(child(spec, "source"));
            List<Endpoint> produces = new ArrayList<>();
            if (child(spec, "steps") instanceof SequenceNode steps) {
                for (org.yaml.snakeyaml.nodes.Node step : steps.getValue()) {
                    add(produces, pipeEndpoint(step));
                }
            }
            add(produces, pipeEndpoint(child(spec, "sink")));
            List<Endpoint> consumes = new ArrayList<>();
            add(consumes, from);
            routes.add(new Route(
                    name != null ? name.toString() : null, "route", null, null, file, line(map), "yaml", false,
                    from, consumes, produces, produces.size(), -1, 0, null, null, false, null));
        }

        private Endpoint pipeEndpoint(org.yaml.snakeyaml.nodes.Node node) {
            if (!(node instanceof MappingNode m)) {
                return null;
            }
            if (value(m, "uri") instanceof String uri) {
                return endpoint(uri, null, false, catalog);
            }
            if (child(m, "ref") instanceof MappingNode ref && value(ref, "name") instanceof String name) {
                return endpoint("kamelet:" + name, null, false, catalog);
            }
            return null;
        }

        private void topLevel(org.yaml.snakeyaml.nodes.Node item) {
            if (!(item instanceof MappingNode map) || map.getValue().isEmpty()) {
                return;
            }
            NodeTuple first = map.getValue().get(0);
            String key = scalar(first.getKeyNode());
            org.yaml.snakeyaml.nodes.Node value = first.getValueNode();
            if (key == null) {
                return;
            }
            int line = line(first.getKeyNode());
            switch (key) {
                case "route" -> {
                    if (value instanceof MappingNode m) {
                        route("route", m, null, line, insertLine(m), column(m));
                    }
                }
                case "from" -> {
                    // the bare form: a route with no id or description of its own
                    if (value instanceof MappingNode) {
                        route("route", map, null, line, -1, 0);
                    }
                }
                case "routeTemplate", "route-template" -> {
                    if (value instanceof MappingNode m) {
                        MappingNode body = child(m, "route") instanceof MappingNode r ? r : m;
                        Route template = route("routeTemplate", body, str(value(m, "id")), line, insertLine(m), column(m));
                        if (template != null && body != m && value(m, "description") instanceof String d) {
                            replaceDescription(template, d);
                        }
                    }
                }
                case "templatedRoute", "templated-route" -> {
                    if (value instanceof MappingNode m) {
                        templatedRoute(m, line);
                    }
                }
                case "rest" -> {
                    if (value instanceof MappingNode m) {
                        rest(m, line);
                    }
                }
                case "onException", "errorHandler", "routeConfiguration", "route-configuration" -> errorHandler(value, line);
                default -> {
                    // beans, restConfiguration and friends are not routes
                }
            }
        }

        private void replaceDescription(Route r, String description) {
            int i = routes.indexOf(r);
            routes.set(i, new Route(
                    r.id(), r.kind(), description, r.group(), r.file(), r.line(), r.format(),
                    r.heuristic(), r.from(), r.consumes(), r.produces(), r.steps(), r.insertAt(), r.indent(), null,
                    null, r.logOnly(), r.note()));
        }

        private Route route(String kind, MappingNode m, String idOverride, int line, int insertAt, int indent) {
            String id = idOverride != null ? idOverride : str(value(m, "id"));
            org.yaml.snakeyaml.nodes.Node fromNode = child(m, "from");
            Endpoint from = null;
            List<Endpoint> consumes = new ArrayList<>();
            List<Endpoint> produces = new ArrayList<>();
            int[] steps = { 0 };
            if (fromNode instanceof ScalarNode s) {
                from = endpoint(s.getValue(), null, false, catalog);
            } else if (fromNode instanceof MappingNode fm) {
                from = endpoint(str(value(fm, "uri")), map(child(fm, "parameters")), false, catalog);
                if (child(fm, "steps") instanceof SequenceNode top) {
                    steps[0] += top.getValue().size();
                }
                walk(child(fm, "steps"), produces, consumes, steps, 0);
            }
            // the older form keeps the steps beside from
            if (child(m, "steps") instanceof SequenceNode top) {
                steps[0] += top.getValue().size();
            }
            walk(child(m, "steps"), produces, consumes, steps, 0);
            if (from != null) {
                consumes.add(0, from);
            }
            if ("routeTemplate".equals(kind) && id != null) {
                consumes.add(endpoint("kamelet:" + id, null, false, catalog));
            }
            Route r = new Route(
                    id, kind, str(value(m, "description")), str(value(m, "group")), file, line, "yaml",
                    false, from, consumes, produces, steps[0], insertAt, indent, null, null,
                    logOnly(fromNode instanceof MappingNode fm2 ? child(fm2, "steps") : child(m, "steps")),
                    str(value(m, "note")));
            routes.add(r);
            return r;
        }

        /** Where an onException or error handler sends failed messages, as a route that only error handling uses. */
        private void errorHandler(org.yaml.snakeyaml.nodes.Node value, int line) {
            List<Endpoint> produces = new ArrayList<>();
            walk(value, produces, new ArrayList<>(), new int[] { 0 }, 0, true);
            if (!produces.isEmpty()) {
                routes.add(new Route(
                        ERROR_HANDLER_PREFIX + file + ":" + line, "errorHandler", null, null, file, line, "yaml",
                        false, null, List.of(), produces, 0, -1, 0, null, null, false, null));
            }
        }

        /** Whether every top-level step only logs: a log EIP, or a to or wireTap of a log endpoint. */
        private boolean logOnly(org.yaml.snakeyaml.nodes.Node steps) {
            if (!(steps instanceof SequenceNode seq) || seq.getValue().isEmpty()) {
                return false;
            }
            for (org.yaml.snakeyaml.nodes.Node item : seq.getValue()) {
                if (!(item instanceof MappingNode m) || m.getValue().size() != 1) {
                    return false;
                }
                String key = scalar(m.getValue().get(0).getKeyNode());
                org.yaml.snakeyaml.nodes.Node value = m.getValue().get(0).getValueNode();
                if ("log".equals(key)) {
                    continue;
                }
                Endpoint e = ("to".equals(key) || "wireTap".equals(key)) ? stepEndpoint(value, false) : null;
                if (e == null || !"log".equals(e.scheme())) {
                    return false;
                }
            }
            return true;
        }

        private void templatedRoute(MappingNode m, int line) {
            String template = str(value(m, "routeTemplateRef"));
            String id = str(value(m, "routeId"));
            List<Endpoint> produces = new ArrayList<>();
            if (template != null) {
                // a templated route runs the template; draw it as a call so it links to the template
                add(produces, endpoint("kamelet:" + template, null, false, catalog));
            }
            routes.add(new Route(
                    id, "templatedRoute", null, null, file, line, "yaml", false, null, List.of(), produces,
                    0, -1, 0, null, null, false, null));
        }

        private void rest(MappingNode m, int line) {
            String base = str(value(m, "path"));
            for (NodeTuple t : m.getValue()) {
                String verb = scalar(t.getKeyNode());
                if (verb == null || !REST_VERBS.contains(verb) || !(t.getValueNode() instanceof SequenceNode ops)) {
                    continue;
                }
                for (org.yaml.snakeyaml.nodes.Node op : ops.getValue()) {
                    if (!(op instanceof MappingNode om)) {
                        continue;
                    }
                    String path = joinPath(base, str(value(om, "path")));
                    List<Endpoint> produces = new ArrayList<>();
                    org.yaml.snakeyaml.nodes.Node to = child(om, "to");
                    if (to instanceof ScalarNode s) {
                        add(produces, endpoint(s.getValue(), null, false, catalog));
                    } else if (to instanceof MappingNode tm) {
                        add(produces, endpoint(str(value(tm, "uri")), map(child(tm, "parameters")), false, catalog));
                    }
                    routes.add(new Route(
                            str(value(om, "id")), "rest", str(value(om, "description")), null, file,
                            line(om), "yaml", false, null, List.of(), produces, 1, -1, 0,
                            verb.toUpperCase(Locale.ROOT), path, false, null));
                }
            }
        }

        /** Collects the endpoints of the steps below a node, whatever EIP nests them. */
        private void walk(
                org.yaml.snakeyaml.nodes.Node node, List<Endpoint> produces, List<Endpoint> consumes, int[] steps,
                int depth) {
            walk(node, produces, consumes, steps, depth, false);
        }

        /** @param onError whether the node is inside error handling, where what is sent to carries a failure */
        private void walk(
                org.yaml.snakeyaml.nodes.Node node, List<Endpoint> produces, List<Endpoint> consumes, int[] steps,
                int depth, boolean onError) {
            if (node == null || depth > MAX_DEPTH) {
                return;
            }
            if (node instanceof SequenceNode seq) {
                for (org.yaml.snakeyaml.nodes.Node item : seq.getValue()) {
                    walk(item, produces, consumes, steps, depth + 1, onError);
                }
            } else if (node instanceof MappingNode map) {
                // what is sent to below this node, marked when it handles a failure
                List<Endpoint> sent = onError ? new ArrayList<>() : produces;
                for (NodeTuple t : map.getValue()) {
                    String key = scalar(t.getKeyNode());
                    org.yaml.snakeyaml.nodes.Node value = t.getValueNode();
                    if (key == null || "parameters".equals(key)) {
                        // endpoint options are not steps: a mail endpoint's to is an address
                        continue;
                    }
                    if ("steps".equals(key) && value instanceof SequenceNode s) {
                        steps[0] += s.getValue().size();
                    }
                    if ("deadLetterUri".equals(key) && value instanceof ScalarNode dlc) {
                        add(produces, onErrorOf(endpoint(dlc.getValue(), null, false, catalog)));
                    } else if (PRODUCER_KEYS.contains(key) || POLL_KEYS.contains(key)) {
                        Endpoint e = stepEndpoint(value, "toD".equals(key) || "enrich".equals(key)
                                || "pollEnrich".equals(key));
                        if (e == null && !(value instanceof ScalarNode)) {
                            // enrich with an expression: known only at runtime
                            e = dynamicEndpoint(key);
                        }
                        add(POLL_KEYS.contains(key) ? consumes : sent, e);
                    } else if ("kamelet".equals(key)) {
                        String name = value instanceof ScalarNode s ? s.getValue()
                                : value instanceof MappingNode km ? str(value(km, "name")) : null;
                        if (name != null) {
                            add(sent, endpoint("kamelet:" + name, null, false, catalog));
                        }
                    } else if (DYNAMIC_KEYS.contains(key)) {
                        sent.add(dynamicEndpoint(key));
                    }
                    if (value instanceof MappingNode || value instanceof SequenceNode) {
                        walk(value, produces, consumes, steps, depth + 1,
                                onError || "doCatch".equals(key) || "onException".equals(key));
                    }
                }
                if (sent != produces) {
                    sent.forEach(e -> produces.add(e.asOnError()));
                }
            }
        }

        private Endpoint stepEndpoint(org.yaml.snakeyaml.nodes.Node value, boolean dynamic) {
            if (value instanceof ScalarNode s) {
                return endpoint(s.getValue(), null, dynamic, catalog);
            }
            if (value instanceof MappingNode m && value(m, "uri") instanceof String uri) {
                return endpoint(uri, map(child(m, "parameters")), dynamic, catalog);
            }
            return null;
        }

        /**
         * Where a description or note line goes: after the id key (or before the first key) of a block mapping,
         * 0-based; -1 for a flow mapping.
         */
        private static int insertLine(MappingNode m) {
            if (m.getFlowStyle() == DumperOptions.FlowStyle.FLOW || m.getValue().isEmpty()) {
                return -1;
            }
            for (NodeTuple t : m.getValue()) {
                if ("id".equals(scalar(t.getKeyNode())) && t.getValueNode() instanceof ScalarNode) {
                    return t.getKeyNode().getStartMark().getLine() + 1;
                }
            }
            return m.getValue().get(0).getKeyNode().getStartMark().getLine();
        }

        private static int column(MappingNode m) {
            return m.getValue().isEmpty() ? 0 : m.getValue().get(0).getKeyNode().getStartMark().getColumn();
        }
    }

    private static int line(org.yaml.snakeyaml.nodes.Node node) {
        return node.getStartMark().getLine() + 1;
    }

    private static String scalar(org.yaml.snakeyaml.nodes.Node node) {
        return node instanceof ScalarNode s ? s.getValue() : null;
    }

    private static org.yaml.snakeyaml.nodes.Node child(MappingNode map, String key) {
        for (NodeTuple t : map.getValue()) {
            if (key.equals(scalar(t.getKeyNode()))) {
                return t.getValueNode();
            }
        }
        return null;
    }

    private static Object value(MappingNode map, String key) {
        return child(map, key) instanceof ScalarNode s ? s.getValue() : null;
    }

    private static Map<String, Object> map(org.yaml.snakeyaml.nodes.Node node) {
        if (!(node instanceof MappingNode m)) {
            return null;
        }
        Map<String, Object> answer = new LinkedHashMap<>();
        for (NodeTuple t : m.getValue()) {
            String key = scalar(t.getKeyNode());
            if (key != null && t.getValueNode() instanceof ScalarNode s) {
                answer.put(key, s.getValue());
            }
        }
        return answer;
    }

    private static String str(Object o) {
        return o == null || o.toString().isBlank() ? null : o.toString();
    }

    private static Endpoint onErrorOf(Endpoint e) {
        return e != null ? e.asOnError() : null;
    }

    private static void add(List<Endpoint> list, Endpoint e) {
        if (e != null) {
            list.add(e);
        }
    }

    static String joinPath(String base, String path) {
        String b = base == null ? "" : base;
        String p = path == null ? "" : path;
        if (b.endsWith("/") && p.startsWith("/")) {
            p = p.substring(1);
        } else if (!b.isEmpty() && !p.isEmpty() && !b.endsWith("/") && !p.startsWith("/")) {
            p = "/" + p;
        }
        String joined = b + p;
        return joined.isEmpty() ? "/" : joined;
    }

    // ---- XML ----

    private static final class XmlReader {
        private final String file;
        private final String content;
        private final CamelCatalog catalog;
        private final List<Route> routes = new ArrayList<>();

        XmlReader(String file, String content, CamelCatalog catalog) {
            this.file = file;
            this.content = content;
            this.catalog = catalog;
        }

        List<Route> read() {
            Document doc = parse(content);
            if (doc == null) {
                return routes;
            }
            NodeList all = doc.getElementsByTagNameNS("*", "*");
            for (int i = 0; i < all.getLength(); i++) {
                Element e = (Element) all.item(i);
                String name = e.getLocalName() != null ? e.getLocalName() : e.getTagName();
                if ("route".equals(name)) {
                    Element parent = e.getParentNode() instanceof Element p ? p : null;
                    boolean inTemplate = parent != null && "routeTemplate".equals(localName(parent));
                    route(e, inTemplate ? parent : null);
                } else if ("rest".equals(name)) {
                    rest(e);
                } else if (("onException".equals(name) || e.hasAttribute("deadLetterUri")) && !insideRoute(e)) {
                    errorHandler(e);
                }
            }
            return routes;
        }

        private void route(Element e, Element template) {
            String id = template != null ? attr(template, "id") : attr(e, "id");
            String description = attr(template != null ? template : e, "description");
            if (description == null) {
                Element d = firstChild(template != null ? template : e, "description");
                description = d != null ? str(d.getTextContent().strip()) : null;
            }
            Element fromEl = firstChild(e, "from");
            Endpoint from = fromEl != null ? endpoint(attr(fromEl, "uri"), null, false, catalog) : null;
            List<Endpoint> consumes = new ArrayList<>();
            List<Endpoint> produces = new ArrayList<>();
            add(consumes, from);
            int steps = 0;
            NodeList all = e.getElementsByTagNameNS("*", "*");
            for (int i = 0; i < all.getLength(); i++) {
                Element s = (Element) all.item(i);
                String name = localName(s);
                if (s.getParentNode() == e && !"from".equals(name) && !"description".equals(name)) {
                    steps++;
                }
                if (PRODUCER_KEYS.contains(name)) {
                    String uri = attr(s, "uri");
                    Endpoint ep = uri != null ? endpoint(uri, null, !"to".equals(name), catalog) : dynamicEndpoint(name);
                    add(produces, handlesFailure(s, e) ? onErrorOf(ep) : ep);
                } else if (POLL_KEYS.contains(name)) {
                    String uri = attr(s, "uri");
                    add(consumes, uri != null ? endpoint(uri, null, true, catalog) : dynamicEndpoint(name));
                } else if ("kamelet".equals(name) && attr(s, "name") != null) {
                    add(produces, endpoint("kamelet:" + attr(s, "name"), null, false, catalog));
                } else if (DYNAMIC_KEYS.contains(name)) {
                    produces.add(dynamicEndpoint(name));
                }
            }
            String kind = template != null ? "routeTemplate" : "route";
            if (template != null && id != null) {
                consumes.add(endpoint("kamelet:" + id, null, false, catalog));
            }
            routes.add(new Route(
                    id, kind, description, attr(e, "group"), file, lineOf(template != null ? "routeTemplate" : "route", id),
                    "xml", false, from, consumes, produces, steps, -1, 0, null, null, logOnly(e),
                    attr(template != null ? template : e, "note")));
        }

        private void errorHandler(Element e) {
            List<Endpoint> produces = new ArrayList<>();
            if (attr(e, "deadLetterUri") != null) {
                add(produces, endpoint(attr(e, "deadLetterUri"), null, false, catalog));
            }
            NodeList all = e.getElementsByTagNameNS("*", "*");
            for (int i = 0; i < all.getLength(); i++) {
                Element s = (Element) all.item(i);
                if (PRODUCER_KEYS.contains(localName(s)) && attr(s, "uri") != null) {
                    add(produces, endpoint(attr(s, "uri"), null, false, catalog));
                } else if (attr(s, "deadLetterUri") != null) {
                    add(produces, endpoint(attr(s, "deadLetterUri"), null, false, catalog));
                }
            }
            if (!produces.isEmpty()) {
                int line = lineOf(localName(e), null);
                routes.add(new Route(
                        ERROR_HANDLER_PREFIX + file + ":" + line, "errorHandler", null, null, file, line, "xml", false,
                        null, List.of(), produces.stream().map(Endpoint::asOnError).toList(), 0, -1, 0, null, null,
                        false, null));
            }
        }

        /** Whether an element sits in a doCatch or onException of the route. */
        private static boolean handlesFailure(Element e, Element route) {
            for (Node p = e.getParentNode(); p instanceof Element pe && pe != route; p = pe.getParentNode()) {
                if ("doCatch".equals(localName(pe)) || "onException".equals(localName(pe))) {
                    return true;
                }
            }
            return false;
        }

        private static boolean insideRoute(Element e) {
            for (Node p = e.getParentNode(); p instanceof Element pe; p = pe.getParentNode()) {
                if ("route".equals(localName(pe)) || "routeTemplate".equals(localName(pe))) {
                    return true;
                }
            }
            return false;
        }

        /** Whether the route's steps only log. */
        private static boolean logOnly(Element route) {
            int steps = 0;
            NodeList children = route.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                if (!(children.item(i) instanceof Element c)) {
                    continue;
                }
                String name = localName(c);
                if ("from".equals(name) || "description".equals(name) || "routeProperty".equals(name)) {
                    continue;
                }
                steps++;
                boolean log = "log".equals(name)
                        || ("to".equals(name) || "wireTap".equals(name)) && String.valueOf(attr(c, "uri")).startsWith("log:");
                if (!log) {
                    return false;
                }
            }
            return steps > 0;
        }

        private void rest(Element rest) {
            String base = attr(rest, "path");
            NodeList children = rest.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                if (!(children.item(i) instanceof Element op) || !REST_VERBS.contains(localName(op))) {
                    continue;
                }
                List<Endpoint> produces = new ArrayList<>();
                Element to = firstChild(op, "to");
                if (to != null) {
                    add(produces, endpoint(attr(to, "uri"), null, false, catalog));
                }
                routes.add(new Route(
                        attr(op, "id"), "rest", attr(op, "description"), null, file, lineOf("rest", null),
                        "xml", false, null, List.of(), produces, 1, -1, 0, localName(op).toUpperCase(Locale.ROOT),
                        joinPath(base, attr(op, "path")), false, null));
            }
        }

        /** The line of the element that starts the route, found by its id in the text (DOM keeps no lines). */
        private int lineOf(String element, String id) {
            int at = -1;
            if (id != null) {
                Matcher m = Pattern.compile("<(?:\\w+:)?" + element + "\\b[^>]*\\bid\\s*=\\s*[\"']" + Pattern.quote(id)
                                            + "[\"']")
                        .matcher(content);
                if (m.find()) {
                    at = m.start();
                }
            }
            if (at < 0) {
                at = Math.max(0, content.indexOf("<" + element));
            }
            return lineAt(content, at);
        }
    }

    /** Parses XML with DTDs and external entities off: a project file is data, never a pointer elsewhere. */
    private static Document parse(String content) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
            dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            dbf.setXIncludeAware(false);
            dbf.setExpandEntityReferences(false);
            DocumentBuilder db = dbf.newDocumentBuilder();
            db.setErrorHandler(null);
            return db.parse(new InputSource(new StringReader(content)));
        } catch (Exception e) {
            return null;
        }
    }

    private static String localName(Node n) {
        return n.getLocalName() != null ? n.getLocalName() : n.getNodeName();
    }

    private static String attr(Element e, String name) {
        return e.hasAttribute(name) ? str(e.getAttribute(name)) : null;
    }

    private static Element firstChild(Element e, String name) {
        NodeList children = e.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element c && name.equals(localName(c))) {
                return c;
            }
        }
        return null;
    }

    static int lineAt(String content, int offset) {
        int line = 1;
        for (int i = 0; i < offset && i < content.length(); i++) {
            if (content.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    // ---- Java ----

    /**
     * The routes of a Java RouteBuilder, one per {@code from("...")}: every string literal passed to an endpoint method
     * until the next from belongs to it.
     */
    private static List<Route> readJava(String file, String content, CamelCatalog catalog) {
        String code = stripComments(content);
        List<Route> routes = new ArrayList<>();
        Matcher from = JAVA_FROM.matcher(code);
        List<int[]> starts = new ArrayList<>();
        List<String> uris = new ArrayList<>();
        while (from.find()) {
            starts.add(new int[] { from.start(), from.end() });
            uris.add(unescape(from.group(1)));
        }
        for (int i = 0; i < starts.size(); i++) {
            int end = i + 1 < starts.size() ? starts.get(i + 1)[0] : code.length();
            String body = code.substring(starts.get(i)[1], end);
            // a statement ends the route: stop at the first semicolon outside a string
            int stop = statementEnd(body);
            body = body.substring(0, stop);
            Endpoint fromEp = endpoint(uris.get(i), null, false, catalog);
            List<Endpoint> consumes = new ArrayList<>();
            List<Endpoint> produces = new ArrayList<>();
            add(consumes, fromEp);
            String id = null;
            String description = null;
            String note = null;
            String group = null;
            Matcher m = JAVA_CALL.matcher(body);
            while (m.find()) {
                String method = m.group(1);
                String literal = m.group(3) != null ? unescape(m.group(3)) : null;
                switch (method) {
                    case "routeId" -> id = literal;
                    case "routeDescription" -> description = literal;
                    case "routeNote" -> note = literal;
                    case "routeGroup" -> group = literal;
                    case "kamelet" ->
                        add(produces, literal != null ? endpoint("kamelet:" + literal, null, false, catalog) : null);
                    case "poll", "pollEnrich" -> add(consumes,
                            literal != null ? endpoint(literal, null, true, catalog) : dynamicEndpoint(method));
                    case "recipientList", "routingSlip", "dynamicRouter" -> produces.add(dynamicEndpoint(method));
                    default -> add(produces, literal != null
                            ? endpoint(literal, null, !"to".equals(method), catalog) : dynamicEndpoint(method));
                }
            }
            int steps = 0;
            Matcher s = JAVA_STEP.matcher(body);
            while (s.find()) {
                steps++;
            }
            routes.add(new Route(
                    id, "route", description, group, file, lineAt(content, starts.get(i)[0]),
                    "java", true, fromEp, consumes, produces, steps, -1, 0, null, null,
                    javaLogOnly(body, produces), note));
        }
        javaErrorHandlers(file, content, code, catalog, routes);
        return routes;
    }

    /** onException statements and dead letter channels outside a route, as error handler routes. */
    private static void javaErrorHandlers(
            String file, String content, String code, CamelCatalog catalog, List<Route> routes) {
        List<Endpoint> produces = new ArrayList<>();
        int first = -1;
        Matcher on = JAVA_ON_EXCEPTION.matcher(code);
        while (on.find()) {
            String statement = code.substring(on.start(), on.start() + statementEnd(code.substring(on.start())));
            Matcher to = JAVA_TO.matcher(statement);
            while (to.find()) {
                add(produces, endpoint(unescape(to.group(1)), null, false, catalog));
                first = first < 0 ? on.start() : first;
            }
        }
        Matcher dlc = JAVA_DEAD_LETTER.matcher(code);
        while (dlc.find()) {
            add(produces, endpoint(unescape(dlc.group(1)), null, false, catalog));
            first = first < 0 ? dlc.start() : Math.min(first, dlc.start());
        }
        if (!produces.isEmpty()) {
            int line = lineAt(content, first);
            routes.add(new Route(
                    ERROR_HANDLER_PREFIX + file + ":" + line, "errorHandler", null, null, file, line, "java", true, null,
                    List.of(), produces.stream().map(Endpoint::asOnError).toList(), 0, -1, 0, null, null, false, null));
        }
    }

    /** Whether a Java route only logs: its calls are log, to a log endpoint, or naming the route. */
    private static boolean javaLogOnly(String body, List<Endpoint> produces) {
        boolean logs = false;
        Matcher m = JAVA_STEP.matcher(body);
        while (m.find()) {
            String method = m.group(1);
            if ("log".equals(method) || "to".equals(method)) {
                logs = true;
            } else if (!JAVA_NAMING.contains(method)) {
                return false;
            }
        }
        return logs && produces.stream().allMatch(e -> "log".equals(e.scheme()));
    }

    /** Comments are replaced by spaces so offsets in the code still match the file. */
    private static String stripComments(String content) {
        StringBuilder sb = new StringBuilder(content.length());
        int i = 0;
        boolean inString = false;
        while (i < content.length()) {
            char c = content.charAt(i);
            if (inString) {
                sb.append(c);
                if (c == '\\' && i + 1 < content.length()) {
                    sb.append(content.charAt(++i));
                } else if (c == '"') {
                    inString = false;
                }
                i++;
            } else if (c == '"') {
                inString = true;
                sb.append(c);
                i++;
            } else if (c == '/' && i + 1 < content.length() && content.charAt(i + 1) == '/') {
                while (i < content.length() && content.charAt(i) != '\n') {
                    sb.append(' ');
                    i++;
                }
            } else if (c == '/' && i + 1 < content.length() && content.charAt(i + 1) == '*') {
                int end = content.indexOf("*/", i + 2);
                end = end < 0 ? content.length() : end + 2;
                while (i < end) {
                    sb.append(content.charAt(i) == '\n' ? '\n' : ' ');
                    i++;
                }
            } else {
                sb.append(c);
                i++;
            }
        }
        return sb.toString();
    }

    private static int statementEnd(String body) {
        boolean inString = false;
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (inString) {
                if (c == '\\') {
                    i++;
                } else if (c == '"') {
                    inString = false;
                }
            } else if (c == '"') {
                inString = true;
            } else if (c == ';') {
                return i;
            }
        }
        return body.length();
    }

    private static String unescape(String literal) {
        return literal.replace("\\\"", "\"").replace("\\\\", "\\");
    }

    /**
     * An endpoint URI for showing or sending to a model: the query is sanitized so a password or token in it is masked.
     */
    static String safeUri(String uri) {
        try {
            return URISupport.sanitizeUri(uri);
        } catch (RuntimeException e) {
            return uri;
        }
    }
}
