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

import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import com.braintreegateway.BraintreeGateway;
import com.braintreegateway.Customer;
import com.braintreegateway.CustomerRequest;
import com.braintreegateway.PaymentMethod;
import com.braintreegateway.PaymentMethodRequest;
import com.braintreegateway.Result;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.braintree.internal.PaymentMethodGatewayApiMethod;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "braintreeAuthenticationType", matches = ".*")
public class PaymentMethodGatewayIT extends AbstractBraintreeTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(PaymentMethodGatewayIT.class);
    private static final String PATH_PREFIX = getApiNameAsString(PaymentMethodGatewayApiMethod.class);

    private BraintreeGateway gateway;
    private Customer customer;
    private final List<String> paymentMethodsTokens;

    // *************************************************************************
    //
    // *************************************************************************

    public PaymentMethodGatewayIT() {
        this.customer = null;
        this.gateway = null;
        this.paymentMethodsTokens = new LinkedList<>();
    }

    @Override
    protected void doPostSetup() {
        this.gateway = getGateway();
        this.customer = gateway.customer().create(
                new CustomerRequest()
                        .firstName("user")
                        .lastName(UUID.randomUUID().toString()))
                .getTarget();

        if (customer != null) {
            LOG.info("Customer created - id={}", this.customer.getId());
        }
    }

    @Override
    @AfterEach
    public void tearDown() {
        if (this.gateway != null) {
            for (String token : this.paymentMethodsTokens) {
                if (this.gateway.paymentMethod().delete(token).isSuccess()) {
                    LOG.info("PaymentMethod deleted - token={}", token);
                } else {
                    LOG.warn("Unable to delete PaymentMethod - token={}", token);
                }
            }

            this.paymentMethodsTokens.clear();

            if (this.gateway.customer().delete(this.customer.getId()).isSuccess()) {
                LOG.info("Customer deleted - id={}", this.customer.getId());
            } else {
                LOG.warn("Unable to delete customer - id={}", this.customer.getId());
            }
        }
    }

    private PaymentMethod createPaymentMethod() {
        Result<? extends PaymentMethod> result = this.gateway.paymentMethod().create(
                new PaymentMethodRequest()
                        .customerId(this.customer.getId())
                        .paymentMethodNonce("fake-valid-payroll-nonce"));

        assertNotNull(result, "create result");
        assertTrue(result.isSuccess());

        LOG.info("PaymentMethod created - token={}", result.getTarget().getToken());

        return result.getTarget();
    }

    // *************************************************************************
    //
    // *************************************************************************

    @Test
    public void testCreate() {
        assertNotNull(this.gateway, "BraintreeGateway can't be null");
        assertNotNull(this.customer, "Customer can't be null");

        final Result<PaymentMethod> result = requestBody("direct://CREATE",
                new PaymentMethodRequest()
                        .customerId(this.customer.getId())
                        .paymentMethodNonce("fake-valid-payroll-nonce"),
                Result.class);

        assertNotNull(result, "create result");
        assertTrue(result.isSuccess());

        LOG.info("PaymentMethod created - token={}", result.getTarget().getToken());
        this.paymentMethodsTokens.add(result.getTarget().getToken());
    }

    @Test
    public void testDelete() {
        assertNotNull(this.gateway, "BraintreeGateway can't be null");
        assertNotNull(this.customer, "Customer can't be null");

        final PaymentMethod paymentMethod = createPaymentMethod();
        final Result<PaymentMethod> deleteResult = requestBody(
                "direct://DELETE", paymentMethod.getToken(), Result.class);

        assertNotNull(deleteResult, "create result");
        assertTrue(deleteResult.isSuccess());

        LOG.info("PaymentMethod deleted - token={}", paymentMethod.getToken());
    }

    @Test
    public void testFind() {
        assertNotNull(this.gateway, "BraintreeGateway can't be null");
        assertNotNull(this.customer, "Customer can't be null");

        final PaymentMethod paymentMethod = createPaymentMethod();
        this.paymentMethodsTokens.add(paymentMethod.getToken());

        final PaymentMethod method = requestBody(
                "direct://FIND", paymentMethod.getToken(), PaymentMethod.class);

        assertNotNull(method, "find result");
        LOG.info("PaymentMethod found - token={}", method.getToken());
    }

    @Test
    public void testUpdate() {
        assertNotNull(this.gateway, "BraintreeGateway can't be null");
        assertNotNull(this.customer, "Customer can't be null");

        final PaymentMethod paymentMethod = createPaymentMethod();
        this.paymentMethodsTokens.add(paymentMethod.getToken());

        final Result<PaymentMethod> result = requestBodyAndHeaders(
                "direct://UPDATE", null,
                new BraintreeHeaderBuilder()
                        .add("token", paymentMethod.getToken())
                        .add("request", new PaymentMethodRequest()
                                .billingAddress()
                                .company("Apache")
                                .streetAddress("100 Maple Lane")
                                .done())
                        .build(),
                Result.class);

        assertNotNull(result, "update result");
        assertTrue(result.isSuccess());

        LOG.info("PaymentMethod updated - token={}", result.getTarget().getToken());
    }

    // *************************************************************************
    // ROUTES
    // *************************************************************************

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // test route for create
                from("direct://CREATE")
                        .to("braintree://" + PATH_PREFIX + "/create?inBody=request");
                // test route for delete
                from("direct://DELETE")
                        .to("braintree://" + PATH_PREFIX + "/delete?inBody=token");
                // test route for find
                from("direct://FIND")
                        .to("braintree://" + PATH_PREFIX + "/find?inBody=token");
                // test route for update
                from("direct://UPDATE")
                        .to("braintree://" + PATH_PREFIX + "/update");
            }
        };
    }
}
