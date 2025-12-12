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

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Security;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.pqc.PQCKeyEncapsulationAlgorithms;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.bouncycastle.jcajce.spec.MLKEMParameterSpec;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests PQCDataFormat with header-based configuration
 */
public class PQCDataFormatHeaderTest extends CamelTestSupport {

    @EndpointInject("mock:result")
    protected MockEndpoint result;

    @Produce("direct:start")
    protected ProducerTemplate template;

    private static final String ORIGINAL_MESSAGE = "Testing header-based configuration";
    private KeyPair keyPair;

    @BeforeAll
    public static void startup() {
        Security.addProvider(new BouncyCastleProvider());
        Security.addProvider(new BouncyCastlePQCProvider());
    }

    @Override
    protected void doPreSetup() throws Exception {
        // Create key pair for testing
        KeyPairGenerator kpg = KeyPairGenerator.getInstance(
                PQCKeyEncapsulationAlgorithms.MLKEM.getAlgorithm(),
                PQCKeyEncapsulationAlgorithms.MLKEM.getBcProvider());
        kpg.initialize(MLKEMParameterSpec.ml_kem_512, new SecureRandom());
        keyPair = kpg.generateKeyPair();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // DataFormat without pre-configured KeyPair
                PQCDataFormat pqcFormat = new PQCDataFormat();
                pqcFormat.setKeyEncapsulationAlgorithm("MLKEM");
                pqcFormat.setSymmetricKeyAlgorithm("AES");

                from("direct:start")
                        .marshal(pqcFormat)
                        .log("Encrypted: ${body}")
                        .unmarshal(pqcFormat)
                        .to("mock:result");
            }
        };
    }

    @Test
    void testKeyPairInHeader() throws Exception {
        result.expectedMessageCount(1);

        // Send message with KeyPair in header
        Map<String, Object> headers = new HashMap<>();
        headers.put(PQCDataFormat.KEY_PAIR, keyPair);

        template.sendBodyAndHeaders(ORIGINAL_MESSAGE, headers);

        result.assertIsSatisfied();

        String decrypted = result.getExchanges().get(0).getMessage().getBody(String.class);
        assertEquals(ORIGINAL_MESSAGE, decrypted);
    }

    @Test
    void testDynamicAlgorithmSelection() throws Exception {
        result.reset();
        result.expectedMessageCount(1);

        Map<String, Object> headers = new HashMap<>();
        headers.put(PQCDataFormat.KEY_PAIR, keyPair);
        headers.put(PQCDataFormat.SYMMETRIC_ALGORITHM, "AES");

        template.sendBodyAndHeaders(ORIGINAL_MESSAGE, headers);

        result.assertIsSatisfied();

        String decrypted = result.getExchanges().get(0).getMessage().getBody(String.class);
        assertEquals(ORIGINAL_MESSAGE, decrypted);
    }
}
