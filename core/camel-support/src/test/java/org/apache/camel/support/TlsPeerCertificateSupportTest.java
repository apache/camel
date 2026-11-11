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
package org.apache.camel.support;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;

import org.apache.camel.Exchange;
import org.apache.camel.spi.TlsPeerCertificateProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TlsPeerCertificateSupportTest {
    // Certificate validity is immaterial: these tests exercise transport snapshots, not certificate verification.
    private static final String CERTIFICATE = """
            -----BEGIN CERTIFICATE-----
            MIIBiTCCAS+gAwIBAgIUPconUFLaFpyI9v3cnRRgkW5RuA4wCgYIKoZIzj0EAwIw
            GjEYMBYGA1UEAwwPY2FtZWwtdW5pdC10ZXN0MB4XDTI2MTExMTEzMTE0MloXDTI3
            MTExMTEzMTE0MlowGjEYMBYGA1UEAwwPY2FtZWwtdW5pdC10ZXN0MFkwEwYHKoZI
            zj0CAQYIKoZIzj0DAQcDQgAE09OFOJCozTqJcYA93cEs/qaqlkPNbmxS/Lv8sldB
            RVT7SxsXpJJVMzXeaxdevH5h5ML2bOyJdxCbKC6oCiQe7qNTMFEwHQYDVR0OBBYE
            FJLqeVCObGKDlpPZKeXaMGkLJRggMB8GA1UdIwQYMBaAFJLqeVCObGKDlpPZKeXa
            MGkLJRggMA8GA1UdEwEB/wQFMAMBAf8wCgYIKoZIzj0EAwIDSAAwRQIhALAsOTMV
            V6ypVb7yivMjhVU9QxMsRa3MI28P3KRYqvpFAiAxDSl2inwNtqPE7fgG/5AsqO7d
            DR07AV8Iz8j07ZtrfQ==
            -----END CERTIFICATE-----
            """;

    private final Map<String, Object> properties = new HashMap<>();
    private final Exchange exchange = (Exchange) Proxy.newProxyInstance(Exchange.class.getClassLoader(),
            new Class<?>[] { Exchange.class }, (proxy, method, args) -> {
                if (method.getName().equals("setProperty")) {
                    properties.put((String) args[0], args[1]);
                    return null;
                }
                throw new UnsupportedOperationException(method.getName());
            });

    private TlsPeerCertificateProvider captured() {
        return (TlsPeerCertificateProvider) properties.get(TlsPeerCertificateProvider.EXCHANGE_PROPERTY);
    }

    private static X509Certificate certificate() throws Exception {
        return (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(CERTIFICATE.getBytes(StandardCharsets.US_ASCII)));
    }

    private static SSLSession session(Certificate[] chain) {
        return (SSLSession) Proxy.newProxyInstance(SSLSession.class.getClassLoader(), new Class<?>[] { SSLSession.class },
                (proxy, method, args) -> {
                    if (method.getName().equals("getPeerCertificates")) {
                        if (chain == null) {
                            throw new SSLPeerUnverifiedException("No client certificate");
                        }
                        return chain;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    @Test
    void snapshotsInputAndReturnedArrays() throws Exception {
        X509Certificate certificate = certificate();
        Certificate[] chain = { certificate };
        TlsPeerCertificateSupport.populateFromVerifiedCertificates(exchange, chain);
        chain[0] = null;
        TlsPeerCertificateProvider provider = captured();
        provider.getPeerCertificates()[0] = null;
        assertThat(provider.getPeerCertificates()).containsExactly(certificate);
    }

    @Test
    void obtainsOnlyVerifiedSessionCertificates() throws Exception {
        X509Certificate certificate = certificate();
        TlsPeerCertificateSupport.populateFromSession(exchange, session(new Certificate[] { certificate }));
        assertThat(captured().getPeerCertificates()).containsExactly(certificate);
    }

    @Test
    void unverifiedSessionCreatesAuthoritativeAbsence() throws Exception {
        TlsPeerCertificateSupport.populateFromSession(exchange, session(null));
        assertThatThrownBy(() -> captured().getPeerCertificates()).isInstanceOf(SSLPeerUnverifiedException.class);
    }

    @Test
    void rejectsNonX509Chains() {
        Certificate certificate = new Certificate("unit-test") {
            @Override
            public byte[] getEncoded() {
                return new byte[] { 1 };
            }

            @Override
            public void verify(PublicKey key) {
            }

            @Override
            public void verify(PublicKey key, String provider) {
            }

            @Override
            public String toString() {
                return "Unit test non-X509 certificate";
            }

            @Override
            public PublicKey getPublicKey() {
                return null;
            }
        };
        TlsPeerCertificateSupport.populateFromVerifiedCertificates(exchange, new Certificate[] { certificate });
        assertThatThrownBy(() -> captured().getPeerCertificates()).isInstanceOf(SSLPeerUnverifiedException.class);
    }
}
