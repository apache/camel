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
 *
 * When multiple patterns match a request, the first registered pattern (in route definition order) takes precedence.
 * This means more specific patterns should be defined before more general patterns.
 */
public class AS2ServerWildcardPatternIT extends AS2ServerSecTestBase {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // Routes are registered in order - first matching pattern wins
                // More specific patterns should be defined first

                // Consumer with exact match - takes precedence over all wildcards
                from("as2://server/listen?requestUriPattern=/consumer/orders")
                        .to("mock:exactConsumer");

                // Consumer with more specific wildcard pattern - should be before less specific patterns
                from("as2://server/listen?requestUriPattern=/consumer/orders/*")
                        .to("mock:specificWildcardConsumer");

                // Consumer with general wildcard pattern - matches remaining /consumer/* paths
                from("as2://server/listen?requestUriPattern=/consumer/*")
                        .to("mock:wildcardConsumer");

                // Consumer with different path - should not match /consumer/* requests
                from("as2://server/listen?requestUriPattern=/admin/*")
                        .to("mock:adminConsumer");

                // Consumer with regex special characters in pattern - should be treated as literals
                from("as2://server/listen?requestUriPattern=/api/v1.2/endpoint")
                        .to("mock:regexSpecialCharsConsumer");

                // Consumer with wildcard and special characters
                from("as2://server/listen?requestUriPattern=/api/v2+3/*")
                        .to("mock:regexSpecialCharsWildcardConsumer");
            }
        };
    }

    @Test
    public void testWildcardPatternMatchesOrders() throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:wildcardConsumer");
        mockEndpoint.expectedMessageCount(1);

        // Send to /consumer/products - should match the /consumer/* pattern
        HttpCoreContext context = sendToPath("/consumer/products", AS2MessageStructure.PLAIN);

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
        MockEndpoint mockEndpoint = getMockEndpoint("mock:specificWildcardConsumer");
        mockEndpoint.expectedMessageCount(1);

        // Send to /consumer/orders/123 - should match the more specific /consumer/orders/* pattern
        HttpCoreContext context = sendToPath("/consumer/orders/123", AS2MessageStructure.PLAIN);

        verifyOkResponse(context);
        mockEndpoint.assertIsSatisfied();

        // Verify the message was received
        Exchange exchange = mockEndpoint.getReceivedExchanges().get(0);
        assertNotNull(exchange);
        assertEquals(EDI_MESSAGE, exchange.getIn().getBody(String.class));
    }

    @Test
    public void testExactMatchTakesPrecedence() throws Exception {
        MockEndpoint exactEndpoint = getMockEndpoint("mock:exactConsumer");
        MockEndpoint wildcardEndpoint = getMockEndpoint("mock:wildcardConsumer");
        exactEndpoint.expectedMessageCount(1);
        wildcardEndpoint.expectedMessageCount(0);

        // Send to /consumer/orders - should match exact pattern, not wildcard
        HttpCoreContext context = sendToPath("/consumer/orders", AS2MessageStructure.PLAIN);

        verifyOkResponse(context);
        exactEndpoint.assertIsSatisfied();
        wildcardEndpoint.assertIsSatisfied();

        // Verify the message was received by exact consumer
        Exchange exchange = exactEndpoint.getReceivedExchanges().get(0);
        assertNotNull(exchange);
        assertEquals(EDI_MESSAGE, exchange.getIn().getBody(String.class));
    }

    @Test
    public void testFirstMatchingPatternWins() throws Exception {
        MockEndpoint specificEndpoint = getMockEndpoint("mock:specificWildcardConsumer");
        MockEndpoint generalEndpoint = getMockEndpoint("mock:wildcardConsumer");
        specificEndpoint.expectedMessageCount(1);
        generalEndpoint.expectedMessageCount(0);

        // Send to /consumer/orders/456 - should match /consumer/orders/* (first matching pattern)
        // not /consumer/* (which also matches but is registered later)
        HttpCoreContext context = sendToPath("/consumer/orders/456", AS2MessageStructure.PLAIN);

        verifyOkResponse(context);
        specificEndpoint.assertIsSatisfied();
        generalEndpoint.assertIsSatisfied();

        // Verify the message was received by specific consumer
        Exchange exchange = specificEndpoint.getReceivedExchanges().get(0);
        assertNotNull(exchange);
        assertEquals(EDI_MESSAGE, exchange.getIn().getBody(String.class));
    }

    @Test
    public void testNoPatternMatch() throws Exception {
        MockEndpoint wildcardEndpoint = getMockEndpoint("mock:wildcardConsumer");
        MockEndpoint adminEndpoint = getMockEndpoint("mock:adminConsumer");
        wildcardEndpoint.expectedMessageCount(0);
        adminEndpoint.expectedMessageCount(1);

        // Send to /admin/test - should match /admin/*, not /consumer/*
        HttpCoreContext context = sendToPath("/admin/test", AS2MessageStructure.PLAIN);

        verifyOkResponse(context);
        wildcardEndpoint.assertIsSatisfied();
        adminEndpoint.assertIsSatisfied();

        // Verify the message was received by admin consumer
        Exchange exchange = adminEndpoint.getReceivedExchanges().get(0);
        assertNotNull(exchange);
        assertEquals(EDI_MESSAGE, exchange.getIn().getBody(String.class));
    }

    @Test
    public void testRegexSpecialCharactersTreatedAsLiterals() throws Exception {
        MockEndpoint regexEndpoint = getMockEndpoint("mock:regexSpecialCharsConsumer");
        regexEndpoint.expectedMessageCount(1);

        // Send to /api/v1.2/endpoint - the dot should be treated as a literal dot, not regex "any character"
        HttpCoreContext context = sendToPath("/api/v1.2/endpoint", AS2MessageStructure.PLAIN);

        verifyOkResponse(context);
        regexEndpoint.assertIsSatisfied();

        // Verify the message was received
        Exchange exchange = regexEndpoint.getReceivedExchanges().get(0);
        assertNotNull(exchange);
        assertEquals(EDI_MESSAGE, exchange.getIn().getBody(String.class));
    }

    @Test
    public void testRegexSpecialCharactersNotMatchedAsRegex() throws Exception {
        MockEndpoint regexEndpoint = getMockEndpoint("mock:regexSpecialCharsConsumer");
        regexEndpoint.expectedMessageCount(0);

        // Send to /api/v1X2/endpoint - should NOT match because dot is literal, not regex "any character"
        // If Pattern.quote() wasn't working, this would incorrectly match
        HttpCoreContext context = sendToPath("/api/v1X2/endpoint", AS2MessageStructure.PLAIN);

        // This should fail to find a matching consumer, but we're just verifying it doesn't match the wrong one
        regexEndpoint.assertIsSatisfied();
    }

    @Test
    public void testRegexSpecialCharactersWithWildcard() throws Exception {
        MockEndpoint regexWildcardEndpoint = getMockEndpoint("mock:regexSpecialCharsWildcardConsumer");
        regexWildcardEndpoint.expectedMessageCount(1);

        // Send to /api/v2+3/orders - the plus should be treated as a literal plus, not regex "one or more"
        HttpCoreContext context = sendToPath("/api/v2+3/orders", AS2MessageStructure.PLAIN);

        verifyOkResponse(context);
        regexWildcardEndpoint.assertIsSatisfied();

        // Verify the message was received
        Exchange exchange = regexWildcardEndpoint.getReceivedExchanges().get(0);
        assertNotNull(exchange);
        assertEquals(EDI_MESSAGE, exchange.getIn().getBody(String.class));
    }

    @Test
    public void testRegexSpecialCharactersWithWildcardNotMatchedAsRegex() throws Exception {
        MockEndpoint regexWildcardEndpoint = getMockEndpoint("mock:regexSpecialCharsWildcardConsumer");
        regexWildcardEndpoint.expectedMessageCount(0);

        // Send to /api/v23/orders - should NOT match because plus is literal, not regex "one or more"
        // If Pattern.quote() wasn't working, this would incorrectly match
        HttpCoreContext context = sendToPath("/api/v23/orders", AS2MessageStructure.PLAIN);

        // This should fail to find a matching consumer, but we're just verifying it doesn't match the wrong one
        regexWildcardEndpoint.assertIsSatisfied();
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
