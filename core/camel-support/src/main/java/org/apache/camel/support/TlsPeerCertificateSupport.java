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

import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;

import org.apache.camel.Exchange;
import org.apache.camel.spi.TlsPeerCertificateProvider;

/** Helpers for consumer adapters publishing authenticated TLS state as a transport-neutral exchange property. */
public final class TlsPeerCertificateSupport {

    private static final X509Certificate[] NO_CERTIFICATES = new X509Certificate[0];
    private static final TlsPeerCertificateProvider UNVERIFIED_PEER = () -> {
        throw new SSLPeerUnverifiedException("The inbound connection has no authenticated X509 peer");
    };

    private TlsPeerCertificateSupport() {
    }

    /** Snapshots the actual server TLS session. An absent/unverified peer overwrites any prior exchange state. */
    public static void populateFromSession(Exchange exchange, SSLSession session) {
        Certificate[] chain = null;
        if (session != null) {
            try {
                chain = session.getPeerCertificates();
            } catch (SSLPeerUnverifiedException e) {
                // Server-only TLS and connections without client authentication have no authenticated peer identity.
            }
        }
        populateFromVerifiedCertificates(exchange, chain);
    }

    /**
     * Snapshots a chain already authenticated by the server container. Callers must never obtain this chain from an
     * inbound certificate header or a proxy's forwarded identity. An absent chain records an unverified peer.
     */
    public static void populateFromVerifiedCertificates(Exchange exchange, Certificate[] chain) {
        X509Certificate[] certificates = x509Chain(chain);
        TlsPeerCertificateProvider provider = certificates.length == 0 ? UNVERIFIED_PEER : () -> certificates.clone();
        exchange.setProperty(TlsPeerCertificateProvider.EXCHANGE_PROPERTY, provider);
    }

    private static X509Certificate[] x509Chain(Certificate[] chain) {
        if (chain == null || chain.length == 0) {
            return NO_CERTIFICATES;
        }
        X509Certificate[] certificates = new X509Certificate[chain.length];
        for (int i = 0; i < chain.length; i++) {
            if (!(chain[i] instanceof X509Certificate certificate)) {
                return NO_CERTIFICATES;
            }
            certificates[i] = certificate;
        }
        return certificates;
    }
}
