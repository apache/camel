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
package org.apache.camel.component.as2;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.as2.api.*;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests AS2 server wildcard pattern matching for requestUriPattern (CAMEL-22849). This test verifies that wildcard
 * patterns like "/consumer/*" correctly match incoming requests to "/consumer/orders", "/consumer/invoices", etc.
 */
public class AS2ServerWildcardPatternIT extends AS2ServerSecTestBase {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // Consumer with wildcard pattern - should match /consumer/orders, /consumer/invoices, etc.
                from("as2://server/listen?requestUriPattern=/consumer/*")
                        .to("mock:wildcardConsumer");
            }
        };
    }

    @Test
    public void testWildcardPatternMatchesOrders() throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:wildcardConsumer");
        mockEndpoint.expectedMessageCount(1);

        // Send to /consumer/orders - should match the /consumer/* pattern
        HttpCoreContext context = sendToPath("/consumer/orders", AS2MessageStructure.PLAIN);

        verifyOkResponse(context);
        mockEndpoint.assertIsSatisfied();

        // Verify the message was received
        Exchange exchange = mockEndpoint.getReceivedExchanges().get(0);
        assertNotNull(exchange);
        assertEquals(EDI_MESSAGE, exchange.getIn().getBody(String.class));
    }

    @Test
    public void testWildcardPatternMatchesInvoices() throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:wildcardConsumer");
        mockEndpoint.expectedMessageCount(1);

        // Send to /consumer/invoices - should also match the /consumer/* pattern
        HttpCoreContext context = sendToPath("/consumer/invoices", AS2MessageStructure.PLAIN);

        verifyOkResponse(context);
        mockEndpoint.assertIsSatisfied();

        // Verify the message was received
        Exchange exchange = mockEndpoint.getReceivedExchanges().get(0);
        assertNotNull(exchange);
        assertEquals(EDI_MESSAGE, exchange.getIn().getBody(String.class));
    }

    @Test
    public void testWildcardPatternMatchesNestedPath() throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:wildcardConsumer");
        mockEndpoint.expectedMessageCount(1);

        // Send to /consumer/orders/123 - should also match the /consumer/* pattern
        HttpCoreContext context = sendToPath("/consumer/orders/123", AS2MessageStructure.PLAIN);

        verifyOkResponse(context);
        mockEndpoint.assertIsSatisfied();

        // Verify the message was received
        Exchange exchange = mockEndpoint.getReceivedExchanges().get(0);
        assertNotNull(exchange);
        assertEquals(EDI_MESSAGE, exchange.getIn().getBody(String.class));
    }

    /**
     * Helper method to send a message to a specific path
     */
    protected HttpCoreContext sendToPath(String requestUri, AS2MessageStructure structure) throws Exception {
        AS2SignatureAlgorithm signingAlgorithm = structure.isSigned() ? AS2SignatureAlgorithm.SHA256WITHRSA : null;
        AS2EncryptionAlgorithm encryptionAlgorithm = structure.isEncrypted() ? AS2EncryptionAlgorithm.AES128_CBC : null;
        AS2CompressionAlgorithm compressionAlgorithm = structure.isCompressed() ? AS2CompressionAlgorithm.ZLIB : null;

        return clientConnection().send(
                EDI_MESSAGE,
                requestUri,  // Use the provided requestUri instead of REQUEST_URI constant
                SUBJECT,
                FROM,
                AS2_NAME,
                AS2_NAME,
                structure,
                AS2MediaType.APPLICATION_EDIFACT,
                null,
                null,
                signingAlgorithm,
                structure.isSigned() ? new java.security.cert.Certificate[] { signingCert } : null,
                structure.isSigned() ? signingKP.getPrivate() : null,
                compressionAlgorithm,
                DISPOSITION_NOTIFICATION_TO,
                SIGNED_RECEIPT_MIC_ALGORITHMS,
                encryptionAlgorithm,
                structure.isEncrypted() ? new java.security.cert.Certificate[] { signingCert } : null,
                null,
                null,
                null,
                null,
                null);
    }
}
