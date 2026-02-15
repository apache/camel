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
package org.apache.camel.component.pqc.dataformat;

import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.Security;

import org.apache.camel.BindToRegistry;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.pqc.PQCKeyEncapsulationAlgorithms;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;
import org.bouncycastle.pqc.jcajce.spec.KyberParameterSpec;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests PQCDataFormat with different KEM and symmetric algorithms
 */
public class PQCDataFormatMultipleAlgorithmsTest extends CamelTestSupport {

    @EndpointInject("mock:kyber-camellia")
    protected MockEndpoint resultKyberCamellia;

    @Produce("direct:kyber-camellia")
    protected ProducerTemplate templateKyberCamellia;

    private static final String ORIGINAL_MESSAGE = "Testing different algorithms";

    @BeforeAll
    public static void startup() {
        Security.addProvider(new BouncyCastleProvider());
        Security.addProvider(new BouncyCastlePQCProvider());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // Test with Kyber + Camellia
                PQCDataFormat kyberCamelliaFormat = new PQCDataFormat();
                kyberCamelliaFormat.setKeyEncapsulationAlgorithm("KYBER");
                kyberCamelliaFormat.setSymmetricKeyAlgorithm("CAMELLIA");
                kyberCamelliaFormat.setSymmetricKeyLength(128);

                from("direct:kyber-camellia")
                        .marshal(kyberCamelliaFormat)
                        .log("Encrypted with Kyber+Camellia: ${body}")
                        .unmarshal(kyberCamelliaFormat)
                        .to("mock:kyber-camellia");
            }
        };
    }

    @Test
    void testKyberWithCamellia() throws Exception {
        resultKyberCamellia.expectedMessageCount(1);

        templateKyberCamellia.sendBody(ORIGINAL_MESSAGE);

        resultKyberCamellia.assertIsSatisfied();

        String decrypted = resultKyberCamellia.getExchanges().get(0).getMessage().getBody(String.class);
        assertEquals(ORIGINAL_MESSAGE, decrypted);
    }

    @BindToRegistry("pqcKeyPair")
    public KeyPair createKyberKeyPair()
            throws NoSuchAlgorithmException, NoSuchProviderException, InvalidAlgorithmParameterException {
        // This test uses Kyber, so register Kyber keypair as pqcKeyPair
        KeyPairGenerator kpg = KeyPairGenerator.getInstance(
                PQCKeyEncapsulationAlgorithms.KYBER.getAlgorithm(),
                PQCKeyEncapsulationAlgorithms.KYBER.getBcProvider());
        kpg.initialize(KyberParameterSpec.kyber512, new SecureRandom());
        return kpg.generateKeyPair();
    }
}
