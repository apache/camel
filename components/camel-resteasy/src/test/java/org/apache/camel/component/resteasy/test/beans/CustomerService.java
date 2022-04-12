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
package org.apache.camel.component.resteasy.test.beans;

import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HEAD;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

@Path("/customer")
public class CustomerService {
    private static CustomerList list;

    public CustomerService() {
        list = new CustomerList();
        list.add();
    }

    @HEAD
    @GET
    @Produces("application/json")
    @Path("/getAll")
    public Response getAllCustomers() {
        return Response.status(200).entity(list.getCustomerList()).build();
    }

    @GET
    @Produces("application/json")
    @Path("/getCustomer")
    public Response getCustomer(@QueryParam("id") Integer id) {
        Customer c = list.getCustomer(id);
        if (c != null) {
            return Response.status(200).entity(c).build();
        } else {
            return Response.status(404).entity("Customer with given id doesn't exist").build();
        }
    }

    @DELETE
    @Path("/deleteCustomer")
    public Response deleteCustomer(@QueryParam("id") Integer id) {
        Customer c = list.deleteCustomer(id);
        return Response.status(200).entity("Customer deleted : " + c).build();
    }

    @POST
    @Consumes("application/json")
    @Path("/createCustomer")
    public Response createCustomer(Customer customer) {
        list.addCustomer(customer);
        return Response.status(200).entity("Customer added : " + customer).build();
    }

    @PUT
    @Consumes("application/json")
    @Path("/updateCustomer")
    public Response updateCustomer(Customer customer) {
        Customer update = list.getCustomer(customer.getId());
        if (update != null) {
            list.deleteCustomer(customer.getId());
            list.addCustomer(customer);
            return Response.status(200).entity("Customer updated : " + customer).build();
        } else {
            return Response.status(404).entity("Customer with given id doesn't exist").build();
        }
    }

    /*
        Specific methods for servlets used in proxy producer test
     */
    // Really forced methods for testing proxy producer with more parameters
    @GET
    @Produces("application/json")
    @Path("/getSpecificThreeCustomers")
    public Response getSpecificThreeCustomers(
            @QueryParam("c1") Integer customerId1, @QueryParam("c2") Integer customerId2,
            @QueryParam("c3") Integer customerId3) {
        List<Customer> customers = new ArrayList<>();
        customers.add(list.getCustomer(customerId1));
        customers.add(list.getCustomer(customerId2));
        customers.add(list.getCustomer(customerId3));

        return Response.status(200).entity(customers).build();
    }

    @POST
    @Produces("application/json")
    @Consumes("application/json")
    @Path("/checkCustomer")
    public Response checkIfCustomerExists(@QueryParam("c1") Integer customerId1, Customer customer) {
        Customer foundCustomer = list.getCustomer(customerId1);
        if (foundCustomer.equals(customer)) {
            return Response.status(200).entity("Customers are equal").build();
        } else {
            return Response.status(200).entity("Customers are not equal").build();
        }

    }

    @GET
    @Produces("application/json")
    @Path("/getCustomerWithoutResponse")
    public Customer getCustomerWithoutResponse(@QueryParam("c1") Integer customerId1) {
        Customer c = list.getCustomer(customerId1);
        return c;
    }

}
