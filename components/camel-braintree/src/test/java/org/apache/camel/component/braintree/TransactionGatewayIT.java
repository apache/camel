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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "braintreeAuthenticationType", matches = ".*")
public class TransactionGatewayIT extends AbstractBraintreeTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(TransactionGatewayIT.class);
    private static final String PATH_PREFIX
            = BraintreeApiCollection.getCollection().getApiName(TransactionGatewayApiMethod.class).getName();

    private BraintreeGateway gateway;
    private final List<String> transactionIds;

    // *************************************************************************
    //
    // *************************************************************************

    public TransactionGatewayIT() {
        this.gateway = null;
        this.transactionIds = new LinkedList<>();
    }

    @Override
    protected void doPostSetup() {
        this.gateway = getGateway();
    }

    @Override
    @AfterEach
    public void tearDown() {
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
    public void testSale() {
        assertNotNull(this.gateway, "BraintreeGateway can't be null");

        final Result<Transaction> result = requestBody(
                "direct://SALE",
                new TransactionRequest()
                        .amount(new BigDecimal("100.00"))
                        .paymentMethodNonce("fake-valid-nonce")
                        .options()
                        .submitForSettlement(true)
                        .done(),
                Result.class);

        LOG.info("Result message: {}", result.getMessage());
        assertNotNull(result, "sale result");
        assertTrue(result.isSuccess());

        LOG.info("Transaction done - id={}", result.getTarget().getId());
        this.transactionIds.add(result.getTarget().getId());
    }

    @Test
    public void testCloneTransaction() {
        assertNotNull(this.gateway, "BraintreeGateway can't be null");

        final Result<Transaction> createResult = requestBody(
                "direct://SALE",
                new TransactionRequest()
                        .amount(new BigDecimal("100.01"))
                        .paymentMethodNonce("fake-valid-nonce")
                        .options()
                        .submitForSettlement(false)
                        .done(),
                Result.class);

        LOG.info("Result message: {}", createResult.getMessage());
        assertNotNull(createResult, "sale result");
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

        assertNotNull(cloneResult, "clone result");
        assertTrue(cloneResult.isSuccess());

        LOG.info("Clone Transaction done - clonedId={}, id={}",
                createResult.getTarget().getId(), cloneResult.getTarget().getId());

        this.transactionIds.add(cloneResult.getTarget().getId());
    }

    @Test
    public void testFind() {
        assertNotNull(this.gateway, "BraintreeGateway can't be null");

        final Result<Transaction> createResult = requestBody(
                "direct://SALE",
                new TransactionRequest()
                        .amount(new BigDecimal("100.02"))
                        .paymentMethodNonce("fake-valid-nonce")
                        .options()
                        .submitForSettlement(false)
                        .done(),
                Result.class);

        LOG.info("Result message: {}", createResult.getMessage());
        assertNotNull(createResult, "sale result");
        assertTrue(createResult.isSuccess());

        LOG.info("Transaction done - id={}", createResult.getTarget().getId());
        this.transactionIds.add(createResult.getTarget().getId());

        // using String message body for single parameter "id"
        final Transaction result = requestBody("direct://FIND", createResult.getTarget().getId());

        assertNotNull(result, "find result");
        LOG.info("Transaction found - id={}", result.getId());
    }

    @Test
    public void testSubmitForSettlementWithId() {
        assertNotNull(this.gateway, "BraintreeGateway can't be null");

        final Result<Transaction> createResult = requestBody(
                "direct://SALE",
                new TransactionRequest()
                        .amount(new BigDecimal("100.03"))
                        .paymentMethodNonce("fake-valid-nonce")
                        .options()
                        .submitForSettlement(false)
                        .done(),
                Result.class);

        LOG.info("Result message: {}", createResult.getMessage());
        assertNotNull(createResult, "sale result");
        assertTrue(createResult.isSuccess());

        LOG.info("Transaction done - id={}", createResult.getTarget().getId());
        this.transactionIds.add(createResult.getTarget().getId());

        final Result<Transaction> result = requestBody(
                "direct://SUBMITFORSETTLEMENT_WITH_ID",
                createResult.getTarget().getId(),
                Result.class);

        assertNotNull(result, "Submit For Settlement result");
        LOG.debug("Transaction submitted for settlement - id={}", result.getTarget().getId());
    }

    @Test
    public void testSubmitForSettlementWithIdAndAmount() {
        assertNotNull(this.gateway, "BraintreeGateway can't be null");

        final Result<Transaction> createResult = requestBody(
                "direct://SALE",
                new TransactionRequest()
                        .amount(new BigDecimal("100.04"))
                        .paymentMethodNonce("fake-valid-nonce")
                        .options()
                        .submitForSettlement(false)
                        .done(),
                Result.class);

        LOG.info("Result message: {}", createResult.getMessage());
        assertNotNull(createResult, "sale result");
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

        assertNotNull(result, "Submit For Settlement result");
        LOG.debug("Transaction submitted for settlement - id={}", result.getTarget().getId());
    }

    @Test
    public void testSubmitForSettlementWithRequest() {
        assertNotNull(this.gateway, "BraintreeGateway can't be null");

        final Result<Transaction> createResult = requestBody(
                "direct://SALE",
                new TransactionRequest()
                        .amount(new BigDecimal("100.05"))
                        .paymentMethodNonce("fake-valid-nonce")
                        .options()
                        .submitForSettlement(false)
                        .done(),
                Result.class);

        LOG.info("Result message: {}", createResult.getMessage());
        assertNotNull(createResult, "sale result");
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

        assertNotNull(result, "Submit For Settlement result");
        LOG.debug("Transaction submitted for settlement - id={}", result.getTarget().getId());
    }

    @Test
    public void testRefund() {
        assertNotNull(this.gateway, "BraintreeGateway can't be null");

        final Result<Transaction> createResult = requestBody(
                "direct://SALE",
                new TransactionRequest()
                        .amount(new BigDecimal("100.06"))
                        .paymentMethodNonce("fake-valid-nonce")
                        .options()
                        .submitForSettlement(true)
                        .done(),
                Result.class);

        LOG.info("Result message: {}", createResult.getMessage());
        assertNotNull(createResult, "sale result");
        assertTrue(createResult.isSuccess());

        String createId = createResult.getTarget().getId();

        final Result<Transaction> settleResult = this.gateway.testing().settle(createId);
        assertNotNull(settleResult, "settle result");
        assertTrue(settleResult.isSuccess());

        final Result<Transaction> result = requestBody(
                "direct://REFUND_WITH_ID",
                createId,
                Result.class);

        assertNotNull(result, "Request Refund result");
        assertTrue(result.isSuccess());
        LOG.info(String.format("Refund id(%s) created for transaction id(%s)", result.getTarget().getId(), createId));
    }

    @Test
    public void testRefundWithAmount() {
        assertNotNull(this.gateway, "BraintreeGateway can't be null");

        final Result<Transaction> createResult = requestBody(
                "direct://SALE",
                new TransactionRequest()
                        .amount(new BigDecimal("100.07"))
                        .paymentMethodNonce("fake-valid-nonce")
                        .options()
                        .submitForSettlement(true)
                        .done(),
                Result.class);

        LOG.info("Result message: {}", createResult.getMessage());
        assertNotNull(createResult, "sale result");
        assertTrue(createResult.isSuccess());

        String createId = createResult.getTarget().getId();

        final Result<Transaction> settleResult = this.gateway.testing().settle(createId);
        assertNotNull(settleResult, "settle result");
        assertTrue(settleResult.isSuccess());

        final Result<Transaction> result = requestBodyAndHeaders(
                "direct://REFUND",
                null,
                new BraintreeHeaderBuilder()
                        .add("id", createId)
                        .add("amount", new BigDecimal("99.00"))
                        .build(),
                Result.class);

        assertNotNull(result, "Request Refund result");
        assertTrue(result.isSuccess());
        LOG.info(String.format("Refund id(%s) created for transaction id(%s)", result.getTarget().getId(), createId));
    }

    @Test
    public void testRefundWithRequest() {
        assertNotNull(this.gateway, "BraintreeGateway can't be null");

        final Result<Transaction> createResult = requestBody(
                "direct://SALE",
                new TransactionRequest()
                        .amount(new BigDecimal("100.08"))
                        .paymentMethodNonce("fake-valid-nonce")
                        .options()
                        .submitForSettlement(true)
                        .done(),
                Result.class);

        LOG.info("Result message: {}", createResult.getMessage());
        assertNotNull(createResult, "sale result");
        assertTrue(createResult.isSuccess());

        String createId = createResult.getTarget().getId();

        final Result<Transaction> settleResult = this.gateway.testing().settle(createId);
        assertNotNull(settleResult, "settle result");
        assertTrue(settleResult.isSuccess());

        final Result<Transaction> result = requestBodyAndHeaders(
                "direct://REFUND",
                null,
                new BraintreeHeaderBuilder()
                        .add("id", createId)
                        .add("refundRequest", new TransactionRefundRequest()
                                .amount(new BigDecimal("100.00")))
                        .build(),
                Result.class);

        assertNotNull(result, "Request Refund result");
        assertTrue(result.isSuccess());
        LOG.info(String.format("Refund id(%s) created for transaction id(%s)", result.getTarget().getId(), createId));
    }

    // *************************************************************************
    // Auto generated tests
    // *************************************************************************

    // TODO provide parameter values for cancelRelease
    @Disabled
    @Test
    public void testCancelRelease() {
        // using String message body for single parameter "id"
        final com.braintreegateway.Result result = requestBody("direct://CANCELRELEASE", null);

        assertNotNull(result, "cancelRelease result");
        LOG.debug("cancelRelease: {}", result);
    }

    // TODO provide parameter values for credit
    @Disabled
    @Test
    public void testCredit() {
        // using com.braintreegateway.TransactionRequest message body for single parameter "request"
        final com.braintreegateway.Result result = requestBody("direct://CREDIT", null);

        assertNotNull(result, "credit result");
        LOG.debug("credit: {}", result);
    }

    // TODO provide parameter values for holdInEscrow
    @Disabled
    @Test
    public void testHoldInEscrow() {
        // using String message body for single parameter "id"
        final com.braintreegateway.Result result = requestBody("direct://HOLDINESCROW", null);

        assertNotNull(result, "holdInEscrow result");
        LOG.debug("holdInEscrow: {}", result);
    }

    // TODO provide parameter values for releaseFromEscrow
    @Disabled
    @Test
    public void testReleaseFromEscrow() {
        // using String message body for single parameter "id"
        final com.braintreegateway.Result result = requestBody("direct://RELEASEFROMESCROW", null);

        assertNotNull(result, "releaseFromEscrow result");
        LOG.debug("releaseFromEscrow: {}", result);
    }

    // TODO provide parameter values for search
    @Disabled
    @Test
    public void testSearch() {
        // using com.braintreegateway.TransactionSearchRequest message body for single parameter "query"
        final com.braintreegateway.ResourceCollection result = requestBody("direct://SEARCH", null);

        assertNotNull(result, "search result");
        LOG.debug("search: {}", result);
    }

    // TODO provide parameter values for submitForPartialSettlement
    @Disabled
    @Test
    public void testSubmitForPartialSettlement() {
        final Map<String, Object> headers = new HashMap<>();
        // parameter type is String
        headers.put("CamelBraintree.id", null);
        // parameter type is java.math.BigDecimal
        headers.put("CamelBraintree.amount", null);

        final com.braintreegateway.Result result = requestBodyAndHeaders("direct://SUBMITFORPARTIALSETTLEMENT", null, headers);

        assertNotNull(result, "submitForPartialSettlement result");
        LOG.debug("submitForPartialSettlement: {}", result);
    }

    // TODO provide parameter values for voidTransaction
    @Disabled
    @Test
    public void testVoidTransaction() {
        // using String message body for single parameter "id"
        final com.braintreegateway.Result result = requestBody("direct://VOIDTRANSACTION", null);

        assertNotNull(result, "voidTransaction result");
        LOG.debug("voidTransaction: {}", result);
    }

    // *************************************************************************
    // ROUTES
    // *************************************************************************

    @Override
    protected RouteBuilder createRouteBuilder() {
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
