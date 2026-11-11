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
package org.apache.camel.http.common;

import java.security.cert.X509Certificate;

import jakarta.servlet.http.HttpServletRequest;

import javax.net.ssl.SSLPeerUnverifiedException;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.TlsPeerCertificateProvider;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HttpMessageTlsPeerTest {

    private void initialize(Exchange exchange, HttpServletRequest request) {
        var endpoint = mock(HttpCommonEndpoint.class);
        when(endpoint.getHttpBinding()).thenReturn(mock(HttpBinding.class));
        new HttpMessage(exchange, endpoint, request, null);
    }

    @Test
    void publishesTheSecureContainerAttributeAndOverwritesStateOnReuse() throws Exception {
        try (var context = new DefaultCamelContext()) {
            var exchange = new DefaultExchange(context);
            var certificate = mock(X509Certificate.class);
            var request = mock(HttpServletRequest.class);
            when(request.isSecure()).thenReturn(true);
            when(request.getAttribute("jakarta.servlet.request.X509Certificate"))
                    .thenReturn(new X509Certificate[] { certificate });
            initialize(exchange, request);
            var provider = exchange.getProperty(TlsPeerCertificateProvider.EXCHANGE_PROPERTY, TlsPeerCertificateProvider.class);
            assertArrayEquals(new X509Certificate[] { certificate }, provider.getPeerCertificates());
            initialize(exchange, mock(HttpServletRequest.class));
            var noPeer = exchange.getProperty(TlsPeerCertificateProvider.EXCHANGE_PROPERTY, TlsPeerCertificateProvider.class);
            assertThrows(SSLPeerUnverifiedException.class, noPeer::getPeerCertificates);
        }
    }

    @Test
    void refusesCertificateHeadersAndAttributesOnAnInsecureRequest() throws Exception {
        try (var context = new DefaultCamelContext()) {
            var certificate = mock(X509Certificate.class);
            for (boolean secure : new boolean[] { false, true }) {
                var exchange = new DefaultExchange(context);
                var request = mock(HttpServletRequest.class);
                when(request.isSecure()).thenReturn(secure);
                when(request.getHeader("X-Forwarded-Client-Cert")).thenReturn("spiffe://example.org/frontend");
                if (!secure) {
                    when(request.getAttribute("jakarta.servlet.request.X509Certificate"))
                            .thenReturn(new X509Certificate[] { certificate });
                }
                initialize(exchange, request);
                var provider
                        = exchange.getProperty(TlsPeerCertificateProvider.EXCHANGE_PROPERTY, TlsPeerCertificateProvider.class);
                assertThrows(SSLPeerUnverifiedException.class, provider::getPeerCertificates);
            }
        }
    }
}
