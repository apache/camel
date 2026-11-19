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
package org.apache.camel.component.nats;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;

import io.nats.client.Connection;
import org.apache.camel.BindToRegistry;
import org.apache.camel.support.jsse.KeyStoreParameters;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.support.jsse.TrustManagersParameters;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Connects with TLS to a local server whose certificate is issued for localhost only, to check that the host name in
 * the certificate is checked.
 */
public class NatsHostnameVerificationTest extends CamelTestSupport {

    private static final String RESOURCES = "org/apache/camel/component/nats/hostname/";
    private static final String PASSWORD = "changeit";
    private static final long TIMEOUT_SECONDS = 20;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private ServerSocket server;

    @BindToRegistry("ssl")
    public SSLContextParameters ssl() {
        KeyStoreParameters trustStore = new KeyStoreParameters();
        trustStore.setResource("classpath:" + RESOURCES + "truststore.p12");
        trustStore.setType("PKCS12");
        trustStore.setPassword(PASSWORD);
        TrustManagersParameters trustManagers = new TrustManagersParameters();
        trustManagers.setKeyStore(trustStore);
        SSLContextParameters ssl = new SSLContextParameters();
        ssl.setTrustManagers(trustManagers);
        return ssl;
    }

    @AfterEach
    void stopServer() throws IOException {
        if (server != null) {
            server.close();
        }
        executor.shutdownNow();
    }

    @Test
    void certificateForAnotherHostIsRefused() throws Exception {
        CompletableFuture<Boolean> handshake = startServer();

        // the certificate names localhost, not the IP address
        connect("127.0.0.1", "");

        assertFalse(handshake.get(TIMEOUT_SECONDS, TimeUnit.SECONDS),
                "the certificate does not name the host, so the connection must fail");
    }

    @Test
    void certificateForTheHostIsAccepted() throws Exception {
        CompletableFuture<Boolean> handshake = startServer();

        connect("localhost", "");

        assertTrue(handshake.get(TIMEOUT_SECONDS, TimeUnit.SECONDS), "the certificate names the host");
    }

    @Test
    void hostnameVerificationCanBeTurnedOff() throws Exception {
        CompletableFuture<Boolean> handshake = startServer();

        connect("127.0.0.1", "&hostnameVerification=false");

        assertTrue(handshake.get(TIMEOUT_SECONDS, TimeUnit.SECONDS),
                "without the host name check, a trusted certificate is accepted for any host");
    }

    private void connect(String host, String options) {
        NatsEndpoint endpoint = context.getEndpoint("nats:test?servers=" + host + ":" + server.getLocalPort()
                                                    + "&secure=true&sslContextParameters=#ssl&reconnect=false"
                                                    + options,
                NatsEndpoint.class);
        try (Connection connection = endpoint.getConnection()) {
            // the server is not a NATS server: it only does the TLS handshake
        } catch (Exception e) {
            // expected, the server does not answer the CONNECT
        }
    }

    /**
     * Starts a server that sends the INFO line of a NATS server that requires TLS, then does the TLS handshake with the
     * certificate for localhost, for the first connection only.
     *
     * @return completes with whether the TLS handshake with the client succeeded
     */
    private CompletableFuture<Boolean> startServer() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(RESOURCES + "server.p12")) {
            keyStore.load(is, PASSWORD.toCharArray());
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, PASSWORD.toCharArray());
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), null, null);

        server = new ServerSocket();
        // localhost may connect to ::1 or 127.0.0.1
        server.bind(new InetSocketAddress(0));
        server.setSoTimeout((int) TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));

        CompletableFuture<Boolean> handshake = new CompletableFuture<>();
        executor.submit(() -> {
            try (Socket socket = server.accept()) {
                socket.getOutputStream().write(("INFO {\"server_id\":\"test\",\"version\":\"2.10.0\",\"proto\":1,"
                                                + "\"max_payload\":1048576,\"tls_required\":true}\r\n")
                        .getBytes(StandardCharsets.US_ASCII));
                socket.getOutputStream().flush();
                SSLSocket tls = (SSLSocket) sslContext.getSocketFactory()
                        .createSocket(socket, null, socket.getPort(), false);
                tls.setUseClientMode(false);
                try {
                    tls.startHandshake();
                    handshake.complete(true);
                } catch (IOException e) {
                    handshake.complete(false);
                }
            } catch (Exception e) {
                handshake.completeExceptionally(e);
            }
        });
        return handshake;
    }
}
