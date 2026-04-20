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
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.Security;
import java.security.Signature;

import org.apache.camel.BindToRegistry;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.pqc.stateful.StatefulKeyState;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;
import org.bouncycastle.pqc.jcajce.spec.XMSSParameterSpec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for stateful key usage tracking with XMSS. Uses a very small tree height (2) so the key has only 4 total
 * signatures, making it feasible to test exhaustion and threshold warnings.
 */
public class PQCStatefulKeyTrackingTest extends CamelTestSupport {

    @EndpointInject("mock:signed")
    protected MockEndpoint resultSigned;

    @EndpointInject("mock:state")
    protected MockEndpoint resultState;

    @Produce("direct:sign")
    protected ProducerTemplate templateSign;

    @Produce("direct:getState")
    protected ProducerTemplate templateGetState;

    public PQCStatefulKeyTrackingTest() throws NoSuchAlgorithmException {
    }

    private static void ensureProviders() {
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
                from("direct:sign")
                        .to("pqc:sign?operation=sign&statefulKeyWarningThreshold=0.5")
                        .to("mock:signed");

                from("direct:getState")
                        .to("pqc:state?operation=getKeyState")
                        .to("mock:state");
            }
        };
    }

    @BindToRegistry("Keypair")
    public KeyPair setKeyPair() throws Exception {
        ensureProviders();
        KeyPairGenerator kpGen = KeyPairGenerator.getInstance(
                PQCSignatureAlgorithms.XMSS.getAlgorithm(),
                PQCSignatureAlgorithms.XMSS.getBcProvider());
        kpGen.initialize(new XMSSParameterSpec(2, XMSSParameterSpec.SHA256), new SecureRandom());
        return kpGen.generateKeyPair();
    }

    @BindToRegistry("Signer")
    public Signature getSigner() throws NoSuchAlgorithmException, NoSuchProviderException {
        ensureProviders();
        return Signature.getInstance(
                PQCSignatureAlgorithms.XMSS.getAlgorithm(),
                PQCSignatureAlgorithms.XMSS.getBcProvider());
    }

    @Test
    void testSignDecreasesRemainingSignatures() throws Exception {
        // Get initial state
        resultState.expectedMessageCount(1);
        templateGetState.sendBody("check");
        resultState.assertIsSatisfied();

        StatefulKeyState initialState = resultState.getExchanges().get(0)
                .getMessage().getHeader(PQCConstants.KEY_STATE, StatefulKeyState.class);
        assertNotNull(initialState);
        long initialRemaining = initialState.getUsagesRemaining();
        assertTrue(initialRemaining > 0, "Initial key should have remaining signatures");

        // Sign once
        resultSigned.expectedMessageCount(1);
        templateSign.sendBody("Hello");
        resultSigned.assertIsSatisfied();

        // Get state after signing
        resultState.reset();
        resultState.expectedMessageCount(1);
        templateGetState.sendBody("check");
        resultState.assertIsSatisfied();

        StatefulKeyState afterState = resultState.getExchanges().get(0)
                .getMessage().getHeader(PQCConstants.KEY_STATE, StatefulKeyState.class);
        assertNotNull(afterState);
        assertEquals(initialRemaining - 1, afterState.getUsagesRemaining(),
                "Remaining signatures should decrease by 1 after signing");
    }

    @Test
    void testKeyExhaustion() throws Exception {
        ensureProviders();

        // Create a fresh key with height=2 (4 signatures)
        KeyPairGenerator kpGen = KeyPairGenerator.getInstance(
                PQCSignatureAlgorithms.XMSS.getAlgorithm(),
                PQCSignatureAlgorithms.XMSS.getBcProvider());
        kpGen.initialize(new XMSSParameterSpec(2, XMSSParameterSpec.SHA256), new SecureRandom());
        KeyPair exhaustionKeyPair = kpGen.generateKeyPair();

        // Sign 4 times to exhaust the key
        Signature xmssSigner = Signature.getInstance(
                PQCSignatureAlgorithms.XMSS.getAlgorithm(),
                PQCSignatureAlgorithms.XMSS.getBcProvider());

        for (int i = 0; i < 4; i++) {
            xmssSigner.initSign(exhaustionKeyPair.getPrivate());
            xmssSigner.update(("message" + i).getBytes());
            xmssSigner.sign();
        }

        // Now the key should be exhausted
        org.bouncycastle.pqc.jcajce.interfaces.XMSSPrivateKey xmssPriv
                = (org.bouncycastle.pqc.jcajce.interfaces.XMSSPrivateKey) exhaustionKeyPair.getPrivate();
        assertEquals(0, xmssPriv.getUsagesRemaining(), "Key should be exhausted after 4 signatures with height=2");
    }

    @Test
    void testStatefulKeyStateNotExhausted() throws Exception {
        ensureProviders();

        KeyPairGenerator kpGen = KeyPairGenerator.getInstance(
                PQCSignatureAlgorithms.XMSS.getAlgorithm(),
                PQCSignatureAlgorithms.XMSS.getBcProvider());
        kpGen.initialize(new XMSSParameterSpec(2, XMSSParameterSpec.SHA256), new SecureRandom());
        KeyPair freshKeyPair = kpGen.generateKeyPair();

        org.bouncycastle.pqc.jcajce.interfaces.XMSSPrivateKey xmssPriv
                = (org.bouncycastle.pqc.jcajce.interfaces.XMSSPrivateKey) freshKeyPair.getPrivate();

        StatefulKeyState state = new StatefulKeyState(
                xmssPriv.getAlgorithm(), xmssPriv.getIndex(), xmssPriv.getUsagesRemaining());

        assertFalse(state.isExhausted(), "Fresh key should not be exhausted");
        assertEquals(4, state.getUsagesRemaining(), "Fresh XMSS key with height=2 should have 4 remaining");
        assertEquals(0, state.getIndex(), "Fresh key should have index 0");
    }

    @Test
    void testStatefulKeyStateExhausted() {
        StatefulKeyState state = new StatefulKeyState("XMSS", 4, 0);

        assertTrue(state.isExhausted(), "Key with 0 remaining should be exhausted");
        assertEquals(0, state.getUsagesRemaining());
        assertEquals(4, state.getIndex());
    }

    @Test
    void testProducerExhaustionThrowsException() throws Exception {
        // Sign 4 times to exhaust the key (XMSS height=2 allows exactly 4)
        for (int i = 0; i < 4; i++) {
            resultSigned.reset();
            resultSigned.expectedMessageCount(1);
            templateSign.sendBody("message" + i);
            resultSigned.assertIsSatisfied();
        }

        // The 5th sign attempt through the producer should throw IllegalStateException
        CamelExecutionException ex = assertThrows(CamelExecutionException.class,
                () -> templateSign.sendBody("message4"));
        assertInstanceOf(IllegalStateException.class, ex.getCause());
        assertTrue(ex.getCause().getMessage().contains("exhausted"),
                "Exception message should mention exhaustion");
    }

    @Test
    void testWarningThresholdValidation() {
        PQCConfiguration config = new PQCConfiguration();

        // Valid values
        config.setStatefulKeyWarningThreshold(0.0);
        assertEquals(0.0, config.getStatefulKeyWarningThreshold());

        config.setStatefulKeyWarningThreshold(0.5);
        assertEquals(0.5, config.getStatefulKeyWarningThreshold());

        config.setStatefulKeyWarningThreshold(1.0);
        assertEquals(1.0, config.getStatefulKeyWarningThreshold());

        // Invalid values
        assertThrows(IllegalArgumentException.class,
                () -> config.setStatefulKeyWarningThreshold(-0.1));
        assertThrows(IllegalArgumentException.class,
                () -> config.setStatefulKeyWarningThreshold(1.1));
    }
}
