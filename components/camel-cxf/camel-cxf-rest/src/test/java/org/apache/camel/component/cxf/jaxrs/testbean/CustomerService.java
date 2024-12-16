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

import java.util.List;

import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;

@Path("/customerservice/")
public interface CustomerService {

    @GET
    @Path("/customers/{id}/")
    public Customer getCustomer(@PathParam("id") String id);

    @GET
    @Path("/customers")
    public Customer getCustomerByQueryParam(@QueryParam("id") String id);

    @GET
    @Path("/customers/")
    @Produces("application/xml")
    public List<Customer> getCustomers();

    @PUT
    @Path("/customers/")
    public Response updateCustomer(Customer customer);

    @POST
    @Path("/customers/")
    public Response addCustomer(Customer customer);

    @POST
    @Path("/customersUniqueResponseCode/")
    public Response addCustomerUniqueResponseCode(Customer customer);

    @DELETE
    @Path("/customers/{id}/")
    public Response deleteCustomer(@PathParam("id") String id);

    @Path("/orders/{orderId}/")
    public Order getOrder(@PathParam("orderId") String orderId);

}
