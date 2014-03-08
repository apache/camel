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
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;

public abstract class AbstractPGPDataFormatTest extends CamelTestSupport {
    
    protected void doRoundTripEncryptionTests(String endpoint) throws Exception {
        MockEndpoint encrypted = setupExpectations(context, 3, "mock:encrypted");
        MockEndpoint unencrypted = setupExpectations(context, 3, "mock:unencrypted");

        String payload = "Hi Alice, Be careful Eve is listening, signed Bob";
        Map<String, Object> headers = getHeaders();
        template.sendBodyAndHeaders(endpoint, payload, headers);
        template.sendBodyAndHeaders(endpoint, payload.getBytes(), headers);
        template.sendBodyAndHeaders(endpoint, new ByteArrayInputStream(payload.getBytes()), headers);

        assertMocksSatisfied(encrypted, unencrypted, payload);
    }

    protected Map<String, Object> getHeaders() {
        return new HashMap<String, Object>();
    }

    protected void assertMocksSatisfied(MockEndpoint encrypted, MockEndpoint unencrypted, String payload) throws Exception {
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

    protected void awaitAndAssert(MockEndpoint mock) throws InterruptedException {
        mock.assertIsSatisfied();
    }

    public MockEndpoint setupExpectations(CamelContext context, int expected, String mock) {
        MockEndpoint mockEp = context.getEndpoint(mock, MockEndpoint.class);
        mockEp.expectedMessageCount(expected);
        return mockEp;
    }

}
