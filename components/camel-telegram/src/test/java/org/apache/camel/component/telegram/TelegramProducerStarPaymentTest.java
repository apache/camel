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

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.telegram.model.payments.EditUserStarSubscriptionMessage;
import org.apache.camel.component.telegram.model.payments.GetMyStarBalanceMessage;
import org.apache.camel.component.telegram.model.payments.GetStarTransactionsMessage;
import org.apache.camel.component.telegram.model.payments.MessageResultStarAmount;
import org.apache.camel.component.telegram.model.payments.MessageResultStarTransactions;
import org.apache.camel.component.telegram.model.payments.RefundStarPaymentMessage;
import org.apache.camel.component.telegram.model.payments.StarAmount;
import org.apache.camel.component.telegram.model.payments.StarTransaction;
import org.apache.camel.component.telegram.model.payments.StarTransactions;
import org.apache.camel.component.telegram.util.TelegramMockRoutes;
import org.apache.camel.component.telegram.util.TelegramMockRoutes.MockProcessor;
import org.apache.camel.component.telegram.util.TelegramTestSupport;
import org.apache.camel.component.telegram.util.TelegramTestUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests a producer that handles Telegram Star payment operations.
 */
public class TelegramProducerStarPaymentTest extends TelegramTestSupport {

    @Test
    public void testGetMyStarBalance() {
        GetMyStarBalanceMessage msg = new GetMyStarBalanceMessage();

        MessageResultStarAmount result = template.requestBody("direct:telegram", msg, MessageResultStarAmount.class);

        assertThat(result).isNotNull();
        assertThat(result.isOk()).isTrue();

        StarAmount starAmount = result.getStarAmount();
        assertThat(starAmount).isNotNull();
        assertThat(starAmount.getAmount()).isEqualTo(1500);
        assertThat(starAmount.getNanostarAmount()).isEqualTo(500000000);

        final MockProcessor<GetMyStarBalanceMessage> mockProcessor = getMockRoutes().getMock("getMyStarBalance");
        assertThat(mockProcessor.awaitRecordedMessages(1, 5000)).hasSize(1);
    }

    @Test
    public void testGetStarTransactions() {
        GetStarTransactionsMessage msg = new GetStarTransactionsMessage();

        MessageResultStarTransactions result
                = template.requestBody("direct:telegram", msg, MessageResultStarTransactions.class);

        assertThat(result).isNotNull();
        assertThat(result.isOk()).isTrue();

        StarTransactions starTransactions = result.getStarTransactions();
        assertThat(starTransactions).isNotNull();
        assertThat(starTransactions.getTransactions()).hasSize(2);

        StarTransaction firstTxn = starTransactions.getTransactions().get(0);
        assertThat(firstTxn.getId()).isEqualTo("txn_001");
        assertThat(firstTxn.getAmount()).isEqualTo(100);
        assertThat(firstTxn.getNanostarAmount()).isEqualTo(0);
        assertThat(firstTxn.getDate()).isEqualTo(1704067200L);
        assertThat(firstTxn.getSource()).isNotNull();

        StarTransaction secondTxn = starTransactions.getTransactions().get(1);
        assertThat(secondTxn.getId()).isEqualTo("txn_002");
        assertThat(secondTxn.getAmount()).isEqualTo(50);
        assertThat(secondTxn.getNanostarAmount()).isEqualTo(250000000);
        assertThat(secondTxn.getReceiver()).isNotNull();

        final MockProcessor<GetStarTransactionsMessage> mockProcessor = getMockRoutes().getMock("getStarTransactions");
        assertThat(mockProcessor.awaitRecordedMessages(1, 5000)).hasSize(1);
    }

    @Test
    public void testGetStarTransactionsWithOffsetAndLimit() {
        GetStarTransactionsMessage msg = new GetStarTransactionsMessage(10, 50);

        template.requestBody("direct:telegram", msg, MessageResultStarTransactions.class);

        final MockProcessor<GetStarTransactionsMessage> mockProcessor = getMockRoutes().getMock("getStarTransactions");
        GetStarTransactionsMessage recorded = mockProcessor.awaitRecordedMessages(1, 5000).get(0);
        assertThat(recorded.getOffset()).isEqualTo(10);
        assertThat(recorded.getLimit()).isEqualTo(50);
    }

    @Test
    public void testRefundStarPayment() {
        RefundStarPaymentMessage msg = new RefundStarPaymentMessage(1585844777L, "telegram_charge_123");

        template.requestBody("direct:telegram", msg);

        final MockProcessor<RefundStarPaymentMessage> mockProcessor = getMockRoutes().getMock("refundStarPayment");
        RefundStarPaymentMessage recorded = mockProcessor.awaitRecordedMessages(1, 5000).get(0);
        assertThat(recorded.getUserId()).isEqualTo(1585844777L);
        assertThat(recorded.getTelegramPaymentChargeId()).isEqualTo("telegram_charge_123");
    }

    @Test
    public void testEditUserStarSubscriptionCancel() {
        EditUserStarSubscriptionMessage msg = new EditUserStarSubscriptionMessage(1585844777L, "subscription_charge_456", true);

        template.requestBody("direct:telegram", msg);

        final MockProcessor<EditUserStarSubscriptionMessage> mockProcessor
                = getMockRoutes().getMock("editUserStarSubscription");
        EditUserStarSubscriptionMessage recorded = mockProcessor.awaitRecordedMessages(1, 5000).get(0);
        assertThat(recorded.getUserId()).isEqualTo(1585844777L);
        assertThat(recorded.getTelegramPaymentChargeId()).isEqualTo("subscription_charge_456");
        assertThat(recorded.getIsCanceled()).isTrue();
    }

    @Test
    public void testEditUserStarSubscriptionReEnable() {
        EditUserStarSubscriptionMessage msg
                = new EditUserStarSubscriptionMessage(1585844777L, "subscription_charge_789", false);

        template.requestBody("direct:telegram", msg);

        final MockProcessor<EditUserStarSubscriptionMessage> mockProcessor
                = getMockRoutes().getMock("editUserStarSubscription");
        EditUserStarSubscriptionMessage recorded = mockProcessor.awaitRecordedMessages(1, 5000).get(0);
        assertThat(recorded.getUserId()).isEqualTo(1585844777L);
        assertThat(recorded.getTelegramPaymentChargeId()).isEqualTo("subscription_charge_789");
        assertThat(recorded.getIsCanceled()).isFalse();
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
                        "getMyStarBalance",
                        "POST",
                        GetMyStarBalanceMessage.class,
                        TelegramTestUtil.stringResource("messages/get-my-star-balance.json"))
                .addEndpoint(
                        "getStarTransactions",
                        "POST",
                        GetStarTransactionsMessage.class,
                        TelegramTestUtil.stringResource("messages/get-star-transactions.json"))
                .addEndpoint(
                        "refundStarPayment",
                        "POST",
                        RefundStarPaymentMessage.class,
                        TelegramTestUtil.stringResource("messages/refund-star-payment.json"))
                .addEndpoint(
                        "editUserStarSubscription",
                        "POST",
                        EditUserStarSubscriptionMessage.class,
                        TelegramTestUtil.stringResource("messages/edit-user-star-subscription.json"));
    }
}
