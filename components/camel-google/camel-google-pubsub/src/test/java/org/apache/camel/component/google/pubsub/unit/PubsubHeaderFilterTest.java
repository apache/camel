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
package org.apache.camel.component.google.pubsub.unit;

import org.apache.camel.Exchange;
import org.apache.camel.component.google.pubsub.GooglePubsubHeaderFilterStrategy;
import org.apache.camel.component.google.pubsub.PubsubTestSupport;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PubsubHeaderFilterTest extends PubsubTestSupport {
    @ParameterizedTest
    @ValueSource(strings = { "google-header", "X-GOOGLE-HEADER", "x-google-header" })
    public void testPubsubHeaderFilter(String headerName) {
        GooglePubsubHeaderFilterStrategy googlePubsubHeaderFilterStrategy = new GooglePubsubHeaderFilterStrategy(false);
        Exchange exchange = new DefaultExchange(context);
        assertTrue(googlePubsubHeaderFilterStrategy.applyFilterToExternalHeaders(headerName, "value", exchange),
                headerName + " not filtered");
    }

    @ParameterizedTest
    @ValueSource(strings = { "authorization", "Authorization", "AUTHORIZATION" })
    public void testAuthorizationHeaderFilteredOnOutbound(String headerName) {
        // Tests that sensitive headers are filtered when sending FROM Camel TO Pub/Sub (producer)
        GooglePubsubHeaderFilterStrategy strategy = new GooglePubsubHeaderFilterStrategy(false);
        Exchange exchange = new DefaultExchange(context);
        assertTrue(strategy.applyFilterToCamelHeaders(headerName, "Bearer token", exchange),
                headerName + " should be filtered on outbound (Camel to Pub/Sub)");
    }

    @ParameterizedTest
    @ValueSource(strings = { "authorization", "Authorization", "AUTHORIZATION" })
    public void testAuthorizationHeaderFilteredOnInbound(String headerName) {
        // Tests that sensitive headers are filtered when receiving FROM Pub/Sub TO Camel (consumer)
        GooglePubsubHeaderFilterStrategy strategy = new GooglePubsubHeaderFilterStrategy(false);
        Exchange exchange = new DefaultExchange(context);
        assertTrue(strategy.applyFilterToExternalHeaders(headerName, "Bearer token", exchange),
                headerName + " should be filtered on inbound (Pub/Sub to Camel)");
    }

    @ParameterizedTest
    @ValueSource(strings = { "grpc-timeout", "Grpc-Timeout", "GRPC-TIMEOUT" })
    public void testGrpcTimeoutHeaderFilteredOnBothDirections(String headerName) {
        GooglePubsubHeaderFilterStrategy strategy = new GooglePubsubHeaderFilterStrategy(false);
        Exchange exchange = new DefaultExchange(context);
        assertTrue(strategy.applyFilterToCamelHeaders(headerName, "30s", exchange),
                headerName + " should be filtered on outbound");
        assertTrue(strategy.applyFilterToExternalHeaders(headerName, "30s", exchange),
                headerName + " should be filtered on inbound");
    }

    @Test
    public void testNonSensitiveHeadersNotFiltered() {
        GooglePubsubHeaderFilterStrategy strategy = new GooglePubsubHeaderFilterStrategy(false);
        Exchange exchange = new DefaultExchange(context);
        // Regular headers should NOT be filtered
        assertFalse(strategy.applyFilterToCamelHeaders("X-Custom-Header", "value", exchange),
                "Custom headers should not be filtered on outbound");
        assertFalse(strategy.applyFilterToExternalHeaders("X-Custom-Header", "value", exchange),
                "Custom headers should not be filtered on inbound");
    }
}
