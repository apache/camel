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
package org.apache.camel.component.spring.security;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import io.undertow.util.StatusCodes;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The Spring Security provider applies to the upgrade request of WebSocket endpoints.
 */
class SpringSecurityWebSocketTest extends AbstractSpringSecurityBearerTokenTest {

    @Test
    void allowedRoleConnects() throws Exception {
        getMockFilter().setJwt(createToken("Alice", "user"));

        CompletableFuture<String> reply = new CompletableFuture<>();
        WebSocket webSocket = connect(new WebSocket.Listener() {
            @Override
            public void onOpen(WebSocket webSocket) {
                webSocket.request(1);
            }

            @Override
            public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                reply.complete(data.toString());
                return null;
            }
        });
        webSocket.sendText("hi", true).join();

        assertEquals("Hello Alice!", reply.get(10, TimeUnit.SECONDS));
        webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "done").join();
    }

    @Test
    void otherRoleIsRefused() {
        getMockFilter().setJwt(createToken("Tom", "wrongUser"));

        CompletionException thrown = assertThrows(CompletionException.class, () -> connect(new WebSocket.Listener() {
        }));
        WebSocketHandshakeException handshake = assertInstanceOf(WebSocketHandshakeException.class, thrown.getCause());
        assertEquals(StatusCodes.FORBIDDEN, handshake.getResponse().statusCode());
    }

    private WebSocket connect(WebSocket.Listener listener) {
        return HttpClient.newHttpClient().newWebSocketBuilder()
                .buildAsync(URI.create("ws://localhost:" + getPort() + "/myws"), listener)
                .orTimeout(5, TimeUnit.SECONDS).join();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("undertow:ws://localhost:{{port}}/myws?allowedRoles=user")
                        .transform(simple("Hello ${in.header." + SpringSecurityProvider.PRINCIPAL_NAME_HEADER + "}!"))
                        .to("undertow:ws://localhost:{{port}}/myws");
            }
        };
    }
}
