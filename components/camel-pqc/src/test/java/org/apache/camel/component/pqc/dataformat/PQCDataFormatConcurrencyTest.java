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
package org.apache.camel.component.pqc.dataformat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
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

import org.apache.camel.component.pqc.InterleavingProbe;
import org.apache.camel.component.pqc.PQCKeyEncapsulationAlgorithms;
import org.apache.camel.component.pqc.ProbedKeyGenerator;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.bouncycastle.jcajce.spec.MLKEMParameterSpec;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A data format instance is shared by every exchange of the routes that use it, while a KEM KeyGenerator keeps the spec
 * passed to init() until generateKey() reads it. Every marshal and unmarshal must work with its own spec, whether the
 * data format creates the KeyGenerator or one is configured.
 */
class PQCDataFormatConcurrencyTest extends CamelTestSupport {

    private static final int THREADS = 8;
    private static final int MESSAGES_PER_THREAD = 40;

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
    void concurrentRoundTripsThroughOneInstanceAllSucceed() throws Exception {
        PQCDataFormat dataFormat = startedDataFormat(newKeyPair(), null);

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            List<Callable<Integer>> work = new ArrayList<>();
            for (int t = 0; t < THREADS; t++) {
                final int thread = t;
                work.add(() -> {
                    int matching = 0;
                    for (int i = 0; i < MESSAGES_PER_THREAD; i++) {
                        String message = "message-" + thread + "-" + i;
                        if (message.equals(unmarshal(dataFormat, marshal(dataFormat, message)))) {
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
        assertNull(dataFormat.getKeyGenerator(), "the data format should not keep a KeyGenerator to share");
    }

    @Test
    void overlappingMarshalAndUnmarshalOnConfiguredKeyGeneratorKeepTheirOwnKeys() throws Exception {
        KeyPair keyPair = newKeyPair();
        byte[] otherMessage = marshal(startedDataFormat(keyPair, null), "other");

        InterleavingProbe probe = new InterleavingProbe();
        PQCDataFormat dataFormat = startedDataFormat(keyPair, ProbedKeyGenerator.wrap(
                KeyGenerator.getInstance(PQCKeyEncapsulationAlgorithms.MLKEM.getAlgorithm(),
                        PQCKeyEncapsulationAlgorithms.MLKEM.getBcProvider()),
                probe));

        probe.holdNextResult();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        byte[] mine;
        String other;
        try {
            Future<byte[]> marshalled = pool.submit(() -> marshal(dataFormat, "mine"));
            assertTrue(probe.awaitHolding(10, TimeUnit.SECONDS));
            Future<String> unmarshalled = pool.submit(() -> unmarshal(dataFormat, otherMessage));
            mine = marshalled.get(10, TimeUnit.SECONDS);
            other = unmarshalled.get(10, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        assertFalse(Arrays.equals(encapsulationOf(otherMessage), encapsulationOf(mine)),
                "the marshal used the encapsulation of the other operation");
        assertEquals("mine", unmarshal(dataFormat, mine));
        assertEquals("other", other);
        assertFalse(probe.interleaved(), "an init() ran between another operation's init() and generateKey()");
    }

    private static KeyPair newKeyPair() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance(PQCKeyEncapsulationAlgorithms.MLKEM.getAlgorithm(),
                PQCKeyEncapsulationAlgorithms.MLKEM.getBcProvider());
        kpg.initialize(MLKEMParameterSpec.ml_kem_512, new SecureRandom());
        return kpg.generateKeyPair();
    }

    private static PQCDataFormat startedDataFormat(KeyPair keyPair, KeyGenerator keyGenerator) {
        PQCDataFormat dataFormat = new PQCDataFormat("MLKEM", "AES", keyPair);
        dataFormat.setKeyGenerator(keyGenerator);
        dataFormat.start();
        return dataFormat;
    }

    private byte[] marshal(PQCDataFormat dataFormat, String body) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        dataFormat.marshal(new DefaultExchange(context), body, out);
        return out.toByteArray();
    }

    private String unmarshal(PQCDataFormat dataFormat, byte[] data) throws Exception {
        byte[] plaintext = (byte[]) dataFormat.unmarshal(new DefaultExchange(context), new ByteArrayInputStream(data));
        return new String(plaintext, StandardCharsets.UTF_8);
    }

    private static byte[] encapsulationOf(byte[] message) {
        ByteBuffer buffer = ByteBuffer.wrap(message);
        byte[] encapsulation = new byte[buffer.getInt()];
        buffer.get(encapsulation);
        return encapsulation;
    }
}
