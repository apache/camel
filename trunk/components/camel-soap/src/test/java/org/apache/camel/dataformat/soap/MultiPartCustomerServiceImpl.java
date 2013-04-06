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

import javax.xml.ws.Holder;

import com.example.customerservice.multipart.Company;
import com.example.customerservice.multipart.CompanyType;
import com.example.customerservice.multipart.Customer;
import com.example.customerservice.multipart.GetAllCustomersResponse;
import com.example.customerservice.multipart.GetCustomersByName;
import com.example.customerservice.multipart.GetCustomersByNameResponse;
import com.example.customerservice.multipart.MultiPartCustomerService;
import com.example.customerservice.multipart.Product;
import com.example.customerservice.multipart.SaveCustomer;

public class MultiPartCustomerServiceImpl implements MultiPartCustomerService {

    private Customer lastSavedCustomer;

    @Override
    public GetCustomersByNameResponse getCustomersByName(GetCustomersByName parameters, Product product) {
        if (product == null) {
            throw new IllegalArgumentException("product may not be null");
        }

        GetCustomersByNameResponse response = new GetCustomersByNameResponse();
        Customer customer = new Customer();
        customer.setName(product.getName());
        customer.setRevenue(100000);
        response.getReturn().add(customer);

        return response;
    }

    @Override
    public void saveCustomer(SaveCustomer parameters, Product product, Holder<Company> company) {
        if (product == null) {
            throw new IllegalArgumentException("product may not be null.");
        }

        if (company == null) {
            throw new IllegalArgumentException("company may not be null.");
        }

        Company returnCompany = new Company();
        returnCompany.setName("MultipartSoft");
        returnCompany.setPresident("Dr. Multipart");

        company.value = returnCompany;

        lastSavedCustomer = parameters.getCustomer();
    }

    @Override
    public void getAllCustomers(Holder<GetAllCustomersResponse> parameters, Holder<CompanyType> companyType) {
        if (companyType == null) {
            throw new IllegalArgumentException("companyType may not be null");
        }

        GetAllCustomersResponse response = new GetAllCustomersResponse();
        Customer customer = new Customer();
        customer.setName("Smith");
        customer.setRevenue(100000);
        response.getReturn().add(customer);
    }
    
    public Customer getLastSavedCustomer() {
        return lastSavedCustomer;
    }

    @Override
    public void saveCustomerToo(SaveCustomer parameters, Product product,
            Holder<Company> company) {
        if (product == null) {
            throw new IllegalArgumentException("product may not be null.");
        }

        if (company == null) {
            throw new IllegalArgumentException("company may not be null.");
        }

        Company returnCompany = new Company();
        returnCompany.setName("MultipartSoft");
        returnCompany.setPresident("Dr. Multipart");

        company.value = returnCompany;

        lastSavedCustomer = parameters.getCustomer();
    }

}
