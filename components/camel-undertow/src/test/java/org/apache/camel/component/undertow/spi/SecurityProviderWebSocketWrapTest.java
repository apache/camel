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
package org.apache.camel.component.undertow.spi;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

import io.undertow.util.StatusCodes;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A security provider that wraps the HTTP handlers wraps the WebSocket endpoints as well.
 */
class SecurityProviderWebSocketWrapTest extends AbstractSecurityProviderTest {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        securityConfiguration.setWrapHttpHandler(next -> exchange -> {
            if ("yes".equals(exchange.getRequestHeaders().getFirst("X-Allow"))) {
                next.handleRequest(exchange);
            } else {
                exchange.setStatusCode(StatusCodes.UNAUTHORIZED);
                exchange.endExchange();
            }
        });
        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("undertow:ws://localhost:{{port}}/wrapped?allowedRoles=user").to("mock:wrapped");
            }
        };
    }

    @Test
    void wrapperAppliesToTheUpgrade() throws Exception {
        securityConfiguration.setRoleToAssign("user");

        CompletionException thrown = assertThrows(CompletionException.class, () -> connect(null));
        WebSocketHandshakeException handshake = assertInstanceOf(WebSocketHandshakeException.class, thrown.getCause());
        assertEquals(StatusCodes.UNAUTHORIZED, handshake.getResponse().statusCode());

        getMockEndpoint("mock:wrapped").expectedBodiesReceived("hello");
        WebSocket webSocket = connect("yes");
        webSocket.sendText("hello", true).join();

        MockEndpoint.assertIsSatisfied(context);
        webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "done").join();
    }

    private WebSocket connect(String allow) {
        WebSocket.Builder builder = HttpClient.newHttpClient().newWebSocketBuilder();
        if (allow != null) {
            builder.header("X-Allow", allow);
        }
        return builder.buildAsync(URI.create("ws://localhost:" + getPort() + "/wrapped"), new WebSocket.Listener() {
        }).orTimeout(5, TimeUnit.SECONDS).join();
    }
}
