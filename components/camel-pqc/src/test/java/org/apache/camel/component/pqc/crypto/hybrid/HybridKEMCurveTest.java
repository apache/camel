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

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Security;
import java.security.spec.ECGenParameterSpec;

import javax.crypto.KeyAgreement;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import org.bouncycastle.jcajce.spec.MLKEMParameterSpec;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ephemeral key of the classical half must be generated on the curve of the recipient's key. An EC public key
 * reports a bare "EC" as its algorithm, so the curve has to come from the key itself.
 */
class HybridKEMCurveTest {

    @BeforeAll
    static void setup() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        if (Security.getProvider(BouncyCastlePQCProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastlePQCProvider());
        }
    }

    @ParameterizedTest
    @CsvSource({
            "secp256r1",
            "secp384r1",
            "secp521r1"
    })
    void hybridKemRoundTripsOnEveryNistCurve(String curve) throws Exception {
        KeyPairGenerator ecKpg = KeyPairGenerator.getInstance("EC", "BC");
        ecKpg.initialize(new ECGenParameterSpec(curve), new SecureRandom());
        KeyPair ecKeyPair = ecKpg.generateKeyPair();

        KeyPairGenerator mlkemKpg = KeyPairGenerator.getInstance("ML-KEM", "BC");
        mlkemKpg.initialize(MLKEMParameterSpec.ml_kem_768, new SecureRandom());
        KeyPair mlkemKeyPair = mlkemKpg.generateKeyPair();

        HybridKEM.HybridEncapsulationResult encapResult = HybridKEM.encapsulate(
                ecKeyPair.getPublic(),
                mlkemKeyPair.getPublic(),
                KeyAgreement.getInstance("ECDH", "BC"),
                KeyGenerator.getInstance("ML-KEM", "BC"),
                "AES",
                256,
                "HKDF-SHA256");

        assertTrue(encapResult.encapsulation().length > 0, "an encapsulation must be produced for " + curve);

        SecretKey extracted = HybridKEM.extract(
                encapResult.encapsulation(),
                ecKeyPair.getPrivate(),
                mlkemKeyPair.getPrivate(),
                KeyAgreement.getInstance("ECDH", "BC"),
                KeyGenerator.getInstance("ML-KEM", "BC"),
                "AES",
                256,
                "HKDF-SHA256");

        assertArrayEquals(encapResult.sharedSecret().getEncoded(), extracted.getEncoded(),
                "sender and recipient must derive the same secret on " + curve);
        assertEquals(32, extracted.getEncoded().length, "a 256 bit key is 32 bytes");
    }

    @Test
    void x25519RoundTrips() throws Exception {
        KeyPairGenerator x25519Kpg = KeyPairGenerator.getInstance("X25519", "BC");
        KeyPair x25519KeyPair = x25519Kpg.generateKeyPair();

        KeyPairGenerator mlkemKpg = KeyPairGenerator.getInstance("ML-KEM", "BC");
        mlkemKpg.initialize(MLKEMParameterSpec.ml_kem_768, new SecureRandom());
        KeyPair mlkemKeyPair = mlkemKpg.generateKeyPair();

        HybridKEM.HybridEncapsulationResult encapResult = HybridKEM.encapsulate(
                x25519KeyPair.getPublic(),
                mlkemKeyPair.getPublic(),
                KeyAgreement.getInstance("X25519", "BC"),
                KeyGenerator.getInstance("ML-KEM", "BC"),
                "AES",
                256,
                "HKDF-SHA256");

        SecretKey extracted = HybridKEM.extract(
                encapResult.encapsulation(),
                x25519KeyPair.getPrivate(),
                mlkemKeyPair.getPrivate(),
                KeyAgreement.getInstance("X25519", "BC"),
                KeyGenerator.getInstance("ML-KEM", "BC"),
                "AES",
                256,
                "HKDF-SHA256");

        assertArrayEquals(encapResult.sharedSecret().getEncoded(), extracted.getEncoded());
    }

    @Test
    void x448RoundTrips() throws Exception {
        KeyPairGenerator x448Kpg = KeyPairGenerator.getInstance("X448", "BC");
        KeyPair x448KeyPair = x448Kpg.generateKeyPair();

        KeyPairGenerator mlkemKpg = KeyPairGenerator.getInstance("ML-KEM", "BC");
        mlkemKpg.initialize(MLKEMParameterSpec.ml_kem_768, new SecureRandom());
        KeyPair mlkemKeyPair = mlkemKpg.generateKeyPair();

        HybridKEM.HybridEncapsulationResult encapResult = HybridKEM.encapsulate(
                x448KeyPair.getPublic(),
                mlkemKeyPair.getPublic(),
                KeyAgreement.getInstance("X448", "BC"),
                KeyGenerator.getInstance("ML-KEM", "BC"),
                "AES",
                256,
                "HKDF-SHA256");

        SecretKey extracted = HybridKEM.extract(
                encapResult.encapsulation(),
                x448KeyPair.getPrivate(),
                mlkemKeyPair.getPrivate(),
                KeyAgreement.getInstance("X448", "BC"),
                KeyGenerator.getInstance("ML-KEM", "BC"),
                "AES",
                256,
                "HKDF-SHA256");

        assertArrayEquals(encapResult.sharedSecret().getEncoded(), extracted.getEncoded());
    }

    @Test
    void anUnknownKdfAlgorithmIsRejected() throws Exception {
        KeyPairGenerator ecKpg = KeyPairGenerator.getInstance("EC", "BC");
        ecKpg.initialize(new ECGenParameterSpec("secp256r1"), new SecureRandom());
        KeyPair ecKeyPair = ecKpg.generateKeyPair();

        KeyPairGenerator mlkemKpg = KeyPairGenerator.getInstance("ML-KEM", "BC");
        mlkemKpg.initialize(MLKEMParameterSpec.ml_kem_768, new SecureRandom());
        KeyPair mlkemKeyPair = mlkemKpg.generateKeyPair();

        // a typo must fail loudly: silently falling back to SHA-256 lets the two ends derive different keys
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> HybridKEM.encapsulate(
                        ecKeyPair.getPublic(),
                        mlkemKeyPair.getPublic(),
                        KeyAgreement.getInstance("ECDH", "BC"),
                        KeyGenerator.getInstance("ML-KEM", "BC"),
                        "AES",
                        256,
                        "HKDF-SHA-512"));

        assertTrue(e.getMessage().contains("HKDF-SHA-512"), "the rejected value must be named: " + e.getMessage());
    }
}
