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

import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpRequest;
import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An https:// address is called with TLS also when no sslContextParameters is configured on the endpoint.
 */
public class VertxHttpHttpsAddressTlsTest extends CamelTestSupport {

    // the first byte of a TLS record that carries a handshake message (the ClientHello)
    private static final int TLS_HANDSHAKE = 0x16;

    @Test
    public void testHttpsAddressWithPortUsesTls() throws Exception {
        assertEquals(Boolean.TRUE, prepareRequest("vertx-http:https://localhost:8443/api").ssl());
    }

    @Test
    public void testHttpsAddressWithoutPortUsesTls() throws Exception {
        HttpRequest<Buffer> request = prepareRequest("vertx-http:https://localhost/api");
        assertEquals(Boolean.TRUE, request.ssl());
        assertEquals(443, request.port());
    }

    @Test
    public void testHttpsAddressWithProxyUsesTls() throws Exception {
        assertEquals(Boolean.TRUE,
                prepareRequest("vertx-http:https://localhost:8443/api?proxyHost=localhost&proxyPort=3128").ssl());
    }

    @Test
    public void testHttpAddressDoesNotSetTls() throws Exception {
        assertNotEquals(Boolean.TRUE, prepareRequest("vertx-http:http://localhost:8080/api").ssl());
        assertNotEquals(Boolean.TRUE, prepareRequest("vertx-http:http://localhost/api").ssl());
    }

    @Test
    public void testHttpsAddressStartsWithTlsHandshake() throws Exception {
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            CompletableFuture<Integer> firstByte = CompletableFuture.supplyAsync(() -> {
                try (Socket socket = server.accept(); InputStream in = socket.getInputStream()) {
                    return in.read();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            CompletableFuture<Exchange> reply = template.asyncSend(
                    "vertx-http:https://localhost:" + server.getLocalPort() + "/api?httpMethod=POST",
                    e -> e.getMessage().setBody("Hello World"));

            int b = firstByte.get(10, TimeUnit.SECONDS);
            assertEquals(TLS_HANDSHAKE, b, "the client must start with a TLS handshake, not an HTTP request line");

            // the server closed the connection without a TLS answer, so the call fails
            Exchange out = reply.get(20, TimeUnit.SECONDS);
            assertTrue(out.isFailed());
        }
    }

    private HttpRequest<Buffer> prepareRequest(String uri) throws Exception {
        VertxHttpEndpoint endpoint = context.getEndpoint(uri, VertxHttpEndpoint.class);
        endpoint.start();
        Exchange exchange = new DefaultExchange(context);
        return new DefaultVertxHttpBinding().prepareHttpRequest(endpoint, exchange);
    }
}
