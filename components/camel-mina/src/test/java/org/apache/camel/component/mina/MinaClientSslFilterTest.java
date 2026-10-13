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
package org.apache.camel.component.mina;

import java.net.InetSocketAddress;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import org.apache.mina.core.session.DummySession;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The SSL filter used by the producer and by the consumer in client mode.
 */
class MinaClientSslFilterTest {

    @Test
    void checksTheIpAddressAsGivenWhenHostnameVerificationIsEnabled() throws Exception {
        MinaClientSslFilter filter = new MinaClientSslFilter(sslContext(), true);

        SSLEngine engine = filter.createEngine(new DummySession(), new InetSocketAddress("127.0.0.1", 9999));

        assertEquals("HTTPS", engine.getSSLParameters().getEndpointIdentificationAlgorithm());
        assertEquals("127.0.0.1", engine.getPeerHost());
    }

    @Test
    void checksTheHostNameAsGivenWhenHostnameVerificationIsEnabled() throws Exception {
        MinaClientSslFilter filter = new MinaClientSslFilter(sslContext(), true);

        SSLEngine engine = filter.createEngine(new DummySession(), new InetSocketAddress("localhost", 9999));

        assertEquals("HTTPS", engine.getSSLParameters().getEndpointIdentificationAlgorithm());
        assertEquals("localhost", engine.getPeerHost());
    }

    @Test
    void doesNotCheckTheHostWhenHostnameVerificationIsDisabled() throws Exception {
        MinaClientSslFilter filter = new MinaClientSslFilter(sslContext(), false);

        SSLEngine engine = filter.createEngine(new DummySession(), new InetSocketAddress("localhost", 9999));

        assertNull(engine.getSSLParameters().getEndpointIdentificationAlgorithm());
    }

    private static SSLContext sslContext() throws Exception {
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, null, null);
        return sslContext;
    }
}
