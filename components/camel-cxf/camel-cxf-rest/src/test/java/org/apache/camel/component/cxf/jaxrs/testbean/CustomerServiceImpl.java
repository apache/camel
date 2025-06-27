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
package org.apache.camel.component.cxf.jaxrs.testbean;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;

import org.apache.cxf.common.util.StringUtils;

@Path("/customerservice/")
public class CustomerServiceImpl implements CustomerService {
    private final AtomicLong currentId = new AtomicLong(123L);
    private final Map<Long, Customer> customers = new ConcurrentHashMap<>();
    private final Map<Long, Order> orders = new ConcurrentHashMap<>();

    public CustomerServiceImpl() {
        init();
    }

    @Override
    public Customer getCustomer(@PathParam("id") String id) {
        long idNumber = Long.parseLong(id);
        Customer c = customers.get(idNumber);
        return c;
    }

    @Override
    public Customer getCustomerByQueryParam(@QueryParam("id") String id) {
        long idNumber = Long.parseLong(id);
        Customer c = customers.get(idNumber);
        return c;
    }

    @Override
    public List<Customer> getCustomers() {
        List<Customer> list = new ArrayList<>(customers.values());
        return list;
    }

    @Override
    public Response updateCustomer(Customer customer) {
        Customer c = customers.get(customer.getId());
        Response r;
        if (c != null) {
            customers.put(customer.getId(), customer);
            r = Response.ok().build();
        } else {
            r = Response.status(406).entity("Cannot find the customer!").build();
        }

        return r;
    }

    @Override
    public Response addCustomer(Customer customer) {
        if (StringUtils.isEmpty(customer.getName())) {
            return Response.status(422).build();
        }

        customer.setId(currentId.incrementAndGet());

        customers.put(customer.getId(), customer);

        return Response.ok(customer).build();
    }

    @Override
    public Response addCustomerUniqueResponseCode(Customer customer) {
        customer.setId(currentId.incrementAndGet());

        customers.put(customer.getId(), customer);

        return Response.status(201).entity(customer).build();
    }

    @Override
    public Response deleteCustomer(@PathParam("id") String id) {
        long idNumber = Long.parseLong(id);
        Customer c = customers.get(idNumber);

        Response r;
        if (c != null) {
            r = Response.ok().build();
            customers.remove(idNumber);
        } else {
            r = Response.notModified().build();
        }
        if (idNumber == currentId.get()) {
            currentId.decrementAndGet();
        }
        return r;
    }

    @Override
    public Order getOrder(@PathParam("orderId") String orderId) {
        long idNumber = Long.parseLong(orderId);
        Order c = orders.get(idNumber);
        return c;
    }

    final void init() {
        Customer c = new Customer();
        c.setName("John");
        c.setId(123);
        customers.put(c.getId(), c);

        c = new Customer();
        c.setName("Dan");
        c.setId(113);
        customers.put(c.getId(), c);

        Order o = new Order();
        o.setDescription("order 223");
        o.setId(223);
        orders.put(o.getId(), o);
    }

}
