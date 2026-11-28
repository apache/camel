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
package org.apache.camel.support.jsse;

import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The endpointIdentificationAlgorithm of the client parameters turns on host name verification for the client sockets
 * of the context. The test server's certificate is issued to localhost only, so a client that connects to 127.0.0.1 is
 * rejected with the algorithm set, and accepted without it, as before.
 */
public class SSLContextClientParametersEndpointIdentificationTest extends AbstractJsseParametersTest {

    private final CamelContext camelContext = new DefaultCamelContext();
    private ExecutorService executor;
    private SSLServerSocket serverSocket;

    @BeforeEach
    public void startServer() throws Exception {
        SSLContext serverContext = createSSLContextParameters(null).createSSLContext(camelContext);
        // on all addresses, as localhost may resolve to ::1
        serverSocket = (SSLServerSocket) serverContext.getServerSocketFactory().createServerSocket(0);
        executor = Executors.newSingleThreadExecutor();
    }

    @AfterEach
    public void stopServer() throws Exception {
        serverSocket.close();
        executor.shutdownNow();
        executor.awaitTermination(10, TimeUnit.SECONDS);
    }

    @Test
    public void testUnsetKeepsTheDefaultOfTheJdk() throws Exception {
        SSLContextParameters scp = createSSLContextParameters(new SSLContextClientParameters());
        SSLContext context = scp.createSSLContext(camelContext);

        try (SSLSocket socket = (SSLSocket) context.getSocketFactory().createSocket("127.0.0.1", port())) {
            assertNull(socket.getSSLParameters().getEndpointIdentificationAlgorithm());
            handshake(socket);
        }
    }

    @Test
    public void testHttpsVerifiesTheHostName() throws Exception {
        SSLContextClientParameters sccp = new SSLContextClientParameters();
        sccp.setEndpointIdentificationAlgorithm("HTTPS");
        SSLContext context = createSSLContextParameters(sccp).createSSLContext(camelContext);

        // the certificate is issued to localhost
        try (SSLSocket socket = (SSLSocket) context.getSocketFactory().createSocket("localhost", port())) {
            assertEquals("HTTPS", socket.getSSLParameters().getEndpointIdentificationAlgorithm());
            handshake(socket);
        }

        // and not to 127.0.0.1
        try (SSLSocket socket = (SSLSocket) context.getSocketFactory().createSocket("127.0.0.1", port())) {
            assertThrows(SSLHandshakeException.class, () -> handshake(socket));
        }
    }

    @Test
    public void testPropertyPlaceholder() throws Exception {
        Properties properties = new Properties();
        properties.setProperty("tls.identification", "HTTPS");
        CamelContext placeholderContext = createPropertiesPlaceholderAwareContext(properties);

        SSLContextClientParameters sccp = new SSLContextClientParameters();
        sccp.setEndpointIdentificationAlgorithm("{{tls.identification}}");
        SSLContextParameters scp = createSSLContextParameters(sccp);
        SSLContext context = scp.createSSLContext(placeholderContext);

        try (SSLSocket socket = (SSLSocket) context.getSocketFactory().createSocket()) {
            assertEquals("HTTPS", socket.getSSLParameters().getEndpointIdentificationAlgorithm());
        }
    }

    @Test
    public void testNotAppliedToEngines() throws Exception {
        SSLContextClientParameters sccp = new SSLContextClientParameters();
        sccp.setEndpointIdentificationAlgorithm("HTTPS");
        SSLContext context = createSSLContextParameters(sccp).createSSLContext(camelContext);

        // an engine starts in server mode, where the JDK may check the certificate of a client against its address
        SSLEngine engine = context.createSSLEngine();
        assertNull(engine.getSSLParameters().getEndpointIdentificationAlgorithm());
        assertTrue(sccp.toString().contains("getEndpointIdentificationAlgorithm()=HTTPS"), sccp.toString());
    }

    private SSLContextParameters createSSLContextParameters(SSLContextClientParameters clientParameters) {
        KeyStoreParameters ksp = new KeyStoreParameters();
        ksp.setResource("org/apache/camel/support/jsse/localhost.p12");
        ksp.setPassword("changeit");

        KeyManagersParameters kmp = new KeyManagersParameters();
        kmp.setKeyPassword("changeit");
        kmp.setKeyStore(ksp);

        // the client trusts the certificate of the server, so only the host name check can fail
        TrustManagersParameters tmp = new TrustManagersParameters();
        tmp.setKeyStore(ksp);

        SSLContextParameters scp = new SSLContextParameters();
        scp.setKeyManagers(kmp);
        scp.setTrustManagers(tmp);
        scp.setClientParameters(clientParameters);
        return scp;
    }

    private int port() {
        return serverSocket.getLocalPort();
    }

    /**
     * Runs the handshake of the client socket against one connection that the server accepts.
     */
    private void handshake(SSLSocket client) throws Exception {
        Future<?> server = executor.submit(() -> {
            try (SSLSocket accepted = (SSLSocket) serverSocket.accept()) {
                accepted.setSoTimeout(10000);
                accepted.startHandshake();
            }
            return null;
        });
        client.setSoTimeout(10000);
        try {
            client.startHandshake();
        } finally {
            client.close();
            try {
                server.get(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                // the server side fails too when the client rejects the certificate
            }
        }
    }
}
