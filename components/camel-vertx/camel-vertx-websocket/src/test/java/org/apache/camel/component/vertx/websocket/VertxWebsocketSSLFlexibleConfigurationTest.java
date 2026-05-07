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

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.jsse.KeyManagersParameters;
import org.apache.camel.support.jsse.KeyStoreParameters;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.support.jsse.TrustManagersParameters;
import org.junit.jupiter.api.Test;

/**
 * Test flexible SSL/TLS configuration scenarios for WebSocket: - Server with only keystore (no truststore) - Client
 * with only truststore (no keystore) This validates CAMEL-23392 fix allowing optional keystore/truststore configuration
 */
class VertxWebsocketSSLFlexibleConfigurationTest extends VertxWebSocketTestSupport {

    @Test
    void testServerKeystoreOnlyClientTruststoreOnly() throws Exception {
        // Test one-way SSL: server presents certificate, client verifies it
        // No mutual authentication (client doesn't present certificate)
        CamelContext context = new DefaultCamelContext();

        // Server SSL: only keystore (presents certificate, no client auth required)
        SSLContextParameters serverSSLParameters = new SSLContextParameters();

        KeyStoreParameters keystoreParameters = new KeyStoreParameters();
        keystoreParameters.setResource("server.jks");
        keystoreParameters.setPassword("security");

        KeyManagersParameters serviceSSLKeyManagers = new KeyManagersParameters();
        serviceSSLKeyManagers.setKeyPassword("security");
        serviceSSLKeyManagers.setKeyStore(keystoreParameters);

        serverSSLParameters.setKeyManagers(serviceSSLKeyManagers);
        // Note: NO truststore configured on server

        // Client SSL: only truststore (verifies server certificate, doesn't present own)
        SSLContextParameters clientSSLParameters = new SSLContextParameters();

        KeyStoreParameters truststoreParameters = new KeyStoreParameters();
        truststoreParameters.setResource("client.jks");
        truststoreParameters.setPassword("storepass");

        TrustManagersParameters clientSSLTrustManagers = new TrustManagersParameters();
        clientSSLTrustManagers.setKeyStore(truststoreParameters);
        clientSSLParameters.setTrustManagers(clientSSLTrustManagers);
        // Note: NO keystore configured on client

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .toF("vertx-websocket:localhost:%d/echo?sslContextParameters=#clientSSLParameters", port.getPort());

                fromF("vertx-websocket:localhost:%d/echo?sslContextParameters=#serverSSLParameters", port.getPort())
                        .setBody(simple("Hello ${body}"))
                        .to("mock:result");
            }
        });

        context.getRegistry().bind("clientSSLParameters", clientSSLParameters);
        context.getRegistry().bind("serverSSLParameters", serverSSLParameters);

        context.start();
        try {
            MockEndpoint mockEndpoint = context.getEndpoint("mock:result", MockEndpoint.class);
            mockEndpoint.expectedBodiesReceived("Hello world");

            ProducerTemplate template = context.createProducerTemplate();
            template.sendBody("direct:start", "world");

            mockEndpoint.assertIsSatisfied();
        } finally {
            context.stop();
        }
    }
}
