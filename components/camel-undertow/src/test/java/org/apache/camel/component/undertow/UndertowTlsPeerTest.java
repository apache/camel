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
package org.apache.camel.component.undertow;

import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;

import io.undertow.server.HttpServerExchange;
import io.undertow.server.SSLSessionInfo;
import io.undertow.server.ServerConnection;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.TlsPeerCertificateProvider;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UndertowTlsPeerTest {

    @Test
    void usesAuthenticatedConnectionSessionAndRejectsForwardedCertificateInfo() throws Exception {
        try (var context = new DefaultCamelContext()) {
            var exchange = new DefaultExchange(context);
            var certificate = mock(X509Certificate.class);
            var session = mock(SSLSession.class);
            when(session.getPeerCertificates()).thenReturn(new Certificate[] { certificate });
            var info = mock(SSLSessionInfo.class);
            when(info.getSSLSession()).thenReturn(session);
            var connection = mock(ServerConnection.class);
            when(connection.getSslSessionInfo()).thenReturn(info);
            var request = new HttpServerExchange(connection);
            UndertowConsumer.populateTlsPeer(exchange, request);
            var provider = exchange.getProperty(TlsPeerCertificateProvider.EXCHANGE_PROPERTY, TlsPeerCertificateProvider.class);
            assertArrayEquals(new X509Certificate[] { certificate }, provider.getPeerCertificates());

            // Undertow's SSLHeaderHandler may expose forwarded certificates without a real TLS session.
            when(info.getSSLSession()).thenReturn(null);
            when(info.getPeerCertificates()).thenReturn(new Certificate[] { certificate });
            exchange.getMessage().setHeader("X-Forwarded-Client-Cert", "spiffe://example.org/frontend");
            UndertowConsumer.populateTlsPeer(exchange, request);
            var noPeer = exchange.getProperty(TlsPeerCertificateProvider.EXCHANGE_PROPERTY, TlsPeerCertificateProvider.class);
            assertThrows(SSLPeerUnverifiedException.class, noPeer::getPeerCertificates);
        }
    }

    @Test
    void missingClientAuthenticationIsNotIdentity() throws Exception {
        try (var context = new DefaultCamelContext()) {
            var exchange = new DefaultExchange(context);
            var session = mock(SSLSession.class);
            when(session.getPeerCertificates()).thenThrow(new SSLPeerUnverifiedException("No client certificate"));
            var info = mock(SSLSessionInfo.class);
            when(info.getSSLSession()).thenReturn(session);
            var connection = mock(ServerConnection.class);
            when(connection.getSslSessionInfo()).thenReturn(info);
            UndertowConsumer.populateTlsPeer(exchange, new HttpServerExchange(connection));
            var provider = exchange.getProperty(TlsPeerCertificateProvider.EXCHANGE_PROPERTY, TlsPeerCertificateProvider.class);
            assertThrows(SSLPeerUnverifiedException.class, provider::getPeerCertificates);
        }
    }
}
