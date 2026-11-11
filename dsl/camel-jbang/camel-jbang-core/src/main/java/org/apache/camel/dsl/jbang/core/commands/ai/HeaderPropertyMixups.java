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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.dsl.yaml.validator.ProjectFacts;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;

/**
 * A route that keeps a value in one place (a header, an exchange property or a variable) and reads it from another gets
 * null with no error (CAMEL-25516): {@code toD: http://localhost:8080/stock/${header.sku}} after
 * {@code setProperty: sku} calls {@code /stock/}. {@link HeaderChecks} says this for Camel's own names
 * (CamelTimerCounter); this is the same mix-up with the route's own names.
 * <p/>
 * Quiet where the name can come from elsewhere: a route that consumes from an endpoint that can carry headers (only
 * timer, scheduler, cron, quartz and file cannot), a path parameter {X} of a rest or platform-http path, and a name the
 * route sets in both forms.
 * <p/>
 * In the same routes, a header copied to itself that nothing sets before ({@code setHeader: sku} with
 * {@code ${header.sku}}) is null too, and so is a header read in a split that nothing sets before, see
 * {@link #unsetHeaders(String, ProjectFacts)}.
 */
final class HeaderPropertyMixups {

    private static final Set<String> PLAIN_CONSUMERS = Set.of("timer", "scheduler", "cron", "quartz", "file");
    /** The headers of the job that a quartz consumer, and a cron one (which runs on quartz), sets on each message. */
    private static final Set<String> QUARTZ_HEADERS = Set.of("calendar", "fireTime", "jobDetail", "jobInstance",
            "jobRunTime", "mergedJobDataMap", "nextFireTime", "previousFireTime", "refireCount", "result",
            "scheduledFireTime", "scheduler", "trigger", "triggerName", "triggerGroup");
    private static final Pattern HEADER_READ
            = Pattern.compile("\\$\\{(?:in\\.)?headers?(?:\\.([A-Za-z_][\\w-]*)|\\[['\"]?([A-Za-z_][\\w-]*)['\"]?])");
    private static final Pattern PROPERTY_READ
            = Pattern.compile("\\$\\{exchangeProperty(?:\\.([A-Za-z_][\\w-]*)|\\[['\"]?([A-Za-z_][\\w-]*)['\"]?])");
    private static final Pattern VARIABLE_READ
            = Pattern.compile("\\$\\{variables?(?:\\.([A-Za-z_][\\w-]*)|\\[['\"]?([A-Za-z_][\\w-]*)['\"]?])");

    /** Where a route keeps a value: what a read says, what the route sets with, and how to read it. */
    private enum Kind {
        HEADER("a header", "header."),
        PROPERTY("an exchange property", "exchangeProperty."),
        VARIABLE("a variable", "variable.");

        final String what;
        final String read;

        Kind(String what, String read) {
            this.what = what;
            this.read = read;
        }
    }

    /** A path parameter of a rest or platform-http path: a header of that name on the request. */
    private static final Pattern PATH_PARAMETER = Pattern.compile("(?<!\\$)\\{([A-Za-z_][\\w-]*)}");

    /** The simple parser's hint for ${property.x}, a function Camel does not have: it names ${exchangeProperty.x}. */
    private static final Pattern PROPERTY_FUNCTION = Pattern.compile("Unknown function: (?:property|prop)"
                                                                     + "(?:\\.([A-Za-z_][\\w-]*)"
                                                                     + "|\\[['\"]?([A-Za-z_][\\w-]*)['\"]?])"
                                                                     + " \\(did you mean"
                                                                     + " \\$\\{exchangeProperty[^}]*}\\?\\)");

    private HeaderPropertyMixups() {
    }

    /**
     * The simple errors of a YAML route, with where the route keeps the value that a ${property.x} reads: the hint of
     * the simple parser names ${exchangeProperty.x} only. r25 run-order-generator step 1: asked to set a variable, a
     * model wrote setProperty and ${property.orderId}, followed the hint to ${exchangeProperty.orderId}, and failed (3
     * runs of 8, and in r24 and r23 too). A value the route sets with setVariable is read with ${variable.x}; for one
     * it sets with setProperty, ${exchangeProperty.x} is right, and a variable is offered as what Camel uses for a
     * value of the route. A value a script sets as an exchange property is read with ${exchangeProperty.x}, also when
     * the route has a variable of that name: r26 connect-sftp-transfer step 2 set the variable count to 0 first, and
     * the script set the exchange property count; the variable was the 0. The did-you-mean stays the first suggestion,
     * as the quick fix reads it.
     */
    static List<String> withWhereKept(List<String> errors, String content) {
        if (errors.stream().noneMatch(e -> e.contains("Unknown function: prop"))) {
            return errors;
        }
        Object doc;
        try {
            doc = new Yaml(new SafeConstructor(new LoaderOptions())).load(content);
        } catch (Exception e) {
            // not YAML that loads: where the route keeps a value is not known
            doc = null;
        }
        List<String> answer = new ArrayList<>(errors.size());
        for (String error : errors) {
            Matcher m = PROPERTY_FUNCTION.matcher(error);
            if (!m.find()) {
                answer.add(error);
                continue;
            }
            String name = m.group(1) != null ? m.group(1) : m.group(2);
            Set<Kind> kept = keptIn(doc, name);
            String unknown = m.group().substring(0, m.group().indexOf(" (did you mean"));
            String hint;
            if (setInScript(content, name)) {
                hint = m.group() + ", which reads the exchange property that the script sets (exchange.setProperty); or"
                       + " keep " + name + " in a variable instead, which is what Camel uses for a value of the route:"
                       + " set it in the script with exchange.setVariable('" + name + "', ...) and read it with"
                       + " ${variable." + name + "}";
            } else if (kept.contains(Kind.VARIABLE)) {
                hint = unknown + " (did you mean ${variable." + name + "}?): the route keeps " + name + " in a variable"
                       + " (setVariable)"
                       + (kept.contains(Kind.PROPERTY)
                               ? "; ${exchangeProperty." + name + "} reads the exchange property that setProperty sets"
                               : "");
            } else if (kept.contains(Kind.HEADER) && !kept.contains(Kind.PROPERTY)) {
                hint = unknown + " (did you mean ${header." + name + "}?): the route keeps " + name + " in a header"
                       + " (setHeader)";
            } else if (name.startsWith("Camel")) {
                // an exchange property of Camel itself, such as CamelSplitIndex
                hint = m.group();
            } else {
                hint = m.group() + (kept.contains(Kind.PROPERTY)
                        ? ", which reads the exchange property that setProperty sets; or keep " + name + " in a"
                          + " variable instead"
                        : "; or keep " + name + " in a variable")
                       + ", which is what Camel uses for a value of the route: set it with setVariable (name: " + name
                       + ") and read it with ${variable." + name + "}";
            }
            answer.add(error.substring(0, m.start()) + hint + error.substring(m.end()));
        }
        return answer;
    }

    /**
     * Whether a script of the route sets the exchange property: exchange.setProperty('x', ...),
     * exchange.properties['x'] = ..., exchange.properties.x = ... or exchange.properties.put('x', ...).
     */
    private static boolean setInScript(String content, String name) {
        if (content == null || !content.contains("exchange.")) {
            return false;
        }
        String quoted = "\\s*['\"]" + Pattern.quote(name) + "['\"]";
        return Pattern.compile("exchange\\.(?:setProperty\\(" + quoted + "|properties(?:\\[" + quoted + "\\s*]|\\."
                               + Pattern.quote(name) + ")\\s*=(?!=)|properties\\.put\\(" + quoted + ")")
                .matcher(content).find();
    }

    /**
     * Where the route keeps the name: the set steps that name it, also as written by mistake (propertyName: x, or x as
     * the key), the items of setHeaders and setVariables, and a variableReceive.
     */
    private static Set<Kind> keptIn(Object node, String name) {
        Set<Kind> answer = java.util.EnumSet.noneOf(Kind.class);
        keptIn(node, name, answer);
        return answer;
    }

    private static void keptIn(Object node, String name, Set<Kind> answer) {
        if (node instanceof Map<?, ?> m) {
            for (Map.Entry<?, ?> e : m.entrySet()) {
                Object value = e.getValue();
                switch (String.valueOf(e.getKey())) {
                    case "setHeader" -> namedBy(value, name, Kind.HEADER, answer);
                    case "setProperty" -> namedBy(value, name, Kind.PROPERTY, answer);
                    case "setVariable" -> namedBy(value, name, Kind.VARIABLE, answer);
                    case "setHeaders", "setVariables" -> {
                        Kind kind = "setHeaders".equals(e.getKey()) ? Kind.HEADER : Kind.VARIABLE;
                        Object items = value instanceof Map<?, ?> v
                                ? v.get(kind == Kind.HEADER ? "headers" : "variables") : null;
                        if (items instanceof List<?> list) {
                            list.forEach(item -> namedBy(item, name, kind, answer));
                        }
                    }
                    case "variableReceive" -> {
                        if (name.equals(value)) {
                            answer.add(Kind.VARIABLE);
                        }
                    }
                    default -> {
                    }
                }
                keptIn(value, name, answer);
            }
        } else if (node instanceof List<?> list) {
            list.forEach(o -> keptIn(o, name, answer));
        }
    }

    /** Whether a set step names the name: name: x, a key such as propertyName: x, or x itself as a key. */
    private static void namedBy(Object step, String name, Kind kind, Set<Kind> answer) {
        if (!(step instanceof Map<?, ?> m)) {
            return;
        }
        for (Map.Entry<?, ?> e : m.entrySet()) {
            String key = String.valueOf(e.getKey());
            if (key.equals(name) || ("name".equals(key) || key.endsWith("Name")) && name.equals(e.getValue())) {
                answer.add(kind);
            }
        }
    }

    /** The problems, each "Line N: message". */
    static List<String> validate(String content) {
        return validate(content, ProjectFacts.NONE);
    }

    /**
     * The problems, each "Line N: message", with the fields of the JSON data of the project for a header that nothing
     * sets.
     */
    static List<String> validate(String content, ProjectFacts facts) {
        List<String> answer = mixups(content);
        answer.addAll(unsetHeaders(content, facts));
        return answer;
    }

    private static List<String> mixups(String content) {
        List<String> answer = new ArrayList<>();
        if (content == null || !content.contains("${")) {
            return answer;
        }
        Object doc;
        try {
            doc = new Yaml(new SafeConstructor(new LoaderOptions())).load(content);
        } catch (Exception e) {
            return answer;
        }
        if (!(doc instanceof List<?> items)) {
            return answer;
        }
        Set<String> pathParameters = new LinkedHashSet<>();
        Matcher pm = PATH_PARAMETER.matcher(content);
        while (pm.find()) {
            pathParameters.add(pm.group(1));
        }
        String[] lines = content.split("\n", -1);
        Set<String> reported = new LinkedHashSet<>();
        try {
            for (Object item : items) {
                Map<?, ?> from = null;
                if (item instanceof Map<?, ?> m && m.get("route") instanceof Map<?, ?> route
                        && route.get("from") instanceof Map<?, ?> f) {
                    from = f;
                } else if (item instanceof Map<?, ?> m && m.get("from") instanceof Map<?, ?> f) {
                    from = f;
                }
                if (from == null || !PLAIN_CONSUMERS.contains(scheme(String.valueOf(from.get("uri"))))) {
                    continue;
                }
                Map<Kind, Set<String>> set = new java.util.EnumMap<>(Kind.class);
                for (Kind k : Kind.values()) {
                    set.put(k, new LinkedHashSet<>());
                }
                set.get(Kind.HEADER).addAll(pathParameters);
                List<String> texts = new ArrayList<>();
                collect(from, set, texts);
                for (String text : texts) {
                    report(text, HEADER_READ, Kind.HEADER, set, lines, reported, answer);
                    report(text, PROPERTY_READ, Kind.PROPERTY, set, lines, reported, answer);
                    report(text, VARIABLE_READ, Kind.VARIABLE, set, lines, reported, answer);
                }
            }
        } catch (RuntimeException e) {
            // a shape the walk does not know: a check must never fail the validation
        }
        return answer;
    }

    private static void report(
            String text, Pattern read, Kind kind, Map<Kind, Set<String>> set, String[] lines, Set<String> reported,
            List<String> answer) {
        Matcher m = read.matcher(text);
        while (m.find()) {
            String name = m.group(1) != null ? m.group(1) : m.group(2);
            if (set.get(kind).contains(name)) {
                continue;
            }
            Kind kept = null;
            for (Kind other : Kind.values()) {
                if (other != kind && set.get(other).contains(name)) {
                    kept = other;
                }
            }
            if (kept == null || !reported.add(kind + ":" + name)) {
                continue;
            }
            String token = m.group() + "}";
            int line = 0;
            for (int i = 0; i < lines.length; i++) {
                if (lines[i].contains(m.group())) {
                    line = i + 1;
                    break;
                }
            }
            // r24 run-order-generator step 1: asked to set a variable, a model set an exchange property in 3 runs of
            // 7, and this answer kept it there; a variable is what Camel keeps a value of the route in
            answer.add("Line " + line + ": " + token + " reads " + kind.what + " " + name + ", but the route keeps " + name
                       + " in " + kept.what + " (the " + kind.what.substring(kind.what.indexOf(' ') + 1)
                       + " is null here): write ${" + kept.read + name + "}"
                       + (kept != Kind.PROPERTY ? ""
                               : kind == Kind.VARIABLE
                                       ? ", or set it with setVariable instead of setProperty, which is what Camel uses"
                                         + " for a value of the route"
                               : ", or keep it in a variable instead (setVariable, read with ${variable." + name
                                 + "}), which is what Camel uses for a value of the route"));
        }
    }

    /**
     * The names the route sets as headers, exchange properties and variables, and every text that may hold an
     * expression.
     */
    private static void collect(Object node, Map<Kind, Set<String>> set, List<String> texts) {
        if (node instanceof Map<?, ?> m) {
            for (Map.Entry<?, ?> e : m.entrySet()) {
                String key = String.valueOf(e.getKey());
                Object value = e.getValue();
                switch (key) {
                    case "setHeader" -> name(value, set.get(Kind.HEADER));
                    case "setProperty" -> name(value, set.get(Kind.PROPERTY));
                    case "setVariable" -> name(value, set.get(Kind.VARIABLE));
                    case "setHeaders" -> names(value, "headers", set.get(Kind.HEADER));
                    case "setVariables" -> names(value, "variables", set.get(Kind.VARIABLE));
                    default -> {
                    }
                }
                collect(value, set, texts);
            }
        } else if (node instanceof List<?> list) {
            list.forEach(o -> collect(o, set, texts));
        } else if (node instanceof String s && s.contains("${")) {
            texts.add(s);
        }
    }

    private static void names(Object body, String key, Set<String> into) {
        if (body instanceof Map<?, ?> m && m.get(key) instanceof List<?> list) {
            list.forEach(h -> name(h, into));
        }
    }

    private static void name(Object body, Set<String> into) {
        if (body instanceof Map<?, ?> m && m.get("name") != null) {
            into.add(String.valueOf(m.get("name")));
        }
    }

    private static String scheme(String uri) {
        int colon = uri.indexOf(':');
        return colon > 0 ? uri.substring(0, colon) : uri;
    }

    /** ${header.sku}, ${headers.sku}, ${in.header.sku} or ${header[sku]}: one header and nothing else. */
    private static final Pattern ONE_HEADER = Pattern.compile(
            "\\$\\{(?:in\\.)?headers?(?:\\.([A-Za-z_][\\w-]*)|\\[['\"]?([A-Za-z_][\\w-]*)['\"]?])}");

    /**
     * The steps that set no header other than the ones they name, and the EIPs whose steps are walked: any other step
     * (a to:, an enrich, a process, a bean...) may set a header of any name.
     */
    private static final Set<String> QUIET_STEPS = Set.of("log", "setBody", "transform", "setHeader", "setHeaders",
            "setProperty", "setVariable", "setVariables", "removeHeader", "removeHeaders", "removeProperty",
            "removeProperties", "removeVariable", "unmarshal", "marshal", "convertBodyTo", "convertHeaderTo",
            "convertVariableTo", "delay", "throttle", "stop", "filter", "choice", "split", "loop", "step", "pipeline",
            "doTry", "idempotentConsumer", "validate", "setExchangePattern");

    /** The EIPs that evaluate their endpoint, a uri with ${...}, before they send to it: the headers it reads count. */
    private static final Set<String> SENDS_TO_EXPRESSION = Set.of("toD", "to-d", "enrich", "pollEnrich", "poll-enrich",
            "wireTap", "wire-tap", "recipientList", "recipient-list", "routingSlip", "routing-slip");

    /** The steps that give the message another body. */
    private static final Set<String> BODY_STEPS = Set.of("setBody", "transform", "marshal", "unmarshal", "convertBodyTo");

    /** The keys of the steps an EIP holds, which the walk goes through in their own order. */
    private static final Set<String> HELD_STEPS = Set.of("steps", "when", "otherwise", "doCatch", "doFinally");

    /** The languages that run code, which can set a header of any name. */
    private static final Set<String> SCRIPTS = Set.of("groovy", "js", "javascript", "python", "java", "joor", "jsh",
            "mvel", "ognl", "spel", "jactl", "quickjs", "method", "bean", "csimple", "datasonnet", "wasm");

    /** split: ${body[lines]}, ${body.lines} or the jsonpath $.lines: the field of the data whose elements it gives. */
    private static final Pattern SPLIT_FIELD = Pattern.compile(
            "\\$\\{body(?:\\??\\.|\\[\\s*['\"]?)([A-Za-z_]\\w*)['\"]?\\s*]?}|\\$\\.([A-Za-z_]\\w*)(?:\\[\\*])?");

    /** What the walk of a route knows at a step: the names set before it, and whether a step may set any header. */
    private static final class Seen {
        final Set<String> headers = new LinkedHashSet<>();
        final Set<String> variables = new LinkedHashSet<>();
        final Set<String> properties = new LinkedHashSet<>();
        boolean anyHeader;

        Seen copy() {
            Seen s = new Seen();
            s.headers.addAll(headers);
            s.variables.addAll(variables);
            s.properties.addAll(properties);
            s.anyHeader = anyHeader;
            return s;
        }

        void merge(Seen inner) {
            headers.addAll(inner.headers);
            variables.addAll(inner.variables);
            properties.addAll(inner.properties);
            anyHeader |= inner.anyHeader;
        }
    }

    /**
     * The split a step is in: the field of the data it gives the elements of (null when it is not one), and the first
     * step in it that gives the message another body, with its line.
     */
    private static final class Split {
        final String field;
        String bodyStep;
        int bodyLine;

        Split(String field) {
            this.field = field;
        }
    }

    /**
     * A setHeader whose value is one header that nothing set before it: the line, the name set, the name read, the
     * field the split it is in gives the elements of, and where the route keeps the name read instead (a variable or an
     * exchange property), or null.
     */
    private record UnsetCopy(int line, String name, String read, String split, Kind kept) {
    }

    /**
     * A header read in a split that nothing set before it: the line, the name, the field the split gives the elements
     * of, the step before it in the split that gave the message another body (null when none) and its line, and where
     * the route keeps the name instead (a variable or an exchange property), or null.
     */
    private record UnsetRead(int line, String name, String split, String bodyStep, int bodyLine, Kind kept) {
    }

    /** What the walk of a route finds, and the lines of the content for the line of a read. */
    private static final class Found {
        final List<UnsetCopy> copies = new ArrayList<>();
        final List<UnsetRead> reads = new ArrayList<>();
        final String[] lines;

        Found(String[] lines) {
            this.lines = lines;
        }
    }

    /**
     * A header that nothing sets before it is read: the route consumes from an endpoint that carries no header of its
     * own (timer, scheduler, cron, quartz, file), and no step before it sets the header. The header is null, and stays
     * null, with no error. A step that may set a header the check does not see (a to:, a process, a bean, a script)
     * makes it say nothing after it. A path parameter of a rest in the file is a header of the route the rest calls,
     * not of these routes.
     * <p/>
     * A setHeader that copies a header to itself, setHeader sku with ${header.sku}: r23 to r27 connect-http-client step
     * 2 (10 runs), in a split over the lines of an order, the HTTP call had no sku, 98 errors of 404 before the model
     * found ${body[sku]}. Only a header that a later step of the route reads, as the toD did; a copy from another
     * header that nothing sets (needed with ${header.qty}) is named in the same message.
     * <p/>
     * A header read in a split: r26 and r28 connect-http-client step 1 (3 runs), toD
     * http://localhost:8080/stock/${header.sku} in the split over the lines of an order, 100 errors of 404 each. In the
     * split the body is the element, so the value is a field of it; when a step before the read gave the message
     * another body (setBody ${null} for the GET), the field is kept in a variable first.
     */
    static List<String> unsetHeaders(String content, ProjectFacts facts) {
        List<String> answer = new ArrayList<>();
        if (content == null || !content.contains("header")) {
            return answer;
        }
        List<Node> roots = new ArrayList<>();
        try {
            for (Node n : new Yaml(new SafeConstructor(new LoaderOptions())).composeAll(new StringReader(content))) {
                roots.add(n);
            }
        } catch (RuntimeException e) {
            // not YAML: the schema check says so
            return answer;
        }
        String[] lines = content.split("\n", -1);
        Set<String> pathParameters = new LinkedHashSet<>();
        Matcher pm = PATH_PARAMETER.matcher(content);
        while (pm.find()) {
            pathParameters.add(pm.group(1));
        }
        try {
            for (Node root : roots) {
                if (!(root instanceof SequenceNode entries)) {
                    continue;
                }
                for (Node entry : entries.getValue()) {
                    MappingNode route = mapping(value(entry, "route"));
                    MappingNode from = mapping(route != null ? value(route, "from") : value(entry, "from"));
                    String uri = from != null ? scalar(value(from, "uri")) : null;
                    if (uri == null || !PLAIN_CONSUMERS.contains(scheme(uri))) {
                        continue;
                    }
                    Seen start = new Seen();
                    if ("quartz".equals(scheme(uri)) || "cron".equals(scheme(uri))) {
                        start.headers.addAll(QUARTZ_HEADERS);
                    }
                    Found found = new Found(lines);
                    walk(value(from, "steps"), start, null, found);
                    // only a header that a later step reads: a copy nothing reads is harmless (a route that worked had
                    // one, with the value read from a variable)
                    List<UnsetCopy> copies = found.copies;
                    int end = from.getEndMark().getLine() + 1;
                    copies.removeIf(c -> !readAfter(lines, c.line(), end, c.name()));
                    ProjectFacts.JsonFields data = consumedData(uri, from, facts);
                    // the headers a message names already
                    Set<String> named = new LinkedHashSet<>();
                    for (UnsetCopy copy : copies) {
                        if (copy.name().equals(copy.read())) {
                            answer.add(selfCopyMessage(copy, copies, data));
                            copies.forEach(c -> named.add(c.read()));
                        }
                    }
                    // a name the route keeps in a variable or exchange property, and never in a header, is said by
                    // mixups(): the read of a header there is the mix-up
                    Seen all = new Seen();
                    setAnywhere(from, all);
                    for (UnsetRead read : found.reads) {
                        String name = read.name();
                        boolean mixup = !all.headers.contains(name) && !pathParameters.contains(name)
                                && (all.variables.contains(name) || all.properties.contains(name));
                        if (!mixup && named.add(name)) {
                            answer.add(unsetReadMessage(read, data));
                        }
                    }
                }
            }
        } catch (RuntimeException e) {
            // a shape the walk does not know: a check must never fail the validation
        }
        return answer;
    }

    /** Whether a line after the given one, up to the end line (1-based, inclusive), reads the header in Simple. */
    private static boolean readAfter(String[] lines, int line, int end, String name) {
        for (int i = line; i < Math.min(end, lines.length); i++) {
            Matcher m = HEADER_READ.matcher(lines[i]);
            while (m.find()) {
                if (name.equals(m.group(1) != null ? m.group(1) : m.group(2))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Walks the steps in order, with what was set before each one, collecting the copies of unset headers, and the
     * headers read in a split that nothing set.
     */
    private static void walk(Node steps, Seen seen, Split split, Found found) {
        if (!(steps instanceof SequenceNode list)) {
            return;
        }
        for (Node item : list.getValue()) {
            if (!(item instanceof MappingNode step) || step.getValue().size() != 1) {
                seen.anyHeader = true;
                continue;
            }
            String eip = scalar(step.getValue().get(0).getKeyNode());
            Node body = step.getValue().get(0).getValueNode();
            boolean code = runsCode(body);
            if (split != null && !code && (QUIET_STEPS.contains(eip) || SENDS_TO_EXPRESSION.contains(eip))) {
                // what the step reads comes before what it sets
                reads(eip, body, seen, split, found);
            }
            if (!QUIET_STEPS.contains(eip) || code) {
                seen.anyHeader = true;
                continue;
            }
            if (split != null && split.bodyStep == null && BODY_STEPS.contains(eip)) {
                split.bodyStep = eip;
                split.bodyLine = item.getStartMark().getLine() + 1;
            }
            switch (eip) {
                case "setHeader" -> setHeader(body, seen, split, found);
                case "setHeaders" -> namesOf(value(body, "headers"), seen.headers);
                case "setVariable" -> name(body, seen.variables);
                case "setVariables" -> namesOf(value(body, "variables"), seen.variables);
                case "setProperty" -> name(body, seen.properties);
                case "split" -> {
                    // each element starts from what was set before the split
                    Seen inner = seen.copy();
                    walk(value(body, "steps"), inner, new Split(split == null ? splitField(body) : null), found);
                    seen.merge(inner);
                }
                case "loop" -> {
                    // a pass of the loop sees what the passes before it set
                    Seen first = seen.copy();
                    walk(value(body, "steps"), first, split, new Found(found.lines));
                    Seen inner = seen.copy();
                    inner.merge(first);
                    walk(value(body, "steps"), inner, split, found);
                    seen.merge(inner);
                }
                default -> nested(body, seen, split, found);
            }
            variableReceive(body, seen);
        }
    }

    /** The steps of the EIPs that hold some: when and otherwise of a choice, doCatch and doFinally of a doTry. */
    private static void nested(Node body, Seen seen, Split split, Found found) {
        if (!(body instanceof MappingNode map)) {
            return;
        }
        List<Seen> branches = new ArrayList<>();
        for (NodeTuple t : map.getValue()) {
            String key = scalar(t.getKeyNode());
            if ("steps".equals(key)) {
                Seen inner = seen.copy();
                walk(t.getValueNode(), inner, split, found);
                branches.add(inner);
            } else if ("when".equals(key) || "doCatch".equals(key)) {
                if (t.getValueNode() instanceof SequenceNode items) {
                    for (Node branch : items.getValue()) {
                        Seen inner = seen.copy();
                        walk(value(branch, "steps"), inner, split, found);
                        branches.add(inner);
                    }
                }
            } else if ("otherwise".equals(key) || "doFinally".equals(key)) {
                Seen inner = seen.copy();
                walk(value(t.getValueNode(), "steps"), inner, split, found);
                branches.add(inner);
            }
        }
        branches.forEach(seen::merge);
    }

    private static void setHeader(Node body, Seen seen, Split split, Found found) {
        String name = scalar(value(body, "name"));
        if (name == null) {
            return;
        }
        Node simple = valueOf(body);
        Matcher m = simple instanceof ScalarNode s ? ONE_HEADER.matcher(s.getValue().trim()) : null;
        if (m != null && m.matches() && !seen.anyHeader) {
            String read = m.group(1) != null ? m.group(1) : m.group(2);
            if (!read.startsWith("Camel") && !seen.headers.contains(read)) {
                Kind kept = seen.variables.contains(read)
                        ? Kind.VARIABLE : seen.properties.contains(read) ? Kind.PROPERTY : null;
                found.copies.add(new UnsetCopy(
                        simple.getStartMark().getLine() + 1, name, read, split != null ? split.field : null, kept));
            }
        }
        seen.headers.add(name);
    }

    /** The simple expression of a setHeader: simple: x, simple: {expression: x}, or the same under expression:. */
    private static Node valueOf(Node body) {
        Node simple = value(body, "simple");
        if (simple == null && value(body, "expression") instanceof MappingNode expression) {
            simple = value(expression, "simple");
        }
        if (simple instanceof MappingNode m) {
            simple = value(m, "expression");
        }
        return simple;
    }

    /**
     * The headers a step in a split reads that nothing set before it: in its expressions, uri and options, and the
     * conditions of a choice, not in the steps it holds. A copy to itself is the copy check's.
     */
    private static void reads(String eip, Node body, Seen seen, Split split, Found found) {
        if (seen.anyHeader) {
            return;
        }
        if ("setHeader".equals(eip) && valueOf(body) instanceof ScalarNode s) {
            Matcher self = ONE_HEADER.matcher(s.getValue().trim());
            if (self.matches() && (self.group(1) != null ? self.group(1) : self.group(2))
                    .equals(scalar(value(body, "name")))) {
                return;
            }
        }
        List<ScalarNode> texts = new ArrayList<>();
        texts(body, texts);
        for (ScalarNode text : texts) {
            Matcher m = HEADER_READ.matcher(text.getValue());
            while (m.find()) {
                String name = m.group(1) != null ? m.group(1) : m.group(2);
                if (name.startsWith("Camel") || seen.headers.contains(name)) {
                    continue;
                }
                Kind kept = seen.variables.contains(name)
                        ? Kind.VARIABLE : seen.properties.contains(name) ? Kind.PROPERTY : null;
                found.reads.add(new UnsetRead(
                        lineOf(text, m.group(), found.lines), name, split.field, split.bodyStep, split.bodyLine, kept));
            }
        }
    }

    /** The texts with ${...} of a step, outside the steps it holds; the conditions of the when items of a choice. */
    private static void texts(Node node, List<ScalarNode> into) {
        if (node instanceof ScalarNode s) {
            if (s.getValue().contains("${")) {
                into.add(s);
            }
        } else if (node instanceof SequenceNode seq) {
            seq.getValue().forEach(n -> texts(n, into));
        } else if (node instanceof MappingNode map) {
            for (NodeTuple t : map.getValue()) {
                String key = scalar(t.getKeyNode());
                if (key == null || !HELD_STEPS.contains(key)) {
                    texts(t.getValueNode(), into);
                } else if ("when".equals(key) && t.getValueNode() instanceof SequenceNode items) {
                    for (Node when : items.getValue()) {
                        if (when instanceof MappingNode w) {
                            w.getValue().stream().filter(c -> !"steps".equals(scalar(c.getKeyNode())))
                                    .forEach(c -> texts(c.getValueNode(), into));
                        }
                    }
                }
            }
        }
    }

    /**
     * The 1-based line of the text that has the token, from the line the text starts at (a block scalar starts after).
     */
    private static int lineOf(ScalarNode text, String token, String[] lines) {
        int first = text.getStartMark().getLine();
        for (int i = first; i < Math.min(lines.length, text.getEndMark().getLine() + 1); i++) {
            if (lines[i].contains(token)) {
                return i + 1;
            }
        }
        return first + 1;
    }

    /** The names that the steps under the node set, wherever they are and in any order. */
    private static void setAnywhere(Node node, Seen into) {
        if (node instanceof MappingNode map) {
            for (NodeTuple t : map.getValue()) {
                Node v = t.getValueNode();
                switch (String.valueOf(scalar(t.getKeyNode()))) {
                    case "setHeader" -> name(v, into.headers);
                    case "setHeaders" -> namesOf(value(v, "headers"), into.headers);
                    case "setVariable" -> name(v, into.variables);
                    case "setVariables" -> namesOf(value(v, "variables"), into.variables);
                    case "setProperty" -> name(v, into.properties);
                    default -> {
                    }
                }
                setAnywhere(v, into);
            }
        } else if (node instanceof SequenceNode seq) {
            seq.getValue().forEach(n -> setAnywhere(n, into));
        }
    }

    /** Whether a step runs code of its own, outside the steps it holds: a script language can set any header. */
    private static boolean runsCode(Node node) {
        if (node instanceof MappingNode map) {
            for (NodeTuple t : map.getValue()) {
                String key = scalar(t.getKeyNode());
                if (SCRIPTS.contains(key)
                        || "simple".equals(key) && scalar(t.getValueNode()) != null
                                && scalar(t.getValueNode()).contains("setHeader(")) {
                    return true;
                }
                if (!"steps".equals(key) && !"when".equals(key) && !"otherwise".equals(key) && !"doCatch".equals(key)
                        && !"doFinally".equals(key) && runsCode(t.getValueNode())) {
                    return true;
                }
            }
        } else if (node instanceof SequenceNode seq) {
            for (Node n : seq.getValue()) {
                if (runsCode(n)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** The field of the data a split gives the elements of, such as lines, or null when it is not one. */
    private static String splitField(Node body) {
        String text = null;
        Node expression = value(body, "expression");
        Node language = expression instanceof MappingNode e ? e : body;
        for (String key : List.of("simple", "jsonpath")) {
            Node n = value(language, key);
            if (n instanceof MappingNode m) {
                n = value(m, "expression");
            }
            if (scalar(n) != null) {
                text = scalar(n).trim();
            }
        }
        Matcher m = text != null ? SPLIT_FIELD.matcher(text) : null;
        return m != null && m.matches() ? m.group(1) != null ? m.group(1) : m.group(2) : null;
    }

    /** The JSON data of the files the route consumes, as the project facts have them, or null. */
    private static ProjectFacts.JsonFields consumedData(String uri, MappingNode from, ProjectFacts facts) {
        if (facts == null || !"file".equals(scheme(uri))) {
            return null;
        }
        String directory = uri.length() > "file:".length() ? uri.substring("file:".length()) : null;
        if (directory != null && directory.contains("?")) {
            directory = directory.substring(0, directory.indexOf('?'));
        }
        if (directory == null || directory.isBlank()) {
            directory = scalar(value(value(from, "parameters"), "directoryName"));
        }
        return directory != null ? facts.jsonFields().get(ProjectFacts.directoryKey(directory)) : null;
    }

    private static String selfCopyMessage(UnsetCopy copy, List<UnsetCopy> copies, ProjectFacts.JsonFields data) {
        String name = copy.name();
        StringBuilder sb = new StringBuilder("Line ").append(copy.line()).append(": ${header.").append(name)
                .append("} is null here: no step before this line sets the header ").append(name)
                .append(", so setHeader ").append(name).append(" copies null to itself. ")
                .append(copy.kept() != null
                        ? "The route keeps " + name + " in " + copy.kept().what + ": write ${" + copy.kept().read + name
                          + "}"
                        : whereTheValueIs(name, copy.split(), data));
        // the other headers the same route copies before anything sets them, such as needed with ${header.qty}
        for (UnsetCopy other : copies) {
            if (other != copy && !other.name().equals(other.read())) {
                sb.append("; the same for ${header.").append(other.read()).append("} on line ").append(other.line())
                        .append(": ").append(other.kept() != null
                                ? "${" + other.kept().read + other.read() + "}"
                                : readInstead(other.read(), other.split(), data));
            }
        }
        return sb.toString();
    }

    /**
     * The message of a header read in a split that nothing set: where the value is; when a step before the read gave
     * the message another body, the field is kept in a variable first, at the start of the split.
     */
    private static String unsetReadMessage(UnsetRead read, ProjectFacts.JsonFields data) {
        String name = read.name();
        String head = "Line " + read.line() + ": ${header." + name + "} is null here: no step before this line sets the"
                      + " header " + name + ". ";
        if (read.kept() != null) {
            return head + "The route keeps " + name + " in " + read.kept().what + ": write ${" + read.kept().read + name
                   + "}";
        }
        Set<String> element = read.split() != null && data != null ? data.elementFields().get(read.split()) : null;
        boolean ofTheData = element != null && !element.contains(name) && data.fields().contains(name);
        if (read.bodyStep() == null || ofTheData) {
            return head + whereTheValueIs(name, read.split(), data);
        }
        String body = element != null && element.contains(name)
                ? "In the split the body is one of the " + read.split() + " of " + data.where() + " ("
                  + String.join(", ", new TreeSet<>(element)) + ")"
                : "In the split the body is the element";
        return head + body + ", until " + read.bodyStep() + " on line " + read.bodyLine() + " replaces it: keep " + name
               + " in a variable first, as the first step of the split: setVariable: {name: " + name + ", simple: \"${body["
               + name + "]}\"}, and write ${variable." + name + "}";
    }

    /** What to write for a value the route has not set as a header: a field of the split element, or of the data. */
    private static String whereTheValueIs(String name, String split, ProjectFacts.JsonFields data) {
        Set<String> element = split != null && data != null ? data.elementFields().get(split) : null;
        if (element != null && element.contains(name)) {
            return "In the split the body is one of the " + split + " of " + data.where() + " ("
                   + String.join(", ", new TreeSet<>(element)) + "): write ${body[" + name + "]}";
        }
        if (element != null && data.fields().contains(name)) {
            return name + " is a field of the data that was split, not of one of the " + split + ": read it before the"
                   + " split, into a variable (setVariable), and write ${variable." + name + "} in the split";
        }
        if (split != null) {
            return "In the split the body is the element: a field of it is ${body[" + name + "]}";
        }
        return "Set it from where the value is, such as a field of the body (${body[" + name + "]}) or a variable"
               + " (${variable." + name + "})";
    }

    /** The read to write instead of a header nothing sets, for the tail of the message. */
    private static String readInstead(String name, String split, ProjectFacts.JsonFields data) {
        Set<String> element = split != null && data != null ? data.elementFields().get(split) : null;
        if (element != null && !element.contains(name) && data.fields().contains(name)) {
            return "${variable." + name + "}, set before the split";
        }
        return "${body[" + name + "]}";
    }

    /** A variableReceive of a step: the variable it sets. */
    private static void variableReceive(Node body, Seen seen) {
        String v = scalar(value(body, "variableReceive"));
        if (v != null) {
            seen.variables.add(v);
        }
    }

    private static void name(Node body, Set<String> into) {
        String n = scalar(value(body, "name"));
        if (n != null) {
            into.add(n);
        }
    }

    private static void namesOf(Node items, Set<String> into) {
        if (items instanceof SequenceNode list) {
            list.getValue().forEach(item -> name(item, into));
        }
    }

    private static Node value(Node node, String key) {
        if (node instanceof MappingNode map) {
            for (NodeTuple t : map.getValue()) {
                if (key.equals(scalar(t.getKeyNode()))) {
                    return t.getValueNode();
                }
            }
        }
        return null;
    }

    private static MappingNode mapping(Node node) {
        return node instanceof MappingNode m ? m : null;
    }

    private static String scalar(Node node) {
        return node instanceof ScalarNode s ? s.getValue() : null;
    }
}
