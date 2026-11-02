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

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.as2.api.AS2MediaType;
import org.apache.camel.component.as2.api.AS2MessageStructure;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Several {@code as2://server/listen} consumers share one {@code serverPortNumber}, each with its own
 * {@code requestUriPattern}. Each of them delivers the asynchronous MDNs for the messages received on its own pattern
 * with its own {@code mdnUserName}, {@code mdnPassword}, {@code mdnAccessToken} and {@code asyncMdnAllowedHosts}, not
 * with the values of the consumer that created the shared server connection. This also holds for a consumer on the root
 * pattern, for which the connection registers a fallback configuration from the endpoint that created it.
 */
class AS2ServerTwoConsumerAsyncMdnSettingsTest extends AS2ServerSecTestBase {

    private static final String FIRST_PATTERN = "/receiver-one";
    private static final String SECOND_PATTERN = "/receiver-two";
    private static final String THIRD_PATTERN = "/receiver-three";
    private static final String ROOT_PATTERN = "/";
    private static final String FIRST_USER_NAME = "first-user";
    private static final String FIRST_PASSWORD = "first-password";
    private static final String SECOND_ACCESS_TOKEN = "second-token";
    private static final String ROOT_ACCESS_TOKEN = "root-token";
    private static final String RECEIPT_PATH = "/receipts";

    // one receipt endpoint per host name, so that each delivery address resolves to the endpoint bound to it
    @RegisterExtension
    AvailablePortFinder.Port localhostReceiptPort = AvailablePortFinder.find();
    @RegisterExtension
    AvailablePortFinder.Port loopbackReceiptPort = AvailablePortFinder.find();

    @Test
    void asyncMdnForTheSecondConsumerUsesItsOwnSettings() throws Exception {
        String authorization = authorizationOfTheReceiptFor(SECOND_PATTERN, loopbackReceiptAddress());

        assertEquals("Bearer " + SECOND_ACCESS_TOKEN, authorization);
    }

    @Test
    void asyncMdnForTheSecondConsumerAppliesItsOwnAllowedHosts() throws Exception {
        // localhost is on the first consumer's list only, so the second consumer's MDN is not delivered to it
        MockEndpoint receipts = getMockEndpoint("mock:receipts");
        receipts.expectedMessageCount(0);

        HttpCoreContext context = sendTo(SECOND_PATTERN, localhostReceiptAddress());
        verifyOkResponse(context);

        receipts.assertIsSatisfied(2000);
    }

    @Test
    void asyncMdnForAConsumerWithoutSettingsCarriesNoAuthorization() throws Exception {
        assertNull(authorizationOfTheReceiptFor(THIRD_PATTERN, localhostReceiptAddress()));
    }

    @Test
    void asyncMdnForTheConsumerOnTheRootPatternUsesItsOwnSettings() throws Exception {
        String authorization = authorizationOfTheReceiptFor(ROOT_PATTERN, localhostReceiptAddress());

        assertEquals("Bearer " + ROOT_ACCESS_TOKEN, authorization);
    }

    @Test
    void asyncMdnForTheFirstConsumerIsUnchanged() throws Exception {
        String authorization = authorizationOfTheReceiptFor(FIRST_PATTERN, localhostReceiptAddress());

        String encoded = Base64.getEncoder()
                .encodeToString((FIRST_USER_NAME + ":" + FIRST_PASSWORD).getBytes(StandardCharsets.UTF_8));
        assertEquals("Basic " + encoded, authorization);
    }

    @Test
    void asyncMdnAfterTheFirstConsumerStopsUsesTheRemainingConsumerSettings() throws Exception {
        context.getRouteController().stopRoute("first");

        String authorization = authorizationOfTheReceiptFor(SECOND_PATTERN, loopbackReceiptAddress());

        assertEquals("Bearer " + SECOND_ACCESS_TOKEN, authorization);
    }

    private String authorizationOfTheReceiptFor(String requestUri, String receiptDeliveryAddress) throws Exception {
        MockEndpoint receipts = getMockEndpoint("mock:receipts");
        receipts.expectedMessageCount(1);

        HttpCoreContext context = sendTo(requestUri, receiptDeliveryAddress);
        verifyOkResponse(context);

        receipts.assertIsSatisfied();
        return receipts.getExchanges().get(0).getIn().getHeader("Authorization", String.class);
    }

    private HttpCoreContext sendTo(String requestUri, String receiptDeliveryAddress) throws Exception {
        return clientConnection().send(EDI_MESSAGE, requestUri, SUBJECT, FROM, AS2_NAME, AS2_NAME,
                AS2MessageStructure.PLAIN, AS2MediaType.APPLICATION_EDIFACT, null, null,
                null, null, null, null,
                DISPOSITION_NOTIFICATION_TO, null, null, null,
                null, receiptDeliveryAddress, null, null, null);
    }

    private String localhostReceiptAddress() {
        return "http://localhost:" + localhostReceiptPort.getPort() + RECEIPT_PATH;
    }

    private String loopbackReceiptAddress() {
        return "http://127.0.0.1:" + loopbackReceiptPort.getPort() + RECEIPT_PATH;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // the first consumer creates the server connection shared by every consumer on the port
                from("as2://server/listen?requestUriPattern=" + FIRST_PATTERN
                     + "&mdnUserName=" + FIRST_USER_NAME + "&mdnPassword=" + FIRST_PASSWORD
                     + "&asyncMdnAllowedHosts=localhost,127.0.0.1")
                        .routeId("first")
                        .to("mock:first");

                from("as2://server/listen?requestUriPattern=" + SECOND_PATTERN
                     + "&mdnAccessToken=" + SECOND_ACCESS_TOKEN + "&asyncMdnAllowedHosts=127.0.0.1")
                        .routeId("second")
                        .to("mock:second");

                // no asynchronous MDN settings at all
                from("as2://server/listen?requestUriPattern=" + THIRD_PATTERN)
                        .routeId("third")
                        .to("mock:third");

                // the root pattern, on the connection another consumer created
                from("as2://server/listen?requestUriPattern=" + ROOT_PATTERN
                     + "&mdnAccessToken=" + ROOT_ACCESS_TOKEN + "&asyncMdnAllowedHosts=localhost")
                        .routeId("root")
                        .to("mock:root");

                // where the asynchronous MDNs are delivered to
                from("jetty:http://localhost:" + localhostReceiptPort.getPort() + RECEIPT_PATH)
                        .to("mock:receipts");
                from("jetty:http://127.0.0.1:" + loopbackReceiptPort.getPort() + RECEIPT_PATH)
                        .to("mock:receipts");
            }
        };
    }
}
