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
package org.apache.camel.component.vertx.http;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import com.sun.net.httpserver.HttpServer;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The CamelHttpPath header is appended to the path of the endpoint URI, also when the endpoint URI has a query: the
 * path goes before the query, as with camel-http.
 */
class VertxHttpPathHeaderWithEndpointQueryTest extends CamelTestSupport {

    @RegisterExtension
    AvailablePortFinder.Port proxyPort = AvailablePortFinder.find();

    private final List<String> requests = new CopyOnWriteArrayList<>();
    private HttpServer server;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", ex -> {
            requests.add(
                    ex.getRequestMethod() + " " + ex.getRequestURI().getRawPath() + " ? " + ex.getRequestURI().getRawQuery());
            ex.getRequestBody().readAllBytes();
            byte[] ok = "ok".getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, ok.length);
            ex.getResponseBody().write(ok);
            ex.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private String endpoint(String pathAndQuery) {
        return "vertx-http:http://localhost:" + server.getAddress().getPort() + pathAndQuery;
    }

    @Test
    void pathHeaderWithEndpointQuery() {
        template.sendBodyAndHeader(endpoint("/orders?apiVersion=2"), null, Exchange.HTTP_PATH, "/123");

        assertEquals(List.of("GET /orders/123 ? apiVersion=2"), requests);
    }

    @Test
    void pathHeaderWithEndpointQueryAndQueryHeader() {
        template.sendBodyAndHeaders(endpoint("/orders?apiVersion=2"), null,
                Map.of(Exchange.HTTP_PATH, "/123", Exchange.HTTP_QUERY, "expand=items"));

        assertEquals(List.of("GET /orders/123 ? expand=items"), requests);
    }

    @Test
    void pathHeaderWithEndpointQueryPost() {
        template.sendBodyAndHeaders(endpoint("/orders?apiVersion=2"), "{\"qty\":1}",
                Map.of(Exchange.HTTP_PATH, "/123/lines", Exchange.HTTP_METHOD, "POST"));

        assertEquals(List.of("POST /orders/123/lines ? apiVersion=2"), requests);
    }

    @Test
    void pathHeaderWithEndpointQueryAndTrailingSlash() {
        template.sendBodyAndHeader(endpoint("/orders/?apiVersion=2"), null, Exchange.HTTP_PATH, "123");

        assertEquals(List.of("GET /orders/123 ? apiVersion=2"), requests);
    }

    @Test
    void pathHeaderWithEndpointQueryAndNoEndpointPath() {
        template.sendBodyAndHeader(endpoint("?apiVersion=2"), null, Exchange.HTTP_PATH, "/123");

        assertEquals(List.of("GET /123 ? apiVersion=2"), requests);
    }

    @Test
    void encodedPathHeaderWithEndpointQuery() {
        template.sendBodyAndHeader(endpoint("/orders?apiVersion=2"), null, Exchange.HTTP_PATH, "/a%20b");

        assertEquals(List.of("GET /orders/a%20b ? apiVersion=2"), requests);
    }

    @Test
    void proxyRouteWithEndpointQuerySendsTheRemainingPath() throws Exception {
        // the undertow consumer sets CamelHttpPath to the rest of the request path, so the backend gets it after the
        // endpoint path; removing the header keeps the endpoint path alone. The consumer also sets CamelHttpQuery from
        // the request, which wins over the query of the endpoint, as before
        String proxy = "undertow:http://localhost:" + proxyPort.getPort();
        String backend = endpoint("/svc?key=1&bridgeEndpoint=true");
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(proxy + "/api?matchOnUriPrefix=true")
                        .to(backend);
                from(proxy + "/old?matchOnUriPrefix=true")
                        .removeHeader(Exchange.HTTP_PATH)
                        .to(backend);
            }
        });

        template.requestBody(proxy + "/api/orders/123?expand=items", null, String.class);
        template.requestBody(proxy + "/old/orders/123?expand=items", null, String.class);

        assertEquals(List.of("GET /svc/orders/123 ? expand=items", "GET /svc ? expand=items"), requests);
    }

    @Test
    void pathHeaderWithoutEndpointQuery() {
        // control: unchanged without a query in the endpoint uri
        template.sendBodyAndHeader(endpoint("/orders"), null, Exchange.HTTP_PATH, "/123");
        template.sendBodyAndHeaders(endpoint("/orders"), null,
                Map.of(Exchange.HTTP_PATH, "/123", Exchange.HTTP_QUERY, "expand=items"));

        assertEquals(List.of("GET /orders/123 ? null", "GET /orders/123 ? expand=items"), requests);
    }
}
