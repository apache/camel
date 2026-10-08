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

import java.security.KeyPairGenerator;
import java.security.Security;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PQCParameterSpecResolverTest {

    @BeforeAll
    static void startup() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        if (Security.getProvider(BouncyCastlePQCProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastlePQCProvider());
        }
    }

    @Test
    void testResolveSignatureParameterSets() {
        // canonical BouncyCastle names
        assertNotNull(PQCParameterSpecResolver.resolve("MLDSA", "ML-DSA-44"));
        assertNotNull(PQCParameterSpecResolver.resolve("MLDSA", "ML-DSA-87"));
        assertNotNull(PQCParameterSpecResolver.resolve("SLHDSA", "SLH-DSA-SHA2-128S"));
        assertNotNull(PQCParameterSpecResolver.resolve("FALCON", "FALCON-1024"));
        // DILITHIUM and SPHINCSPLUS generate ML-DSA and SLH-DSA keys, so they take the standardized names
        assertNotNull(PQCParameterSpecResolver.resolve("DILITHIUM", "ML-DSA-65"));
        assertNotNull(PQCParameterSpecResolver.resolve("SPHINCSPLUS", "SLH-DSA-SHA2-128S"));
    }

    @Test
    void testNamesAreCaseInsensitiveAndAcceptUnderscoreAlias() {
        assertNotNull(PQCParameterSpecResolver.resolve("MLDSA", "ml-dsa-65"));
        // the underscore form matching the BouncyCastle constant names is accepted as an alias
        assertNotNull(PQCParameterSpecResolver.resolve("MLDSA", "ml_dsa_87"));
        assertNotNull(PQCParameterSpecResolver.resolve("MLKEM", "ml_kem_1024"));
        assertNotNull(PQCParameterSpecResolver.resolve("SPHINCSPLUS", "sha2_128s"));
    }

    @Test
    void testResolveKeyEncapsulationParameterSets() {
        assertNotNull(PQCParameterSpecResolver.resolve("MLKEM", "ml_kem_512"));
        assertNotNull(PQCParameterSpecResolver.resolve("MLKEM", "ml_kem_1024"));
        assertNotNull(PQCParameterSpecResolver.resolve("KYBER", "kyber768"));
        assertNotNull(PQCParameterSpecResolver.resolve("KYBER", "ML-KEM-1024"));
        assertNotNull(PQCParameterSpecResolver.resolve("BIKE", "bike128"));
        assertNotNull(PQCParameterSpecResolver.resolve("CMCE", "mceliece460896"));
        assertNotNull(PQCParameterSpecResolver.resolve("FRODO", "frodokem976aes"));
    }

    @ParameterizedTest
    @CsvSource({
            "MLDSA, ML-DSA-65", "SLHDSA, SLH-DSA-SHA2-128S", "FALCON, FALCON-512", "DILITHIUM, ML-DSA-65",
            "SPHINCSPLUS, SLH-DSA-SHA2-128S" })
    void testResolvedSignatureSpecInitializesTheAlgorithmKeyPairGenerator(String algorithm, String parameterSpec)
            throws Exception {
        PQCSignatureAlgorithms signatureAlgorithm = PQCSignatureAlgorithms.valueOf(algorithm);
        KeyPairGenerator generator = KeyPairGenerator.getInstance(signatureAlgorithm.getAlgorithm(),
                signatureAlgorithm.getBcProvider());

        assertDoesNotThrow(() -> generator.initialize(PQCParameterSpecResolver.resolve(algorithm, parameterSpec)));
    }

    @ParameterizedTest
    @CsvSource({
            "MLKEM, ML-KEM-768", "KYBER, kyber768", "NTRU, ntruhps2048509", "NTRULPRime, ntrulpr653",
            "SNTRUPrime, sntrup761", "BIKE, bike128", "HQC, hqc128", "CMCE, mceliece460896", "FRODO, frodokem976aes",
            "SABER, lightsaberkem128r3" })
    void testResolvedKeyEncapsulationSpecInitializesTheAlgorithmKeyPairGenerator(String algorithm, String parameterSpec)
            throws Exception {
        PQCKeyEncapsulationAlgorithms kemAlgorithm = PQCKeyEncapsulationAlgorithms.valueOf(algorithm);
        KeyPairGenerator generator = KeyPairGenerator.getInstance(kemAlgorithm.getAlgorithm(),
                kemAlgorithm.getBcProvider());

        assertDoesNotThrow(() -> generator.initialize(PQCParameterSpecResolver.resolve(algorithm, parameterSpec)));
    }

    @Test
    void testIsSupported() {
        assertTrue(PQCParameterSpecResolver.isSupported("MLDSA"));
        assertTrue(PQCParameterSpecResolver.isSupported("MLKEM"));
        // Stateful hash-based signatures and MAYO/SNOVA have no name-addressable parameter spec
        assertFalse(PQCParameterSpecResolver.isSupported("XMSS"));
        assertFalse(PQCParameterSpecResolver.isSupported("XMSSMT"));
        assertFalse(PQCParameterSpecResolver.isSupported("LMS"));
        assertFalse(PQCParameterSpecResolver.isSupported("HSS"));
        assertFalse(PQCParameterSpecResolver.isSupported("MAYO"));
        assertFalse(PQCParameterSpecResolver.isSupported("SNOVA"));
        assertFalse(PQCParameterSpecResolver.isSupported(null));
    }

    @Test
    void testUnsupportedAlgorithmRejected() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> PQCParameterSpecResolver.resolve("XMSS", "anything"));
        assertTrue(e.getMessage().contains("not supported"));
    }

    @Test
    void testUnknownParameterSpecRejected() {
        // ML-DSA/ML-KEM/SLH-DSA throw for an unknown name
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> PQCParameterSpecResolver.resolve("MLDSA", "ml_dsa_999"));
        assertTrue(e.getMessage().contains("Unknown parameterSpec"));
    }

    @Test
    void testParameterSetsDroppedByBouncyCastleRejected() {
        // The Classic McEliece and FrodoKEM specs of the BC provider have no mceliece348864 or frodokem640 sets
        IllegalArgumentException cmce = assertThrows(IllegalArgumentException.class,
                () -> PQCParameterSpecResolver.resolve("CMCE", "mceliece348864"));
        assertTrue(cmce.getMessage().contains("Unknown parameterSpec"));
        IllegalArgumentException frodo = assertThrows(IllegalArgumentException.class,
                () -> PQCParameterSpecResolver.resolve("FRODO", "frodokem640aes"));
        assertTrue(frodo.getMessage().contains("Unknown parameterSpec"));
        IllegalArgumentException dilithium = assertThrows(IllegalArgumentException.class,
                () -> PQCParameterSpecResolver.resolve("DILITHIUM", "dilithium2"));
        assertTrue(dilithium.getMessage().contains("Unknown parameterSpec"));
    }

    @Test
    void testUnknownParameterSpecRejectedWhenSpecReturnsNull() {
        // the older BouncyCastle spec classes return null rather than throwing for an unknown name
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> PQCParameterSpecResolver.resolve("BIKE", "bogus"));
        assertTrue(e.getMessage().contains("Unknown parameterSpec"));
    }
}
