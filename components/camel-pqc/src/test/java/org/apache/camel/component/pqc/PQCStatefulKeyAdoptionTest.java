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
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.Security;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;

import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.pqc.lifecycle.InMemoryKeyLifecycleManager;
import org.apache.camel.component.pqc.lifecycle.KeyMetadata;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Taking the claim is not enough on its own. The key pair is read once when the producer starts, so an instance that
 * waits for the claim while another instance keeps signing would otherwise resume from its own stale index and hand out
 * every index the other one already used - the exact failover this feature exists to prevent.
 * <p/>
 * The two instances are modelled with two independent copies of one LMS key, decoded separately from the same PKCS#8
 * encoding: signing with one leaves the other untouched, as it would across two processes.
 */
public class PQCStatefulKeyAdoptionTest extends CamelTestSupport {

    private static final String KEY_ID = "lms-key";
    private static final int BURNED_ELSEWHERE = 2;

    private final InMemoryKeyLifecycleManager keyManager = new InMemoryKeyLifecycleManager();

    private KeyPair loadedAtStartup;
    private long remainingAtStartup;
    private long remainingInStore;

    @Produce("direct:sign")
    protected ProducerTemplate templateSign;

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        if (Security.getProvider(BouncyCastlePQCProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastlePQCProvider());
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                KeyPair original = keyManager.generateKeyPair("LMS", KEY_ID);
                byte[] encoded = original.getPrivate().getEncoded();

                // what this producer loaded when it started
                loadedAtStartup = new KeyPair(original.getPublic(), decode(encoded));
                // what the other instance has been signing with, stored ahead of us
                KeyPair elsewhere = new KeyPair(original.getPublic(), decode(encoded));
                burn(elsewhere, BURNED_ELSEWHERE);

                KeyMetadata metadata = keyManager.getKeyMetadata(KEY_ID);
                keyManager.storeKey(KEY_ID, elsewhere, metadata);

                remainingAtStartup = PQCProducer.getStatefulKeyRemaining(loadedAtStartup.getPrivate());
                remainingInStore = PQCProducer.getStatefulKeyRemaining(elsewhere.getPrivate());

                context.getRegistry().bind("keyManager", keyManager);
                context.getRegistry().bind("startupKeyPair", loadedAtStartup);
                from("direct:sign")
                        .to("pqc:sign?operation=sign&signatureAlgorithm=LMS&keyLifecycleManager=#keyManager"
                            + "&keyPair=#startupKeyPair");
            }
        };
    }

    @Test
    void aStoredKeyThatMovedAheadIsAdoptedOnTheFirstSign() throws Exception {
        assertEquals(remainingAtStartup - BURNED_ELSEWHERE, remainingInStore,
                "the stored key must be ahead of the one loaded at startup for this test to mean anything");

        templateSign.sendBodyAndHeader("direct:sign", "Hello", PQCConstants.KEY_ID, KEY_ID);

        long remainingAfter = PQCProducer.getStatefulKeyRemaining(keyManager.getKey(KEY_ID).getPrivate());

        // Signing from the stored index consumes one more of its remaining signatures. Signing from the stale
        // startup index would instead store a key with more remaining than the store already had - every index in
        // between handed out twice.
        assertEquals(remainingInStore - 1, remainingAfter,
                "the producer must sign from the stored index, not from the one it loaded at startup");
        assertTrue(remainingAfter < remainingInStore, "the stored key must have advanced, not rewound");
    }

    private static PrivateKey decode(byte[] pkcs8) throws Exception {
        return KeyFactory.getInstance("LMS", "BC").generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
    }

    /**
     * Consumes signatures from a stateful key, standing in for another instance having used it.
     */
    private static void burn(KeyPair keyPair, int count) throws Exception {
        Signature signer = Signature.getInstance("LMS", "BC");
        for (int i = 0; i < count; i++) {
            signer.initSign(keyPair.getPrivate());
            signer.update("burn".getBytes(StandardCharsets.UTF_8));
            signer.sign();
        }
    }
}
