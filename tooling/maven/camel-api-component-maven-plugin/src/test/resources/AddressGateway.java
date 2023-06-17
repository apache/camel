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

/**
 * Provides methods to create, delete, find, and update {@link Address} objects.
 * This class does not need to be instantiated directly.
 * Instead, use {@link BraintreeGateway#address()} to get an instance of this class:
 *
 * <pre>
 * BraintreeGateway gateway = new BraintreeGateway(...);
 * gateway.address().create(...)
 * </pre>
 */
public class AddressGateway {

    private Http http;
    private Configuration configuration;

    public AddressGateway(Http http, Configuration configuration) {
        this.http = http;
        this.configuration = configuration;
    }

    /**
     * Creates an {@link Address} for a {@link Customer}.
     * @param customerId the id of the {@link Customer}.
     * @param request the request object.
     * @return a {@link Result} object.
     */
    public Result<Address> create(String customerId, AddressRequest request) {
        NodeWrapper node = http.post(configuration.getMerchantPath() + "/customers/" + customerId + "/addresses", request);
        return new Result<Address>(node, Address.class);
    }

    /**
     * Deletes a Customer's {@link Address}.
     * @param customerId the id of the {@link Customer}.
     * @param id the id of the {@link Address} to delete.
     * @return a {@link Result} object.
     */
    public Result<Address> delete(String customerId, String id) {
        http.delete(configuration.getMerchantPath() + "/customers/" + customerId + "/addresses/" + id);
        return new Result<Address>();
    }

    /**
     * Finds a Customer's {@link Address}.
     * @param customerId the id of the {@link Customer}.
     * @param id the id of the {@link Address}.
     * @return the {@link Address} or raises a {@link com.braintreegateway.exceptions.NotFoundException}.
     */
    public Address find(String customerId, String id) {
        if(customerId == null || customerId.trim().equals("") || id == null || id.trim().equals(""))
            throw new NotFoundException();

        return new Address(http.get(configuration.getMerchantPath() + "/customers/" + customerId + "/addresses/" + id));
    }


    /**
     * Updates a Customer's {@link Address}.
     * @param customerId the id of the {@link Customer}.
     * @param id the id of the {@link Address}.
     * @param request the request object containing the {@link AddressRequest} parameters.
     * @return the {@link Address} or raises a {@link com.braintreegateway.exceptions.NotFoundException}.
     */
    public Result<Address> update(String customerId, String id, AddressRequest request) {
        NodeWrapper node = http.put(configuration.getMerchantPath() + "/customers/" + customerId + "/addresses/" + id, request);
        return new Result<Address>(node, Address.class);
    }
}