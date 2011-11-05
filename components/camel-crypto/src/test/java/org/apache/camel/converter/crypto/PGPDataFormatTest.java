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
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.util.ExchangeHelper;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.junit.Test;

public class PGPDataFormatTest extends CamelTestSupport {

    static String keyFileName = "src/test/resources/org/apache/camel/component/crypto/pubring.gpg";
    static String keyFileNameSec = "src/test/resources/org/apache/camel/component/crypto/secring.gpg";
    static String keyUserid = "sdude@nowhere.net";
    static String keyPassword = "sdude";

    @Test
    public void testEncryption() throws Exception {
        doRoundTripEncryptionTests("direct:inline", new HashMap<String, Object>());
    }

    @Test
    public void testEncryptionHeaders() throws Exception {
        doRoundTripEncryptionTests("direct:inlineHeaders", new HashMap<String, Object>());
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
            assertEquals(payload, ExchangeHelper.getMandatoryInBody(e, String.class));
        }
        for (Exchange e : encrypted.getReceivedExchanges()) {
            byte[] ciphertext = ExchangeHelper.getMandatoryInBody(e, byte[].class);
            assertNotSame(payload, new String(ciphertext));
        }
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: pgp-format
                PGPDataFormat pgpDataFormat = new PGPDataFormat();
                PGPPublicKey pKey = PGPDataFormatUtil.findPublicKey(keyFileName, keyUserid);
                PGPPrivateKey sKey = PGPDataFormatUtil.findPrivateKey(keyFileNameSec, keyUserid, keyPassword);
                pgpDataFormat.setPublicKey(pKey);
                pgpDataFormat.setPrivateKey(sKey);

                from("direct:inline")
                    .marshal(pgpDataFormat)
                    .to("mock:encrypted")
                    .unmarshal(pgpDataFormat)
                    .to("mock:unencrypted");
                // END SNIPPET: pgp-format

                // START SNIPPET: pgp-format-header
                PGPDataFormat pgpDataFormatNoKey = new PGPDataFormat();
                pgpDataFormat.setPublicKey(pKey);
                pgpDataFormat.setPrivateKey(sKey);

                from("direct:inlineHeaders")
                    .setHeader(PGPDataFormat.KEY_PUB).constant(pKey)
                    .setHeader(PGPDataFormat.KEY_PRI).constant(sKey)
                    .marshal(pgpDataFormatNoKey)
                    .to("mock:encrypted")
                    .unmarshal(pgpDataFormatNoKey)
                    .to("mock:unencrypted");
                // END SNIPPET: pgp-format-header
            }
        };
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
