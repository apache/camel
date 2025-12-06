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

import com.stripe.model.PaymentIntent;
import com.stripe.model.PaymentIntentCollection;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.stripe.StripeConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for Stripe PaymentIntent operations.
 *
 * To run this test: mvn verify -Dtest=StripePaymentIntentIntegrationTest -DSTRIPE_API_KEY=sk_test_...
 */
@EnabledIfSystemProperty(named = "STRIPE_API_KEY", matches = ".*")
public class StripePaymentIntentIntegrationTest extends StripeIntegrationTestSupport {

    @Test
    public void testCreatePaymentIntent() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("amount", 1000L); // $10.00
        params.put("currency", "usd");
        params.put("payment_method_types", List.of("card"));
        params.put("description", "Integration test payment");

        PaymentIntent paymentIntent = template.requestBody("direct:createPaymentIntent", params,
                PaymentIntent.class);

        assertNotNull(paymentIntent);
        assertNotNull(paymentIntent.getId());
        assertTrue(paymentIntent.getId().startsWith("pi_"));
        assertEquals(1000L, paymentIntent.getAmount().longValue());
        assertEquals("usd", paymentIntent.getCurrency());
        assertEquals("requires_payment_method", paymentIntent.getStatus());
    }

    @Test
    public void testRetrievePaymentIntent() throws Exception {
        // First create a payment intent
        Map<String, Object> createParams = new HashMap<>();
        createParams.put("amount", 2000L);
        createParams.put("currency", "usd");
        createParams.put("payment_method_types", List.of("card"));

        PaymentIntent created = template.requestBody("direct:createPaymentIntent", createParams,
                PaymentIntent.class);
        assertNotNull(created.getId());

        // Now retrieve it
        PaymentIntent retrieved = template.requestBodyAndHeader("direct:retrievePaymentIntent", null,
                StripeConstants.OBJECT_ID, created.getId(), PaymentIntent.class);

        assertNotNull(retrieved);
        assertEquals(created.getId(), retrieved.getId());
        assertEquals(2000L, retrieved.getAmount().longValue());
    }

    @Test
    public void testUpdatePaymentIntent() throws Exception {
        // Create a payment intent
        Map<String, Object> createParams = new HashMap<>();
        createParams.put("amount", 3000L);
        createParams.put("currency", "usd");
        createParams.put("payment_method_types", List.of("card"));

        PaymentIntent created = template.requestBody("direct:createPaymentIntent", createParams,
                PaymentIntent.class);
        assertNotNull(created.getId());

        // Update the payment intent
        Map<String, Object> updateParams = new HashMap<>();
        updateParams.put("description", "Updated payment description");
        Map<String, String> metadata = new HashMap<>();
        metadata.put("order_id", "12345");
        updateParams.put("metadata", metadata);

        Map<String, Object> headers = new HashMap<>();
        headers.put(StripeConstants.OBJECT_ID, created.getId());

        PaymentIntent updated = template.requestBodyAndHeaders("direct:updatePaymentIntent", updateParams,
                headers, PaymentIntent.class);

        assertNotNull(updated);
        assertEquals(created.getId(), updated.getId());
        assertEquals("Updated payment description", updated.getDescription());
        assertNotNull(updated.getMetadata());
        assertEquals("12345", updated.getMetadata().get("order_id"));
    }

    @Test
    public void testCancelPaymentIntent() throws Exception {
        // Create a payment intent
        Map<String, Object> createParams = new HashMap<>();
        createParams.put("amount", 5000L);
        createParams.put("currency", "usd");
        createParams.put("payment_method_types", List.of("card"));

        PaymentIntent created = template.requestBody("direct:createPaymentIntent", createParams,
                PaymentIntent.class);
        assertNotNull(created.getId());
        assertEquals("requires_payment_method", created.getStatus());

        // Cancel the payment intent
        PaymentIntent canceled = template.requestBodyAndHeader("direct:cancelPaymentIntent", null,
                StripeConstants.OBJECT_ID, created.getId(), PaymentIntent.class);

        assertNotNull(canceled);
        assertEquals(created.getId(), canceled.getId());
        assertEquals("canceled", canceled.getStatus());
    }

    @Test
    public void testListPaymentIntents() throws Exception {
        // Create a couple of payment intents
        Map<String, Object> params1 = new HashMap<>();
        params1.put("amount", 1000L);
        params1.put("currency", "usd");
        params1.put("payment_method_types", List.of("card"));
        template.requestBody("direct:createPaymentIntent", params1, PaymentIntent.class);

        Map<String, Object> params2 = new HashMap<>();
        params2.put("amount", 2000L);
        params2.put("currency", "usd");
        params2.put("payment_method_types", List.of("card"));
        template.requestBody("direct:createPaymentIntent", params2, PaymentIntent.class);

        // List payment intents with limit
        Map<String, Object> listParams = new HashMap<>();
        listParams.put("limit", 10);

        PaymentIntentCollection paymentIntents = template.requestBody("direct:listPaymentIntents",
                listParams, PaymentIntentCollection.class);

        assertNotNull(paymentIntents);
        assertNotNull(paymentIntents.getData());
        assertTrue(paymentIntents.getData().size() > 0);
    }

    @Test
    public void testCreatePaymentIntentWithJson() throws Exception {
        String json = """
                {
                    "amount": 1500,
                    "currency": "usd",
                    "payment_method_types": ["card"],
                    "description": "Payment created with JSON"
                }
                """;

        PaymentIntent paymentIntent = template.requestBody("direct:createPaymentIntent", json,
                PaymentIntent.class);

        assertNotNull(paymentIntent);
        assertNotNull(paymentIntent.getId());
        assertTrue(paymentIntent.getId().startsWith("pi_"));
        assertEquals(1500L, paymentIntent.getAmount().longValue());
        assertEquals("usd", paymentIntent.getCurrency());
        assertEquals("Payment created with JSON", paymentIntent.getDescription());
    }

    @Test
    public void testUpdatePaymentIntentWithJson() throws Exception {
        // Create a payment intent with Map
        Map<String, Object> createParams = new HashMap<>();
        createParams.put("amount", 2500L);
        createParams.put("currency", "usd");
        createParams.put("payment_method_types", List.of("card"));

        PaymentIntent created = template.requestBody("direct:createPaymentIntent", createParams,
                PaymentIntent.class);
        assertNotNull(created.getId());

        // Update with JSON
        String updateJson = """
                {
                    "description": "Updated via JSON",
                    "metadata": {
                        "order_id": "json-order-123",
                        "customer_ref": "cust-456"
                    }
                }
                """;

        Map<String, Object> headers = new HashMap<>();
        headers.put(StripeConstants.OBJECT_ID, created.getId());

        PaymentIntent updated = template.requestBodyAndHeaders("direct:updatePaymentIntent", updateJson,
                headers, PaymentIntent.class);

        assertNotNull(updated);
        assertEquals(created.getId(), updated.getId());
        assertEquals("Updated via JSON", updated.getDescription());
        assertNotNull(updated.getMetadata());
        assertEquals("json-order-123", updated.getMetadata().get("order_id"));
        assertEquals("cust-456", updated.getMetadata().get("customer_ref"));
    }

    @Test
    public void testCreatePaymentIntentWithComplexJson() throws Exception {
        // Test with nested objects in JSON
        String json = """
                {
                    "amount": 3500,
                    "currency": "usd",
                    "payment_method_types": ["card"],
                    "metadata": {
                        "integration_test": "true",
                        "test_type": "json_complex"
                    }
                }
                """;

        PaymentIntent paymentIntent = template.requestBody("direct:createPaymentIntent", json,
                PaymentIntent.class);

        assertNotNull(paymentIntent);
        assertNotNull(paymentIntent.getId());
        assertEquals(3500L, paymentIntent.getAmount().longValue());
        assertNotNull(paymentIntent.getMetadata());
        assertEquals("true", paymentIntent.getMetadata().get("integration_test"));
        assertEquals("json_complex", paymentIntent.getMetadata().get("test_type"));
    }

    @Test
    public void testListPaymentIntentsWithJson() throws Exception {
        // Create some payment intents first
        String json1 = """
                {
                    "amount": 1000,
                    "currency": "usd",
                    "payment_method_types": ["card"]
                }
                """;
        template.requestBody("direct:createPaymentIntent", json1, PaymentIntent.class);

        // List with JSON
        String listJson = """
                {
                    "limit": 5
                }
                """;

        PaymentIntentCollection paymentIntents = template.requestBody("direct:listPaymentIntents",
                listJson, PaymentIntentCollection.class);

        assertNotNull(paymentIntents);
        assertNotNull(paymentIntents.getData());
        assertTrue(paymentIntents.getData().size() > 0);
    }

    @Test
    public void testCancelPaymentIntentWithJson() throws Exception {
        // Create a payment intent with JSON
        String createJson = """
                {
                    "amount": 2500,
                    "currency": "usd",
                    "payment_method_types": ["card"],
                    "description": "Payment to be canceled"
                }
                """;

        PaymentIntent created = template.requestBody("direct:createPaymentIntent", createJson,
                PaymentIntent.class);
        assertNotNull(created.getId());

        // Cancel it
        PaymentIntent canceled = template.requestBodyAndHeader("direct:cancelPaymentIntent", null,
                StripeConstants.OBJECT_ID, created.getId(), PaymentIntent.class);

        assertNotNull(canceled);
        assertEquals("canceled", canceled.getStatus());
        assertEquals("Payment to be canceled", canceled.getDescription());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:createPaymentIntent")
                        .to("stripe:paymentIntents?apiKey={{STRIPE_API_KEY}}");

                from("direct:retrievePaymentIntent")
                        .setHeader(StripeConstants.METHOD_HEADER, constant(StripeConstants.METHOD_RETRIEVE))
                        .to("stripe:paymentIntents?apiKey={{STRIPE_API_KEY}}");

                from("direct:updatePaymentIntent")
                        .setHeader(StripeConstants.METHOD_HEADER, constant(StripeConstants.METHOD_UPDATE))
                        .to("stripe:paymentIntents?apiKey={{STRIPE_API_KEY}}");

                from("direct:cancelPaymentIntent")
                        .setHeader(StripeConstants.METHOD_HEADER, constant(StripeConstants.METHOD_CANCEL))
                        .to("stripe:paymentIntents?apiKey={{STRIPE_API_KEY}}");

                from("direct:listPaymentIntents")
                        .setHeader(StripeConstants.METHOD_HEADER, constant(StripeConstants.METHOD_LIST))
                        .to("stripe:paymentIntents?apiKey={{STRIPE_API_KEY}}");
            }
        };
    }
}
