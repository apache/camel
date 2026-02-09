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
import org.apache.camel.component.telegram.model.IncomingMessage;
import org.apache.camel.component.telegram.model.payments.OrderInfo;
import org.apache.camel.component.telegram.model.payments.ShippingAddress;
import org.apache.camel.component.telegram.model.payments.SuccessfulPayment;
import org.apache.camel.component.telegram.util.TelegramMockRoutes;
import org.apache.camel.component.telegram.util.TelegramTestSupport;
import org.apache.camel.component.telegram.util.TelegramTestUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests that successful payment updates are properly received and converted.
 */
public class TelegramConsumerSuccessfulPaymentTest extends TelegramTestSupport {

    @EndpointInject("mock:telegram")
    private MockEndpoint endpoint;

    @Test
    public void testReceptionOfSuccessfulPayment() throws Exception {
        endpoint.expectedMinimumMessageCount(1);
        endpoint.assertIsSatisfied(5000);

        Exchange exchange = endpoint.getExchanges().get(0);
        IncomingMessage message = exchange.getIn().getBody(IncomingMessage.class);

        assertNotNull(message);
        SuccessfulPayment payment = message.getSuccessfulPayment();
        assertNotNull(payment);

        assertEquals("USD", payment.getCurrency());
        assertEquals(Integer.valueOf(1000), payment.getTotalAmount());
        assertEquals("test_payload", payment.getInvoicePayload());
        assertEquals("shipping_option_1", payment.getShippingOptionId());
        assertEquals("telegram_charge_123", payment.getTelegramPaymentChargeId());
        assertEquals("provider_charge_456", payment.getProviderPaymentChargeId());

        OrderInfo orderInfo = payment.getOrderInfo();
        assertNotNull(orderInfo);
        assertEquals("John Doe", orderInfo.getName());
        assertEquals("+15551234567", orderInfo.getPhoneNumber());
        assertEquals("john@example.com", orderInfo.getEmail());

        ShippingAddress address = orderInfo.getShippingAddress();
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
                        TelegramTestUtil.stringResource("messages/updates-successful-payment.json"),
                        TelegramTestUtil.stringResource("messages/updates-empty.json"));
    }
}
