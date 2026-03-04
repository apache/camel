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

import org.bouncycastle.jcajce.spec.MLDSAParameterSpec;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HybridSignatureTest {

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
    void testSignAndVerifyWithECDSAAndMLDSA() throws Exception {
        // Generate ECDSA key pair
        KeyPairGenerator ecKpg = KeyPairGenerator.getInstance("EC", "BC");
        ecKpg.initialize(new ECGenParameterSpec("secp256r1"), new SecureRandom());
        KeyPair ecKeyPair = ecKpg.generateKeyPair();
        Signature ecSigner = Signature.getInstance("SHA256withECDSA", "BC");

        // Generate ML-DSA key pair
        KeyPairGenerator mldsaKpg = KeyPairGenerator.getInstance("ML-DSA", "BC");
        mldsaKpg.initialize(MLDSAParameterSpec.ml_dsa_65, new SecureRandom());
        KeyPair mldsaKeyPair = mldsaKpg.generateKeyPair();
        Signature mldsaSigner = Signature.getInstance("ML-DSA", "BC");

        byte[] data = "Hello, hybrid cryptography!".getBytes();

        // Sign
        byte[] hybridSig = HybridSignature.sign(
                data,
                ecKeyPair.getPrivate(), ecSigner,
                mldsaKeyPair.getPrivate(), mldsaSigner);

        assertNotNull(hybridSig);
        assertTrue(hybridSig.length > 0);

        // Verify with same keys
        Signature ecVerifier = Signature.getInstance("SHA256withECDSA", "BC");
        Signature mldsaVerifier = Signature.getInstance("ML-DSA", "BC");

        boolean valid = HybridSignature.verify(
                data,
                hybridSig,
                ecKeyPair.getPublic(), ecVerifier,
                mldsaKeyPair.getPublic(), mldsaVerifier);

        assertTrue(valid);
    }

    @Test
    void testVerifyFailsWithWrongData() throws Exception {
        // Generate key pairs
        KeyPairGenerator ecKpg = KeyPairGenerator.getInstance("EC", "BC");
        ecKpg.initialize(new ECGenParameterSpec("secp256r1"), new SecureRandom());
        KeyPair ecKeyPair = ecKpg.generateKeyPair();
        Signature ecSigner = Signature.getInstance("SHA256withECDSA", "BC");

        KeyPairGenerator mldsaKpg = KeyPairGenerator.getInstance("ML-DSA", "BC");
        mldsaKpg.initialize(MLDSAParameterSpec.ml_dsa_65, new SecureRandom());
        KeyPair mldsaKeyPair = mldsaKpg.generateKeyPair();
        Signature mldsaSigner = Signature.getInstance("ML-DSA", "BC");

        byte[] data = "Original data".getBytes();
        byte[] tamperedData = "Tampered data".getBytes();

        // Sign original data
        byte[] hybridSig = HybridSignature.sign(
                data,
                ecKeyPair.getPrivate(), ecSigner,
                mldsaKeyPair.getPrivate(), mldsaSigner);

        // Verify with tampered data
        Signature ecVerifier = Signature.getInstance("SHA256withECDSA", "BC");
        Signature mldsaVerifier = Signature.getInstance("ML-DSA", "BC");

        boolean valid = HybridSignature.verify(
                tamperedData,
                hybridSig,
                ecKeyPair.getPublic(), ecVerifier,
                mldsaKeyPair.getPublic(), mldsaVerifier);

        assertFalse(valid);
    }

    @Test
    void testParseHybridSignature() throws Exception {
        // Create a simple hybrid signature manually
        byte[] classicalSig = new byte[] { 1, 2, 3, 4, 5 };
        byte[] pqcSig = new byte[] { 10, 20, 30, 40, 50, 60 };

        byte[] combined = HybridSignature.combineSignatures(classicalSig, pqcSig);

        HybridSignature.HybridSignatureComponents components = HybridSignature.parse(combined);

        assertArrayEquals(classicalSig, components.classicalSignature());
        assertArrayEquals(pqcSig, components.pqcSignature());
    }

    @Test
    void testDetailedVerification() throws Exception {
        // Generate key pairs
        KeyPairGenerator ecKpg = KeyPairGenerator.getInstance("EC", "BC");
        ecKpg.initialize(new ECGenParameterSpec("secp256r1"), new SecureRandom());
        KeyPair ecKeyPair = ecKpg.generateKeyPair();
        Signature ecSigner = Signature.getInstance("SHA256withECDSA", "BC");

        KeyPairGenerator mldsaKpg = KeyPairGenerator.getInstance("ML-DSA", "BC");
        mldsaKpg.initialize(MLDSAParameterSpec.ml_dsa_65, new SecureRandom());
        KeyPair mldsaKeyPair = mldsaKpg.generateKeyPair();
        Signature mldsaSigner = Signature.getInstance("ML-DSA", "BC");

        byte[] data = "Test data".getBytes();

        // Sign
        byte[] hybridSig = HybridSignature.sign(
                data,
                ecKeyPair.getPrivate(), ecSigner,
                mldsaKeyPair.getPrivate(), mldsaSigner);

        // Verify with detailed results
        Signature ecVerifier = Signature.getInstance("SHA256withECDSA", "BC");
        Signature mldsaVerifier = Signature.getInstance("ML-DSA", "BC");

        HybridSignature.HybridVerificationResult result = HybridSignature.verifyDetailed(
                data,
                hybridSig,
                ecKeyPair.getPublic(), ecVerifier,
                mldsaKeyPair.getPublic(), mldsaVerifier);

        assertTrue(result.classicalValid());
        assertTrue(result.pqcValid());
        assertTrue(result.isValid());
    }

    @Test
    void testInvalidSignatureFormat() {
        byte[] invalidSig = new byte[] { 0, 0, 0, 0 }; // Length 0 for classical sig

        assertThrows(IllegalArgumentException.class, () -> HybridSignature.parse(invalidSig));
    }
}
