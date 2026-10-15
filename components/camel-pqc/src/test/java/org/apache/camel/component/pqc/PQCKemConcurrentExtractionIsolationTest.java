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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.crypto.KeyGenerator;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.Registry;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.bouncycastle.jcajce.SecretKeyWithEncapsulation;
import org.bouncycastle.jcajce.spec.KEMGenerateSpec;
import org.bouncycastle.jcajce.spec.MLKEMParameterSpec;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * When no KeyGenerator is configured the producer creates one itself, and a Camel producer is a singleton invoked
 * concurrently. Every encapsulation extracted through the same producer must yield its own secret, no matter how many
 * exchanges are in flight at once.
 */
class PQCKemConcurrentExtractionIsolationTest extends CamelTestSupport {

    private static final int THREADS = 8;
    private static final int MESSAGES_PER_THREAD = 40;

    private KeyPair keyPair;

    @BeforeAll
    static void startup() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        if (Security.getProvider(BouncyCastlePQCProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastlePQCProvider());
        }
    }

    @Override
    protected void bindToRegistry(Registry registry) throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance(PQCKeyEncapsulationAlgorithms.MLKEM.getAlgorithm(),
                PQCKeyEncapsulationAlgorithms.MLKEM.getBcProvider());
        kpg.initialize(MLKEMParameterSpec.ml_kem_512, new SecureRandom());
        keyPair = kpg.generateKeyPair();
        registry.bind("kemKeyPair", keyPair);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:extract")
                        .to("pqc:extract?operation=extractSecretKeyEncapsulation&symmetricKeyAlgorithm=AES"
                            + "&keyEncapsulationAlgorithm=MLKEM&keyPair=#kemKeyPair");
            }
        };
    }

    @Test
    void everyConcurrentExtractionReturnsItsOwnSecret() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            List<Callable<Integer>> work = new ArrayList<>();
            for (int t = 0; t < THREADS; t++) {
                work.add(() -> {
                    KeyGenerator sender = KeyGenerator.getInstance(PQCKeyEncapsulationAlgorithms.MLKEM.getAlgorithm(),
                            PQCKeyEncapsulationAlgorithms.MLKEM.getBcProvider());
                    int matching = 0;
                    for (int i = 0; i < MESSAGES_PER_THREAD; i++) {
                        sender.init(new KEMGenerateSpec(keyPair.getPublic(), "AES", 128), new SecureRandom());
                        SecretKeyWithEncapsulation sent = (SecretKeyWithEncapsulation) sender.generateKey();
                        SecretKeyWithEncapsulation extracted
                                = template.requestBody("direct:extract", sent, SecretKeyWithEncapsulation.class);
                        if (Arrays.equals(sent.getEncoded(), extracted.getEncoded())) {
                            matching++;
                        }
                    }
                    return matching;
                });
            }

            int matching = 0;
            for (Future<Integer> f : pool.invokeAll(work, 5, TimeUnit.MINUTES)) {
                matching += f.get();
            }

            assertEquals(THREADS * MESSAGES_PER_THREAD, matching);
        } finally {
            pool.shutdownNow();
        }
    }
}
