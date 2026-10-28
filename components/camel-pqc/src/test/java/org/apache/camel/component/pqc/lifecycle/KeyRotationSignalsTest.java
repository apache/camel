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
package org.apache.camel.component.pqc.lifecycle;

import java.security.KeyPair;
import java.security.Security;

import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.pqc.PQCConstants;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two rotation signals that never fired: usageCount was only advanced for stateful keys, and PENDING_ROTATION was
 * filtered out before needsRotation() was ever consulted.
 */
public class KeyRotationSignalsTest extends CamelTestSupport {

    private static final String KEY_ID = "signing-key";

    private static void registerProviders() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        if (Security.getProvider(BouncyCastlePQCProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastlePQCProvider());
        }
    }

    private final FailingReadKeyLifecycleManager keyManager = new FailingReadKeyLifecycleManager();

    /**
     * An in-memory manager whose metadata reads can be made to fail, like a vault that is unreachable.
     */
    private static final class FailingReadKeyLifecycleManager extends InMemoryKeyLifecycleManager {
        private volatile boolean failReads;

        @Override
        public KeyMetadata getKeyMetadata(String keyId) {
            if (failReads) {
                throw new IllegalStateException("simulated vault outage");
            }
            return super.getKeyMetadata(keyId);
        }
    }

    private KeyPair signingKeyPair;

    @EndpointInject("mock:sign")
    protected MockEndpoint resultSign;

    @Produce("direct:sign")
    protected ProducerTemplate templateSign;

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // re-registered per route build: until CAMEL-25795 lands, stopping the previous test's context
                // de-registers the providers for the whole JVM
                registerProviders();
                context.getRegistry().bind("keyManager", keyManager);
                // the key that signs is the one the usage is recorded against: recordKeyUsageAfterSign counts
                // against the key named by CamelPQCKeyId, so the endpoint signs with that very key pair
                signingKeyPair = keyManager.generateKeyPair("MLDSA", KEY_ID);
                context.getRegistry().bind("signingKeyPair", signingKeyPair);
                from("direct:sign")
                        .to("pqc:sign?operation=sign&signatureAlgorithm=MLDSA&keyLifecycleManager=#keyManager"
                            + "&keyPair=#signingKeyPair")
                        .to("mock:sign");
                // the key status check before signing is a deliberate fail-closed read of the same metadata; this
                // route turns it off, so the only vault read of an exchange is the one recording the usage
                from("direct:signNoStatusCheck")
                        .to("pqc:sign?operation=sign&signatureAlgorithm=MLDSA&keyLifecycleManager=#keyManager"
                            + "&keyPair=#signingKeyPair&strictKeyLifecycle=false")
                        .to("mock:sign");
            }
        };
    }

    @Test
    void signingAStatelessKeyAdvancesTheUsageCount() throws Exception {
        assertEquals(0, keyManager.getKeyMetadata(KEY_ID).getUsageCount());

        resultSign.expectedMessageCount(3);
        for (int i = 0; i < 3; i++) {
            templateSign.sendBodyAndHeader("Hello", PQCConstants.KEY_ID, KEY_ID);
        }
        resultSign.assertIsSatisfied();

        // ML-DSA is not a stateful key; before the fix the counter stayed at zero and maxKeyUsage never fired
        assertEquals(3, keyManager.getKeyMetadata(KEY_ID).getUsageCount(),
                "every signing operation must advance the usage count");

        assertTrue(keyManager.needsRotation(KEY_ID, null, 3),
                "a key at its maxKeyUsage must be reported as needing rotation");
    }

    @Test
    void aFailedMetadataReadKeepsTheSignatureOfAStatelessKey() throws Exception {
        resultSign.expectedMessageCount(1);

        keyManager.failReads = true;
        try {
            template.sendBodyAndHeader("direct:signNoStatusCheck", "Hello", PQCConstants.KEY_ID, KEY_ID);
        } finally {
            keyManager.failReads = false;
        }

        resultSign.assertIsSatisfied();
        // ML-DSA is not stateful, so no one-time index was consumed: only the usage count is lost
        assertNotNull(resultSign.getExchanges().get(0).getMessage().getHeader(PQCConstants.SIGNATURE, byte[].class),
                "a vault that cannot be read must not cost the signature of a stateless key");
        assertEquals(0, keyManager.getKeyMetadata(KEY_ID).getUsageCount(),
                "the usage that could not be recorded must not be counted afterwards");
    }

    @Test
    void aPendingRotationKeyIsSelectedByTheDefaultFilter() throws Exception {
        KeyMetadata metadata = keyManager.getKeyMetadata(KEY_ID);
        metadata.setStatus(KeyMetadata.KeyStatus.PENDING_ROTATION);
        keyManager.updateKeyMetadata(KEY_ID, metadata);

        KeyRotationScheduler scheduler = new KeyRotationScheduler(keyManager);
        scheduler.setCamelContext(context);

        assertTrue(scheduler.getKeyFilter().test(keyManager.getKeyMetadata(KEY_ID)),
                "the default key filter must not drop a PENDING_ROTATION key before needsRotation() is consulted");

        int rotated = scheduler.checkAndRotate();

        assertEquals(1, rotated, "a PENDING_ROTATION key must be rotated by the default configuration");
    }
}
