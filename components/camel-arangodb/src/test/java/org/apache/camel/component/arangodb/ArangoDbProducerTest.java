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
package org.apache.camel.component.arangodb;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import com.arangodb.ArangoDB;
import com.arangodb.Protocol;
import com.arangodb.entity.DocumentCreateEntity;
import com.arangodb.entity.MultiDocumentEntity;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.Registry;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The producer against a fake of the document API of ArangoDB, which, like the server, inserts a JSON array as several
 * documents and replies with an array.
 */
class ArangoDbProducerTest extends CamelTestSupport {

    private static final String DOCUMENT_API = "/_db/testDb/_api/document/coll";

    private final List<String> requests = new CopyOnWriteArrayList<>();
    private HttpServer server;

    @Override
    protected void setupResources() throws Exception {
        super.setupResources();
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", this::reply);
        server.start();
    }

    @Override
    protected void cleanupResources() throws Exception {
        if (server != null) {
            server.stop(0);
        }
        super.cleanupResources();
    }

    @Override
    protected void bindToRegistry(Registry registry) {
        registry.bind("arangoDB", newClient());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:save").to("arangodb:testDb?documentCollection=coll&operation=SAVE_DOCUMENT");
            }
        };
    }

    @Test
    void saveDocumentOfAListInsertsTheDocumentsOnce() {
        List<Map<String, Object>> documents = List.of(Map.of("name", "foo"), Map.of("name", "bar"));

        Exchange result = template.request("direct:save", exchange -> exchange.getMessage().setBody(documents));

        // the server inserted the documents, so the exchange must not fail, or a retry would insert them again
        assertNull(result.getException(), () -> "the exchange failed with " + result.getException());
        MultiDocumentEntity<?> created = assertInstanceOf(MultiDocumentEntity.class, result.getMessage().getBody());
        assertEquals(2, created.getDocuments().size());
        assertEquals(1, requests.size(), requests::toString);
        assertTrue(requests.get(0).startsWith("POST " + DOCUMENT_API + " ["), requests.get(0));
    }

    @Test
    void saveDocumentOfOneDocumentInsertsOne() {
        Exchange result = template.request("direct:save", exchange -> exchange.getMessage().setBody(Map.of("name", "foo")));

        assertNull(result.getException(), () -> "the exchange failed with " + result.getException());
        assertInstanceOf(DocumentCreateEntity.class, result.getMessage().getBody());
        assertEquals(1, requests.size(), requests::toString);
        assertTrue(requests.get(0).startsWith("POST " + DOCUMENT_API + " {"), requests.get(0));
    }

    @Test
    void routeWithoutOperationFailsToStart() throws Exception {
        try (CamelContext camel = new DefaultCamelContext()) {
            camel.getRegistry().bind("arangoDB", newClient());
            camel.addRoutes(saveRoute());

            Exception error = assertThrows(Exception.class, camel::start);

            Throwable cause = error;
            while (cause.getCause() != null) {
                cause = cause.getCause();
            }
            assertInstanceOf(IllegalArgumentException.class, cause, () -> "the context failed with " + error);
            assertTrue(cause.getMessage().contains("The operation option must be set"), cause.getMessage());
        }
        assertTrue(requests.isEmpty(), requests::toString);
    }

    @Test
    void operationOfTheComponentConfigurationIsEnough() throws Exception {
        try (CamelContext camel = new DefaultCamelContext()) {
            camel.getRegistry().bind("arangoDB", newClient());
            camel.getComponent("arangodb", ArangoDbComponent.class).getConfiguration()
                    .setOperation(ArangoDbOperation.SAVE_DOCUMENT);
            camel.addRoutes(saveRoute());
            camel.start();

            try (ProducerTemplate producer = camel.createProducerTemplate()) {
                Object body = producer.requestBody("direct:save", Map.of("name", "foo"));

                assertInstanceOf(DocumentCreateEntity.class, body);
            }
        }
    }

    @Test
    void sharedClientKeepsWorkingWhenAnotherEndpointShutsDown() {
        // the autowired client of the component is shared by every endpoint
        ArangoDbEndpoint other = context.getEndpoint(
                "arangodb:testDb?documentCollection=other&operation=SAVE_DOCUMENT", ArangoDbEndpoint.class);
        other.start();
        // as for the endpoint of a removed route, or one evicted from the endpoint cache of a toD
        other.stop();
        other.shutdown();

        Exchange result = template.request("direct:save", exchange -> exchange.getMessage().setBody(Map.of("name", "foo")));

        // the shut down endpoint closed the shared client, so this send failed
        assertNull(result.getException(), () -> "the exchange failed with " + result.getException());
        assertEquals(1, requests.size(), requests::toString);
    }

    @Test
    void endpointShutsDownTheClientItBuilt() throws Exception {
        try (CamelContext camel = new DefaultCamelContext()) {
            camel.start();
            ArangoDbEndpoint endpoint = camel.getEndpoint(
                    "arangodb:testDb?documentCollection=coll&operation=SAVE_DOCUMENT&host=localhost&port="
                                                          + server.getAddress().getPort(),
                    ArangoDbEndpoint.class);
            endpoint.start();
            assertNotNull(endpoint.getArangoDB());

            endpoint.stop();
            endpoint.shutdown();

            assertNull(endpoint.getArangoDB());
        }
    }

    @Test
    void hostWithoutPortFailsToStart() throws Exception {
        // the port was 0, so the endpoint started and failed on the first message
        assertStartFailsNamingTheOptions("arangodb:testDb?documentCollection=coll&operation=SAVE_DOCUMENT&host=localhost");
    }

    @Test
    void noHostNorClientFailsToStart() throws Exception {
        // the driver failed with "No host has been set!", which names no option
        assertStartFailsNamingTheOptions("arangodb:testDb?documentCollection=coll&operation=SAVE_DOCUMENT");
    }

    private static void assertStartFailsNamingTheOptions(String uri) throws Exception {
        try (CamelContext camel = new DefaultCamelContext()) {
            camel.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:save").to(uri);
                }
            });

            Exception error = assertThrows(Exception.class, camel::start);

            Throwable cause = error;
            while (cause.getCause() != null) {
                cause = cause.getCause();
            }
            assertInstanceOf(IllegalArgumentException.class, cause, () -> "the context failed with " + error);
            assertTrue(cause.getMessage().startsWith("The host and port options must be set"), cause.getMessage());
        }
    }

    @Test
    void serviceUrlIsNullWithoutHost() {
        ArangoDbEndpoint withClient = context.getEndpoint(
                "arangodb:testDb?documentCollection=coll&operation=SAVE_DOCUMENT", ArangoDbEndpoint.class);
        ArangoDbEndpoint withHost = context.getEndpoint(
                "arangodb:testDb?documentCollection=coll&operation=SAVE_DOCUMENT&host=db.example.com&port=8529",
                ArangoDbEndpoint.class);

        // was null:0
        assertNull(withClient.getServiceUrl());
        assertEquals("db.example.com:8529", withHost.getServiceUrl());
    }

    /**
     * A route that sends to an arangodb endpoint without the operation option.
     */
    private static RouteBuilder saveRoute() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:save").to("arangodb:testDb?documentCollection=coll");
            }
        };
    }

    private ArangoDB newClient() {
        // HTTP/1.1, which the JDK HTTP server speaks
        return new ArangoDB.Builder()
                .host("localhost", server.getAddress().getPort())
                .protocol(Protocol.HTTP_JSON)
                .build();
    }

    private void reply(HttpExchange http) throws IOException {
        String body = new String(http.getRequestBody().readAllBytes(), StandardCharsets.UTF_8).trim();
        String path = http.getRequestURI().getPath();
        requests.add(http.getRequestMethod() + " " + path + " " + body);

        int status;
        String reply;
        if ("POST".equals(http.getRequestMethod()) && DOCUMENT_API.equals(path)) {
            status = 202;
            // the tests send one document, or an array of two
            reply = body.startsWith("[") ? "[" + created(1) + "," + created(2) + "]" : created(1);
        } else {
            status = 404;
            reply = "{\"error\":true,\"code\":404,\"errorNum\":1203,\"errorMessage\":\"unknown path " + path + "\"}";
        }
        byte[] bytes = reply.getBytes(StandardCharsets.UTF_8);
        http.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        http.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = http.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static String created(int key) {
        return "{\"_id\":\"coll/" + key + "\",\"_key\":\"" + key + "\",\"_rev\":\"_r" + key + "\"}";
    }
}
