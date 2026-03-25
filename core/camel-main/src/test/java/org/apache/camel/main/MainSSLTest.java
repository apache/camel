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
package org.apache.camel.main;

import java.security.KeyStore;
import java.util.List;

import javax.net.ssl.SSLContext;

import org.apache.camel.CamelContext;
import org.apache.camel.support.jsse.ClientAuthentication;
import org.apache.camel.support.jsse.FilterParameters;
import org.apache.camel.support.jsse.KeyManagersParameters;
import org.apache.camel.support.jsse.KeyStoreParameters;
import org.apache.camel.support.jsse.NamedGroupsParameters;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.support.jsse.SSLContextServerParameters;
import org.apache.camel.support.jsse.SignatureSchemesParameters;
import org.apache.camel.support.jsse.TrustAllTrustManager;
import org.apache.camel.support.jsse.TrustManagersParameters;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class MainSSLTest {

    @Test
    public void testMainSSLParameters() {
        Main main = new Main();

        main.addInitialProperty("camel.ssl.enabled", "true");
        main.addInitialProperty("camel.ssl.keyStore", "server.jks");
        main.addInitialProperty("camel.ssl.keystorePassword", "security");
        main.addInitialProperty("camel.ssl.trustStore", "client.jks");
        main.addInitialProperty("camel.ssl.trustStorePassword", "storepass");
        main.addInitialProperty("camel.ssl.clientAuthentication", "REQUIRE");

        main.start();

        CamelContext context = main.getCamelContext();
        assertNotNull(context);

        SSLContextParameters sslParams = context.getSSLContextParameters();
        assertNotNull(sslParams);

        KeyManagersParameters kmp = sslParams.getKeyManagers();
        assertNotNull(kmp);

        Assertions.assertEquals("security", kmp.getKeyPassword());

        KeyStoreParameters ksp = kmp.getKeyStore();
        assertNotNull(ksp);

        Assertions.assertEquals("server.jks", ksp.getResource());
        Assertions.assertEquals("security", ksp.getPassword());

        TrustManagersParameters tmp = sslParams.getTrustManagers();
        assertNotNull(tmp);

        KeyStoreParameters tsp = tmp.getKeyStore();
        Assertions.assertEquals("client.jks", tsp.getResource());
        Assertions.assertEquals("storepass", tsp.getPassword());

        SSLContextServerParameters scsp = sslParams.getServerParameters();
        assertNotNull(scsp);

        Assertions.assertEquals(ClientAuthentication.REQUIRE.name(), scsp.getClientAuthentication());

        main.stop();
    }

    @Test
    public void testMainSSLParametersFluent() {
        Main main = new Main();

        main.configure().sslConfig()
                .withEnabled(true)
                .withKeyStore("server.jks")
                .withKeystorePassword("security")
                .withTrustStore("client.jks")
                .withTrustStorePassword("storepass")
                .withClientAuthentication("REQUIRE");

        main.start();

        CamelContext context = main.getCamelContext();
        assertNotNull(context);

        SSLContextParameters sslParams = context.getSSLContextParameters();
        assertNotNull(sslParams);

        KeyManagersParameters kmp = sslParams.getKeyManagers();
        assertNotNull(kmp);

        Assertions.assertEquals("security", kmp.getKeyPassword());

        KeyStoreParameters ksp = kmp.getKeyStore();
        assertNotNull(ksp);

        Assertions.assertEquals("server.jks", ksp.getResource());
        Assertions.assertEquals("security", ksp.getPassword());

        TrustManagersParameters tmp = sslParams.getTrustManagers();
        assertNotNull(tmp);

        KeyStoreParameters tsp = tmp.getKeyStore();
        Assertions.assertEquals("client.jks", tsp.getResource());
        Assertions.assertEquals("storepass", tsp.getPassword());

        SSLContextServerParameters scsp = sslParams.getServerParameters();
        assertNotNull(scsp);

        Assertions.assertEquals(ClientAuthentication.REQUIRE.name(), scsp.getClientAuthentication());

        main.stop();
    }

    @Test
    public void testMainSSLTrustAll() {
        Main main = new Main();

        main.addInitialProperty("camel.ssl.enabled", "true");
        main.addInitialProperty("camel.ssl.selfSigned", "true");
        main.addInitialProperty("camel.ssl.trustAllCertificates", "true");

        main.start();

        CamelContext context = main.getCamelContext();
        SSLContextParameters sslParams = context.getSSLContextParameters();
        TrustManagersParameters tmp = sslParams.getTrustManagers();
        Assertions.assertEquals(tmp.getTrustManager(), TrustAllTrustManager.INSTANCE);

        main.stop();
    }

    @Test
    public void testMainSSLTrustAllFluent() {
        Main main = new Main();

        main.configure().sslConfig()
                .withEnabled(true)
                .withSelfSigned(true)
                .withTrustAllCertificates(true);

        main.start();

        CamelContext context = main.getCamelContext();
        SSLContextParameters sslParams = context.getSSLContextParameters();
        TrustManagersParameters tmp = sslParams.getTrustManagers();
        Assertions.assertEquals(tmp.getTrustManager(), TrustAllTrustManager.INSTANCE);

        main.stop();
    }

    @Test
    public void testMainSSLNamedGroups() {
        Main main = new Main();

        main.addInitialProperty("camel.ssl.enabled", "true");
        main.addInitialProperty("camel.ssl.keyStore", "server.jks");
        main.addInitialProperty("camel.ssl.keystorePassword", "security");
        main.addInitialProperty("camel.ssl.namedGroups", "X25519MLKEM768,X25519,secp256r1");

        main.start();

        CamelContext context = main.getCamelContext();
        assertNotNull(context);

        SSLContextParameters sslParams = context.getSSLContextParameters();
        assertNotNull(sslParams);

        NamedGroupsParameters ngp = sslParams.getNamedGroups();
        assertNotNull(ngp);

        List<String> groups = ngp.getNamedGroup();
        Assertions.assertEquals(3, groups.size());
        Assertions.assertEquals("X25519MLKEM768", groups.get(0));
        Assertions.assertEquals("X25519", groups.get(1));
        Assertions.assertEquals("secp256r1", groups.get(2));

        // when explicit named groups are set, filter should be null
        assertNull(sslParams.getNamedGroupsFilter());

        main.stop();
    }

    @Test
    public void testMainSSLNamedGroupsFluent() {
        Main main = new Main();

        main.configure().sslConfig()
                .withEnabled(true)
                .withKeyStore("server.jks")
                .withKeystorePassword("security")
                .withNamedGroups("X25519MLKEM768,X25519,secp256r1,secp384r1");

        main.start();

        CamelContext context = main.getCamelContext();
        assertNotNull(context);

        SSLContextParameters sslParams = context.getSSLContextParameters();
        assertNotNull(sslParams);

        NamedGroupsParameters ngp = sslParams.getNamedGroups();
        assertNotNull(ngp);

        List<String> groups = ngp.getNamedGroup();
        Assertions.assertEquals(4, groups.size());
        Assertions.assertEquals("X25519MLKEM768", groups.get(0));
        Assertions.assertEquals("secp384r1", groups.get(3));

        main.stop();
    }

    @Test
    public void testMainSSLNamedGroupsFilter() {
        Main main = new Main();

        main.addInitialProperty("camel.ssl.enabled", "true");
        main.addInitialProperty("camel.ssl.keyStore", "server.jks");
        main.addInitialProperty("camel.ssl.keystorePassword", "security");
        main.addInitialProperty("camel.ssl.namedGroupsInclude", "X25519.*,secp.*");
        main.addInitialProperty("camel.ssl.namedGroupsExclude", "secp521r1");

        main.start();

        CamelContext context = main.getCamelContext();
        assertNotNull(context);

        SSLContextParameters sslParams = context.getSSLContextParameters();
        assertNotNull(sslParams);

        // when filters are used, explicit named groups should be null
        assertNull(sslParams.getNamedGroups());

        FilterParameters fp = sslParams.getNamedGroupsFilter();
        assertNotNull(fp);

        List<String> includes = fp.getInclude();
        Assertions.assertEquals(2, includes.size());
        Assertions.assertEquals("X25519.*", includes.get(0));
        Assertions.assertEquals("secp.*", includes.get(1));

        List<String> excludes = fp.getExclude();
        Assertions.assertEquals(1, excludes.size());
        Assertions.assertEquals("secp521r1", excludes.get(0));

        main.stop();
    }

    @Test
    public void testMainSSLNamedGroupsFilterFluent() {
        Main main = new Main();

        main.configure().sslConfig()
                .withEnabled(true)
                .withKeyStore("server.jks")
                .withKeystorePassword("security")
                .withNamedGroupsInclude("X25519.*")
                .withNamedGroupsExclude("secp521r1");

        main.start();

        CamelContext context = main.getCamelContext();
        assertNotNull(context);

        SSLContextParameters sslParams = context.getSSLContextParameters();
        assertNotNull(sslParams);

        assertNull(sslParams.getNamedGroups());

        FilterParameters fp = sslParams.getNamedGroupsFilter();
        assertNotNull(fp);

        Assertions.assertEquals(1, fp.getInclude().size());
        Assertions.assertEquals("X25519.*", fp.getInclude().get(0));
        Assertions.assertEquals(1, fp.getExclude().size());
        Assertions.assertEquals("secp521r1", fp.getExclude().get(0));

        main.stop();
    }

    @Test
    public void testMainSSLSignatureSchemes() {
        Main main = new Main();

        main.addInitialProperty("camel.ssl.enabled", "true");
        main.addInitialProperty("camel.ssl.keyStore", "server.jks");
        main.addInitialProperty("camel.ssl.keystorePassword", "security");
        main.addInitialProperty("camel.ssl.signatureSchemes", "ed25519,rsa_pss_rsae_sha256,ecdsa_secp256r1_sha256");

        main.start();

        CamelContext context = main.getCamelContext();
        assertNotNull(context);

        SSLContextParameters sslParams = context.getSSLContextParameters();
        assertNotNull(sslParams);

        SignatureSchemesParameters ssp = sslParams.getSignatureSchemes();
        assertNotNull(ssp);

        List<String> schemes = ssp.getSignatureScheme();
        Assertions.assertEquals(3, schemes.size());
        Assertions.assertEquals("ed25519", schemes.get(0));
        Assertions.assertEquals("rsa_pss_rsae_sha256", schemes.get(1));
        Assertions.assertEquals("ecdsa_secp256r1_sha256", schemes.get(2));

        assertNull(sslParams.getSignatureSchemesFilter());

        main.stop();
    }

    @Test
    public void testMainSSLSignatureSchemesFluent() {
        Main main = new Main();

        main.configure().sslConfig()
                .withEnabled(true)
                .withKeyStore("server.jks")
                .withKeystorePassword("security")
                .withSignatureSchemes("ed25519,rsa_pss_rsae_sha256");

        main.start();

        CamelContext context = main.getCamelContext();
        assertNotNull(context);

        SSLContextParameters sslParams = context.getSSLContextParameters();
        assertNotNull(sslParams);

        SignatureSchemesParameters ssp = sslParams.getSignatureSchemes();
        assertNotNull(ssp);

        List<String> schemes = ssp.getSignatureScheme();
        Assertions.assertEquals(2, schemes.size());
        Assertions.assertEquals("ed25519", schemes.get(0));
        Assertions.assertEquals("rsa_pss_rsae_sha256", schemes.get(1));

        main.stop();
    }

    @Test
    public void testMainSSLSignatureSchemesFilter() {
        Main main = new Main();

        main.addInitialProperty("camel.ssl.enabled", "true");
        main.addInitialProperty("camel.ssl.keyStore", "server.jks");
        main.addInitialProperty("camel.ssl.keystorePassword", "security");
        main.addInitialProperty("camel.ssl.signatureSchemesInclude", "ecdsa_.*,ed.*");
        main.addInitialProperty("camel.ssl.signatureSchemesExclude", "ed448");

        main.start();

        CamelContext context = main.getCamelContext();
        assertNotNull(context);

        SSLContextParameters sslParams = context.getSSLContextParameters();
        assertNotNull(sslParams);

        assertNull(sslParams.getSignatureSchemes());

        FilterParameters fp = sslParams.getSignatureSchemesFilter();
        assertNotNull(fp);

        List<String> includes = fp.getInclude();
        Assertions.assertEquals(2, includes.size());
        Assertions.assertEquals("ecdsa_.*", includes.get(0));
        Assertions.assertEquals("ed.*", includes.get(1));

        List<String> excludes = fp.getExclude();
        Assertions.assertEquals(1, excludes.size());
        Assertions.assertEquals("ed448", excludes.get(0));

        main.stop();
    }

    @Test
    public void testMainSSLSelfSigned() {
        Main main = new Main();

        // enabling SSL with selfSigned should generate a self-signed certificate
        main.addInitialProperty("camel.ssl.enabled", "true");
        main.addInitialProperty("camel.ssl.selfSigned", "true");

        main.start();

        CamelContext context = main.getCamelContext();
        assertNotNull(context);

        SSLContextParameters sslParams = context.getSSLContextParameters();
        assertNotNull(sslParams);

        // should have key managers with a self-signed certificate
        KeyManagersParameters kmp = sslParams.getKeyManagers();
        assertNotNull(kmp);

        KeyStoreParameters ksp = kmp.getKeyStore();
        assertNotNull(ksp);
        // the keystore should be set directly (not via resource)
        assertNull(ksp.getResource());

        // verify that an SSLContext can be created from the parameters
        try {
            SSLContext sslContext = sslParams.createSSLContext(context);
            assertNotNull(sslContext);
        } catch (Exception e) {
            Assertions.fail("Should be able to create SSLContext from self-signed certificate: " + e.getMessage());
        }

        main.stop();
    }

    @Test
    public void testMainSSLSelfSignedFluent() {
        Main main = new Main();

        main.configure().sslConfig()
                .withEnabled(true)
                .withSelfSigned(true);

        main.start();

        CamelContext context = main.getCamelContext();
        assertNotNull(context);

        SSLContextParameters sslParams = context.getSSLContextParameters();
        assertNotNull(sslParams);
        assertNotNull(sslParams.getKeyManagers());

        main.stop();
    }

    @Test
    public void testSelfSignedCertificateGenerator() throws Exception {
        KeyStore ks = SelfSignedCertificateGenerator.generateKeyStore("test-password");
        assertNotNull(ks);
        Assertions.assertTrue(ks.containsAlias("camel-self-signed"));
        assertNotNull(ks.getKey("camel-self-signed", "test-password".toCharArray()));

        java.security.cert.X509Certificate cert
                = (java.security.cert.X509Certificate) ks.getCertificate("camel-self-signed");
        assertNotNull(cert);

        // verify the certificate has a SAN extension with localhost
        java.util.Collection<java.util.List<?>> sans = cert.getSubjectAlternativeNames();
        assertNotNull(sans);
        // should have DNS:localhost and IP:127.0.0.1
        Assertions.assertTrue(sans.size() >= 2);
    }

    @Test
    public void testMainSSLSignatureSchemesFilterFluent() {
        Main main = new Main();

        main.configure().sslConfig()
                .withEnabled(true)
                .withKeyStore("server.jks")
                .withKeystorePassword("security")
                .withSignatureSchemesInclude("ecdsa_.*")
                .withSignatureSchemesExclude("ed448");

        main.start();

        CamelContext context = main.getCamelContext();
        assertNotNull(context);

        SSLContextParameters sslParams = context.getSSLContextParameters();
        assertNotNull(sslParams);

        assertNull(sslParams.getSignatureSchemes());

        FilterParameters fp = sslParams.getSignatureSchemesFilter();
        assertNotNull(fp);

        Assertions.assertEquals(1, fp.getInclude().size());
        Assertions.assertEquals("ecdsa_.*", fp.getInclude().get(0));
        Assertions.assertEquals(1, fp.getExclude().size());
        Assertions.assertEquals("ed448", fp.getExclude().get(0));

        main.stop();
    }
}
