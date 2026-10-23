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
import java.security.Security;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.pqc.lifecycle.InMemoryKeyLifecycleManager;
import org.apache.camel.component.pqc.lifecycle.KeyMetadata;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Signing consumes the one-time index of a stateful key, so the advanced key has to be persisted before the signature
 * is published. If persisting fails, the exchange must fail rather than hand out a signature whose index advance was
 * never recorded.
 */
public class PQCStatefulKeyPersistOrderTest extends CamelTestSupport {

    private static final String KEY_ID = "lms-key";

    private final FailingOnStoreKeyManager keyManager = new FailingOnStoreKeyManager();

    @Produce("direct:sign")
    protected ProducerTemplate templateSign;

    @BeforeEach
    void addProviders() {
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
            public void configure() {
                context.getRegistry().bind("keyManager", keyManager);
                from("direct:sign")
                        .to("pqc:sign?operation=sign&signatureAlgorithm=LMS&keyLifecycleManager=#keyManager");
            }
        };
    }

    @Test
    void aFailureToPersistTheIndexFailsTheExchange() throws Exception {
        keyManager.generateKeyPair("LMS", KEY_ID);
        keyManager.failOnStore = true;

        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> templateSign.sendBodyAndHeader("direct:sign", "Hello", PQCConstants.KEY_ID, KEY_ID));

        assertTrue(e.getCause().getMessage().contains("storeKey failed"),
                "the persistence failure must surface: " + e.getCause());
    }

    @Test
    void theSignatureIsNotPublishedWhenPersistenceFails() throws Exception {
        keyManager.generateKeyPair("LMS", KEY_ID);
        keyManager.failOnStore = true;

        Exchange out = templateSign.send("direct:sign", e -> {
            e.getMessage().setBody("Hello");
            e.getMessage().setHeader(PQCConstants.KEY_ID, KEY_ID);
        });

        assertTrue(out.isFailed(), "the exchange must be marked failed");
        assertNull(out.getMessage().getHeader(PQCConstants.SIGNATURE),
                "no signature may be published when the advanced index could not be persisted");
    }

    @Test
    void aSuccessfulSignStillPersistsAndPublishes() throws Exception {
        keyManager.generateKeyPair("LMS", KEY_ID);
        // generateKeyPair stores the key itself; count only what the signing operation does
        keyManager.storeCount = 0;
        keyManager.failOnStore = false;

        Exchange out = templateSign.send("direct:sign", e -> {
            e.getMessage().setBody("Hello");
            e.getMessage().setHeader(PQCConstants.KEY_ID, KEY_ID);
        });

        assertNull(out.getException(), "signing must succeed");
        assertTrue(out.getMessage().getHeader(PQCConstants.SIGNATURE, byte[].class).length > 0);
        assertEquals(1, keyManager.storeCount, "the advanced stateful key must have been stored");
        assertEquals(1, keyManager.getKeyMetadata(KEY_ID).getUsageCount());
    }

    /**
     * An InMemoryKeyLifecycleManager whose storeKey can be made to fail, standing in for an unreachable vault.
     */
    private static final class FailingOnStoreKeyManager extends InMemoryKeyLifecycleManager {

        private boolean failOnStore;
        private int storeCount;

        @Override
        public void storeKey(String keyId, KeyPair keyPair, KeyMetadata metadata) {
            if (failOnStore) {
                throw new IllegalStateException("storeKey failed");
            }
            storeCount++;
            super.storeKey(keyId, keyPair, metadata);
        }
    }
}
