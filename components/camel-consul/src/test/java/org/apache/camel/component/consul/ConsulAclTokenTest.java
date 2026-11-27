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
package org.apache.camel.component.consul;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.camel.BindToRegistry;
import org.apache.camel.component.consul.endpoint.ConsulAgentActions;
import org.apache.camel.component.consul.endpoint.ConsulCatalogActions;
import org.apache.camel.component.consul.endpoint.ConsulHealthActions;
import org.apache.camel.component.consul.endpoint.ConsulKeyValueActions;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kiwiproject.consul.Consul;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The ACL token is sent in the X-Consul-Token header, and never as the token query parameter of the URL.
 */
public class ConsulAclTokenTest extends CamelTestSupport {

    private static final String TOKEN = "my-acl-token";
    private static final String CLIENT_TOKEN = "client-acl-token";

    private static HttpServer server;
    private static final List<RecordedRequest> REQUESTS = new CopyOnWriteArrayList<>();

    private record RecordedRequest(String method, String path, String query, String token) {
    }

    @BindToRegistry("clientWithoutToken")
    public Consul clientWithoutToken() {
        return Consul.builder().withUrl(url()).withPing(false).build();
    }

    @BindToRegistry("clientWithToken")
    public Consul clientWithToken() {
        return Consul.builder().withUrl(url()).withPing(false).withTokenAuth(CLIENT_TOKEN).build();
    }

    @BeforeAll
    public static void startServer() throws IOException {
        // port 0: the OS picks a free port
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", ConsulAclTokenTest::handle);
        server.start();
    }

    @AfterAll
    public static void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @BeforeEach
    public void clearRequests() {
        REQUESTS.clear();
    }

    private static String url() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    private static void handle(HttpExchange exchange) throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            in.readAllBytes();
        }
        REQUESTS.add(new RecordedRequest(
                exchange.getRequestMethod(), exchange.getRequestURI().getPath(), exchange.getRequestURI().getRawQuery(),
                exchange.getRequestHeaders().getFirst("X-Consul-Token")));

        String json = exchange.getRequestURI().getPath().equals("/v1/catalog/services") ? "{}" : "[]";
        byte[] body = "GET".equals(exchange.getRequestMethod()) ? json.getBytes(StandardCharsets.UTF_8) : new byte[0];
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.getResponseHeaders().add("X-Consul-Index", "1");
        exchange.getResponseHeaders().add("X-Consul-Knownleader", "true");
        exchange.getResponseHeaders().add("X-Consul-Lastcontact", "0");
        exchange.sendResponseHeaders(200, body.length == 0 ? -1 : body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private String endpoint(String api, String action) {
        return "consul:" + api + "?url=" + url() + "&pingInstance=false&aclToken=" + TOKEN + "&action=" + action;
    }

    private RecordedRequest lastRequest() {
        assertEquals(1, REQUESTS.size(), "Expected one request but got: " + REQUESTS);
        return REQUESTS.get(0);
    }

    @Test
    public void testCatalogListNodes() {
        template.sendBody(endpoint("catalog", ConsulCatalogActions.LIST_NODES), null);

        RecordedRequest request = lastRequest();
        assertEquals("/v1/catalog/nodes", request.path());
        assertNull(request.query(), "The token must not be in the URL");
        assertEquals(TOKEN, request.token());
    }

    @Test
    public void testCatalogListServicesWithDatacenter() {
        template.sendBody(endpoint("catalog", ConsulCatalogActions.LIST_SERVICES) + "&datacenter=dc1", null);

        RecordedRequest request = lastRequest();
        assertEquals("/v1/catalog/services", request.path());
        assertEquals("dc=dc1", request.query());
        assertEquals(TOKEN, request.token());
    }

    @Test
    public void testHealthServiceChecks() {
        template.sendBodyAndHeader(endpoint("health", ConsulHealthActions.SERVICE_CHECKS), null,
                ConsulConstants.CONSUL_SERVICE, "my-service");

        RecordedRequest request = lastRequest();
        assertEquals("/v1/health/checks/my-service", request.path());
        assertNull(request.query(), "The token must not be in the URL");
        assertEquals(TOKEN, request.token());
    }

    @Test
    public void testAgentDeregister() {
        template.sendBodyAndHeader(endpoint("agent", ConsulAgentActions.DEREGISTER), null,
                ConsulConstants.CONSUL_SERVICE_ID, "my-service-1");

        RecordedRequest request = lastRequest();
        assertEquals("PUT", request.method());
        assertEquals("/v1/agent/service/deregister/my-service-1", request.path());
        assertNull(request.query(), "The token must not be in the URL");
        assertEquals(TOKEN, request.token());
    }

    @Test
    public void testKeyValueGetValue() {
        template.sendBodyAndHeader(endpoint("kv", ConsulKeyValueActions.GET_VALUE), null,
                ConsulConstants.CONSUL_KEY, "my-key");

        RecordedRequest request = lastRequest();
        assertEquals("/v1/kv/my-key", request.path());
        assertNull(request.query(), "The token must not be in the URL");
        assertEquals(TOKEN, request.token());
    }

    @Test
    public void testCustomClientUsesItsOwnToken() {
        template.sendBody("consul:catalog?consulClient=#clientWithToken&aclToken=" + TOKEN + "&action="
                          + ConsulCatalogActions.LIST_NODES,
                null);

        RecordedRequest request = lastRequest();
        assertNull(request.query(), "The token must not be in the URL");
        assertEquals(CLIENT_TOKEN, request.token());
    }

    @Test
    public void testCustomClientWithoutToken() {
        template.sendBodyAndHeaders("consul:health?consulClient=#clientWithoutToken&aclToken=" + TOKEN + "&action="
                                    + ConsulHealthActions.SERVICE_CHECKS,
                null, Map.of(ConsulConstants.CONSUL_SERVICE, "my-service"));

        // the aclToken option configures the client that the endpoint creates, not a custom client
        RecordedRequest request = lastRequest();
        assertNull(request.query(), "The token must not be in the URL");
        assertNull(request.token());
    }
}
