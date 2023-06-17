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
package com.braintreegateway;

import com.braintreegateway.exceptions.NotFoundException;
import com.braintreegateway.util.Http;
import com.braintreegateway.util.NodeWrapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Provides methods to create, delete, find, and update {@link Customer}
 * objects. This class does not need to be instantiated directly. Instead, use
 * {@link BraintreeGateway#customer()} to get an instance of this class:
 *
 * <pre>
 * BraintreeGateway gateway = new BraintreeGateway(...);
 * gateway.customer().create(...)
 * </pre>
 *
 * For more detailed information on {@link Customer Customers}, see <a href="https://developers.braintreepayments.com/reference/response/customer/java" target="_blank">https://developers.braintreepayments.com/reference/response/customer/java</a>
 */
public class CustomerGateway {
    private Configuration configuration;
    private Http http;

    public CustomerGateway(Http http, Configuration configuration) {
        this.configuration = configuration;
        this.http = http;
    }

    /**
     * Finds all Customers and returns a {@link ResourceCollection}.
     *
     * @return a {@link ResourceCollection}.
     */
    public ResourceCollection<Customer> all() {
        NodeWrapper response = http.post(configuration.getMerchantPath() + "/customers/advanced_search_ids");
        return new ResourceCollection<Customer>(new CustomerPager(this, new CustomerSearchRequest()), response);
    }

    List<Customer> fetchCustomers(CustomerSearchRequest query, List<String> ids) {
        query.ids().in(ids);
        NodeWrapper response = http.post(configuration.getMerchantPath() + "/customers/advanced_search", query);

        List<Customer> items = new ArrayList<Customer>();
        for (NodeWrapper node : response.findAll("customer")) {
            items.add(new Customer(node));
        }

        return items;
    }

    /**
     * Creates a {@link Customer}.
     *
     * @param request
     *            the request.
     * @return a {@link Result}.
     */
    public Result<Customer> create(CustomerRequest request) {
        NodeWrapper node = http.post(configuration.getMerchantPath() + "/customers", request);
        return new Result<Customer>(node, Customer.class);
    }

    /**
     * Deletes a {@link Customer} by id.
     *
     * @param id
     *            the id of the {@link Customer}.
     * @return a {@link Result}.
     */
    public Result<Customer> delete(String id) {
        http.delete(configuration.getMerchantPath() + "/customers/" + id);
        return new Result<Customer>();
    }

    /**
     * Finds a {@link Customer} by id.
     *
     * @param id
     *            the id of the {@link Customer}.
     * @return the {@link Customer} or raises a
     *         {@link com.braintreegateway.exceptions.NotFoundException}.
     */
    public Customer find(String id) {
        if(id == null || id.trim().equals(""))
            throw new NotFoundException();

        return new Customer(http.get(configuration.getMerchantPath() + "/customers/" + id));
    }

    /**
     * Finds a {@link Customer} by id.
     *
     * @param id
     *            the id of the {@link Customer}.
     * @param associationFilterId
     *            the id of the association filter to use.
     * @return the {@link Customer} or raises a
     *         {@link com.braintreegateway.exceptions.NotFoundException}.
     */
    public Customer find(String id, String associationFilterId) {
        if(id == null || id.trim().equals(""))
            throw new NotFoundException();

        if(associationFilterId == null || associationFilterId.isEmpty())
            throw new NotFoundException();

        String queryParams = "?association_filter_id=" + associationFilterId;
        return new Customer(http.get(configuration.getMerchantPath() + "/customers/" + id + queryParams));
    }

    /**
     * Finds all Transactions that match the query and returns a {@link ResourceCollection}.
     * See: <a href="https://developers.braintreepayments.com/reference/request/transaction/search/java" target="_blank">https://developers.braintreepayments.com/reference/request/transaction/search/java</a>
     * @param query the request query to use for search
     * @return a {@link ResourceCollection}.
     */
    public ResourceCollection<Customer> search(CustomerSearchRequest query) {
        NodeWrapper node = http.post(configuration.getMerchantPath() + "/customers/advanced_search_ids", query);
        return new ResourceCollection<Customer>(new CustomerPager(this, query), node);
    }

    /**
     * Updates a {@link Customer}.
     *
     * @param id
     *            the id of the {@link Customer}.
     * @param request
     *            the request.
     * @return a {@link Result}.
     */
    public Result<Customer> update(String id, CustomerRequest request) {
        NodeWrapper node = http.put(configuration.getMerchantPath() + "/customers/" + id, request);
        return new Result<Customer>(node, Customer.class);
    }

}