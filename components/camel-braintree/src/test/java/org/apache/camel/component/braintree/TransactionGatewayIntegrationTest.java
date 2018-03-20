/**
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

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.braintreegateway.BraintreeGateway;
import com.braintreegateway.Result;
import com.braintreegateway.Transaction;
import com.braintreegateway.TransactionCloneRequest;
import com.braintreegateway.TransactionRefundRequest;
import com.braintreegateway.TransactionRequest;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.braintree.internal.BraintreeApiCollection;
import org.apache.camel.component.braintree.internal.TransactionGatewayApiMethod;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TransactionGatewayIntegrationTest extends AbstractBraintreeTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(TransactionGatewayIntegrationTest.class);
    private static final String PATH_PREFIX = BraintreeApiCollection.getCollection().getApiName(TransactionGatewayApiMethod.class).getName();

    private BraintreeGateway gateway;
    private final List<String> transactionIds;

    // *************************************************************************
    //
    // *************************************************************************

    public TransactionGatewayIntegrationTest() {
        this.gateway = null;
        this.transactionIds = new LinkedList<>();
    }

    @Override
    protected void doPostSetup() throws Exception {
        this.gateway = getGateway();
    }

    @Override
    public void tearDown() throws Exception {
        if (this.gateway != null) {
            for (String token : this.transactionIds) {
                // TODO: cleanup
            }
        }
        this.transactionIds.clear();
    }

    // *************************************************************************
    //
    // *************************************************************************

    @Test
    public void testSale() throws Exception {
        assertNotNull("BraintreeGateway can't be null", this.gateway);

        final Result<Transaction> result = requestBody(
            "direct://SALE",
            new TransactionRequest()
                .amount(new BigDecimal("100.00"))
                .paymentMethodNonce("fake-valid-nonce")
                .options()
                    .submitForSettlement(true)
                .done(),
            Result.class);

        assertNotNull("sale result", result);
        assertTrue(result.isSuccess());

        LOG.info("Transaction done - id={}", result.getTarget().getId());
        this.transactionIds.add(result.getTarget().getId());
    }

    @Test
    public void testCloneTransaction() throws Exception {
        assertNotNull("BraintreeGateway can't be null", this.gateway);

        final Result<Transaction> createResult = requestBody(
            "direct://SALE",
            new TransactionRequest()
                .amount(new BigDecimal("100.00"))
                .paymentMethodNonce("fake-valid-nonce")
                .options()
                    .submitForSettlement(false)
                .done(),
            Result.class);

        assertNotNull("sale result", createResult);
        assertTrue(createResult.isSuccess());

        LOG.info("Transaction done - id={}", createResult.getTarget().getId());
        this.transactionIds.add(createResult.getTarget().getId());

        final Result<Transaction> cloneResult = requestBodyAndHeaders(
            "direct://CLONETRANSACTION",
            null,
            new BraintreeHeaderBuilder()
                .add("id", createResult.getTarget().getId())
                .add("cloneRequest", new TransactionCloneRequest()
                    .amount(new BigDecimal("99.00"))
                    .options()
                        .submitForSettlement(true)
                    .done())
                .build(),
            Result.class);

        assertNotNull("clone result", cloneResult);
        assertTrue(cloneResult.isSuccess());

        LOG.info("Clone Transaction done - clonedId={}, id={}",
            createResult.getTarget().getId(), cloneResult.getTarget().getId());

        this.transactionIds.add(cloneResult.getTarget().getId());
    }

    @Test
    public void testFind() throws Exception {
        assertNotNull("BraintreeGateway can't be null", this.gateway);

        final Result<Transaction> createResult = requestBody(
            "direct://SALE",
            new TransactionRequest()
                .amount(new BigDecimal("100.00"))
                .paymentMethodNonce("fake-valid-nonce")
                .options()
                    .submitForSettlement(false)
                .done(),
            Result.class);

        assertNotNull("sale result", createResult);
        assertTrue(createResult.isSuccess());

        LOG.info("Transaction done - id={}", createResult.getTarget().getId());
        this.transactionIds.add(createResult.getTarget().getId());


        // using String message body for single parameter "id"
        final Transaction result = requestBody("direct://FIND", createResult.getTarget().getId());

        assertNotNull("find result", result);
        LOG.info("Transaction found - id={}", result.getId());
    }

    @Test
    public void testSubmitForSettlementWithId() throws Exception {
        assertNotNull("BraintreeGateway can't be null", this.gateway);

        final Result<Transaction> createResult = requestBody(
            "direct://SALE",
            new TransactionRequest()
                .amount(new BigDecimal("100.00"))
                .paymentMethodNonce("fake-valid-nonce")
                .options()
                    .submitForSettlement(false)
                .done(),
            Result.class);

        assertNotNull("sale result", createResult);
        assertTrue(createResult.isSuccess());

        LOG.info("Transaction done - id={}", createResult.getTarget().getId());
        this.transactionIds.add(createResult.getTarget().getId());

        final Result<Transaction> result = requestBody(
            "direct://SUBMITFORSETTLEMENT_WITH_ID",
            createResult.getTarget().getId(),
            Result.class);

        assertNotNull("Submit For Settlement result", result);
        LOG.debug("Transaction submitted for settlement - id={}" + result.getTarget().getId());
    }

    @Test
    public void testSubmitForSettlementWithIdAndAmount() throws Exception {
        assertNotNull("BraintreeGateway can't be null", this.gateway);

        final Result<Transaction> createResult = requestBody(
            "direct://SALE",
            new TransactionRequest()
                .amount(new BigDecimal("100.00"))
                .paymentMethodNonce("fake-valid-nonce")
                .options()
                .submitForSettlement(false)
                .done(),
            Result.class);

        assertNotNull("sale result", createResult);
        assertTrue(createResult.isSuccess());

        LOG.info("Transaction done - id={}", createResult.getTarget().getId());
        this.transactionIds.add(createResult.getTarget().getId());

        final Result<Transaction> result = requestBodyAndHeaders(
            "direct://SUBMITFORSETTLEMENT_WITH_ID_ADN_AMOUNT",
            null,
            new BraintreeHeaderBuilder()
                .add("id", createResult.getTarget().getId())
                .add("amount", new BigDecimal("100.00"))
                .build(),
            Result.class);

        assertNotNull("Submit For Settlement result", result);
        LOG.debug("Transaction submitted for settlement - id={}" + result.getTarget().getId());
    }

    @Test
    public void testSubmitForSettlementWithRequest() throws Exception {
        assertNotNull("BraintreeGateway can't be null", this.gateway);

        final Result<Transaction> createResult = requestBody(
            "direct://SALE",
            new TransactionRequest()
                .amount(new BigDecimal("100.00"))
                .paymentMethodNonce("fake-valid-nonce")
                .options()
                    .submitForSettlement(false)
                .done(),
            Result.class);

        assertNotNull("sale result", createResult);
        assertTrue(createResult.isSuccess());

        LOG.info("Transaction done - id={}", createResult.getTarget().getId());
        this.transactionIds.add(createResult.getTarget().getId());

        final Result<Transaction> result = requestBodyAndHeaders(
            "direct://SUBMITFORSETTLEMENT_WITH_REQUEST",
            null,
            new BraintreeHeaderBuilder()
                .add("id", createResult.getTarget().getId())
                .add("request", new TransactionRequest()
                    .amount(new BigDecimal("100.00")))
                .build(),
            Result.class);

        assertNotNull("Submit For Settlement result", result);
        LOG.debug("Transaction submitted for settlement - id={}" + result.getTarget().getId());
    }

    @Test
    public void testRefund() throws Exception {
        assertNotNull("BraintreeGateway can't be null", this.gateway);

        final Result<Transaction> createResult = requestBody(
                "direct://SALE",
                new TransactionRequest()
                        .amount(new BigDecimal("100.00"))
                        .paymentMethodNonce("fake-valid-nonce")
                        .options()
                            .submitForSettlement(true)
                        .done(),
                Result.class
        );

        assertNotNull("sale result", createResult);
        assertTrue(createResult.isSuccess());

        String createId = createResult.getTarget().getId();

        final Result<Transaction> settleResult = this.gateway.testing().settle(createId);
        assertNotNull("settle result", settleResult);
        assertTrue(settleResult.isSuccess());

        final Result<Transaction> result = requestBody(
                "direct://REFUND_WITH_ID",
                createId,
                Result.class
        );

        assertNotNull("Request Refund result", result);
        assertTrue(result.isSuccess());
        LOG.info(String.format("Refund id(%s) created for transaction id(%s)", result.getTarget().getId(), createId));
    }

    @Test
    public void testRefundWithAmount() throws Exception {
        assertNotNull("BraintreeGateway can't be null", this.gateway);

        final Result<Transaction> createResult = requestBody(
                "direct://SALE",
                new TransactionRequest()
                        .amount(new BigDecimal("100.00"))
                        .paymentMethodNonce("fake-valid-nonce")
                        .options()
                        .submitForSettlement(true)
                        .done(),
                Result.class
        );

        assertNotNull("sale result", createResult);
        assertTrue(createResult.isSuccess());

        String createId = createResult.getTarget().getId();

        final Result<Transaction> settleResult = this.gateway.testing().settle(createId);
        assertNotNull("settle result", settleResult);
        assertTrue(settleResult.isSuccess());

        final Result<Transaction> result = requestBodyAndHeaders(
                "direct://REFUND",
                null,
                new BraintreeHeaderBuilder()
                        .add("id", createId)
                        .add("amount", new BigDecimal("99.00"))
                        .build(),
                Result.class
        );

        assertNotNull("Request Refund result", result);
        assertTrue(result.isSuccess());
        LOG.info(String.format("Refund id(%s) created for transaction id(%s)", result.getTarget().getId(), createId));
    }

    @Test
    public void testRefundWithRequest() throws Exception {
        assertNotNull("BraintreeGateway can't be null", this.gateway);

        final Result<Transaction> createResult = requestBody(
                "direct://SALE",
                new TransactionRequest()
                        .amount(new BigDecimal("100.00"))
                        .paymentMethodNonce("fake-valid-nonce")
                        .options()
                        .submitForSettlement(true)
                        .done(),
                Result.class
        );

        assertNotNull("sale result", createResult);
        assertTrue(createResult.isSuccess());

        String createId = createResult.getTarget().getId();

        final Result<Transaction> settleResult = this.gateway.testing().settle(createId);
        assertNotNull("settle result", settleResult);
        assertTrue(settleResult.isSuccess());

        final Result<Transaction> result = requestBodyAndHeaders(
                "direct://REFUND",
                null,
                new BraintreeHeaderBuilder()
                        .add("id", createId)
                        .add("refundRequest", new TransactionRefundRequest()
                                .amount(new BigDecimal("100.00")))
                        .build(),
                Result.class
        );

        assertNotNull("Request Refund result", result);
        assertTrue(result.isSuccess());
        LOG.info(String.format("Refund id(%s) created for transaction id(%s)", result.getTarget().getId(), createId));
    }

    // *************************************************************************
    // Auto generated tests
    // *************************************************************************

    // TODO provide parameter values for cancelRelease
    @Ignore
    @Test
    public void testCancelRelease() throws Exception {
        // using String message body for single parameter "id"
        final com.braintreegateway.Result result = requestBody("direct://CANCELRELEASE", null);

        assertNotNull("cancelRelease result", result);
        LOG.debug("cancelRelease: " + result);
    }

    // TODO provide parameter values for credit
    @Ignore
    @Test
    public void testCredit() throws Exception {
        // using com.braintreegateway.TransactionRequest message body for single parameter "request"
        final com.braintreegateway.Result result = requestBody("direct://CREDIT", null);

        assertNotNull("credit result", result);
        LOG.debug("credit: " + result);
    }

    // TODO provide parameter values for holdInEscrow
    @Ignore
    @Test
    public void testHoldInEscrow() throws Exception {
        // using String message body for single parameter "id"
        final com.braintreegateway.Result result = requestBody("direct://HOLDINESCROW", null);

        assertNotNull("holdInEscrow result", result);
        LOG.debug("holdInEscrow: " + result);
    }

    // TODO provide parameter values for releaseFromEscrow
    @Ignore
    @Test
    public void testReleaseFromEscrow() throws Exception {
        // using String message body for single parameter "id"
        final com.braintreegateway.Result result = requestBody("direct://RELEASEFROMESCROW", null);

        assertNotNull("releaseFromEscrow result", result);
        LOG.debug("releaseFromEscrow: " + result);
    }

    // TODO provide parameter values for search
    @Ignore
    @Test
    public void testSearch() throws Exception {
        // using com.braintreegateway.TransactionSearchRequest message body for single parameter "query"
        final com.braintreegateway.ResourceCollection result = requestBody("direct://SEARCH", null);

        assertNotNull("search result", result);
        LOG.debug("search: " + result);
    }

    // TODO provide parameter values for submitForPartialSettlement
    @Ignore
    @Test
    public void testSubmitForPartialSettlement() throws Exception {
        final Map<String, Object> headers = new HashMap<String, Object>();
        // parameter type is String
        headers.put("CamelBraintree.id", null);
        // parameter type is java.math.BigDecimal
        headers.put("CamelBraintree.amount", null);

        final com.braintreegateway.Result result = requestBodyAndHeaders("direct://SUBMITFORPARTIALSETTLEMENT", null, headers);

        assertNotNull("submitForPartialSettlement result", result);
        LOG.debug("submitForPartialSettlement: " + result);
    }

    // TODO provide parameter values for voidTransaction
    @Ignore
    @Test
    public void testVoidTransaction() throws Exception {
        // using String message body for single parameter "id"
        final com.braintreegateway.Result result = requestBody("direct://VOIDTRANSACTION", null);

        assertNotNull("voidTransaction result", result);
        LOG.debug("voidTransaction: " + result);
    }

    // *************************************************************************
    // ROUTES
    // *************************************************************************

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                // test route for cancelRelease
                from("direct://CANCELRELEASE")
                    .to("braintree://" + PATH_PREFIX + "/cancelRelease?inBody=id");
                // test route for cloneTransaction
                from("direct://CLONETRANSACTION")
                    .to("braintree://" + PATH_PREFIX + "/cloneTransaction");
                // test route for credit
                from("direct://CREDIT")
                    .to("braintree://" + PATH_PREFIX + "/credit?inBody=request");
                // test route for find
                from("direct://FIND")
                    .to("braintree://" + PATH_PREFIX + "/find?inBody=id");
                // test route for holdInEscrow
                from("direct://HOLDINESCROW")
                    .to("braintree://" + PATH_PREFIX + "/holdInEscrow?inBody=id");
                // test route for refund
                from("direct://REFUND")
                    .to("braintree://" + PATH_PREFIX + "/refund");
                // test route for refund
                from("direct://REFUND_WITH_ID")
                        .to("braintree://" + PATH_PREFIX + "/refund?inBody=id");
                // test route for releaseFromEscrow
                from("direct://RELEASEFROMESCROW")
                    .to("braintree://" + PATH_PREFIX + "/releaseFromEscrow?inBody=id");
                // test route for sale
                from("direct://SALE")
                    .to("braintree://" + PATH_PREFIX + "/sale?inBody=request");
                // test route for search
                from("direct://SEARCH")
                        .to("braintree://" + PATH_PREFIX + "/search?inBody=query");
                // test route for submitForPartialSettlement
                from("direct://SUBMITFORPARTIALSETTLEMENT")
                    .to("braintree://" + PATH_PREFIX + "/submitForPartialSettlement");
                // test route for submitForSettlement
                from("direct://SUBMITFORSETTLEMENT_WITH_ID")
                    .to("braintree://" + PATH_PREFIX + "/submitForSettlement?inBody=id");
                // test route for submitForSettlement
                from("direct://SUBMITFORSETTLEMENT_WITH_ID_ADN_AMOUNT")
                    .to("braintree://" + PATH_PREFIX + "/submitForSettlement");
                // test route for submitForSettlement
                from("direct://SUBMITFORSETTLEMENT_WITH_REQUEST")
                    .to("braintree://" + PATH_PREFIX + "/submitForSettlement");
                // test route for voidTransaction
                from("direct://VOIDTRANSACTION")
                    .to("braintree://" + PATH_PREFIX + "/voidTransaction?inBody=id");
            }
        };
    }
}
