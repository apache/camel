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

import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.undertow.BaseUndertowTest;
import org.apache.camel.test.infra.common.http.WebsocketTestClient;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A security provider configured on a WebSocket endpoint that requires the servlet context gets it, whichever endpoint
 * registers first on the port.
 */
class ProviderWithServletWebSocketEndpointTest extends BaseUndertowTest {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getRegistry().bind("servletProvider", new ProviderWithServletTest.MockSecurityProvider());
        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // a path only used by a producer, which configures the provider
                from("direct:feed")
                        .to("undertow:ws://localhost:{{port}}/feed?securityProvider=#servletProvider&sendToAll=true");

                // the producer of the route registers first on the port, without a provider
                from("undertow:ws://localhost:{{port2}}/chat?securityProvider=#servletProvider")
                        .to("mock:chat")
                        .transform(simple("${in.header." + AbstractSecurityProviderTest.PRINCIPAL_PARAMETER + "}"))
                        .to("undertow:ws://localhost:{{port2}}/chat");
            }
        };
    }

    @Test
    void producerOnlyPathWithItsOwnProvider() {
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
    void consumerWithItsOwnProviderOnAPortWhereAProducerRegisteredFirst() throws Exception {
        getMockEndpoint("mock:chat").expectedBodiesReceived("hello");

        WebsocketTestClient client = new WebsocketTestClient("ws://localhost:" + getPort2() + "/chat", 1);
        client.connect();
        client.sendTextMessage("hello");

        MockEndpoint.assertIsSatisfied(context);
        assertTrue(client.await(10));
        assertEquals("user", client.getReceived(String.class).get(0));
        client.close();
    }
}
