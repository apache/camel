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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two rotation signals that never fired: usageCount was only advanced for stateful keys, and PENDING_ROTATION was
 * filtered out before needsRotation() was ever consulted.
 */
public class KeyRotationSignalsTest extends CamelTestSupport {

    private static final String KEY_ID = "signing-key";

    private final InMemoryKeyLifecycleManager keyManager = new InMemoryKeyLifecycleManager();

    @EndpointInject("mock:sign")
    protected MockEndpoint resultSign;

    @Produce("direct:sign")
    protected ProducerTemplate templateSign;

    @BeforeEach
    void addProvider() {
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
                        .to("pqc:sign?operation=sign&signatureAlgorithm=MLDSA&keyLifecycleManager=#keyManager")
                        .to("mock:sign");
            }
        };
    }

    @Test
    void signingAStatelessKeyAdvancesTheUsageCount() throws Exception {
        keyManager.generateKeyPair("MLDSA", KEY_ID);
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
    void aPendingRotationKeyIsSelectedByTheDefaultFilter() throws Exception {
        keyManager.generateKeyPair("MLDSA", KEY_ID);
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
