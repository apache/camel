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
package org.apache.camel.component.vertx.websocket;

import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.WebSocketConnectOptions;
import org.apache.camel.BindToRegistry;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.AvailablePortFinder;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.vertx.websocket.VertxWebsocketConstants.ORIGIN_HTTP_HEADER_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VertxWebsocketEndpointConfigurationTest extends VertxWebSocketTestSupport {

    private static final int PORT = AvailablePortFinder.getNextAvailable();

    @BindToRegistry("clientOptions")
    HttpClientOptions clientOptions = new HttpClientOptions();

    @BindToRegistry("serverOptions")
    HttpServerOptions serverOptions = new HttpServerOptions();

    @Test
    public void testHttpClientOptions() {
        VertxWebsocketEndpoint endpoint = context
                .getEndpoint("vertx-websocket:localhost:" + PORT + "/options/client?clientOptions=#clientOptions",
                        VertxWebsocketEndpoint.class);

        assertSame(clientOptions, endpoint.getConfiguration().getClientOptions());
    }

    @Test
    public void testHttpServerOptions() {
        VertxWebsocketEndpoint endpoint = context
                .getEndpoint("vertx-websocket:localhost:" + PORT + "/options/server?serverOptions=#serverOptions",
                        VertxWebsocketEndpoint.class);

        assertSame(serverOptions, endpoint.getConfiguration().getServerOptions());
    }

    @Test
    public void testDefaultHostConfiguration() throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:defaultConfiguration");
        mockEndpoint.expectedMessageCount(1);

        template.sendBody("vertx-websocket:localhost:" + getVertxServerRandomPort() + "/default/configuration", "Hello world");

        mockEndpoint.assertIsSatisfied();
    }

    @Test
    void testAllowOriginHeader() {
        VertxWebsocketEndpoint endpoint = context.getEndpoint("vertx-websocket:localhost/test", VertxWebsocketEndpoint.class);
        WebSocketConnectOptions connectOptions = endpoint.getWebSocketConnectOptions(new HttpClientOptions());
        assertTrue(connectOptions.getAllowOriginHeader());
    }

    @Test
    void testDisallowOriginHeader() {
        VertxWebsocketEndpoint endpoint
                = context.getEndpoint("vertx-websocket:localhost/test?allowOriginHeader=false", VertxWebsocketEndpoint.class);
        WebSocketConnectOptions connectOptions = endpoint.getWebSocketConnectOptions(new HttpClientOptions());
        assertFalse(connectOptions.getAllowOriginHeader());
    }

    @Test
    void testCustomOriginHeaderUrl() {
        String originUrl = "https://foo.bar.com";

        VertxWebsocketEndpoint endpoint = context.getEndpoint("vertx-websocket:localhost/test?originHeaderUrl=" + originUrl,
                VertxWebsocketEndpoint.class);

        WebSocketConnectOptions connectOptions = endpoint.getWebSocketConnectOptions(new HttpClientOptions());
        MultiMap headers = connectOptions.getHeaders();
        String originHeaderValue = headers.get(ORIGIN_HTTP_HEADER_NAME);
        assertNotNull(headers);
        assertEquals(originUrl, originHeaderValue);
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("vertx-websocket:///default/configuration")
                        .to("mock:defaultConfiguration");
            }
        };
    }
}
