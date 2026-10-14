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
package org.apache.camel.component.smpp;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;

import org.jsmpp.session.connection.Connection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The connection factory checks that the certificate of the SMSC matches the host it connects to, directly and through
 * an HTTP proxy.
 * <p>
 * The certificate in jsse/localhost.p12 lists only DNS:localhost as subject alternative name, so it matches "localhost"
 * but not "127.0.0.1".
 */
class SmppConnectionFactoryHostnameVerificationTest {

    private static final char[] PASSWORD = "changeit".toCharArray();
    // the proxy test server ignores the target of the CONNECT request and answers the TLS handshake itself
    private static final int SMSC_PORT = 2775;

    private final ExecutorService executor = Executors.newCachedThreadPool();
    private SSLContext serverContext;
    private SSLContext clientContext;
    private ServerSocket server;

    @BeforeEach
    void createSslContexts() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("jsse/localhost.p12")) {
            keyStore.load(in, PASSWORD);
        }
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStore, PASSWORD);
        serverContext = SSLContext.getInstance("TLS");
        serverContext.init(keyManagerFactory.getKeyManagers(), null, null);

        TrustManagerFactory trustManagerFactory
                = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(keyStore);
        clientContext = SSLContext.getInstance("TLS");
        clientContext.init(null, trustManagerFactory.getTrustManagers(), null);
    }

    @AfterEach
    void stopServer() throws IOException {
        if (server != null) {
            server.close();
        }
        executor.shutdownNow();
    }

    @Test
    void connectsWhenTheCertificateMatchesTheHost() throws Exception {
        startTlsServer();

        Connection connection = factory(true, false).createConnection("localhost", server.getLocalPort());
        try {
            write(connection);
        } finally {
            connection.close();
        }
    }

    @Test
    void failsWhenTheCertificateDoesNotMatchTheHost() throws Exception {
        startTlsServer();

        // the handshake runs in createConnection, so the mismatch is reported there rather than on the first
        // read or write - which is what SMPPSession.connectAndBind rethrows when the bind fails
        SmppConnectionFactory factory = factory(true, false);
        IOException e = assertThrows(IOException.class, () -> factory.createConnection("127.0.0.1", server.getLocalPort()));

        SSLHandshakeException cause = assertInstanceOf(SSLHandshakeException.class, e.getCause());
        assertTrue(cause.getMessage().contains("127.0.0.1"), cause.getMessage());
    }

    @Test
    void connectsToAHostTheCertificateDoesNotListWhenHostnameVerificationIsDisabled() throws Exception {
        startTlsServer();

        Connection connection = factory(false, false).createConnection("127.0.0.1", server.getLocalPort());
        try {
            write(connection);
        } finally {
            connection.close();
        }
    }

    @Test
    void connectsThroughTheProxyWhenTheCertificateMatchesTheHost() throws Exception {
        startProxyAnsweringWithTls();

        Connection connection = factory(true, true).createConnection("localhost", SMSC_PORT);
        try {
            write(connection);
        } finally {
            connection.close();
        }
    }

    @Test
    void failsThroughTheProxyWhenTheCertificateDoesNotMatchTheHost() throws Exception {
        startProxyAnsweringWithTls();

        SmppConnectionFactory factory = factory(true, true);
        IOException e = assertThrows(IOException.class, () -> factory.createConnection("127.0.0.1", SMSC_PORT));

        SSLHandshakeException cause = assertInstanceOf(SSLHandshakeException.class, e.getCause());
        assertTrue(cause.getMessage().contains("127.0.0.1"), cause.getMessage());
    }

    @Test
    void connectsThroughTheProxyToAHostTheCertificateDoesNotListWhenHostnameVerificationIsDisabled() throws Exception {
        startProxyAnsweringWithTls();

        Connection connection = factory(false, true).createConnection("127.0.0.1", SMSC_PORT);
        try {
            write(connection);
        } finally {
            connection.close();
        }
    }

    private SmppConnectionFactory factory(boolean hostnameVerification, boolean throughProxy) {
        SmppConfiguration configuration = new SmppConfiguration();
        configuration.setUsingSSL(true);
        configuration.setHostnameVerification(hostnameVerification);
        configuration.setEnquireLinkTimer(5000);
        if (throughProxy) {
            configuration.setHttpProxyHost("localhost");
            configuration.setHttpProxyPort(server.getLocalPort());
        }
        return new SmppConnectionFactory(configuration, clientContext.getSocketFactory());
    }

    private static void write(Connection connection) throws IOException {
        OutputStream out = connection.getOutputStream();
        out.write(0);
        out.flush();
    }

    private void startTlsServer() throws IOException {
        // no host, so the server can be reached with a host name and with an IP address
        server = serverContext.getServerSocketFactory().createServerSocket();
        server.bind(new InetSocketAddress(0));
        executor.submit(() -> {
            try (SSLSocket socket = (SSLSocket) server.accept()) {
                socket.startHandshake();
                socket.getInputStream().read();
            }
            return null;
        });
    }

    private void startProxyAnsweringWithTls() throws IOException {
        server = new ServerSocket();
        server.bind(new InetSocketAddress(0));
        executor.submit(() -> {
            try (Socket socket = server.accept()) {
                readRequestHeaders(socket.getInputStream());
                socket.getOutputStream()
                        .write("HTTP/1.0 200 Connection established\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                socket.getOutputStream().flush();
                try (SSLSocket tls = (SSLSocket) serverContext.getSocketFactory().createSocket(socket, null, true)) {
                    tls.startHandshake();
                    tls.getInputStream().read();
                }
            }
            return null;
        });
    }

    private static void readRequestHeaders(InputStream in) throws IOException {
        int matched = 0;
        byte[] end = "\r\n\r\n".getBytes(StandardCharsets.US_ASCII);
        while (matched < end.length) {
            int b = in.read();
            if (b == -1) {
                throw new IOException("Connection closed before the end of the CONNECT request");
            }
            matched = b == end[matched] ? matched + 1 : (b == end[0] ? 1 : 0);
        }
    }
}
