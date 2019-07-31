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
package org.apache.camel.component.braintree;

import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;

import com.braintreegateway.BraintreeGateway;
import com.braintreegateway.ConnectedMerchantPayPalStatusChanged;
import com.braintreegateway.ConnectedMerchantStatusTransitioned;
import com.braintreegateway.WebhookNotification;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.braintree.internal.BraintreeApiCollection;
import org.apache.camel.component.braintree.internal.BraintreeConstants;
import org.apache.camel.component.braintree.internal.WebhookNotificationGatewayApiMethod;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

public class WebhookNotificationGatewayIntegrationTest extends AbstractBraintreeTestSupport {
    private static final String PATH_PREFIX = BraintreeApiCollection.getCollection().getApiName(WebhookNotificationGatewayApiMethod.class).getName();

    @Before
    public void checkAuthenticationType() {
        Assume.assumeTrue(checkAuthenticationType(AuthenticationType.PUBLIC_PRIVATE_KEYS));
    }

    @Test
    public void testParseSubscription() throws Exception {
        runParseSubscriptionTest(WebhookNotification.Kind.SUBSCRIPTION_CANCELED);
        runParseSubscriptionTest(WebhookNotification.Kind.SUBSCRIPTION_CHARGED_SUCCESSFULLY);
        runParseSubscriptionTest(WebhookNotification.Kind.SUBSCRIPTION_CHARGED_UNSUCCESSFULLY);
        runParseSubscriptionTest(WebhookNotification.Kind.SUBSCRIPTION_TRIAL_ENDED);
        runParseSubscriptionTest(WebhookNotification.Kind.SUBSCRIPTION_WENT_ACTIVE);
        runParseSubscriptionTest(WebhookNotification.Kind.SUBSCRIPTION_WENT_PAST_DUE);
    }

    private void runParseSubscriptionTest(WebhookNotification.Kind kind) throws Exception {
        final WebhookNotification result = sendSampleNotification(kind, "my_id");
        assertNotNull("parse result", result);
        assertEquals(kind, result.getKind());
        assertEquals("my_id", result.getSubscription().getId());
    }

    @Test
    public void testParseMerchantAccount() throws Exception {
        runParseMerchantAccountTest(WebhookNotification.Kind.SUB_MERCHANT_ACCOUNT_APPROVED);
        runParseMerchantAccountTest(WebhookNotification.Kind.SUB_MERCHANT_ACCOUNT_DECLINED);
    }

    private void runParseMerchantAccountTest(WebhookNotification.Kind kind) throws Exception {
        final WebhookNotification result = sendSampleNotification(kind, "my_id");
        assertNotNull("parse result", result);
        assertEquals(kind, result.getKind());
        assertEquals("my_id", result.getMerchantAccount().getId());
    }

    @Test
    public void testParseTransaction() throws Exception {
        runParseTransactionTest(WebhookNotification.Kind.TRANSACTION_DISBURSED);
        runParseTransactionTest(WebhookNotification.Kind.TRANSACTION_SETTLED);
        runParseTransactionTest(WebhookNotification.Kind.TRANSACTION_SETTLEMENT_DECLINED);
    }

    private void runParseTransactionTest(WebhookNotification.Kind kind) throws Exception {
        final WebhookNotification result = sendSampleNotification(kind, "my_id");
        assertNotNull("parse result", result);
        assertEquals(kind, result.getKind());
        assertEquals("my_id", result.getTransaction().getId());
    }

    @Test
    public void testParseDisbursement() throws Exception {
        runParseDisbursementTest(WebhookNotification.Kind.DISBURSEMENT);
        runParseDisbursementTest(WebhookNotification.Kind.DISBURSEMENT_EXCEPTION);
    }

    private void runParseDisbursementTest(WebhookNotification.Kind kind) throws Exception {
        final WebhookNotification result = sendSampleNotification(kind, "my_id");
        assertNotNull("parse result", result);
        assertEquals(kind, result.getKind());
        assertEquals("my_id", result.getDisbursement().getId());
    }

    @Test
    public void testParseDispute() throws Exception {
        runParseDisputeTest(WebhookNotification.Kind.DISPUTE_OPENED);
        runParseDisputeTest(WebhookNotification.Kind.DISPUTE_LOST);
        runParseDisputeTest(WebhookNotification.Kind.DISPUTE_WON);
    }

    private void runParseDisputeTest(WebhookNotification.Kind kind) throws Exception {
        final WebhookNotification result = sendSampleNotification(kind, "my_id");
        assertNotNull("parse result", result);
        assertEquals(kind, result.getKind());
        assertEquals("my_id", result.getDispute().getId());
    }

    @Test
    public void testParsePartnerMerchant() throws Exception {
        runParsePartnerMerchantTest(WebhookNotification.Kind.PARTNER_MERCHANT_CONNECTED);
        runParsePartnerMerchantTest(WebhookNotification.Kind.PARTNER_MERCHANT_DISCONNECTED);
        runParsePartnerMerchantTest(WebhookNotification.Kind.PARTNER_MERCHANT_DECLINED);
    }

    private void runParsePartnerMerchantTest(WebhookNotification.Kind kind) throws Exception {
        final WebhookNotification result = sendSampleNotification(kind, "merchant_public_id");
        assertNotNull("parse result", result);
        assertEquals(kind, result.getKind());
        assertEquals("abc123", result.getPartnerMerchant().getPartnerMerchantId());
    }

    @Test
    public void testParseConnectedMerchantStatusTransitioned() throws Exception {
        final WebhookNotification result = sendSampleNotification(
                WebhookNotification.Kind.CONNECTED_MERCHANT_STATUS_TRANSITIONED,
                "my_merchant_public_id"
        );

        assertNotNull("parse result", result);
        assertEquals(WebhookNotification.Kind.CONNECTED_MERCHANT_STATUS_TRANSITIONED, result.getKind());

        ConnectedMerchantStatusTransitioned connectedMerchantStatusTransitioned = result.getConnectedMerchantStatusTransitioned();
        assertEquals("my_merchant_public_id", connectedMerchantStatusTransitioned.getMerchantPublicId());
        assertEquals("oauth_application_client_id", connectedMerchantStatusTransitioned.getOAuthApplicationClientId());
        assertEquals("new_status", connectedMerchantStatusTransitioned.getStatus());
    }

    @Test
    public void testParseConnectedMerchantPayPalStatusChanged() throws Exception {
        final WebhookNotification result = sendSampleNotification(
                WebhookNotification.Kind.CONNECTED_MERCHANT_PAYPAL_STATUS_CHANGED,
                "my_merchant_public_id"
        );

        assertNotNull("parse result", result);
        assertEquals(WebhookNotification.Kind.CONNECTED_MERCHANT_PAYPAL_STATUS_CHANGED, result.getKind());

        ConnectedMerchantPayPalStatusChanged connectedMerchantPayPalStatusChanged = result.getConnectedMerchantPayPalStatusChanged();
        assertEquals("my_merchant_public_id", connectedMerchantPayPalStatusChanged.getMerchantPublicId());
        assertEquals("oauth_application_client_id", connectedMerchantPayPalStatusChanged.getOAuthApplicationClientId());
        assertEquals("link", connectedMerchantPayPalStatusChanged.getAction());
    }

    @Test
    public void testParseAccountUpdater() throws Exception {
        runParsePAccountUpdaterTest(WebhookNotification.Kind.ACCOUNT_UPDATER_DAILY_REPORT);
    }

    private void runParsePAccountUpdaterTest(WebhookNotification.Kind kind) throws Exception {
        final WebhookNotification result = sendSampleNotification(kind, "my_id");
        assertNotNull("parse result", result);
        assertEquals(kind, result.getKind());
        assertEquals("link-to-csv-report", result.getAccountUpdaterDailyReport().getReportUrl());
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        assertEquals("2016-01-14", sdf.format(result.getAccountUpdaterDailyReport().getReportDate().getTime()));
    }

    /* see https://issues.apache.org/jira/browse/CAMEL-12180
    @Test
    public void testParseIdealPayment() throws Exception {
        runParseIdealPaymentTest(WebhookNotification.Kind.IDEAL_PAYMENT_COMPLETE);
        runParseIdealPaymentTest(WebhookNotification.Kind.IDEAL_PAYMENT_FAILED);
    }

    private void runParseIdealPaymentTest(WebhookNotification.Kind kind) throws Exception {
        final WebhookNotification result = sendSampleNotification(kind, "my_id");
        assertNotNull("parse result", result);
        assertEquals(kind, result.getKind());
        assertEquals("my_id", result.getIdealPayment().getId());
    }

    @Test
    public void testParsePaymentInstrument() throws Exception {
        runParsePaymentInstrumentTest(WebhookNotification.Kind.GRANTED_PAYMENT_INSTRUMENT_UPDATE);
    }

    private void runParsePaymentInstrumentTest(WebhookNotification.Kind kind) throws Exception {
        final WebhookNotification result = sendSampleNotification(kind, "my_id");
        assertNotNull("parse result", result);
        assertEquals(kind, result.getKind());
        assertEquals("abc123z", result.getGrantedPaymentInstrumentUpdate().getToken());
    }*/

    private WebhookNotification sendSampleNotification(WebhookNotification.Kind kind, String id) {
        final BraintreeGateway gateway = getGateway();
        Map<String, String> notification = gateway.webhookTesting().sampleNotification(kind, id);
        final Map<String, Object> headers = new HashMap<>();
        headers.put(BraintreeConstants.PROPERTY_PREFIX + "signature", notification.get("bt_signature"));
        headers.put(BraintreeConstants.PROPERTY_PREFIX + "payload", notification.get("bt_payload"));
        return requestBodyAndHeaders("direct://PARSE", null, headers);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                // test route for parse
                from("direct://PARSE")
                    .to("braintree://" + PATH_PREFIX + "/parse");
                // test route for verify
                from("direct://VERIFY")
                    .to("braintree://" + PATH_PREFIX + "/verify?inBody=challenge");
            }
        };
    }
}
