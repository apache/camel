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

import org.apache.camel.CamelContext;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.pqc.lifecycle.InMemoryKeyLifecycleManager;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * deleteKeyState must not report success when nothing was deleted: for a stateful key the caller would believe the
 * exhausted state is gone and could reuse the key.
 */
public class PQCDeleteKeyStateTest extends CamelTestSupport {

    private final InMemoryKeyLifecycleManager keyManager = new InMemoryKeyLifecycleManager();

    @Produce("direct:withManager")
    protected ProducerTemplate withManager;

    @Produce("direct:withoutManager")
    protected ProducerTemplate withoutManager;

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
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        // keyLifecycleManager is autowired, so the registry bean would also reach the endpoint that is meant to
        // have none, and the branch under test would never run
        context.setAutowiredEnabled(false);
        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                context.getRegistry().bind("keyManager", keyManager);
                from("direct:withManager")
                        .to("pqc:a?operation=deleteKeyState&signatureAlgorithm=MLDSA&keyLifecycleManager=#keyManager");
                from("direct:withoutManager")
                        .to("pqc:b?operation=deleteKeyState&signatureAlgorithm=MLDSA");
            }
        };
    }

    @Test
    void deletingWithoutAKeyLifecycleManagerFailsLoudly() {
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> withoutManager.sendBodyAndHeader("direct:withoutManager", "x", PQCConstants.KEY_ID, "k1"));

        Throwable cause = e.getCause();
        assertInstanceOf(IllegalStateException.class, cause, "expected the same error the other lifecycle operations"
                                                             + " raise, but got: " + cause);
        assertTrue(cause.getMessage().contains("KeyLifecycleManager"), cause.getMessage());
    }

    @Test
    void deletingWithAManagerRemovesTheKey() throws Exception {
        keyManager.generateKeyPair("MLDSA", "k1");
        assertNotNull(keyManager.getKeyMetadata("k1"));

        withManager.sendBodyAndHeader("direct:withManager", "x", PQCConstants.KEY_ID, "k1");

        assertNull(keyManager.getKeyMetadata("k1"), "the key state must actually be gone");
    }

    @Test
    void deletingWithoutAKeyIdIsRejected() {
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> withManager.sendBody("direct:withManager", "x"));

        assertTrue(e.getCause().getMessage().contains(PQCConstants.KEY_ID), e.getCause().getMessage());
    }
}
