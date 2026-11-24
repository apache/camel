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
import java.security.Security;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.crypto.KeyAgreement;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import org.apache.camel.component.pqc.InterleavingProbe;
import org.apache.camel.component.pqc.PQCKemLocks;
import org.apache.camel.component.pqc.PQCKeyEncapsulationAlgorithms;
import org.apache.camel.component.pqc.ProbedKeyGenerator;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hybrid encapsulations and extractions using two different PQC KeyGenerators of the same algorithm. For an algorithm
 * that keeps state outside the KeyGenerator (SABER) one operation must not run its {@code init()} between the
 * {@code init()} and the {@code generateKey()} of the other. The first operation is held between those two calls, so
 * the outcome does not depend on thread scheduling.
 */
class HybridKEMSharedEngineIsolationTest {

    private static final long SECONDS = 20;

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
    @EnumSource(PQCKeyEncapsulationAlgorithms.class)
    void overlappingEncapsulationAndExtractionOnDifferentKeyGeneratorsKeepTheirOwnSecrets(
            PQCKeyEncapsulationAlgorithms kem)
            throws Exception {
        KeyPair classical = KeyPairGenerator.getInstance("X25519", "BC").generateKeyPair();
        KeyPair pqc = KeyPairGenerator.getInstance(kem.getAlgorithm(), kem.getBcProvider()).generateKeyPair();
        InterleavingProbe probe = new InterleavingProbe();

        HybridKEM.HybridEncapsulationResult other = HybridKEM.encapsulate(classical.getPublic(), pqc.getPublic(),
                KeyAgreement.getInstance("X25519", "BC"), newKeyGenerator(kem), "AES", 256, "HKDF-SHA256");

        probe.holdNextResult();
        KeyGenerator encapsulationGenerator = ProbedKeyGenerator.wrap(newKeyGenerator(kem), probe);
        KeyGenerator extractionGenerator = ProbedKeyGenerator.wrap(newKeyGenerator(kem), probe);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        HybridKEM.HybridEncapsulationResult mine;
        SecretKey extracted;
        try {
            Future<HybridKEM.HybridEncapsulationResult> encapsulated = pool.submit(
                    () -> HybridKEM.encapsulate(classical.getPublic(), pqc.getPublic(),
                            KeyAgreement.getInstance("X25519", "BC"), encapsulationGenerator, "AES", 256, "HKDF-SHA256"));
            assertThat(probe.awaitHolding(SECONDS, TimeUnit.SECONDS)).isTrue();
            Future<SecretKey> extraction = pool.submit(
                    () -> HybridKEM.extract(other.encapsulation(), classical.getPrivate(), pqc.getPrivate(),
                            KeyAgreement.getInstance("X25519", "BC"), extractionGenerator, "AES", 256, "HKDF-SHA256"));
            mine = encapsulated.get(SECONDS, TimeUnit.SECONDS);
            extracted = extraction.get(SECONDS, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        SecretKey mineExtracted = HybridKEM.extract(mine.encapsulation(), classical.getPrivate(), pqc.getPrivate(),
                KeyAgreement.getInstance("X25519", "BC"), newKeyGenerator(kem), "AES", 256, "HKDF-SHA256");
        assertThat(mineExtracted.getEncoded()).isEqualTo(mine.sharedSecret().getEncoded());
        assertThat(extracted.getEncoded()).isEqualTo(other.sharedSecret().getEncoded());
        if (PQCKemLocks.sharesEngineAcrossKeyGenerators(kem)) {
            assertThat(probe.interleaved())
                    .as("%s: an init() of another KeyGenerator ran between init() and generateKey()", kem)
                    .isFalse();
        }
    }

    private static KeyGenerator newKeyGenerator(PQCKeyEncapsulationAlgorithms kem) throws Exception {
        return KeyGenerator.getInstance(kem.getAlgorithm(), kem.getBcProvider());
    }
}
