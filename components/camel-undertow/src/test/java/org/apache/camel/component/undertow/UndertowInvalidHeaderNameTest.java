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

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A message header whose name is not a valid HTTP header name, such as the text of a form field without "=", is not
 * written to the HTTP response or request.
 */
class UndertowInvalidHeaderNameTest extends BaseUndertowTest {

    private static final String FIELD = "Save 50 percent now";

    @Test
    void testHeaderWithInvalidNameIsNotWrittenToTheResponse() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:form");
        mock.expectedMessageCount(1);

        String body = FIELD.replace(' ', '+');
        String response;
        try (Socket socket = new Socket("localhost", getPort())) {
            // fail fast instead of hanging if the connection stays open
            socket.setSoTimeout(10_000);
            OutputStream out = socket.getOutputStream();
            out.write(("POST /form HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n"
                       + "Content-Type: application/x-www-form-urlencoded\r\nContent-Length: " + body.length()
                       + "\r\n\r\n" + body)
                    .getBytes(UTF_8));
            out.flush();
            response = new String(socket.getInputStream().readAllBytes(), UTF_8);
        }

        assertTrue(response.startsWith("HTTP/1.1 200"), response);
        assertFalse(response.contains(FIELD), response);
        assertFalse(response.contains("Bad Name"), response);
        MockEndpoint.assertIsSatisfied(context);
        // the form field without "=" is still a message header, it is only not sent back as an HTTP header
        assertEquals("", mock.getExchanges().get(0).getIn().getHeader(FIELD));
    }

    @Test
    void testProducerSkipsHeaderWithInvalidName() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            CompletableFuture<String> received = CompletableFuture.supplyAsync(() -> readRequestAndReply(server));

            Exchange exchange = template.request("undertow:http://localhost:" + server.getLocalPort() + "/echo", e -> {
                e.getIn().setBody("Hello");
                e.getIn().setHeader(FIELD, "x");
                e.getIn().setHeader("X-Valid", "yes");
            });

            String request = received.get(10, TimeUnit.SECONDS);
            assertNull(exchange.getException());
            assertTrue(request.contains("X-Valid: yes"), request);
            assertFalse(request.contains(FIELD), request);
        }
    }

    private static String readRequestAndReply(ServerSocket server) {
        try (Socket socket = server.accept()) {
            socket.setSoTimeout(10_000);
            InputStream in = socket.getInputStream();
            ByteArrayOutputStream head = new ByteArrayOutputStream();
            // the request head ends with an empty line
            while (!head.toString(UTF_8).endsWith("\r\n\r\n")) {
                int b = in.read();
                if (b < 0) {
                    break;
                }
                head.write(b);
            }
            socket.getOutputStream().write(
                    "HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nOK".getBytes(UTF_8));
            socket.getOutputStream().flush();
            return head.toString(UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("undertow:http://localhost:{{port}}/form")
                        .to("mock:form")
                        .setHeader("Bad Name", constant("x"))
                        .setBody(constant("OK"));
            }
        };
    }
}
