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

import java.security.Security;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

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
    }

    @Test
    void testNamesAreCaseInsensitiveAndAcceptUnderscoreAlias() {
        assertNotNull(PQCParameterSpecResolver.resolve("MLDSA", "ml-dsa-65"));
        // the underscore form matching the BouncyCastle constant names is accepted as an alias
        assertNotNull(PQCParameterSpecResolver.resolve("MLDSA", "ml_dsa_87"));
        assertNotNull(PQCParameterSpecResolver.resolve("MLKEM", "ml_kem_1024"));
    }

    @Test
    void testResolveKeyEncapsulationParameterSets() {
        assertNotNull(PQCParameterSpecResolver.resolve("MLKEM", "ml_kem_512"));
        assertNotNull(PQCParameterSpecResolver.resolve("MLKEM", "ml_kem_1024"));
        assertNotNull(PQCParameterSpecResolver.resolve("KYBER", "kyber768"));
        assertNotNull(PQCParameterSpecResolver.resolve("BIKE", "bike128"));
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
    void testUnknownParameterSpecRejectedWhenSpecReturnsNull() {
        // the older BouncyCastle spec classes return null rather than throwing for an unknown name
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> PQCParameterSpecResolver.resolve("BIKE", "bogus"));
        assertTrue(e.getMessage().contains("Unknown parameterSpec"));
    }
}
