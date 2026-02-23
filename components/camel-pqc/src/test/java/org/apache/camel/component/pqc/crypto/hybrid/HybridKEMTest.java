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
package org.apache.camel.component.pqc.crypto.hybrid;

import java.security.*;
import java.security.spec.ECGenParameterSpec;

import javax.crypto.KeyAgreement;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import org.bouncycastle.jcajce.spec.MLKEMParameterSpec;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HybridKEMTest {

    @BeforeAll
    static void setup() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        if (Security.getProvider(BouncyCastlePQCProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastlePQCProvider());
        }
    }

    @Test
    void testEncapsulateAndExtractWithX25519AndMLKEM() throws Exception {
        // Generate X25519 key pair (recipient)
        KeyPairGenerator x25519Kpg = KeyPairGenerator.getInstance("X25519", "BC");
        KeyPair x25519KeyPair = x25519Kpg.generateKeyPair();
        KeyAgreement x25519KA = KeyAgreement.getInstance("X25519", "BC");

        // Generate ML-KEM key pair (recipient)
        KeyPairGenerator mlkemKpg = KeyPairGenerator.getInstance("ML-KEM", "BC");
        mlkemKpg.initialize(MLKEMParameterSpec.ml_kem_768, new SecureRandom());
        KeyPair mlkemKeyPair = mlkemKpg.generateKeyPair();
        KeyGenerator mlkemKg = KeyGenerator.getInstance("ML-KEM", "BC");

        // Sender: Create encapsulation
        KeyAgreement senderKA = KeyAgreement.getInstance("X25519", "BC");
        KeyGenerator senderKg = KeyGenerator.getInstance("ML-KEM", "BC");

        HybridKEM.HybridEncapsulationResult encapResult = HybridKEM.encapsulate(
                x25519KeyPair.getPublic(),
                mlkemKeyPair.getPublic(),
                senderKA,
                senderKg,
                "AES",
                256,
                "HKDF-SHA256");

        assertNotNull(encapResult);
        assertNotNull(encapResult.encapsulation());
        assertNotNull(encapResult.sharedSecret());
        assertTrue(encapResult.encapsulation().length > 0);

        // Recipient: Extract shared secret
        SecretKey extractedSecret = HybridKEM.extract(
                encapResult.encapsulation(),
                x25519KeyPair.getPrivate(),
                mlkemKeyPair.getPrivate(),
                x25519KA,
                mlkemKg,
                "AES",
                256,
                "HKDF-SHA256");

        assertNotNull(extractedSecret);
        // The extracted secret should match the encapsulated secret
        assertArrayEquals(encapResult.sharedSecret().getEncoded(), extractedSecret.getEncoded());
    }

    @Test
    void testEncapsulateAndExtractWithECDHAndMLKEM() throws Exception {
        // Generate ECDH P-256 key pair (recipient)
        KeyPairGenerator ecKpg = KeyPairGenerator.getInstance("EC", "BC");
        ecKpg.initialize(new ECGenParameterSpec("secp256r1"), new SecureRandom());
        KeyPair ecKeyPair = ecKpg.generateKeyPair();
        KeyAgreement ecdhKA = KeyAgreement.getInstance("ECDH", "BC");

        // Generate ML-KEM key pair (recipient)
        KeyPairGenerator mlkemKpg = KeyPairGenerator.getInstance("ML-KEM", "BC");
        mlkemKpg.initialize(MLKEMParameterSpec.ml_kem_768, new SecureRandom());
        KeyPair mlkemKeyPair = mlkemKpg.generateKeyPair();
        KeyGenerator mlkemKg = KeyGenerator.getInstance("ML-KEM", "BC");

        // Sender: Create encapsulation
        KeyAgreement senderKA = KeyAgreement.getInstance("ECDH", "BC");
        KeyGenerator senderKg = KeyGenerator.getInstance("ML-KEM", "BC");

        HybridKEM.HybridEncapsulationResult encapResult = HybridKEM.encapsulate(
                ecKeyPair.getPublic(),
                mlkemKeyPair.getPublic(),
                senderKA,
                senderKg,
                "AES",
                128,
                "HKDF-SHA256");

        assertNotNull(encapResult);
        assertNotNull(encapResult.encapsulation());
        assertNotNull(encapResult.sharedSecret());

        // Recipient: Extract shared secret
        SecretKey extractedSecret = HybridKEM.extract(
                encapResult.encapsulation(),
                ecKeyPair.getPrivate(),
                mlkemKeyPair.getPrivate(),
                ecdhKA,
                mlkemKg,
                "AES",
                128,
                "HKDF-SHA256");

        assertNotNull(extractedSecret);
        assertArrayEquals(encapResult.sharedSecret().getEncoded(), extractedSecret.getEncoded());
    }

    @Test
    void testParseHybridEncapsulation() {
        byte[] classicalEncap = new byte[] { 1, 2, 3, 4, 5, 6, 7, 8 };
        byte[] pqcEncap = new byte[] { 10, 20, 30, 40, 50, 60, 70, 80, 90 };

        byte[] combined = HybridKEM.combineEncapsulations(classicalEncap, pqcEncap);

        HybridKEM.HybridEncapsulationComponents components = HybridKEM.parse(combined);

        assertArrayEquals(classicalEncap, components.classicalEncapsulation());
        assertArrayEquals(pqcEncap, components.pqcEncapsulation());
    }

    @Test
    void testInvalidEncapsulationFormat() {
        byte[] invalidEncap = new byte[] { 0, 0, 0, 0 }; // Length 0 for classical encap

        assertThrows(IllegalArgumentException.class, () -> HybridKEM.parse(invalidEncap));
    }

    @Test
    void testDifferentKDFAlgorithms() throws Exception {
        // Generate key pairs
        KeyPairGenerator x25519Kpg = KeyPairGenerator.getInstance("X25519", "BC");
        KeyPair x25519KeyPair = x25519Kpg.generateKeyPair();

        KeyPairGenerator mlkemKpg = KeyPairGenerator.getInstance("ML-KEM", "BC");
        mlkemKpg.initialize(MLKEMParameterSpec.ml_kem_512, new SecureRandom());
        KeyPair mlkemKeyPair = mlkemKpg.generateKeyPair();

        String[] kdfAlgorithms = { "HKDF-SHA256", "HKDF-SHA384", "HKDF-SHA512" };

        for (String kdf : kdfAlgorithms) {
            KeyAgreement senderKA = KeyAgreement.getInstance("X25519", "BC");
            KeyGenerator senderKg = KeyGenerator.getInstance("ML-KEM", "BC");

            HybridKEM.HybridEncapsulationResult result = HybridKEM.encapsulate(
                    x25519KeyPair.getPublic(),
                    mlkemKeyPair.getPublic(),
                    senderKA,
                    senderKg,
                    "AES",
                    256,
                    kdf);

            assertNotNull(result.sharedSecret());
            assertEquals("AES", result.sharedSecret().getAlgorithm());
            assertEquals(32, result.sharedSecret().getEncoded().length); // 256 bits = 32 bytes
        }
    }
}
