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
import java.util.concurrent.TimeUnit;

import io.undertow.util.StatusCodes;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.common.http.WebsocketTestClient;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The security provider and the allowed roles apply to the upgrade request of WebSocket endpoints: on a path with a
 * consumer, on a path only used by producers, and on a path whose consumer is stopped.
 */
class SecurityProviderWebSocketTest extends AbstractSecurityProviderTest {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("undertow:ws://localhost:{{port}}/wssecure?allowedRoles=user")
                        .to("mock:input")
                        .transform(simple("echo:${body}"))
                        .to("undertow:ws://localhost:{{port}}/wssecure?sendToAll=true");

                from("direct:feed")
                        .to("undertow:ws://localhost:{{port}}/feed?sendToAll=true&allowedRoles=user");

                from("direct:stopped")
                        .to("undertow:ws://localhost:{{port}}/stopped?sendToAll=true");
                from("undertow:ws://localhost:{{port}}/stopped?allowedRoles=user").routeId("stopped")
                        .to("mock:stopped");
            }
        };
    }

    @Test
    void matchingRoleConnectsAndExchangesMessages() throws Exception {
        securityConfiguration.setRoleToAssign("user");
        MockEndpoint input = getMockEndpoint("mock:input");
        input.expectedBodiesReceived("ping");
        // the header added by the security provider during the upgrade
        input.expectedHeaderReceived(PRINCIPAL_PARAMETER, "user");

        WebsocketTestClient client = new WebsocketTestClient("ws://localhost:" + getPort() + "/wssecure", 1);
        client.connect();
        client.sendTextMessage("ping");

        input.assertIsSatisfied();
        assertTrue(client.await(10));
        assertEquals("echo:ping", client.getReceived(String.class).get(0));
        client.close();
    }

    @Test
    void mismatchedRoleIsRefused() {
        securityConfiguration.setRoleToAssign("admin");

        assertRefused("/wssecure");
    }

    @Test
    void missingRoleIsRefused() {
        securityConfiguration.setRoleToAssign(null);

        assertRefused("/wssecure");
    }

    @Test
    void producerOnlyPathAppliesTheProducerSettings() throws Exception {
        securityConfiguration.setRoleToAssign("admin");
        assertRefused("/feed");

        securityConfiguration.setRoleToAssign("user");
        WebsocketTestClient client = new WebsocketTestClient("ws://localhost:" + getPort() + "/feed");
        client.connect();
        // the server registers the connection shortly after the client has completed the upgrade
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            template.sendBody("direct:feed", "update");
            assertFalse(client.getReceived().isEmpty());
        });

        assertEquals("update", client.getReceived(String.class).get(0));
        client.close();
    }

    @Test
    void pathStaysGuardedWhileTheConsumerIsStopped() throws Exception {
        context.getRouteController().stopRoute("stopped");

        securityConfiguration.setRoleToAssign("admin");
        assertRefused("/stopped");

        securityConfiguration.setRoleToAssign("user");
        WebsocketTestClient client = new WebsocketTestClient("ws://localhost:" + getPort() + "/stopped");
        client.connect();
        context.getRouteController().startRoute("stopped");

        MockEndpoint stopped = getMockEndpoint("mock:stopped");
        stopped.expectedBodiesReceived("hello");
        client.sendTextMessage("hello");

        stopped.assertIsSatisfied();
        client.close();
    }

    private void assertRefused(String path) {
        WebsocketTestClient client = new WebsocketTestClient("ws://localhost:" + getPort() + path);

        CompletionException thrown = assertThrows(CompletionException.class, client::connect);
        WebSocketHandshakeException handshake = assertInstanceOf(WebSocketHandshakeException.class, thrown.getCause());
        assertEquals(StatusCodes.FORBIDDEN, handshake.getResponse().statusCode());
    }
}
