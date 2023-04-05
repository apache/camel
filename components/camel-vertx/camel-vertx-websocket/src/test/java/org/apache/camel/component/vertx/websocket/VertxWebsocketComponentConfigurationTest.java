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

import java.net.URI;

import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.WebSocketConnectOptions;
import io.vertx.core.metrics.MetricsOptions;
import io.vertx.core.metrics.impl.DummyVertxMetrics;
import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VertxWebsocketComponentConfigurationTest {

    @Test
    public void testCustomVertx() {
        Vertx vertx = Vertx.vertx();

        CamelContext context = new DefaultCamelContext();
        VertxWebsocketComponent component = new VertxWebsocketComponent();
        component.setVertx(vertx);

        context.start();
        try {
            assertSame(vertx, component.getVertx());
        } finally {
            context.stop();
        }
    }

    @Test
    public void testCustomVertxFromRegistry() {
        Vertx vertx = Vertx.vertx();

        CamelContext context = new DefaultCamelContext();
        context.getRegistry().bind("vertx", vertx);
        context.start();
        try {
            VertxWebsocketComponent component = context.getComponent("vertx-websocket", VertxWebsocketComponent.class);
            assertSame(vertx, component.getVertx());
        } finally {
            context.stop();
        }
    }

    @Test
    public void testCustomVertxOptions() {
        MetricsOptions metrics = new MetricsOptions();
        metrics.setEnabled(true);
        metrics.setFactory(options -> DummyVertxMetrics.INSTANCE);

        VertxOptions options = new VertxOptions();
        options.setMetricsOptions(metrics);

        CamelContext context = new DefaultCamelContext();
        VertxWebsocketComponent component = new VertxWebsocketComponent();
        component.setVertxOptions(options);

        context.addComponent("vertx-websocket", component);
        context.start();
        try {
            Vertx vertx = component.getVertx();
            assertTrue(vertx.isMetricsEnabled());
        } finally {
            context.stop();
        }
    }

    @Test
    void testDefaultHost() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            String defaultHostName = "foo.bar.com";

            VertxWebsocketComponent component = new VertxWebsocketComponent();
            component.setDefaultHost(defaultHostName);
            context.addComponent("vertx-websocket", component);
            context.start();

            VertxWebsocketEndpoint endpoint = context.getEndpoint("vertx-websocket:/test", VertxWebsocketEndpoint.class);
            URI websocketURI = endpoint.getConfiguration().getWebsocketURI();
            assertEquals(defaultHostName, websocketURI.getHost());
        }
    }

    @Test
    void testDefaultPort() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            int defaultPort = 8888;

            VertxWebsocketComponent component = new VertxWebsocketComponent();
            component.setDefaultPort(defaultPort);
            context.addComponent("vertx-websocket", component);
            context.start();

            VertxWebsocketEndpoint endpoint
                    = context.getEndpoint("vertx-websocket:foo.bar.com/test", VertxWebsocketEndpoint.class);
            URI websocketURI = endpoint.getConfiguration().getWebsocketURI();
            assertEquals(defaultPort, websocketURI.getPort());
        }
    }

    @Test
    void testAllowOriginHeader() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            VertxWebsocketComponent component = new VertxWebsocketComponent();
            context.addComponent("vertx-websocket", component);
            context.start();

            VertxWebsocketEndpoint endpoint
                    = context.getEndpoint("vertx-websocket:localhost/test", VertxWebsocketEndpoint.class);
            WebSocketConnectOptions connectOptions = endpoint.getWebSocketConnectOptions(new HttpClientOptions());
            assertTrue(connectOptions.getAllowOriginHeader());
        }
    }

    @Test
    void testDisallowOriginHeader() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            VertxWebsocketComponent component = new VertxWebsocketComponent();
            component.setAllowOriginHeader(false);
            context.addComponent("vertx-websocket", component);
            context.start();

            VertxWebsocketEndpoint endpoint
                    = context.getEndpoint("vertx-websocket:localhost/test", VertxWebsocketEndpoint.class);
            WebSocketConnectOptions connectOptions = endpoint.getWebSocketConnectOptions(new HttpClientOptions());
            assertFalse(connectOptions.getAllowOriginHeader());
        }
    }

    @Test
    void testCustomOriginHeaderUrl() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            String originUrl = "https://foo.bar.com";
            VertxWebsocketComponent component = new VertxWebsocketComponent();
            component.setOriginHeaderUrl(originUrl);
            context.addComponent("vertx-websocket", component);
            context.start();

            VertxWebsocketEndpoint endpoint
                    = context.getEndpoint("vertx-websocket:localhost/test", VertxWebsocketEndpoint.class);

            WebSocketConnectOptions connectOptions = endpoint.getWebSocketConnectOptions(new HttpClientOptions());
            MultiMap headers = connectOptions.getHeaders();
            String originHeaderValue = headers.get(VertxWebsocketConstants.ORIGIN_HTTP_HEADER_NAME);
            assertNotNull(headers);
            assertEquals(originUrl, originHeaderValue);
        }
    }
}
