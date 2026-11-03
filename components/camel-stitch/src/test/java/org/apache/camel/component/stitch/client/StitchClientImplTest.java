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
package org.apache.camel.component.stitch.client;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.Map;

import com.sun.net.httpserver.HttpServer;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.stitch.StitchConstants;
import org.apache.camel.component.stitch.client.models.StitchException;
import org.apache.camel.component.stitch.client.models.StitchMessage;
import org.apache.camel.component.stitch.client.models.StitchSchema;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Sends the batch to a local HTTP server that answers with an empty body.
 */
class StitchClientImplTest extends CamelTestSupport {

    private static HttpServer server;
    private static ConnectionProvider connectionProvider;
    private static volatile int statusCode;

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/v2/import/batch", httpExchange -> {
            try (InputStream is = httpExchange.getRequestBody()) {
                is.readAllBytes();
            }
            httpExchange.getResponseHeaders().add("Test", "empty");
            // -1 is no response body
            httpExchange.sendResponseHeaders(statusCode, -1);
            httpExchange.close();
        });
        server.start();
        connectionProvider = ConnectionProvider.create("stitch-test");
    }

    @AfterAll
    static void stopServer() {
        server.stop(0);
        connectionProvider.disposeLater().block();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getRegistry().bind("client", new StitchClientImpl(
                HttpClient.create(connectionProvider), "http://localhost:" + server.getAddress().getPort(), "dummy"));
        return context;
    }

    @Test
    void testOkResponseWithEmptyBody() {
        statusCode = 200;

        final Exchange exchange = template.send("direct:sendStitch", this::createMessage);

        assertNull(exchange.getException());
        assertEquals("", exchange.getMessage().getBody());
        assertEquals(200, exchange.getMessage().getHeader(StitchConstants.CODE));
        assertEquals("", exchange.getMessage().getHeader(StitchConstants.STATUS));
        assertEquals("empty", exchange.getMessage().getHeader(StitchConstants.HEADERS, Map.class).get("Test"));
    }

    @Test
    void testErrorResponseWithEmptyBody() {
        statusCode = 500;

        final Exchange exchange = template.send("direct:sendStitch", this::createMessage);

        final StitchException exception = assertInstanceOf(StitchException.class, exchange.getException());
        assertEquals(500, exception.getResponse().getHttpStatusCode());
    }

    private void createMessage(Exchange exchange) {
        exchange.getMessage().setHeader(StitchConstants.SCHEMA,
                StitchSchema.builder().addKeyword("field_1", "string").build());
        exchange.getMessage().setHeader(StitchConstants.KEY_NAMES, "field_1");
        exchange.getMessage().setBody(StitchMessage.builder()
                .withData("field_1", "data")
                .build());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:sendStitch")
                        .to("stitch:table_1?stitchClient=#client&token=dummy");
            }
        };
    }
}
