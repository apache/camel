/**
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
package org.apache.camel.converter.crypto;

import java.io.ByteArrayInputStream;
import java.security.Key;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class CryptoDataFormatTest extends CamelTestSupport {
    
    @Test
    public void testBasicSymmetric() throws Exception {
        doRoundTripEncryptionTests("direct:basic-encryption");
    }

    @Test
    public void testSymmetricWithInitVector() throws Exception {
        doRoundTripEncryptionTests("direct:init-vector");
    }

    @Test
    public void testSymmetricWithInlineInitVector() throws Exception {
        doRoundTripEncryptionTests("direct:inline");
    }

    @Test
    public void testSymmetricWithHMAC() throws Exception {
        doRoundTripEncryptionTests("direct:hmac");
    }

    @Test
    public void testSymmetricWithMD5HMAC() throws Exception {
        doRoundTripEncryptionTests("direct:hmac-algorithm");
    }
    
    @Test
    public void testSymmetricWithSHA256HMAC() throws Exception {
        doRoundTripEncryptionTests("direct:hmac-sha-256-algorithm");
    }
    
    @Test
    public void testKeySuppliedAsHeader() throws Exception {
        KeyGenerator generator = KeyGenerator.getInstance("DES");
        Key key = generator.generateKey();

        Exchange unecrypted = getMandatoryEndpoint("direct:key-in-header-encrypt").createExchange();
        unecrypted.getIn().setBody("Hi Alice, Be careful Eve is listening, signed Bob");
        unecrypted.getIn().setHeader(CryptoDataFormat.KEY, key);
        unecrypted = template.send("direct:key-in-header-encrypt", unecrypted);
        validateHeaderIsCleared(unecrypted);

        MockEndpoint mock = setupExpectations(context, 1, "mock:unencrypted");
        Exchange encrypted = getMandatoryEndpoint("direct:key-in-header-decrypt").createExchange();
        encrypted.getIn().copyFrom(unecrypted.getIn());
        encrypted.getIn().setHeader(CryptoDataFormat.KEY, key);
        template.send("direct:key-in-header-decrypt", encrypted);

        assertMockEndpointsSatisfied();

        Exchange received = mock.getReceivedExchanges().get(0);
        validateHeaderIsCleared(received);
    }
    
    @Test
    public void test3DESECBSymmetric() throws Exception {
        doRoundTripEncryptionTests("direct:3des-ecb-encryption");
    }
    
    @Test
    public void test3DESCBCSymmetric() throws Exception {
        doRoundTripEncryptionTests("direct:3des-cbc-encryption");
    }
    
    @Test
    public void testAES128ECBSymmetric() throws Exception {
        if (checkUnrestrictedPoliciesInstalled()) {
            doRoundTripEncryptionTests("direct:aes-128-ecb-encryption");
        }
    }

    private void validateHeaderIsCleared(Exchange ex) {
        Object header = ex.getIn().getHeader(CryptoDataFormat.KEY);
        assertTrue(!ex.getIn().getHeaders().containsKey(CryptoDataFormat.KEY) || "".equals(header) || header == null);
    }

    private void doRoundTripEncryptionTests(String endpointUri) throws Exception {
        doRoundTripEncryptionTests(endpointUri, Collections.<String, Object>emptyMap());
    }

    private void doRoundTripEncryptionTests(String endpoint, Map<String, Object> headers) throws Exception {
        MockEndpoint encrypted = setupExpectations(context, 3, "mock:encrypted");
        MockEndpoint unencrypted = setupExpectations(context, 3, "mock:unencrypted");

        String payload = "Hi Alice, Be careful Eve is listening, signed Bob";
        template.sendBodyAndHeaders(endpoint, payload, headers);
        template.sendBodyAndHeaders(endpoint, payload.getBytes(), headers);
        template.sendBodyAndHeaders(endpoint, new ByteArrayInputStream(payload.getBytes()), headers);

        assertMocksSatisfied(encrypted, unencrypted, payload);
    }

    private void assertMocksSatisfied(MockEndpoint encrypted, MockEndpoint unencrypted, String payload) throws Exception {
        awaitAndAssert(unencrypted);
        awaitAndAssert(encrypted);
        for (Exchange e : unencrypted.getReceivedExchanges()) {
            assertEquals(payload, e.getIn().getMandatoryBody(String.class));
        }
        for (Exchange e : encrypted.getReceivedExchanges()) {
            byte[] ciphertext = e.getIn().getMandatoryBody(byte[].class);
            assertNotSame(payload, new String(ciphertext));
        }
    }

    protected RouteBuilder[] createRouteBuilders() throws Exception {
        return new RouteBuilder[] {new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: basic
                KeyGenerator generator = KeyGenerator.getInstance("DES");

                CryptoDataFormat cryptoFormat = new CryptoDataFormat("DES", generator.generateKey());

                from("direct:basic-encryption")
                    .marshal(cryptoFormat)
                    .to("mock:encrypted")
                    .unmarshal(cryptoFormat)
                    .to("mock:unencrypted");
                // END SNIPPET: basic
            }
        }, new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: init-vector
                KeyGenerator generator = KeyGenerator.getInstance("DES");
                byte[] initializationVector = new byte[] {0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07};

                CryptoDataFormat cryptoFormat = new CryptoDataFormat("DES/CBC/PKCS5Padding", generator.generateKey());
                cryptoFormat.setInitializationVector(initializationVector);

                from("direct:init-vector")
                    .marshal(cryptoFormat)
                    .to("mock:encrypted")
                    .unmarshal(cryptoFormat)
                    .to("mock:unencrypted");
                // END SNIPPET: init-vector
            }
        }, new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: inline-init-vector
                KeyGenerator generator = KeyGenerator.getInstance("DES");
                byte[] initializationVector = new byte[] {0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07};
                SecretKey key = generator.generateKey();

                CryptoDataFormat cryptoFormat = new CryptoDataFormat("DES/CBC/PKCS5Padding", key);
                cryptoFormat.setInitializationVector(initializationVector);
                cryptoFormat.setShouldInlineInitializationVector(true);
                CryptoDataFormat decryptFormat = new CryptoDataFormat("DES/CBC/PKCS5Padding", key);
                decryptFormat.setShouldInlineInitializationVector(true);

                from("direct:inline")
                    .marshal(cryptoFormat)
                    .to("mock:encrypted")
                    .unmarshal(decryptFormat)
                    .to("mock:unencrypted");
                // END SNIPPET: inline-init-vector
            }
        }, new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: hmac
                KeyGenerator generator = KeyGenerator.getInstance("DES");

                CryptoDataFormat cryptoFormat = new CryptoDataFormat("DES", generator.generateKey());
                cryptoFormat.setShouldAppendHMAC(true);

                from("direct:hmac")
                    .marshal(cryptoFormat)
                    .to("mock:encrypted")
                    .unmarshal(cryptoFormat)
                    .to("mock:unencrypted");
                // END SNIPPET: hmac
            }
        }, new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: hmac-algorithm
                KeyGenerator generator = KeyGenerator.getInstance("DES");

                CryptoDataFormat cryptoFormat = new CryptoDataFormat("DES", generator.generateKey());
                cryptoFormat.setShouldAppendHMAC(true);
                cryptoFormat.setMacAlgorithm("HmacMD5");

                from("direct:hmac-algorithm")
                    .marshal(cryptoFormat)
                    .to("mock:encrypted")
                    .unmarshal(cryptoFormat)
                    .to("mock:unencrypted");
                // END SNIPPET: hmac-algorithm
            }
        }, new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: hmac-sha256-algorithm
                KeyGenerator generator = KeyGenerator.getInstance("DES");

                CryptoDataFormat cryptoFormat = new CryptoDataFormat("DES", generator.generateKey());
                cryptoFormat.setShouldAppendHMAC(true);
                cryptoFormat.setMacAlgorithm("HmacSHA256");

                from("direct:hmac-sha-256-algorithm")
                    .marshal(cryptoFormat)
                    .to("mock:encrypted")
                    .unmarshal(cryptoFormat)
                    .to("mock:unencrypted");
                // END SNIPPET: hmac-sha256-algorithm
            }
        }, new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: key-in-header
                CryptoDataFormat cryptoFormat = new CryptoDataFormat("DES", null);
                /**
                 * Note: the header containing the key should be cleared after
                 * marshalling to stop it from leaking by accident and
                 * potentially being compromised. The processor version below is
                 * arguably better as the key is left in the header when you use
                 * the DSL leaks the fact that camel encryption was used.
                 */
                from("direct:key-in-header-encrypt")
                    .marshal(cryptoFormat)
                    .removeHeader(CryptoDataFormat.KEY)
                    .to("mock:encrypted");

                from("direct:key-in-header-decrypt").unmarshal(cryptoFormat).process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        exchange.getIn().getHeaders().remove(CryptoDataFormat.KEY);
                        exchange.getOut().copyFrom(exchange.getIn());
                    }
                }).to("mock:unencrypted");
                // END SNIPPET: key-in-header
            }
        }, new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: 3DES-ECB
                KeyGenerator generator = KeyGenerator.getInstance("DESede");

                CryptoDataFormat cryptoFormat = new CryptoDataFormat("DESede/ECB/PKCS5Padding", generator.generateKey());
                
                from("direct:3des-ecb-encryption")
                    .marshal(cryptoFormat)
                    .to("mock:encrypted")
                    .unmarshal(cryptoFormat)
                    .to("mock:unencrypted");
                // END SNIPPET: 3DES-ECB
            }
        }, new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: 3DES-CBC
                KeyGenerator generator = KeyGenerator.getInstance("DES");
                byte[] iv = new byte[8];
                SecureRandom random = new SecureRandom();
                random.nextBytes(iv);
                Key key = generator.generateKey();

                CryptoDataFormat encCryptoFormat = new CryptoDataFormat("DES/CBC/PKCS5Padding", key);
                encCryptoFormat.setInitializationVector(iv);
                encCryptoFormat.setShouldInlineInitializationVector(true);
                
                CryptoDataFormat decCryptoFormat = new CryptoDataFormat("DES/CBC/PKCS5Padding", key);
                decCryptoFormat.setShouldInlineInitializationVector(true);
                
                from("direct:3des-cbc-encryption")
                    .marshal(encCryptoFormat)
                    .to("mock:encrypted")
                    .unmarshal(decCryptoFormat)
                    .to("mock:unencrypted");
                // END SNIPPET: 3DES-CBC
            }
        }, new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: AES-128-ECB
                KeyGenerator generator = KeyGenerator.getInstance("AES");

                CryptoDataFormat cryptoFormat = new CryptoDataFormat("AES/ECB/PKCS5Padding", generator.generateKey());
                
                from("direct:aes-128-ecb-encryption")
                    .marshal(cryptoFormat)
                    .to("mock:encrypted")
                    .unmarshal(cryptoFormat)
                    .to("mock:unencrypted");
                // END SNIPPET: AES-128-ECB
            }
        }};
    }

    private void awaitAndAssert(MockEndpoint mock) throws InterruptedException {
        mock.assertIsSatisfied();
    }

    public MockEndpoint setupExpectations(CamelContext context, int expected, String mock) {
        MockEndpoint mockEp = context.getEndpoint(mock, MockEndpoint.class);
        mockEp.expectedMessageCount(expected);
        return mockEp;
    }
    
    public static boolean checkUnrestrictedPoliciesInstalled() {
        try {
            byte[] data = {0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07};

            SecretKey key192 = new SecretKeySpec(
                new byte[] {0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07,
                            0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f,
                            0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17},
                            "AES");
            Cipher c = Cipher.getInstance("AES");
            c.init(Cipher.ENCRYPT_MODE, key192);
            c.doFinal(data);
            return true;
        } catch (Exception e) {
            //
        }
        return false;
    }

}
