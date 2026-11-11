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
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import com.stripe.model.BalanceTransaction;
import com.stripe.model.Charge;
import com.stripe.model.Customer;
import com.stripe.model.Invoice;
import com.stripe.model.PaymentIntent;
import com.stripe.model.PaymentMethod;
import com.stripe.model.Price;
import com.stripe.model.Product;
import com.stripe.model.Refund;
import com.stripe.model.Subscription;
import com.stripe.net.ApiResource;
import com.stripe.net.RequestOptions;
import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

public class StripeProducer extends DefaultProducer {

    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builderWithJackson2Defaults().build();

    private volatile RequestOptions requestOptions;

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
        if (ObjectHelper.isEmpty(config.getApiKey())) {
            throw new IllegalArgumentException(
                    "Stripe apiKey must be configured for endpoint: " + getEndpoint().getEndpointUri());
        }

        RequestOptions.RequestOptionsBuilder builder = RequestOptions.builder();
        builder.setApiKey(config.getApiKey());

        if (ObjectHelper.isNotEmpty(config.getApiBase())) {
            builder.setBaseUrl(config.getApiBase());
        }

        this.requestOptions = builder.build();
    }

    RequestOptions getRequestOptions() {
        return requestOptions;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        StripeOperation operation = getEndpoint().getConfiguration().getOperation();
        Object operationHeader = exchange.getIn().getHeader(StripeConstants.OPERATION_HEADER);
        if (operationHeader instanceof StripeOperation op) {
            operation = op;
        } else if (ObjectHelper.isNotEmpty(operationHeader)) {
            operation = StripeOperation.fromValue(exchange.getIn().getHeader(StripeConstants.OPERATION_HEADER, String.class));
        }
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
     * Converts the exchange body to a Map for Stripe API calls. Supports Map objects and JSON as a String, or as any
     * other body that converts to a String, such as bytes or a stream.
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

        String json = body instanceof String text ? text : exchange.getIn().getBody(String.class);
        if (json == null) {
            throw new IllegalArgumentException(
                    "Body must be a Map<String, Object> or a JSON String, but was: " + body.getClass().getName());
        }
        if (json.trim().isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            return OBJECT_MAPPER.readValue(json, Map.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse JSON body: " + e.getMessage(), e);
        }
    }

    /**
     * Creates a Stripe object that has only the given id, so update, delete, capture and cancel call the API on the id
     * directly, without retrieving the object first.
     */
    private static <T extends ApiResource> T forId(Supplier<T> factory, BiConsumer<T, String> idSetter, String id) {
        T resource = factory.get();
        idSetter.accept(resource, id);
        resource.setResponseGetter(ApiResource.getGlobalResponseGetter());
        return resource;
    }

    private static String getObjectId(Exchange exchange) {
        return exchange.getIn().getHeader(StripeConstants.OBJECT_ID, String.class);
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
                return Charge.create(getParametersFromBody(exchange), requestOptions);
            case StripeConstants.METHOD_RETRIEVE:
                String id = exchange.getIn().getHeader(StripeConstants.OBJECT_ID, String.class);
                return Charge.retrieve(id, requestOptions);
            case StripeConstants.METHOD_UPDATE:
                return forId(Charge::new, Charge::setId, getObjectId(exchange)).update(getParametersFromBody(exchange),
                        requestOptions);
            case StripeConstants.METHOD_LIST:
                return Charge.list(getParametersFromBody(exchange), requestOptions);
            case StripeConstants.METHOD_CAPTURE:
                return forId(Charge::new, Charge::setId, getObjectId(exchange)).capture(getParametersFromBody(exchange),
                        requestOptions);
            default:
                throw new IllegalArgumentException("Unsupported method for charges: " + method);
        }
    }

    private Object handleCustomers(Exchange exchange, String method) throws Exception {
        switch (method) {
            case StripeConstants.METHOD_CREATE:
                return Customer.create(getParametersFromBody(exchange), requestOptions);
            case StripeConstants.METHOD_RETRIEVE:
                String id = exchange.getIn().getHeader(StripeConstants.OBJECT_ID, String.class);
                return Customer.retrieve(id, requestOptions);
            case StripeConstants.METHOD_UPDATE:
                return forId(Customer::new, Customer::setId, getObjectId(exchange)).update(getParametersFromBody(exchange),
                        requestOptions);
            case StripeConstants.METHOD_DELETE:
                return forId(Customer::new, Customer::setId, getObjectId(exchange)).delete(requestOptions);
            case StripeConstants.METHOD_LIST:
                return Customer.list(getParametersFromBody(exchange), requestOptions);
            default:
                throw new IllegalArgumentException("Unsupported method for customers: " + method);
        }
    }

    private Object handlePaymentIntents(Exchange exchange, String method) throws Exception {
        switch (method) {
            case StripeConstants.METHOD_CREATE:
                return PaymentIntent.create(getParametersFromBody(exchange), requestOptions);
            case StripeConstants.METHOD_RETRIEVE:
                String id = exchange.getIn().getHeader(StripeConstants.OBJECT_ID, String.class);
                return PaymentIntent.retrieve(id, requestOptions);
            case StripeConstants.METHOD_UPDATE:
                return forId(PaymentIntent::new, PaymentIntent::setId, getObjectId(exchange))
                        .update(getParametersFromBody(exchange), requestOptions);
            case StripeConstants.METHOD_CANCEL:
                return forId(PaymentIntent::new, PaymentIntent::setId, getObjectId(exchange)).cancel(requestOptions);
            case StripeConstants.METHOD_LIST:
                return PaymentIntent.list(getParametersFromBody(exchange), requestOptions);
            default:
                throw new IllegalArgumentException("Unsupported method for payment intents: " + method);
        }
    }

    private Object handlePaymentMethods(Exchange exchange, String method) throws Exception {
        switch (method) {
            case StripeConstants.METHOD_CREATE:
                return PaymentMethod.create(getParametersFromBody(exchange), requestOptions);
            case StripeConstants.METHOD_RETRIEVE:
                String id = exchange.getIn().getHeader(StripeConstants.OBJECT_ID, String.class);
                return PaymentMethod.retrieve(id, requestOptions);
            case StripeConstants.METHOD_UPDATE:
                return forId(PaymentMethod::new, PaymentMethod::setId, getObjectId(exchange))
                        .update(getParametersFromBody(exchange), requestOptions);
            case StripeConstants.METHOD_LIST:
                return PaymentMethod.list(getParametersFromBody(exchange), requestOptions);
            default:
                throw new IllegalArgumentException("Unsupported method for payment methods: " + method);
        }
    }

    private Object handleRefunds(Exchange exchange, String method) throws Exception {
        switch (method) {
            case StripeConstants.METHOD_CREATE:
                return Refund.create(getParametersFromBody(exchange), requestOptions);
            case StripeConstants.METHOD_RETRIEVE:
                String id = exchange.getIn().getHeader(StripeConstants.OBJECT_ID, String.class);
                return Refund.retrieve(id, requestOptions);
            case StripeConstants.METHOD_UPDATE:
                return forId(Refund::new, Refund::setId, getObjectId(exchange)).update(getParametersFromBody(exchange),
                        requestOptions);
            case StripeConstants.METHOD_LIST:
                return Refund.list(getParametersFromBody(exchange), requestOptions);
            default:
                throw new IllegalArgumentException("Unsupported method for refunds: " + method);
        }
    }

    private Object handleSubscriptions(Exchange exchange, String method) throws Exception {
        switch (method) {
            case StripeConstants.METHOD_CREATE:
                return Subscription.create(getParametersFromBody(exchange), requestOptions);
            case StripeConstants.METHOD_RETRIEVE:
                String id = exchange.getIn().getHeader(StripeConstants.OBJECT_ID, String.class);
                return Subscription.retrieve(id, requestOptions);
            case StripeConstants.METHOD_UPDATE:
                return forId(Subscription::new, Subscription::setId, getObjectId(exchange))
                        .update(getParametersFromBody(exchange), requestOptions);
            case StripeConstants.METHOD_CANCEL:
                return forId(Subscription::new, Subscription::setId, getObjectId(exchange)).cancel((Map<String, Object>) null,
                        requestOptions);
            case StripeConstants.METHOD_LIST:
                return Subscription.list(getParametersFromBody(exchange), requestOptions);
            default:
                throw new IllegalArgumentException("Unsupported method for subscriptions: " + method);
        }
    }

    private Object handleInvoices(Exchange exchange, String method) throws Exception {
        switch (method) {
            case StripeConstants.METHOD_CREATE:
                return Invoice.create(getParametersFromBody(exchange), requestOptions);
            case StripeConstants.METHOD_RETRIEVE:
                String id = exchange.getIn().getHeader(StripeConstants.OBJECT_ID, String.class);
                return Invoice.retrieve(id, requestOptions);
            case StripeConstants.METHOD_UPDATE:
                return forId(Invoice::new, Invoice::setId, getObjectId(exchange)).update(getParametersFromBody(exchange),
                        requestOptions);
            case StripeConstants.METHOD_LIST:
                return Invoice.list(getParametersFromBody(exchange), requestOptions);
            default:
                throw new IllegalArgumentException("Unsupported method for invoices: " + method);
        }
    }

    private Object handleProducts(Exchange exchange, String method) throws Exception {
        switch (method) {
            case StripeConstants.METHOD_CREATE:
                return Product.create(getParametersFromBody(exchange), requestOptions);
            case StripeConstants.METHOD_RETRIEVE:
                String id = exchange.getIn().getHeader(StripeConstants.OBJECT_ID, String.class);
                return Product.retrieve(id, requestOptions);
            case StripeConstants.METHOD_UPDATE:
                return forId(Product::new, Product::setId, getObjectId(exchange)).update(getParametersFromBody(exchange),
                        requestOptions);
            case StripeConstants.METHOD_DELETE:
                return forId(Product::new, Product::setId, getObjectId(exchange)).delete(requestOptions);
            case StripeConstants.METHOD_LIST:
                return Product.list(getParametersFromBody(exchange), requestOptions);
            default:
                throw new IllegalArgumentException("Unsupported method for products: " + method);
        }
    }

    private Object handlePrices(Exchange exchange, String method) throws Exception {
        switch (method) {
            case StripeConstants.METHOD_CREATE:
                return Price.create(getParametersFromBody(exchange), requestOptions);
            case StripeConstants.METHOD_RETRIEVE:
                String id = exchange.getIn().getHeader(StripeConstants.OBJECT_ID, String.class);
                return Price.retrieve(id, requestOptions);
            case StripeConstants.METHOD_UPDATE:
                return forId(Price::new, Price::setId, getObjectId(exchange)).update(getParametersFromBody(exchange),
                        requestOptions);
            case StripeConstants.METHOD_LIST:
                return Price.list(getParametersFromBody(exchange), requestOptions);
            default:
                throw new IllegalArgumentException("Unsupported method for prices: " + method);
        }
    }

    private Object handleBalanceTransactions(Exchange exchange, String method) throws Exception {
        switch (method) {
            case StripeConstants.METHOD_RETRIEVE:
                String id = exchange.getIn().getHeader(StripeConstants.OBJECT_ID, String.class);
                return BalanceTransaction.retrieve(id, requestOptions);
            case StripeConstants.METHOD_LIST:
                return BalanceTransaction.list(getParametersFromBody(exchange), requestOptions);
            default:
                throw new IllegalArgumentException("Unsupported method for balance transactions: " + method);
        }
    }
}
