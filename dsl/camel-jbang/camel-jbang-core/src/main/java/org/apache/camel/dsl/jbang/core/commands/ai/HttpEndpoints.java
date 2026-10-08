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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

/**
 * What a running integration serves over HTTP (CAMEL-25307), read from its status: the Rest DSL services
 * ({@code rests}) and the platform-http endpoints ({@code platform-http}), the port of the embedded server and the
 * OpenAPI contract of a contract-first service. Pure and defensive like {@link AppFeatures}.
 */
public final class HttpEndpoints {

    /** How much of a response body http_request returns; a small model's context is the limit. */
    static final int MAX_BODY_CHARS = 6000;

    /** How much of each OpenAPI contract get_http_endpoints returns with includeSpec. */
    static final int MAX_SPEC_CHARS = 12000;

    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "0.0.0.0", "::1", "[::1]");

    /** The components whose consumers serve HTTP. */
    static final Set<String> HTTP_COMPONENTS = Set.of("platform-http", "rest", "rest-openapi");

    /**
     * One served operation.
     *
     * @param method        the HTTP method, ANY when the endpoint takes every method
     * @param path          the path, with {placeholders}
     * @param consumes      the accepted media types, null when not known
     * @param produces      the returned media types, null when not known
     * @param routeId       the route, null when not known
     * @param operationId   the OpenAPI operation, null when not contract-first
     * @param source        rest (Rest DSL) or platform-http
     * @param specification whether this is the api-doc endpoint that serves the contract itself
     */
    public record Endpoint(
            String method, String path, String consumes, String produces, String routeId, String operationId,
            String source, boolean specification) {
    }

    /**
     * What the integration serves.
     *
     * @param port      the port of the embedded server, 0 when not known
     * @param basePath  the path all operations share, empty when none
     * @param contract  the file name of the OpenAPI contract, null when not contract-first
     * @param endpoints the operations, Rest DSL first
     * @param signals   the status keys that told, with what they said
     */
    public record Served(int port, String basePath, String contract, List<Endpoint> endpoints,
            Map<String, String> signals) {

        public Served {
            endpoints = List.copyOf(endpoints);
            signals = Collections.unmodifiableMap(new LinkedHashMap<>(signals));
        }

        /** {@code http://localhost:<port><basePath>}, null when the port is not known. */
        public String baseUrl() {
            return port > 0 ? "http://localhost:" + port + basePath : null;
        }
    }

    private HttpEndpoints() {
    }

    /** What the status tells about HTTP, or null when the integration serves nothing over HTTP. */
    public static Served fromStatus(Map<?, ?> status) {
        if (status == null) {
            return null;
        }
        Map<String, String> signals = new LinkedHashMap<>();
        List<Endpoint> endpoints = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        int port = 0;
        String contract = null;

        // Rest DSL services: rests.rests[] with url, method, consumes, produces, routeId, operationId
        List<Map<?, ?>> rests = objects(section(status, "rests").get("rests"));
        for (Map<?, ?> r : rests) {
            String url = text(r.get("url"));
            String path = pathOf(url);
            String method = upper(text(r.get("method")));
            if (path == null) {
                continue;
            }
            if (port == 0) {
                port = portOf(url);
            }
            if (contract == null) {
                contract = fileName(text(r.get("specificationUri")));
            }
            endpoints.add(new Endpoint(
                    method != null ? method : "ANY", path, text(r.get("consumes")), text(r.get("produces")),
                    text(r.get("routeId")), text(r.get("operationId")), "rest",
                    Boolean.TRUE.equals(r.get("specification"))));
            seen.add((method != null ? method : "ANY") + " " + path);
        }
        if (!rests.isEmpty()) {
            signals.put("rests", rests.size() + " service(s)");
        }

        // platform-http: the server URL and the endpoints, one per verb; the Rest DSL ones are already listed
        Map<?, ?> php = section(status, "platform-http");
        String server = text(php.get("server"));
        int serverPort = portOf(server);
        if (serverPort > 0) {
            // the embedded server knows the port; a Rest DSL URL may carry a configured one instead
            port = serverPort;
        }
        List<Map<?, ?>> phpEndpoints = objects(php.get("endpoints"));
        for (Map<?, ?> e : phpEndpoints) {
            String path = text(e.get("path"));
            if (path == null) {
                path = pathOf(text(e.get("url")));
            }
            if (path == null) {
                continue;
            }
            if (!path.startsWith("/")) {
                path = "/" + path;
            }
            if (port == 0) {
                port = portOf(text(e.get("url")));
            }
            String verbs = text(e.get("verbs"));
            List<String> methods = new ArrayList<>();
            if (verbs != null) {
                for (String v : verbs.split(",")) {
                    if (!v.isBlank()) {
                        methods.add(upper(v.trim()));
                    }
                }
            }
            if (methods.isEmpty()) {
                methods.add("ANY");
            }
            for (String m : methods) {
                if (seen.add(m + " " + path)) {
                    endpoints.add(new Endpoint(
                            m, path, text(e.get("consumes")), text(e.get("produces")), null, null,
                            "platform-http", false));
                }
            }
        }
        if (server != null || !phpEndpoints.isEmpty()) {
            signals.put("platform-http", server != null ? server : phpEndpoints.size() + " endpoint(s)");
        }

        // HTTP consumers in the endpoint registry and the route inputs, for a status without the consoles above
        Set<String> schemes = new LinkedHashSet<>();
        List<String> uris = new ArrayList<>();
        for (Map<?, ?> ep : objects(section(status, "endpoints").get("endpoints"))) {
            uris.add(text(ep.get("uri")));
        }
        for (Map<?, ?> route : objects(status.get("routes"))) {
            uris.add(text(route.get("from")));
        }
        for (String uri : uris) {
            String scheme = schemeOf(uri);
            if (scheme != null && HTTP_COMPONENTS.contains(scheme)) {
                schemes.add(scheme);
                if (contract == null && "rest-openapi".equals(scheme)) {
                    contract = fileName(uri.substring(scheme.length() + 1));
                }
            }
        }
        if (!schemes.isEmpty()) {
            signals.put("endpoints.http", String.join(",", schemes));
        }
        if (contract != null) {
            signals.put("contract", contract);
        }
        if (signals.isEmpty()) {
            return null;
        }
        return new Served(port, basePath(endpoints), contract, endpoints, signals);
    }

    /** The answer of get_http_endpoints: the server, the contract and the operations. */
    public static JsonObject toJson(Served served) {
        JsonObject answer = new JsonObject();
        if (served == null) {
            answer.put("endpoints", new JsonArray());
            answer.put("hint", "The integration serves nothing over HTTP (no platform-http or Rest DSL endpoints)");
            return answer;
        }
        JsonObject server = new JsonObject();
        server.put("baseUrl", served.baseUrl());
        server.put("port", served.port() > 0 ? served.port() : null);
        server.put("basePath", served.basePath());
        answer.put("server", server);
        if (served.contract() != null) {
            answer.put("contract", served.contract());
        }
        JsonArray list = new JsonArray();
        for (Endpoint e : served.endpoints()) {
            JsonObject jo = new JsonObject();
            jo.put("method", e.method());
            jo.put("path", e.path());
            putIfNotNull(jo, "consumes", e.consumes());
            putIfNotNull(jo, "produces", e.produces());
            putIfNotNull(jo, "routeId", e.routeId());
            putIfNotNull(jo, "operationId", e.operationId());
            jo.put("source", e.source());
            if (e.specification()) {
                jo.put("specification", true);
            }
            list.add(jo);
        }
        answer.put("endpoints", list);
        return answer;
    }

    /**
     * The URL a request goes to: the path on the integration's own server. An absolute URL is accepted only when it
     * points at that server (localhost and its port), so the tool cannot be used to reach anything else.
     *
     * @throws ToolExecutionException when the port is not known or the URL points elsewhere
     */
    static URI requestUri(int port, String path) {
        if (path == null || path.isBlank()) {
            throw new ToolExecutionException("path is required, e.g. /api/orders/1");
        }
        if (port <= 0) {
            throw new ToolExecutionException(
                    "The integration's HTTP port is not known (no platform-http server in its status yet)");
        }
        String p = path.trim();
        if (p.startsWith("//") || p.contains("://")) {
            URI given;
            try {
                given = URI.create(p);
            } catch (IllegalArgumentException e) {
                throw new ToolExecutionException("Not a valid URL: " + p);
            }
            String host = given.getHost();
            if (!"http".equalsIgnoreCase(given.getScheme()) || host == null
                    || !LOCAL_HOSTS.contains(host.toLowerCase(Locale.ROOT)) || given.getPort() != port) {
                throw new ToolExecutionException(
                        "Only the integration's own server can be called: pass a path such as /api/orders, it goes to "
                                                 + "http://localhost:" + port);
            }
            p = given.getRawPath() == null || given.getRawPath().isEmpty() ? "/" : given.getRawPath();
            if (given.getRawQuery() != null) {
                p += "?" + given.getRawQuery();
            }
        }
        if (!p.startsWith("/")) {
            p = "/" + p;
        }
        try {
            return URI.create("http://localhost:" + port + p.replace(" ", "%20"));
        } catch (IllegalArgumentException e) {
            throw new ToolExecutionException("Not a valid path: " + p + " (" + e.getMessage() + ")");
        }
    }

    /**
     * One client for every request: a client per call would leave its executor threads and connections behind until the
     * next GC (a model calls this dozens of times in a session), and HttpClient cannot be closed before Java 21. The
     * per-request timeout is on the request.
     */
    private static final class Client {
        static final HttpClient INSTANCE = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Sends a request to the integration's own server and returns the status, the headers and the body (at most
     * {@value #MAX_BODY_CHARS} characters, the answer says when it was cut). Redirects are not followed, the answer
     * shows the Location header instead.
     */
    static JsonObject request(int port, String method, String path, String headers, String body, int timeoutSeconds) {
        URI uri = requestUri(port, path);
        String m = method == null || method.isBlank() ? "GET" : method.trim().toUpperCase(Locale.ROOT);
        int timeout = timeoutSeconds > 0 ? Math.min(timeoutSeconds, 120) : 30;
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(timeout));
        parseHeaders(headers).forEach((k, v) -> {
            try {
                builder.header(k, v);
            } catch (IllegalArgumentException e) {
                throw new ToolExecutionException("Header " + k + " cannot be set: " + e.getMessage());
            }
        });
        builder.method(m, body == null || body.isEmpty()
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        long start = System.nanoTime();
        HttpResponse<String> response;
        try {
            response = Client.INSTANCE.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new ToolExecutionException(
                    m + " " + uri + " failed: " + e.getClass().getSimpleName()
                                             + (e.getMessage() != null ? ": " + e.getMessage() : ""));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ToolExecutionException(m + " " + uri + " was interrupted");
        }
        JsonObject answer = new JsonObject();
        answer.put("request", m + " " + uri);
        answer.put("status", response.statusCode());
        answer.put("elapsedMs", (System.nanoTime() - start) / 1_000_000);
        JsonObject responseHeaders = new JsonObject();
        response.headers().map().forEach((k, v) -> responseHeaders.put(k, String.join(", ", v)));
        answer.put("headers", responseHeaders);
        String text = response.body() != null ? response.body() : "";
        if (text.length() > MAX_BODY_CHARS) {
            answer.put("body", text.substring(0, MAX_BODY_CHARS));
            answer.put("truncated", "the body has " + text.length() + " characters, the first " + MAX_BODY_CHARS
                                    + " are shown");
        } else {
            answer.put("body", text);
        }
        return answer;
    }

    /** Headers as a JSON object, or one {@code name: value} (or {@code name=value}) per line. */
    static Map<String, String> parseHeaders(String headers) {
        Map<String, String> answer = new LinkedHashMap<>();
        if (headers == null || headers.isBlank()) {
            return answer;
        }
        String h = headers.trim();
        if (h.startsWith("{")) {
            try {
                if (Jsoner.deserialize(h) instanceof Map<?, ?> map) {
                    map.forEach((k, v) -> {
                        if (k != null && v != null) {
                            answer.put(k.toString(), v.toString());
                        }
                    });
                    return answer;
                }
            } catch (Exception e) {
                throw new ToolExecutionException("headers is not a valid JSON object: " + e.getMessage());
            }
        }
        for (String line : h.split("\\r?\\n")) {
            int idx = indexOfAny(line, ':', '=');
            if (idx > 0) {
                answer.put(line.substring(0, idx).trim(), line.substring(idx + 1).trim());
            }
        }
        return answer;
    }

    /** The rest-spec action answer with each contract cut to {@value #MAX_SPEC_CHARS} characters. */
    static Object capSpecs(String restSpec) {
        try {
            if (Jsoner.deserialize(restSpec) instanceof JsonObject jo && jo.get("specs") instanceof List<?> specs) {
                for (Object o : specs) {
                    if (o instanceof JsonObject spec && spec.get("content") instanceof String content
                            && content.length() > MAX_SPEC_CHARS) {
                        spec.put("content", content.substring(0, MAX_SPEC_CHARS));
                        spec.put("truncated", "the contract has " + content.length() + " characters, the first "
                                              + MAX_SPEC_CHARS + " are shown");
                    }
                }
                return jo;
            }
        } catch (Exception e) {
            // not JSON: return as it came
        }
        return restSpec;
    }

    /**
     * The path all operations share, by whole segments and before the first placeholder: {@code /api} for
     * {@code /api/stock} and {@code /api/stock/{id}}; empty for a single operation directly under the root.
     */
    static String basePath(List<Endpoint> endpoints) {
        List<String> common = null;
        for (Endpoint e : endpoints) {
            if (e.specification()) {
                continue;
            }
            List<String> segments = new ArrayList<>();
            for (String s : e.path().split("/")) {
                if (s.isEmpty()) {
                    continue;
                }
                if (s.contains("{")) {
                    break;
                }
                segments.add(s);
            }
            // the last segment names the operation, not the base
            if (!segments.isEmpty() && segments.size() == countSegments(e.path())) {
                segments.remove(segments.size() - 1);
            }
            if (common == null) {
                common = segments;
            } else {
                int i = 0;
                while (i < common.size() && i < segments.size() && common.get(i).equals(segments.get(i))) {
                    i++;
                }
                common = new ArrayList<>(common.subList(0, i));
            }
        }
        return common == null || common.isEmpty() ? "" : "/" + String.join("/", common);
    }

    private static int countSegments(String path) {
        int n = 0;
        for (String s : path.split("/")) {
            if (!s.isEmpty()) {
                n++;
            }
        }
        return n;
    }

    /** The port of a URL, 0 when it has none. */
    static int portOf(String url) {
        if (url == null) {
            return 0;
        }
        try {
            int port = URI.create(url.replace("{", "%7B").replace("}", "%7D")).getPort();
            return Math.max(port, 0);
        } catch (IllegalArgumentException e) {
            return 0;
        }
    }

    /** The path of a URL, or the text itself when it is a path already. */
    static String pathOf(String url) {
        if (url == null) {
            return null;
        }
        int idx = url.indexOf("://");
        if (idx < 0) {
            return url.startsWith("/") ? url : "/" + url;
        }
        int slash = url.indexOf('/', idx + 3);
        return slash >= 0 ? url.substring(slash) : "/";
    }

    /** The file name of a contract location: {@code stock-api.json} for {@code classpath:api/stock-api.json?x=y}. */
    static String fileName(String location) {
        if (location == null) {
            return null;
        }
        String s = location;
        int cut = indexOfAny(s, '?', '#');
        if (cut >= 0) {
            s = s.substring(0, cut);
        }
        int slash = Math.max(s.lastIndexOf('/'), s.lastIndexOf(':'));
        s = s.substring(slash + 1);
        return s.isBlank() ? null : s;
    }

    private static int indexOfAny(String s, char a, char b) {
        int i = s.indexOf(a);
        int j = s.indexOf(b);
        return i < 0 ? j : j < 0 ? i : Math.min(i, j);
    }

    private static String schemeOf(String uri) {
        if (uri == null) {
            return null;
        }
        int colon = uri.indexOf(':');
        return colon > 0 ? uri.substring(0, colon) : null;
    }

    private static String upper(String s) {
        return s != null ? s.toUpperCase(Locale.ROOT) : null;
    }

    private static void putIfNotNull(JsonObject jo, String key, Object value) {
        if (value != null) {
            jo.put(key, value);
        }
    }

    private static Map<?, ?> section(Map<?, ?> root, String key) {
        return root.get(key) instanceof Map<?, ?> m ? m : Map.of();
    }

    private static List<Map<?, ?>> objects(Object value) {
        List<Map<?, ?>> answer = new ArrayList<>();
        if (value instanceof List<?> l) {
            for (Object o : l) {
                if (o instanceof Map<?, ?> m) {
                    answer.add(m);
                }
            }
        }
        return answer;
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String s = value.toString();
        return s.isBlank() ? null : s;
    }
}
