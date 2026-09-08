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
import java.net.ConnectException;
import java.net.URI;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class SSLCertTrustTest extends AbstractKeycloakTest {

    private static final Logger LOG = LoggerFactory.getLogger(SSLCertTrustTest.class);

    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 10_000;

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
        String url = "https://untrusted-root.badssl.com"; // Example of an untrusted cert
        try {
            connectToUrl(url);
            Assertions.fail("Certificate should not be trusted");
        } catch (SSLHandshakeException e) {
            // Expected: untrusted certificate
        } catch (ConnectException e) {
            // External site unreachable (e.g. CI network restrictions) — skip test
            Assumptions.assumeTrue(false, "External site unreachable: " + e.getMessage());
        } catch (IOException e) {
            // Other network errors (timeout, etc.) — skip test rather than fail
            Assumptions.assumeTrue(false, "External site unreachable: " + e.getMessage());
        }
    }

    private static void connectToUrl(String httpsUrl) throws IOException {
        var url = URI.create(httpsUrl).toURL();
        var con = (HttpsURLConnection) url.openConnection();
        con.setConnectTimeout(CONNECT_TIMEOUT_MS);
        con.setReadTimeout(READ_TIMEOUT_MS);
        con.connect();
    }
}
