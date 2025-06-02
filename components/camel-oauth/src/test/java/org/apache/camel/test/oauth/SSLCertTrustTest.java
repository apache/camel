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
import java.net.URL;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.TrustManagerFactory;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class SSLCertTrustTest extends AbstractKeycloakTest {

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
                var xtm = (javax.net.ssl.X509TrustManager) tm;
                xtm.checkServerTrusted(new X509Certificate[] { cert }, "RSA");
            }
        } catch (CertificateException ex) {
            System.err.println("Untrusted, because of: " + ex);
            return;
        }

        System.out.println("Trusted");
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
        Assertions.assertThrows(SSLHandshakeException.class, () -> connectToUrl(url), "Certificate should not be trusted");
    }

    private static void connectToUrl(String httpsUrl) throws IOException {
        var url = new URL(httpsUrl);
        var con = (HttpsURLConnection) url.openConnection();
        con.connect();
    }
}
