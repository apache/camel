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
package org.apache.camel.component.cxf.jaxrs.testbean;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;



/**
 *
 * @version 
 */
@Path("/customerservice/")
public class CustomerService {
    private final AtomicLong currentId = new AtomicLong(123L);
    private final Map<Long, Customer> customers = new ConcurrentHashMap<Long, Customer>();
    private final Map<Long, Order> orders = new ConcurrentHashMap<Long, Order>();

    public CustomerService() {
        init();
    }

    @GET
    @Path("/customers/{id}/")
    public Customer getCustomer(@PathParam("id") String id) {
        long idNumber = Long.parseLong(id);
        Customer c = customers.get(idNumber);
        return c;
    }
    
    @GET
    @Path("/customers")
    public Customer getCustomerByQueryParam(@QueryParam("id") String id) {
        long idNumber = Long.parseLong(id);
        Customer c = customers.get(idNumber);
        return c;
    }
    
    @GET
    @Path("/customers/")
    @Produces("application/xml")
    public List<Customer> getCustomers() {
        List<Customer> list = new ArrayList<Customer>(customers.values());
        return list;
    }
    

    @PUT
    @Path("/customers/")
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

    @POST
    @Path("/customers/")
    public Response addCustomer(Customer customer) {
        customer.setId(currentId.incrementAndGet());

        customers.put(customer.getId(), customer);
        
        return Response.ok(customer).build();
    }
    
    @POST
    @Path("/customersUniqueResponseCode/")
    public Response addCustomerUniqueResponseCode(Customer customer) {
        customer.setId(currentId.incrementAndGet());

        customers.put(customer.getId(), customer);
        
        return Response.status(201).entity(customer).build();
    }

    @DELETE
    @Path("/customers/{id}/")
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

    @Path("/orders/{orderId}/")
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
