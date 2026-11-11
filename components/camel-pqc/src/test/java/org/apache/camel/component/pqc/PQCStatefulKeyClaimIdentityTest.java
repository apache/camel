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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.Security;
import java.security.Signature;
import java.util.List;
import java.util.stream.Stream;

import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.pqc.lifecycle.FileBasedKeyLifecycleManager;
import org.apache.camel.component.pqc.lifecycle.KeyLifecycleManager;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The claim on a stateful key is on the key material, not on the name an exchange gives the key: whatever CamelPQCKeyId
 * values the exchanges carry, and also when they carry none, one producer signing with one key holds one lock, named
 * from the fingerprint of the public key.
 */
public class PQCStatefulKeyClaimIdentityTest extends CamelTestSupport {

    @TempDir
    Path tempDir;

    private static final String KEY_ID = "my-key";
    private static final int BURNED_ELSEWHERE = 2;

    private Path keyDir;
    private KeyPair keyPair;
    private FileBasedKeyLifecycleManager keyManager;
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
                keyDir = tempDir.resolve("keys");

                // the previous owner of the key: it generated the key, signed with it and stored it back ahead
                FileBasedKeyLifecycleManager leaving = new FileBasedKeyLifecycleManager(keyDir.toString());
                KeyPair original = leaving.generateKeyPair("LMS", KEY_ID);

                // this producer loaded the key at startup, as documented, through its own manager, which caches it
                keyManager = new FileBasedKeyLifecycleManager(keyDir.toString());
                keyPair = keyManager.getKey(KEY_ID);
                remainingAtStartup = PQCProducer.getStatefulKeyRemaining(keyPair.getPrivate());

                leaving.claimStatefulKey(KEY_ID, original.getPublic());
                burn(original, BURNED_ELSEWHERE);
                leaving.storeKey(KEY_ID, original, leaving.getKeyMetadata(KEY_ID));
                leaving.releaseStatefulKey(KEY_ID, original.getPublic());
                remainingInStore = PQCProducer.getStatefulKeyRemaining(original.getPrivate());

                context.getRegistry().bind("keyManager", keyManager);
                context.getRegistry().bind("startupKeyPair", keyPair);
                from("direct:sign")
                        .to("pqc:sign?operation=sign&signatureAlgorithm=LMS&keyLifecycleManager=#keyManager"
                            + "&keyPair=#startupKeyPair");
            }
        };
    }

    @Test
    void theClaimFollowsTheKeyWhateverNameTheExchangesGiveIt() throws Exception {
        sign("name-a");
        sign("name-b");

        // one key, one lock: a name per exchange would have taken one lock per name, and none of them on the key
        assertEquals(List.of(KeyLifecycleManager.fingerprint(keyPair.getPublic()) + ".lock"), lockFiles());
    }

    @Test
    void anExchangeWithoutTheHeaderIsClaimedToo() throws Exception {
        sign(null);

        assertEquals(List.of(KeyLifecycleManager.fingerprint(keyPair.getPublic()) + ".lock"), lockFiles(),
                "a key the exchange gives no name to is still claimed, by its fingerprint");
    }

    @Test
    void anUnnamedFirstExchangeDoesNotHideTheStoredIndexFromALaterNamedOne() throws Exception {
        assertEquals(remainingAtStartup - BURNED_ELSEWHERE, remainingInStore,
                "the stored key must be ahead of the one loaded at startup for this test to mean anything");

        // The first exchange after the handover names no key: it takes the claim, which must drop the cached copy.
        // Nothing names the stored key to read back, so this signature still comes from the key loaded at startup.
        sign(null);
        // The next one names the key, re-reads it, and must find the stored index, not the one loaded at startup.
        // Without the eviction the re-read would serve the cached startup copy, adopt nothing, and the store would
        // end up holding that copy at remainingInStore, as if the previous owner had never advanced the key.
        sign(KEY_ID);

        long remainingAfter = PQCProducer.getStatefulKeyRemaining(keyManager.getKey(KEY_ID).getPrivate());
        assertEquals(remainingInStore - 1, remainingAfter,
                "the named signature must come from the stored index, one past where the previous owner left it");
    }

    private void sign(String keyId) {
        Exchange out = templateSign.send("direct:sign", e -> {
            e.getMessage().setBody("Hello");
            if (keyId != null) {
                e.getMessage().setHeader(PQCConstants.KEY_ID, keyId);
            }
        });
        assertNull(out.getException(), "signing must succeed");
        assertTrue(out.getMessage().getHeader(PQCConstants.SIGNATURE, byte[].class).length > 0);
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

    private List<String> lockFiles() throws IOException {
        try (Stream<Path> files = Files.list(keyDir)) {
            return files.map(f -> f.getFileName().toString()).filter(n -> n.endsWith(".lock")).sorted().toList();
        }
    }
}
