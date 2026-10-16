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

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.security.KeyStore;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.TrustManagerFactory;

import org.apache.mina.core.session.DummySession;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The SSL filter used by the producer and by the consumer in client mode.
 * <p>
 * The certificate in jsse/localhost.p12 lists only DNS:localhost as subject alternative name, so it matches "localhost"
 * but not "127.0.0.1".
 */
class MinaClientSslFilterTest {

    private static final char[] PASSWORD = "changeit".toCharArray();

    @Test
    void checksTheIpAddressAsGivenWhenHostnameVerificationIsEnabled() throws Exception {
        MinaClientSslFilter filter = new MinaClientSslFilter(defaultSslContext(), true);

        SSLEngine engine = filter.createEngine(new DummySession(), new InetSocketAddress("127.0.0.1", 9999));

        assertEquals("HTTPS", engine.getSSLParameters().getEndpointIdentificationAlgorithm());
        assertEquals("127.0.0.1", engine.getPeerHost());
    }

    @Test
    void checksTheHostNameAsGivenWhenHostnameVerificationIsEnabled() throws Exception {
        MinaClientSslFilter filter = new MinaClientSslFilter(defaultSslContext(), true);

        SSLEngine engine = filter.createEngine(new DummySession(), new InetSocketAddress("localhost", 9999));

        assertEquals("HTTPS", engine.getSSLParameters().getEndpointIdentificationAlgorithm());
        assertEquals("localhost", engine.getPeerHost());
    }

    @Test
    void doesNotCheckTheHostWhenHostnameVerificationIsDisabled() throws Exception {
        MinaClientSslFilter filter = new MinaClientSslFilter(defaultSslContext(), false);

        SSLEngine engine = filter.createEngine(new DummySession(), new InetSocketAddress("localhost", 9999));

        assertNull(engine.getSSLParameters().getEndpointIdentificationAlgorithm());
    }

    @Test
    void handshakeFailsWhenTheCertificateDoesNotMatchTheHost() throws Exception {
        SSLEngine client = clientEngine(true, "127.0.0.1");
        SSLEngine server = serverEngine();

        SSLHandshakeException e = assertThrows(SSLHandshakeException.class, () -> handshake(client, server));

        assertTrue(e.getMessage().contains("127.0.0.1"), e.getMessage());
    }

    @Test
    void handshakeSucceedsWhenTheCertificateMatchesTheHost() throws Exception {
        handshake(clientEngine(true, "localhost"), serverEngine());
    }

    @Test
    void handshakeSucceedsWithAHostTheCertificateDoesNotListWhenHostnameVerificationIsDisabled() throws Exception {
        handshake(clientEngine(false, "127.0.0.1"), serverEngine());
    }

    private static SSLContext defaultSslContext() throws Exception {
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, null, null);
        return sslContext;
    }

    private static SSLEngine clientEngine(boolean hostnameVerification, String host) throws Exception {
        TrustManagerFactory trustManagerFactory
                = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(keyStore());
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustManagerFactory.getTrustManagers(), null);

        SSLEngine engine = new MinaClientSslFilter(sslContext, hostnameVerification)
                .createEngine(new DummySession(), new InetSocketAddress(host, 9999));
        // a DummySession belongs to an acceptor, so MINA created the engine in server mode
        engine.setUseClientMode(true);
        return engine;
    }

    private static SSLEngine serverEngine() throws Exception {
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStore(), PASSWORD);
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(keyManagerFactory.getKeyManagers(), null, null);

        SSLEngine engine = sslContext.createSSLEngine();
        engine.setUseClientMode(false);
        return engine;
    }

    private static KeyStore keyStore() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream in = MinaClientSslFilterTest.class.getClassLoader().getResourceAsStream("jsse/localhost.p12")) {
            keyStore.load(in, PASSWORD);
        }
        return keyStore;
    }

    /**
     * Runs a TLS handshake between the two engines in memory.
     */
    private static void handshake(SSLEngine client, SSLEngine server) throws SSLException {
        ByteBuffer clientToServer = ByteBuffer.allocate(65536);
        ByteBuffer serverToClient = ByteBuffer.allocate(65536);
        client.beginHandshake();
        server.beginHandshake();
        for (int i = 0; i < 100 && !(isDone(client) && isDone(server)); i++) {
            step(client, serverToClient, clientToServer);
            step(server, clientToServer, serverToClient);
        }
        assertTrue(isDone(client) && isDone(server), "The handshake should complete");
    }

    private static boolean isDone(SSLEngine engine) {
        HandshakeStatus status = engine.getHandshakeStatus();
        return status == HandshakeStatus.FINISHED || status == HandshakeStatus.NOT_HANDSHAKING;
    }

    /**
     * Reads what the peer sent from {@code in}, then writes what the engine has to send to {@code out}.
     */
    private static void step(SSLEngine engine, ByteBuffer in, ByteBuffer out) throws SSLException {
        ByteBuffer application = ByteBuffer.allocate(65536);
        in.flip();
        while (in.hasRemaining() && engine.getHandshakeStatus() == HandshakeStatus.NEED_UNWRAP) {
            SSLEngineResult result = engine.unwrap(in, application);
            runTasks(engine);
            if (result.getStatus() != SSLEngineResult.Status.OK) {
                break;
            }
        }
        in.compact();
        runTasks(engine);
        while (engine.getHandshakeStatus() == HandshakeStatus.NEED_WRAP) {
            SSLEngineResult result = engine.wrap(ByteBuffer.allocate(0), out);
            runTasks(engine);
            if (result.getStatus() != SSLEngineResult.Status.OK) {
                break;
            }
        }
    }

    private static void runTasks(SSLEngine engine) {
        Runnable task = engine.getDelegatedTask();
        while (task != null) {
            task.run();
            task = engine.getDelegatedTask();
        }
    }
}
