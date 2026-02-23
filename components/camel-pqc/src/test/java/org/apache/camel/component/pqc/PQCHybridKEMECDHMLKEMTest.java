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

import javax.crypto.SecretKey;

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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for hybrid KEM using ECDH P-256 + ML-KEM.
 */
public class PQCHybridKEMECDHMLKEMTest extends CamelTestSupport {

    @EndpointInject("mock:encapsulated")
    protected MockEndpoint resultEncapsulate;

    @EndpointInject("mock:extracted")
    protected MockEndpoint resultExtract;

    @Produce("direct:encapsulate")
    protected ProducerTemplate templateEncapsulate;

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
                from("direct:encapsulate")
                        .to("pqc:hybridkem?operation=hybridGenerateSecretKeyEncapsulation"
                            + "&keyEncapsulationAlgorithm=MLKEM"
                            + "&classicalKEMAlgorithm=ECDH_P256"
                            + "&symmetricKeyAlgorithm=AES"
                            + "&symmetricKeyLength=128")
                        .to("mock:encapsulated")
                        .to("pqc:hybridkem?operation=hybridExtractSecretKeyEncapsulation"
                            + "&keyEncapsulationAlgorithm=MLKEM"
                            + "&classicalKEMAlgorithm=ECDH_P256"
                            + "&symmetricKeyAlgorithm=AES"
                            + "&symmetricKeyLength=128")
                        .to("mock:extracted");
            }
        };
    }

    @Test
    void testHybridKEMWithECDH() throws Exception {
        resultEncapsulate.expectedMessageCount(1);
        resultExtract.expectedMessageCount(1);

        templateEncapsulate.sendBody("trigger");

        resultEncapsulate.assertIsSatisfied();
        resultExtract.assertIsSatisfied();

        // Check that hybrid encapsulation was created
        byte[] hybridEncap = resultEncapsulate.getExchanges().get(0).getMessage()
                .getHeader(PQCConstants.HYBRID_ENCAPSULATION, byte[].class);
        assertNotNull(hybridEncap);
        assertTrue(hybridEncap.length > 0);

        // Check that shared secret was created and has correct length (128 bits = 16 bytes)
        SecretKey encapSecret = resultEncapsulate.getExchanges().get(0).getMessage()
                .getHeader(PQCConstants.HYBRID_SECRET_KEY, SecretKey.class);
        assertNotNull(encapSecret);
        assertEquals("AES", encapSecret.getAlgorithm());
        assertEquals(16, encapSecret.getEncoded().length);

        // Check that extracted secret matches
        SecretKey extractedSecret = resultExtract.getExchanges().get(0).getMessage()
                .getHeader(PQCConstants.HYBRID_SECRET_KEY, SecretKey.class);
        assertNotNull(extractedSecret);

        // The secrets should be identical
        assertArrayEquals(encapSecret.getEncoded(), extractedSecret.getEncoded());
    }
}
