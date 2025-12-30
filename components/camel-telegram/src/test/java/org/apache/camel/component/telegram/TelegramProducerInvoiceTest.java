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

import java.util.Arrays;
import java.util.Collections;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.telegram.model.InlineKeyboardButton;
import org.apache.camel.component.telegram.model.InlineKeyboardMarkup;
import org.apache.camel.component.telegram.model.MessageResult;
import org.apache.camel.component.telegram.model.MessageResultString;
import org.apache.camel.component.telegram.model.payments.AnswerPreCheckoutQueryMessage;
import org.apache.camel.component.telegram.model.payments.AnswerShippingQueryMessage;
import org.apache.camel.component.telegram.model.payments.CreateInvoiceLinkMessage;
import org.apache.camel.component.telegram.model.payments.LabeledPrice;
import org.apache.camel.component.telegram.model.payments.SendInvoiceMessage;
import org.apache.camel.component.telegram.model.payments.ShippingOption;
import org.apache.camel.component.telegram.util.TelegramMockRoutes;
import org.apache.camel.component.telegram.util.TelegramMockRoutes.MockProcessor;
import org.apache.camel.component.telegram.util.TelegramTestSupport;
import org.apache.camel.component.telegram.util.TelegramTestUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests a producer that sends invoices and handles payment operations.
 */
public class TelegramProducerInvoiceTest extends TelegramTestSupport {

    @Test
    public void testSendInvoice() {
        SendInvoiceMessage msg = new SendInvoiceMessage();
        msg.setTitle("Test Product");
        msg.setDescription("Test product description");
        msg.setPayload("test_payload");
        msg.setCurrency("USD");
        msg.setPrices(Collections.singletonList(new LabeledPrice("Product Price", 1000)));

        template.requestBody("direct:telegram", msg, MessageResult.class);

        final MockProcessor<SendInvoiceMessage> mockProcessor = getMockRoutes().getMock("sendInvoice");
        assertThat(mockProcessor.awaitRecordedMessages(1, 5000).get(0))
                .usingRecursiveComparison()
                .isEqualTo(msg);
    }

    @Test
    public void testAnswerPreCheckoutQuery() {
        AnswerPreCheckoutQueryMessage msg = new AnswerPreCheckoutQueryMessage("pre_checkout_query_123", true, null);

        template.requestBody("direct:telegram", msg);

        final MockProcessor<AnswerPreCheckoutQueryMessage> mockProcessor = getMockRoutes().getMock("answerPreCheckoutQuery");
        assertThat(mockProcessor.awaitRecordedMessages(1, 5000).get(0))
                .usingRecursiveComparison()
                .isEqualTo(msg);
    }

    @Test
    public void testAnswerPreCheckoutQueryWithError() {
        AnswerPreCheckoutQueryMessage msg
                = new AnswerPreCheckoutQueryMessage("pre_checkout_query_123", false, "Payment declined");

        template.requestBody("direct:telegram", msg);

        final MockProcessor<AnswerPreCheckoutQueryMessage> mockProcessor = getMockRoutes().getMock("answerPreCheckoutQuery");
        AnswerPreCheckoutQueryMessage recorded = mockProcessor.awaitRecordedMessages(1, 5000).get(0);
        assertThat(recorded.getPreCheckoutQueryId()).isEqualTo("pre_checkout_query_123");
        assertThat(recorded.getOk()).isFalse();
        assertThat(recorded.getErrorMessage()).isEqualTo("Payment declined");
    }

    @Test
    public void testCreateInvoiceLink() {
        CreateInvoiceLinkMessage msg = new CreateInvoiceLinkMessage();
        msg.setTitle("Test Product");
        msg.setDescription("Test product description");
        msg.setPayload("test_payload");
        msg.setCurrency("USD");
        msg.setPrices(Collections.singletonList(new LabeledPrice("Product Price", 1000)));

        MessageResultString result = template.requestBody("direct:telegram", msg, MessageResultString.class);

        assertThat(result).isNotNull();
        assertThat(result.getResult()).isEqualTo("https://t.me/$invoice_link_123");

        final MockProcessor<CreateInvoiceLinkMessage> mockProcessor = getMockRoutes().getMock("createInvoiceLink");
        assertThat(mockProcessor.awaitRecordedMessages(1, 5000).get(0))
                .usingRecursiveComparison()
                .isEqualTo(msg);
    }

    @Test
    public void testAnswerShippingQuery() {
        ShippingOption option = new ShippingOption(
                "shipping_standard",
                "Standard Shipping",
                Collections.singletonList(new LabeledPrice("Shipping", 500)));

        AnswerShippingQueryMessage msg = new AnswerShippingQueryMessage(
                "shipping_query_123", true, Collections.singletonList(option), null);

        template.requestBody("direct:telegram", msg);

        final MockProcessor<AnswerShippingQueryMessage> mockProcessor = getMockRoutes().getMock("answerShippingQuery");
        AnswerShippingQueryMessage recorded = mockProcessor.awaitRecordedMessages(1, 5000).get(0);
        assertThat(recorded.getShippingQueryId()).isEqualTo("shipping_query_123");
        assertThat(recorded.getOk()).isTrue();
        assertThat(recorded.getShippingOptions()).hasSize(1);
        assertThat(recorded.getShippingOptions().get(0).getId()).isEqualTo("shipping_standard");
    }

    @Test
    public void testAnswerShippingQueryWithError() {
        AnswerShippingQueryMessage msg = new AnswerShippingQueryMessage(
                "shipping_query_123", false, null, "Cannot ship to this address");

        template.requestBody("direct:telegram", msg);

        final MockProcessor<AnswerShippingQueryMessage> mockProcessor = getMockRoutes().getMock("answerShippingQuery");
        AnswerShippingQueryMessage recorded = mockProcessor.awaitRecordedMessages(1, 5000).get(0);
        assertThat(recorded.getShippingQueryId()).isEqualTo("shipping_query_123");
        assertThat(recorded.getOk()).isFalse();
        assertThat(recorded.getErrorMessage()).isEqualTo("Cannot ship to this address");
    }

    @Test
    public void testAnswerShippingQueryWithMultipleOptions() {
        ShippingOption standardOption = new ShippingOption(
                "shipping_standard",
                "Standard Shipping (5-7 days)",
                Collections.singletonList(new LabeledPrice("Standard Shipping", 500)));
        ShippingOption expressOption = new ShippingOption(
                "shipping_express",
                "Express Shipping (1-2 days)",
                Collections.singletonList(new LabeledPrice("Express Shipping", 1500)));

        AnswerShippingQueryMessage msg = new AnswerShippingQueryMessage(
                "shipping_query_123", true, Arrays.asList(standardOption, expressOption), null);

        template.requestBody("direct:telegram", msg);

        final MockProcessor<AnswerShippingQueryMessage> mockProcessor = getMockRoutes().getMock("answerShippingQuery");
        AnswerShippingQueryMessage recorded = mockProcessor.awaitRecordedMessages(1, 5000).get(0);
        assertThat(recorded.getShippingOptions()).hasSize(2);
        assertThat(recorded.getShippingOptions().get(0).getTitle()).isEqualTo("Standard Shipping (5-7 days)");
        assertThat(recorded.getShippingOptions().get(1).getTitle()).isEqualTo("Express Shipping (1-2 days)");
    }

    @Test
    public void testInlineKeyboardMarkupWithPayButton() {
        InlineKeyboardButton payButton = InlineKeyboardButton.builder()
                .text("Pay $10.00")
                .pay(true)
                .build();

        assertThat(payButton.getText()).isEqualTo("Pay $10.00");
        assertThat(payButton.getPay()).isTrue();

        InlineKeyboardMarkup markup = InlineKeyboardMarkup.builder()
                .addRow(Collections.singletonList(payButton))
                .build();

        assertThat(markup.getInlineKeyboard()).hasSize(1);
        assertThat(markup.getInlineKeyboard().get(0)).hasSize(1);
        assertThat(markup.getInlineKeyboard().get(0).get(0).getPay()).isTrue();
        assertThat(markup.getInlineKeyboard().get(0).get(0).getText()).isEqualTo("Pay $10.00");

        // Verify it can be set on SendInvoiceMessage
        SendInvoiceMessage msg = new SendInvoiceMessage();
        msg.setReplyMarkup(markup);
        assertThat(msg.getReplyMarkup()).isEqualTo(markup);
    }

    @Test
    public void testInlineKeyboardButtonPayFieldBuilder() {
        InlineKeyboardButton button = InlineKeyboardButton.builder()
                .text("Pay Now")
                .pay(true)
                .build();

        assertThat(button.getText()).isEqualTo("Pay Now");
        assertThat(button.getPay()).isTrue();
        assertThat(button.getUrl()).isNull();
        assertThat(button.getCallbackData()).isNull();
    }

    @Test
    public void testInlineKeyboardButtonPayFieldConstructor() {
        InlineKeyboardButton button = new InlineKeyboardButton(
                "Pay Now", null, null, null, null, null, null, true);

        assertThat(button.getText()).isEqualTo("Pay Now");
        assertThat(button.getPay()).isTrue();
    }

    @Override
    protected RoutesBuilder[] createRouteBuilders() {
        return new RoutesBuilder[] {
                getMockRoutes(),
                new RouteBuilder() {
                    @Override
                    public void configure() {
                        from("direct:telegram").to("telegram:bots?authorizationToken=mock-token&chatId=" + chatId);
                    }
                } };
    }

    @Override
    protected TelegramMockRoutes createMockRoutes() {
        return new TelegramMockRoutes(port)
                .addEndpoint(
                        "sendInvoice",
                        "POST",
                        SendInvoiceMessage.class,
                        TelegramTestUtil.stringResource("messages/send-invoice.json"))
                .addEndpoint(
                        "answerPreCheckoutQuery",
                        "POST",
                        AnswerPreCheckoutQueryMessage.class,
                        TelegramTestUtil.stringResource("messages/answer-pre-checkout-query.json"))
                .addEndpoint(
                        "createInvoiceLink",
                        "POST",
                        CreateInvoiceLinkMessage.class,
                        TelegramTestUtil.stringResource("messages/create-invoice-link.json"))
                .addEndpoint(
                        "answerShippingQuery",
                        "POST",
                        AnswerShippingQueryMessage.class,
                        TelegramTestUtil.stringResource("messages/answer-shipping-query.json"));
    }
}
