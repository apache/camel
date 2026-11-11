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

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.dsl.jbang.core.common.RuntimeHelper;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

/**
 * About an integration as text and JSON (CAMEL-26700), for {@code camel get about} and the AI tools: {@link About}
 * (what it does, from its sources) and, when it runs, what has happened: the exchanges of its routes since it started
 * (its status), and the last minutes from the timeline it records (the timeline dev console).
 */
public final class AboutReport {

    private AboutReport() {
    }

    /**
     * About the integration as JSON.
     *
     * @param model    about it, from its sources
     * @param status   its status (the status file of the CLI connector), or null when it does not run
     * @param timeline its timeline (the timeline dev console), or null
     */
    public static JsonObject json(About.Model model, JsonObject status, JsonObject timeline) {
        Map<String, JsonObject> routeStatus = routeStatus(status);
        JsonObject answer = new JsonObject();
        if (model.purpose() != null) {
            answer.put("purpose", model.purpose());
            answer.put("purposeFrom", model.purposeFrom());
        }
        JsonArray in = new JsonArray();
        for (About.In e : model.in()) {
            JsonObject jo = new JsonObject();
            jo.put("kind", e.kind());
            jo.put("label", e.label());
            jo.put("routeId", e.route());
            putTotal(jo, routeStatus.get(e.route()));
            in.add(jo);
        }
        answer.put("in", in);
        JsonArray routes = new JsonArray();
        for (About.RouteLine r : model.routes()) {
            JsonObject jo = new JsonObject();
            jo.put("routeId", r.routeId());
            jo.put("depth", r.depth());
            if (r.how() != null) {
                jo.put("calledHow", r.how());
            }
            jo.put("from", r.from());
            jo.put("steps", r.steps());
            putTotal(jo, routeStatus.get(r.routeId()));
            routes.add(jo);
        }
        answer.put("routes", routes);
        JsonArray out = new JsonArray();
        for (About.Out o : model.out()) {
            JsonObject jo = new JsonObject();
            jo.put("category", o.category());
            jo.put("uri", o.uri());
            jo.put("direction", o.direction());
            jo.put("routeId", o.route());
            Long hits = sends(status, o.uri(), o.route());
            if (hits != null) {
                jo.put("sends", hits);
            }
            out.add(jo);
        }
        answer.put("out", out);
        if (timeline != null) {
            answer.put("lastMinutes", lastMinutes(timeline));
        }
        return answer;
    }

    /**
     * About the integration that runs from the directory, if one does: its pid and name, and {@link #json} with its
     * status and timeline; null when none runs from there.
     */
    public static JsonObject running(Path dir, About.Model model) {
        Path wanted = dir.toAbsolutePath().normalize();
        for (RuntimeHelper.ProcessInfo p : RuntimeHelper.discoverProcesses()) {
            JsonObject status = RuntimeHelper.readStatus(p.pid());
            JsonObject runtime = status != null ? (JsonObject) status.get("runtime") : null;
            String directory = runtime != null ? runtime.getString("directory") : null;
            if (directory == null || !Path.of(directory).toAbsolutePath().normalize().equals(wanted)) {
                continue;
            }
            JsonObject timeline = null;
            try {
                JsonObject request = new JsonObject();
                request.put("action", "timeline");
                request.put("exchanges", "true");
                timeline = RuntimeHelper.executeAction(p.pid(), request, 5000);
            } catch (IOException e) {
                // no timeline: the routes since the start only
            }
            JsonObject answer = json(model, status, timeline != null && timeline.containsKey("routes") ? timeline : null);
            answer.put("pid", Long.toString(p.pid()));
            answer.put("name", p.name());
            return answer;
        }
        return null;
    }

    /** About the integration as text, from its JSON ({@link #json}). */
    public static String text(String title, JsonObject about) {
        StringBuilder sb = new StringBuilder();
        sb.append(title).append('\n');
        if (about.getString("purpose") != null) {
            sb.append(about.getString("purpose")).append('\n');
            sb.append("  (").append(about.getString("purposeFrom")).append(")\n");
        }
        sb.append("\nIn\n");
        List<JsonObject> in = list(about, "in");
        if (in.isEmpty()) {
            sb.append("  nothing from outside\n");
        }
        for (JsonObject e : in) {
            sb.append(String.format("  %-44s %s%n", e.getString("label"), total(e)));
        }
        sb.append("\nRoutes\n");
        for (JsonObject r : list(about, "routes")) {
            String how = "call".equals(r.getString("calledHow")) ? "↳ " : "async".equals(r.getString("calledHow")) ? "⇢ " : "";
            int steps = r.getIntegerOrDefault("steps", 0);
            String line = "  ".repeat(r.getIntegerOrDefault("depth", 0)) + how + r.getString("routeId") + "  "
                          + r.getString("from") + " → " + steps + (steps == 1 ? " step" : " steps");
            sb.append(String.format("  %-44s %s%n", line, total(r)));
        }
        sb.append("\nOut\n");
        List<JsonObject> out = list(about, "out");
        if (out.isEmpty()) {
            sb.append("  nothing outside the integration\n");
        }
        for (JsonObject o : out) {
            sb.append(String.format("  %-44s %s%n", o.getString("uri") + "  from " + o.getString("routeId"),
                    o.get("sends") != null ? o.getLong("sends") + "" : ""));
        }
        JsonObject last = (JsonObject) about.get("lastMinutes");
        if (last != null) {
            sb.append("\nLast ").append(minutes(last.getIntegerOrDefault("seconds", 0))).append('\n');
            sb.append("  ").append(last.getLongOrDefault("exchanges", 0)).append(" exchanges");
            for (String k : List.of("recovered", "handled", "failed")) {
                if (last.getLongOrDefault(k, 0) > 0) {
                    sb.append(" · ").append(last.getLong(k)).append(' ').append(k);
                }
            }
            JsonObject cause = (JsonObject) last.get("cause");
            if (cause != null) {
                sb.append(" · ").append(cause.getString("label")).append(" ×").append(cause.getLong("count"))
                        .append(" (last ").append(ago(cause.getLongOrDefault("lastSecondsAgo", 0))).append(')');
            }
            sb.append('\n');
            for (JsonObject r : list(last, "routes")) {
                StringBuilder line = new StringBuilder();
                line.append(String.format("  %-20s %6d", r.getString("routeId"), r.getLongOrDefault("exchanges", 0)));
                if (r.get("worst") != null) {
                    line.append("  ").append(r.getLong("worstCount")).append(' ').append(r.getString("worst"))
                            .append(' ').append(ago(r.getLongOrDefault("worstSecondsAgo", 0)));
                    if (r.getString("recoveredFrom") != null) {
                        line.append(" · ").append(r.getString("recoveredFrom"));
                    }
                }
                if (r.getLongOrDefault("circuitBreakerOpenSeconds", 0) > 0) {
                    line.append("  circuit breaker open ").append(r.getLong("circuitBreakerOpenSeconds")).append('s');
                }
                if (r.getLongOrDefault("downSeconds", 0) > 0) {
                    line.append("  down ").append(r.getLong("downSeconds")).append('s');
                }
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }

    /**
     * What happened in the seconds of the timeline: the exchanges and how they ended, the cause that happened most with
     * when it last did, and per route the worst that happened to it with when, the failure a recovery recovered from,
     * and the seconds a circuit breaker was open or a chaos rule had it down.
     */
    static JsonObject lastMinutes(JsonObject timeline) {
        int n = timeline.getIntegerOrDefault("seconds", 0);
        JsonObject answer = new JsonObject();
        answer.put("seconds", n);
        long exchanges = 0;
        Map<String, Long> totals = new LinkedHashMap<>();
        Map<String, long[]> open = new LinkedHashMap<>();
        for (JsonObject cb : list(timeline, "circuitBreakers")) {
            long[] states = values(cb, "states", n);
            long[] v = open.computeIfAbsent(cb.getString("routeId"), k -> new long[n]);
            for (int i = 0; i < n; i++) {
                v[i] = Math.max(v[i], states[i]);
            }
        }
        // the failure a recovered exchange of a route was redelivered for, the last one
        Map<String, String> recoveredFrom = new LinkedHashMap<>();
        String anyRecoveredFrom = null;
        for (JsonObject e : list(timeline, "exchanges")) {
            if (e.getBooleanOrDefault("redelivered", false) && !e.getBooleanOrDefault("failed", false)
                    && !e.getBooleanOrDefault("handled", false) && e.getString("exception") != null) {
                recoveredFrom.put(e.getString("routeId"), e.getString("exception"));
                anyRecoveredFrom = e.getString("exception");
            }
        }
        JsonArray routes = new JsonArray();
        for (JsonObject r : list(timeline, "routes")) {
            String routeId = r.getString("routeId");
            long[] completed = values(r, "completed", n);
            long[] recovered = values(r, "recovered", n);
            long[] handled = values(r, "handled", n);
            long[] failed = values(r, "failed", n);
            long done = sum(completed) + sum(recovered) + sum(handled) + sum(failed);
            totals.merge("recovered", sum(recovered), Long::sum);
            totals.merge("handled", sum(handled), Long::sum);
            totals.merge("failed", sum(failed), Long::sum);
            JsonObject jo = new JsonObject();
            jo.put("routeId", routeId);
            jo.put("exchanges", done);
            long[] worst = sum(failed) > 0 ? failed : sum(handled) > 0 ? handled : sum(recovered) > 0 ? recovered : null;
            if (worst != null) {
                jo.put("worst", worst == failed ? "failed" : worst == handled ? "handled" : "recovered");
                jo.put("worstCount", sum(worst));
                jo.put("worstSecondsAgo", n - 1 - last(worst));
                if (worst == recovered) {
                    String from = recoveredFrom.getOrDefault(routeId, anyRecoveredFrom);
                    if (from != null) {
                        jo.put("recoveredFrom", from);
                    }
                }
            }
            long[] states = open.get(routeId);
            if (states != null) {
                jo.put("circuitBreakerOpenSeconds", Arrays.stream(states).filter(v -> v >= 2).count());
            }
            long down = r.get("down") != null ? sum(values(r, "down", n)) : 0;
            if (down > 0) {
                jo.put("downSeconds", down);
            }
            routes.add(jo);
        }
        for (Object v : timeline.getCollectionOrDefault("exchangesDone", List.of())) {
            exchanges += v instanceof Number num ? num.longValue() : 0;
        }
        answer.put("exchanges", exchanges);
        totals.forEach(answer::put);
        // the cause that happened most
        JsonObject cause = null;
        long most = 0;
        for (JsonObject e : list(timeline, "errors")) {
            long[] counts = values(e, "counts", n);
            if (sum(counts) > most) {
                most = sum(counts);
                String exception = e.getString("exception");
                String label = exception != null ? exception.substring(exception.lastIndexOf('.') + 1) : "error";
                if (e.get("statusCode") != null) {
                    label += " " + e.getInteger("statusCode");
                }
                if (e.getString("chaosRule") != null) {
                    label = "chaos rule " + e.getString("chaosRule") + ": " + label;
                }
                cause = new JsonObject();
                cause.put("label", label);
                cause.put("routeId", e.getString("routeId"));
                cause.put("count", most);
                cause.put("lastSecondsAgo", n - 1 - last(counts));
            }
        }
        if (cause != null) {
            answer.put("cause", cause);
        }
        answer.put("routes", routes);
        return answer;
    }

    private static Map<String, JsonObject> routeStatus(JsonObject status) {
        Map<String, JsonObject> answer = new LinkedHashMap<>();
        if (status != null && status.get("routes") instanceof List<?> routes) {
            for (Object o : routes) {
                if (o instanceof JsonObject r && r.getString("routeId") != null) {
                    answer.put(r.getString("routeId"), r);
                }
            }
        }
        return answer;
    }

    private static void putTotal(JsonObject jo, JsonObject route) {
        if (route != null && route.get("statistics") instanceof Map<?, ?> stats) {
            if (stats.get("exchangesTotal") instanceof Number total) {
                jo.put("exchangesTotal", total.longValue());
            }
            if (stats.get("exchangesFailed") instanceof Number failed && failed.longValue() > 0) {
                jo.put("exchangesFailed", failed.longValue());
            }
        }
    }

    /**
     * How many times the route sent to the endpoint, from the endpoint statistics in the status; null when there is no
     * such endpoint (a URI with a placeholder, or no statistics).
     */
    static Long sends(JsonObject status, String uri, String route) {
        if (status == null || !(status.get("endpoints") instanceof JsonObject endpoints)
                || !(endpoints.get("endpoints") instanceof List<?> list)) {
            return null;
        }
        String base = baseUri(uri);
        Long answer = null;
        for (Object o : list) {
            if (o instanceof JsonObject ep && "out".equals(ep.getString("direction"))
                    && (route == null || route.equals(ep.getString("routeId"))) && base.equals(baseUri(ep.getString("uri")))
                    && ep.get("hits") instanceof Number hits) {
                answer = (answer != null ? answer : 0) + hits.longValue();
            }
        }
        return answer;
    }

    /** The URI without its options, and the same however many slashes follow the scheme: file:/x is file:///x. */
    public static String baseUri(String uri) {
        if (uri == null) {
            return "";
        }
        int q = uri.indexOf('?');
        String answer = q >= 0 ? uri.substring(0, q) : uri;
        answer = answer.replaceFirst("^([a-zA-Z0-9+.-]+):/{0,3}", "$1:");
        // the runtime has the URI encoded as Camel normalizes it (rest-openapi://stock-api.json%23reserveStock,
        // platform-http:///stock/%7Bsku%7D), the sources as written
        answer = decoded(answer);
        return answer.endsWith("/") ? answer.substring(0, answer.length() - 1) : answer;
    }

    private static String decoded(String uri) {
        if (uri.indexOf('%') < 0) {
            return uri;
        }
        try {
            // a + is a plus in a path, not a space
            return URLDecoder.decode(uri.replace("+", "%2B"), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            // a % that is no escape
            return uri;
        }
    }

    private static String total(JsonObject jo) {
        if (jo.get("exchangesTotal") == null) {
            return "";
        }
        return jo.getLong("exchangesTotal")
               + (jo.get("exchangesFailed") != null ? " (" + jo.getLong("exchangesFailed") + " failed)" : "");
    }

    private static String minutes(int seconds) {
        return seconds % 60 == 0 ? seconds / 60 + "m" : seconds + "s";
    }

    private static String ago(long seconds) {
        if (seconds <= 0) {
            return "now";
        }
        return seconds < 60 ? seconds + "s ago" : seconds / 60 + "m" + (seconds % 60 > 0 ? seconds % 60 + "s" : "") + " ago";
    }

    private static List<JsonObject> list(JsonObject jo, String key) {
        List<JsonObject> answer = new ArrayList<>();
        if (jo.get(key) instanceof List<?> l) {
            for (Object o : l) {
                if (o instanceof JsonObject e) {
                    answer.add(e);
                }
            }
        }
        return answer;
    }

    private static long[] values(JsonObject jo, String key, int n) {
        long[] answer = new long[n];
        if (jo.get(key) instanceof List<?> l) {
            for (int i = 0; i < Math.min(n, l.size()); i++) {
                answer[i] = l.get(i) instanceof Number num ? num.longValue() : 0;
            }
        }
        return answer;
    }

    private static long sum(long[] values) {
        long sum = 0;
        for (long v : values) {
            sum += Math.max(0, v);
        }
        return sum;
    }

    private static int last(long[] values) {
        for (int i = values.length - 1; i >= 0; i--) {
            if (values[i] > 0) {
                return i;
            }
        }
        return values.length - 1;
    }
}
