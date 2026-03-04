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
import org.bouncycastle.jcajce.spec.MLKEMParameterSpec;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class PQCDataFormatTest extends CamelTestSupport {

    @EndpointInject("mock:encrypted")
    protected MockEndpoint resultEncrypted;

    @EndpointInject("mock:decrypted")
    protected MockEndpoint resultDecrypted;

    @Produce("direct:encrypt")
    protected ProducerTemplate templateEncrypt;

    private static final String ORIGINAL_MESSAGE = "Hello from Apache Camel with Post-Quantum Cryptography!";

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
                PQCDataFormat pqcFormat = new PQCDataFormat();
                pqcFormat.setKeyEncapsulationAlgorithm("MLKEM");
                pqcFormat.setSymmetricKeyAlgorithm("AES");

                from("direct:encrypt")
                        .marshal(pqcFormat)
                        .to("mock:encrypted")
                        .unmarshal(pqcFormat)
                        .to("mock:decrypted");
            }
        };
    }

    @Test
    void testBasicRoundTrip() throws Exception {
        resultEncrypted.expectedMessageCount(1);
        resultDecrypted.expectedMessageCount(1);

        templateEncrypt.sendBody(ORIGINAL_MESSAGE);

        resultEncrypted.assertIsSatisfied();
        resultDecrypted.assertIsSatisfied();

        // Verify encrypted data is not the same as original
        byte[] encrypted = resultEncrypted.getExchanges().get(0).getMessage().getBody(byte[].class);
        assertNotNull(encrypted);
        assertNotEquals(ORIGINAL_MESSAGE, new String(encrypted));

        // Verify decrypted data matches original
        String decrypted = resultDecrypted.getExchanges().get(0).getMessage().getBody(String.class);
        assertEquals(ORIGINAL_MESSAGE, decrypted);
    }

    @Test
    void testLargePayload() throws Exception {
        resultEncrypted.reset();
        resultDecrypted.reset();
        resultEncrypted.expectedMessageCount(1);
        resultDecrypted.expectedMessageCount(1);

        // Create a large message (1MB)
        StringBuilder largeMessage = new StringBuilder();
        for (int i = 0; i < 10000; i++) {
            largeMessage.append("This is line ").append(i).append(" of a large message for testing PQC DataFormat.\n");
        }
        String originalLargeMessage = largeMessage.toString();

        templateEncrypt.sendBody(originalLargeMessage);

        resultEncrypted.assertIsSatisfied();
        resultDecrypted.assertIsSatisfied();

        // Verify encrypted data is not the same as original
        byte[] encrypted = resultEncrypted.getExchanges().get(0).getMessage().getBody(byte[].class);
        assertNotNull(encrypted);
        assertNotEquals(originalLargeMessage, new String(encrypted));

        // Verify decrypted data matches original
        String decrypted = resultDecrypted.getExchanges().get(0).getMessage().getBody(String.class);
        assertEquals(originalLargeMessage, decrypted);
    }

    @BindToRegistry("pqcKeyPair")
    public KeyPair createKeyPair()
            throws NoSuchAlgorithmException, NoSuchProviderException, InvalidAlgorithmParameterException {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance(
                PQCKeyEncapsulationAlgorithms.MLKEM.getAlgorithm(),
                PQCKeyEncapsulationAlgorithms.MLKEM.getBcProvider());
        kpg.initialize(MLKEMParameterSpec.ml_kem_512, new SecureRandom());
        return kpg.generateKeyPair();
    }
}
