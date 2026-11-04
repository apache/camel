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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * A route that keeps a value in one place (a header, an exchange property or a variable) and reads it from another gets
 * null with no error (CAMEL-25516): {@code toD: http://localhost:8080/stock/${header.sku}} after
 * {@code setProperty: sku} calls {@code /stock/}. {@link HeaderChecks} says this for Camel's own names
 * (CamelTimerCounter); this is the same mix-up with the route's own names.
 * <p/>
 * Quiet where the name can come from elsewhere: a route that consumes from an endpoint that can carry headers (only
 * timer, scheduler, cron, quartz and file cannot), a path parameter {X} of a rest or platform-http path, and a name the
 * route sets in both forms.
 */
final class HeaderPropertyMixups {

    private static final Set<String> PLAIN_CONSUMERS = Set.of("timer", "scheduler", "cron", "quartz", "file");
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
}
