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
package org.apache.camel.component.pulsar;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;

import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Connects with pulsar+ssl:// to localhost, to a local server whose certificate is issued either for localhost or for
 * another host name, to check that the host name in the certificate is checked.
 */
public class PulsarHostnameVerificationTest extends CamelTestSupport {

    private static final String RESOURCES = "org/apache/camel/component/pulsar/hostname/";
    private static final String PASSWORD = "changeit";
    private static final long TIMEOUT_SECONDS = 20;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private SSLServerSocket server;

    @AfterEach
    void stopServer() throws IOException {
        if (server != null) {
            server.close();
        }
        executor.shutdownNow();
    }

    @Test
    void certificateForAnotherHostIsRefused() throws Exception {
        CompletableFuture<Boolean> command = startServer("other.p12");

        connect("");

        assertFalse(command.get(TIMEOUT_SECONDS, TimeUnit.SECONDS),
                "the certificate names another host, so the connection must fail");
    }

    @Test
    void certificateForTheHostIsAccepted() throws Exception {
        CompletableFuture<Boolean> command = startServer("server.p12");

        connect("");

        assertTrue(command.get(TIMEOUT_SECONDS, TimeUnit.SECONDS), "the certificate names the host");
    }

    @Test
    void hostnameVerificationCanBeTurnedOff() throws Exception {
        CompletableFuture<Boolean> command = startServer("other.p12");

        connect("&hostnameVerification=false");

        assertTrue(command.get(TIMEOUT_SECONDS, TimeUnit.SECONDS),
                "without the host name check, a trusted certificate is accepted for any host");
    }

    private void connect(String options) throws Exception {
        URL ca = getClass().getClassLoader().getResource(RESOURCES + "ca.pem");
        PulsarEndpoint endpoint = context.getEndpoint("pulsar:persistent://public/default/hostname?serviceUrl=RAW(pulsar+ssl://"
                                                      + "localhost:" + server.getLocalPort() + ")"
                                                      + "&tlsTrustCertsFilePath=" + Path.of(ca.toURI())
                                                      + options,
                PulsarEndpoint.class);
        endpoint.start();
        // the client connects when it is first used; the server is not a broker, so this does not complete
        endpoint.getPulsarClient().getPartitionsForTopic("persistent://public/default/hostname");
    }

    /**
     * Starts a TLS server with the given certificate (server.p12 for localhost, other.p12 for broker.example.org, both
     * in ca.pem), for the first connection only.
     *
     * @return completes with whether the client sent its first command once the TLS handshake was done
     */
    private CompletableFuture<Boolean> startServer(String certificate) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(RESOURCES + certificate)) {
            keyStore.load(is, PASSWORD.toCharArray());
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, PASSWORD.toCharArray());
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), null, null);

        server = (SSLServerSocket) sslContext.getServerSocketFactory().createServerSocket();
        // localhost may connect to ::1 or 127.0.0.1
        server.bind(new InetSocketAddress(0));
        server.setSoTimeout((int) TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));

        CompletableFuture<Boolean> command = new CompletableFuture<>();
        executor.submit(() -> {
            try (SSLSocket socket = (SSLSocket) server.accept()) {
                socket.setSoTimeout((int) TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
                try {
                    socket.startHandshake();
                    command.complete(socket.getInputStream().read() >= 0);
                } catch (IOException e) {
                    command.complete(false);
                }
            } catch (Exception e) {
                command.completeExceptionally(e);
            }
        });
        return command;
    }
}
