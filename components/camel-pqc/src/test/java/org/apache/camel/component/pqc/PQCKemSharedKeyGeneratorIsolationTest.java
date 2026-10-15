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
import java.util.Arrays;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.crypto.KeyGenerator;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.Registry;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.bouncycastle.jcajce.SecretKeyWithEncapsulation;
import org.bouncycastle.jcajce.spec.KEMExtractSpec;
import org.bouncycastle.jcajce.spec.KEMGenerateSpec;
import org.bouncycastle.jcajce.spec.MLKEMParameterSpec;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A KeyGenerator configured on the endpoints - for example a registry bean, which is autowired into every pqc endpoint
 * - is shared by all their exchanges, while a KEM KeyGenerator keeps the spec passed to init() until generateKey()
 * reads it. Every exchange must get the result of its own spec, however the exchanges overlap.
 */
class PQCKemSharedKeyGeneratorIsolationTest extends CamelTestSupport {

    private final InterleavingProbe probe = new InterleavingProbe();
    private KeyPair keyPair;

    @BeforeAll
    static void startup() {
        ensureProviders();
    }

    /**
     * PQCEndpoint removes the BouncyCastle providers from the JVM when it stops, so with more than one test method in
     * the class they must be registered again before each CamelContext is built.
     */
    private static void ensureProviders() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        if (Security.getProvider(BouncyCastlePQCProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastlePQCProvider());
        }
    }

    @Override
    protected void bindToRegistry(Registry registry) throws Exception {
        ensureProviders();
        KeyPairGenerator kpg = KeyPairGenerator.getInstance(PQCKeyEncapsulationAlgorithms.MLKEM.getAlgorithm(),
                PQCKeyEncapsulationAlgorithms.MLKEM.getBcProvider());
        kpg.initialize(MLKEMParameterSpec.ml_kem_512, new SecureRandom());
        keyPair = kpg.generateKeyPair();
        registry.bind("kemKeyPair", keyPair);
        registry.bind("sharedKeyGenerator", ProbedKeyGenerator.wrap(newKeyGenerator(), probe));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:generate")
                        .to("pqc:generate?operation=generateSecretKeyEncapsulation&symmetricKeyAlgorithm=AES"
                            + "&keyPair=#kemKeyPair&keyGenerator=#sharedKeyGenerator");
                from("direct:extract")
                        .to("pqc:extract?operation=extractSecretKeyEncapsulation&symmetricKeyAlgorithm=AES"
                            + "&keyPair=#kemKeyPair&keyGenerator=#sharedKeyGenerator");
            }
        };
    }

    @Test
    void overlappingExtractionsEachReturnTheirOwnSecret() throws Exception {
        SecretKeyWithEncapsulation first = encapsulate();
        SecretKeyWithEncapsulation second = encapsulate();

        probe.holdNextResult();
        Future<SecretKeyWithEncapsulation> firstResult
                = template.asyncRequestBody("direct:extract", first, SecretKeyWithEncapsulation.class);
        assertTrue(probe.awaitHolding(10, TimeUnit.SECONDS));
        Future<SecretKeyWithEncapsulation> secondResult
                = template.asyncRequestBody("direct:extract", second, SecretKeyWithEncapsulation.class);

        assertArrayEquals(first.getEncoded(), firstResult.get(10, TimeUnit.SECONDS).getEncoded());
        assertArrayEquals(second.getEncoded(), secondResult.get(10, TimeUnit.SECONDS).getEncoded());
        assertFalse(probe.interleaved(), "an init() ran between another exchange's init() and generateKey()");
    }

    @Test
    void overlappingGenerationAndExtractionEachReturnTheirOwnResult() throws Exception {
        SecretKeyWithEncapsulation other = encapsulate();

        probe.holdNextResult();
        Future<SecretKeyWithEncapsulation> generated
                = template.asyncRequestBody("direct:generate", "generate", SecretKeyWithEncapsulation.class);
        assertTrue(probe.awaitHolding(10, TimeUnit.SECONDS));
        Future<SecretKeyWithEncapsulation> extracted
                = template.asyncRequestBody("direct:extract", other, SecretKeyWithEncapsulation.class);

        SecretKeyWithEncapsulation generatedKey = generated.get(10, TimeUnit.SECONDS);
        assertFalse(Arrays.equals(other.getEncapsulation(), generatedKey.getEncapsulation()),
                "the generation returned the encapsulation of the other exchange");
        assertArrayEquals(generatedKey.getEncoded(), decapsulate(generatedKey.getEncapsulation()));
        assertArrayEquals(other.getEncoded(), extracted.get(10, TimeUnit.SECONDS).getEncoded());
        assertFalse(probe.interleaved(), "an init() ran between another exchange's init() and generateKey()");
    }

    private SecretKeyWithEncapsulation encapsulate() throws Exception {
        KeyGenerator kg = newKeyGenerator();
        kg.init(new KEMGenerateSpec(keyPair.getPublic(), "AES", 128), new SecureRandom());
        return (SecretKeyWithEncapsulation) kg.generateKey();
    }

    private byte[] decapsulate(byte[] encapsulation) throws Exception {
        KeyGenerator kg = newKeyGenerator();
        kg.init(new KEMExtractSpec(keyPair.getPrivate(), encapsulation, "AES", 128), new SecureRandom());
        return kg.generateKey().getEncoded();
    }

    private static KeyGenerator newKeyGenerator() throws Exception {
        return KeyGenerator.getInstance(PQCKeyEncapsulationAlgorithms.MLKEM.getAlgorithm(),
                PQCKeyEncapsulationAlgorithms.MLKEM.getBcProvider());
    }
}
