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

import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Consumers sharing a host and port are served by one server, so they must use the same SSLContextParameters.
 */
public class VertxWebsocketSharedHostSSLContextParametersTest extends VertxWebSocketTestSupport {

    private SSLContextParameters serverParameters;
    private SSLContextParameters clientAuthServerParameters;
    private SSLContextParameters clientParameters;

    @BeforeEach
    public void setupSSLContextParameters() {
        serverParameters = new SSLContextParameters();
        serverParameters.setKeyManagers(keyManagers());

        clientAuthServerParameters = new SSLContextParameters();
        clientAuthServerParameters.setKeyManagers(keyManagers());
        clientAuthServerParameters.setTrustManagers(trustManagers());
        SSLContextServerParameters clientAuthentication = new SSLContextServerParameters();
        clientAuthentication.setClientAuthentication("REQUIRE");
        clientAuthServerParameters.setServerParameters(clientAuthentication);

        clientParameters = new SSLContextParameters();
        clientParameters.setTrustManagers(trustManagers());
    }

    @Test
    void consumerWithSSLContextParametersAfterConsumerWithoutFailsToStart() throws Exception {
        assertFailsToStart(createContext("", "?sslContextParameters=#serverParameters"));
    }

    @Test
    void consumerWithoutSSLContextParametersAfterConsumerWithFailsToStart() throws Exception {
        assertFailsToStart(createContext("?sslContextParameters=#serverParameters", ""));
    }

    @Test
    void consumerWithOtherSSLContextParametersFailsToStart() throws Exception {
        assertFailsToStart(createContext("?sslContextParameters=#serverParameters",
                "?sslContextParameters=#clientAuthServerParameters"));
    }

    @Test
    void consumersWithTheSameSSLContextParametersShareTheHost() throws Exception {
        CamelContext context = createContext("?sslContextParameters=#serverParameters",
                "?sslContextParameters=#serverParameters");
        context.start();
        try {
            MockEndpoint mockEndpoint = context.getEndpoint("mock:b", MockEndpoint.class);
            mockEndpoint.expectedBodiesReceived("Hello");
            mockEndpoint.setResultWaitTime(TimeUnit.SECONDS.toMillis(5));

            context.createProducerTemplate().sendBody(
                    "vertx-websocket:localhost:" + port.getPort() + "/b?sslContextParameters=#clientParameters", "Hello");

            mockEndpoint.assertIsSatisfied();
        } finally {
            context.stop();
        }
    }

    private CamelContext createContext(String firstOptions, String secondOptions) throws Exception {
        CamelContext context = new DefaultCamelContext();
        context.getRegistry().bind("serverParameters", serverParameters);
        context.getRegistry().bind("clientAuthServerParameters", clientAuthServerParameters);
        context.getRegistry().bind("clientParameters", clientParameters);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                fromF("vertx-websocket:localhost:%d/a%s", port.getPort(), firstOptions)
                        .routeId("a").startupOrder(1)
                        .to("mock:a");

                fromF("vertx-websocket:localhost:%d/b%s", port.getPort(), secondOptions)
                        .routeId("b").startupOrder(2)
                        .to("mock:b");
            }
        });
        return context;
    }

    private static void assertFailsToStart(CamelContext context) {
        try {
            Exception exception = assertThrows(Exception.class, context::start);
            Throwable cause = exception;
            while (cause != null && !(cause instanceof IllegalArgumentException)) {
                cause = cause.getCause();
            }
            assertNotNull(cause, "Expected an IllegalArgumentException, got " + exception);
            assertTrue(cause.getMessage().contains("localhost:"), cause.getMessage());
            assertTrue(cause.getMessage().contains("same SSLContextParameters"), cause.getMessage());
        } finally {
            context.stop();
        }
    }

    private static KeyManagersParameters keyManagers() {
        KeyStoreParameters keyStore = new KeyStoreParameters();
        keyStore.setResource("server.jks");
        keyStore.setPassword("security");
        KeyManagersParameters keyManagers = new KeyManagersParameters();
        keyManagers.setKeyPassword("security");
        keyManagers.setKeyStore(keyStore);
        return keyManagers;
    }

    private static TrustManagersParameters trustManagers() {
        KeyStoreParameters trustStore = new KeyStoreParameters();
        trustStore.setResource("client.jks");
        trustStore.setPassword("storepass");
        TrustManagersParameters trustManagers = new TrustManagersParameters();
        trustManagers.setKeyStore(trustStore);
        return trustManagers;
    }
}
