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

/**
 * Integration test demonstrating real-world usage of PQCDataFormat in a file encryption/decryption scenario
 */
public class PQCDataFormatIntegrationTest extends CamelTestSupport {

    @EndpointInject("mock:encrypted-files")
    protected MockEndpoint encryptedFiles;

    @EndpointInject("mock:decrypted-files")
    protected MockEndpoint decryptedFiles;

    @Produce("direct:secure-channel")
    protected ProducerTemplate secureChannel;

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
                // Define PQC DataFormat for quantum-resistant encryption
                PQCDataFormat pqcFormat = new PQCDataFormat();
                pqcFormat.setKeyEncapsulationAlgorithm("MLKEM");
                pqcFormat.setSymmetricKeyAlgorithm("AES");
                pqcFormat.setSymmetricKeyLength(256);

                // Simulated secure channel: encrypt -> transmit -> decrypt
                from("direct:secure-channel")
                        .log("Original message: ${body}")
                        .marshal(pqcFormat)
                        .log("Message encrypted with PQC (size: ${body.length} bytes)")
                        .to("mock:encrypted-files")
                        // Simulating transmission/storage...
                        .unmarshal(pqcFormat)
                        .log("Message decrypted: ${body}")
                        .to("mock:decrypted-files");

                // Alternative route: Encrypt-at-rest scenario
                from("direct:encrypt-at-rest")
                        .marshal(pqcFormat)
                        .to("mock:storage");

                from("direct:decrypt-from-storage")
                        .unmarshal(pqcFormat)
                        .to("mock:retrieved");
            }
        };
    }

    @Test
    void testSecureChannelEncryption() throws Exception {
        encryptedFiles.expectedMessageCount(1);
        decryptedFiles.expectedMessageCount(1);

        String sensitiveData = "TOP SECRET: This message contains sensitive information protected by PQC.";

        secureChannel.sendBody(sensitiveData);

        encryptedFiles.assertIsSatisfied();
        decryptedFiles.assertIsSatisfied();

        // Verify the message was successfully decrypted
        String decrypted = decryptedFiles.getExchanges().get(0).getMessage().getBody(String.class);
        assertEquals(sensitiveData, decrypted);
    }

    @Test
    void testMultipleMessagesInSequence() throws Exception {
        encryptedFiles.reset();
        decryptedFiles.reset();

        int messageCount = 5;
        encryptedFiles.expectedMessageCount(messageCount);
        decryptedFiles.expectedMessageCount(messageCount);

        for (int i = 0; i < messageCount; i++) {
            String message = "Message #" + i + ": Testing PQC DataFormat with sequential messages";
            secureChannel.sendBody(message);
        }

        encryptedFiles.assertIsSatisfied();
        decryptedFiles.assertIsSatisfied();

        // Verify all messages were correctly decrypted
        for (int i = 0; i < messageCount; i++) {
            String expected = "Message #" + i + ": Testing PQC DataFormat with sequential messages";
            String actual = decryptedFiles.getExchanges().get(i).getMessage().getBody(String.class);
            assertEquals(expected, actual);
        }
    }

    @Test
    void testJsonPayloadEncryption() throws Exception {
        encryptedFiles.reset();
        decryptedFiles.reset();

        encryptedFiles.expectedMessageCount(1);
        decryptedFiles.expectedMessageCount(1);

        String jsonPayload = """
                {
                    "userId": "12345",
                    "action": "transfer",
                    "amount": 10000,
                    "currency": "USD",
                    "timestamp": "2025-01-15T10:30:00Z"
                }
                """;

        secureChannel.sendBody(jsonPayload);

        encryptedFiles.assertIsSatisfied();
        decryptedFiles.assertIsSatisfied();

        String decrypted = decryptedFiles.getExchanges().get(0).getMessage().getBody(String.class);
        assertEquals(jsonPayload, decrypted);
    }

    @BindToRegistry("pqcKeyPair")
    public KeyPair createKeyPair()
            throws NoSuchAlgorithmException, NoSuchProviderException, InvalidAlgorithmParameterException {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance(
                PQCKeyEncapsulationAlgorithms.MLKEM.getAlgorithm(),
                PQCKeyEncapsulationAlgorithms.MLKEM.getBcProvider());
        kpg.initialize(MLKEMParameterSpec.ml_kem_768, new SecureRandom());
        return kpg.generateKeyPair();
    }
}
