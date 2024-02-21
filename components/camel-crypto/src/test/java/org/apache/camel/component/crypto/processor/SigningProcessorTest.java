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
package org.apache.camel.component.crypto.processor;

import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.crypto.DigitalSignatureConfiguration;
import org.apache.camel.component.crypto.DigitalSignatureConstants;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.DefaultMessage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SigningProcessorTest {

    @Test
    public void testClearHeadersEnabled() {
        DigitalSignatureConfiguration configuration = new DigitalSignatureConfiguration();
        configuration.setClearHeaders(true);

        DigitalSignatureProcessor processor = new DigitalSignatureProcessor(configuration) {
            @Override
            public void process(Exchange exchange) {
                // Noop
            }
        };

        Exchange exchange = new DefaultExchange(new DefaultCamelContext());
        Message message = new DefaultMessage(exchange);
        message.setHeader(DigitalSignatureConstants.SIGNATURE, "test-signature");
        message.setHeader(DigitalSignatureConstants.SIGNATURE_PRIVATE_KEY, "test-signature-private-key");
        message.setHeader(DigitalSignatureConstants.SIGNATURE_PUBLIC_KEY_OR_CERT, "test-public-key-or-cert");
        message.setHeader(DigitalSignatureConstants.KEYSTORE_ALIAS, "test-alias");
        message.setHeader(DigitalSignatureConstants.KEYSTORE_PASSWORD, "test-password");
        message.setHeader("CamelFooHeader", "bar");
        message.setHeader("CamelCheeseHeader", "wine");
        message.setHeader("custom", "test");

        processor.clearMessageHeaders(message);

        // Verify all of the crypto headers were removed but the others remain
        Map<String, Object> headers = message.getHeaders();
        assertEquals(3, headers.size());
        assertTrue(headers.containsKey("CamelFooHeader"));
        assertTrue(headers.containsKey("CamelCheeseHeader"));
        assertTrue(headers.containsKey("custom"));
    }

    @Test
    public void testClearHeadersDisabled() {
        DigitalSignatureConfiguration configuration = new DigitalSignatureConfiguration();
        configuration.setClearHeaders(false);

        DigitalSignatureProcessor processor = new DigitalSignatureProcessor(configuration) {
            @Override
            public void process(Exchange exchange) {
                // Noop
            }
        };

        Exchange exchange = new DefaultExchange(new DefaultCamelContext());
        Message message = new DefaultMessage(exchange);
        message.setHeader(DigitalSignatureConstants.SIGNATURE, "test-signature");
        message.setHeader(DigitalSignatureConstants.SIGNATURE_PRIVATE_KEY, "test-signature-private-key");
        message.setHeader(DigitalSignatureConstants.SIGNATURE_PUBLIC_KEY_OR_CERT, "test-public-key-or-cert");
        message.setHeader(DigitalSignatureConstants.KEYSTORE_ALIAS, "test-alias");
        message.setHeader(DigitalSignatureConstants.KEYSTORE_PASSWORD, "test-password");

        processor.clearMessageHeaders(message);

        Map<String, Object> headers = message.getHeaders();
        assertEquals(5, headers.size());
    }
}
