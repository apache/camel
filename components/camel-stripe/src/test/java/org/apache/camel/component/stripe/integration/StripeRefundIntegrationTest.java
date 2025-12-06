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
import java.util.Map;

import com.stripe.model.Charge;
import com.stripe.model.Refund;
import com.stripe.model.RefundCollection;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.stripe.StripeConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for Stripe Refund operations with JSON payloads.
 *
 * To run this test: mvn verify -Dtest=StripeRefundIntegrationTest -DSTRIPE_API_KEY=sk_test_...
 */
@EnabledIfSystemProperty(named = "STRIPE_API_KEY", matches = ".*")
public class StripeRefundIntegrationTest extends StripeIntegrationTestSupport {

    @Test
    public void testCreateRefundWithJson() throws Exception {
        // First create a charge to refund
        Map<String, Object> chargeParams = new HashMap<>();
        chargeParams.put("amount", 5000L);
        chargeParams.put("currency", "usd");
        chargeParams.put("source", "tok_visa");
        chargeParams.put("description", "Charge for refund test");

        Charge charge = template.requestBody("direct:createCharge", chargeParams, Charge.class);
        assertNotNull(charge.getId());

        // Create refund with JSON
        String refundJson = String.format("""
                {
                    "charge": "%s",
                    "amount": 2000,
                    "reason": "requested_by_customer",
                    "metadata": {
                        "order_id": "refund-order-123",
                        "reason_detail": "customer_not_satisfied"
                    }
                }
                """, charge.getId());

        Refund refund = template.requestBody("direct:createRefund", refundJson, Refund.class);

        assertNotNull(refund);
        assertNotNull(refund.getId());
        assertTrue(refund.getId().startsWith("re_"));
        assertEquals(2000L, refund.getAmount().longValue());
        assertEquals("requested_by_customer", refund.getReason());
        assertNotNull(refund.getMetadata());
        assertEquals("refund-order-123", refund.getMetadata().get("order_id"));
    }

    @Test
    public void testCreateFullRefundWithJson() throws Exception {
        // Create a charge
        Map<String, Object> chargeParams = new HashMap<>();
        chargeParams.put("amount", 3000L);
        chargeParams.put("currency", "usd");
        chargeParams.put("source", "tok_visa");

        Charge charge = template.requestBody("direct:createCharge", chargeParams, Charge.class);
        assertNotNull(charge.getId());

        // Full refund with JSON (no amount specified means full refund)
        String refundJson = String.format("""
                {
                    "charge": "%s",
                    "reason": "duplicate"
                }
                """, charge.getId());

        Refund refund = template.requestBody("direct:createRefund", refundJson, Refund.class);

        assertNotNull(refund);
        assertEquals(3000L, refund.getAmount().longValue());
        assertEquals("duplicate", refund.getReason());
    }

    @Test
    public void testUpdateRefundWithJson() throws Exception {
        // Create a charge and refund
        Map<String, Object> chargeParams = new HashMap<>();
        chargeParams.put("amount", 4000L);
        chargeParams.put("currency", "usd");
        chargeParams.put("source", "tok_visa");

        Charge charge = template.requestBody("direct:createCharge", chargeParams, Charge.class);

        Map<String, Object> refundParams = new HashMap<>();
        refundParams.put("charge", charge.getId());

        Refund created = template.requestBody("direct:createRefund", refundParams, Refund.class);
        assertNotNull(created.getId());

        // Update refund metadata with JSON
        String updateJson = """
                {
                    "metadata": {
                        "updated": "true",
                        "processor": "automated_system"
                    }
                }
                """;

        Map<String, Object> headers = new HashMap<>();
        headers.put(StripeConstants.OBJECT_ID, created.getId());

        Refund updated = template.requestBodyAndHeaders("direct:updateRefund", updateJson,
                headers, Refund.class);

        assertNotNull(updated);
        assertEquals(created.getId(), updated.getId());
        assertEquals("true", updated.getMetadata().get("updated"));
        assertEquals("automated_system", updated.getMetadata().get("processor"));
    }

    @Test
    public void testListRefundsWithJson() throws Exception {
        // Create a charge and refund
        Map<String, Object> chargeParams = new HashMap<>();
        chargeParams.put("amount", 2500L);
        chargeParams.put("currency", "usd");
        chargeParams.put("source", "tok_visa");

        Charge charge = template.requestBody("direct:createCharge", chargeParams, Charge.class);

        Map<String, Object> refundParams = new HashMap<>();
        refundParams.put("charge", charge.getId());
        template.requestBody("direct:createRefund", refundParams, Refund.class);

        // List refunds with JSON parameters
        String listJson = """
                {
                    "limit": 10
                }
                """;

        RefundCollection refunds = template.requestBody("direct:listRefunds", listJson,
                RefundCollection.class);

        assertNotNull(refunds);
        assertNotNull(refunds.getData());
        assertTrue(refunds.getData().size() > 0);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // Charge routes (needed to create charges for refunds)
                from("direct:createCharge")
                        .to("stripe:charges?apiKey={{STRIPE_API_KEY}}");

                // Refund routes
                from("direct:createRefund")
                        .to("stripe:refunds?apiKey={{STRIPE_API_KEY}}");

                from("direct:retrieveRefund")
                        .setHeader(StripeConstants.METHOD_HEADER, constant(StripeConstants.METHOD_RETRIEVE))
                        .to("stripe:refunds?apiKey={{STRIPE_API_KEY}}");

                from("direct:updateRefund")
                        .setHeader(StripeConstants.METHOD_HEADER, constant(StripeConstants.METHOD_UPDATE))
                        .to("stripe:refunds?apiKey={{STRIPE_API_KEY}}");

                from("direct:listRefunds")
                        .setHeader(StripeConstants.METHOD_HEADER, constant(StripeConstants.METHOD_LIST))
                        .to("stripe:refunds?apiKey={{STRIPE_API_KEY}}");
            }
        };
    }
}
