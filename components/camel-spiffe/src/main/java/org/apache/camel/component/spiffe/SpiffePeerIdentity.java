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
package org.apache.camel.component.spiffe;

import java.security.cert.Certificate;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.List;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;

import io.spiffe.spiffeid.SpiffeId;
import org.apache.camel.Exchange;
import org.apache.camel.Message;

/**
 * Extracts the peer SPIFFE ID from the <em>verified</em> TLS peer certificate carried on an exchange.
 * <p>
 * The identity is taken from the certificate the TLS layer authenticated, never from a message header a sender could
 * set (see CAMEL-24730 for why that distinction matters in this component). The supported sources are, in order:
 * <ol>
 * <li>an {@link SSLSession} placed on the message by a TLS consumer - camel-netty-http stores it under
 * {@code CamelNettySSLSession}. {@link SSLSession#getPeerCertificates()} returns only the peer certificates the
 * handshake verified, and throws when the peer presented none;</li>
 * <li>the Servlet container's verified client-certificate request attribute
 * ({@code jakarta.servlet.request.X509Certificate}, or the legacy {@code javax} name), when a consumer surfaces it onto
 * the message.</li>
 * </ol>
 */
final class SpiffePeerIdentity {

    static final String SPIFFE_URI_SCHEME = "spiffe://";
    static final String SERVLET_X509_ATTRIBUTE = "jakarta.servlet.request.X509Certificate";
    static final String SERVLET_X509_ATTRIBUTE_LEGACY = "javax.servlet.request.X509Certificate";

    // RFC 5280 GeneralName tag for uniformResourceIdentifier, as returned by X509Certificate#getSubjectAlternativeNames
    private static final int SAN_TYPE_URI = 6;

    private SpiffePeerIdentity() {
    }

    /**
     * Returns the SPIFFE ID of the verified TLS peer, or {@code null} when the exchange carries no verified peer
     * certificate or the certificate is not a valid SPIFFE X509-SVID.
     */
    static SpiffeId fromExchange(Exchange exchange, String sslSessionHeader) {
        X509Certificate peer = peerCertificate(exchange.getMessage(), sslSessionHeader);
        return peer != null ? fromCertificate(peer) : null;
    }

    private static X509Certificate peerCertificate(Message message, String sslSessionHeader) {
        SSLSession session = message.getHeader(sslSessionHeader, SSLSession.class);
        if (session != null) {
            X509Certificate cert = leafOf(peerCertificates(session));
            if (cert != null) {
                return cert;
            }
        }
        X509Certificate cert = certificateFromHeader(message, SERVLET_X509_ATTRIBUTE);
        if (cert == null) {
            cert = certificateFromHeader(message, SERVLET_X509_ATTRIBUTE_LEGACY);
        }
        return cert;
    }

    private static Certificate[] peerCertificates(SSLSession session) {
        try {
            return session.getPeerCertificates();
        } catch (SSLPeerUnverifiedException e) {
            // the peer did not present a verified certificate - there is no identity to authorize on
            return null;
        }
    }

    private static X509Certificate certificateFromHeader(Message message, String name) {
        Object value = message.getHeader(name);
        if (value instanceof X509Certificate[] certs) {
            return leafOf(certs);
        }
        if (value instanceof Certificate[] certs) {
            return leafOf(certs);
        }
        if (value instanceof X509Certificate cert) {
            return cert;
        }
        return null;
    }

    private static X509Certificate leafOf(Certificate[] chain) {
        if (chain == null || chain.length == 0) {
            return null;
        }
        // the peer's own (leaf) certificate is first in the chain returned by the JSSE
        return chain[0] instanceof X509Certificate x509 ? x509 : null;
    }

    /**
     * Extracts the SPIFFE ID from a certificate's URI SAN. A SPIFFE X509-SVID carries exactly one URI SAN, which is the
     * SPIFFE ID; a certificate with none, or with more than one SPIFFE URI, is not a valid SVID and yields {@code null}
     * rather than an arbitrary choice between identities.
     */
    static SpiffeId fromCertificate(X509Certificate cert) {
        Collection<List<?>> sans;
        try {
            sans = cert.getSubjectAlternativeNames();
        } catch (CertificateParsingException e) {
            return null;
        }
        if (sans == null) {
            return null;
        }
        String spiffeUri = null;
        for (List<?> san : sans) {
            if (san == null || san.size() < 2) {
                continue;
            }
            if (san.get(0) instanceof Integer type && type == SAN_TYPE_URI
                    && san.get(1) instanceof String uri && uri.startsWith(SPIFFE_URI_SCHEME)) {
                if (spiffeUri != null) {
                    // more than one SPIFFE URI SAN: not a valid SVID, refuse to guess which identity to trust
                    return null;
                }
                spiffeUri = uri;
            }
        }
        if (spiffeUri == null) {
            return null;
        }
        try {
            return SpiffeId.parse(spiffeUri);
        } catch (RuntimeException e) {
            // malformed SPIFFE ID in the SAN
            return null;
        }
    }
}
