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

    private HeaderPropertyMixups() {
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
            answer.add("Line " + line + ": " + token + " reads " + kind.what + " " + name + ", but the route keeps " + name
                       + " in " + kept.what + " (the " + kind.what.substring(kind.what.indexOf(' ') + 1)
                       + " is null here): write ${" + kept.read + name + "}");
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
