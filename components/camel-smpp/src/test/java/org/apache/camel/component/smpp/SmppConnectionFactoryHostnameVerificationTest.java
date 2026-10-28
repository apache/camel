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
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

import org.jsmpp.session.connection.Connection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    private final CountDownLatch stop = new CountDownLatch(1);
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
        stop.countDown();
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

    /**
     * createConnection returns no Connection when it fails, so jSMPP cannot close the socket either.
     * <p/>
     * An SMSC that accepts the connection and then never answers the ClientHello makes the handshake time out, and the
     * JDK does not close the socket on a read timeout - unlike a fatal alert, which it does close. So without closing
     * it here, every reconnect attempt against such an SMSC would leave one socket open.
     */
    @Test
    void closesTheSocketWhenTheHandshakeTimesOut() throws Exception {
        startServerThatNeverAnswers();

        List<SSLSocket> created = new ArrayList<>();
        SSLSocketFactory recording = new RecordingSslSocketFactory(clientContext.getSocketFactory(), created);

        SmppConfiguration configuration = new SmppConfiguration();
        configuration.setUsingSSL(true);
        configuration.setHostnameVerification(true);
        configuration.setEnquireLinkTimer(1000);
        SmppConnectionFactory factory = new SmppConnectionFactory(configuration, recording);

        IOException e
                = assertThrows(IOException.class, () -> factory.createConnection("localhost", server.getLocalPort()));
        assertInstanceOf(SocketTimeoutException.class, e.getCause());

        assertEquals(1, created.size(), "the factory should have created exactly one socket");
        assertTrue(created.get(0).isClosed(), "the socket must be closed when createConnection fails");
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

    private void startServerThatNeverAnswers() throws IOException {
        server = new ServerSocket();
        server.bind(new InetSocketAddress(0));
        executor.submit(() -> {
            try (Socket socket = server.accept()) {
                // hold the connection open without answering the ClientHello, until the test is done
                stop.await();
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

    /**
     * Delegates to the real factory and keeps the sockets it handed out, so a test can assert they were closed.
     */
    private static final class RecordingSslSocketFactory extends SSLSocketFactory {

        private final SSLSocketFactory delegate;
        private final List<SSLSocket> created;

        private RecordingSslSocketFactory(SSLSocketFactory delegate, List<SSLSocket> created) {
            this.delegate = delegate;
            this.created = created;
        }

        private Socket record(Socket socket) {
            created.add((SSLSocket) socket);
            return socket;
        }

        @Override
        public Socket createSocket() throws IOException {
            return record(delegate.createSocket());
        }

        @Override
        public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
            return record(delegate.createSocket(s, host, port, autoClose));
        }

        @Override
        public Socket createSocket(String host, int port) throws IOException {
            return record(delegate.createSocket(host, port));
        }

        @Override
        public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
            return record(delegate.createSocket(host, port, localHost, localPort));
        }

        @Override
        public Socket createSocket(InetAddress host, int port) throws IOException {
            return record(delegate.createSocket(host, port));
        }

        @Override
        public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort)
                throws IOException {
            return record(delegate.createSocket(address, port, localAddress, localPort));
        }

        @Override
        public String[] getDefaultCipherSuites() {
            return delegate.getDefaultCipherSuites();
        }

        @Override
        public String[] getSupportedCipherSuites() {
            return delegate.getSupportedCipherSuites();
        }
    }
}
