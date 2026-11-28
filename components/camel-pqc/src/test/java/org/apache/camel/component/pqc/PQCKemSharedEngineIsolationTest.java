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

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Security;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.crypto.KeyGenerator;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.impl.DefaultCamelContext;
import org.bouncycastle.jcajce.SecretKeyWithEncapsulation;
import org.bouncycastle.jcajce.spec.KEMGenerateSpec;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The KEM operations of a Camel producer are serialized on the KeyGenerator they use, which is enough as long as the
 * algorithm keeps its state in that generator. Bouncy Castle SABER does not: all keys of a parameter set share one
 * engine, so two exchanges using two different KeyGenerators still corrupt each other.
 * <p>
 * The test is deterministic. It holds one exchange between {@code init()} and {@code generateKey()} of its generator
 * and starts a second exchange on another generator in the meantime. Whether the second {@code init()} ran inside that
 * window does not depend on thread scheduling.
 */
class PQCKemSharedEngineIsolationTest {

    private static final long SECONDS = 20;

    @BeforeAll
    static void startup() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        if (Security.getProvider(BouncyCastlePQCProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastlePQCProvider());
        }
    }

    @ParameterizedTest
    @EnumSource(PQCKeyEncapsulationAlgorithms.class)
    void overlappingExtractionsOnDifferentKeyGeneratorsEachReturnTheirOwnSecret(PQCKeyEncapsulationAlgorithms kem)
            throws Exception {
        InterleavingProbe probe = new InterleavingProbe();
        KeyPair keyPair
                = KeyPairGenerator.getInstance(kem.getAlgorithm(), kem.getBcProvider("KeyPairGenerator")).generateKeyPair();

        try (CamelContext context = new DefaultCamelContext()) {
            context.getRegistry().bind("kemKeyPair", keyPair);
            context.getRegistry().bind("firstGenerator", ProbedKeyGenerator.wrap(newKeyGenerator(kem), probe));
            context.getRegistry().bind("secondGenerator", ProbedKeyGenerator.wrap(newKeyGenerator(kem), probe));
            context.start();
            ProducerTemplate template = context.createProducerTemplate();

            SecretKeyWithEncapsulation first = encapsulate(kem, keyPair);
            SecretKeyWithEncapsulation second = encapsulate(kem, keyPair);

            probe.holdNextResult();
            Future<SecretKeyWithEncapsulation> firstResult
                    = template.asyncRequestBody(extractUri(kem, "firstGenerator"), first, SecretKeyWithEncapsulation.class);
            assertThat(probe.awaitHolding(SECONDS, TimeUnit.SECONDS)).isTrue();
            Future<SecretKeyWithEncapsulation> secondResult
                    = template.asyncRequestBody(extractUri(kem, "secondGenerator"), second, SecretKeyWithEncapsulation.class);

            assertThat(firstResult.get(SECONDS, TimeUnit.SECONDS).getEncoded()).isEqualTo(first.getEncoded());
            assertThat(secondResult.get(SECONDS, TimeUnit.SECONDS).getEncoded()).isEqualTo(second.getEncoded());
            if (PQCKemLocks.sharesEngineAcrossKeyGenerators(kem)) {
                assertThat(probe.interleaved())
                        .as("%s: an init() of another KeyGenerator ran between init() and generateKey() of an exchange", kem)
                        .isFalse();
            }
        }
    }

    @ParameterizedTest
    @EnumSource(PQCKeyEncapsulationAlgorithms.class)
    void lockIsSharedByKeyGeneratorsOnlyWhenTheEngineIsShared(PQCKeyEncapsulationAlgorithms kem) throws Exception {
        KeyPair keyPair
                = KeyPairGenerator.getInstance(kem.getAlgorithm(), kem.getBcProvider("KeyPairGenerator")).generateKeyPair();
        KeyGenerator first = newKeyGenerator(kem);
        KeyGenerator second = newKeyGenerator(kem);

        assertThat(PQCKemLocks.lockFor(first, keyPair.getPublic())).isSameAs(
                PQCKemLocks.sharesEngineAcrossKeyGenerators(kem)
                        ? PQCKemLocks.lockFor(second, keyPair.getPrivate())
                        : first);
    }

    private static String extractUri(PQCKeyEncapsulationAlgorithms kem, String generator) {
        return "pqc:extract-" + generator + "?operation=extractSecretKeyEncapsulation&symmetricKeyAlgorithm=AES"
               + "&keyEncapsulationAlgorithm=" + kem.name() + "&keyPair=#kemKeyPair&keyGenerator=#" + generator;
    }

    private static SecretKeyWithEncapsulation encapsulate(PQCKeyEncapsulationAlgorithms kem, KeyPair keyPair)
            throws Exception {
        KeyGenerator kg = newKeyGenerator(kem);
        kg.init(new KEMGenerateSpec(keyPair.getPublic(), "AES", 256), new SecureRandom());
        return (SecretKeyWithEncapsulation) kg.generateKey();
    }

    private static KeyGenerator newKeyGenerator(PQCKeyEncapsulationAlgorithms kem) throws Exception {
        return KeyGenerator.getInstance(kem.getAlgorithm(), kem.getBcProvider("KeyGenerator"));
    }
}
