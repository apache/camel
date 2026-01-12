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
import org.apache.camel.component.telegram.model.User;
import org.apache.camel.component.telegram.model.payments.PaidMediaPurchased;
import org.apache.camel.component.telegram.util.TelegramMockRoutes;
import org.apache.camel.component.telegram.util.TelegramTestSupport;
import org.apache.camel.component.telegram.util.TelegramTestUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests that paid media purchased updates are properly received and converted.
 */
public class TelegramConsumerPaidMediaPurchasedTest extends TelegramTestSupport {

    @EndpointInject("mock:telegram")
    private MockEndpoint endpoint;

    @Test
    public void testReceptionOfPaidMediaPurchased() throws Exception {
        endpoint.expectedMinimumMessageCount(1);
        endpoint.assertIsSatisfied(5000);

        Exchange exchange = endpoint.getExchanges().get(0);
        PaidMediaPurchased paidMediaPurchased = exchange.getIn().getBody(PaidMediaPurchased.class);

        assertNotNull(paidMediaPurchased);

        // Verify from user
        User from = paidMediaPurchased.getFrom();
        assertNotNull(from);
        assertEquals(Long.valueOf(1585844777L), from.getId());
        assertFalse(from.isBot());
        assertEquals("John", from.getFirstName());
        assertEquals("Doe", from.getLastName());
        assertEquals("johndoe", from.getUsername());

        // Verify paid media payload
        assertEquals("premium_content_12345", paidMediaPurchased.getPaidMediaPayload());
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
                        TelegramTestUtil.stringResource("messages/updates-paid-media-purchased.json"),
                        TelegramTestUtil.stringResource("messages/updates-empty.json"));
    }
}
