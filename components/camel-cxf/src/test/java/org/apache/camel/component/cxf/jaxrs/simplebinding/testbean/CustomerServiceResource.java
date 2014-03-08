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
package org.apache.camel.component.cxf.jaxrs.simplebinding.testbean;

import java.io.InputStream;

import javax.activation.DataHandler;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import org.apache.camel.component.cxf.jaxrs.testbean.Customer;

@Path("/customerservice/")
public class CustomerServiceResource {
    
    @PathParam("id")
    private String id;

    @GET @Path("/customers/{id}/")
    public Customer getCustomer(@PathParam("id") String id) {
        return null;
    }

    @PUT @Path("/customers/{id}")
    public Response updateCustomer(Customer customer, @PathParam("id") String id) {
        return null;
    }
    
    @POST @Path("/customers/")
    public Response newCustomer(Customer customer, @PathParam("type") String type, @QueryParam("age") int age) {
        return null;
    }
    
    @Path("/customers/vip/{status}")
    public VipCustomerResource vipCustomer(@PathParam("status") String status) {
        return new VipCustomerResource();
    }

    @Consumes("image/jpeg")
    @POST @Path("/customers/{id}/image_inputstream")
    public Response uploadImageInputStream(InputStream is) {
        return null;
    }
    
    @Consumes("image/jpeg")
    @POST @Path("/customers/{id}/image_datahandler")
    public Response uploadImageDataHandler(DataHandler dh) {
        return null;
    }
    
    @Path("/customers/multipart")
    public MultipartCustomer multipart() {
        return new MultipartCustomer();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
    
}
