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
package org.apache.camel.component.cyberark.vault;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.Base64;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.Endpoint;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The verifySsl and certificatePath options, of an endpoint and of the vault configuration, against an HTTPS server
 * with a self-signed certificate for localhost (conjur-server.p12, and its certificate in conjur-server.pem).
 * <p>
 * The files were made with: {@code keytool -genkeypair -alias conjur -keyalg EC -groupname secp256r1 -validity 36500
 * -dname CN=localhost -ext SAN=dns:localhost -keystore conjur-server.p12 -storetype PKCS12 -storepass changeit} and
 * {@code keytool -exportcert -rfc -alias conjur -keystore conjur-server.p12 -storepass changeit -file conjur-server.pem}
 */
class CyberArkVaultSslTest extends CamelTestSupport {

    private static final String SSL_RESOURCES = "/org/apache/camel/component/cyberark/vault/ssl/";

    @TempDir
    Path tempDir;

    private HttpsServer server;
    private String certificatePath;

    @BeforeEach
    void startConjur() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream in = getClass().getResourceAsStream(SSL_RESOURCES + "conjur-server.p12")) {
            keyStore.load(in, "changeit".toCharArray());
        }
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStore, "changeit".toCharArray());
        SSLContext sslContext = SSLContext.getInstance("TLSv1.3");
        sslContext.init(keyManagerFactory.getKeyManagers(), null, null);

        server = HttpsServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(sslContext));
        server.createContext("/secrets/", exchange -> {
            byte[] body = "secret-value".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();

        certificatePath = Path.of(getClass().getResource(SSL_RESOURCES + "conjur-server.pem").toURI()).toString();
    }

    @AfterEach
    void stopConjur() {
        server.stop(0);
    }

    @Test
    void defaultTrustRejectsTheServer() {
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> getSecret("localhost", ""));
        assertSslFailure(e);
    }

    @Test
    void certificatePathTrustsTheServer() {
        assertEquals("secret-value", getSecret("localhost", "&certificatePath=RAW(" + certificatePath + ")"));
    }

    @Test
    void certificatePathStillChecksTheHostName() {
        // the certificate is for localhost only
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> getSecret("127.0.0.1", "&certificatePath=RAW(" + certificatePath + ")"));
        assertSslFailure(e);
    }

    @Test
    void certificatePathTrustsOnlyItsCertificates() throws Exception {
        String otherCertificatePath = writeAnotherCertificate();

        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> getSecret("localhost", "&certificatePath=RAW(" + otherCertificatePath + ")"));
        assertSslFailure(e);
    }

    @Test
    void verifySslFalseTrustsAnyServer() {
        // neither the certificate nor the host name is checked
        assertEquals("secret-value", getSecret("127.0.0.1", "&verifySsl=false"));
    }

    @Test
    void missingCertificateFileFailsAtStart() throws Exception {
        Endpoint endpoint = context.getComponent("cyberark-vault").createEndpoint(
                "cyberark-vault:secret?secretId=db/password&url=https://localhost:1&account=myaccount&authToken=my-token"
                                                                                  + "&certificatePath=RAW("
                                                                                  + tempDir.resolve("missing.pem") + ")");

        RuntimeCamelException e = assertThrows(RuntimeCamelException.class, endpoint::start);
        assertInstanceOf(NoSuchFileException.class, e.getCause());
    }

    @Test
    void propertiesFunctionUsesTheCertificatePathOfTheVaultConfiguration() throws Exception {
        var vault = context.getVaultConfiguration().cyberark();
        vault.setCertificatePath(certificatePath);

        assertEquals("secret-value", resolveWithPropertiesFunction("localhost"));
    }

    @Test
    void propertiesFunctionUsesTheVerifySslOfTheVaultConfiguration() throws Exception {
        var vault = context.getVaultConfiguration().cyberark();
        vault.setVerifySsl(false);

        assertEquals("secret-value", resolveWithPropertiesFunction("127.0.0.1"));
    }

    private String getSecret(String host, String options) {
        return template.requestBody(
                "cyberark-vault:secret?secretId=db/password&url=" + conjurUrl(host)
                                    + "&account=myaccount&authToken=my-token" + options,
                null, String.class);
    }

    private String resolveWithPropertiesFunction(String host) throws Exception {
        var vault = context.getVaultConfiguration().cyberark();
        vault.setUrl(conjurUrl(host));
        vault.setAccount("myaccount");
        vault.setAuthToken("my-token");

        CyberArkVaultPropertiesFunction function = new CyberArkVaultPropertiesFunction();
        function.setCamelContext(context);
        function.start();
        try {
            return function.apply("db/password");
        } finally {
            function.stop();
        }
    }

    private String conjurUrl(String host) {
        return "https://" + host + ":" + server.getAddress().getPort();
    }

    /**
     * Writes a certificate trusted by the JVM, which is not the certificate of the server, to a PEM file.
     */
    private String writeAnotherCertificate() throws Exception {
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init((KeyStore) null);
        X509TrustManager trustManager = (X509TrustManager) trustManagerFactory.getTrustManagers()[0];
        X509Certificate other = trustManager.getAcceptedIssuers()[0];

        Path file = tempDir.resolve("other.pem");
        Files.writeString(file, "-----BEGIN CERTIFICATE-----\n"
                                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                                        .encodeToString(other.getEncoded())
                                + "\n-----END CERTIFICATE-----\n");
        return file.toString();
    }

    private static void assertSslFailure(Exception e) {
        Throwable cause = e;
        while (cause != null && !(cause instanceof SSLException)) {
            cause = cause.getCause();
        }
        assertTrue(cause instanceof SSLException, "Expected an SSL failure, but was: " + e);
    }
}
