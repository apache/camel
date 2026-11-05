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

import java.security.Security;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.pqc.lifecycle.InMemoryKeyLifecycleManager;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A stateful signature key must belong to one process. When the configured KeyLifecycleManager cannot promise that - a
 * shared vault hands the same key to every replica - signing is refused rather than risking a one-time index being
 * handed out twice.
 */
public class PQCStatefulKeyClaimTest extends CamelTestSupport {

    private static final String KEY_ID = "lms-key";

    private final UnclaimableKeyManager keyManager = new UnclaimableKeyManager();

    @Produce("direct:sign")
    protected ProducerTemplate templateSign;

    @Produce("direct:signShared")
    protected ProducerTemplate templateSignShared;

    @Produce("direct:signStateless")
    protected ProducerTemplate templateSignStateless;

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
                from("direct:signShared")
                        .to("pqc:signShared?operation=sign&signatureAlgorithm=LMS&keyLifecycleManager=#keyManager"
                            + "&allowSharedStatefulKey=true");
                from("direct:signStateless")
                        .to("pqc:signStateless?operation=sign&signatureAlgorithm=MLDSA&keyLifecycleManager=#keyManager");
            }
        };
    }

    @Test
    void aStatefulKeyThatCannotBeClaimedIsRefused() throws Exception {
        keyManager.generateKeyPair("LMS", KEY_ID);

        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> templateSign.sendBodyAndHeader("direct:sign", "Hello", PQCConstants.KEY_ID, KEY_ID));

        String message = e.getCause().getMessage();
        assertTrue(message.contains(KEY_ID), "the message must name the key: " + message);
        assertTrue(message.contains("allowSharedStatefulKey"),
                "the message must say how to proceed deliberately: " + message);
    }

    @Test
    void noSignatureIsPublishedForAKeyThatCannotBeClaimed() throws Exception {
        keyManager.generateKeyPair("LMS", KEY_ID);

        Exchange out = templateSign.send("direct:sign", e -> {
            e.getMessage().setBody("Hello");
            e.getMessage().setHeader(PQCConstants.KEY_ID, KEY_ID);
        });

        assertTrue(out.isFailed(), "the exchange must be marked failed");
        assertNull(out.getMessage().getHeader(PQCConstants.SIGNATURE),
                "the index must not be consumed by a key this process may not own");
    }

    @Test
    void allowSharedStatefulKeySignsAnyway() throws Exception {
        keyManager.generateKeyPair("LMS", KEY_ID);

        Exchange out = templateSignShared.send("direct:signShared", e -> {
            e.getMessage().setBody("Hello");
            e.getMessage().setHeader(PQCConstants.KEY_ID, KEY_ID);
        });

        assertNull(out.getException(), "the opt-in must allow the signature");
        assertTrue(out.getMessage().getHeader(PQCConstants.SIGNATURE, byte[].class).length > 0);
    }

    @Test
    void aStatelessKeyIsNotClaimedAtAll() throws Exception {
        keyManager.generateKeyPair("MLDSA", "mldsa-key");

        Exchange out = templateSignStateless.send("direct:signStateless", e -> {
            e.getMessage().setBody("Hello");
            e.getMessage().setHeader(PQCConstants.KEY_ID, "mldsa-key");
        });

        assertNull(out.getException(), "ML-DSA has no one-time index, so no claim is needed");
        assertTrue(out.getMessage().getHeader(PQCConstants.SIGNATURE, byte[].class).length > 0);
    }

    /**
     * Stands in for the vault-backed managers, which cannot promise that the key they hand out is not also held
     * somewhere else.
     */
    private static final class UnclaimableKeyManager extends InMemoryKeyLifecycleManager {

        @Override
        public boolean claimStatefulKey(String keyId) {
            return false;
        }
    }
}
