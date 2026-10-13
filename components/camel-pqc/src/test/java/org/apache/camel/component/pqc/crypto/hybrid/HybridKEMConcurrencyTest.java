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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.crypto.KeyAgreement;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import org.apache.camel.component.pqc.InterleavingProbe;
import org.apache.camel.component.pqc.ProbedKeyAgreement;
import org.apache.camel.component.pqc.ProbedKeyGenerator;
import org.bouncycastle.jcajce.spec.MLKEMParameterSpec;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HybridKEM receives its KeyAgreement and KeyGenerator from the caller, and they can be shared by concurrent callers: a
 * configured bean, or the {@code PQCDefault*Material} instances. Both keep state from init() to the call that returns
 * the result, so every call must work with its own keys and encapsulation however the calls overlap.
 */
class HybridKEMConcurrencyTest {

    private final InterleavingProbe agreementProbe = new InterleavingProbe();
    private final InterleavingProbe generatorProbe = new InterleavingProbe();
    private KeyPair classicalKeyPair;
    private KeyPair pqcKeyPair;
    private KeyAgreement sharedAgreement;
    private KeyGenerator sharedGenerator;

    @BeforeAll
    static void setup() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        if (Security.getProvider(BouncyCastlePQCProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastlePQCProvider());
        }
    }

    @BeforeEach
    void createKeys() throws Exception {
        classicalKeyPair = KeyPairGenerator.getInstance("X25519", "BC").generateKeyPair();
        KeyPairGenerator mlkemKpg = KeyPairGenerator.getInstance("ML-KEM", "BC");
        mlkemKpg.initialize(MLKEMParameterSpec.ml_kem_768, new SecureRandom());
        pqcKeyPair = mlkemKpg.generateKeyPair();
        sharedAgreement = ProbedKeyAgreement.wrap(KeyAgreement.getInstance("X25519", "BC"), agreementProbe);
        sharedGenerator = ProbedKeyGenerator.wrap(KeyGenerator.getInstance("ML-KEM", "BC"), generatorProbe);
    }

    @Test
    void overlappingExtractionsOnSharedInstancesEachReturnTheirOwnSecret() throws Exception {
        HybridKEM.HybridEncapsulationResult first = encapsulate(newAgreement(), newGenerator());
        HybridKEM.HybridEncapsulationResult second = encapsulate(newAgreement(), newGenerator());

        agreementProbe.holdNextResult();
        generatorProbe.holdNextResult();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<SecretKey> firstSecret = pool.submit(() -> extract(first.encapsulation()));
            assertTrue(agreementProbe.awaitHolding(10, TimeUnit.SECONDS));
            Future<SecretKey> secondSecret = pool.submit(() -> extract(second.encapsulation()));

            assertArrayEquals(first.sharedSecret().getEncoded(), firstSecret.get(10, TimeUnit.SECONDS).getEncoded());
            assertArrayEquals(second.sharedSecret().getEncoded(), secondSecret.get(10, TimeUnit.SECONDS).getEncoded());
        } finally {
            pool.shutdownNow();
        }
        assertFalse(agreementProbe.interleaved(), "an init() ran inside another call's KeyAgreement sequence");
        assertFalse(generatorProbe.interleaved(), "an init() ran inside another call's KeyGenerator sequence");
    }

    @Test
    void overlappingEncapsulationsOnSharedInstancesEachProduceAMatchingSecret() throws Exception {
        agreementProbe.holdNextResult();
        generatorProbe.holdNextResult();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        HybridKEM.HybridEncapsulationResult first;
        HybridKEM.HybridEncapsulationResult second;
        try {
            Future<HybridKEM.HybridEncapsulationResult> firstResult
                    = pool.submit(() -> encapsulate(sharedAgreement, sharedGenerator));
            assertTrue(agreementProbe.awaitHolding(10, TimeUnit.SECONDS));
            Future<HybridKEM.HybridEncapsulationResult> secondResult
                    = pool.submit(() -> encapsulate(sharedAgreement, sharedGenerator));
            first = firstResult.get(10, TimeUnit.SECONDS);
            second = secondResult.get(10, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        // Each encapsulation must lead the recipient to the secret its sender was given
        assertArrayEquals(first.sharedSecret().getEncoded(), extractWithOwnInstances(first.encapsulation()).getEncoded());
        assertArrayEquals(second.sharedSecret().getEncoded(),
                extractWithOwnInstances(second.encapsulation()).getEncoded());
        assertFalse(agreementProbe.interleaved(), "an init() ran inside another call's KeyAgreement sequence");
        assertFalse(generatorProbe.interleaved(), "an init() ran inside another call's KeyGenerator sequence");
    }

    private HybridKEM.HybridEncapsulationResult encapsulate(KeyAgreement agreement, KeyGenerator generator)
            throws Exception {
        return HybridKEM.encapsulate(classicalKeyPair.getPublic(), pqcKeyPair.getPublic(), agreement, generator,
                "AES", 256, "HKDF-SHA256");
    }

    private SecretKey extract(byte[] encapsulation) throws Exception {
        return HybridKEM.extract(encapsulation, classicalKeyPair.getPrivate(), pqcKeyPair.getPrivate(),
                sharedAgreement, sharedGenerator, "AES", 256, "HKDF-SHA256");
    }

    private SecretKey extractWithOwnInstances(byte[] encapsulation) throws Exception {
        return HybridKEM.extract(encapsulation, classicalKeyPair.getPrivate(), pqcKeyPair.getPrivate(),
                newAgreement(), newGenerator(), "AES", 256, "HKDF-SHA256");
    }

    private static KeyAgreement newAgreement() throws Exception {
        return KeyAgreement.getInstance("X25519", "BC");
    }

    private static KeyGenerator newGenerator() throws Exception {
        return KeyGenerator.getInstance("ML-KEM", "BC");
    }
}
