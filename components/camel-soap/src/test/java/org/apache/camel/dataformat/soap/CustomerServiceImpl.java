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
package org.apache.camel.dataformat.soap;

import com.example.customerservice.Customer;
import com.example.customerservice.CustomerService;
import com.example.customerservice.GetAllAmericanCustomersResponse;
import com.example.customerservice.GetAllCustomersResponse;
import com.example.customerservice.GetCustomersByName;
import com.example.customerservice.GetCustomersByNameResponse;
import com.example.customerservice.NoSuchCustomer;
import com.example.customerservice.NoSuchCustomerException;
import com.example.customerservice.SaveCustomer;

/**
 * Simple implementation of CustomerService that supports returning a customer
 * or a NoSuchCustomerException depending on input
 */
public class CustomerServiceImpl implements CustomerService {
    private Customer lastSavedCustomer;

    public Customer getLastSavedCustomer() {
        return lastSavedCustomer;
    }

    public void setLastSavedCustomer(Customer lastSavedCustomer) {
        this.lastSavedCustomer = lastSavedCustomer;
    }

    /**
     * If the request.name is "none" a NoSuchCustomerException is thrown in any
     * other case a dummy customer is returned that has the same name as the
     * request
     */
    public GetCustomersByNameResponse getCustomersByName(GetCustomersByName request) throws NoSuchCustomerException {
        if ("none".equals(request.getName())) {
            NoSuchCustomer noSuchCustomer = new NoSuchCustomer();
            noSuchCustomer.setCustomerId(request.getName());
            throw new NoSuchCustomerException("Customer not found", noSuchCustomer);
        }
        GetCustomersByNameResponse response = new GetCustomersByNameResponse();
        Customer customer = new Customer();
        customer.setName(request.getName());
        customer.setRevenue(100000);
        response.getReturn().add(customer);
        return response;
    }

    /**
     * This method is to test a call without input parameter
     */
    public GetAllCustomersResponse getAllCustomers() {
        GetAllCustomersResponse response = new GetAllCustomersResponse();
        Customer customer = new Customer();
        customer.setName("Smith");
        customer.setRevenue(100000);
        response.getReturn().add(customer);
        return response;
    }
    
    /**
     * This method is to test a call without input parameter
     */
    public GetAllAmericanCustomersResponse getAllAmericanCustomers() {
        GetAllAmericanCustomersResponse response = new GetAllAmericanCustomersResponse();
        Customer customer = new Customer();
        customer.setName("Schmitz");
        customer.setRevenue(100000);
        response.getReturn().add(customer);
        return response;
    }

    public void saveCustomer(SaveCustomer request) {
        lastSavedCustomer = request.getCustomer();
    }

}
