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
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Security;
import java.security.Signature;

import org.apache.camel.BindToRegistry;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.bouncycastle.jcajce.spec.MLDSAParameterSpec;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A String body must be signed as UTF-8, not with the JVM default charset, so that a signature produced on one JVM
 * verifies on another with a different default. This pins the UTF-8 contract by verifying the produced signature
 * independently over the UTF-8 bytes of the payload (and confirming it does not match the ISO-8859-1 bytes).
 */
public class PQCSignatureCharsetTest extends CamelTestSupport {

    private static final String NON_ASCII = "héllo wörld — ünïcode";

    private KeyPair keyPair;

    @EndpointInject("mock:sign")
    protected MockEndpoint resultSign;

    @Produce("direct:sign")
    protected ProducerTemplate templateSign;

    @BeforeAll
    public static void startup() {
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
                from("direct:sign").to("pqc:sign?operation=sign").to("mock:sign");
            }
        };
    }

    @Test
    void testStringBodyIsSignedAsUtf8() throws Exception {
        resultSign.expectedMessageCount(1);
        templateSign.sendBody(NON_ASCII);
        resultSign.assertIsSatisfied();

        byte[] signature = resultSign.getExchanges().get(0).getMessage().getHeader(PQCConstants.SIGNATURE, byte[].class);
        assertNotNull(signature);

        // The signature must validate against the UTF-8 bytes of the payload
        assertTrue(verifyOver(NON_ASCII.getBytes(StandardCharsets.UTF_8), signature),
                "a String body must be signed as UTF-8");
        // ... and must not validate against a different encoding of the same non-ASCII text
        assertFalse(verifyOver(NON_ASCII.getBytes(StandardCharsets.ISO_8859_1), signature),
                "a String body must not be signed with the platform charset (ISO-8859-1 here)");
    }

    private boolean verifyOver(byte[] data, byte[] signature) throws Exception {
        Signature verifier = Signature.getInstance(PQCSignatureAlgorithms.MLDSA.getAlgorithm(),
                PQCSignatureAlgorithms.MLDSA.getBcProvider());
        verifier.initVerify(keyPair.getPublic());
        verifier.update(data);
        return verifier.verify(signature);
    }

    @BindToRegistry("Keypair")
    public KeyPair setKeyPair()
            throws NoSuchAlgorithmException, NoSuchProviderException, InvalidAlgorithmParameterException {
        KeyPairGenerator kpGen = KeyPairGenerator.getInstance(PQCSignatureAlgorithms.MLDSA.getAlgorithm(),
                PQCSignatureAlgorithms.MLDSA.getBcProvider());
        kpGen.initialize(MLDSAParameterSpec.ml_dsa_65);
        keyPair = kpGen.generateKeyPair();
        return keyPair;
    }

    @BindToRegistry("Signer")
    public Signature getSigner() throws NoSuchAlgorithmException, NoSuchProviderException {
        return Signature.getInstance(PQCSignatureAlgorithms.MLDSA.getAlgorithm(),
                PQCSignatureAlgorithms.MLDSA.getBcProvider());
    }
}
