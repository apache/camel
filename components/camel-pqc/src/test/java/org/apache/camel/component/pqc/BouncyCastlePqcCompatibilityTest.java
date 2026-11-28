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
package org.apache.camel.component.pqc;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.NamedParameterSpec;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BouncyCastlePqcCompatibilityTest {

    @Test
    void resolvesNamedParametersUsingTypedPublicApis() {
        assertNotNull(PQCParameterSpecResolver.resolve("MLDSA", "ML-DSA-65"));
        assertNotNull(PQCParameterSpecResolver.resolve("SLHDSA", "SLH-DSA-SHA2-128S"));
        assertNotNull(PQCParameterSpecResolver.resolve("FALCON", "falcon-512"));
        assertNotNull(PQCParameterSpecResolver.resolve("MLKEM", "ML-KEM-768"));
        assertNotNull(PQCParameterSpecResolver.resolve("NTRU", "ntruhps2048509"));
        assertNotNull(PQCParameterSpecResolver.resolve("CMCE", "mceliece460896"));
        assertTrue(PQCParameterSpecResolver.resolve("MLDSA", "ML-DSA-65") instanceof NamedParameterSpec);
        assertTrue(PQCParameterSpecResolver.resolve("MLKEM", "ML-KEM-768") instanceof NamedParameterSpec);
        assertTrue(PQCParameterSpecResolver.resolve("CMCE", "mceliece460896") instanceof NamedParameterSpec);
    }

    @Test
    void createsStatefulParameterSpecsAndTracksXmssKeyUse() throws Exception {
        AlgorithmParameterSpec lms = BouncyCastlePqcCompatibility.lmsKeyGenParameterSpec(
                "lms_sha256_n32_h5", "sha256_n32_w1");
        AlgorithmParameterSpec hss = BouncyCastlePqcCompatibility.lmsHssKeyGenParameterSpec(
                new String[] { "lms_sha256_n32_h5", "lms_sha256_n32_h5" },
                new String[] { "sha256_n32_w2", "sha256_n32_w2" });
        AlgorithmParameterSpec xmss = BouncyCastlePqcCompatibility.xmssParameterSpec(2, "SHA256");
        AlgorithmParameterSpec xmssMt = BouncyCastlePqcCompatibility.xmssMtParameterSpec(20, 2, "SHA256");
        assertNotNull(lms);
        assertNotNull(hss);
        assertNotNull(xmss);
        assertNotNull(xmssMt);

        BouncyCastleSupport.ensureProviders();
        String kpgProvider = BouncyCastleSupport.getProviderName("KeyPairGenerator", "XMSS");
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("XMSS", kpgProvider);
        keyPairGenerator.initialize(xmss);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        PrivateKey privateKey = keyPair.getPrivate();

        assertEquals(0, BouncyCastlePqcCompatibility.getStatefulKeyIndex(privateKey));
        assertEquals(4, BouncyCastlePqcCompatibility.getStatefulKeyRemaining(privateKey));

        String signatureProvider = BouncyCastleSupport.getProviderName("Signature", "XMSS");
        Signature signature = Signature.getInstance("XMSS", signatureProvider);
        signature.initSign(privateKey);
        signature.update("typed-stateful-api".getBytes(StandardCharsets.UTF_8));
        assertNotNull(signature.sign());
        assertEquals(1, BouncyCastlePqcCompatibility.getStatefulKeyIndex(privateKey));
        assertEquals(3, BouncyCastlePqcCompatibility.getStatefulKeyRemaining(privateKey));
    }

    @Test
    void tracksLmsHssAndXmssMtStatefulKeyUse() throws Exception {
        assertStatefulKeyUse(createStatefulKeyPair(PQCSignatureAlgorithms.LMS), PQCSignatureAlgorithms.LMS);
        assertStatefulKeyUse(createStatefulKeyPair(PQCSignatureAlgorithms.HSS), PQCSignatureAlgorithms.HSS);
        assertStatefulKeyUse(createStatefulKeyPair(PQCSignatureAlgorithms.XMSSMT), PQCSignatureAlgorithms.XMSSMT);
    }

    private static KeyPair createStatefulKeyPair(PQCSignatureAlgorithms algorithm) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance(
                algorithm.getAlgorithm(), algorithm.getBcProvider("KeyPairGenerator"));
        switch (algorithm) {
            case LMS -> generator.initialize(BouncyCastlePqcCompatibility.lmsKeyGenParameterSpec(
                    "lms_sha256_n32_h5", "sha256_n32_w1"), new SecureRandom());
            case HSS -> generator.initialize(BouncyCastlePqcCompatibility.lmsHssKeyGenParameterSpec(
                    new String[] { "lms_sha256_n32_h5", "lms_sha256_n32_h5" },
                    new String[] { "sha256_n32_w2", "sha256_n32_w2" }), new SecureRandom());
            case XMSSMT -> generator.initialize(
                    BouncyCastlePqcCompatibility.xmssMtParameterSpec(20, 2, "SHA256"), new SecureRandom());
            default -> throw new IllegalArgumentException("Not a stateful test algorithm: " + algorithm);
        }
        return generator.generateKeyPair();
    }

    private static void assertStatefulKeyUse(KeyPair pair, PQCSignatureAlgorithms algorithm) throws Exception {
        PrivateKey privateKey = pair.getPrivate();
        assertEquals(0, BouncyCastlePqcCompatibility.getStatefulKeyIndex(privateKey),
                algorithm + " starts at stateful signature index zero");
        long remainingBefore = BouncyCastlePqcCompatibility.getStatefulKeyRemaining(privateKey);
        assertTrue(remainingBefore > 0, algorithm + " must expose a positive remaining-signature count");

        Signature signature = Signature.getInstance(algorithm.getAlgorithm(), algorithm.getBcProvider("Signature"));
        signature.initSign(privateKey);
        signature.update("stateful-adapter-regression".getBytes(StandardCharsets.UTF_8));
        assertNotNull(signature.sign());

        assertEquals(1, BouncyCastlePqcCompatibility.getStatefulKeyIndex(privateKey),
                algorithm + " increments its signature index");
        assertEquals(remainingBefore - 1, BouncyCastlePqcCompatibility.getStatefulKeyRemaining(privateKey),
                algorithm + " decrements its remaining-signature count");
    }

    @Test
    void nonStatefulKeysKeepTheExistingFallbackValues() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(1024);
        PrivateKey privateKey = generator.generateKeyPair().getPrivate();
        assertEquals(0, PQCProducer.getStatefulKeyIndex(privateKey));
        assertEquals(-1, PQCProducer.getStatefulKeyRemaining(privateKey));
    }
}
