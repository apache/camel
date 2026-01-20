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
package org.apache.camel.component.stripe.integration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.stripe.model.Customer;
import com.stripe.model.Price;
import com.stripe.model.Product;
import com.stripe.model.Subscription;
import com.stripe.model.SubscriptionCollection;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.stripe.StripeConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for Stripe Subscription operations with JSON payloads.
 *
 * To run this test: mvn verify -Dtest=StripeSubscriptionIntegrationTest -DSTRIPE_API_KEY=sk_test_...
 */
@EnabledIfSystemProperty(named = "STRIPE_API_KEY", matches = ".*")
public class StripeSubscriptionIntegrationTest extends StripeIntegrationTestSupport {

    @Test
    public void testCreateSubscriptionWithJson() throws Exception {
        // Create customer with test card source
        Map<String, Object> customerParams = new HashMap<>();
        customerParams.put("email", "subscription@example.com");
        customerParams.put("source", "tok_visa");
        Customer customer = template.requestBody("direct:createCustomer", customerParams, Customer.class);

        // Create product
        Map<String, Object> productParams = new HashMap<>();
        productParams.put("name", "Subscription Product");
        Product product = template.requestBody("direct:createProduct", productParams, Product.class);

        // Create price
        Map<String, Object> priceParams = new HashMap<>();
        priceParams.put("product", product.getId());
        priceParams.put("currency", "usd");
        priceParams.put("unit_amount", 1999L);
        priceParams.put("recurring", Map.of("interval", "month"));
        Price price = template.requestBody("direct:createPrice", priceParams, Price.class);

        // Create subscription with JSON
        String subscriptionJson = String.format("""
                {
                    "customer": "%s",
                    "items": [
                        {
                            "price": "%s"
                        }
                    ],
                    "metadata": {
                        "plan_type": "premium",
                        "auto_renew": "true"
                    }
                }
                """, customer.getId(), price.getId());

        Subscription subscription = template.requestBody("direct:createSubscription", subscriptionJson,
                Subscription.class);

        assertNotNull(subscription);
        assertNotNull(subscription.getId());
        assertTrue(subscription.getId().startsWith("sub_"));
        assertEquals(customer.getId(), subscription.getCustomer());
        assertEquals("premium", subscription.getMetadata().get("plan_type"));

        // Cleanup
        template.sendBodyAndHeader("direct:cancelSubscription", null, StripeConstants.OBJECT_ID, subscription.getId());
        template.sendBodyAndHeader("direct:deleteCustomer", null, StripeConstants.OBJECT_ID, customer.getId());

        Map<String, Object> archiveParams = new HashMap<>();
        archiveParams.put("active", false);
        Map<String, Object> headers = new HashMap<>();
        headers.put(StripeConstants.OBJECT_ID, product.getId());
        template.requestBodyAndHeaders("direct:updateProduct", archiveParams, headers, Product.class);
    }

    @Test
    public void testUpdateSubscriptionWithJson() throws Exception {
        // Create customer with test card source
        Map<String, Object> customerParams = new HashMap<>();
        customerParams.put("email", "update-sub@example.com");
        customerParams.put("source", "tok_visa");
        Customer customer = template.requestBody("direct:createCustomer", customerParams, Customer.class);

        Map<String, Object> productParams = new HashMap<>();
        productParams.put("name", "Update Sub Product");
        Product product = template.requestBody("direct:createProduct", productParams, Product.class);

        Map<String, Object> priceParams = new HashMap<>();
        priceParams.put("product", product.getId());
        priceParams.put("currency", "usd");
        priceParams.put("unit_amount", 999L);
        priceParams.put("recurring", Map.of("interval", "month"));
        Price price = template.requestBody("direct:createPrice", priceParams, Price.class);

        Map<String, Object> subscriptionParams = new HashMap<>();
        subscriptionParams.put("customer", customer.getId());
        subscriptionParams.put("items", List.of(Map.of("price", price.getId())));
        Subscription created = template.requestBody("direct:createSubscription", subscriptionParams,
                Subscription.class);

        // Update subscription with JSON
        String updateJson = """
                {
                    "metadata": {
                        "upgraded": "true",
                        "upgrade_date": "2025-12-05"
                    }
                }
                """;

        Map<String, Object> headers = new HashMap<>();
        headers.put(StripeConstants.OBJECT_ID, created.getId());

        Subscription updated = template.requestBodyAndHeaders("direct:updateSubscription", updateJson,
                headers, Subscription.class);

        assertNotNull(updated);
        assertEquals(created.getId(), updated.getId());
        assertEquals("true", updated.getMetadata().get("upgraded"));
        assertEquals("2025-12-05", updated.getMetadata().get("upgrade_date"));

        // Cleanup
        template.sendBodyAndHeader("direct:cancelSubscription", null, StripeConstants.OBJECT_ID, created.getId());
        template.sendBodyAndHeader("direct:deleteCustomer", null, StripeConstants.OBJECT_ID, customer.getId());

        Map<String, Object> archiveParams = new HashMap<>();
        archiveParams.put("active", false);
        Map<String, Object> archiveHeaders = new HashMap<>();
        archiveHeaders.put(StripeConstants.OBJECT_ID, product.getId());
        template.requestBodyAndHeaders("direct:updateProduct", archiveParams, archiveHeaders, Product.class);
    }

    @Test
    public void testListSubscriptionsWithJson() throws Exception {
        // List subscriptions with JSON
        String listJson = """
                {
                    "limit": 5,
                    "status": "all"
                }
                """;

        SubscriptionCollection subscriptions = template.requestBody("direct:listSubscriptions", listJson,
                SubscriptionCollection.class);

        assertNotNull(subscriptions);
        assertNotNull(subscriptions.getData());
    }

    @Test
    public void testCancelSubscription() throws Exception {
        // Create customer with test card source
        Map<String, Object> customerParams = new HashMap<>();
        customerParams.put("email", "cancel-sub@example.com");
        customerParams.put("source", "tok_visa");
        Customer customer = template.requestBody("direct:createCustomer", customerParams, Customer.class);

        Map<String, Object> productParams = new HashMap<>();
        productParams.put("name", "Cancel Sub Product");
        Product product = template.requestBody("direct:createProduct", productParams, Product.class);

        Map<String, Object> priceParams = new HashMap<>();
        priceParams.put("product", product.getId());
        priceParams.put("currency", "usd");
        priceParams.put("unit_amount", 1500L);
        priceParams.put("recurring", Map.of("interval", "month"));
        Price price = template.requestBody("direct:createPrice", priceParams, Price.class);

        Map<String, Object> subscriptionParams = new HashMap<>();
        subscriptionParams.put("customer", customer.getId());
        subscriptionParams.put("items", List.of(Map.of("price", price.getId())));
        Subscription created = template.requestBody("direct:createSubscription", subscriptionParams,
                Subscription.class);

        // Cancel subscription
        Subscription canceled = template.requestBodyAndHeader("direct:cancelSubscription", null,
                StripeConstants.OBJECT_ID, created.getId(), Subscription.class);

        assertNotNull(canceled);
        assertEquals("canceled", canceled.getStatus());

        // Cleanup
        template.sendBodyAndHeader("direct:deleteCustomer", null, StripeConstants.OBJECT_ID, customer.getId());

        Map<String, Object> archiveParams = new HashMap<>();
        archiveParams.put("active", false);
        Map<String, Object> headers = new HashMap<>();
        headers.put(StripeConstants.OBJECT_ID, product.getId());
        template.requestBodyAndHeaders("direct:updateProduct", archiveParams, headers, Product.class);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // Customer routes
                from("direct:createCustomer")
                        .to("stripe:customers?apiKey={{STRIPE_API_KEY}}");

                from("direct:deleteCustomer")
                        .setHeader(StripeConstants.METHOD_HEADER, constant(StripeConstants.METHOD_DELETE))
                        .to("stripe:customers?apiKey={{STRIPE_API_KEY}}");

                // Product routes
                from("direct:createProduct")
                        .to("stripe:products?apiKey={{STRIPE_API_KEY}}");

                from("direct:updateProduct")
                        .setHeader(StripeConstants.METHOD_HEADER, constant(StripeConstants.METHOD_UPDATE))
                        .to("stripe:products?apiKey={{STRIPE_API_KEY}}");

                // Price routes
                from("direct:createPrice")
                        .to("stripe:prices?apiKey={{STRIPE_API_KEY}}");

                // Subscription routes
                from("direct:createSubscription")
                        .to("stripe:subscriptions?apiKey={{STRIPE_API_KEY}}");

                from("direct:retrieveSubscription")
                        .setHeader(StripeConstants.METHOD_HEADER, constant(StripeConstants.METHOD_RETRIEVE))
                        .to("stripe:subscriptions?apiKey={{STRIPE_API_KEY}}");

                from("direct:updateSubscription")
                        .setHeader(StripeConstants.METHOD_HEADER, constant(StripeConstants.METHOD_UPDATE))
                        .to("stripe:subscriptions?apiKey={{STRIPE_API_KEY}}");

                from("direct:cancelSubscription")
                        .setHeader(StripeConstants.METHOD_HEADER, constant(StripeConstants.METHOD_CANCEL))
                        .to("stripe:subscriptions?apiKey={{STRIPE_API_KEY}}");

                from("direct:listSubscriptions")
                        .setHeader(StripeConstants.METHOD_HEADER, constant(StripeConstants.METHOD_LIST))
                        .to("stripe:subscriptions?apiKey={{STRIPE_API_KEY}}");
            }
        };
    }
}
