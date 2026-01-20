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
import com.stripe.model.CustomerCollection;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.stripe.StripeConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for Stripe Customer operations.
 *
 * To run this test: mvn verify -Dtest=StripeCustomerIntegrationTest -DSTRIPE_API_KEY=sk_test_...
 */
@EnabledIfSystemProperty(named = "STRIPE_API_KEY", matches = ".*")
public class StripeCustomerIntegrationTest extends StripeIntegrationTestSupport {

    @Test
    public void testCreateCustomer() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("email", "test@example.com");
        params.put("name", "Test Customer");
        params.put("description", "Integration test customer");

        Customer customer = template.requestBody("direct:createCustomer", params, Customer.class);

        assertNotNull(customer);
        assertNotNull(customer.getId());
        assertEquals("test@example.com", customer.getEmail());
        assertEquals("Test Customer", customer.getName());
        assertTrue(customer.getId().startsWith("cus_"));

        // Cleanup
        template.sendBodyAndHeader("direct:deleteCustomer", null, StripeConstants.OBJECT_ID, customer.getId());
    }

    @Test
    public void testRetrieveCustomer() throws Exception {
        // First create a customer
        Map<String, Object> createParams = new HashMap<>();
        createParams.put("email", "retrieve@example.com");
        createParams.put("name", "Retrieve Test");

        Customer created = template.requestBody("direct:createCustomer", createParams, Customer.class);
        assertNotNull(created.getId());

        // Now retrieve it
        Customer retrieved = template.requestBodyAndHeader("direct:retrieveCustomer", null,
                StripeConstants.OBJECT_ID, created.getId(), Customer.class);

        assertNotNull(retrieved);
        assertEquals(created.getId(), retrieved.getId());
        assertEquals("retrieve@example.com", retrieved.getEmail());

        // Cleanup
        template.sendBodyAndHeader("direct:deleteCustomer", null, StripeConstants.OBJECT_ID, created.getId());
    }

    @Test
    public void testUpdateCustomer() throws Exception {
        // Create a customer
        Map<String, Object> createParams = new HashMap<>();
        createParams.put("email", "update@example.com");
        createParams.put("name", "Original Name");

        Customer created = template.requestBody("direct:createCustomer", createParams, Customer.class);
        assertNotNull(created.getId());

        // Update the customer
        Map<String, Object> updateParams = new HashMap<>();
        updateParams.put("name", "Updated Name");
        updateParams.put("description", "Updated description");

        Map<String, Object> headers = new HashMap<>();
        headers.put(StripeConstants.OBJECT_ID, created.getId());

        Customer updated = template.requestBodyAndHeaders("direct:updateCustomer", updateParams,
                headers, Customer.class);

        assertNotNull(updated);
        assertEquals(created.getId(), updated.getId());
        assertEquals("Updated Name", updated.getName());
        assertEquals("Updated description", updated.getDescription());

        // Cleanup
        template.sendBodyAndHeader("direct:deleteCustomer", null, StripeConstants.OBJECT_ID, created.getId());
    }

    @Test
    public void testDeleteCustomer() throws Exception {
        // Create a customer
        Map<String, Object> createParams = new HashMap<>();
        createParams.put("email", "delete@example.com");

        Customer created = template.requestBody("direct:createCustomer", createParams, Customer.class);
        assertNotNull(created.getId());

        // Delete the customer
        Customer deleted = template.requestBodyAndHeader("direct:deleteCustomer", null,
                StripeConstants.OBJECT_ID, created.getId(), Customer.class);

        assertNotNull(deleted);
        assertTrue(deleted.getDeleted());
    }

    @Test
    public void testListCustomers() throws Exception {
        // Create a couple of customers
        Map<String, Object> params1 = new HashMap<>();
        params1.put("email", "list1@example.com");
        Customer customer1 = template.requestBody("direct:createCustomer", params1, Customer.class);

        Map<String, Object> params2 = new HashMap<>();
        params2.put("email", "list2@example.com");
        Customer customer2 = template.requestBody("direct:createCustomer", params2, Customer.class);

        // List customers with limit
        Map<String, Object> listParams = new HashMap<>();
        listParams.put("limit", 10);

        CustomerCollection customers = template.requestBody("direct:listCustomers", listParams,
                CustomerCollection.class);

        assertNotNull(customers);
        assertNotNull(customers.getData());
        assertTrue(customers.getData().size() > 0);

        // Cleanup
        template.sendBodyAndHeader("direct:deleteCustomer", null, StripeConstants.OBJECT_ID, customer1.getId());
        template.sendBodyAndHeader("direct:deleteCustomer", null, StripeConstants.OBJECT_ID, customer2.getId());
    }

    @Test
    public void testCreateCustomerWithJson() throws Exception {
        String json = """
                {
                    "email": "json@example.com",
                    "name": "JSON Test Customer",
                    "description": "Customer created with JSON"
                }
                """;

        Customer customer = template.requestBody("direct:createCustomer", json, Customer.class);

        assertNotNull(customer);
        assertNotNull(customer.getId());
        assertEquals("json@example.com", customer.getEmail());
        assertEquals("JSON Test Customer", customer.getName());
        assertTrue(customer.getId().startsWith("cus_"));

        // Cleanup
        template.sendBodyAndHeader("direct:deleteCustomer", null, StripeConstants.OBJECT_ID, customer.getId());
    }

    @Test
    public void testUpdateCustomerWithJson() throws Exception {
        // Create a customer with Map
        Map<String, Object> createParams = new HashMap<>();
        createParams.put("email", "updatejson@example.com");
        createParams.put("name", "Original Name");

        Customer created = template.requestBody("direct:createCustomer", createParams, Customer.class);
        assertNotNull(created.getId());

        // Update the customer with JSON
        String updateJson = """
                {
                    "name": "Updated via JSON",
                    "description": "Updated using JSON string"
                }
                """;

        Map<String, Object> headers = new HashMap<>();
        headers.put(StripeConstants.OBJECT_ID, created.getId());

        Customer updated = template.requestBodyAndHeaders("direct:updateCustomer", updateJson,
                headers, Customer.class);

        assertNotNull(updated);
        assertEquals(created.getId(), updated.getId());
        assertEquals("Updated via JSON", updated.getName());
        assertEquals("Updated using JSON string", updated.getDescription());

        // Cleanup
        template.sendBodyAndHeader("direct:deleteCustomer", null, StripeConstants.OBJECT_ID, created.getId());
    }

    @Test
    public void testCreateCustomerWithEmptyJson() throws Exception {
        // Empty JSON should create customer with minimal info
        String json = "{}";

        Customer customer = template.requestBody("direct:createCustomer", json, Customer.class);

        assertNotNull(customer);
        assertNotNull(customer.getId());
        assertTrue(customer.getId().startsWith("cus_"));

        // Cleanup
        template.sendBodyAndHeader("direct:deleteCustomer", null, StripeConstants.OBJECT_ID, customer.getId());
    }

    @Test
    public void testCreateCustomerWithMetadataJson() throws Exception {
        String json = """
                {
                    "email": "metadata@example.com",
                    "name": "Metadata Customer",
                    "metadata": {
                        "account_type": "business",
                        "company_size": "50-100",
                        "industry": "technology",
                        "source": "website"
                    }
                }
                """;

        Customer customer = template.requestBody("direct:createCustomer", json, Customer.class);

        assertNotNull(customer);
        assertNotNull(customer.getMetadata());
        assertEquals("business", customer.getMetadata().get("account_type"));
        assertEquals("50-100", customer.getMetadata().get("company_size"));
        assertEquals("technology", customer.getMetadata().get("industry"));
        assertEquals("website", customer.getMetadata().get("source"));

        // Cleanup
        template.sendBodyAndHeader("direct:deleteCustomer", null, StripeConstants.OBJECT_ID, customer.getId());
    }

    @Test
    public void testListCustomersWithJson() throws Exception {
        // Create customers first
        String json1 = """
                {
                    "email": "list-json1@example.com"
                }
                """;
        Customer customer1 = template.requestBody("direct:createCustomer", json1, Customer.class);

        String json2 = """
                {
                    "email": "list-json2@example.com"
                }
                """;
        Customer customer2 = template.requestBody("direct:createCustomer", json2, Customer.class);

        // List with JSON
        String listJson = """
                {
                    "limit": 10
                }
                """;

        CustomerCollection customers = template.requestBody("direct:listCustomers", listJson,
                CustomerCollection.class);

        assertNotNull(customers);
        assertNotNull(customers.getData());
        assertTrue(customers.getData().size() > 0);

        // Cleanup
        template.sendBodyAndHeader("direct:deleteCustomer", null, StripeConstants.OBJECT_ID, customer1.getId());
        template.sendBodyAndHeader("direct:deleteCustomer", null, StripeConstants.OBJECT_ID, customer2.getId());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:createCustomer")
                        .to("stripe:customers?apiKey={{STRIPE_API_KEY}}");

                from("direct:retrieveCustomer")
                        .setHeader(StripeConstants.METHOD_HEADER, constant(StripeConstants.METHOD_RETRIEVE))
                        .to("stripe:customers?apiKey={{STRIPE_API_KEY}}");

                from("direct:updateCustomer")
                        .setHeader(StripeConstants.METHOD_HEADER, constant(StripeConstants.METHOD_UPDATE))
                        .to("stripe:customers?apiKey={{STRIPE_API_KEY}}");

                from("direct:deleteCustomer")
                        .setHeader(StripeConstants.METHOD_HEADER, constant(StripeConstants.METHOD_DELETE))
                        .to("stripe:customers?apiKey={{STRIPE_API_KEY}}");

                from("direct:listCustomers")
                        .setHeader(StripeConstants.METHOD_HEADER, constant(StripeConstants.METHOD_LIST))
                        .to("stripe:customers?apiKey={{STRIPE_API_KEY}}");
            }
        };
    }
}
