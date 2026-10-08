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

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpServer;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpEndpointsTest {

    @Test
    void theRequestGoesToTheIntegrationsOwnServer() {
        assertEquals("http://localhost:8080/api/orders?status=open",
                HttpEndpoints.requestUri(8080, "/api/orders?status=open").toString());
        assertEquals("http://localhost:8080/hello", HttpEndpoints.requestUri(8080, "hello").toString());
        // an absolute URL to the same server is fine
        assertEquals("http://localhost:8080/api/x?a=1",
                HttpEndpoints.requestUri(8080, "http://0.0.0.0:8080/api/x?a=1").toString());
        assertEquals("http://localhost:8080/api/x", HttpEndpoints.requestUri(8080, "http://127.0.0.1:8080/api/x")
                .toString());
    }

    @Test
    void otherHostsAreRefused() {
        for (String url : List.of("http://example.com/api", "https://localhost:8080/api", "http://localhost:9090/api",
                "//evil.example.com/x", "http://localhost.example.com:8080/x", "ftp://localhost:8080/x")) {
            ToolExecutionException e
                    = assertThrows(ToolExecutionException.class, () -> HttpEndpoints.requestUri(8080, url), url);
            assertTrue(e.getMessage().startsWith("Only the integration's own server"), e.getMessage());
        }
        assertTrue(assertThrows(ToolExecutionException.class, () -> HttpEndpoints.requestUri(0, "/x"))
                .getMessage().contains("port is not known"));
        assertTrue(assertThrows(ToolExecutionException.class, () -> HttpEndpoints.requestUri(8080, " "))
                .getMessage().contains("path is required"));
    }

    @Test
    void headersAreJsonOrLines() {
        assertEquals(Map.of("Accept", "application/json", "X-Id", "7"),
                HttpEndpoints.parseHeaders("{\"Accept\": \"application/json\", \"X-Id\": 7}"));
        assertEquals(Map.of("Accept", "text/plain", "X-Id", "a:b"),
                HttpEndpoints.parseHeaders("Accept: text/plain\nX-Id=a:b"));
        assertTrue(HttpEndpoints.parseHeaders(null).isEmpty());
    }

    @Test
    void theBasePathIsWhatTheOperationsShare() {
        assertEquals("/api", HttpEndpoints.basePath(List.of(op("/api/stock"), op("/api/stock/{sku}"), op("/api/x"))));
        assertEquals("/api/stock", HttpEndpoints.basePath(List.of(op("/api/stock/{sku}"))));
        assertEquals("", HttpEndpoints.basePath(List.of(op("/hello"))));
        assertEquals("", HttpEndpoints.basePath(List.of(op("/a/x"), op("/b/y"))));
        assertEquals("stock-api.json", HttpEndpoints.fileName("classpath:api/stock-api.json?x=y"));
        assertNull(HttpEndpoints.fileName(null));
    }

    @Test
    void theEndpointsAnswerListsServerContractAndOperations() throws Exception {
        String json = "{'rests': {'rests': [{'url': 'http://0.0.0.0:8080/api/hello', 'method': 'get',"
                      + " 'routeId': 'hello', 'produces': 'text/plain'}]},"
                      + " 'platform-http': {'server': 'http://0.0.0.0:8080', 'endpoints': ["
                      + "{'path': '/api/hello', 'verbs': 'GET'}, {'path': '/api/upload', 'verbs': 'POST'}]}}";
        JsonObject status = (JsonObject) Jsoner.deserialize(json.replace('\'', '"'));
        JsonObject answer = HttpEndpoints.toJson(HttpEndpoints.fromStatus(status));
        assertEquals("http://localhost:8080/api", ((JsonObject) answer.get("server")).get("baseUrl"));
        JsonArray endpoints = (JsonArray) answer.get("endpoints");
        assertEquals(2, endpoints.size(), "the Rest DSL operation is not listed twice");
        assertEquals("hello", ((JsonObject) endpoints.get(0)).get("routeId"));
        assertEquals("POST", ((JsonObject) endpoints.get(1)).get("method"));

        assertTrue(HttpEndpoints.toJson(null).get("hint").toString().contains("serves nothing over HTTP"));
    }

    @Test
    void aLongContractIsCut() {
        String spec = "{\"specs\": [{\"specificationUri\": \"a.json\", \"content\": \"" + "x".repeat(20000) + "\"}]}";
        JsonObject capped = (JsonObject) HttpEndpoints.capSpecs(spec);
        JsonObject first = (JsonObject) ((JsonArray) capped.get("specs")).get(0);
        assertEquals(HttpEndpoints.MAX_SPEC_CHARS, first.get("content").toString().length());
        assertTrue(first.get("truncated").toString().contains("20000"));
        assertEquals("not json", HttpEndpoints.capSpecs("not json"));
    }

    @Test
    void aRequestReturnsStatusHeadersAndACutBody() throws Exception {
        AtomicReference<String> seen = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            String sent = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            seen.set(exchange.getRequestMethod() + " " + exchange.getRequestURI() + " "
                     + exchange.getRequestHeaders().getFirst("X-Id") + " " + sent);
            byte[] body = (exchange.getRequestURI().getPath().endsWith("/big") ? "y".repeat(10000) : "created")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/plain");
            exchange.sendResponseHeaders(exchange.getRequestURI().getPath().endsWith("/moved") ? 302 : 201,
                    body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        try {
            int port = server.getAddress().getPort();
            JsonObject answer = HttpEndpoints.request(port, "post", "/api/orders?x=1", "X-Id: 7", "{\"a\":1}", 5);
            assertEquals(201, answer.get("status"));
            assertEquals("created", answer.get("body"));
            assertEquals("POST /api/orders?x=1 7 {\"a\":1}", seen.get());
            assertTrue(((JsonObject) answer.get("headers")).get("content-type").toString().contains("text/plain"));
            assertFalse(answer.containsKey("truncated"));

            answer = HttpEndpoints.request(port, null, "/big", null, null, 5);
            assertEquals(HttpEndpoints.MAX_BODY_CHARS, answer.get("body").toString().length());
            assertTrue(answer.get("truncated").toString().contains("10000"));

            // a redirect is reported, not followed
            assertEquals(302, HttpEndpoints.request(port, "GET", "/moved", null, null, 5).get("status"));
        } finally {
            server.stop(0);
        }
    }

    private static HttpEndpoints.Endpoint op(String path) {
        return new HttpEndpoints.Endpoint("GET", path, null, null, null, null, "rest", false);
    }
}
