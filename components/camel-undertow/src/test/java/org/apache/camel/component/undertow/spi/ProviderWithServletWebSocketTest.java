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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.common.http.WebsocketTestClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A security provider that requires the servlet context gets it for WebSocket upgrade requests too, also when the first
 * endpoint registered on the port is a WebSocket producer.
 */
class ProviderWithServletWebSocketTest extends AbstractProviderServletTest {

    @BeforeAll
    static void initProvider() throws Exception {
        createSecurtyProviderConfigurationFile(ProviderWithServletTest.MockSecurityProvider.class);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // the producer of the route is started, and registered on the port, before its consumer
                from("undertow:ws://localhost:{{port}}/foo?allowedRoles=user")
                        .to("mock:input")
                        .transform(simple("${in.header." + AbstractSecurityProviderTest.PRINCIPAL_PARAMETER + "}"))
                        .to("undertow:ws://localhost:{{port}}/foo");
            }
        };
    }

    @Test
    void upgradeRequestHasTheServletContext() throws Exception {
        getMockEndpoint("mock:input").expectedBodiesReceived("hello");

        WebsocketTestClient client = new WebsocketTestClient("ws://localhost:" + getPort() + "/foo", 1);
        client.connect();
        client.sendTextMessage("hello");

        MockEndpoint.assertIsSatisfied(context);
        assertTrue(client.await(10));
        assertEquals("user", client.getReceived(String.class).get(0));
        client.close();
    }
}
