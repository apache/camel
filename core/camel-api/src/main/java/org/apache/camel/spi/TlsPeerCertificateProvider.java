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
package org.apache.camel.spi;

import java.security.cert.X509Certificate;

import javax.net.ssl.SSLPeerUnverifiedException;

/**
 * Supplies the peer certificate chain authenticated by an inbound transport's TLS handshake. Consumers place this typed
 * value in an exchange property, never in message headers. Implementations must derive certificates from the actual
 * server connection or container TLS state, not from inbound identity/certificate headers. Route authors and container
 * authentication configuration remain trusted.
 */
@FunctionalInterface
public interface TlsPeerCertificateProvider {

    String EXCHANGE_PROPERTY = TlsPeerCertificateProvider.class.getName();

    /**
     * Returns the authenticated X509 chain, leaf first.
     *
     * @throws SSLPeerUnverifiedException when the connection has no authenticated X509 peer
     */
    X509Certificate[] getPeerCertificates() throws SSLPeerUnverifiedException;
}
