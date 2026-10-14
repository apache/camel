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
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import com.influxdb.client.write.Point;
import com.influxdb.exceptions.InfluxException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.influxdb2.data.Record;
import org.apache.camel.component.influxdb2.data.Records;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The producer writes with the real InfluxDB client against a local HTTP server that plays the write API of InfluxDB
 * 2.x: a write that the server rejects, or that never reaches it, must fail the exchange, and an accepted write must be
 * at the server when the exchange completes.
 */
class InfluxDb2ProducerWriteFailureTest extends CamelTestSupport {

    private static final String ORG_ID = "00000000000000a1";

    private final AtomicInteger writeStatus = new AtomicInteger(204);
    private final List<String> writes = new CopyOnWriteArrayList<>();
    private final List<String> writeQueries = new CopyOnWriteArrayList<>();

    private HttpServer server;
    private InfluxDBClient client;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/api/v2/orgs",
                exchange -> respond(exchange, 200, "{\"orgs\":[{\"id\":\"" + ORG_ID + "\",\"name\":\"myOrg\"}]}"));
        server.createContext("/api/v2/buckets",
                exchange -> respond(exchange, 200,
                        "{\"buckets\":[{\"id\":\"00000000000000b1\",\"orgID\":\"" + ORG_ID
                                                   + "\",\"name\":\"myBucket\",\"retentionRules\":[]}]}"));
        server.createContext("/api/v2/write", exchange -> {
            int status = writeStatus.get();
            String lines = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if (status == 204) {
                writeQueries.add(String.valueOf(exchange.getRequestURI().getQuery()));
                writes.add(lines);
                respond(exchange, 204, null);
            } else {
                respond(exchange, status, "{\"code\":\"rejected\",\"message\":\"write rejected with " + status + "\"}");
            }
        });
        server.start();

        client = InfluxDBClientFactory.create("http://127.0.0.1:" + server.getAddress().getPort(), "token".toCharArray());

        CamelContext context = super.createCamelContext();
        context.getRegistry().bind("influxDbBean", client);
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
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:write")
                        .to("influxdb2:influxDbBean?org=myOrg&bucket=myBucket")
                        .to("mock:after");
            }
        };
    }

    @ParameterizedTest
    @ValueSource(ints = { 400, 401, 404, 503 })
    void pointRejectedByTheServerFailsTheExchange(int status) throws Exception {
        writeStatus.set(status);
        MockEndpoint after = getMockEndpoint("mock:after");
        after.expectedMessageCount(0);

        Exchange exchange = template.send("direct:write", e -> e.getIn().setBody(point()));

        CamelInfluxDb2Exception failure
                = assertInstanceOf(CamelInfluxDb2Exception.class, exchange.getException(),
                        "exception of an exchange whose write was answered with " + status);
        InfluxException cause = assertInstanceOf(InfluxException.class, failure.getCause());
        assertEquals(status, cause.status());
        after.assertIsSatisfied();
    }

    @Test
    void recordsRejectedByTheServerFailTheExchange() throws Exception {
        writeStatus.set(400);
        MockEndpoint after = getMockEndpoint("mock:after");
        after.expectedMessageCount(0);

        Exchange exchange = template.send("direct:write",
                e -> e.getIn().setBody(Records.create("temperature,location=north value=60.0")
                        .addRecord("temperature,location=south value=61.0")));

        assertInstanceOf(CamelInfluxDb2Exception.class, exchange.getException(),
                "exception of an exchange whose records were answered with 400");
        after.assertIsSatisfied();
    }

    @Test
    void writeToServerThatIsDownFailsTheExchange() throws Exception {
        server.stop(0);
        MockEndpoint after = getMockEndpoint("mock:after");
        after.expectedMessageCount(0);

        Exchange exchange = template.send("direct:write", e -> e.getIn().setBody(point()));

        assertInstanceOf(CamelInfluxDb2Exception.class, exchange.getException(),
                "exception of an exchange whose write could not be sent");
        after.assertIsSatisfied();
    }

    @Test
    void acceptedPointIsAtTheServerWhenTheExchangeCompletes() throws Exception {
        MockEndpoint after = getMockEndpoint("mock:after");
        after.expectedMessageCount(1);

        Exchange exchange = template.send("direct:write", e -> e.getIn().setBody(point()));

        assertNull(exchange.getException());
        assertEquals(1, writes.size(), "write requests at the server when the exchange has completed");
        assertTrue(writes.get(0).startsWith("temperature,location=north value=60.0"), writes.get(0));
        after.assertIsSatisfied();
    }

    @Test
    void acceptedRecordIsWrittenWithTheOrganizationBucketAndPrecisionOfTheEndpoint() throws Exception {
        MockEndpoint after = getMockEndpoint("mock:after");
        after.expectedMessageCount(1);

        Exchange exchange = template.send("direct:write",
                e -> e.getIn().setBody(Record.fromString("temperature,location=north value=60.0")));

        assertNull(exchange.getException());
        assertEquals(List.of("temperature,location=north value=60.0"), writes,
                "write requests at the server when the exchange has completed");
        // the parameters of the write request, in any order; ms is the default writePrecision of the endpoint
        List<String> parameters = List.of(writeQueries.get(0).split("&"));
        assertTrue(parameters.contains("org=myOrg"), writeQueries.get(0));
        assertTrue(parameters.contains("bucket=myBucket"), writeQueries.get(0));
        assertTrue(parameters.contains("precision=ms"), writeQueries.get(0));
        after.assertIsSatisfied();
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
