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

import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test for hybrid signature using ECDSA P-256 + ML-DSA.
 */
public class PQCHybridSignatureECDSAMLDSATest extends CamelTestSupport {

    @EndpointInject("mock:signed")
    protected MockEndpoint resultSign;

    @EndpointInject("mock:verified")
    protected MockEndpoint resultVerify;

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
                from("direct:sign")
                        .to("pqc:hybridsign?operation=hybridSign"
                            + "&signatureAlgorithm=MLDSA"
                            + "&classicalSignatureAlgorithm=ECDSA_P256")
                        .to("mock:signed")
                        .to("pqc:hybridverify?operation=hybridVerify"
                            + "&signatureAlgorithm=MLDSA"
                            + "&classicalSignatureAlgorithm=ECDSA_P256")
                        .to("mock:verified");
            }
        };
    }

    @Test
    void testHybridSignAndVerify() throws Exception {
        resultSign.expectedMessageCount(1);
        resultVerify.expectedMessageCount(1);

        templateSign.sendBody("Hello, hybrid cryptography!");

        resultSign.assertIsSatisfied();
        resultVerify.assertIsSatisfied();

        // Check that hybrid signature was created
        byte[] hybridSig = resultSign.getExchanges().get(0).getMessage()
                .getHeader(PQCConstants.HYBRID_SIGNATURE, byte[].class);
        assertNotNull(hybridSig);
        assertTrue(hybridSig.length > 0);

        // Check that classical signature component exists
        byte[] classicalSig = resultSign.getExchanges().get(0).getMessage()
                .getHeader(PQCConstants.CLASSICAL_SIGNATURE, byte[].class);
        assertNotNull(classicalSig);

        // Check that PQC signature component exists
        byte[] pqcSig = resultSign.getExchanges().get(0).getMessage()
                .getHeader(PQCConstants.PQC_SIGNATURE, byte[].class);
        assertNotNull(pqcSig);

        // Check verification passed
        assertTrue(resultVerify.getExchanges().get(0).getMessage()
                .getHeader(PQCConstants.HYBRID_VERIFY, Boolean.class));
    }
}
