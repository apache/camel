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
package org.apache.camel.component.milo;

import java.security.cert.X509Certificate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link KeyStoreLoader} certificate chain loading functionality.
 */
public class KeyStoreLoaderTest {

    @Test
    public void testLoadCertificateChain() throws Exception {
        final KeyStoreLoader loader = new KeyStoreLoader();
        loader.setUrl("file:src/test/resources/keystore");
        loader.setKeyStorePassword("testtest");
        loader.setKeyPassword("test");

        final KeyStoreLoader.Result result = loader.load();

        assertNotNull(result, "Result should not be null");
        assertNotNull(result.getCertificate(), "Certificate should not be null");
        assertNotNull(result.getCertificateChain(), "Certificate chain should not be null");
        assertNotNull(result.getKeyPair(), "KeyPair should not be null");

        // The certificate chain should contain at least the end-entity certificate
        assertTrue(result.getCertificateChain().length >= 1,
                "Certificate chain should contain at least one certificate");

        // The first certificate in the chain should be the same as getCertificate()
        assertEquals(result.getCertificate(), result.getCertificateChain()[0],
                "First certificate in chain should match getCertificate()");
    }

    @Test
    public void testLoadCertificateChainWithAlias() throws Exception {
        final KeyStoreLoader loader = new KeyStoreLoader();
        loader.setUrl("file:src/test/resources/keystore");
        loader.setKeyStorePassword("testtest");
        loader.setKeyPassword("test");
        loader.setKeyAlias("test");

        final KeyStoreLoader.Result result = loader.load();

        assertNotNull(result, "Result should not be null");
        assertNotNull(result.getCertificateChain(), "Certificate chain should not be null");

        // Verify the chain is properly loaded
        X509Certificate[] chain = result.getCertificateChain();
        assertTrue(chain.length >= 1, "Certificate chain should have at least one certificate");

        // Verify all certificates in the chain are valid X509Certificates
        for (X509Certificate cert : chain) {
            assertNotNull(cert, "Each certificate in chain should not be null");
            assertNotNull(cert.getSubjectX500Principal(), "Certificate should have a subject");
        }
    }

    @Test
    public void testCertificateChainOrder() throws Exception {
        final KeyStoreLoader loader = new KeyStoreLoader();
        loader.setUrl("file:src/test/resources/keystore");
        loader.setKeyStorePassword("testtest");
        loader.setKeyPassword("test");

        final KeyStoreLoader.Result result = loader.load();

        assertNotNull(result, "Result should not be null");

        X509Certificate[] chain = result.getCertificateChain();

        // If the chain has more than one certificate, verify proper chain order
        // Each certificate should be issued by the next one in the chain
        if (chain.length > 1) {
            for (int i = 0; i < chain.length - 1; i++) {
                X509Certificate subject = chain[i];
                X509Certificate issuer = chain[i + 1];

                // The issuer of chain[i] should match the subject of chain[i+1]
                assertEquals(subject.getIssuerX500Principal(), issuer.getSubjectX500Principal(),
                        "Certificate at index " + i + " should be issued by certificate at index " + (i + 1));
            }
        }
    }
}
