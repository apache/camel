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
package org.apache.camel.component.hashicorp.vault;

import java.nio.file.Path;
import java.util.Map;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.X509KeyManager;

import org.apache.camel.CamelContext;
import org.apache.camel.component.hashicorp.vault.FakeVault.Request;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.vault.support.VaultResponse;

import static org.apache.camel.component.hashicorp.vault.FakeVault.tlsFile;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The trust store, the key store and the cert login, against a fake Vault over HTTPS.
 * <p/>
 * The TLS material in tls/ was made with OpenSSL, valid for 100 years: a CA (ca.pem, also in the PKCS12 trust store
 * truststore.p12), the certificate of localhost (server.p12), and the client certificate camel-client with its RSA key
 * in PKCS#8 (client.pem), in PKCS#1 (client-rsa.pem) and in PKCS12 (client.p12), and a self-signed certificate with an
 * EC key in PKCS#8 (client-ec.pem). The password of the PKCS12 stores is changeit.
 */
class HashicorpVaultTlsTest {

    private FakeVault vault;
    private CamelContext context;
    private HashicorpVaultClient client;

    @BeforeEach
    void startVault() throws Exception {
        vault = FakeVault.https();
        vault.on("secret/data/api", request -> FakeVault.secret("pw"));
        vault.on("auth/token/revoke-self", request -> FakeVault.noContent());
        context = new DefaultCamelContext();
        context.start();
    }

    @AfterEach
    void stopVault() {
        if (client != null) {
            client.close();
        }
        context.stop();
        vault.close();
    }

    @Test
    void trustsVaultWithPemCertificates() {
        HashicorpVaultClientSettings settings = token();
        settings.setTrustStore(tlsFile("ca.pem"));
        client = HashicorpVaultClient.create(context, this, settings);

        assertEquals("pw", readPassword());
        assertNull(vault.requests("secret/data/api").get(0).clientCertificate(), "no key store, no client certificate");
    }

    @Test
    void trustsVaultWithAPkcs12TrustStore() {
        HashicorpVaultClientSettings settings = token();
        settings.setTrustStore(tlsFile("truststore.p12"));
        settings.setTrustStorePassword("changeit");
        client = HashicorpVaultClient.create(context, this, settings);

        assertEquals("pw", readPassword());
    }

    @Test
    void doesNotTrustVaultWithoutTheTrustStore() {
        client = HashicorpVaultClient.create(context, this, token());

        Exception e = assertThrows(Exception.class, this::readPassword);
        assertTrue(hasCause(e, SSLHandshakeException.class), "the JVM does not trust the test CA: " + e);
    }

    @Test
    void certLoginPresentsTheClientCertificateOfAPemKeyStore() {
        vault.on("auth/cert/login", request -> FakeVault.login("cert-token", 3600));
        HashicorpVaultClientSettings settings = cert("client.pem");
        settings.setRole("camel");
        client = HashicorpVaultClient.create(context, this, settings);

        assertEquals("pw", readPassword());

        Request login = vault.requests("auth/cert/login").get(0);
        assertEquals("CN=camel-client", login.clientCertificate());
        assertEquals(Map.of("name", "camel"), login.json());
        assertEquals("cert-token", vault.requests("secret/data/api").get(0).token());
    }

    @Test
    void certLoginWithAnRsaKeyInPkcs1AtTheMountPath() {
        vault.on("auth/my-cert/login", request -> FakeVault.login("cert-token", 3600));
        HashicorpVaultClientSettings settings = cert("client-rsa.pem");
        settings.setAuthMountPath("my-cert");
        client = HashicorpVaultClient.create(context, this, settings);

        assertEquals("pw", readPassword());

        Request login = vault.requests("auth/my-cert/login").get(0);
        assertEquals("CN=camel-client", login.clientCertificate());
        // no role: Vault picks the role whose CA signed the certificate
        assertEquals(Map.of(), login.json());
    }

    @Test
    void certLoginWithAPkcs12KeyStore() {
        vault.on("auth/cert/login", request -> FakeVault.login("cert-token", 3600));
        HashicorpVaultClientSettings settings = cert("client.p12");
        settings.setKeyStorePassword("changeit");
        client = HashicorpVaultClient.create(context, this, settings);

        assertEquals("pw", readPassword());
        assertEquals("CN=camel-client", vault.requests("auth/cert/login").get(0).clientCertificate());
    }

    @Test
    void validatesTheSettingsOfTheCertLogin() {
        HashicorpVaultClientSettings settings = token();
        settings.setAuthentication("cert");
        IllegalArgumentException e
                = assertThrows(IllegalArgumentException.class, () -> HashicorpVaultClient.validate(settings));
        assertEquals("The cert login needs a key store with the client certificate", e.getMessage());

        settings.setKeyStore(tlsFile("client.pem"));
        settings.setScheme("http");
        e = assertThrows(IllegalArgumentException.class, () -> HashicorpVaultClient.validate(settings));
        assertEquals("The cert login needs the https scheme", e.getMessage());
    }

    @Test
    void readsAnEcKeyInPkcs8() {
        KeyManager[] managers = HashicorpVaultTls.keyManagers(tlsFile("client-ec.pem"), null);

        X509KeyManager manager = (X509KeyManager) managers[0];
        assertEquals("EC", manager.getPrivateKey("client").getAlgorithm());
        assertEquals("CN=camel-client-ec", manager.getCertificateChain("client")[0].getSubjectX500Principal().getName());
    }

    @Test
    void reportsATrustStoreThatReadsAsEmptyWithoutItsPassword() {
        HashicorpVaultClientSettings settings = token();
        // the certificates of a PKCS12 trust store are encrypted
        settings.setTrustStore(tlsFile("truststore.p12"));

        IllegalArgumentException e
                = assertThrows(IllegalArgumentException.class, () -> HashicorpVaultClient.create(context, this, settings));
        assertTrue(e.getMessage().endsWith("no certificate in the trust store, which may need its trustStorePassword"),
                e.getMessage());
    }

    @Test
    void failsWhenAStoreCannotBeRead() {
        HashicorpVaultClientSettings settings = token();
        settings.setTrustStore(tlsFile("truststore.p12"));
        settings.setTrustStorePassword("wrong");
        IllegalArgumentException e
                = assertThrows(IllegalArgumentException.class, () -> HashicorpVaultClient.create(context, this, settings));
        assertTrue(e.getMessage().startsWith("Cannot read the trust store "), e.getMessage());

        HashicorpVaultClientSettings missing = cert("client.pem");
        missing.setKeyStore(Path.of(tlsFile("client.pem")).resolveSibling("missing.pem").toString());
        e = assertThrows(IllegalArgumentException.class, () -> HashicorpVaultClient.create(context, this, missing));
        assertTrue(e.getMessage().startsWith("Cannot read the key store "), e.getMessage());

        HashicorpVaultClientSettings noKey = cert("ca.pem");
        e = assertThrows(IllegalArgumentException.class, () -> HashicorpVaultClient.create(context, this, noKey));
        assertTrue(e.getMessage().contains("no private key"), e.getMessage());
    }

    private HashicorpVaultClientSettings token() {
        HashicorpVaultClientSettings settings = new HashicorpVaultClientSettings();
        settings.setHost("localhost");
        settings.setPort(String.valueOf(vault.port()));
        settings.setScheme("https");
        settings.setToken("my-token");
        return settings;
    }

    private HashicorpVaultClientSettings cert(String keyStore) {
        HashicorpVaultClientSettings settings = token();
        settings.setToken(null);
        settings.setAuthentication("cert");
        settings.setTrustStore(tlsFile("ca.pem"));
        settings.setKeyStore(tlsFile(keyStore));
        return settings;
    }

    private String readPassword() {
        VaultResponse response = client.getVaultTemplate().read("secret/data/api");
        return ((Map<?, ?>) response.getData().get("data")).get("password").toString();
    }

    private static boolean hasCause(Throwable e, Class<? extends Throwable> type) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (type.isInstance(cause)) {
                return true;
            }
        }
        return false;
    }
}
