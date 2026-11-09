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
package org.apache.camel.dsl.jbang.core.common;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.cli.connector.RepeatedSend;
import org.apache.camel.dsl.jbang.core.commands.ai.HttpEndpoints;
import org.apache.camel.util.json.JsonObject;

/**
 * The feed of a chaos experiment for a route that serves HTTP (CAMEL-26793): such a route starts from platform-http or
 * the REST DSL, which has no producer, so it is fed with HTTP requests to the integration's own server (localhost and
 * the port of its HTTP server, as the status has them). The experiment sends them while it waits for the verdict, and
 * its traffic counts the status codes the clients got.
 */
public final class ChaosHttpFeed {

    /** Where the requests go: the method and the URL on the integration's own server. */
    public record Target(String method, String url) {

        @Override
        public String toString() {
            return method + " " + url;
        }
    }

    // a path to call, with an optional method: /orders/42, PUT /orders/42
    private static final Pattern PATH
            = Pattern.compile("(?:(GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS)\\s+)?(/\\S*)", Pattern.CASE_INSENSITIVE);

    private ChaosHttpFeed() {
    }

    /** Whether the feed is a path to call, such as /orders/42 or PUT /orders/42, rather than a route or endpoint. */
    public static boolean isPath(String feed) {
        return feed != null && PATH.matcher(feed.strip()).matches();
    }

    /** Whether the route starts from an input that is fed over the HTTP server of Camel: platform-http or rest. */
    static boolean servedByCamel(String from) {
        return from != null && (from.startsWith("platform-http:") || from.startsWith("rest:"));
    }

    // an HTTP server with its own address in the URI: netty-http:http://0.0.0.0:9000/hooks
    private static final Pattern OWN_ADDRESS = Pattern.compile(
            "(?:netty-http|undertow|jetty|vertx-http):(?://)?(https?)://([^/:?]+)(:\\d+)?([^?]*)(?:\\?.*)?");
    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "0.0.0.0", "::1", "[::1]");

    /**
     * The URL to call for a route that starts from an HTTP server with its own address on this machine (netty-http,
     * undertow, jetty), or null: the host it listens on is called as localhost.
     */
    static String ownAddress(String from) {
        Matcher m = from != null ? OWN_ADDRESS.matcher(from) : null;
        if (m == null || !m.matches() || !LOCAL_HOSTS.contains(m.group(2))) {
            return null;
        }
        String path = m.group(4).isEmpty() ? "/" : m.group(4);
        return m.group(1) + "://localhost" + (m.group(3) != null ? m.group(3) : "") + path;
    }

    /**
     * Where the requests of the feed go: for a path to call, or for a route that starts from platform-http or rest (by
     * its id or endpoint); null when the feed is for another route.
     *
     * @throws IllegalArgumentException when the feed is for HTTP but cannot be sent, with the reason
     */
    public static Target target(JsonObject status, JsonObject route, String feed) {
        Matcher m = PATH.matcher(feed.strip());
        String method;
        String path;
        String routeId = null;
        if (m.matches()) {
            method = m.group(1);
            path = m.group(2);
        } else if (route != null && ownAddress(route.getString("from")) instanceof String address) {
            // its own server on this machine: no port to look up
            String m0 = method(route.getString("from"));
            return new Target(m0 != null ? m0 : "POST", address);
        } else if (route != null && servedByCamel(route.getString("from"))) {
            routeId = route.getString("routeId");
            String from = route.getString("from");
            method = method(from);
            path = path(from);
        } else {
            return null;
        }
        HttpEndpoints.Served served = HttpEndpoints.fromStatus(status);
        int port = served != null ? served.port() : 0;
        if (port <= 0) {
            throw new IllegalArgumentException(
                    "The feed " + feed + " is sent over HTTP, but the port of the HTTP server of the integration is not"
                                               + " known from its status");
        }
        // the path as served, with the context path of the server or the REST DSL
        HttpEndpoints.Endpoint served0 = served(served, routeId, method, path);
        if (served0 != null) {
            path = served0.path();
            if (method == null && !"ANY".equals(served0.method())) {
                method = served0.method();
            }
        }
        if (path.contains("{")) {
            throw new IllegalArgumentException(
                    "Route " + (routeId != null ? routeId : feed) + " serves " + path + ", with a parameter: give the"
                                               + " feed as the path to call, such as POST "
                                               + path.replaceAll("\\{[^}]*}", "1"));
        }
        String m0 = method != null ? method.toUpperCase(Locale.ROOT) : "POST";
        return new Target(m0, "http://localhost:" + port + path);
    }

    private static HttpEndpoints.Endpoint served(
            HttpEndpoints.Served served, String routeId, String method, String path) {
        if (served == null) {
            return null;
        }
        HttpEndpoints.Endpoint answer = null;
        for (HttpEndpoints.Endpoint e : served.endpoints()) {
            if (routeId != null && routeId.equals(e.routeId())) {
                return e;
            }
            boolean samePath = e.path().equals(path) || (e.path().endsWith(path) && !"/".equals(path));
            if (samePath && (method == null || method.equalsIgnoreCase(e.method()) || "ANY".equals(e.method()))) {
                if (answer == null || e.path().equals(path)) {
                    answer = e;
                }
            }
        }
        return answer;
    }

    /** The method of a platform-http or rest input: the one it restricts to, else null. */
    static String method(String from) {
        if (from.startsWith("rest:")) {
            String[] parts = from.split(":", 3);
            return parts.length == 3 ? parts[1].toUpperCase(Locale.ROOT) : null;
        }
        int query = from.indexOf('?');
        if (query > 0) {
            for (String option : from.substring(query + 1).split("&")) {
                if (option.startsWith("httpMethodRestrict=")) {
                    return option.substring(19).split("[,%]")[0].trim().toUpperCase(Locale.ROOT);
                }
            }
        }
        return null;
    }

    /** The path of a platform-http or rest input, such as /payment. */
    static String path(String from) {
        String path;
        if (from.startsWith("rest:")) {
            String[] parts = from.split(":", 3);
            path = parts.length == 3 ? parts[2] : "";
        } else {
            path = from.substring(from.indexOf(':') + 1);
        }
        int query = path.indexOf('?');
        if (query >= 0) {
            path = path.substring(0, query);
        }
        while (path.startsWith("//")) {
            path = path.substring(1);
        }
        return path.startsWith("/") ? path : "/" + path;
    }

    /** The bodies of the feed: the text, the content of a file, or of each file of a folder (in name order). */
    static List<String> bodies(String body) throws IOException {
        if (body == null) {
            return List.of("");
        }
        if (!body.startsWith("file:")) {
            return List.of(body);
        }
        File file = new File(body.substring(5));
        if (file.isDirectory()) {
            File[] files = file.listFiles(File::isFile);
            if (files == null || files.length == 0) {
                throw new IOException("No files in the folder " + file);
            }
            Arrays.sort(files);
            List<String> answer = new ArrayList<>();
            for (File f : files) {
                answer.add(Files.readString(f.toPath(), StandardCharsets.UTF_8));
            }
            return answer;
        }
        return List.of(Files.readString(file.toPath(), StandardCharsets.UTF_8));
    }

    /** The headers of the feed, as key=value (or key: value) lines, as HTTP headers. */
    static Map<String, String> headers(String headers, String body) {
        Map<String, String> answer = new LinkedHashMap<>();
        if (headers != null) {
            for (String line : headers.split("\n")) {
                int sep = indexOfSeparator(line);
                if (sep > 0) {
                    answer.put(line.substring(0, sep).strip(), line.substring(sep + 1).strip());
                }
            }
        }
        boolean typed = answer.keySet().stream().anyMatch(k -> k.equalsIgnoreCase("Content-Type"));
        String b = body != null ? body.stripLeading() : "";
        if (!typed && (b.startsWith("{") || b.startsWith("["))) {
            answer.put("Content-Type", "application/json");
        }
        return answer;
    }

    private static int indexOfSeparator(String line) {
        int eq = line.indexOf('=');
        int colon = line.indexOf(':');
        if (eq < 0) {
            return colon;
        }
        return colon < 0 ? eq : Math.min(eq, colon);
    }

    /**
     * Sends the requests of a feed, one after the other with the pause of its interval, until all are sent or it is
     * stopped (the experiment ended); counts the status codes the clients got.
     */
    static final class Sender implements Runnable {

        private final ChaosExperimentRun.Integration integration;
        private final Target target;
        private final ChaosExperimentRun.Feed feed;
        private final ChaosExperimentRun.Clock clock;
        private final Map<Integer, Long> codes = new TreeMap<>();
        private volatile boolean stopped;
        private long sent;
        private long failed;
        private long planned;
        private long elapsed;
        private String error;

        Sender(ChaosExperimentRun.Integration integration, Target target, ChaosExperimentRun.Feed feed,
               ChaosExperimentRun.Clock clock) {
            this.integration = integration;
            this.target = target;
            this.feed = feed;
            this.clock = clock;
        }

        void stop() {
            stopped = true;
        }

        @Override
        public void run() {
            long start = clock.now();
            try {
                List<String> bodies = bodies(feed.body());
                long[] interval = RepeatedSend.interval(feed.interval());
                int count = feed.repeat() > 0 ? feed.repeat() : bodies.size();
                synchronized (this) {
                    planned = count;
                }
                for (int i = 0; i < count && !stopped; i++) {
                    if (i > 0) {
                        long pause = interval[1] > interval[0]
                                ? ThreadLocalRandom.current().nextLong(interval[0], interval[1] + 1) : interval[0];
                        if (pause > 0) {
                            clock.sleep(pause);
                        }
                        if (stopped) {
                            break;
                        }
                    }
                    String body = bodies.get(i % bodies.size());
                    try {
                        int code = integration.http(target.method(), target.url(), headers(feed.headers(), body),
                                body);
                        synchronized (this) {
                            sent++;
                            codes.merge(code, 1L, Long::sum);
                        }
                    } catch (IOException | RuntimeException e) {
                        synchronized (this) {
                            failed++;
                            error = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                        }
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (IOException | RuntimeException e) {
                synchronized (this) {
                    error = e.getMessage();
                }
            } finally {
                synchronized (this) {
                    elapsed = clock.now() - start;
                }
            }
        }

        /** The traffic: the requests sent and answered with each status code, failed (no answer), and why. */
        synchronized JsonObject toJson() {
            JsonObject answer = new JsonObject();
            answer.put("endpoint", target.toString());
            answer.put("sent", sent + failed);
            answer.put("failed", failed);
            answer.put("elapsed", elapsed);
            JsonObject statusCodes = new JsonObject();
            codes.forEach((code, n) -> statusCodes.put(Integer.toString(code), n));
            answer.put("statusCodes", statusCodes);
            if (sent + failed < planned) {
                // the experiment ended before all were sent
                answer.put("planned", planned);
            }
            if (sent == 0 && error != null) {
                answer.put("status", "failed");
                answer.put("error", error);
            } else {
                answer.put("status", "success");
                if (error != null) {
                    answer.put("lastError", error);
                }
            }
            return answer;
        }
    }
}
