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
package org.apache.camel.component.telegram;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.telegram.model.payments.ShippingAddress;
import org.apache.camel.component.telegram.model.payments.ShippingQuery;
import org.apache.camel.component.telegram.util.TelegramMockRoutes;
import org.apache.camel.component.telegram.util.TelegramTestSupport;
import org.apache.camel.component.telegram.util.TelegramTestUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests that shipping query updates are properly received and converted.
 */
public class TelegramConsumerShippingQueryTest extends TelegramTestSupport {

    @EndpointInject("mock:telegram")
    private MockEndpoint endpoint;

    @Test
    public void testReceptionOfShippingQuery() throws Exception {
        endpoint.expectedMinimumMessageCount(1);
        endpoint.assertIsSatisfied(5000);

        Exchange exchange = endpoint.getExchanges().get(0);
        ShippingQuery query = exchange.getIn().getBody(ShippingQuery.class);

        assertNotNull(query);
        assertEquals("shipping_query_123", query.getId());
        assertEquals("test_payload", query.getInvoicePayload());
        assertEquals("Doe", query.getFrom().getLastName());
        assertEquals("John", query.getFrom().getFirstName());

        ShippingAddress address = query.getShippingAddress();
        assertNotNull(address);
        assertEquals("US", address.getCountryCode());
        assertEquals("California", address.getState());
        assertEquals("San Francisco", address.getCity());
        assertEquals("123 Main St", address.getStreetLine1());
        assertEquals("Apt 4", address.getStreetLine2());
        assertEquals("94102", address.getPostCode());
    }

    @Override
    protected RoutesBuilder[] createRouteBuilders() {
        return new RoutesBuilder[] {
                getMockRoutes(),
                new RouteBuilder() {
                    @Override
                    public void configure() {
                        from("telegram:bots?authorizationToken=mock-token")
                                .to("mock:telegram");
                    }
                } };
    }

    @Override
    protected TelegramMockRoutes createMockRoutes() {
        return new TelegramMockRoutes(port)
                .addEndpoint(
                        "getUpdates",
                        "GET",
                        String.class,
                        TelegramTestUtil.stringResource("messages/updates-shipping-query.json"),
                        TelegramTestUtil.stringResource("messages/updates-empty.json"));
    }
}
