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

import java.nio.file.Files;
import java.nio.file.Path;
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
 * A {@code rest-openapi} call to an operation whose path holds a parameter, with no step before it that sets a header
 * (or variable) of that name: the request goes out with {@code {sku}} in the path and the service answers 404
 * (CAMEL-24992). The runtime names the parameter once the route has run (CAMEL-24986); this says it before, while the
 * route is written.
 * <p/>
 * The check stays quiet when it cannot prove the header is missing: the route consumes from an endpoint that can carry
 * any header (only timer, scheduler, cron, quartz and file cannot), or a step before the call does something the walk
 * cannot follow (a bean, a processor, a script, a call to another endpoint).
 */
final class OpenApiPathParams {

    /** Consumers whose messages carry no headers of the application's choosing. */
    private static final Set<String> PLAIN_CONSUMERS = Set.of("timer", "scheduler", "cron", "quartz", "file");
    /** Steps that set no header but the ones the walk reads (setHeader, setHeaders, setVariable, setVariables). */
    private static final Set<String> SAFE_STEPS = Set.of(
            "log", "setBody", "transform", "setProperty", "removeProperty", "removeProperties", "removeHeader",
            "removeHeaders", "removeVariable", "convertBodyTo", "marshal", "unmarshal", "split", "filter", "choice",
            "loop", "delay", "validate", "stop", "aggregate");
    /** Endpoints a call to sets no header of the application's choosing. */
    private static final Set<String> SAFE_ENDPOINTS = Set.of("log", "mock", "rest-openapi");
    private static final Pattern PATH_PARAMETER = Pattern.compile("\\{([^}/]+)}");

    private OpenApiPathParams() {
    }

    /** The problems, each "Line N: message". */
    static List<String> validate(String content, Path directory) {
        List<String> answer = new ArrayList<>();
        if (content == null || directory == null || !content.contains("rest-openapi")) {
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
        try {
            walk(items, directory, answer, callLines(content));
        } catch (RuntimeException e) {
            // a shape the walk does not know: a check must never fail the validation
        }
        return answer;
    }

    private static void walk(List<?> items, Path directory, List<String> answer, List<Integer> callLines) {
        int[] call = { 0 };
        for (Object item : items) {
            Map<?, ?> from = null;
            if (item instanceof Map<?, ?> m && m.get("route") instanceof Map<?, ?> route
                    && route.get("from") instanceof Map<?, ?> f) {
                from = f;
            } else if (item instanceof Map<?, ?> m && m.get("from") instanceof Map<?, ?> f) {
                from = f;
            }
            if (from == null) {
                continue;
            }
            Walk walk = new Walk(directory, answer, callLines, call);
            walk.followed = PLAIN_CONSUMERS.contains(scheme(String.valueOf(from.get("uri"))));
            walk.steps(from.get("steps"));
        }
    }

    /** The lines that call rest-openapi, in document order. */
    private static List<Integer> callLines(String content) {
        List<Integer> lines = new ArrayList<>();
        String[] all = content.split("\n", -1);
        for (int i = 0; i < all.length; i++) {
            String line = all[i];
            if (line.contains("rest-openapi") && (line.contains("uri") || line.contains("to:"))
                    && !line.stripLeading().startsWith("#")) {
                lines.add(i + 1);
            }
        }
        return lines;
    }

    private static String scheme(String uri) {
        int colon = uri.indexOf(':');
        return colon > 0 ? uri.substring(0, colon) : uri;
    }

    private static final class Walk {
        private final Path directory;
        private final List<String> answer;
        private final List<Integer> callLines;
        private final int[] call;
        private final Set<String> set = new LinkedHashSet<>();
        private final Set<String> properties = new LinkedHashSet<>();
        /** Whether every header so far is known: false after a step the walk cannot follow. */
        private boolean followed;

        Walk(Path directory, List<String> answer, List<Integer> callLines, int[] call) {
            this.directory = directory;
            this.answer = answer;
            this.callLines = callLines;
            this.call = call;
        }

        void steps(Object steps) {
            if (!(steps instanceof List<?> list)) {
                return;
            }
            for (Object step : list) {
                if (!(step instanceof Map<?, ?> m) || m.size() != 1) {
                    followed = false;
                    continue;
                }
                Map.Entry<?, ?> e = m.entrySet().iterator().next();
                step(String.valueOf(e.getKey()), e.getValue());
            }
        }

        private void step(String eip, Object body) {
            switch (eip) {
                case "setHeader" -> name(body, set);
                case "setVariable" -> name(body, set);
                case "setProperty" -> name(body, properties);
                case "setHeaders" -> names(body, "headers");
                case "setVariables" -> names(body, "variables");
                case "to", "toD" -> to(eip, body);
                default -> {
                    if (!SAFE_STEPS.contains(eip)) {
                        followed = false;
                    }
                }
            }
            nested(body);
        }

        private static void name(Object body, Set<String> into) {
            if (body instanceof Map<?, ?> m && m.get("name") != null) {
                into.add(String.valueOf(m.get("name")));
            }
        }

        private void names(Object body, String key) {
            if (body instanceof Map<?, ?> m && m.get(key) instanceof List<?> list) {
                list.forEach(h -> name(h, set));
            }
        }

        /** The steps nested in an EIP: its steps, and those of its when, otherwise, doCatch and doFinally. */
        private void nested(Object body) {
            if (!(body instanceof Map<?, ?> m)) {
                return;
            }
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if ("steps".equals(e.getKey())) {
                    steps(e.getValue());
                } else if (e.getValue() instanceof List<?> list) {
                    list.forEach(this::nested);
                } else if (e.getValue() instanceof Map<?, ?>) {
                    nested(e.getValue());
                }
            }
        }

        private void to(String eip, Object body) {
            String uri = body instanceof Map<?, ?> m ? String.valueOf(m.get("uri")) : String.valueOf(body);
            Map<?, ?> parameters = body instanceof Map<?, ?> m && m.get("parameters") instanceof Map<?, ?> p ? p : Map.of();
            String scheme = scheme(uri);
            if (!"rest-openapi".equals(scheme)) {
                if (!SAFE_ENDPOINTS.contains(scheme)) {
                    followed = false;
                }
                return;
            }
            int line = call[0] < callLines.size() ? callLines.get(call[0]) : 0;
            call[0]++;
            if (!followed || "toD".equals(eip)) {
                return;
            }
            Set<String> options = new LinkedHashSet<>();
            parameters.keySet().forEach(k -> options.add(String.valueOf(k)));
            // rest-openapi:spec#operationId, or plain rest-openapi with the specificationUri and operationId options
            String path = uri.length() > scheme.length() ? uri.substring(scheme.length() + 1) : "";
            int q = path.indexOf('?');
            if (q >= 0) {
                for (String pair : path.substring(q + 1).split("&")) {
                    options.add(pair.split("=", 2)[0]);
                }
                path = path.substring(0, q);
            }
            String spec
                    = parameters.get("specificationUri") != null ? String.valueOf(parameters.get("specificationUri")) : null;
            String operation = parameters.get("operationId") != null ? String.valueOf(parameters.get("operationId")) : null;
            int hash = path.indexOf('#');
            if (hash >= 0) {
                spec = spec != null ? spec : path.substring(0, hash);
                operation = operation != null ? operation : path.substring(hash + 1);
            } else if (operation == null && !path.isEmpty()) {
                operation = path;
            }
            String template = operationPath(spec, operation);
            if (template == null) {
                return; // the specification or the operation is not known: nothing can be said
            }
            Matcher m = PATH_PARAMETER.matcher(template);
            while (m.find()) {
                String param = m.group(1);
                if (set.contains(param) || options.contains(param)) {
                    continue;
                }
                answer.add("Line " + line + ": the operation " + operation + " (" + template + ") needs the path parameter "
                           + param + ", and no step before this call sets a header " + param
                           + ": the request goes out with {" + param + "} in the path and the service answers 404"
                           + (properties.contains(param)
                                   ? " (the exchange property " + param + " is not used for the path)"
                                   : "")
                           + "; add - setHeader: {name: " + param + ", expression: {simple: ...}} before the call");
            }
        }

        /** The path of the operation in the specification beside the route, or null when either is not known. */
        private String operationPath(String spec, String operation) {
            if (operation == null || operation.isBlank()) {
                return null;
            }
            String file = spec == null || spec.isBlank() ? "openapi.json" : spec;
            for (String prefix : List.of("file:", "classpath:")) {
                if (file.startsWith(prefix)) {
                    file = file.substring(prefix.length());
                }
            }
            if (file.contains("://") || file.contains("{{")) {
                return null;
            }
            Path p = directory.resolve(file);
            if (!Files.isRegularFile(p)) {
                p = directory.resolve("src/main/resources").resolve(file);
            }
            if (!Files.isRegularFile(p)) {
                return null;
            }
            Map<?, ?> paths;
            try {
                paths = OpenApiVerbs.paths(Files.readString(p));
            } catch (Exception e) {
                return null;
            }
            if (paths == null) {
                return null;
            }
            for (Map.Entry<?, ?> e : paths.entrySet()) {
                if (e.getValue() instanceof Map<?, ?> operations) {
                    for (Object o : operations.values()) {
                        if (o instanceof Map<?, ?> details && operation.equals(String.valueOf(details.get("operationId")))) {
                            return String.valueOf(e.getKey());
                        }
                    }
                }
            }
            return null;
        }
    }
}
