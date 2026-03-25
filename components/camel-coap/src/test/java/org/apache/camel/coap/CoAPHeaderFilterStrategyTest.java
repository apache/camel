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
package org.apache.camel.coap;

import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.CoapResponse;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Test that the default {@link CoAPHeaderFilterStrategy} prevents external CoAP clients from injecting internal Camel
 * headers via query parameters.
 */
public class CoAPHeaderFilterStrategyTest extends CoAPTestSupport {

    @Test
    void testCamelHeadersAreFilteredFromQueryParameters() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        // Send a CoAP request with a Camel-prefixed query parameter that should be filtered
        CoapClient client = createClient("/test?CamelHttpMethod=DELETE&safeParam=hello");
        CoapResponse response = client.post("body", MediaTypeRegistry.TEXT_PLAIN);

        mock.assertIsSatisfied();

        Exchange exchange = mock.getReceivedExchanges().get(0);
        // CamelHttpMethod should be filtered out by the header filter strategy
        assertNull(exchange.getIn().getHeader("CamelHttpMethod"),
                "Camel-prefixed header should be filtered from external CoAP query parameters");
        // Non-Camel headers should pass through
        assertEquals("hello", exchange.getIn().getHeader("safeParam"),
                "Non-Camel headers should be allowed through");
    }

    @Test
    void testCamelLowercaseHeadersAreFiltered() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        CoapClient client = createClient("/test?camelInternal=secret&allowed=value");
        CoapResponse response = client.post("body", MediaTypeRegistry.TEXT_PLAIN);

        mock.assertIsSatisfied();

        Exchange exchange = mock.getReceivedExchanges().get(0);
        assertNull(exchange.getIn().getHeader("camelInternal"),
                "Lowercase camel-prefixed header should be filtered");
        assertEquals("value", exchange.getIn().getHeader("allowed"),
                "Non-Camel headers should be allowed through");
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                fromF("coap://localhost:%d/test", PORT.getPort())
                        .to("mock:result");
            }
        };
    }
}
