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
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.crypto.KeyGenerator;

import org.apache.camel.component.pqc.InterleavingProbe;
import org.apache.camel.component.pqc.PQCKemLocks;
import org.apache.camel.component.pqc.PQCKeyEncapsulationAlgorithms;
import org.apache.camel.component.pqc.ProbedKeyGenerator;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Two data formats, each with its own KeyGenerator, work on the same KEM algorithm. For an algorithm that keeps state
 * outside the KeyGenerator (SABER) one operation must not run its {@code init()} between the {@code init()} and the
 * {@code generateKey()} of the other. The first operation is held between those two calls, so the outcome does not
 * depend on thread scheduling.
 */
class PQCDataFormatSharedEngineIsolationTest {

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
    void overlappingMarshalAndUnmarshalOnDifferentKeyGeneratorsKeepTheirOwnSecrets(PQCKeyEncapsulationAlgorithms kem)
            throws Exception {
        KeyPair keyPair = KeyPairGenerator.getInstance(kem.getAlgorithm(), kem.getBcProvider()).generateKeyPair();
        InterleavingProbe probe = new InterleavingProbe();
        PQCDataFormat marshaller = startedDataFormat(kem, keyPair, probe);
        PQCDataFormat unmarshaller = startedDataFormat(kem, keyPair, probe);
        byte[] otherMessage = marshal(startedDataFormat(kem, keyPair, null), "other");

        probe.holdNextResult();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        byte[] mine;
        String other;
        try {
            Future<byte[]> marshalled = pool.submit(() -> marshal(marshaller, "mine"));
            assertThat(probe.awaitHolding(SECONDS, TimeUnit.SECONDS)).isTrue();
            Future<String> unmarshalled = pool.submit(() -> unmarshal(unmarshaller, otherMessage));
            mine = marshalled.get(SECONDS, TimeUnit.SECONDS);
            other = unmarshalled.get(SECONDS, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        assertThat(unmarshal(unmarshaller, mine)).isEqualTo("mine");
        assertThat(other).isEqualTo("other");
        if (PQCKemLocks.sharesEngineAcrossKeyGenerators(kem)) {
            assertThat(probe.interleaved())
                    .as("%s: an init() of another KeyGenerator ran between init() and generateKey()", kem)
                    .isFalse();
        }
    }

    private static PQCDataFormat startedDataFormat(PQCKeyEncapsulationAlgorithms kem, KeyPair keyPair, InterleavingProbe probe)
            throws Exception {
        PQCDataFormat dataFormat = new PQCDataFormat(kem.name(), "AES", keyPair);
        if (probe != null) {
            dataFormat.setKeyGenerator(
                    ProbedKeyGenerator.wrap(KeyGenerator.getInstance(kem.getAlgorithm(), kem.getBcProvider()), probe));
        }
        dataFormat.start();
        return dataFormat;
    }

    private static byte[] marshal(PQCDataFormat dataFormat, String body) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        dataFormat.marshal(new DefaultExchange(new DefaultCamelContext()), body, out);
        return out.toByteArray();
    }

    private static String unmarshal(PQCDataFormat dataFormat, byte[] data) throws Exception {
        byte[] plaintext = (byte[]) dataFormat.unmarshal(new DefaultExchange(new DefaultCamelContext()),
                new ByteArrayInputStream(data));
        return new String(plaintext, StandardCharsets.UTF_8);
    }
}
