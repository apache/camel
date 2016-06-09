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

import java.util.HashMap;
import java.util.UUID;

import com.braintreegateway.Customer;
import com.braintreegateway.CustomerRequest;
import com.braintreegateway.CustomerSearchRequest;
import com.braintreegateway.ResourceCollection;
import com.braintreegateway.Result;
import com.braintreegateway.ValidationError;
import com.braintreegateway.ValidationErrorCode;
import com.braintreegateway.ValidationErrors;
import com.braintreegateway.exceptions.NotFoundException;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.braintree.internal.CustomerGatewayApiMethod;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class CustomerGatewayIntegrationTest extends AbstractBraintreeTestSupport {

    private static final String PATH_PREFIX = getApiNameAsString(CustomerGatewayApiMethod.class);
    private static final Logger LOG = LoggerFactory.getLogger(CustomerGatewayIntegrationTest.class);

    /**
     * Customers management workflow:
     * - create a customer
     * - lookup by id
     * - update first name
     * - delete by id
     * - confirm deletion by searching again
     *
     * @throws Exception
     */
    @Test
    public void testCustomerManagementWorkflow() throws Exception {
        String customerLastName = UUID.randomUUID().toString();
        String customerId = null;

        // Create customer
        Result<Customer> createResult = requestBody(
            "direct://CREATE_IN_BODY",
            new CustomerRequest()
                .firstName("user")
                .lastName(customerLastName)
                .company("Apache")
                .email("user@braintree.camel")
                .website("http://user.braintree.camel"),
            Result.class
        );

        assertNotNull(createResult);
        assertTrue(createResult.isSuccess());
        assertNotNull(createResult.getTarget());
        assertNotNull(createResult.getTarget().getId());

        customerId = createResult.getTarget().getId();

        // Find customer by ID
        Customer customer1 = requestBody("direct://FIND_IN_BODY", customerId, Customer.class);
        assertNotNull(customer1);
        assertEquals("user", customer1.getFirstName());
        assertEquals(customerLastName, customer1.getLastName());
        assertEquals("Apache", customer1.getCompany());
        assertEquals("user@braintree.camel", customer1.getEmail());
        assertEquals("http://user.braintree.camel", customer1.getWebsite());

        // Update customer
        HashMap<String, Object> headers = new HashMap<>();
        headers.put("CamelBraintree.id", customerId);
        Result<Customer> updateResult = requestBodyAndHeaders(
            "direct://UPDATE_IN_BODY",
            new CustomerRequest().firstName("user-mod"),
            headers,
            Result.class
        );

        assertNotNull(updateResult);
        assertTrue(updateResult.isSuccess());
        assertNotNull(updateResult.getTarget());
        assertEquals("user-mod", updateResult.getTarget().getFirstName());

        // Delete customer
        Result<Customer> customerResult = requestBody("direct://DELETE_IN_BODY", customerId, Result.class);
        assertNotNull(customerResult);
        assertTrue(customerResult.isSuccess());
        assertNull(customerResult.getTarget());

        // Check if customer has been deleted customer
        ResourceCollection<Customer> customers = requestBody(
            "direct://SEARCH_IN_BODY",
            new CustomerSearchRequest().id().is(customerId),
            ResourceCollection.class
        );

        assertNotNull(customers);
        assertEquals(0, customers.getMaximumSize());
    }

    @Test
    public void testUpdateUnknownCustomer() throws Exception {
        try {
            String id = "unknown-" + UUID.randomUUID().toString();

            HashMap<String, Object> headers = new HashMap<>();
            headers.put("CamelBraintree.id", id);

            requestBodyAndHeaders("direct://UPDATE_IN_BODY",
                new CustomerRequest().firstName(id),
                headers);

            fail("Should have thrown NotFoundException");
        } catch (CamelExecutionException e) {
            assertIsInstanceOf(NotFoundException.class, e.getCause().getCause());
        }
    }

    @Test
    public void testSearchUnknownCustomer() throws Exception {
        try {
            requestBody("direct://FIND_IN_BODY", "unknown-" + UUID.randomUUID().toString());
            fail("Should have thrown NotFoundException");
        } catch (CamelExecutionException e) {
            assertIsInstanceOf(NotFoundException.class, e.getCause().getCause());
        }
    }

    @Test
    public void testWrongCustomerCreateRequest() throws Exception {
        // Create customer
        Result<Customer> createResult = requestBody(
            "direct://CREATE_IN_BODY",
            new CustomerRequest()
                .firstName("user")
                .lastName(UUID.randomUUID().toString())
                .company("Apache")
                .email("wrongEmail")
                .website("http://user.braintree.camel"),
            Result.class
        );

        assertNotNull(createResult);
        assertFalse(createResult.isSuccess());


        final ValidationErrors errors = createResult.getErrors();
        assertNotNull(errors);
        assertNotNull(errors.getAllDeepValidationErrors());

        ValidationError invalidMailError = null;
        for (ValidationError error : errors.getAllDeepValidationErrors()) {
            if (error.getCode() == ValidationErrorCode.CUSTOMER_EMAIL_FORMAT_IS_INVALID) {
                invalidMailError = error;
                break;
            }
        }

        assertNotNull(invalidMailError);
    }

    // *************************************************************************
    // Routes
    // *************************************************************************

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct://CREATE_IN_BODY")
                    .to("braintree://" + PATH_PREFIX + "/create?inBody=request");
                from("direct://DELETE_IN_BODY")
                    .to("braintree://" + PATH_PREFIX + "/delete?inBody=id");
                from("direct://FIND_IN_BODY")
                    .to("braintree://" + PATH_PREFIX + "/find?inBody=id");
                from("direct://SEARCH_IN_BODY")
                    .to("braintree://" + PATH_PREFIX + "/search?inBody=query");
                from("direct://UPDATE_IN_BODY")
                    .to("braintree://" + PATH_PREFIX + "/update?inBody=request");
            }
        };
    }
}
