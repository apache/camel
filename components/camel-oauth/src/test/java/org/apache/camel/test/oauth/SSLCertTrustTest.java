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
package org.apache.camel.test.oauth;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class SSLCertTrustTest extends AbstractKeycloakTest {

    private static final Logger LOG = LoggerFactory.getLogger(SSLCertTrustTest.class);

    /** PKCS12 keystore containing a self-signed certificate not in any default trust store. */
    private static final String SELFSIGNED_KEYSTORE = "selfsigned-keystore.p12";
    private static final String KEYSTORE_PASSWORD = "changeit";
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 10_000;

    private static SSLServerSocket serverSocket;
    private static Thread serverThread;
    private static int localHttpsPort;

    @BeforeAll
    static void startLocalHttpsServer() throws Exception {
        // Load the self-signed keystore from test resources
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (InputStream is = SSLCertTrustTest.class.getClassLoader().getResourceAsStream(SELFSIGNED_KEYSTORE)) {
            Assertions.assertNotNull(is, "Test keystore not found on classpath: " + SELFSIGNED_KEYSTORE);
            ks.load(is, KEYSTORE_PASSWORD.toCharArray());
        }

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, KEYSTORE_PASSWORD.toCharArray());

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), null, null);

        serverSocket = (SSLServerSocket) sslContext.getServerSocketFactory().createServerSocket(0);
        localHttpsPort = serverSocket.getLocalPort();

        // Accept connections in a daemon thread — just complete TLS handshake and respond
        serverThread = new Thread(() -> {
            while (!serverSocket.isClosed()) {
                try (var socket = serverSocket.accept()) {
                    // Read enough to satisfy the HTTP request, then send a minimal response
                    socket.getInputStream().read(new byte[1]);
                    socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n"
                            .getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                    socket.getOutputStream().flush();
                } catch (IOException e) {
                    if (!serverSocket.isClosed()) {
                        LOG.debug("Server accept error", e);
                    }
                }
            }
        }, "ssl-test-server");
        serverThread.setDaemon(true);
        serverThread.start();

        LOG.info("Started local HTTPS server with self-signed cert on port {}", localHttpsPort);
    }

    @AfterAll
    static void stopLocalHttpsServer() throws Exception {
        if (serverSocket != null && !serverSocket.isClosed()) {
            serverSocket.close();
        }
        if (serverThread != null) {
            serverThread.join(5000);
        }
    }

    @Test
    void testCheckClusterCertificateTrust() throws Exception {

        // Load certificate to check
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        FileInputStream fis = new FileInputStream("helm/etc/cluster.crt");
        X509Certificate cert = (X509Certificate) cf.generateCertificate(fis);

        // Load default Java truststore
        FileInputStream trustStream = new FileInputStream(System.getProperty("java.home") + "/lib/security/cacerts");
        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(trustStream, "changeit".toCharArray());

        // Initialize TrustManager
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);

        try {
            for (var tm : tmf.getTrustManagers()) {
                var xtm = (X509TrustManager) tm;
                xtm.checkServerTrusted(new X509Certificate[] { cert }, "RSA");
            }
        } catch (CertificateException ex) {
            LOG.error("Untrusted, because of: ", ex);
            return;
        }

        LOG.info("Trusted");
    }

    @Test
    void testCheckKeycloakCertificateTrust() {
        var admin = new KeycloakAdmin(new KeycloakAdmin.AdminParams(KEYCLOAK_BASE_URL));
        Assumptions.assumeTrue(admin.isKeycloakRunning(), "Keycloak is not running");
        Assertions.assertDoesNotThrow(() -> connectToUrl(KEYCLOAK_BASE_URL), "Certificate should be trusted");
    }

    @Test
    void testUntrustedCertificate() {
        // Connect to local HTTPS server whose self-signed cert is NOT in the default trust store
        String url = "https://localhost:" + localHttpsPort;
        Assertions.assertThrows(SSLHandshakeException.class, () -> connectToUrl(url),
                "Certificate should not be trusted");
    }

    private static void connectToUrl(String httpsUrl) throws IOException {
        var url = URI.create(httpsUrl).toURL();
        var con = (HttpsURLConnection) url.openConnection();
        con.setConnectTimeout(CONNECT_TIMEOUT_MS);
        con.setReadTimeout(READ_TIMEOUT_MS);
        con.connect();
    }
}
