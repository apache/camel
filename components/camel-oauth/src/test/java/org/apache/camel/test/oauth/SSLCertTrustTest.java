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

import java.io.IOException;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLHandshakeException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class SSLCertTrustTest extends AbstractKeycloakTest {

    @Test
    void testTrustedCertificate() {
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
