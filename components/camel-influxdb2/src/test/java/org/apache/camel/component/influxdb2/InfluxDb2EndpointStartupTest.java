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
package org.apache.camel.component.influxdb2;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import com.influxdb.client.write.Point;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.camel.util.ObjectHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The endpoint calls InfluxDB when it starts only to create the organization or the bucket when it is missing. A local
 * HTTP server plays the organizations, buckets and write APIs of InfluxDB 2.x and records the requests it receives.
 */
class InfluxDb2EndpointStartupTest extends CamelTestSupport {

    private static final String ORG_ID = "00000000000000a1";
    private static final String BUCKET_JSON
            = "{\"id\":\"00000000000000b1\",\"orgID\":\"" + ORG_ID + "\",\"name\":\"myBucket\",\"retentionRules\":[]}";

    private final AtomicBoolean orgExists = new AtomicBoolean(true);
    private final AtomicBoolean bucketExists = new AtomicBoolean(true);
    private final List<String> requests = new CopyOnWriteArrayList<>();
    private final List<String> createdBuckets = new CopyOnWriteArrayList<>();
    private final List<String> writeQueries = new CopyOnWriteArrayList<>();

    private HttpServer server;
    private InfluxDBClient client;
    private InfluxDBClient clientOfAServerThatIsDown;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/api/v2/orgs", exchange -> {
            String method = record(exchange);
            if ("POST".equals(method)) {
                orgExists.set(true);
                respond(exchange, 201, "{\"id\":\"" + ORG_ID + "\",\"name\":\"myOrg\"}");
            } else if (orgExists.get()) {
                respond(exchange, 200, "{\"orgs\":[{\"id\":\"" + ORG_ID + "\",\"name\":\"myOrg\"}]}");
            } else {
                respond(exchange, 404, "{\"code\":\"not found\",\"message\":\"organization name not found\"}");
            }
        });
        server.createContext("/api/v2/buckets", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String method = record(exchange);
            if ("POST".equals(method)) {
                createdBuckets.add(body);
                bucketExists.set(true);
                respond(exchange, 201, BUCKET_JSON);
            } else if (!orgExists.get()) {
                respond(exchange, 404, "{\"code\":\"not found\",\"message\":\"organization name not found\"}");
            } else {
                respond(exchange, 200, "{\"buckets\":[" + (bucketExists.get() ? BUCKET_JSON : "") + "]}");
            }
        });
        server.createContext("/api/v2/write", exchange -> {
            record(exchange);
            writeQueries.add(String.valueOf(exchange.getRequestURI().getQuery()));
            respond(exchange, 204, null);
        });
        server.start();
        client = InfluxDBClientFactory.create("http://127.0.0.1:" + server.getAddress().getPort(), "token".toCharArray());

        // nothing listens on the port of a socket that has been closed
        int portOfAServerThatIsDown;
        try (ServerSocket socket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress())) {
            portOfAServerThatIsDown = socket.getLocalPort();
        }
        clientOfAServerThatIsDown
                = InfluxDBClientFactory.create("http://127.0.0.1:" + portOfAServerThatIsDown, "token".toCharArray());

        CamelContext context = super.createCamelContext();
        context.getRegistry().bind("influxDbBean", client);
        context.getRegistry().bind("influxDbThatIsDown", clientOfAServerThatIsDown);
        return context;
    }

    @Override
    protected void cleanupResources() {
        if (server != null) {
            server.stop(0);
        }
        if (client != null) {
            client.close();
        }
        if (clientOfAServerThatIsDown != null) {
            clientOfAServerThatIsDown.close();
        }
    }

    @Test
    void endpointThatCreatesNeitherDoesNotCallInfluxDbAtStartup() throws Exception {
        addRouteTo("influxdb2:influxDbBean?org=myOrg&bucket=myBucket&autoCreateOrg=false&autoCreateBucket=false");

        assertEquals(List.of(), requests, "requests when the route has started");

        Exchange exchange = template.send("direct:write", e -> e.getIn().setBody(point()));

        assertNull(exchange.getException());
        assertEquals(List.of("POST /api/v2/write"), requests, "requests when the exchange has completed");
        // the write names the organization and the bucket
        List<String> parameters = List.of(writeQueries.get(0).split("&"));
        assertTrue(parameters.contains("org=myOrg"), writeQueries.get(0));
        assertTrue(parameters.contains("bucket=myBucket"), writeQueries.get(0));
    }

    @Test
    void routeStartsWhileInfluxDbIsDownWhenTheEndpointCreatesNeither() {
        assertDoesNotThrow(() -> addRouteTo(
                "influxdb2:influxDbThatIsDown?org=myOrg&bucket=myBucket&autoCreateOrg=false&autoCreateBucket=false"));

        Exchange exchange = template.send("direct:write", e -> e.getIn().setBody(point()));

        assertInstanceOf(CamelInfluxDb2Exception.class, exchange.getException(),
                "exception of an exchange whose write could not be sent");
    }

    @Test
    void defaultsLookTheOrganizationAndTheBucketUpOnce() throws Exception {
        addRouteTo("influxdb2:influxDbBean?org=myOrg&bucket=myBucket");

        assertEquals(List.of("GET /api/v2/orgs", "GET /api/v2/buckets"), requests,
                "requests when the route has started");
    }

    @Test
    void defaultsCreateTheMissingOrganizationAndBucket() throws Exception {
        orgExists.set(false);
        bucketExists.set(false);

        addRouteTo("influxdb2:influxDbBean?org=myOrg&bucket=myBucket");

        assertEquals(List.of("GET /api/v2/orgs", "POST /api/v2/orgs", "GET /api/v2/buckets", "POST /api/v2/buckets"),
                requests, "requests when the route has started");
        assertTrue(createdBuckets.get(0).contains("\"orgID\":\"" + ORG_ID + "\""), createdBuckets.get(0));
    }

    @Test
    void autoCreateBucketAloneLooksOnlyTheBucketUp() throws Exception {
        addRouteTo("influxdb2:influxDbBean?org=myOrg&bucket=myBucket&autoCreateOrg=false");

        assertEquals(List.of("GET /api/v2/buckets"), requests, "requests when the route has started");
    }

    @Test
    void autoCreateBucketAloneCreatesTheMissingBucketInTheOrganization() throws Exception {
        bucketExists.set(false);

        addRouteTo("influxdb2:influxDbBean?org=myOrg&bucket=myBucket&autoCreateOrg=false");

        assertEquals(List.of("GET /api/v2/buckets", "GET /api/v2/orgs", "POST /api/v2/buckets"), requests,
                "requests when the route has started");
        assertTrue(createdBuckets.get(0).contains("\"orgID\":\"" + ORG_ID + "\""), createdBuckets.get(0));
    }

    @Test
    void autoCreateBucketAloneFailsToStartWhenTheOrganizationDoesNotExist() {
        orgExists.set(false);
        bucketExists.set(false);

        Exception failure = assertThrows(Exception.class,
                () -> addRouteTo("influxdb2:influxDbBean?org=myOrg&bucket=myBucket&autoCreateOrg=false"));

        CamelInfluxDb2Exception cause = ObjectHelper.getException(CamelInfluxDb2Exception.class, failure);
        assertNotNull(cause, "CamelInfluxDb2Exception in the causes of " + failure);
        assertEquals("Cannot create the bucket myBucket because the organization myOrg does not exist and autoCreateOrg is"
                     + " false",
                cause.getMessage());
        assertEquals(List.of("GET /api/v2/buckets", "GET /api/v2/orgs"), requests,
                "requests when the route has failed to start");
    }

    private void addRouteTo(String uri) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:write").to(uri);
            }
        });
    }

    private String record(HttpExchange exchange) {
        String method = exchange.getRequestMethod();
        requests.add(method + " " + exchange.getRequestURI().getPath());
        return method;
    }

    private static Point point() {
        return Point.measurement("temperature").addTag("location", "north").addField("value", 60.0);
    }

    private static void respond(HttpExchange exchange, int status, String json) throws IOException {
        // read the request completely before answering
        exchange.getRequestBody().readAllBytes();
        if (json == null) {
            exchange.sendResponseHeaders(status, -1);
        } else {
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        }
        exchange.close();
    }
}
