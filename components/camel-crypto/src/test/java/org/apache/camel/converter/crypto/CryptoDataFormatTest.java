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
import java.util.Collections;
import java.util.Map;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.util.ExchangeHelper;
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

    private void validateHeaderIsCleared(Exchange ex) {
        Object header = ex.getIn().getHeader(CryptoDataFormat.KEY);
        assertTrue(!ex.getIn().getHeaders().containsKey(CryptoDataFormat.KEY) || "".equals(header) || header == null);
    }

    @SuppressWarnings("unchecked")
    private void doRoundTripEncryptionTests(String endpointUri) throws Exception, InterruptedException, InvalidPayloadException {
        doRoundTripEncryptionTests(endpointUri, Collections.EMPTY_MAP);
    }

    private void doRoundTripEncryptionTests(String endpoint, Map<String, Object> headers) throws Exception, InterruptedException, InvalidPayloadException {
        MockEndpoint encrypted = setupExpectations(context, 3, "mock:encrypted");
        MockEndpoint unencrypted = setupExpectations(context, 3, "mock:unencrypted");

        String payload = "Hi Alice, Be careful Eve is listening, signed Bob";
        template.sendBodyAndHeaders(endpoint, payload, headers);
        template.sendBodyAndHeaders(endpoint, payload.getBytes(), headers);
        template.sendBodyAndHeaders(endpoint, new ByteArrayInputStream(payload.getBytes()), headers);

        assertMocksSatisfied(encrypted, unencrypted, payload);
    }

    private void assertMocksSatisfied(MockEndpoint encrypted, MockEndpoint unencrypted, String payload) throws InterruptedException, InvalidPayloadException {
        awaitAndAssert(unencrypted);
        awaitAndAssert(encrypted);
        for (Exchange e : unencrypted.getReceivedExchanges()) {
            assertEquals(payload, ExchangeHelper.getMandatoryInBody(e, String.class));
        }
        for (Exchange e : encrypted.getReceivedExchanges()) {
            byte[] ciphertext = ExchangeHelper.getMandatoryInBody(e, byte[].class);
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

}
