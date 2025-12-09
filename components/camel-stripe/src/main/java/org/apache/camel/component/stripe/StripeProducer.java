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
package org.apache.camel.component.stripe;

import java.util.Collections;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.Stripe;
import com.stripe.model.*;
import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;

public class StripeProducer extends DefaultProducer {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public StripeProducer(StripeEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public StripeEndpoint getEndpoint() {
        return (StripeEndpoint) super.getEndpoint();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        StripeConfiguration config = getEndpoint().getConfiguration();
        Stripe.apiKey = config.getApiKey();

        if (ObjectHelper.isNotEmpty(config.getApiBase())) {
            Stripe.overrideApiBase(config.getApiBase());
        }
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        StripeOperation operation = getEndpoint().getConfiguration().getOperation();
        String method = exchange.getIn().getHeader(StripeConstants.METHOD_HEADER, String.class);

        if (operation == null) {
            throw new IllegalArgumentException("Operation must be specified");
        }

        if (ObjectHelper.isEmpty(method)) {
            method = StripeConstants.METHOD_CREATE;
        }

        Object result = performOperation(exchange, operation, method);
        exchange.getMessage().setBody(result);
    }

    /**
     * Converts the exchange body to a Map for Stripe API calls. Supports Map objects and JSON strings.
     *
     * @param  exchange the Camel exchange
     * @return          a Map of parameters, or an empty map if body is null
     */
    private Map<String, Object> getParametersFromBody(Exchange exchange) {
        Object body = exchange.getIn().getBody();

        if (body == null) {
            return Collections.emptyMap();
        }

        if (body instanceof Map) {
            return (Map<String, Object>) body;
        }

        if (body instanceof String) {
            String json = (String) body;
            if (json.trim().isEmpty()) {
                return Collections.emptyMap();
            }
            try {
                return OBJECT_MAPPER.readValue(json, Map.class);
            } catch (Exception e) {
                throw new IllegalArgumentException("Failed to parse JSON body: " + e.getMessage(), e);
            }
        }

        throw new IllegalArgumentException(
                "Body must be a Map<String, Object> or a JSON String, but was: " + body.getClass().getName());
    }

    private Object performOperation(Exchange exchange, StripeOperation operation, String method) throws Exception {
        switch (operation) {
            case CHARGES:
                return handleCharges(exchange, method);
            case CUSTOMERS:
                return handleCustomers(exchange, method);
            case PAYMENT_INTENTS:
                return handlePaymentIntents(exchange, method);
            case PAYMENT_METHODS:
                return handlePaymentMethods(exchange, method);
            case REFUNDS:
                return handleRefunds(exchange, method);
            case SUBSCRIPTIONS:
                return handleSubscriptions(exchange, method);
            case INVOICES:
                return handleInvoices(exchange, method);
            case PRODUCTS:
                return handleProducts(exchange, method);
            case PRICES:
                return handlePrices(exchange, method);
            case BALANCE_TRANSACTIONS:
                return handleBalanceTransactions(exchange, method);
            default:
                throw new IllegalArgumentException("Unsupported operation: " + operation);
        }
    }

    private Object handleCharges(Exchange exchange, String method) throws Exception {
        switch (method) {
            case StripeConstants.METHOD_CREATE:
                return Charge.create(getParametersFromBody(exchange));
            case StripeConstants.METHOD_RETRIEVE:
                String id = exchange.getIn().getHeader(StripeConstants.OBJECT_ID, String.class);
                return Charge.retrieve(id);
            case StripeConstants.METHOD_UPDATE:
                String chargeId = exchange.getIn().getHeader(StripeConstants.OBJECT_ID, String.class);
                Charge charge = Charge.retrieve(chargeId);
                return charge.update(getParametersFromBody(exchange));
            case StripeConstants.METHOD_LIST:
                return Charge.list(getParametersFromBody(exchange));
            case StripeConstants.METHOD_CAPTURE:
                String captureId = exchange.getIn().getHeader(StripeConstants.OBJECT_ID, String.class);
                Charge captureCharge = Charge.retrieve(captureId);
                return captureCharge.capture(getParametersFromBody(exchange));
            default:
                throw new IllegalArgumentException("Unsupported method for charges: " + method);
        }
    }

    private Object handleCustomers(Exchange exchange, String method) throws Exception {
        switch (method) {
            case StripeConstants.METHOD_CREATE:
                return Customer.create(getParametersFromBody(exchange));
            case StripeConstants.METHOD_RETRIEVE:
                String id = exchange.getIn().getHeader(StripeConstants.OBJECT_ID, String.class);
                return Customer.retrieve(id);
            case StripeConstants.METHOD_UPDATE:
                String customerId = exchange.getIn().getHeader(StripeConstants.OBJECT_ID, String.class);
                Customer customer = Customer.retrieve(customerId);
                return customer.update(getParametersFromBody(exchange));
            case StripeConstants.METHOD_DELETE:
                String deleteId = exchange.getIn().getHeader(StripeConstants.OBJECT_ID, String.class);
                Customer deleteCustomer = Customer.retrieve(deleteId);
                return deleteCustomer.delete();
            case StripeConstants.METHOD_LIST:
                return Customer.list(getParametersFromBody(exchange));
            default:
                throw new IllegalArgumentException("Unsupported method for customers: " + method);
        }
    }

    private Object handlePaymentIntents(Exchange exchange, String method) throws Exception {
        switch (method) {
            case StripeConstants.METHOD_CREATE:
                return PaymentIntent.create(getParametersFromBody(exchange));
            case StripeConstants.METHOD_RETRIEVE:
                String id = exchange.getIn().getHeader(StripeConstants.OBJECT_ID, String.class);
                return PaymentIntent.retrieve(id);
            case StripeConstants.METHOD_UPDATE:
                String piId = exchange.getIn().getHeader(StripeConstants.OBJECT_ID, String.class);
                PaymentIntent pi = PaymentIntent.retrieve(piId);
                return pi.update(getParametersFromBody(exchange));
            case StripeConstants.METHOD_CANCEL:
                String cancelId = exchange.getIn().getHeader(StripeConstants.OBJECT_ID, String.class);
                PaymentIntent cancelPi = PaymentIntent.retrieve(cancelId);
                return cancelPi.cancel();
            case StripeConstants.METHOD_LIST:
                return PaymentIntent.list(getParametersFromBody(exchange));
            default:
                throw new IllegalArgumentException("Unsupported method for payment intents: " + method);
        }
    }

    private Object handlePaymentMethods(Exchange exchange, String method) throws Exception {
        switch (method) {
            case StripeConstants.METHOD_CREATE:
                return PaymentMethod.create(getParametersFromBody(exchange));
            case StripeConstants.METHOD_RETRIEVE:
                String id = exchange.getIn().getHeader(StripeConstants.OBJECT_ID, String.class);
                return PaymentMethod.retrieve(id);
            case StripeConstants.METHOD_UPDATE:
                String pmId = exchange.getIn().getHeader(StripeConstants.OBJECT_ID, String.class);
                PaymentMethod pm = PaymentMethod.retrieve(pmId);
                return pm.update(getParametersFromBody(exchange));
            case StripeConstants.METHOD_LIST:
                return PaymentMethod.list(getParametersFromBody(exchange));
            default:
                throw new IllegalArgumentException("Unsupported method for payment methods: " + method);
        }
    }

    private Object handleRefunds(Exchange exchange, String method) throws Exception {
        switch (method) {
            case StripeConstants.METHOD_CREATE:
                return Refund.create(getParametersFromBody(exchange));
            case StripeConstants.METHOD_RETRIEVE:
                String id = exchange.getIn().getHeader(StripeConstants.OBJECT_ID, String.class);
                return Refund.retrieve(id);
            case StripeConstants.METHOD_UPDATE:
                String refundId = exchange.getIn().getHeader(StripeConstants.OBJECT_ID, String.class);
                Refund refund = Refund.retrieve(refundId);
                return refund.update(getParametersFromBody(exchange));
            case StripeConstants.METHOD_LIST:
                return Refund.list(getParametersFromBody(exchange));
            default:
                throw new IllegalArgumentException("Unsupported method for refunds: " + method);
        }
    }

    private Object handleSubscriptions(Exchange exchange, String method) throws Exception {
        switch (method) {
            case StripeConstants.METHOD_CREATE:
                return Subscription.create(getParametersFromBody(exchange));
            case StripeConstants.METHOD_RETRIEVE:
                String id = exchange.getIn().getHeader(StripeConstants.OBJECT_ID, String.class);
                return Subscription.retrieve(id);
            case StripeConstants.METHOD_UPDATE:
                String subId = exchange.getIn().getHeader(StripeConstants.OBJECT_ID, String.class);
                Subscription subscription = Subscription.retrieve(subId);
                return subscription.update(getParametersFromBody(exchange));
            case StripeConstants.METHOD_CANCEL:
                String cancelId = exchange.getIn().getHeader(StripeConstants.OBJECT_ID, String.class);
                Subscription cancelSub = Subscription.retrieve(cancelId);
                return cancelSub.cancel();
            case StripeConstants.METHOD_LIST:
                return Subscription.list(getParametersFromBody(exchange));
            default:
                throw new IllegalArgumentException("Unsupported method for subscriptions: " + method);
        }
    }

    private Object handleInvoices(Exchange exchange, String method) throws Exception {
        switch (method) {
            case StripeConstants.METHOD_CREATE:
                return Invoice.create(getParametersFromBody(exchange));
            case StripeConstants.METHOD_RETRIEVE:
                String id = exchange.getIn().getHeader(StripeConstants.OBJECT_ID, String.class);
                return Invoice.retrieve(id);
            case StripeConstants.METHOD_UPDATE:
                String invoiceId = exchange.getIn().getHeader(StripeConstants.OBJECT_ID, String.class);
                Invoice invoice = Invoice.retrieve(invoiceId);
                return invoice.update(getParametersFromBody(exchange));
            case StripeConstants.METHOD_LIST:
                return Invoice.list(getParametersFromBody(exchange));
            default:
                throw new IllegalArgumentException("Unsupported method for invoices: " + method);
        }
    }

    private Object handleProducts(Exchange exchange, String method) throws Exception {
        switch (method) {
            case StripeConstants.METHOD_CREATE:
                return Product.create(getParametersFromBody(exchange));
            case StripeConstants.METHOD_RETRIEVE:
                String id = exchange.getIn().getHeader(StripeConstants.OBJECT_ID, String.class);
                return Product.retrieve(id);
            case StripeConstants.METHOD_UPDATE:
                String productId = exchange.getIn().getHeader(StripeConstants.OBJECT_ID, String.class);
                Product product = Product.retrieve(productId);
                return product.update(getParametersFromBody(exchange));
            case StripeConstants.METHOD_DELETE:
                String deleteId = exchange.getIn().getHeader(StripeConstants.OBJECT_ID, String.class);
                Product deleteProduct = Product.retrieve(deleteId);
                return deleteProduct.delete();
            case StripeConstants.METHOD_LIST:
                return Product.list(getParametersFromBody(exchange));
            default:
                throw new IllegalArgumentException("Unsupported method for products: " + method);
        }
    }

    private Object handlePrices(Exchange exchange, String method) throws Exception {
        switch (method) {
            case StripeConstants.METHOD_CREATE:
                return Price.create(getParametersFromBody(exchange));
            case StripeConstants.METHOD_RETRIEVE:
                String id = exchange.getIn().getHeader(StripeConstants.OBJECT_ID, String.class);
                return Price.retrieve(id);
            case StripeConstants.METHOD_UPDATE:
                String priceId = exchange.getIn().getHeader(StripeConstants.OBJECT_ID, String.class);
                Price price = Price.retrieve(priceId);
                return price.update(getParametersFromBody(exchange));
            case StripeConstants.METHOD_LIST:
                return Price.list(getParametersFromBody(exchange));
            default:
                throw new IllegalArgumentException("Unsupported method for prices: " + method);
        }
    }

    private Object handleBalanceTransactions(Exchange exchange, String method) throws Exception {
        switch (method) {
            case StripeConstants.METHOD_RETRIEVE:
                String id = exchange.getIn().getHeader(StripeConstants.OBJECT_ID, String.class);
                return BalanceTransaction.retrieve(id);
            case StripeConstants.METHOD_LIST:
                return BalanceTransaction.list(getParametersFromBody(exchange));
            default:
                throw new IllegalArgumentException("Unsupported method for balance transactions: " + method);
        }
    }
}
