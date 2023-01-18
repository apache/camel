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
import org.apache.camel.support.jsse.SSLContextServerParameters;
import org.apache.camel.support.jsse.TrustManagersParameters;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class VertxWebsocketSSLTest extends VertxWebSocketTestSupport {

    private SSLContextParameters serverSSLParameters;
    private SSLContextParameters clientSSLParameters;

    @BeforeEach
    public void setupSecurity() {
        serverSSLParameters = new SSLContextParameters();
        clientSSLParameters = new SSLContextParameters();

        KeyStoreParameters keystoreParameters = new KeyStoreParameters();
        keystoreParameters.setResource("server.jks");
        keystoreParameters.setPassword("security");

        KeyManagersParameters serviceSSLKeyManagers = new KeyManagersParameters();
        serviceSSLKeyManagers.setKeyPassword("security");
        serviceSSLKeyManagers.setKeyStore(keystoreParameters);

        serverSSLParameters.setKeyManagers(serviceSSLKeyManagers);

        KeyStoreParameters truststoreParameters = new KeyStoreParameters();
        truststoreParameters.setResource("client.jks");
        truststoreParameters.setPassword("storepass");

        TrustManagersParameters clientAuthServiceSSLTrustManagers = new TrustManagersParameters();
        clientAuthServiceSSLTrustManagers.setKeyStore(truststoreParameters);
        serverSSLParameters.setTrustManagers(clientAuthServiceSSLTrustManagers);
        SSLContextServerParameters clientAuthSSLContextServerParameters = new SSLContextServerParameters();
        clientAuthSSLContextServerParameters.setClientAuthentication("REQUIRE");
        serverSSLParameters.setServerParameters(clientAuthSSLContextServerParameters);

        TrustManagersParameters clientSSLTrustManagers = new TrustManagersParameters();
        clientSSLTrustManagers.setKeyStore(truststoreParameters);
        clientSSLParameters.setTrustManagers(clientSSLTrustManagers);

        KeyManagersParameters clientAuthClientSSLKeyManagers = new KeyManagersParameters();
        clientAuthClientSSLKeyManagers.setKeyPassword("security");
        clientAuthClientSSLKeyManagers.setKeyStore(keystoreParameters);
        clientSSLParameters.setKeyManagers(clientAuthClientSSLKeyManagers);
    }

    @Test
    public void testSSLContextParametersFromRegistry() throws Exception {
        CamelContext context = new DefaultCamelContext();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .toF("vertx-websocket:localhost:%d/echo?sslContextParameters=#clientSSLParameters", port);

                fromF("vertx-websocket:localhost:%d/echo?sslContextParameters=#serverSSLParameters", port)
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

    @Test
    public void testGlobalServerSSLContextParameters() throws Exception {
        CamelContext context = new DefaultCamelContext();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .toF("vertx-websocket:localhost:%d/echo?sslContextParameters=#clientSSLParameters", port);

                fromF("vertx-websocket:localhost:%d/echo", port)
                        .setBody(simple("Hello ${body}"))
                        .to("mock:result");
            }
        });

        VertxWebsocketComponent component = new VertxWebsocketComponent();
        component.setUseGlobalSslContextParameters(true);
        context.setSSLContextParameters(serverSSLParameters);
        context.addComponent("vertx-websocket", component);
        context.getRegistry().bind("clientSSLParameters", clientSSLParameters);

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

    @Test
    void testWssSchemeUriPrefix() throws Exception {
        CamelContext context = new DefaultCamelContext();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                fromF("vertx-websocket:localhost:%d/test?sslContextParameters=#serverSSLParameters", port)
                        .setBody(simple("Hello ${body}"))
                        .to("mock:result");
            }
        });

        context.getRegistry().bind("clientSSLParameters", clientSSLParameters);
        context.getRegistry().bind("serverSSLParameters", serverSSLParameters);

        context.start();
        try {
            MockEndpoint mockEndpoint = context.getEndpoint("mock:result", MockEndpoint.class);
            mockEndpoint.expectedBodiesReceived("Hello World 1", "Hello World 2", "Hello World 3");

            ProducerTemplate template = context.createProducerTemplate();
            template.sendBody("vertx-websocket:wss:localhost:" + port + "/test?sslContextParameters=#clientSSLParameters",
                    "World 1");
            template.sendBody("vertx-websocket:wss:/localhost:" + port + "/test?sslContextParameters=#clientSSLParameters",
                    "World 2");
            template.sendBody("vertx-websocket:wss://localhost:" + port + "/test?sslContextParameters=#clientSSLParameters",
                    "World 3");

            mockEndpoint.assertIsSatisfied();
        } finally {
            context.stop();
        }
    }

    @Test
    void testConsumeAsSecureClient() throws Exception {
        CamelContext context = new DefaultCamelContext();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                fromF("vertx-websocket:localhost:%d/echo?sslContextParameters=#serverSSLParameters", port)
                        .log("Server consumer received message: ${body}")
                        .toF("vertx-websocket:localhost:%d/echo?sendToAll=true&sslContextParameters=#clientSSLParameters",
                                port);

                fromF("vertx-websocket:localhost:%d/echo?consumeAsClient=true&sslContextParameters=#clientSSLParameters", port)
                        .log("Client consumer received message: ${body}")
                        .to("mock:result");
            }
        });

        context.getRegistry().bind("clientSSLParameters", clientSSLParameters);
        context.getRegistry().bind("serverSSLParameters", serverSSLParameters);

        context.start();
        try {
            MockEndpoint mockEndpoint = context.getEndpoint("mock:result", MockEndpoint.class);
            mockEndpoint.expectedMessageCount(5);

            ProducerTemplate template = context.createProducerTemplate();
            String uri = "vertx-websocket:wss:localhost:" + port + "/echo?sslContextParameters=#clientSSLParameters";
            for (int i = 1; i <= 5; i++) {
                template.sendBody(uri, "Hello World " + i);
            }

            mockEndpoint.assertIsSatisfied();
        } finally {
            context.stop();
        }
    }

    @Override
    protected void startCamelContext() {
    }
}
