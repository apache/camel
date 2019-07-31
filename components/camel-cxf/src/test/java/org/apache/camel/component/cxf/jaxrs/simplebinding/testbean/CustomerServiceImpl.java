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
package org.apache.camel.component.cxf.jaxrs.simplebinding.testbean;

import java.io.InputStream;

import javax.activation.DataHandler;
import javax.ws.rs.core.Response;

import org.apache.camel.component.cxf.jaxrs.testbean.Customer;

public class CustomerServiceImpl implements CustomerService {

    public Customer getCustomer(String id) {
        return null;
    }

    @Override
    public Response updateCustomer(Customer customer, String id) {
        return null;
    }

    @Override
    public Response newCustomer(Customer customer, String type, int age) {
        return null;
    }

    @Override
    public VipCustomerResource vipCustomer(String status) {
        return new VipCustomerResource();
    }

    @Override
    public Response uploadImageInputStream(InputStream is) {
        return null;
    }

    @Override
    public Response uploadImageDataHandler(DataHandler dh) {
        return null;
    }

    @Override
    public MultipartCustomer multipart() {
        return new MultipartCustomer();
    }

    @Override
    public Customer getCustomer(String id, String test) {
        return null;
    }

}
