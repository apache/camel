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
package org.apache.camel.component.undertow;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A number is sent as its text, a Map, a List or an object fails clearly rather than be sent as an empty body, a body
 * is read for any method, and a 204 response has no body.
 */
public class UndertowBodyConversionTest extends BaseUndertowTest {

    @Test
    public void testReplyNumberIsText() throws Exception {
        HttpResponse<String> response = send(HttpRequest.newBuilder(uri("number")).GET().build());

        assertEquals(200, response.statusCode());
        assertEquals("42", response.body());
    }

    @Test
    public void testReplyBooleanIsText() throws Exception {
        HttpResponse<String> response = send(HttpRequest.newBuilder(uri("boolean")).GET().build());

        assertEquals(200, response.statusCode());
        assertEquals("true", response.body());
    }

    @Test
    public void testReplyMapIs500() throws Exception {
        HttpResponse<String> response = send(HttpRequest.newBuilder(uri("map")).GET().build());

        assertEquals(500, response.statusCode());
        assertEquals("", response.body());
    }

    @Test
    public void testReplyListIs500() throws Exception {
        HttpResponse<String> response = send(HttpRequest.newBuilder(uri("list")).GET().build());

        assertEquals(500, response.statusCode());
    }

    @Test
    public void testReplyObjectIs500() throws Exception {
        HttpResponse<String> response = send(HttpRequest.newBuilder(uri("object")).GET().build());

        assertEquals(500, response.statusCode());
    }

    @Test
    public void testProducerSendsNumberAsText() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:received");
        mock.expectedBodiesReceived("42");

        template.sendBody("undertow:http://localhost:{{port}}/received", 42);

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testProducerFailsOnMap() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:received");
        mock.expectedMessageCount(0);

        Exchange out = template.send("undertow:http://localhost:{{port}}/received",
                exchange -> exchange.getIn().setBody(Map.of("id", 1)));

        InvalidPayloadException cause = assertInstanceOf(InvalidPayloadException.class, out.getException());
        assertTrue(cause.getMessage().contains("which is not an HTTP request body: marshal it first"), cause.getMessage());
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testProducerFailsOnMapWithJsonContentType() {
        Exchange out = template.send("undertow:http://localhost:{{port}}/received", exchange -> {
            exchange.getIn().setBody(List.of("a", "b"));
            exchange.getIn().setHeader(Exchange.CONTENT_TYPE, "application/json");
        });

        InvalidPayloadException cause = assertInstanceOf(InvalidPayloadException.class, out.getException());
        assertTrue(cause.getMessage().contains("marshal it first (marshal: json"), cause.getMessage());
    }

    @Test
    public void testProducerGetIgnoresMap() throws Exception {
        // a GET sends no body it cannot convert, as before
        Exchange out = template.send("undertow:http://localhost:{{port}}/number", exchange -> {
            exchange.getIn().setBody(Map.of("id", 1));
            exchange.getIn().setHeader(Exchange.HTTP_METHOD, "GET");
        });

        assertNull(out.getException());
        assertEquals("42", out.getMessage().getBody(String.class));
    }

    @Test
    public void testDeleteWithBody() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:received");
        mock.expectedBodiesReceived("remove me");

        HttpResponse<String> response = send(HttpRequest.newBuilder(uri("received"))
                .method("DELETE", HttpRequest.BodyPublishers.ofString("remove me", UTF_8)).build());

        assertEquals(200, response.statusCode());
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testDeleteWithoutBody() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:received");
        mock.expectedMessageCount(1);

        HttpResponse<String> response = send(HttpRequest.newBuilder(uri("received")).DELETE().build());

        assertEquals(200, response.statusCode());
        MockEndpoint.assertIsSatisfied(context);
        assertNull(mock.getExchanges().get(0).getIn().getBody());
    }

    @Test
    public void testProducerNoContentHasNullBody() {
        Exchange out = template.request("undertow:http://localhost:{{port}}/noContent", null);

        assertNull(out.getException());
        assertEquals(204, out.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertNull(out.getMessage().getBody());
    }

    @Test
    public void testProducerHeadHasNullBody() {
        Exchange out = template.request("undertow:http://localhost:{{port}}/number",
                exchange -> exchange.getIn().setHeader(Exchange.HTTP_METHOD, "HEAD"));

        assertNull(out.getException());
        assertEquals(200, out.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertNull(out.getMessage().getBody());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + getPort() + "/" + path);
    }

    private static HttpResponse<String> send(HttpRequest request) throws Exception {
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    public static class Order {
        private final int id = 1;

        public int getId() {
            return id;
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("undertow:http://localhost:{{port}}/number")
                        .setBody(constant(42));

                from("undertow:http://localhost:{{port}}/boolean")
                        .setBody(constant(true));

                from("undertow:http://localhost:{{port}}/map")
                        .setBody(constant(Map.of("id", 1)));

                from("undertow:http://localhost:{{port}}/list")
                        .setBody(constant(List.of("a", "b")));

                from("undertow:http://localhost:{{port}}/object")
                        .setBody(constant(new Order()));

                from("undertow:http://localhost:{{port}}/received")
                        .convertBodyTo(String.class)
                        .to("mock:received")
                        .setBody(constant("OK"));

                from("undertow:http://localhost:{{port}}/noContent")
                        .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(204))
                        .setBody(constant(""));
            }
        };
    }
}
