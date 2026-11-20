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
package org.apache.camel.component.netty.http;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A text sent as form data, mapped to a header whose name is not a valid HTTP header name, does not fail a route that
 * forwards the request with the netty-http producer, and a text with a % that is not followed by two hex digits reaches
 * the route.
 */
public class NettyHttpForwardFormTextTest extends BaseNettyTestSupport {

    private static final String TEXT = "Hi, I'm alice@example.com. My order is late, can you refund it?";

    private final AtomicInteger frontRuns = new AtomicInteger();
    private final AtomicInteger backRuns = new AtomicInteger();

    @Test
    public void testFormTextIsForwarded() throws Exception {
        HttpResponse<String> response = post(TEXT);

        assertEquals(200, response.statusCode());
        assertEquals("back ok", response.body());
        assertEquals(1, frontRuns.get());
        assertEquals(1, backRuns.get());
    }

    @Test
    public void testFormTextWithPercentIsForwarded() throws Exception {
        assertEquals(200, post("Save 50% now").statusCode());
        assertEquals(200, post("{\"discount\":\"50%\"}").statusCode());
        assertEquals(2, backRuns.get());
    }

    private HttpResponse<String> post(String body) throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + getPort() + "/front"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("netty-http:http://localhost:{{port}}/front?urlDecodeHeaders=true")
                        .process(e -> frontRuns.incrementAndGet())
                        .to("netty-http:http://localhost:{{port}}/back");

                from("netty-http:http://localhost:{{port}}/back")
                        .process(e -> backRuns.incrementAndGet())
                        .setBody(constant("back ok"));
            }
        };
    }
}
