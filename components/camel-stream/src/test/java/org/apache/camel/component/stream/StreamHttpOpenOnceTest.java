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
package org.apache.camel.component.stream;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpServer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * CAMEL-25975: the stream:http consumer requests the URL once when it starts.
 */
public class StreamHttpOpenOnceTest extends CamelTestSupport {

    private static final AtomicInteger REQUESTS = new AtomicInteger();
    private static HttpServer server;

    @BeforeAll
    public static void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/data.txt", exchange -> {
            REQUESTS.incrementAndGet();
            byte[] data = "Hello\nWorld\n".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, data.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(data);
            }
        });
        server.start();
    }

    @AfterAll
    public static void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @BeforeEach
    public void resetRequests() {
        REQUESTS.set(0);
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testLineModeRequestsOnce() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello", "World");

        startRoute("");

        MockEndpoint.assertIsSatisfied(context);
        assertEquals(1, REQUESTS.get());
    }

    @Test
    public void testRawModeRequestsOnce() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        startRoute("&readLine=false");

        MockEndpoint.assertIsSatisfied(context);
        assertEquals("Hello\nWorld\n", mock.getExchanges().get(0).getIn().getBody(String.class));
        assertEquals(1, REQUESTS.get());
    }

    private void startRoute(String options) throws Exception {
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/data.txt";
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("stream:http?httpUrl=" + url + options).to("mock:result");
            }
        });
        context.start();
    }
}
