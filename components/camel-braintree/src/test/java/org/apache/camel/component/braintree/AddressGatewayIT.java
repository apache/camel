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

import com.braintreegateway.Address;
import com.braintreegateway.AddressRequest;
import com.braintreegateway.BraintreeGateway;
import com.braintreegateway.Customer;
import com.braintreegateway.CustomerRequest;
import com.braintreegateway.Result;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.braintree.internal.AddressGatewayApiMethod;
import org.apache.camel.component.braintree.internal.BraintreeApiCollection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "braintreeAuthenticationType", matches = ".*")
public class AddressGatewayIT extends AbstractBraintreeTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(AddressGatewayIT.class);
    private static final String PATH_PREFIX
            = BraintreeApiCollection.getCollection().getApiName(AddressGatewayApiMethod.class).getName();

    private BraintreeGateway gateway;
    private Customer customer;
    private final List<String> addressIds;

    // *************************************************************************
    //
    // *************************************************************************

    public AddressGatewayIT() {
        this.customer = null;
        this.gateway = null;
        this.addressIds = new LinkedList<>();
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
        if (this.gateway != null && customer != null) {
            for (String id : this.addressIds) {
                if (this.gateway.address().delete(customer.getId(), id).isSuccess()) {
                    LOG.info("Address deleted - customer={}, id={}", customer.getId(), id);
                } else {
                    LOG.warn("Unable to delete address - customer={}, id={}", customer.getId(), id);
                }
            }

            this.addressIds.clear();

            if (this.gateway.customer().delete(this.customer.getId()).isSuccess()) {
                LOG.info("Customer deleted - id={}", this.customer.getId());
            } else {
                LOG.warn("Unable to delete customer - id={}", this.customer.getId());
            }
        }
    }

    private Address createAddress() {
        // Create address
        final Result<Address> result = gateway.address().create(
                this.customer.getId(),
                new AddressRequest()
                        .company("Apache")
                        .streetAddress("1901 Munsey Drive")
                        .locality("Forest Hill"));

        assertNotNull(result, "create");
        assertTrue(result.isSuccess());

        LOG.info("Address created - customer={}, id={}", this.customer.getId(), result.getTarget().getId());

        return result.getTarget();
    }

    // *************************************************************************
    //
    // *************************************************************************

    @Test
    public void testCreate() {
        assertNotNull(this.gateway, "BraintreeGateway can't be null");
        assertNotNull(this.customer, "Customer can't be null");

        final Result<Address> address = requestBodyAndHeaders(
                "direct://CREATE",
                null,
                new BraintreeHeaderBuilder()
                        .add("customerId", customer.getId())
                        .add("request", new AddressRequest()
                                .company("Apache")
                                .streetAddress("1901 Munsey Drive")
                                .locality("Forest Hill"))
                        .build(),
                Result.class);

        assertNotNull(address, "create");
        assertTrue(address.isSuccess());

        LOG.info("Address created - customer={}, id={}", customer.getId(), address.getTarget().getId());
        this.addressIds.add(address.getTarget().getId());
    }

    @Test
    public void testDelete() {
        assertNotNull(this.gateway, "BraintreeGateway can't be null");
        assertNotNull(this.customer, "Customer can't be null");

        final Address address = createAddress();
        final Result<Address> result = requestBodyAndHeaders(
                "direct://DELETE",
                null,
                new BraintreeHeaderBuilder()
                        .add("customerId", customer.getId())
                        .add("id", address.getId())
                        .build(),
                Result.class);

        assertNotNull(address, "delete");
        assertTrue(result.isSuccess());

        LOG.info("Address deleted - customer={}, id={}", customer.getId(), address.getId());
    }

    @Test
    public void testFind() {
        assertNotNull(this.gateway, "BraintreeGateway can't be null");
        assertNotNull(this.customer, "Customer can't be null");

        final Address addressRef = createAddress();
        this.addressIds.add(addressRef.getId());

        final Address address = requestBodyAndHeaders(
                "direct://FIND", null,
                new BraintreeHeaderBuilder()
                        .add("customerId", customer.getId())
                        .add("id", addressRef.getId())
                        .build(),
                Address.class);

        assertNotNull(address, "find");
        LOG.info("Address found - customer={}, id={}", customer.getId(), address.getId());
    }

    @Test
    public void testUpdate() {
        assertNotNull(this.gateway, "BraintreeGateway can't be null");
        assertNotNull(this.customer, "Customer can't be null");

        final Address addressRef = createAddress();
        this.addressIds.add(addressRef.getId());

        final Result<Address> result = requestBodyAndHeaders(
                "direct://UPDATE", null,
                new BraintreeHeaderBuilder()
                        .add("customerId", customer.getId())
                        .add("id", addressRef.getId())
                        .add("request", new AddressRequest()
                                .company("Apache")
                                .streetAddress(customer.getId())
                                .locality(customer.getId()))
                        .build(),
                Result.class);

        assertNotNull(result, "update");
        assertTrue(result.isSuccess());

        LOG.info("Address updated - customer={}, id={}", customer.getId(), result.getTarget().getId());
    }

    // *************************************************************************
    // Routes
    // *************************************************************************

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // test route for create
                from("direct://CREATE")
                        .to("braintree://" + PATH_PREFIX + "/create");
                // test route for delete
                from("direct://DELETE")
                        .to("braintree://" + PATH_PREFIX + "/delete");
                // test route for find
                from("direct://FIND")
                        .to("braintree://" + PATH_PREFIX + "/find");
                // test route for update
                from("direct://UPDATE")
                        .to("braintree://" + PATH_PREFIX + "/update");
            }
        };
    }
}
