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

import javax.activation.DataHandler;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import org.apache.cxf.jaxrs.ext.multipart.Multipart;

public class MultipartCustomer {

    @POST @Path("/{id}")
    public Response multipartPostWithParametersAndPayload(
            @QueryParam("query") String abc, @PathParam("id") String id,
            @Multipart(value = "part1", type = "image/jpeg") DataHandler dh1, 
            @Multipart(value = "part2", type = "image/jpeg") DataHandler dh2, 
            @Multipart(value = "body", type = "text/xml") Customer request) {
        return null;
    }
    
    @POST @Path("/withoutParameters")
    // Added the path due to change of CXF-6321
    public Response multipartPostWithoutParameters(
            @Multipart(value = "part1", type = "image/jpeg") DataHandler dh1, 
            @Multipart(value = "part2", type = "image/jpeg") DataHandler dh2, 
            @Multipart(value = "body", type = "text/xml") Customer request) {
        return null;
    }
    
}
