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
package org.apache.camel.component.spiffe.integration;

import java.net.InetAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;

import org.apache.camel.component.spiffe.SpiffeSSLContextParameters;
import org.apache.camel.support.jsse.ClientAuthentication;
import org.apache.camel.support.jsse.SSLContextServerParameters;
import org.apache.camel.test.infra.spiffe.services.SpiffeService;
import org.apache.camel.test.infra.spiffe.services.SpiffeServiceFactory;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Completes a real mutual-TLS handshake through {@link SpiffeSSLContextParameters} against SVIDs from a live SPIRE
 * Workload API, asserting that an allow-listed peer connects and a non-allow-listed one is refused during the
 * handshake.
 */
class SpiffeMutualTlsIT extends CamelTestSupport {

    @RegisterExtension
    static SpiffeService service = SpiffeServiceFactory.createSingletonService();

    private SpiffeSSLContextParameters serverSsl(String acceptedSpiffeIds) {
        SpiffeSSLContextParameters ssl = new SpiffeSSLContextParameters();
        ssl.setSpiffeSocketPath(service.getWorkloadApiSocketPath());
        ssl.setAcceptedSpiffeIds(acceptedSpiffeIds);
        SSLContextServerParameters serverParameters = new SSLContextServerParameters();
        serverParameters.setClientAuthentication(ClientAuthentication.REQUIRE.name());
        ssl.setServerParameters(serverParameters);
        return ssl;
    }

    private SpiffeSSLContextParameters clientSsl() {
        SpiffeSSLContextParameters ssl = new SpiffeSSLContextParameters();
        ssl.setSpiffeSocketPath(service.getWorkloadApiSocketPath());
        ssl.setAcceptedSpiffeIds(service.getWorkloadSpiffeId());
        return ssl;
    }

    @Test
    void allowsAnAllowListedPeer() throws Exception {
        SSLContext serverContext = serverSsl(service.getWorkloadSpiffeId()).createSSLContext(context);
        SSLContext clientContext = clientSsl().createSSLContext(context);

        assertThatCode(() -> handshake(serverContext, clientContext)).doesNotThrowAnyException();
    }

    @Test
    void refusesANonAllowListedPeer() throws Exception {
        // the server accepts only an id the client does not have; its SVID is spiffe://example.org/workload
        SSLContext serverContext = serverSsl("spiffe://example.org/not-allowed").createSSLContext(context);
        SSLContext clientContext = clientSsl().createSSLContext(context);

        assertThatThrownBy(() -> handshake(serverContext, clientContext))
                .satisfies(t -> assertThat(causedBySsl(t)).as("handshake should fail with a TLS error").isTrue());
    }

    /**
     * Drives a mutual-TLS handshake over a loopback socket: the server requires a client certificate and both sides
     * present their SVID. Returns normally when the handshake and a one-byte exchange complete, and throws when either
     * side rejects the peer.
     */
    private void handshake(SSLContext serverContext, SSLContext clientContext) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (SSLServerSocket serverSocket = (SSLServerSocket) serverContext.getServerSocketFactory()
                .createServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            serverSocket.setNeedClientAuth(true);
            serverSocket.setSoTimeout(15000);

            Future<Void> server = executor.submit(() -> {
                try (SSLSocket accepted = (SSLSocket) serverSocket.accept()) {
                    accepted.setSoTimeout(15000);
                    accepted.startHandshake();
                    accepted.getInputStream().read();
                }
                return null;
            });

            int port = serverSocket.getLocalPort();
            try (SSLSocket clientSocket = (SSLSocket) clientContext.getSocketFactory()
                    .createSocket(InetAddress.getLoopbackAddress(), port)) {
                clientSocket.setSoTimeout(15000);
                clientSocket.startHandshake();
                clientSocket.getOutputStream().write(42);
                clientSocket.getOutputStream().flush();
            }
            // surface a server-side handshake rejection to the caller
            server.get(20, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
    }

    private static boolean causedBySsl(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof SSLException) {
                return true;
            }
        }
        return false;
    }
}
