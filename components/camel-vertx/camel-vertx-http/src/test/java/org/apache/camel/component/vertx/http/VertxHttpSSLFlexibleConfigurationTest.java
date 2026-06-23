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
package org.apache.camel.component.vertx.http;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.jsse.KeyManagersParameters;
import org.apache.camel.support.jsse.KeyStoreParameters;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.support.jsse.TrustManagersParameters;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test flexible SSL/TLS configuration scenarios: - Server with only keystore (no truststore) - Client with only
 * truststore (no keystore) This validates CAMEL-23392 fix allowing optional keystore/truststore configuration
 */
class VertxHttpSSLFlexibleConfigurationTest extends VertxHttpTestSupport {

    @Test
    void testServerKeystoreOnlyClientTruststoreOnly() {
        // Test one-way SSL: server presents certificate, client verifies it
        // No mutual authentication (client doesn't present certificate)
        String result
                = template.requestBody(getProducerUri() + "?sslContextParameters=#clientSSLParameters", null, String.class);
        assertEquals("Hello World", result);
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // Server with only keystore configured (no truststore)
                from(getTestServerUri() + "?sslContextParameters=#serverSSLParameters")
                        .setBody(constant("Hello World"));
            }
        };
    }

    @Override
    protected void bindToRegistry(Registry registry) {
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

        registry.bind("serverSSLParameters", serverSSLParameters);

        // Client SSL: only truststore (verifies server certificate, doesn't present own)
        SSLContextParameters clientSSLParameters = new SSLContextParameters();

        KeyStoreParameters truststoreParameters = new KeyStoreParameters();
        truststoreParameters.setResource("client.jks");
        truststoreParameters.setPassword("storepass");

        TrustManagersParameters clientSSLTrustManagers = new TrustManagersParameters();
        clientSSLTrustManagers.setKeyStore(truststoreParameters);
        clientSSLParameters.setTrustManagers(clientSSLTrustManagers);
        // Note: NO keystore configured on client

        registry.bind("clientSSLParameters", clientSSLParameters);
    }
}
