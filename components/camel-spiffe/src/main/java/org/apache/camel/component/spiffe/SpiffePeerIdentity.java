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
 * set (see CAMEL-24730 for why that distinction matters in this component). The source is the {@link SSLSession} a TLS
 * consumer places on the message - camel-netty-http stores it under {@code CamelNettySSLSession}:
 * {@link SSLSession#getPeerCertificates()} returns only the peer certificates the handshake verified, and throws when
 * the peer presented none. A certificate object read from a message header is deliberately not trusted, because nothing
 * proves the TLS layer verified it.
 */
final class SpiffePeerIdentity {

    static final String SPIFFE_URI_SCHEME = "spiffe://";

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
        return session != null ? leafOf(peerCertificates(session)) : null;
    }

    private static Certificate[] peerCertificates(SSLSession session) {
        try {
            return session.getPeerCertificates();
        } catch (SSLPeerUnverifiedException e) {
            // the peer did not present a verified certificate - there is no identity to authorize on
            return null;
        }
    }

    private static X509Certificate leafOf(Certificate[] chain) {
        if (chain == null || chain.length == 0) {
            return null;
        }
        // the peer's own (leaf) certificate is first in the chain returned by the JSSE
        return chain[0] instanceof X509Certificate x509 ? x509 : null;
    }

    /**
     * Extracts the SPIFFE ID from a certificate's URI SAN. A SPIFFE X509-SVID carries exactly one URI SAN <em>in
     * total</em>, and it is the SPIFFE ID. A certificate with no URI SAN, with more than one URI SAN (even if only one
     * is a {@code spiffe://} URI), or whose single URI SAN is not a {@code spiffe://} URI, is not a valid SVID and
     * yields {@code null} rather than an arbitrary choice of identity.
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
        String uri = null;
        for (List<?> san : sans) {
            if (san == null || san.size() < 2) {
                continue;
            }
            if (san.get(0) instanceof Integer type && type == SAN_TYPE_URI && san.get(1) instanceof String value) {
                if (uri != null) {
                    // a valid X509-SVID has exactly one URI SAN in total; more than one is not an SVID, so refuse to
                    // single out the spiffe:// one and trust it
                    return null;
                }
                uri = value;
            }
        }
        if (uri == null || !uri.startsWith(SPIFFE_URI_SCHEME)) {
            return null;
        }
        try {
            return SpiffeId.parse(uri);
        } catch (RuntimeException e) {
            // malformed SPIFFE ID in the SAN
            return null;
        }
    }
}
