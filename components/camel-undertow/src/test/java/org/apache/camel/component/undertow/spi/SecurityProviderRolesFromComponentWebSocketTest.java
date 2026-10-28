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

import java.net.http.WebSocketHandshakeException;
import java.util.concurrent.CompletionException;

import io.undertow.util.StatusCodes;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.undertow.UndertowComponent;
import org.apache.camel.test.infra.common.http.WebsocketTestClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The allowed roles of the component apply to the WebSocket endpoints that do not configure their own.
 */
class SecurityProviderRolesFromComponentWebSocketTest extends AbstractSecurityProviderTest {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        camelContext.getComponent("undertow", UndertowComponent.class).setAllowedRoles("user");
        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("undertow:ws://localhost:{{port}}/roles").to("mock:input");
            }
        };
    }

    @Test
    void roleOfTheComponentIsAllowed() throws Exception {
        securityConfiguration.setRoleToAssign("user");
        getMockEndpoint("mock:input").expectedBodiesReceived("hello");

        WebsocketTestClient client = new WebsocketTestClient("ws://localhost:" + getPort() + "/roles");
        client.connect();
        client.sendTextMessage("hello");

        MockEndpoint.assertIsSatisfied(context);
        client.close();
    }

    @Test
    void otherRoleIsRefused() {
        securityConfiguration.setRoleToAssign("admin");

        WebsocketTestClient client = new WebsocketTestClient("ws://localhost:" + getPort() + "/roles");
        CompletionException thrown = assertThrows(CompletionException.class, client::connect);
        WebSocketHandshakeException handshake = assertInstanceOf(WebSocketHandshakeException.class, thrown.getCause());
        assertEquals(StatusCodes.FORBIDDEN, handshake.getResponse().statusCode());
    }
}
