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

import com.stripe.model.Customer;
import com.stripe.model.Invoice;
import com.stripe.model.InvoiceCollection;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.stripe.StripeConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for Stripe Invoice operations with JSON payloads.
 *
 * To run this test: mvn verify -Dtest=StripeInvoiceIntegrationTest -DSTRIPE_API_KEY=sk_test_...
 */
@EnabledIfSystemProperty(named = "STRIPE_API_KEY", matches = ".*")
public class StripeInvoiceIntegrationTest extends StripeIntegrationTestSupport {

    @Test
    public void testCreateInvoiceWithJson() throws Exception {
        // Create customer first
        Map<String, Object> customerParams = new HashMap<>();
        customerParams.put("email", "invoice@example.com");
        customerParams.put("name", "Invoice Test Customer");
        Customer customer = template.requestBody("direct:createCustomer", customerParams, Customer.class);

        // Create invoice with JSON
        String invoiceJson = String.format("""
                {
                    "customer": "%s",
                    "collection_method": "send_invoice",
                    "days_until_due": 30,
                    "description": "Monthly service invoice",
                    "metadata": {
                        "invoice_type": "recurring",
                        "billing_period": "december_2025"
                    }
                }
                """, customer.getId());

        Invoice invoice = template.requestBody("direct:createInvoice", invoiceJson, Invoice.class);

        assertNotNull(invoice);
        assertNotNull(invoice.getId());
        assertTrue(invoice.getId().startsWith("in_"));
        assertEquals(customer.getId(), invoice.getCustomer());
        assertEquals("Monthly service invoice", invoice.getDescription());
        assertEquals("recurring", invoice.getMetadata().get("invoice_type"));
        assertEquals("december_2025", invoice.getMetadata().get("billing_period"));

        // Cleanup
        template.sendBodyAndHeader("direct:deleteCustomer", null, StripeConstants.OBJECT_ID, customer.getId());
    }

    @Test
    public void testUpdateInvoiceWithJson() throws Exception {
        // Create customer
        Map<String, Object> customerParams = new HashMap<>();
        customerParams.put("email", "update-invoice@example.com");
        Customer customer = template.requestBody("direct:createCustomer", customerParams, Customer.class);

        // Create invoice
        Map<String, Object> invoiceParams = new HashMap<>();
        invoiceParams.put("customer", customer.getId());
        invoiceParams.put("collection_method", "send_invoice");
        invoiceParams.put("days_until_due", 15);

        Invoice created = template.requestBody("direct:createInvoice", invoiceParams, Invoice.class);
        assertNotNull(created.getId());

        // Update invoice with JSON
        String updateJson = """
                {
                    "description": "Updated invoice description",
                    "metadata": {
                        "updated": "true",
                        "update_reason": "customer_request"
                    }
                }
                """;

        Map<String, Object> headers = new HashMap<>();
        headers.put(StripeConstants.OBJECT_ID, created.getId());

        Invoice updated = template.requestBodyAndHeaders("direct:updateInvoice", updateJson,
                headers, Invoice.class);

        assertNotNull(updated);
        assertEquals(created.getId(), updated.getId());
        assertEquals("Updated invoice description", updated.getDescription());
        assertEquals("true", updated.getMetadata().get("updated"));
        assertEquals("customer_request", updated.getMetadata().get("update_reason"));

        // Cleanup
        template.sendBodyAndHeader("direct:deleteCustomer", null, StripeConstants.OBJECT_ID, customer.getId());
    }

    @Test
    public void testRetrieveInvoice() throws Exception {
        // Create customer and invoice
        Map<String, Object> customerParams = new HashMap<>();
        customerParams.put("email", "retrieve-invoice@example.com");
        Customer customer = template.requestBody("direct:createCustomer", customerParams, Customer.class);

        Map<String, Object> invoiceParams = new HashMap<>();
        invoiceParams.put("customer", customer.getId());
        invoiceParams.put("collection_method", "send_invoice");
        invoiceParams.put("days_until_due", 20);

        Invoice created = template.requestBody("direct:createInvoice", invoiceParams, Invoice.class);
        assertNotNull(created.getId());

        // Retrieve invoice
        Invoice retrieved = template.requestBodyAndHeader("direct:retrieveInvoice", null,
                StripeConstants.OBJECT_ID, created.getId(), Invoice.class);

        assertNotNull(retrieved);
        assertEquals(created.getId(), retrieved.getId());
        assertEquals(customer.getId(), retrieved.getCustomer());

        // Cleanup
        template.sendBodyAndHeader("direct:deleteCustomer", null, StripeConstants.OBJECT_ID, customer.getId());
    }

    @Test
    public void testListInvoicesWithJson() throws Exception {
        // Create customer and invoice
        Map<String, Object> customerParams = new HashMap<>();
        customerParams.put("email", "list-invoice@example.com");
        Customer customer = template.requestBody("direct:createCustomer", customerParams, Customer.class);

        Map<String, Object> invoiceParams = new HashMap<>();
        invoiceParams.put("customer", customer.getId());
        invoiceParams.put("collection_method", "send_invoice");
        invoiceParams.put("days_until_due", 10);
        template.requestBody("direct:createInvoice", invoiceParams, Invoice.class);

        // List invoices with JSON
        String listJson = String.format("""
                {
                    "customer": "%s",
                    "limit": 10
                }
                """, customer.getId());

        InvoiceCollection invoices = template.requestBody("direct:listInvoices", listJson,
                InvoiceCollection.class);

        assertNotNull(invoices);
        assertNotNull(invoices.getData());
        assertTrue(invoices.getData().size() > 0);

        // Cleanup
        template.sendBodyAndHeader("direct:deleteCustomer", null, StripeConstants.OBJECT_ID, customer.getId());
    }

    @Test
    public void testCreateInvoiceWithComplexMetadataJson() throws Exception {
        // Create customer
        Map<String, Object> customerParams = new HashMap<>();
        customerParams.put("email", "complex-invoice@example.com");
        Customer customer = template.requestBody("direct:createCustomer", customerParams, Customer.class);

        // Create invoice with complex metadata
        String invoiceJson = String.format("""
                {
                    "customer": "%s",
                    "collection_method": "send_invoice",
                    "days_until_due": 45,
                    "metadata": {
                        "department": "sales",
                        "cost_center": "CC-1234",
                        "project_id": "PRJ-567",
                        "approval_status": "pending",
                        "created_by": "automated_system"
                    }
                }
                """, customer.getId());

        Invoice invoice = template.requestBody("direct:createInvoice", invoiceJson, Invoice.class);

        assertNotNull(invoice);
        assertNotNull(invoice.getMetadata());
        assertEquals(5, invoice.getMetadata().size());
        assertEquals("sales", invoice.getMetadata().get("department"));
        assertEquals("CC-1234", invoice.getMetadata().get("cost_center"));
        assertEquals("PRJ-567", invoice.getMetadata().get("project_id"));
        assertEquals("pending", invoice.getMetadata().get("approval_status"));
        assertEquals("automated_system", invoice.getMetadata().get("created_by"));

        // Cleanup
        template.sendBodyAndHeader("direct:deleteCustomer", null, StripeConstants.OBJECT_ID, customer.getId());
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

                // Invoice routes
                from("direct:createInvoice")
                        .to("stripe:invoices?apiKey={{STRIPE_API_KEY}}");

                from("direct:retrieveInvoice")
                        .setHeader(StripeConstants.METHOD_HEADER, constant(StripeConstants.METHOD_RETRIEVE))
                        .to("stripe:invoices?apiKey={{STRIPE_API_KEY}}");

                from("direct:updateInvoice")
                        .setHeader(StripeConstants.METHOD_HEADER, constant(StripeConstants.METHOD_UPDATE))
                        .to("stripe:invoices?apiKey={{STRIPE_API_KEY}}");

                from("direct:listInvoices")
                        .setHeader(StripeConstants.METHOD_HEADER, constant(StripeConstants.METHOD_LIST))
                        .to("stripe:invoices?apiKey={{STRIPE_API_KEY}}");
            }
        };
    }
}
